package net.kroia.stockmarket.screen;

import com.google.gson.JsonParser;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.CheckBox;
import net.kroia.modutilities.gui.elements.CloseButton;
import net.kroia.modutilities.gui.elements.ItemView;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.TextBox;
import net.kroia.modutilities.gui.elements.VerticalListView;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.modutilities.gui.elements.base.ListView;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.modutilities.gui.screens.CreativeModeItemSelectionScreen;
import net.kroia.modutilities.networking.client_server.arrs.AsynchronousRequestResponseSystem;
import net.kroia.modutilities.setting.Setting;
import net.kroia.modutilities.setting.SettingsGroup;
import net.kroia.modutilities.setting.SettingsStore;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModSettings;
import net.kroia.stockmarket.networking.request.ModSettingsRequest;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Master-only admin screen for editing the server's {@code settings.json} in-game.
 * Opened from the ManagementScreen's Overview tab via the "Mod Settings" button
 * (visible only when connected to the master server; the server additionally
 * enforces op level 2 + master status in {@link ModSettingsRequest}).
 * <p>
 * The screen never reads client-side settings — on open it fetches the current
 * values from the master (GET), and "Apply" sends the edited values (SET); the
 * server validates/clamps, persists to settings.json and returns the confirmed
 * state, which is re-displayed.
 * <p>
 * Fields are built generically from {@link StockMarketModSettings#getEditableGroups()}:
 * Booleans become checkboxes, Integer/Long/Float become numeric text boxes and the
 * ItemStack currency gets an item view + creative-inventory item picker.
 * <p>
 * <b>DEVELOPER NOTE — when adding a new setting:</b>
 * <ol>
 *   <li>You MUST also add a matching editing field to this screen. Settings of a
 *       supported type (Boolean/Integer/Long/Float/ItemStack) inside a registered
 *       group appear automatically via the generic row builder — still add the
 *       label/tooltip lang keys ({@code gui.stockmarket.mod_settings_screen.setting.<Group>.<NAME>})
 *       and verify the field renders and applies correctly. Unsupported types need
 *       a dedicated editor row.</li>
 *   <li>If the new setting is only read ONCE at startup (cached for the server
 *       lifetime), mark the field as restart-required by adding
 *       {@code "<Group>.<NAME>"} to {@link #RESTART_REQUIRED_SETTINGS}.</li>
 *   <li>Consider whether the value must be propagated to slave servers after a
 *       change — see the per-group propagation decision documented in
 *       {@link ModSettingsRequest#handleOnMasterServer}.</li>
 * </ol>
 */
public class ModSettingsScreen extends StockMarketGuiScreen {

    private static class Texts {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".mod_settings_screen.";
        public static final Component TITLE = Component.translatable(PREFIX + "title");
        public static final Component APPLY = Component.translatable(PREFIX + "apply");
        public static final Component RELOAD = Component.translatable(PREFIX + "reload");
        public static final Component DEFAULTS = Component.translatable(PREFIX + "defaults");
        public static final Component DEFAULTS_TOOLTIP = Component.translatable(PREFIX + "defaults.tooltip");
        public static final Component SELECT_ITEM = Component.translatable(PREFIX + "select_item");
        public static final Component RESTART_REQUIRED = Component.translatable(PREFIX + "restart_required");
        public static final Component RESTART_REQUIRED_TOOLTIP = Component.translatable(PREFIX + "restart_required.tooltip");
        public static final Component STATUS_LOADING = Component.translatable(PREFIX + "status.loading");
        public static final Component STATUS_LOADED = Component.translatable(PREFIX + "status.loaded");
        public static final Component STATUS_SAVED = Component.translatable(PREFIX + "status.saved");
        public static final Component STATUS_FAILED = Component.translatable(PREFIX + "status.failed");

        /** Heading label of a settings group ("Utilities", "ServerMarket", "VillagerTrading"). */
        public static Component group(String groupName) {
            return Component.translatable(PREFIX + "group." + groupName);
        }
        /** Display label of one setting. */
        public static Component setting(String groupName, String settingName) {
            return Component.translatable(PREFIX + "setting." + groupName + "." + settingName);
        }
        /** Hover tooltip of one setting. */
        public static Component settingTooltip(String groupName, String settingName) {
            return Component.translatable(PREFIX + "setting." + groupName + "." + settingName + ".tooltip");
        }
    }

    private static final int ELEMENT_HEIGHT = 20;
    private static final int STATUS_COLOR_OK = 0xFF40D040;
    private static final int STATUS_COLOR_ERROR = 0xFFF04040;
    private static final int STATUS_COLOR_NEUTRAL = 0xFFC0C0C0;
    private static final int RESTART_MARKER_COLOR = 0xFFFFAA30;
    private static final int GROUP_HEADING_COLOR = 0xFFFFD966;

    /**
     * Settings ("&lt;GroupName&gt;.&lt;SETTING_NAME&gt;") whose value is only read once at
     * startup and cached for the server lifetime — editing them requires a server
     * restart to take effect. Verified consumption points:
     * <ul>
     *   <li>{@code ServerMarket.VIRTUAL_ORDERBOOK_DEFAULT_ARRAY_SIZE}: read once per
     *       market in the VirtualOrderbook constructor — only NEWLY created markets
     *       pick up a changed value.</li>
     *   <li>{@code ServerMarket.CURRENCY}: the trading currency ItemID is resolved
     *       lazily once and cached in ServerMarketManager ({@code tradingCurrencyID})
     *       and pushed to BankSystem at startup. (The villager-trade price table
     *       does pick it up on recompute, but market settlement does not.)</li>
     * </ul>
     * All other registered settings are read live at their point of use
     * (autosave interval and logging flags per call/tick, candle time per tick,
     * villager settings per table recompute).
     */
    private static final Set<String> RESTART_REQUIRED_SETTINGS = Set.of(
            "ServerMarket.VIRTUAL_ORDERBOOK_DEFAULT_ARRAY_SIZE",
            "ServerMarket.CURRENCY"
    );

    /**
     * Binds one GUI editor widget to one {@link Setting} of the local working copy.
     * {@link #loadFromSetting()} writes setting → widget, {@link #storeToSetting()}
     * writes widget → setting (invalid input keeps the current setting value).
     */
    private interface FieldBinding {
        void loadFromSetting();
        void storeToSetting();
    }

    /**
     * Local working copy of the server settings. Populated from the master's GET
     * response, edited through the field bindings and serialized as the SET payload.
     * Never persisted locally — the master remains the single source of truth.
     */
    private final StockMarketModSettings workingCopy = new StockMarketModSettings();
    private final List<FieldBinding> bindings = new ArrayList<>();

    private final Label titleLabel;
    private final CloseButton closeButton;
    private final ListView listView;
    private final Label statusLabel;
    private final Button defaultsButton;
    private final Button reloadButton;
    private final Button applyButton;

    /**
     * Creates the screen and immediately fetches the current settings from the master.
     *
     * @param parent the screen to return to when this screen is closed
     *               (normally the ManagementScreen)
     */
    public ModSettingsScreen(StockMarketGuiScreen parent) {
        super(Texts.TITLE, parent);

        titleLabel = new Label(Texts.TITLE.getString());
        titleLabel.setAlignment(Label.Alignment.LEFT);

        closeButton = new CloseButton(this::onClose);

        listView = new VerticalListView();
        LayoutVertical layout = new LayoutVertical();
        layout.stretchX = true;
        layout.stretchY = false;
        listView.setLayout(layout);

        statusLabel = new Label("");
        statusLabel.setAlignment(Label.Alignment.LEFT);

        defaultsButton = new Button(Texts.DEFAULTS.getString(), this::onDefaults);
        defaultsButton.setHoverTooltipSupplier(Texts.DEFAULTS_TOOLTIP::getString);
        defaultsButton.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);
        // The button sits in the bottom-right corner: anchor the cursor at the
        // tooltip's BOTTOM-RIGHT corner so it extends left and up, staying inside
        // the window.
        defaultsButton.setHoverTooltipMousePositionAlignment(GuiElement.Alignment.BOTTOM_RIGHT);

        reloadButton = new Button(Texts.RELOAD.getString(), this::loadFromServer);
        applyButton = new Button(Texts.APPLY.getString(), this::onApply);

        buildRows();

        addElement(titleLabel);
        addElement(closeButton);
        addElement(listView);
        addElement(statusLabel);
        addElement(defaultsButton);
        addElement(reloadButton);
        addElement(applyButton);

        loadFromServer();
    }

    @Override
    protected void updateLayout(Gui gui) {
        int p = StockMarketGuiElement.padding;
        int s = StockMarketGuiElement.spacing;
        int w = getWidth() - 2 * p;

        closeButton.setBounds(getWidth() - p - 20, p, 20, 20);
        titleLabel.setBounds(p, p, w - 20 - s, ELEMENT_HEIGHT);

        int bottomY = getHeight() - p - ELEMENT_HEIGHT;
        int buttonW = Math.max(60, w / 8);
        applyButton.setBounds(getWidth() - p - buttonW, bottomY, buttonW, ELEMENT_HEIGHT);
        reloadButton.setBounds(applyButton.getLeft() - s - buttonW, bottomY, buttonW, ELEMENT_HEIGHT);
        defaultsButton.setBounds(reloadButton.getLeft() - s - buttonW, bottomY, buttonW, ELEMENT_HEIGHT);
        statusLabel.setBounds(p, bottomY, defaultsButton.getLeft() - s - p, ELEMENT_HEIGHT);

        int listTop = titleLabel.getBottom() + s;
        listView.setBounds(p, listTop, w, bottomY - s - listTop);
    }

    // ------------------------------------------------------------------
    // Row construction
    // ------------------------------------------------------------------

    /**
     * Builds one heading row per group and one editor row per setting, generically
     * from the working copy's editable groups.
     */
    private void buildRows() {
        for (SettingsGroup group : workingCopy.getEditableGroups()) {
            Label heading = new Label(Texts.group(group.getName()).getString());
            heading.setAlignment(Label.Alignment.LEFT);
            heading.setTextColor(GROUP_HEADING_COLOR);
            heading.setHeight(ELEMENT_HEIGHT);
            listView.addChild(heading);

            for (Setting<?> setting : group.getAllSettings()) {
                listView.addChild(new SettingRow(group, setting));
            }
        }
    }

    /**
     * A single "label | editor [| restart marker]" row for one setting.
     * Creates the appropriate editor widget for the setting's value type and
     * registers a {@link FieldBinding} for it.
     */
    private final class SettingRow extends StockMarketGuiElement {
        private final Label nameLabel;
        private CheckBox checkBox = null;
        private TextBox textBox = null;
        private ItemView itemView = null;
        private Button selectItemButton = null;
        private Label unsupportedLabel = null;
        private Label restartMarker = null;

        SettingRow(SettingsGroup group, Setting<?> setting) {
            setEnableBackground(false);
            setHeight(ELEMENT_HEIGHT + spacing);

            String tooltip = Texts.settingTooltip(group.getName(), setting.getName()).getString();

            nameLabel = new Label(Texts.setting(group.getName(), setting.getName()).getString());
            nameLabel.setAlignment(Label.Alignment.LEFT);
            nameLabel.setHoverTooltipSupplier(() -> tooltip);
            nameLabel.setHoverTooltipFontScale(hoverToolTipFontSize);
            addChild(nameLabel);

            createEditor(setting, tooltip);

            if (RESTART_REQUIRED_SETTINGS.contains(group.getName() + "." + setting.getName())) {
                // Clearly visible warning-colored marker + explanatory hover tooltip
                // (the "restart" symbol is part of the lang string).
                restartMarker = new Label(Texts.RESTART_REQUIRED.getString());
                restartMarker.setAlignment(Label.Alignment.RIGHT);
                restartMarker.setTextColor(RESTART_MARKER_COLOR);
                restartMarker.setHoverTooltipSupplier(Texts.RESTART_REQUIRED_TOOLTIP::getString);
                restartMarker.setHoverTooltipFontScale(hoverToolTipFontSize);
                // The marker sits at the far right edge of the screen: anchor the
                // cursor at the tooltip's TOP-RIGHT corner so the tooltip body
                // extends to the LEFT and stays inside the window.
                restartMarker.setHoverTooltipMousePositionAlignment(Alignment.TOP_RIGHT);
                addChild(restartMarker);
            }
        }

        /**
         * Creates the editor widget matching the setting's value type and registers
         * the corresponding {@link FieldBinding} (unchecked casts are safe because
         * the widget was chosen from {@code setting.getType()}).
         */
        @SuppressWarnings("unchecked")
        private void createEditor(Setting<?> setting, String tooltip) {
            java.lang.reflect.Type type = setting.getType();

            if (type == Boolean.class) {
                Setting<Boolean> boolSetting = (Setting<Boolean>) setting;
                checkBox = new CheckBox("");
                checkBox.setHoverTooltipSupplier(() -> tooltip);
                checkBox.setHoverTooltipFontScale(hoverToolTipFontSize);
                // Editors stretch to the right screen edge — anchor tooltips at the
                // cursor's TOP-RIGHT so they extend left and stay inside the window.
                checkBox.setHoverTooltipMousePositionAlignment(Alignment.TOP_RIGHT);
                addChild(checkBox);
                bindings.add(new FieldBinding() {
                    @Override public void loadFromSetting() {
                        checkBox.setChecked(Boolean.TRUE.equals(boolSetting.get()));
                    }
                    @Override public void storeToSetting() {
                        boolSetting.set(checkBox.isChecked());
                    }
                });
            }
            else if (type == Integer.class || type == Long.class) {
                textBox = new TextBox();
                // Positive whole numbers only (all current int/long settings are positive).
                textBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 12, 0));
                textBox.setHoverTooltipSupplier(() -> tooltip);
                textBox.setHoverTooltipFontScale(hoverToolTipFontSize);
                // Editors stretch to the right screen edge — tooltip extends left.
                textBox.setHoverTooltipMousePositionAlignment(Alignment.TOP_RIGHT);
                addChild(textBox);
                if (type == Integer.class) {
                    Setting<Integer> intSetting = (Setting<Integer>) setting;
                    bindings.add(new FieldBinding() {
                        @Override public void loadFromSetting() {
                            textBox.setText(intSetting.get() != null ? intSetting.get() : 0);
                        }
                        @Override public void storeToSetting() {
                            try { intSetting.set(textBox.getInt()); }
                            catch (Exception e) { /* invalid input — keep the current value */ }
                        }
                    });
                } else {
                    Setting<Long> longSetting = (Setting<Long>) setting;
                    bindings.add(new FieldBinding() {
                        @Override public void loadFromSetting() {
                            textBox.setText(longSetting.get() != null ? longSetting.get() : 0L);
                        }
                        @Override public void storeToSetting() {
                            try { longSetting.set(textBox.getLong()); }
                            catch (Exception e) { /* invalid input — keep the current value */ }
                        }
                    });
                }
            }
            else if (type == Float.class) {
                Setting<Float> floatSetting = (Setting<Float>) setting;
                textBox = new TextBox();
                // Positive decimals with up to 4 decimal digits (margins etc.).
                textBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 10, 4));
                textBox.setHoverTooltipSupplier(() -> tooltip);
                textBox.setHoverTooltipFontScale(hoverToolTipFontSize);
                // Editors stretch to the right screen edge — tooltip extends left.
                textBox.setHoverTooltipMousePositionAlignment(Alignment.TOP_RIGHT);
                addChild(textBox);
                bindings.add(new FieldBinding() {
                    @Override public void loadFromSetting() {
                        float v = floatSetting.get() != null ? floatSetting.get() : 0f;
                        textBox.setText(formatFloat(v));
                    }
                    @Override public void storeToSetting() {
                        try { floatSetting.set((float) textBox.getDouble()); }
                        catch (Exception e) { /* invalid input — keep the current value */ }
                    }
                });
            }
            else if (type == ItemStack.class) {
                Setting<ItemStack> stackSetting = (Setting<ItemStack>) setting;
                itemView = new ItemView();
                itemView.setShowTooltip(true);
                addChild(itemView);
                // Opens the vanilla creative inventory as an item picker; clicking an
                // item selects it (count forced to 1) and returns to this screen.
                selectItemButton = new Button(Texts.SELECT_ITEM.getString(), () -> {
                    CreativeModeItemSelectionScreen selectionScreen = new CreativeModeItemSelectionScreen(
                            clickedStack -> {
                                ItemStack copy = clickedStack.copy();
                                copy.setCount(1);
                                stackSetting.set(copy);
                                itemView.setItemStack(copy);
                                Minecraft.getInstance().setScreen(ModSettingsScreen.this);
                            },
                            () -> Minecraft.getInstance().setScreen(ModSettingsScreen.this));
                    Minecraft.getInstance().setScreen(selectionScreen);
                });
                selectItemButton.setHoverTooltipSupplier(() -> tooltip);
                selectItemButton.setHoverTooltipFontScale(hoverToolTipFontSize);
                // Editors stretch to the right screen edge — tooltip extends left.
                selectItemButton.setHoverTooltipMousePositionAlignment(Alignment.TOP_RIGHT);
                addChild(selectItemButton);
                bindings.add(new FieldBinding() {
                    @Override public void loadFromSetting() {
                        ItemStack v = stackSetting.get();
                        itemView.setItemStack(v != null ? v : ItemStack.EMPTY);
                    }
                    @Override public void storeToSetting() {
                        // The setting is already updated on selection; re-assert from the
                        // displayed stack for robustness.
                        ItemStack shown = itemView.getItemStack();
                        if (shown != null && !shown.isEmpty()) stackSetting.set(shown);
                    }
                });
            }
            else {
                // Defensive fallback for future settings with unsupported types:
                // show the raw value read-only instead of hiding the setting.
                unsupportedLabel = new Label(String.valueOf(setting.get()));
                unsupportedLabel.setAlignment(Label.Alignment.LEFT);
                unsupportedLabel.setTextColor(STATUS_COLOR_NEUTRAL);
                addChild(unsupportedLabel);
                bindings.add(new FieldBinding() {
                    @Override public void loadFromSetting() {
                        unsupportedLabel.setText(String.valueOf(setting.get()));
                    }
                    @Override public void storeToSetting() { /* read-only */ }
                });
            }
        }

        @Override
        protected void render() {}

        @Override
        protected void layoutChanged() {
            int w = getWidth() - 2 * padding;
            int labelW = (int) (w * 0.45);
            int markerW = restartMarker != null ? (int) (w * 0.2) : 0;
            int editorX = padding + labelW + spacing;
            int editorW = w - labelW - spacing - (markerW > 0 ? markerW + spacing : 0);

            nameLabel.setBounds(padding, padding / 2, labelW, ELEMENT_HEIGHT);

            if (checkBox != null)
                checkBox.setBounds(editorX, padding / 2, editorW, ELEMENT_HEIGHT);
            if (textBox != null)
                textBox.setBounds(editorX, padding / 2, editorW, ELEMENT_HEIGHT);
            if (itemView != null) {
                itemView.setBounds(editorX, padding / 2, ELEMENT_HEIGHT, ELEMENT_HEIGHT);
                selectItemButton.setBounds(itemView.getRight() + spacing, padding / 2,
                        editorW - ELEMENT_HEIGHT - spacing, ELEMENT_HEIGHT);
            }
            if (unsupportedLabel != null)
                unsupportedLabel.setBounds(editorX, padding / 2, editorW, ELEMENT_HEIGHT);
            if (restartMarker != null)
                restartMarker.setBounds(padding + w - markerW, padding / 2, markerW, ELEMENT_HEIGHT);
        }
    }

    // ------------------------------------------------------------------
    // Server communication
    // ------------------------------------------------------------------

    /**
     * Fetches the current settings from the master server (GET) and fills all
     * editor fields with the response.
     */
    private void loadFromServer() {
        setStatus(Texts.STATUS_LOADING.getString(), STATUS_COLOR_NEUTRAL);
        applyButton.setClickable(false);
        ModSettingsRequest.InputData input = new ModSettingsRequest.InputData(ModSettingsRequest.Action.GET, "");
        AsynchronousRequestResponseSystem.sendRequestToServer(BACKEND_INSTANCES.NETWORKING.MOD_SETTINGS_REQUEST, input)
                .thenAccept(output -> Minecraft.getInstance().execute(() -> handleResponse(output, Texts.STATUS_LOADED.getString())));
    }

    /**
     * Sends all edited values to the master server (SET). The server validates,
     * clamps, persists to settings.json and returns the confirmed state, which is
     * re-displayed (so any clamped value becomes immediately visible).
     */
    private void onApply() {
        for (FieldBinding binding : bindings)
            binding.storeToSetting();

        String payload = new SettingsStore().toJsonString(workingCopy.getEditableGroups());
        setStatus(Texts.STATUS_LOADING.getString(), STATUS_COLOR_NEUTRAL);
        applyButton.setClickable(false);
        ModSettingsRequest.InputData input = new ModSettingsRequest.InputData(ModSettingsRequest.Action.SET, payload);
        AsynchronousRequestResponseSystem.sendRequestToServer(BACKEND_INSTANCES.NETWORKING.MOD_SETTINGS_REQUEST, input)
                .thenAccept(output -> Minecraft.getInstance().execute(() -> handleResponse(output, Texts.STATUS_SAVED.getString())));
    }

    /**
     * Resets all FIELDS to the compile-time default values. Nothing is sent to the
     * server until the admin presses Apply.
     */
    private void onDefaults() {
        for (SettingsGroup group : workingCopy.getEditableGroups())
            group.setToDefaultValue();
        refreshFields();
        setStatus("", STATUS_COLOR_NEUTRAL);
    }

    /**
     * Applies a GET/SET response: loads the confirmed server state into the working
     * copy, refreshes all fields and updates the status label.
     *
     * @param output        the server response
     * @param successStatus status text shown when the response reports success
     */
    private void handleResponse(ModSettingsRequest.OutputData output, String successStatus) {
        applyButton.setClickable(true);
        if (output.settingsJson() != null && !output.settingsJson().isEmpty()) {
            try {
                new SettingsStore().fromJson(workingCopy.getEditableGroups(),
                        JsonParser.parseString(output.settingsJson()));
                refreshFields();
            } catch (Exception e) {
                error("Failed to parse mod settings response", e);
                setStatus(Texts.STATUS_FAILED.getString(), STATUS_COLOR_ERROR);
                return;
            }
        }
        if (output.success()) {
            setStatus(successStatus, STATUS_COLOR_OK);
        } else {
            String msg = output.message() == null || output.message().isEmpty()
                    ? Texts.STATUS_FAILED.getString() : output.message();
            setStatus(msg, STATUS_COLOR_ERROR);
        }
    }

    /** Writes all working-copy values into their editor widgets. */
    private void refreshFields() {
        for (FieldBinding binding : bindings)
            binding.loadFromSetting();
    }

    private void setStatus(String text, int color) {
        statusLabel.setText(text);
        statusLabel.setTextColor(color);
    }

    /** Formats a float without trailing zeros ("0.8" instead of "0.8000"). */
    private static String formatFloat(float value) {
        String s = String.format(Locale.ROOT, "%.4f", value);
        s = s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
        return s;
    }
}

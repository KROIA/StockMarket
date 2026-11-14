package net.kroia.stockmarket.screen;

import com.google.gson.JsonParser;
import net.kroia.modutilities.PermissionCompat;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.client.ClientCompat;
import net.kroia.modutilities.gui.client.GuiScreen;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.CheckBox;
import net.kroia.modutilities.gui.elements.CloseButton;
import net.kroia.modutilities.gui.elements.FeatureToggleSection;
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
import net.kroia.stockmarket.pluginsystem.plugins.screen.NewsDialogScreen;
import net.kroia.stockmarket.screen.uiElements.ApplyButton;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.kroia.stockmarket.util.StockMarketPermissions;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Master-only admin screen for editing the server's {@code settings.json} in-game.
 * Opened from the ManagementScreen's Overview tab via the "Mod Settings" button
 * (visible only when {@link #canOpen()} holds; the server additionally enforces
 * operator level 2 + StockMarket admin + master status in {@link ModSettingsRequest}).
 * <p>
 * The screen never reads client-side settings — on open it fetches the current
 * values from the master (GET), and "Apply" sends the edited values (SET); the
 * server validates/clamps, persists to settings.json and returns the confirmed
 * state, which is re-displayed.
 * <p>
 * Fields are built generically from {@link StockMarketModSettings#getEditableGroups()}:
 * Booleans become checkboxes, Integer/Long/Float become numeric text boxes and the
 * ItemStack currency gets an item view + creative-inventory item picker.
 * Exception: the Integrations group is laid out by hand as collapsible on/off
 * sections ({@link #buildIntegrationSections}); new settings there must be added
 * explicitly.
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
        /** T-227: a GET got no answer (timeout or connection lost); nothing was changed. */
        public static final Component STATUS_NO_RESPONSE = Component.translatable(PREFIX + "status.no_response");
        /** T-227: a SET / apply-to-all got no answer; the master may still have applied it. */
        public static final Component STATUS_OUTCOME_UNKNOWN = Component.translatable(PREFIX + "status.outcome_unknown");
        /** T-179: "Apply to all markets" button next to MAX_ORDER_VOLUME_FACTOR (also the confirm label). */
        public static final Component APPLY_ALL_MAX_ORDER = Component.translatable(PREFIX + "apply_all_max_order");
        public static final Component APPLY_ALL_MAX_ORDER_TOOLTIP = Component.translatable(PREFIX + "apply_all_max_order.tooltip");
        public static final Component APPLY_ALL_MAX_ORDER_CONFIRM_TITLE = Component.translatable(PREFIX + "apply_all_max_order.confirm_title");
        /** Args: market count, saved factor. Line breaks separate dialog lines. */
        public static final String APPLY_ALL_MAX_ORDER_CONFIRM_MESSAGE = PREFIX + "apply_all_max_order.confirm_message";
        /** Args: updated market count, factor. */
        public static final String APPLY_ALL_MAX_ORDER_DONE = PREFIX + "apply_all_max_order.done";

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
        /** T-217: description text of an Integrations section (one label per line of the lang text). */
        public static Component settingDescription(String groupName, String settingName) {
            return Component.translatable(PREFIX + "setting." + groupName + "." + settingName + ".description");
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
     *   <li>{@code ServerMarket.CANDLE_USE_GAME_TIME} (T-218): cached once by
     *       ServerMarketManager.isCandleUseGameTime() and sent to clients on join.</li>
     *   <li>{@code Integrations.PLACEHOLDERS_ENABLED} (T-217): checked once at server start
     *       (placeholder registration is skipped when off).</li>
     *   <li>{@code Integrations.SYNC_ADMIN_FLAG_WITH_PERMISSION} (T-217): the LuckPerms listener
     *       is registered at server start only while it is on.</li>
     * </ul>
     * All other registered settings are read live at their point of use
     * (autosave interval and logging flags per call/tick, candle time per tick,
     * villager settings per table recompute).
     */
    private static final Set<String> RESTART_REQUIRED_SETTINGS = Set.of(
            "ServerMarket.VIRTUAL_ORDERBOOK_DEFAULT_ARRAY_SIZE",
            "ServerMarket.CURRENCY",
            "ServerMarket.CANDLE_USE_GAME_TIME",
            "Integrations.PLACEHOLDERS_ENABLED",
            "Integrations.SYNC_ADMIN_FLAG_WITH_PERMISSION"
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
    /**
     * T-179: MAX_ORDER_VOLUME_FACTOR as last confirmed by the master (GET/SET response); -1 until
     * loaded. "Apply to all markets" always uses this SAVED value, never an unsaved edit.
     */
    private long savedMaxOrderVolumeFactor = -1;
    private final List<FieldBinding> bindings = new ArrayList<>();
    /**
     * T-219: the current editor value of every field ({@link SettingRow#editorValue()}, and the header
     * check box of each Integrations section, T-217), in the same order as {@link #savedEditorValues}.
     */
    private final List<Supplier<Object>> editorValues = new ArrayList<>();
    /**
     * T-219: editor values ({@link #editorValues}) as last loaded from / confirmed by
     * the master. Empty until the first GET response, so nothing counts as unsaved before that.
     */
    private final List<Object> savedEditorValues = new ArrayList<>();

    private final Label titleLabel;
    private final CloseButton closeButton;
    private final ListView listView;
    private final Label statusLabel;
    private final Button defaultsButton;
    private final Button reloadButton;
    /** T-219: turns orange while any field differs from {@link #savedEditorValues}. */
    private final ApplyButton applyButton;
    /** T-243: check box of VillagerTrading.REPLACE_EMERALDS_FOR_UNLISTED; drives {@link #usePresetPricesCheckBox}. */
    private CheckBox replaceEmeraldsCheckBox = null;
    /**
     * T-243: check box of VillagerTrading.USE_PRESET_PRICES_FOR_VILLAGERS; greyed out (not editable)
     * while the emerald replacement is off, because the server only uses it then.
     */
    private CheckBox usePresetPricesCheckBox = null;

    /**
     * SM-300: client-side gate for the Mod Settings button and for opening this screen.
     * True only on the master server when the local player has vanilla operator level 2.
     * The StockMarket admin flag is not stored on the client: the only way to reach this
     * screen is the ManagementScreen, which the server opens only for admins
     * ({@code /stockmarket manage} checks the flag). This is UI convenience only;
     * {@link ModSettingsRequest} enforces op level 2 + admin flag + master server.
     *
     * @return true if the Mod Settings screen may be shown
     */
    public static boolean canOpen() {
        return isMasterServer()
                && Minecraft.getInstance().player != null
                && PermissionCompat.hasPermission(Minecraft.getInstance().player, StockMarketPermissions.FALLBACK_LEVEL);
    }

    /**
     * Opens the screen if {@link #canOpen()} holds; otherwise the parent stays open
     * (same refuse-to-open pattern as {@code ManagementScreen.openScreen()}).
     *
     * @param parent the screen to return to when this screen is closed
     *               (normally the ManagementScreen)
     */
    public static void open(StockMarketGuiScreen parent) {
        if (canOpen())
            ClientCompat.setScreen(new ModSettingsScreen(parent));
    }

    /**
     * Creates the screen and immediately fetches the current settings from the master.
     * Use {@link #open} so the {@link #canOpen()} gate is applied.
     *
     * @param parent the screen to return to when this screen is closed
     *               (normally the ManagementScreen)
     */
    private ModSettingsScreen(StockMarketGuiScreen parent) {
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
        applyButton = new ApplyButton(Texts.APPLY.getString(), this::onApply);

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

            if (group == workingCopy.INTEGRATIONS) {
                buildIntegrationSections(workingCopy.INTEGRATIONS);
                continue;
            }
            for (Setting<?> setting : group.getAllSettings()) {
                SettingRow row = new SettingRow(group, setting);
                editorValues.add(row::editorValue);
                listView.addChild(row);
            }
        }
    }

    /**
     * T-217: the Integrations group is laid out by hand as one collapsible {@link FeatureToggleSection}
     * per integration (same layout as BankSystem); the header check box is the integration's on/off setting.
     */
    private void buildIntegrationSections(StockMarketModSettings.Integrations group) {
        addSection(group, group.PLACEHOLDERS_ENABLED);
        addSection(group, group.SYNC_ADMIN_FLAG_WITH_PERMISSION);
        // Text only: the webhook routing lives in config/modutilities/discord/stockmarket.json (secrets), not here.
        addSection(group, group.DISCORD_ENABLED);
    }

    /**
     * T-217: adds one section titled with the toggle's setting label, its description lines as children,
     * a {@link FieldBinding} for the toggle and its dirty tracking. The header shows the static
     * restart marker when the toggle is in {@link #RESTART_REQUIRED_SETTINGS}.
     *
     * @param group  the settings group (for the lang keys)
     * @param toggle the Boolean setting the header check box edits
     * @return the section, so callers can add further child rows
     */
    private FeatureToggleSection addSection(SettingsGroup group, Setting<Boolean> toggle) {
        FeatureToggleSection section = new FeatureToggleSection(Texts.setting(group.getName(), toggle.getName()).getString());
        section.setRestartRequired(RESTART_REQUIRED_SETTINGS.contains(group.getName() + "." + toggle.getName()));
        // Labels do not wrap: the lang text carries its own line breaks.
        for (String line : Texts.settingDescription(group.getName(), toggle.getName()).getString().split("\n")) {
            Label label = new Label(line);
            label.setAlignment(Label.Alignment.LEFT);
            label.setTextColor(STATUS_COLOR_NEUTRAL);
            section.addChild(label);
        }
        bindings.add(new FieldBinding() {
            @Override public void loadFromSetting() {
                section.setFeatureEnabled(Boolean.TRUE.equals(toggle.get()));
            }
            @Override public void storeToSetting() {
                toggle.set(section.isFeatureEnabled());
            }
        });
        editorValues.add(section::isFeatureEnabled);
        listView.addChild(section);
        return section;
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
        /** T-179: "Apply to all markets" button, only on the MAX_ORDER_VOLUME_FACTOR row. */
        private Button applyAllButton = null;

        /**
         * T-219: the value currently shown in this row's editor, used to detect unsaved changes.
         * Text boxes report their raw text, so retyping "0.8" as "0.80" counts as a change.
         *
         * @return the checkbox state, the text box text, the selected item stack, or null for a
         *         read-only (unsupported type) row
         */
        Object editorValue() {
            if (checkBox != null) return checkBox.isChecked();
            if (textBox != null) return textBox.getText();
            if (itemView != null) return itemView.getItemStack();
            return null;
        }

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

            if (setting == workingCopy.MARKET.MAX_ORDER_VOLUME_FACTOR) {
                applyAllButton = new Button(Texts.APPLY_ALL_MAX_ORDER.getString(), ModSettingsScreen.this::onApplyMaxOrderVolumeToAll);
                applyAllButton.setHoverTooltipSupplier(Texts.APPLY_ALL_MAX_ORDER_TOOLTIP::getString);
                applyAllButton.setHoverTooltipFontScale(hoverToolTipFontSize);
                // Right edge of the screen: tooltip extends left.
                applyAllButton.setHoverTooltipMousePositionAlignment(Alignment.TOP_RIGHT);
                addChild(applyAllButton);
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
                // T-243: remember the two dependent villager check boxes (see tick()).
                if (setting == workingCopy.VILLAGER_TRADING.REPLACE_EMERALDS_FOR_UNLISTED)
                    replaceEmeraldsCheckBox = checkBox;
                else if (setting == workingCopy.VILLAGER_TRADING.USE_PRESET_PRICES_FOR_VILLAGERS)
                    usePresetPricesCheckBox = checkBox;
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
                                net.kroia.modutilities.gui.client.ClientCompat.setScreen(ModSettingsScreen.this);
                            },
                            () -> net.kroia.modutilities.gui.client.ClientCompat.setScreen(ModSettingsScreen.this));
                    net.kroia.modutilities.gui.client.ClientCompat.setScreen(selectionScreen);
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
            int markerW = restartMarker != null || applyAllButton != null ? (int) (w * 0.2) : 0;
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
            if (applyAllButton != null)
                applyAllButton.setBounds(padding + w - markerW, padding / 2, markerW, ELEMENT_HEIGHT);
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
                .whenComplete((output, error) -> Minecraft.getInstance().execute(() -> {
                    if (error != null || output == null) {
                        // Apply stays disabled: the fields do not hold the server state, applying them
                        // would overwrite it. Reopening the screen loads again.
                        setStatus(Texts.STATUS_NO_RESPONSE.getString(), STATUS_COLOR_ERROR);
                        return;
                    }
                    handleResponse(output, Texts.STATUS_LOADED.getString());
                }));
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
                .whenComplete((output, error) -> Minecraft.getInstance().execute(() -> {
                    if (error != null || output == null) {
                        // May have been saved; nothing is resent. Apply is usable again: SET carries the
                        // full state, so pressing it again gives the same result.
                        applyButton.setClickable(true);
                        setStatus(Texts.STATUS_OUTCOME_UNKNOWN.getString(), STATUS_COLOR_ERROR);
                        return;
                    }
                    handleResponse(output, Texts.STATUS_SAVED.getString());
                }));
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
                Long factor = workingCopy.MARKET.MAX_ORDER_VOLUME_FACTOR.get();
                savedMaxOrderVolumeFactor = factor != null ? factor : -1;
                refreshFields();
                // T-219: the fields now show the master's confirmed state — that is the new baseline.
                savedEditorValues.clear();
                for (Supplier<Object> value : editorValues)
                    savedEditorValues.add(value.get());
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

    /**
     * T-179: asks for confirmation, then lets the master recompute the max order size of EVERY
     * market from its abundance and the SAVED factor (see {@link #savedMaxOrderVolumeFactor}).
     * The popup warns that per-market custom limits are overwritten and that unsaved edits are not used.
     */
    private void onApplyMaxOrderVolumeToAll() {
        if (savedMaxOrderVolumeFactor < 1) return; // settings not loaded yet
        List<String> lines = Arrays.asList(Component.translatable(Texts.APPLY_ALL_MAX_ORDER_CONFIRM_MESSAGE,
                String.valueOf(getAvailableMarkets().size()), String.valueOf(savedMaxOrderVolumeFactor))
                .getString().split("\n"));
        GuiScreen.setScreen(NewsDialogScreen.confirmDialog(ClientCompat.screen(),
                Texts.APPLY_ALL_MAX_ORDER_CONFIRM_TITLE, lines, Texts.APPLY_ALL_MAX_ORDER,
                this::sendApplyMaxOrderVolumeToAll));
    }

    /**
     * T-179: sends {@link ModSettingsRequest.Action#APPLY_MAX_ORDER_VOLUME_TO_ALL_MARKETS} and shows the
     * updated market count. Does not reload the fields, so unsaved edits stay in the editors.
     */
    private void sendApplyMaxOrderVolumeToAll() {
        long factor = savedMaxOrderVolumeFactor;
        setStatus(Texts.STATUS_LOADING.getString(), STATUS_COLOR_NEUTRAL);
        ModSettingsRequest.InputData input = new ModSettingsRequest.InputData(
                ModSettingsRequest.Action.APPLY_MAX_ORDER_VOLUME_TO_ALL_MARKETS, "");
        AsynchronousRequestResponseSystem.sendRequestToServer(BACKEND_INSTANCES.NETWORKING.MOD_SETTINGS_REQUEST, input)
                .whenComplete((output, error) -> Minecraft.getInstance().execute(() -> {
                    if (error != null || output == null)
                        setStatus(Texts.STATUS_OUTCOME_UNKNOWN.getString(), STATUS_COLOR_ERROR); // nothing resent
                    else if (output.success())
                        setStatus(Component.translatable(Texts.APPLY_ALL_MAX_ORDER_DONE, output.message(),
                                String.valueOf(factor)).getString(), STATUS_COLOR_OK);
                    else
                        setStatus(output.message() == null || output.message().isEmpty()
                                ? Texts.STATUS_FAILED.getString() : output.message(), STATUS_COLOR_ERROR);
                }));
    }

    /** Writes all working-copy values into their editor widgets. */
    private void refreshFields() {
        for (FieldBinding binding : bindings)
            binding.loadFromSetting();
    }

    /** T-219: polls the fields every tick (catches typing, Defaults and the item picker alike). */
    @Override
    public void tick() {
        super.tick();
        applyButton.setUnsaved(hasUnsavedChanges());
        // T-243: "use preset prices" only applies while the emerald replacement is on. Greyed out
        // otherwise; its value is kept and the tooltip still explains it.
        if (replaceEmeraldsCheckBox != null && usePresetPricesCheckBox != null)
            usePresetPricesCheckBox.setInteractive(replaceEmeraldsCheckBox.isChecked());
    }

    /**
     * T-219: whether any editor differs from the last loaded/applied value. Reverting every
     * field by hand makes this false again.
     *
     * @return true if Apply would send something that is not saved on the master yet
     */
    private boolean hasUnsavedChanges() {
        if (savedEditorValues.size() != editorValues.size())
            return false; // not loaded yet
        for (int i = 0; i < editorValues.size(); i++) {
            Object current = editorValues.get(i).get();
            Object saved = savedEditorValues.get(i);
            boolean same = current instanceof ItemStack a && saved instanceof ItemStack b
                    ? ItemStack.isSameItemSameComponents(a, b) // ItemStack has no equals()
                    : Objects.equals(current, saved);
            if (!same)
                return true;
        }
        return false;
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

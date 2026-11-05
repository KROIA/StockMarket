package net.kroia.stockmarket.screen.widgets;

import net.kroia.modutilities.TimerMillis;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.CheckBox;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.TextBox;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.stockmarket.market.MarketSettings;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.networking.request.ServerTimeRequest;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class MarketSettingsWidget extends StockMarketGuiElement {

    private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".market_settings_widget.";
    private static final Component MAINTENANCE_TEXT = Component.translatable(PREFIX + "maintenance");
    private static final Component MAINTENANCE_TOOLTIP = Component.translatable(PREFIX + "maintenance.tooltip");
    private static final Component DEFAULT_PRICE_TEXT = Component.translatable(PREFIX + "default_price");
    private static final Component ABUNDANCE_TEXT = Component.translatable(PREFIX + "abundance");
    private static final Component VIRTUAL_ORDERBOOK_ENABLED_TEXT = Component.translatable(PREFIX + "virtual_orderbook_enabled");
    private static final Component VIRTUAL_ORDERBOOK_ENABLED_TOOLTIP = Component.translatable(PREFIX + "virtual_orderbook_enabled.tooltip");
    private static final Component RESET_VIRTUAL_ORDERBOOK_TEXT = Component.translatable(PREFIX + "reset_virtual_orderbook");
    private static final Component RESET_VIRTUAL_ORDERBOOK_TOOLTIP = Component.translatable(PREFIX + "reset_virtual_orderbook.tooltip");
    private static final Component CLEAR_VIRTUAL_ORDERBOOK_TEXT = Component.translatable(PREFIX + "clear_virtual_orderbook");
    private static final Component CLEAR_VIRTUAL_ORDERBOOK_TOOLTIP = Component.translatable(PREFIX + "clear_virtual_orderbook.tooltip");
    // T-159: net player item flow row + apply button
    private static final Component NET_FLOW_TEXT = Component.translatable(PREFIX + "net_flow");
    private static final Component RESET_NET_FLOW_TEXT = Component.translatable(PREFIX + "reset_net_flow");
    private static final Component APPLY_TEXT = Component.translatable(PREFIX + "apply");

    private static final Component TRADING_HOURS_TEXT = Component.translatable(PREFIX + "trading_hours");
    private static final Component APPLY_ALL_HOURS_TEXT = Component.translatable(PREFIX + "apply_all_hours");
    private static final Component APPLY_ALL_HOURS_TOOLTIP = Component.translatable(PREFIX + "apply_all_hours.tooltip");
    private static final Component APPLY_ALL_HOURS_CONFIRM_TITLE = Component.translatable(PREFIX + "apply_all_hours_confirm.title");
    private static final String APPLY_ALL_HOURS_CONFIRM_MESSAGE_KEY = PREFIX + "apply_all_hours_confirm.message";
    private static final Component INVALID_TIME_TEXT = Component.translatable(PREFIX + "invalid_time");
    private static final String SERVER_TIME_KEY = PREFIX + "server_time";
    private static final Component CLOCK_SOURCE_INGAME = Component.translatable(PREFIX + "clock_source.ingame");
    private static final Component CLOCK_SOURCE_SERVER = Component.translatable(PREFIX + "clock_source.server");
    /** Apply-all feedback: "Trading hours %s-%s applied to %s/%s markets". */
    private static final String APPLY_ALL_HOURS_DONE_KEY = PREFIX + "apply_all_hours_done";

    /** Typing filter for the HH:MM boxes; full validation happens in {@link MarketSettings#parseMinute(String)}. */
    private static final String TIME_TYPING_REGEX = "^\\d{0,2}(:\\d{0,2})?$";
    private static final int ERROR_TEXT_COLOR = 0xFFFF5555;
    private static final int SUCCESS_TEXT_COLOR = 0xFF55FF55;

    private static final int elementHeight = 20;

    /** How often the Net Flow value is re-requested from the server, in milliseconds. */
    private static final long NET_FLOW_REFRESH_INTERVAL_MS = 1000;

    private final ClientMarket market;
    private final MarketSettings settings;
    private boolean loading = false;

    /**
     * Throttle for the live Net Flow refresh. Auto-restarting, ticked from {@link #render()},
     * which the GUI framework only calls while this element is actually visible
     * (see {@code GuiElement.renderInternal} / {@code isVisible}). That means the polling
     * stops by itself as soon as the panel is hidden, the tab is switched away or the screen
     * is closed — there is no background thread or scheduled task to tear down.
     */
    private final TimerMillis netFlowRefreshTimer = new TimerMillis(true);

    /** Guards against stacking requests if the server (or a master hop) is slow to answer. */
    private boolean netFlowRequestInFlight = false;

    /** Same guard as {@link #netFlowRequestInFlight}, for the server-time request. */
    private boolean serverTimeRequestInFlight = false;

    private final CheckBox maintenanceCheckBox;
    private final CheckBox virtualOrderbookEnabledCheckBox;
    private final Label defaultPriceLabel;
    private final TextBox defaultPriceTextBox;
    private final Label abundanceLabel;
    private final TextBox abundanceTextBox;
    private final Label netFlowLabel;
    private final Label netFlowValueLabel;
    private final Button resetNetFlowButton;
    private final Button resetVirtualOrderbookButton;
    private final Button clearVirtualOrderbookButton;
    private final Button applyButton;

    private final Label tradingHoursLabel;
    private final TextBox tradingOpenTextBox;
    private final Label tradingHoursDashLabel;
    private final TextBox tradingCloseTextBox;
    private final Button applyAllHoursButton;
    /** Shows {@code invalid_time} (red) while the window is invalid, otherwise apply-all feedback. */
    private final Label tradingHoursStatusLabel;
    private final Label serverTimeLabel;
    /** Default text color of the time boxes, restored once the input becomes valid again. */
    private final int defaultTimeTextColor;

    public MarketSettingsWidget(ClientMarket market)
    {
        this.market = market;
        this.settings = new MarketSettings();

        maintenanceCheckBox = new CheckBox(MAINTENANCE_TEXT.getString());
        maintenanceCheckBox.setChecked(settings.maintenance);
        maintenanceCheckBox.setHoverTooltipSupplier(MAINTENANCE_TOOLTIP::getString);
        maintenanceCheckBox.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);

        virtualOrderbookEnabledCheckBox = new CheckBox(VIRTUAL_ORDERBOOK_ENABLED_TEXT.getString(),
                this::onVirtualOrderbookEnabledChanged);
        // NOTE: initial setChecked is deferred to after clear/reset buttons are constructed,
        // because the listener dereferences them.
        virtualOrderbookEnabledCheckBox.setHoverTooltipSupplier(VIRTUAL_ORDERBOOK_ENABLED_TOOLTIP::getString);
        virtualOrderbookEnabledCheckBox.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);

        defaultPriceLabel = new Label(DEFAULT_PRICE_TEXT.getString());
        defaultPriceLabel.setAlignment(Label.Alignment.RIGHT);

        defaultPriceTextBox = new TextBox();
        defaultPriceTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false,
                (int)Math.log10((double)(Long.MAX_VALUE/getBankManager().getItemFractionScaleFactor())),
                (int)Math.log10(getBankManager().getItemFractionScaleFactor())));

        abundanceLabel = new Label(ABUNDANCE_TEXT.getString());
        abundanceLabel.setAlignment(Label.Alignment.RIGHT);

        abundanceTextBox = new TextBox();
        abundanceTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 10, 6));

        netFlowLabel = new Label(NET_FLOW_TEXT.getString());
        netFlowLabel.setAlignment(Label.Alignment.RIGHT);

        netFlowValueLabel = new Label("0");
        netFlowValueLabel.setAlignment(Label.Alignment.LEFT);

        resetNetFlowButton = new Button(RESET_NET_FLOW_TEXT.getString(), () -> {
            market.resetNetPlayerItemFlow().thenAccept(success -> {
                if (success) loadSettings();
            });
        });

        // Reset button for the virtual orderbook — fires an admin-gated request that
        // clears the bot-generated depth on the master. On success we reload settings
        // so any related fields the server may have adjusted are refreshed.
        resetVirtualOrderbookButton = new Button(RESET_VIRTUAL_ORDERBOOK_TEXT.getString(), () -> {
            market.resetVirtualOrderbook().thenAccept(success -> {
                if (success) loadSettings();
            });
        });
        resetVirtualOrderbookButton.setHoverTooltipSupplier(RESET_VIRTUAL_ORDERBOOK_TOOLTIP::getString);
        resetVirtualOrderbookButton.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);

        // Sticky-clear button — parallel to reset but goes through the distinct
        // ClearVirtualOrderbook FunctionType. Both live server-side in ServerMarket;
        // clear zeros + sets the sticky-clear flag; reset refills + drops it.
        clearVirtualOrderbookButton = new Button(CLEAR_VIRTUAL_ORDERBOOK_TEXT.getString(), () -> {
            market.clearVirtualOrderbook().thenAccept(success -> {
                if (success) loadSettings();
            });
        });
        clearVirtualOrderbookButton.setHoverTooltipSupplier(CLEAR_VIRTUAL_ORDERBOOK_TOOLTIP::getString);
        clearVirtualOrderbookButton.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);

        applyButton = new Button(APPLY_TEXT.getString(), this::saveSettings);

        // Trading hours row: [label] [open] - [close], followed by the apply-all button.
        tradingHoursLabel = new Label(TRADING_HOURS_TEXT.getString());
        tradingHoursLabel.setAlignment(Label.Alignment.RIGHT);
        tradingOpenTextBox = new TextBox();
        tradingOpenTextBox.setMatchRegex(TIME_TYPING_REGEX);
        tradingOpenTextBox.setOnTextChanged(t -> validateTradingHours());
        tradingHoursDashLabel = new Label("-");
        tradingHoursDashLabel.setAlignment(Label.Alignment.CENTER);
        tradingCloseTextBox = new TextBox();
        tradingCloseTextBox.setMatchRegex(TIME_TYPING_REGEX);
        tradingCloseTextBox.setOnTextChanged(t -> validateTradingHours());
        defaultTimeTextColor = tradingOpenTextBox.getTextColor();

        applyAllHoursButton = new Button(APPLY_ALL_HOURS_TEXT.getString(), this::onApplyAllHoursClicked);
        applyAllHoursButton.setHoverTooltipSupplier(APPLY_ALL_HOURS_TOOLTIP::getString);
        applyAllHoursButton.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);

        tradingHoursStatusLabel = new Label("");
        serverTimeLabel = new Label("");

        // Now that clear/reset buttons exist, safe to fire the listener via setChecked.
        virtualOrderbookEnabledCheckBox.setChecked(settings.virtualOrderbookEnabled);

        addChild(maintenanceCheckBox);
        addChild(virtualOrderbookEnabledCheckBox);
        addChild(defaultPriceLabel);
        addChild(defaultPriceTextBox);
        addChild(abundanceLabel);
        addChild(abundanceTextBox);
        addChild(netFlowLabel);
        addChild(netFlowValueLabel);
        addChild(resetNetFlowButton);
        addChild(resetVirtualOrderbookButton);
        addChild(clearVirtualOrderbookButton);
        addChild(applyButton);
        addChild(tradingHoursLabel);
        addChild(tradingOpenTextBox);
        addChild(tradingHoursDashLabel);
        addChild(tradingCloseTextBox);
        addChild(applyAllHoursButton);
        addChild(tradingHoursStatusLabel);
        addChild(serverTimeLabel);

        // The panel sits on the right edge of the screen, so every tooltip opens to the LEFT
        // of the cursor (tooltip's top-right corner at the mouse). ModUtilities does not
        // propagate these alignments from a parent, so set them on each direct child.
        for (var child : getChilds()) {
            child.setHoverTooltipMousePositionAlignment(Alignment.TOP_RIGHT);
            child.setTooltipPositionAlignment(Alignment.TOP_RIGHT);
        }

        // 8 base rows + trading hours, apply-all, status and server-time rows.
        setHeight(12 * elementHeight + 13 * padding);
        setTradingHoursText(settings.tradingOpenMinute, settings.tradingCloseMinute);
        loadSettings();
        netFlowRefreshTimer.start(NET_FLOW_REFRESH_INTERVAL_MS);
    }

    /**
     * Drives the live Net Flow refresh. {@code netPlayerItemFlow} is server-side state
     * (ServerMarket) that is shipped inside {@link MarketSettings}; there is no server push
     * for it, so the client has to re-request it. We reuse the existing
     * {@code ClientMarket.getSettings()} request rather than adding a dedicated one, and
     * throttle it to roughly once per second via {@link #netFlowRefreshTimer}.
     * <p>
     * Only {@code netFlowValueLabel} is updated — deliberately <b>not</b> {@link #setSettings}.
     * That method also calls {@code setText} on the price/abundance text boxes and
     * {@code setChecked} on the checkboxes, so running it on a timer would clobber whatever
     * the admin is currently typing or toggling before they press Apply.
     * <p>
     * The framework only calls {@code render()} while the element is visible, so this stops
     * polling automatically when the panel is hidden or the screen is closed.
     */
    @Override
    protected void render() {
        // The same once-per-second tick also refreshes the server-time row. Each request has
        // its own in-flight guard so a slow one never stacks and never blocks the other.
        if (!netFlowRefreshTimer.check())
            return;
        if (!netFlowRequestInFlight) {
            netFlowRequestInFlight = true;
            // whenComplete (not thenAccept) so an exceptionally completed future still clears
            // the in-flight flag — otherwise one failed request would stop the refresh forever.
            market.getSettings().whenComplete((result, error) -> updateNetFlowLabel(error == null ? result : null));
        }
        if (!serverTimeRequestInFlight) {
            serverTimeRequestInFlight = true;
            BACKEND_INSTANCES.NETWORKING.SERVER_TIME_REQUEST.sendRequestToServer(System.currentTimeMillis())
                    .whenComplete((result, error) -> updateServerTimeLabel(error == null ? result : null));
        }
    }

    /**
     * Writes the master's real and in-game clock plus the active clock source into the
     * server-time row.
     *
     * @param data the server-time response; may be {@code null} if the request failed
     */
    private void updateServerTimeLabel(ServerTimeRequest.OutputData data) {
        serverTimeRequestInFlight = false;
        if (data == null)
            return;
        String source = (data.useRealTime() ? CLOCK_SOURCE_SERVER : CLOCK_SOURCE_INGAME).getString();
        serverTimeLabel.setText(Component.translatable(SERVER_TIME_KEY,
                MarketSettings.formatMinute(data.realMinuteOfDay()),
                MarketSettings.formatMinute(data.gameMinuteOfDay()),
                source).getString());
    }

    /**
     * Writes the freshly fetched net player item flow into the value label and keeps the
     * locally cached {@link #settings} in sync, so a later Apply does not push a stale value
     * back to the server. Touches nothing else on the form — see {@link #render()}.
     *
     * @param marketSettings the settings snapshot returned by the server; may be {@code null}
     *                       if the request failed
     */
    private void updateNetFlowLabel(MarketSettings marketSettings) {
        netFlowRequestInFlight = false;
        if (marketSettings == null)
            return;
        settings.netPlayerItemFlow = marketSettings.netPlayerItemFlow;
        netFlowValueLabel.setText(String.valueOf(getBankManager().convertToRealAmount(settings.netPlayerItemFlow)));
    }

    @Override
    protected void layoutChanged() {
        int width = getWidth() - 2 * padding;
        int labelWidth = width / 3;
        int fieldWidth = width - labelWidth - spacing;

        maintenanceCheckBox.setBounds(padding, padding, width, elementHeight);

        virtualOrderbookEnabledCheckBox.setBounds(padding, maintenanceCheckBox.getBottom() + padding, width, elementHeight);

        defaultPriceLabel.setBounds(padding, virtualOrderbookEnabledCheckBox.getBottom() + padding, labelWidth, elementHeight);
        defaultPriceTextBox.setBounds(defaultPriceLabel.getRight() + spacing, defaultPriceLabel.getTop(), fieldWidth, elementHeight);

        abundanceLabel.setBounds(padding, defaultPriceLabel.getBottom() + padding, labelWidth, elementHeight);
        abundanceTextBox.setBounds(abundanceLabel.getRight() + spacing, abundanceLabel.getTop(), fieldWidth, elementHeight);

        // Net flow row: label | value label | reset button
        int resetButtonWidth = 40;
        int valueWidth = fieldWidth - resetButtonWidth - spacing;
        netFlowLabel.setBounds(padding, abundanceLabel.getBottom() + padding, labelWidth, elementHeight);
        netFlowValueLabel.setBounds(netFlowLabel.getRight() + spacing, netFlowLabel.getTop(), valueWidth, elementHeight);
        resetNetFlowButton.setBounds(netFlowValueLabel.getRight() + spacing, netFlowLabel.getTop(), resetButtonWidth, elementHeight);

        // Virtual-orderbook control group: the enable checkbox and the Clear/Reset
        // buttons sit together as a visually cohesive block so the "these buttons act
        // on the same subsystem the checkbox toggles" relationship reads at a glance.
        // Task 3 also gates Clear/Reset enabled state on the checkbox value.
        virtualOrderbookEnabledCheckBox.setBounds(padding, netFlowLabel.getBottom() + padding, width, elementHeight);

        // Clear and Reset side by side directly beneath the checkbox, split 50/50.
        int clearResetLeftW = (width - spacing) / 2;
        int clearResetRightW = width - clearResetLeftW - spacing;
        clearVirtualOrderbookButton.setBounds(padding, virtualOrderbookEnabledCheckBox.getBottom() + padding, clearResetLeftW, elementHeight);
        resetVirtualOrderbookButton.setBounds(clearVirtualOrderbookButton.getRight() + spacing, clearVirtualOrderbookButton.getTop(), clearResetRightW, elementHeight);

        // Trading hours row: label | open | "-" | close (the two boxes share the field width).
        int dashWidth = 8;
        int timeBoxWidth = (fieldWidth - dashWidth - 2 * spacing) / 2;
        tradingHoursLabel.setBounds(padding, resetVirtualOrderbookButton.getBottom() + padding, labelWidth, elementHeight);
        tradingOpenTextBox.setBounds(tradingHoursLabel.getRight() + spacing, tradingHoursLabel.getTop(), timeBoxWidth, elementHeight);
        tradingHoursDashLabel.setBounds(tradingOpenTextBox.getRight() + spacing, tradingHoursLabel.getTop(), dashWidth, elementHeight);
        tradingCloseTextBox.setBounds(tradingHoursDashLabel.getRight() + spacing, tradingHoursLabel.getTop(),
                fieldWidth - timeBoxWidth - dashWidth - 2 * spacing, elementHeight);
        applyAllHoursButton.setBounds(padding, tradingHoursLabel.getBottom() + padding, width, elementHeight);
        tradingHoursStatusLabel.setBounds(padding, applyAllHoursButton.getBottom() + padding, width, elementHeight);
        serverTimeLabel.setBounds(padding, tradingHoursStatusLabel.getBottom() + padding, width, elementHeight);

        applyButton.setBounds(padding, serverTimeLabel.getBottom() + padding, width, elementHeight);
    }

    /**
     * Reads and validates the trading window from the two boxes
     * (see {@link MarketSettings#parseMinute} / {@link MarketSettings#validateTradingWindow}).
     *
     * @return normalized {@code {open, close}} in minutes, or {@code null} if the input is invalid
     */
    private int[] readTradingHours() {
        return MarketSettings.validateTradingWindow(
                MarketSettings.parseMinute(tradingOpenTextBox.getText()),
                MarketSettings.parseMinute(tradingCloseTextBox.getText()));
    }

    /**
     * Re-validates the time boxes: colors them red, shows {@code invalid_time} and disables
     * Apply / Apply-to-all while invalid.
     *
     * @return true if the window is valid
     */
    private boolean validateTradingHours() {
        boolean valid = readTradingHours() != null;
        int color = valid ? defaultTimeTextColor : ERROR_TEXT_COLOR;
        tradingOpenTextBox.setTextColor(color);
        tradingCloseTextBox.setTextColor(color);
        applyButton.setEnabled(valid);
        applyAllHoursButton.setEnabled(valid);
        tradingHoursStatusLabel.setTextColor(valid ? SUCCESS_TEXT_COLOR : ERROR_TEXT_COLOR);
        tradingHoursStatusLabel.setText(valid ? "" : INVALID_TIME_TEXT.getString());
        return valid;
    }

    /** Fills both time boxes (programmatic, so validation is run explicitly). */
    private void setTradingHoursText(int openMinute, int closeMinute) {
        tradingOpenTextBox.setText(MarketSettings.formatMinute(openMinute));
        tradingCloseTextBox.setText(MarketSettings.formatMinute(closeMinute));
        validateTradingHours();
    }

    /**
     * Opens a vanilla confirmation screen; on confirm the current window is copied to every
     * available market. Either way the user returns to the hosting screen.
     */
    private void onApplyAllHoursClicked() {
        int[] hours = readTradingHours();
        if (hours == null)
            return;
        Minecraft mc = Minecraft.getInstance();
        Screen parent = mc.screen;
        String openText = MarketSettings.formatMinute(hours[0]);
        String closeText = MarketSettings.formatMinute(hours[1]);
        mc.setScreen(new ConfirmScreen(confirmed -> {
            mc.setScreen(parent);
            if (confirmed)
                applyTradingHoursToAllMarkets(hours[0], hours[1]);
        }, APPLY_ALL_HOURS_CONFIRM_TITLE,
                Component.translatable(APPLY_ALL_HOURS_CONFIRM_MESSAGE_KEY, openText, closeText)));
    }

    /**
     * Sends {@code setTradingHoursAsync} to every available market (only the window is copied)
     * and shows "Trading hours HH:MM-HH:MM (succeeded/total)" in the status row once all
     * requests have completed.
     */
    private void applyTradingHoursToAllMarkets(int openMinute, int closeMinute) {
        List<ItemID> markets = getAvailableMarkets();
        int total = markets.size();
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger succeeded = new AtomicInteger();
        Runnable onOneDone = () -> {
            if (completed.incrementAndGet() < total)
                return;
            Minecraft.getInstance().execute(() -> {
                // Keep the local copy in sync so a later Apply does not revert the window.
                settings.tradingOpenMinute = openMinute;
                settings.tradingCloseMinute = closeMinute;
                tradingHoursStatusLabel.setTextColor(succeeded.get() == total ? SUCCESS_TEXT_COLOR : ERROR_TEXT_COLOR);
                tradingHoursStatusLabel.setText(Component.translatable(APPLY_ALL_HOURS_DONE_KEY,
                        MarketSettings.formatMinute(openMinute), MarketSettings.formatMinute(closeMinute),
                        succeeded.get(), total).getString());
            });
        };
        for (ItemID marketID : markets) {
            ClientMarket m = getMarket(marketID);
            if (m == null) {
                onOneDone.run();
                continue;
            }
            m.setTradingHoursAsync(openMinute, closeMinute).whenComplete((success, error) -> {
                if (error == null && Boolean.TRUE.equals(success))
                    succeeded.incrementAndGet();
                onOneDone.run();
            });
        }
    }

    /**
     * Fires when the "Enable Virtual Orderbook" checkbox toggles. Grays out the Clear
     * and Reset buttons when unchecked — the server-side ops are no-ops for a disabled
     * VO (see ServerMarket.clearVirtualOrderbook/resetVirtualOrderbook), so preventing
     * the click is defense-in-depth.
     */
    private void onVirtualOrderbookEnabledChanged(Boolean enabled) {
        updateVirtualOrderbookButtonEnabled(Boolean.TRUE.equals(enabled));
    }

    private void updateVirtualOrderbookButtonEnabled(boolean enabled) {
        clearVirtualOrderbookButton.setEnabled(enabled);
        resetVirtualOrderbookButton.setEnabled(enabled);
    }

    public void loadSettings()
    {
        market.getSettings().thenAccept(this::setSettings);
    }

    public void saveSettings()
    {
        // Block saving while the trading window is invalid (Apply is also disabled then).
        if (!validateTradingHours())
            return;
        applyTradingHoursToSettings();
        settings.maintenance = maintenanceCheckBox.isChecked();
        settings.virtualOrderbookEnabled = virtualOrderbookEnabledCheckBox.isChecked();
        settings.defaultPrice = getBankManager().convertToRawAmount(defaultPriceTextBox.getDouble());
        float abundance = parseAbundance(abundanceTextBox.getText());
        if (abundance > 0)
            settings.naturalAbundance = abundance;
        market.setSettings(settings);
    }

    public void setSettings(MarketSettings marketSettings)
    {
        loading = true;
        settings.maintenance = marketSettings.maintenance;
        settings.defaultPrice = marketSettings.defaultPrice;
        settings.naturalAbundance = marketSettings.naturalAbundance;
        settings.netPlayerItemFlow = marketSettings.netPlayerItemFlow;
        settings.virtualOrderbookEnabled = marketSettings.virtualOrderbookEnabled;
        // Round-trip fields that this widget does not surface as controls but must
        // preserve when Apply writes settings back to the server. Otherwise the
        // client would blindly overwrite server-side state with widget defaults.
        settings.virtualOrderbookCleared = marketSettings.virtualOrderbookCleared;
        settings.ignorePluginAutosubscribe = marketSettings.ignorePluginAutosubscribe;
        settings.tradingOpenMinute = marketSettings.tradingOpenMinute;
        settings.tradingCloseMinute = marketSettings.tradingCloseMinute;
        maintenanceCheckBox.setChecked(settings.maintenance);
        virtualOrderbookEnabledCheckBox.setChecked(settings.virtualOrderbookEnabled);
        // Sync the Clear/Reset button enabled state with the freshly-loaded checkbox
        // value. Without this, the buttons would remain in their construction-time state
        // (enabled) until the user toggles the checkbox for the first time.
        updateVirtualOrderbookButtonEnabled(settings.virtualOrderbookEnabled);
        defaultPriceTextBox.setText(getBankManager().convertToRealAmount(settings.defaultPrice), getBankManager().getItemFractionDigitsCount());
        abundanceTextBox.setText(formatAbundance(settings.naturalAbundance));
        netFlowValueLabel.setText(String.valueOf(getBankManager().convertToRealAmount(settings.netPlayerItemFlow)));
        setTradingHoursText(settings.tradingOpenMinute, settings.tradingCloseMinute);
        loading = false;
    }

    public MarketSettings getMarketSettings() {
        applyTradingHoursToSettings();
        settings.maintenance = maintenanceCheckBox.isChecked();
        settings.virtualOrderbookEnabled = virtualOrderbookEnabledCheckBox.isChecked();
        settings.defaultPrice = getBankManager().convertToRawAmount(defaultPriceTextBox.getDouble());
        float abundance = parseAbundance(abundanceTextBox.getText());
        if (abundance > 0)
            settings.naturalAbundance = abundance;
        return settings;
    }

    /** Copies the window from the boxes into {@link #settings} if valid; otherwise keeps the old one. */
    private void applyTradingHoursToSettings() {
        int[] hours = readTradingHours();
        if (hours == null)
            return;
        settings.tradingOpenMinute = hours[0];
        settings.tradingCloseMinute = hours[1];
    }

    private static String formatAbundance(float value) {
        String s = String.format(Locale.ROOT, "%.4f", value);
        s = s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
        return s;
    }

    private static float parseAbundance(String text) {
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException e) {
            return -1f;
        }
    }
}

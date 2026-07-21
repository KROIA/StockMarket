package net.kroia.stockmarket.screen.uiElements;

import net.kroia.banksystem.banking.clientdata.BankData;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ClientPlayerUtilities;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.Frame;
import net.kroia.modutilities.gui.elements.ItemView;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.TextBox;
import net.kroia.modutilities.gui.elements.VerticalListView;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.modutilities.gui.layout.LayoutGrid;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Widget for selecting a trading pair in Item-to-Item mode.
 * <p>
 * Layout:
 * <pre>
 * ┌─────────────────────────────────┐
 * │  I WANT:         I HAVE:        │
 * │  [Gold Ingot]    [Iron Ingot]   │
 * │  Rate: 10.0 iron/gold     [Swap]│
 * │  Balance: 128 iron | 3 gold     │
 * └─────────────────────────────────┘
 * </pre>
 * <p>
 * Clicking either item icon opens a dropdown grid of available markets.
 * The swap button [Swap] exchanges the "have" and "want" selections.
 * The cross-rate is calculated client-side: {@code rate = wantPrice / havePrice}.
 */
public class PairSelectorWidget extends StockMarketGuiElement {

    /** Callback fired when the pair changes (either item changes or swap). */
    private final Consumer<PairSelection> onPairSelected;

    // "I HAVE" side
    private final Label haveLabel;
    private final ItemView haveItemView;
    private final Label haveNameLabel;

    // "I WANT" side
    private final Label wantLabel;
    private final ItemView wantItemView;
    private final Label wantNameLabel;

    // Rate and swap
    private final Label rateLabel;
    private final Button swapButton;

    // Balance labels
    private final Label haveBalanceLabel;
    private final Label wantBalanceLabel;

    // Dropdown for item selection (shown one at a time)
    private final Frame dropdownFrame;
    private final Label dropdownSearchLabel;
    private final TextBox dropdownSearchField;
    private final VerticalListView dropdownList;
    private final LayoutGrid dropdownGridLayout;

    /** All dropdown buttons in display order, kept for search filtering. */
    private final List<MarketFavoriteButton> allDropdownButtons = new ArrayList<>();

    /** Which side the dropdown is currently selecting for, or null if closed. */
    @Nullable
    private Side dropdownTarget = null;

    /** Currently selected "have" market item. */
    @Nullable
    private ItemID haveMarketID = null;

    /** Currently selected "want" market item. */
    @Nullable
    private ItemID wantMarketID = null;

    /**
     * T-131 (balance-display fix): the bank account whose balances are shown next to
     * the "have"/"want" items. Mirrors {@code TradeScreen.selectedBankAccountNr} so
     * the "Bal:" labels reflect the SELECTED account (not always the personal one).
     * {@code -1} falls back to the player's personal account.
     */
    private int selectedBankAccountNr = -1;

    /** Cached trading currency ID for display. */
    @Nullable
    private ItemID tradingCurrencyID = null;

    // Timer for periodic balance refresh
    private long lastBalanceRefreshMs = 0;
    private static final long BALANCE_REFRESH_INTERVAL_MS = 2000;

    // Timer for periodic rate refresh
    private long lastRateRefreshMs = 0;
    private static final long RATE_REFRESH_INTERVAL_MS = 500;

    /** Enum to track which side the dropdown is selecting for. */
    private enum Side { HAVE, WANT }

    /**
     * Data class representing the current pair selection.
     */
    public static class PairSelection {
        public final @Nullable ItemID haveMarketID;
        public final @Nullable ItemID wantMarketID;

        public PairSelection(@Nullable ItemID haveMarketID, @Nullable ItemID wantMarketID) {
            this.haveMarketID = haveMarketID;
            this.wantMarketID = wantMarketID;
        }
    }

    /**
     * @param onPairSelected callback fired when the pair changes
     */
    public PairSelectorWidget(Consumer<PairSelection> onPairSelected) {
        super();
        this.onPairSelected = onPairSelected;
        setEnableBackground(true);

        // "I HAVE" side
        haveLabel = new Label(Component.translatable("gui.stockmarket.pair_selector.i_have").getString());
        haveLabel.setTextFontScale(0.7f);
        haveLabel.setAlignment(Label.Alignment.CENTER);
        addChild(haveLabel);

        haveItemView = new ItemView();
        haveItemView.setEnabled(false);
        addChild(haveItemView);

        haveNameLabel = new Label(Component.translatable("gui.stockmarket.pair_selector.select").getString());
        haveNameLabel.setTextFontScale(0.6f);
        haveNameLabel.setAlignment(Label.Alignment.CENTER);
        addChild(haveNameLabel);

        // "I WANT" side
        wantLabel = new Label(Component.translatable("gui.stockmarket.pair_selector.i_want").getString());
        wantLabel.setTextFontScale(0.7f);
        wantLabel.setAlignment(Label.Alignment.CENTER);
        addChild(wantLabel);

        wantItemView = new ItemView();
        wantItemView.setEnabled(false);
        addChild(wantItemView);

        wantNameLabel = new Label(Component.translatable("gui.stockmarket.pair_selector.select").getString());
        wantNameLabel.setTextFontScale(0.6f);
        wantNameLabel.setAlignment(Label.Alignment.CENTER);
        addChild(wantNameLabel);

        // Rate label
        rateLabel = new Label(Component.translatable("gui.stockmarket.pair_selector.rate_unknown").getString());
        rateLabel.setTextFontScale(0.7f);
        rateLabel.setAlignment(Label.Alignment.CENTER);
        addChild(rateLabel);

        // Swap button
        swapButton = new Button(Component.translatable("gui.stockmarket.pair_selector.swap").getString(), this::onSwap);
        swapButton.setTextFontScale(0.8f);
        addChild(swapButton);

        // Balance labels
        haveBalanceLabel = new Label(Component.translatable("gui.stockmarket.pair_selector.balance_unknown").getString());
        haveBalanceLabel.setTextFontScale(0.6f);
        haveBalanceLabel.setAlignment(Label.Alignment.CENTER);
        addChild(haveBalanceLabel);

        wantBalanceLabel = new Label(Component.translatable("gui.stockmarket.pair_selector.balance_unknown").getString());
        wantBalanceLabel.setTextFontScale(0.6f);
        wantBalanceLabel.setAlignment(Label.Alignment.CENTER);
        addChild(wantBalanceLabel);

        // Dropdown overlay (hidden by default)
        dropdownFrame = new Frame();
        dropdownFrame.setEnableBackground(true);
        dropdownFrame.setBackgroundColor(0xFF2a2a2a);  // dark opaque background for clear overlay
        dropdownFrame.setEnabled(false);

        dropdownSearchLabel = new Label("Search");
        dropdownSearchLabel.setTextFontScale(0.7f);
        dropdownSearchLabel.setAlignment(Label.Alignment.RIGHT);
        dropdownFrame.addChild(dropdownSearchLabel);

        dropdownSearchField = new TextBox();
        dropdownSearchField.setTextFontScale(0.7f);
        dropdownSearchField.setMaxChars(40);
        dropdownSearchField.setOnTextChanged(text -> applyDropdownFilter());
        dropdownFrame.addChild(dropdownSearchField);

        dropdownList = new VerticalListView();
        dropdownGridLayout = new LayoutGrid(0, 0, false, false, 0, 0, Alignment.TOP);
        dropdownList.setLayout(dropdownGridLayout);
        dropdownFrame.addChild(dropdownList);
        addChild(dropdownFrame);

        // Fetch trading currency ID for display
        getMarketManager().getTradingCurrencyIDAsync().thenAccept(currencyID -> {
            tradingCurrencyID = currencyID;
        });
    }

    // ── Public API ──

    /**
     * Returns the currently selected "have" market ID, or null if none selected.
     */
    public @Nullable ItemID getHaveMarketID() {
        return haveMarketID;
    }

    /**
     * Returns the currently selected "want" market ID, or null if none selected.
     */
    public @Nullable ItemID getWantMarketID() {
        return wantMarketID;
    }

    /**
     * Sets the "have" market selection programmatically.
     * Does not fire the onPairSelected callback.
     */
    public void setHaveMarketID(@Nullable ItemID marketID) {
        haveMarketID = marketID;
        updateHaveDisplay();
        updateRate();
    }

    /**
     * Sets the "want" market selection programmatically.
     * Does not fire the onPairSelected callback.
     */
    public void setWantMarketID(@Nullable ItemID marketID) {
        wantMarketID = marketID;
        updateWantDisplay();
        updateRate();
    }

    /**
     * Sets the bank account whose balances are shown next to the "have"/"want" items
     * (T-131 balance-display fix) and refreshes the "Bal:" labels immediately so they
     * follow the account selected in the trade screen. Passing {@code -1} restores the
     * personal-account fallback.
     * @param accountNr the selected account number, or {@code -1} for personal
     */
    public void setSelectedBankAccountNr(int accountNr) {
        this.selectedBankAccountNr = accountNr;
        // Reflect the new account immediately instead of waiting for the next refresh.
        refreshBalances();
    }

    // ── Click handling ──

    /**
     * Intercepts clicks on the "have" and "want" item areas to open the dropdown.
     * Clicks elsewhere close the dropdown if open.
     */
    @Override
    protected boolean mouseClickedOverElement(int button) {
        if (button != 0) return false;

        int mx = getMouseX();
        int my = getMouseY();

        // When dropdown is open, only allow clicks inside the dropdown or close it
        if (dropdownTarget != null && dropdownFrame.isEnabled()) {
            if (isInBounds(mx, my, dropdownFrame)) {
                return false; // Let dropdown children handle the click
            }
            closeDropdown();
            return true; // Consume the click (don't let it reach swap button etc.)
        }

        // Check if click is in the "have" area (icon or name label)
        if (isInBounds(mx, my, haveItemView) || isInBounds(mx, my, haveNameLabel) || isInBounds(mx, my, haveLabel)) {
            openDropdown(Side.HAVE);
            return true;
        }

        // Check if click is in the "want" area (icon or name label)
        if (isInBounds(mx, my, wantItemView) || isInBounds(mx, my, wantNameLabel) || isInBounds(mx, my, wantLabel)) {
            openDropdown(Side.WANT);
            return true;
        }

        return false;
    }

    /**
     * Checks whether the given mouse coordinates (relative to this element) fall
     * within the bounds of a child element.
     */
    private boolean isInBounds(int mx, int my, GuiElement child) {
        return mx >= child.getLeft() && mx < child.getRight()
                && my >= child.getTop() && my < child.getBottom();
    }

    // ── Dropdown ──

    /**
     * Opens the dropdown grid for the specified side, populated with all available
     * markets except the one already selected on the opposite side.
     */
    private void openDropdown(Side side) {
        dropdownTarget = side;
        dropdownList.removeChilds();
        allDropdownButtons.clear();
        dropdownSearchField.setText("");

        List<ItemID> markets = new ArrayList<>(getAvailableMarkets());
        markets.sort(StockMarketGuiElement.MARKET_TYPE_COMPARATOR);
        // Exclude the item already selected on the opposite side
        ItemID excludeID = (side == Side.HAVE) ? wantMarketID : haveMarketID;

        for (ItemID marketID : markets) {
            if (marketID.equals(excludeID)) continue;

            MarketFavoriteButton btn = new MarketFavoriteButton(
                    marketID.getStack(),
                    marketID,
                    id -> onDropdownItemSelected(id),
                    () -> {} // No favorite toggle in pair selector
            );
            // Highlight the currently selected item
            ItemID currentSelection = (side == Side.HAVE) ? haveMarketID : wantMarketID;
            btn.setSelected(marketID.equals(currentSelection));
            btn.setFavorite(false);
            allDropdownButtons.add(btn);
        }
        applyDropdownFilter();

        // Hide elements that the dropdown covers
        haveItemView.setEnabled(false);
        wantItemView.setEnabled(false);
        haveNameLabel.setEnabled(false);
        wantNameLabel.setEnabled(false);
        haveBalanceLabel.setEnabled(false);
        wantBalanceLabel.setEnabled(false);
        rateLabel.setEnabled(false);
        swapButton.setEnabled(false);

        dropdownFrame.setEnabled(true);
        // Pre-compute grid columns from known dropdown width (don't wait for container layout)
        int dropdownWidth = getWidth() - 2 * 2; // p = 2 on each side
        int cols = Math.max(1, dropdownWidth / ItemView.DEFAULT_WIDTH);
        dropdownGridLayout.columns = cols;

        layoutChanged();
    }

    /**
     * Closes the dropdown and clears the selection target.
     */
    private void closeDropdown() {
        dropdownTarget = null;
        dropdownFrame.setEnabled(false);
        allDropdownButtons.clear();

        // Restore hidden elements
        if (haveMarketID != null) haveItemView.setEnabled(true);
        if (wantMarketID != null) wantItemView.setEnabled(true);
        haveNameLabel.setEnabled(true);
        wantNameLabel.setEnabled(true);
        haveBalanceLabel.setEnabled(true);
        wantBalanceLabel.setEnabled(true);
        rateLabel.setEnabled(true);
        swapButton.setEnabled(true);
    }

    /**
     * Filters the dropdown list based on the current search field text.
     * Matches against the item display name first, then falls back to the full
     * tooltip text (includes enchantment names, potion effects, etc.).
     */
    private void applyDropdownFilter() {
        dropdownList.removeChilds();
        String filter = dropdownSearchField.getText().toLowerCase().trim();
        for (MarketFavoriteButton btn : allDropdownButtons) {
            if (filter.isEmpty()
                    || btn.getItemStack().getHoverName().getString().toLowerCase().contains(filter)
                    || ClientPlayerUtilities.getItemDisplayText(btn.getItemStack()).toLowerCase().contains(filter)) {
                dropdownList.addChild(btn);
            }
        }
    }

    /**
     * Called when an item is selected from the dropdown grid.
     */
    private void onDropdownItemSelected(ItemID selectedID) {
        if (dropdownTarget == Side.HAVE) {
            haveMarketID = selectedID;
            updateHaveDisplay();
        } else if (dropdownTarget == Side.WANT) {
            wantMarketID = selectedID;
            updateWantDisplay();
        }
        closeDropdown();
        updateRate();
        refreshBalances();
        firePairSelected();
    }

    // ── Swap ──

    /**
     * Swaps the "have" and "want" selections and fires the callback.
     */
    private void onSwap() {
        ItemID temp = haveMarketID;
        haveMarketID = wantMarketID;
        wantMarketID = temp;
        updateHaveDisplay();
        updateWantDisplay();
        updateRate();
        refreshBalances();
        firePairSelected();
    }

    // ── Display updates ──

    /**
     * Updates the "have" side display (icon and name label).
     */
    private void updateHaveDisplay() {
        if (haveMarketID != null) {
            haveItemView.setItemStack(haveMarketID.getStack());
            haveItemView.setEnabled(true);
            haveNameLabel.setText(haveMarketID.getStack().getHoverName().getString());
        } else {
            haveItemView.setEnabled(false);
            haveNameLabel.setText(Component.translatable("gui.stockmarket.pair_selector.select").getString());
        }
    }

    /**
     * Updates the "want" side display (icon and name label).
     */
    private void updateWantDisplay() {
        if (wantMarketID != null) {
            wantItemView.setItemStack(wantMarketID.getStack());
            wantItemView.setEnabled(true);
            wantNameLabel.setText(wantMarketID.getStack().getHoverName().getString());
        } else {
            wantItemView.setEnabled(false);
            wantNameLabel.setText(Component.translatable("gui.stockmarket.pair_selector.select").getString());
        }
    }

    /**
     * Recalculates and updates the cross-rate label from the two market prices.
     * Rate = wantPrice / havePrice (how many "have" items per 1 "want" item).
     */
    private void updateRate() {
        if (haveMarketID == null || wantMarketID == null) {
            rateLabel.setText(Component.translatable("gui.stockmarket.pair_selector.rate_unknown").getString());
            return;
        }

        ClientMarket haveMarket = getMarket(haveMarketID);
        ClientMarket wantMarket = getMarket(wantMarketID);
        if (haveMarket == null || wantMarket == null) {
            rateLabel.setText(Component.translatable("gui.stockmarket.pair_selector.rate_unknown").getString());
            return;
        }

        double havePrice = haveMarket.getCurrentMarketRealPrice();
        double wantPrice = wantMarket.getCurrentMarketRealPrice();

        if (havePrice <= 0) {
            rateLabel.setText(Component.translatable("gui.stockmarket.pair_selector.rate_unknown").getString());
            return;
        }

        double rate = wantPrice / havePrice;
        String haveName = haveMarketID.getStack().getHoverName().getString();
        String wantName = wantMarketID.getStack().getHoverName().getString();
        rateLabel.setText(String.format(Locale.ROOT, "%.2f %s / %s", rate, haveName, wantName));
    }

    /**
     * Fetches bank balances for both the "have" and "want" items and updates the labels.
     */
    private void refreshBalances() {
        UUID playerUUID = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.getUUID() : null;
        if (playerUUID == null) return;

        // T-131 balance-display fix: show the SELECTED account's balances; only fall
        // back to the personal account while none has been resolved (nr == -1).
        java.util.concurrent.CompletableFuture<net.kroia.banksystem.banking.clientdata.BankAccountData> accountFuture =
                (selectedBankAccountNr == -1)
                        ? getBankManager().getPersonalBankAccountDataAsync(playerUUID)
                        : getBankManager().getBankAccountDataAsync(selectedBankAccountNr);
        accountFuture.thenAccept(bankAccountData -> {
            if (bankAccountData == null) return;

            // "Have" item balance
            if (haveMarketID != null) {
                BankData haveBalance = bankAccountData.bankData.get(haveMarketID);
                haveBalanceLabel.setText("Bal: " + (haveBalance != null ? haveBalance.getFormattedBalance() : "0"));
            } else {
                haveBalanceLabel.setText(Component.translatable("gui.stockmarket.pair_selector.balance_unknown").getString());
            }

            // "Want" item balance
            if (wantMarketID != null) {
                BankData wantBalance = bankAccountData.bankData.get(wantMarketID);
                wantBalanceLabel.setText("Bal: " + (wantBalance != null ? wantBalance.getFormattedBalance() : "0"));
            } else {
                wantBalanceLabel.setText(Component.translatable("gui.stockmarket.pair_selector.balance_unknown").getString());
            }
        });
    }

    /**
     * Fires the pair selected callback with the current selections.
     */
    private void firePairSelected() {
        onPairSelected.accept(new PairSelection(haveMarketID, wantMarketID));
    }

    // ── Render ──

    @Override
    protected void render() {
        long now = System.currentTimeMillis();

        // Periodic rate refresh (~500ms)
        if (now - lastRateRefreshMs > RATE_REFRESH_INTERVAL_MS) {
            lastRateRefreshMs = now;
            updateRate();
        }

        // Periodic balance refresh (~2s)
        if (now - lastBalanceRefreshMs > BALANCE_REFRESH_INTERVAL_MS) {
            lastBalanceRefreshMs = now;
            refreshBalances();
        }
    }

    // ── Layout ──

    /**
     * Layout:
     * <pre>
     * Row 0: [I WANT:    ] [I HAVE:    ]
     * Row 1: [  ItemIcon  ] [  ItemIcon  ]
     * Row 2: [  ItemName  ] [  ItemName  ]
     * Row 3: [  Bal: xxx  ] [  Bal: xxx  ]
     * Row 4: [ Rate: x.xx have/want ] [Swap]
     * (Dropdown overlays the bottom half when open)
     * </pre>
     */
    @Override
    protected void layoutChanged() {
        int p = 2;
        int s = 2;
        int w = getWidth();
        int h = getHeight();
        int halfW = (w - p * 2 - s) / 2;
        int iconSize = 24;
        int labelHeight = 12;
        int buttonHeight = 16;
        int swapButtonWidth = 36;

        int y = p;
        int leftX = p;
        int rightX = p + halfW + s;

        // Row 0: labels — WANT (traded item) on the left, HAVE on the right
        wantLabel.setBounds(leftX, y, halfW, labelHeight);
        haveLabel.setBounds(rightX, y, halfW, labelHeight);
        y += labelHeight + s;

        // Row 1: item icons (centered in each half)
        int iconLeftOffset = (halfW - iconSize) / 2;
        wantItemView.setBounds(leftX + iconLeftOffset, y, iconSize, iconSize);
        haveItemView.setBounds(rightX + iconLeftOffset, y, iconSize, iconSize);
        y += iconSize + s;

        // Row 2: item names
        wantNameLabel.setBounds(leftX, y, halfW, labelHeight);
        haveNameLabel.setBounds(rightX, y, halfW, labelHeight);
        y += labelHeight + s;

        // Row 3: balances
        wantBalanceLabel.setBounds(leftX, y, halfW, labelHeight);
        haveBalanceLabel.setBounds(rightX, y, halfW, labelHeight);
        y += labelHeight + s;

        // Row 4: rate label and swap button
        int rateWidth = w - p * 2 - swapButtonWidth - s;
        rateLabel.setBounds(leftX, y, rateWidth, buttonHeight);
        swapButton.setBounds(leftX + rateWidth + s, y, swapButtonWidth, buttonHeight);

        // Dropdown frame spans full width, below the header labels
        if (dropdownTarget != null && dropdownFrame.isEnabled()) {
            int dropdownY = haveLabel.getBottom() + s;
            int dropdownH = h - dropdownY - p;

            dropdownFrame.setBounds(leftX, dropdownY, w - 2 * p, Math.max(0, dropdownH));

            // Search label + field at the top of the dropdown, list below it
            int searchH = 14;
            int innerP = 2;
            int dw = dropdownFrame.getWidth() - 2 * innerP;
            int searchLabelW = dw / 4;
            dropdownSearchLabel.setBounds(innerP, innerP, searchLabelW, searchH);
            dropdownSearchField.setBounds(innerP + searchLabelW + innerP, innerP, dw - searchLabelW - innerP, searchH);
            int listY = dropdownSearchField.getBottom() + innerP;
            dropdownList.setBounds(0, listY, dropdownFrame.getWidth(), Math.max(0, dropdownFrame.getHeight() - listY));
        }
    }
}

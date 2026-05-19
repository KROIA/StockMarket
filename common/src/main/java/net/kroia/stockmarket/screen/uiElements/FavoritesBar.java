package net.kroia.stockmarket.screen.uiElements;

import net.kroia.banksystem.banking.clientdata.BankAccountData;
import net.kroia.banksystem.banking.clientdata.BankData;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.elements.Frame;
import net.kroia.modutilities.gui.elements.ItemView;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.TextBox;
import net.kroia.modutilities.gui.elements.VerticalListView;
import net.kroia.modutilities.gui.layout.LayoutGrid;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Scrollable grid panel displaying ALL available markets.
 * <p>
 * Each market is shown as a {@link MarketFavoriteButton} with a star overlay
 * to toggle favorite status. Favorites are pinned to the top of the grid in
 * their stable add-order from player preferences; non-favorites follow sorted
 * alphabetically by display name.
 * <p>
 * An {@link ItemView} at the top shows the currently selected market icon.
 */
public class FavoritesBar extends StockMarketGuiElement {

    private final Consumer<ItemID> onMarketSelected;

    private final ItemView selectedMarketView;
    private final Label priceLabel;
    private final Label searchLabel;
    private final TextBox searchField;
    private final VerticalListView marketGrid;
    private final LayoutGrid gridLayout;

    /** All market buttons in display order, kept for search filtering. */
    private final List<MarketFavoriteButton> allMarketButtons = new ArrayList<>();

    // Bank balance display section
    private final Frame balanceFrame;
    private final Label balanceTitleLabel;
    private final ItemView currencyIcon;
    private final Label currencyBalanceLabel;
    private final ItemView marketItemIcon;
    private final Label marketItemBalanceLabel;
    @Nullable
    private ItemID tradingCurrencyID;

    // Timer for periodic balance refresh
    private long lastBalanceRefreshMs = 0;
    private static final long BALANCE_REFRESH_INTERVAL_MS = 2000;

    /** The market currently displayed in the trade screen. */
    @Nullable
    private ItemID currentMarketID;

    // Dirty-flag for deferred rebuild (avoids ConcurrentModificationException
    // when rebuild is triggered from inside a click handler while the GUI
    // framework is still iterating the children list).
    private boolean needsRebuild = false;
    private List<ItemID> pendingAllMarkets;
    private List<ItemID> pendingFavoriteIDs;
    @Nullable
    private ItemID pendingSelectedID;

    /**
     * @param onMarketSelected callback fired when a market button is clicked
     */
    public FavoritesBar(Consumer<ItemID> onMarketSelected) {
        super();
        this.onMarketSelected = onMarketSelected;
        setEnableBackground(true);

        // Icon showing the currently selected market item
        selectedMarketView = new ItemView();
        selectedMarketView.setEnabled(false);
        addChild(selectedMarketView);

        // Live price label next to the selected market icon
        priceLabel = new Label("--");
        priceLabel.setTextFontScale(0.7f);
        priceLabel.setEnabled(false);
        addChild(priceLabel);

        // Search label and field for filtering the market grid
        searchLabel = new Label("Search");
        searchLabel.setTextFontScale(0.7f);
        searchLabel.setAlignment(Label.Alignment.RIGHT);
        addChild(searchLabel);

        searchField = new TextBox();
        searchField.setTextFontScale(0.7f);
        searchField.setMaxChars(40);
        searchField.setOnTextChanged(text -> applySearchFilter());
        addChild(searchField);

        // Scrollable grid for all market buttons
        marketGrid = new VerticalListView();
        gridLayout = new LayoutGrid(0, 0, false, false, 0, 1, Alignment.TOP);
        marketGrid.setLayout(gridLayout);
        addChild(marketGrid);

        // Bank balance frame below the grid
        balanceFrame = new Frame();
        balanceFrame.setEnableBackground(true);
        addChild(balanceFrame);

        balanceTitleLabel = new Label("Bank Balance");
        balanceTitleLabel.setAlignment(Label.Alignment.CENTER);
        balanceTitleLabel.setTextFontScale(0.7f);
        balanceFrame.addChild(balanceTitleLabel);

        currencyIcon = new ItemView();
        currencyIcon.setEnabled(false);
        balanceFrame.addChild(currencyIcon);
        currencyBalanceLabel = new Label("--");
        currencyBalanceLabel.setTextFontScale(0.7f);
        balanceFrame.addChild(currencyBalanceLabel);

        marketItemIcon = new ItemView();
        marketItemIcon.setEnabled(false);
        balanceFrame.addChild(marketItemIcon);
        marketItemBalanceLabel = new Label("--");
        marketItemBalanceLabel.setTextFontScale(0.7f);
        balanceFrame.addChild(marketItemBalanceLabel);
    }

    /**
     * Clears and rebuilds the grid from the given market lists.
     * Call this when markets change or when the selected market switches.
     *
     * @param allMarkets       all available market IDs
     * @param favoriteIDs      ordered list of favorite market IDs (in the order they were added)
     * @param selectedMarketID the currently active market, or null if none
     */
    public void rebuild(List<ItemID> allMarkets, List<ItemID> favoriteIDs,
                        @Nullable ItemID selectedMarketID) {
        this.currentMarketID = selectedMarketID;
        marketGrid.removeChilds();
        allMarketButtons.clear();

        // Update selected market icon
        if (selectedMarketID != null) {
            selectedMarketView.setItemStack(selectedMarketID.getStack());
            selectedMarketView.setEnabled(true);
            priceLabel.setEnabled(true);
            marketItemIcon.setItemStack(selectedMarketID.getStack());
            marketItemIcon.setEnabled(true);
        } else {
            selectedMarketView.setEnabled(false);
            priceLabel.setEnabled(false);
            marketItemIcon.setEnabled(false);
        }

        // Build sorted list: favorites first (stable order), then non-favorites alphabetically
        List<ItemID> sorted = new ArrayList<>();
        for (ItemID fav : favoriteIDs) {
            if (allMarkets.contains(fav)) {
                sorted.add(fav);
            }
        }
        List<ItemID> nonFavorites = new ArrayList<>();
        for (ItemID market : allMarkets) {
            if (!favoriteIDs.contains(market)) {
                nonFavorites.add(market);
            }
        }
        nonFavorites.sort(StockMarketGuiElement.MARKET_TYPE_COMPARATOR);
        sorted.addAll(nonFavorites);

        // Create buttons (stored for search filtering)
        for (ItemID marketID : sorted) {
            boolean isFav = favoriteIDs.contains(marketID);
            boolean isSel = marketID.equals(selectedMarketID);
            MarketFavoriteButton btn = new MarketFavoriteButton(
                    marketID.getStack(),
                    marketID,
                    id -> onMarketSelected.accept(id),
                    () -> onFavoriteToggled(marketID)
            );
            btn.setFavorite(isFav);
            btn.setSelected(isSel);
            allMarketButtons.add(btn);
        }

        // Apply current search filter to populate the grid
        applySearchFilter();

        // Fetch updated bank balances
        refreshBalances();

        // Trigger layout recalculation
        layoutChanged();
    }

    /**
     * Schedules a rebuild for the next render frame.
     * Use this instead of rebuild() when called from inside a click handler.
     */
    public void scheduleRebuild(List<ItemID> allMarkets, List<ItemID> favoriteIDs,
                                @Nullable ItemID selectedMarketID) {
        needsRebuild = true;
        pendingAllMarkets = allMarkets;
        pendingFavoriteIDs = favoriteIDs;
        pendingSelectedID = selectedMarketID;
    }

    /**
     * Called when the star on a market button is toggled.
     * Adds or removes the market from favorites and triggers a rebuild.
     */
    private void onFavoriteToggled(ItemID marketID) {
        var prefs = getPlayerPreferences();
        if (prefs.isFavorite(marketID)) {
            prefs.removeFavorite(marketID);
        } else {
            prefs.addFavorite(marketID);
        }
        updatePlayerPreferences(prefs);
        scheduleRebuild(getAvailableMarkets(), prefs.getFavoriteMarketIDs(), currentMarketID);
    }

    /**
     * Filters the market grid based on the current search field text.
     * Only buttons whose item display name contains the search string are shown.
     */
    private void applySearchFilter() {
        marketGrid.removeChilds();
        String filter = searchField.getText().toLowerCase().trim();
        for (MarketFavoriteButton btn : allMarketButtons) {
            if (filter.isEmpty() || btn.getItemStack().getHoverName().getString().toLowerCase().contains(filter)) {
                marketGrid.addChild(btn);
            }
        }
    }

    /**
     * Fetches current bank balances for the trading currency and the selected market item.
     * Called after market switch and periodically from render().
     */
    public void refreshBalances() {
        if (currentMarketID == null) return;
        UUID playerUUID = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.getUUID() : null;
        if (playerUUID == null) return;

        // Fetch currency ID if not yet known
        if (tradingCurrencyID == null) {
            getMarketManager().getTradingCurrencyIDAsync().thenAccept(currencyID -> {
                tradingCurrencyID = currencyID;
                if (currencyID != null) {
                    currencyIcon.setItemStack(currencyID.getStack());
                    currencyIcon.setEnabled(true);
                }
                fetchBalances(playerUUID);
            });
        } else {
            fetchBalances(playerUUID);
        }
    }

    /**
     * Fetches bank account data and updates the currency and market item balance labels.
     */
    private void fetchBalances(UUID playerUUID) {
        getBankManager().getPersonalBankAccountDataAsync(playerUUID).thenAccept(bankAccountData -> {
            if (bankAccountData == null) return;

            // Currency balance
            if (tradingCurrencyID != null) {
                BankData currencyBalance = bankAccountData.bankData.get(tradingCurrencyID);
                currencyBalanceLabel.setText(currencyBalance != null ? currencyBalance.getFormattedBalance() : "0");
            }

            // Market item balance
            if (currentMarketID != null) {
                BankData itemBalance = bankAccountData.bankData.get(currentMarketID);
                marketItemBalanceLabel.setText(itemBalance != null ? itemBalance.getFormattedBalance() : "0");
            }
        });
    }

    @Override
    protected void render() {
        if (needsRebuild) {
            needsRebuild = false;
            rebuild(pendingAllMarkets, pendingFavoriteIDs, pendingSelectedID);
        }

        // Update live price label
        if (currentMarketID != null) {
            ClientMarket market = getMarket(currentMarketID);
            if (market != null) {
                priceLabel.setText(formatPriceWithSeparators(market.getCurrentMarketRealPrice()));
            }
        }

        // Periodic balance refresh
        long now = System.currentTimeMillis();
        if (now - lastBalanceRefreshMs > BALANCE_REFRESH_INTERVAL_MS) {
            lastBalanceRefreshMs = now;
            refreshBalances();
        }
    }

    private static String formatPriceWithSeparators(double price) {
        String str = String.format("%.2f", price);
        int dot = str.indexOf('.');
        String intPart = str.substring(0, dot);
        String decPart = str.substring(dot);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = intPart.length() - 1; i >= 0; i--) {
            if (count > 0 && count % 3 == 0) sb.insert(0, '\'');
            sb.insert(0, intPart.charAt(i));
            count++;
        }
        return sb.toString() + decPart;
    }

    /**
     * Layout:
     * [Selected Market Icon 24x24]
     * [--- Market Grid (scrollable, fills remaining) ---]
     * [--- Bank Balance Frame ---]
     * | "Bank Balance" title                        |
     * | [CurrencyIcon] balance | [ItemIcon] balance |
     */
    @Override
    protected void layoutChanged() {
        int p = 2;
        int s = 2;
        int selectedIconSize = 24;
        int balanceIconSize = 16;
        int titleHeight = 12;
        int balanceRowHeight = 18;
        int balanceFrameHeight = titleHeight + s + balanceRowHeight + p * 2;

        int y = p;
        int w = getWidth();

        // Selected market icon at top-left, price label to its right
        if (selectedMarketView.isEnabled()) {
            selectedMarketView.setBounds(p, y, selectedIconSize, selectedIconSize);
            int labelX = selectedMarketView.getRight() + s;
            priceLabel.setBounds(labelX, y, w - labelX - p, selectedIconSize);
            y = selectedMarketView.getBottom() + s;
        }

        // Search label + field between the selected market icon and the market grid
        int searchHeight = 14;
        int searchLabelW = w / 4;
        searchLabel.setBounds(p, y, searchLabelW, searchHeight);
        searchField.setBounds(p + searchLabelW + s, y, w - 2 * p - searchLabelW - s, searchHeight);
        y += searchHeight + s;

        // Market grid fills the space between the search field and the balance frame
        int gridWidth = w - 2 * p;
        int gridHeight = getHeight() - y - s - balanceFrameHeight - p;
        marketGrid.setBounds(p, y, gridWidth, Math.max(0, gridHeight));

        // Update grid columns
        int containerWidth = marketGrid.getContainerWidth();
        if (containerWidth > 0) {
            gridLayout.columns = Math.max(1, containerWidth / ItemView.DEFAULT_WIDTH);
        }

        // Bank Balance frame at the bottom
        int frameY = marketGrid.getBottom() + s;
        balanceFrame.setBounds(p, frameY, w - 2 * p, balanceFrameHeight);

        // Layout inside the balance frame
        int fw = balanceFrame.getWidth();
        int halfW = fw / 2;
        balanceTitleLabel.setBounds(0, p, fw, titleHeight);

        int balanceY = titleHeight + s + p;
        // Left half: currency
        currencyIcon.setBounds(p, balanceY, balanceIconSize, balanceIconSize);
        currencyBalanceLabel.setBounds(p + balanceIconSize + s, balanceY, halfW - balanceIconSize - s - p * 2, balanceRowHeight);

        // Right half: market item
        marketItemIcon.setBounds(halfW + p, balanceY, balanceIconSize, balanceIconSize);
        marketItemBalanceLabel.setBounds(halfW + p + balanceIconSize + s, balanceY, halfW - balanceIconSize - s - p * 2, balanceRowHeight);
    }
}

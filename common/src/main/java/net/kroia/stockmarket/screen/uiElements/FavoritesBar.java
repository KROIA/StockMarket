package net.kroia.stockmarket.screen.uiElements;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.elements.ItemView;
import net.kroia.modutilities.gui.elements.VerticalListView;
import net.kroia.modutilities.gui.layout.LayoutGrid;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    private final VerticalListView marketGrid;
    private final LayoutGrid gridLayout;

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

        // Scrollable grid for all market buttons
        marketGrid = new VerticalListView();
        gridLayout = new LayoutGrid(0, 0, false, false, 0, 1, Alignment.TOP);
        marketGrid.setLayout(gridLayout);
        addChild(marketGrid);
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

        // Update selected market icon
        if (selectedMarketID != null) {
            selectedMarketView.setItemStack(selectedMarketID.getStack());
            selectedMarketView.setEnabled(true);
        } else {
            selectedMarketView.setEnabled(false);
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
        nonFavorites.sort(Comparator.comparing(id -> id.getStack().getHoverName().getString()));
        sorted.addAll(nonFavorites);

        // Create buttons
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
            marketGrid.addChild(btn);
        }

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

    @Override
    protected void render() {
        if (needsRebuild) {
            needsRebuild = false;
            rebuild(pendingAllMarkets, pendingFavoriteIDs, pendingSelectedID);
        }
    }

    /**
     * Positions the selected market icon at the top and the scrollable grid below it.
     * Grid columns are computed dynamically based on available width.
     */
    @Override
    protected void layoutChanged() {
        int p = 2;
        int s = 2;
        int selectedIconSize = 24;

        // Selected market icon at top-left
        if (selectedMarketView.isEnabled()) {
            selectedMarketView.setBounds(p, p, selectedIconSize, selectedIconSize);
        }

        // Grid fills the remaining space below the selected icon
        int gridTop = selectedMarketView.isEnabled()
                ? selectedMarketView.getBottom() + s
                : p;
        int gridWidth = getWidth() - 2 * p;
        int gridHeight = getHeight() - gridTop - p;
        marketGrid.setBounds(p, gridTop, gridWidth, gridHeight);

        // Update grid columns based on available width
        int containerWidth = marketGrid.getContainerWidth();
        if (containerWidth > 0) {
            gridLayout.columns = Math.max(1, containerWidth / ItemView.DEFAULT_WIDTH);
        }
    }
}

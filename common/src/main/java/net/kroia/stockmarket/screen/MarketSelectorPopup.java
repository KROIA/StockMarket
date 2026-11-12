package net.kroia.stockmarket.screen;

import net.kroia.banksystem.util.ItemID;
import org.jetbrains.annotations.Nullable;
import net.kroia.stockmarket.networking.packet.MarketChangedPacket;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.elements.ItemSelectionView;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Full-screen popup for selecting a market from all available markets.
 * <p>
 * Opens on top of the parent screen (typically TradeScreen) and displays an
 * {@link ItemSelectionView} with all tradeable items. When an item is selected,
 * the callback is fired and the popup closes, returning to the parent screen.
 * <p>
 * Follows the same pattern as {@code PluginManagementScreen.MarketSubscribeScreen}.
 */
public class MarketSelectorPopup extends StockMarketGuiScreen {

    private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".trade_screen.";
    private static final Component SELECT_MARKET_TITLE = Component.translatable(PREFIX + "select_market");

    private final StockMarketGuiScreen parentScreen;
    private final Consumer<ItemID> onMarketSelected;
    private final Label titleLabel;
    private final ItemSelectionView itemSelectionView;

    /**
     * @param parentScreen     the screen to return to after selection or on close
     * @param onMarketSelected callback fired with the selected market's ItemID
     */
    public MarketSelectorPopup(StockMarketGuiScreen parentScreen, Consumer<ItemID> onMarketSelected) {
        super(SELECT_MARKET_TITLE, parentScreen);
        this.parentScreen = parentScreen;
        this.onMarketSelected = onMarketSelected;

        titleLabel = new Label(SELECT_MARKET_TITLE.getString());
        titleLabel.setAlignment(Label.Alignment.CENTER);

        itemSelectionView = new ItemSelectionView(this::onItemSelected);
        populateItems();

        addElement(titleLabel);
        addElement(itemSelectionView);
    }

    /** Fills the selection view with all available market items, sorted like the other market lists. */
    private void populateItems() {
        List<ItemID> markets = new ArrayList<>(getAvailableMarkets());
        markets.sort(StockMarketGuiElement.MARKET_TYPE_COMPARATOR);
        List<ItemStack> stacks = markets.stream().map(ItemID::getStack).toList();
        itemSelectionView.setItems(stacks);
    }

    /**
     * {@inheritDoc}
     * While this popup is displayed it is the screen receiving market-removed
     * notifications — forward them to the covered parent screen (typically the
     * TradeScreen) so its market selection stays consistent.
     */
    @Override
    public void onMarketRemoved(ItemID marketID) {
        parentScreen.onMarketRemoved(marketID);
    }

    /**
     * {@inheritDoc}
     * SM-304/SM-310: lists new markets here and forwards every change to the covered parent screen,
     * whose lists were built before the popup opened.
     */
    @Override
    public void onMarketsChanged(MarketChangedPacket.Kind kind, @Nullable ItemID marketID) {
        if (kind == MarketChangedPacket.Kind.ADDED)
            populateItems();
        parentScreen.onMarketsChanged(kind, marketID);
    }

    /**
     * Called when a market item is selected from the ItemSelectionView.
     * Resolves the ItemID asynchronously, fires the callback, and returns to the parent screen.
     */
    private void onItemSelected(ItemStack item) {
        ItemID.getOrRegisterFromItemStackClientSide(item).thenAccept(itemID -> {
            Minecraft.getInstance().execute(() -> {
                onMarketSelected.accept(itemID);
                setScreen(parentScreen);
            });
        });
    }

    @Override
    protected void updateLayout(Gui gui) {
        int p = StockMarketGuiElement.padding;
        int s = StockMarketGuiElement.spacing;
        int w = getWidth() - 2 * p;
        int eh = StockMarketGuiElement.defaultElementHeight;

        titleLabel.setBounds(p, p, w, eh);
        itemSelectionView.setBounds(p, titleLabel.getBottom() + s, w, getHeight() - titleLabel.getBottom() - s - p);
    }

    @Override
    public void onClose() {
        super.onClose();
    }
}

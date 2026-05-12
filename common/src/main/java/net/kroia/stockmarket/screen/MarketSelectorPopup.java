package net.kroia.stockmarket.screen;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.elements.ItemSelectionView;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

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

        // Populate with all available market items
        List<ItemID> markets = getAvailableMarkets();
        List<ItemStack> stacks = markets.stream().map(ItemID::getStack).toList();
        itemSelectionView.setItems(stacks);

        addElement(titleLabel);
        addElement(itemSelectionView);
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

package net.kroia.stockmarket.screen;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.elements.ItemSelectionView;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Screen for creating a new market. Displays all available items in an
 * {@link ItemSelectionView}. When the player selects an item, a market
 * creation request is sent to the server. On success, the screen closes
 * and a fresh {@link ManagementScreen} is opened so the new market
 * appears in the list.
 */
public class CreateMarketScreen extends StockMarketGuiScreen {

    private static class Texts {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".management_screen.";
        public static final Component TITLE = Component.translatable(PREFIX + "new_market_title");
    }

    private final Label titleLabel;
    private final ItemSelectionView itemSelectionView;

    /**
     * Creates the market-creation screen.
     *
     * @param parent the parent screen to return to if the user cancels
     */
    public CreateMarketScreen(StockMarketGuiScreen parent) {
        super(Texts.TITLE, parent);

        titleLabel = new Label(Texts.TITLE.getString());
        titleLabel.setAlignment(Label.Alignment.CENTER);

        itemSelectionView = new ItemSelectionView(this::onItemSelected);

        addElement(titleLabel);
        addElement(itemSelectionView);
    }

    /**
     * Called when the player selects an item from the selection view.
     * Converts the item stack to an {@link ItemID} and requests market creation.
     *
     * @param item the selected item stack
     */
    private void onItemSelected(ItemStack item) {
        info("Item selected for market creation: " + item.getDisplayName().getString());
        ItemID.getOrRegisterFromItemStackClientSide(item).thenAccept(itemID -> {
            info("ItemID resolved: " + itemID);
            getMarketManager().requestCreateMarket(itemID).thenAccept(success -> {
                info("Market creation result: " + success);
                if (success) {
                    // Schedule screen change on the render thread
                    net.minecraft.client.Minecraft.getInstance().execute(() -> {
                        setScreen(new ManagementScreen());
                    });
                }
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

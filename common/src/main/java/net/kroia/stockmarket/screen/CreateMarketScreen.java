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
     * T-123 / T-125 (untrusted slave gate): red info banner overlaid on the
     * item selection view when the master does NOT trust this slave. The
     * item picker itself is also disabled so no click can fire a market
     * creation attempt. T-125: rendered as three stacked labels because
     * ModUtilities' Label widget is single-line only and the full trust
     * explanation would overflow.
     */
    private final Label untrustedSlaveBanner1;
    private final Label untrustedSlaveBanner2;
    private final Label untrustedSlaveBanner3;

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

        // T-123 / T-125: red info banner rendered as three stacked labels
        // (ModUtilities' Label is single-line only). Colors + font match the
        // other T-123 banners.
        untrustedSlaveBanner1 = new Label(Component.translatable("gui.stockmarket.untrusted_slave.banner_line1").getString());
        untrustedSlaveBanner1.setAlignment(Label.Alignment.CENTER);
        untrustedSlaveBanner1.setTextColor(0xFFe8711c);
        untrustedSlaveBanner1.setTextFontScale(0.7f);
        untrustedSlaveBanner1.setEnabled(isUntrustedSlave());

        untrustedSlaveBanner2 = new Label(Component.translatable("gui.stockmarket.untrusted_slave.banner_line2").getString());
        untrustedSlaveBanner2.setAlignment(Label.Alignment.CENTER);
        untrustedSlaveBanner2.setTextColor(0xFFe8711c);
        untrustedSlaveBanner2.setTextFontScale(0.7f);
        untrustedSlaveBanner2.setEnabled(isUntrustedSlave());

        untrustedSlaveBanner3 = new Label(Component.translatable("gui.stockmarket.untrusted_slave.banner_line3").getString());
        untrustedSlaveBanner3.setAlignment(Label.Alignment.CENTER);
        untrustedSlaveBanner3.setTextColor(0xFFe8711c);
        untrustedSlaveBanner3.setTextFontScale(0.7f);
        untrustedSlaveBanner3.setEnabled(isUntrustedSlave());

        if (isUntrustedSlave()) {
            // T-123: refuse market creation clicks on an untrusted slave.
            itemSelectionView.setEnabled(false);
        }

        addElement(titleLabel);
        addElement(untrustedSlaveBanner1);
        addElement(untrustedSlaveBanner2);
        addElement(untrustedSlaveBanner3);
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
        // T-123 / T-125 banner slots between title and item picker; three
        // stacked labels because ModUtilities' Label is single-line only.
        int bannerLineH = 12; // matches textFontScale=0.7f visible height
        int bannerHeight = untrustedSlaveBanner1.isEnabled() ? 3 * bannerLineH : 0;
        int bannerReserve = bannerHeight == 0 ? 0 : bannerHeight + s;
        int bannerTop = titleLabel.getBottom() + s;
        untrustedSlaveBanner1.setBounds(p, bannerTop, w, bannerLineH);
        untrustedSlaveBanner2.setBounds(p, bannerTop + bannerLineH, w, bannerLineH);
        untrustedSlaveBanner3.setBounds(p, bannerTop + 2 * bannerLineH, w, bannerLineH);
        itemSelectionView.setBounds(p, bannerTop + bannerReserve, w,
                getHeight() - bannerTop - bannerReserve - p);
    }

    @Override
    public void onClose() {
        super.onClose();
    }
}

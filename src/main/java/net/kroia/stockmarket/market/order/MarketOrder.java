package net.kroia.stockmarket.market.order;

import net.kroia.stockmarket.StockMarketMod;
import net.minecraft.world.entity.player.Player;

public class MarketOrder extends Order {


    public MarketOrder(Player player, int amount) {
        super(player, amount);

        StockMarketMod.LOGGER.info("MarketOrder created: " + toString());
    }



    @Override
    public String toString() {
        return "MarketOrder{ Owner: " + player.getName() + " Amount: " + amount + " Filled: " + filledAmount + " AveragePrice: " + averagePrice + " Status:" + status+" }";
    }
}

package net.kroia.stockmarket.market.order;

import net.kroia.stockmarket.StockMarketMod;
import net.minecraft.world.entity.player.Player;

/*
    * The LimitOrder class represents a spot order.
 */
public class LimitOrder extends Order {
    private final int price;

    public LimitOrder(Player player, int amount, int price) {
        super(player,  amount);
        this.price = price;

        StockMarketMod.LOGGER.info("MarketOrder created: " + toString());
    }

    public int getPrice() {
        return price;
    }

    @Override
    public String toString() {
        return "LimitOrder{ Owner: " + player.getName() + " Amount: " + amount + " Filled: " + filledAmount + " Price: " + price + " AveragePrice: " + averagePrice + " Status:" + status+" }";
    }


}

package net.kroia.stockmarket.market.server.order;

import net.kroia.stockmarket.StockMarketMod;
import net.minecraft.world.entity.player.Player;

/*
    * The Order class represents a buy or sell order.
 */
public abstract class Order {
    protected final Player player;
    //private final String itemID;
    protected final int amount;
    protected int filledAmount = 0;

    protected int averagePrice = 0;

    public enum Status {
        PENDING,
        PROCESSED,
        INVALID
    }
    protected Status status = Status.PENDING;

    public Order(Player player, /*String itemID,*/ int amount) {
        this.player = player;
        //this.itemID = itemID;
        this.amount = amount;
    }

    public Player getPlayer() {
        return player;
    }

    /*public String getItemID() {
        return itemID;
    }*/

    public int getAmount() {
        return amount;
    }

    public boolean isBuy() {
        return amount > 0;
    }
    public boolean isSell() {
        return amount < 0;
    }

    public void markAsProcessed() {
        status = Status.PROCESSED;
        StockMarketMod.LOGGER.info("Order processed: " + toString());
    }
    public void markAsInvalid() {
        status = Status.INVALID;
        StockMarketMod.LOGGER.info("Order invalid: " + toString());
    }

    public Status getStatus() {
        return status;
    }

    public int getAveragePrice() {
        return averagePrice;
    }
    public void setAveragePrice(int averagePrice) {
        this.averagePrice = averagePrice;
    }

    /**
     * Fills the order with the given amount.
     * @param amount The amount to fill the order with.
     * @return The remaining amount that could not be filled.
     */
    public int fill(int amount)
    {
        int fillAmount = this.amount - filledAmount;
        if(Math.abs(fillAmount) > Math.abs(amount))
            fillAmount = amount;
        filledAmount += fillAmount;
        if(isFilled())
        {
            markAsProcessed();
        }
        return amount - fillAmount;
    }

    public boolean isFilled() {
        return Math.abs(filledAmount) >= Math.abs(amount);
    }

    public int getFilledAmount() {
        return filledAmount;
    }

    // pure virtual function
    public abstract String toString();

}

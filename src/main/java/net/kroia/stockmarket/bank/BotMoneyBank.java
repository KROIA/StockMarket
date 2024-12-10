package net.kroia.stockmarket.bank;

import net.kroia.stockmarket.StockMarketMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public class BotMoneyBank extends MoneyBank {

    private static BotMoneyBank instance;

    public BotMoneyBank(long balance) {
        super(UUID.randomUUID(), balance);
        instance = this;
    }
    public BotMoneyBank(UUID botID, long balance) {
        super(botID, balance);
        instance = this;
    }
    public BotMoneyBank(FriendlyByteBuf buf) {
        super(buf);
        instance = this;
    }
    public BotMoneyBank(CompoundTag tag) {
        super(tag);
        instance = this;
    }

    public static BotMoneyBank getInstance() {
        return instance;
    }







    protected void notifyUser(String msg) {
        StockMarketMod.LOGGER.info("[BOT MONEY BANK] "+msg);
        //StockMarketMod.printToClientConsole(userUUID, msg);
    }
}

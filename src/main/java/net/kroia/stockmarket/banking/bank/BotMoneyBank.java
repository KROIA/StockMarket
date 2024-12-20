package net.kroia.stockmarket.banking.bank;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.banking.BankUser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public class BotMoneyBank extends MoneyBank {



    public BotMoneyBank(BankUser owner, long balance) {
        super(owner, balance);

    }
    public BotMoneyBank(BankUser owner, CompoundTag tag) {
        super(owner, tag);

    }

    @Override
    public boolean save(CompoundTag tag) {
        tag.putString("BankType", BankType.BOT_MONEY.name());
        return super.save(tag);
    }

    @Override
    public boolean load(CompoundTag tag) {
        return super.load(tag);
    }


    @Override
    protected void notifyUser(String msg) {
        StockMarketMod.LOGGER.info("[BOT MONEY BANK] "+msg);
    }
}

package net.kroia.stockmarket.banking.bank;

import net.kroia.stockmarket.banking.BankUser;
import net.minecraft.nbt.CompoundTag;

public class MoneyBank extends Bank {

    public static final String ITEM_ID = "$";


    public MoneyBank(BankUser owner, long balance) {
        super(owner, ITEM_ID, balance);
    }
    public MoneyBank(BankUser owner, CompoundTag tag) {
        super(owner, tag);
    }

    @Override
    public boolean save(CompoundTag tag) {
        tag.putString("BankType", BankType.MONEY.name());
        return super.save(tag);
    }

    @Override
    public boolean load(CompoundTag tag) {
        return super.load(tag);
    }


}

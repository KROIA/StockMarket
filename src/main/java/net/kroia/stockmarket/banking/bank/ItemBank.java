package net.kroia.stockmarket.banking.bank;

import net.kroia.stockmarket.banking.BankUser;
import net.minecraft.nbt.CompoundTag;

public class ItemBank extends Bank {

    public ItemBank(BankUser owner, String itemID, long balance) {
        super(owner, itemID, balance);
    }
    public ItemBank(BankUser owner, CompoundTag tag) {
        super(owner, tag);
    }

    @Override
    public boolean save(CompoundTag tag) {
        tag.putString("BankType", BankType.ITEM.name());
        return super.save(tag);
    }

    @Override
    public boolean load(CompoundTag tag) {
        return super.load(tag);
    }

}

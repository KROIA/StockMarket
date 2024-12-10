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
    public void save(CompoundTag tag) {
        tag.putString("BankType", BankType.ITEM.name());
        super.save(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
    }

}

package net.kroia.stockmarket.item.custom.software;

import net.kroia.stockmarket.block.ModBlocks;
import net.kroia.stockmarket.block.custom.TerminalBlock;

public class BankingSoftware extends Software {
    public static final String NAME = "banking_software";
    public BankingSoftware() {
        super();
    }

    @Override
    public TerminalBlock getProgrammedBlock()
    {
        return ModBlocks.BANK_TERMINAL_BLOCK.get();
    }

}

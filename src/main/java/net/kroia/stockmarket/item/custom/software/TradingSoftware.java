package net.kroia.stockmarket.item.custom.software;

import net.kroia.banksystem.block.custom.TerminalBlock;
import net.kroia.banksystem.item.custom.software.Software;
import net.kroia.stockmarket.block.ModBlocks;

public class TradingSoftware extends Software {
    public static final String NAME = "trading_software";
    public TradingSoftware() {
        super();
    }

    @Override
    public TerminalBlock getProgrammedBlock()
    {
        return ModBlocks.STOCK_MARKET_BLOCK.get();
    }
}

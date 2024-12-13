package net.kroia.stockmarket.item.custom.software;

import net.kroia.stockmarket.block.ModBlocks;
import net.kroia.stockmarket.block.custom.TerminalBlock;

public class TradingSoftware extends Software{
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

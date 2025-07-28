package net.kroia.stockmarket.util;

import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.kroia.stockmarket.screen.custom.StockMarketManagementScreen;
import net.kroia.stockmarket.screen.custom.TradeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.BlockEntity;

public class StockMarketClientHooks {
    public static InteractionResult openStockMarketBlockScreen(BlockEntity entity, BlockPos pos)
    {
        if(entity instanceof StockMarketBlockEntity stockMarketBlockEntity)
        {
            Minecraft.getInstance().submit(() -> {
                TradeScreen.openScreen(stockMarketBlockEntity);
            });
        }
        else
        {
            //BACKEND_INSTANCES.LOGGER.warn("Block entity at position: "+pos+" is not of type StockMarketBlockEntity");
            return InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }
    public static void openStockMarketBlockScreen()
    {
        Minecraft.getInstance().submit(() -> {
            TradeScreen.openScreen();
        });
    }
    public static void openBotSettingsScreen()
    {
        System.out.println("NOT_IMPLEMENTED Opening Bot Settings Screen");
       // Minecraft.getInstance().submit(()->{BotSettingsScreen.openScreen();});
    }
    public static void openStockMarketManagementScreen()
    {
        //System.out.println("NOT_IMPLEMENTED Opening Bot Settings Screen");
        Minecraft.getInstance().submit(()->{
            StockMarketManagementScreen.openScreen();});
    }
}

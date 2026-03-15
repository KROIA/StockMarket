package net.kroia.stockmarket.util;

import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.kroia.stockmarket.networking.packet.OpenUIPacket;
import net.kroia.stockmarket.screen.DevTestScreen;
import net.kroia.stockmarket.screen.TradeScreen;
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

    public static void openGUI(OpenUIPacket.GUIType type)
    {
        Minecraft mc = Minecraft.getInstance();
        switch(type)
        {
            case DEVELOPMENT:
            {
                DevTestScreen screen = new DevTestScreen();
                mc.setScreen(screen);
                break;
            }
        }
    }
}

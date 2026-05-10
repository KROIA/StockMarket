package net.kroia.stockmarket.util;

import net.kroia.stockmarket.networking.packet.OpenUIPacket;
import net.kroia.stockmarket.screen.DevTestScreen;
import net.kroia.stockmarket.screen.ManagementScreen;
import net.kroia.stockmarket.screen.TradeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.BlockEntity;

public class StockMarketClientHooks {
    public static InteractionResult openStockMarketBlockScreen(BlockEntity entity, BlockPos pos)
    {
        Minecraft.getInstance().submit(TradeScreen::openScreen);
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
            case MANAGEMENT:
            {
                ManagementScreen.openScreen();
                break;
            }
        }
    }
}

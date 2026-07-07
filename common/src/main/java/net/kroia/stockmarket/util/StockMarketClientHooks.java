package net.kroia.stockmarket.util;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.client.RecipeImageExporter;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.networking.packet.OpenUIPacket;
import net.kroia.stockmarket.screen.DevTestScreen;
import net.kroia.stockmarket.screen.ManagementScreen;
import net.kroia.stockmarket.screen.TradeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.nio.file.Path;
import java.util.Map;

public class StockMarketClientHooks {
    public static InteractionResult openStockMarketBlockScreen(BlockEntity entity, BlockPos pos)
    {
        Minecraft.getInstance().submit(TradeScreen::openScreen);
        return InteractionResult.SUCCESS;
    }

    /**
     * Notifies the currently open StockMarket screen that a market has been deleted
     * on the server. Called by {@code MarketRemovedPacket} on the client main thread
     * after the client market caches have already been purged.
     * Screens that keep a market selection (TradeScreen, ManagementScreen) override
     * {@link StockMarketGuiScreen#onMarketRemoved} to deselect the dead market.
     *
     * @param marketID the market that was deleted on the server
     */
    public static void notifyScreenMarketRemoved(ItemID marketID)
    {
        if (Minecraft.getInstance().screen instanceof StockMarketGuiScreen guiScreen) {
            guiScreen.onMarketRemoved(marketID);
        }
    }

    public static void openGUI(OpenUIPacket.GUIType type)
    {
        Minecraft mc = Minecraft.getInstance();
        switch(type)
        {
            case DEVELOPMENT:
            {
                if(StockMarketMod.ENABLE_DEV_FEATURES) {
                    DevTestScreen screen = new DevTestScreen();
                    mc.setScreen(screen);
                }
                break;
            }
            case MANAGEMENT:
            {
                ManagementScreen.openScreen();
                break;
            }
            case EXPORT_RECIPES:
            {
                exportRecipeImages();
                break;
            }
        }
    }

    /**
     * Exports all StockMarket mod recipe images to the game directory.
     * Runs on the render thread via Minecraft.execute() for GL safety.
     * Output: <gameDir>/recipe_exports/stockmarket/recipe_<name>.png
     */
    private static void exportRecipeImages()
    {
        Minecraft mc = Minecraft.getInstance();

        // Schedule on the render thread to ensure GL context is available
        mc.execute(() -> {
            Path exportDir = mc.gameDirectory.toPath().resolve("recipe_exports/stockmarket");
            int successCount = 0;

            // Recipe 1: Trading Software
            // Pattern: "ST" (1x2 shaped recipe)
            // S = banksystem:software, T = minecraft:emerald
            {
                String[] pattern = {"ST"};
                Map<Character, String> keys = Map.of(
                        'S', "banksystem:software",
                        'T', "minecraft:emerald"
                );
                Path outputPath = exportDir.resolve("recipe_trading_software.png");
                if (RecipeImageExporter.exportShapedRecipe(pattern, keys, "stockmarket:trading_software", outputPath))
                {
                    successCount++;
                }
            }

            // Recipe 2: Stock Market Display Block
            // Pattern: 3x3 shaped recipe
            // I = minecraft:iron_nugget, G = minecraft:glass_pane,
            // D = banksystem:display, E = minecraft:emerald
            {
                String[] pattern = {"IGI", "GDG", "IEI"};
                Map<Character, String> keys = Map.of(
                        'I', "minecraft:iron_nugget",
                        'G', "minecraft:glass_pane",
                        'D', "banksystem:display",
                        'E', "minecraft:emerald"
                );
                Path outputPath = exportDir.resolve("recipe_stockmarket_display_block.png");
                if (RecipeImageExporter.exportShapedRecipe(pattern, keys, "stockmarket:stockmarket_display_block", outputPath))
                {
                    successCount++;
                }
            }

            // Notify the player in chat
            String message = "StockMarket: Exported " + successCount + "/2 recipe images to " + exportDir;
            mc.gui.getChat().addMessage(Component.literal(message));
        });
    }
}

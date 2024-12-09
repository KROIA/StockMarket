package net.kroia.stockmarket.util;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.File;

@Mod.EventBusSubscriber
public class ServerEvents {
    private static final DataHandler DATA_HANDLER = new DataHandler();

    @SubscribeEvent
    public static void onServerStart(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            MinecraftServer server = serverLevel.getServer();
            ResourceKey<Level> levelKey = serverLevel.dimension();

            // Only load data for the main overworld level
            if (levelKey.equals(ServerLevel.OVERWORLD)) {
                File rootSaveFolder = server.getWorldPath(LevelResource.ROOT).toFile();

                // Load data from the root save folder
                DATA_HANDLER.loadFromFile(rootSaveFolder);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStop(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            MinecraftServer server = serverLevel.getServer();
            ResourceKey<Level> levelKey = serverLevel.dimension();

            // Only save data for the main overworld level
            if (levelKey.equals(ServerLevel.OVERWORLD)) {
                File rootSaveFolder = server.getWorldPath(LevelResource.ROOT).toFile();

                // Save data to the root save folder
                DATA_HANDLER.saveToFile(rootSaveFolder);
            }
        }
    }

    public static DataHandler getDataHandler() {
        return DATA_HANDLER;
    }
}

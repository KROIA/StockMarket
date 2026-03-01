package net.kroia.stockmarket;

import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.kroia.banksystem.block.BankSystemBlocks;
import net.kroia.banksystem.entity.BankSystemEntities;
import net.kroia.banksystem.item.BankSystemCreativeModeTab;
import net.kroia.banksystem.item.BankSystemItems;
import net.kroia.banksystem.menu.BankSystemMenus;
import net.kroia.banksystem.util.BankSystemTextMessages;
import net.kroia.stockmarket.api.StockMarketAPI;
import net.kroia.stockmarket.block.StockMarketBlocks;
import net.kroia.stockmarket.compat.NEZNAMY_TAB_Placeholders;
import net.kroia.stockmarket.entity.StockMarketEntities;
import net.kroia.stockmarket.event.EventRegistration;
import net.kroia.stockmarket.item.StockMarketCreativeModeTab;
import net.kroia.stockmarket.item.StockMarketItems;
import net.kroia.stockmarket.menu.StockMarketMenus;
import net.kroia.stockmarket.util.StockMarketLogger;
import net.kroia.stockmarket.util.StockMarketTextMessages;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public class StockMarketModBackend implements StockMarketAPI {

    public static class Instances
    {
        public StockMarketModSettings SERVER_SETTINGS;


        public StockMarketLogger LOGGER;
    }

    private static Instances INSTANCES = new Instances();


    StockMarketModBackend()
    {
        INSTANCES.LOGGER = new StockMarketLogger(INSTANCES);


        StockMarketModSettings.setBackend(INSTANCES);
        StockMarketTextMessages.setBackend(INSTANCES);


        StockMarketBlocks.init();
        StockMarketItems.init();
        StockMarketEntities.init();
        StockMarketMenus.init();
        StockMarketCreativeModeTab.init();
        StockMarketTextMessages.init();

        EventRegistration.init();
    }



    // Called from the client side
    public static void onClientSetup()
    {
        StockMarketMenus.setupScreens();


        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(StockMarketModBackend::onPlayerLeaveClientSide);
    }

    // Called from the server side
    public static void onServerSetup()
    {

    }


    // Called from the server side
    public static void onServerStart(MinecraftServer server)
    {
        INSTANCES.SERVER_SETTINGS = new StockMarketModSettings();
        INSTANCES.SERVER_SETTINGS.setLogger(INSTANCES.LOGGER::error, INSTANCES.LOGGER::error, INSTANCES.LOGGER::debug);

        NEZNAMY_TAB_Placeholders.setBackend(INSTANCES);

        loadDataFromFiles(server);

        TickEvent.SERVER_POST.register(StockMarketModBackend::onServerTick);
    }


    // Called from the server side
    public static void onServerStop(MinecraftServer server)
    {
        TickEvent.SERVER_POST.unregister(StockMarketModBackend::onServerTick);
        saveDataToFiles(server);

    }


    // Called from the server side
    public static void onPlayerJoin(ServerPlayer player)
    {

    }


    // Called from the server side
    public static void onPlayerLeave(ServerPlayer player)
    {

    }

    // Called from the client side
    private static void onPlayerLeaveClientSide(@Nullable LocalPlayer localPlayer)
    {

    }

    // Called from the server side
    private static void onServerTick(MinecraftServer server)
    {

    }

    public static void loadDataFromFiles(MinecraftServer server)
    {
        Path rootSaveFolder = server.getWorldPath(LevelResource.ROOT);
        // Load data from the root save folder


    }
    public static void saveDataToFiles(MinecraftServer server)
    {
        Path rootSaveFolder = server.getWorldPath(LevelResource.ROOT);
        // Load data from the root save folder

    }



    @Override
    public String getModID()
    {
        return StockMarketMod.MOD_ID;
    }

    @Override
    public String getModVersion() {
        return StockMarketMod.VERSION;
    }
}

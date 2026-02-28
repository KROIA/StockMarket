package net.kroia.stockmarket.event;

import dev.architectury.event.events.common.LifecycleEvent;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.data.DatabaseManager;

public class EventRegistration {

    public static void init(){
        StockMarketMod.LOGGER.info("Registering database events");
        LifecycleEvent.SERVER_STARTED.register(DatabaseManager::connectToDatabase);
        LifecycleEvent.SERVER_STOPPED.register(DatabaseManager::shutdownDatabase);
    }

}

package net.kroia.stockmarket.event;

import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.minecraft.command.StockMarketCommands;

public class EventRegistration {

    public static void init(){
        StockMarketMod.LOGGER.info("Registering events");
        CommandRegistrationEvent.EVENT.register(StockMarketCommands::register);
    }

}

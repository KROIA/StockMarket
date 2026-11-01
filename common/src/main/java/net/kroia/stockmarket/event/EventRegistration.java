package net.kroia.stockmarket.event;

import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.InteractionEvent;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.minecraft.command.StockMarketCommands;
import net.kroia.stockmarket.villagertrading.VillagerTradeRewriter;

public class EventRegistration {

    public static void init(){
        StockMarketMod.LOGGER.info("Registering events");
        CommandRegistrationEvent.EVENT.register(StockMarketCommands::register);
        // Villager trade repricing: fires server-side before the trade menu opens.
        // Registered once and never unregistered — the handler is inert while no
        // backend/manager/price table exists.
        InteractionEvent.INTERACT_ENTITY.register(VillagerTradeRewriter::onInteractEntity);
    }

}

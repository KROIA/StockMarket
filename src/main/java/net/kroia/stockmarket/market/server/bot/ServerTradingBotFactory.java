package net.kroia.stockmarket.market.server.bot;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.HashMap;

public class ServerTradingBotFactory {

    public static ServerTradingBot loadFromTag(CompoundTag tag)
    {
        if(tag == null)
            return null;

        if(!tag.contains("class"))
            return null;

        String className = tag.getString("class");

        if(className.compareTo(ServerTradingBot.class.getSimpleName()) == 0)
        {
            ServerTradingBot bot = new ServerTradingBot();
            if(bot.load(tag))
                return bot;
        }
        else if(className.compareTo(ServerVolatilityBot.class.getSimpleName()) == 0)
        {
            ServerVolatilityBot bot = new ServerVolatilityBot();
            if(bot.load(tag))
                return bot;
        }
        else if(className.compareTo(ServerMarketMakerBot.class.getSimpleName()) == 0)
        {
            ServerMarketMakerBot bot = new ServerMarketMakerBot();
            if(bot.load(tag))
                return bot;
        }

        throw new RuntimeException("Unknown bot class: " + className);
    }


    // Template
    /*public static <T extends ServerTradingBot> T instantiateBot(Class<T> clazz, T.Settings settings) {
        if (settings == null) {
            return null;
        }

        try {
            // Create a new instance of T using its default constructor
            T bot = clazz.getDeclaredConstructor().newInstance();
            bot.setSettings(settings);
            return bot;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to instantiate bot of type " + clazz.getName(), e);
        }
    }
*/

    public static class BotBuilderContainer
    {
        public String itemID;
        public ServerTradingBot bot;
        public ServerTradingBot.Settings settings;
        public long initialItemStock;
    }
    public static <T extends ServerTradingBot> void botTableBuilder(
            HashMap<String, ArrayList<BotBuilderContainer>> table,
            String itemID,
            ServerTradingBot instance,
            T.Settings settings,
            long initialItemStock)
    {
        //T bot = instantiateBot(clazz, settings);
        if(instance == null)
            return;
        instance.setSettings(settings);
        if(instance.getSettings() != settings)
            return;
        if(!table.containsKey(itemID))
        {
            ArrayList<BotBuilderContainer> bots = new ArrayList<>();
            table.put(itemID, bots);
        }
        ArrayList<BotBuilderContainer> bots = table.get(itemID);
        BotBuilderContainer container = new BotBuilderContainer();
        container.bot = instance;
        container.settings = settings;
        container.itemID = itemID;
        container.initialItemStock = initialItemStock;
        bots.add(container);
    }
}

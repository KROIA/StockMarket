package net.kroia.stockmarket.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ItemUtilities;
import net.kroia.modutilities.PlayerUtilities;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModSettings;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.market.server.bot.ServerTradingBot;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.kroia.stockmarket.networking.packet.server_sender.update.OpenScreenPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncBotSettingsPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncTradeItemsPacket;
import net.kroia.stockmarket.util.ServerPlayerList;
import net.kroia.stockmarket.util.StockMarketTextMessages;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class StockMarketCommands {
    // Method to register commands
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {


        // /StockMarket setPriceCandleTimeInterval <seconds>                            - Set the interval for the price candles. (Each candle will represent this amount of time)
        // /StockMarket createDefaultBots                                               - Create default bots
        // /StockMarket createDefaultBots <category>                                    - Create a all default bots that are defined in the specific json file
        // /StockMarket createDefaultBot <itemID>                                       - Create a default bot for that item if presets are available
        // /StockMarket removeMarkets <category>                                        - Remove all markets of a category which are defined in the specific json file
        // /StockMarket order cancelAll                                                 - Cancel all orders
        // /StockMarket order cancelAll <itemID>                                        - Cancel all orders of an item
        // /StockMarket order <username> cancelAll                                      - Cancel all orders of a player
        // /StockMarket order <username> cancelAll <itemID>                             - Cancel all orders of a player for an item
        // /StockMarket BotSettingsGUI                                                  - Open the settings GUI for the market bots
        // /StockMarket ManagementGUI                                                   - Open the management GUI to create and remove trading items
        // /StockMarket <itemID> bot create                                             - Create bot
        // /StockMarket <itemID> bot remove                                             - Remove bot
        // /StockMarket <itemID> create                                                 - Create marketplace
        // /StockMarket <itemID> remove                                                 - Remove marketplace
        // /StockMarket <itemID> open                                                   - Open the marketplace for trading
        // /StockMarket <itemID> close                                                  - Close the marketplace for trading
        // /StockMarket <itemID> currentPrice                                           - Get current price
        // /StockMarket save                                                            - Save market data
        // /StockMarket load                                                            - Load market data
        // /StockMarket closeAllMarkets                                                 - Closes the market place for trading for all items on the market
        // /StockMarket openAllMarkets                                                  - Opens the market place for trading for all items on the market

        dispatcher.register(
                Commands.literal("StockMarket")
                        /*.then(Commands.literal("help")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player = source.getPlayerOrException();
                                    player.sendSystemMessage(Component.literal("StockMarket commands:"));

                                    return Command.SINGLE_SUCCESS;
                                })
                        )*/
                        .then(Commands.literal("setPriceCandleTimeInterval")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                        .executes(context -> {
                                            CommandSourceStack source = context.getSource();
                                            ServerPlayer player = source.getPlayerOrException();
                                            int seconds = IntegerArgumentType.getInteger(context, "seconds");
                                            if(seconds < 0)
                                                return Command.SINGLE_SUCCESS; // Do not allow negative values
                                            StockMarketMod.SERVER_SETTINGS.MARKET.SHIFT_PRICE_CANDLE_INTERVAL_MS.set(seconds * 1000L);
                                            // Execute the command on the server_sender
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(Commands.literal("createDefaultBots")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player = source.getPlayerOrException();
                                    ServerMarket.createDefaultBots();
                                    player.sendSystemMessage(Component.literal("Default bots created"));

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(Commands.literal("createDefaultBots")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("category", StringArgumentType.string()).suggests((context, builder) -> {
                                                    List<String> suggestions = StockMarketMod.SERVER_DATA_HANDLER.getDefaultBotSettingsFileNames();
                                                    for (String suggestion : suggestions) {
                                                        builder.suggest("\"" + suggestion + "\"");
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> {
                                                    CommandSourceStack source = context.getSource();
                                                    ServerPlayer player = source.getPlayerOrException();
                                                    String category = StringArgumentType.getString(context, "category");
                                                    if(ServerMarket.createDefaultBots(category))
                                                    {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getDefaultBotsCategoryCreatedMessage(category));
                                                    }
                                                    else
                                                    {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getCanNotCreateDefaultBotsCategoryMessage(category));
                                                    }
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                )
                        )
                        /*.then(Commands.literal("createDefaultBots_allItems")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player = source.getPlayerOrException();
                                    ArrayList<ItemID> items = ServerBankManager.getPotentialBankItemIDs();

                                    try {
                                        for(ItemID itemID : items)
                                        {
                                            ServerVolatilityBot bot = new ServerVolatilityBot();
                                            if(ServerMarket.addTradeItemIfNotExists(itemID, 100))
                                                ServerMarket.setTradingBot(itemID, bot);
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }

                                    player.sendSystemMessage(Component.literal("Default bots created"));

                                    return Command.SINGLE_SUCCESS;
                                })
                        )*/
                        .then(Commands.literal("createDefaultBot")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("itemID", StringArgumentType.string()).suggests((context, builder) -> {
                                                    Set<ItemID> suggestions = StockMarketModSettings.MarketBot.getBotBuilder().keySet();
                                                    for (ItemID suggestion : suggestions) {
                                                        builder.suggest("\"" + suggestion.getName() + "\"");
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> {
                                                    CommandSourceStack source = context.getSource();
                                                    ServerPlayer player = source.getPlayerOrException();
                                                    String itemIDStr = StringArgumentType.getString(context, "itemID");
                                                    ItemID itemID = new ItemID(itemIDStr);
                                                    if(ServerMarket.createDefaultBot(itemID))
                                                    {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getDefaultBotCreatedMessage(itemIDStr));
                                                    }
                                                    else
                                                    {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getCanNotCreateDefaultBotMessage(itemIDStr));
                                                    }
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                )
                        )
                        .then(Commands.literal("removeMarkets")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("category", StringArgumentType.string()).suggests((context, builder) -> {
                                                    List<String> suggestions = StockMarketMod.SERVER_DATA_HANDLER.getDefaultBotSettingsFileNames();
                                                    builder.suggest("All");
                                                    for (String suggestion : suggestions) {
                                                        builder.suggest("\"" + suggestion + "\"");
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> {
                                                    CommandSourceStack source = context.getSource();
                                                    ServerPlayer player = source.getPlayerOrException();
                                                    String category = StringArgumentType.getString(context, "category");
                                                    if(category.equals("All"))
                                                    {
                                                        ServerMarket.removeAllTradingItems();
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getAllMarketsRemovedMessage());
                                                    }
                                                    else {
                                                        if (ServerMarket.removeTradingItems(category)) {
                                                            PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getDefaultMarketCategoryRemovedMessage(category));
                                                        } else {
                                                            PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getUnknownMarketCategory(category));
                                                        }
                                                    }
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                )
                        )
                        .then(Commands.literal("order")
                                .then(Commands.literal("cancelAll")
                                        .executes(context -> {
                                            CommandSourceStack source = context.getSource();
                                            ServerPlayer player = source.getPlayerOrException();
                                            ServerMarket.cancelAllOrders(player.getUUID());

                                            return Command.SINGLE_SUCCESS;
                                        })
                                        .then(Commands.argument("itemID", StringArgumentType.string()).suggests((context, builder) -> {
                                                            ArrayList<ItemID> suggestions = ServerMarket.getTradeItemIDs();
                                                            for (ItemID suggestion : suggestions) {
                                                                builder.suggest("\"" + suggestion.getName() + "\"");
                                                            }
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(context -> {
                                                            CommandSourceStack source = context.getSource();
                                                            ServerPlayer player = source.getPlayerOrException();
                                                            ItemID itemID = new ItemID(StringArgumentType.getString(context, "itemID"));
                                                            ServerMarket.cancelAllOrders(player.getUUID(), itemID);

                                                            return Command.SINGLE_SUCCESS;
                                                        })
                                        )
                                )
                                .then(Commands.argument("username", StringArgumentType.string()).suggests((context, builder) -> {
                                                    //builder.suggest("\""+ ModSettings.MarketBot.USER_NAME +"\"");
                                                    Map<UUID, String> uuidToNameMap = ServerPlayerList.getUuidToNameMap();
                                                    for (String name : uuidToNameMap.values()) {

                                                        builder.suggest("\"" + name + "\"");
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .requires(source -> source.hasPermission(2))
                                                .then(Commands.literal("cancelAll")
                                                        .executes(context -> {
                                                            CommandSourceStack source = context.getSource();
                                                            String username = StringArgumentType.getString(context, "username");
                                                            ServerMarket.cancelAllOrders(ServerPlayerList.getPlayerUUID(username));
                                                            return Command.SINGLE_SUCCESS;
                                                        })
                                                        .then(Commands.argument("itemID", StringArgumentType.string()).suggests((context, builder) -> {
                                                                            ArrayList<ItemID> suggestions = ServerMarket.getTradeItemIDs();
                                                                            for (ItemID suggestion : suggestions) {
                                                                                builder.suggest("\"" + suggestion.getName()+ "\"");
                                                                            }
                                                                            return builder.buildFuture();
                                                                        })
                                                                        .executes(context -> {
                                                                            CommandSourceStack source = context.getSource();
                                                                            ServerPlayer player = source.getPlayerOrException();
                                                                            ItemID itemID = new ItemID(StringArgumentType.getString(context, "itemID"));
                                                                            String username = StringArgumentType.getString(context, "username");
                                                                            ServerMarket.cancelAllOrders(ServerPlayerList.getPlayerUUID(username), itemID);

                                                                            return Command.SINGLE_SUCCESS;
                                                                        })
                                                        )
                                                )

                                )
                        )
                        .then(Commands.literal("BotSettingsGUI")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if(player != null) {
                                        ArrayList<ItemID> suggestions = ServerMarket.getTradeItemIDs();
                                        if(!suggestions.isEmpty()) {
                                            ItemID itemID = suggestions.get(0);
                                            SyncBotSettingsPacket.sendPacket(player, itemID);
                                            OpenScreenPacket.sendPacket(player, OpenScreenPacket.ScreenType.BOT_SETTINGS);
                                        }
                                        else {
                                            PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getNoTradingItemAvailableMessage());
                                        }
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(Commands.literal("ManagementGUI")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if(player != null) {
                                        SyncTradeItemsPacket.sendPacket(player);
                                        OpenScreenPacket.sendPacket(player, OpenScreenPacket.ScreenType.STOCKMARKET_MANAGEMENT);
                                        /*
                                        ArrayList<String> suggestions = ServerMarket.getTradeItemIDs();
                                        if(!suggestions.isEmpty()) {
                                            //String itemID = suggestions.get(0);
                                            //SyncBotSettingsPacket.sendPacket(player, itemID, ServerMarket.getBotUserUUID());
                                            }
                                        else {
                                            PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getNoTradingItemAvailableMessage());
                                        }*/
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(Commands.literal("resetAllPriceCharts")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if(player != null) {
                                        ServerMarket.resetPriceChart();
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(Commands.argument("itemID", StringArgumentType.string()).suggests((context, builder) -> {
                                            ArrayList<ItemID> suggestions = ServerMarket.getTradeItemIDs();
                                            for (ItemID suggestion : suggestions) {
                                                builder.suggest("\"" + suggestion.getName() + "\"");
                                            }
                                            return builder.buildFuture();
                                        })
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.literal("bot")
                                                .then(Commands.literal("create")
                                                        .executes(context -> {
                                                            CommandSourceStack source = context.getSource();
                                                            ServerPlayer player = source.getPlayerOrException();
                                                            String orgItemID = StringArgumentType.getString(context, "itemID");
                                                            String itemIDStr = ItemUtilities.getNormalizedItemID(orgItemID);
                                                            if (itemIDStr == null) {
                                                                PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getInvalidItemIDMessage(orgItemID));
                                                                return Command.SINGLE_SUCCESS;
                                                            }
                                                            ItemID itemID = new ItemID(itemIDStr);
                                                            ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                            if (bot == null) {
                                                                ServerVolatilityBot volatilityBot = new ServerVolatilityBot();
                                                                volatilityBot.setEnabled(false);
                                                                ServerMarket.setTradingBot(itemID, volatilityBot);
                                                                PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getBotCreatedMessage(itemIDStr));
                                                            } else {
                                                                PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getBotAlreadyExistMessage(itemIDStr));
                                                            }
                                                            // Execute the command on the server_sender
                                                            return Command.SINGLE_SUCCESS;
                                                        })
                                                )

                                                .then(Commands.literal("remove")
                                                        .executes(context -> {
                                                            CommandSourceStack source = context.getSource();
                                                            ServerPlayer player = source.getPlayerOrException();
                                                            String orgItemID = StringArgumentType.getString(context, "itemID");
                                                            String itemIDStr = ItemUtilities.getNormalizedItemID(orgItemID);
                                                            if (itemIDStr == null) {
                                                                PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getInvalidItemIDMessage(orgItemID));
                                                                return Command.SINGLE_SUCCESS;
                                                            }
                                                            ItemID itemID = new ItemID(itemIDStr);
                                                            ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                            if (bot == null) {
                                                                PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getBotNotExistMessage(itemIDStr));
                                                            } else {
                                                                ServerMarket.removeTradingBot(itemID);
                                                                PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getBotDeletedMessage(itemIDStr));
                                                            }
                                                            // Execute the command on the server_sender
                                                            return Command.SINGLE_SUCCESS;
                                                        })
                                                )
                                        )
                                        .then(Commands.literal("create")
                                                .executes(context -> {
                                                    CommandSourceStack source = context.getSource();
                                                    ServerPlayer player = source.getPlayerOrException();
                                                    String orgItemID = StringArgumentType.getString(context, "itemID");
                                                    String itemIDStr = ItemUtilities.getNormalizedItemID(orgItemID);
                                                    if (itemIDStr == null) {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getInvalidItemIDMessage(orgItemID));
                                                        return Command.SINGLE_SUCCESS;
                                                    }

                                                    ItemID itemID = new ItemID(itemIDStr);
                                                    if (ServerMarket.hasItem(itemID)) {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getMarketplaceAlreadyExistingMessage(itemIDStr));
                                                    } else {
                                                        if(ServerMarket.addTradeItem(itemID, 0))
                                                        {
                                                            // Notify all serverPlayers
                                                            PlayerUtilities.printToClientConsole(StockMarketTextMessages.getMarketplaceCreatedMessage(itemIDStr));
                                                        }
                                                        else
                                                        {
                                                            PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getMarketplaceIsNotAllowedMessage(itemIDStr));
                                                        }
                                                    }
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                        .then(Commands.literal("remove")
                                                .executes(context -> {
                                                    CommandSourceStack source = context.getSource();
                                                    ServerPlayer player = source.getPlayerOrException();
                                                    String orgItemID = StringArgumentType.getString(context, "itemID");
                                                    String itemIDStr = ItemUtilities.getNormalizedItemID(orgItemID);
                                                    if (itemIDStr == null) {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getInvalidItemIDMessage(orgItemID));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    ItemID itemID = new ItemID(itemIDStr);
                                                    if (ServerMarket.hasItem(itemID)) {
                                                        ServerMarket.removeTradingItem(itemID);
                                                        // Notify all serverPlayers
                                                        PlayerUtilities.printToClientConsole(StockMarketTextMessages.getMarketplaceDeletedMessage(itemIDStr));
                                                    } else {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getMarketplaceNotExistingMessage(itemIDStr));
                                                    }
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                        .then(Commands.literal("open")
                                                .executes(context -> {
                                                    CommandSourceStack source = context.getSource();
                                                    ServerPlayer player = source.getPlayerOrException();
                                                    String orgItemID = StringArgumentType.getString(context, "itemID");
                                                    String itemIDStr = ItemUtilities.getNormalizedItemID(orgItemID);
                                                    if (itemIDStr == null) {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getInvalidItemIDMessage(orgItemID));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    ItemID itemID = new ItemID(itemIDStr);
                                                    if (!ServerMarket.hasItem(itemID)) {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getMarketplaceNotExistingMessage(itemIDStr));
                                                    } else {
                                                        ServerMarket.setMarketOpen(itemID, true);
                                                        // Notify all serverPlayers
                                                        PlayerUtilities.printToClientConsole(StockMarketTextMessages.getMarketplaceIsNowOpenMessage(itemIDStr));
                                                    }
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                        .then(Commands.literal("close")
                                                .executes(context -> {
                                                    CommandSourceStack source = context.getSource();
                                                    ServerPlayer player = source.getPlayerOrException();
                                                    String orgItemID = StringArgumentType.getString(context, "itemID");
                                                    String itemIDStr = ItemUtilities.getNormalizedItemID(orgItemID);
                                                    if (itemIDStr == null) {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getInvalidItemIDMessage(orgItemID));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    ItemID itemID = new ItemID(itemIDStr);
                                                    if (!ServerMarket.hasItem(itemID)) {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getMarketplaceNotExistingMessage(itemIDStr));
                                                    } else {
                                                        ServerMarket.setMarketOpen(itemID, false);
                                                        // Notify all serverPlayers
                                                        PlayerUtilities.printToClientConsole(StockMarketTextMessages.getMarketplaceIsNowClosedMessage(itemIDStr));
                                                    }
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                        .then(Commands.literal("currentPrice")
                                                .executes(context -> {
                                                    CommandSourceStack source = context.getSource();
                                                    ServerPlayer player = source.getPlayerOrException();
                                                    String rawItemID = StringArgumentType.getString(context, "itemID");
                                                    String itemIDStr = ItemUtilities.getNormalizedItemID(rawItemID);
                                                    if (itemIDStr == null) {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getInvalidItemIDMessage(rawItemID));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    ItemID itemID = new ItemID(itemIDStr);
                                                    if (!ServerMarket.hasItem(itemID)) {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getMarketplaceNotExistingMessage(itemIDStr));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    int price = ServerMarket.getPrice(itemID);
                                                    PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getCurrentPriceOfMessage(itemIDStr, price));
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                        .then(Commands.literal("resetPriceChart")
                                                .executes(context -> {
                                                    CommandSourceStack source = context.getSource();
                                                    ServerPlayer player = source.getPlayerOrException();
                                                    String rawItemID = StringArgumentType.getString(context, "itemID");
                                                    String itemIDStr = ItemUtilities.getNormalizedItemID(rawItemID);
                                                    if (itemIDStr == null) {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getInvalidItemIDMessage(rawItemID));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    ItemID itemID = new ItemID(itemIDStr);
                                                    if (!ServerMarket.hasItem(itemID)) {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getMarketplaceNotExistingMessage(itemIDStr));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    ServerMarket.resetPriceChart(itemID);
                                                    //PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getCurrentPriceOfMessage(itemIDStr, price));
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )

                        )
                        .then(Commands.literal("save")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player = source.getPlayerOrException();

                                    StockMarketMod.SERVER_DATA_HANDLER.saveAllAsync().thenAccept(success -> {
                                        if(success)
                                            PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getStockMarketDataSavedMessage());
                                        else
                                            PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getStockMarketDataSaveFailedMessage());
                                    });


                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(Commands.literal("load")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player = source.getPlayerOrException();

                                    if(StockMarketMod.SERVER_DATA_HANDLER.loadAll())
                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getStockMarketDataLoadedMessage());
                                    else
                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getStockMarketDataLoadFailedMessage());

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(Commands.literal("closeAllMarkets")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player = source.getPlayerOrException();

                                    ServerMarket.setAllMarketsOpen(false);
                                    // Notify all serverPlayers
                                    PlayerUtilities.printToClientConsole(StockMarketTextMessages.getMarketplaceIsNowClosedAllMessage());

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(Commands.literal("openAllMarkets")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player = source.getPlayerOrException();

                                    ServerMarket.setAllMarketsOpen(true);
                                    // Notify all serverPlayers
                                    PlayerUtilities.printToClientConsole(StockMarketTextMessages.getMarketplaceIsNowOpenAllMessage());

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
        );
    }
/*
    private static void bot_set_setting(ServerPlayer executor, ServerVolatilityBot.Settings.Type settingsType, String itemID, double value)
    {
        ServerVolatilityBot bot = bot_set_setting_checkParams(executor, settingsType, itemID);
        if (bot == null)
            return;
        switch (settingsType) {
            case ENABLED:
            {
                boolean enabled = value > 0.5 && value < 1.5;
                bot.setEnabled(enabled);
                PlayerUtilities.printToClientConsole(executor, StockMarketTextMessages.getBotSettingEnabledMessage(enabled));
                break;
            }
            case MAX_ORDER_COUNT:
            {
                bot.setMaxOrderCount((int)value);
                PlayerUtilities.printToClientConsole(executor, StockMarketTextMessages.getBotSettingMaxOrderCountMessage((int)value));
                break;
            }
            case VOLUME_RANDOMNESS:
            {
                bot.setVolumeRandomness(value);
                PlayerUtilities.printToClientConsole(executor, StockMarketTextMessages.getBotSettingVolumeRandomnessMessage(value));
                break;
            }
            case VOLUME_SCALE:
            {
                bot.setVolumeScale(value);
                PlayerUtilities.printToClientConsole(executor, StockMarketTextMessages.getBotSettingVolumeScaleMessage(value));
                break;
            }
            case VOLUME_SPREAD:
            {
                bot.setVolumeSpread(value);
                PlayerUtilities.printToClientConsole(executor, StockMarketTextMessages.getBotSettingVolumeSpreadMessage(value));
                break;
            }
            case UPDATE_INTERVAL:
            {
                bot.setUpdateInterval((long)value);
                PlayerUtilities.printToClientConsole(executor, StockMarketTextMessages.getBotSettingUpdateIntervalMessage((long)value));
                break;
            }
            case VOLATILITY:
            {
                bot.setVolatility(value);
                PlayerUtilities.printToClientConsole(executor, StockMarketTextMessages.getBotSettingVolatilityMessage(value));
                break;
            }
            case ORDER_RANDOMNESS:
            {
                bot.setOrderRandomness(value);
                PlayerUtilities.printToClientConsole(executor, StockMarketTextMessages.getBotSettingOrderRandomnessMessage(value));
                break;
            }
            case INTEGRATED_ERROR:
            {
                bot.setintegratedError(value);
                PlayerUtilities.printToClientConsole(executor, StockMarketTextMessages.getBotSettingIntegratedMessage(value));
                break;
            }
            case TARGET_ITEM_BALANCE:
            {
                bot.setTargetItemBalance((long)value);
                PlayerUtilities.printToClientConsole(executor, StockMarketTextMessages.getBotSettingTargetItemBalanceMessage((long)value));
                break;
            }
            case TIMER_VOLATILITY_MILLIS:
            {
                bot.setTimerMillis((long)value);
                PlayerUtilities.printToClientConsole(executor, StockMarketTextMessages.getBotSettingVolatilityTimerMessage((long)value));
                break;
            }
            case MIN_VOLATILITY_TIMER_MILLIS:
            {
                bot.setMinTimerMillis((long)value);
                PlayerUtilities.printToClientConsole(executor, StockMarketTextMessages.getBotSettingVolatilityMinTimerMessage((long)value));
                break;
            }
            case MAX_VOLATILITY_TIMER_MILLIS:
            {
                bot.setMaxTimerMillis((long)value);
                PlayerUtilities.printToClientConsole(executor, StockMarketTextMessages.getBotSettingVolatilityMaxTimerMessage((long)value));
                break;
            }
            case IMBALANCE_PRICE_RANGE:
            {
                bot.setImbalancePriceRange((int)value);
                PlayerUtilities.printToClientConsole(executor, StockMarketTextMessages.getBotSettingImbalancePriceRangeMessage((int)value));
                break;
            }
            case IMBALANCE_PRICE_CHANGE_FACTOR:
            {
                bot.setImbalancePriceChangeFactor(value);
                PlayerUtilities.printToClientConsole(executor, StockMarketTextMessages.getBotSettingImbalanceChangeFactorMessage(value));
                break;
            }
            case IMBALANCE_PRICE_CHANGE_QUAD_FACTOR:
            {
                bot.setImbalancePriceChangeQuadFactor(value);
                PlayerUtilities.printToClientConsole(executor, StockMarketTextMessages.getBotSettingImbalanceChangeQuadFactorMessage(value));
                break;
            }
            case PID_P:
            {
                bot.setPidP(value);
                PlayerUtilities.printToClientConsole(executor, StockMarketTextMessages.getBotSettingPIDPSetMessage(value));
                break;
            }
            case PID_I:
            {
                bot.setPidI(value);
                PlayerUtilities.printToClientConsole(executor, StockMarketTextMessages.getBotSettingPIDISetMessage(value));
                break;
            }
            case PID_D:
            {
                bot.setPidD(value);
                PlayerUtilities.printToClientConsole(executor, StockMarketTextMessages.getBotSettingPIDDSetMessage(value));
                break;
            }
            case PID_I_BOUNDS:
            {
                bot.setPidIBound(value);
                PlayerUtilities.printToClientConsole(executor, StockMarketTextMessages.getBotSettingPIDIBoundsSetMessage(value));
                break;
            }
            default:
                StockMarketMod.LOGGER.error("Unknown/Unhandled setting type: \""+settingsType+"\" für bot settings set command");
                break;
        }
    }
    private static ServerVolatilityBot bot_set_setting_checkParams(ServerPlayer executor, ServerVolatilityBot.Settings.Type settingsType, String itemIDStr)
    {
        String orgItemID = itemIDStr;
        itemIDStr = ItemUtilities.getNormalizedItemID(orgItemID);
        if (itemIDStr == null) {
            PlayerUtilities.printToClientConsole(executor, StockMarketTextMessages.getInvalidItemIDMessage(orgItemID));
            return null;
        }

        ItemID itemID = new ItemID(itemIDStr);
        ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
        if (bot instanceof ServerVolatilityBot volatilityBot) {
            return volatilityBot;
        }
        PlayerUtilities.printToClientConsole(executor, StockMarketTextMessages.getBotNotExistMessage(itemIDStr));
        return null;
    }*/
}

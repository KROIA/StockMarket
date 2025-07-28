package net.kroia.stockmarket.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.kroia.modutilities.PlayerUtilities;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.networking.packet.server_sender.update.OpenScreenPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncTradeItemsPacket;
import net.kroia.stockmarket.util.StockMarketTextMessages;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public class StockMarketCommands {
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }
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
                                .requires(StockMarketCommands::isPlayerAdmin)
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                        .executes(context -> {
                                            CommandSourceStack source = context.getSource();
                                            ServerPlayer player = source.getPlayerOrException();
                                            int seconds = IntegerArgumentType.getInteger(context, "seconds");
                                            if(seconds < 0)
                                                return Command.SINGLE_SUCCESS; // Do not allow negative values
                                            BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.SHIFT_PRICE_CANDLE_INTERVAL_MS.set(seconds * 1000L);
                                            BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.setShiftPriceCandleIntervalMS(seconds * 1000L);
                                            // Execute the command on the server_sender
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        /*.then(Commands.literal("createDefaultBots")
                                .requires(StockMarketCommands::isPlayerAdmin)
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player = source.getPlayerOrException();
                                    BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.createDefaultBots();
                                    player.sendSystemMessage(Component.literal("Default bots created"));

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(Commands.literal("createDefaultBots")
                                .requires(StockMarketCommands::isPlayerAdmin)
                                .then(Commands.argument("category", StringArgumentType.string()).suggests((context, builder) -> {
                                                    List<String> suggestions = BACKEND_INSTANCES.SERVER_DATA_HANDLER.getDefaultBotSettingsFileNames();
                                                    for (String suggestion : suggestions) {
                                                        builder.suggest("\"" + suggestion + "\"");
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> {
                                                    CommandSourceStack source = context.getSource();
                                                    ServerPlayer player = source.getPlayerOrException();
                                                    String category = StringArgumentType.getString(context, "category");
                                                    if(BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.createDefaultBots(category))
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
                        .then(Commands.literal("createDefaultBot")
                                .requires(StockMarketCommands::isPlayerAdmin)
                                .then(Commands.argument("itemID", StringArgumentType.string()).suggests((context, builder) -> {
                                                    Set<ItemID> suggestions = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET_BOT.getBotBuilder().keySet();
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
                                                    if(BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.createDefaultBot(itemID))
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
                                .requires(StockMarketCommands::isPlayerAdmin)
                                .then(Commands.argument("category", StringArgumentType.string()).suggests((context, builder) -> {
                                                    List<String> suggestions = BACKEND_INSTANCES.SERVER_DATA_HANDLER.getDefaultBotSettingsFileNames();
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
                                                        BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.removeAllTradingItems();
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getAllMarketsRemovedMessage());
                                                    }
                                                    else {
                                                        if (BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.removeTradingItems(category)) {
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
                                            BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.cancelAllOrders(player.getUUID());

                                            return Command.SINGLE_SUCCESS;
                                        })
                                        .then(Commands.argument("itemID", StringArgumentType.string()).suggests((context, builder) -> {
                                                            ArrayList<ItemID> suggestions = BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getTradeItemIDs();
                                                            for (ItemID suggestion : suggestions) {
                                                                builder.suggest("\"" + suggestion.getName() + "\"");
                                                            }
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(context -> {
                                                            CommandSourceStack source = context.getSource();
                                                            ServerPlayer player = source.getPlayerOrException();
                                                            ItemID itemID = new ItemID(StringArgumentType.getString(context, "itemID"));
                                                            BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.cancelAllOrders(player.getUUID(), itemID);

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
                                                .requires(StockMarketCommands::isPlayerAdmin)
                                                .then(Commands.literal("cancelAll")
                                                        .executes(context -> {
                                                            CommandSourceStack source = context.getSource();
                                                            String username = StringArgumentType.getString(context, "username");
                                                            BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.cancelAllOrders(ServerPlayerList.getPlayerUUID(username));
                                                            return Command.SINGLE_SUCCESS;
                                                        })
                                                        .then(Commands.argument("itemID", StringArgumentType.string()).suggests((context, builder) -> {
                                                                            ArrayList<ItemID> suggestions = BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getTradeItemIDs();
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
                                                                            BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.cancelAllOrders(ServerPlayerList.getPlayerUUID(username), itemID);

                                                                            return Command.SINGLE_SUCCESS;
                                                                        })
                                                        )
                                                )

                                )
                        )
                        .then(Commands.literal("BotSettingsGUI")
                                .requires(StockMarketCommands::isPlayerAdmin)
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if(player != null) {
                                        ArrayList<ItemID> suggestions = BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getTradeItemIDs();
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
                        )*/
                        .then(Commands.literal("ManagementGUI")
                                .requires(StockMarketCommands::isPlayerAdmin)
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if(player != null) {
                                        SyncTradeItemsPacket.sendPacket(player);
                                        OpenScreenPacket.sendPacket(player, OpenScreenPacket.ScreenType.STOCKMARKET_MANAGEMENT);
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        /*.then(Commands.literal("resetAllPriceCharts")
                                .requires(StockMarketCommands::isPlayerAdmin)
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if(player != null) {
                                        BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.resetPriceChart();
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(Commands.argument("itemID", StringArgumentType.string()).suggests((context, builder) -> {
                                            ArrayList<ItemID> suggestions = BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getTradeItemIDs();
                                            for (ItemID suggestion : suggestions) {
                                                builder.suggest("\"" + suggestion.getName() + "\"");
                                            }
                                            return builder.buildFuture();
                                        })
                                        .requires(StockMarketCommands::isPlayerAdmin)
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
                                                            TradingPair pair = new TradingPair(itemID, BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getDefaultCurrencyItemID());
                                                            ServerMarket market = BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getMarket(pair);
                                                            if(market == null) {
                                                                PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getMarketplaceNotExistingMessage(itemIDStr));
                                                                return Command.SINGLE_SUCCESS; // No market for this item, nothing to do
                                                            }

                                                            if (!market.hasVolatilityBot()) {
                                                                ServerVolatilityBot.Settings settings = new ServerVolatilityBot.Settings();
                                                                settings.enabled = false; // Default to enabled
                                                                market.createVolatilityBot(settings);
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
                                                            TradingPair pair = new TradingPair(itemID, BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getDefaultCurrencyItemID());
                                                            ServerMarket market = BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getMarket(pair);
                                                            if(market == null) {
                                                                PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getBotNotExistMessage(itemIDStr));
                                                                return Command.SINGLE_SUCCESS; // No market for this item, nothing to do
                                                            }

                                                            if (!market.hasVolatilityBot()) {
                                                                PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getBotNotExistMessage(itemIDStr));
                                                            } else {
                                                                if(market.destroyVolatilityBot())
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
                                                    TradingPair pair = new TradingPair(itemID, BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getDefaultCurrencyItemID());
                                                    if (BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.marketExists(pair)) {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getMarketplaceAlreadyExistingMessage(itemIDStr));
                                                    } else {
                                                        if(BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.createMarket(itemID, 0))
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
                                                    if (BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.hasItem(itemID)) {
                                                        BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.removeTradingItem(itemID);
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
                                                    if (!BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.hasItem(itemID)) {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getMarketplaceNotExistingMessage(itemIDStr));
                                                    } else {
                                                        BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.setMarketOpen(itemID, true);
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
                                                    if (!BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.hasItem(itemID)) {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getMarketplaceNotExistingMessage(itemIDStr));
                                                    } else {
                                                        BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.setMarketOpen(itemID, false);
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
                                                    if (!BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.hasItem(itemID)) {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getMarketplaceNotExistingMessage(itemIDStr));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    int price = BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getPrice(itemID);
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
                                                    if (!BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.hasItem(itemID)) {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getMarketplaceNotExistingMessage(itemIDStr));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.resetPriceChart(itemID);
                                                    //PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getCurrentPriceOfMessage(itemIDStr, price));
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )

                        )*/
                        .then(Commands.literal("save")
                                .requires(StockMarketCommands::isPlayerAdmin)
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player = source.getPlayerOrException();

                                    BACKEND_INSTANCES.SERVER_DATA_HANDLER.saveAllAsync().thenAccept(success -> {
                                        if(success)
                                            PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getStockMarketDataSavedMessage());
                                        else
                                            PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getStockMarketDataSaveFailedMessage());
                                    });


                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(Commands.literal("load")
                                .requires(StockMarketCommands::isPlayerAdmin)
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player = source.getPlayerOrException();

                                    if(BACKEND_INSTANCES.SERVER_DATA_HANDLER.loadAll())
                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getStockMarketDataLoadedMessage());
                                    else
                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getStockMarketDataLoadFailedMessage());

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(Commands.literal("closeAllMarkets")
                                .requires(StockMarketCommands::isPlayerAdmin)
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player = source.getPlayerOrException();

                                    BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.setAllMarketsOpen(false);
                                    // Notify all serverPlayers
                                    PlayerUtilities.printToClientConsole(StockMarketTextMessages.getMarketplaceIsNowClosedAllMessage());

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(Commands.literal("openAllMarkets")
                                .requires(StockMarketCommands::isPlayerAdmin)
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player = source.getPlayerOrException();

                                    BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.setAllMarketsOpen(true);
                                    // Notify all serverPlayers
                                    PlayerUtilities.printToClientConsole(StockMarketTextMessages.getMarketplaceIsNowOpenAllMessage());

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
        );
    }

    private static boolean isPlayerAdmin(CommandSourceStack source)
    {
        return source.hasPermission(BACKEND_INSTANCES.SERVER_SETTINGS.UTILITIES.ADMIN_PERMISSION_LEVEL.get());
    }
}

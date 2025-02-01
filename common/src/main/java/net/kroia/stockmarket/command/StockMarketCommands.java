package net.kroia.stockmarket.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.kroia.banksystem.util.BankSystemDataHandler;
import net.kroia.banksystem.util.BankSystemTextMessages;
import net.kroia.modutilities.ItemUtilities;
import net.kroia.modutilities.PlayerUtilities;
import net.kroia.stockmarket.StockMarketClientHooks;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModSettings;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.market.server.bot.ServerTradingBot;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.kroia.stockmarket.networking.packet.server_sender.update.OpenScreenPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncBotSettingsPacket;
import net.kroia.stockmarket.util.ServerPlayerList;
import net.kroia.stockmarket.util.StockMarketDataHandler;
import net.kroia.stockmarket.util.StockMarketTextMessages;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class StockMarketCommands {
    // Method to register commands
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {


        // /StockMarket setPriceCandleTimeInterval <seconds>                            - Set the interval for the price candles. (Each candle will represent this amount of time)
        // /StockMarket createDefaultBots                                               - Create default bots
        // /StockMarket createDefaultBot <itemID>                                       - Create a default bot for that item if presets are available
        // /StockMarket order cancelAll                                                 - Cancel all orders
        // /StockMarket order cancelAll <itemID>                                        - Cancel all orders of an item
        // /StockMarket order <username> cancelAll                                      - Cancel all orders of a player
        // /StockMarket order <username> cancelAll <itemID>                             - Cancel all orders of a player for an item
        // /StockMarket BotSettingsGUI                                                  - Open the settings GUI for the market bots
        // /StockMarket ManagementGUI                                                   - Open the management GUI to create and remove trading items
        // /StockMarket <itemID> bot settings get                                       - Get bot settings
        // /StockMarket <itemID> bot settings set enabled                               - Enable bot
        // /StockMarket <itemID> bot settings set disabled                              - Disable bot
        // /StockMarket <itemID> bot settings set volatility <volatility>               - Set volatility
        // /StockMarket <itemID> bot settings set orderRandomness <randomness>          - Set scale for random market orders
        // /StockMarket <itemID> bot settings set targetPriceRange <priceRange>         - Set imbalance price range
        // /StockMarket <itemID> bot settings set targetItemBalance <balance>           - Set target item balance
        // /StockMarket <itemID> bot settings set maxOrderCount <orderCount>            - Set max order count
        // /StockMarket <itemID> bot settings set volumeScale <volumeScale>             - Set volume scale
        // /StockMarket <itemID> bot settings set volumeSpread <volumeSpread>           - Set volume spread
        // /StockMarket <itemID> bot settings set volumeRandomness <volumeRandomness>   - Set volume randomness
        // /StockMarket <itemID> bot settings set timer <timer>                         - Set timer
        // /StockMarket <itemID> bot settings set minTimer <timer>                      - Set min timer
        // /StockMarket <itemID> bot settings set maxTimer <timer>                      - Set max timer
        // /StockMarket <itemID> bot settings set pidP <pidP>                           - Set PID P
        // /StockMarket <itemID> bot settings set pidI <pidI>                           - Set PID I
        // /StockMarket <itemID> bot settings set pidD <pidD>                           - Set PID D
        // /StockMarket <itemID> bot settings set pidIntegratedError <pidIntegratedError> - Set the current integrated error of the PID controller
        // /StockMarket <itemID> bot create                                             - Create bot
        // /StockMarket <itemID> bot remove                                             - Remove bot
        // /StockMarket <itemID> create                                                 - Create marketplace
        // /StockMarket <itemID> remove                                                 - Remove marketplace
        // /StockMarket <itemID> open                                                   - Open the marketplace for trading
        // /StockMarket <itemID> close                                                  - Close the marketplace for trading
        // /StockMarket <itemID> currentPrice                                           - Get current price
        // /StockMarket save                                                            - Save market data
        // /StockMarket load                                                            - Load market data

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
                                            ServerMarket.shiftPriceHistoryInterval = seconds * 1000L;
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
                        .then(Commands.literal("createDefaultBot")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("itemID", StringArgumentType.string()).suggests((context, builder) -> {
                                                    Set<String> suggestions = StockMarketModSettings.MarketBot.getBotBuilder().keySet();
                                                    for (String suggestion : suggestions) {
                                                        builder.suggest("\"" + suggestion + "\"");
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> {
                                                    CommandSourceStack source = context.getSource();
                                                    ServerPlayer player = source.getPlayerOrException();
                                                    String itemID = StringArgumentType.getString(context, "itemID");
                                                    if(ServerMarket.createDefaultBot(itemID))
                                                    {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getDefaultBotCreatedMessage(itemID));
                                                    }
                                                    else
                                                    {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getCanNotCreateDefaultBotMessage(itemID));
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
                                                            ArrayList<String> suggestions = ServerMarket.getTradeItemIDs();
                                                            for (String suggestion : suggestions) {
                                                                builder.suggest("\"" + suggestion + "\"");
                                                            }
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(context -> {
                                                            CommandSourceStack source = context.getSource();
                                                            ServerPlayer player = source.getPlayerOrException();
                                                            String itemID = StringArgumentType.getString(context, "itemID");
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
                                                                            ArrayList<String> suggestions = ServerMarket.getTradeItemIDs();
                                                                            for (String suggestion : suggestions) {
                                                                                builder.suggest("\"" + suggestion + "\"");
                                                                            }
                                                                            return builder.buildFuture();
                                                                        })
                                                                        .executes(context -> {
                                                                            CommandSourceStack source = context.getSource();
                                                                            ServerPlayer player = source.getPlayerOrException();
                                                                            String itemID = StringArgumentType.getString(context, "itemID");
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
                                        ArrayList<String> suggestions = ServerMarket.getTradeItemIDs();
                                        if(!suggestions.isEmpty()) {
                                            String itemID = suggestions.get(0);
                                            SyncBotSettingsPacket.sendPacket(player, itemID, ServerMarket.getBotUserUUID());
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
                        .then(Commands.argument("itemID", StringArgumentType.string()).suggests((context, builder) -> {
                                            ArrayList<String> suggestions = ServerMarket.getTradeItemIDs();
                                            for (String suggestion : suggestions) {
                                                builder.suggest("\"" + suggestion + "\"");
                                            }
                                            return builder.buildFuture();
                                        })
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.literal("bot")
                                                .then(Commands.literal("settings")
                                                        .then(Commands.literal("get")
                                                                .executes(context -> {
                                                                    CommandSourceStack source = context.getSource();
                                                                    ServerPlayer player = source.getPlayerOrException();
                                                                    String itemID = ItemUtilities.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                    if (itemID == null) {
                                                                        player.sendSystemMessage(Component.literal("Item not found"));
                                                                        return Command.SINGLE_SUCCESS;
                                                                    }
                                                                    ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                                    if (bot instanceof ServerVolatilityBot) {
                                                                        ServerVolatilityBot volatilityBot = (ServerVolatilityBot) bot;
                                                                        StringBuilder msg = new StringBuilder();
                                                                        msg.append("StockMarketBot settings for item: " + itemID + "\n");
                                                                        msg.append("| Enabled: " + (volatilityBot.isEnabled() ? "Yes" : "No") + "\n");
                                                                        msg.append("| Volatility: " + volatilityBot.getVolatility() + "\n");
                                                                        msg.append("| Order randomness: " + volatilityBot.getOrderRandomness() + "\n");
                                                                        msg.append("| Target Price: " + volatilityBot.getTargetPrice() + "\n");
                                                                        msg.append("| Target Price Range: " + volatilityBot.getImbalancePriceRange() + "\n");
                                                                        msg.append("| Price change linear fac: " + volatilityBot.getImbalancePriceChangeFactor() + "\n");
                                                                        msg.append("| Price change quad fac: " + volatilityBot.getImbalancePriceChangeQuadFactor() + "\n");
                                                                        msg.append("| Target Item Balance: " + volatilityBot.getTargetItemBalance() + "\n");
                                                                        msg.append("| Max Order count: " + volatilityBot.getMaxOrderCount() + "\n");
                                                                        msg.append("| Volume scale: " + volatilityBot.getVolumeScale() + "\n");
                                                                        msg.append("| Volume spread: " + volatilityBot.getVolumeSpread() + "\n");
                                                                        msg.append("| Volume randomness: " + volatilityBot.getVolumeRandomness() + "\n");
                                                                        msg.append("| Timer: " + volatilityBot.getTimerMillis() + "ms\n");
                                                                        msg.append("| Min Timer: " + volatilityBot.getMinTimerMillis() + "ms\n");
                                                                        msg.append("| Max Timer: " + volatilityBot.getMaxTimerMillis() + "ms\n");
                                                                        msg.append("| Update Interval: " + volatilityBot.getUpdateInterval() + "ms\n");
                                                                        msg.append("| PID P: " + volatilityBot.getPidP() + "\n");
                                                                        msg.append("| PID D: " + volatilityBot.getPidD() + "\n");
                                                                        msg.append("| PID I: " + volatilityBot.getPidI() + "\n");
                                                                        msg.append("| PID I Bounds: " + volatilityBot.getPidIBound() + "\n");

                                                                        player.sendSystemMessage(Component.literal(msg.toString()));
                                                                    } else {
                                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getBotNotExistMessage(itemID));
                                                                    }
                                                                    // Execute the command on the server_sender
                                                                    return Command.SINGLE_SUCCESS;
                                                                })
                                                        )
                                                        .then(Commands.literal("set")
                                                                .then(Commands.literal("enabled")
                                                                        .executes(context -> {
                                                                            CommandSourceStack source = context.getSource();
                                                                            ServerPlayer player = source.getPlayerOrException();
                                                                            String itemID = StringArgumentType.getString(context, "itemID");
                                                                            bot_set_setting(player, ServerVolatilityBot.Settings.Type.ENABLED, itemID, 1.0);
                                                                            // Execute the command on the server_sender
                                                                            return Command.SINGLE_SUCCESS;
                                                                        })
                                                                )
                                                                .then(Commands.literal("disabled")
                                                                        .executes(context -> {
                                                                            CommandSourceStack source = context.getSource();
                                                                            ServerPlayer player = source.getPlayerOrException();
                                                                            String itemID = StringArgumentType.getString(context, "itemID");
                                                                            bot_set_setting(player, ServerVolatilityBot.Settings.Type.ENABLED, itemID, 0.0);
                                                                            return Command.SINGLE_SUCCESS;
                                                                        })
                                                                )
                                                                .then(Commands.literal("volatility")
                                                                        .then(Commands.argument("volatility", IntegerArgumentType.integer(0))
                                                                                .executes(context -> {
                                                                                    CommandSourceStack source = context.getSource();
                                                                                    ServerPlayer player = source.getPlayerOrException();
                                                                                    String itemID = StringArgumentType.getString(context, "itemID");
                                                                                    int volatility = IntegerArgumentType.getInteger(context, "volatility");
                                                                                    bot_set_setting(player, ServerVolatilityBot.Settings.Type.VOLATILITY, itemID, volatility);
                                                                                    // Execute the command on the server_sender
                                                                                    return Command.SINGLE_SUCCESS;
                                                                                })
                                                                        )
                                                                )
                                                                .then(Commands.literal("orderRandomness")
                                                                        .then(Commands.argument("randomness", DoubleArgumentType.doubleArg(0))
                                                                                .executes(context -> {
                                                                                    CommandSourceStack source = context.getSource();
                                                                                    ServerPlayer player = source.getPlayerOrException();
                                                                                    String itemID = StringArgumentType.getString(context, "itemID");
                                                                                    double randomness = DoubleArgumentType.getDouble(context, "randomness");
                                                                                    bot_set_setting(player, ServerVolatilityBot.Settings.Type.ORDER_RANDOMNESS, itemID, randomness);
                                                                                    // Execute the command on the server_sender
                                                                                    return Command.SINGLE_SUCCESS;
                                                                                })
                                                                        )
                                                                )
                                                                .then(Commands.literal("imbalancePriceChangeFactorLinear")
                                                                        .then(Commands.argument("factor", DoubleArgumentType.doubleArg(0))
                                                                                .executes(context -> {
                                                                                    CommandSourceStack source = context.getSource();
                                                                                    ServerPlayer player = source.getPlayerOrException();
                                                                                    String itemID = StringArgumentType.getString(context, "itemID");
                                                                                    double factor = DoubleArgumentType.getDouble(context, "factor");
                                                                                    bot_set_setting(player, ServerVolatilityBot.Settings.Type.IMBALANCE_PRICE_CHANGE_FACTOR, itemID, factor);
                                                                                    // Execute the command on the server_sender
                                                                                    return Command.SINGLE_SUCCESS;
                                                                                })
                                                                        )
                                                                )
                                                                .then(Commands.literal("imbalancePriceChangeFactorQuadratic")
                                                                        .then(Commands.argument("factor", DoubleArgumentType.doubleArg(0))
                                                                                .executes(context -> {
                                                                                    CommandSourceStack source = context.getSource();
                                                                                    ServerPlayer player = source.getPlayerOrException();
                                                                                    String itemID = StringArgumentType.getString(context, "itemID");
                                                                                    double factor = DoubleArgumentType.getDouble(context, "factor");
                                                                                    bot_set_setting(player, ServerVolatilityBot.Settings.Type.IMBALANCE_PRICE_CHANGE_QUAD_FACTOR, itemID, factor);
                                                                                    // Execute the command on the server_sender
                                                                                    return Command.SINGLE_SUCCESS;
                                                                                })
                                                                        )
                                                                )
                                                                .then(Commands.literal("targetItemBalance")
                                                                        .then(Commands.argument("balance", IntegerArgumentType.integer(1))
                                                                                .executes(context -> {
                                                                                    CommandSourceStack source = context.getSource();
                                                                                    ServerPlayer player = source.getPlayerOrException();
                                                                                    String itemID = StringArgumentType.getString(context, "itemID");
                                                                                    int balance = IntegerArgumentType.getInteger(context, "balance");
                                                                                    ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                                                    bot_set_setting(player, ServerVolatilityBot.Settings.Type.TARGET_ITEM_BALANCE, itemID, balance);
                                                                                    // Execute the command on the server_sender
                                                                                    return Command.SINGLE_SUCCESS;
                                                                                })
                                                                        )
                                                                )
                                                                .then(Commands.literal("targetPriceRange")
                                                                        .then(Commands.argument("priceRange", IntegerArgumentType.integer(1))
                                                                                .executes(context -> {
                                                                                    CommandSourceStack source = context.getSource();
                                                                                    ServerPlayer player = source.getPlayerOrException();
                                                                                    String itemID = StringArgumentType.getString(context, "itemID");
                                                                                    int range = IntegerArgumentType.getInteger(context, "priceRange");
                                                                                    bot_set_setting(player, ServerVolatilityBot.Settings.Type.IMBALANCE_PRICE_RANGE, itemID, range);
                                                                                    // Execute the command on the server_sender
                                                                                    return Command.SINGLE_SUCCESS;
                                                                                })
                                                                        )
                                                                )
                                                                .then(Commands.literal("maxOrderCount")
                                                                        .then(Commands.argument("orderCount", IntegerArgumentType.integer(2))
                                                                                .executes(context -> {
                                                                                    CommandSourceStack source = context.getSource();
                                                                                    ServerPlayer player = source.getPlayerOrException();
                                                                                    String itemID = StringArgumentType.getString(context, "itemID");
                                                                                    int amount = IntegerArgumentType.getInteger(context, "orderCount");
                                                                                    bot_set_setting(player, ServerVolatilityBot.Settings.Type.MAX_ORDER_COUNT, itemID, amount);
                                                                                    // Execute the command on the server_sender
                                                                                    return Command.SINGLE_SUCCESS;
                                                                                })
                                                                        )
                                                                )
                                                                .then(Commands.literal("volumeScale")
                                                                        .then(Commands.argument("volumeScale", DoubleArgumentType.doubleArg(0))
                                                                                .executes(context -> {
                                                                                    CommandSourceStack source = context.getSource();
                                                                                    ServerPlayer player = source.getPlayerOrException();
                                                                                    String itemID = StringArgumentType.getString(context, "itemID");
                                                                                    double scale = DoubleArgumentType.getDouble(context, "volumeScale");
                                                                                    bot_set_setting(player, ServerVolatilityBot.Settings.Type.VOLUME_SCALE, itemID, scale);
                                                                                    // Execute the command on the server_sender
                                                                                    return Command.SINGLE_SUCCESS;
                                                                                })
                                                                        )
                                                                )
                                                                .then(Commands.literal("volumeSpread")
                                                                        .then(Commands.argument("volumeSpread", DoubleArgumentType.doubleArg(0.000001))
                                                                                .executes(context -> {
                                                                                    CommandSourceStack source = context.getSource();
                                                                                    ServerPlayer player = source.getPlayerOrException();
                                                                                    String itemID = StringArgumentType.getString(context, "itemID");
                                                                                    double spread = DoubleArgumentType.getDouble(context, "volumeSpread");
                                                                                    bot_set_setting(player, ServerVolatilityBot.Settings.Type.VOLUME_SPREAD, itemID, spread);
                                                                                    // Execute the command on the server_sender
                                                                                    return Command.SINGLE_SUCCESS;
                                                                                })
                                                                        )
                                                                )
                                                                .then(Commands.literal("volumeRandomness")
                                                                        .then(Commands.argument("volumeRandomness", DoubleArgumentType.doubleArg(0.0))
                                                                                .executes(context -> {
                                                                                    CommandSourceStack source = context.getSource();
                                                                                    ServerPlayer player = source.getPlayerOrException();
                                                                                    String itemID = StringArgumentType.getString(context, "itemID");
                                                                                    double randomness = DoubleArgumentType.getDouble(context, "volumeRandomness");
                                                                                    bot_set_setting(player, ServerVolatilityBot.Settings.Type.VOLUME_RANDOMNESS, itemID, randomness);
                                                                                    // Execute the command on the server_sender
                                                                                    return Command.SINGLE_SUCCESS;
                                                                                })
                                                                        )
                                                                )
                                                                .then(Commands.literal("timer")
                                                                        .then(Commands.argument("timeMS", LongArgumentType.longArg(0))
                                                                                .executes(context -> {
                                                                                    CommandSourceStack source = context.getSource();
                                                                                    ServerPlayer player = source.getPlayerOrException();
                                                                                    String itemID = StringArgumentType.getString(context, "itemID");
                                                                                    long timer = LongArgumentType.getLong(context, "timeMS");
                                                                                    bot_set_setting(player, ServerVolatilityBot.Settings.Type.TIMER_VOLATILITY_MILLIS, itemID, timer);
                                                                                    // Execute the command on the server_sender
                                                                                    return Command.SINGLE_SUCCESS;
                                                                                })
                                                                        )
                                                                )
                                                                .then(Commands.literal("minTimer")
                                                                        .then(Commands.argument("timeMS", LongArgumentType.longArg(1))
                                                                                .executes(context -> {
                                                                                    CommandSourceStack source = context.getSource();
                                                                                    ServerPlayer player = source.getPlayerOrException();
                                                                                    String itemID = StringArgumentType.getString(context, "itemID");
                                                                                    long timer = LongArgumentType.getLong(context, "timeMS");
                                                                                    bot_set_setting(player, ServerVolatilityBot.Settings.Type.MIN_VOLATILITY_TIMER_MILLIS, itemID, timer);
                                                                                    // Execute the command on the server_sender
                                                                                    return Command.SINGLE_SUCCESS;
                                                                                })
                                                                        )
                                                                )
                                                                .then(Commands.literal("maxTimer")
                                                                        .then(Commands.argument("timeMS", LongArgumentType.longArg(1))
                                                                                .executes(context -> {
                                                                                    CommandSourceStack source = context.getSource();
                                                                                    ServerPlayer player = source.getPlayerOrException();
                                                                                    String itemID = StringArgumentType.getString(context, "itemID");
                                                                                    long timer = LongArgumentType.getLong(context, "timeMS");
                                                                                    bot_set_setting(player, ServerVolatilityBot.Settings.Type.MAX_VOLATILITY_TIMER_MILLIS, itemID, timer);
                                                                                    // Execute the command on the server_sender
                                                                                    return Command.SINGLE_SUCCESS;
                                                                                })
                                                                        )
                                                                )
                                                                .then(Commands.literal("updateInterval")
                                                                        .then(Commands.argument("timeMS", LongArgumentType.longArg(1))
                                                                                .executes(context -> {
                                                                                    CommandSourceStack source = context.getSource();
                                                                                    ServerPlayer player = source.getPlayerOrException();
                                                                                    String itemID = StringArgumentType.getString(context, "itemID");
                                                                                    long timer = LongArgumentType.getLong(context, "timeMS");
                                                                                    bot_set_setting(player, ServerVolatilityBot.Settings.Type.UPDATE_INTERVAL, itemID, timer);
                                                                                    // Execute the command on the server_sender
                                                                                    return Command.SINGLE_SUCCESS;
                                                                                })
                                                                        )
                                                                )
                                                                .then(Commands.literal("pidP")
                                                                        .then(Commands.argument("pidP", DoubleArgumentType.doubleArg())
                                                                                .executes(context -> {
                                                                                    CommandSourceStack source = context.getSource();
                                                                                    ServerPlayer player = source.getPlayerOrException();
                                                                                    String itemID = StringArgumentType.getString(context, "itemID");
                                                                                    double pidP = DoubleArgumentType.getDouble(context, "pidP");
                                                                                    bot_set_setting(player, ServerVolatilityBot.Settings.Type.PID_P, itemID, pidP);
                                                                                    // Execute the command on the server_sender
                                                                                    return Command.SINGLE_SUCCESS;
                                                                                })
                                                                        )
                                                                )
                                                                .then(Commands.literal("pidI")
                                                                        .then(Commands.argument("pidI", DoubleArgumentType.doubleArg())
                                                                                .executes(context -> {
                                                                                    CommandSourceStack source = context.getSource();
                                                                                    ServerPlayer player = source.getPlayerOrException();
                                                                                    String itemID = StringArgumentType.getString(context, "itemID");
                                                                                    double pidI = DoubleArgumentType.getDouble(context, "pidI");
                                                                                    bot_set_setting(player, ServerVolatilityBot.Settings.Type.PID_I, itemID, pidI);
                                                                                    // Execute the command on the server_sender
                                                                                    return Command.SINGLE_SUCCESS;
                                                                                })
                                                                        )
                                                                )
                                                                .then(Commands.literal("pidD")
                                                                        .then(Commands.argument("pidD", DoubleArgumentType.doubleArg())
                                                                                .executes(context -> {
                                                                                    CommandSourceStack source = context.getSource();
                                                                                    ServerPlayer player = source.getPlayerOrException();
                                                                                    String itemID = StringArgumentType.getString(context, "itemID");
                                                                                    double pidD = DoubleArgumentType.getDouble(context, "pidD");
                                                                                    bot_set_setting(player, ServerVolatilityBot.Settings.Type.PID_D, itemID, pidD);
                                                                                    // Execute the command on the server_sender
                                                                                    return Command.SINGLE_SUCCESS;
                                                                                })
                                                                        )
                                                                )
                                                                .then(Commands.literal("pidIBounds")
                                                                        .then(Commands.argument("pidIBounds", DoubleArgumentType.doubleArg())
                                                                                .executes(context -> {
                                                                                    CommandSourceStack source = context.getSource();
                                                                                    ServerPlayer player = source.getPlayerOrException();
                                                                                    String itemID = StringArgumentType.getString(context, "itemID");
                                                                                    double pidIBounds = DoubleArgumentType.getDouble(context, "pidIBounds");
                                                                                    bot_set_setting(player, ServerVolatilityBot.Settings.Type.PID_I_BOUNDS, itemID, pidIBounds);
                                                                                    // Execute the command on the server_sender
                                                                                    return Command.SINGLE_SUCCESS;
                                                                                })
                                                                        )
                                                                )
                                                                .then(Commands.literal("pidIntegratedError")
                                                                        .then(Commands.argument("pidIntegratedError", DoubleArgumentType.doubleArg())
                                                                                .executes(context -> {
                                                                                    CommandSourceStack source = context.getSource();
                                                                                    ServerPlayer player = source.getPlayerOrException();
                                                                                    String itemID = StringArgumentType.getString(context, "itemID");
                                                                                    double pidIBounds = DoubleArgumentType.getDouble(context, "pidIntegratedError");
                                                                                    bot_set_setting(player, ServerVolatilityBot.Settings.Type.INTEGRATED_ERROR, itemID, pidIBounds);
                                                                                    // Execute the command on the server_sender
                                                                                    return Command.SINGLE_SUCCESS;
                                                                                })
                                                                        )
                                                                )
                                                        )

                                                )
                                                .then(Commands.literal("create")
                                                        .executes(context -> {
                                                            CommandSourceStack source = context.getSource();
                                                            ServerPlayer player = source.getPlayerOrException();
                                                            String orgItemID = StringArgumentType.getString(context, "itemID");
                                                            String itemID = ItemUtilities.getNormalizedItemID(orgItemID);
                                                            if (itemID == null) {
                                                                PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getInvalidItemIDMessage(orgItemID));
                                                                return Command.SINGLE_SUCCESS;
                                                            }
                                                            ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                            if (bot == null) {
                                                                ServerVolatilityBot volatilityBot = new ServerVolatilityBot();
                                                                volatilityBot.setEnabled(false);
                                                                ServerMarket.setTradingBot(itemID, volatilityBot);
                                                                PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getBotCreatedMessage(itemID));
                                                            } else {
                                                                PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getBotAlreadyExistMessage(itemID));
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
                                                            String itemID = ItemUtilities.getNormalizedItemID(orgItemID);
                                                            if (itemID == null) {
                                                                PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getInvalidItemIDMessage(orgItemID));
                                                                return Command.SINGLE_SUCCESS;
                                                            }
                                                            ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                            if (bot == null) {
                                                                PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getBotNotExistMessage(itemID));
                                                            } else {
                                                                ServerMarket.removeTradingBot(itemID);
                                                                PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getBotDeletedMessage(itemID));
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
                                                    String itemID = ItemUtilities.getNormalizedItemID(orgItemID);
                                                    if (itemID == null) {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getInvalidItemIDMessage(orgItemID));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    if (ServerMarket.hasItem(itemID)) {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getMarketplaceAlreadyExistingMessage(itemID));
                                                    } else {
                                                        if(ServerMarket.addTradeItem(itemID, 0))
                                                        {
                                                            // Notify all serverPlayers
                                                            PlayerUtilities.printToClientConsole(StockMarketTextMessages.getMarketplaceCreatedMessage(itemID));
                                                        }
                                                        else
                                                        {
                                                            PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getMarketplaceIsNotAllowedMessage(itemID));
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
                                                    String itemID = ItemUtilities.getNormalizedItemID(orgItemID);
                                                    if (itemID == null) {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getInvalidItemIDMessage(orgItemID));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    if (ServerMarket.hasItem(itemID)) {
                                                        ServerMarket.removeTradingItem(itemID);
                                                        // Notify all serverPlayers
                                                        PlayerUtilities.printToClientConsole(StockMarketTextMessages.getMarketplaceDeletedMessage(itemID));
                                                    } else {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getMarketplaceNotExistingMessage(itemID));
                                                    }
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                        .then(Commands.literal("open")
                                                .executes(context -> {
                                                    CommandSourceStack source = context.getSource();
                                                    ServerPlayer player = source.getPlayerOrException();
                                                    String orgItemID = StringArgumentType.getString(context, "itemID");
                                                    String itemID = ItemUtilities.getNormalizedItemID(orgItemID);
                                                    if (itemID == null) {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getInvalidItemIDMessage(orgItemID));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    if (!ServerMarket.hasItem(itemID)) {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getMarketplaceNotExistingMessage(itemID));
                                                    } else {
                                                        ServerMarket.setMarketOpen(itemID, true);
                                                        // Notify all serverPlayers
                                                        PlayerUtilities.printToClientConsole(StockMarketTextMessages.getMarketplaceIsNowOpenMessage(itemID));
                                                    }
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                        .then(Commands.literal("close")
                                                .executes(context -> {
                                                    CommandSourceStack source = context.getSource();
                                                    ServerPlayer player = source.getPlayerOrException();
                                                    String orgItemID = StringArgumentType.getString(context, "itemID");
                                                    String itemID = ItemUtilities.getNormalizedItemID(orgItemID);
                                                    if (itemID == null) {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getInvalidItemIDMessage(orgItemID));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    if (!ServerMarket.hasItem(itemID)) {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getMarketplaceNotExistingMessage(itemID));
                                                    } else {
                                                        ServerMarket.setMarketOpen(itemID, false);
                                                        // Notify all serverPlayers
                                                        PlayerUtilities.printToClientConsole(StockMarketTextMessages.getMarketplaceIsNowClosedMessage(itemID));
                                                    }
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                        .then(Commands.literal("currentPrice")
                                                .executes(context -> {
                                                    CommandSourceStack source = context.getSource();
                                                    ServerPlayer player = source.getPlayerOrException();
                                                    String rawItemID = StringArgumentType.getString(context, "itemID");
                                                    String itemID = ItemUtilities.getNormalizedItemID(rawItemID);
                                                    if (itemID == null) {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getInvalidItemIDMessage(rawItemID));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    if (!ServerMarket.hasItem(itemID)) {
                                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getMarketplaceNotExistingMessage(itemID));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    int price = ServerMarket.getPrice(itemID);
                                                    PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getCurrentPriceOfMessage(itemID, price));
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )

                        )
                        .then(Commands.literal("save")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player = source.getPlayerOrException();

                                    if(StockMarketDataHandler.saveAll())
                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getStockMarketDataSavedMessage());
                                    else
                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getStockMarketDataSaveFailedMessage());

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(Commands.literal("load")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player = source.getPlayerOrException();

                                    if(StockMarketDataHandler.loadAll())
                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getStockMarketDataLoadedMessage());
                                    else
                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getStockMarketDataLoadFailedMessage());

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
        );
    }

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
    private static ServerVolatilityBot bot_set_setting_checkParams(ServerPlayer executor, ServerVolatilityBot.Settings.Type settingsType, String itemID)
    {
        String orgItemID = itemID;
        itemID = ItemUtilities.getNormalizedItemID(orgItemID);
        if (itemID == null) {
            PlayerUtilities.printToClientConsole(executor, StockMarketTextMessages.getInvalidItemIDMessage(orgItemID));
            return null;
        }
        ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
        if (bot instanceof ServerVolatilityBot volatilityBot) {
            return volatilityBot;
        }
        PlayerUtilities.printToClientConsole(executor, StockMarketTextMessages.getBotNotExistMessage(itemID));
        return null;
    }
}

package net.kroia.stockmarket.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kroia.stockmarket.ModSettings;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.banking.BankUser;
import net.kroia.stockmarket.banking.bank.Bank;
import net.kroia.stockmarket.banking.bank.MoneyBank;
import net.kroia.stockmarket.banking.ServerBankManager;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.market.server.bot.ServerMarketMakerBot;
import net.kroia.stockmarket.market.server.bot.ServerTradingBot;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.kroia.stockmarket.util.ServerPlayerList;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class ModCommands {
    // Method to register commands
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // /money                               - Show balance
        // /money add <amount>                  - Add money to self
        // /money add <user> <amount>           - Add money to another player
        // /money set <amount>                  - Set money to self
        // /money set <user> <amount>           - Set money to another player
        // /money remove <amount>               - Remove money from self
        // /money remove <user> <amount>        - Remove money from another player
        // /money send <user> <amount>          - Send money to another player
        // /money circulation                   - Show money circulation of all players + bots
        dispatcher.register(
                Commands.literal("money")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            ServerPlayer player = source.getPlayerOrException();

                            // Execute the balance command on the server_sender
                            return showBalance(player);
                        })
                        .then(Commands.literal("add")
                                .requires(source -> source.hasPermission(2)) // Admin-only for adding money
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(context -> {
                                            CommandSourceStack source = context.getSource();
                                            ServerPlayer player = source.getPlayerOrException();

                                            // Get arguments
                                            String username = player.getName().getString();
                                            int amount = IntegerArgumentType.getInteger(context, "amount");

                                            // Execute the command on the server_sender
                                            return executeAddMoney(player, username, amount);
                                        })) // Add to self
                                .then(Commands.argument("username", StringArgumentType.string()).suggests((context, builder) -> {
                                                    //builder.suggest("\""+ ModSettings.MarketBot.USER_NAME +"\"");
                                                    Map<UUID, String> uuidToNameMap = ServerPlayerList.getUuidToNameMap();
                                                    for(String name : uuidToNameMap.values()) {

                                                        builder.suggest("\""+name+"\"");
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                        .executes(context -> {
                                                            CommandSourceStack source = context.getSource();
                                                            ServerPlayer player = source.getPlayerOrException();

                                                            // Get arguments
                                                            String username = StringArgumentType.getString(context, "username");
                                                            int amount = IntegerArgumentType.getInteger(context, "amount");

                                                            // Execute the command on the server_sender
                                                            return executeAddMoney(player, username, amount);
                                                        })
                                                )
                                )
                        )
                        .then(Commands.literal("set")
                                .requires(source -> source.hasPermission(2)) // Admin-only for adding money
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(context -> {
                                            CommandSourceStack source = context.getSource();
                                            ServerPlayer player = source.getPlayerOrException();

                                            // Get arguments
                                            String username = player.getName().getString();
                                            int amount = IntegerArgumentType.getInteger(context, "amount");

                                            // Execute the command on the server_sender
                                            return executeSetMoney(player, username, amount);
                                        })) // Add to self
                                .then(Commands.argument("username", StringArgumentType.string()).suggests((context, builder) -> {
                                                    //builder.suggest("\""+ ModSettings.MarketBot.USER_NAME +"\"");
                                                    Map<UUID, String> uuidToNameMap = ServerPlayerList.getUuidToNameMap();
                                                    for(String name : uuidToNameMap.values()) {

                                                        builder.suggest("\""+name+"\"");
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                        .executes(context -> {
                                                            CommandSourceStack source = context.getSource();
                                                            ServerPlayer player = source.getPlayerOrException();

                                                            // Get arguments
                                                            String username = StringArgumentType.getString(context, "username");
                                                            int amount = IntegerArgumentType.getInteger(context, "amount");

                                                            // Execute the command on the server_sender
                                                            return executeSetMoney(player, username, amount);
                                                        })
                                                )
                                )
                        )
                        .then(Commands.literal("remove")
                                .requires(source -> source.hasPermission(2)) // Admin-only for adding money
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(context -> {
                                            CommandSourceStack source = context.getSource();
                                            ServerPlayer player = source.getPlayerOrException();

                                            // Get arguments
                                            String username = player.getName().getString();
                                            int amount = IntegerArgumentType.getInteger(context, "amount");

                                            // Execute the command on the server_sender
                                            return executeRemoveMoney(player, username, amount);
                                        })) // Add to self
                                .then(Commands.argument("username", StringArgumentType.string()).suggests((context, builder) -> {
                                                    //builder.suggest("\""+ ModSettings.MarketBot.USER_NAME +"\"");
                                                    Map<UUID, String> uuidToNameMap = ServerPlayerList.getUuidToNameMap();
                                                    for(String name : uuidToNameMap.values()) {

                                                        builder.suggest("\""+name+"\"");
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                        .executes(context -> {
                                                            CommandSourceStack source = context.getSource();
                                                            ServerPlayer player = source.getPlayerOrException();

                                                            // Get arguments
                                                            String username = StringArgumentType.getString(context, "username");
                                                            int amount = IntegerArgumentType.getInteger(context, "amount");

                                                            // Execute the command on the server_sender
                                                            return executeRemoveMoney(player, username, amount);
                                                        })
                                                )
                                )
                        )
                        .then(Commands.literal("send")
                                .then(Commands.argument("username", StringArgumentType.string()).suggests((context, builder) -> {
                                                    //builder.suggest("\""+ ModSettings.MarketBot.USER_NAME +"\"");
                                                    Map<UUID, String> uuidToNameMap = ServerPlayerList.getUuidToNameMap();
                                                    for(String name : uuidToNameMap.values()) {

                                                        builder.suggest("\""+name+"\"");
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                        .executes(context -> {
                                                            CommandSourceStack source = context.getSource();
                                                            ServerPlayer player = source.getPlayerOrException();

                                                            // Get arguments
                                                            String fromPlayer = player.getName().getString();
                                                            String toPlayer = StringArgumentType.getString(context, "username");
                                                            int amount = IntegerArgumentType.getInteger(context, "amount");

                                                            // Execute the command on the server_sender
                                                            return executeSendMoney(player,fromPlayer, toPlayer, amount);
                                                        })
                                                )
                                )
                        )
                        .then(Commands.literal("circulation")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player = source.getPlayerOrException();
                                    long circulation = ServerBankManager.getMoneyCirculation();
                                    player.sendSystemMessage(Component.literal("Circulation: "+MoneyBank.ITEM_ID + circulation));
                                    return Command.SINGLE_SUCCESS;
                                })
                        )


        );


        // /bank                                                - Show bank balance (money and items)
        // /bank <username> show                                - Show bank balance of another player
        // /bank <username> create <itemID> <amount>            - Create a bank for another player
        // /bank <username> setBalance <itemID> <amount>        - Set balance of a bank for another player
        // /bank <username> delete <itemID>                     - Delete a bank for another player
        dispatcher.register(
                Commands.literal("bank")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            ServerPlayer player = source.getPlayerOrException();

                            // Execute the balance command on the server_sender
                            return bank_show(player, player.getName().getString());
                        })
                        .then(Commands.argument("username", StringArgumentType.string()).suggests((context, builder) -> {
                                            //builder.suggest("\""+ ModSettings.MarketBot.USER_NAME +"\"");
                                            Map<UUID, String> uuidToNameMap = ServerPlayerList.getUuidToNameMap();
                                            for(String name : uuidToNameMap.values()) {

                                                builder.suggest("\""+name+"\"");
                                            }
                                            return builder.buildFuture();
                                        })
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.literal("show")
                                                .executes(context -> {
                                                    CommandSourceStack source = context.getSource();
                                                    ServerPlayer player = source.getPlayerOrException();
                                                    String username = StringArgumentType.getString(context, "username");

                                                    // Execute the balance command on the server_sender
                                                    return bank_show(player, username);
                                                })
                                        )
                                        .then(Commands.literal("create")
                                                .then(Commands.argument("itemID", StringArgumentType.string()).suggests((context, builder) -> {
                                                                    ArrayList<String> suggestions = ServerMarket.getTradeItemIDs();
                                                                    for(String suggestion : suggestions) {
                                                                        builder.suggest("\""+suggestion+"\"");
                                                                    }
                                                                    return builder.buildFuture();
                                                                })
                                                                .then(Commands.argument("balance", LongArgumentType.longArg(0))
                                                                        .executes(context -> {
                                                                            CommandSourceStack source = context.getSource();
                                                                            ServerPlayer player = source.getPlayerOrException();

                                                                            // Get arguments
                                                                            String itemID = StringArgumentType.getString(context, "itemID");
                                                                            long balance = LongArgumentType.getLong(context, "balance");
                                                                            String username = StringArgumentType.getString(context, "username");


                                                                            // Execute the command on the server_sender
                                                                            return bank_create(player, username, itemID, balance);
                                                                        })
                                                                )
                                                )
                                        )
                                        .then(Commands.literal("setBalance")
                                                .then(Commands.argument("itemID", StringArgumentType.string()).suggests((context, builder) -> {
                                                                    ArrayList<String> suggestions = ServerMarket.getTradeItemIDs();
                                                                    builder.suggest("\""+MoneyBank.ITEM_ID+"\"");
                                                                    for(String suggestion : suggestions) {
                                                                        builder.suggest("\""+suggestion+"\"");
                                                                    }
                                                                    return builder.buildFuture();
                                                                })
                                                                .then(Commands.argument("balance", LongArgumentType.longArg(0))
                                                                        .executes(context -> {
                                                                            CommandSourceStack source = context.getSource();
                                                                            ServerPlayer player = source.getPlayerOrException();

                                                                            // Get arguments
                                                                            String itemID = StringArgumentType.getString(context, "itemID");
                                                                            long balance = LongArgumentType.getLong(context, "balance");
                                                                            String username = StringArgumentType.getString(context, "username");


                                                                            // Execute the command on the server_sender
                                                                            return bank_setBalance(player, username, itemID, balance);
                                                                        })
                                                                )
                                                )
                                        )
                                        .then(Commands.literal("delete")
                                                .then(Commands.argument("itemID", StringArgumentType.string()).suggests((context, builder) -> {
                                                                    ArrayList<String> suggestions = ServerMarket.getTradeItemIDs();
                                                                    for(String suggestion : suggestions) {
                                                                        builder.suggest("\""+suggestion+"\"");
                                                                    }
                                                                    return builder.buildFuture();
                                                                })
                                                                .executes(context -> {
                                                                    CommandSourceStack source = context.getSource();
                                                                    ServerPlayer player = source.getPlayerOrException();

                                                                    // Get arguments
                                                                    String itemID = StringArgumentType.getString(context, "itemID");
                                                                    String username = StringArgumentType.getString(context, "username");


                                                                    // Execute the command on the server_sender
                                                                    return bank_delete(player, username, itemID);
                                                                })
                                                )
                                        )
                        )


        );



        // /StockMarket setPriceCandleTimeInterval <seconds>                            - Set price candle time interval
        // /StockMarket createDefaultBots                                               - Create default bots
        // /StockMarket order cancelAll                                                 - Cancel all orders
        // /StockMarket order cancelAll <itemID>                                        - Cancel all orders of an item
        // /StockMarket order <username> cancelAll                                      - Cancel all orders of a player
        // /StockMarket order <username> cancelAll <itemID>                             - Cancel all orders of a player for an item
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
        // /StockMarket <itemID> bot create                                             - Create bot
        // /StockMarket <itemID> bot remove                                             - Remove bot
        // /StockMarket <itemID> create                                                 - Create marketplace
        // /StockMarket <itemID> remove                                                 - Remove marketplace
        // /StockMarket <itemID> currentPrice                                           - Get current price

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
                                                            for(String suggestion : suggestions) {
                                                                builder.suggest("\""+suggestion+"\"");
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
                                                    for(String name : uuidToNameMap.values()) {

                                                        builder.suggest("\""+name+"\"");
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
                                                                            for(String suggestion : suggestions) {
                                                                                builder.suggest("\""+suggestion+"\"");
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

                        .then(Commands.argument("itemID", StringArgumentType.string()).suggests((context, builder) -> {
                                            ArrayList<String> suggestions = ServerMarket.getTradeItemIDs();
                                            for(String suggestion : suggestions) {
                                                builder.suggest("\""+suggestion+"\"");
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
                                                                    String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                    if(itemID == null) {
                                                                        player.sendSystemMessage(Component.literal("Item not found"));
                                                                        return Command.SINGLE_SUCCESS;
                                                                    }
                                                                    ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                                    if(bot instanceof ServerVolatilityBot) {
                                                                        ServerVolatilityBot volatilityBot = (ServerVolatilityBot) bot;
                                                                        StringBuilder msg = new StringBuilder();
                                                                        msg.append("StockMarketBot settings for item: "+itemID+"\n");
                                                                        msg.append("| Enabled: "+(volatilityBot.isEnabled()?"Yes":"No")+"\n");
                                                                        msg.append("| Volatility: "+volatilityBot.getVolatility()+"\n");
                                                                        msg.append("| Order randomness: "+volatilityBot.getOrderRandomness()+"\n");
                                                                        msg.append("| Target Price: "+volatilityBot.getTargetPrice()+"\n");
                                                                        msg.append("| Target Price Range: "+volatilityBot.getImbalancePriceRange()+"\n");
                                                                        msg.append("| Price change linear fac: "+volatilityBot.getImbalancePriceChangeFactor()+"\n");
                                                                        msg.append("| Price change quad fac: "+volatilityBot.getImbalancePriceChangeQuadFactor()+"\n");
                                                                        msg.append("| Target Item Balance: "+volatilityBot.getTargetItemBalance()+"\n");
                                                                        msg.append("| Max Order count: "+volatilityBot.getMaxOrderCount()+"\n");
                                                                        msg.append("| Volume scale: "+volatilityBot.getVolumeScale()+"\n");
                                                                        msg.append("| Volume spread: "+volatilityBot.getVolumeSpread()+"\n");
                                                                        msg.append("| Volume randomness: "+volatilityBot.getVolumeRandomness()+"\n");
                                                                        msg.append("| Timer: "+volatilityBot.getTimerMillis()+"ms\n");
                                                                        msg.append("| Min Timer: "+volatilityBot.getMinTimerMillis()+"ms\n");
                                                                        msg.append("| Max Timer: "+volatilityBot.getMaxTimerMillis()+"ms\n");
                                                                        msg.append("| Update Interval: "+volatilityBot.getUpdateInterval()+"ms\n");
                                                                        msg.append("| PID P: "+volatilityBot.getPidP()+"\n");
                                                                        msg.append("| PID D: "+volatilityBot.getPidD()+"\n");
                                                                        msg.append("| PID I: "+volatilityBot.getPidI()+"\n");
                                                                        msg.append("| PID I Bounds: "+volatilityBot.getPidIBound()+"\n");

                                                                        player.sendSystemMessage(Component.literal(msg.toString()));
                                                                    }
                                                                    else
                                                                    {
                                                                        player.sendSystemMessage(Component.literal("Bot not found"));
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
                                                                            String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                            if(itemID == null) {
                                                                                player.sendSystemMessage(Component.literal("Item not found"));
                                                                                return Command.SINGLE_SUCCESS;
                                                                            }
                                                                            ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                                            if(bot instanceof ServerVolatilityBot) {
                                                                                ServerVolatilityBot volatilityBot = (ServerVolatilityBot) bot;
                                                                                volatilityBot.setEnabled(true);
                                                                                player.sendSystemMessage(Component.literal("Bot enabled"));
                                                                            }
                                                                            else {
                                                                                player.sendSystemMessage(Component.literal("Bot not found"));
                                                                            }
                                                                            // Execute the command on the server_sender
                                                                            return Command.SINGLE_SUCCESS;
                                                                        })
                                                                )
                                                                .then(Commands.literal("disabled")
                                                                        .executes(context -> {
                                                                            CommandSourceStack source = context.getSource();
                                                                            ServerPlayer player = source.getPlayerOrException();
                                                                            String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                            if(itemID == null) {
                                                                                player.sendSystemMessage(Component.literal("Item not found"));
                                                                                return Command.SINGLE_SUCCESS;
                                                                            }
                                                                            ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                                            if(bot instanceof ServerVolatilityBot) {
                                                                                ServerVolatilityBot volatilityBot = (ServerVolatilityBot) bot;
                                                                                volatilityBot.setEnabled(false);
                                                                                player.sendSystemMessage(Component.literal("Bot disabled"));
                                                                            }else {
                                                                                player.sendSystemMessage(Component.literal("Bot not found"));
                                                                            }
                                                                            // Execute the command on the server_sender
                                                                            return Command.SINGLE_SUCCESS;
                                                                        })
                                                                )
                                                                .then(Commands.literal("volatility")
                                                                        .then(Commands.argument("volatility", IntegerArgumentType.integer(0))
                                                                                .executes(context -> {
                                                                                    CommandSourceStack source = context.getSource();
                                                                                    ServerPlayer player = source.getPlayerOrException();
                                                                                    String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                                    if(itemID == null) {
                                                                                        player.sendSystemMessage(Component.literal("Item not found"));
                                                                                        return Command.SINGLE_SUCCESS;
                                                                                    }
                                                                                    int volatility = IntegerArgumentType.getInteger(context, "volatility");
                                                                                    ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                                                    if(bot instanceof ServerVolatilityBot) {
                                                                                        ServerVolatilityBot volatilityBot = (ServerVolatilityBot) bot;
                                                                                        volatilityBot.setVolatility(volatility);
                                                                                        player.sendSystemMessage(Component.literal("Volatility set to "+volatility));
                                                                                    }else {
                                                                                        player.sendSystemMessage(Component.literal("Bot not found"));
                                                                                    }
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
                                                                                    String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                                    if(itemID == null) {
                                                                                        player.sendSystemMessage(Component.literal("Item not found"));
                                                                                        return Command.SINGLE_SUCCESS;
                                                                                    }
                                                                                    double randomness = DoubleArgumentType.getDouble(context, "randomness");
                                                                                    ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                                                    if(bot instanceof ServerVolatilityBot) {
                                                                                        ServerVolatilityBot volatilityBot = (ServerVolatilityBot) bot;
                                                                                        volatilityBot.setOrderRandomness(randomness);
                                                                                        player.sendSystemMessage(Component.literal("Order randomness set to "+randomness));
                                                                                    }else {
                                                                                        player.sendSystemMessage(Component.literal("Bot not found"));
                                                                                    }
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
                                                                                    String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                                    if(itemID == null) {
                                                                                        player.sendSystemMessage(Component.literal("Item not found"));
                                                                                        return Command.SINGLE_SUCCESS;
                                                                                    }
                                                                                    double factor = DoubleArgumentType.getDouble(context, "factor");
                                                                                    ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                                                    if(bot instanceof ServerVolatilityBot volatilityBot) {
                                                                                        volatilityBot.setImbalancePriceChangeFactor(factor);
                                                                                        player.sendSystemMessage(Component.literal("Imbalance-price-change-factor-linear price range set to "+factor));
                                                                                    }else {
                                                                                        player.sendSystemMessage(Component.literal("Bot not found"));
                                                                                    }
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
                                                                                    String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                                    if(itemID == null) {
                                                                                        player.sendSystemMessage(Component.literal("Item not found"));
                                                                                        return Command.SINGLE_SUCCESS;
                                                                                    }
                                                                                    double factor = DoubleArgumentType.getDouble(context, "factor");
                                                                                    ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                                                    if(bot instanceof ServerVolatilityBot volatilityBot) {
                                                                                        volatilityBot.setImbalancePriceChangeQuadFactor(factor);
                                                                                        player.sendSystemMessage(Component.literal("Imbalance-price-change-factor-quadratic price range set to "+factor));
                                                                                    }else {
                                                                                        player.sendSystemMessage(Component.literal("Bot not found"));
                                                                                    }
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
                                                                                    String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                                    if(itemID == null) {
                                                                                        player.sendSystemMessage(Component.literal("Item not found"));
                                                                                        return Command.SINGLE_SUCCESS;
                                                                                    }
                                                                                    int balance = IntegerArgumentType.getInteger(context, "balance");
                                                                                    ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                                                    if(bot instanceof ServerVolatilityBot) {
                                                                                        ServerVolatilityBot volatilityBot = (ServerVolatilityBot) bot;
                                                                                        volatilityBot.setTargetItemBalance(balance);
                                                                                        player.sendSystemMessage(Component.literal("Target item balance set to "+balance));
                                                                                    }else {
                                                                                        player.sendSystemMessage(Component.literal("Bot not found"));
                                                                                    }
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
                                                                                    String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                                    if(itemID == null) {
                                                                                        player.sendSystemMessage(Component.literal("Item not found"));
                                                                                        return Command.SINGLE_SUCCESS;
                                                                                    }
                                                                                    int range = IntegerArgumentType.getInteger(context, "priceRange");
                                                                                    ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                                                    if(bot instanceof ServerVolatilityBot volatilityBot) {
                                                                                        volatilityBot.setImbalancePriceRange(range);
                                                                                        player.sendSystemMessage(Component.literal("Target price range set to "+range));
                                                                                    }else {
                                                                                        player.sendSystemMessage(Component.literal("Bot not found"));
                                                                                    }
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
                                                                                    String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                                    if(itemID == null) {
                                                                                        player.sendSystemMessage(Component.literal("Item not found"));
                                                                                        return Command.SINGLE_SUCCESS;
                                                                                    }
                                                                                    int amount = IntegerArgumentType.getInteger(context, "orderCount");
                                                                                    ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                                                    if(bot instanceof ServerVolatilityBot volatilityBot) {
                                                                                        volatilityBot.setMaxOrderCount(amount);
                                                                                        player.sendSystemMessage(Component.literal("Max order count set to "+amount));
                                                                                    }else {
                                                                                        player.sendSystemMessage(Component.literal("Bot not found"));
                                                                                    }
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
                                                                                    String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                                    if(itemID == null) {
                                                                                        player.sendSystemMessage(Component.literal("Item not found"));
                                                                                        return Command.SINGLE_SUCCESS;
                                                                                    }
                                                                                    double scale = DoubleArgumentType.getDouble(context, "volumeScale");
                                                                                    ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                                                    if(bot instanceof ServerVolatilityBot volatilityBot) {
                                                                                        volatilityBot.setVolumeScale(scale);
                                                                                        player.sendSystemMessage(Component.literal("Volume scale set to "+scale));
                                                                                    }else {
                                                                                        player.sendSystemMessage(Component.literal("Bot not found"));
                                                                                    }
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
                                                                                    String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                                    if(itemID == null) {
                                                                                        player.sendSystemMessage(Component.literal("Item not found"));
                                                                                        return Command.SINGLE_SUCCESS;
                                                                                    }
                                                                                    double spread = DoubleArgumentType.getDouble(context, "volumeSpread");
                                                                                    ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                                                    if(bot instanceof ServerVolatilityBot volatilityBot) {
                                                                                        volatilityBot.setVolumeSpread(spread);
                                                                                        player.sendSystemMessage(Component.literal("Volume spread set to "+spread));
                                                                                    }else {
                                                                                        player.sendSystemMessage(Component.literal("Bot not found"));
                                                                                    }
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
                                                                                    String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                                    if(itemID == null) {
                                                                                        player.sendSystemMessage(Component.literal("Item not found"));
                                                                                        return Command.SINGLE_SUCCESS;
                                                                                    }
                                                                                    double randomness = DoubleArgumentType.getDouble(context, "volumeRandomness");
                                                                                    ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                                                    if(bot instanceof ServerVolatilityBot volatilityBot) {
                                                                                        volatilityBot.setVolumeRandomness(randomness);
                                                                                        player.sendSystemMessage(Component.literal("Volume randomness set to "+randomness));
                                                                                    }else {
                                                                                        player.sendSystemMessage(Component.literal("Bot not found"));
                                                                                    }
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
                                                                                    String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                                    if(itemID == null) {
                                                                                        player.sendSystemMessage(Component.literal("Item not found"));
                                                                                        return Command.SINGLE_SUCCESS;
                                                                                    }
                                                                                    long timer = LongArgumentType.getLong(context, "timeMS");
                                                                                    if(timer < 0)
                                                                                    {
                                                                                        player.sendSystemMessage(Component.literal("Timer must be greater than 0"));
                                                                                        return Command.SINGLE_SUCCESS;
                                                                                    }
                                                                                    ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                                                    if(bot instanceof ServerVolatilityBot) {
                                                                                        ServerVolatilityBot volatilityBot = (ServerVolatilityBot) bot;
                                                                                        volatilityBot.setTimerMillis(timer);
                                                                                        player.sendSystemMessage(Component.literal("Timer set to "+timer+"ms"));
                                                                                    }else {
                                                                                        player.sendSystemMessage(Component.literal("Bot not found"));
                                                                                    }
                                                                                    // Execute the command on the server_sender
                                                                                    return Command.SINGLE_SUCCESS;
                                                                                })
                                                                        )
                                                                )
                                                                .then(Commands.literal("minTimer")
                                                                        .then(Commands.argument("timeMS", LongArgumentType.longArg(0))
                                                                                .executes(context -> {
                                                                                    CommandSourceStack source = context.getSource();
                                                                                    ServerPlayer player = source.getPlayerOrException();
                                                                                    String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                                    if(itemID == null) {
                                                                                        player.sendSystemMessage(Component.literal("Item not found"));
                                                                                        return Command.SINGLE_SUCCESS;
                                                                                    }
                                                                                    long timer = LongArgumentType.getLong(context, "timeMS");
                                                                                    if(timer < 0)
                                                                                    {
                                                                                        player.sendSystemMessage(Component.literal("Timer must be greater than 0"));
                                                                                        return Command.SINGLE_SUCCESS;
                                                                                    }
                                                                                    ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                                                    if(bot instanceof ServerVolatilityBot) {
                                                                                        ServerVolatilityBot volatilityBot = (ServerVolatilityBot) bot;
                                                                                        volatilityBot.setMinTimerMillis(timer);
                                                                                        player.sendSystemMessage(Component.literal("Min Timer set to "+timer+"ms"));
                                                                                    }else {
                                                                                        player.sendSystemMessage(Component.literal("Bot not found"));
                                                                                    }
                                                                                    // Execute the command on the server_sender
                                                                                    return Command.SINGLE_SUCCESS;
                                                                                })
                                                                        )
                                                                )
                                                                .then(Commands.literal("maxTimer")
                                                                        .then(Commands.argument("timeMS", LongArgumentType.longArg(0))
                                                                                .executes(context -> {
                                                                                    CommandSourceStack source = context.getSource();
                                                                                    ServerPlayer player = source.getPlayerOrException();
                                                                                    String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                                    if(itemID == null) {
                                                                                        player.sendSystemMessage(Component.literal("Item not found"));
                                                                                        return Command.SINGLE_SUCCESS;
                                                                                    }
                                                                                    long timer = LongArgumentType.getLong(context, "timeMS");
                                                                                    if(timer < 0)
                                                                                    {
                                                                                        player.sendSystemMessage(Component.literal("Timer must be greater than 0"));
                                                                                        return Command.SINGLE_SUCCESS;
                                                                                    }
                                                                                    ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                                                    if(bot instanceof ServerVolatilityBot) {
                                                                                        ServerVolatilityBot volatilityBot = (ServerVolatilityBot) bot;
                                                                                        volatilityBot.setMaxTimerMillis(timer);
                                                                                        player.sendSystemMessage(Component.literal("Max Timer set to "+timer+"ms"));
                                                                                    }else {
                                                                                        player.sendSystemMessage(Component.literal("Bot not found"));
                                                                                    }
                                                                                    // Execute the command on the server_sender
                                                                                    return Command.SINGLE_SUCCESS;
                                                                                })
                                                                        )
                                                                )
                                                                .then(Commands.literal("updateInterval")
                                                                        .then(Commands.argument("timeMS", LongArgumentType.longArg(0))
                                                                                .executes(context -> {
                                                                                    CommandSourceStack source = context.getSource();
                                                                                    ServerPlayer player = source.getPlayerOrException();
                                                                                    String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                                    if(itemID == null) {
                                                                                        player.sendSystemMessage(Component.literal("Item not found"));
                                                                                        return Command.SINGLE_SUCCESS;
                                                                                    }
                                                                                    long timer = LongArgumentType.getLong(context, "timeMS");
                                                                                    if(timer < 0)
                                                                                    {
                                                                                        player.sendSystemMessage(Component.literal("Timer must be greater than 0"));
                                                                                        return Command.SINGLE_SUCCESS;
                                                                                    }
                                                                                    ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                                                    if(bot instanceof ServerVolatilityBot) {
                                                                                        ServerVolatilityBot volatilityBot = (ServerVolatilityBot) bot;
                                                                                        volatilityBot.setUpdateInterval(timer);
                                                                                        player.sendSystemMessage(Component.literal("Update interval set to "+timer+"ms"));
                                                                                    }else {
                                                                                        player.sendSystemMessage(Component.literal("Bot not found"));
                                                                                    }
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
                                                                                    String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                                    if(itemID == null) {
                                                                                        player.sendSystemMessage(Component.literal("Item not found"));
                                                                                        return Command.SINGLE_SUCCESS;
                                                                                    }
                                                                                    double pidP = DoubleArgumentType.getDouble(context, "pidP");
                                                                                    ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                                                    if(bot instanceof ServerVolatilityBot) {
                                                                                        ServerVolatilityBot volatilityBot = (ServerVolatilityBot) bot;
                                                                                        volatilityBot.setPidP(pidP);
                                                                                        player.sendSystemMessage(Component.literal("PID P set to "+pidP));
                                                                                    }else {
                                                                                        player.sendSystemMessage(Component.literal("Bot not found"));
                                                                                    }
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
                                                                                    String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                                    if(itemID == null) {
                                                                                        player.sendSystemMessage(Component.literal("Item not found"));
                                                                                        return Command.SINGLE_SUCCESS;
                                                                                    }
                                                                                    double pidI = DoubleArgumentType.getDouble(context, "pidI");
                                                                                    ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                                                    if(bot instanceof ServerVolatilityBot) {
                                                                                        ServerVolatilityBot volatilityBot = (ServerVolatilityBot) bot;
                                                                                        volatilityBot.setPidI(pidI);
                                                                                        player.sendSystemMessage(Component.literal("PID I set to "+pidI));
                                                                                    }else {
                                                                                        player.sendSystemMessage(Component.literal("Bot not found"));
                                                                                    }
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
                                                                                    String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                                    if(itemID == null) {
                                                                                        player.sendSystemMessage(Component.literal("Item not found"));
                                                                                        return Command.SINGLE_SUCCESS;
                                                                                    }
                                                                                    double pidD = DoubleArgumentType.getDouble(context, "pidD");
                                                                                    ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                                                    if(bot instanceof ServerVolatilityBot) {
                                                                                        ServerVolatilityBot volatilityBot = (ServerVolatilityBot) bot;
                                                                                        volatilityBot.setPidD(pidD);
                                                                                        player.sendSystemMessage(Component.literal("PID D set to "+pidD));
                                                                                    }else {
                                                                                        player.sendSystemMessage(Component.literal("Bot not found"));
                                                                                    }
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
                                                                                    String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                                    if(itemID == null) {
                                                                                        player.sendSystemMessage(Component.literal("Item not found"));
                                                                                        return Command.SINGLE_SUCCESS;
                                                                                    }
                                                                                    double pidIBounds = DoubleArgumentType.getDouble(context, "pidIBounds");
                                                                                    ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                                                    if(bot instanceof ServerVolatilityBot) {
                                                                                        ServerVolatilityBot volatilityBot = (ServerVolatilityBot) bot;
                                                                                        volatilityBot.setPidIBound(pidIBounds);
                                                                                        player.sendSystemMessage(Component.literal("PID I bounds set to "+pidIBounds));
                                                                                    }else {
                                                                                        player.sendSystemMessage(Component.literal("Bot not found"));
                                                                                    }
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
                                                            String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                            if(itemID == null) {
                                                                player.sendSystemMessage(Component.literal("Item not found"));
                                                                return Command.SINGLE_SUCCESS;
                                                            }ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                            if(bot == null) {
                                                                ServerVolatilityBot volatilityBot = new ServerVolatilityBot();
                                                                volatilityBot.setEnabled(false);
                                                                ServerMarket.setTradingBot(itemID, volatilityBot);
                                                                player.sendSystemMessage(Component.literal("Bot created, you can change its settings and than enable it"));
                                                            }
                                                            else {
                                                                player.sendSystemMessage(Component.literal("Bot already exists"));
                                                            }
                                                            // Execute the command on the server_sender
                                                            return Command.SINGLE_SUCCESS;
                                                        })
                                                )

                                                .then(Commands.literal("remove")
                                                        .executes(context -> {
                                                            CommandSourceStack source = context.getSource();
                                                            ServerPlayer player = source.getPlayerOrException();
                                                            String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                            if(itemID == null) {
                                                                player.sendSystemMessage(Component.literal("Item not found"));
                                                                return Command.SINGLE_SUCCESS;
                                                            }ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                            if(bot == null) {
                                                                player.sendSystemMessage(Component.literal("Bot doesn't exist"));
                                                            }
                                                            else {
                                                                ServerMarket.removeTradingBot(itemID);
                                                                player.sendSystemMessage(Component.literal("Bot removed"));
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
                                                    String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                    if(itemID == null) {
                                                        player.sendSystemMessage(Component.literal("Item not found"));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    if(ServerMarket.hasItem(itemID)) {
                                                        player.sendSystemMessage(Component.literal("Marketplace already exists"));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    else {


                                                        ServerMarket.addTradeItem(itemID, 0);
                                                        // Notify all serverPlayers
                                                        StockMarketMod.printToClientConsone(itemID+" can now be traded on the stock market.");
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                })
                                        )
                                        .then(Commands.literal("remove")
                                                .executes(context -> {
                                                    CommandSourceStack source = context.getSource();
                                                    ServerPlayer player = source.getPlayerOrException();
                                                    String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                    if(itemID == null) {
                                                        player.sendSystemMessage(Component.literal("Item not found"));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    if(ServerMarket.hasItem(itemID)) {
                                                        ServerMarket.removeTradingItem(itemID);
                                                        player.sendSystemMessage(Component.literal("Marketplace removed"));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    else {
                                                        // Notify all serverPlayers
                                                        StockMarketMod.printToClientConsone(itemID+" can no longer be traded on the stock market.");
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                })
                                        )
                                        /*.then(Commands.literal("createDefault")
                                                .then(Commands.argument("startPrice", IntegerArgumentType.integer(0))
                                                      .then(Commands.argument("startItemCount", IntegerArgumentType.integer(0))
                                                              .then(Commands.argument("priceRange", IntegerArgumentType.integer(0))
                                                                .executes(context -> {
                                                                    CommandSourceStack source = context.getSource();
                                                                    ServerPlayer player = source.getPlayerOrException();
                                                                    String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                    if(itemID == null) {
                                                                        player.sendSystemMessage(Component.literal("Item not found"));
                                                                        return Command.SINGLE_SUCCESS;
                                                                    }


                                                                    createNewMarketDefault(player, itemID,
                                                                            IntegerArgumentType.getInteger(context, "startPrice"),
                                                                            IntegerArgumentType.getInteger(context, "startItemCount"),
                                                                            IntegerArgumentType.getInteger(context, "priceRange"));



                                                                    // Execute the command on the server_sender
                                                                    return Command.SINGLE_SUCCESS;
                                                                })
                                                              )
                                                        )
                                                )
                                        )*/
                                        .then(Commands.literal("currentPrice")
                                                .executes(context -> {
                                                    CommandSourceStack source = context.getSource();
                                                    ServerPlayer player = source.getPlayerOrException();
                                                    String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                    if(itemID == null) {
                                                        player.sendSystemMessage(Component.literal("Item not found"));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    if(!ServerMarket.hasItem(itemID)) {
                                                        player.sendSystemMessage(Component.literal("Marketplace doesn't exist for "+itemID));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    long price = ServerMarket.getPrice(itemID);
                                                    player.sendSystemMessage(Component.literal("Current price of "+itemID+": "+price));
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )

                        )
        );
    }


    private static int executeAddMoney(ServerPlayer executor, String username, int amount) {
        // Server-side logic for adding money
        UUID playerUUID = ServerPlayerList.getPlayerUUID(username);
        if(playerUUID == null)
        {
            executor.sendSystemMessage(
                    Component.literal("Player " + username + " not found.")
            );
            return Command.SINGLE_SUCCESS;
        }
        Bank bank = ServerBankManager.getMoneyBank(playerUUID);
        if(bank == null)
        {
            executor.sendSystemMessage(
                    Component.literal("Bank not found for " + username)
            );
            return Command.SINGLE_SUCCESS;
        }
        bank.deposit(amount);
        executor.sendSystemMessage(
                Component.literal("Added " + amount + " to " + username + "'s account!")
        );
        return Command.SINGLE_SUCCESS;
    }
    private static int executeSetMoney(ServerPlayer executor, String username, int amount) {
        // Server-side logic for adding money
        UUID playerUUID = ServerPlayerList.getPlayerUUID(username);
        if(playerUUID == null)
        {
            executor.sendSystemMessage(
                    Component.literal("Player " + username + " not found.")
            );
            return Command.SINGLE_SUCCESS;
        }
        Bank bank = ServerBankManager.getMoneyBank(playerUUID);
        if(bank == null)
        {
            executor.sendSystemMessage(
                    Component.literal("Bank not found for " + username)
            );
            return Command.SINGLE_SUCCESS;
        }
        bank.setBalance(amount);
        executor.sendSystemMessage(
                Component.literal("Set " + amount + " to " + username + "'s account!")
        );
        return Command.SINGLE_SUCCESS;
    }
    private static int executeRemoveMoney(ServerPlayer executor, String username, int amount) {
        // Server-side logic for adding money
        UUID playerUUID = ServerPlayerList.getPlayerUUID(username);
        if(playerUUID == null)
        {
            executor.sendSystemMessage(
                    Component.literal("Player " + username + " not found.")
            );
            return Command.SINGLE_SUCCESS;
        }
        Bank bank = ServerBankManager.getMoneyBank(playerUUID);
        if(bank == null)
        {
            executor.sendSystemMessage(
                    Component.literal("Bank not found for " + username)
            );
            return Command.SINGLE_SUCCESS;
        }
        if(bank.getBalance() >= amount) {
            bank.withdraw(amount);

            executor.sendSystemMessage(
                    Component.literal("Removed " + amount + " from " + username + "'s account!")
            );
        }
        else {
            executor.sendSystemMessage(
                    Component.literal("Not enough money in " + username + "'s account!")
            );
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeSendMoney(ServerPlayer executor, String fromUser, String toUser, int amount) {
        // Server-side logic for adding money
        ServerPlayer fromPlayer = ServerPlayerList.getPlayer(fromUser);
        UUID fromPlayerUUID = ServerPlayerList.getPlayerUUID(fromUser);
        if(fromPlayerUUID == null)
        {
            executor.sendSystemMessage(
                    Component.literal("Player " + fromUser + " not found.")
            );
            return Command.SINGLE_SUCCESS;
        }
        ServerPlayer toPlayer = ServerPlayerList.getPlayer(toUser);
        UUID toPlayerUUID = ServerPlayerList.getPlayerUUID(toUser);
        if(toPlayerUUID == null)
        {
            executor.sendSystemMessage(
                    Component.literal("Player " + toUser + " not found.")
            );
            return Command.SINGLE_SUCCESS;
        }

        Bank fromBank = ServerBankManager.getMoneyBank(fromPlayerUUID);
        Bank toBank = ServerBankManager.getMoneyBank(toPlayerUUID);

        if(fromBank == null)
        {
            if(fromPlayer != null)
                StockMarketMod.printToClientConsole(fromPlayer, "You don't have a money bank account.");
            return Command.SINGLE_SUCCESS;
        }
        if(toBank == null)
        {
            if(fromPlayer != null)
                StockMarketMod.printToClientConsole(fromPlayer, toUser + " doesn't have a money bank account.");
            return Command.SINGLE_SUCCESS;
        }
        if(fromBank.transfer(amount, toBank))
        {
            if(fromPlayer != null)
                StockMarketMod.printToClientConsole(fromPlayer, "Transfered " + amount + "$ from " + fromUser + " to " + toUser + "'s account!");
            if(toPlayer != null)
                StockMarketMod.printToClientConsole(toPlayer, "Received " + amount + "$ from " + fromUser + "'s account!");
        }
        else {
            if (fromPlayer != null) {
                if (fromBank.getBalance() < amount)
                    StockMarketMod.printToClientConsole(fromPlayer, "You don't have enough money to transfer " + amount + "$!");
                else
                    StockMarketMod.printToClientConsole(fromPlayer, "Failed to transfer " + amount + "$ to " + toUser + "'s account!");
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int showBalance(ServerPlayer player) {
        // Server-side logic for showing the balance
        long balance = ServerBankManager.getMoneyBank(player.getUUID()).getBalance(); // Example call
        player.sendSystemMessage(Component.literal("Your balance: " + balance));
        return Command.SINGLE_SUCCESS;
    }

    private static int bank_show(ServerPlayer player, String targetPlayer) {
        // Server-side logic for showing the balance
        UUID targetPlayerUUID = ServerPlayerList.getPlayerUUID(targetPlayer);

        BankUser user = ServerBankManager.getUser(targetPlayerUUID);
        if(user == null) {
            player.sendSystemMessage(Component.literal("User not found: " + targetPlayer));
            return Command.SINGLE_SUCCESS;
        }
        player.sendSystemMessage(Component.literal(user.toString()));
        return Command.SINGLE_SUCCESS;
    }

    private static int bank_create(ServerPlayer player,String targetPlayer, String itemID, long balance) {
        // Server-side logic for creating a bank
        UUID targetPlayerUUID =  ServerPlayerList.getPlayerUUID(targetPlayer);

        BankUser user = ServerBankManager.getUser(targetPlayerUUID);
        Bank bank = user.getBank(itemID);
        boolean created = bank == null;

        bank = user.createItemBank(itemID, balance);
        if(created)
            player.sendSystemMessage(Component.literal("Bank created for " + player.getName().getString()+"\n"+bank.toStringNoOwner()));
        else
            player.sendSystemMessage(Component.literal("Bank already exists for " + player.getName().getString()+"\n"+bank.toStringNoOwner()));
        return Command.SINGLE_SUCCESS;
    }
    private static int bank_setBalance(ServerPlayer player,String targetPlayer, String itemID, long balance) {
        // Server-side logic for creating a bank
        UUID targetPlayerUUID =  ServerPlayerList.getPlayerUUID(targetPlayer);

        BankUser user = ServerBankManager.getUser(targetPlayerUUID);
        Bank bank = user.getBank(itemID);
        if(bank == null) {
            player.sendSystemMessage(Component.literal("Bank not found for " + player.getName().getString()+" ItemID: "+itemID));
            return Command.SINGLE_SUCCESS;
        }
        bank.setBalance(balance);
        player.sendSystemMessage(Component.literal("Bank balance set for " + player.getName().getString()+"\n"+bank.toStringNoOwner()));
        return Command.SINGLE_SUCCESS;
    }

    private static int bank_delete(ServerPlayer player,String targetPlayer, String itemID) {
        // Server-side logic for creating a bank
        UUID targetPlayerUUID =  ServerPlayerList.getPlayerUUID(targetPlayer);

        BankUser user = ServerBankManager.getUser(targetPlayerUUID);
        if(user.removeBank(itemID))
            player.sendSystemMessage(Component.literal("Bank deleted for " + player.getName().getString()+" ItemID: "+itemID));
        else {
            player.sendSystemMessage(Component.literal("Bank not found for " + player.getName().getString()+" ItemID: "+itemID));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static void createNewMarketDefault(ServerPlayer player, String itemID,
                                               int startPrice,
                                               int startItemCount,
                                               int priceRange)
    {
        /*int playerCapacity = 1;
        Level level = player.level();
        if(level != null)
        {
            if(level.getServer() != null)
            {
                playerCapacity = level.getServer().getMaxPlayers();
            }
        }*/
        if(ServerMarket.hasItem(itemID)) {
            player.sendSystemMessage(Component.literal("Marketplace already exists"));
            return;
        }

        ServerMarket.addTradeItem(itemID, startPrice);
        Bank itemBank = ServerBankManager.getBotUser().createItemBank(itemID, startItemCount);


        ServerVolatilityBot volatilityBot = new ServerVolatilityBot();
        //volatilityBot
        volatilityBot.setEnabled(true);
        ServerMarket.setTradingBot(itemID, volatilityBot);
        player.sendSystemMessage(Component.literal("Bot created, you can change its settings and than enable it"));



        // Notify all serverPlayers
        StockMarketMod.printToClientConsone(itemID+" can now be traded on the stock market.");
    }
}

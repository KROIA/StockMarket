package net.kroia.stockmarket.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
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
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
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
                        .then(Commands.literal("add")
                                .requires(source -> source.hasPermission(2)) // Admin-only for adding money
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(context -> {
                                            CommandSourceStack source = context.getSource();
                                            ServerPlayer player = source.getPlayerOrException();

                                            // Get arguments
                                            String username = player.getName().getString();
                                            int amount = IntegerArgumentType.getInteger(context, "amount");

                                            // Execute the command on the server_sender
                                            return executeAddMoney(player, username, amount);
                                        })) // Add to self
                                .then(Commands.argument("username", StringArgumentType.string())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
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
                                .then(Commands.argument("username", StringArgumentType.string())
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
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(context -> {
                                            CommandSourceStack source = context.getSource();
                                            ServerPlayer player = source.getPlayerOrException();

                                            // Get arguments
                                            String username = player.getName().getString();
                                            int amount = IntegerArgumentType.getInteger(context, "amount");

                                            // Execute the command on the server_sender
                                            return executeRemoveMoney(player, username, amount);
                                        })) // Add to self
                                .then(Commands.argument("username", StringArgumentType.string())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
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
                                .then(Commands.argument("username", StringArgumentType.string())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
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
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            ServerPlayer player = source.getPlayerOrException();

                            // Execute the balance command on the server_sender
                            return showBalance(player);
                        })

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
                                            builder.suggest("\""+ ModSettings.MarketBot.USER_NAME +"\"");
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



        // /StockMarket <itemID> bot settings get                                       - Get bot settings
        // /StockMarket <itemID> bot settings set enabled                               - Enable bot
        // /StockMarket <itemID> bot settings set disabled                              - Disable bot
        // /StockMarket <itemID> bot settings set volatility <volatility>               - Set volatility
        // /StockMarket <itemID> bot settings set imbalancePriceRange <priceRange>      - Set imbalance price range
        // /StockMarket <itemID> bot settings set targetItemBalance <balance>           - Set target item balance
        // /StockMarket <itemID> bot settings set timer <timer>                         - Set timer
        // /StockMarket <itemID> bot settings set minTimer <timer>                      - Set min timer
        // /StockMarket <itemID> bot settings set maxTimer <timer>                      - Set max timer
        // /StockMarket <itemID> bot settings set pidP <pidP>                           - Set PID P
        // /StockMarket <itemID> bot settings set pidI <pidI>                           - Set PID I
        // /StockMarket <itemID> bot settings set pidD <pidD>                           - Set PID D
        // /StockMarket <itemID> bot create                                             - Create bot
        // /StockMarket <itemID> bot remove                                             - Remove bot
        // /StockMarket <itemID> create                                                 - Create marketplace
        // /StockMarket <itemID> currentPrice                                           - Get current price

        dispatcher.register(
                Commands.literal("StockMarket")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("itemID", StringArgumentType.string()).suggests((context, builder) -> {
                                    ArrayList<String> suggestions = ServerMarket.getTradeItemIDs();
                                    for(String suggestion : suggestions) {
                                        builder.suggest("\""+suggestion+"\"");
                                    }
                                    return builder.buildFuture();
                                })
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
                                                                msg.append("Settings about bot for item: "+itemID+"\n");
                                                                msg.append("| Enabled: "+(volatilityBot.isEnabled()?"Yes":"No")+"\n");
                                                                msg.append("| Volatility: "+volatilityBot.getVolatility()+"\n");
                                                                msg.append("| Target Price: "+volatilityBot.getTargetPrice()+"\n");
                                                                msg.append("| Imbalance Price Range: "+volatilityBot.getImbalancePriceRange()+"\n");
                                                                msg.append("| Target Item Stock: "+volatilityBot.getTargetItemBalance()+"\n");
                                                                msg.append("| Timer: "+volatilityBot.getTimerMillis()+"ms\n");
                                                                msg.append("| Min Timer: "+volatilityBot.getMinTimerMillis()+"ms\n");
                                                                msg.append("| Max Timer: "+volatilityBot.getMaxTimerMillis()+"ms\n");
                                                                msg.append("| Update Interval: "+volatilityBot.getUpdateInterval()+"ms\n");
                                                                msg.append("| PID P: "+volatilityBot.getPidI()+"\n");
                                                                msg.append("| PID D: "+volatilityBot.getPidD()+"\n");
                                                                msg.append("| PID I: "+volatilityBot.getPidI()+"\n");

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
                                                                            if(volatility < 0)
                                                                            {
                                                                                player.sendSystemMessage(Component.literal("Volatility must be greater than 0"));
                                                                                return Command.SINGLE_SUCCESS;
                                                                            }
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
                                                        .then(Commands.literal("imbalancePriceRange")
                                                                .then(Commands.argument("priceRange", IntegerArgumentType.integer(0))
                                                                        .executes(context -> {
                                                                            CommandSourceStack source = context.getSource();
                                                                            ServerPlayer player = source.getPlayerOrException();
                                                                            String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                            if(itemID == null) {
                                                                                player.sendSystemMessage(Component.literal("Item not found"));
                                                                                return Command.SINGLE_SUCCESS;
                                                                            }
                                                                            int range = IntegerArgumentType.getInteger(context, "priceRange");
                                                                            if(range < 1)
                                                                            {
                                                                                player.sendSystemMessage(Component.literal("Imbalance price range must be greater than 1"));
                                                                                return Command.SINGLE_SUCCESS;
                                                                            }
                                                                            ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
                                                                            if(bot instanceof ServerVolatilityBot) {
                                                                                ServerVolatilityBot volatilityBot = (ServerVolatilityBot) bot;
                                                                                volatilityBot.setImbalancePriceRange(range);
                                                                                player.sendSystemMessage(Component.literal("Imbalance price range set to "+range));
                                                                            }else {
                                                                                player.sendSystemMessage(Component.literal("Bot not found"));
                                                                            }
                                                                            // Execute the command on the server_sender
                                                                            return Command.SINGLE_SUCCESS;
                                                                        })
                                                                )
                                                        )
                                                        .then(Commands.literal("targetItemBalance")
                                                                .then(Commands.argument("balance", IntegerArgumentType.integer(0))
                                                                        .executes(context -> {
                                                                            CommandSourceStack source = context.getSource();
                                                                            ServerPlayer player = source.getPlayerOrException();
                                                                            String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                            if(itemID == null) {
                                                                                player.sendSystemMessage(Component.literal("Item not found"));
                                                                                return Command.SINGLE_SUCCESS;
                                                                            }
                                                                            int balance = IntegerArgumentType.getInteger(context, "balance");
                                                                            if(balance < 0)
                                                                            {
                                                                                player.sendSystemMessage(Component.literal("Balance must be greater than 0"));
                                                                                return Command.SINGLE_SUCCESS;
                                                                            }
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
                                                                .then(Commands.argument("pidP", IntegerArgumentType.integer(0))
                                                                        .executes(context -> {
                                                                            CommandSourceStack source = context.getSource();
                                                                            ServerPlayer player = source.getPlayerOrException();
                                                                            String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                            if(itemID == null) {
                                                                                player.sendSystemMessage(Component.literal("Item not found"));
                                                                                return Command.SINGLE_SUCCESS;
                                                                            }
                                                                            int pidP = IntegerArgumentType.getInteger(context, "pidP");
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
                                                                .then(Commands.argument("pidI", IntegerArgumentType.integer(0))
                                                                        .executes(context -> {
                                                                            CommandSourceStack source = context.getSource();
                                                                            ServerPlayer player = source.getPlayerOrException();
                                                                            String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                            if(itemID == null) {
                                                                                player.sendSystemMessage(Component.literal("Item not found"));
                                                                                return Command.SINGLE_SUCCESS;
                                                                            }
                                                                            int pidI = IntegerArgumentType.getInteger(context, "pidI");
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
                                                                .then(Commands.argument("pidD", IntegerArgumentType.integer(0))
                                                                        .executes(context -> {
                                                                            CommandSourceStack source = context.getSource();
                                                                            ServerPlayer player = source.getPlayerOrException();
                                                                            String itemID = StockMarketMod.getNormalizedItemID(StringArgumentType.getString(context, "itemID"));
                                                                            if(itemID == null) {
                                                                                player.sendSystemMessage(Component.literal("Item not found"));
                                                                                return Command.SINGLE_SUCCESS;
                                                                            }
                                                                            int pidD = IntegerArgumentType.getInteger(context, "pidD");
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
                StockMarketMod.printToClientConsole(fromPlayer, "Received " + amount + "$ from " + fromUser + "'s account!");
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
}

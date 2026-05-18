package net.kroia.stockmarket.minecraft.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.kroia.modutilities.ServerPlayerUtilities;
import net.kroia.stockmarket.api.command.IAsyncStockMarketCommandHandler;
import net.kroia.stockmarket.api.command.IServerStockMarketCommandHandler;
import net.kroia.modutilities.testing.TestCommandRegistration;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.networking.packet.OpenUIPacket;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.data.table.MarketPriceManager;
import net.kroia.stockmarket.data.filter.DateFilter;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


public class StockMarketCommands {
    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        StockMarketCommands.BACKEND_INSTANCES = backend;
    }

    private static IAsyncStockMarketCommandHandler handler() {
        return BACKEND_INSTANCES.COMMAND_HANDLER.getAsync();
    }
    private static IServerStockMarketCommandHandler masterHandler() {
        return BACKEND_INSTANCES.COMMAND_HANDLER.getSync();
    }
    private static boolean isMaster() {
        return BACKEND_INSTANCES.COMMAND_HANDLER.getSync() != null;
    }

    // Method to register commands
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext commandBuildContext, Commands.CommandSelection commandSelection) {


        // /StockMarket setPriceCandleTimeInterval <seconds>                            - Set the interval for the price candles. (Each candle will represent this amount of time)
        // /StockMarket createDefaultBots                                               - Create default bots
        // /StockMarket createDefaultBot <itemID>                                       - Create a default bot for that item if presets are available
        // /StockMarket order cancelAll                                                 - Cancel all order
        // /StockMarket order cancelAll <itemID>                                        - Cancel all order of an item
        // /StockMarket order <username> cancelAll                                      - Cancel all order of a player
        // /StockMarket order <username> cancelAll <itemID>                             - Cancel all order of a player for an item
        // /StockMarket BotSettingsGUI                                                  - Open the settings GUI for the stockmarket bots
        // /StockMarket ManagementGUI                                                   - Open the management GUI to create and remove trading items
        // /StockMarket <itemID> bot settings get                                       - Get bot settings
        // /StockMarket <itemID> bot settings set enabled                               - Enable bot
        // /StockMarket <itemID> bot settings set disabled                              - Disable bot
        // /StockMarket <itemID> bot settings set volatility <volatility>               - Set volatility
        // /StockMarket <itemID> bot settings set orderRandomness <randomness>          - Set scale for random stockmarket order
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
        // /StockMarket save                                                            - Save stockmarket data
        // /StockMarket load                                                            - Load stockmarket data


        dispatcher.register(Commands.literal("stockmarket")
                .then(Commands.literal("testdb")
                        .then(Commands.argument("table", StringArgumentType.string())
                                .suggests((x, y) -> {
                                    y.suggest("OrderRecord");
                                    y.suggest("MarketPrice");
                                    return y.buildFuture();
                                })

                                .then(Commands.argument("record_count", IntegerArgumentType.integer())
                                        .executes(context -> {
                                            int numRecords = IntegerArgumentType.getInteger(context, "record_count");
                                            String table = StringArgumentType.getString(context, "table");
                                            CommandSourceStack source = context.getSource();

                                            if ("OrderRecord".equals(table)) {
                                                List<OrderRecordStruct> exData = OrderRecordStruct.generateExampleData(numRecords);
                                                var manager = BACKEND_INSTANCES.ORDER_RECORD_MANAGER;
                                                long time = System.currentTimeMillis();
                                                manager.save(exData).thenRun(() -> {
                                                    long writeTime = System.currentTimeMillis() - time;
                                                    try {
                                                        source.sendSystemMessage(Component.literal("Database write for " + numRecords + " records took " + writeTime + " ms"));
                                                    } catch (Exception e) {
                                                    }
                                                    long time2 = System.currentTimeMillis();
                                                    manager.getHistory(Optional.of(new DateFilter(Long.MAX_VALUE, Long.MAX_VALUE)), Optional.empty(), Optional.empty(), Optional.empty())
                                                            .thenRun(() -> {
                                                                long readTime = System.currentTimeMillis() - time2;
                                                                try {
                                                                    source.sendSystemMessage(Component.literal("Database read for " + numRecords + " records took " + readTime + " ms"));
                                                                } catch (Exception e) {
                                                                }
                                                                long time3 = System.currentTimeMillis();
                                                                manager.removeHistory(Optional.of(new DateFilter(Long.MAX_VALUE, Long.MAX_VALUE)), Optional.empty(), Optional.empty(), Optional.empty())
                                                                        .thenRun(() -> {
                                                                            long deleteTime = System.currentTimeMillis() - time3;
                                                                            try {
                                                                                source.sendSystemMessage(Component.literal("Database delete for " + numRecords + " records took " + deleteTime + " ms"));
                                                                            } catch (Exception e) {
                                                                            }
                                                                        });
                                                            });

                                                });
                                            } else {
                                                List<MarketPriceStruct> exData = MarketPriceStruct.generateExampleData(numRecords);
                                                MarketPriceManager manager = BACKEND_INSTANCES.MARKET_PRICE_HISTORY_MANAGER;
                                                long time = System.currentTimeMillis();
                                                manager.save(exData).thenRun(() -> {
                                                    long writeTime = System.currentTimeMillis() - time;
                                                    try {
                                                        source.sendSystemMessage(Component.literal("Database write for " + numRecords + " records took " + writeTime + " ms"));
                                                    } catch (Exception e) {
                                                    }
                                                    long time2 = System.currentTimeMillis();
                                                    manager.getHistory(Optional.of(new DateFilter(Long.MAX_VALUE, Long.MAX_VALUE)), Optional.empty(), -1)
                                                            .thenRun(() -> {
                                                                long readTime = System.currentTimeMillis() - time2;
                                                                try {
                                                                    source.sendSystemMessage(Component.literal("Database read for " + numRecords + " records took " + readTime + " ms"));
                                                                } catch (Exception e) {
                                                                }
                                                                long time3 = System.currentTimeMillis();
                                                                manager.removeHistory(Optional.of(new DateFilter(Long.MAX_VALUE, Long.MAX_VALUE)), Optional.empty())
                                                                        .thenRun(() -> {
                                                                            long deleteTime = System.currentTimeMillis() - time3;
                                                                            try {
                                                                                source.sendSystemMessage(Component.literal("Database delete for " + numRecords + " records took " + deleteTime + " ms"));
                                                                            } catch (Exception e) {
                                                                            }
                                                                        });
                                                            });

                                                });
                                            }
                                            return 1;
                                        }))))
                .then(Commands.literal("db")
                        .then(Commands.argument("table", StringArgumentType.string())
                                .suggests((x, y) -> {
                                    y.suggest("OrderRecord");
                                    y.suggest("MarketPrice");
                                    return y.buildFuture();
                                })
                                .then(Commands.literal("count")
                                        .executes(context -> {
                                            String table = StringArgumentType.getString(context, "table");
                                            CommandSourceStack source = context.getSource();
                                            if ("OrderRecord".equals(table)) {
                                                BACKEND_INSTANCES.ORDER_RECORD_MANAGER.getRecordCount(Optional.empty(), Optional.empty())
                                                        .thenAccept(count -> source.sendSystemMessage(Component.literal("OrderRecord table currently has " + count + " records.")));
                                            } else if ("MarketPrice".equals(table)) {
                                                BACKEND_INSTANCES.MARKET_PRICE_HISTORY_MANAGER.getRecordCount(Optional.empty(), Optional.empty())
                                                        .thenAccept(count -> source.sendSystemMessage(Component.literal("MarketPrice table currently has " + count + " records.")));
                                            } else {
                                                source.sendFailure(Component.literal("Unknown table: " + table + " (expected OrderRecord or MarketPrice)"));
                                            }
                                            return 1;
                                        }))))
                .then(Commands.literal("op")
                        .requires(source -> source.hasPermission(2)) // Admin-only
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            ServerPlayer player = source.getPlayerOrException();
                            if (isMaster())
                                masterHandler().stockmarket_setStockmarketAdminMode(player.getUUID(), true);
                            else
                                ServerPlayerUtilities.printToClientConsole(player, "This command can only be used on the master server!");
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("username", StringArgumentType.string()).suggests((context, builder) -> getPlayerNamesSuggestion(builder))
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player = source.getPlayerOrException();
                                    String toPlayer = StringArgumentType.getString(context, "username");
                                    if (isMaster())
                                        masterHandler().stockmarket_setStockmarketAdminMode_user(player.getUUID(), toPlayer, true);
                                    else
                                        ServerPlayerUtilities.printToClientConsole(player, "This command can only be used on the master server!");
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .then(Commands.literal("deop")
                        .requires(source -> source.hasPermission(2)) // Admin-only
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            ServerPlayer player = source.getPlayerOrException();
                            if (isMaster())
                                masterHandler().stockmarket_setStockmarketAdminMode(player.getUUID(), false);
                            else
                                ServerPlayerUtilities.printToClientConsole(player, "This command can only be used on the master server!");
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("username", StringArgumentType.string()).suggests((context, builder) -> getPlayerNamesSuggestion(builder))
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player = source.getPlayerOrException();
                                    String toPlayer = StringArgumentType.getString(context, "username");
                                    if (isMaster())
                                        masterHandler().stockmarket_setStockmarketAdminMode_user(player.getUUID(), toPlayer, false);
                                    else
                                        ServerPlayerUtilities.printToClientConsole(player, "This command can only be used on the master server!");
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .then(Commands.literal("manage")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            ServerPlayer player = source.getPlayerOrException();
                            handler().stockmarket_manage_async(player.getUUID());
                            return Command.SINGLE_SUCCESS;
                        })

                )
                .then(Commands.literal("devTestScreen")
                        .requires(source -> source.hasPermission(2) && StockMarketMod.ENABLE_DEV_FEATURES)
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            ServerPlayer player = source.getPlayerOrException();
                            handler().stockmarket_devTestScreen_async(player.getUUID());
                            return Command.SINGLE_SUCCESS;
                        })

                )
                // Dev-only: export recipe images to PNG files
                .then(Commands.literal("exportrecipes")
                        .requires(source -> source.hasPermission(2) && StockMarketMod.ENABLE_DEV_FEATURES)
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            ServerPlayer player = source.getPlayerOrException();
                            OpenUIPacket.sendToClient(player, OpenUIPacket.GUIType.EXPORT_RECIPES);
                            return Command.SINGLE_SUCCESS;
                        })
                )
        );

        boolean isSlave = BACKEND_INSTANCES != null
                && BACKEND_INSTANCES.MARKET_MANAGER != null
                && BACKEND_INSTANCES.MARKET_MANAGER.getSync() == null;

        if (StockMarketMod.ENABLE_DEV_FEATURES)
            TestCommandRegistration.register(dispatcher, "stockmarket", "StockMarket", isSlave);
    }


    private static CompletableFuture<Suggestions> getPlayerNamesSuggestion(SuggestionsBuilder builder)
    {
        CompletableFuture<Suggestions> future = new CompletableFuture<>();
        BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getAsync().getBankManagerUserMapDataAsync().thenAccept(userMapData ->
        {
            userMapData.userMap().values().forEach(userData -> {
                builder.suggest("\""+ userData.userName() +"\"");
            });
            future.complete(builder.build());
        });
        return future;
    }
}

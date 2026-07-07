package net.kroia.stockmarket.minecraft.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ServerPlayerUtilities;
import net.kroia.stockmarket.api.command.IAsyncStockMarketCommandHandler;
import net.kroia.stockmarket.api.command.IServerStockMarketCommandHandler;
import net.kroia.modutilities.testing.TestCommandRegistration;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.networking.packet.OpenUIPacket;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


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
                // Dev-only: give a one-time starter kit of items for testing the stock market
                .then(Commands.literal("starterkit")
                        .requires(source -> StockMarketMod.ENABLE_DEV_FEATURES)
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            ServerPlayer player = source.getPlayerOrException();
                            if (isMaster())
                                masterHandler().stockmarket_starterKit(player.getUUID());
                            else
                                ServerPlayerUtilities.printToClientConsole(player, "This command can only be used on the master server!");
                            return Command.SINGLE_SUCCESS;
                        })
                )
                // /stockmarket <market> remove
                // Admin fallback to delete a market without the management GUI.
                // The market is addressed purely by its registry name (e.g. "minecraft:iron_ingot",
                // quotes required because of the ':') or by its numeric ItemID — never by an
                // ItemStack — so even "broken" markets whose item can no longer be resolved
                // (e.g. items with volatile data components) can be deleted.
                .then(Commands.argument("market", StringArgumentType.string())
                        .requires(source -> source.hasPermission(2)) // Admin-only, like the other management commands
                        .suggests((context, builder) -> getMarketNamesSuggestion(builder))
                        .then(Commands.literal("remove")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    String marketArg = StringArgumentType.getString(context, "market");
                                    removeMarket(source, marketArg);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
        );

        boolean isSlave = BACKEND_INSTANCES != null
                && BACKEND_INSTANCES.MARKET_MANAGER != null
                && BACKEND_INSTANCES.MARKET_MANAGER.getSync() == null;

        if (StockMarketMod.ENABLE_DEV_FEATURES)
            TestCommandRegistration.register(dispatcher, "stockmarket", "StockMarket", "stockmarket", isSlave);
    }


    /**
     * Resolves the given market identifier and deletes the matching market.
     * <p>
     * A market matches if its registry name (e.g. "minecraft:iron_ingot") or its
     * numeric ItemID equals {@code marketArg}. Broken markets whose item can no
     * longer be resolved report their numeric ItemID as their name, so they are
     * addressable by that number.
     * <p>
     * Uses the async market manager, so the command works on both master and slave
     * servers (slaves forward the existing DeleteMarket request to the master).
     * Feedback is sent to the command source as translatable messages once the
     * async operations complete.
     *
     * @param source    the command source to send success/failure feedback to
     * @param marketArg the market registry name or numeric ItemID entered by the user
     */
    private static void removeMarket(CommandSourceStack source, String marketArg)
    {
        MinecraftServer server = source.getServer();
        BACKEND_INSTANCES.MARKET_MANAGER.getAsync().getAvailableMarketIDsAsync().thenAccept(marketIDs -> {
            // Collect all markets matching the given name or numeric ItemID
            List<ItemID> matches = new ArrayList<>();
            for (ItemID marketID : marketIDs) {
                if (marketID.getName().equals(marketArg) || String.valueOf(marketID.getShort()).equals(marketArg)) {
                    matches.add(marketID);
                }
            }

            if (matches.isEmpty()) {
                server.execute(() -> source.sendFailure(
                        Component.translatable("message.stockmarket.command_market_remove_not_found", marketArg)));
                return;
            }
            if (matches.size() > 1) {
                // Multiple markets share this registry name (e.g. component variants).
                // List them as "name=id" pairs so the user can retry with the unique numeric ID.
                String candidates = matches.stream().map(ItemID::toString).collect(Collectors.joining(", "));
                server.execute(() -> source.sendFailure(
                        Component.translatable("message.stockmarket.command_market_remove_ambiguous", marketArg, candidates)));
                return;
            }

            ItemID target = matches.get(0);
            BACKEND_INSTANCES.MARKET_MANAGER.getAsync().deleteMarketAsync(target).thenAccept(success ->
                    server.execute(() -> {
                        if (success)
                            source.sendSuccess(() -> Component.translatable(
                                    "message.stockmarket.command_market_removed", target.getName()), true);
                        else
                            source.sendFailure(Component.translatable(
                                    "message.stockmarket.command_market_remove_failed", target.getName()));
                    }));
        });
    }

    /**
     * Suggests all existing market identifiers for the {@code <market>} command argument.
     * <p>
     * Registry names are suggested in quotes because they contain a ':' which the
     * brigadier string() argument type only accepts inside quotes. For markets that
     * share the same registry name the unique numeric ItemID is suggested as well,
     * so every market stays addressable. Broken markets (unresolvable item) report
     * their numeric ItemID as their name and are therefore suggested by number.
     *
     * @param builder the suggestion builder provided by brigadier
     * @return a future completing with the market name suggestions
     */
    private static CompletableFuture<Suggestions> getMarketNamesSuggestion(SuggestionsBuilder builder)
    {
        CompletableFuture<Suggestions> future = new CompletableFuture<>();
        BACKEND_INSTANCES.MARKET_MANAGER.getAsync().getAvailableMarketIDsAsync().thenAccept(marketIDs -> {
            // Count name occurrences to detect ambiguous registry names
            Map<String, Integer> nameCounts = new HashMap<>();
            for (ItemID marketID : marketIDs)
                nameCounts.merge(marketID.getName(), 1, Integer::sum);

            Set<String> suggestedNames = new HashSet<>();
            for (ItemID marketID : marketIDs) {
                String name = marketID.getName();
                if (suggestedNames.add(name))
                    builder.suggest("\"" + name + "\"");
                // Ambiguous names can't be removed by name alone — offer the unique numeric ID too
                if (nameCounts.get(name) > 1)
                    builder.suggest(String.valueOf(marketID.getShort()));
            }
            future.complete(builder.build());
        });
        return future;
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

package net.kroia.stockmarket.minecraft.command;

import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
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
import net.kroia.stockmarket.api.pluginmanager.IServerPluginManager;
import net.kroia.stockmarket.data.DataManager;
import net.kroia.stockmarket.networking.packet.OpenUIPacket;
import net.kroia.stockmarket.networking.request.NewsAdminRequest;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.pluginsystem.pluginmanager.ServerPluginManager;
import net.kroia.stockmarket.pluginsystem.plugins.NewsPlugin;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.kroia.stockmarket.stockmarket.market.preset.MarketPreset;
import net.kroia.stockmarket.stockmarket.market.preset.MarketPresetCategory;
import net.kroia.stockmarket.stockmarket.market.preset.MarketPresetManager;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

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
                // /stockmarket preset add <category> [name]
                // Captures the executing player's MAIN-HAND ItemStack as a new market preset.
                // Unlike the PresetEditorTab item picker (which builds stacks from the item
                // registry), this captures component-bearing survival items too: renamed items,
                // TFC food, enchanted gear, etc. The optional [name] sets a custom name on the
                // captured stack, which is how a preset's display name is represented; without
                // it the preset keeps the item's normal hover name.
                .then(Commands.literal("preset")
                        .requires(source -> source.hasPermission(2)) // Admin-only, like the other management commands
                        .then(Commands.literal("add")
                                .then(Commands.argument("category", StringArgumentType.string())
                                        .suggests((context, builder) -> getPresetCategoryNamesSuggestion(builder))
                                        .executes(context -> {
                                            addHeldItemPreset(context.getSource(),
                                                    StringArgumentType.getString(context, "category"), null);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> {
                                                    addHeldItemPreset(context.getSource(),
                                                            StringArgumentType.getString(context, "category"),
                                                            StringArgumentType.getString(context, "name"));
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        )
                )
                // /stockmarket news                              - open the newspaper screen (player-level)
                // /stockmarket news reload                       - reload the JSON event definitions (admin)
                // /stockmarket news trigger <eventId> [market]   - fire an event now, bypassing cooldown/weights (admin)
                // /stockmarket news list                         - loaded definitions + active events + cooldowns (admin)
                // /stockmarket news stop <eventId|all>           - hard-stop active event(s): influence removed in any phase, full cooldown restarts (admin, T-093 semantics)
                // /stockmarket news skipphase <eventId>          - fast-forward an active event to the start of its next phase (admin, T-093)
                // /stockmarket news info <eventId>               - full event details: texts, impact, matched markets (admin)
                // /stockmarket news enable <eventId>             - re-enable a disabled event (admin, T-081)
                // /stockmarket news disable <eventId>            - disable an event; it can never activate while disabled (admin, T-081)
                // /stockmarket news resetcooldown <eventId>      - clear an event's remaining cooldown (admin, T-085)
                // /stockmarket news scheduler show               - effective scheduler values + upcoming timeline (admin, T-082)
                // /stockmarket news scheduler set <key> <value>  - override one scheduler value (admin, T-082)
                // /stockmarket news scheduler reset [key]        - reset one/all overrides to the file values (admin, T-082)
                // /stockmarket news registry list                - world-event registry: fire records + custom key/values (admin, T-099)
                // /stockmarket news registry clear all           - wipe the whole registry (admin, T-099)
                // /stockmarket news registry clear <eventId>     - delete one event's fire record ("never fired" again) (admin, T-099)
                // /stockmarket news registry clear key <key>     - delete one custom key/value pair (admin, T-099)
                // All admin ops go through the single NewsAdminRequest (auto master-routing),
                // so they work identically on master and slave servers (T-076).
                .then(Commands.literal("news")
                        // No-args: player-level, NOT admin — opens the newspaper client-side
                        // via OpenUIPacket (commands run server-side, same pattern as
                        // /stockmarket exportrecipes and the management GUI open).
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            OpenUIPacket.sendToClient(player, OpenUIPacket.GUIType.NEWS);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.literal("reload")
                                .requires(source -> source.hasPermission(2)) // Admin-only, like preset
                                .executes(context -> {
                                    executeNewsAdminOp(context.getSource(),
                                            NewsAdminRequest.Op.RELOAD, null, null);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(Commands.literal("trigger")
                                .requires(source -> source.hasPermission(2)) // Admin-only, like preset
                                .then(Commands.argument("eventId", StringArgumentType.string())
                                        .suggests((context, builder) -> getNewsEventIdsSuggestion(builder))
                                        .executes(context -> {
                                            executeNewsAdminOp(context.getSource(), NewsAdminRequest.Op.TRIGGER,
                                                    StringArgumentType.getString(context, "eventId"), null);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                        .then(Commands.argument("market", StringArgumentType.string())
                                                .suggests((context, builder) -> getMarketNamesSuggestion(builder))
                                                .executes(context -> {
                                                    executeNewsAdminOp(context.getSource(), NewsAdminRequest.Op.TRIGGER,
                                                            StringArgumentType.getString(context, "eventId"),
                                                            StringArgumentType.getString(context, "market"));
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("list")
                                .requires(source -> source.hasPermission(2)) // Admin-only, like preset
                                .executes(context -> {
                                    executeNewsAdminOp(context.getSource(),
                                            NewsAdminRequest.Op.LIST, null, null);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(Commands.literal("stop")
                                .requires(source -> source.hasPermission(2)) // Admin-only, like preset
                                .then(Commands.argument("eventId", StringArgumentType.string())
                                        .suggests((context, builder) -> getActiveNewsEventIdsSuggestion(builder, true))
                                        .executes(context -> {
                                            executeNewsAdminOp(context.getSource(), NewsAdminRequest.Op.STOP,
                                                    StringArgumentType.getString(context, "eventId"), null);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        // T-093: fast-forward an active event out of its current phase
                        // (pending → ramp-up → hold → recovery → ended). Unlike stop —
                        // which cancels the event — skipping the last phase ends it
                        // normally (reversal:none bakes like a natural completion).
                        .then(Commands.literal("skipphase")
                                .requires(source -> source.hasPermission(2)) // Admin-only, like preset
                                .then(Commands.argument("eventId", StringArgumentType.string())
                                        // Active ids only — no "all" keyword for skipphase.
                                        .suggests((context, builder) -> getActiveNewsEventIdsSuggestion(builder, false))
                                        .executes(context -> {
                                            executeNewsAdminOp(context.getSource(), NewsAdminRequest.Op.SKIP_PHASE,
                                                    StringArgumentType.getString(context, "eventId"), null);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(Commands.literal("info")
                                .requires(source -> source.hasPermission(2)) // Admin-only, like preset
                                .then(Commands.argument("eventId", StringArgumentType.string())
                                        .suggests((context, builder) -> getNewsEventIdsSuggestion(builder))
                                        .executes(context -> {
                                            executeNewsAdminOp(context.getSource(), NewsAdminRequest.Op.INFO,
                                                    StringArgumentType.getString(context, "eventId"), null);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(Commands.literal("enable")
                                .requires(source -> source.hasPermission(2)) // Admin-only, like preset
                                .then(Commands.argument("eventId", StringArgumentType.string())
                                        // Only disabled ids are useful enable targets
                                        .suggests((context, builder) -> getNewsEventIdsSuggestionFiltered(builder, false))
                                        .executes(context -> {
                                            executeNewsAdminOp(context.getSource(), NewsAdminRequest.Op.SET_ENABLED,
                                                    StringArgumentType.getString(context, "eventId"), null, true);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(Commands.literal("disable")
                                .requires(source -> source.hasPermission(2)) // Admin-only, like preset
                                .then(Commands.argument("eventId", StringArgumentType.string())
                                        // Only enabled ids are useful disable targets
                                        .suggests((context, builder) -> getNewsEventIdsSuggestionFiltered(builder, true))
                                        .executes(context -> {
                                            executeNewsAdminOp(context.getSource(), NewsAdminRequest.Op.SET_ENABLED,
                                                    StringArgumentType.getString(context, "eventId"), null, false);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(Commands.literal("resetcooldown")
                                .requires(source -> source.hasPermission(2)) // Admin-only, like preset
                                .then(Commands.argument("eventId", StringArgumentType.string())
                                        .suggests((context, builder) -> getNewsEventIdsSuggestion(builder))
                                        .executes(context -> {
                                            executeNewsAdminOp(context.getSource(), NewsAdminRequest.Op.RESET_COOLDOWN,
                                                    StringArgumentType.getString(context, "eventId"), null);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        // T-082 scheduler subtree — all three verbs map onto the single
                        // SET_SCHEDULER op (show = pure query payload, reset = negative
                        // per-value sentinel / resetAll flag), so slave routing and the
                        // admin audit work exactly like the other news admin commands.
                        .then(Commands.literal("scheduler")
                                .requires(source -> source.hasPermission(2)) // Admin-only, like preset
                                .then(Commands.literal("show")
                                        .executes(context -> {
                                            executeNewsSchedulerOp(context.getSource(),
                                                    NewsAdminRequest.SchedulerInput.query());
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                                .then(Commands.literal("set")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .suggests((context, builder) -> getSchedulerKeySuggestion(builder))
                                                // longArg(1): 0/negative values are rejected by
                                                // brigadier already (negative = internal reset
                                                // sentinel, must never come from user input).
                                                .then(Commands.argument("value", LongArgumentType.longArg(1))
                                                        .executes(context -> {
                                                            executeNewsSchedulerSet(context.getSource(),
                                                                    StringArgumentType.getString(context, "key"),
                                                                    LongArgumentType.getLong(context, "value"));
                                                            return Command.SINGLE_SUCCESS;
                                                        })
                                                )
                                        )
                                )
                                .then(Commands.literal("reset")
                                        // No key: reset ALL overrides back to the file values.
                                        .executes(context -> {
                                            executeNewsSchedulerOp(context.getSource(),
                                                    new NewsAdminRequest.SchedulerInput(
                                                            null, null, null, null, true));
                                            return Command.SINGLE_SUCCESS;
                                        })
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .suggests((context, builder) -> getSchedulerKeySuggestion(builder))
                                                .executes(context -> {
                                                    // -1 = per-value reset sentinel (see SchedulerInput).
                                                    executeNewsSchedulerSet(context.getSource(),
                                                            StringArgumentType.getString(context, "key"), -1L);
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        )
                        // T-099 world-event registry subtree — both verbs map onto the
                        // appended REGISTRY_LIST/REGISTRY_CLEAR ops of the single
                        // NewsAdminRequest, so slave routing and the admin audit work
                        // exactly like the other news admin commands. The clear target
                        // travels in the eventId field; "key" mode is flagged via the
                        // (otherwise unused) market field — see NewsAdminRequest.
                        .then(Commands.literal("registry")
                                .requires(source -> source.hasPermission(2)) // Admin-only, like preset
                                .then(Commands.literal("list")
                                        .executes(context -> {
                                            executeNewsAdminOp(context.getSource(),
                                                    NewsAdminRequest.Op.REGISTRY_LIST, null, null);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                                .then(Commands.literal("clear")
                                        // Literal subtrees ("all", "key") take precedence
                                        // over the generic eventId argument in brigadier.
                                        .then(Commands.literal("all")
                                                .executes(context -> {
                                                    executeNewsAdminOp(context.getSource(),
                                                            NewsAdminRequest.Op.REGISTRY_CLEAR,
                                                            NewsAdminRequest.REGISTRY_CLEAR_ALL, null);
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                        .then(Commands.literal("key")
                                                .then(Commands.argument("key", StringArgumentType.string())
                                                        .executes(context -> {
                                                            executeNewsAdminOp(context.getSource(),
                                                                    NewsAdminRequest.Op.REGISTRY_CLEAR,
                                                                    StringArgumentType.getString(context, "key"),
                                                                    NewsAdminRequest.REGISTRY_CLEAR_KEY_MODE);
                                                            return Command.SINGLE_SUCCESS;
                                                        })
                                                )
                                        )
                                        .then(Commands.argument("eventId", StringArgumentType.string())
                                                .suggests((context, builder) -> getNewsEventIdsSuggestion(builder))
                                                .executes(context -> {
                                                    executeNewsAdminOp(context.getSource(),
                                                            NewsAdminRequest.Op.REGISTRY_CLEAR,
                                                            StringArgumentType.getString(context, "eventId"), null);
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        )
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
     * Captures the executing player's main-hand ItemStack as a new market preset.
     * <p>
     * The stack is serialized through {@link MarketPreset#serializeItemStack(ItemStack)},
     * which already normalizes volatile components (BankSystem's
     * {@code VolatileItemComponents.normalize}) — no extra normalization happens here.
     * The new preset receives the same default price/abundance values a GUI-created
     * preset gets (see {@code PresetEditorTab.onItemSelectedFromPicker}: 10.0 / 10.0)
     * and is persisted via {@link MarketPresetManager#addOrReplaceCategory}, the exact
     * server-side path the GUI's SaveCategory request lands in
     * ({@code AsyncPresetManager.Request.handleOnMasterServer}). Clients always pull
     * preset categories from the server when opening the editor/creation UI, so no
     * extra push is needed for them to see the new preset.
     * <p>
     * Preset editing is master-only (the preset request handler rejects SaveCategory
     * from slave servers), so on a slave this command fails with a translatable
     * message instead of writing to the slave's local preset files.
     *
     * @param source       the command source; must be a player (the main hand is read from it)
     * @param categoryName target preset category; created on the fly if it does not exist yet
     * @param presetName   optional display name for the preset — applied as a custom name
     *                     component on the captured stack; {@code null} keeps the item's
     *                     normal hover name
     */
    private static void addHeldItemPreset(CommandSourceStack source, String categoryName, @Nullable String presetName)
    {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("message.stockmarket.command_preset_add_not_player"));
            return;
        }
        if (!isMaster()) {
            // Preset persistence lives on the master; the GUI request path rejects
            // SaveCategory from slaves too. Fail cleanly instead of corrupting state.
            source.sendFailure(Component.translatable("message.stockmarket.command_preset_add_master_only"));
            return;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.translatable("message.stockmarket.command_preset_add_hand_empty"));
            return;
        }

        // Work on a copy so the player's actual stack is never mutated.
        ItemStack stack = held.copy();
        if (presetName != null && !presetName.isBlank()) {
            // The preset's display name is represented by the stack's custom name component.
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(presetName));
        }

        // Serialize (includes volatile-component normalization — do not normalize again).
        JsonObject serialized = MarketPreset.serializeItemStack(stack);
        String itemId = serialized.get("id").getAsString();
        if (itemId.equals("minecraft:air")) {
            source.sendFailure(Component.translatable("message.stockmarket.command_preset_add_failed"));
            return;
        }
        JsonObject components = serialized.has("components") ? serialized.getAsJsonObject("components") : null;
        // Same defaults as a preset created through the GUI item picker (PresetEditorTab).
        MarketPreset newPreset = new MarketPreset(itemId, components, 10.0f, 10.0f);
        String displayName = stack.getHoverName().getString();

        MarketPresetManager presetManager = BACKEND_INSTANCES.PRESET_MANAGER;
        MarketPresetCategory category = presetManager.getCategory(categoryName);
        boolean createdCategory = category == null;
        if (createdCategory) {
            category = new MarketPresetCategory(categoryName, List.of());
        }

        // Reject duplicates by unique key (itemId + component hash) — no silent replace.
        if (category.findPresetByKey(newPreset.getUniqueKey()) != null) {
            source.sendFailure(Component.translatable(
                    "message.stockmarket.command_preset_add_duplicate", displayName, categoryName));
            return;
        }

        category.getPresets().add(newPreset);
        // Same add/save path as the GUI's SaveCategory request handler: updates the
        // in-memory category list and writes the category's JSON file.
        presetManager.addOrReplaceCategory(category, DataManager.getPresetPath());

        if (createdCategory) {
            source.sendSuccess(() -> Component.translatable(
                    "message.stockmarket.command_preset_add_category_created", categoryName), true);
        }
        source.sendSuccess(() -> Component.translatable(
                "message.stockmarket.command_preset_added", displayName, categoryName), true);
    }

    /**
     * Suggests all existing preset category names for the {@code <category>} argument
     * of {@code /stockmarket preset add}.
     * <p>
     * Names are suggested in quotes (same pattern as {@link #getMarketNamesSuggestion})
     * so category names containing spaces or other special characters stay valid for
     * the brigadier string() argument type. The local sync preset manager is available
     * on both master and slave servers, so suggestions never need a network round-trip.
     *
     * @param builder the suggestion builder provided by brigadier
     * @return a future completing with the category name suggestions
     */
    private static CompletableFuture<Suggestions> getPresetCategoryNamesSuggestion(SuggestionsBuilder builder)
    {
        MarketPresetManager presetManager = BACKEND_INSTANCES == null ? null : BACKEND_INSTANCES.PRESET_MANAGER;
        if (presetManager != null) {
            for (MarketPresetCategory category : presetManager.getCategories()) {
                builder.suggest("\"" + category.getCategory() + "\"");
            }
        }
        return CompletableFuture.completedFuture(builder.build());
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

    /**
     * Runs one news admin operation (T-076) through the shared {@link NewsAdminRequest}
     * and prints the result to the command sender.
     * <p>
     * <b>Slave-awareness:</b> the request handles the topology itself
     * ({@link NewsAdminRequest#sendFromServerCommand}): on the master it executes
     * directly, on a slave it is forwarded to the master with the executing player's
     * UUID — so the same command works on both server types. The response future may
     * complete on a network thread, so all feedback is re-dispatched onto the server
     * main thread before printing.
     * <p>
     * The response {@code message}/{@code lines} are server-generated admin diagnostics
     * (validation report entries, event ids, factors) and are printed literally — only
     * the local failure wrappers are translatable, mirroring the preset command style.
     *
     * @param source  the command source (must be a player — the admin check needs an identity)
     * @param op      the operation to run
     * @param eventId the event id argument, or null when the op takes none
     * @param market  the optional market restriction (TRIGGER), or null
     */
    private static void executeNewsAdminOp(CommandSourceStack source, NewsAdminRequest.Op op,
                                           @Nullable String eventId, @Nullable String market)
    {
        executeNewsAdminOp(source, op, eventId, market, false);
    }

    /**
     * Same as {@link #executeNewsAdminOp(CommandSourceStack, NewsAdminRequest.Op, String, String)}
     * but with the target state for {@link NewsAdminRequest.Op#SET_ENABLED}
     * ({@code /stockmarket news enable|disable <eventId>}, T-081). All other ops ignore
     * the flag.
     *
     * @param source  the command source (must be a player — the admin check needs an identity)
     * @param op      the operation to run
     * @param eventId the event id argument, or null when the op takes none
     * @param market  the optional market restriction (TRIGGER), or null
     * @param enabled the target enabled state (SET_ENABLED only)
     */
    private static void executeNewsAdminOp(CommandSourceStack source, NewsAdminRequest.Op op,
                                           @Nullable String eventId, @Nullable String market,
                                           boolean enabled)
    {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("message.stockmarket.command_news_not_player"));
            return;
        }
        // On a slave without a master connection the request could never be answered —
        // fail fast with the standard "not connected" console message.
        if (!MultiServerUtils.canInteractWithStockMarket(player.getUUID())) {
            return;
        }

        MinecraftServer server = source.getServer();
        BACKEND_INSTANCES.NETWORKING.NEWS_ADMIN_REQUEST
                .sendFromServerCommand(player, op, eventId, market, enabled)
                .whenComplete((response, throwable) -> server.execute(() -> {
                    if (throwable != null || response == null) {
                        // Timeout / transport failure (e.g. master connection dropped mid-request).
                        source.sendFailure(Component.translatable(
                                "message.stockmarket.command_news_request_failed"));
                        return;
                    }
                    if (response.success()) {
                        source.sendSuccess(() -> Component.literal(response.message()), false);
                    } else {
                        source.sendFailure(Component.literal(response.message()));
                    }
                    for (String line : response.lines()) {
                        source.sendSuccess(() -> Component.literal(line), false);
                    }
                }));
    }

    /** The scheduler override keys accepted by {@code /stockmarket news scheduler set|reset} (T-082). */
    private static final String[] SCHEDULER_KEYS = {
            "minSecondsBetweenEvents", "maxSecondsBetweenEvents",
            "maxActiveEventsGlobal", "maxActiveEventsPerMarket"};

    /**
     * Maps one {@code /stockmarket news scheduler set|reset <key> <value>} pair onto a
     * {@link NewsAdminRequest.SchedulerInput} and runs it — or fails locally with the
     * valid key list when the key is unknown (no server round-trip for a typo).
     * A negative {@code value} is the per-value reset sentinel; positive values are
     * range-validated server-side ({@code 0 < min <= max}, caps {@code >= 1}).
     *
     * @param source the command source (must be a player)
     * @param key    the scheduler value name (one of {@link #SCHEDULER_KEYS})
     * @param value  the value to set, or a negative number to reset that value
     */
    private static void executeNewsSchedulerSet(CommandSourceStack source, String key, long value)
    {
        // Caps are ints on the wire — clamp instead of overflowing for absurd inputs.
        Integer intValue = (int) Math.min(Integer.MAX_VALUE, Math.max(Integer.MIN_VALUE, value));
        NewsAdminRequest.SchedulerInput change = switch (key) {
            case "minSecondsBetweenEvents" ->
                    new NewsAdminRequest.SchedulerInput(value, null, null, null, false);
            case "maxSecondsBetweenEvents" ->
                    new NewsAdminRequest.SchedulerInput(null, value, null, null, false);
            case "maxActiveEventsGlobal" ->
                    new NewsAdminRequest.SchedulerInput(null, null, intValue, null, false);
            case "maxActiveEventsPerMarket" ->
                    new NewsAdminRequest.SchedulerInput(null, null, null, intValue, false);
            default -> null;
        };
        if (change == null) {
            source.sendFailure(Component.literal("Unknown scheduler key '" + key
                    + "' — valid keys: " + String.join(", ", SCHEDULER_KEYS)));
            return;
        }
        executeNewsSchedulerOp(source, change);
    }

    /**
     * Runs one T-082 scheduler operation ({@link NewsAdminRequest.Op#SET_SCHEDULER})
     * through the shared request — same slave routing, admin check and main-thread
     * feedback dispatch as {@link #executeNewsAdminOp}.
     *
     * @param source the command source (must be a player — the admin check needs an identity)
     * @param change the override change, or {@link NewsAdminRequest.SchedulerInput#query()}
     *               for a pure state display ({@code scheduler show})
     */
    private static void executeNewsSchedulerOp(CommandSourceStack source,
                                               NewsAdminRequest.SchedulerInput change)
    {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("message.stockmarket.command_news_not_player"));
            return;
        }
        // On a slave without a master connection the request could never be answered —
        // fail fast with the standard "not connected" console message.
        if (!MultiServerUtils.canInteractWithStockMarket(player.getUUID())) {
            return;
        }

        MinecraftServer server = source.getServer();
        BACKEND_INSTANCES.NETWORKING.NEWS_ADMIN_REQUEST
                .sendSchedulerFromServerCommand(player, change)
                .whenComplete((response, throwable) -> server.execute(() -> {
                    if (throwable != null || response == null) {
                        source.sendFailure(Component.translatable(
                                "message.stockmarket.command_news_request_failed"));
                        return;
                    }
                    if (response.success()) {
                        source.sendSuccess(() -> Component.literal(response.message()), false);
                    } else {
                        source.sendFailure(Component.literal(response.message()));
                    }
                    for (String line : response.lines()) {
                        source.sendSuccess(() -> Component.literal(line), false);
                    }
                }));
    }

    /**
     * Suggests the four scheduler override keys for
     * {@code /stockmarket news scheduler set|reset} (T-082). Keys are static — no
     * master/slave lookup needed.
     *
     * @param builder the suggestion builder provided by brigadier
     * @return a future completing with the key suggestions
     */
    private static CompletableFuture<Suggestions> getSchedulerKeySuggestion(SuggestionsBuilder builder)
    {
        for (String key : SCHEDULER_KEYS) {
            builder.suggest(key);
        }
        return CompletableFuture.completedFuture(builder.build());
    }

    /**
     * Finds the local NewsPlugin instance for tab-completion. Only available on the
     * master server (slaves have no sync plugin manager) — suggestion helpers fall
     * back to no/minimal suggestions there, see {@link #getNewsEventIdsSuggestion}.
     *
     * @return the NewsPlugin, or null on a slave server / when no instance exists
     */
    private static @Nullable NewsPlugin findLocalNewsPlugin()
    {
        if (BACKEND_INSTANCES == null || BACKEND_INSTANCES.PLUGIN_MANAGER == null) return null;
        IServerPluginManager syncManager = BACKEND_INSTANCES.PLUGIN_MANAGER.getSync();
        if (!(syncManager instanceof ServerPluginManager pluginManager)) return null;
        for (ServerPlugin<?, ?> plugin : pluginManager.getPlugins().values()) {
            if (plugin instanceof NewsPlugin newsPlugin) {
                return newsPlugin;
            }
        }
        return null;
    }

    /**
     * Suggests the loaded news event definition ids for {@code /stockmarket news trigger}.
     * <p>
     * Suggestions come from the master's local NewsPlugin library. On a <b>slave</b> server
     * the definitions are not locally known (the library lives on the master and there is
     * no suggestion round-trip), so this deliberately falls back to no suggestions — the
     * command itself still works there via the request routing.
     *
     * @param builder the suggestion builder provided by brigadier
     * @return a future completing with the event id suggestions
     */
    private static CompletableFuture<Suggestions> getNewsEventIdsSuggestion(SuggestionsBuilder builder)
    {
        NewsPlugin plugin = findLocalNewsPlugin();
        if (plugin != null) {
            for (String id : plugin.getLibrary().getDefinitions().keySet()) {
                builder.suggest(quoteIfNeeded(id));
            }
        }
        return CompletableFuture.completedFuture(builder.build());
    }

    /**
     * Suggests news event ids filtered by their enabled state (T-081): for
     * {@code /stockmarket news disable} the currently <b>enabled</b> loaded ids, for
     * {@code /stockmarket news enable} the currently <b>disabled</b> ids — including
     * disabled ids whose definition is absent from the library, so a stale disabled
     * state stays discoverable and clearable. Same slave fallback as
     * {@link #getNewsEventIdsSuggestion} (no suggestions there).
     *
     * @param builder        the suggestion builder provided by brigadier
     * @param suggestEnabled true to suggest enabled ids (disable command),
     *                       false to suggest disabled ids (enable command)
     * @return a future completing with the filtered event id suggestions
     */
    private static CompletableFuture<Suggestions> getNewsEventIdsSuggestionFiltered(
            SuggestionsBuilder builder, boolean suggestEnabled)
    {
        NewsPlugin plugin = findLocalNewsPlugin();
        if (plugin != null) {
            if (suggestEnabled) {
                for (String id : plugin.getLibrary().getDefinitions().keySet()) {
                    if (plugin.isEventEnabled(id)) {
                        builder.suggest(quoteIfNeeded(id));
                    }
                }
            } else {
                for (String id : plugin.getDisabledEventIds()) {
                    builder.suggest(quoteIfNeeded(id));
                }
            }
        }
        return CompletableFuture.completedFuture(builder.build());
    }

    /**
     * Suggests the currently active event ids — optionally prefixed with the
     * {@code "all"} keyword — for {@code /stockmarket news stop} (with {@code "all"})
     * and {@code /stockmarket news skipphase} (ids only, T-093). Same slave fallback
     * as {@link #getNewsEventIdsSuggestion} (only {@code "all"} — when included — is
     * suggested there).
     *
     * @param builder           the suggestion builder provided by brigadier
     * @param includeAllKeyword true to additionally suggest {@link NewsAdminRequest#STOP_ALL}
     * @return a future completing with the suggestions
     */
    private static CompletableFuture<Suggestions> getActiveNewsEventIdsSuggestion(SuggestionsBuilder builder,
                                                                                  boolean includeAllKeyword)
    {
        if (includeAllKeyword) {
            builder.suggest(NewsAdminRequest.STOP_ALL);
        }
        NewsPlugin plugin = findLocalNewsPlugin();
        if (plugin != null) {
            Set<String> suggested = new HashSet<>();
            for (net.kroia.stockmarket.news.ActiveNewsEvent event : plugin.getActiveEvents()) {
                if (suggested.add(event.getDefinitionId())) {
                    builder.suggest(quoteIfNeeded(event.getDefinitionId()));
                }
            }
        }
        return CompletableFuture.completedFuture(builder.build());
    }

    /**
     * Quotes a suggestion when it contains characters the brigadier string() argument
     * type only accepts inside quotes (anything outside {@code [A-Za-z0-9_.+-]}).
     * Event ids are usually simple identifiers, but they are admin-defined free text.
     */
    private static String quoteIfNeeded(String value)
    {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z') || c == '_' || c == '-' || c == '.' || c == '+';
            if (!allowed) {
                return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            }
        }
        return value;
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

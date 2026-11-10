package net.kroia.stockmarket.minecraft.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.architectury.event.events.common.TickEvent;
import net.kroia.banksystem.api.MoneyFormat;
import net.kroia.banksystem.api.bank.IServerBank;
import net.kroia.banksystem.api.bankaccount.IServerBankAccount;
import net.kroia.banksystem.api.bankmanager.IServerBankManager;
import net.kroia.banksystem.banking.binding.BankAccountBindings;
import net.kroia.banksystem.minecraft.item.custom.money.MoneyItem;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.placeholder.PlaceholderRegistry;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.marketmanager.ServerMarketManager;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.zip.CRC32;

/**
 * StockMarket text placeholders, bridged to TAB / Patbox Placeholder API through ModUtilities'
 * {@link PlaceholderRegistry} (namespace {@code stockmarket}).
 * <p>
 * Server placeholders per trading pair: {@code price_<pair>}, {@code change_<pair>}, {@code volume_<pair>};
 * player placeholders: {@code open_orders}, {@code portfolio}. Every one also has a {@code _raw} variant.
 * <p>
 * <b>Threading:</b> placeholder functions may run off the server thread, so they only do map lookups
 * on {@link #serverValues} / {@link #playerValues}. All values are computed on the server thread in a
 * wall-clock gated {@link TickEvent#SERVER_POST} handler: the master computes them from the markets,
 * slaves pull them from the master via {@code PlaceholderSnapshotRequest}.
 */
public final class StockMarketPlaceholders {

    public static final String NAMESPACE = "stockmarket";
    /** Cache refresh period and the refresh interval handed to the placeholder backends. */
    public static final int REFRESH_MS = 2000;
    private static final long WINDOW_MS = 24L * 60 * 60 * 1000;
    private static final long WINDOW_QUERY_MS = 60_000;
    private static final long REMOTE_INTEREST_MS = 10_000;
    private static final String[] PLAYER_NAMES = {"open_orders", "open_orders_raw", "portfolio", "portfolio_raw"};
    /**
     * T-240: prefix of the per-market rows the master adds to a player's values: key = prefix + market ItemID
     * short, value = that market's share of the portfolio in raw money units (a long). They let a slave leave out
     * markets it cannot resolve (see {@link #withoutUnresolvableMarkets}); no placeholder is registered for them.
     */
    private static final String PORTFOLIO_PART_PREFIX = "portfolio_part_";

    private static volatile StockMarketModBackend.ServerInstances BACKEND;

    /** Market placeholder name -> value. Immutable snapshot, swapped atomically. */
    static volatile Map<String, String> serverValues = Map.of();
    /** Player UUID -> (player placeholder name -> value). */
    static final ConcurrentHashMap<UUID, Map<String, String>> playerValues = new ConcurrentHashMap<>();
    /** (Master) UUIDs requested by slaves -> expiry time (ms). */
    private static final ConcurrentHashMap<UUID, Long> remoteInterest = new ConcurrentHashMap<>();
    /** (Master) marketid -> {oldestOpen, tradedVolumeSum} of the last 24h, from the DB. */
    private static volatile Map<Short, double[]> window24h = Map.of();

    /** Names already handed to the registry; it has no unregister, so this outlives server restarts. */
    private static final Set<String> REGISTERED = ConcurrentHashMap.newKeySet();
    /** Pair-name collisions already logged, to warn once instead of every refresh. */
    private static final Set<String> WARNED_COLLISIONS = ConcurrentHashMap.newKeySet();
    /** (Master) ItemID -> {fullKey, pairName}; naming needs codec work, so cache it. */
    private static final Map<ItemID, String[]> NAME_CACHE = new HashMap<>();

    private static final TickEvent.Server TICK = StockMarketPlaceholders::onServerTick;
    private static long lastRefreshMs = 0;
    private static long lastWindowQueryMs = 0;
    private static long slaveRequestSentMs = 0;
    private static volatile boolean slaveRequestInFlight = false;

    private StockMarketPlaceholders() {}

    /**
     * Wires the placeholders for the current server. Called once per server start from
     * {@code StockMarketModBackend} after the server instances exist (master and slave).
     * Registers the player placeholders (once per JVM) and the refresh tick handler.
     *
     * @param backend the server instances of the starting server
     */
    public static void init(StockMarketModBackend.ServerInstances backend) {
        BACKEND = backend;
        for (String name : PLAYER_NAMES) {
            if (REGISTERED.add(name)) {
                PlaceholderRegistry.registerPlayer(NAMESPACE, name, REFRESH_MS, player -> {
                    Map<String, String> values = playerValues.get(player.getUUID());
                    return values == null ? "" : values.getOrDefault(name, "");
                });
            }
        }
        if (!TickEvent.SERVER_POST.isRegistered(TICK))
            TickEvent.SERVER_POST.register(TICK);
    }

    /**
     * Unregisters the tick handler and clears all cached values. Called from {@code onServerStop}.
     * Registered placeholders stay (no unregister API) and simply return "" until the next start.
     */
    public static void shutdown() {
        TickEvent.SERVER_POST.unregister(TICK);
        BACKEND = null;
        serverValues = Map.of();
        window24h = Map.of();
        playerValues.clear();
        remoteInterest.clear();
        NAME_CACHE.clear();
        lastRefreshMs = 0;
        lastWindowQueryMs = 0;
        slaveRequestInFlight = false;
    }

    /** @return the current market placeholder snapshot (thread-safe, immutable). */
    public static Map<String, String> getServerValues() {
        return serverValues;
    }

    /**
     * (Master, any thread) Returns the cached player values for the given UUIDs and marks them as
     * remote interest so the next master refresh keeps computing them for {@link #REMOTE_INTEREST_MS}.
     *
     * @param uuids players online on the requesting slave
     * @return UUID -> values for every UUID that already has values
     */
    public static Map<UUID, Map<String, String>> snapshotForRemote(List<UUID> uuids) {
        long expiry = System.currentTimeMillis() + REMOTE_INTEREST_MS;
        Map<UUID, Map<String, String>> out = new HashMap<>();
        for (UUID uuid : uuids) {
            remoteInterest.put(uuid, expiry);
            Map<String, String> values = playerValues.get(uuid);
            if (values != null)
                out.put(uuid, values);
        }
        return out;
    }

    // ------------------------------------------------------------------ tick

    private static void onServerTick(MinecraftServer server) {
        StockMarketModBackend.ServerInstances backend = BACKEND;
        if (backend == null || backend.MARKET_MANAGER == null)
            return;
        long now = System.currentTimeMillis();
        if (now - lastRefreshMs < REFRESH_MS)
            return;
        lastRefreshMs = now;
        try {
            if (backend.MARKET_MANAGER.isMaster())
                refreshMaster(backend, server, now);
            else
                refreshSlave(backend, server, now);
            registerNewServerPlaceholders();
        } catch (Exception e) {
            StockMarketMod.LOGGER.error("[Placeholders] Refresh failed", e);
        }
    }

    private static void refreshMaster(StockMarketModBackend.ServerInstances backend, MinecraftServer server, long now) {
        ServerMarketManager smm = backend.MARKET_MANAGER.getServerMarketManager();
        if (smm == null)
            return;

        // 1. market values
        ItemID currency = smm.getTradingCurrencyID();
        Map<String, String> fullKeyToName = new HashMap<>();
        Map<String, ServerMarket> fullKeyToMarket = new HashMap<>();
        for (ItemID id : smm.getAvailableMarketIDs()) {
            if (id.equals(currency))
                continue;
            ServerMarket market = smm.getServerMarket(id);
            String[] naming = NAME_CACHE.computeIfAbsent(id, k -> naming(k.getStackTemplate(), server.registryAccess()));
            if (market == null || naming == null)
                continue;
            fullKeyToName.put(naming[0], naming[1]);
            fullKeyToMarket.put(naming[0], market);
        }
        Map<Short, double[]> window = window24h;
        Map<String, String> values = new HashMap<>();
        resolveCollisions(fullKeyToName, msg -> {
            if (WARNED_COLLISIONS.add(msg))
                StockMarketMod.LOGGER.warn("[Placeholders] {}", msg);
        }).forEach((name, fullKey) -> {
            ServerMarket market = fullKeyToMarket.get(fullKey);
            long price = market.getCurrentMarketPrice();
            double[] stats = window.get(market.getItemID().getShort());
            double ref = stats == null ? 0 : stats[0];
            double change = ref > 0 ? (price - ref) / ref * 100.0 : 0.0;
            long volume = Math.round((stats == null ? 0 : stats[1]) + market.getCurrentCandleTradedVolume());
            values.put("price_" + name, formatMoney(price));
            values.put("price_" + name + "_raw", MoneyFormat.formatMoneyRaw(price));
            values.put("change_" + name, String.format(Locale.ROOT, "%+.2f%%", change));
            values.put("change_" + name + "_raw", String.format(Locale.ROOT, "%.2f", change));
            values.put("volume_" + name, formatQuantity(volume));
            values.put("volume_" + name + "_raw", MoneyFormat.formatMoneyRaw(volume));
        });
        serverValues = Map.copyOf(values);

        // 2. open-order counts, one pass over all order books
        Map<UUID, Integer> openOrders = new HashMap<>();
        for (ItemID id : smm.getAvailableMarketIDs()) {
            ServerMarket market = smm.getServerMarket(id);
            if (market == null)
                continue;
            for (Order order : market.getLimitOrders()) {
                UUID owner = order.getExecutorPlayerUUID();
                if (!order.isBotOrder() && owner != null)
                    openOrders.merge(owner, 1, Integer::sum);
            }
        }

        // 3. player values for local online players + remote interest
        remoteInterest.values().removeIf(expiry -> expiry < now);
        Set<UUID> wanted = new HashSet<>(remoteInterest.keySet());
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            wanted.add(player.getUUID());
        IServerBankManager bankManager = backend.BANK_SYSTEM_API.getServerBankManager().getSync();
        for (UUID uuid : wanted) {
            int orders = openOrders.getOrDefault(uuid, 0) + smm.getPendingInterMarketOrdersForPlayer(uuid).size();
            Map<ItemID, Long> parts = bankManager == null ? Map.of() : portfolioParts(bankManager, smm, uuid);
            playerValues.put(uuid, playerValueMap(orders, parts));
        }
        // 4. drop players that are neither online nor of interest
        playerValues.keySet().retainAll(wanted);

        // 5. 24h window aggregate from the DB, refreshed in the background
        if (backend.MARKET_PRICE_HISTORY_MANAGER != null && now - lastWindowQueryMs >= WINDOW_QUERY_MS) {
            lastWindowQueryMs = now;
            backend.MARKET_PRICE_HISTORY_MANAGER.getWindowStats(now - WINDOW_MS).thenAccept(result -> {
                if (BACKEND == backend)
                    window24h = result;
            });
        }
    }

    /**
     * Item value of a player's personal account in raw money units, per market: for every non-money bank,
     * (balance + locked) * market price / raw-units-per-item. The sum mirrors BankSystem's Total Wealth
     * (ServerBankManager.collectBalanceSnapshot) without the cash part. Items without a market count 0.
     *
     * @return market -> its non-zero share of the portfolio value
     */
    private static Map<ItemID, Long> portfolioParts(IServerBankManager bankManager, ServerMarketManager smm, UUID uuid) {
        int accountNr = bankManager.getPersonalBankAccountNr(uuid);
        IServerBankAccount account = bankManager.getBankAccount(accountNr);
        if (account == null)
            return Map.of();
        Map<ItemID, Long> parts = new HashMap<>();
        for (Map.Entry<ItemID, IServerBank> entry : account.getAllBanks().entrySet()) {
            ItemID itemID = entry.getKey();
            IServerBank bank = entry.getValue();
            if (bank == null || MoneyItem.isMoney(itemID))
                continue;
            ServerMarket market = smm.getServerMarket(itemID);
            if (market == null)
                continue;
            long price = market.getCurrentMarketPrice();
            long part = price > 0 ? (bank.getBalance() + bank.getLockedBalance()) * price
                    / BankAccountBindings.getRawUnitsPerItem(accountNr, itemID) : 0;
            if (part != 0)
                parts.put(itemID, part);
        }
        return parts;
    }

    /**
     * (Master) Builds a player's placeholder values. The portfolio is the sum of all {@code parts}; each part is
     * also stored as a {@link #PORTFOLIO_PART_PREFIX} row so a slave can leave out markets it cannot resolve
     * (T-240, see {@link #withoutUnresolvableMarkets}).
     *
     * @param openOrders the player's open order count
     * @param parts      market -> its share of the portfolio value in raw money units
     * @return placeholder name -> value (immutable)
     */
    public static Map<String, String> playerValueMap(int openOrders, Map<ItemID, Long> parts) {
        Map<String, String> values = new HashMap<>();
        long portfolio = 0;
        for (Map.Entry<ItemID, Long> part : parts.entrySet()) {
            portfolio += part.getValue();
            values.put(PORTFOLIO_PART_PREFIX + part.getKey().getShort(), String.valueOf(part.getValue()));
        }
        values.put("open_orders", String.valueOf(openOrders));
        values.put("open_orders_raw", String.valueOf(openOrders));
        putPortfolio(values, portfolio);
        return Map.copyOf(values);
    }

    /**
     * (Slave) T-240: the player's values with the portfolio summed again without the markets this server cannot
     * resolve (items only the master has, see {@link MultiServerUtils#isMarketResolvableOnThisServer}), so the
     * portfolio matches the markets the slave shows. Lookup only, never registers an ItemID.
     *
     * @param values a player's values from the master, with {@link #PORTFOLIO_PART_PREFIX} rows
     * @return {@code values} itself if every market resolves here, otherwise a copy with the reduced portfolio
     */
    public static Map<String, String> withoutUnresolvableMarkets(Map<String, String> values) {
        long portfolio = 0;
        boolean hidden = false;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!entry.getKey().startsWith(PORTFOLIO_PART_PREFIX))
                continue;
            ItemID marketID = new ItemID(Short.parseShort(entry.getKey().substring(PORTFOLIO_PART_PREFIX.length())));
            if (MultiServerUtils.isMarketResolvableOnThisServer(marketID))
                portfolio += Long.parseLong(entry.getValue());
            else
                hidden = true;
        }
        if (!hidden)
            return values; // the master's strings, unchanged
        Map<String, String> filtered = new HashMap<>(values);
        putPortfolio(filtered, portfolio);
        return filtered;
    }

    /** Writes the formatted and raw portfolio value into {@code values}. */
    private static void putPortfolio(Map<String, String> values, long portfolio) {
        values.put("portfolio", formatMoney(portfolio));
        values.put("portfolio_raw", MoneyFormat.formatMoneyRaw(portfolio));
    }

    private static void refreshSlave(StockMarketModBackend.ServerInstances backend, MinecraftServer server, long now) {
        List<UUID> online = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            online.add(player.getUUID());
        playerValues.keySet().retainAll(online);

        // The ARRS fails an unanswered request after 30 s (whenComplete below clears the flag), but the
        // poll treats a request older than 10 s as lost already so a slow answer does not stall the refresh.
        if (slaveRequestInFlight && now - slaveRequestSentMs < REMOTE_INTEREST_MS)
            return;
        if (!MultiServerUtils.canInteractWithStockMarket())
            return;
        slaveRequestInFlight = true;
        slaveRequestSentMs = now;
        backend.NETWORKING.PLACEHOLDER_SNAPSHOT_REQUEST.sendRequestToMaster(online).whenComplete((out, ex) -> {
            slaveRequestInFlight = false;
            if (ex != null || out == null || BACKEND != backend)
                return;
            serverValues = Map.copyOf(out.market());
            // T-240: the portfolio leaves out markets this slave cannot resolve, like its market lists
            out.players().forEach((uuid, values) -> playerValues.put(uuid, withoutUnresolvableMarkets(values)));
        });
    }

    /** Registers server placeholders for keys that appeared in {@link #serverValues} since the last refresh. */
    private static void registerNewServerPlaceholders() {
        for (String name : serverValues.keySet()) {
            if (REGISTERED.add(name))
                PlaceholderRegistry.registerServer(NAMESPACE, name, REFRESH_MS, () -> serverValues.getOrDefault(name, ""));
        }
    }

    // ------------------------------------------------------------ formatting

    /** Single funnel for formatted money output (BankSystem MONEY_FORMAT, e.g. "$1'234.56"). */
    private static String formatMoney(long raw) {
        return MoneyFormat.formatMoney(raw);
    }

    /** Item quantity in the money number style without currency pattern, e.g. "1'234.56". */
    private static String formatQuantity(long raw) {
        return MoneyFormat.formatMoney(raw, "%s");
    }

    // ---------------------------------------------------------------- naming

    /**
     * Computes {fullKey, pairName} for a market item stack, or {@code null} for an empty stack.
     * The component part uses the canonical (key-sorted) JSON of the stack's {@link DataComponentPatch}.
     */
    private static @Nullable String[] naming(ItemStack stack, RegistryAccess registryAccess) {
        if (stack == null || stack.isEmpty())
            return null;
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String componentJson = null;
        DataComponentPatch patch = stack.getComponentsPatch();
        if (!patch.isEmpty()) {
            componentJson = DataComponentPatch.CODEC
                    .encodeStart(registryAccess.createSerializationContext(JsonOps.INSTANCE), patch)
                    .result()
                    .map(json -> canonicalJson(json).toString())
                    .orElse(patch.toString());
        }
        return new String[]{componentJson == null ? itemId : itemId + componentJson, pairName(itemId, componentJson)};
    }

    /**
     * Builds the stable, server-independent pair name used in placeholder ids (never savegame ItemIDs).
     * <ul>
     *   <li>{@code minecraft:x} becomes {@code x}, any other {@code ns:path} becomes {@code ns_path};</li>
     *   <li>lowercased, every character outside {@code [a-z0-9_]} replaced by {@code _};</li>
     *   <li>if {@code componentJson} is non-empty, {@code _} + 6 lowercase hex chars of its CRC32 is appended.</li>
     * </ul>
     * The hash suffix is only stable within one Minecraft version: component encodings may change
     * between versions, so names of items with components (enchanted books, potions) may differ after an update.
     *
     * @param itemId        item resource id, e.g. {@code minecraft:diamond}
     * @param componentJson canonical key-sorted JSON of the stack's component patch, or {@code null}/empty if none
     * @return the pair name, matching {@code [a-z0-9_]+}
     */
    public static String pairName(String itemId, @Nullable String componentJson) {
        String base = itemId.startsWith("minecraft:") ? itemId.substring("minecraft:".length()) : itemId.replace(':', '_');
        base = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (componentJson == null || componentJson.isEmpty())
            return base;
        CRC32 crc = new CRC32();
        crc.update(componentJson.getBytes(StandardCharsets.UTF_8));
        return base + "_" + String.format(Locale.ROOT, "%06x", crc.getValue() & 0xFFFFFF);
    }

    /**
     * Returns a deep copy of {@code json} with every object's keys sorted, so equal data always
     * serializes to the same string.
     *
     * @param json any JSON element
     * @return the canonical copy
     */
    public static JsonElement canonicalJson(JsonElement json) {
        if (json.isJsonObject()) {
            JsonObject sorted = new JsonObject();
            new TreeMap<>(json.getAsJsonObject().asMap()).forEach((k, v) -> sorted.add(k, canonicalJson(v)));
            return sorted;
        }
        if (json.isJsonArray()) {
            JsonArray array = new JsonArray();
            json.getAsJsonArray().forEach(e -> array.add(canonicalJson(e)));
            return array;
        }
        return json;
    }

    /**
     * Resolves pair-name collisions: candidates are processed sorted by full key, the first one
     * claims a name, later ones with the same name are skipped and reported.
     *
     * @param fullKeyToName full key (item id + component JSON) -> sanitized pair name
     * @param warn          receives one message per skipped candidate
     * @return pair name -> winning full key
     */
    public static Map<String, String> resolveCollisions(Map<String, String> fullKeyToName, Consumer<String> warn) {
        Map<String, String> winners = new LinkedHashMap<>();
        new TreeMap<>(fullKeyToName).forEach((fullKey, name) -> {
            String winner = winners.putIfAbsent(name, fullKey);
            if (winner != null)
                warn.accept("Pair name \"" + name + "\" of " + fullKey + " collides with " + winner + "; skipped");
        });
        return winners;
    }
}

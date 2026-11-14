package net.kroia.stockmarket.testing;

import dev.architectury.utils.GameInstance;
import net.kroia.banksystem.data.DiagnosticsDatabase;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.client_server.arrs.GenericRequest;
import net.kroia.modutilities.setting.Setting;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.data.OrderOriginLog;
import net.kroia.stockmarket.data.filter.DateFilter;
import net.kroia.stockmarket.data.filter.EqualityFilter;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.kroia.stockmarket.networking.NetworkGate;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * BS-269 — test only: a trusted slave makes the master do the StockMarket fixture steps of the audit fixture
 * (change a live market setting, read back OrderHistory and TradeAlert rows, backfill candles, place bot orders and
 * detach a market's plugins). Copy of BankSystem's {@code AdminFlagTestRequest}: lives in {@code testing/} (not
 * compiled in release builds), registered by {@link DevBootstrap}, answered only for a trusted slave without a
 * player sender; anything else gets {@link Output#FAILED}.
 * <p>
 * Slave side: call the static wrappers ({@link #setMarketSetting}, {@link #collect}, {@link #backfillCandles},
 * {@link #botOrder}, {@link #detachPlugins}); each sends one request to the master.
 */
public class FixtureTestRequest extends GenericRequest<FixtureTestRequest.Input, FixtureTestRequest.Output> {

    public static final FixtureTestRequest INSTANCE = new FixtureTestRequest();

    /** How long the master waits for its db-worker and the diagnostics writer per step. */
    private static final long DB_WAIT_SECONDS = 60;

    /** The fixture step the master runs. */
    public enum Op {
        /** text = MARKET setting name (a Long setting, e.g. TRADE_COOLDOWN_MS, CANDLE_TIME), values[0] = new value; value = previous value */
        SET_MARKET_SETTING,
        /** values[0] = since (ms); orders/alerts = rows with time/ts >= since */
        COLLECT,
        /** item = market, candles = rows to write (their id is replaced by the item's); value = candles of that market in the rows' time range */
        BACKFILL_CANDLES,
        /** item = market, text = Order.Type name (LIMIT or MARKET), values = {raw volume (signed, + buy), raw price}; ok = accepted */
        BOT_ORDER,
        /** item = market; drops the market's plugin cache so plugins and their bots stop trading it */
        DETACH_PLUGINS
    }

    /**
     * @param op      the step
     * @param item    the market, {@link ItemID#INVALID_ID} if the step has none
     * @param text    setting name or order type, empty if unused
     * @param values  the step's numbers, see {@link Op}
     * @param candles candles for {@link Op#BACKFILL_CANDLES}, empty otherwise
     */
    public record Input(Op op, ItemID item, String text, long[] values, List<MarketPriceStruct> candles) {
        static final StreamCodec<RegistryFriendlyByteBuf, List<MarketPriceStruct>> CANDLES =
                MarketPriceStruct.STREAM_CODEC.apply(ByteBufCodecs.list());
    }

    /**
     * One OrderHistory row (stockdata.db). Nullable fields are SQL NULL in the row.
     *
     * @param orderId     {@code order_id}, the {@code <id>} of ledger ref {@code sm:order:<id>}
     * @param player      the order's player
     * @param itemId      the market's ItemID short
     * @param amount      filled volume (raw, + buy, - sell)
     * @param time        order creation time (ms)
     * @param finishTime  {@code finish_time} (ms)
     * @param price       average execution price (raw)
     * @param limitPrice  {@code limit_price} (raw, 0 for market orders)
     * @param finalStatus {@code final_status}, one of {@code OrderRecordStruct.STATUS_*}
     */
    public record OrderRow(@Nullable Long orderId, UUID player, short itemId, long amount, long time,
                           @Nullable Long finishTime, long price, @Nullable Long limitPrice, @Nullable Integer finalStatus) {}

    /** One TradeAlert row (stockmarket_diagnostics.db); nullable fields are SQL NULL in the row. */
    public record AlertRow(long id, long ts, String kind, @Nullable String playerUuid, @Nullable Long itemId,
                           @Nullable Long amount, @Nullable String orderIds) {}

    /**
     * @param ok     false if the master refused or the step failed
     * @param value  the step's number, see {@link Op}
     * @param orders OrderHistory rows ({@link Op#COLLECT} only)
     * @param alerts TradeAlert rows ({@link Op#COLLECT} only)
     */
    public record Output(boolean ok, long value, List<OrderRow> orders, List<AlertRow> alerts) {
        public static final Output FAILED = new Output(false, 0, List.of(), List.of());

        static Output of(boolean ok, long value) {
            return new Output(ok, value, List.of(), List.of());
        }
    }

    // ── slave-side wrappers ──────────────────────────────────────────────────

    /** @return the setting's previous value, or null if refused (unknown name, not a Long setting) */
    public static CompletableFuture<@Nullable Long> setMarketSetting(String name, long value) {
        return send(new Input(Op.SET_MARKET_SETTING, ItemID.INVALID_ID, name, new long[]{value}, List.of()))
                .thenApply(o -> o.ok() ? o.value() : null);
    }

    /** Waits for the master's db-worker and diagnostics writer, then reads the rows with time >= {@code sinceMillis}. */
    public static CompletableFuture<Output> collect(long sinceMillis) {
        return send(new Input(Op.COLLECT, ItemID.INVALID_ID, "", new long[]{sinceMillis}, List.of()));
    }

    /** @return the market's candle count in the rows' time range after the write, or null if it failed */
    public static CompletableFuture<@Nullable Long> backfillCandles(ItemID item, List<MarketPriceStruct> rows) {
        return send(new Input(Op.BACKFILL_CANDLES, item, "", new long[0], rows))
                .thenApply(o -> o.ok() ? o.value() : null);
    }

    /**
     * Puts a bot order (no player, no bank account, never in OrderHistory) straight into the master's market.
     * Bot orders get no order ID, so only the acceptance comes back.
     *
     * @return true if the market accepted it
     */
    public static CompletableFuture<Boolean> botOrder(ItemID item, Order.Type type, long rawVolume, long rawPrice) {
        return send(new Input(Op.BOT_ORDER, item, type.name(), new long[]{rawVolume, rawPrice}, List.of()))
                .thenApply(Output::ok);
    }

    /** @return true if the master dropped the market's plugin cache */
    public static CompletableFuture<Boolean> detachPlugins(ItemID item) {
        return send(new Input(Op.DETACH_PLUGINS, item, "", new long[0], List.of())).thenApply(Output::ok);
    }

    private static CompletableFuture<Output> send(Input input) {
        return INSTANCE.sendRequestToMaster(input);
    }

    // ── master side ──────────────────────────────────────────────────────────

    @Override
    public String getRequestTypeID() {
        return FixtureTestRequest.class.getName();
    }

    @Override
    public long getTimeoutMs() {
        return StockMarketGenericRequest.DB_READ_TIMEOUT_MS; // collect/backfill wait on the db-worker
    }

    /** A client never gets an answer. */
    @Override
    public CompletableFuture<Output> handleOnServer(Input input, ServerPlayer sender) {
        return CompletableFuture.completedFuture(Output.FAILED);
    }

    @Override
    public CompletableFuture<Output> handleOnMasterServer(Input in, String slaveID, @Nullable UUID playerSender) {
        MinecraftServer server = GameInstance.getServer();
        StockMarketModBackend.ServerInstances b = StockMarketModBackend.getServerInstances();
        if (playerSender != null || slaveID == null || slaveID.isEmpty() || server == null || b == null
                || !NetworkGate.isMutatingCallAllowed(slaveID, "FixtureTestRequest"))
            return CompletableFuture.completedFuture(Output.FAILED);
        CompletableFuture<Output> out = switch (in.op()) {
            case SET_MARKET_SETTING -> CompletableFuture.supplyAsync(() -> setSetting(b, in.text(), in.values()[0]), server);
            case BOT_ORDER -> CompletableFuture.supplyAsync(() -> putBotOrder(b, in), server);
            case DETACH_PLUGINS -> CompletableFuture.supplyAsync(() -> {
                b.PLUGIN_MANAGER.getSync().removeCache(in.item());
                return Output.of(true, 0);
            }, server);
            // these block on the db-worker and the diagnostics writer: off the server thread
            case BACKFILL_CANDLES -> CompletableFuture.supplyAsync(() -> backfill(b, in));
            case COLLECT -> CompletableFuture.supplyAsync(() -> collectRows(b, in.values()[0]));
        };
        return out.exceptionally(e -> {
            StockMarketMod.LOGGER.warn("[FixtureTestRequest] {} failed", in.op(), e);
            return Output.FAILED;
        });
    }

    private static Output setSetting(StockMarketModBackend.ServerInstances b, String name, long value) {
        Setting<?> setting = b.SERVER_SETTINGS.MARKET.getSetting(name);
        if (setting == null || !(setting.get() instanceof Long previous))
            return Output.FAILED;
        @SuppressWarnings("unchecked")
        Setting<Long> longSetting = (Setting<Long>) setting;
        longSetting.set(value); // read live by the master (TRADE_COOLDOWN_MS per order, CANDLE_TIME per tick)
        return Output.of(true, previous);
    }

    private static Output putBotOrder(StockMarketModBackend.ServerInstances b, Input in) {
        IServerMarket market = b.MARKET_MANAGER.getSync().getMarket(in.item());
        Order.Type type = Order.Type.valueOf(in.text());
        if (market == null || type == Order.Type.INTER_MARKET)
            return Output.FAILED;
        Order order = new Order(in.item(), type, in.values()[0], in.values()[1], System.currentTimeMillis());
        return Output.of(market.putOrder(order), 0);
    }

    private static Output backfill(StockMarketModBackend.ServerInstances b, Input in) {
        List<MarketPriceStruct> rows = new ArrayList<>();
        long from = Long.MAX_VALUE, to = Long.MIN_VALUE;
        for (MarketPriceStruct c : in.candles()) {
            rows.add(new MarketPriceStruct(in.item().getShort(), c.open(), c.low(), c.high(), c.time(), c.tradedVolume(), c.gameTime()));
            from = Math.min(from, c.time());
            to = Math.max(to, c.time());
        }
        if (rows.isEmpty())
            return Output.FAILED;
        try {
            b.MARKET_PRICE_HISTORY_MANAGER.save(rows).get(DB_WAIT_SECONDS, TimeUnit.SECONDS); // done after its commit
            int count = b.MARKET_PRICE_HISTORY_MANAGER.getRecordCount(Optional.of(new DateFilter(from, to)),
                    Optional.of(new EqualityFilter(in.item().getShort()))).get(DB_WAIT_SECONDS, TimeUnit.SECONDS);
            return Output.of(true, count);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Output collectRows(StockMarketModBackend.ServerInstances b, long since) {
        try {
            TestFixture.dbBarrier(b.DATABASE_MANAGER).get(DB_WAIT_SECONDS, TimeUnit.SECONDS);
            // the query seals the open order-record batch and runs behind it on the db-worker
            List<OrderRow> orders = new ArrayList<>();
            for (OrderRecordStruct r : b.ORDER_RECORD_MANAGER.getHistory(Optional.of(new DateFilter(since, Long.MAX_VALUE)),
                    Optional.empty(), Optional.empty(), Optional.empty()).get(DB_WAIT_SECONDS, TimeUnit.SECONDS))
                orders.add(new OrderRow(r.orderID(), r.user(), r.itemID(), r.amount(), r.time(), r.finishTime(),
                        r.price(), r.limitPrice(), r.finalStatus()));

            List<AlertRow> alerts = new ArrayList<>();
            DiagnosticsDatabase live = OrderOriginLog.live();
            if (live != null) {
                live.flush().get(DB_WAIT_SECONDS, TimeUnit.SECONDS);
                try (Connection c = DiagnosticsDatabase.connect(live.getFile());
                     PreparedStatement ps = c.prepareStatement(
                             "SELECT id, ts, kind, player_uuid, item_id, amount, order_ids FROM TradeAlert WHERE ts >= ? ORDER BY id")) {
                    ps.setLong(1, since);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next())
                            alerts.add(new AlertRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                                    nullableLong(rs, 5), nullableLong(rs, 6), rs.getString(7)));
                    }
                }
            }
            return new Output(true, 0, orders, alerts);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static @Nullable Long nullableLong(ResultSet rs, int column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }

    // ── wire format ──────────────────────────────────────────────────────────

    @Override
    public void encodeInput(RegistryFriendlyByteBuf buf, Input in) {
        buf.writeEnum(in.op());
        ItemID.STREAM_CODEC.encode(buf, in.item());
        buf.writeUtf(in.text());
        buf.writeLongArray(in.values());
        Input.CANDLES.encode(buf, in.candles());
    }

    @Override
    public Input decodeInput(RegistryFriendlyByteBuf buf) {
        return new Input(buf.readEnum(Op.class), ItemID.STREAM_CODEC.decode(buf), buf.readUtf(), buf.readLongArray(),
                Input.CANDLES.decode(buf));
    }

    @Override
    public void encodeOutput(RegistryFriendlyByteBuf buf, Output out) {
        buf.writeBoolean(out.ok());
        buf.writeLong(out.value());
        buf.writeCollection(out.orders(), (b, r) -> {
            writeNullableLong(b, r.orderId());
            b.writeUUID(r.player());
            b.writeShort(r.itemId());
            b.writeLong(r.amount());
            b.writeLong(r.time());
            writeNullableLong(b, r.finishTime());
            b.writeLong(r.price());
            writeNullableLong(b, r.limitPrice());
            writeNullableLong(b, r.finalStatus() == null ? null : r.finalStatus().longValue());
        });
        buf.writeCollection(out.alerts(), (b, r) -> {
            b.writeLong(r.id());
            b.writeLong(r.ts());
            b.writeUtf(r.kind());
            writeNullableString(b, r.playerUuid());
            writeNullableLong(b, r.itemId());
            writeNullableLong(b, r.amount());
            writeNullableString(b, r.orderIds());
        });
    }

    @Override
    public Output decodeOutput(RegistryFriendlyByteBuf buf) {
        boolean ok = buf.readBoolean();
        long value = buf.readLong();
        List<OrderRow> orders = buf.readList(b -> {
            Long orderId = readNullableLong(b);
            UUID player = b.readUUID();
            short itemId = b.readShort();
            long amount = b.readLong();
            long time = b.readLong();
            Long finishTime = readNullableLong(b);
            long price = b.readLong();
            Long limitPrice = readNullableLong(b);
            Long status = readNullableLong(b);
            return new OrderRow(orderId, player, itemId, amount, time, finishTime, price, limitPrice,
                    status == null ? null : status.intValue());
        });
        List<AlertRow> alerts = buf.readList(b -> new AlertRow(b.readLong(), b.readLong(), b.readUtf(),
                readNullableString(b), readNullableLong(b), readNullableLong(b), readNullableString(b)));
        return new Output(ok, value, orders, alerts);
    }

    private static void writeNullableLong(FriendlyByteBuf buf, @Nullable Long v) {
        buf.writeBoolean(v != null);
        if (v != null) buf.writeLong(v);
    }

    private static @Nullable Long readNullableLong(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readLong() : null;
    }

    private static void writeNullableString(FriendlyByteBuf buf, @Nullable String v) {
        buf.writeBoolean(v != null);
        if (v != null) buf.writeUtf(v);
    }

    private static @Nullable String readNullableString(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readUtf() : null;
    }
}

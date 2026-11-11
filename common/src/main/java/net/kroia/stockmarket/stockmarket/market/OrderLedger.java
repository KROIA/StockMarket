package net.kroia.stockmarket.stockmarket.market;

import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.BankSystemModSettings;
import net.kroia.banksystem.api.ledger.TradeLogEntry;
import net.kroia.banksystem.api.ledger.TradeRowKind;
import net.kroia.banksystem.api.ledger.TradeSide;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * T-191: writes BankSystem trade-ledger rows ({@code ITransactionLedger.logTrade}) for player orders.
 * <p>
 * Every row is a <b>delta</b>: the fills of one order since its previous row. The order keeps the
 * already-logged state ({@link Order#getLoggedFilledVolume()}, {@link Order#getLoggedTransferredMoney()},
 * persisted in its NBT), so the rows of one ref always sum to the order totals.
 * <ul>
 *   <li>{@link #CLOSE}: the order was consumed or canceled.</li>
 *   <li>{@link #PERIODIC}: 10-minute sweep over resting orders whose fill state changed.</li>
 *   <li>{@link #SHUTDOWN}: server stop, resting orders with unlogged fills.</li>
 * </ul>
 * Bot orders and deltas of 0 write no row. Placement, lock and unlock write no row either.
 * <p>
 * Only the master calls this (the matching engine and the market manager exist only there).
 */
public final class OrderLedger {

    /** Row kind: the order finished (filled or canceled). Name of {@code TradeRowKind.CLOSE}. */
    public static final String CLOSE = "CLOSE";
    /** Row kind: periodic flush of a resting order. Name of {@code TradeRowKind.PERIODIC}. */
    public static final String PERIODIC = "PERIODIC";
    /** Row kind: flush on server stop. Name of {@code TradeRowKind.SHUTDOWN}. */
    public static final String SHUTDOWN = "SHUTDOWN";

    /** Interval of the {@link #PERIODIC} sweep: 10 minutes in server ticks. */
    public static final int SWEEP_INTERVAL_TICKS = 10 * 60 * 20;

    private OrderLedger() {}

    /** @return the ledger ref of a regular order: {@code sm:order:<orderID>} */
    public static String orderRef(long orderID) {
        return "sm:order:" + orderID;
    }

    /** @return the ledger ref shared by both legs of an inter-market order: {@code sm:im:<groupID>} */
    public static String interMarketRef(UUID groupID) {
        return "sm:im:" + groupID;
    }

    /**
     * Writes one row with the fills of {@code order} since its last row, then marks them logged.
     * No-op for bot orders or a delta of 0.
     *
     * @param order    the player order (a regular order or one inter-market leg)
     * @param ref      ledger ref, see {@link #orderRef(long)} / {@link #interMarketRef(UUID)}
     * @param rowKind  {@link #CLOSE}, {@link #PERIODIC} or {@link #SHUTDOWN}
     * @param currency the trading currency of the money moved
     * @return true if a row was written
     */
    public static boolean logDelta(Order order, String ref, String rowKind, ItemID currency) {
        if (order.isBotOrder())
            return false;
        long qty = Math.abs(order.getFilledVolume() - order.getLoggedFilledVolume());
        long money = Math.abs(order.getTransferredMoney() - order.getLoggedTransferredMoney());
        if (qty == 0 && money == 0)
            return false;
        long avgPrice = qty == 0 ? 0 : money * BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR / qty;
        Writer.write(order.getBankAccountNr(), order.getExecutorPlayerUUID(), order.isBuyOrder(), order.getItemID(),
                qty, currency, money, avgPrice, ref, rowKind);
        order.markLedgerLogged();
        return true;
    }

    /** Builds the {@link TradeLogEntry} and hands it to the BankSystem ledger (or the test sink). */
    public static final class Writer {
        /** Test hook: when set, rows go here instead of the BankSystem ledger. */
        public static volatile @Nullable Consumer<TradeLogEntry> testSink = null;

        private Writer() {}

        static void write(int account, @Nullable UUID actor, boolean buy, ItemID item, long qty, ItemID currency,
                          long money, long avgPrice, String ref, String rowKind) {
            TradeLogEntry entry = new TradeLogEntry(account, actor, buy ? TradeSide.BUY : TradeSide.SELL, item, qty,
                    currency, money, avgPrice, null, ref, TradeRowKind.valueOf(rowKind), System.currentTimeMillis(), null);
            Consumer<TradeLogEntry> sink = testSink;
            if (sink != null)
                sink.accept(entry);
            else
                BankSystemMod.getAPI().getLedger().logTrade(entry);
        }
    }
}

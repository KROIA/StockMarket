package net.kroia.stockmarket.screen.widgets;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.NbtCompat;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.api.market.IPriceDataProvider;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.util.PriceHistoryData;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * CandlestickChart variant for the display block system.
 * Uses a display-specific viewport key so the display's zoom/position
 * is independent from the TradeScreen's viewport for the same market.
 * Manages stream subscription lifecycle.
 *
 * Supports two modes:
 * - Item/Money (single market): only targetMarketID is set
 * - Item/Item (cross-rate): both targetMarketID and secondMarketID are set
 * <p>
 * Subscription lifecycle (SM-313): connects on {@link #connect()} or lazily on render, and
 * disconnects on {@link #close()}, on {@link #setRoot(Gui) setRoot(null)} (removed from its GUI)
 * and when its market disappears from the client cache. Each subscribe is paired with exactly
 * one unsubscribe, so the market's price stream stops once no chart or screen needs it.
 */
public class DisplayCandlestickChart extends CandlestickChart implements AutoCloseable {
    /** Viewport key part of charts embedded by other mods (SM-313), instead of a display block key. */
    public static final String EMBED_KEY = "embed";

    // Not final: deserializeState() fills these in when the chart is rebuilt from a
    // guiTree shipment through GuiElementRegistry (see the no-arg constructor).
    private ItemID targetMarketID;
    private @Nullable ItemID secondMarketID;
    private long blockKey;
    private String displayViewportKey;
    private boolean connected = false;
    // The ClientMarkets subscribed by connect(), released exactly once by disconnect().
    private @Nullable ClientMarket subscribedTarget = null;
    private @Nullable ClientMarket subscribedSecond = null;
    private @Nullable CompoundTag initialViewport = null;

    /**
     * @param itemID       the primary market to display
     * @param secondItemID optional second market for cross-rate (Item/Item) display; null for Item/Money
     * @param blockKey     unique identifier for this display (e.g. blockPos.asLong())
     */
    public DisplayCandlestickChart(ItemID itemID, @Nullable ItemID secondItemID, long blockKey) {
        super();
        setTarget(itemID, secondItemID, blockKey);
    }

    /**
     * Factory constructor for {@link net.kroia.modutilities.gui.GuiElementRegistry}.
     * Creates an unbound chart; {@link #deserializeState(CompoundTag)} restores the
     * market IDs and block key, after which the chart lazily connects on first render.
     */
    public DisplayCandlestickChart() {
        this(null, null, 0L);
    }

    /**
     * Creates a chart embedded in another mod's screen (SM-313, see
     * {@code StockMarketClientIntegration.createPriceChart}). Its zoom/position is stored under its
     * own key, so it never moves the TradeScreen's or a display block's view; on first use it
     * starts from the player's TradeScreen view of the market.
     *
     * @param itemID the market to show
     * @return a chart that connects on creation if the market is cached, else lazily on render
     */
    public static DisplayCandlestickChart createEmbedded(ItemID itemID) {
        DisplayCandlestickChart chart = new DisplayCandlestickChart(itemID, null, 0L);
        chart.displayViewportKey = "display_" + EMBED_KEY + "_" + itemID.getShort();
        chart.connect();
        return chart;
    }

    /**
     * Binds the chart to its market(s) and derives the display-specific viewport key.
     *
     * @param itemID       the primary market, or null if unbound
     * @param secondItemID optional second market for cross-rate display
     * @param blockKey     unique identifier for the owning display
     */
    private void setTarget(@Nullable ItemID itemID, @Nullable ItemID secondItemID, long blockKey) {
        this.targetMarketID = itemID;
        this.secondMarketID = secondItemID;
        this.blockKey = blockKey;
        if (secondItemID != null) {
            this.displayViewportKey = "display_" + blockKey + "_pair_" + (itemID != null ? itemID.getShort() : "none") + "_" + secondItemID.getShort();
        } else {
            this.displayViewportKey = "display_" + blockKey + "_" + (itemID != null ? itemID.getShort() : "none");
        }
    }

    // ── guiTree serialization (GuiElementRegistry) ──
    // Only the market binding is shipped. Candle data comes from the client market stream
    // once connected, and the viewport is restored by StockMarketDisplayBlockEntity from its
    // own custom NBT (the server-side chart never connects, so its viewport is meaningless).

    @Override
    public CompoundTag serializeState() {
        CompoundTag tag = super.serializeState();
        if (targetMarketID != null) tag.putShort("targetMarketID", targetMarketID.getShort());
        if (secondMarketID != null) tag.putShort("secondMarketID", secondMarketID.getShort());
        tag.putLong("blockKey", blockKey);
        return tag;
    }

    @Override
    public void deserializeState(CompoundTag tag) {
        super.deserializeState(tag);
        if (!tag.contains("blockKey")) return;
        disconnect();
        setTarget(
                tag.contains("targetMarketID") ? new ItemID(NbtCompat.getShort(tag, "targetMarketID")) : null,
                tag.contains("secondMarketID") ? new ItemID(NbtCompat.getShort(tag, "secondMarketID")) : null,
                NbtCompat.getLong(tag, "blockKey"));
    }

    /**
     * The timescale buttons are created by the {@link CandlestickChart} constructor, so
     * they must not be serialized as children (a rebuild would duplicate them).
     */
    @Override
    public List<GuiElement> getSerializableChildren() {
        return List.of();
    }

    public ItemID getTargetMarketID() {
        return targetMarketID;
    }

    public @Nullable ItemID getSecondMarketID() {
        return secondMarketID;
    }

    public boolean isConnected() {
        return connected;
    }

    @Override
    public boolean isDirty() {
        return connected || super.isDirty();
    }

    @Override
    protected void renderBackground() {
        // Lazy (re)connect: covers Gui.deserializeTree, a close() followed by reuse and a detach/re-add.
        // A market that left the client cache (deleted, or hidden on this slave) is released; its
        // stream was force-stopped already, and connect() picks it up again if it comes back.
        if (connected && marketsChanged())
            disconnect();
        if (!connected)
            connect();
        super.renderBackground();
    }

    /** @return true if a subscribed market is no longer the one in the client cache */
    private boolean marketsChanged() {
        if (BACKEND_INSTANCES == null) return true;
        if (BACKEND_INSTANCES.MARKET_MANAGER.getMarket(targetMarketID) != subscribedTarget) return true;
        return secondMarketID != null && BACKEND_INSTANCES.MARKET_MANAGER.getMarket(secondMarketID) != subscribedSecond;
    }

    /**
     * Detaching the chart from its GUI ({@code root == null}) releases the subscription; the next
     * render after it is attached again reconnects.
     */
    @Override
    public void setRoot(Gui root) {
        super.setRoot(root);
        if (root == null)
            disconnect();
    }

    /** Releases the price stream subscription (same as {@link #disconnect()}). Safe to call repeatedly. */
    @Override
    public void close() {
        disconnect();
    }

    /**
     * Returns the current viewport state for NBT persistence in the block entity.
     */
    public CompoundTag getViewportState() {
        return serializeViewport();
    }

    /**
     * Sets viewport state to apply once the chart connects to its market.
     * If already connected, applies immediately.
     */
    public void setInitialViewport(@Nullable CompoundTag tag) {
        if (tag != null && !tag.isEmpty()) {
            if (connected) {
                deserializeViewport(tag);
            } else {
                this.initialViewport = tag.copy();
            }
        }
    }

    /**
     * Releases the subscriptions taken by {@link #connect()} and clears the chart. Idempotent.
     */
    public void disconnect() {
        // Unsubscribe the instances that were subscribed, not a fresh cache lookup: a market
        // deleted and re-created meanwhile is a different ClientMarket with its own count.
        if (subscribedTarget != null) subscribedTarget.unsubscribeFromMarketPriceUpdate();
        if (subscribedSecond != null) subscribedSecond.unsubscribeFromMarketPriceUpdate();
        subscribedTarget = null;
        subscribedSecond = null;
        connected = false;
        setPriceDataProvider(null);
    }

    /**
     * Subscribes to the market's live prices and binds the chart to it. Idempotent: does nothing
     * while connected. Client thread only.
     *
     * @return true if connected (now or already); false if the chart has no market or the market
     *         (or, for a cross rate, either leg) is not in the client cache
     */
    public boolean connect() {
        if (!connected && targetMarketID != null)
            connected = tryConnect();
        return connected;
    }

    private boolean tryConnect() {
        if (BACKEND_INSTANCES == null) return false;
        // Own saved view first (set by an earlier connection of this chart key); else the trade view.
        boolean hasOwnView = lookupSavedViewport(displayViewportKey) != null;

        if (secondMarketID != null) {
            // Cross-rate (Item/Item) mode
            // targetMarketID = "I want" item (numerator in the price ratio)
            // secondMarketID = "I have" / currency item (denominator)
            ClientMarket wantMarket = BACKEND_INSTANCES.MARKET_MANAGER.getMarket(targetMarketID);
            ClientMarket haveMarket = BACKEND_INSTANCES.MARKET_MANAGER.getMarket(secondMarketID);
            if (haveMarket == null || wantMarket == null) return false;

            // Resolve the provider BEFORE subscribing: a failed attempt leaves connected == false,
            // so disconnect() would never release subscriptions taken here and every retry
            // (once per frame) would leak two ref-counted price streams.
            IPriceDataProvider provider = BACKEND_INSTANCES.MARKET_MANAGER.getCrossRateMarket(secondMarketID, targetMarketID);
            if (provider == null) return false;

            // Subscribe only on the success path (returns true -> connected) so each
            // subscribe is paired with exactly one unsubscribe in disconnect().
            haveMarket.subscribeToMarketPriceUpdate();
            wantMarket.subscribeToMarketPriceUpdate();
            subscribedTarget = wantMarket;
            subscribedSecond = haveMarket;

            setPriceDataProvider(new DisplayProviderWrapper(provider, displayViewportKey));
            if (initialViewport != null) {
                deserializeViewport(initialViewport);
                initialViewport = null;
            } else if (!hasOwnView) {
                CompoundTag tradeViewport = lookupSavedViewport(provider.getViewportKey());
                if (tradeViewport != null) {
                    deserializeViewport(tradeViewport);
                }
            }
            return true;
        } else {
            // Single market (Item/Money) mode
            ClientMarket market = BACKEND_INSTANCES.MARKET_MANAGER.getMarket(targetMarketID);
            if (market == null) return false;

            market.subscribeToMarketPriceUpdate();
            subscribedTarget = market;
            setPriceDataProvider(new DisplayProviderWrapper(market, displayViewportKey));

            if (initialViewport != null) {
                deserializeViewport(initialViewport);
                initialViewport = null;
            } else if (!hasOwnView) {
                CompoundTag tradeViewport = lookupSavedViewport(market.getViewportKey());
                if (tradeViewport != null) {
                    deserializeViewport(tradeViewport);
                }
            }
            return true;
        }
    }

    /**
     * Wraps an IPriceDataProvider to override the viewport key so the display chart
     * stores its own zoom/position separately from the TradeScreen chart.
     * Accepts both ClientMarket (Item/Money) and CrossRateMarket (Item/Item).
     */
    private record DisplayProviderWrapper(IPriceDataProvider delegate, String key) implements IPriceDataProvider {
        @Override
        public @Nullable PriceHistoryData getPriceHistoryData(long candleTimeDelta) {
            return delegate.getPriceHistoryData(candleTimeDelta);
        }

        @Override
        public double getCurrentMarketRealPrice() {
            return delegate.getCurrentMarketRealPrice();
        }

        @Override
        public @NotNull ItemID getItemID() {
            return delegate.getItemID();
        }

        @Override
        public @NotNull String getViewportKey() {
            return key;
        }

        // The two pagination methods below MUST be forwarded: the interface defaults
        // report "no data / nothing older", which left the display chart empty until
        // some other screen (TradeScreen) had fetched the same market/timeframe.

        /** Forwards older-history paging to the wrapped provider. */
        @Override
        public @NotNull CompletableFuture<Boolean> requestOlderData(long candleDeltaMs, long beforeTimestamp) {
            return delegate.requestOlderData(candleDeltaMs, beforeTimestamp);
        }

        /** Forwards the "server has older data" query to the wrapped provider. */
        @Override
        public boolean hasMoreOlderData(long candleDeltaMs) {
            return delegate.hasMoreOlderData(candleDeltaMs);
        }
    }
}

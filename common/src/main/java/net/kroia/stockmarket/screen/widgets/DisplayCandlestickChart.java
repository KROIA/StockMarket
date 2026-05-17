package net.kroia.stockmarket.screen.widgets;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.api.market.IPriceDataProvider;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.util.PriceHistoryData;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * CandlestickChart variant for the display block system.
 * Uses a display-specific viewport key so the display's zoom/position
 * is independent from the TradeScreen's viewport for the same market.
 * Manages stream subscription lifecycle.
 */
public class DisplayCandlestickChart extends CandlestickChart {
    private final ItemID targetMarketID;
    private final String displayViewportKey;
    private boolean connected = false;
    private @Nullable CompoundTag initialViewport = null;

    /**
     * @param itemID   the market to display
     * @param blockKey unique identifier for this display (e.g. blockPos.asLong())
     */
    public DisplayCandlestickChart(ItemID itemID, long blockKey) {
        super();
        this.targetMarketID = itemID;
        this.displayViewportKey = "display_" + blockKey + "_" + (itemID != null ? itemID.getShort() : "none");
    }

    public ItemID getTargetMarketID() {
        return targetMarketID;
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
        if (!connected && targetMarketID != null) {
            connected = tryConnect();
        }
        super.renderBackground();
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

    public void disconnect() {
        if (connected && targetMarketID != null && BACKEND_INSTANCES != null) {
            ClientMarket market = BACKEND_INSTANCES.MARKET_MANAGER.getMarket(targetMarketID);
            if (market != null) {
                market.unsubscribeFromMarketPriceUpdate();
            }
        }
        connected = false;
        setPriceDataProvider(null);
    }

    private boolean tryConnect() {
        if (BACKEND_INSTANCES == null) return false;
        ClientMarket market = BACKEND_INSTANCES.MARKET_MANAGER.getMarket(targetMarketID);
        if (market != null) {
            market.subscribeToMarketPriceUpdate();
            // Wrap the market with a display-specific viewport key so the
            // display's zoom/position is independent from the TradeScreen
            setPriceDataProvider(new DisplayProviderWrapper(market, displayViewportKey));

            if (initialViewport != null) {
                // Restore viewport saved by the block entity (from previous session)
                deserializeViewport(initialViewport);
                initialViewport = null;
            } else {
                // No display-specific viewport yet — check if the player has a
                // TradeScreen viewport for this market and use it as initial state
                CompoundTag tradeViewport = lookupSavedViewport(market.getViewportKey());
                if (tradeViewport != null) {
                    deserializeViewport(tradeViewport);
                }
                // else: firstDraw stays true → auto-center once candle data loads
            }
            return true;
        }
        return false;
    }

    /**
     * Wraps a ClientMarket to override the viewport key so the display chart
     * stores its own zoom/position separately from the TradeScreen chart.
     */
    private record DisplayProviderWrapper(ClientMarket delegate, String key) implements IPriceDataProvider {
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
    }
}

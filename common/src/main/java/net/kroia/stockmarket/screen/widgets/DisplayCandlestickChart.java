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
 *
 * Supports two modes:
 * - Item/Money (single market): only targetMarketID is set
 * - Item/Item (cross-rate): both targetMarketID and secondMarketID are set
 */
public class DisplayCandlestickChart extends CandlestickChart {
    private final ItemID targetMarketID;
    private final @Nullable ItemID secondMarketID;
    private final String displayViewportKey;
    private boolean connected = false;
    private @Nullable CompoundTag initialViewport = null;

    /**
     * @param itemID       the primary market to display
     * @param secondItemID optional second market for cross-rate (Item/Item) display; null for Item/Money
     * @param blockKey     unique identifier for this display (e.g. blockPos.asLong())
     */
    public DisplayCandlestickChart(ItemID itemID, @Nullable ItemID secondItemID, long blockKey) {
        super();
        this.targetMarketID = itemID;
        this.secondMarketID = secondItemID;
        if (secondItemID != null) {
            this.displayViewportKey = "display_" + blockKey + "_pair_" + (itemID != null ? itemID.getShort() : "none") + "_" + secondItemID.getShort();
        } else {
            this.displayViewportKey = "display_" + blockKey + "_" + (itemID != null ? itemID.getShort() : "none");
        }
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
        if (connected && BACKEND_INSTANCES != null) {
            if (targetMarketID != null) {
                ClientMarket market = BACKEND_INSTANCES.MARKET_MANAGER.getMarket(targetMarketID);
                if (market != null) market.unsubscribeFromMarketPriceUpdate();
            }
            if (secondMarketID != null) {
                ClientMarket market = BACKEND_INSTANCES.MARKET_MANAGER.getMarket(secondMarketID);
                if (market != null) market.unsubscribeFromMarketPriceUpdate();
            }
        }
        connected = false;
        setPriceDataProvider(null);
    }

    private boolean tryConnect() {
        if (BACKEND_INSTANCES == null) return false;

        if (secondMarketID != null) {
            // Cross-rate (Item/Item) mode
            // targetMarketID = "I want" item (numerator in the price ratio)
            // secondMarketID = "I have" / currency item (denominator)
            ClientMarket wantMarket = BACKEND_INSTANCES.MARKET_MANAGER.getMarket(targetMarketID);
            ClientMarket haveMarket = BACKEND_INSTANCES.MARKET_MANAGER.getMarket(secondMarketID);
            if (haveMarket == null || wantMarket == null) return false;

            haveMarket.subscribeToMarketPriceUpdate();
            wantMarket.subscribeToMarketPriceUpdate();

            IPriceDataProvider provider = BACKEND_INSTANCES.MARKET_MANAGER.getCrossRateMarket(secondMarketID, targetMarketID);
            if (provider == null) return false;

            setPriceDataProvider(new DisplayProviderWrapper(provider, displayViewportKey));
            if (initialViewport != null) {
                deserializeViewport(initialViewport);
                initialViewport = null;
            } else {
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
            setPriceDataProvider(new DisplayProviderWrapper(market, displayViewportKey));

            if (initialViewport != null) {
                deserializeViewport(initialViewport);
                initialViewport = null;
            } else {
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
    }
}

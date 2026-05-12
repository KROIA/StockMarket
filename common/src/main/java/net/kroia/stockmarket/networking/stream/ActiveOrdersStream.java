package net.kroia.stockmarket.networking.stream;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.modutilities.networking.client_server.streaming.GenericStream;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.api.marketmanager.IServerMarketManager;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.util.StockMarketGenericStream;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server-to-client stream that delivers real-time updates of a player's pending (active) orders
 * across all markets. The client subscribes with a dummy Byte context; the server identifies
 * the subscribing player via {@link #getRequestorPlayerUUID()}.
 * <p>
 * Updates are sent at most every 500ms and only when the order set has changed (detected by
 * comparing order count and a content hash).
 */
public class ActiveOrdersStream extends StockMarketGenericStream<Byte, ActiveOrdersStream.ResponseData> {

    /**
     * Payload containing the list of active orders for the subscribing player.
     */
    public static class ResponseData {
        public List<Order> orders;

        public ResponseData() {
            this.orders = new ArrayList<>();
        }

        public ResponseData(List<Order> orders) {
            this.orders = orders;
        }
    }

    private static final long UPDATE_INTERVAL_MS = 500;

    private long lastUpdateMs = System.currentTimeMillis();
    /** Cached player UUID, resolved once when the stream starts on the server */
    private @Nullable UUID playerUUID;
    /** Last sent order count — used for simple change detection */
    private int lastSentOrderCount = -1;
    /** Hash of last sent order data — catches changes even when count stays the same */
    private int lastSentOrderHash = 0;
    /** Current response data to be sent in the next packet */
    private ResponseData currentResponse = new ResponseData();

    @Override
    public GenericStream<Byte, ResponseData> copy() {
        return new ActiveOrdersStream();
    }

    @Override
    public String getStreamTypeID() {
        return ActiveOrdersStream.class.getName();
    }

    @Override
    public void onStartStreamSendingOnSever() {
        // Resolve the subscribing player's UUID from the stream framework
        playerUUID = getRequestorPlayerUUID();
        lastSentOrderCount = -1;
        lastSentOrderHash = 0;
        currentResponse = new ResponseData();
    }

    @Override
    public void onStopStreamSendingOnServer() {
        info("ActiveOrdersStream stopped for player: " + playerUUID);
    }

    @Override
    protected void updateOnServer() {
        long now = System.currentTimeMillis();
        if (now - lastUpdateMs < UPDATE_INTERVAL_MS) return;
        lastUpdateMs = now;

        if (playerUUID == null) {
            stopStream();
            return;
        }

        @Nullable IServerMarketManager mgr = getMarketManager();
        if (mgr == null) {
            stopStream();
            return;
        }

        // Collect all pending orders belonging to this player across all markets
        List<Order> currentOrders = new ArrayList<>();
        for (ItemID marketID : mgr.getAvailableMarketIDs()) {
            IServerMarket market = mgr.getMarket(marketID);
            if (market == null) continue;
            for (Order order : market.getLimitOrders()) {
                if (order.getExecutorPlayerUUID() != null && order.getExecutorPlayerUUID().equals(playerUUID)) {
                    currentOrders.add(order);
                }
            }
        }

        // Change detection: compare count and content hash to avoid redundant packets
        int orderHash = computeOrderHash(currentOrders);
        if (currentOrders.size() != lastSentOrderCount || orderHash != lastSentOrderHash) {
            lastSentOrderCount = currentOrders.size();
            lastSentOrderHash = orderHash;
            currentResponse = new ResponseData(currentOrders);
            sendPacket();
        }
    }

    @Override
    public ResponseData provideStreamPacketOnServer() {
        return currentResponse;
    }

    // --- Context (Byte — dummy, player identified from subscription) ---

    @Override
    public void encodeContextData(RegistryFriendlyByteBuf buffer, Byte context) {
        ByteBufCodecs.BYTE.encode(buffer, context);
    }

    @Override
    public Byte decodeContextData(RegistryFriendlyByteBuf buffer) {
        return ByteBufCodecs.BYTE.decode(buffer);
    }

    // --- Data encode/decode ---

    @Override
    public void encodeData(RegistryFriendlyByteBuf buffer, ResponseData data) {
        ExtraCodecUtils.listStreamCodec(Order.STREAM_CODEC).encode(buffer, data.orders);
    }

    @Override
    public ResponseData decodeData(RegistryFriendlyByteBuf buffer) {
        ResponseData data = new ResponseData();
        data.orders = new ArrayList<>(ExtraCodecUtils.listStreamCodec(Order.STREAM_CODEC).decode(buffer));
        return data;
    }

    /**
     * Computes a simple hash over the order list to detect content changes even
     * when the order count remains the same (e.g. partial fills change filledVolume).
     */
    private static int computeOrderHash(List<Order> orders) {
        int hash = 1;
        for (Order order : orders) {
            // Combine key fields that may change between ticks
            hash = 31 * hash + Long.hashCode(order.getTime());
            hash = 31 * hash + Long.hashCode(order.getStartPrice());
            hash = 31 * hash + Long.hashCode(order.getTargetVolume());
            hash = 31 * hash + Long.hashCode(order.getFilledVolume());
            hash = 31 * hash + Long.hashCode(order.getTransferredMoney());
        }
        return hash;
    }
}

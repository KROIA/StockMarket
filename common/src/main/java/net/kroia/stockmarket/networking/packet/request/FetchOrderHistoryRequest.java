package net.kroia.stockmarket.networking.packet.request;

import net.kroia.modutilities.networking.INetworkPayloadConverter;
import net.kroia.modutilities.networking.INetworkPayloadEncoder;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.server.order.OrderDataRecord;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class FetchOrderHistoryRequest extends StockMarketGenericRequest<FetchOrderHistoryRequest.Input, FetchOrderHistoryRequest.Output> {

    @Override
    public String getRequestTypeID() {
        return "";
    }

    @Override
    public Output handleOnClient(Input input) {
        return null;
    }

    @Override
    public Output handleOnServer(Input input, ServerPlayer sender) {
        List<OrderDataRecord> records = new ArrayList<>();
        if(input.isPlayerHistory){
            records = BACKEND_INSTANCES.SERVER_PLAYER_MANAGER.retrieveOrderData(sender.getUUID(), input.numFetched, 20);
        }
        else{
            records = BACKEND_INSTANCES.SERVER_MARKET_MANAGER.fetchOrderChunk(input.market, input.numFetched);
        }
        Map<UUID, String> nameMap = new HashMap<>();
        records.forEach((r) -> nameMap.putIfAbsent(r.getPlayer(), BACKEND_INSTANCES.SERVER_PLAYER_MANAGER.getPlayerName(r.getPlayer())));
        return new Output(records, nameMap);
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, Input input) {
        input.encode(buf);
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, Output output) {
        output.encode(buf);
    }

    @Override
    public Input decodeInput(FriendlyByteBuf buf) {
        Input i = new Input();
        i.decode(buf);
        return i;
    }

    @Override
    public Output decodeOutput(FriendlyByteBuf buf) {
        Output o = new Output();
        o.decode(buf);
        return o;
    }

    public static class Input implements INetworkPayloadConverter {
        int numFetched = 0;
        boolean isPlayerHistory;
        TradingPair market;

        public Input(int numFetched, TradingPair market, boolean isPlayerHistory){
            this.numFetched = numFetched;
            this.market = market;
            this.isPlayerHistory = isPlayerHistory;
        }

        private Input(){}

        @Override
        public void decode(FriendlyByteBuf buf) {
            isPlayerHistory = buf.readBoolean();
            numFetched = buf.readVarInt();
            if(buf.readBoolean()){
                market = new TradingPair();
                market.decode(buf);
            }
        }

        @Override
        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(isPlayerHistory);
            buf.writeVarInt(numFetched);
            buf.writeBoolean(market != null && !isPlayerHistory);
            if(market != null && !isPlayerHistory){
                market.encode(buf);
            }
        }
    }
    
    public static class Output implements INetworkPayloadConverter{
        public List<OrderDataRecord> records = new ArrayList<>();
        public Map<UUID, String> nameMap = new HashMap<>();

        public Output(List<OrderDataRecord> records, Map<UUID, String> nameMap){
            this.records = records;
            this.nameMap = nameMap;
        }

        public Output(){}

        @Override
        public void decode(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            for(int i = 0; i < size; ++i){
                records.add(OrderDataRecord.fromBuf(buf));
            }

            size = buf.readVarInt();
            for(int i = 0; i < size; ++i){
                nameMap.put(buf.readUUID(), buf.readUtf());
            }
        }

        @Override
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(records.size());
            for(OrderDataRecord record : records){
                record.encode(buf);
            }
            buf.writeVarInt(nameMap.size());
            for(Map.Entry<UUID, String> entry : nameMap.entrySet()){
                buf.writeUUID(entry.getKey());
                buf.writeUtf(entry.getValue());
            }
        }
    }
}

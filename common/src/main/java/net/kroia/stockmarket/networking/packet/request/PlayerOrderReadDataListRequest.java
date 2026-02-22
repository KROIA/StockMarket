package net.kroia.stockmarket.networking.packet.request;

import net.kroia.modutilities.networking.INetworkPayloadEncoder;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.OrderReadListData;
import net.kroia.stockmarket.market.clientdata.TradingPairData;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class PlayerOrderReadDataListRequest extends StockMarketGenericRequest<PlayerOrderReadDataListRequest.InputData, OrderReadListData> {
    public static class InputData implements INetworkPayloadEncoder
    {
        public final TradingPairData tradingPairData;
        public final UUID playerUUID;

        public InputData(TradingPair pair, UUID playerUUID) {
            this.tradingPairData = new TradingPairData(pair);
            this.playerUUID = playerUUID;
        }
        private InputData(TradingPairData tradingPairData, UUID playerUUID) {
            this.tradingPairData = tradingPairData;
            this.playerUUID = playerUUID;
        }

        @Override
        public void encode(FriendlyByteBuf buf) {
            // Encode the TradingPairData and player UUID into the buffer
            tradingPairData.encode(buf);
            buf.writeUUID(playerUUID);

        }

        public static InputData decode(FriendlyByteBuf buf) {
            // Decode the TradingPairData and player UUID from the buffer
            TradingPairData tradingPairData = TradingPairData.decode(buf);
            UUID playerUUID = buf.readUUID();
            return new InputData(tradingPairData, playerUUID);
        }
    }



    @Override
    public String getRequestTypeID() {
        return PlayerOrderReadDataListRequest.class.getName();
    }

    @Override
    public OrderReadListData handleOnClient(InputData input) {
        return null;
    }

    @Override
    public OrderReadListData handleOnServer(InputData input, ServerPlayer sender) {
        if(input.playerUUID.compareTo(sender.getUUID()) != 0 && !playerIsAdmin(sender)) {
            return null; // If the player is not the owner or an admin, return null
        }

        TradingPair pair = input.tradingPairData.toTradingPair();
        if(!pair.isValid())
            return null; // If the trading pair is invalid, return null

        return BACKEND_INSTANCES.SERVER_MARKET_MANAGER.getOrderReadListData(pair, input.playerUUID);
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, InputData input) {
        input.encode(buf); // Encode the InputData into the buffer
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, OrderReadListData output) {
        buf.writeBoolean(output != null);
        if(output != null) {
            output.encode(buf); // Encode the OrderReadListData into the buffer
        }
    }

    @Override
    public InputData decodeInput(FriendlyByteBuf buf) {
        return InputData.decode(buf); // Decode the InputData from the buffer
    }

    @Override
    public OrderReadListData decodeOutput(FriendlyByteBuf buf) {
        if(buf.readBoolean()) {
            return OrderReadListData.decode(buf); // Decode the OrderReadListData from the buffer
        }
        return null; // If no data was encoded, return null
    }



}

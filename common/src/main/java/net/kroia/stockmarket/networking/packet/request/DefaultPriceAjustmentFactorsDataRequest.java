package net.kroia.stockmarket.networking.packet.request;

import net.kroia.stockmarket.market.clientdata.DefaultPriceAdjustmentFactorsData;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;


/**
 * When the client sends a request, providing "null" as input, it will receive the current parameters from the server
 * When the client sends a request, providing a non-null input, it will update the parameters on the server and receiving back the updated parameters.
 */
public class DefaultPriceAjustmentFactorsDataRequest extends StockMarketGenericRequest<DefaultPriceAdjustmentFactorsData, DefaultPriceAdjustmentFactorsData> {
    @Override
    public String getRequestTypeID() {
        return DefaultPriceAjustmentFactorsDataRequest.class.getSimpleName();
    }

    @Override
    public DefaultPriceAdjustmentFactorsData handleOnClient(DefaultPriceAdjustmentFactorsData input) {
        return null;
    }

    @Override
    public DefaultPriceAdjustmentFactorsData handleOnServer(DefaultPriceAdjustmentFactorsData input, ServerPlayer sender) {
        if(!playerIsAdmin(sender)) {
            return new DefaultPriceAdjustmentFactorsData(0,0,0); // Only allow admins to request or update default price adjustment factors
        }

        if (input == null) {
            DefaultPriceAdjustmentFactorsData data = new DefaultPriceAdjustmentFactorsData();
            data.linearFactor = BACKEND_INSTANCES.SERVER_SETTINGS.DEFAULT_MARKET_SETUP_DATA.PRICE_AJUSTMENT_LINEAR_PARAMETER.get();
            data.quadraticFactor = BACKEND_INSTANCES.SERVER_SETTINGS.DEFAULT_MARKET_SETUP_DATA.PRICE_AJUSTMENT_QUADRATIC_PARAMETER.get();
            data.exponentialFactor = BACKEND_INSTANCES.SERVER_SETTINGS.DEFAULT_MARKET_SETUP_DATA.PRICE_AJUSTMENT_EXPONENTIAL_PARAMETER.get();
            return data; // Return the current DefaultPriceAdjustmentFactorsData
        } else {
            // Update the parameters on the server
            BACKEND_INSTANCES.SERVER_SETTINGS.DEFAULT_MARKET_SETUP_DATA.PRICE_AJUSTMENT_LINEAR_PARAMETER.set(input.linearFactor);
            BACKEND_INSTANCES.SERVER_SETTINGS.DEFAULT_MARKET_SETUP_DATA.PRICE_AJUSTMENT_QUADRATIC_PARAMETER.set(input.quadraticFactor);
            BACKEND_INSTANCES.SERVER_SETTINGS.DEFAULT_MARKET_SETUP_DATA.PRICE_AJUSTMENT_EXPONENTIAL_PARAMETER.set(input.exponentialFactor);

            input.linearFactor = BACKEND_INSTANCES.SERVER_SETTINGS.DEFAULT_MARKET_SETUP_DATA.PRICE_AJUSTMENT_LINEAR_PARAMETER.get();
            input.quadraticFactor = BACKEND_INSTANCES.SERVER_SETTINGS.DEFAULT_MARKET_SETUP_DATA.PRICE_AJUSTMENT_QUADRATIC_PARAMETER.get();
            input.exponentialFactor = BACKEND_INSTANCES.SERVER_SETTINGS.DEFAULT_MARKET_SETUP_DATA.PRICE_AJUSTMENT_EXPONENTIAL_PARAMETER.get();
            return input; // Return the updated DefaultPriceAdjustmentFactorsData
        }
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, DefaultPriceAdjustmentFactorsData input) {
        buf.writeBoolean(input != null); // Encode if input is not null
        if (input != null) {
            input.encode(buf); // Encode the DefaultPriceAdjustmentFactorsData
        }
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, DefaultPriceAdjustmentFactorsData output) {
        buf.writeBoolean(output != null); // Encode if input is not null
        if (output != null) {
            output.encode(buf); // Encode the DefaultPriceAdjustmentFactorsData
        }
    }

    @Override
    public DefaultPriceAdjustmentFactorsData decodeInput(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return null; // If the input is not present, return null
        }
        return DefaultPriceAdjustmentFactorsData.decode(buf); // Decode the DefaultPriceAdjustmentFactorsData
    }

    @Override
    public DefaultPriceAdjustmentFactorsData decodeOutput(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return null; // If the output is not present, return null
        }
        return DefaultPriceAdjustmentFactorsData.decode(buf); // Decode the DefaultPriceAdjustmentFactorsData
    }
}

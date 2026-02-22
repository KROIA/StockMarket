package net.kroia.stockmarket.market.clientdata;

import net.kroia.modutilities.networking.INetworkPayloadEncoder;
import net.minecraft.network.FriendlyByteBuf;

public class DefaultPriceAdjustmentFactorsData implements INetworkPayloadEncoder {

    public float linearFactor;
    public float quadraticFactor;
    public float exponentialFactor;

    public DefaultPriceAdjustmentFactorsData() {
        linearFactor = 1.0f; // Default linear factor
        quadraticFactor = 0.0f; // Default quadratic factor
        exponentialFactor = 0.0f; // Default exponential factor
    }
    public DefaultPriceAdjustmentFactorsData(float linearFactor, float quadraticFactor, float exponentialFactor) {
        this.linearFactor = linearFactor;
        this.quadraticFactor = quadraticFactor;
        this.exponentialFactor = exponentialFactor;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(linearFactor);
        buf.writeFloat(quadraticFactor);
        buf.writeFloat(exponentialFactor);
    }

    public static DefaultPriceAdjustmentFactorsData decode(FriendlyByteBuf buf) {
        DefaultPriceAdjustmentFactorsData data = new DefaultPriceAdjustmentFactorsData();
        data.linearFactor = buf.readFloat();
        data.quadraticFactor = buf.readFloat();
        data.exponentialFactor = buf.readFloat();
        return data;
    }
}

package net.kroia.stockmarket.plugin.plugins.MarketTrend.Trend;

import net.kroia.stockmarket.plugin.base.IMarketPluginInterface;
import net.minecraft.util.Mth;

import java.util.Random;

public class MarketTrend implements IMarketTrend{
    public static final Random RANDOM = new Random();

    private final float[] weights;
    private final float strength;
    private final float decay;

    public MarketTrend(float[] weights, float strength, float decay) {
        this.weights = weights;
        this.strength = strength;
        this.decay = decay;
    }


    @Override
    public void applyImpulse(IMarketPluginInterface market) {
        float currentPrice = market.getPrice();
        float defaultPrice = market.getDefaultPrice();
        int action = getAction(RANDOM.nextFloat());

        //price change calculation
        float delta = Mth.abs(currentPrice - defaultPrice)/defaultPrice;
        float priceChange = (float) (action * (market.getPrice() * .01F * strength) * Math.exp(-decay * delta));

        //penalty if price gets too low relative to default price
        float floorThreshold = 0.05F * defaultPrice;
        if (currentPrice < floorThreshold && action == -1) {
            float proximityToFloor = currentPrice / floorThreshold;
            priceChange *= proximityToFloor * proximityToFloor;
        }

        market.addToTargetPrice(priceChange);

    }

    @Override
    public int getAction(float random) {
        return random < weights[0] ? 1 : random < weights[0] + weights[1] ? 0 : -1;
    }

    @Override
    public float[] getTrendWeights() {
        return weights;
    }
}

package net.kroia.stockmarket.api.market;

public interface IServerMarket extends IAsyncMarket, ISyncServerMarket{

    /**
     * Sets (or clears, with null) the price impact curve player fills are charged against.
     * Default: no-op, for markets without a matching engine.
     */
    default void setFlowPriceCurve(@org.jetbrains.annotations.Nullable net.kroia.stockmarket.stockmarket.market.core.FlowPriceCurve curve) {}
}

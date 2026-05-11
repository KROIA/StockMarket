package net.kroia.stockmarket.stockmarket.market.preset;

// Single item preset with default price and natural abundance
public record MarketPreset(
    String itemId,           // e.g. "minecraft:iron_ingot"
    float defaultPrice,      // relative wealth value
    float naturalAbundance   // how common in nature (high = common, low = rare)
) {}

package net.kroia.stockmarket.api;

public interface StockMarketAPI {

    /**
     * Returns the mod ID of the Stock Market mod.
     *
     * @return The mod ID as a String.
     */
    String getModID();

    /**
     * Returns the version of the Stock Market mod.
     *
     * @return The mod version as a String.
     */
    String getModVersion();

    /**
     * Returns the server market manager instance.
     * This is only available on the server side.
     *
     * @return An instance of IServerMarketManager.
     */
    IServerMarketManager getServerMarketManager();

    /**
     * Returns the client market manager instance.
     * This is only available on the client side.
     *
     * @return An instance of IClientMarketManager.
     */
    IClientMarketManager getClientMarketManager();


}

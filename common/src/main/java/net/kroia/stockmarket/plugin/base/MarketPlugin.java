package net.kroia.stockmarket.plugin.base;

import net.kroia.modutilities.networking.INetworkPayloadConverter;
import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.StockMarketModBackend;

public abstract class MarketPlugin implements ServerSaveable, INetworkPayloadConverter {
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }
    protected IMarketPluginInterface market = null;
    protected String name;
    protected boolean loggerEnabled = true;

    public MarketPlugin() {

    }
    public final void setInterface(IMarketPluginInterface market)
    {
        this.market = market;
    }
    public void setName(String name)
    {
        this.name = name;
    }

    //public abstract String getMarketPluginTypeID();

    public IMarketPluginInterface getPluginInterface() {
        return market;
    }
    public String getName() {
        return name;
    }


    public void setup()
    {

    }
    public void update()
    {

    }









    protected void info(String msg)
    {
        if(loggerEnabled)
            BACKEND_INSTANCES.LOGGER.info("[MarketPlugin: "+name+"("+ market.getTradingPair().getUltraShortDescription() + ")] " + msg);
    }
    protected void error(String msg)
    {
        if(loggerEnabled)
            BACKEND_INSTANCES.LOGGER.error("[MarketPlugin: "+name+"("+ market.getTradingPair().getUltraShortDescription() + ")] " + msg);
    }
    protected void error(String msg, Throwable e)
    {
        if(loggerEnabled)
            BACKEND_INSTANCES.LOGGER.error("[MarketPlugin: "+name+"("+ market.getTradingPair().getUltraShortDescription() + ")] " + msg, e);
    }
    protected void warn(String msg)
    {
        if(loggerEnabled)
            BACKEND_INSTANCES.LOGGER.warn("[MarketPlugin: "+name+"("+ market.getTradingPair().getUltraShortDescription() + ")] " + msg);
    }
    protected void debug(String msg)
    {
        if(loggerEnabled)
            BACKEND_INSTANCES.LOGGER.debug("[MarketPlugin: "+name+"("+ market.getTradingPair().getUltraShortDescription() + ")] " + msg);
    }
}

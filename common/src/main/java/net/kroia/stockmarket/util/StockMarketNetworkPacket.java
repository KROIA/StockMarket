package net.kroia.stockmarket.util;

import net.kroia.modutilities.networking.NetworkPacket;
import net.kroia.stockmarket.StockMarketModBackend;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public abstract class StockMarketNetworkPacket extends NetworkPacket {
    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }

    public StockMarketNetworkPacket() {
        super();
    }

    public StockMarketNetworkPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    protected void sendToServer()
    {
        BACKEND_INSTANCES.NETWORKING.sendToServer(this);
    }
    protected void sendToClient(ServerPlayer player)
    {
        BACKEND_INSTANCES.NETWORKING.sendToClient(player, this);
    }

    public void info(String message) {
        BACKEND_INSTANCES.LOGGER.info(message);
    }
    public void error(String message) {
        BACKEND_INSTANCES.LOGGER.error(message);
    }
    public void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error(message, throwable);
    }
    public void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn(message);
    }
    public void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug(message);
    }
}

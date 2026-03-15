package net.kroia.stockmarket.util;

import dev.architectury.networking.NetworkManager;
import io.netty.channel.ChannelHandlerContext;
import net.kroia.banksystem.util.BankSystemNetworkPacket;
import net.kroia.modutilities.networking.NetworkPacket;
import net.kroia.modutilities.networking.PacketHandler;
import net.kroia.stockmarket.StockMarketModBackend;
import net.minecraft.server.level.ServerPlayer;

public abstract class StockMarketNetworkPacket extends NetworkPacket {
    protected static StockMarketModBackend.CommonInstances BACKEND_COMMON_INSTANCES;
    protected static StockMarketModBackend.ServerInstances BACKEND_SERVER_INSTANCES;
    protected static StockMarketModBackend.ClientInstances BACKEND_CLIENT_INSTANCES;
    public static void setBackend(StockMarketModBackend.CommonInstances backend) {
        BACKEND_COMMON_INSTANCES = backend;
    }
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_SERVER_INSTANCES = backend;
    }
    public static void setBackend(StockMarketModBackend.ClientInstances backend) {
        BACKEND_CLIENT_INSTANCES = backend;
    }



    public static class StockMarketPacketHandler implements PacketHandler<StockMarketNetworkPacket> {

        @Override
        public void handleServer(StockMarketNetworkPacket packet, NetworkManager.PacketContext context) {
            packet.handleOnServer(context);
        }

        @Override
        public void handleClient(StockMarketNetworkPacket packet, NetworkManager.PacketContext context) {
            packet.handleOnClient(context);
        }

        public void handleServerRedirection(StockMarketNetworkPacket packet, ChannelHandlerContext redirectionContext)
        {
            packet.handleOnServer(redirectionContext);
        }
    }
    public static final StockMarketPacketHandler HANDLER = new StockMarketPacketHandler();

    /*public static final PacketHandler<StockMarketNetworkPacket> HANDLER = new PacketHandler<>(){
        @Override
        public void handleServer(StockMarketNetworkPacket packet, NetworkManager.PacketContext context) {
            packet.handleOnServer(context);
        }

        @Override
        public void handleClient(StockMarketNetworkPacket packet, NetworkManager.PacketContext context) {
            packet.handleOnClient(context);
        }


        public void handleServerRedirection(StockMarketNetworkPacket packet, ChannelHandlerContext redirectionContext)
        {
            packet.handleOnServer(redirectionContext);
        }
    };*/


    protected void handleOnClient(NetworkManager.PacketContext context)
    {

    }


    protected void handleOnServer(NetworkManager.PacketContext context)
    {

    }

    protected void handleOnServer(ChannelHandlerContext context)
    {

    }


    protected void sendToServer()
    {
        BACKEND_CLIENT_INSTANCES.NETWORKING.sendToServer(this);
    }
    protected void sendToClient(ServerPlayer player)
    {
        BACKEND_SERVER_INSTANCES.NETWORKING.sendToClient(player, this);
    }

    protected void info(String message) {
        BACKEND_COMMON_INSTANCES.LOGGER.info("[StockMarketNetworkPacket] "+ message);
    }
    protected void error(String message) {
        BACKEND_COMMON_INSTANCES.LOGGER.error("[StockMarketNetworkPacket] "+ message);
    }
    protected void error(String message, Throwable throwable) {
        BACKEND_COMMON_INSTANCES.LOGGER.error("[StockMarketNetworkPacket] "+ message, throwable);
    }
    protected void warn(String message) {
        BACKEND_COMMON_INSTANCES.LOGGER.warn("[StockMarketNetworkPacket] "+ message);
    }
    protected void debug(String message) {
        BACKEND_COMMON_INSTANCES.LOGGER.debug("[StockMarketNetworkPacket] "+ message);
    }

}

package net.kroia.stockmarket.util;

import dev.architectury.networking.NetworkManager;
import net.kroia.banksystem.api.bankmanager.ISyncServerBankManager;
import net.kroia.modutilities.networking.client_server.NetworkPacket;
import net.kroia.modutilities.networking.client_server.PacketHandler;
import net.kroia.modutilities.networking.multi_server.ForwardPacketContext;
import net.kroia.modutilities.networking.multi_server.ForwardPacketHandler;
import net.kroia.modutilities.networking.multi_server.MultiServerManager;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.marketmanager.ISyncServerMarketManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

public abstract class StockMarketNetworkPacket extends NetworkPacket {

    public static class StockMarketPacketHandler implements
            PacketHandler<StockMarketNetworkPacket>,
            ForwardPacketHandler<StockMarketNetworkPacket>
    {
        @Override
        public void handleServer(StockMarketNetworkPacket packet, NetworkManager.PacketContext context) {
            if(MultiServerManager.isRunning() && MultiServerManager.isSlave())
            {
                if(packet.needsRoutingToMaster())
                {
                    MultiServerManager.sendToMaster(context.getPlayer().getUUID(),  packet);
                }
                else
                    packet.handleOnServer(context);
            }
            else
                packet.handleOnServer(context);
        }

        @Override
        public void handleClient(StockMarketNetworkPacket packet, NetworkManager.PacketContext context) {
            packet.handleOnClient(context);
        }

        @Override
        public void handleMaster(StockMarketNetworkPacket packet, ForwardPacketContext context) {
            packet.handleOnMaster(context);
        }

        @Override
        public void handleSlave(StockMarketNetworkPacket packet, ForwardPacketContext context) {
            packet.handleOnSlave(context);
        }
    }
    public static final StockMarketPacketHandler HANDLER = new StockMarketPacketHandler();

    protected boolean needsRoutingToMaster() {return false; }

    protected void handleOnClient(NetworkManager.PacketContext context) {}
    protected void handleOnServer(NetworkManager.PacketContext context) {}
    protected void handleOnMaster(ForwardPacketContext context) {}
    protected void handleOnSlave(ForwardPacketContext context) {}

    protected ISyncServerMarketManager getSyncMarketManager()
    {
        return BACKEND_SERVER_INSTANCES.MARKET_MANAGER.getSync();
    }


    protected boolean sendToMaster()
    {
        if(MultiServerManager.isRunning() && MultiServerManager.isSlave())
        {
            return MultiServerManager.sendToMaster(null, this);
        }
        return false;
    }
    protected boolean sendToMaster(UUID senderPlayerUUID)
    {
        if(MultiServerManager.isRunning() && MultiServerManager.isSlave())
        {
            return MultiServerManager.sendToMaster(senderPlayerUUID, this);
        }
        return false;
    }
    protected void broadcastToSlaves()
    {
        if(MultiServerManager.isRunning() && MultiServerManager.isMaster())
        {
            MultiServerManager.broadcastToSlaves(this);
        }
    }
    protected void sendToSlave(String slaveID)
    {
        if(MultiServerManager.isRunning() && MultiServerManager.isMaster())
        {
            MultiServerManager.sendToSlave(slaveID, this);
        }
    }






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

    protected void sendToServer()
    {
        BACKEND_CLIENT_INSTANCES.NETWORKING.sendToServer(this);
    }
    protected void sendToClient(ServerPlayer player)
    {
        BACKEND_SERVER_INSTANCES.NETWORKING.sendToClient(player, this);
    }

    /**
     * Sends this packet to a group of clients (e.g. all players of this server).
     * Mirrors {@code BankSystemNetworkPacket.sendToClients}.
     *
     * @param players the players who will receive the packet
     */
    protected void sendToClients(List<ServerPlayer> players)
    {
        BACKEND_SERVER_INSTANCES.NETWORKING.sendToClients(players, this);
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

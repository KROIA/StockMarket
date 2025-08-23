package net.kroia.stockmarket.networking.packet.server_sender;

import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class ClientServerManagerMetaDataPacket extends StockMarketNetworkPacket {

    long ABSOLUTE_SERVER_FIRST_STARTUP_TIME_MILLIS;

    private ClientServerManagerMetaDataPacket() {
        super();
    }
    public ClientServerManagerMetaDataPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static void sendToClinet(ServerPlayer player)
    {
        ClientServerManagerMetaDataPacket packet = new ClientServerManagerMetaDataPacket();
        packet.ABSOLUTE_SERVER_FIRST_STARTUP_TIME_MILLIS = BACKEND_INSTANCES.SERVER_MARKET_MANAGER.getAbsoluteServerFirstStartupTimeMillis();
        packet.sendToClient(player);
    }


    public long getAbsoluteServerFirstStartupTimeMillis()
    {
        return ABSOLUTE_SERVER_FIRST_STARTUP_TIME_MILLIS;
    }


    @Override
    public void decode(FriendlyByteBuf buf) {
        ABSOLUTE_SERVER_FIRST_STARTUP_TIME_MILLIS = buf.readLong();
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(ABSOLUTE_SERVER_FIRST_STARTUP_TIME_MILLIS);
    }

    @Override
    protected void handleOnClient() {
        BACKEND_INSTANCES.CLIENT_MARKET_MANAGER.handlePacket(this);
    }
}

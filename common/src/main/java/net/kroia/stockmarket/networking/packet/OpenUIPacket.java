package net.kroia.stockmarket.networking.packet;

import dev.architectury.networking.NetworkManager;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.StockMarketClientHooks;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation; // mc<=1.21.10
//import net.minecraft.resources.Identifier; // mc>=1.21.11
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

public class OpenUIPacket extends StockMarketNetworkPacket {

    public static final Type<OpenUIPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StockMarketMod.MOD_ID, "open_ui_packet")); // mc<=1.21.10
    //public static final Type<OpenUIPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(StockMarketMod.MOD_ID, "open_ui_packet")); // mc>=1.21.11
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenUIPacket> STREAM_CODEC = StreamCodec.composite(
            ExtraCodecUtils.enumStreamCodec(OpenUIPacket.GUIType.class), p -> p.guiType,
            OpenUIPacket::new
    );
    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum GUIType
    {
        DEVELOPMENT,
        MANAGEMENT,
        EXPORT_RECIPES,
        /** Newspaper screen — opened by the player-level {@code /stockmarket news} command (T-076). */
        NEWS,
        /** Trade screen — opened by the StockMarket block (T-245: server-driven so a slave without master link refuses). */
        TRADE;

        /**
         * T-245: whether this screen talks to the market and so needs the master on a slave server.
         * The dev-only screens work without one.
         */
        public boolean needsMaster()
        {
            return this != DEVELOPMENT && this != EXPORT_RECIPES;
        }
    }


    private final GUIType  guiType;

    public OpenUIPacket(GUIType guiType)
    {
        this.guiType = guiType;
    }


    /**
     * Tells the player's client to open a screen. Every server-driven StockMarket screen open goes through here.
     * T-245: on a slave without a master link a screen that {@link GUIType#needsMaster() needs the master} is
     * refused and the player gets the "not connected" chat message instead. Master and standalone servers are
     * never refused.
     *
     * @param player  the player whose client opens the screen
     * @param guiType the screen to open
     */
    public static void sendToClient(ServerPlayer player, GUIType guiType)
    {
        if (guiType.needsMaster() && !MultiServerUtils.canInteractWithStockMarket(player.getUUID()))
            return;
        OpenUIPacket packet = new OpenUIPacket(guiType);
        packet.sendToClient(player);
    }





    @Override
    protected void handleOnClient(NetworkManager.PacketContext context)
    {
        StockMarketClientHooks.openGUI(guiType);
    }
}

package net.kroia.stockmarket.networking.packet;

import dev.architectury.networking.NetworkManager;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation; // mc<=1.21.10
//import net.minecraft.resources.Identifier; // mc>=1.21.11
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

/**
 * C2S (SM-313): the client asks to open the trade screen on a market, e.g. from BankSystem's company
 * screen through {@code StockMarketClientIntegration.openTradeScreen}. The player's own server answers
 * with {@link OpenUIPacket} TRADE plus the market, so a slave without a master link refuses with the
 * usual chat message ({@link OpenUIPacket#canOpen}). No block is needed; the server checks every order.
 */
public class OpenTradeScreenPacket extends StockMarketNetworkPacket {

    public static final Type<OpenTradeScreenPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StockMarketMod.MOD_ID, "open_trade_screen_packet")); // mc<=1.21.10
    //public static final Type<OpenTradeScreenPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(StockMarketMod.MOD_ID, "open_trade_screen_packet")); // mc>=1.21.11
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTradeScreenPacket> STREAM_CODEC = StreamCodec.composite(
            ItemID.STREAM_CODEC, p -> p.market,
            OpenTradeScreenPacket::new
    );

    private final ItemID market;

    /** @param market the market the trade screen opens on */
    public OpenTradeScreenPacket(ItemID market) {
        this.market = market;
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Handled by the player's own server, not the master: that server decides whether the screen may open. */
    @Override
    protected boolean needsRoutingToMaster() { return false; }

    /**
     * Sends the request to the server. Client thread only.
     *
     * @param market the market the trade screen opens on
     */
    public static void send(ItemID market) {
        new OpenTradeScreenPacket(market).sendToServer();
    }

    @Override
    protected void handleOnServer(NetworkManager.PacketContext context) {
        if (context.getPlayer() instanceof ServerPlayer player)
            OpenUIPacket.sendToClient(player, OpenUIPacket.GUIType.TRADE, market);
    }
}

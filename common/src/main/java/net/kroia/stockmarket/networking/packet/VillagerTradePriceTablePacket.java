package net.kroia.stockmarket.networking.packet;

import net.kroia.modutilities.networking.multi_server.ForwardPacketContext;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.kroia.stockmarket.villagertrading.VillagerTradePriceTable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * One-way <b>server-to-server</b> broadcast carrying the current
 * {@link VillagerTradePriceTable} from the master server to all slave servers.
 * <p>
 * <b>Why a Packet and not a Request/Stream:</b> per the project's networking
 * conventions this is a master-initiated, fire-and-forget broadcast — exactly
 * the Packet use case. Clients never receive it: rewritten offers reach
 * clients through vanilla's own {@code ClientboundMerchantOffersPacket} when
 * the trade menu opens.
 * <p>
 * <b>When it is sent:</b>
 * <ul>
 *   <li>whenever the master's refresh interval elapses
 *       ({@code VillagerTradeManager.tickMaster()}), <i>even when the feature is
 *       disabled</i> — slaves must learn about the disabling so they lazily
 *       restore their villagers' original offers;</li>
 *   <li>when a slave (re)connects (BankSystem's
 *       {@code MASTER_SERVER_SLAVE_CONNECTED} signal, which fires <i>after</i>
 *       the ItemID sync, so the table's ItemID shorts are resolvable on the
 *       slave).</li>
 * </ul>
 */
public class VillagerTradePriceTablePacket extends StockMarketNetworkPacket {

    public static final Type<VillagerTradePriceTablePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StockMarketMod.MOD_ID, "villager_trade_price_table_packet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerTradePriceTablePacket> STREAM_CODEC =
            StreamCodec.composite(
                    VillagerTradePriceTable.STREAM_CODEC, p -> p.table,
                    VillagerTradePriceTablePacket::new
            );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** The price table snapshot computed on the master server. */
    private final VillagerTradePriceTable table;

    public VillagerTradePriceTablePacket(VillagerTradePriceTable table) {
        this.table = table;
    }

    /**
     * Broadcasts the given price table to every connected slave server.
     * No-op unless this server runs as a multi-server master (single-server
     * setups need no sync — the master rewrites its own villagers directly).
     *
     * @param table the table to broadcast
     */
    public static void broadcast(VillagerTradePriceTable table) {
        new VillagerTradePriceTablePacket(table).broadcastToSlaves();
    }

    /**
     * Slave-side handler: swaps the received table into this server's
     * {@code VillagerTradeManager} (volatile reference swap — safe regardless
     * of the receiving thread; the rewriter reads it on the main thread).
     */
    @Override
    protected void handleOnSlave(ForwardPacketContext context) {
        if (BACKEND_SERVER_INSTANCES == null || BACKEND_SERVER_INSTANCES.VILLAGER_TRADE_MANAGER == null) {
            return;
        }
        BACKEND_SERVER_INSTANCES.VILLAGER_TRADE_MANAGER.applyTable(table);
    }
}

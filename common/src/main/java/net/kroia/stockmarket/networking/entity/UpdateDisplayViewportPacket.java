package net.kroia.stockmarket.networking.entity;

import dev.architectury.networking.NetworkManager;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.minecraft.entity.custom.StockMarketDisplayBlockEntity;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//import net.minecraft.resources.ResourceLocation; // mc<=1.21.10
import net.minecraft.resources.Identifier; // mc>=1.21.11
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

/**
 * C2S packet that sends the display chart's viewport state to the server
 * for NBT persistence. Sent when the player closes the DisplayChartScreen.
 */
public class UpdateDisplayViewportPacket extends StockMarketNetworkPacket {

    public static final Type<UpdateDisplayViewportPacket> TYPE =
            //new Type<>(ResourceLocation.fromNamespaceAndPath(StockMarketMod.MOD_ID, "update_display_viewport_packet")); // mc<=1.21.10
            new Type<>(Identifier.fromNamespaceAndPath(StockMarketMod.MOD_ID, "update_display_viewport_packet")); // mc>=1.21.11

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateDisplayViewportPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, p -> p.pos,
                    ByteBufCodecs.COMPOUND_TAG, p -> p.viewport,
                    UpdateDisplayViewportPacket::new
            );

    private final BlockPos pos;
    private final CompoundTag viewport;

    public UpdateDisplayViewportPacket(BlockPos pos, CompoundTag viewport) {
        super();
        this.pos = pos;
        this.viewport = viewport;
    }

    @Override
    protected boolean needsRoutingToMaster() { return false; }

    public static void sendToServer(BlockPos pos, CompoundTag viewport) {
        new UpdateDisplayViewportPacket(pos, viewport).sendToServer();
    }

    @Override
    protected void handleOnServer(NetworkManager.PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        if (!player.level().isLoaded(pos)) return; // never load a chunk for a client-sent position

        BlockEntity blockEntity = player.level().getBlockEntity(pos);
        // SM-376: reach is measured to the whole screen, not only its top-left block
        if (blockEntity instanceof StockMarketDisplayBlockEntity displayEntity
                && displayEntity.isWithinReach(player.position())) {
            displayEntity.applyViewport(viewport);
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

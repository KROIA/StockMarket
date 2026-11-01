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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

/**
 * C2S packet that sends the display chart's viewport state to the server
 * for NBT persistence. Sent when the player closes the DisplayChartScreen.
 */
public class UpdateDisplayViewportPacket extends StockMarketNetworkPacket {

    private static final double MAX_INTERACT_DISTANCE_SQR = 64.0;

    public static final Type<UpdateDisplayViewportPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StockMarketMod.MOD_ID, "update_display_viewport_packet"));

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
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_INTERACT_DISTANCE_SQR)
            return;

        BlockEntity blockEntity = player.level().getBlockEntity(pos);
        if (blockEntity instanceof StockMarketDisplayBlockEntity displayEntity) {
            displayEntity.applyViewport(viewport);
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

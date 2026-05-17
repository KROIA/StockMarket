package net.kroia.stockmarket.networking.entity;

import dev.architectury.networking.NetworkManager;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.minecraft.entity.custom.StockMarketDisplayBlockEntity;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

public class UpdateStockMarketDisplayConfigPacket extends StockMarketNetworkPacket {

    private static final double MAX_INTERACT_DISTANCE_SQR = 64.0;

    public static final Type<UpdateStockMarketDisplayConfigPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StockMarketMod.MOD_ID, "update_stockmarket_display_config_packet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateStockMarketDisplayConfigPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, p -> p.pos,
                    ByteBufCodecs.STRING_UTF8, p -> p.displayType,
                    ByteBufCodecs.SHORT, p -> p.itemIdShort,
                    UpdateStockMarketDisplayConfigPacket::new
            );

    private final BlockPos pos;
    private final String displayType;
    private final short itemIdShort;

    public UpdateStockMarketDisplayConfigPacket(BlockPos pos, String displayType, short itemIdShort) {
        super();
        this.pos = pos;
        this.displayType = displayType;
        this.itemIdShort = itemIdShort;
    }

    @Override
    protected boolean needsRoutingToMaster() { return false; }

    public static void sendToServer(BlockPos pos, String displayType, short itemIdShort) {
        new UpdateStockMarketDisplayConfigPacket(pos, displayType, itemIdShort).sendToServer();
    }

    @Override
    protected void handleOnServer(NetworkManager.PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        if (player.distanceToSqr(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) > MAX_INTERACT_DISTANCE_SQR)
            return;

        BlockEntity blockEntity = player.level().getBlockEntity(this.pos);
        if (blockEntity instanceof StockMarketDisplayBlockEntity displayEntity) {
            StockMarketDisplayBlockEntity.DisplayType type =
                    StockMarketDisplayBlockEntity.DisplayType.fromId(this.displayType);
            ItemID itemID = (this.itemIdShort >= 0) ? new ItemID(this.itemIdShort) : null;
            if (type != StockMarketDisplayBlockEntity.DisplayType.NONE && itemID != null) {
                displayEntity.setConfig(type, itemID);
            }
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

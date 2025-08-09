package net.kroia.stockmarket.networking.packet.client_sender.update.entity;

import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class UpdateStockMarketBlockEntityPacket extends StockMarketNetworkPacket {
    private BlockPos pos;
    private TradingPair tradingPair;
    private int amount;
    private float price;



    public UpdateStockMarketBlockEntityPacket(BlockPos pos, StockMarketBlockEntity blockEntity) {
        super();
        this.pos = pos;
        this.tradingPair = blockEntity.getTradringPair();
        this.amount = blockEntity.getAmount();
        this.price = blockEntity.getPrice();
    }


    public UpdateStockMarketBlockEntityPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public BlockPos getPos() {
        return pos;
    }

    public TradingPair getItemID() {
        return tradingPair;
    }

    public int getAmount() {
        return amount;
    }

    public float getPrice() {
        return price;
    }

    public static void sendPacketToServer(BlockPos pos, StockMarketBlockEntity blockEntity) {
        new UpdateStockMarketBlockEntityPacket(pos, blockEntity).sendToServer();
    }

    @Override
    public void encode(FriendlyByteBuf buf)
    {
        buf.writeBlockPos(pos);
        tradingPair.encode(buf);
        buf.writeInt(amount);
        buf.writeFloat(price);
    }

    @Override
    public void decode(FriendlyByteBuf buf)
    {
        this.pos = buf.readBlockPos();
        if(this.tradingPair == null)
        {
            this.tradingPair = new TradingPair();
        }
        this.tradingPair.decode(buf);
        this.amount = buf.readInt();
        this.price = buf.readFloat();
    }

    @Override
    protected void handleOnServer(ServerPlayer sender)
    {
        //debug("[SERVER] Received UpdateStockMarketBlockEntityPacket from client");
        StockMarketBlockEntity blockEntity = (StockMarketBlockEntity) sender.level().getBlockEntity(this.pos);
        if(blockEntity == null)
        {
            error("BlockEntity not found at position "+this.pos);
            return;
        }
        blockEntity.setTradingPair(this.tradingPair);
        blockEntity.setAmount(this.amount);
        blockEntity.setPrice(this.price);
        blockEntity.setChanged();
        sender.level().getChunkAt(this.pos).setUnsaved(true);
    }
}

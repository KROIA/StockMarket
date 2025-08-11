package net.kroia.stockmarket.networking.packet.server_sender.update.entity;

import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.screen.custom.TradeScreen;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class SyncStockMarketBlockEntityPacket extends StockMarketNetworkPacket {
    private BlockPos pos;
    private TradingPair tradingPair;
    private float amount;
    private float price;



    public SyncStockMarketBlockEntityPacket(BlockPos pos, StockMarketBlockEntity blockEntity) {
        super();
        this.pos = pos;
        this.tradingPair = blockEntity.getTradringPair();
        this.amount = blockEntity.getAmount();
        this.price = blockEntity.getPrice();
    }


    public SyncStockMarketBlockEntityPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public BlockPos getPos() {
        return pos;
    }

    public TradingPair getTradingPair() {
        return tradingPair;
    }

    public float getAmount() {
        return amount;
    }

    public float getPrice() {
        return price;
    }

    public static void sendPacketToClient(BlockPos pos, StockMarketBlockEntity blockEntity, ServerPlayer player) {
        new SyncStockMarketBlockEntityPacket(pos, blockEntity).sendToClient(player);
    }

    @Override
    public void encode(FriendlyByteBuf buf)
    {
        buf.writeBlockPos(pos);
        tradingPair.encode(buf);
        buf.writeFloat(amount);
        buf.writeFloat(price);
    }
    @Override
    public void decode(FriendlyByteBuf buf)
    {
        this.pos = buf.readBlockPos();
        if(this.tradingPair == null)
            this.tradingPair = new TradingPair();
        this.tradingPair.decode(buf);
        this.amount = buf.readFloat();
        this.price = buf.readFloat();
    }

    @Override
    protected void handleOnClient() {
        TradeScreen.handlePacket(this);
    }
}

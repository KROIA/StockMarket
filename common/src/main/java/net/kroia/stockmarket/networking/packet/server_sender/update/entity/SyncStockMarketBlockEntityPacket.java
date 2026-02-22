package net.kroia.stockmarket.networking.packet.server_sender.update.entity;

import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.screen.custom.TradeScreen;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class SyncStockMarketBlockEntityPacket extends StockMarketNetworkPacket {
    private BlockPos pos;
    private TradingPair tradingPair;
    private int selectedBankAccountNumber;
    private float amount;
    private float price;



    public SyncStockMarketBlockEntityPacket(UUID playerUUID, BlockPos pos, StockMarketBlockEntity blockEntity) {
        super();
        this.pos = pos;
        this.tradingPair = blockEntity.getTradringPair(playerUUID);
        this.amount = blockEntity.getAmount(playerUUID);
        this.price = blockEntity.getPrice(playerUUID);
        this.selectedBankAccountNumber = blockEntity.getSelectedBankAccountNumber(playerUUID);
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
    public int getSelectedBankAccountNumber() {
        return selectedBankAccountNumber;
    }

    public static void sendPacketToClient(BlockPos pos, StockMarketBlockEntity blockEntity, ServerPlayer player) {
        new SyncStockMarketBlockEntityPacket(player.getUUID(), pos, blockEntity).sendToClient(player);
    }

    @Override
    public void encode(FriendlyByteBuf buf)
    {
        buf.writeBlockPos(pos);
        tradingPair.encode(buf);
        buf.writeFloat(amount);
        buf.writeFloat(price);
        buf.writeInt(selectedBankAccountNumber);
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
        this.selectedBankAccountNumber = buf.readInt();
    }

    @Override
    protected void handleOnClient() {
        TradeScreen.handlePacket(this);
    }
}

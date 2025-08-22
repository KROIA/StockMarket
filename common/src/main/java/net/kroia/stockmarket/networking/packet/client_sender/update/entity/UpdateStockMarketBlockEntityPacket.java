package net.kroia.stockmarket.networking.packet.client_sender.update.entity;

import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;

public class UpdateStockMarketBlockEntityPacket extends StockMarketNetworkPacket {
    private BlockPos pos;
    private TradingPair tradingPair;
    private int selectedBankAccountNumber;
    private float amount;
    private float price;



    public UpdateStockMarketBlockEntityPacket(UUID playerUUID, BlockPos pos, StockMarketBlockEntity blockEntity) {
        super();
        this.pos = pos;
        this.tradingPair = blockEntity.getTradringPair(playerUUID);
        this.amount = blockEntity.getAmount(playerUUID);
        this.price = blockEntity.getPrice(playerUUID);
        this.selectedBankAccountNumber = blockEntity.getSelectedBankAccountNumber(playerUUID);
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

    public float getAmount() {
        return amount;
    }

    public float getPrice() {
        return price;
    }
    public int getSelectedBankAccountNumber() {
        return selectedBankAccountNumber;
    }

    public static void sendPacketToServer(BlockPos pos, StockMarketBlockEntity blockEntity) {
        new UpdateStockMarketBlockEntityPacket(Minecraft.getInstance().player.getUUID(), pos, blockEntity).sendToServer();
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
        {
            this.tradingPair = new TradingPair();
        }
        this.tradingPair.decode(buf);
        this.amount = buf.readFloat();
        this.price = buf.readFloat();
        this.selectedBankAccountNumber = buf.readInt();
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
        Map<UUID, StockMarketBlockEntity.UserData> userDataMap = blockEntity.getUserDataMap();
        StockMarketBlockEntity.UserData userData = userDataMap.computeIfAbsent(sender.getUUID(), k -> new StockMarketBlockEntity.UserData());
        userData.selectedBankAccountNumber = this.selectedBankAccountNumber;
        userData.amount = this.amount;
        userData.price = this.price;
        userData.tradingPair = this.tradingPair;
        blockEntity.set(userDataMap);
        sender.level().getChunkAt(this.pos).setUnsaved(true);
    }
}

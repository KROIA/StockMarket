package net.kroia.stockmarket.networking.packet;

import net.kroia.stockmarket.banking.BankUser;
import net.kroia.stockmarket.banking.ServerBankManager;
import net.kroia.stockmarket.banking.bank.Bank;
import net.kroia.stockmarket.banking.bank.ClientBankManager;
import net.kroia.stockmarket.banking.bank.MoneyBank;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.networking.ModMessages;
import net.kroia.stockmarket.util.OrderbookVolume;
import net.kroia.stockmarket.util.PriceHistory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Supplier;

public class UpdateBankDataPacket {

    public class BankData{
        private String itemID;
        private long balance;
        private long lockedBalance;

        public BankData(FriendlyByteBuf buf)
        {
            this.itemID = buf.readUtf();
            this.balance = buf.readLong();
            this.lockedBalance = buf.readLong();
        }
        public BankData(Bank bank)
        {
            this.itemID = bank.getItemID();
            this.balance = bank.getBalance();
            this.lockedBalance = bank.getLockedBalance();
        }
        public void toBytes(FriendlyByteBuf buf)
        {
            buf.writeUtf(itemID);
            buf.writeLong(balance);
            buf.writeLong(lockedBalance);
        }
        public String getItemID() {
            return itemID;
        }
        public long getBalance() {
            return balance;
        }
        public long getLockedBalance() {
            return lockedBalance;
        }
    }

    HashMap<String, BankData> bankData;

    public UpdateBankDataPacket(BankUser user) {
        bankData = new HashMap<>();
        HashMap<String, Bank> bankMap = user.getBankMap();
        for(Bank bank : bankMap.values())
        {
            BankData data = new BankData(bank);
            bankData.put(data.itemID, data);
        }
    }
    public UpdateBankDataPacket(FriendlyByteBuf buf) {
        fromBytes(buf);
    }

    public long getBalance(String itemID)
    {
        BankData data = bankData.get(itemID);
        if(data == null)
            return 0;
        return data.getBalance();
    }
    public long getBalance()
    {
        return getBalance(MoneyBank.ITEM_ID);
    }
    public long getLockedBalance(String itemID)
    {
        BankData data = bankData.get(itemID);
        if(data == null)
            return 0;
        return data.getLockedBalance();
    }
    public long getLockedBalance()
    {
        return getLockedBalance(MoneyBank.ITEM_ID);
    }
    public boolean hasItemBank(String itemID)
    {
        return bankData.containsKey(itemID);
    }


    public static void sendPacket(ServerPlayer player)
    {
        BankUser user = ServerBankManager.getUser(player.getUUID());
        if(user == null)
            return;
        UpdateBankDataPacket packet = new UpdateBankDataPacket(user);
        ModMessages.sendToPlayer(packet, player);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(bankData.size());
        bankData.forEach((itemID, data) -> {
            data.toBytes(buf);
        });
    }
    public void fromBytes(FriendlyByteBuf buf) {
        int size = buf.readInt();
        bankData = new HashMap<>();
        for(int i = 0; i < size; i++)
        {
            BankData data = new BankData(buf);
            bankData.put(data.getItemID(), data);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // Check if on server or client
        if(contextSupplier.get().getDirection().getReceptionSide().isClient()) {
            //StockMarketMod.LOGGER.info("[CLIENT] Received current prices from the server");
            // HERE WE ARE ON THE CLIENT!
            ClientBankManager.handlePacket(this);
            context.setPacketHandled(true);
            return;
        }


        context.enqueueWork(() -> {
            // HERE WE ARE ON THE SERVER!
            // Update client-side data

        });
        context.setPacketHandled(true);
    }
}

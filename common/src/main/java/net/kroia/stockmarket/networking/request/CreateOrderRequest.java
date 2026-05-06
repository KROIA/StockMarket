package net.kroia.stockmarket.networking.request;


import net.kroia.banksystem.api.bank.BankStatus;
import net.kroia.banksystem.api.bank.IServerBank;
import net.kroia.banksystem.api.bankaccount.ISyncServerBankAccount;
import net.kroia.banksystem.api.bankmanager.IBankManager;
import net.kroia.banksystem.banking.BankPermission;
import net.kroia.banksystem.banking.User;
import net.kroia.banksystem.banking.clientdata.BankUserData;
import net.kroia.banksystem.banking.clientdata.UserData;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CreateOrderRequest extends StockMarketGenericRequest<CreateOrderRequest.InputData, CreateOrderRequest.OutputData> {

    public record InputData(ItemID itemID, int bankAccountNr, Order.Type type, double volume, double price)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, CreateOrderRequest.InputData> STREAM_CODEC = StreamCodec.composite(
                ItemID.STREAM_CODEC, p -> p.itemID,
                ByteBufCodecs.INT, p -> p.bankAccountNr,
                ExtraCodecUtils.enumStreamCodec(Order.Type.class), p -> p.type,
                ByteBufCodecs.DOUBLE, p -> p.volume,
                ByteBufCodecs.DOUBLE, p -> p.price,
                InputData::new
        );
    }

    public enum Status
    {
        CREATED,
        NO_SUCH_MARKET,
        INVALID_VOLUME,
        INVALID_PRICE,
        NO_SERVER_BANK_MANAGER, // should never occur
        NO_BANK_ACCOUNT,
        NO_BANK_USER,
        NO_ITEM_BANK,
        NO_MONEY_BANK,
        NO_PERMISSION_TO_DEPOSIT,
        NO_PERMISSION_TO_WITHDRAW,
        NOT_ENOUGH_MONEY,
        NOT_ENOUGH_ITEM,
        UNABLE_TO_LOCK_MONEY,
        UNABLE_TO_LOCK_ITEM,
        UNABLE_TO_UNLOCK_MONEY,
        UNABLE_TO_UNLOCK_ITEM,
        NO_PLAYER_SENDER
    }
    public static class OutputData
    {
        public Status status;
        public @Nullable Order orderEcho;
        public static final StreamCodec<RegistryFriendlyByteBuf, OutputData> STREAM_CODEC = StreamCodec.composite(
                ExtraCodecUtils.enumStreamCodec(Status.class), p -> p.status,
                ExtraCodecUtils.nullable(Order.STREAM_CODEC), p -> p.orderEcho,
                OutputData::new
        );
        public OutputData(Status status, @Nullable Order orderEcho)
        {
            this.status = status;
            this.orderEcho = orderEcho;
        }
    }

    @Override
    public String getRequestTypeID() {
        return CreateOrderRequest.class.getName();
    }

    @Override
    protected OutputData getDefaultResponse() {
        return new OutputData(Status.NO_SERVER_BANK_MANAGER, null);
    }

    @Override
    public CompletableFuture<OutputData> handleOnMasterServer(CreateOrderRequest.InputData input, String slaveID, @Nullable UUID playerSender) {
        if(playerSender == null || (needsRoutingToMaster() && !MultiServerUtils.canInteractWithStockMarket(playerSender)))
            return CompletableFuture.completedFuture(new OutputData(Status.NO_PLAYER_SENDER, null));

        CompletableFuture<OutputData> future = new CompletableFuture<>();
        OutputData response = new OutputData(Status.CREATED, null);
         IServerMarket serverMarket = getServerMarketManager().getMarket(input.itemID);
         if(serverMarket == null || input.volume == 0 || input.price < 0)
         {
             if(serverMarket == null)
                 response.status = Status.NO_SUCH_MARKET;
             if(input.volume == 0)
                 response.status = Status.INVALID_VOLUME;
             if(input.price < 0)
                 response.status = Status.INVALID_PRICE;
             future.complete(response);
             return future;
         }

        IBankManager serverBankManager = getServerBankManager();
        if(serverBankManager == null) {
            error("No IServerBankManager found ");
            response.status = Status.NO_SERVER_BANK_MANAGER;
            future.complete(response);
            return future;
        }

        ISyncServerBankAccount bankAccount = serverBankManager.getSync().getBankAccount(input.bankAccountNr);
        if(bankAccount == null) {
            warn("No BankAccount found with BankAccountNr " + input.bankAccountNr);
            response.status = Status.NO_BANK_ACCOUNT;
            future.complete(response);
            return future;
        }

        BankUserData bankUserData = bankAccount.getUserData(playerSender);
        if(bankUserData == null) {
            UserData userData = bankAccount.getPersonalBankOwnerData();
            if(userData == null) {
                User us = getServerBankManager().getSync().getUserByUUID(playerSender);
                String userName;
                if(us != null)
                    userName = us.getName();
                else
                    userName = playerSender.toString();
                warn("No BankUserData found with BankAccountNr " + input.bankAccountNr + " for user: " +userName+
                        "\nThis player seems not to be a member of the BankAccount.");
                response.status = Status.NO_BANK_USER;
                future.complete(response);
                return future;
            }
        }
        else {
            if (input.volume > 0) {
                if (!BankPermission.hasPermission(bankUserData.permissions, BankPermission.DEPOSIT)) {
                    response.status = Status.NO_PERMISSION_TO_DEPOSIT;
                    future.complete(response);
                    return future; // User is not allowed to deposit items by buying
                }
            } else {
                if (!BankPermission.hasPermission(bankUserData.permissions, BankPermission.WITHDRAW)) {
                    response.status = Status.NO_PERMISSION_TO_WITHDRAW;
                    future.complete(response);
                    return future; // User is not allowed to withdraw items by selling
                }
            }
        }

        if(input.type == Order.Type.INTER_MARKET) {
            response.status = Status.NO_SUCH_MARKET;
            future.complete(response);
            return future;
        }

        // Check balance
        IServerBank itemBank = bankAccount.getBank(input.itemID);
        if(itemBank == null) {
            if(input.volume < 0) {
                response.status = Status.NO_ITEM_BANK;
                future.complete(response);
                return future;
            }
            else
            {
                itemBank = bankAccount.createBank(input.itemID, 0);
            }
        }

        ItemID moneyItemID = getServerMarketManager().getTradingCurrencyID();
        IServerBank moneyBank = bankAccount.getBank(moneyItemID);
        if(moneyBank == null)
        {
            response.status = Status.NO_MONEY_BANK;
            future.complete(response);
            return future;
        }

        long currentMarketPrice = serverMarket.getCurrentMarketPrice();
        long toLockAmount = 0;
        if(input.volume > 0)
        {
            // Buy, check money balance
            switch(input.type)
            {
                case Order.Type.LIMIT:
                    try {
                        toLockAmount = Math.multiplyExact(toRawAmount(input.volume), toRawAmount(input.price));
                    } catch (ArithmeticException e) {
                        response.status = Status.INVALID_VOLUME;
                        future.complete(response);
                        return future;
                    }
                    if(moneyBank.getBalance() < toLockAmount) {
                        response.status = Status.NOT_ENOUGH_MONEY;
                        future.complete(response);
                        return future;
                    }
                    break;
                case Order.Type.MARKET:
                    try {
                        toLockAmount = Math.multiplyExact(toRawAmount(input.volume), currentMarketPrice);
                    } catch (ArithmeticException e) {
                        response.status = Status.INVALID_VOLUME;
                        future.complete(response);
                        return future;
                    }
                    if(moneyBank.getBalance() < toLockAmount) {
                        response.status = Status.NOT_ENOUGH_MONEY;
                        future.complete(response);
                        return future;
                    }
                    break;
            }
        }
        else
        {
            switch(input.type)
            {
                case Order.Type.LIMIT:
                case Order.Type.MARKET:
                    toLockAmount = toRawAmount(-input.volume);
                    if(itemBank.getBalance() < toLockAmount) {
                        response.status = Status.NOT_ENOUGH_ITEM;
                        future.complete(response);
                        return future;
                    }
                break;
            }
        }

        // Reserve balances
        if(input.volume > 0)
        {
            BankStatus lockStatus = moneyBank.lockAmount(toLockAmount);
            if(lockStatus != BankStatus.SUCCESS)
            {
                warn("Trying to lock "+toLockAmount+" of "+ moneyItemID + " for bank: " +moneyBank +
                        " of BankAccount: "+ bankAccount.getAccountNumber() + "["+bankAccount.getAccountName()+"]. Got status: "+lockStatus);
                response.status = Status.UNABLE_TO_LOCK_MONEY;
                future.complete(response);
                return future;
            }
        }
        else
        {
            BankStatus lockStatus = itemBank.lockAmount(toLockAmount);
            if(lockStatus != BankStatus.SUCCESS)
            {
                warn("Trying to lock "+toLockAmount+" of "+ input.itemID + " for bank: " +itemBank +
                        " of BankAccount: "+ bankAccount.getAccountNumber() + "["+bankAccount.getAccountName()+"]. Got status: "+lockStatus);
                response.status = Status.UNABLE_TO_LOCK_ITEM;
                future.complete(response);
                return future;
            }
        }

        long time = System.currentTimeMillis();
        Order order = new Order(input.itemID, input.type, toRawAmount(input.volume), toRawAmount(input.price), time,
                playerSender, bankAccount.getAccountNumber());

        if(!serverMarket.putOrder(order))
        {
            if(input.volume > 0) {
                BankStatus unlockStatus = moneyBank.unlockAmount(toLockAmount);
                if(unlockStatus != BankStatus.SUCCESS)
                {
                    warn("Trying to unlock "+toLockAmount+" of "+ moneyItemID + " for bank: " +moneyBank +
                            " of BankAccount: "+ bankAccount.getAccountNumber() + "["+bankAccount.getAccountName()+"] " +
                            "after failed to place order. Got status: "+unlockStatus);
                    response.status = Status.UNABLE_TO_UNLOCK_MONEY;
                    future.complete(response);
                    return future;
                }
            }
            else
            {
                BankStatus unlockStatus = itemBank.unlockAmount(toLockAmount);
                if(unlockStatus != BankStatus.SUCCESS)
                {
                    warn("Trying to unlock "+toLockAmount+" of "+ input.itemID + " for bank: " +itemBank +
                            " of BankAccount: "+ bankAccount.getAccountNumber() + "["+bankAccount.getAccountName()+"] " +
                            "after failed to place order. Got status: "+unlockStatus);
                    response.status = Status.UNABLE_TO_UNLOCK_ITEM;
                    future.complete(response);
                    return future;
                }
            }
        }
        response.status = Status.CREATED;
        response.orderEcho = order;
        future.complete(response);
        return future;
    }

    private long toRawAmount(double realAmount)
    {
        return getServerBankManager().getSync().convertToRawAmount(realAmount);
    }

    @Override
    public void encodeInput(RegistryFriendlyByteBuf buf, InputData input) {
        InputData.STREAM_CODEC.encode(buf, input);
    }

    @Override
    public void encodeOutput(RegistryFriendlyByteBuf buf, OutputData output) {
        OutputData.STREAM_CODEC.encode(buf, output);
    }

    @Override
    public InputData decodeInput(RegistryFriendlyByteBuf buf) {
        return InputData.STREAM_CODEC.decode(buf);
    }

    @Override
    public OutputData decodeOutput(RegistryFriendlyByteBuf buf) {
        return OutputData.STREAM_CODEC.decode(buf);
    }


}

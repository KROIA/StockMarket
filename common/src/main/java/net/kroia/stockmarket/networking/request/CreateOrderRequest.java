package net.kroia.stockmarket.networking.request;

import net.kroia.banksystem.api.IBank;
import net.kroia.banksystem.api.IBankAccount;
import net.kroia.banksystem.api.IServerBankManager;
import net.kroia.banksystem.banking.BankPermission;
import net.kroia.banksystem.banking.clientdata.BankUserData;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.stockmarket.market.order.Order;
import net.kroia.stockmarket.market.server.Market;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class CreateOrderRequest extends StockMarketGenericRequest<CreateOrderRequest.InputData, CreateOrderRequest.OutputData> {

    public record InputData(ItemID itemID, int bankAccountNr, Order.Type type, long volume, long price)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, CreateOrderRequest.InputData> STREAM_CODEC = StreamCodec.composite(
                ItemID.STREAM_CODEC, p -> p.itemID,
                ByteBufCodecs.INT, p -> p.bankAccountNr,
                ExtraCodecUtils.enumStreamCodec(Order.Type.class), p -> p.type,
                ByteBufCodecs.VAR_LONG, p -> p.volume,
                ByteBufCodecs.VAR_LONG, p -> p.price,
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
        UNABLE_TO_UNLOCK_ITEM
    }
    public static class OutputData
    {
        public Status status;
        public Order.@Nullable Data orderEcho;
        public static final StreamCodec<RegistryFriendlyByteBuf, OutputData> STREAM_CODEC = StreamCodec.composite(
                ExtraCodecUtils.enumStreamCodec(Status.class), p -> p.status,
                ExtraCodecUtils.nullable(Order.Data.STREAM_CODEC), p -> p.orderEcho,
                OutputData::new
        );
        public OutputData(Status status, Order.@Nullable Data orderEcho)
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
    public CompletableFuture<OutputData> handleOnServer(CreateOrderRequest.InputData input, ServerPlayer sender) {
        CompletableFuture<OutputData> future = new CompletableFuture<>();
        OutputData response = new OutputData(Status.CREATED, null);
         Market market = getServerMarketManager().getMarket(input.itemID);
         if(market == null || input.volume == 0 || input.price < 0)
         {
             if(market == null)
                 response.status = Status.NO_SUCH_MARKET;
             if(input.volume == 0)
                 response.status = Status.INVALID_VOLUME;
             if(input.price < 0)
                 response.status = Status.INVALID_PRICE;
             future.complete(response);
             return future;
         }

        IServerBankManager  serverBankManager = getServerBankManager();
        if(serverBankManager == null) {
            error("No IServerBankManager found ");
            response.status = Status.NO_SERVER_BANK_MANAGER;
            future.complete(response);
            return future;
        }

        IBankAccount bankAccount = serverBankManager.getBankAccount(input.bankAccountNr);
        if(bankAccount == null) {
            warn("No BankAccount found with BankAccountNr " + input.bankAccountNr);
            response.status = Status.NO_BANK_ACCOUNT;
            future.complete(response);
            return future;
        }

        BankUserData bankUserData = bankAccount.getUserData(sender.getUUID());
        if(bankUserData == null) {
            warn("No BankUserData found with BankAccountNr " + input.bankAccountNr + " for user: " +sender.getName()+
                    "\nThis player seems not to be a member of the BankAccount.");
            response.status = Status.NO_BANK_USER;
            future.complete(response);
            return future;
        }


        if(input.volume > 0)
        {
            if(!BankPermission.hasPermission(bankUserData.permissions, BankPermission.DEPOSIT)) {
                response.status = Status.NO_PERMISSION_TO_DEPOSIT;
                future.complete(response);
                return future; // User is not allowed to deposit items by buying
            }
        }
        else
        {
            if(!BankPermission.hasPermission(bankUserData.permissions, BankPermission.WITHDRAW)) {
                response.status = Status.NO_PERMISSION_TO_WITHDRAW;
                future.complete(response);
                return future; // User is not allowed to withdraw items by selling
            }
        }

        // Check balance
        IBank itemBank = bankAccount.getBank(input.itemID);
        if(itemBank == null) {
            response.status = Status.NO_ITEM_BANK;
            future.complete(response);
            return future;
        }

        ItemID moneyItemID = getServerMarketManager().getTradingCurrencyID();
        IBank moneyBank = bankAccount.getBank(moneyItemID);
        if(moneyBank == null)
        {
            response.status = Status.NO_MONEY_BANK;
            future.complete(response);
            return future;
        }

        long currentMarketPrice = market.getCurrentMarketPrice();
        long toLockAmount = 0;
        if(input.volume > 0)
        {
            // Buy, check money balance
            switch(input.type)
            {
                case Order.Type.LIMIT:
                    toLockAmount = input.volume * input.price;
                    if(moneyBank.getBalance() < toLockAmount) {
                        response.status = Status.NOT_ENOUGH_MONEY;
                        future.complete(response);
                        return future;
                    }
                    break;
                case Order.Type.MARKET:
                    toLockAmount  = input.volume * currentMarketPrice;
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
                    toLockAmount = -input.volume;
                    if(itemBank.getBalance() < toLockAmount) {
                        response.status = Status.NOT_ENOUGH_ITEM;
                        future.complete(response);
                        return future;
                    }
                break;
            }
        }

        if(input.type == Order.Type.INTER_MARKET)
            throw new RuntimeException("CreateOrderRequest::handleOnServer not implemented for Order.Type.INTER_MARKET");

        // Reserve balances
        if(input.volume > 0)
        {
            IBank.Status lockStatus = moneyBank.lockAmount(toLockAmount);
            if(lockStatus != IBank.Status.SUCCESS)
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
            IBank.Status lockStatus = itemBank.lockAmount(toLockAmount);
            if(lockStatus != IBank.Status.SUCCESS)
            {
                warn("Trying to lock "+toLockAmount+" of "+ input.itemID + " for bank: " +itemBank +
                        " of BankAccount: "+ bankAccount.getAccountNumber() + "["+bankAccount.getAccountName()+"]. Got status: "+lockStatus);
                response.status = Status.UNABLE_TO_LOCK_ITEM;
                future.complete(response);
                return future;
            }
        }

        long time = System.currentTimeMillis();
        Order order = new Order(input.itemID, input.type, input.volume, input.price, time,
                sender.getUUID(), bankAccount.getAccountNumber());

        if(!market.putOrder(order))
        {
            if(input.volume > 0) {
                IBank.Status unlockStatus = moneyBank.unlockAmount(toLockAmount);
                if(unlockStatus != IBank.Status.SUCCESS)
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
                IBank.Status unlockStatus = itemBank.unlockAmount(toLockAmount);
                if(unlockStatus != IBank.Status.SUCCESS)
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
        response.orderEcho = order.getData();
        future.complete(response);
        return future;
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

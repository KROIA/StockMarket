package net.kroia.stockmarket.market.server.order;

import net.kroia.banksystem.api.IBank;
import net.kroia.banksystem.api.IBankUser;
import net.kroia.banksystem.banking.bank.Bank;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.TradingPair;

import java.util.UUID;

public class OrderFactory {
    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }


    public static LimitOrder createLimitOrder(UUID playerUUID, TradingPair pair, long amount, int price)
    {
        long lockedMoney = (amount>0? amount * price : 0);
        long lockedItem = (amount<0? -amount : 0);

        if(tryReserveItem(playerUUID, pair, lockedMoney, lockedItem))
            return new LimitOrder(playerUUID, amount, price, lockedMoney);
        return null;
    }
    public static LimitOrder createLimitOrder(UUID playerUUID, TradingPair pair, long amount, int price, long alreadyFilledAmount)
    {
        long lockedMoney = (amount>0? (amount-alreadyFilledAmount) * price : 0);
        long lockedItem = (amount<0? -amount : 0);

        if(tryReserveItem(playerUUID, pair, lockedMoney, price))
            return new LimitOrder(playerUUID, amount, price, lockedMoney, alreadyFilledAmount);
        return null;
    }
    public static LimitOrder createBotLimitOrder(long amount, int price)
    {
        return new LimitOrder(amount, price);
    }


    public static MarketOrder createMarketOrder(UUID playerUUID, TradingPair pair, long amount, int currentMarketPrice)
    {
        long lockedMoney = (amount>0? amount * currentMarketPrice : 0);
        long lockedItem = (amount<0? -amount : 0);

        if(tryReserveItem(playerUUID, pair, lockedMoney, lockedItem)) {
            return new MarketOrder(playerUUID, amount, lockedMoney);
        }
        return null;
    }
    public static MarketOrder createBotMarketOrder(long amount)
    {
        return new MarketOrder(amount);
    }


    public static boolean tryReserveItem(UUID playerUUID, TradingPair pair, long moneyAmount, long itemAmount)
    {
        if(pair == null || !pair.isValid())
            return false;


        IBankUser bankUser = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getUser(playerUUID);
        if(bankUser == null)
        {
            //PlayerUtilities.printToClientConsole(player, BankSystemTextMessages.getBankNotFoundMessage(player.getName().getString(), pair.getItem().getName()));
            return false;
        }

        IBank moneyBank = bankUser.getBank(pair.getCurrency());
        IBank itemBank = bankUser.getBank(pair.getItem());
        if(itemBank == null)
            itemBank = bankUser.createItemBank(pair.getItem(), 0, true);
        if(moneyBank == null)
            moneyBank = bankUser.createItemBank(pair.getCurrency(), 0, true);
        if(itemBank == null || moneyBank == null)
            return false;


        if(moneyAmount > 0)
        {
            // Is buy
            return tryReserveItem(moneyBank, moneyAmount);
        }

        if(itemAmount > 0)
        {
            // Is sell
            return tryReserveItem(itemBank, itemAmount);
        }
        return false;
    }
    /*public static boolean tryReserveBankFund(IBank moneyBank, IBank itemBank, ServerPlayer dbgPlayer, TradingPair pair, int amount, int price)
    {
        if(moneyBank == null)
        {
            if(dbgPlayer != null)
                PlayerUtilities.printToClientConsole(dbgPlayer, BankSystemTextMessages.getBankNotFoundMessage(dbgPlayer.getName().getString(), MoneyItem.getName()));
            return false;
        }
        if(itemBank == null)
        {
            if(dbgPlayer != null)
                PlayerUtilities.printToClientConsole(dbgPlayer, BankSystemTextMessages.getBankNotFoundMessage(dbgPlayer.getName().getString(), MoneyItem.getName()));
            return false;
        }
        if(amount > 0) {
            if (moneyBank.lockAmount((long) price * amount) != Bank.Status.SUCCESS) {
                if(dbgPlayer != null)
                    PlayerUtilities.printToClientConsole(dbgPlayer, StockMarketTextMessages.getInsufficientFundToBuyMessage(pair.getCurrency().getName(), amount, price));
                return false;
            }
        }
        else {
            if (itemBank.lockAmount(-amount) != Bank.Status.SUCCESS){
                if(dbgPlayer != null)
                    PlayerUtilities.printToClientConsole(dbgPlayer, StockMarketTextMessages.getInsufficientItemsToSellMessage(pair.getItem().getName(), amount));
                return false;
            }
        }
        return true;
    }*/
    public static boolean tryReserveItem(IBank bank, long amount)
    {
        if(bank == null)
        {
            //if(dbgPlayer != null)
            //    PlayerUtilities.printToClientConsole(dbgPlayer, BankSystemTextMessages.getBankNotFoundMessage(dbgPlayer.getName().getString(), MoneyItem.getName()));
            return false;
        }
        if(amount > 0) {
            if (bank.lockAmount(amount) != Bank.Status.SUCCESS) {
                //if(dbgPlayer != null)
                //    PlayerUtilities.printToClientConsole(dbgPlayer, StockMarketTextMessages.getMissingItemsMessage(bank.getItemID().getName(), amount));
                return false;
            }
        }
        return true;
    }

}

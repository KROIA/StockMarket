package net.kroia.stockmarket.market.server.order;

import net.kroia.banksystem.api.IBank;
import net.kroia.banksystem.api.IBankAccount;
import net.kroia.banksystem.banking.bank.Bank;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.server.ServerMarketManager;

import java.util.UUID;

public class OrderFactory {
    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }


    public static LimitOrder createLimitOrder(UUID playerUUID, int bankAccountNumber, TradingPair pair, float amount, int price, int priceScaleFactor, int currencyItemFractionScaleFactor, int itemFractionScaleFactor)
    {
        long rawAmount = ((long)(amount * priceScaleFactor)*itemFractionScaleFactor/priceScaleFactor);
        long lockedMoney = ServerMarketManager.scaleToBankSystemMoneyAmount((long)(rawAmount>0? rawAmount * price : 0), priceScaleFactor, currencyItemFractionScaleFactor) / itemFractionScaleFactor;
        long lockedItem = (long)((rawAmount<0? -rawAmount : 0));

        if(tryReserveItem(bankAccountNumber, pair, lockedMoney, lockedItem, rawAmount > 0)){
            LimitOrder order = new LimitOrder(playerUUID, bankAccountNumber, (long)(rawAmount), price, lockedMoney);
            return order;

        }
        return null;
    }
    /*public static LimitOrder createLimitOrder(UUID playerUUID, TradingPair pair, long amount, int price, int priceScaleFactor, int currencyItemFractionScaleFactor, int itemFractionScaleFactor, long alreadyFilledAmount)
    {
        //long lockedMoney = (amount>0? (amount-alreadyFilledAmount) * price : 0);
        long lockedMoney = ServerMarketManager.scaleToBankSystemMoneyAmount((amount>0? (amount-alreadyFilledAmount) * price * itemFractionScaleFactor : 0), priceScaleFactor, currencyItemFractionScaleFactor) / itemFractionScaleFactor;

        if(tryReserveItem(playerUUID, pair, lockedMoney, price, amount > 0))
            return new LimitOrder(playerUUID, amount, price, lockedMoney, alreadyFilledAmount);
        return null;
    }*/
    public static LimitOrder createBotLimitOrder(long amount, int price)
    {
        return new LimitOrder(amount, price);
    }


    public static MarketOrder createMarketOrder(UUID playerUUID, int bankAccountNumber, TradingPair pair, float amount, int currentMarketPrice, int priceScaleFactor, int currencyItemFractionScaleFactor, int itemFractionScaleFactor)
    {
        long rawAmount = ((long)(amount * priceScaleFactor)*itemFractionScaleFactor/priceScaleFactor);
        long lockedMoney = ServerMarketManager.scaleToBankSystemMoneyAmount((long)(rawAmount>0? rawAmount * currentMarketPrice : 0), priceScaleFactor, currencyItemFractionScaleFactor) / itemFractionScaleFactor;
        long lockedItem = (long)((rawAmount<0? -rawAmount : 0));

        if(tryReserveItem(bankAccountNumber, pair, lockedMoney, lockedItem, rawAmount > 0)) {
            return new MarketOrder(playerUUID, bankAccountNumber, (long)(rawAmount), lockedMoney);
        }
        return null;
    }
    public static MarketOrder createBotMarketOrder(long amount)
    {
        return new MarketOrder(amount);
    }


    public static boolean tryReserveItem(int bankAccountNumber, TradingPair pair, long moneyAmount, long itemAmount, boolean isBuy)
    {
        if(pair == null || !pair.isValid())
            return false;


        IBankAccount account = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getBankAccount(bankAccountNumber);
        if(account == null)
        {
            //PlayerUtilities.printToClientConsole(player, BankSystemTextMessages.getBankNotFoundMessage(player.getName().getString(), pair.getItem().getName()));
            return false;
        }

        IBank moneyBank = account.getBank(pair.getCurrency());
        IBank itemBank = account.getBank(pair.getItem());
        if(itemBank == null)
            itemBank = account.createBank(pair.getItem(), 0);
        if(moneyBank == null)
            moneyBank = account.createBank(pair.getCurrency(), 0);
        if(itemBank == null || moneyBank == null)
            return false;


        if(isBuy)
        {
            // Is buy
            return tryReserveItem(moneyBank, moneyAmount);
        }
        else
        {
            // Is sell
            return tryReserveItem(itemBank, itemAmount);
        }
    }

    public static boolean tryReserveItem(IBank bank, long amount)
    {
        if(bank == null)
        {
            return false;
        }
        if(amount > 0) {
            return bank.lockAmount(amount) == Bank.Status.SUCCESS;
        }
        return true;
    }

}

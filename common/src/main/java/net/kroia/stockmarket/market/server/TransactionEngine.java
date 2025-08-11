package net.kroia.stockmarket.market.server;

import net.kroia.banksystem.api.IBank;
import net.kroia.banksystem.api.IBankUser;
import net.kroia.banksystem.banking.bank.Bank;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ServerPlayerUtilities;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.util.StockMarketTextMessages;

import java.util.UUID;

/**
 * The transaction engine is responsible for processing orders
 * It manages the exchange of item and money between the orders
 */
public class TransactionEngine {
    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }

    public static long fill(TradingPair pair, Order o1, Order o2, int currentPrice, int priceScaleFactor, int currencyScaleFactor)
    {
        long fillAmount1 = o1.getPendingAmount();
        long fillAmount2 = o2.getPendingAmount();
        if(fillAmount1 > 0 && fillAmount2 > 0 || fillAmount1 < 0 && fillAmount2 < 0)
        {
            // same sign -> both buy or both sell
            return 0;
        }
        long fillVolume = Math.min(Math.abs(fillAmount1), Math.abs(fillAmount2));
        long fillAmount = fillVolume;
        if(fillAmount1 < 0)
            fillAmount = -fillVolume;


        UUID playerUUID1 = o1.getPlayerUUID();
        UUID playerUUID2 = o2.getPlayerUUID();
        IBankUser user1 = (playerUUID1!=null?BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getUser(playerUUID1):null);
        IBankUser user2 = (playerUUID2!=null?BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getUser(playerUUID2):null);
        IBank moneyBank1 = (user1!=null?user1.getBank(pair.getCurrency()):null);
        IBank moneyBank2 = (user2!=null?user2.getBank(pair.getCurrency()):null);
        IBank itemBank1 = (user1!=null?user1.getBank(pair.getItem()):null);
        IBank itemBank2 = (user2!=null?user2.getBank(pair.getItem()):null);

        UUID senderUUID = fillAmount > 0 ? playerUUID1 : playerUUID2;
        UUID receiverUUID = fillAmount > 0 ? playerUUID2 : playerUUID1;
        IBank senderMoneyBank = fillAmount > 0 ? moneyBank1 : moneyBank2;
        IBank receiverMoneyBank = fillAmount > 0 ? moneyBank2 : moneyBank1;
        IBank senderItemBank = fillAmount > 0 ? itemBank2 : itemBank1;
        IBank receiverItemBank = fillAmount > 0 ? itemBank1 : itemBank2;
        Order senderOrder = fillAmount > 0 ? o1 : o2;
        Order receiverOrder = fillAmount > 0 ? o2 : o1;
        ItemID senderItemID = fillAmount > 0 ? pair.getItem() : pair.getCurrency();



        if(senderOrder.isBot() && receiverOrder.isBot())
        {
            senderOrder.addFilledAmount(fillVolume);
            receiverOrder.addFilledAmount(-fillVolume);
            return fillVolume;
        }

        int itemFractionScaleFactor = senderItemBank != null ? senderItemBank.getItemFractionScaleFactor() : BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getItemFractionScaleFactor(senderItemID);
        long money = fillVolume * ServerMarketManager.scaleToBankSystemMoneyAmount(currentPrice, priceScaleFactor, currencyScaleFactor) / itemFractionScaleFactor;

        // Overflow check
        if(receiverMoneyBank != null)
        {
            if(receiverMoneyBank.getTotalBalance() + money < receiverMoneyBank.getTotalBalance())
            {
                error("Overflow while filling order from player: " + receiverUUID.toString() +
                        " Order1: " + senderOrder + " Order2: " + receiverOrder +
                        " Can't fill order");
                receiverOrder.markAsInvalid("Would lead to an variable overflow");
                return 0;
            }
        }
        if(receiverItemBank != null)
        {
            if(receiverItemBank.getTotalBalance() + fillVolume < receiverItemBank.getTotalBalance())
            {
                error("Overflow while filling order from player: " + senderUUID.toString() +
                        " Order1: " + senderOrder + " Order2: " + receiverOrder +
                        " Can't fill order");
                receiverOrder.markAsInvalid("Would lead to an variable overflow");
                return 0;
            }
        }

        if(senderOrder.isBot() || receiverOrder.isBot())
        {
            if(receiverItemBank != null)
            {
                Bank.Status status = receiverItemBank.deposit(fillVolume);
                if(status != Bank.Status.SUCCESS)
                {
                    error("Failed to deposit item for bot: " + receiverUUID.toString() +
                            " Order1: " + senderOrder + " Order2: " + receiverOrder +
                            " Can't fill order");
                    receiverOrder.markAsInvalid("");
                    return 0;
                }
            }
            if(receiverMoneyBank != null)
            {
                Bank.Status status = receiverMoneyBank.deposit(money);
                if(status != Bank.Status.SUCCESS)
                {
                    error("Failed to deposit money for bot: " + receiverUUID.toString() +
                            " Order1: " + senderOrder + " Order2: " + receiverOrder +
                            " Can't fill order");
                    receiverOrder.markAsInvalid("");
                    return 0;
                }
            }
            if(senderItemBank != null)
            {
                Bank.Status status = senderItemBank.withdrawLockedPrefered(fillVolume);
                if(status != Bank.Status.SUCCESS)
                {
                    error("Failed to withdraw item for bot: " + senderUUID.toString() +
                            " Order1: " + senderOrder + " Order2: " + receiverOrder +
                            " Can't fill order");
                    receiverOrder.markAsInvalid("");
                    return 0;
                }
            }
            if(senderMoneyBank != null)
            {
                Bank.Status status = senderMoneyBank.withdrawLockedPrefered(money);
                if(status != Bank.Status.SUCCESS)
                {
                    error("Failed to withdraw money for bot: " + senderUUID.toString() +
                            " Order1: " + senderOrder + " Order2: " + receiverOrder +
                            " Can't fill order");
                    receiverOrder.markAsInvalid("");
                    return 0;
                }
            }
            senderOrder.addFilledAmount(fillVolume);
            senderOrder.addTransferedMoney(-money);
            receiverOrder.addFilledAmount(-fillVolume);
            receiverOrder.addTransferedMoney(money);

            if(senderOrder.isFilled())
                senderOrder.markAsProcessed();
            else if(senderOrder.getStatus() == Order.Status.PENDING)
                senderOrder.setStatus(Order.Status.PARTIAL);

            if(receiverOrder.isFilled())
                receiverOrder.markAsProcessed();
            else if(receiverOrder.getStatus() == Order.Status.PENDING)
                receiverOrder.setStatus(Order.Status.PARTIAL);
            return fillVolume;
        }

        Bank.Status status = Bank.exchangeFromLockedPrefered(senderMoneyBank, receiverMoneyBank, money,    senderItemBank, receiverItemBank, fillVolume);
        if(status != Bank.Status.SUCCESS)
        {
            switch(status) {
                case FAILED_OVERFLOW:
                {
                    error("Overflow while filling order from player: " + senderUUID.toString() +
                            " Order1: " + senderOrder + " Order2: " + receiverOrder +
                            " Can't fill order");
                    ServerPlayerUtilities.printToClientConsole(senderMoneyBank.getPlayerUUID(), status.toString());
                    ServerPlayerUtilities.printToClientConsole(receiverItemBank.getPlayerUUID(), status.toString());
                    return 0;
                }
                case FAILED_NOT_ENOUGH_FUNDS: {
                    long missingMoney = (money - senderMoneyBank.getBalance() - senderMoneyBank.getLockedBalance());
                    long missingItems = (fillVolume - senderItemBank.getBalance() - senderItemBank.getLockedBalance());
                    error("Insufficient funds from player: " + senderUUID.toString() +
                            " Order1: " + senderOrder + " Order2: " + receiverOrder +
                            " Can't fill order");
                    senderOrder.markAsInvalid(StockMarketTextMessages.getInsufficientFundMessage());
                    String missingText = "";
                    if (missingMoney > 0)
                        missingText += "\n " + StockMarketTextMessages.getMissingMoneyMessage(missingMoney);
                    if (missingItems > 0)
                        missingText += "\n  " + StockMarketTextMessages.getMissingItemsMessage(senderItemID.getName(), missingItems);

                    ServerPlayerUtilities.printToClientConsole(senderMoneyBank.getPlayerUUID(),
                            StockMarketTextMessages.getInsufficientFundToConsumeMessage(receiverOrder.toString(), Bank.getFormattedAmount(currentPrice, priceScaleFactor),
                                    fillVolume,
                                    Bank.getFormattedAmount(money, priceScaleFactor)) + missingText);

                    return 0;
                }
            }
        }
        else {
            senderOrder.addFilledAmount(fillVolume);
            senderOrder.addTransferedMoney(-money);
            receiverOrder.addFilledAmount(-fillVolume);
            receiverOrder.addTransferedMoney(money);

            if(senderOrder.isFilled())
                senderOrder.markAsProcessed();
            else if(senderOrder.getStatus() == Order.Status.PENDING)
                senderOrder.setStatus(Order.Status.PARTIAL);

            if(receiverOrder.isFilled())
                receiverOrder.markAsProcessed();
            else if(receiverOrder.getStatus() == Order.Status.PENDING)
                receiverOrder.setStatus(Order.Status.PARTIAL);
        }
        return fillVolume;
    }
    public static long virtualFill(TradingPair pair, Order o1, long virtualAmount, int currentPrice, int priceScaleFactor, int currencyScaleFactor)
    {
        if(virtualAmount == 0 || o1.getAmount()-o1.getFilledAmount() == 0)
            return 0;
        if(virtualAmount > 0 && o1.isBuy() || virtualAmount < 0 && !o1.isBuy())
        {
            // same sign -> both buy or both sell
            return 0;
        }

        long fillAmount1 = o1.getPendingAmount();
        long fillVolume = Math.min(Math.abs(fillAmount1), Math.abs(virtualAmount));
        long money = (long)fillVolume * (long)currentPrice;

        if(o1.isBot())
        {
            if(o1.isBuy())
                o1.addFilledAmount(fillVolume);
            else
                o1.addFilledAmount(-fillVolume);
            return fillVolume;
        }

        long transferedMoney = 0;
        long fillAmount = fillVolume;
        if(fillAmount1 < 0)
            fillAmount = -fillVolume;

        UUID playerUUID1 = o1.getPlayerUUID();
        IBankUser user1 = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getUser(playerUUID1);
        IBank moneyBank1 = user1.getBank(pair.getCurrency());
        IBank itemBank1 = user1.getBank(pair.getItem());

        long moneyToTransfer = fillVolume * ServerMarketManager.scaleToBankSystemMoneyAmount(currentPrice, priceScaleFactor, currencyScaleFactor) / itemBank1.getItemFractionScaleFactor();
        if(o1.isBuy())
        {
            if(moneyBank1.getBalance()+(o1.getLockedMoney() + o1.getTransferedMoney()) < moneyToTransfer)
            {
                fillVolume = Math.min(fillVolume, (int)(moneyBank1.getBalance()+(o1.getLockedMoney() + o1.getTransferedMoney()) / currentPrice));
                moneyToTransfer = (long)fillVolume * (long)currentPrice;
            }

            if(moneyToTransfer > 0) {
                Bank.Status status = moneyBank1.withdrawLockedPrefered(moneyToTransfer);
                if (status != Bank.Status.SUCCESS) {
                    error("Failed to withdraw money for virtual fill: " + o1 + " Status: " + status);
                    o1.markAsInvalid("");
                    return 0;
                }
                status = itemBank1.deposit(fillVolume);
                if (status != Bank.Status.SUCCESS) {
                    error("Failed to deposit item for virtual fill: " + o1 + " Status: " + status);
                    moneyBank1.deposit(moneyToTransfer);
                    o1.markAsInvalid("");
                    return 0;
                }
                o1.addFilledAmount(fillVolume);
                o1.addTransferedMoney(-money);
            }
        }
        else
        {
            Bank.Status status = itemBank1.withdrawLockedPrefered(fillVolume);
            if(status != Bank.Status.SUCCESS)
            {
                error("Failed to withdraw item for virtual fill: " + o1 + " Status: " + status);
                o1.markAsInvalid("");
                return 0;
            }

            status = moneyBank1.deposit(moneyToTransfer);
            if(status != Bank.Status.SUCCESS)
            {
                error("Failed to deposit money for virtual fill: " + o1 + " Status: " + status);
                itemBank1.deposit(fillVolume);
                itemBank1.lockAmount(fillVolume);
                o1.markAsInvalid("Would lead to an variable overflow");
                return 0;
            }
            o1.addFilledAmount(-fillVolume);
            o1.addTransferedMoney(money);
        }
        if(o1.isFilled())
            o1.markAsProcessed();
        else if(o1.getStatus() == Order.Status.PENDING)
            o1.setStatus(Order.Status.PARTIAL);
        return fillVolume;
    }


    private static void info(String msg)
    {
        BACKEND_INSTANCES.LOGGER.info("[TransactionEngine] " + msg);
    }
    private static void error(String msg)
    {
        BACKEND_INSTANCES.LOGGER.error("[TransactionEngine] " + msg);
    }
    private static void error(String msg, Throwable e)
    {
        BACKEND_INSTANCES.LOGGER.error("[TransactionEngine] " + msg, e);
    }
    private static void warn(String msg)
    {
        BACKEND_INSTANCES.LOGGER.warn("[TransactionEngine] " + msg);
    }
    private static void debug(String msg)
    {
        BACKEND_INSTANCES.LOGGER.debug("[TransactionEngine] " + msg);
    }
}

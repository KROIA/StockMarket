package net.kroia.stockmarket.market.server;

import net.kroia.banksystem.banking.BankUser;
import net.kroia.banksystem.banking.ServerBankManager;
import net.kroia.banksystem.banking.bank.Bank;
import net.kroia.modutilities.PlayerUtilities;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.util.StockMarketTextMessages;

import java.util.UUID;

/**
 * The transaction engine is responsible for processing orders
 * It manages the exchange of item and money between the orders
 */
public class TransactionEngine {


    public static int fill(Order o1, Order o2, int currentPrice)
    {
        int fillAmount1 = o1.getAmount() - o1.getFilledAmount();
        int fillAmount2 = o2.getAmount() - o2.getFilledAmount();
        if(fillAmount1 > 0 && fillAmount2 > 0 || fillAmount1 < 0 && fillAmount2 < 0)
        {
            // same sign -> both buy or both sell
            return 0;
        }
        int fillVolume = Math.min(Math.abs(fillAmount1), Math.abs(fillAmount2));
        int fillAmount = fillVolume;
        if(fillAmount1 < 0)
            fillAmount = -fillVolume;

        long money = (long)fillVolume * (long)currentPrice;

        UUID playerUUID1 = o1.getPlayerUUID();
        UUID playerUUID2 = o2.getPlayerUUID();
        BankUser user1 = ServerBankManager.getUser(playerUUID1);
        BankUser user2 = ServerBankManager.getUser(playerUUID2);
        if(user2 == null)
        {
            user2 = ServerBankManager.getUser(playerUUID2);
        }
        Bank moneyBank1 = user1.getMoneyBank();
        Bank moneyBank2 = user2.getMoneyBank();
        Bank itemBank1 = user1.getBank(o1.getItemID());
        Bank itemBank2 = user2.getBank(o2.getItemID());
        if(moneyBank1 == null || moneyBank2 == null || itemBank1 == null || itemBank2 == null)
        {
            StockMarketMod.LOGGER.error("Bank/Itembank not found for player: " + o1.getPlayerUUID() + " or " + o2.getPlayerUUID()+
                    " Order1: " + o1 + " Order2: " + o2+
                    " Can't fill order");
            return 0;
        }

        UUID senderUUID = fillAmount > 0 ? playerUUID1 : playerUUID2;
        UUID receiverUUID = fillAmount > 0 ? playerUUID2 : playerUUID1;
        Bank senderMoneyBank = fillAmount > 0 ? moneyBank1 : moneyBank2;
        Bank receiverMoneyBank = fillAmount > 0 ? moneyBank2 : moneyBank1;
        Bank senderItemBank = fillAmount > 0 ? itemBank2 : itemBank1;
        Bank receiverItemBank = fillAmount > 0 ? itemBank1 : itemBank2;
        Order senderOrder = fillAmount > 0 ? o1 : o2;
        Order receiverOrder = fillAmount > 0 ? o2 : o1;

        // Overflow check
        if(receiverMoneyBank.getTotalBalance() + money < receiverMoneyBank.getTotalBalance() ||
           receiverItemBank.getTotalBalance() + fillVolume < receiverItemBank.getTotalBalance())
        {
            StockMarketMod.LOGGER.error("Overflow while filling order from player: " + senderUUID.toString() +
                    " Order1: " + senderOrder + " Order2: " + receiverOrder +
                    " Can't fill order");
            receiverOrder.markAsInvalid("Would lead to an variable overflow");
            return 0;
        }

        Bank.Status status = Bank.exchangeFromLockedPrefered(senderMoneyBank, receiverMoneyBank, money,    senderItemBank, receiverItemBank, fillVolume);
        if(status != Bank.Status.SUCCESS)
        {
            switch(status) {
                case FAILED_OVERFLOW:
                {
                    StockMarketMod.LOGGER.error("Overflow while filling order from player: " + senderUUID.toString() +
                            " Order1: " + senderOrder + " Order2: " + receiverOrder +
                            " Can't fill order");
                    PlayerUtilities.printToClientConsole(senderMoneyBank.getPlayerUUID(), status.toString());
                    PlayerUtilities.printToClientConsole(receiverItemBank.getPlayerUUID(), status.toString());
                    return 0;
                }
                case FAILED_NOT_ENOUGH_FUNDS: {
                    long missingMoney = (money - senderMoneyBank.getBalance() - senderMoneyBank.getLockedBalance());
                    long missingItems = (fillVolume - senderItemBank.getBalance() - senderItemBank.getLockedBalance());
                    StockMarketMod.LOGGER.error("Insufficient funds from player: " + senderUUID.toString() +
                            " Order1: " + senderOrder + " Order2: " + receiverOrder +
                            " Can't fill order");
                    senderOrder.markAsInvalid(StockMarketTextMessages.getInsufficientFundMessage());
                    String missingText = "";
                    if (missingMoney > 0)
                        missingText += "\n " + StockMarketTextMessages.getMissingMoneyMessage(missingMoney);
                    if (missingItems > 0)
                        missingText += "\n  " + StockMarketTextMessages.getMissingItemsMessage(senderOrder.getItemID(), missingItems);

                    PlayerUtilities.printToClientConsole(senderMoneyBank.getPlayerUUID(), StockMarketTextMessages.getInsufficientFundToConsumeMessage(receiverOrder.toString(), currentPrice, fillVolume, money) + missingText);

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
    public static int ghostFill(Order o1, int ghostAmount, int currentPrice)
    {
        if(ghostAmount == 0 || o1.getAmount()-o1.getFilledAmount() == 0)
            return 0;
        if(ghostAmount > 0 && o1.isBuy() || ghostAmount < 0 && !o1.isBuy())
        {
            // same sign -> both buy or both sell
            return 0;
        }

        int fillAmount1 = o1.getAmount() - o1.getFilledAmount();
        int fillVolume = Math.min(Math.abs(fillAmount1), Math.abs(ghostAmount));
        long money = (long)fillVolume * (long)currentPrice;
        long transferedMoney = 0;
        int fillAmount = fillVolume;
        if(fillAmount1 < 0)
            fillAmount = -fillVolume;

        UUID playerUUID1 = o1.getPlayerUUID();
        BankUser user1 = ServerBankManager.getUser(playerUUID1);
        Bank moneyBank1 = user1.getMoneyBank();
        Bank itemBank1 = user1.getBank(o1.getItemID());

        long moneyToTransfer = (long)fillVolume * (long)currentPrice;
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
                    StockMarketMod.LOGGER.error("Failed to withdraw money for ghost fill: " + o1 + " Status: " + status);
                    o1.markAsInvalid("");
                    return 0;
                }
                status = itemBank1.deposit(fillVolume);
                if (status != Bank.Status.SUCCESS) {
                    StockMarketMod.LOGGER.error("Failed to deposit item for ghost fill: " + o1 + " Status: " + status);
                    moneyBank1.deposit(moneyToTransfer);
                    o1.markAsInvalid("");
                    return 0;
                }
                o1.addFilledAmount(fillVolume);
                o1.addTransferedMoney(-money);

                /*if (o1.getTransferedMoney() < o1.getLockedMoney()) {
                    long toTransfer = Math.min(moneyToTransfer, o1.getLockedMoney() - o1.getTransferedMoney());
                    Bank.Status status = moneyBank1.withdrawLockedPrefered(toTransfer);
                    if (status != Bank.Status.SUCCESS) {
                        StockMarketMod.LOGGER.error("Failed to withdraw money for ghost fill: " + o1 + " Status: " + status);
                        return 0;
                    } else {
                        status = itemBank1.deposit(fillVolume);
                        if (status != Bank.Status.SUCCESS) {
                            StockMarketMod.LOGGER.error("Failed to deposit item for ghost fill: " + o1 + " Status: " + status);
                            moneyBank1.deposit(moneyToTransfer);
                            return 0;
                        }
                        transferedMoney += toTransfer;
                    }
                }

                if(transferedMoney < moneyToTransfer)
                {
                    Bank.Status status = moneyBank1.withdraw(moneyToTransfer - transferedMoney);
                    if (status != Bank.Status.SUCCESS) {
                        StockMarketMod.LOGGER.error("Failed to withdraw money for ghost fill: " + o1 + " Status: " + status);
                        return 0;
                    }
                    else
                        transferedMoney += moneyToTransfer - transferedMoney;
                }


                long lockedMoneyToTransfer = Math.min(o1.getLockedMoney() + o1.getTransferedMoney(), moneyToTransfer);
                Bank.Status status = moneyBank1.withdrawLockedPrefered(lockedMoneyToTransfer);
                if (status != Bank.Status.SUCCESS) {
                    StockMarketMod.LOGGER.error("Failed to withdraw money for ghost fill: " + o1 + " Status: " + status);
                    return 0;
                }
                status = moneyBank1.withdrawLockedPrefered(lockedMoneyToTransfer);
                if (status != Bank.Status.SUCCESS) {
                    StockMarketMod.LOGGER.error("Failed to withdraw money for ghost fill: " + o1 + " Status: " + status);
                    return 0;
                }*/


            }
        }
        else
        {
            Bank.Status status = itemBank1.withdrawLockedPrefered(fillVolume);
            if(status != Bank.Status.SUCCESS)
            {
                StockMarketMod.LOGGER.error("Failed to withdraw item for ghost fill: " + o1 + " Status: " + status);
                o1.markAsInvalid("");
                return 0;
            }

            status = moneyBank1.deposit(moneyToTransfer);
            if(status != Bank.Status.SUCCESS)
            {
                StockMarketMod.LOGGER.error("Failed to deposit money for ghost fill: " + o1 + " Status: " + status);
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
}

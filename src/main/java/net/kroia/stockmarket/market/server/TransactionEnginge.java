package net.kroia.stockmarket.market.server;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.banking.BankUser;
import net.kroia.stockmarket.banking.bank.Bank;
import net.kroia.stockmarket.banking.bank.MoneyBank;
import net.kroia.stockmarket.banking.ServerBankManager;
import net.kroia.stockmarket.market.server.order.Order;

import java.util.UUID;

/**
 * The transaction engine is responsible for processing orders
 * It manages the exchange of item and money between the orders
 */
public class TransactionEnginge {


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

        long money = fillVolume * currentPrice;

        UUID playerUUID1 = UUID.fromString(o1.getPlayerUUID());
        UUID playerUUID2 = UUID.fromString(o2.getPlayerUUID());
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

        //if(!senderBank.transferFromLockedPrefered(money, receiverBank))
        if(!Bank.exchangeFromLockedPrefered(senderMoneyBank, receiverMoneyBank, money,    senderItemBank, receiverItemBank, fillVolume))
        {
            long missingMoney = (money - senderMoneyBank.getBalance()-senderMoneyBank.getLockedBalance());
            long missingItems = (fillVolume - senderItemBank.getBalance()-senderItemBank.getLockedBalance());
            StockMarketMod.LOGGER.error("Insufficient funds from player: " + senderUUID.toString()+
                    " Order1: " + senderOrder + " Order2: " + receiverOrder+
                    " Can't fill order");
            senderOrder.markAsInvalid("Insufficient funds");
            String missingText = "";
            if(missingMoney > 0)
                missingText += "\n  Missing money: $"+missingMoney;
            if(missingItems > 0)
                missingText += "\n  Missing items: "+missingItems;
            StockMarketMod.printToClientConsole(senderUUID, "Insufficient funds to consume order:\n  "+receiverOrder.toString()+
                    "\n  price: $"+currentPrice+
                    "\n  amount: "+fillVolume+
                    "\n  total cost: $"+money +
                    missingText);

            return 0;
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
}
package net.kroia.stockmarket.market.server;

import net.kroia.stockmarket.StockMarketMod;
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
        Bank bank1 = ServerBankManager.getMoneyBank(playerUUID1);
        Bank bank2 = ServerBankManager.getMoneyBank(playerUUID2);
        if(bank1 == null || bank2 == null)
        {
            StockMarketMod.LOGGER.error("Bank not found for player: " + o1.getPlayerUUID() + " or " + o2.getPlayerUUID()+
                    " Order1: " + o1 + " Order2: " + o2+
                    " Can't fill order");
            return 0;
        }

        UUID senderUUID = fillAmount > 0 ? playerUUID1 : playerUUID2;
        UUID receiverUUID = fillAmount > 0 ? playerUUID2 : playerUUID1;
        Bank senderBank = fillAmount > 0 ? bank1 : bank2;
        Bank receiverBank = fillAmount > 0 ? bank2 : bank1;
        Order senderOrder = fillAmount > 0 ? o1 : o2;
        Order receiverOrder = fillAmount > 0 ? o2 : o1;

        if(!senderBank.transferFromLockedPrefered(money, receiverBank))
        {
            long missingAmount = (money - senderBank.getBalance()-senderBank.getLockedBalance());
            StockMarketMod.LOGGER.error("Insufficient funds from player: " + senderUUID.toString()+
                    " Order1: " + senderOrder + " Order2: " + receiverOrder+
                    " Can't fill order");
            senderOrder.markAsInvalid("Insufficient funds");
            StockMarketMod.printToClientConsole(senderUUID, "Insufficient funds to consume order:\n  "+receiverOrder.toString()+
                    "\n  price: $"+currentPrice+
                    "\n  amount: "+fillVolume+
                    "\n  total cost: $"+money +
                    "\n  missing: $"+missingAmount);

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

package net.kroia.stockmarket.market.server;

import net.kroia.banksystem.api.IBankAccount;
import net.kroia.banksystem.item.BankSystemItems;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.order.Order;
import net.minecraft.world.item.Items;

import java.util.UUID;

public class Testing {
    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
        Orderbook.setBackend(backend);
        MatchingEngine.setBackend(backend);
    }

    ItemID moneyID;

    Market market;
    int bankAccountNr1;
    IBankAccount bankAccount1;
    int bankAccountNr2;
    IBankAccount bankAccount2;
    long uniformVolumeDistributionScale = 5;

    public Testing()
    {

    }

    public boolean setup()
    {
        moneyID = ItemID.getOrRegisterFromItemStack(BankSystemItems.MONEY.get().getDefaultInstance());
        bankAccount1 = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getBankAccount(2);
        if(bankAccount1 == null)
            bankAccount1 = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().createBankAccount("UnitTestAccount_1");
        if(bankAccount1 == null)
        {
            error("Can't create UnitTestBankAccount_1");
            return false;
        }
        bankAccountNr1 = bankAccount1.getAccountNumber();

        bankAccount2 = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getBankAccount(3);
        if(bankAccount2 == null)
            bankAccount2 = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().createBankAccount("UnitTestAccount_2");
        if(bankAccount2 == null)
        {
            error("Can't create UnitTestBankAccount_2");
            return false;
        }
        bankAccountNr2 = bankAccount2.getAccountNumber();

        ItemID id = ItemID.getOrRegisterFromItemStack(Items.GOLD_INGOT.getDefaultInstance());
        market = BACKEND_INSTANCES.MARKET_MANAGER.createMarket(id);

        // Set balance
        bankAccount1.createBank(id, 100);
        bankAccount2.createBank(id, 100);

        bankAccount1.createBank(moneyID, 1000);
        bankAccount2.createBank(moneyID, 1000);

        return true;
    }


    public boolean runTests()
    {
        boolean success = true;
        success &= printTestResult(marketOrderTest_1(), "marketOrderTest_1");
        success &= printTestResult(marketOrderTest_2(), "marketOrderTest_2");
        success &= printTestResult(marketOrderTest_3(), "marketOrderTest_3");
        success &= printTestResult(marketOrderTest_4(), "marketOrderTest_4");
        success &= printTestResult(marketOrderTest_5(), "marketOrderTest_5");
        success &= printTestResult(limitOrderTest_1(), "limitOrderTest_1");
        success &= printTestResult(limitOrderTest_2(), "limitOrderTest_2");
        success &= printTestResult(limitOrderTest_3(), "limitOrderTest_3");
        success &= printTestResult(limitOrderTest_4(), "limitOrderTest_4");
        success &= printTestResult(mixedOrderTest_1(), "mixedOrderTest_1");

        return success;
    }


    private boolean marketOrderTest_1()
    {
        boolean success = true;
        market.test_setDefaultVolumeProviderFunction(this::uniformVolumeDistribution);
        uniformVolumeDistributionScale = 5;
        market.test_setCurrentMarketPrice(10);
        market.test_clearOrderbook();
        ItemID item = market.getItemID();

        bankAccount1.getBank(item).setBalance( 100);
        bankAccount2.getBank(item).setBalance( 100);

        bankAccount1.getBank(moneyID).setBalance( 1000);
        bankAccount2.getBank(moneyID).setBalance( 1000);


        //Order o1 = new Order(market.getItemID(), Order.Type.LIMIT, -10, 12, 0, UUID.randomUUID(), bankAccountNr1);
        //market.putOrder(o1);
        //market.update(); // Processes Orders
        //success &= errorIfTrue(o1.getFilledVolume() > 0, "Order should not be filled");
        Order o1 = new Order(market.getItemID(), Order.Type.MARKET, -10, market.getCurrentMarketPrice(), 0, UUID.randomUUID(), bankAccountNr1);
        market.putOrder(o1);
        market.update();
        success &= errorIfFalse(o1.isFilled(), "Order should be filled. Order: " + o1);
        success &= errorIfFalse(o1.getTransferredMoney() == 95, "5x10$ + 5x9$ should be 95$ but was: " + o1.getTransferredMoney());

        long newItemBalance = bankAccount1.getBank(market.getItemID()).getTotalBalance();
        long newMoneyBalance = bankAccount1.getBank(moneyID).getTotalBalance();

        success &= errorIfFalse(newMoneyBalance - o1.getTransferredMoney() == 1000, "Wrong money amount transfered: "+newMoneyBalance);
        success &= errorIfFalse(newItemBalance == 90, "Wrong end balance of items: "+newItemBalance);
        success &= errorIfFalse(market.getCurrentMarketPrice() == 9, "Wrong market end price: "+market.getCurrentMarketPrice());

        // Ensure multiple updates will not change the results from above
        market.update();
        market.update();
        market.update();
        market.update();


        success &= errorIfFalse(o1.isFilled(), "Order should be filled. Order: " + o1);
        success &= errorIfFalse(o1.getTransferredMoney() == 95, "5x10$ + 5x9$ should be 95$ but was: " + o1.getTransferredMoney());

        newItemBalance = bankAccount1.getBank(market.getItemID()).getTotalBalance();
        newMoneyBalance = bankAccount1.getBank(moneyID).getTotalBalance();

        success &= errorIfFalse(newMoneyBalance - o1.getTransferredMoney() == 1000, "Wrong money amount transfered: "+newMoneyBalance);
        success &= errorIfFalse(newItemBalance == 90, "Wrong end balance of items: "+newItemBalance);
        success &= errorIfFalse(market.getCurrentMarketPrice() == 9, "Wrong market end price: "+market.getCurrentMarketPrice());
        return success;
    }


    private boolean marketOrderTest_2()
    {
        boolean success = true;
        market.test_setDefaultVolumeProviderFunction(this::uniformVolumeDistribution);
        uniformVolumeDistributionScale = 5;
        market.test_setCurrentMarketPrice(10);
        market.test_clearOrderbook();
        ItemID item = market.getItemID();

        bankAccount1.getBank(item).setBalance( 100);
        bankAccount2.getBank(item).setBalance( 100);

        bankAccount1.getBank(moneyID).setBalance( 1000);
        bankAccount2.getBank(moneyID).setBalance( 1000);


        //Order o1 = new Order(market.getItemID(), Order.Type.LIMIT, -10, 12, 0, UUID.randomUUID(), bankAccountNr1);
        //market.putOrder(o1);
        //market.update(); // Processes Orders
        //success &= errorIfTrue(o1.getFilledVolume() > 0, "Order should not be filled");
        Order o1 = new Order(market.getItemID(), Order.Type.MARKET, 10, market.getCurrentMarketPrice(), 0, UUID.randomUUID(), bankAccountNr1);
        market.putOrder(o1);
        market.update();
        success &= errorIfFalse(o1.isFilled(), "Order should be filled. Order: " + o1);
        success &= errorIfFalse(o1.getTransferredMoney() == -115, "-5x11$ - 5x12$ should be -115$ but was: " + o1.getTransferredMoney());

        long newItemBalance = bankAccount1.getBank(market.getItemID()).getTotalBalance();
        long newMoneyBalance = bankAccount1.getBank(moneyID).getTotalBalance();

        success &= errorIfFalse(newMoneyBalance - o1.getTransferredMoney() == 1000, "Wrong money amount transfered: "+newMoneyBalance);
        success &= errorIfFalse(newItemBalance == 110, "Wrong end balance of items: "+newItemBalance);
        success &= errorIfFalse(market.getCurrentMarketPrice() == 12, "Wrong market end price: "+market.getCurrentMarketPrice());

        // Ensure multiple updates will not change the results from above
        market.update();
        market.update();
        market.update();
        market.update();


        success &= errorIfFalse(o1.isFilled(), "Order should be filled. Order: " + o1);
        success &= errorIfFalse(o1.getTransferredMoney() == -115, "-5x11$ - 5x12$ should be -115$ but was: " + o1.getTransferredMoney());

        newItemBalance = bankAccount1.getBank(market.getItemID()).getTotalBalance();
        newMoneyBalance = bankAccount1.getBank(moneyID).getTotalBalance();

        success &= errorIfFalse(newMoneyBalance - o1.getTransferredMoney() == 1000, "Wrong money amount transfered: "+newMoneyBalance);
        success &= errorIfFalse(newItemBalance == 110, "Wrong end balance of items: "+newItemBalance);
        success &= errorIfFalse(market.getCurrentMarketPrice() == 12, "Wrong market end price: "+market.getCurrentMarketPrice());
        return success;
    }


    private boolean marketOrderTest_3()
    {
        boolean success = true;
        market.test_setDefaultVolumeProviderFunction(this::emptyVolumeDistribution);
        market.test_setCurrentMarketPrice(10);
        market.test_clearOrderbook();
        ItemID item = market.getItemID();

        bankAccount1.getBank(item).setBalance( 100);
        bankAccount2.getBank(item).setBalance( 100);

        bankAccount1.getBank(moneyID).setBalance( 1000);
        bankAccount2.getBank(moneyID).setBalance( 1000);


        Order limitOrder = new Order(market.getItemID(), Order.Type.LIMIT, -10, 12, 0, UUID.randomUUID(), bankAccountNr1);
        market.putOrder(limitOrder);
        market.update();
        //market.putOrder(o1);
        //market.update(); // Processes Orders
        //success &= errorIfTrue(o1.getFilledVolume() > 0, "Order should not be filled");
        Order o1 = new Order(market.getItemID(), Order.Type.MARKET, 5, market.getCurrentMarketPrice(), 0, UUID.randomUUID(), bankAccountNr2);
        market.putOrder(o1);
        market.update();
        success &= errorIfFalse(o1.isFilled(), "Order should be filled. Order: " + o1);
        success &= errorIfFalse(o1.getTransferredMoney() == -limitOrder.getStartPrice()*o1.getFilledVolume(),
                " should be "+-limitOrder.getStartPrice()*o1.getTargetVolume()+"$ but was: " + o1.getTransferredMoney());

        long newItemBalance = bankAccount2.getBank(market.getItemID()).getTotalBalance();
        long newMoneyBalance = bankAccount2.getBank(moneyID).getTotalBalance();

        success &= errorIfFalse(newMoneyBalance - o1.getTransferredMoney() == 1000, "Wrong money amount transfered: "+newMoneyBalance);
        success &= errorIfFalse(newItemBalance == 105, "Wrong end balance of items: "+newItemBalance);
        success &= errorIfFalse(market.getCurrentMarketPrice() == 12, "Wrong market end price: "+market.getCurrentMarketPrice());
        success &= errorIfFalse(limitOrder.getFilledVolume() + o1.getFilledVolume() == 0, "Filled volume not correctly saved in one of the order: "+limitOrder + " or: "+o1);
        success &= errorIfFalse(bankAccount1.getBank(moneyID).getTotalBalance() + o1.getTransferredMoney() == 1000 , "Limit order has not received the correct amount of money. Balance: "+bankAccount1.getBank(moneyID).getTotalBalance());
        success &= errorIfFalse(bankAccount1.getBank(item).getTotalBalance() + o1.getFilledVolume() == 100 , "Limit order has not transfered the correct amount of items. Balance: "+bankAccount1.getBank(item).getTotalBalance());
        success &= errorIfFalse(!limitOrder.isFilled(), "Limit order is filled but it should not be");

        // Ensure multiple updates will not change the results from above
        market.update();
        market.update();
        market.update();
        market.update();


        success &= errorIfFalse(o1.isFilled(), "Order should be filled. Order: " + o1);
        success &= errorIfFalse(o1.getTransferredMoney() == -limitOrder.getStartPrice()*o1.getTargetVolume(),
                " should be "+-limitOrder.getStartPrice()*o1.getTargetVolume()+"$ but was: " + o1.getTransferredMoney());

        newItemBalance = bankAccount2.getBank(market.getItemID()).getTotalBalance();
        newMoneyBalance = bankAccount2.getBank(moneyID).getTotalBalance();

        success &= errorIfFalse(newMoneyBalance - o1.getTransferredMoney() == 1000, "Wrong money amount transfered: "+newMoneyBalance);
        success &= errorIfFalse(newItemBalance == 105, "Wrong end balance of items: "+newItemBalance);
        success &= errorIfFalse(market.getCurrentMarketPrice() == 12, "Wrong market end price: "+market.getCurrentMarketPrice());
        success &= errorIfFalse(limitOrder.getFilledVolume() + o1.getFilledVolume() == 0, "Filled volume not correctly saved in one of the order: "+limitOrder + " or: "+o1);
        success &= errorIfFalse(bankAccount1.getBank(moneyID).getTotalBalance() + o1.getTransferredMoney() == 1000 , "Limit order has not received the correct amount of money. Balance: "+bankAccount1.getBank(moneyID).getTotalBalance());
        success &= errorIfFalse(bankAccount1.getBank(item).getTotalBalance() + o1.getFilledVolume() == 100 , "Limit order has not transfered the correct amount of items. Balance: "+bankAccount1.getBank(item).getTotalBalance());
        success &= errorIfFalse(!limitOrder.isFilled(), "Limit order is filled but it should not be");

        return success;
    }

    private boolean marketOrderTest_4()
    {
        boolean success = true;
        market.test_setDefaultVolumeProviderFunction(this::emptyVolumeDistribution);
        market.test_setCurrentMarketPrice(10);
        market.test_clearOrderbook();
        ItemID item = market.getItemID();

        bankAccount1.getBank(item).setBalance( 100);
        bankAccount2.getBank(item).setBalance( 100);

        bankAccount1.getBank(moneyID).setBalance( 1000);
        bankAccount2.getBank(moneyID).setBalance( 1000);


        Order limitOrder = new Order(market.getItemID(), Order.Type.LIMIT, -10, 12, 0, UUID.randomUUID(), bankAccountNr1);
        market.putOrder(limitOrder);
        market.update();
        //market.putOrder(o1);
        //market.update(); // Processes Orders
        //success &= errorIfTrue(o1.getFilledVolume() > 0, "Order should not be filled");
        Order o1 = new Order(market.getItemID(), Order.Type.MARKET, 10, market.getCurrentMarketPrice(), 0, UUID.randomUUID(), bankAccountNr2);
        market.putOrder(o1);
        market.update();
        success &= errorIfFalse(o1.isFilled(), "Order should be filled. Order: " + o1);
        success &= errorIfFalse(o1.getTransferredMoney() == -limitOrder.getStartPrice()*o1.getFilledVolume(),
                " should be "+-limitOrder.getStartPrice()*o1.getTargetVolume()+"$ but was: " + o1.getTransferredMoney());

        long newItemBalance = bankAccount2.getBank(market.getItemID()).getTotalBalance();
        long newMoneyBalance = bankAccount2.getBank(moneyID).getTotalBalance();

        success &= errorIfFalse(newMoneyBalance - o1.getTransferredMoney() == 1000, "Wrong money amount transfered: "+newMoneyBalance);
        success &= errorIfFalse(newItemBalance == 110, "Wrong end balance of items: "+newItemBalance);
        success &= errorIfFalse(market.getCurrentMarketPrice() == 12, "Wrong market end price: "+market.getCurrentMarketPrice());
        success &= errorIfFalse(limitOrder.getFilledVolume() + o1.getFilledVolume() == 0, "Filled volume not correctly saved in one of the order: "+limitOrder + " or: "+o1);
        success &= errorIfFalse(bankAccount1.getBank(moneyID).getTotalBalance() + o1.getTransferredMoney() == 1000 , "Limit order has not received the correct amount of money. Balance: "+bankAccount1.getBank(moneyID).getTotalBalance());
        success &= errorIfFalse(bankAccount1.getBank(item).getTotalBalance() + o1.getFilledVolume() == 100 , "Limit order has not transfered the correct amount of items. Balance: "+bankAccount1.getBank(item).getTotalBalance());
        success &= errorIfFalse(limitOrder.isFilled(), "Limit order is not filled but it should be");
        // Ensure multiple updates will not change the results from above
        market.update();
        market.update();
        market.update();
        market.update();


        success &= errorIfFalse(o1.isFilled(), "Order should be filled. Order: " + o1);
        success &= errorIfFalse(o1.getTransferredMoney() == -limitOrder.getStartPrice()*o1.getTargetVolume(),
                " should be "+-limitOrder.getStartPrice()*o1.getTargetVolume()+"$ but was: " + o1.getTransferredMoney());

        newItemBalance = bankAccount2.getBank(market.getItemID()).getTotalBalance();
        newMoneyBalance = bankAccount2.getBank(moneyID).getTotalBalance();

        success &= errorIfFalse(newMoneyBalance - o1.getTransferredMoney() == 1000, "Wrong money amount transfered: "+newMoneyBalance);
        success &= errorIfFalse(newItemBalance == 110, "Wrong end balance of items: "+newItemBalance);
        success &= errorIfFalse(market.getCurrentMarketPrice() == 12, "Wrong market end price: "+market.getCurrentMarketPrice());
        success &= errorIfFalse(limitOrder.getFilledVolume() + o1.getFilledVolume() == 0, "Filled volume not correctly saved in one of the order: "+limitOrder + " or: "+o1);
        success &= errorIfFalse(bankAccount1.getBank(moneyID).getTotalBalance() + o1.getTransferredMoney() == 1000 , "Limit order has not received the correct amount of money. Balance: "+bankAccount1.getBank(moneyID).getTotalBalance());
        success &= errorIfFalse(bankAccount1.getBank(item).getTotalBalance() + o1.getFilledVolume() == 100 , "Limit order has not transfered the correct amount of items. Balance: "+bankAccount1.getBank(item).getTotalBalance());
        success &= errorIfFalse(limitOrder.isFilled(), "Limit order is not filled but it should be");
        return success;
    }

    private boolean marketOrderTest_5()
    {
        boolean success = true;
        market.test_setDefaultVolumeProviderFunction(this::emptyVolumeDistribution);
        market.test_setCurrentMarketPrice(10);
        market.test_clearOrderbook();
        ItemID item = market.getItemID();

        bankAccount1.getBank(item).setBalance( 100);
        bankAccount2.getBank(item).setBalance( 100);

        bankAccount1.getBank(moneyID).setBalance( 1000);
        bankAccount2.getBank(moneyID).setBalance( 1000);


        Order limitOrder = new Order(market.getItemID(), Order.Type.LIMIT, -10, 12, 0, UUID.randomUUID(), bankAccountNr1);
        market.putOrder(limitOrder);
        market.update();
        //market.putOrder(o1);
        //market.update(); // Processes Orders
        //success &= errorIfTrue(o1.getFilledVolume() > 0, "Order should not be filled");
        Order o1 = new Order(market.getItemID(), Order.Type.MARKET, 15, market.getCurrentMarketPrice(), 0, UUID.randomUUID(), bankAccountNr2);
        market.putOrder(o1);
        market.update();
        success &= errorIfFalse(!o1.isFilled(), "Order should not be filled. Order: " + o1);
        success &= errorIfFalse(o1.getTransferredMoney() == -limitOrder.getStartPrice()*o1.getFilledVolume(),
                " should be "+-limitOrder.getStartPrice()*o1.getTargetVolume()+"$ but was: " + o1.getTransferredMoney());

        long newItemBalance = bankAccount2.getBank(market.getItemID()).getTotalBalance();
        long newMoneyBalance = bankAccount2.getBank(moneyID).getTotalBalance();

        success &= errorIfFalse(newMoneyBalance - o1.getTransferredMoney() == 1000, "Wrong money amount transfered: "+newMoneyBalance);
        success &= errorIfFalse(newItemBalance == 110, "Wrong end balance of items: "+newItemBalance);
        success &= errorIfFalse(market.getCurrentMarketPrice() == 12, "Wrong market end price: "+market.getCurrentMarketPrice());
        success &= errorIfFalse(limitOrder.getFilledVolume() + o1.getFilledVolume() == 0, "Filled volume not correctly saved in one of the order: "+limitOrder + " or: "+o1);
        success &= errorIfFalse(bankAccount1.getBank(moneyID).getTotalBalance() + o1.getTransferredMoney() == 1000 , "Limit order has not received the correct amount of money. Balance: "+bankAccount1.getBank(moneyID).getTotalBalance());
        success &= errorIfFalse(bankAccount1.getBank(item).getTotalBalance() + o1.getFilledVolume() == 100 , "Limit order has not transfered the correct amount of items. Balance: "+bankAccount1.getBank(item).getTotalBalance());
        success &= errorIfFalse(limitOrder.isFilled(), "Limit order is not filled but it should be");
        // Ensure multiple updates will not change the results from above
        market.update();
        market.update();
        market.update();
        market.update();


        success &= errorIfFalse(!o1.isFilled(), "Order should not be filled. Order: " + o1);
        success &= errorIfFalse(o1.getTransferredMoney() == -limitOrder.getStartPrice()*o1.getFilledVolume(),
                " should be "+-limitOrder.getStartPrice()*o1.getTargetVolume()+"$ but was: " + o1.getTransferredMoney());

        newItemBalance = bankAccount2.getBank(market.getItemID()).getTotalBalance();
        newMoneyBalance = bankAccount2.getBank(moneyID).getTotalBalance();

        success &= errorIfFalse(newMoneyBalance - o1.getTransferredMoney() == 1000, "Wrong money amount transfered: "+newMoneyBalance);
        success &= errorIfFalse(newItemBalance == 110, "Wrong end balance of items: "+newItemBalance);
        success &= errorIfFalse(market.getCurrentMarketPrice() == 12, "Wrong market end price: "+market.getCurrentMarketPrice());
        success &= errorIfFalse(limitOrder.getFilledVolume() + o1.getFilledVolume() == 0, "Filled volume not correctly saved in one of the order: "+limitOrder + " or: "+o1);
        success &= errorIfFalse(bankAccount1.getBank(moneyID).getTotalBalance() + o1.getTransferredMoney() == 1000 , "Limit order has not received the correct amount of money. Balance: "+bankAccount1.getBank(moneyID).getTotalBalance());
        success &= errorIfFalse(bankAccount1.getBank(item).getTotalBalance() + o1.getFilledVolume() == 100 , "Limit order has not transfered the correct amount of items. Balance: "+bankAccount1.getBank(item).getTotalBalance());
        success &= errorIfFalse(limitOrder.isFilled(), "Limit order is not filled but it should be");
        return success;
    }


    private boolean limitOrderTest_1()
    {
        boolean success = true;
        market.test_setDefaultVolumeProviderFunction(this::uniformVolumeDistribution);
        uniformVolumeDistributionScale = 5;
        market.test_setCurrentMarketPrice(10);
        market.test_clearOrderbook();
        ItemID item = market.getItemID();

        bankAccount1.getBank(item).setBalance( 100);
        bankAccount2.getBank(item).setBalance( 100);

        bankAccount1.getBank(moneyID).setBalance( 1000);
        bankAccount2.getBank(moneyID).setBalance( 1000);

        Order limitOrder = new Order(market.getItemID(), Order.Type.LIMIT, 10, 13, 0, UUID.randomUUID(), bankAccountNr1);
        market.putOrder(limitOrder);
        market.update();

        success &= errorIfFalse(limitOrder.isFilled(), "Order should be filled. Order: " + limitOrder);
        success &= errorIfFalse(limitOrder.getTransferredMoney() == -(11*5+12*5),
                " should be "+-(11*5+12*5)+"$ but was: " + limitOrder.getTransferredMoney());

        long newItemBalance = bankAccount1.getBank(market.getItemID()).getTotalBalance();
        long newMoneyBalance = bankAccount1.getBank(moneyID).getTotalBalance();

        success &= errorIfFalse(newMoneyBalance - limitOrder.getTransferredMoney() == 1000, "Wrong money amount transfered: "+newMoneyBalance);
        success &= errorIfFalse(newItemBalance == 110, "Wrong end balance of items: "+newItemBalance);
        success &= errorIfFalse(market.getCurrentMarketPrice() == 12, "Wrong market end price: "+market.getCurrentMarketPrice());
        success &= errorIfFalse(market.getVolume(11) == 0, "Virtual orderbook at price=11 should be empty now");
        success &= errorIfFalse(market.getVolume(12) == 0, "Virtual orderbook at price=12 should be empty now");
        success &= errorIfFalse(market.getVolume(13) == -5, "Virtual orderbook at price=13 should be at -5");

        market.update();
        market.update();
        market.update();

        success &= errorIfFalse(limitOrder.isFilled(), "Order should be filled. Order: " + limitOrder);
        success &= errorIfFalse(limitOrder.getTransferredMoney() == -(11*5+12*5),
                " should be "+-(11*5+12*5)+"$ but was: " + limitOrder.getTransferredMoney());

        newItemBalance = bankAccount1.getBank(market.getItemID()).getTotalBalance();
        newMoneyBalance = bankAccount1.getBank(moneyID).getTotalBalance();

        success &= errorIfFalse(newMoneyBalance - limitOrder.getTransferredMoney() == 1000, "Wrong money amount transfered: "+newMoneyBalance);
        success &= errorIfFalse(newItemBalance == 110, "Wrong end balance of items: "+newItemBalance);
        success &= errorIfFalse(market.getCurrentMarketPrice() == 12, "Wrong market end price: "+market.getCurrentMarketPrice());
        return success;
    }

    private boolean limitOrderTest_2()
    {
        boolean success = true;
        market.test_setDefaultVolumeProviderFunction(this::uniformVolumeDistribution);
        uniformVolumeDistributionScale = 5;
        market.test_setCurrentMarketPrice(10);
        market.test_clearOrderbook();
        ItemID item = market.getItemID();

        bankAccount1.getBank(item).setBalance( 100);
        bankAccount2.getBank(item).setBalance( 100);

        bankAccount1.getBank(moneyID).setBalance( 1000);
        bankAccount2.getBank(moneyID).setBalance( 1000);

        Order limitOrder = new Order(market.getItemID(), Order.Type.LIMIT, 30, 13, 0, UUID.randomUUID(), bankAccountNr1);
        market.putOrder(limitOrder);
        market.update();

        success &= errorIfFalse(!limitOrder.isFilled(), "Order should not be filled. Order: " + limitOrder);
        success &= errorIfFalse(limitOrder.getTransferredMoney() == -(11*5+12*5+13*5),
                " should be "+-(11*5+12*5+13*5)+"$ but was: " + limitOrder.getTransferredMoney());

        long newItemBalance = bankAccount1.getBank(market.getItemID()).getTotalBalance();
        long newMoneyBalance = bankAccount1.getBank(moneyID).getTotalBalance();

        success &= errorIfFalse(newMoneyBalance - limitOrder.getTransferredMoney() == 1000, "Wrong money amount transfered: "+newMoneyBalance);
        success &= errorIfFalse(newItemBalance == 115, "Wrong end balance of items: "+newItemBalance);
        success &= errorIfFalse(market.getCurrentMarketPrice() == 13, "Wrong market end price: "+market.getCurrentMarketPrice());
        success &= errorIfFalse(market.getVolume(13) == limitOrder.getRemainingVolume(), "limit order may not be placed into the orderbook after not filling it completly.");
        success &= errorIfFalse(market.getVolume(11) == 0, "Virtual orderbook at price=11 should be empty now");
        success &= errorIfFalse(market.getVolume(12) == 0, "Virtual orderbook at price=12 should be empty now");
        success &= errorIfFalse(market.getVolume(13) == limitOrder.getRemainingVolume(), "Virtual orderbook at price=13 should be: "+limitOrder.getRemainingVolume());
        success &= errorIfFalse(market.getVolume(14) == -5, "Virtual orderbook at price=14 should be -5 now");

        market.update();
        market.update();
        market.update();

        success &= errorIfFalse(!limitOrder.isFilled(), "Order should not be filled. Order: " + limitOrder);
        success &= errorIfFalse(limitOrder.getTransferredMoney() == -(11*5+12*5+13*5),
                " should be "+-(11*5+12*5+13*5)+"$ but was: " + limitOrder.getTransferredMoney());

        newItemBalance = bankAccount1.getBank(market.getItemID()).getTotalBalance();
        newMoneyBalance = bankAccount1.getBank(moneyID).getTotalBalance();

        success &= errorIfFalse(newMoneyBalance - limitOrder.getTransferredMoney() == 1000, "Wrong money amount transfered: "+newMoneyBalance);
        success &= errorIfFalse(newItemBalance == 115, "Wrong end balance of items: "+newItemBalance);
        success &= errorIfFalse(market.getCurrentMarketPrice() == 13, "Wrong market end price: "+market.getCurrentMarketPrice());
        return success;
    }



    private boolean limitOrderTest_3()
    {
        boolean success = true;
        market.test_setDefaultVolumeProviderFunction(this::uniformVolumeDistribution);
        uniformVolumeDistributionScale = 5;
        market.test_setCurrentMarketPrice(10);
        market.test_clearOrderbook();
        ItemID item = market.getItemID();

        bankAccount1.getBank(item).setBalance( 100);
        bankAccount2.getBank(item).setBalance( 100);

        bankAccount1.getBank(moneyID).setBalance( 1000);
        bankAccount2.getBank(moneyID).setBalance( 1000);

        Order limitOrder = new Order(market.getItemID(), Order.Type.LIMIT, -10, 7, 0, UUID.randomUUID(), bankAccountNr1);
        market.putOrder(limitOrder);
        market.update();

        success &= errorIfFalse(limitOrder.isFilled(), "Order should be filled. Order: " + limitOrder);
        success &= errorIfFalse(limitOrder.getTransferredMoney() == (10*5+9*5),
                " should be "+(10*5+9*5)+"$ but was: " + limitOrder.getTransferredMoney());

        long newItemBalance = bankAccount1.getBank(market.getItemID()).getTotalBalance();
        long newMoneyBalance = bankAccount1.getBank(moneyID).getTotalBalance();

        success &= errorIfFalse(newMoneyBalance - limitOrder.getTransferredMoney() == 1000, "Wrong money amount transfered: "+newMoneyBalance);
        success &= errorIfFalse(newItemBalance == 90, "Wrong end balance of items: "+newItemBalance);
        success &= errorIfFalse(market.getCurrentMarketPrice() == 9, "Wrong market end price: "+market.getCurrentMarketPrice());
        success &= errorIfFalse(market.getVolume(10) == 0, "Virtual orderbook at price=10 should be empty now");
        success &= errorIfFalse(market.getVolume(9) == 0, "Virtual orderbook at price=9 should be empty now");
        success &= errorIfFalse(market.getVolume(8) == 5, "Virtual orderbook at price=8 should be at 5");

        market.update();
        market.update();
        market.update();

        success &= errorIfFalse(limitOrder.isFilled(), "Order should be filled. Order: " + limitOrder);
        success &= errorIfFalse(limitOrder.getTransferredMoney() == (10*5+9*5),
                " should be "+(10*5+9*5)+"$ but was: " + limitOrder.getTransferredMoney());

        newItemBalance = bankAccount1.getBank(market.getItemID()).getTotalBalance();
        newMoneyBalance = bankAccount1.getBank(moneyID).getTotalBalance();

        success &= errorIfFalse(newMoneyBalance - limitOrder.getTransferredMoney() == 1000, "Wrong money amount transfered: "+newMoneyBalance);
        success &= errorIfFalse(newItemBalance == 90, "Wrong end balance of items: "+newItemBalance);
        success &= errorIfFalse(market.getCurrentMarketPrice() == 9, "Wrong market end price: "+market.getCurrentMarketPrice());
        success &= errorIfFalse(market.getVolume(10) == 0, "Virtual orderbook at price=10 should be empty now");
        success &= errorIfFalse(market.getVolume(9) == 0, "Virtual orderbook at price=9 should be empty now");
        success &= errorIfFalse(market.getVolume(8) == 5, "Virtual orderbook at price=8 should be at 5");
        return success;
    }

    private boolean limitOrderTest_4()
    {
        boolean success = true;
        market.test_setDefaultVolumeProviderFunction(this::uniformVolumeDistribution);
        uniformVolumeDistributionScale = 5;
        market.test_setCurrentMarketPrice(10);
        market.test_clearOrderbook();
        ItemID item = market.getItemID();

        bankAccount1.getBank(item).setBalance( 100);
        bankAccount2.getBank(item).setBalance( 100);

        bankAccount1.getBank(moneyID).setBalance( 1000);
        bankAccount2.getBank(moneyID).setBalance( 1000);

        Order limitOrder = new Order(market.getItemID(), Order.Type.LIMIT, -30, 7, 0, UUID.randomUUID(), bankAccountNr1);
        market.putOrder(limitOrder);
        market.update();

        success &= errorIfFalse(!limitOrder.isFilled(), "Order should not be filled. Order: " + limitOrder);
        success &= errorIfFalse(limitOrder.getTransferredMoney() == (10*5+9*5+8*5+7*5),
                " should be "+(10*5+9*5+8*5+7*5)+"$ but was: " + limitOrder.getTransferredMoney());

        long newItemBalance = bankAccount1.getBank(market.getItemID()).getTotalBalance();
        long newMoneyBalance = bankAccount1.getBank(moneyID).getTotalBalance();

        success &= errorIfFalse(newMoneyBalance - limitOrder.getTransferredMoney() == 1000, "Wrong money amount transfered: "+newMoneyBalance);
        success &= errorIfFalse(newItemBalance == 80, "Wrong end balance of items: "+newItemBalance);
        success &= errorIfFalse(market.getCurrentMarketPrice() == 7, "Wrong market end price: "+market.getCurrentMarketPrice());
        success &= errorIfFalse(market.getVolume(10) == 0, "Virtual orderbook at price=10 should be empty now");
        success &= errorIfFalse(market.getVolume(9) == 0, "Virtual orderbook at price=9 should be empty now");
        success &= errorIfFalse(market.getVolume(8) == 0, "Virtual orderbook at price=8 should be empty now");
        success &= errorIfFalse(market.getVolume(7) == limitOrder.getRemainingVolume(), "Virtual orderbook at price=7 should be: "+limitOrder.getRemainingVolume());

        market.update();
        market.update();
        market.update();

        success &= errorIfFalse(!limitOrder.isFilled(), "Order should not be filled. Order: " + limitOrder);
        success &= errorIfFalse(limitOrder.getTransferredMoney() == (10*5+9*5+8*5+7*5),
                " should be "+(10*5+9*5+8*5+7*5)+"$ but was: " + limitOrder.getTransferredMoney());

        newItemBalance = bankAccount1.getBank(market.getItemID()).getTotalBalance();
        newMoneyBalance = bankAccount1.getBank(moneyID).getTotalBalance();

        success &= errorIfFalse(newMoneyBalance - limitOrder.getTransferredMoney() == 1000, "Wrong money amount transfered: "+newMoneyBalance);
        success &= errorIfFalse(newItemBalance == 80, "Wrong end balance of items: "+newItemBalance);
        success &= errorIfFalse(market.getCurrentMarketPrice() == 7, "Wrong market end price: "+market.getCurrentMarketPrice());
        success &= errorIfFalse(market.getVolume(10) == 0, "Virtual orderbook at price=10 should be empty now");
        success &= errorIfFalse(market.getVolume(9) == 0, "Virtual orderbook at price=9 should be empty now");
        success &= errorIfFalse(market.getVolume(8) == 0, "Virtual orderbook at price=8 should be empty now");
        success &= errorIfFalse(market.getVolume(7) == limitOrder.getRemainingVolume(), "Virtual orderbook at price=7 should be: "+limitOrder.getRemainingVolume());
        return success;
    }



    private boolean mixedOrderTest_1()
    {
        boolean success = true;
        market.test_setDefaultVolumeProviderFunction(this::emptyVolumeDistribution);
        market.test_setCurrentMarketPrice(10);
        market.test_clearOrderbook();
        ItemID item = market.getItemID();

        bankAccount1.getBank(item).setBalance( 100);
        bankAccount2.getBank(item).setBalance( 100);

        bankAccount1.getBank(moneyID).setBalance( 1000);
        bankAccount2.getBank(moneyID).setBalance( 1000);

        Order limitOrder = new Order(item, Order.Type.LIMIT, -10, 13, 0, UUID.randomUUID(), bankAccountNr1);
        market.putOrder(limitOrder);

        Order marketOrder = new Order(item, Order.Type.MARKET, 10, 10, 0, UUID.randomUUID(), bankAccountNr2);
        market.putOrder(marketOrder);
        market.update();

        success &= errorIfFalse(limitOrder.isFilled(), "LimitOrder should be filled. Order: " + limitOrder);
        success &= errorIfFalse(marketOrder.isFilled(), "MarketOrder should be filled. Order: " + marketOrder);
        success &= errorIfFalse(limitOrder.getTransferredMoney() == (10*13),
                " should be "+(10*13)+"$ but was: " + limitOrder.getTransferredMoney());
        success &= errorIfFalse(marketOrder.getTransferredMoney() == -(10*13),
                " should be "+-(10*13)+"$ but was: " + marketOrder.getTransferredMoney());

        long newItemBalance1 = bankAccount1.getBank(market.getItemID()).getTotalBalance();
        long newMoneyBalance1 = bankAccount1.getBank(moneyID).getTotalBalance();
        long newItemBalance2 = bankAccount2.getBank(market.getItemID()).getTotalBalance();
        long newMoneyBalance2 = bankAccount2.getBank(moneyID).getTotalBalance();

        success &= errorIfFalse(newMoneyBalance1 - limitOrder.getTransferredMoney() == 1000, "Wrong money amount transfered: "+newMoneyBalance1);
        success &= errorIfFalse(newItemBalance1 == 90, "Wrong end balance of items: "+newItemBalance1);
        success &= errorIfFalse(newMoneyBalance2 - marketOrder.getTransferredMoney() == 1000, "Wrong money amount transfered: "+newMoneyBalance2);
        success &= errorIfFalse(newItemBalance2 == 110, "Wrong end balance of items: "+newItemBalance2);
        success &= errorIfFalse(market.getCurrentMarketPrice() == 13, "Wrong market end price: "+market.getCurrentMarketPrice());
        success &= errorIfFalse(market.getVolume(10) == 0, "Virtual orderbook at price=10 should be empty now");
        success &= errorIfFalse(market.getVolume(11) == 0, "Virtual orderbook at price=11 should be empty now");
        success &= errorIfFalse(market.getVolume(12) == 0, "Virtual orderbook at price=12 should be empty now");
        success &= errorIfFalse(market.getVolume(13) == 0, "Virtual orderbook at price=13 should be empty now");

        market.update();
        market.update();
        market.update();

        success &= errorIfFalse(limitOrder.isFilled(), "LimitOrder should be filled. Order: " + limitOrder);
        success &= errorIfFalse(marketOrder.isFilled(), "MarketOrder should be filled. Order: " + marketOrder);
        success &= errorIfFalse(limitOrder.getTransferredMoney() == (10*13),
                " should be "+(10*13)+"$ but was: " + limitOrder.getTransferredMoney());
        success &= errorIfFalse(marketOrder.getTransferredMoney() == -(10*13),
                " should be "+-(10*13)+"$ but was: " + marketOrder.getTransferredMoney());

        newItemBalance1 = bankAccount1.getBank(market.getItemID()).getTotalBalance();
        newMoneyBalance1 = bankAccount1.getBank(moneyID).getTotalBalance();
        newItemBalance2 = bankAccount2.getBank(market.getItemID()).getTotalBalance();
        newMoneyBalance2 = bankAccount2.getBank(moneyID).getTotalBalance();

        success &= errorIfFalse(newMoneyBalance1 - limitOrder.getTransferredMoney() == 1000, "Wrong money amount transfered: "+newMoneyBalance1);
        success &= errorIfFalse(newItemBalance1 == 90, "Wrong end balance of items: "+newItemBalance1);
        success &= errorIfFalse(newMoneyBalance2 - marketOrder.getTransferredMoney() == 1000, "Wrong money amount transfered: "+newMoneyBalance2);
        success &= errorIfFalse(newItemBalance2 == 110, "Wrong end balance of items: "+newItemBalance2);
        success &= errorIfFalse(market.getCurrentMarketPrice() == 13, "Wrong market end price: "+market.getCurrentMarketPrice());
        success &= errorIfFalse(market.getVolume(10) == 0, "Virtual orderbook at price=10 should be empty now");
        success &= errorIfFalse(market.getVolume(11) == 0, "Virtual orderbook at price=11 should be empty now");
        success &= errorIfFalse(market.getVolume(12) == 0, "Virtual orderbook at price=12 should be empty now");
        success &= errorIfFalse(market.getVolume(13) == 0, "Virtual orderbook at price=13 should be empty now");

        return success;
    }


    private float emptyVolumeDistribution(long price)
    {
        return 0;
    }
    private float uniformVolumeDistribution(long price)
    {
        return uniformVolumeDistributionScale;
    }


    private boolean errorIfTrue(boolean condition, String message)
    {
        if(condition)
            error(message);
        return condition;
    }
    private boolean errorIfFalse(boolean condition, String message)
    {
        if(!condition)
            error(message);
        return condition;
    }
    private boolean printTestResult(boolean success, String message)
    {
        if(success)
            info(message + " [SUCCESS]");
        else
            error(message + " [FAILURE]");
        return success;
    }

    protected void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("[Testing]: "+message);
    }
    protected void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("[Testing]: "+message);
    }
    protected void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("[Testing]: "+message, throwable);
    }
    protected void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("[Testing]: "+message);
    }
    protected void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("[Testing]: "+message);
    }
}


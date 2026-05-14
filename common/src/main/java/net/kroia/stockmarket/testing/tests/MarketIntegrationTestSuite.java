package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.api.bankaccount.IServerBankAccount;
import net.kroia.banksystem.minecraft.item.BankSystemItems;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.world.item.Items;

import java.util.UUID;

public class MarketIntegrationTestSuite extends TestSuite {

    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;

    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
    }

    private ItemID moneyID;
    private IServerMarket serverMarket;
    private int bankAccountNr1;
    private IServerBankAccount bankAccount1;
    private int bankAccountNr2;
    private IServerBankAccount bankAccount2;
    private long uniformVolumeDistributionScale = 5;
    private int scaleFactor;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.INTEGRATION;
    }

    @Override
    public void registerTests() {
        addTest("market_order_sell_against_virtual", this::marketOrderTest_1);
        addTest("market_order_buy_against_virtual", this::marketOrderTest_2);
        addTest("market_order_buy_partial_against_limit", this::marketOrderTest_3);
        addTest("market_order_buy_full_against_limit", this::marketOrderTest_4);
        addTest("market_order_buy_overfill_against_limit", this::marketOrderTest_5);
        addTest("limit_order_buy_filled_by_virtual", this::limitOrderTest_1);
        addTest("limit_order_buy_partial_by_virtual", this::limitOrderTest_2);
        addTest("limit_order_sell_filled_by_virtual", this::limitOrderTest_3);
        addTest("limit_order_sell_partial_by_virtual", this::limitOrderTest_4);
        addTest("mixed_limit_and_market_order", this::mixedOrderTest_1);
    }

    @Override
    public void setup() {
        if (BACKEND_INSTANCES == null) {
            throw new RuntimeException("MarketIntegrationTestSuite requires BACKEND_INSTANCES to be set");
        }

        moneyID = ItemID.getOrRegisterFromItemStackServerSide_direct(BankSystemItems.MONEY.get().getDefaultInstance());
        bankAccount1 = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getSync().getBankAccount(2);
        if (bankAccount1 == null)
            bankAccount1 = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getSync().createBankAccount("UnitTestAccount_1");
        if (bankAccount1 == null)
            throw new RuntimeException("Can't create UnitTestBankAccount_1");
        bankAccountNr1 = bankAccount1.getAccountNumber();

        bankAccount2 = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getSync().getBankAccount(3);
        if (bankAccount2 == null)
            bankAccount2 = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getSync().createBankAccount("UnitTestAccount_2");
        if (bankAccount2 == null)
            throw new RuntimeException("Can't create UnitTestBankAccount_2");
        bankAccountNr2 = bankAccount2.getAccountNumber();

        ItemID id = ItemID.getOrRegisterFromItemStackServerSide_direct(Items.GOLD_INGOT.getDefaultInstance());
        serverMarket = BACKEND_INSTANCES.MARKET_MANAGER.getSync().createMarket(id);
        scaleFactor = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getSync().getItemFractionScaleFactor();

        bankAccount1.createBank(id, 100 * scaleFactor);
        bankAccount2.createBank(id, 100 * scaleFactor);
        bankAccount1.createBank(moneyID, 1000);
        bankAccount2.createBank(moneyID, 1000);
    }

    @Override
    public void teardown() {
        if (serverMarket != null) {
            serverMarket.test_setDefaultVolumeProviderFunction(this::uniformVolumeDistribution);
            serverMarket.test_resetVirtualOrderBookVolume();
        }
    }

    private void resetMarketState(boolean useUniformVolume, long price) {
        if (useUniformVolume) {
            serverMarket.test_setDefaultVolumeProviderFunction(this::uniformVolumeDistribution);
            uniformVolumeDistributionScale = 5;
        } else {
            serverMarket.test_setDefaultVolumeProviderFunction(this::emptyVolumeDistribution);
        }
        serverMarket.test_setCurrentMarketPrice(price);
        serverMarket.test_clearOrderbook();
        ItemID item = serverMarket.getItemID();

        bankAccount1.getBank(item).setBalance(100 * scaleFactor);
        bankAccount2.getBank(item).setBalance(100 * scaleFactor);
        bankAccount1.getBank(moneyID).setBalance(1000);
        bankAccount2.getBank(moneyID).setBalance(1000);
    }

    private TestResult marketOrderTest_1() {
        resetMarketState(true, 10);

        Order o1 = new Order(serverMarket.getItemID(), Order.Type.MARKET, -10 * scaleFactor, serverMarket.getCurrentMarketPrice(), 0, UUID.randomUUID(), bankAccountNr1);
        serverMarket.putOrder(o1);
        serverMarket.update();

        if (!o1.isFilled())
            return fail("Order should be filled. Order: " + o1);
        if (o1.getTransferredMoney() != 95)
            return fail("5x10$ + 5x9$ should be 95$ but was: " + o1.getTransferredMoney());

        long newItemBalance = bankAccount1.getBank(serverMarket.getItemID()).getTotalBalance();
        long newMoneyBalance = bankAccount1.getBank(moneyID).getTotalBalance();

        if (newMoneyBalance - o1.getTransferredMoney() != 1000)
            return fail("Wrong money amount transferred: " + newMoneyBalance);
        if (newItemBalance != 90 * scaleFactor)
            return fail("Wrong end balance of items: " + newItemBalance);
        if (serverMarket.getCurrentMarketPrice() != 9)
            return fail("Wrong market end price: " + serverMarket.getCurrentMarketPrice());

        serverMarket.update();
        serverMarket.update();
        serverMarket.update();
        serverMarket.update();

        if (!o1.isFilled())
            return fail("Order should still be filled after extra updates");
        if (o1.getTransferredMoney() != 95)
            return fail("TransferredMoney changed after extra updates: " + o1.getTransferredMoney());

        return pass("Market sell order against virtual orderbook fills correctly");
    }

    private TestResult marketOrderTest_2() {
        resetMarketState(true, 10);

        Order o1 = new Order(serverMarket.getItemID(), Order.Type.MARKET, 10 * scaleFactor, serverMarket.getCurrentMarketPrice(), 0, UUID.randomUUID(), bankAccountNr1);
        serverMarket.putOrder(o1);
        serverMarket.update();

        if (!o1.isFilled())
            return fail("Order should be filled. Order: " + o1);
        if (o1.getTransferredMoney() != -115)
            return fail("-5x11$ - 5x12$ should be -115$ but was: " + o1.getTransferredMoney());

        long newItemBalance = bankAccount1.getBank(serverMarket.getItemID()).getTotalBalance();
        long newMoneyBalance = bankAccount1.getBank(moneyID).getTotalBalance();

        if (newMoneyBalance - o1.getTransferredMoney() != 1000)
            return fail("Wrong money amount transferred: " + newMoneyBalance);
        if (newItemBalance != 110 * scaleFactor)
            return fail("Wrong end balance of items: " + newItemBalance);
        if (serverMarket.getCurrentMarketPrice() != 12)
            return fail("Wrong market end price: " + serverMarket.getCurrentMarketPrice());

        serverMarket.update();
        serverMarket.update();
        serverMarket.update();
        serverMarket.update();

        if (!o1.isFilled())
            return fail("Order should still be filled after extra updates");
        if (o1.getTransferredMoney() != -115)
            return fail("TransferredMoney changed after extra updates: " + o1.getTransferredMoney());

        return pass("Market buy order against virtual orderbook fills correctly");
    }

    private TestResult marketOrderTest_3() {
        resetMarketState(false, 10);

        Order limitOrder = new Order(serverMarket.getItemID(), Order.Type.LIMIT, -10 * scaleFactor, 12, 0, UUID.randomUUID(), bankAccountNr1);
        serverMarket.putOrder(limitOrder);
        serverMarket.update();

        Order o1 = new Order(serverMarket.getItemID(), Order.Type.MARKET, 5 * scaleFactor, serverMarket.getCurrentMarketPrice(), 0, UUID.randomUUID(), bankAccountNr2);
        serverMarket.putOrder(o1);
        serverMarket.update();

        if (!o1.isFilled())
            return fail("Market order should be filled. Order: " + o1);
        if (o1.getTransferredMoney() != -Math.round((double)limitOrder.getStartPrice() * o1.getFilledVolume() / scaleFactor))
            return fail("Transferred money mismatch: " + o1.getTransferredMoney());

        long newItemBalance = bankAccount2.getBank(serverMarket.getItemID()).getTotalBalance();
        long newMoneyBalance = bankAccount2.getBank(moneyID).getTotalBalance();

        if (newMoneyBalance - o1.getTransferredMoney() != 1000)
            return fail("Wrong money amount transferred: " + newMoneyBalance);
        if (newItemBalance != 105 * scaleFactor)
            return fail("Wrong end balance of items: " + newItemBalance);
        if (serverMarket.getCurrentMarketPrice() != 12)
            return fail("Wrong market end price: " + serverMarket.getCurrentMarketPrice());
        if (limitOrder.getFilledVolume() + o1.getFilledVolume() != 0)
            return fail("Filled volumes should cancel out");
        if (limitOrder.isFilled())
            return fail("Limit order should NOT be filled (only partially matched)");

        serverMarket.update();
        serverMarket.update();
        serverMarket.update();
        serverMarket.update();

        if (!o1.isFilled())
            return fail("Market order should still be filled after extra updates");

        return pass("Market buy order partially matches against limit sell order");
    }

    private TestResult marketOrderTest_4() {
        resetMarketState(false, 10);

        Order limitOrder = new Order(serverMarket.getItemID(), Order.Type.LIMIT, -10 * scaleFactor, 12, 0, UUID.randomUUID(), bankAccountNr1);
        serverMarket.putOrder(limitOrder);
        serverMarket.update();

        Order o1 = new Order(serverMarket.getItemID(), Order.Type.MARKET, 10 * scaleFactor, serverMarket.getCurrentMarketPrice(), 0, UUID.randomUUID(), bankAccountNr2);
        serverMarket.putOrder(o1);
        serverMarket.update();

        if (!o1.isFilled())
            return fail("Market order should be filled. Order: " + o1);
        if (o1.getTransferredMoney() != -Math.round((double)limitOrder.getStartPrice() * o1.getFilledVolume() / scaleFactor))
            return fail("Transferred money mismatch: " + o1.getTransferredMoney());

        long newItemBalance = bankAccount2.getBank(serverMarket.getItemID()).getTotalBalance();
        long newMoneyBalance = bankAccount2.getBank(moneyID).getTotalBalance();

        if (newMoneyBalance - o1.getTransferredMoney() != 1000)
            return fail("Wrong money amount transferred: " + newMoneyBalance);
        if (newItemBalance != 110 * scaleFactor)
            return fail("Wrong end balance of items: " + newItemBalance);
        if (serverMarket.getCurrentMarketPrice() != 12)
            return fail("Wrong market end price: " + serverMarket.getCurrentMarketPrice());
        if (limitOrder.getFilledVolume() + o1.getFilledVolume() != 0)
            return fail("Filled volumes should cancel out");
        if (!limitOrder.isFilled())
            return fail("Limit order should be filled");

        serverMarket.update();
        serverMarket.update();
        serverMarket.update();
        serverMarket.update();

        if (!o1.isFilled())
            return fail("Market order should still be filled after extra updates");
        if (!limitOrder.isFilled())
            return fail("Limit order should still be filled after extra updates");

        return pass("Market buy order fully matches against limit sell order");
    }

    private TestResult marketOrderTest_5() {
        resetMarketState(false, 10);

        Order limitOrder = new Order(serverMarket.getItemID(), Order.Type.LIMIT, -10 * scaleFactor, 12, 0, UUID.randomUUID(), bankAccountNr1);
        serverMarket.putOrder(limitOrder);
        serverMarket.update();

        Order o1 = new Order(serverMarket.getItemID(), Order.Type.MARKET, 15 * scaleFactor, serverMarket.getCurrentMarketPrice(), 0, UUID.randomUUID(), bankAccountNr2);
        serverMarket.putOrder(o1);
        serverMarket.update();

        if (o1.isFilled())
            return fail("Market order should NOT be filled (not enough supply). Order: " + o1);
        if (o1.getTransferredMoney() != -Math.round((double)limitOrder.getStartPrice() * o1.getFilledVolume() / scaleFactor))
            return fail("Transferred money mismatch: " + o1.getTransferredMoney());

        long newItemBalance = bankAccount2.getBank(serverMarket.getItemID()).getTotalBalance();
        long newMoneyBalance = bankAccount2.getBank(moneyID).getTotalBalance();

        if (newMoneyBalance - o1.getTransferredMoney() != 1000)
            return fail("Wrong money amount transferred: " + newMoneyBalance);
        if (newItemBalance != 110 * scaleFactor)
            return fail("Wrong end balance of items: " + newItemBalance);
        if (serverMarket.getCurrentMarketPrice() != 12)
            return fail("Wrong market end price: " + serverMarket.getCurrentMarketPrice());
        if (!limitOrder.isFilled())
            return fail("Limit order should be filled");

        serverMarket.update();
        serverMarket.update();
        serverMarket.update();
        serverMarket.update();

        if (o1.isFilled())
            return fail("Market order should still NOT be filled after extra updates");
        if (!limitOrder.isFilled())
            return fail("Limit order should still be filled after extra updates");

        return pass("Market buy order with larger volume than available limit supply is partially filled");
    }

    private TestResult limitOrderTest_1() {
        resetMarketState(true, 10);

        Order limitOrder = new Order(serverMarket.getItemID(), Order.Type.LIMIT, 10 * scaleFactor, 13, 0, UUID.randomUUID(), bankAccountNr1);
        serverMarket.putOrder(limitOrder);
        serverMarket.update();

        if (!limitOrder.isFilled())
            return fail("Limit order should be filled. Order: " + limitOrder);
        if (limitOrder.getTransferredMoney() != -(11 * 5 + 12 * 5))
            return fail("Expected " + -(11 * 5 + 12 * 5) + "$ but was: " + limitOrder.getTransferredMoney());

        long newItemBalance = bankAccount1.getBank(serverMarket.getItemID()).getTotalBalance();
        long newMoneyBalance = bankAccount1.getBank(moneyID).getTotalBalance();

        if (newMoneyBalance - limitOrder.getTransferredMoney() != 1000)
            return fail("Wrong money amount transferred: " + newMoneyBalance);
        if (newItemBalance != 110 * scaleFactor)
            return fail("Wrong end balance of items: " + newItemBalance);
        if (serverMarket.getCurrentMarketPrice() != 12)
            return fail("Wrong market end price: " + serverMarket.getCurrentMarketPrice());

        serverMarket.update();
        serverMarket.update();
        serverMarket.update();

        if (!limitOrder.isFilled())
            return fail("Limit order should still be filled after extra updates");

        return pass("Limit buy order filled by virtual orderbook");
    }

    private TestResult limitOrderTest_2() {
        resetMarketState(true, 10);

        Order limitOrder = new Order(serverMarket.getItemID(), Order.Type.LIMIT, 30 * scaleFactor, 13, 0, UUID.randomUUID(), bankAccountNr1);
        serverMarket.putOrder(limitOrder);
        serverMarket.update();

        if (limitOrder.isFilled())
            return fail("Limit order should NOT be filled. Order: " + limitOrder);
        if (limitOrder.getTransferredMoney() != -(11 * 5 + 12 * 5 + 13 * 5))
            return fail("Expected " + -(11 * 5 + 12 * 5 + 13 * 5) + "$ but was: " + limitOrder.getTransferredMoney());

        long newItemBalance = bankAccount1.getBank(serverMarket.getItemID()).getTotalBalance();
        long newMoneyBalance = bankAccount1.getBank(moneyID).getTotalBalance();

        if (newMoneyBalance - limitOrder.getTransferredMoney() != 1000)
            return fail("Wrong money amount transferred: " + newMoneyBalance);
        if (newItemBalance != 115 * scaleFactor)
            return fail("Wrong end balance of items: " + newItemBalance);
        if (serverMarket.getCurrentMarketPrice() != 13)
            return fail("Wrong market end price: " + serverMarket.getCurrentMarketPrice());

        serverMarket.update();
        serverMarket.update();
        serverMarket.update();

        if (limitOrder.isFilled())
            return fail("Limit order should still NOT be filled after extra updates");

        return pass("Limit buy order partially filled by virtual orderbook, remainder placed in orderbook");
    }

    private TestResult limitOrderTest_3() {
        resetMarketState(true, 10);

        Order limitOrder = new Order(serverMarket.getItemID(), Order.Type.LIMIT, -10 * scaleFactor, 7, 0, UUID.randomUUID(), bankAccountNr1);
        serverMarket.putOrder(limitOrder);
        serverMarket.update();

        if (!limitOrder.isFilled())
            return fail("Limit order should be filled. Order: " + limitOrder);
        if (limitOrder.getTransferredMoney() != (10 * 5 + 9 * 5))
            return fail("Expected " + (10 * 5 + 9 * 5) + "$ but was: " + limitOrder.getTransferredMoney());

        long newItemBalance = bankAccount1.getBank(serverMarket.getItemID()).getTotalBalance();
        long newMoneyBalance = bankAccount1.getBank(moneyID).getTotalBalance();

        if (newMoneyBalance - limitOrder.getTransferredMoney() != 1000)
            return fail("Wrong money amount transferred: " + newMoneyBalance);
        if (newItemBalance != 90 * scaleFactor)
            return fail("Wrong end balance of items: " + newItemBalance);
        if (serverMarket.getCurrentMarketPrice() != 9)
            return fail("Wrong market end price: " + serverMarket.getCurrentMarketPrice());

        serverMarket.update();
        serverMarket.update();
        serverMarket.update();

        if (!limitOrder.isFilled())
            return fail("Limit order should still be filled after extra updates");

        return pass("Limit sell order filled by virtual orderbook");
    }

    private TestResult limitOrderTest_4() {
        resetMarketState(true, 10);

        Order limitOrder = new Order(serverMarket.getItemID(), Order.Type.LIMIT, -30 * scaleFactor, 7, 0, UUID.randomUUID(), bankAccountNr1);
        serverMarket.putOrder(limitOrder);
        serverMarket.update();

        if (limitOrder.isFilled())
            return fail("Limit order should NOT be filled. Order: " + limitOrder);
        if (limitOrder.getTransferredMoney() != (10 * 5 + 9 * 5 + 8 * 5 + 7 * 5))
            return fail("Expected " + (10 * 5 + 9 * 5 + 8 * 5 + 7 * 5) + "$ but was: " + limitOrder.getTransferredMoney());

        long newItemBalance = bankAccount1.getBank(serverMarket.getItemID()).getTotalBalance();
        long newMoneyBalance = bankAccount1.getBank(moneyID).getTotalBalance();

        if (newMoneyBalance - limitOrder.getTransferredMoney() != 1000)
            return fail("Wrong money amount transferred: " + newMoneyBalance);
        if (newItemBalance != 80 * scaleFactor)
            return fail("Wrong end balance of items: " + newItemBalance);
        if (serverMarket.getCurrentMarketPrice() != 7)
            return fail("Wrong market end price: " + serverMarket.getCurrentMarketPrice());

        serverMarket.update();
        serverMarket.update();
        serverMarket.update();

        if (limitOrder.isFilled())
            return fail("Limit order should still NOT be filled after extra updates");

        return pass("Limit sell order partially filled by virtual orderbook, remainder placed in orderbook");
    }

    private TestResult mixedOrderTest_1() {
        resetMarketState(false, 10);

        ItemID item = serverMarket.getItemID();
        Order limitOrder = new Order(item, Order.Type.LIMIT, -10 * scaleFactor, 13, 0, UUID.randomUUID(), bankAccountNr1);
        serverMarket.putOrder(limitOrder);

        Order marketOrder = new Order(item, Order.Type.MARKET, 10 * scaleFactor, 10, 0, UUID.randomUUID(), bankAccountNr2);
        serverMarket.putOrder(marketOrder);
        serverMarket.update();

        if (!limitOrder.isFilled())
            return fail("Limit order should be filled. Order: " + limitOrder);
        if (!marketOrder.isFilled())
            return fail("Market order should be filled. Order: " + marketOrder);
        if (limitOrder.getTransferredMoney() != (10 * 13))
            return fail("Limit transferred money should be " + (10 * 13) + "$ but was: " + limitOrder.getTransferredMoney());
        if (marketOrder.getTransferredMoney() != -(10 * 13))
            return fail("Market transferred money should be " + -(10 * 13) + "$ but was: " + marketOrder.getTransferredMoney());

        long newItemBalance1 = bankAccount1.getBank(serverMarket.getItemID()).getTotalBalance();
        long newMoneyBalance1 = bankAccount1.getBank(moneyID).getTotalBalance();
        long newItemBalance2 = bankAccount2.getBank(serverMarket.getItemID()).getTotalBalance();
        long newMoneyBalance2 = bankAccount2.getBank(moneyID).getTotalBalance();

        if (newMoneyBalance1 - limitOrder.getTransferredMoney() != 1000)
            return fail("Wrong money transferred for limit order: " + newMoneyBalance1);
        if (newItemBalance1 != 90 * scaleFactor)
            return fail("Wrong item balance for limit order account: " + newItemBalance1);
        if (newMoneyBalance2 - marketOrder.getTransferredMoney() != 1000)
            return fail("Wrong money transferred for market order: " + newMoneyBalance2);
        if (newItemBalance2 != 110 * scaleFactor)
            return fail("Wrong item balance for market order account: " + newItemBalance2);
        if (serverMarket.getCurrentMarketPrice() != 13)
            return fail("Wrong market end price: " + serverMarket.getCurrentMarketPrice());

        serverMarket.update();
        serverMarket.update();
        serverMarket.update();

        if (!limitOrder.isFilled())
            return fail("Limit order should still be filled after extra updates");
        if (!marketOrder.isFilled())
            return fail("Market order should still be filled after extra updates");

        return pass("Mixed limit sell and market buy orders match correctly");
    }

    private float emptyVolumeDistribution(double price) {
        return 0;
    }

    private float uniformVolumeDistribution(double price) {
        return (float) uniformVolumeDistributionScale;
    }
}

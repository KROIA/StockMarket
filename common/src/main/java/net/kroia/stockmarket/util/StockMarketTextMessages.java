package net.kroia.stockmarket.util;

import net.kroia.banksystem.BankSystemModBackend;
import net.kroia.banksystem.util.BankSystemTextMessages;
import net.kroia.stockmarket.StockMarketModBackend;

public class StockMarketTextMessages {

    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    private static boolean initialized = false;
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
    }
    public static void setBackend(StockMarketModBackend.Instances backend) {
        StockMarketTextMessages.BACKEND_INSTANCES = backend;
    }

    private static class Variables
    {
        public static final String AMOUNT = "{amount}";
        public static final String BALANCE = "{balance}";
        public static final String LOCKED_BALANCE = "{locked_balance}";

        public static final String ITEM_NAME = "{item_name}";
        public static final String USER = "{user_name}";
        public static final String RECEIVER = "{receiver}";
        public static final String SENDER = "{sender}";
        public static final String PLAYER = "{player_name}";
        public static final String REASON = "{reason}";
        public static final String ACCOUNT = "{account_number}";
    }
}

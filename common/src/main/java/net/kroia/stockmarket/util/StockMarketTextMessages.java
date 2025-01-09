package net.kroia.stockmarket.util;

import net.kroia.banksystem.util.BankSystemTextMessages;
import net.kroia.stockmarket.StockMarketMod;
import net.minecraft.network.chat.Component;

public class StockMarketTextMessages {
    private static boolean initialized = false;
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
    }
    public static class Variables
    {
        public static final String ITEM_NAME = "{item_name}";
        public static final String CURRENT_PRICE = "{current_price}";
        public static final String PRICE = "{price}";
        public static final String COST = "{cost}";
        public static final String VALUE = "{value}";
        public static final String ORDER_TEXT = "{order_text}";
        public static final String AMOUNT = "{amount}";
        public static final String CURRENCY = "{currency}";
        public static final String DIRECTION = "{buy_sell_direction}";
        public static final String REASON = "{reason}";
    }
    private static final String prefix  = "message."+ StockMarketMod.MOD_ID+".";

    private static final Component INVALID_ITEM_ID = Component.translatable(prefix+"invalid_item_id");
    public static String getInvalidItemIDMessage(String itemName)
    {
        String msg = INVALID_ITEM_ID.getString();
        msg = replaceVariable(msg, Variables.ITEM_NAME, itemName);
        return msg;
    }

    private static final Component MARKETPLACE_NOT_EXISTING = Component.translatable(prefix+"marketplace_not_existing");
    public static String getMarketplaceNotExistingMessage(String itemName)
    {
        String msg = MARKETPLACE_NOT_EXISTING.getString();
        msg = replaceVariable(msg, Variables.ITEM_NAME, itemName);
        return msg;
    }

    private static final Component MARKETPLACE_ALREADY_EXISTING = Component.translatable(prefix+"marketplace_already_existing");
    public static String getMarketplaceAlreadyExistingMessage(String itemName)
    {
        String msg = MARKETPLACE_ALREADY_EXISTING.getString();
        msg = replaceVariable(msg, Variables.ITEM_NAME, itemName);
        return msg;
    }



    private static final Component MARKETPLACE_DELETED = Component.translatable(prefix+"marketplace_deleted");
    public static String getMarketplaceDeletedMessage(String itemName)
    {
        String msg = MARKETPLACE_DELETED.getString();
        msg = replaceVariable(msg, Variables.ITEM_NAME, itemName);
        return msg;
    }

    private static final Component MARKETPLACE_CREATED = Component.translatable(prefix+"marketplace_created");
    public static String getMarketplaceCreatedMessage(String itemName)
    {
        String msg = MARKETPLACE_CREATED.getString();
        msg = replaceVariable(msg, Variables.ITEM_NAME, itemName);
        return msg;
    }


    private static final Component CURRENT_PRICE_OF = Component.translatable(prefix+"current_price_of");
    public static String getCurrentPriceOfMessage(String itemName, int currentPrice)
    {
        String msg = CURRENT_PRICE_OF.getString();
        msg = replaceVariable(msg, Variables.ITEM_NAME, itemName);
        msg = replaceVariable(msg, Variables.CURRENT_PRICE, String.valueOf(currentPrice));
        return msg;
    }



    private static final Component BOT_CREATED = Component.translatable(prefix+"bot_created");
    public static String getBotCreatedMessage(String itemName)
    {
        String msg = BOT_CREATED.getString();
        msg = replaceVariable(msg, Variables.ITEM_NAME, itemName);
        return msg;
    }

    private static final Component BOT_DELETED = Component.translatable(prefix+"bot_deleted");
    public static String getBotDeletedMessage(String itemName)
    {
        String msg = BOT_DELETED.getString();
        msg = replaceVariable(msg, Variables.ITEM_NAME, itemName);
        return msg;
    }
    private static final Component BOT_NOT_EXIST = Component.translatable(prefix+"bot_not_exist");
    public static String getBotNotExistMessage(String itemName)
    {
        String msg = BOT_NOT_EXIST.getString();
        msg = replaceVariable(msg, Variables.ITEM_NAME, itemName);
        return msg;
    }
    private static final Component BOT_ALREADY_EXIST = Component.translatable(prefix+"bot_already_exist");
    public static String getBotAlreadyExistMessage(String itemName)
    {
        String msg = BOT_ALREADY_EXIST.getString();
        msg = replaceVariable(msg, Variables.ITEM_NAME, itemName);
        return msg;
    }
    private static final Component BOT_SETTING_PID_I_SET = Component.translatable(prefix+"bot_setting_pid_i_set");
    public static String getBotSettingPIDISetMessage(double value)
    {
        String msg = BOT_SETTING_PID_I_SET.getString();
        msg = replaceVariable(msg, Variables.VALUE, String.valueOf(value));
        return msg;
    }
    private static final Component BOT_SETTING_INTEGRATED = Component.translatable(prefix+"bot_setting_integrated");
    public static String getBotSettingIntegratedMessage(double value)
    {
        String msg = BOT_SETTING_INTEGRATED.getString();
        msg = replaceVariable(msg, Variables.VALUE, String.valueOf(value));
        return msg;
    }
    private static final Component BOT_SETTING_TARGET_ITEM_BALANCE = Component.translatable(prefix+"bot_setting_target_item_balance");
    public static String getBotSettingTargetItemBalanceMessage(long value)
    {
        String msg = BOT_SETTING_TARGET_ITEM_BALANCE.getString();
        msg = replaceVariable(msg, Variables.VALUE, String.valueOf(value));
        return msg;
    }
    private static final Component BOT_SETTING_PID_IBOUNDS_SET = Component.translatable(prefix+"bot_setting_pid_ibounds_set");
    public static String getBotSettingPIDIBoundsSetMessage(double value)
    {
        String msg = BOT_SETTING_PID_IBOUNDS_SET.getString();
        msg = replaceVariable(msg, Variables.VALUE, String.valueOf(value));
        return msg;
    }
    private static final Component BOT_SETTING_PID_D_SET = Component.translatable(prefix+"bot_setting_pid_d_set");
    public static String getBotSettingPIDDSetMessage(double value)
    {
        String msg = BOT_SETTING_PID_I_SET.getString();
        msg = replaceVariable(msg, Variables.VALUE, String.valueOf(value));
        return msg;
    }
    private static final Component BOT_SETTING_PID_P_SET = Component.translatable(prefix+"bot_setting_pid_p_set");
    public static String getBotSettingPIDPSetMessage(double value)
    {
        String msg = BOT_SETTING_PID_I_SET.getString();
        msg = replaceVariable(msg, Variables.VALUE, String.valueOf(value));
        return msg;
    }

    private static final Component BOT_SETTING_UPDATE_INTERVAL = Component.translatable(prefix+"bot_setting_update_interval");
    public static String getBotSettingUpdateIntervalMessage(long value)
    {
        String msg = BOT_SETTING_UPDATE_INTERVAL.getString();
        msg = replaceVariable(msg, Variables.VALUE, String.valueOf(value));
        return msg;
    }

    private static final Component BOT_SETTING_VOLATILITX_MAX_TIMER = Component.translatable(prefix+"bot_setting_volatility_max_timer");
    public static String getBotSettingVolatilityMaxTimerMessage(long value)
    {
        String msg = BOT_SETTING_VOLATILITX_MAX_TIMER.getString();
        msg = replaceVariable(msg, Variables.VALUE, String.valueOf(value));
        return msg;
    }

    private static final Component BOT_SETTING_VOLATILITX_MIN_TIMER = Component.translatable(prefix+"bot_setting_volatility_min_timer");
    public static String getBotSettingVolatilityMinTimerMessage(long value)
    {
        String msg = BOT_SETTING_VOLATILITX_MIN_TIMER.getString();
        msg = replaceVariable(msg, Variables.VALUE, String.valueOf(value));
        return msg;
    }

    private static final Component BOT_SETTING_VOLATILITX_TIMER = Component.translatable(prefix+"bot_setting_volatility_timer");
    public static String getBotSettingVolatilityTimerMessage(long value)
    {
        String msg = BOT_SETTING_VOLATILITX_TIMER.getString();
        msg = replaceVariable(msg, Variables.VALUE, String.valueOf(value));
        return msg;
    }

    private static final Component BOT_SETTING_VOLUME_SCALE = Component.translatable(prefix+"bot_setting_volume_scale");
    public static String getBotSettingVolumeScaleMessage(double value) {
        String msg = BOT_SETTING_VOLUME_SCALE.getString();
        msg = replaceVariable(msg, Variables.VALUE, String.valueOf(value));
        return msg;
    }

    private static final Component BOT_SETTING_VOLUME_SPREAD = Component.translatable(prefix+"bot_setting_volume_spread");
    public static String getBotSettingVolumeSpreadMessage(double value) {
        String msg = BOT_SETTING_VOLUME_SPREAD.getString();
        msg = replaceVariable(msg, Variables.VALUE, String.valueOf(value));
        return msg;
    }

    private static final Component BOT_SETTING_VOLUME_RANDOMNESS = Component.translatable(prefix+"bot_setting_volume_randomness");
    public static String getBotSettingVolumeRandomnessMessage(double value)
    {
        String msg = BOT_SETTING_VOLUME_RANDOMNESS.getString();
        msg = replaceVariable(msg, Variables.VALUE, String.valueOf(value));
        return msg;
    }

    private static final Component BOT_SETTING_VOLATILITY = Component.translatable(prefix+"bot_setting_volatility");
    public static String getBotSettingVolatilityMessage(double value)
    {
        String msg = BOT_SETTING_VOLATILITY.getString();
        msg = replaceVariable(msg, Variables.VALUE, String.valueOf(value));
        return msg;
    }

    private static final Component BOT_SETTING_ORDER_RANDOMNESS = Component.translatable(prefix+"bot_setting_order_randomness");
    public static String getBotSettingOrderRandomnessMessage(double value)
    {
        String msg = BOT_SETTING_ORDER_RANDOMNESS.getString();
        msg = replaceVariable(msg, Variables.VALUE, String.valueOf(value));
        return msg;
    }


    private static final Component BOT_SETTING_IMBALANCE_PRICE_RANGE = Component.translatable(prefix+"bot_setting_imbalance_price_range");
    public static String getBotSettingImbalancePriceRangeMessage(int value)
    {
        String msg = BOT_SETTING_IMBALANCE_PRICE_RANGE.getString();
        msg = replaceVariable(msg, Variables.VALUE, String.valueOf(value));
        return msg;
    }
    private static final Component BOT_SETTING_IMBALANCE_CHANGE_FACTOR = Component.translatable(prefix+"bot_setting_imbalance_change_factor");
    public static String getBotSettingImbalanceChangeFactorMessage(double value) {
        String msg = BOT_SETTING_IMBALANCE_CHANGE_FACTOR.getString();
        msg = replaceVariable(msg, Variables.VALUE, String.valueOf(value));
        return msg;
    }

    private static final Component BOT_SETTING_IMBALANCE_CHANGE_QUAD_FACTOR = Component.translatable(prefix+"bot_setting_imbalance_change_quad_factor");
    public static String getBotSettingImbalanceChangeQuadFactorMessage(double value) {
        String msg = BOT_SETTING_IMBALANCE_CHANGE_QUAD_FACTOR.getString();
        msg = replaceVariable(msg, Variables.VALUE, String.valueOf(value));
        return msg;
    }

    private static final Component BOT_SETTING_ENABLED = Component.translatable(prefix+"bot_setting_enabled");
    private static final Component BOT_SETTING_ENABLED_VALUE = Component.translatable(prefix+"bot_setting_enabled_value");
    private static final Component BOT_SETTING_DISABLED_VALUE = Component.translatable(prefix+"bot_setting_disabled_value");
    public static String getBotSettingEnabledMessage(boolean value) {
        String msg = BOT_SETTING_ENABLED.getString();
        String enableMsg = value ? BOT_SETTING_ENABLED_VALUE.getString() : BOT_SETTING_DISABLED_VALUE.getString();
        msg = replaceVariable(msg, Variables.VALUE, enableMsg);
        return msg;
    }

    private static final Component BOT_SETTING_MAX_ORDER_COUNT = Component.translatable(prefix+"bot_setting_max_order_count");
    public static String getBotSettingMaxOrderCountMessage(int value) {
        String msg = BOT_SETTING_MAX_ORDER_COUNT.getString();
        msg = replaceVariable(msg, Variables.VALUE, String.valueOf(value));
        return msg;
    }



    private static final Component INSUFFICIENT_FUND = Component.translatable(prefix+"insufficient_fund");
    public static String getInsufficientFundMessage()
    {
        return INSUFFICIENT_FUND.getString();
    }


    private static final Component INSUFFICIENT_FUND_TO_CONSUME= Component.translatable(prefix+"insufficient_fund_to_consume");
    public static String getInsufficientFundToConsumeMessage(String orderText, int price, long amount, long totalCost)
    {
        String msg = INSUFFICIENT_FUND_TO_CONSUME.getString();
        msg = replaceVariable(msg, Variables.ORDER_TEXT, orderText);
        msg = replaceVariable(msg, Variables.PRICE, String.valueOf(price));
        msg = replaceVariable(msg, Variables.CURRENCY, BankSystemTextMessages.getCurrencyName());
        msg = replaceVariable(msg, Variables.AMOUNT, String.valueOf(amount));
        msg = replaceVariable(msg, Variables.COST, String.valueOf(totalCost));
        msg = replaceVariable(msg, Variables.CURRENCY, BankSystemTextMessages.getCurrencyName());
        return msg;
    }

    private static final Component INSUFFICIENT_FUND_TO_BUY = Component.translatable(prefix+"insufficient_fund_to_buy");
    public static String getInsufficientFundToBuyMessage(String itemName, long amount, int price)
    {
        String msg = INSUFFICIENT_FUND_TO_BUY.getString();
        msg = replaceVariable(msg, Variables.ITEM_NAME, itemName);
        msg = replaceVariable(msg, Variables.AMOUNT, String.valueOf(amount));
        msg = replaceVariable(msg, Variables.PRICE, String.valueOf(price));
        msg = replaceVariable(msg, Variables.CURRENCY, BankSystemTextMessages.getCurrencyName());
        return msg;
    }


    private static final Component INSUFFICIENT_ITEMS_TO_SELL = Component.translatable(prefix+"insufficient_items_to_sell");
    public static String getInsufficientItemsToSellMessage(String itemName, long amount)
    {
        String msg = INSUFFICIENT_ITEMS_TO_SELL.getString();
        msg = replaceVariable(msg, Variables.ITEM_NAME, itemName);
        msg = replaceVariable(msg, Variables.AMOUNT, String.valueOf(amount));
        return msg;
    }

    private static final Component MISSING_ITEMS = Component.translatable(prefix+"missing_items");
    public static String getMissingItemsMessage(String itemName, long amount)
    {
        String msg = MISSING_ITEMS.getString();
        msg = replaceVariable(msg, Variables.ITEM_NAME, itemName);
        msg = replaceVariable(msg, Variables.AMOUNT, String.valueOf(amount));
        return msg;
    }
    private static final Component MISSING_MONEY = Component.translatable(prefix+"missing_money");
    public static String getMissingMoneyMessage(long amount)
    {
        String msg = MISSING_MONEY.getString();
        msg = replaceVariable(msg, Variables.AMOUNT, String.valueOf(amount));
        msg = replaceVariable(msg, Variables.CURRENCY, BankSystemTextMessages.getCurrencyName());

        return msg;
    }



    private static final Component ORDER_INVALID = Component.translatable(prefix+"order_invalid");
    public static String getOrderInvalidMessage(String reason)
    {
        String msg = ORDER_INVALID.getString();
        msg += reason;
        return msg;
    }
    private static final Component ORDER_INVALID_REASON_ORDERS_TO_FILL_TRANSACTION = Component.translatable(prefix+"order_invalid_reason_orders_to_fill_transaction");
    private static final Component BUY_DIRECTION = Component.translatable(prefix+"buy_direction");
    private static final Component SELL_DIRECTION = Component.translatable(prefix+"sell_direction");
    public static String getOrderInvalidReasonOrdersToFillTransactionMessage(boolean isBuy)
    {
        String msg = ORDER_INVALID_REASON_ORDERS_TO_FILL_TRANSACTION.getString();
        String dirStr = isBuy ? BUY_DIRECTION.getString() : SELL_DIRECTION.getString();
        msg = replaceVariable(msg, Variables.DIRECTION, dirStr);
        return msg;
    }

    private static final Component ORDER_AMOUNT = Component.translatable(prefix+"order_amount");
    public static String getOrderAmountMessage(long amount)
    {
        String msg = ORDER_AMOUNT.getString();
        msg = replaceVariable(msg, Variables.AMOUNT, String.valueOf(amount));
        return msg;
    }
    private static final Component ORDER_LIMIT_PRICE = Component.translatable(prefix+"order_limit_price");
    public static String getOrderLimitPriceMessage(int price)
    {
        String msg = ORDER_LIMIT_PRICE.getString();
        msg = replaceVariable(msg, Variables.PRICE, String.valueOf(price));
        msg = replaceVariable(msg, Variables.CURRENCY, BankSystemTextMessages.getCurrencyName());
        return msg;
    }
    private static final Component ORDER_AVERAGE_PRICE = Component.translatable(prefix+"order_average_price");
    public static String getOrderAveragePriceMessage(int price)
    {
        String msg = ORDER_AVERAGE_PRICE.getString();
        msg = replaceVariable(msg, Variables.PRICE, String.valueOf(price));
        msg = replaceVariable(msg, Variables.CURRENCY, BankSystemTextMessages.getCurrencyName());
        return msg;
    }

    private static final Component ORDER_HAS_BEEN_PLACED = Component.translatable(prefix+"order_has_been_placed");
    public static String getOrderHasBeenPlacedMessage(boolean isBuy)
    {
        String msg = ORDER_HAS_BEEN_PLACED.getString();
        String dirStr = isBuy ? BUY_DIRECTION.getString() : SELL_DIRECTION.getString();
        msg = replaceVariable(msg, Variables.DIRECTION, dirStr);
        return msg;
    }

    private static final Component ORDER_HAS_BEEN_FILLED = Component.translatable(prefix+"order_has_been_filled");
    public static String getOrderHasBeenFilledMessage(boolean isBuy)
    {
        String msg = ORDER_HAS_BEEN_FILLED.getString();
        String dirStr = isBuy ? BUY_DIRECTION.getString() : SELL_DIRECTION.getString();
        msg = replaceVariable(msg, Variables.DIRECTION, dirStr);
        return msg;
    }

    private static final Component ORDER_HAS_BEEN_CANCELLED = Component.translatable(prefix+"order_has_been_cancelled");
    public static String getOrderHasBeenCancelledMessage(boolean isBuy)
    {
        String msg = ORDER_HAS_BEEN_CANCELLED.getString();
        String dirStr = isBuy ? BUY_DIRECTION.getString() : SELL_DIRECTION.getString();
        msg = replaceVariable(msg, Variables.DIRECTION, dirStr);
        return msg;
    }
    private static final Component ORDER_FILLED_AMOUNT = Component.translatable(prefix+"order_filled_amount");
    public static String getOrderFilledAmountMessage(long amount, String itemName)
    {
        String msg = ORDER_FILLED_AMOUNT.getString();
        msg = replaceVariable(msg, Variables.AMOUNT, String.valueOf(amount));
        msg = replaceVariable(msg, Variables.ITEM_NAME, itemName);
        return msg;
    }

    private static final Component ORDER_INVALID_REASON = Component.translatable(prefix+"order_invalid_reason");
    public static String getOrderInvalidReasonMessage(String reason)
    {
        String msg = ORDER_INVALID_REASON.getString();
        msg = replaceVariable(msg, Variables.REASON, reason);
        return msg;
    }


    private static final Component NO_TRADING_ITEM_AVAILABLE = Component.translatable(prefix+"no_trading_item_available");
    public static String getNoTradingItemAvailableMessage()
    {
        return NO_TRADING_ITEM_AVAILABLE.getString();
    }

    private static final Component ORDER_REPLACED = Component.translatable(prefix+"order_replaced");
    public static String getOrderReplacedMessage()
    {
        String msg = ORDER_REPLACED.getString();
        return msg;
    }

    private static final Component ORDER_NOT_REPLACED = Component.translatable(prefix+"order_not_replaced");
    public static String getOrderNotReplacedMessage()
    {
        String msg = ORDER_NOT_REPLACED.getString();
        return msg;
    }



    //--------------------------------------------------------------------------------------------------------
    // Helper methods
    //--------------------------------------------------------------------------------------------------------

    private static String replaceVariable(String message, String variable, String replacement)
    {
        if(!message.contains(variable))
        {
            StockMarketMod.LOGGER.error("Message: \""+message+"\" does not contain variable: \""+variable+"\" which should be replaced with: \""+replacement+"\"");
            return message;
            //throw new IllegalArgumentException("Message: \""+message+"\" does not contain variable: \""+variable+"\" which should be replaced with: \""+replacement+"\"");
        }
        // Replace first occurrence of variable
        int indexOccurrence = message.indexOf(variable);
        return message.substring(0, indexOccurrence) + replacement + message.substring(indexOccurrence + variable.length());
    }
}

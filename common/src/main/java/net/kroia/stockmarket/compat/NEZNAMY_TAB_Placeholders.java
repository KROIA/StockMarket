package net.kroia.stockmarket.compat;

import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.placeholder.PlaceholderManager;
import net.kroia.modutilities.setting.Setting;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.StockMarketModSettings;

import java.util.function.Function;
import java.util.function.Supplier;

public class NEZNAMY_TAB_Placeholders {
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        NEZNAMY_TAB_Placeholders.BACKEND_INSTANCES = backend;
    }

    public static void register() {
        // Placeholder for NEZNAMY_TAB integration
        // This method can be used to register placeholders or other integration features

        TabAPI tab = TabAPI.getInstance();
        if(tab==null)
            return;

        BACKEND_INSTANCES.LOGGER.info("Registering NEZNAMY/TAB placeholders for BankSystemMod");

        //tab.getEventBus().register(PlaceholderRegisterEvent.class, NEZNAMY_TAB_Placeholders::onPlaceholderRegister);


        PlaceholderManager pm = tab.getPlaceholderManager();
        registerPlayerPlaceholder();
        registerServerPlaceholder();

        BACKEND_INSTANCES.LOGGER.info("Registering NEZNAMY/TAB placeholders for BankSystemMod - done");
    }


    private static void registerPlayerPlaceholder()
    {
        /*IServerBankManager sbm = BACKEND_INSTANCES.SERVER_BANK_MANAGER;
        StockMarketModSettings.Placeholder settings = BACKEND_INSTANCES.SERVER_SETTINGS.PLACEHOLDER;


        registerPlayerPlaceholder(settings.PLAYER_BALANCE, (playerTab) -> {
            UUID playerUUID = playerTab.getUniqueId();
            IBank bank = sbm.getMoneyBank(playerUUID);
            if(bank != null)
                return bank.getNormalizedBalance();
            return "0";
        });

        registerPlayerPlaceholder(settings.PLAYER_LOCKED_BALANCE, (playerTab) -> {
            UUID playerUUID = playerTab.getUniqueId();
            IBank bank = sbm.getMoneyBank(playerUUID);
            if(bank != null)
                return bank.getNormalizedLockedBalance();
            return "0";
        });

        registerPlayerPlaceholder(settings.PLAYER_TOTAL_BALANCE, (playerTab) -> {
            UUID playerUUID = playerTab.getUniqueId();
            IBank bank = sbm.getMoneyBank(playerUUID);
            if(bank != null)
                return bank.getNormalizedTotalBalance();
            return "0";
        });

        registerPlayerPlaceholder(settings.PLAYER_BANKUSER_JSON, (playerTab) -> {
            UUID playerUUID = playerTab.getUniqueId();
            IBankUser bankUser = sbm.getUser(playerUUID);
            if(bankUser != null) {
                return bankUser.toJsonString();
            }
            return "0";
        });*/
    }

    private static void registerServerPlaceholder()
    {
        /*
        IServerBankManager sbm = BACKEND_INSTANCES.SERVER_BANK_MANAGER;
        StockMarketModSettings.Placeholder settings = BACKEND_INSTANCES.SERVER_SETTINGS.PLACEHOLDER;

        registerServerPlaceholder(settings.SERVER_CIRCULATION_JSON, sbm::getCirculationDataJsonString);
        */
    }





    /*
     *                    Helper functions for registering placeholders
     *
     */

    private static void registerPlayerPlaceholder(Setting<StockMarketModSettings.Placeholder.PlaceholderSettingData> setting, Function<TabPlayer, String> function)
    {
        PlaceholderManager pm = TabAPI.getInstance().getPlaceholderManager();

        String identifier = setting.get().getIdentifier();
        if(identifier.startsWith("%") && identifier.endsWith("%"))
        {
            pm.registerPlayerPlaceholder(setting.get().getIdentifier(), setting.get().getRefreshRate(), function);
        }
        else
        {
            BACKEND_INSTANCES.LOGGER.info("Skipping registration of placeholder \"" + identifier + "\" from the setting: \""+setting.getName()+"\" because it does not start and end with '%' character.");
        }
    }

    private static void registerServerPlaceholder(Setting<StockMarketModSettings.Placeholder.PlaceholderSettingData> setting, Supplier<String> function)
    {
        PlaceholderManager pm = TabAPI.getInstance().getPlaceholderManager();

        String identifier = setting.get().getIdentifier();
        if(identifier.startsWith("%") && identifier.endsWith("%"))
        {
            pm.registerServerPlaceholder(setting.get().getIdentifier(), setting.get().getRefreshRate(), function);
        }
        else
        {
            BACKEND_INSTANCES.LOGGER.info("Skipping registration of placeholder \"" + identifier + "\" from the setting: \""+setting.getName()+"\" because it does not start and end with '%' character.");
        }
    }

    /*private static void onPlaceholderRegister(PlaceholderRegisterEvent event) {
        String placeholder = event.getIdentifier(); // e.g. "%get_balance[diamond]%"

        // Check if it's our placeholder with argument
        if (placeholder.startsWith("%get_balance[") && placeholder.endsWith("]%")) {
            String itemName = placeholder.substring("%get_balance[".length(), placeholder.length() - 2);

            BACKEND_INSTANCES.LOGGER.info("Requesting balance for item: " + itemName);
            // Register a new placeholder specifically for this item

            event.setPlayerPlaceholder((TabPlayer playerTab) -> {
                UUID playerUUID = playerTab.getUniqueId();
                return "test " + itemName + " balance"; // Replace with actual logic to get the balance for the item
            });

        }
    }*/

}

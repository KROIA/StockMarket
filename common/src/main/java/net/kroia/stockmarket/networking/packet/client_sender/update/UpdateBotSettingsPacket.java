package net.kroia.stockmarket.networking.packet.client_sender.update;

import net.kroia.banksystem.banking.BankUser;
import net.kroia.banksystem.banking.ServerBankManager;
import net.kroia.banksystem.banking.bank.Bank;
import net.kroia.banksystem.banking.bank.MoneyBank;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.NetworkPacket;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.market.server.bot.ServerTradingBot;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class UpdateBotSettingsPacket extends NetworkPacket {

    ItemID itemID;
    ServerVolatilityBot.Settings settings;

    private boolean destroyBot;
    private boolean createBot;
    private boolean marketOpen;
    private boolean setBotItemBalance;
    private boolean setBotMoneyBalance;
    private long itemBalance;
    private long moneyBalance;


    private UpdateBotSettingsPacket() {
        super();
    }
    public UpdateBotSettingsPacket(FriendlyByteBuf buf)
    {
        super(buf);
    }

    public static void sendPacket(ItemID itemID, ServerVolatilityBot.Settings settings, boolean destroyBot, boolean createBot, boolean marketOpen)
    {
        UpdateBotSettingsPacket packet = new UpdateBotSettingsPacket();
        packet.itemID = itemID;
        packet.settings = settings;
        packet.destroyBot = destroyBot;
        packet.createBot = createBot;
        packet.marketOpen = marketOpen;
        packet.setBotItemBalance = false;
        packet.setBotMoneyBalance = false;
        StockMarketNetworking.sendToServer(packet);
    }
    public static void sendPacket(ItemID itemID, ServerVolatilityBot.Settings settings, boolean destroyBot, boolean createBot, boolean marketOpen,
                                  boolean setItemBalance, long itemBalance, boolean setMoneyBalance, long moneyBalance)
    {
        UpdateBotSettingsPacket packet = new UpdateBotSettingsPacket();
        packet.itemID = itemID;
        packet.settings = settings;
        packet.destroyBot = destroyBot;
        packet.createBot = createBot;
        packet.marketOpen = marketOpen;
        packet.setBotItemBalance = setItemBalance;
        packet.setBotMoneyBalance = setMoneyBalance;
        packet.itemBalance = itemBalance;
        packet.moneyBalance = moneyBalance;
        StockMarketNetworking.sendToServer(packet);
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeItem(itemID.getStack());
        buf.writeBoolean(destroyBot);
        buf.writeBoolean(createBot);
        buf.writeBoolean(marketOpen);
        buf.writeBoolean(setBotItemBalance);
        buf.writeBoolean(setBotMoneyBalance);
        buf.writeLong(itemBalance);
        buf.writeLong(moneyBalance);
        CompoundTag tag = new CompoundTag();
        settings.save(tag);
        buf.writeNbt(tag);
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        itemID = new ItemID(buf.readItem());
        destroyBot = buf.readBoolean();
        createBot = buf.readBoolean();
        marketOpen = buf.readBoolean();
        setBotItemBalance = buf.readBoolean();
        setBotMoneyBalance = buf.readBoolean();
        itemBalance = buf.readLong();
        moneyBalance = buf.readLong();
        settings = new ServerVolatilityBot.Settings();
        settings.load(buf.readNbt());

    }

    @Override
    protected void handleOnServer(ServerPlayer sender) {
        if(sender.hasPermissions(2)) {
            // Apply bot settings
            ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
            if(bot == null && createBot)
            {
                bot = new ServerVolatilityBot();
                bot.setSettings(settings);
                ServerMarket.setTradingBot(itemID, bot);
            }
            else if(bot != null && destroyBot)
            {
                ServerMarket.removeTradingBot(itemID);
            }
            else {
                if (bot instanceof ServerVolatilityBot volatilityBot) {
                    volatilityBot.setSettings(settings);
                }
            }
            ServerMarket.setMarketOpen(itemID, marketOpen);
            BankUser botBankUser = ServerBankManager.getUser(ServerMarket.getBotUserUUID());
            if(setBotItemBalance)
            {
                Bank botBank = botBankUser.getBank(itemID);
                if(botBank != null)
                {
                    botBank.setBalance(itemBalance);
                }
                else {
                    botBank = botBankUser.createItemBank(itemID, itemBalance);
                }
            }
            if(setBotMoneyBalance)
            {
                Bank botBank = botBankUser.getBank(MoneyBank.ITEM_ID);
                if(botBank != null)
                {
                    botBank.deposit(moneyBalance);
                }
                else {
                    botBank = botBankUser.createItemBank(MoneyBank.ITEM_ID, moneyBalance);
                }
            }
        }
    }
}

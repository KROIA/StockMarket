package net.kroia.stockmarket.market;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kroia.banksystem.item.BankSystemItems;
import net.kroia.banksystem.item.custom.money.MoneyItem;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ClientPlayerUtilities;
import net.kroia.modutilities.JsonUtilities;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.modutilities.networking.INetworkPayloadConverter;
import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.StockMarketModBackend;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Items;

import java.util.Set;
import java.util.UUID;

public class TradingPair implements ServerSaveable, INetworkPayloadConverter {

    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backendInstances)
    {
        BACKEND_INSTANCES = backendInstances;
    }

    private ItemID item;
    private ItemID currency;
    private UUID pairUUID;
    private boolean isValid;

    public TradingPair()
    {
        this.item = new ItemID("minecraft:air");
        this.currency = new ItemID("minecraft:air");
        this.pairUUID = UUID.randomUUID(); // Generate a random UUID if invalid
        isValid = false;
    }
    public TradingPair(ItemID item, ItemID currency) {
        this.item = item;
        this.currency = currency;

        UUID itemUUID = item.getUUID();
        UUID currencyUUID = currency.getUUID();
        if(itemUUID == null || currencyUUID == null || itemUUID.toString().compareTo(currencyUUID.toString()) == 0) {
            this.pairUUID = UUID.nameUUIDFromBytes(("").getBytes());
            isValid = false;
        }
        else {
            this.pairUUID = UUID.nameUUIDFromBytes((itemUUID.toString() + currencyUUID.toString()).getBytes());
            isValid = true;
        }
    }
    public TradingPair(ItemID item, ItemID currency, boolean forceInvalid)
    {
        this(item, currency);
        if(forceInvalid) {
            isValid = false;
        }
    }

    public TradingPair(TradingPair other)
    {
        this.item = new ItemID(other.item.getStack());
        this.currency = new ItemID(other.currency.getStack());
        this.pairUUID = other.pairUUID;
        this.isValid = other.isValid;
    }
    public TradingPair(CompoundTag tag) {
        this();
        load(tag);
    }
    public TradingPair(FriendlyByteBuf buf) {
        this();
        decode(buf);
    }
    public TradingPair(JsonElement json) {
        this();
        fromJson(json);
    }

    public static TradingPair createDefault()
    {
        return new TradingPair(new ItemID(Items.DIAMOND.getDefaultInstance()), new ItemID(BankSystemItems.MONEY.get().getDefaultInstance()));
    }


    public ItemID getItem() {
        return item;
    }
    public ItemID getCurrency() {
        return currency;
    }

    public UUID getUUID()
    {
        return pairUUID;
    }

    public boolean isValid() {
        return isValid;
    }
    public void setInvalid() {
        this.isValid = false;
    }
    public void checkValidity(Set<ItemID> blacklistedItems)
    {
        if(item == null || currency == null || item.isAir() || currency.isAir()) {
            isValid = false;
            return;
        }

        if(blacklistedItems != null && (blacklistedItems.contains(item)) || blacklistedItems.contains(currency)) {
            isValid = false;
            return;
        }

        UUID itemUUID = item.getUUID();
        UUID currencyUUID = currency.getUUID();
        if(itemUUID == null || currencyUUID == null || itemUUID.toString().compareTo(currencyUUID.toString()) == 0) {
            isValid = false;
            return;
        }

        pairUUID = UUID.nameUUIDFromBytes((itemUUID.toString() + currencyUUID.toString()).getBytes());
        isValid = true;
    }
    public boolean isMoneyCurrency()
    {
        if(currency == null)
            return false;
        return MoneyItem.isMoney(currency);
    }


    @Override
    public boolean save(CompoundTag tag) {
        CompoundTag itemTag = new CompoundTag();
        item.save(itemTag);
        tag.put("item", itemTag);

        CompoundTag currencyTag = new CompoundTag();
        currency.save(currencyTag);
        tag.put("currency", currencyTag);
        tag.putUUID("pairUUID", pairUUID);
        tag.putBoolean("isValid", isValid);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(     !tag.contains("item") ||
                !tag.contains("currency") ||
                !tag.contains("pairUUID") ||
                !tag.contains("isValid")) {
            return false;
        }

        item.load(tag.getCompound("item"));
        currency.load(tag.getCompound("currency"));
        pairUUID = tag.getUUID("pairUUID");
        isValid = tag.getBoolean("isValid");
        return true;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        item.encode(buf);
        currency.encode(buf);
        buf.writeUUID(pairUUID);
        buf.writeBoolean(isValid);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        item.decode(buf);
        currency.decode(buf);
        pairUUID = buf.readUUID();
        isValid = buf.readBoolean();
    }

    public JsonElement toJson()
    {
        JsonObject jsonObject = new JsonObject();
        jsonObject.add("item", item.toJson());
        jsonObject.add("currency", currency.toJson());
        jsonObject.addProperty("pairUUID", pairUUID.toString());
        jsonObject.addProperty("isValid", isValid);
        return jsonObject;
    }
    public boolean fromJson(JsonElement json) {
        if(!json.isJsonObject())
        {
            this.isValid = false;
            return false;
        }

        JsonObject jsonObject = json.getAsJsonObject();
        JsonElement itemElement = jsonObject.get("item");
        JsonElement currencyElement = jsonObject.get("currency");
        String pairUUIDString = jsonObject.get("pairUUID").getAsString();
        this.isValid = jsonObject.get("isValid").getAsBoolean();

        if (    itemElement == null ||
                //currencyElement == null ||
                pairUUIDString == null) {
            this.isValid = false;
            return false;
        }

        boolean success = item.fromJson(itemElement);

        if(currencyElement == null)
        {
            if(BACKEND_INSTANCES.SERVER_MARKET_MANAGER != null)
                currency = BACKEND_INSTANCES.SERVER_MARKET_MANAGER.getDefaultCurrencyItemID();
            else
                currency = new ItemID(BankSystemItems.MONEY.get().getDefaultInstance());
        }
        else {
            success &= currency.fromJson(currencyElement);
        }
        pairUUID = UUID.fromString(pairUUIDString);
        isValid &= success;
        return success;
    }

    public String toJsonString() {
        return JsonUtilities.toPrettyString(toJson());
    }
    @Override
    public String toString() {
        return toJsonString();
    }

    public String getShortDescription() {

        if(UtilitiesPlatform.isClient()) {
            String itemText = ClientPlayerUtilities.getItemDisplayText(item.getStack());
            String currencyText = ClientPlayerUtilities.getItemDisplayText(currency.getStack());
            return itemText + "\n<->\n" + currencyText;
        }
        else {
            return item.getName() + "\n<->\n" + currency.getName();
        }

        //return item.getName() + " <-> " + currency.getName();
    }

    @Override
    public int hashCode() {
        return pairUUID.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TradingPair other)) return false;
        return pairUUID.equals(other.pairUUID);
    }
}

package net.kroia.stockmarket.market.clientdata;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.INetworkPayloadEncoder;
import net.kroia.stockmarket.market.TradingPair;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class TradingPairData implements INetworkPayloadEncoder {
    private final ItemID item;
    private final ItemID currency;
    private final UUID uuid;
    private final boolean isValid;


    public TradingPairData(@NotNull TradingPair pair) {
        this.item = pair.getItem();
        this.currency = pair.getCurrency();
        this.uuid = pair.getUUID();
        this.isValid = pair.isValid();
    }
    public TradingPairData(@NotNull ItemID item, @NotNull ItemID currency, @NotNull UUID uuid, boolean isValid) {
        this.item = item;
        this.currency = currency;
        this.uuid = uuid;
        this.isValid = isValid;
    }

    public ItemID getItem() {
        return item;
    }
    public ItemID getCurrency() {
        return currency;
    }
    public UUID getUUID() {
        return uuid;
    }
    public boolean isValid() {
        return isValid;
    }

    public TradingPair toTradingPair() {
        TradingPair pair = new TradingPair(item, currency);
        if(pair.getUUID().compareTo(uuid) != 0) {
            throw new IllegalStateException("UUID mismatch: " + pair.getUUID() + " != " + uuid+". Should be the same UUID as the TradingPairData was created from.");
        }
        return pair;
    }


    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeItem(item.getStack());
        buf.writeItem(currency.getStack());
        buf.writeUUID(uuid);
        buf.writeBoolean(isValid);
    }


    public static TradingPairData decode(FriendlyByteBuf buf) {
        return new TradingPairData(
                new ItemID(buf.readItem()),
                new ItemID(buf.readItem()),
                buf.readUUID(),
                buf.readBoolean());
    }
}

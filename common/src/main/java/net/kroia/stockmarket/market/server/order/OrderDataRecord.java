package net.kroia.stockmarket.market.server.order;

import net.kroia.modutilities.networking.INetworkPayloadConverter;
import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.TradingPair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class OrderDataRecord implements INetworkPayloadConverter{
    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    /**
     * The absolute server first startup time in milliseconds.
     * This is used to calculate the relative timestamp
     * It is set when the ServerMarketManager gets loaded
     */
    protected static long ABSOLUTE_SERVER_FIRST_STARTUP_TIME_SECONDS = 0;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
        if(BACKEND_INSTANCES.SERVER_MARKET_MANAGER != null)
        {
            ABSOLUTE_SERVER_FIRST_STARTUP_TIME_SECONDS = BACKEND_INSTANCES.SERVER_MARKET_MANAGER.getAbsoluteServerFirstStartupTimeMillis();
        }
        else if(BACKEND_INSTANCES.CLIENT_MARKET_MANAGER != null)
        {
            ABSOLUTE_SERVER_FIRST_STARTUP_TIME_SECONDS = BACKEND_INSTANCES.CLIENT_MARKET_MANAGER.getAbsoluteServerFirstStartupTimeMillis();
        }
    }
    public static void setAbsoluteServerFirstStartupTimeSeconds(long timeMillis) {
        ABSOLUTE_SERVER_FIRST_STARTUP_TIME_SECONDS = timeMillis;
    }


    private final static UUID TEST_DUMMY_UUID = UUID.randomUUID(); // For testing purposes, replace with a valid UUID if needed



    public static final class KEY
    {
        public static final String AMOUNT = "a";
        public static final String PLAYER = "p";
        public static final String ENUM_DATA = "e";
        public static final String TRADING_PAIR = "t";
        public static final String TIMESTAMP = "i";
    }
    public static final byte USE_AMOUNT = 1;
    public static final byte USE_PLAYER = 2;
    public static final byte USE_TYPE = 4;
    public static final byte USE_STATUS = 8;
    public static final byte USE_TIMESTAMP = 16;
    //public static final byte USE_TRADING_PAIR = 32;


    private long amount;
    private UUID player;
    private Order.Type type;
    private Order.Status status;
    private long timestampSeconds;


    // The trading pair is not saved inside the save/load methods!
    public TradingPair tradingPair;

    public OrderDataRecord(long amount,
                           @NotNull UUID player,
                           Order.Type type,
                           Order.Status status,
                           TradingPair tradingPair,
                           long timestamp) {
        this.amount = amount;
        this.player = player;
        this.type = type;
        this.status = status;
        this.tradingPair = tradingPair;
        this.timestampSeconds = timestamp;
    }

    private OrderDataRecord(){}
    public static OrderDataRecord loadFromTag(CompoundTag tag, TradingPair pair, byte useFlags)
    {
        OrderDataRecord record = new OrderDataRecord();
        record.tradingPair = pair;
        if(!record.load(tag, useFlags)){
            return null; // Failed to load
        }
        return record;
    }

    public void setTradingPair(TradingPair tradingPair) {
        this.tradingPair = tradingPair;
    }
    public TradingPair getTradingPair() {
        return tradingPair;
    }
    public void setStatus(Order.Status status) {
        this.status = status;
    }
    public Order.Status getStatus() {
        return status;
    }
    public void setAmount(long amount) {
        this.amount = amount;
    }
    public long getAmount() {
        return amount;
    }
    public void setPlayer(@NotNull UUID player) {
        this.player = player;
    }
    public UUID getPlayer() {
        return player;
    }
    public void setType(Order.Type type) {
        this.type = type;
    }
    public Order.Type getType() {
        return type;
    }
    public void setTimestampSeconds(long timestampSeconds) {
        this.timestampSeconds = timestampSeconds;
    }
    public long getTimestampSeconds() {
        return timestampSeconds;
    }


    @Override
    public void decode(FriendlyByteBuf friendlyByteBuf) {
        amount = friendlyByteBuf.readVarLong();
        player = friendlyByteBuf.readUUID();
        byte enumData = friendlyByteBuf.readByte();
        type = Order.Type.values()[enumData >> 4 & 0x0F];
        status = Order.Status.values()[enumData & 0x0F];
        tradingPair = new TradingPair();
        tradingPair.decode(friendlyByteBuf);

    }

    @Override
    public void encode(FriendlyByteBuf friendlyByteBuf) {
        friendlyByteBuf.writeVarLong(amount);
        friendlyByteBuf.writeUUID(player);
        friendlyByteBuf.writeByte((byte)((type.ordinal() << 4) | status.ordinal()));
        tradingPair.encode(friendlyByteBuf);
    }

    public boolean save(CompoundTag compoundTag, byte useFlags) {
        if ((useFlags & USE_AMOUNT) != 0) {
            compoundTag.putLong(KEY.AMOUNT, amount);
        }
        if ((useFlags & USE_PLAYER) != 0) {
            compoundTag.putUUID(KEY.PLAYER, player);
        }
        byte enumData = 0;
        boolean useEnumData = false;
        if ((useFlags & USE_TYPE) != 0) {
            enumData |= (byte)(type.ordinal() << 4);
            useEnumData = true;
        }
        if ((useFlags & USE_STATUS) != 0) {
            enumData |= (byte)(status.ordinal());
            useEnumData = true;
        }
        if(useEnumData) {
            compoundTag.putByte(KEY.ENUM_DATA, enumData);
        }

        if ((useFlags & USE_TIMESTAMP) != 0) {
            // Storing the timestamp as a relative time in seconds to save some space
            // Max
            compoundTag.putInt(KEY.TIMESTAMP, (int)((timestampSeconds - ABSOLUTE_SERVER_FIRST_STARTUP_TIME_SECONDS))+Integer.MIN_VALUE);
        }
        return true;
    }

    public boolean load(CompoundTag compoundTag, byte useFlags) {
        if ((useFlags & USE_AMOUNT) != 0) {
            if(!compoundTag.contains(KEY.AMOUNT)) {
                return false; // Amount is required
            }
            amount = compoundTag.getLong(KEY.AMOUNT);
        }

        if ((useFlags & USE_PLAYER) != 0) {
            if(!compoundTag.contains(KEY.PLAYER)) {
                return false; // Player is required
            }
            player = compoundTag.getUUID(KEY.PLAYER);
        }

        if ((useFlags & (USE_TYPE | USE_STATUS)) != 0) {
            if(!compoundTag.contains(KEY.ENUM_DATA)) {
                return false; // Enum data is required
            }
            byte enumData = compoundTag.getByte(KEY.ENUM_DATA);
            if ((useFlags & USE_TYPE) != 0) {
                type = Order.Type.values()[enumData >> 4 & 0x0F];
            }
            if ((useFlags & USE_STATUS) != 0) {
                status = Order.Status.values()[enumData & 0x0F];
            }
        } else {
            // If neither type nor status is used, we cannot load them
            type = Order.Type.LIMIT; // Default value
            status = Order.Status.PROCESSED; // Default value
        }

        if ((useFlags & USE_TIMESTAMP) != 0) {
            if(!compoundTag.contains(KEY.TIMESTAMP)) {
                return false; // Timestamp is required
            }
            // Read the timestamp as a relative time in seconds
            timestampSeconds = ABSOLUTE_SERVER_FIRST_STARTUP_TIME_SECONDS + (long)(compoundTag.getInt(KEY.TIMESTAMP)-Integer.MIN_VALUE);
        } else {
            timestampSeconds = 0;
        }
        return true;
    }

    public static @Nullable OrderDataRecord fromOrder(Order order, TradingPair pair){
        UUID playerUUID = order.getPlayerUUID();
        if (playerUUID == null) {
            playerUUID = TEST_DUMMY_UUID; // Use a dummy UUID for testing purposes
            // If you removed the TEST_DUMMY_UUID, you must return null here
            // Do not create an instance of the record if the player is null since that means the order does not belong to any player
            // and is a bot order!
        }
        return new OrderDataRecord(order.amount,
                playerUUID,
                order instanceof MarketOrder ? Order.Type.MARKET : Order.Type.LIMIT ,
                order.status,
                pair,
                order.getOrderID()/1000 // Use the id as timestamp in seconds
        );
    }

    public static OrderDataRecord fromBuf(FriendlyByteBuf buf){
        OrderDataRecord rec = new OrderDataRecord();
        rec.decode(buf);
        return rec;
    }
}

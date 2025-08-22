package net.kroia.stockmarket.entity.custom;

import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.block.custom.StockMarketBlock;
import net.kroia.stockmarket.entity.StockMarketEntities;
import net.kroia.stockmarket.market.TradingPair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.UUID;

public class StockMarketBlockEntity extends BlockEntity{
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;

    // Current Item that the chart is displaying
    public static final class UserData implements ServerSaveable
    {
        public TradingPair tradingPair;
        public int selectedBankAccountNumber;
        public float amount;
        public float price;

        @Override
        public boolean save(CompoundTag tag) {
            CompoundTag userTag = new CompoundTag();
            if (tradingPair != null) {
                CompoundTag tradingPairTag = new CompoundTag();
                tradingPair.save(tradingPairTag);
                userTag.put("tradingPair", tradingPairTag);
            }
            userTag.putInt("selectedBankAccountNumber", selectedBankAccountNumber);
            userTag.putFloat("amount", amount);
            userTag.putFloat("price", price);
            tag.put("userData", userTag);
            return true;
        }

        @Override
        public boolean load(CompoundTag tag) {
            CompoundTag userTag = tag.getCompound("userData");
            if (userTag.contains("tradingPair")) {
                CompoundTag tradingPairTag = userTag.getCompound("tradingPair");
                tradingPair = new TradingPair();
                tradingPair.load(tradingPairTag);
            } else {
                tradingPair = TradingPair.createDefault();
            }
            selectedBankAccountNumber = userTag.getInt("selectedBankAccountNumber");
            amount = userTag.getFloat("amount");
            price = userTag.getFloat("price");
            return true;
        }
        public static UserData loadFromTag(CompoundTag tag) {
            UserData userData = new UserData();
            if (userData.load(tag)) {
                return userData;
            }
            return null;
        }
    }

    private final Map<UUID, UserData> userDataMap = new java.util.HashMap<>();


    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }

    public StockMarketBlockEntity(BlockPos pos, BlockState state) {
        super(StockMarketEntities.STOCK_MARKET_BLOCK_ENTITY.get(), pos, state);
        //tradingPair = TradingPair.createDefault();
        //amount = 1;
        //price = 1;
    }

    public void set(Map<UUID, UserData> userDataMap) {
        this.userDataMap.clear();
        this.userDataMap.putAll(userDataMap);
    }
    public void setTradingPair(UUID playerUUID, TradingPair tradingPair) {
        UserData userData = this.userDataMap.computeIfAbsent(playerUUID, k -> new UserData());
        userData.tradingPair = tradingPair;
    }

    public TradingPair getTradringPair(UUID playerUUID) {
        if(this.userDataMap.get(playerUUID) != null)
            return this.userDataMap.get(playerUUID).tradingPair;
        return TradingPair.createDefault();
    }

    public void setSelectedBankAccountNumber(UUID playerUUID, int selectedBankAccountNumber) {
        UserData userData = this.userDataMap.computeIfAbsent(playerUUID, k -> new UserData());
        userData.selectedBankAccountNumber = selectedBankAccountNumber;
    }
    public int getSelectedBankAccountNumber(UUID playerUUID) {
        UserData userData = this.userDataMap.get(playerUUID);
        if (userData != null) {
            return userData.selectedBankAccountNumber;
        }
        return 0;
    }

    public float getAmount(UUID playerUUID)
    {
        UserData userData = this.userDataMap.get(playerUUID);
        if (userData != null) {
            return userData.amount;
        }
        return 1.0f; // Default amount if not set
    }
    public float getPrice(UUID playerUUID)
    {
        UserData userData = this.userDataMap.get(playerUUID);
        if (userData != null) {
            return userData.price;
        }
        return 1.0f; // Default price if not set
    }
    public void setAmount(UUID playerUUID, float amount) {
        UserData userData = this.userDataMap.computeIfAbsent(playerUUID, k -> new UserData());
        userData.amount = amount;
    }
    public void setPrice(UUID playerUUID, float price)
    {
        UserData userData = this.userDataMap.computeIfAbsent(playerUUID, k -> new UserData());
        userData.price = price;
    }
    public Map<UUID, UserData> getUserDataMap() {
        return userDataMap;
    }

    public Direction getFacing() {
        if (this.level != null) {
            BlockState state = this.level.getBlockState(this.worldPosition);
            if (state.getBlock() instanceof StockMarketBlock) {
                return state.getValue(StockMarketBlock.FACING);
            }
        }
        return Direction.NORTH; // Default fallback
    }

    public void onBlockPlacedBy(BlockState state, LivingEntity placer) {
        // Custom logic when block is placed
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);

        ListTag dataList = new ListTag();
        for(Map.Entry<UUID, UserData> entry : userDataMap.entrySet()) {
            UUID playerUUID = entry.getKey();
            UserData userData = entry.getValue();
            CompoundTag userTag = new CompoundTag();
            userTag.putUUID("playerUUID", playerUUID);
            if (userData != null) {
                userData.save(userTag);
            }
            dataList.add(userTag);
        }
        tag.put(StockMarketMod.MOD_ID, dataList);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }


    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        CompoundTag dataTag = tag.getCompound(StockMarketMod.MOD_ID);
        ListTag dataList = dataTag.getList(StockMarketMod.MOD_ID, 10); // 10 is the type for CompoundTag
        for (int i = 0; i < dataList.size(); i++) {
            CompoundTag userTag = dataList.getCompound(i);
            UUID playerUUID = userTag.getUUID("playerUUID");
            UserData userData = UserData.loadFromTag(userTag);
            if (userData != null) {
                this.userDataMap.put(playerUUID, userData);
            }
        }
    }
}

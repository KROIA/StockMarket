package net.kroia.stockmarket.entity.custom;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.block.custom.StockMarketBlock;
import net.kroia.stockmarket.entity.StockMarketEntities;
import net.kroia.stockmarket.market.TradingPair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class StockMarketBlockEntity extends BlockEntity{
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;

    // Current Item that the chart is displaying
    private TradingPair tradingPair;
    private int amount;
    private int price;

    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }

    public StockMarketBlockEntity(BlockPos pos, BlockState state) {
        super(StockMarketEntities.STOCK_MARKET_BLOCK_ENTITY.get(), pos, state);
        tradingPair = TradingPair.createDefault();
        amount = 1;
        price = 1;
    }

    public void setTradingPair(TradingPair tradingPair) {
        this.tradingPair = tradingPair;
    }

    public TradingPair getTradringPair() {
        return tradingPair;
    }

    public int getAmount()
    {
        return amount;
    }
    public int getPrice()
    {
        return price;
    }
    public void setAmount(int amount)
    {
        this.amount = amount;
    }
    public void setPrice(int price)
    {
        this.price = price;
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
    protected void saveAdditional( CompoundTag tag)
    {
        super.saveAdditional(tag);

        CompoundTag dataTag = new CompoundTag();
        CompoundTag itemTag = new CompoundTag();
        tradingPair.save(itemTag);
        dataTag.put("tradingPair", itemTag);
        dataTag.putInt("amount", amount);
        dataTag.putInt("price", price);
        tag.put(StockMarketMod.MOD_ID, dataTag);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }


    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        CompoundTag dataTag = tag.getCompound(StockMarketMod.MOD_ID);

        if (dataTag.contains("tradingPair")) {
            CompoundTag itemTag = dataTag.getCompound("tradingPair");
            tradingPair = new TradingPair();
            tradingPair.load(itemTag);
        } else {
            tradingPair = new TradingPair(new ItemID("minecraft:diamond"), BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getDefaultCurrencyItemID());
        }

        amount = dataTag.getInt("amount");
        price = dataTag.getInt("price");
    }
}

package net.kroia.stockmarket.entity.custom;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.block.custom.StockMarketBlock;
import net.kroia.stockmarket.entity.StockMarketEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// StockMarketBlockEntity.java
public class StockMarketBlockEntity extends BlockEntity /*implements MenuProvider */{


    //private ArrayList<CandleStickChart.CandleData> chartData;

    // Current Item that the chart is displaying
    private String itemID;
    private int amount;
    private int price;

    public StockMarketBlockEntity(BlockPos pos, BlockState state) {
        super(StockMarketEntities.STOCK_MARKET_BLOCK_ENTITY.get(), pos, state);
        StockMarketMod.LOGGER.info("StockMarketBlockEntity created at position " + pos);
        itemID = "minecraft:diamond";
        amount = 1;
        price = 1;
    }

    public void setItemID(String itemID) {
        this.itemID = itemID;
        // save entity changes
        //setChanged();
    }

    public String getItemID() {
        return itemID;
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
        //setChanged();
    }
    public void setPrice(int price)
    {
        this.price = price;
        //setChanged();
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


/*
    @Override
    public Component getDisplayName() {
        return Component.translatable("container.chart");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new ChartMenu(id, playerInventory, this, ContainerLevelAccess.NULL);
    }*/

    @Override
    protected void saveAdditional( CompoundTag tag)
    {
        super.saveAdditional(tag);

        CompoundTag dataTag = new CompoundTag();
        dataTag.putString("itemID", itemID);
        dataTag.putInt("amount", amount);
        dataTag.putInt("price", price);
        tag.put(StockMarketMod.MOD_ID, dataTag);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

/*
    @Override
    public @Nullable CompoundTag getUpdateTag()
    {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        // Deserialize your data
        CompoundTag dataTag = tag.getCompound(StockMarketMod.MODID);
        itemID = dataTag.getString("itemID");
        amount = dataTag.getInt("amount");
        price = dataTag.getInt("price");
    }*/

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        CompoundTag dataTag = tag.getCompound(StockMarketMod.MOD_ID);
        itemID = dataTag.getString("itemID");
        amount = dataTag.getInt("amount");
        price = dataTag.getInt("price");
    }


}

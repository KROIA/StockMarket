package net.kroia.stockmarket.entity.custom;

import net.kroia.banksystem.util.ItemID;
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

public class StockMarketBlockEntity extends BlockEntity /*implements MenuProvider */{

    // Current Item that the chart is displaying
    private ItemID itemID;
    private int amount;
    private int price;

    public StockMarketBlockEntity(BlockPos pos, BlockState state) {
        super(StockMarketEntities.STOCK_MARKET_BLOCK_ENTITY.get(), pos, state);
        itemID = new ItemID("minecraft:diamond");
        amount = 1;
        price = 1;
    }

    public void setItemID(ItemID itemID) {
        this.itemID = itemID;
    }

    public ItemID getItemID() {
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
        itemID.save(itemTag);
        dataTag.put("itemID", itemTag);
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
        String oldItemID = dataTag.getString("itemID");

        // Compatibility with old itemID format
        if(oldItemID.compareTo("") == 0)
        {
            itemID = new ItemID(dataTag.getCompound("itemID"));
        }
        else
        {
            itemID = new ItemID(oldItemID);
        }
        amount = dataTag.getInt("amount");
        price = dataTag.getInt("price");
    }
}

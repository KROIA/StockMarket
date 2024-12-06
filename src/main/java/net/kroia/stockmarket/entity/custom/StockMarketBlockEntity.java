package net.kroia.stockmarket.entity.custom;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.entity.ModEntities;
import net.kroia.stockmarket.menu.custom.ChartMenu;
import net.kroia.stockmarket.util.CandleStickChart;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

// StockMarketBlockEntity.java
public class StockMarketBlockEntity extends BlockEntity implements MenuProvider {
    //private ArrayList<CandleStickChart.CandleData> chartData;

    // Current Item that the chart is displaying
    private String itemID;

    public StockMarketBlockEntity(BlockPos pos, BlockState state) {
        super(ModEntities.STOCK_MARKET_BLOCK_ENTITY.get(), pos, state);
        StockMarketMod.LOGGER.info("ChartBlockEntity created at position " + pos);
        itemID = "item.minecraft.diamond";
    }

    public void setItemID(String itemID) {
        this.itemID = itemID;
    }

    public String getItemID() {
        return itemID;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.chart");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new ChartMenu(id, playerInventory, this, ContainerLevelAccess.NULL);
    }

    @Override
    protected void saveAdditional(@Nullable CompoundTag tag)
    {
        super.saveAdditional(tag);

        CompoundTag dataTag = new CompoundTag();
        dataTag.putString("itemID", itemID);
        tag.put(StockMarketMod.MODID, dataTag);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

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
    }
}

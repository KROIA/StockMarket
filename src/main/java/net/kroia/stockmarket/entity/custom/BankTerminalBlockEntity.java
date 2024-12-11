package net.kroia.stockmarket.entity.custom;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.block.custom.StockMarketBlock;
import net.kroia.stockmarket.entity.ModEntities;
import net.kroia.stockmarket.menu.custom.ChartMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public class BankTerminalBlockEntity  extends BlockEntity/* implements MenuProvider */{


    public BankTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModEntities.BANK_TERMINAL_BLOCK_ENTITY.get(), pos, state);
        StockMarketMod.LOGGER.info("BankTerminalBlockEntity created at position " + pos);
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

    public void onBlockPlacedBy(BlockState state, @Nullable LivingEntity placer) {
        // Custom logic when block is placed
    }



    /*@Override
    public Component getDisplayName() {
        return Component.translatable("container.chart");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new ChartMenu(id, playerInventory, this, ContainerLevelAccess.NULL);
    }*/

    @Override
    protected void saveAdditional(@Nullable CompoundTag tag)
    {
        super.saveAdditional(tag);

        CompoundTag dataTag = new CompoundTag();
        tag.put(StockMarketMod.MODID, dataTag);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        CompoundTag dataTag = tag.getCompound(StockMarketMod.MODID);
    }


}
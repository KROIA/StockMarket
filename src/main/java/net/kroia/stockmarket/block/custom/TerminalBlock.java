package net.kroia.stockmarket.block.custom;

import net.kroia.stockmarket.block.ModBlocks;
import net.kroia.stockmarket.item.custom.software.Software;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

public class TerminalBlock extends Block {
    public static final String NAME = "terminal_block";
    public TerminalBlock() {
        super(Properties.ofFullCopy(Blocks.IRON_BLOCK));
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH)); // Default facing
    }
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;


    @Override
    public final @NotNull ItemInteractionResult useItemOn(ItemStack pStack, BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHitResult) {
        // Get the item in the player's hand
        Item item = pStack.getItem();

        BlockState state = pLevel.getBlockState(pPos);
        Software softwareItem = null;
        if(item instanceof Software) {
            softwareItem = (Software)item;
        }
        if (!pLevel.isClientSide && softwareItem != null)
        {
            TerminalBlock programmedBlock = softwareItem.getProgrammedBlock();
            if(programmedBlock == null)
            {
                // Replace the block with the programmed block
                pLevel.setBlockAndUpdate(pPos, ModBlocks.TERMINAL_BLOCK.get().defaultBlockState().setValue(FACING, state.getValue(FACING)));
            }
            else
            {
                // Replace the block with the programmed block
                pLevel.setBlockAndUpdate(pPos, programmedBlock.defaultBlockState().setValue(FACING, state.getValue(FACING)));
            }
            return ItemInteractionResult.SUCCESS;
        }
        if(softwareItem == null)
        {
            // Open the GUI
            openGui(pLevel, pPos, pPlayer);
        }
        return ItemInteractionResult.SUCCESS;
    }
    @Override
    protected InteractionResult useWithoutItem(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, BlockHitResult pHitResult) {
        openGui(pLevel, pPos, pPlayer);
        return InteractionResult.PASS;
    }
    protected void openGui(Level level, BlockPos pos, Player player) {
        // Open the GUI
    }


    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
}

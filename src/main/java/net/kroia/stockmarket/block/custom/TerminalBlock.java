package net.kroia.stockmarket.block.custom;

import net.kroia.stockmarket.block.ModBlocks;
import net.kroia.stockmarket.item.custom.software.Software;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
        super(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK));
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH)); // Default facing
    }
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;


    @Override
    public final @NotNull InteractionResult use(@NotNull BlockState state,
                                                @NotNull Level level,
                                                @NotNull BlockPos pos,
                                                @NotNull Player player,
                                                @NotNull InteractionHand hand,
                                                @NotNull BlockHitResult hit) {
        // Get the item in the player's hand
        ItemStack itemInHand = player.getItemInHand(hand);
        Item item = itemInHand.getItem();

        Software softwareItem = null;
        if(item instanceof Software) {
            softwareItem = (Software)item;
        }
        if (!level.isClientSide && softwareItem != null)
        {
            TerminalBlock programmedBlock = softwareItem.getProgrammedBlock();
            if(programmedBlock == null)
            {
                // Replace the block with the programmed block
                level.setBlockAndUpdate(pos, ModBlocks.TERMINAL_BLOCK.get().defaultBlockState().setValue(FACING, state.getValue(FACING)));
            }
            else
            {
                // Replace the block with the programmed block
                level.setBlockAndUpdate(pos, programmedBlock.defaultBlockState().setValue(FACING, state.getValue(FACING)));
            }
            return InteractionResult.SUCCESS;
        }

        if(softwareItem == null) {
            openGui(state, level, pos, player, hand, hit);
        }
        return InteractionResult.SUCCESS;
    }
    protected void openGui(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
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

package net.kroia.stockmarket.minecraft.block.custom;

import com.mojang.serialization.MapCodec;
import net.kroia.modutilities.gui.display.AbstractDisplayBlock;
import net.kroia.modutilities.gui.display.AbstractDisplayBlockEntity;
import net.kroia.stockmarket.minecraft.entity.custom.StockMarketDisplayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class StockMarketDisplayBlock extends AbstractDisplayBlock {

    public static final String NAME = "stockmarket_display_block";

    private static final VoxelShape SHAPE_NORTH = Block.box(0, 0, 14, 16, 16, 16);
    private static final VoxelShape SHAPE_SOUTH = Block.box(0, 0, 0, 16, 16, 2);
    private static final VoxelShape SHAPE_EAST  = Block.box(0, 0, 0, 2, 16, 16);
    private static final VoxelShape SHAPE_WEST  = Block.box(14, 0, 0, 16, 16, 16);

    public static final MapCodec<StockMarketDisplayBlock> CODEC = simpleCodec(p -> new StockMarketDisplayBlock());

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    public StockMarketDisplayBlock() {
        super(BlockBehaviour.Properties.of().strength(2.0f).noOcclusion());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return switch (facing) {
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case EAST  -> SHAPE_EAST;
            case WEST  -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof StockMarketDisplayBlockEntity displayBE) || !displayBE.isActive())
            return InteractionResult.PASS;

        AbstractDisplayBlockEntity controllerBase = displayBE.getControllerEntity();
        if (!(controllerBase instanceof StockMarketDisplayBlockEntity ctrl))
            return InteractionResult.PASS;

        if (level.isClientSide()) {
            if (ctrl.getDisplayType() == StockMarketDisplayBlockEntity.DisplayType.PRICE_CHART
                    && ctrl.getSelectedItemID() != null) {
                ClientScreenHelper.openChartScreen(ctrl);
            } else {
                ClientScreenHelper.openConfigScreen(ctrl.getBlockPos(),
                        ctrl.getDisplayType(), ctrl.getSelectedItemID(), ctrl.getSecondItemID());
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StockMarketDisplayBlockEntity(pos, state);
    }

    private static class ClientScreenHelper {
        static void openChartScreen(StockMarketDisplayBlockEntity ctrl) {
            net.minecraft.nbt.CompoundTag viewport = null;
            if (ctrl.getChart() instanceof net.kroia.stockmarket.screen.widgets.DisplayCandlestickChart dcc) {
                viewport = dcc.getViewportState();
            }
            net.minecraft.client.Minecraft.getInstance().setScreen(
                    new net.kroia.stockmarket.screen.custom.DisplayChartScreen(
                            ctrl.getBlockPos(), ctrl.getSelectedItemID(),
                            ctrl.getSecondItemID(),
                            ctrl.getBlockPos().asLong(), viewport));
        }

        static void openConfigScreen(BlockPos pos, StockMarketDisplayBlockEntity.DisplayType type,
                                      net.kroia.banksystem.util.ItemID itemID,
                                      net.kroia.banksystem.util.ItemID secondItemID) {
            net.minecraft.client.Minecraft.getInstance().setScreen(
                    new net.kroia.stockmarket.screen.custom.DisplayConfigScreen(pos, type, itemID, secondItemID));
        }
    }
}

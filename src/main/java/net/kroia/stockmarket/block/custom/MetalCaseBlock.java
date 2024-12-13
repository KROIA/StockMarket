package net.kroia.stockmarket.block.custom;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class MetalCaseBlock extends Block {
    public static final String NAME = "metal_case_block";

    public MetalCaseBlock() {
        super(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK));
        this.registerDefaultState(this.defaultBlockState()); // Default facing
    }
    public MetalCaseBlock(Properties pProperties) {
        super(pProperties);
    }
}

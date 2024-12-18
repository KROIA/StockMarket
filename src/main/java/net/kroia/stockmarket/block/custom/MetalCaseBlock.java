package net.kroia.stockmarket.block.custom;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class MetalCaseBlock extends Block {
    public static final String NAME = "metal_case_block";

    public MetalCaseBlock() {
        super(Properties.ofFullCopy(Blocks.IRON_BLOCK)); // Copy properties from iron block
        this.registerDefaultState(this.defaultBlockState()); // Default facing
    }
    public MetalCaseBlock(Properties pProperties) {
        super(pProperties);
    }
}

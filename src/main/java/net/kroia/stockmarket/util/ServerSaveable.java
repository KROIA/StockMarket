package net.kroia.stockmarket.util;

import net.minecraft.nbt.CompoundTag;

public interface ServerSaveable {

    boolean save(CompoundTag tag);
    boolean load(CompoundTag tag);
}

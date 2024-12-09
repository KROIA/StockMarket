package net.kroia.stockmarket.util;

import net.minecraft.nbt.CompoundTag;

public interface ServerSaveable {

    void save(CompoundTag tag);
    void load(CompoundTag tag);
}

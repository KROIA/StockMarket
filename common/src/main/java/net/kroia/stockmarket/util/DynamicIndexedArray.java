package net.kroia.stockmarket.util;

import net.kroia.modutilities.ServerSaveable;
import net.minecraft.nbt.CompoundTag;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Function;

public class DynamicIndexedArray implements ServerSaveable {

    private float[] array;
    private int indexOffset;
    private final Function<Integer, Float> defaultValueProvider;

    public DynamicIndexedArray(int size, Function<Integer, Float> defaultValueProvider) {
        array = new float[size];
        this.defaultValueProvider = defaultValueProvider;
        indexOffset = 0;
    }

    public int getSize()
    {
        return array.length;
    }

    public float[] getArray() {
        return array;
    }

    public int getIndexOffset()
    {
        return indexOffset;
    }
    public float get(int virtualIndex)
    {
        int realIndex = getOffsetedIndex(virtualIndex);
        if(!isInRange(virtualIndex))
        {
            return defaultValueProvider.apply(virtualIndex);
        }
        return array[realIndex];
    }
    public boolean set(int virtualIndex, float value)
    {
        int realIndex = getOffsetedIndex(virtualIndex);
        if(isInRange(virtualIndex))
        {
            array[realIndex] = value;
            return true;
        }
        return false;
    }
    public void setAll(float value)
    {
        for(int i = 0; i < array.length; i++)
        {
            array[i] = value;
        }
    }
    public void clear()
    {
        setAll(0);
    }

    public boolean add(int virtualIndex, float value)
    {
        int realIndex = getOffsetedIndex(virtualIndex);
        if(isInRange(virtualIndex))
        {
            array[realIndex] += value;
            return true;
        }
        return false;
    }
    public boolean multyply(int virtualIndex, float value)
    {
        int realIndex = getOffsetedIndex(virtualIndex);
        if(isInRange(virtualIndex))
        {
            array[realIndex] *= value;
            return true;
        }
        return false;
    }

    public void moveOffset(int offset)
    {
        if(offset > 0)
        {
            // Move the array to the left
            int upperBound = array.length - offset;
            indexOffset += offset;
            if(upperBound>0) {
                for (int i = 0; i < upperBound; i++) {
                    array[i] = array[i + offset];
                }

                // Fill the rest with default values
                for (int i = upperBound; i < array.length; i++) {
                    array[i] = defaultValueProvider.apply(i + indexOffset);
                }
            }
            else {
                for (int i = 0; i < array.length; i++) {
                    array[i] = defaultValueProvider.apply(i + indexOffset);
                }
            }
        }
        else if(offset < 0)
        {
            indexOffset += offset;
            if(offset > -array.length) {
                // Move the array to the right
                for (int i = array.length - 1; i >= -offset; i--) {
                    array[i] = array[i + offset];
                }
                // Fill the rest with default values
                for (int i = 0; i < -offset; i++) {
                    array[i] = defaultValueProvider.apply(i + indexOffset);
                }
            }
            else {
                for (int i = 0; i < array.length; i++) {
                    array[i] = defaultValueProvider.apply(i + indexOffset);
                }
            }
        }
    }
    public void setOffset(int offset)
    {
        moveOffset(offset - indexOffset);
    }
    public boolean isInRange(int virtualIndex)
    {
        int realIndex = getOffsetedIndex(virtualIndex);
        return realIndex >= 0 && realIndex < array.length;
    }

    private int getOffsetedIndex(int index)
    {
        return index - indexOffset;
    }
    public int getVirtualIndex(int realIndex)
    {
        return realIndex + indexOffset;
    }


    @Override
    public boolean save(CompoundTag tag) {
        tag.putInt("indexOffset", indexOffset);
        tag.putByteArray("array", floatArrayToByteArray(array));
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(!tag.contains("indexOffset") || !tag.contains("array"))
            return false;

        indexOffset = tag.getInt("indexOffset");
        byte[] byteArray = tag.getByteArray("array");
        if(byteArray.length % 4 != 0) {
            return false; // Invalid byte array length for floats
        }
        array = byteArrayToFloatArray(byteArray);
        return true;
    }

    private static byte[] floatArrayToByteArray(float[] floatArray) {
        ByteBuffer buffer = ByteBuffer.allocate(floatArray.length * 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN); // or ByteOrder.BIG_ENDIAN
        for (float f : floatArray) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }
    private static float[] byteArrayToFloatArray(byte[] byteArray) {
        ByteBuffer buffer = ByteBuffer.wrap(byteArray);
        buffer.order(ByteOrder.LITTLE_ENDIAN); // same as used when writing
        float[] floatArray = new float[byteArray.length / 4];
        for (int i = 0; i < floatArray.length; i++) {
            floatArray[i] = buffer.getFloat();
        }
        return floatArray;
    }
}

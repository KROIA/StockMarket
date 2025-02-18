package net.kroia.stockmarket.util;

import dev.architectury.utils.value.FloatSupplier;

import java.security.Provider;
import java.util.function.Consumer;
import java.util.function.Function;

public class DynamicIndexedArray{

    private final float[] array;
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
            for(int i = 0; i < array.length - offset; i++)
            {
                array[i] = array[i + offset];
            }
            // Fill the rest with default values
            for(int i = array.length - offset; i < array.length; i++)
            {
                array[i] = defaultValueProvider.apply(i + indexOffset);
            }
            indexOffset += offset;
        }
        else if(offset < 0)
        {
            // Move the array to the right
            for(int i = array.length - 1; i >= -offset; i--)
            {
                array[i] = array[i + offset];
            }
            // Fill the rest with default values
            for(int i = 0; i < -offset; i++)
            {
                array[i] = defaultValueProvider.apply(i + indexOffset);
            }
            indexOffset += offset;
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


}

package net.kroia.stockmarket.util;

import net.kroia.modutilities.persistence.ServerSaveable;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.function.Function;

/**
 * @brief
 * Float array with a custom index offset
 *
 * @details
 * This class is used to create a float array.
 * An offset can be defined to shift the array in the used indexing.
 * This is used to dynamically adjust for the current market price to have the used float array
 * always in the area of the current market price.
 */
public class DynamicIndexedArray implements ServerSaveable {

    private float[] array;
    private long indexOffset;
    private final Function<Long, Float> defaultValueProvider;

    /**
     *
     * @param size of the used float array
     * @param defaultValueProvider function returning an default float value for a given index.
     *                             The given index can also be any number outside the real float array range.
     */
    public DynamicIndexedArray(int size, @NotNull Function<Long, Float> defaultValueProvider) {
        array = new float[size];
        this.defaultValueProvider = defaultValueProvider;
        indexOffset = 0;
    }

    /**
     * @description
     * Sets each value of the float array to its default value using the predefined defaultValueProvider function
     */
    public void resetToDefaultValues()
    {
        for(int i = 0; i < array.length; i++)
        {
            array[i] = defaultValueProvider.apply((long)i + indexOffset);
        }
    }
    public void clear()
    {
        Arrays.fill(array, 0);
    }

    /**
     * @return the elementcount of the float array
     */
    public int getSize()
    {
        return array.length;
    }

    /**
     * @return the float array itself. Index-offset does not apply to it.
     */
    public float[] getArray() {
        return array;
    }

    /**
     * @return the current lower end of the shifted array
     */
    public long getIndexOffset()
    {
        return indexOffset;
    }

    /**
     *
     * @param virtualIndex the offseted index
     * @return the float element of the given virtual index,
     *         or the default value if the virtual index is outside the array range
     */
    public float get(long virtualIndex)
    {
        int arrayIndex = getArrayIndex(virtualIndex);
        if(!isInRange(virtualIndex))
        {
            return defaultValueProvider.apply(virtualIndex);
        }
        return array[arrayIndex];
    }
    public long getRounded(long virtualIndex)
    {
        int arrayIndex = getArrayIndex(virtualIndex);
        if(!isInRange(virtualIndex))
        {
            return roundConservative(defaultValueProvider.apply(virtualIndex));
        }
        return roundConservative(array[arrayIndex]);
    }

    public float getSum(long virtualStartIndex, long virtualEndIndex)
    {
        float sum = 0;

        // Outside the array
        for(long i=virtualStartIndex; i<indexOffset; ++i)
        {
            sum += defaultValueProvider.apply(i);
        }

        // Inside the array range
        int arrayLoopEndIndex = (int)Math.min(virtualEndIndex - indexOffset, array.length);
        for(int i=0; i<arrayLoopEndIndex; ++i)
        {
            sum += array[i];
        }

        // Outside the array
        for(long i=virtualStartIndex + arrayLoopEndIndex; i<virtualEndIndex; i++)
        {
            sum += defaultValueProvider.apply(i);
        }
        return sum;
    }
    public long getSumRounded(long virtualStartIndex, long virtualEndIndex)
    {
        long sum = 0;

        // Outside the array
        for(long i=virtualStartIndex; i<indexOffset; ++i)
        {
            sum += roundConservative(defaultValueProvider.apply(i));
        }

        // Inside the array range
        int arrayLoopEndIndex = (int)Math.min(virtualEndIndex - indexOffset, array.length);
        for(int i=0; i<arrayLoopEndIndex; ++i)
        {
            sum += roundConservative(array[i]);
        }

        // Outside the array
        for(long i=virtualStartIndex + arrayLoopEndIndex; i<virtualEndIndex; i++)
        {
            sum += roundConservative(defaultValueProvider.apply(i));
        }
        return sum;
    }


    /**
     * Multiplies each element with its virtualIndex and sums them up
     * @param virtualStartIndex
     * @param virtualEndIndex
     * @return
     */
    public float getSumProduct(long virtualStartIndex, long virtualEndIndex)
    {
        float sum = 0;

        // Outside the array
        for(long i=virtualStartIndex; i<indexOffset; ++i)
        {
            sum += defaultValueProvider.apply(i) * i;
        }

        // Inside the array range
        int arrayLoopEndIndex = (int)Math.min(virtualEndIndex - indexOffset, array.length);
        for(int i=0; i<arrayLoopEndIndex; ++i)
        {
            sum += array[i] * (indexOffset + i);
        }

        // Outside the array
        for(long i=virtualStartIndex + arrayLoopEndIndex; i<virtualEndIndex; i++)
        {
            sum += defaultValueProvider.apply(i) * i;
        }
        return sum;
    }
    public long getSumProductRounded(long virtualStartIndex, long virtualEndIndex)
    {
        long sum = 0;

        // Outside the array
        for(long i=virtualStartIndex; i<indexOffset; ++i)
        {
            sum += roundConservative(defaultValueProvider.apply(i)) * i;
        }

        // Inside the array range
        int arrayLoopEndIndex = (int)Math.min(virtualEndIndex - indexOffset, array.length);
        for(int i=0; i<arrayLoopEndIndex; ++i)
        {
            sum += roundConservative(array[i]) * (indexOffset + i);
        }

        // Outside the array
        for(long i=virtualStartIndex + arrayLoopEndIndex; i<virtualEndIndex; i++)
        {
            sum += roundConservative(defaultValueProvider.apply(i)) * i;
        }
        return sum;
    }



    /**
     * @param virtualIndex on which the value should be set
     * @param value to set
     * @return true if the value was saved to the float array,
     *         false if the virtual index is outside the range of the float array
     */
    public boolean set(long virtualIndex, float value)
    {
        int arrayIndex = getArrayIndex(virtualIndex);
        if(isInRange(virtualIndex))
        {
            array[arrayIndex] = value;
            return true;
        }
        return false;
    }

    public boolean set(long virtualStartIndex, int count, long flipSignAboveVirtualIndex, float value)
    {
        int arrayIndex = getArrayIndex(virtualStartIndex);
        int flipIndex = getArrayIndex(flipSignAboveVirtualIndex);
        int endIndex = Math.min(arrayIndex + count, array.length);
        if(arrayIndex < 0)
            arrayIndex = 0;
        if(arrayIndex >= array.length)
            return false;
        if(endIndex < 0)
            return false;
        if(endIndex > array.length)
            endIndex = array.length;

        float absValue = Math.abs(value);

        flipIndex = Math.min(flipIndex, endIndex);
        for(int i = arrayIndex; i < flipIndex; i++)
        {
            array[i] = absValue;
        }
        absValue = -absValue;
        for(int i = flipIndex; i < endIndex; i++)
        {
            array[i] = absValue;
        }
        return true;
    }


    /**
     * @brief
     * Overwrites the float array, beginning from the virtualIndex up to the virtualIndex+value.length
     *
     * @param virtualIndex start index to overwrite
     * @param value float array to overwrite with. The array contains positive and negative values
     *              Below the (signFlipAboveVirtualIndex-offset) index all values must be positive, above negative.
     * @param signFlipAboveVirtualIndex the index from which on the sign inside the given value array is flipped
     * @param scaleMultiplier scaling factor, applied to the value elements
     * @return true if the value has been set.
     *         false if the desired virtualIndex is outside the array range
     */
    /*public boolean set(long virtualIndex, float[] value,  long signFlipAboveVirtualIndex, float scaleMultiplier)
    {
        int arrayIndex = getArrayIndex(virtualIndex);
        int endIndex = Math.min(arrayIndex + value.length, array.length);
        if(arrayIndex < 0)
            arrayIndex = 0;
        if(arrayIndex >= array.length)
            return false;
        if(endIndex < 0)
            return false;
        if(endIndex > array.length)
            endIndex = array.length;

        for(int i = arrayIndex; i < endIndex; i++, virtualIndex++)
        {
            if(virtualIndex > signFlipAboveVirtualIndex)
                array[i] = Math.min(0, value[i - arrayIndex] * scaleMultiplier);
            else
                array[i] = Math.max(0, value[i - arrayIndex] * scaleMultiplier);
        }
        return true;
    }*/

    /**
     * @brief
     * Overwrites the float array, beginning from the virtualIndex up to the virtualIndex+value.length
     *
     * @param virtualIndex start index to overwrite
     * @param value float array to overwrite with. The array contains positive and negative values
     *              Below the (signFlipAboveVirtualIndex-offset) index all values must be positive, above negative.
     * @return true if the value has been set.
     *         false if the desired virtualIndex is outside the array range
     */
    public boolean set(long virtualIndex, float[] value)
    {
        int arrayIndex = getArrayIndex(virtualIndex);
        int endIndex = Math.min(arrayIndex + value.length, array.length);
        if(arrayIndex < 0)
            arrayIndex = 0;
        if(arrayIndex >= array.length)
            return false;
        if(endIndex < 0)
            return false;
        if(endIndex > array.length)
            endIndex = array.length;

        if (endIndex - arrayIndex >= 0)
            System.arraycopy(value, 0, array, arrayIndex, endIndex - arrayIndex);
        return true;
    }


    /**
     * Adds the given volume to the element at the given virtual index
     * @param virtualIndex to add the value to
     * @param value to be added
     * @return true if the value has been added
     *         false if the desired virtualIndex is outside the array range
     */
    public boolean add(long virtualIndex, float value)
    {
        int arrayIndex = getArrayIndex(virtualIndex);
        if(isInRange(virtualIndex))
        {
            array[arrayIndex] += value;
            return true;
        }
        return false;
    }


    /**
     * @brief
     * Adds the float array, beginning from the virtualIndex up to the virtualIndex+value.length
     *
     * @param virtualIndex start index to add
     * @param value float array to add with. The array contains positive and negative values
     * @param signFlipAboveVirtualIndex the index from which on the sign inside the given value array is flipped
     * @param scaleMultiplier scaling factor, applied to the value elements
     * @return true if the value has been set.
     *         false if the desired virtualIndex is outside the array range
     */
    /*public boolean add(long virtualIndex, float[] value, long signFlipAboveVirtualIndex, float scaleMultiplier)
    {
        int arrayIndex = getArrayIndex(virtualIndex);
        int endIndex = Math.min(arrayIndex + value.length, array.length);
        if(arrayIndex < 0)
            arrayIndex = 0;
        if(arrayIndex >= array.length)
            return false;
        if(endIndex < 0)
            return false;
        if(endIndex > array.length)
            endIndex = array.length;
        for(int i = arrayIndex; i < endIndex; i++, virtualIndex++)
        {
            if(virtualIndex > signFlipAboveVirtualIndex)
                array[i] = Math.min(0, array[i] + value[i - arrayIndex] * scaleMultiplier);
            else
                array[i] = Math.max(0, array[i]+ value[i - arrayIndex] * scaleMultiplier);
        }
        return false;
    }*/

    public boolean add(long virtualIndex, float[] value, long flipSignAboveVirtualIndex)
    {
        int arrayIndex = getArrayIndex(virtualIndex);

        int endIndex = Math.min(arrayIndex + value.length, array.length);
        if(arrayIndex < 0)
            arrayIndex = 0;
        if(arrayIndex >= array.length)
            return false;
        if(endIndex < 0)
            return false;
        if(endIndex > array.length)
            endIndex = array.length;

        int flipIndex = Math.min(getArrayIndex(flipSignAboveVirtualIndex), endIndex);

        for(int i = arrayIndex; i < flipIndex; i++)
        {
            array[i] = Math.max(0, array[i] + value[i]);
        }
        for(int i = flipIndex; i < endIndex; i++)
        {
            array[i] =  Math.min(0, array[i] + value[i]);
        }
        return true;
    }

    public boolean add(long virtualStartIndex, int count, long flipSignAboveVirtualIndex, float value)
    {
        int arrayIndex = getArrayIndex(virtualStartIndex);
        int flipIndex = getArrayIndex(flipSignAboveVirtualIndex);
        int endIndex = Math.min(arrayIndex + count, array.length);
        if(arrayIndex < 0)
            arrayIndex = 0;
        if(arrayIndex >= array.length)
            return false;
        if(endIndex < 0)
            return false;
        if(endIndex > array.length)
            endIndex = array.length;

        flipIndex = Math.min(flipIndex, endIndex);

        float absValue = Math.abs(value);
        for(int i = arrayIndex; i < flipIndex; i++)
        {
            array[i] += absValue;
        }

        for(int i = flipIndex; i < endIndex; i++)
        {
            array[i] -= absValue;
        }
        return true;
    }


    public boolean multyply(long virtualIndex, float value)
    {
        int arrayIndex = getArrayIndex(virtualIndex);
        if(isInRange(virtualIndex))
        {
            array[arrayIndex] *= value;
            return true;
        }
        return false;
    }

    /**
     * Rounds the given value in such a way that the absolute value of the returned
     * long is always less or equal than the input:
     *                      |result| <= |input| && sign(result) == sign(input)
     * @param value
     * @return
     */
    public static long roundConservative(float value)
    {
        if(value < 0)
            return (long)Math.ceil(value);
        else
            return (long)Math.floor(value);
    }

    public void moveOffset(long offset)
    {
        if(offset > 0)
        {
            // Move the array to the a lower index
            int upperBound = (int)((long)array.length - offset);
            indexOffset += offset;
            if(upperBound>0) {
                for (int i = 0; i < upperBound; i++) {
                    array[i] = array[(int)((long)i + offset)];
                }

                // Fill the rest with default values
                for (int i = upperBound; i < array.length; i++) {
                    array[i] = defaultValueProvider.apply((long)i + indexOffset);
                }
            }
            else {
                for (int i = 0; i < array.length; i++) {
                    array[i] = defaultValueProvider.apply((long)i + indexOffset);
                }
            }
        }
        else if(offset < 0)
        {
            indexOffset += offset;
            if(offset > (long)-array.length) {
                // Move the array to the right
                for (int i = array.length - 1; i >= -offset; i--) {
                    array[i] = array[(int)(i + offset)];
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
    public void setOffset(long offset)
    {
        moveOffset(offset - indexOffset);
    }
    public boolean isInRange(long virtualIndex)
    {
        long arrayIndex = getArrayIndex(virtualIndex);
        return arrayIndex >= 0 && arrayIndex < (long)array.length;
    }

    private int getArrayIndex(long virtualIndex)
    {
        return (int)(virtualIndex - indexOffset);
    }
    public long getVirtualIndex(int arrayIndex)
    {
        return arrayIndex + indexOffset;
    }


    @Override
    public boolean save(CompoundTag tag) {
        tag.putLong("indexOffset", indexOffset);
        tag.putByteArray("array", floatArrayToByteArray(array));
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(!tag.contains("indexOffset") || !tag.contains("array"))
            return false;

        indexOffset = tag.getLong("indexOffset");
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

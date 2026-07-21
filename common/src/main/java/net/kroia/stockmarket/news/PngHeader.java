package net.kroia.stockmarket.news;

import org.jetbrains.annotations.Nullable;

/**
 * Minimal, dependency-free PNG header validator/parser (NewsEventSystem picture plan §3).
 * <p>
 * Validates the 8-byte PNG signature and parses the mandatory first chunk (IHDR) to
 * extract {@code width}, {@code height}, {@code bitDepth} and {@code colorType}.
 * <b>Nothing beyond the header is inspected</b> — the server never decompresses or
 * decodes pixel data (no ImageIO/AWT, no decompression-bomb surface). The dimension
 * caps enforced by {@link NewsPictureLibrary} bound the client's decode cost instead.
 * <p>
 * Pure and side-effect free: operates only on the given byte array, never throws
 * (structurally invalid input simply yields {@code null} from {@link #parse(byte[])}),
 * and is therefore trivially unit-testable.
 */
public final class PngHeader {

    /** The fixed 8-byte PNG file signature ({@code \x89 P N G \r \n \x1a \n}). */
    public static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };

    /**
     * Minimum byte count that can hold signature + IHDR chunk header + 13 IHDR data
     * bytes (8 + 4 + 4 + 13). The IHDR CRC is not required to be present — this parser
     * validates structure, not integrity (a corrupt file fails on the client decode,
     * which is guarded separately).
     */
    public static final int MIN_HEADER_BYTES = 8 + 4 + 4 + 13;

    /** Byte length of the IHDR chunk data as mandated by the PNG specification. */
    private static final int IHDR_DATA_LENGTH = 13;

    private final int width;
    private final int height;
    private final int bitDepth;
    private final int colorType;

    private PngHeader(int width, int height, int bitDepth, int colorType) {
        this.width = width;
        this.height = height;
        this.bitDepth = bitDepth;
        this.colorType = colorType;
    }

    /** @return the image width in pixels (always &gt; 0 for a parsed header) */
    public int getWidth() { return width; }

    /** @return the image height in pixels (always &gt; 0 for a parsed header) */
    public int getHeight() { return height; }

    /** @return the bit depth per sample (1, 2, 4, 8 or 16 for spec-conforming files) */
    public int getBitDepth() { return bitDepth; }

    /**
     * @return the PNG color type (0 = grayscale, 2 = truecolor, 3 = palette,
     *         4 = grayscale+alpha, 6 = truecolor+alpha)
     */
    public int getColorType() { return colorType; }

    /**
     * Checks whether the given bytes start with the 8-byte PNG signature.
     *
     * @param data the raw file bytes (may be null or short — both yield false)
     * @return true if the PNG signature is present
     */
    public static boolean hasPngSignature(@Nullable byte[] data) {
        if (data == null || data.length < PNG_SIGNATURE.length) return false;
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (data[i] != PNG_SIGNATURE[i]) return false;
        }
        return true;
    }

    /**
     * Parses the PNG signature and IHDR chunk from raw file bytes.
     * <p>
     * Never throws. Returns {@code null} when the data is null/truncated, the
     * signature is wrong, the first chunk is not a well-formed IHDR, or the parsed
     * dimensions are not strictly positive (the PNG spec forbids zero dimensions;
     * values above {@code Integer.MAX_VALUE} read as negative and are rejected too).
     * <p>
     * Note: dimension <b>caps</b> (16..512) are intentionally not enforced here —
     * that is {@link NewsPictureLibrary} policy; this class only answers
     * "is this structurally a PNG and what does its header say".
     *
     * @param data the raw file bytes
     * @return the parsed header, or null if the data is not a structurally valid PNG
     */
    public static @Nullable PngHeader parse(@Nullable byte[] data) {
        if (data == null || data.length < MIN_HEADER_BYTES) return null;
        if (!hasPngSignature(data)) return null;

        // Chunk layout after the signature: 4-byte big-endian length, 4-byte type,
        // <length> data bytes, 4-byte CRC. The first chunk MUST be IHDR with length 13.
        int chunkLength = readIntBE(data, 8);
        if (chunkLength != IHDR_DATA_LENGTH) return null;
        if (data[12] != 'I' || data[13] != 'H' || data[14] != 'D' || data[15] != 'R') return null;

        // IHDR data: width(4) height(4) bitDepth(1) colorType(1) compression(1) filter(1) interlace(1)
        int width = readIntBE(data, 16);
        int height = readIntBE(data, 20);
        if (width <= 0 || height <= 0) return null; // zero or > 2^31-1 (reads negative)

        int bitDepth = data[24] & 0xFF;
        int colorType = data[25] & 0xFF;
        return new PngHeader(width, height, bitDepth, colorType);
    }

    /** Reads a big-endian 32-bit signed integer at the given offset. */
    private static int readIntBE(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    @Override
    public String toString() {
        return "PngHeader{" + width + "x" + height
                + ", bitDepth=" + bitDepth + ", colorType=" + colorType + "}";
    }
}

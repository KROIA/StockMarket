package net.kroia.stockmarket.screen;

import net.kroia.modutilities.ColorUtilities;

public class UI_Colors {

    public static final int buyColorGreen = ColorUtilities.getRGB(63,255,139);
    public static final int buyColorGreen_dark = ColorUtilities.setBrightness(buyColorGreen, 0.8f);
    public static final int buyColorGreen_bright = ColorUtilities.setBrightness(buyColorGreen, 1.2f);

    public static final int sellColorRed = ColorUtilities.getRGB(255,113,108);
    public static final int sellColorRed_dark = ColorUtilities.setBrightness(sellColorRed, 0.8f);
    public static final int sellColorRed_bright = ColorUtilities.setBrightness(sellColorRed, 1.2f);

    public static final int candlestickChart_horizontalLine = ColorUtilities.getRGB(32,32,32,32);
    public static final int candlestickChart_zeroLine = ColorUtilities.getRGB(32,32,32,64);
    public static final int candlestickChart_currentPriceLine = ColorUtilities.getRGB(200,200,200,180);
}

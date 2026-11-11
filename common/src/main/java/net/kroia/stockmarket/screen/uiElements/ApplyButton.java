package net.kroia.stockmarket.screen.uiElements;

import net.kroia.modutilities.ColorUtilities;
import net.kroia.modutilities.gui.elements.Button;

/**
 * T-219: an Apply button that turns orange while its form has unsaved changes, so players
 * notice they still have to press it before leaving the page.
 * <p>
 * ModUtilities' {@link Button} has no highlight state, and its default colors are only
 * known after construction, so this subclass remembers them to restore them later.
 * The owner decides what "unsaved" means and calls {@link #setUnsaved(boolean)}
 * (typically every tick or frame).
 */
public class ApplyButton extends Button {

    /** Background while there are unsaved changes (same amber-orange as BankSystem's unsaved Save button). */
    public static final int UNSAVED_COLOR = 0xFFD99A26;

    private final int defaultBackgroundColor;
    private final int defaultHoverColor;
    private final int defaultPressedColor;

    /**
     * Creates the button with the default button colors.
     *
     * @param text          the button label
     * @param onFallingEdge action run when the button is pressed
     */
    public ApplyButton(String text, Runnable onFallingEdge) {
        super(text, onFallingEdge);
        defaultBackgroundColor = getBackgroundColor();
        defaultHoverColor = getHoverColor();
        defaultPressedColor = getPressedColor();
    }

    /**
     * Switches between the orange "unsaved changes" colors and the default button colors.
     * Only sets three color fields, so it is cheap enough to call every frame.
     *
     * @param unsaved true while the form differs from the last loaded/applied values
     */
    public void setUnsaved(boolean unsaved) {
        setBackgroundColor(unsaved ? UNSAVED_COLOR : defaultBackgroundColor);
        setHoverColor(unsaved ? ColorUtilities.setBrightness(UNSAVED_COLOR, 0.8f) : defaultHoverColor);
        setPressedColor(unsaved ? ColorUtilities.setBrightness(UNSAVED_COLOR, 0.6f) : defaultPressedColor);
    }
}

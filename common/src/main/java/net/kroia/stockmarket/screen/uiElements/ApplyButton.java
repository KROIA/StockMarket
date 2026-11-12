package net.kroia.stockmarket.screen.uiElements;

import net.kroia.banksystem.util.BankSystemGuiScreen;
import net.kroia.modutilities.gui.elements.Button;

/**
 * T-219: an Apply button that turns orange while its form has unsaved changes, so players
 * notice they still have to press it before leaving the page.
 * <p>
 * SM-311: the color and the recoloring come from BankSystem
 * ({@link BankSystemGuiScreen#UNSAVED_COLOR}, {@link BankSystemGuiScreen#setButtonColor}), so the
 * Mod Settings and market settings Apply buttons match BankSystem's unsaved Save buttons exactly.
 * The owner decides what "unsaved" means and calls {@link #setUnsaved(boolean)}
 * (typically every tick or frame).
 */
public class ApplyButton extends Button {

    /**
     * Creates the button with the default button colors.
     *
     * @param text          the button label
     * @param onFallingEdge action run when the button is pressed
     */
    public ApplyButton(String text, Runnable onFallingEdge) {
        super(text, onFallingEdge);
    }

    /**
     * Switches between BankSystem's orange "unsaved changes" colors and the default button colors.
     * Only sets three color fields, so it is cheap enough to call every frame.
     *
     * @param unsaved true while the form differs from the last loaded/applied values
     */
    public void setUnsaved(boolean unsaved) {
        BankSystemGuiScreen.setButtonColor(this, unsaved ? BankSystemGuiScreen.UNSAVED_COLOR : 0);
    }
}

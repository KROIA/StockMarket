package net.kroia.stockmarket.screen.uiElements;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.elements.ItemView;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * Clickable item icon with a built-in close button in the top-right corner.
 * <p>
 * Left-clicking the icon fires the {@code onSelected} callback.
 * The close button uses press-and-release semantics: the remove event
 * only fires when the mouse is pressed AND released over the close area.
 * Moving the mouse away before releasing cancels the removal.
 * <p>
 * The close button occupies 1/4 of the total area (top-right quadrant)
 * and renders an X drawn with lines above the item icon Z-level.
 * <p>
 * Selection highlighting is driven externally via {@link #setSelected(boolean)}.
 */
public class MarketItemButton extends ItemView {

    private final ItemID marketID;
    private final Consumer<ItemID> onSelected;
    private final Runnable onRemove;
    private boolean selected = false;
    private boolean closeButtonPressed = false;

    /**
     * @param stack      the item stack to display
     * @param marketID   the market this button represents
     * @param onSelected called when the icon area (not close button) is clicked
     * @param onRemove   called when the close button is pressed and released
     */
    public MarketItemButton(ItemStack stack, ItemID marketID, Consumer<ItemID> onSelected, Runnable onRemove) {
        super(stack);
        this.marketID = marketID;
        this.onSelected = onSelected;
        this.onRemove = onRemove;
    }

    public ItemID getMarketID() {
        return marketID;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isSelected() {
        return selected;
    }

    @Override
    public void renderBackground() {
        super.renderBackground();
        if (selected) {
            drawRect(0, 0, getWidth(), getHeight(), 0x6000FF00);
        }
        if (isMouseOver()) {
            drawRect(0, 0, getWidth(), getHeight(), 0x80FFFFFF);
        }
    }

    @Override
    protected void render() {
        super.render();
        int w = getWidth();
        int h = getHeight();
        int closeW = w / 2;
        int closeH = h / 2;
        int closeX = w - closeW;

        boolean hoverClose = isMouseOver() && isInCloseArea(getMouseX(), getMouseY());

        var graphics = getGraphics();
        graphics.pushPose();
        graphics.translate(0, 0, 200);

        int bgColor = (hoverClose && closeButtonPressed) ? 0xC0FF0000
                     : hoverClose ? 0xA0CC0000
                     : 0x80000000;
        drawRect(closeX, 0, closeW, closeH, bgColor);

        int pad = 2;
        int lineColor = hoverClose ? 0xFFFFFFFF : 0xFFFF4444;
        StockMarketGuiElement.drawXMark(this, closeX, 0, closeW, closeH, pad, 1.0f, lineColor);

        graphics.popPose();
    }

    @Override
    protected boolean mouseClickedOverElement(int button) {
        if (button == 0) {
            if (isInCloseArea(getMouseX(), getMouseY())) {
                closeButtonPressed = true;
                return true;
            }
            onSelected.accept(marketID);
            return true;
        }
        return false;
    }

    @Override
    protected void mouseReleased(int button) {
        if (button == 0 && closeButtonPressed) {
            closeButtonPressed = false;
            if (isMouseOver() && isInCloseArea(getMouseX(), getMouseY())) {
                onRemove.run();
            }
        }
    }

    private boolean isInCloseArea(int mouseX, int mouseY) {
        int closeW = getWidth() / 2;
        int closeH = getHeight() / 2;
        int closeX = getWidth() - closeW;
        return mouseX >= closeX && mouseX < getWidth()
                && mouseY >= 0 && mouseY < closeH;
    }
}

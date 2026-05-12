package net.kroia.stockmarket.screen.uiElements;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.elements.ItemView;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * Clickable item icon with a star overlay in the top-right corner for toggling
 * favorite status.
 * <p>
 * Left-clicking the icon area fires the {@code onSelected} callback.
 * The star button uses press-and-release semantics: the toggle event only fires
 * when the mouse is pressed AND released over the star area. Moving the mouse
 * away before releasing cancels the toggle.
 * <p>
 * The star occupies the top-right quadrant (1/2 width, 1/2 height) and renders
 * above the item icon Z-level. A filled gold star indicates a favorite;
 * an empty gray star indicates a non-favorite.
 * <p>
 * Selection highlighting is driven externally via {@link #setSelected(boolean)}.
 */
public class MarketFavoriteButton extends ItemView {

    private final ItemID marketID;
    private final Consumer<ItemID> onSelected;
    private final Runnable onFavoriteToggle;
    private boolean selected = false;
    private boolean favorite = false;
    private boolean starPressed = false;

    /**
     * @param stack            the item stack to display
     * @param marketID         the market this button represents
     * @param onSelected       called when the icon area (not star) is clicked
     * @param onFavoriteToggle called when the star is pressed and released
     */
    public MarketFavoriteButton(ItemStack stack, ItemID marketID,
                                Consumer<ItemID> onSelected, Runnable onFavoriteToggle) {
        super(stack);
        this.marketID = marketID;
        this.onSelected = onSelected;
        this.onFavoriteToggle = onFavoriteToggle;
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

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public boolean isFavorite() {
        return favorite;
    }

    @Override
    public void renderBackground() {
        super.renderBackground();
        // Green selection overlay
        if (selected) {
            drawRect(0, 0, getWidth(), getHeight(), 0x6000FF00);
        }
        // White hover overlay on item area
        if (isMouseOver()) {
            drawRect(0, 0, getWidth(), getHeight(), 0x80FFFFFF);
        }
    }

    @Override
    protected void render() {
        super.render();

        int w = getWidth();
        int h = getHeight();
        int starW = w / 2;
        int starH = h / 2;
        int starX = w - starW;

        var graphics = getGraphics();
        graphics.pushPose();
        graphics.translate(0, 0, 200);

        int starColor = favorite ? 0xFFFFD700 : 0x60444444;

        float cx = starX + starW / 2.0f;
        float cy = starH / 2.0f;
        float r = Math.min(starW, starH) / 2.0f - 1;

        // 5 outer vertices of a regular pentagram, top point up
        int[] px = new int[5];
        int[] py = new int[5];
        for (int i = 0; i < 5; i++) {
            double angle = Math.toRadians(-90 + i * 72);
            px[i] = Math.round(cx + (float) (r * Math.cos(angle)));
            py[i] = Math.round(cy + (float) (r * Math.sin(angle)));
        }

        // Draw pentagram (connect every other vertex: 0→2→4→1→3→0)
        float lineWidth = 1.5f;
        drawLine(px[0], py[0], px[2], py[2], lineWidth, starColor);
        drawLine(px[2], py[2], px[4], py[4], lineWidth, starColor);
        drawLine(px[4], py[4], px[1], py[1], lineWidth, starColor);
        drawLine(px[1], py[1], px[3], py[3], lineWidth, starColor);
        drawLine(px[3], py[3], px[0], py[0], lineWidth, starColor);

        graphics.popPose();
    }

    @Override
    protected boolean mouseClickedOverElement(int button) {
        if (button == 0) {
            if (isInStarArea(getMouseX(), getMouseY())) {
                starPressed = true;
                return true;
            }
            onSelected.accept(marketID);
            return true;
        }
        return false;
    }

    @Override
    protected void mouseReleased(int button) {
        if (button == 0 && starPressed) {
            starPressed = false;
            if (isMouseOver() && isInStarArea(getMouseX(), getMouseY())) {
                onFavoriteToggle.run();
            }
        }
    }

    /**
     * Checks whether the given mouse coordinates (relative to this element) fall
     * within the star overlay area (top-right quadrant).
     */
    private boolean isInStarArea(int mouseX, int mouseY) {
        int starW = getWidth() / 2;
        int starH = getHeight() / 2;
        int starX = getWidth() - starW;
        return mouseX >= starX && mouseX < getWidth()
                && mouseY >= 0 && mouseY < starH;
    }
}

package net.kroia.stockmarket.screen.uiElements;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.news.ClientNewsPictureCache;
import net.kroia.stockmarket.news.NewsPictureLibrary;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import org.jetbrains.annotations.Nullable;

/**
 * Reusable display box for one published news picture (task T-091, picture plan §6).
 * <p>
 * The element is constructed with the record's picture <b>content hash</b> and simply
 * polls {@link ClientNewsPictureCache#getTexture(byte[])} every frame: the first poll
 * self-enqueues the fetch at HIGH priority, later polls return the registered texture
 * once it landed — so the widget needs no callback wiring of its own (the hosting
 * screens listen to the cache's change listener only to trigger repaints/rebuilds).
 * While the picture is not (yet) available — still loading <i>or</i> permanently
 * unavailable, the cache API does not distinguish the two — a silent flat paper box
 * with a thin ink outline is drawn instead, so the layout never jumps when the
 * texture pops in (fixed-box contract, plan §6).
 * <p>
 * <b>Fit modes</b> ({@link FitMode}):
 * <ul>
 *   <li>{@link FitMode#COVER} — the image is scaled to fully cover the box and
 *       center-cropped (float-UV {@code drawTexture} overload with a virtual texture
 *       size). Used by the newspaper entries and the event details screen; square
 *       sources in a square box render uncropped.</li>
 *   <li>{@link FitMode#FIT} — the whole image is scaled down aspect-correct and
 *       centered, the remaining bars are letterboxed in the newspaper's paper tone.
 *       Used by the Active-tab thumbnails (resolved decision §12.5: no cropping
 *       there).</li>
 * </ul>
 * <p>
 * <b>Sizing:</b> the box is whatever bounds the host assigns — by convention a
 * <b>square (1:1)</b> everywhere (resolved decision §12.2); the element itself does
 * not enforce that. Hosts must not create the element for records without a picture
 * (null hash simply renders the placeholder forever, wasting the space).
 */
public class NewsPictureElement extends StockMarketGuiElement {

    /** How the source image is mapped into the display box — see the class Javadoc. */
    public enum FitMode {
        /** Scale to cover the whole box, center-crop the overflow (newspaper/details). */
        COVER,
        /** Scale the whole image into the box, letterbox the rest (Active-tab thumbnails). */
        FIT
    }

    /**
     * Placeholder fill while the picture is not available: a slightly darker paper
     * tone than the entry cards ({@link NewsEntryPanel#COLOR_ENTRY_PAPER}), so the
     * pending box reads as a subtle inset on the card without shouting "error".
     */
    private static final int COLOR_PLACEHOLDER_PAPER = 0xFFE7E0D0;

    /** The record's 20-byte picture content hash, or null (permanent placeholder). */
    private final byte @Nullable [] pictureHash;
    private final FitMode fitMode;
    /**
     * Diagnostic (T-106): logs the element's first paint once — hash prefix, box
     * dimensions, cache state — so a picture that never shows up in-game reveals
     * whether the widget is even being rendered (bounds/parent) or the fetch/decode
     * chain is silently dropping the bytes. WARN-level, one log per element instance.
     */
    private boolean loggedFirstRender = false;

    /**
     * Creates the picture box for one record picture.
     *
     * @param pictureHash the record's 20-byte picture content hash; null is tolerated
     *                    (renders the placeholder forever) but hosts should not create
     *                    the element for picture-less records in the first place
     * @param fitMode     how the image is mapped into the box (see {@link FitMode})
     */
    public NewsPictureElement(byte @Nullable [] pictureHash, FitMode fitMode) {
        super();
        this.pictureHash = pictureHash;
        this.fitMode = fitMode;
        // The element paints its own content — the GuiElement default translucent
        // background/outline must never show through (NewsScreen T-086 precedent).
        setEnableBackground(false);
        setEnableOutline(false);
    }

    /** @return the per-connection picture cache, or null while not connected */
    private static @Nullable ClientNewsPictureCache getPictureCache() {
        return BACKEND_INSTANCES != null ? BACKEND_INSTANCES.NEWS_PICTURE_CACHE : null;
    }

    @Override
    protected void layoutChanged() {
        // All geometry (crop/fit scaling) is derived from the current bounds at
        // render time — nothing to precompute here.
    }

    @Override
    protected void render() {
        int boxW = getWidth();
        int boxH = getHeight();
        if (boxW <= 0 || boxH <= 0)
            return;

        // Poll per frame: null both while pending and when unavailable — the
        // placeholder silently covers both states (see class Javadoc). getTexture
        // itself enqueues/retries the fetch, so no extra bookkeeping is needed here.
        ClientNewsPictureCache cache = getPictureCache();
        ClientNewsPictureCache.LoadedPicture picture =
                cache != null ? cache.getTexture(pictureHash) : null;
        // T-106 diagnostic (downgraded T-112): one DEBUG per element on first
        // paint. Reveals whether the widget is being rendered at all
        // (bounds/parent), whether the picture cache is reachable at that
        // point, and whether a texture landed before the first frame ever
        // drew. Kept for future troubleshooting but now DEBUG-level: the
        // pipeline root cause was fixed via the picture-store self-heal path
        // (T-112), covered by tests.
        if (!loggedFirstRender) {
            loggedFirstRender = true;
            String hashHex = pictureHash != null ? NewsPictureLibrary.toHex(pictureHash) : "null";
            String hashShort = hashHex.length() > 12 ? hashHex.substring(0, 12) + "…" : hashHex;
            StockMarketMod.LOGGER.debug(
                    "[NewsPictureElement] First render: hash={} box={}x{} fit={} cache={} texture={}",
                    hashShort, boxW, boxH, fitMode,
                    cache != null ? "wired" : "null",
                    picture != null ? "loaded" : "pending");
        }
        if (picture == null || picture.width() <= 0 || picture.height() <= 0) {
            renderPlaceholder(boxW, boxH);
            return;
        }
        if (fitMode == FitMode.COVER) {
            renderCover(picture, boxW, boxH);
        } else {
            renderFit(picture, boxW, boxH);
        }
    }

    /**
     * COVER: scale so the image covers the box ({@code s = max(boxW/imgW, boxH/imgH)}),
     * then crop the centered box-sized window out of it. Implemented with the float-UV
     * {@code drawTexture} overload: the scaled image dimensions act as the virtual
     * texture size and the UV offset is half the overflow — the GPU samples exactly
     * the centered crop, no ModUtilities change needed (plan §0/§6).
     */
    private void renderCover(ClientNewsPictureCache.LoadedPicture picture, int boxW, int boxH) {
        float scale = Math.max(boxW / (float) picture.width(), boxH / (float) picture.height());
        // Never smaller than the box (rounding), or the UV window would leave the texture.
        int virtualW = Math.max(boxW, Math.round(picture.width() * scale));
        int virtualH = Math.max(boxH, Math.round(picture.height() * scale));
        float uOffset = (virtualW - boxW) / 2.0f;
        float vOffset = (virtualH - boxH) / 2.0f;
        drawTexture(picture.location(), 0, 0, uOffset, vOffset, boxW, boxH, virtualW, virtualH);
    }

    /**
     * FIT: scale the whole image into the box ({@code s = min(boxW/imgW, boxH/imgH)}),
     * centered, with the uncovered bars letterboxed in the newspaper paper tone —
     * nothing is ever cropped (Active-tab thumbnails, resolved decision §12.5).
     */
    private void renderFit(ClientNewsPictureCache.LoadedPicture picture, int boxW, int boxH) {
        drawRect(0, 0, boxW, boxH, NewsEntryPanel.COLOR_ENTRY_PAPER); // letterbox bars
        float scale = Math.min(boxW / (float) picture.width(), boxH / (float) picture.height());
        int drawW = Math.max(1, Math.round(picture.width() * scale));
        int drawH = Math.max(1, Math.round(picture.height() * scale));
        drawTexture(picture.location(), (boxW - drawW) / 2, (boxH - drawH) / 2,
                0.0f, 0.0f, drawW, drawH, drawW, drawH);
    }

    /**
     * Pending/unavailable placeholder: flat paper-gray box with a thin ink outline
     * (palette shared with {@link NewsEntryPanel}). Deliberately silent — no text,
     * no spinner — because the box either fills in a moment later or the picture is
     * gone for good; both read fine as plain paper.
     */
    private void renderPlaceholder(int boxW, int boxH) {
        drawRect(0, 0, boxW, boxH, COLOR_PLACEHOLDER_PAPER);
        drawFrame(0, 0, boxW, boxH, NewsEntryPanel.COLOR_ENTRY_EDGE, 1);
    }
}

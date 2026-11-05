package net.kroia.stockmarket.screen.uiElements;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.minecraft.item.StockMarketItems;
import net.kroia.stockmarket.news.NewsRecord;
import net.kroia.stockmarket.news.NewsTranslations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics; // mc<=1.21.11
//import net.minecraft.client.gui.GuiGraphicsExtractor; // mc>=26.1
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent; // mc<=1.21.1
//import net.minecraft.client.gui.components.toasts.ToastManager; // mc>=1.21.2
//import net.minecraft.client.renderer.RenderType; // mc>=1.21.2 mc<=1.21.5
//import net.minecraft.client.renderer.RenderPipelines; // mc>=1.21.6
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation; // mc<=1.21.10
//import net.minecraft.resources.Identifier; // mc>=1.21.11
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Vanilla-style toast popup shown when a news event publishes — the <b>only</b> push
 * notification of the news system, and only for players who opted in via the
 * checkbox in the newspaper screen (task T-074, plan §4; default off, no chat
 * message ever, no explicit sound — only whatever the vanilla toast rail does).
 * <p>
 * Rendering mimics the advancement toast: the vanilla {@code toast/advancement}
 * background sprite, the newspaper item as icon, a fixed title line ("Market News")
 * and below it the record's headline. The headline is resolved from the inline
 * translation map <b>at render time</b> ({@link NewsTranslations} fallback chain),
 * so it follows the client's current language. Headlines wider than the toast are
 * clipped to the first wrapped line — the full text is always available in the
 * newspaper itself.
 */
public class NewsToast implements Toast {

    /** Vanilla advancement-toast background sprite (gray frame, 160x32). */
    private static final ResourceLocation BACKGROUND_SPRITE = // mc<=1.21.10
    //private static final Identifier BACKGROUND_SPRITE = // mc>=1.21.11
            ResourceLocation.withDefaultNamespace("toast/advancement"); // mc<=1.21.10
            //Identifier.withDefaultNamespace("toast/advancement"); // mc>=1.21.11

    private static final Component TITLE =
            Component.translatable("gui." + StockMarketMod.MOD_ID + ".news_toast.title");

    /** How long the toast stays visible (scaled by the accessibility multiplier). */
    private static final long DISPLAY_TIME_MS = 5000L;

    /** Text area geometry (matches the vanilla advancement/recipe toasts). */
    private static final int TEXT_X = 30;
    private static final int TEXT_WIDTH = 126;

    // ARGB with explicit opaque alpha: 1.21.6+ fonts render a 0xRRGGBB color as fully transparent.
    private static final int TITLE_COLOR = 0xFFFFCA00; // newspaper-gold, advancement style
    private static final int HEADLINE_COLOR = 0xFFFFFFFF;

    private final NewsRecord record;

    /**
     * @param record the freshly published news record whose headline is shown
     */
    public NewsToast(NewsRecord record) {
        this.record = record;
    }

    // Visibility computed in update(), returned by getWantedVisibility() (1.21.2+ toast API).
    //private Visibility wantedVisibility = Visibility.SHOW; // mc>=1.21.2

    /**
     * Toast entry point (1.21.1 API): draws the toast and decides its visibility.
     *
     * @param guiGraphics          the draw context
     * @param toastComponent       the owning toast rail
     * @param timeSinceLastVisible milliseconds since the toast became visible
     * @return {@code SHOW} while within the display time, then {@code HIDE}
     */
    @Override // mc<=1.21.1
    public @NotNull Visibility render(GuiGraphics guiGraphics, ToastComponent toastComponent, // mc<=1.21.1
                                      long timeSinceLastVisible) { // mc<=1.21.1
        draw(guiGraphics, toastComponent.getMinecraft().font); // mc<=1.21.1
        return visibilityAt(timeSinceLastVisible, toastComponent.getNotificationDisplayTimeMultiplier()); // mc<=1.21.1
    } // mc<=1.21.1

    // 1.21.2+ toast API: visibility is updated per tick, rendering is separate.
    //@Override // mc>=1.21.2
    //public void update(ToastManager toastManager, long timeSinceLastVisible) { // mc>=1.21.2
    //    wantedVisibility = visibilityAt(timeSinceLastVisible, toastManager.getNotificationDisplayTimeMultiplier()); // mc>=1.21.2
    //} // mc>=1.21.2
    //@Override // mc>=1.21.2
    //public @NotNull Visibility getWantedVisibility() { // mc>=1.21.2
    //    return wantedVisibility; // mc>=1.21.2
    //} // mc>=1.21.2
    //@Override // mc>=1.21.2
    //public void render(GuiGraphics guiGraphics, Font font, long timeSinceLastVisible) { // mc>=1.21.2 mc<=1.21.11
    //public void extractRenderState(GuiGraphicsExtractor guiGraphics, Font font, long timeSinceLastVisible) { // mc>=26.1
    //    draw(guiGraphics, font); // mc>=1.21.2
    //} // mc>=1.21.2

    /**
     * @param timeSinceLastVisible milliseconds since the toast became visible
     * @param displayTimeMultiplier the accessibility notification-time multiplier
     * @return {@code SHOW} while within the scaled display time, then {@code HIDE}
     */
    private static Visibility visibilityAt(long timeSinceLastVisible, double displayTimeMultiplier) {
        double displayTime = DISPLAY_TIME_MS * displayTimeMultiplier;
        return timeSinceLastVisible >= displayTime ? Visibility.HIDE : Visibility.SHOW;
    }

    /**
     * Draws the toast frame, newspaper icon, title line and (current-language)
     * headline. Called by the vanilla toast rail every frame while visible.
     *
     * @param guiGraphics the draw context
     * @param font        the font used for title and headline
     */
    private void draw(GuiGraphics guiGraphics, Font font) { // mc<=1.21.11
    //private void draw(GuiGraphicsExtractor guiGraphics, Font font) { // mc>=26.1
        guiGraphics.blitSprite(BACKGROUND_SPRITE, 0, 0, width(), height()); // mc<=1.21.1
        //guiGraphics.blitSprite(RenderType::guiTextured, BACKGROUND_SPRITE, 0, 0, width(), height()); // mc>=1.21.2 mc<=1.21.5
        //guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND_SPRITE, 0, 0, width(), height()); // mc>=1.21.6

        guiGraphics.drawString(font, TITLE, TEXT_X, 7, TITLE_COLOR, false); // mc<=1.21.11
        //guiGraphics.text(font, TITLE, TEXT_X, 7, TITLE_COLOR, false); // mc>=26.1

        // Resolve at render time so a mid-session language switch is reflected.
        String headline = NewsTranslations.resolve(record.getHeadline(),
                Minecraft.getInstance().options.languageCode);
        List<FormattedCharSequence> lines = font.split(FormattedText.of(headline), TEXT_WIDTH);
        if (!lines.isEmpty()) {
            // Single line only (32px toast height) — clipped headlines are fine,
            // the newspaper screen shows the full text.
            guiGraphics.drawString(font, lines.get(0), TEXT_X, 18, HEADLINE_COLOR, false); // mc<=1.21.11
            //guiGraphics.text(font, lines.get(0), TEXT_X, 18, HEADLINE_COLOR, false); // mc>=26.1
        }

        guiGraphics.renderFakeItem(new ItemStack(StockMarketItems.NEWSPAPER.get()), 8, 8); // mc<=1.21.11
        //guiGraphics.fakeItem(new ItemStack(StockMarketItems.NEWSPAPER.get()), 8, 8); // mc>=26.1
    }
}

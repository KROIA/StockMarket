package net.kroia.stockmarket.screen.uiElements;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.minecraft.item.StockMarketItems;
import net.kroia.stockmarket.news.NewsRecord;
import net.kroia.stockmarket.news.NewsTranslations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
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
    private static final ResourceLocation BACKGROUND_SPRITE =
            ResourceLocation.withDefaultNamespace("toast/advancement");

    private static final Component TITLE =
            Component.translatable("gui." + StockMarketMod.MOD_ID + ".news_toast.title");

    /** How long the toast stays visible (scaled by the accessibility multiplier). */
    private static final long DISPLAY_TIME_MS = 5000L;

    /** Text area geometry (matches the vanilla advancement/recipe toasts). */
    private static final int TEXT_X = 30;
    private static final int TEXT_WIDTH = 126;

    private static final int TITLE_COLOR = 0xFFCA00; // newspaper-gold, advancement style
    private static final int HEADLINE_COLOR = 0xFFFFFF;

    private final NewsRecord record;

    /**
     * @param record the freshly published news record whose headline is shown
     */
    public NewsToast(NewsRecord record) {
        this.record = record;
    }

    /**
     * Draws the toast frame, newspaper icon, title line and (current-language)
     * headline. Called by the vanilla toast rail every frame while visible.
     *
     * @param guiGraphics          the draw context
     * @param toastComponent       the owning toast rail
     * @param timeSinceLastVisible milliseconds since the toast became visible
     * @return {@code SHOW} while within the display time, then {@code HIDE}
     */
    @Override
    public @NotNull Visibility render(GuiGraphics guiGraphics, ToastComponent toastComponent,
                                      long timeSinceLastVisible) {
        guiGraphics.blitSprite(BACKGROUND_SPRITE, 0, 0, width(), height());

        Font font = toastComponent.getMinecraft().font;
        guiGraphics.drawString(font, TITLE, TEXT_X, 7, TITLE_COLOR, false);

        // Resolve at render time so a mid-session language switch is reflected.
        String headline = NewsTranslations.resolve(record.getHeadline(),
                Minecraft.getInstance().options.languageCode);
        List<FormattedCharSequence> lines = font.split(FormattedText.of(headline), TEXT_WIDTH);
        if (!lines.isEmpty()) {
            // Single line only (32px toast height) — clipped headlines are fine,
            // the newspaper screen shows the full text.
            guiGraphics.drawString(font, lines.get(0), TEXT_X, 18, HEADLINE_COLOR, false);
        }

        guiGraphics.renderFakeItem(new ItemStack(StockMarketItems.NEWSPAPER.get()), 8, 8);

        double displayTime = DISPLAY_TIME_MS * toastComponent.getNotificationDisplayTimeMultiplier();
        return timeSinceLastVisible >= displayTime ? Visibility.HIDE : Visibility.SHOW;
    }
}

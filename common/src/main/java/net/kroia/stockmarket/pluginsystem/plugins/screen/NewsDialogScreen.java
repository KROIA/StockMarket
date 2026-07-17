package net.kroia.stockmarket.pluginsystem.plugins.screen;

import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.Frame;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.VerticalListView;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Small modal dialog screen for the news admin UI (T-085): a title, a scrollable list
 * of (word-wrapped) text lines and either a single OK button or a Confirm/Cancel pair.
 * <p>
 * Built as a real child {@link Screen} (parent-screen navigation, ModSettingsScreen
 * precedent): closing it — OK, Cancel, ESC or the inventory key — returns to the
 * <b>same parent screen instance</b>, so the news plugin window keeps its full state
 * (selected tab, scroll positions, pending edits). {@code onConfirm} runs <b>after</b>
 * the dialog closed, i.e. with the parent screen active again.
 * <p>
 * Used for:
 * <ul>
 *   <li>long admin-operation results that would overflow the one-line status label
 *       (e.g. multi-event Stop-all outcomes) — {@link #okDialog},</li>
 *   <li>the trigger-despite-cooldown confirmation — {@link #confirmDialog}.</li>
 * </ul>
 */
public class NewsDialogScreen extends StockMarketGuiScreen {

    private static class Texts {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".news_plugin.";
        public static final Component OK = Component.translatable(PREFIX + "dialog_ok");
        public static final Component CANCEL = Component.translatable(PREFIX + "dialog_cancel");
    }

    /** Font scale of the dialog body lines (matches the news window's meta scale). */
    private static final float BODY_SCALE = 0.8f;
    /** Dialog panel background (matches the former details-popup panel color). */
    private static final int PANEL_BACKGROUND = 0xFF3B3B3B;

    private final Frame panel = new Frame();
    private final Label titleLabel;
    private final VerticalListView linesList = new VerticalListView();
    private final Button primaryButton;
    private final @Nullable Button cancelButton;
    /** Runs after the dialog closed when the primary (OK/Confirm) button was pressed. */
    private final @Nullable Runnable onConfirm;
    /** The unwrapped body lines; wrapped to the panel width in {@link #updateLayout}. */
    private final List<String> rawLines;

    /**
     * Creates an information dialog with a single OK button.
     *
     * @param parent the screen to return to on close (never null in practice)
     * @param title  the dialog title
     * @param lines  the body lines (each wrapped independently to the dialog width)
     * @return the dialog screen, ready for {@code GuiScreen.setScreen}
     */
    public static NewsDialogScreen okDialog(Screen parent, Component title, List<String> lines) {
        return new NewsDialogScreen(parent, title, lines, Texts.OK, null);
    }

    /**
     * Creates a confirmation dialog with Confirm and Cancel buttons.
     *
     * @param parent       the screen to return to on close/cancel
     * @param title        the dialog title
     * @param lines        the body lines (the question / context)
     * @param confirmLabel the label of the confirm button
     * @param onConfirm    runs after the dialog closed when the user confirmed
     * @return the dialog screen, ready for {@code GuiScreen.setScreen}
     */
    public static NewsDialogScreen confirmDialog(Screen parent, Component title, List<String> lines,
                                                 Component confirmLabel, Runnable onConfirm) {
        return new NewsDialogScreen(parent, title, lines, confirmLabel, onConfirm);
    }

    private NewsDialogScreen(Screen parent, Component title, List<String> lines,
                             Component primaryLabel, @Nullable Runnable onConfirm) {
        super(title, parent);
        this.onConfirm = onConfirm;
        this.rawLines = new ArrayList<>(lines);

        titleLabel = new Label(title.getString());
        titleLabel.setAlignment(Label.Alignment.CENTER);

        linesList.setLayout(new LayoutVertical(0, 0, true, false));

        primaryButton = new Button(primaryLabel.getString(), this::onPrimaryPressed);
        cancelButton = onConfirm != null ? new Button(Texts.CANCEL.getString(), this::onClose) : null;

        panel.setBackgroundColor(PANEL_BACKGROUND);
        panel.addChild(titleLabel);
        panel.addChild(linesList);
        panel.addChild(primaryButton);
        if (cancelButton != null) {
            panel.addChild(cancelButton);
        }
        addElement(panel);
    }

    /** OK/Confirm: close first (back to the parent screen), then run the callback. */
    private void onPrimaryPressed() {
        onClose();
        if (onConfirm != null) {
            onConfirm.run();
        }
    }

    @Override
    protected void updateLayout(Gui gui) {
        int p = StockMarketGuiElement.padding;
        int s = StockMarketGuiElement.spacing;
        int eh = StockMarketGuiElement.defaultElementHeight;
        int w = getWidth();
        int h = getHeight();

        // Body sizing: wrap all lines for the target width, then shrink the panel
        // height to the content (clamped to the window).
        int panelW = Math.min(Math.max(280, w * 60 / 100), Math.max(80, w - 2 * p));
        int textW = panelW - 2 * p - linesList.getScrollbarThickness();
        int lineH = NewsUiText.lineHeight(panel, BODY_SCALE);

        linesList.removeChilds();
        int contentH = 0;
        for (String raw : rawLines) {
            for (String wrapped : NewsUiText.wrapText(panel, raw, textW, BODY_SCALE)) {
                Label line = new Label(wrapped);
                line.setPadding(0);
                line.setTextFontScale(BODY_SCALE);
                line.setHeight(lineH);
                linesList.addChild(line);
                contentH += lineH;
            }
        }

        int maxPanelH = Math.max(100, h - 2 * p);
        int panelH = Math.min(eh + s + contentH + s + eh + 3 * p, maxPanelH);
        panel.setBounds((w - panelW) / 2, (h - panelH) / 2, panelW, panelH);

        titleLabel.setBounds(p, p, panelW - 2 * p, eh);
        int buttonsY = panelH - p - eh;
        linesList.setBounds(p, titleLabel.getBottom() + s,
                panelW - 2 * p, buttonsY - s - titleLabel.getBottom() - s);
        if (cancelButton != null) {
            int halfW = (panelW - 2 * p - s) / 2;
            primaryButton.setBounds(p, buttonsY, halfW, eh);
            cancelButton.setBounds(p + halfW + s, buttonsY, panelW - 2 * p - halfW - s, eh);
        } else {
            primaryButton.setBounds(p, buttonsY, panelW - 2 * p, eh);
        }
    }
}

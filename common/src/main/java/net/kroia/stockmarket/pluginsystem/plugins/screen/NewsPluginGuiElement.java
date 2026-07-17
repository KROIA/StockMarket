package net.kroia.stockmarket.pluginsystem.plugins.screen;

import io.netty.buffer.ByteBuf;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.client.GuiScreen;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.networking.request.NewsAdminRequest;
import net.kroia.stockmarket.news.NewsTranslations;
import net.kroia.stockmarket.news.NewsUiFormatting;
import net.kroia.stockmarket.pluginsystem.plugin.core.PluginSyncData;
import net.kroia.stockmarket.pluginsystem.plugins.NewsPlugin;
import net.kroia.stockmarket.pluginsystem.screen.PluginGuiElement;
import net.kroia.stockmarket.screen.uiElements.NewsPictureElement;
import net.kroia.stockmarket.screen.widgets.CandlestickChart;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dedicated management window for the {@link NewsPlugin} (tasks T-075/T-076/T-083/T-085).
 * Hosted in a {@code PluginDetailScreen} ({@link #needsCustomScreen()} = true).
 *
 * <pre>
 * +--------------------------------------+----------------------+
 * | [Reload] [Stop all]  status label    |  Per-market settings |
 * +--------------------------------------+  selected market     |
 * | [Active][All events][Scheduler] tabs |  news enabled  [x]   |
 * | +----------------------------------+ |  sensitivity  [1.0]  |
 * | | tab content:                     | |  [Apply]             |
 * | |  Active     — live event list    | |                      |
 * | |  All events — definitions +      | |  Market selection    |
 * | |    enable / reset-CD / trigger   | |  (ItemSelectionView) |
 * | |  Scheduler  — overrides + the    | |                      |
 * | |    upcoming timeline             | |  Price chart of the  |
 * | +----------------------------------+ |  selected market     |
 * +--------------------------------------+----------------------+
 * </pre>
 *
 * <p><b>Live data:</b> the element consumes the plugin's
 * {@code PluginRuntimeDataStream} ({@link NewsPlugin.RuntimeStreamData}). The stream is
 * started in {@link #onPluginSyncDataReceived} (same lifecycle as
 * {@link TargetPriceBotGuiElement}): the hosting screens stop it via
 * {@link #stopDataStream()} ({@code PluginDetailScreen.onClose},
 * {@code PluginManagementScreen} entry cleanup) and restart it when the management
 * screen re-layouts. Each received snapshot rebuilds the active-event list, refreshes
 * the scheduler tab's effective values and redraws the upcoming timeline; an empty
 * event list is streamed deliberately by the server so the GUI clears itself when the
 * last event ends. Child screens (details/dialogs, see below) do <b>not</b> stop the
 * stream — the element keeps receiving data while they are open.</p>
 *
 * <p><b>Per-market custom settings</b> ({@code newsEnabled}, {@code sensitivity}) are
 * edited through the existing generic custom-settings machinery
 * ({@link #sendCustomSettings} → {@code PluginCustomSettingsRequest}), exactly like
 * {@link VolatilityPluginGuiElement}. Selecting a market in the
 * {@link ItemSelectionView} binds the editor to that market, names it in the
 * selected-market label and shows its live price history in the small
 * {@link CandlestickChart} at the bottom right (T-085; same
 * {@code subscribeToMarketPriceUpdate} plumbing as {@code TradeScreen}, released in
 * {@link #stopDataStream()} and on selection change).</p>
 *
 * <p><b>Admin actions</b> (T-076/T-085): Reload reloads the JSON definitions (full
 * validation report goes to the client chat) and Stop-all stops every active event.
 * Triggering is <b>per row</b> in the All-events tab — each definition row carries a
 * Trigger button and (while on cooldown) a Reset-cooldown button. Triggering an event
 * whose cooldown is still running first asks for confirmation via a
 * {@link NewsDialogScreen} (the server-side TRIGGER deliberately bypasses cooldowns,
 * so no extra force flag is needed — see {@code NewsAdminRequest#handleTrigger}).
 * All operations are admin-gated <b>server-side</b> ({@code playerIsAdmin}). The
 * one-line result is shown in the status label; results that do not fit the label
 * (e.g. multi-event Stop-all outcomes) additionally open an OK dialog with the full
 * text, one line per message (T-085).</p>
 *
 * <p><b>All-events tab &amp; details screen</b> (T-083/T-085): every successful
 * {@code NewsAdminRequest} response carries a full {@link NewsAdminRequest.EventDetails}
 * snapshot — the definition list and (if open) the details screen are refreshed from
 * it, so the GUI state is always server-confirmed. The per-row enable checkbox is
 * <b>server-authoritative</b>: a click immediately reverts the visual state and sends
 * {@code SET_ENABLED}; only the confirmed response snapshot re-checks it. Clicking a
 * row (or a planned upcoming-timeline entry) opens a {@link NewsEventDetailsScreen} —
 * a real child screen, so ESC returns to this window with its full state (selected
 * tab, scroll, market selection) intact.</p>
 *
 * <p><b>Scheduler tab</b> (T-082/T-083): shows the four <b>effective</b> scheduler
 * values (stream-refreshed every 500 ms), marks admin-overridden values, and lets the
 * admin edit them. Apply sends only the values that were actually edited
 * ({@code SET_SCHEDULER}, unchanged fields stay {@code null}); Reset-to-file sends
 * {@code resetAll}. Validation is server-side and atomic — on rejection nothing changed,
 * the server message is shown and the confirmed values are re-displayed. The upcoming
 * timeline below presents the pre-planned slots as <i>planned</i> (fire-time
 * re-validation is authoritative); time-only slots (empty event id) state that the
 * event is picked at trigger time. Planned entries are clickable and open the event's
 * details screen (T-085).</p>
 */
public class NewsPluginGuiElement extends PluginGuiElement<NewsPlugin.Settings, NewsPlugin.RuntimeStreamData> {

    /**
     * Translatable UI texts. Package-visible because {@link NewsEventDetailsScreen}
     * renders the same detail lines (T-085).
     */
    static class Texts {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".news_plugin.";
        public static final Component ACTIVE_EVENTS_TITLE = Component.translatable(PREFIX + "active_events_title");
        public static final Component NO_ACTIVE_EVENTS = Component.translatable(PREFIX + "no_active_events");
        public static final Component PUBLISHED = Component.translatable(PREFIX + "published");
        public static final Component PENDING_PUBLICATION = Component.translatable(PREFIX + "pending_publication");
        public static final Component SETTINGS_TITLE = Component.translatable(PREFIX + "settings_title");
        public static final Component NEWS_ENABLED = Component.translatable(PREFIX + "news_enabled");
        public static final Component SENSITIVITY = Component.translatable(PREFIX + "sensitivity");
        public static final Component APPLY = Component.translatable(PREFIX + "apply");
        public static final Component TRIGGER = Component.translatable(PREFIX + "trigger");
        public static final Component RELOAD = Component.translatable(PREFIX + "reload");
        public static final Component STOP_ALL = Component.translatable(PREFIX + "stop_all");
        public static final Component REQUEST_FAILED = Component.translatable(PREFIX + "request_failed");

        // ── T-083: tabs ──────────────────────────────────────────────────
        public static final Component TAB_ACTIVE = Component.translatable(PREFIX + "tab_active");
        public static final Component TAB_ALL_EVENTS = Component.translatable(PREFIX + "tab_all_events");
        public static final Component TAB_SCHEDULER = Component.translatable(PREFIX + "tab_scheduler");
        public static final Component ALL_EVENTS_TITLE = Component.translatable(PREFIX + "all_events_title");
        public static final Component SCHEDULER_TITLE = Component.translatable(PREFIX + "scheduler_title");

        // ── T-083: all-events list ───────────────────────────────────────
        public static final Component NO_DEFINITIONS = Component.translatable(PREFIX + "no_definitions");
        public static final Component MARKER_ADMIN_ONLY = Component.translatable(PREFIX + "marker_admin_only");
        public static final Component MARKER_ACTIVE = Component.translatable(PREFIX + "marker_active");
        public static final Component MARKER_DISABLED = Component.translatable(PREFIX + "marker_disabled");
        public static final Component ROW_DETAILS_TOOLTIP = Component.translatable(PREFIX + "row_details_tooltip");
        public static final Component ENABLE_EVENT_TOOLTIP = Component.translatable(PREFIX + "enable_event_tooltip");

        // ── T-085: per-row trigger + reset cooldown ──────────────────────
        public static final Component ROW_TRIGGER_TOOLTIP = Component.translatable(PREFIX + "row_trigger_tooltip");
        public static final Component RESET_COOLDOWN = Component.translatable(PREFIX + "reset_cooldown");
        public static final Component RESET_COOLDOWN_TOOLTIP = Component.translatable(PREFIX + "reset_cooldown_tooltip");
        public static final Component CONFIRM_TRIGGER_TITLE = Component.translatable(PREFIX + "confirm_trigger_title");
        public static final Component CONFIRM_TRIGGER_YES = Component.translatable(PREFIX + "confirm_trigger_yes");

        // ── T-085: status dialog / details screen / market selection ────
        public static final Component STATUS_DIALOG_TITLE = Component.translatable(PREFIX + "status_dialog_title");
        public static final Component DETAILS_SCREEN_TITLE = Component.translatable(PREFIX + "details_screen_title");
        public static final Component NO_MARKET_SELECTED = Component.translatable(PREFIX + "no_market_selected");
        public static final Component NEWS_ENABLED_TOOLTIP = Component.translatable(PREFIX + "news_enabled_tooltip");
        public static final Component SENSITIVITY_TOOLTIP = Component.translatable(PREFIX + "sensitivity_tooltip");

        // ── T-086: clickable active-row market icons + weight-factor tooltip ──
        /** Action hint on an active-event row's market icon (selects the market). */
        public static final Component ICON_SELECT_TOOLTIP = Component.translatable(PREFIX + "icon_select_tooltip");
        /** Explains the per-market impact weight multiplier (details screen). */
        public static final Component WEIGHT_FACTOR_TOOLTIP = Component.translatable(PREFIX + "details_weight_factor_tooltip");

        // ── T-093: per-event Stop + Skip-phase buttons (Active tab rows) ──
        /** Label of the per-event hard-stop button. */
        public static final Component ROW_STOP = Component.translatable(PREFIX + "row_stop");
        /** Explains the hard-stop semantics (cancel in any phase, cooldown restart). */
        public static final Component ROW_STOP_TOOLTIP = Component.translatable(PREFIX + "row_stop_tooltip");
        /** Label of the per-event skip-phase button. */
        public static final Component ROW_SKIP_PHASE = Component.translatable(PREFIX + "row_skip_phase");
        /** Explains the skip-phase semantics (fast-forward to the next phase). */
        public static final Component ROW_SKIP_PHASE_TOOLTIP = Component.translatable(PREFIX + "row_skip_phase_tooltip");

        // ── T-100: Active-tab step progress + requirement-confirm dialog ──
        /** Title of the trigger confirmation when requirements are unmet (plan §10.1). */
        public static final Component CONFIRM_TRIGGER_REQUIREMENTS_TITLE =
                Component.translatable(PREFIX + "confirm_trigger_requirements_title");
        /** Explanatory line above the unmet-requirement list in the confirm dialog. */
        public static final Component CONFIRM_TRIGGER_REQUIREMENTS =
                Component.translatable(PREFIX + "confirm_trigger_requirements");

        // ── T-083: scheduler tab ─────────────────────────────────────────
        public static final Component SCHED_MIN_SECONDS = Component.translatable(PREFIX + "scheduler_min_seconds");
        public static final Component SCHED_MAX_SECONDS = Component.translatable(PREFIX + "scheduler_max_seconds");
        public static final Component SCHED_MAX_GLOBAL = Component.translatable(PREFIX + "scheduler_max_global");
        public static final Component SCHED_MAX_PER_MARKET = Component.translatable(PREFIX + "scheduler_max_per_market");
        public static final Component SCHED_OVERRIDDEN = Component.translatable(PREFIX + "scheduler_overridden");
        public static final Component SCHED_APPLY = Component.translatable(PREFIX + "scheduler_apply");
        public static final Component SCHED_RESET = Component.translatable(PREFIX + "scheduler_reset");
        public static final Component SCHED_MIN_TOOLTIP = Component.translatable(PREFIX + "scheduler_min_tooltip");
        public static final Component SCHED_MAX_TOOLTIP = Component.translatable(PREFIX + "scheduler_max_tooltip");
        public static final Component SCHED_MAX_GLOBAL_TOOLTIP = Component.translatable(PREFIX + "scheduler_max_global_tooltip");
        public static final Component SCHED_MAX_PER_MARKET_TOOLTIP = Component.translatable(PREFIX + "scheduler_max_per_market_tooltip");
        public static final Component SCHED_APPLY_TOOLTIP = Component.translatable(PREFIX + "scheduler_apply_tooltip");
        public static final Component SCHED_RESET_TOOLTIP = Component.translatable(PREFIX + "scheduler_reset_tooltip");

        // ── T-083: upcoming timeline ─────────────────────────────────────
        public static final Component UPCOMING_TITLE = Component.translatable(PREFIX + "upcoming_title");
        public static final Component UPCOMING_TOOLTIP = Component.translatable(PREFIX + "upcoming_tooltip");
        public static final Component UPCOMING_TIME_ONLY = Component.translatable(PREFIX + "upcoming_time_only");
        public static final Component UPCOMING_NONE = Component.translatable(PREFIX + "upcoming_none");

        // ── T-083: details view ──────────────────────────────────────────
        public static final Component DETAILS_COOLDOWN_READY = Component.translatable(PREFIX + "details_cooldown_ready");
        public static final Component DETAILS_REVERSAL_PERMANENT = Component.translatable(PREFIX + "details_reversal_permanent");
        public static final Component DETAILS_NO_MARKETS = Component.translatable(PREFIX + "details_no_markets");
        public static final Component DETAILS_NOT_ACTIVE = Component.translatable(PREFIX + "details_not_active");
        public static final Component VALUE_YES = Component.translatable(PREFIX + "value_yes");
        public static final Component VALUE_NO = Component.translatable(PREFIX + "value_no");

        // ── T-100: details screen — sequence breakdown, requirements, chains ──
        /** Marker appended to a step that bakes its final value permanently. */
        public static final Component DETAILS_STEP_PERMANENT =
                Component.translatable(PREFIX + "details_step_permanent");
        /** Header of the trigger-requirements section (only rendered when non-empty). */
        public static final Component DETAILS_REQUIREMENTS_TITLE =
                Component.translatable(PREFIX + "details_requirements_title");
        /** Header of the chained-events section (only rendered when non-empty). */
        public static final Component DETAILS_CHAINS_TITLE =
                Component.translatable(PREFIX + "details_chains_title");

        /**
         * Localizes a phase/step name for display (T-100): the classic phase names
         * (PENDING/RAMPING/HOLDING/REVERTING/PERMANENT/EXPIRED) resolve through their
         * {@code …phase.<name>} lang keys; author-defined sequence step names have no
         * lang key and are shown verbatim (they are server-side authoring data and
         * deliberately not localized — previously they fell through to a raw
         * untranslated key string).
         *
         * @param phaseName the streamed phase/step name
         * @return the translated component, or the raw name when no lang key exists
         */
        public static Component phase(String phaseName) {
            String key = PREFIX + "phase." + phaseName.toLowerCase(Locale.ROOT);
            return I18n.exists(key) ? Component.translatable(key) : Component.literal(phaseName);
        }

        /**
         * The "phase i of n" fragment of the Active-tab step progress line (T-100,
         * plan §6). The numerator is already 1-based here.
         *
         * @param stepNumber 1-based index of the running step
         * @param stepCount  total steps of the event's resolved sequence
         */
        public static Component stepProgress(int stepNumber, int stepCount) {
            return Component.translatable(PREFIX + "active_step_progress",
                    String.valueOf(stepNumber), String.valueOf(stepCount));
        }

        public static Component remaining(String formattedTime) {
            return Component.translatable(PREFIX + "remaining", formattedTime);
        }

        public static Component eventId(String eventId) {
            return Component.translatable(PREFIX + "event_id", eventId);
        }

        public static Component unknownMarket(short marketId) {
            return Component.translatable(PREFIX + "unknown_market", String.valueOf(marketId));
        }

        public static Component markerCooldown(String formattedTime) {
            return Component.translatable(PREFIX + "marker_cooldown", formattedTime);
        }

        public static Component upcomingSlot(int slotNumber, String eta, String eventName) {
            return Component.translatable(PREFIX + "upcoming_slot",
                    String.valueOf(slotNumber), eta, eventName);
        }

        public static Component selectedMarket(String marketName) {
            return Component.translatable(PREFIX + "selected_market", marketName);
        }

        public static Component confirmTriggerMessage(String formattedCooldown, String eventName) {
            return Component.translatable(PREFIX + "confirm_trigger_message",
                    formattedCooldown, eventName);
        }

        public static Component detailsEnabled(String yesNo) {
            return Component.translatable(PREFIX + "details_enabled", yesNo);
        }

        public static Component detailsAdminOnly(String yesNo) {
            return Component.translatable(PREFIX + "details_admin_only", yesNo);
        }

        public static Component detailsWeight(String weight) {
            return Component.translatable(PREFIX + "details_weight", weight);
        }

        public static Component detailsCooldownRemaining(String formattedTime) {
            return Component.translatable(PREFIX + "details_cooldown_remaining", formattedTime);
        }

        public static Component detailsAnnounceDelay(String minMs, String maxMs) {
            return Component.translatable(PREFIX + "details_announce_delay", minMs, maxMs);
        }

        public static Component detailsPeak(String peakPercent, String peakFactor) {
            return Component.translatable(PREFIX + "details_peak", peakPercent, peakFactor);
        }

        public static Component detailsRampUp(String seconds) {
            return Component.translatable(PREFIX + "details_ramp_up", seconds);
        }

        public static Component detailsHold(String seconds) {
            return Component.translatable(PREFIX + "details_hold", seconds);
        }

        public static Component detailsReversal(String mode, String seconds) {
            return Component.translatable(PREFIX + "details_reversal", mode, seconds);
        }

        public static Component detailsMarketsTitle(int count) {
            return Component.translatable(PREFIX + "details_markets_title", String.valueOf(count));
        }

        public static Component detailsLive(String phase, String remaining) {
            return Component.translatable(PREFIX + "details_live", phase, remaining);
        }

        public static Component detailsWeightFactor(String weightFactor) {
            return Component.translatable(PREFIX + "details_weight_factor", weightFactor);
        }

        /**
         * Header of one sequence in the details breakdown (T-100) — single-sequence
         * form without the pick chance (a lone sequence always fires).
         */
        public static Component detailsSequenceHeader(String name, int stepCount) {
            return Component.translatable(PREFIX + "details_sequence_header",
                    name, String.valueOf(stepCount));
        }

        /**
         * Header of one sequence in the details breakdown (T-100) — multi-sequence
         * form including the weighted pick chance (percentage of the weight total).
         */
        public static Component detailsSequenceHeaderChance(String name, int stepCount,
                                                            String chancePercent) {
            return Component.translatable(PREFIX + "details_sequence_header_chance",
                    name, String.valueOf(stepCount), chancePercent);
        }

        /** Indented per-step market-override line (T-100; arg = joined "name ×w" list). */
        public static Component detailsStepMarkets(String joinedMarkets) {
            return Component.translatable(PREFIX + "details_step_markets", joinedMarkets);
        }

        /** @return the localized yes/no value string */
        public static String yesNo(boolean value) {
            return (value ? VALUE_YES : VALUE_NO).getString();
        }
    }

    // ── Palette (T-085: brightened for readability on the 0xAA888888 widget
    // background — the previous darker tones were hard to read; semantics kept:
    // green = positive/enabled, red = negative/disabled, orange = pending/override).
    // Package-visible so NewsEventDetailsScreen renders with identical colors.
    static final int COLOR_UP_GREEN = 0xFF6BE886;
    static final int COLOR_DOWN_RED = 0xFFFF7A70;
    static final int COLOR_NEUTRAL_GRAY = 0xFFD4D4D4;
    static final int COLOR_PENDING_ORANGE = 0xFFFFB84D;
    /** Status label colors for successful / failed admin operation responses. */
    static final int COLOR_STATUS_OK = 0xFFA8F0A8;
    static final int COLOR_STATUS_ERROR = 0xFFFF9C94;
    /** Near-white for secondary text (news body text, market names). */
    static final int COLOR_TEXT_SECONDARY = 0xFFEDEDED;

    /** Shared meta font scale for secondary text rows. */
    static final float META_SCALE = 0.8f;

    // ── T-100: requirement met/unmet line prefixes ────────────────────────
    // Plain unicode symbols (always renderable via the vanilla unifont fallback);
    // deliberately not lang keys — they are state markers, not words. Shared with
    // NewsEventDetailsScreen so the details list and the confirm dialog match.
    /** Prefix of a met trigger-requirement line. */
    static final String REQ_MET_PREFIX = "✔ ";   // ✔
    /** Prefix of an unmet trigger-requirement line. */
    static final String REQ_UNMET_PREFIX = "✘ "; // ✘

    /**
     * Phase label colors keyed by the streamed phase name (see RuntimeStreamData codec).
     * T-085: brightened (the old violet/dark-blue tones were hard to read on gray).
     */
    static int phaseColor(String phaseName) {
        return switch (phaseName) {
            case "RAMPING" -> 0xFFFFC266;
            case "HOLDING" -> 0xFF7FBFFF;
            case "REVERTING" -> 0xFFCFA0FF;
            case "PERMANENT" -> COLOR_UP_GREEN;
            default -> COLOR_NEUTRAL_GRAY; // PENDING, EXPIRED, unknown
        };
    }

    // ── Elements ─────────────────────────────────────────────────────────
    private final Button reloadButton;
    private final Button stopAllButton;
    private final Label adminStatusLabel;
    private final VerticalListView eventListView;
    private final Label emptyLabel;
    private final TabElement tabElement;
    private final ActiveEventsTab activeEventsTab;
    private final AllEventsTab allEventsTab;
    private final SchedulerTab schedulerTab;
    private final Label settingsTitle;
    private final Label selectedMarketLabel;
    /**
     * Visual group around the per-market editor controls (T-086): makes it obvious
     * that Apply commits exactly the two settings inside the frame. The checkbox,
     * sensitivity row and Apply button are children of this frame.
     */
    private final Frame settingsFrame;
    private final CheckBox newsEnabledCheckBox;
    private final Label sensitivityLabel;
    private final TextBox sensitivityTextBox;
    private final Button applyButton;
    private final ItemSelectionView marketSelectionView;
    /** Small price-history chart of the selected market (bottom right, T-085). */
    private final CandlestickChart marketChart;

    // ── State ────────────────────────────────────────────────────────────
    /** ItemID lookup by short id, rebuilt from the plugin's subscribed markets. */
    private final Map<Short, ItemID> subscribedMarketsByShort = new HashMap<>();
    /** Decoded per-market custom settings, kept in sync with server confirmations. */
    private Map<ItemID, NewsPlugin.Settings> allSettings = new HashMap<>();
    /** The event panels currently shown in the active list (children of eventListView). */
    private final List<ActiveEventPanel> eventPanels = new ArrayList<>();
    /** The latest runtime snapshot; empty until the stream delivers data. */
    private NewsPlugin.RuntimeStreamData lastRuntimeData = new NewsPlugin.RuntimeStreamData(new ArrayList<>());
    private @Nullable ItemID selectedMarketID;
    /** The ClientMarket the chart is currently subscribed to, or null. */
    private @Nullable ClientMarket chartMarket;
    /**
     * The latest server-confirmed per-definition snapshot (T-081), refreshed from every
     * successful {@code NewsAdminRequest} response. Source of truth for the all-events
     * list, the details screen and the timeline headline resolution.
     */
    private final List<NewsAdminRequest.EventDetails> allEventDetails = new ArrayList<>();
    /** {@link #allEventDetails} indexed by definition id (insertion = file order). */
    private final Map<String, NewsAdminRequest.EventDetails> detailsById = new LinkedHashMap<>();
    /**
     * Client-side cooldown end wall-clock for each definition id, in
     * {@link System#currentTimeMillis()} space (T-107). Filled from every incoming
     * {@link NewsAdminRequest.EventDetails} snapshot as
     * {@code clientNow + details.cooldownRemainingMs()}, so the all-events tab can
     * derive a live remaining value at render time without firing any extra admin
     * ops per tick. Stale entries for events no longer in the snapshot are left
     * alone — they simply tick to zero and get overwritten when the next snapshot
     * mentions the id again.
     */
    private final Map<String, Long> cooldownEndByEventId = new HashMap<>();

    /**
     * Creates the news plugin GUI element. Market data and settings are populated
     * later via {@link #onPluginSyncDataReceived(PluginSyncData, Map)}.
     */
    public NewsPluginGuiElement() {
        // Admin actions (T-076/T-085): Reload + Stop-all live in the header row;
        // triggering moved to the per-row buttons of the All-events tab. Both
        // buttons go through the shared NewsAdminRequest (server-side admin gating).
        reloadButton = new Button(Texts.RELOAD.getString(), this::onReloadClicked);
        stopAllButton = new Button(Texts.STOP_ALL.getString(), this::onStopAllClicked);

        adminStatusLabel = new Label("");
        adminStatusLabel.setTextColor(COLOR_NEUTRAL_GRAY);

        eventListView = new VerticalListView();
        LayoutVertical eventLayout = new LayoutVertical(0, spacing, true, false);
        eventListView.setLayout(eventLayout);

        emptyLabel = new Label(Texts.NO_ACTIVE_EVENTS.getString());
        emptyLabel.setAlignment(Label.Alignment.CENTER);
        emptyLabel.setTextColor(COLOR_NEUTRAL_GRAY);

        // T-083: tabbed left column — Active (live events), All events (definitions
        // + enable checkboxes + trigger/reset-cooldown), Scheduler (overrides +
        // upcoming timeline). Tabs keep the dense window navigable.
        activeEventsTab = new ActiveEventsTab();
        allEventsTab = new AllEventsTab();
        schedulerTab = new SchedulerTab();
        tabElement = new TabElement();
        tabElement.addTab(Texts.TAB_ACTIVE.getString(), activeEventsTab);
        tabElement.addTab(Texts.TAB_ALL_EVENTS.getString(), allEventsTab);
        tabElement.addTab(Texts.TAB_SCHEDULER.getString(), schedulerTab);
        tabElement.setTitleElementHoverTooltipSupplier(index -> switch (index) {
            case 0 -> Texts.ACTIVE_EVENTS_TITLE.getString();
            case 1 -> Texts.ALL_EVENTS_TITLE.getString();
            case 2 -> Texts.SCHEDULER_TITLE.getString();
            default -> null;
        });

        // Per-market settings (existing generic custom-settings flow, see class Javadoc)
        settingsTitle = new Label(Texts.SETTINGS_TITLE.getString());
        settingsTitle.setAlignment(Label.Alignment.CENTER);

        // T-085: names the market the editor is currently bound to — selecting an item
        // below visibly binds the editor (the user could not tell before).
        selectedMarketLabel = new Label(Texts.NO_MARKET_SELECTED.getString());
        selectedMarketLabel.setAlignment(Label.Alignment.CENTER);
        selectedMarketLabel.setTextColor(COLOR_NEUTRAL_GRAY);

        newsEnabledCheckBox = new CheckBox(Texts.NEWS_ENABLED.getString());
        newsEnabledCheckBox.setChecked(NewsPlugin.DEFAULT_NEWS_ENABLED);
        // T-085: explain what the setting gates (verified against NewsPlugin: a
        // news-disabled market is excluded from eligibility AND from active impacts).
        // The editor sits at the right window edge — anchor the tooltip's top-RIGHT
        // corner at the mouse so it expands leftwards and cannot clip outside.
        setupRightAnchoredTooltip(newsEnabledCheckBox, Texts.NEWS_ENABLED_TOOLTIP);

        sensitivityLabel = new Label(Texts.SENSITIVITY.getString());
        sensitivityLabel.setAlignment(Label.Alignment.RIGHT);
        sensitivityTextBox = new TextBox();
        // Negative sensitivity is allowed (inverts impacts, like a negative weightFactor)
        sensitivityTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, true, 10, 6));
        sensitivityTextBox.setText(NewsPlugin.DEFAULT_SENSITIVITY);
        setupRightAnchoredTooltip(sensitivityTextBox, Texts.SENSITIVITY_TOOLTIP);

        applyButton = new Button(Texts.APPLY.getString(), this::onApplySettings);

        // T-086: group the editor controls in an outlined frame so it reads as one
        // unit — Apply commits exactly the checkbox + sensitivity inside the frame.
        settingsFrame = new Frame();
        settingsFrame.setEnableBackground(false);
        settingsFrame.setEnableOutline(true);
        settingsFrame.addChild(newsEnabledCheckBox);
        settingsFrame.addChild(sensitivityLabel);
        settingsFrame.addChild(sensitivityTextBox);
        settingsFrame.addChild(applyButton);

        marketSelectionView = new ItemSelectionView(this::onMarketSelected);

        // T-085: small live price chart of the selected market. Reuses the TradeScreen
        // plumbing: ClientMarket price stream subscription + CandlestickChart.setMarket.
        marketChart = new CandlestickChart();
        marketChart.setMarket(null);

        addChild(reloadButton);
        addChild(stopAllButton);
        addChild(adminStatusLabel);
        addChild(tabElement);
        addChild(settingsTitle);
        addChild(selectedMarketLabel);
        addChild(settingsFrame); // holds checkbox, sensitivity row and Apply (T-086)
        addChild(marketSelectionView);
        addChild(marketChart);
    }

    /** Attaches a right-anchored hover tooltip (expands leftwards, never clips). */
    private static void setupRightAnchoredTooltip(
            net.kroia.modutilities.gui.elements.base.GuiElement element, Component tooltip) {
        element.setHoverTooltipSupplier(tooltip::getString);
        element.setHoverTooltipFontScale(hoverToolTipFontSize);
        element.setHoverTooltipMousePositionAlignment(Alignment.TOP_RIGHT);
    }

    @Override
    public boolean needsCustomScreen() {
        return true;
    }

    @Override
    protected StreamCodec<ByteBuf, NewsPlugin.Settings> customSettingsCodec() {
        return NewsPlugin.Settings.CODEC;
    }

    @Override
    protected StreamCodec<ByteBuf, NewsPlugin.RuntimeStreamData> runtimeDataCodec() {
        return NewsPlugin.RuntimeStreamData.CODEC;
    }

    /**
     * Populates the market selection view and the short→ItemID lookup from the
     * plugin's sync data, stores the per-market settings and starts the runtime
     * data stream for live event updates.
     *
     * @param data              the plugin sync data containing subscribed markets
     * @param customSettingsMap the decoded per-market news settings, or null
     */
    @Override
    protected void onPluginSyncDataReceived(PluginSyncData data, @Nullable Map<ItemID, NewsPlugin.Settings> customSettingsMap) {
        subscribedMarketsByShort.clear();
        List<ItemID> sortedMarkets = new ArrayList<>(data.getSubscribedMarkets());
        sortedMarkets.sort(MARKET_TYPE_COMPARATOR);
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemID id : sortedMarkets) {
            subscribedMarketsByShort.put(id.getShort(), id);
            ItemStack stack = id.getStack();
            if (stack != null) {
                stacks.add(stack);
            }
        }
        marketSelectionView.setItems(stacks);

        if (customSettingsMap != null) {
            allSettings = new HashMap<>(customSettingsMap);
        }
        if (selectedMarketID != null) {
            bindSelectedMarket(selectedMarketID);
        }

        // Live active-event updates while the GUI is visible; the hosting screens
        // stop/restart the stream (see class Javadoc).
        startDataStream();

        // Populate the all-events tab via a LIST round-trip (T-076/T-083). The op is
        // admin-gated server-side: non-admins get an error status and empty lists,
        // which is fine — every other admin operation would be rejected server-side
        // for them anyway. NOT user-initiated (T-086 bug fix): sync data arrives for
        // every entry of the plugin management list right when that screen opens, so
        // this background fetch must never pop up a result dialog.
        sendAdminRequest(new NewsAdminRequest.InputData(
                NewsAdminRequest.Op.LIST, "", "", null), false, false);
    }

    /**
     * Keeps the settings editor in sync when the management screen changes the
     * active market (inline mode); the dedicated screen uses its own selector.
     */
    @Override
    protected void onActiveMarketChanged(@Nullable ItemID marketID) {
        if (marketID != null) {
            bindSelectedMarket(marketID);
        }
    }

    /**
     * Stops the runtime data stream <b>and</b> releases the selected-market chart's
     * price stream subscription (T-085). Called by the hosting screens on close /
     * entry cleanup — the chart must never keep a market price stream alive after
     * the window is gone.
     */
    @Override
    public void stopDataStream() {
        super.stopDataStream();
        releaseChartMarket();
    }

    /**
     * Stores the latest runtime snapshot, rebuilds the active-event list and pushes
     * the effective scheduler values into the scheduler tab (T-082 data). The upcoming
     * timeline reads {@link #lastRuntimeData} directly at render time, so it needs no
     * explicit refresh. An empty event list clears all previously shown events
     * (streamed deliberately by the server when the last event retires).
     *
     * @param data the decoded runtime snapshot
     */
    @Override
    protected void onRuntimeDataReceived(NewsPlugin.RuntimeStreamData data) {
        lastRuntimeData = data != null ? data : new NewsPlugin.RuntimeStreamData(new ArrayList<>());
        rebuildEventList();
        schedulerTab.onSchedulerState(lastRuntimeData.scheduler());
    }

    @Override
    protected void onCustomSettingsResponse(boolean success, @Nullable ItemID marketID, @Nullable NewsPlugin.Settings confirmedSettings) {
        if (success && marketID != null && confirmedSettings != null) {
            allSettings.put(marketID, confirmedSettings);
        }
    }

    // ── Market selection + chart (T-085) ─────────────────────────────────

    /**
     * Called when a market item is selected from the ItemSelectionView.
     * Resolves the ItemID and binds the editor + chart to that market.
     *
     * @param item the selected item stack
     */
    private void onMarketSelected(ItemStack item) {
        ItemID.getOrRegisterFromItemStackClientSide(item).thenAccept(this::bindSelectedMarket);
    }

    /**
     * Binds the per-market editor to the given market: populates the settings fields,
     * names the market in the selected-market label and switches the price chart to
     * its live data (T-085).
     *
     * @param marketID the market to bind; null clears label and chart
     */
    private void bindSelectedMarket(@Nullable ItemID marketID) {
        this.selectedMarketID = marketID;
        if (marketID == null) {
            selectedMarketLabel.setText(Texts.NO_MARKET_SELECTED.getString());
            selectedMarketLabel.setTextColor(COLOR_NEUTRAL_GRAY);
            releaseChartMarket();
            return;
        }
        populateSettingsFromMarket(marketID);
        ItemStack stack = resolveMarketStack(marketID.getShort(), "");
        selectedMarketLabel.setText(
                Texts.selectedMarket(stack.getHoverName().getString()).getString());
        selectedMarketLabel.setTextColor(COLOR_TEXT_SECONDARY);
        bindChartMarket(marketID);
    }

    /**
     * Points the price chart at the given market: subscribes its price-update stream
     * (requests the full history first — same plumbing as TradeScreen) and releases
     * the previously charted market's subscription.
     */
    private void bindChartMarket(ItemID marketID) {
        ClientMarket market = getMarket(marketID);
        if (market == chartMarket) return; // unchanged (incl. both null)
        releaseChartMarket();
        if (market != null) {
            market.subscribeToMarketPriceUpdate();
            chartMarket = market;
        }
        marketChart.setMarket(market);
    }

    /** Unsubscribes the charted market's price stream and clears the chart. */
    private void releaseChartMarket() {
        if (chartMarket != null) {
            chartMarket.unsubscribeFromMarketPriceUpdate();
            chartMarket = null;
        }
        marketChart.setMarket(null);
    }

    /**
     * Fills the settings controls from the stored settings for the given market
     * (defaults when the market has no explicit settings yet).
     *
     * @param marketID the market whose settings should be displayed
     */
    private void populateSettingsFromMarket(ItemID marketID) {
        NewsPlugin.Settings settings = allSettings.get(marketID);
        if (settings == null) {
            settings = NewsPlugin.Settings.createDefault();
        }
        newsEnabledCheckBox.setChecked(settings.newsEnabled());
        sensitivityTextBox.setText(settings.sensitivity());
    }

    /**
     * Sends the edited news settings for the selected market to the server via
     * the existing generic custom-settings flow.
     */
    private void onApplySettings() {
        if (selectedMarketID == null) return;
        sendCustomSettings(selectedMarketID, new NewsPlugin.Settings(
                newsEnabledCheckBox.isChecked(),
                (float) sensitivityTextBox.getDouble()));
    }

    // ── Admin actions (T-076/T-081/T-082/T-085, backed by NewsAdminRequest) ──

    /**
     * Reload: reloads the JSON definition library. The one-line summary goes to the
     * status label; the full validation report lines are printed to the client chat
     * (readable after closing the screen — this is admin feedback, not a news publish).
     */
    private void onReloadClicked() {
        sendAdminRequest(new NewsAdminRequest.InputData(
                NewsAdminRequest.Op.RELOAD, "", "", null), true, true);
    }

    /**
     * Stop all: hard-stops every active event (T-093 semantics — influence removed in
     * any phase, full cooldowns restarted; audited server-side).
     */
    private void onStopAllClicked() {
        sendAdminRequest(new NewsAdminRequest.InputData(
                NewsAdminRequest.Op.STOP, NewsAdminRequest.STOP_ALL, "", null), false, true);
    }

    /**
     * Per-row Trigger (T-085/T-100): fires the definition on all its matched markets.
     * A confirmation dialog is shown first when
     * <ul>
     *   <li>the event's cooldown is still running (T-085 — the server-side TRIGGER
     *       deliberately bypasses cooldowns as an admin override, so a confirmed
     *       trigger needs no extra force flag; disabled events are still refused
     *       server-side), and/or</li>
     *   <li>the snapshot reports unmet trigger requirements (T-100, plan §10.1 —
     *       the server-side TRIGGER bypasses {@code requires[]} too; the dialog
     *       lists the unmet requirement descriptions).</li>
     * </ul>
     * When both apply, ONE combined dialog lists both warnings (never two chained
     * popups). The requirement data comes from the freshest cached
     * {@link NewsAdminRequest.EventDetails} snapshot for the event (snapshots ride
     * along on every admin response); without a cached snapshot the trigger proceeds
     * directly — the popup is best-effort client UX, the server does not block on
     * requirements anyway.
     *
     * @param details the row's server-confirmed definition snapshot
     */
    private void requestTrigger(NewsAdminRequest.EventDetails details) {
        // Prefer the freshest cached snapshot over the (possibly older) row snapshot.
        NewsAdminRequest.EventDetails fresh = detailsFor(details.id());
        if (fresh == null) {
            fresh = details;
        }
        boolean cooldownRunning = fresh.cooldownRemainingMs() > 0;
        List<String> unmet = new ArrayList<>();
        for (NewsAdminRequest.EventDetails.RequirementStatus requirement : fresh.requirements()) {
            if (!requirement.met()) {
                unmet.add(REQ_UNMET_PREFIX + requirement.description());
            }
        }
        if (!cooldownRunning && unmet.isEmpty()) {
            sendTrigger(fresh.id());
            return;
        }

        // One combined dialog: cooldown warning first, then the unmet requirements.
        List<String> lines = new ArrayList<>();
        if (cooldownRunning) {
            lines.add(Texts.confirmTriggerMessage(
                    NewsUiFormatting.formatRemainingTime(fresh.cooldownRemainingMs()),
                    resolveHeadline(fresh)).getString());
        }
        if (!unmet.isEmpty()) {
            lines.add(Texts.CONFIRM_TRIGGER_REQUIREMENTS.getString());
            lines.addAll(unmet);
        }
        lines.add(Texts.eventId(fresh.id()).getString());
        // The requirement warning is the stronger one — it titles the combined case.
        Component title = unmet.isEmpty()
                ? Texts.CONFIRM_TRIGGER_TITLE : Texts.CONFIRM_TRIGGER_REQUIREMENTS_TITLE;
        String eventId = fresh.id();
        Screen current = Minecraft.getInstance().screen;
        GuiScreen.setScreen(NewsDialogScreen.confirmDialog(current, title, lines,
                Texts.CONFIRM_TRIGGER_YES, () -> sendTrigger(eventId)));
    }

    /** Sends the TRIGGER op for one definition id (audited server-side). */
    private void sendTrigger(String eventId) {
        sendAdminRequest(new NewsAdminRequest.InputData(
                NewsAdminRequest.Op.TRIGGER, eventId, "", null), false, true);
    }

    /**
     * Per-row Reset-cooldown (T-085): clears the event's remaining activation cooldown
     * on the master ({@code RESET_COOLDOWN}, audited server-side when a cooldown was
     * actually cleared).
     *
     * @param details the row's server-confirmed definition snapshot
     */
    private void requestResetCooldown(NewsAdminRequest.EventDetails details) {
        sendAdminRequest(new NewsAdminRequest.InputData(
                NewsAdminRequest.Op.RESET_COOLDOWN, details.id(), "", null), false, true);
    }

    /**
     * Per-event Stop (T-093, Active tab rows): hard-stops one active event on the master
     * ({@code STOP_EVENT}, audited server-side) — the event ends in any phase, its price
     * influence is removed and its full cooldown restarts; {@code reversal:none} events
     * are cancelled without baking their permanent shift.
     *
     * @param eventId the active event's definition id
     */
    private void requestStopEvent(String eventId) {
        sendAdminRequest(new NewsAdminRequest.InputData(
                NewsAdminRequest.Op.STOP_EVENT, eventId, "", null), false, true);
    }

    /**
     * Per-event Skip phase (T-093, Active tab rows): fast-forwards one active event to
     * the start of its next phase on the master ({@code SKIP_PHASE}, audited
     * server-side). Skipping the last phase ends the event normally.
     *
     * @param eventId the active event's definition id
     */
    private void requestSkipPhase(String eventId) {
        sendAdminRequest(new NewsAdminRequest.InputData(
                NewsAdminRequest.Op.SKIP_PHASE, eventId, "", null), false, true);
    }

    /**
     * Sends one admin operation and routes the response into the status label (and
     * optionally the client chat). Runs entirely on the client main thread — ARRS
     * response futures complete in the client packet handler.
     * <p>
     * Every <b>successful</b> response carries the structured
     * {@link NewsAdminRequest.EventDetails} snapshot — consumed here, keeping the
     * all-events tab and an open details screen server-confirmed after <b>any</b>
     * operation (T-081 contract). SET_SCHEDULER responses additionally resolve the
     * scheduler tab's pending edits: success clears the edit flags (the 500 ms stream
     * re-displays the confirmed values), failure restores the last confirmed values
     * immediately.
     * <p>
     * <b>Long results</b> (T-085): the status label shows a truncated one-liner; when
     * the full message does not fit — or a Stop-all reports multiple per-event outcome
     * lines — an OK dialog with the complete text opens on top (only if the user is
     * still on the same screen the operation was started from).
     * <p>
     * <b>Dialogs are user-initiated only</b> (T-086 bug fix): background fetches (the
     * LIST on plugin sync data) run while the status label is not even laid out yet
     * (width 0), so their message always counts as "truncated" — opening a screen must
     * never surface a result dialog. Only operations the user explicitly clicked may.
     *
     * @param input            the operation payload (commandExecutor stays null: the GUI is
     *                         client-originated, the transport supplies the player identity)
     * @param printLinesToChat true to additionally print the response detail lines to chat
     * @param userInitiated    true when the user explicitly triggered this operation
     *                         (button/checkbox click) — only then may an overflow
     *                         result dialog open; background refreshes pass false
     */
    private void sendAdminRequest(NewsAdminRequest.InputData input, boolean printLinesToChat,
                                  boolean userInitiated) {
        final Screen screenAtSend = Minecraft.getInstance().screen;
        BACKEND_INSTANCES.NETWORKING.NEWS_ADMIN_REQUEST.sendRequestToServer(input)
                .thenAccept(response -> {
                    if (response == null) {
                        showStatus(Texts.REQUEST_FAILED.getString(), false);
                        if (input.op() == NewsAdminRequest.Op.SET_SCHEDULER) {
                            schedulerTab.restoreConfirmedValues();
                        }
                        return;
                    }
                    boolean truncated = showStatus(response.message(), response.success());
                    if (printLinesToChat) {
                        Minecraft mc = Minecraft.getInstance();
                        mc.gui.getChat().addMessage(Component.literal(response.message()));
                        for (String line : response.lines()) {
                            mc.gui.getChat().addMessage(Component.literal(line));
                        }
                    }
                    // Every successful response carries the structured details
                    // snapshot — keeps the all-events tab and the details screen in
                    // sync after any operation.
                    if (response.success()) {
                        refreshEventDetails(response.details());
                    }
                    if (input.op() == NewsAdminRequest.Op.SET_SCHEDULER) {
                        if (response.success()) {
                            schedulerTab.clearEditedFlags();
                        } else {
                            // Server rejected atomically — nothing changed; re-display
                            // the last confirmed values (T-082 contract).
                            schedulerTab.restoreConfirmedValues();
                        }
                    }
                    // T-085: full text in an OK dialog when the one-liner is not the
                    // whole story. Chat-printed responses (Reload) already carry their
                    // lines to a persistent place, so only the message overflow counts.
                    // T-086: never for background operations (see method Javadoc).
                    // T-108: per-event Skip-phase and Stop-event are silent on success —
                    // the Active-row updates visibly (event advances or disappears), so
                    // the popup was just noise. Failures still surface via the popup
                    // (permission refused, AlreadyStopped, slave routing lag, …).
                    // The top-level Stop-all (Op.STOP) status dialog is untouched — it
                    // summarizes cross-event outcomes and is genuinely useful.
                    boolean multiLineStop = input.op() == NewsAdminRequest.Op.STOP
                            && response.lines().size() > 1;
                    boolean silentPerEventOnSuccess = response.success()
                            && (input.op() == NewsAdminRequest.Op.STOP_EVENT
                                || input.op() == NewsAdminRequest.Op.SKIP_PHASE);
                    if (userInitiated && (truncated || multiLineStop)
                            && !silentPerEventOnSuccess
                            && Minecraft.getInstance().screen == screenAtSend
                            && screenAtSend != null) {
                        List<String> dialogLines = new ArrayList<>();
                        dialogLines.add(response.message());
                        if (!printLinesToChat) {
                            dialogLines.addAll(response.lines());
                        }
                        GuiScreen.setScreen(NewsDialogScreen.okDialog(screenAtSend,
                                Texts.STATUS_DIALOG_TITLE, dialogLines));
                    }
                });
    }

    /**
     * Shows one admin operation result in the status label (green = ok, red = failed),
     * truncating it to the label width.
     *
     * @return true if the message had to be truncated (full text should go to a dialog)
     */
    private boolean showStatus(String message, boolean success) {
        int available = Math.max(0, adminStatusLabel.getWidth() - 2 * adminStatusLabel.getPadding());
        String shown = NewsUiText.truncate(adminStatusLabel, message, available, 1.0f);
        adminStatusLabel.setText(shown);
        adminStatusLabel.setTextColor(success ? COLOR_STATUS_OK : COLOR_STATUS_ERROR);
        return !shown.equals(message);
    }

    /**
     * Stores the server-confirmed per-definition snapshot and refreshes every consumer:
     * the all-events tab rows (checkbox state comes exclusively from here) and — if
     * currently open — the details screen (T-085).
     * <p>
     * Also refreshes {@link #cooldownEndByEventId} (T-107) so the all-events tab
     * can compute a live remaining cooldown between admin responses. Only ids
     * present in the incoming snapshot are updated; stale entries for events that
     * dropped out of the snapshot are harmless — they tick to zero.
     *
     * @param details the snapshot from a successful admin response, in file order
     */
    private void refreshEventDetails(List<NewsAdminRequest.EventDetails> details) {
        allEventDetails.clear();
        allEventDetails.addAll(details);
        detailsById.clear();
        long now = System.currentTimeMillis();
        for (NewsAdminRequest.EventDetails d : details) {
            detailsById.put(d.id(), d);
            // T-107: cache the wall-clock deadline for the live per-row countdown.
            cooldownEndByEventId.put(d.id(), now + d.cooldownRemainingMs());
        }
        allEventsTab.rebuildRows();
        if (Minecraft.getInstance().screen instanceof NewsEventDetailsScreen detailsScreen) {
            detailsScreen.onSnapshotRefreshed();
        }
    }

    // ── Locale / lookup helpers ──────────────────────────────────────────

    /** @return the client's current language code (resolved at call time, see NewsTranslations) */
    static String clientLanguage() {
        return Minecraft.getInstance().options.languageCode;
    }

    /**
     * Resolves the locale display headline of a definition (exact language →
     * {@code en_us} → first entry), falling back to the definition id when the
     * definition ships no headline at all.
     */
    static String resolveHeadline(NewsAdminRequest.EventDetails details) {
        String headline = NewsTranslations.resolve(details.headline(), clientLanguage());
        return headline.isEmpty() ? details.id() : headline;
    }

    /**
     * Resolves the display name of an upcoming-timeline slot: the locale headline of
     * the planned definition, the raw id when the definition is not (yet) in the
     * snapshot, or the "picked at trigger time" wording for time-only slots.
     */
    private String resolveUpcomingName(String eventId) {
        if (eventId.isEmpty()) {
            return Texts.UPCOMING_TIME_ONLY.getString();
        }
        NewsAdminRequest.EventDetails details = detailsById.get(eventId);
        return details != null ? resolveHeadline(details) : eventId;
    }

    /**
     * The streamed live info of the given definition, or null while not active.
     * Package-visible: {@link NewsEventDetailsScreen} draws its live phase line from
     * this element's stream data (the stream keeps running while the child screen is
     * open, see class Javadoc).
     */
    NewsPlugin.RuntimeStreamData.@Nullable ActiveEventInfo liveInfoFor(String eventId) {
        for (NewsPlugin.RuntimeStreamData.ActiveEventInfo event : lastRuntimeData.events()) {
            if (event.eventId().equals(eventId)) {
                return event;
            }
        }
        return null;
    }

    /** @return true if the runtime stream currently reports the definition as active */
    private boolean isEventActiveLive(String eventId) {
        return liveInfoFor(eventId) != null;
    }

    /**
     * The latest server-confirmed details of one definition id, or null when it is
     * not (any longer) part of the snapshot. Package-visible for
     * {@link NewsEventDetailsScreen} refreshes.
     */
    @Nullable NewsAdminRequest.EventDetails detailsFor(String eventId) {
        return detailsById.get(eventId);
    }

    /**
     * Live remaining cooldown (T-107): derives {@code cooldownEnd - now} from the
     * client-side {@link #cooldownEndByEventId} map that was cached at the last
     * snapshot arrival. Returns 0 when the deadline has passed or when no snapshot
     * has ever cached this id — callers should treat 0 as "off cooldown".
     *
     * @param eventId the definition id
     * @return remaining milliseconds, clamped to a non-negative value
     */
    private long liveCooldownRemainingMs(String eventId) {
        Long endMs = cooldownEndByEventId.get(eventId);
        if (endMs == null) return 0L;
        return Math.max(0L, endMs - System.currentTimeMillis());
    }

    /**
     * Resolves a market's display ItemStack from the streamed short id, falling back
     * to a named barrier placeholder for unresolvable markets (NewsEntryPanel pattern).
     * Package-visible: the details screen renders the same impact rows.
     *
     * @param marketId    the market's ItemID short
     * @param displayName pre-captured server-side fallback name (registry name)
     * @return the display stack, never null/empty
     */
    ItemStack resolveMarketStack(short marketId, String displayName) {
        ItemID itemID = subscribedMarketsByShort.get(marketId);
        ItemStack stack = itemID != null ? itemID.getStack() : null;
        if (stack == null || stack.isEmpty()) {
            String name = displayName.isEmpty()
                    ? Texts.unknownMarket(marketId).getString() : displayName;
            stack = new ItemStack(Items.BARRIER);
            stack.set(DataComponents.CUSTOM_NAME,
                    Component.literal(name).withStyle(style -> style.withItalic(false)));
        }
        return stack;
    }

    // ── Details screen (T-085, replaces the T-083 overlay popup) ─────────

    /**
     * Opens the {@link NewsEventDetailsScreen} for one definition as a child screen of
     * the currently active screen — ESC returns to this window with its full state
     * (selected tab, scroll positions) intact, because the parent screen <b>instance</b>
     * is preserved (ModSettingsScreen precedent).
     *
     * @param details the server-confirmed definition snapshot to show
     */
    private void openEventDetailsScreen(NewsAdminRequest.EventDetails details) {
        Screen current = Minecraft.getInstance().screen;
        GuiScreen.setScreen(new NewsEventDetailsScreen(current, this, details));
    }

    // ── Event list ───────────────────────────────────────────────────────

    /**
     * Rebuilds the active-event panels from {@link #lastRuntimeData}, preserving the
     * scroll position, and toggles the empty-state label.
     */
    private void rebuildEventList() {
        int savedScroll = eventListView.getScrollOffset();
        for (ActiveEventPanel panel : eventPanels) {
            eventListView.removeChild(panel);
        }
        eventPanels.clear();

        for (NewsPlugin.RuntimeStreamData.ActiveEventInfo event : lastRuntimeData.events()) {
            ActiveEventPanel panel = new ActiveEventPanel(event);
            eventPanels.add(panel);
            eventListView.addChild(panel);
        }
        eventListView.setScrollOffset(savedScroll);
        emptyLabel.setEnabled(eventPanels.isEmpty());
    }

    @Override
    protected void layoutChanged() {
        // Re-bind the chart when the window is re-opened after a close released the
        // price stream (stopDataStream) while a market selection is still stored.
        // layoutChanged only runs while this element is hosted (PluginDetailScreen),
        // so the stream never restarts invisibly from the management list.
        if (selectedMarketID != null && chartMarket == null) {
            bindChartMarket(selectedMarketID);
        }

        int w = getWidth();
        int h = getHeight();
        int eh = defaultElementHeight;

        // Right column: per-market settings + market selection + chart
        int rightW = Math.max(120, w * 35 / 100);
        int leftW = w - rightW - spacing;
        int rightX = leftW + spacing;

        // Left column, row 1: admin action buttons + status label (T-085: the trigger
        // picker moved into the All-events rows, freeing the row for the status text).
        int btnW = Math.max(60, leftW * 18 / 100);
        reloadButton.setBounds(0, 0, btnW, eh);
        stopAllButton.setBounds(reloadButton.getRight() + spacing, 0, btnW, eh);
        adminStatusLabel.setBounds(stopAllButton.getRight() + spacing, 0,
                leftW - 2 * btnW - 2 * spacing, eh);

        // Left column, row 2: the tab area (Active / All events / Scheduler)
        tabElement.setBounds(0, reloadButton.getBottom() + spacing,
                leftW, h - reloadButton.getBottom() - spacing);

        // Right column: settings editor on top, market selection in the middle,
        // price chart of the selected market at the bottom (T-085).
        settingsTitle.setBounds(rightX, 0, rightW, eh);
        selectedMarketLabel.setBounds(rightX, settingsTitle.getBottom(), rightW, eh);

        // T-086: the editor controls live inside the outlined settings frame —
        // child coordinates below are relative to the frame.
        int frameH = 2 * padding + 3 * eh + 2 * spacing;
        settingsFrame.setBounds(rightX, selectedMarketLabel.getBottom() + spacing, rightW, frameH);
        int frameInnerW = rightW - 2 * padding;
        newsEnabledCheckBox.setBounds(padding, padding, frameInnerW, eh);
        int sensLabelW = frameInnerW / 2;
        sensitivityLabel.setBounds(padding, newsEnabledCheckBox.getBottom() + spacing, sensLabelW, eh);
        sensitivityTextBox.setBounds(padding + sensLabelW + spacing, sensitivityLabel.getTop(),
                frameInnerW - sensLabelW - spacing, eh);
        applyButton.setBounds(padding, sensitivityTextBox.getBottom() + spacing, frameInnerW, eh);

        int chartH = Math.max(90, h * 30 / 100);
        int chartY = h - chartH;
        marketChart.setBounds(rightX, chartY, rightW, chartH);
        marketSelectionView.setBounds(rightX, settingsFrame.getBottom() + spacing,
                rightW, chartY - spacing - settingsFrame.getBottom() - spacing);
    }

    @Override
    protected void render() {
    }

    // ── Inner containers ─────────────────────────────────────────────────

    /**
     * "Active" tab content: the pre-existing live event list (T-075) plus its
     * empty-state label, hosted inside the tab area. Since T-086 the entries are
     * interactive: market icons select their market in the right-side editor,
     * a click anywhere else on an entry opens its {@link NewsEventDetailsScreen}.
     */
    private class ActiveEventsTab extends StockMarketGuiElement {

        ActiveEventsTab() {
            setEnableBackground(false);
            setEnableOutline(false);
            addChild(eventListView);
            addChild(emptyLabel);
        }

        @Override
        protected void layoutChanged() {
            eventListView.setBounds(0, 0, getWidth(), getHeight());
            // Empty-state label centered over the top of the (empty) list area
            emptyLabel.setBounds(0, 0, getWidth(), defaultElementHeight);
        }

        @Override
        protected void render() {
        }
    }

    /**
     * "All events" tab content (T-083): one {@link EventDefRow} per loaded definition
     * from the server-confirmed {@link NewsAdminRequest.EventDetails} snapshot.
     * Rows are rebuilt from every successful admin response (scroll preserved).
     */
    private class AllEventsTab extends StockMarketGuiElement {

        private final VerticalListView listView = new VerticalListView();
        private final Label noDefinitionsLabel = new Label(Texts.NO_DEFINITIONS.getString());
        private final List<EventDefRow> rows = new ArrayList<>();

        AllEventsTab() {
            setEnableBackground(false);
            setEnableOutline(false);
            listView.setLayout(new LayoutVertical(0, spacing, true, false));
            noDefinitionsLabel.setAlignment(Label.Alignment.CENTER);
            noDefinitionsLabel.setTextColor(COLOR_NEUTRAL_GRAY);
            addChild(listView);
            addChild(noDefinitionsLabel);
        }

        /**
         * Rebuilds the definition rows from {@link #allEventDetails}, preserving the
         * scroll position, and toggles the empty-state label. Checkbox states are
         * taken from the snapshot only — never from optimistic client-side toggles.
         */
        void rebuildRows() {
            int savedScroll = listView.getScrollOffset();
            for (EventDefRow row : rows) {
                listView.removeChild(row);
            }
            rows.clear();
            for (NewsAdminRequest.EventDetails details : allEventDetails) {
                EventDefRow row = new EventDefRow(details);
                rows.add(row);
                listView.addChild(row);
            }
            listView.setScrollOffset(savedScroll);
            noDefinitionsLabel.setEnabled(rows.isEmpty());
        }

        @Override
        protected void layoutChanged() {
            listView.setBounds(0, 0, getWidth(), getHeight());
            noDefinitionsLabel.setBounds(0, 0, getWidth(), defaultElementHeight);
        }

        @Override
        protected void render() {
        }
    }

    /**
     * One definition row of the all-events tab (T-083/T-085): enable checkbox (left),
     * locale-resolved headline, state markers ({@code [disabled]}/{@code [adminOnly]}/
     * {@code [active]}/cooldown) and — right-aligned — the per-row admin buttons:
     * a Reset-cooldown button (only visible while the snapshot reports a running
     * cooldown; a disabled element is fully hidden) LEFT of the Trigger button.
     * The {@code [active]} marker is derived live from the runtime stream at render
     * time; the cooldown marker shows the response-snapshot value.
     * <p>
     * <b>Server-authoritative checkbox:</b> a click immediately reverts the visual
     * state to the last confirmed value and sends SET_ENABLED; the confirmed state
     * arrives with the response snapshot, which rebuilds all rows.
     * Clicking anywhere else on the row opens the {@link NewsEventDetailsScreen}.
     */
    private class EventDefRow extends StockMarketGuiElement {

        private static final int INNER_PAD = 3;

        private final NewsAdminRequest.EventDetails details;
        private final CheckBox enabledBox;
        private final Button rowTriggerButton;
        private final Button resetCooldownButton;
        private final String headline;
        /** Guards against callback recursion while reverting the checkbox. */
        private boolean suppressToggle = false;

        /**
         * Builds the row for one definition snapshot.
         *
         * @param details the server-confirmed definition details
         */
        EventDefRow(NewsAdminRequest.EventDetails details) {
            setEnableBackground(true);
            this.details = details;
            this.headline = resolveHeadline(details);
            setHeight(defaultElementHeight);

            enabledBox = new CheckBox("");
            // Set the confirmed state BEFORE registering the callback so the initial
            // setChecked cannot fire a SET_ENABLED request.
            enabledBox.setChecked(details.enabled());
            enabledBox.setOnStateChanged(this::onToggleRequested);
            enabledBox.setHoverTooltipSupplier(Texts.ENABLE_EVENT_TOOLTIP::getString);
            enabledBox.setHoverTooltipFontScale(hoverToolTipFontSize);
            addChild(enabledBox);

            // T-085: per-row admin actions. The buttons sit at the right row edge —
            // right-anchored tooltips expand leftwards and cannot clip.
            rowTriggerButton = new Button(Texts.TRIGGER.getString(),
                    () -> requestTrigger(details));
            setupRightAnchoredTooltip(rowTriggerButton, Texts.ROW_TRIGGER_TOOLTIP);
            addChild(rowTriggerButton);

            resetCooldownButton = new Button(Texts.RESET_COOLDOWN.getString(),
                    () -> requestResetCooldown(details));
            setupRightAnchoredTooltip(resetCooldownButton, Texts.RESET_COOLDOWN_TOOLTIP);
            // Hidden (disabled elements neither render nor receive input) while the
            // snapshot reports no cooldown — the button is meaningless then.
            resetCooldownButton.setEnabled(details.cooldownRemainingMs() > 0);
            addChild(resetCooldownButton);

            String tooltip = headline + "\n" + Texts.eventId(details.id()).getString()
                    + "\n" + Texts.ROW_DETAILS_TOOLTIP.getString();
            setHoverTooltipSupplier(() -> tooltip);
            setHoverTooltipFontScale(hoverToolTipFontSize);
        }

        /**
         * Handles a user toggle: reverts to the confirmed state immediately (the
         * checkbox may only ever show server-confirmed values) and requests the
         * change via SET_ENABLED. The confirmed response snapshot re-checks it.
         *
         * @param requested the state the user asked for
         */
        private void onToggleRequested(boolean requested) {
            if (suppressToggle) return;
            suppressToggle = true;
            enabledBox.setChecked(details.enabled());
            suppressToggle = false;
            sendAdminRequest(new NewsAdminRequest.InputData(
                    NewsAdminRequest.Op.SET_ENABLED, details.id(), "", null, requested), false, true);
        }

        @Override
        protected void layoutChanged() {
            int w = getWidth();
            int h = getHeight();
            enabledBox.setBounds(0, 0, h, h); // square checkbox, empty label

            // Per-row buttons, right-aligned: [Reset cooldown] [Trigger] (T-085).
            int btnH = h - 2;
            int triggerW = getTextWidth(Texts.TRIGGER.getString()) + 2 * padding;
            rowTriggerButton.setBounds(w - INNER_PAD - triggerW, 1, triggerW, btnH);
            int resetW = getTextWidth(Texts.RESET_COOLDOWN.getString()) + 2 * padding;
            resetCooldownButton.setBounds(rowTriggerButton.getLeft() - spacing - resetW,
                    1, resetW, btnH);
        }

        @Override
        protected boolean mouseClickedOverElement(int button) {
            // The checkbox and the buttons consume their own clicks first (child
            // dispatch); everything else on the row opens the details screen.
            if (button == 0) {
                openEventDetailsScreen(details);
                return true;
            }
            return false;
        }

        @Override
        protected void render() {
            int h = getHeight();
            int textH = Math.round(getTextHeight() * META_SCALE);
            int textY = (h - textH) / 2;

            // State markers, drawn right-to-left starting left of the row buttons.
            int rightX = (resetCooldownButton.isEnabled()
                    ? resetCooldownButton.getLeft() : rowTriggerButton.getLeft()) - spacing;
            // T-107: live cooldown — derived from cooldownEndByEventId (cached at
            // the last admin snapshot) so the label ticks between admin responses
            // instead of freezing on the snapshot's captured value.
            long liveCooldownMs = liveCooldownRemainingMs(details.id());
            if (liveCooldownMs > 0) {
                String marker = Texts.markerCooldown(NewsUiFormatting
                        .formatRemainingTime(liveCooldownMs)).getString();
                rightX -= (int) (getTextWidth(marker) * META_SCALE);
                drawText(marker, rightX, textY, COLOR_NEUTRAL_GRAY, META_SCALE);
                rightX -= spacing;
            }
            if (isEventActiveLive(details.id())) {
                String marker = Texts.MARKER_ACTIVE.getString();
                rightX -= (int) (getTextWidth(marker) * META_SCALE);
                drawText(marker, rightX, textY, COLOR_UP_GREEN, META_SCALE);
                rightX -= spacing;
            }
            if (details.adminOnly()) {
                String marker = Texts.MARKER_ADMIN_ONLY.getString();
                rightX -= (int) (getTextWidth(marker) * META_SCALE);
                drawText(marker, rightX, textY, COLOR_PENDING_ORANGE, META_SCALE);
                rightX -= spacing;
            }
            if (!details.enabled()) {
                String marker = Texts.MARKER_DISABLED.getString();
                rightX -= (int) (getTextWidth(marker) * META_SCALE);
                drawText(marker, rightX, textY, COLOR_DOWN_RED, META_SCALE);
                rightX -= spacing;
            }

            // Headline between the checkbox and the markers (gray when disabled).
            int nameX = enabledBox.getRight() + spacing;
            drawText(NewsUiText.truncate(this, headline, rightX - nameX - spacing, META_SCALE),
                    nameX, textY, details.enabled() ? 0xFFFFFFFF : COLOR_NEUTRAL_GRAY, META_SCALE);
        }
    }

    /**
     * "Scheduler" tab content (T-082/T-083): the four effective scheduler values with
     * override markers, Apply/Reset actions, and the upcoming timeline.
     * <p>
     * <b>Server-authoritative values:</b> the text boxes always show the effective
     * values confirmed by the 500 ms runtime stream. A box deviates only while the
     * admin edits it (tracked per box); Apply sends exactly the edited values
     * (everything else stays {@code null} = unchanged) and the flags are resolved from
     * the response: success → cleared (the stream re-displays the confirmed result),
     * failure → the confirmed values are restored immediately.
     */
    private class SchedulerTab extends StockMarketGuiElement {

        private final Label minLabel = new Label(Texts.SCHED_MIN_SECONDS.getString());
        private final Label maxLabel = new Label(Texts.SCHED_MAX_SECONDS.getString());
        private final Label globalLabel = new Label(Texts.SCHED_MAX_GLOBAL.getString());
        private final Label perMarketLabel = new Label(Texts.SCHED_MAX_PER_MARKET.getString());
        private final TextBox minBox = new TextBox();
        private final TextBox maxBox = new TextBox();
        private final TextBox globalBox = new TextBox();
        private final TextBox perMarketBox = new TextBox();
        private final Button applySchedulerButton;
        private final Button resetSchedulerButton;
        private final Label upcomingTitle = new Label(Texts.UPCOMING_TITLE.getString());
        private final TimelinePanel timelinePanel = new TimelinePanel();

        /** The last stream-confirmed scheduler state (effective values + override flags). */
        private NewsPlugin.RuntimeStreamData.SchedulerState confirmedState =
                NewsPlugin.RuntimeStreamData.SchedulerState.createDefault();
        // Per-box "admin edited this since the last confirm" flags — an edited box is
        // never overwritten by the stream until Apply/Reset resolves it.
        private boolean minEdited, maxEdited, globalEdited, perMarketEdited;

        SchedulerTab() {
            setEnableBackground(false);
            setEnableOutline(false);

            // T-085: right-align the row labels so they sit next to their text boxes.
            minLabel.setAlignment(Label.Alignment.RIGHT);
            maxLabel.setAlignment(Label.Alignment.RIGHT);
            globalLabel.setAlignment(Label.Alignment.RIGHT);
            perMarketLabel.setAlignment(Label.Alignment.RIGHT);

            // Positive integers only — clearing an override goes through the reset
            // button (SchedulerInput's negative-value semantics stay command-only).
            String integerRegex = "^\\d*$";
            minBox.setMatchRegex(integerRegex);
            maxBox.setMatchRegex(integerRegex);
            globalBox.setMatchRegex(integerRegex);
            perMarketBox.setMatchRegex(integerRegex);
            minBox.setOnTextChanged(text ->
                    minEdited = isEdited(text, confirmedState.minSecondsBetweenEvents()));
            maxBox.setOnTextChanged(text ->
                    maxEdited = isEdited(text, confirmedState.maxSecondsBetweenEvents()));
            globalBox.setOnTextChanged(text ->
                    globalEdited = isEdited(text, confirmedState.maxActiveEventsGlobal()));
            perMarketBox.setOnTextChanged(text ->
                    perMarketEdited = isEdited(text, confirmedState.maxActiveEventsPerMarket()));

            // Tooltips: the boxes sit in the right half of the left column — anchor
            // the tooltip's top-RIGHT corner at the mouse so it expands leftwards and
            // cannot clip outside the window (ModSettingsScreen pattern).
            setupRightAnchoredTooltip(minBox, Texts.SCHED_MIN_TOOLTIP);
            setupRightAnchoredTooltip(maxBox, Texts.SCHED_MAX_TOOLTIP);
            setupRightAnchoredTooltip(globalBox, Texts.SCHED_MAX_GLOBAL_TOOLTIP);
            setupRightAnchoredTooltip(perMarketBox, Texts.SCHED_MAX_PER_MARKET_TOOLTIP);

            applySchedulerButton = new Button(Texts.SCHED_APPLY.getString(), this::onApplyScheduler);
            resetSchedulerButton = new Button(Texts.SCHED_RESET.getString(), this::onResetScheduler);
            setupRightAnchoredTooltip(applySchedulerButton, Texts.SCHED_APPLY_TOOLTIP);
            setupRightAnchoredTooltip(resetSchedulerButton, Texts.SCHED_RESET_TOOLTIP);

            upcomingTitle.setAlignment(Label.Alignment.CENTER);

            addChild(minLabel);
            addChild(minBox);
            addChild(maxLabel);
            addChild(maxBox);
            addChild(globalLabel);
            addChild(globalBox);
            addChild(perMarketLabel);
            addChild(perMarketBox);
            addChild(applySchedulerButton);
            addChild(resetSchedulerButton);
            addChild(upcomingTitle);
            addChild(timelinePanel);
        }

        /** @return true if the box text deviates from the confirmed effective value */
        private boolean isEdited(String text, long confirmedValue) {
            Long value = parseLongOrNull(text);
            // Empty/unparseable counts as "not edited": the stream may refill the box.
            return value != null && value != confirmedValue;
        }

        /** @return the parsed non-negative long, or null for empty/invalid text */
        private @Nullable Long parseLongOrNull(String text) {
            if (text.isEmpty()) return null;
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        /**
         * Consumes a stream-confirmed scheduler state (every 500 ms): updates the
         * override markers and re-displays the effective values in every box that is
         * neither focused nor carrying a pending admin edit.
         *
         * @param state the effective values + per-value override flags
         */
        void onSchedulerState(NewsPlugin.RuntimeStreamData.SchedulerState state) {
            confirmedState = state;
            updateOverrideMarker(minLabel, Texts.SCHED_MIN_SECONDS, state.minOverridden());
            updateOverrideMarker(maxLabel, Texts.SCHED_MAX_SECONDS, state.maxOverridden());
            updateOverrideMarker(globalLabel, Texts.SCHED_MAX_GLOBAL, state.globalOverridden());
            updateOverrideMarker(perMarketLabel, Texts.SCHED_MAX_PER_MARKET, state.perMarketOverridden());
            refreshBox(minBox, state.minSecondsBetweenEvents(), minEdited);
            refreshBox(maxBox, state.maxSecondsBetweenEvents(), maxEdited);
            refreshBox(globalBox, state.maxActiveEventsGlobal(), globalEdited);
            refreshBox(perMarketBox, state.maxActiveEventsPerMarket(), perMarketEdited);
        }

        /** Marks an overridden value with the "(overridden)" suffix + orange label. */
        private void updateOverrideMarker(Label label, Component baseName, boolean overridden) {
            label.setText(overridden
                    ? baseName.getString() + " " + Texts.SCHED_OVERRIDDEN.getString()
                    : baseName.getString());
            label.setTextColor(overridden ? COLOR_PENDING_ORANGE : DEFAULT_TEXT_COLOR);
        }

        /** Re-displays a confirmed value unless the admin is editing the box. */
        private void refreshBox(TextBox box, long confirmedValue, boolean edited) {
            if (edited || box.isFocused()) return;
            String text = String.valueOf(confirmedValue);
            if (!box.getText().equals(text)) {
                box.setText(text);
            }
        }

        /** Clears all pending-edit flags; the stream then re-displays confirmed values. */
        void clearEditedFlags() {
            minEdited = maxEdited = globalEdited = perMarketEdited = false;
        }

        /**
         * Rejected/failed change: nothing changed server-side (atomic validation) —
         * drop the pending edits and re-display the last confirmed values immediately.
         */
        void restoreConfirmedValues() {
            clearEditedFlags();
            minBox.setText(String.valueOf(confirmedState.minSecondsBetweenEvents()));
            maxBox.setText(String.valueOf(confirmedState.maxSecondsBetweenEvents()));
            globalBox.setText(String.valueOf(confirmedState.maxActiveEventsGlobal()));
            perMarketBox.setText(String.valueOf(confirmedState.maxActiveEventsPerMarket()));
        }

        /**
         * Apply: sends only the values the admin actually edited ({@code null} for the
         * rest — SET_SCHEDULER treats null as unchanged). Server-side validation is
         * atomic; the response resolves the pending flags (see sendAdminRequest).
         */
        private void onApplyScheduler() {
            Long min = minEdited ? parseLongOrNull(minBox.getText()) : null;
            Long max = maxEdited ? parseLongOrNull(maxBox.getText()) : null;
            Long globalCap = globalEdited ? parseLongOrNull(globalBox.getText()) : null;
            Long perMarketCap = perMarketEdited ? parseLongOrNull(perMarketBox.getText()) : null;
            sendAdminRequest(new NewsAdminRequest.InputData(
                    NewsAdminRequest.Op.SET_SCHEDULER, "", "", null, false,
                    new NewsAdminRequest.SchedulerInput(min, max,
                            globalCap != null ? Math.toIntExact(Math.min(globalCap, Integer.MAX_VALUE)) : null,
                            perMarketCap != null ? Math.toIntExact(Math.min(perMarketCap, Integer.MAX_VALUE)) : null,
                            false)), false, true);
        }

        /** Reset-to-file: clears all four admin overrides ({@code resetAll}). */
        private void onResetScheduler() {
            sendAdminRequest(new NewsAdminRequest.InputData(
                    NewsAdminRequest.Op.SET_SCHEDULER, "", "", null, false,
                    new NewsAdminRequest.SchedulerInput(null, null, null, null, true)), false, true);
        }

        @Override
        protected void layoutChanged() {
            int w = getWidth();
            int h = getHeight();
            int eh = defaultElementHeight;
            int labelW = w * 60 / 100;
            int boxW = w - labelW - spacing;

            int y = 0;
            Label[] labels = {minLabel, maxLabel, globalLabel, perMarketLabel};
            TextBox[] boxes = {minBox, maxBox, globalBox, perMarketBox};
            for (int i = 0; i < labels.length; i++) {
                labels[i].setBounds(0, y, labelW, eh);
                boxes[i].setBounds(labelW + spacing, y, boxW, eh);
                y += eh + spacing;
            }

            int halfW = (w - spacing) / 2;
            applySchedulerButton.setBounds(0, y, halfW, eh);
            resetSchedulerButton.setBounds(halfW + spacing, y, w - halfW - spacing, eh);
            y += eh + spacing;

            upcomingTitle.setBounds(0, y, w, eh);
            y += eh + spacing;
            timelinePanel.setBounds(0, y, w, Math.max(0, h - y));
        }

        @Override
        protected void render() {
        }
    }

    /**
     * The upcoming-events timeline (T-082 data): draws the pre-planned scheduler slots
     * straight from {@link #lastRuntimeData} every frame, so the 500 ms stream refresh
     * needs no child rebuilding. Slots are presented as <i>planned</i> — the fire-time
     * re-validation is authoritative (tooltip wording) — and time-only slots (empty
     * event id) state that the event is picked at trigger time. Planned slots show the
     * locale-resolved headline of their event and are clickable: a click opens the
     * event's {@link NewsEventDetailsScreen}; time-only slots are not clickable
     * (T-085).
     */
    private class TimelinePanel extends StockMarketGuiElement {

        private static final int INNER_PAD = 3;

        TimelinePanel() {
            setEnableBackground(true);
            setHoverTooltipSupplier(Texts.UPCOMING_TOOLTIP::getString);
            setHoverTooltipFontScale(hoverToolTipFontSize);
            // T-085 (user request): anchor the tooltip's top-LEFT corner at the mouse.
            setHoverTooltipMousePositionAlignment(Alignment.TOP_LEFT);
        }

        /** @return the timeline slot index <b>rendered</b> at the given panel-relative y, or -1 */
        private int slotIndexAt(int mouseY) {
            int lineH = NewsUiText.lineHeight(this, META_SCALE);
            if (mouseY < INNER_PAD || lineH <= 0) return -1;
            int index = (mouseY - INNER_PAD) / lineH;
            // Only fully visible lines are drawn (see render) — never map clicks
            // beyond them onto hidden slots.
            int visibleLines = Math.max(0, (getHeight() - 2 * INNER_PAD) / lineH);
            if (index >= visibleLines) return -1;
            return index < lastRuntimeData.upcoming().size() ? index : -1;
        }

        @Override
        protected boolean mouseClickedOverElement(int button) {
            if (button != 0) return false;
            int index = slotIndexAt(getMouseY());
            if (index < 0) return false;
            String eventId = lastRuntimeData.upcoming().get(index).eventId();
            if (eventId.isEmpty()) return false; // time-only slots are not clickable
            NewsAdminRequest.EventDetails details = detailsById.get(eventId);
            if (details == null) return false;
            openEventDetailsScreen(details);
            return true;
        }

        @Override
        protected void layoutChanged() {
        }

        @Override
        protected void render() {
            int innerW = getWidth() - 2 * INNER_PAD;
            int lineH = NewsUiText.lineHeight(this, META_SCALE);
            int y = INNER_PAD;

            List<NewsPlugin.RuntimeStreamData.UpcomingEvent> upcoming = lastRuntimeData.upcoming();
            if (upcoming.isEmpty()) {
                drawText(NewsUiText.truncate(this, Texts.UPCOMING_NONE.getString(), innerW, META_SCALE),
                        INNER_PAD, y, COLOR_NEUTRAL_GRAY, META_SCALE);
                return;
            }
            int hoveredIndex = isMouseOver() ? slotIndexAt(getMouseY()) : -1;
            int slot = 1;
            for (int i = 0; i < upcoming.size(); i++) {
                if (y + lineH > getHeight() - INNER_PAD) break;
                NewsPlugin.RuntimeStreamData.UpcomingEvent entry = upcoming.get(i);
                String line = Texts.upcomingSlot(slot,
                        NewsUiFormatting.formatRemainingTime(entry.etaMs()),
                        resolveUpcomingName(entry.eventId())).getString();
                boolean clickable = !entry.eventId().isEmpty()
                        && detailsById.containsKey(entry.eventId());
                int color = entry.eventId().isEmpty() ? COLOR_NEUTRAL_GRAY
                        : (clickable && i == hoveredIndex) ? 0xFFFFFFFF : COLOR_TEXT_SECONDARY;
                drawText(NewsUiText.truncate(this, line, innerW, META_SCALE),
                        INNER_PAD, y, color, META_SCALE);
                y += lineH;
                slot++;
            }
        }
    }

    /**
     * Clickable market icon of an active-event row (T-086): a left-click selects
     * the market in the right-side per-market editor — exactly the binding that
     * selecting the item in the {@link ItemSelectionView} performs
     * ({@link #bindSelectedMarket}: settings fields, selected-market label, chart).
     * Icons of unresolvable markets (no ItemID for the streamed short id) stay
     * display-only.
     * <p>
     * The {@code MarketItemButton} widget is deliberately <b>not</b> reused here:
     * its built-in close button carries remove semantics that make no sense in
     * this list. This is a plain ModUtilities {@link ItemView} plus a click
     * handler and a hover highlight (same overlay style as MarketItemButton).
     */
    private class SelectMarketIcon extends ItemView {

        private final @Nullable ItemID marketID;

        /**
         * @param stack    the market's display stack
         * @param marketID the market to select on click, or null for display-only
         */
        SelectMarketIcon(ItemStack stack, @Nullable ItemID marketID) {
            super(stack);
            this.marketID = marketID;
            if (marketID != null) {
                // Replace the plain item tooltip with name + action hint.
                setShowTooltip(false);
                String tooltip = stack.getHoverName().getString() + "\n"
                        + Texts.ICON_SELECT_TOOLTIP.getString();
                setHoverTooltipSupplier(() -> tooltip);
                setHoverTooltipFontScale(hoverToolTipFontSize);
            }
        }

        @Override
        public void renderBackground() {
            super.renderBackground();
            if (marketID != null && isMouseOver()) {
                drawRect(0, 0, getWidth(), getHeight(), 0x80FFFFFF); // hover highlight
            }
        }

        @Override
        protected boolean mouseClickedOverElement(int button) {
            if (button == 0 && marketID != null) {
                bindSelectedMarket(marketID);
                return true; // consumed — the row click (details screen) must not fire
            }
            return false;
        }
    }

    /**
     * One live event entry: headline (event id as hover tooltip), published state,
     * phase + remaining time, and one row per impacted market with its item icon,
     * the current factor as a signed colored percentage directly right of the icon
     * (T-086, matching the details screen's left-clustered impact rows) and the
     * display name.
     * <p>
     * <b>Interaction (T-086):</b> clicking a market icon selects that market in the
     * right-side editor ({@link SelectMarketIcon}); clicking anywhere else on the
     * panel opens the same {@link NewsEventDetailsScreen} the All-events rows use
     * (only possible once the definition snapshot is loaded — non-admins whose LIST
     * was rejected simply have no details to open). Child clicks are dispatched
     * first, so the two cannot conflict.
     * <p>
     * <b>Per-event admin actions (T-093):</b> a [Skip phase]-over-[Stop] button stack
     * at the right panel edge fast-forwards the event to its next phase / hard-stops
     * it ({@code SKIP_PHASE}/{@code STOP_EVENT}, audited server-side). Gated like the
     * All-events rows' admin buttons: hidden until the admin snapshot is loaded.
     * <p>
     * <b>Picture thumbnail (T-091, resized T-118):</b> events whose streamed
     * snapshot carries a picture hash show a square {@link NewsPictureElement}
     * thumbnail (FIT mode — the whole image aspect-fit scaled, never cropped;
     * resolved decision §12.5) on the right side of the row, directly left of
     * the T-093 button stack. In the admin view (T-118) the thumbnail spans the
     * full vertical from the top of the Skip-phase button to the bottom of the
     * Stop button (width == height == that span) so the picture reads at a
     * glance. In the non-admin view (no button stack laid out) it stays at the
     * classic 32 px size at the row's right edge. The space is reserved only
     * when a hash exists, so picture-less events keep today's layout;
     * market-name and phase-line truncation both read the thumbnail's live
     * bounds so long labels never overlap the enlarged picture. The element
     * polls the picture cache per frame and the panels are rebuilt by the
     * 500 ms stream, so the thumbnail pops in naturally.
     * <p>
     * The panel height is width-independent (long texts are truncated with an
     * ellipsis instead of wrapped), so it is fixed at construction time — the
     * {@code VerticalListView} layout reads child heights when it is applied.
     */
    private class ActiveEventPanel extends StockMarketGuiElement {

        private static final float HEADLINE_SCALE = 1.0f;
        private static final int INNER_PAD = 3;
        private static final int ICON_SIZE = 16;
        private static final int MARKET_ROW_H = ICON_SIZE + 2;
        /**
         * Side length of the square picture thumbnail (T-091): twice the market item
         * icon size — the row height available right of the market rows fits it
         * without growing picture-less rows.
         */
        private static final int THUMB_SIZE = 2 * ICON_SIZE;

        /** One impacted market: resolved icon (child ItemView), name and factor text. */
        private record MarketRow(ItemView icon, String name, String factorText, int factorColor) {}

        private final String eventId;
        private final String headline;
        private final String publishedText;
        private final int publishedColor;
        private final String phaseText;
        private final int phaseColor;
        private final String remainingText;
        /**
         * Row-2 tail while the sequence is running (T-100, plan §6): {@code " · "} +
         * countdown to the next step boundary, plus {@code " · phase i of n"} for
         * multi-step sequences. {@code null} while PENDING or terminal — those states
         * keep the classic phase + total-remaining rendering.
         */
        private final @Nullable String stepProgressText;
        private final List<MarketRow> marketRows = new ArrayList<>();
        /** Width of the factor column (max over all rows), left-clustered layout (T-086). */
        private final int factorColumnW;

        // T-093: per-event admin actions, stacked at the right panel edge —
        // [Skip phase] on top, [Stop] below. Hidden (disabled) without admin data.
        private final Button skipPhaseButton;
        private final Button stopButton;
        /** Shared width of the two stacked buttons (max label width + padding). */
        private final int actionButtonW;

        /**
         * The event's picture thumbnail (FIT mode), or null for picture-less events
         * (T-091) — created only when the streamed snapshot carries a hash, so rows
         * without a picture reserve no dead space.
         */
        private final @Nullable NewsPictureElement pictureThumbnail;

        private int metaRowY;
        private int marketsY;

        /**
         * Builds the panel for one streamed active event.
         *
         * @param event the event snapshot from the runtime data stream
         */
        ActiveEventPanel(NewsPlugin.RuntimeStreamData.ActiveEventInfo event) {
            super();
            setEnableBackground(true);

            eventId = event.eventId();
            headline = event.headline().isEmpty() ? event.eventId() : event.headline();
            publishedText = (event.published() ? Texts.PUBLISHED : Texts.PENDING_PUBLICATION).getString();
            publishedColor = event.published() ? COLOR_UP_GREEN : COLOR_PENDING_ORANGE;
            phaseText = Texts.phase(event.phaseName()).getString();
            phaseColor = NewsPluginGuiElement.phaseColor(event.phaseName());
            remainingText = Texts.remaining(
                    NewsUiFormatting.formatRemainingTime(event.remainingMs())).getString();

            // T-100: step progress while the sequence is running. stepRemainingMs
            // carries the -1 sentinel while PENDING and once terminal (PERMANENT/
            // EXPIRED). The countdown is a per-snapshot value exactly like
            // remainingText: the 500 ms runtime stream rebuilds these panels, which
            // ticks the display (same approach as every other countdown here).
            if (event.stepRemainingMs() >= 0) {
                String countdown = NewsUiFormatting.formatRemainingTime(event.stepRemainingMs());
                // "phase 1 of 1" is pure noise for single-step sequences — omitted.
                stepProgressText = event.stepCount() > 1
                        ? " · " + countdown + " · " + Texts.stepProgress(
                                event.stepIndex() + 1, event.stepCount()).getString()
                        : " · " + countdown;
            } else {
                stepProgressText = null;
            }

            // Full headline + event id on hover (the headline may be truncated);
            // the details hint is appended only while the snapshot actually has the
            // definition (T-086) — evaluated at hover time, the snapshot loads async.
            String tooltip = headline + "\n" + Texts.eventId(event.eventId()).getString();
            setHoverTooltipSupplier(() -> detailsFor(eventId) != null
                    ? tooltip + "\n" + Texts.ROW_DETAILS_TOOLTIP.getString() : tooltip);
            setHoverTooltipFontScale(hoverToolTipFontSize);

            int maxFactorW = 0;
            for (NewsPlugin.RuntimeStreamData.MarketFactor market : event.markets()) {
                ItemStack stack = resolveMarketStack(market.marketId(), "");
                String name = stack.getHoverName().getString();

                float factor = market.currentFactor();
                int factorColor = factor > 1.0f ? COLOR_UP_GREEN
                        : factor < 1.0f ? COLOR_DOWN_RED : COLOR_NEUTRAL_GRAY;
                // Clickable icon: selects the market in the editor (T-086).
                ItemView icon = new SelectMarketIcon(stack,
                        subscribedMarketsByShort.get(market.marketId()));
                addChild(icon);
                String factorText = NewsUiFormatting.formatFactorPercent(factor);
                maxFactorW = Math.max(maxFactorW, (int) (getTextWidth(factorText) * META_SCALE));
                marketRows.add(new MarketRow(icon, name, factorText, factorColor));
            }
            factorColumnW = maxFactorW;

            // T-093: per-event Stop + Skip-phase buttons. Gated like the All-events
            // tab's Trigger/Reset-CD buttons: they need the server-confirmed admin
            // snapshot (non-admins never get one — their LIST is rejected), so they
            // stay hidden (disabled elements neither render nor receive input) until
            // detailsFor() resolves. Panels are rebuilt on every 500 ms stream
            // snapshot, so the gate re-evaluates continuously.
            boolean adminDataAvailable = detailsFor(eventId) != null;
            skipPhaseButton = new Button(Texts.ROW_SKIP_PHASE.getString(),
                    () -> requestSkipPhase(eventId));
            setupRightAnchoredTooltip(skipPhaseButton, Texts.ROW_SKIP_PHASE_TOOLTIP);
            skipPhaseButton.setEnabled(adminDataAvailable);
            addChild(skipPhaseButton);
            stopButton = new Button(Texts.ROW_STOP.getString(),
                    () -> requestStopEvent(eventId));
            setupRightAnchoredTooltip(stopButton, Texts.ROW_STOP_TOOLTIP);
            stopButton.setEnabled(adminDataAvailable);
            addChild(stopButton);
            actionButtonW = Math.max(getTextWidth(Texts.ROW_SKIP_PHASE.getString()),
                    getTextWidth(Texts.ROW_STOP.getString())) + 2 * padding;

            // T-091: square picture thumbnail (FIT — whole image, no crop) for
            // events that stream a picture hash; positioned in layoutChanged (right
            // side, below the published label, directly left of the button stack).
            if (event.pictureHash() != null) {
                pictureThumbnail = new NewsPictureElement(event.pictureHash(),
                        NewsPictureElement.FitMode.FIT);
                addChild(pictureThumbnail);
            } else {
                pictureThumbnail = null;
            }

            // Fixed, width-independent height (see class Javadoc).
            int headlineH = Math.round(getTextHeight() * HEADLINE_SCALE);
            int metaH = Math.round(getTextHeight() * META_SCALE);
            metaRowY = INNER_PAD + headlineH + 2;
            marketsY = metaRowY + metaH + 2;
            int contentBottom = marketsY + marketRows.size() * MARKET_ROW_H;
            if (adminDataAvailable) {
                // Ensure the two stacked buttons (starting below the headline row)
                // always fit, even for events with a single market row.
                int buttonStackBottom = metaRowY + 2 * actionButtonH() + spacing;
                contentBottom = Math.max(contentBottom, buttonStackBottom);
            }
            if (pictureThumbnail != null) {
                // Ensure the thumbnail (starting below the published label) fits
                // too — relevant for single-market rows without admin buttons.
                contentBottom = Math.max(contentBottom, metaRowY + THUMB_SIZE);
            }
            setHeight(contentBottom + INNER_PAD);
        }

        /** @return the height of one per-event action button (T-093, small row button). */
        private int actionButtonH() {
            return defaultElementHeight - 2; // same sizing as the All-events row buttons
        }

        @Override
        protected void layoutChanged() {
            // Only the icon positions depend on geometry; text is drawn in render().
            int y = marketsY;
            for (MarketRow row : marketRows) {
                row.icon().setBounds(INNER_PAD, y, ICON_SIZE, ICON_SIZE);
                y += MARKET_ROW_H;
            }

            // T-093: [Skip phase] over [Stop] at the right panel edge, starting below
            // the headline/published row. Right-anchored tooltips expand leftwards.
            if (skipPhaseButton.isEnabled()) {
                int btnH = actionButtonH();
                int btnX = getWidth() - INNER_PAD - actionButtonW;
                skipPhaseButton.setBounds(btnX, metaRowY, actionButtonW, btnH);
                stopButton.setBounds(btnX, skipPhaseButton.getBottom() + spacing,
                        actionButtonW, btnH);
            }

            // T-091: the picture thumbnail sits on the right side of the row, below
            // the published/meta label and directly LEFT of the T-093 button stack
            // (at the right panel edge when the buttons are hidden for non-admins).
            // T-118: when the admin button stack is laid out, the thumbnail grows to
            // a square that spans the full vertical from the top of Skip-phase to the
            // bottom of Stop (side = stopButton.getBottom() - skipPhaseButton.getTop())
            // so the picture reads at a glance instead of a tiny 32 px chip. Width
            // equals height (square 1:1). For the non-admin path — no button stack —
            // the original 32 px thumbnail is retained since there is no button span
            // to align to.
            if (pictureThumbnail != null) {
                if (skipPhaseButton.isEnabled()) {
                    int squareSide = stopButton.getBottom() - skipPhaseButton.getTop();
                    int rightEdge = skipPhaseButton.getLeft() - spacing;
                    pictureThumbnail.setBounds(rightEdge - squareSide,
                            skipPhaseButton.getTop(), squareSide, squareSide);
                } else {
                    int rightEdge = getWidth() - INNER_PAD;
                    pictureThumbnail.setBounds(rightEdge - THUMB_SIZE, metaRowY,
                            THUMB_SIZE, THUMB_SIZE);
                }
            }
        }

        /**
         * Opens the event's details screen — the same screen the All-events rows use
         * (T-086). Icon clicks are consumed by the {@link SelectMarketIcon} children
         * before this handler runs (child-first dispatch), so they never end up here.
         * No-op while the server-confirmed snapshot does not contain the definition
         * (snapshot still loading, or a non-admin viewer).
         */
        @Override
        protected boolean mouseClickedOverElement(int button) {
            if (button == 0) {
                NewsAdminRequest.EventDetails details = detailsFor(eventId);
                if (details != null) {
                    openEventDetailsScreen(details);
                    return true;
                }
            }
            return false;
        }

        @Override
        protected void render() {
            int w = getWidth();
            int innerW = w - 2 * INNER_PAD;

            // Row 1: headline (left, truncated) + published state (right)
            int publishedW = (int) (getTextWidth(publishedText) * META_SCALE);
            drawText(NewsUiText.truncate(this, headline,
                            innerW - publishedW - spacing, HEADLINE_SCALE),
                    INNER_PAD, INNER_PAD, 0xFFFFFFFF, HEADLINE_SCALE);
            drawText(publishedText, w - INNER_PAD - publishedW, INNER_PAD, publishedColor, META_SCALE);

            // Row 2 (T-100): while the sequence runs — step name · countdown to the
            // next step · "phase i of n"; PENDING/terminal keep the classic phase +
            // total-remaining display. The T-093 button stack and the T-091 thumbnail
            // both start at this row, so the line truncates left of them (same limit
            // the market-name column uses further down).
            int row2Limit = skipPhaseButton.isEnabled()
                    ? skipPhaseButton.getLeft() - spacing : w - INNER_PAD;
            if (pictureThumbnail != null) {
                row2Limit = Math.min(row2Limit, pictureThumbnail.getLeft() - spacing);
            }
            String shownPhase = NewsUiText.truncate(this, phaseText,
                    row2Limit - INNER_PAD, META_SCALE);
            drawText(shownPhase, INNER_PAD, metaRowY, phaseColor, META_SCALE);
            int phaseW = (int) (getTextWidth(shownPhase) * META_SCALE);
            if (stepProgressText != null) {
                // The tail carries its own " · " separator — no extra spacing.
                int tailX = INNER_PAD + phaseW;
                drawText(NewsUiText.truncate(this, stepProgressText,
                                row2Limit - tailX, META_SCALE),
                        tailX, metaRowY, COLOR_NEUTRAL_GRAY, META_SCALE);
            } else {
                int tailX = INNER_PAD + phaseW + 2 * spacing;
                drawText(NewsUiText.truncate(this, remainingText,
                                row2Limit - tailX, META_SCALE),
                        tailX, metaRowY, COLOR_NEUTRAL_GRAY, META_SCALE);
            }

            // Market rows, left-clustered (T-086): icon | ±factor % | name — the
            // percentage sits directly right of the icon (details-screen pattern)
            // instead of detached at the far row edge. T-093/T-091: the name column
            // stops left of the per-event button stack and of the picture thumbnail
            // instead of running underneath them.
            int rightLimit = skipPhaseButton.isEnabled()
                    ? skipPhaseButton.getLeft() - spacing : w - INNER_PAD;
            if (pictureThumbnail != null) {
                rightLimit = Math.min(rightLimit, pictureThumbnail.getLeft() - spacing);
            }
            int y = marketsY;
            int textOffsetY = (ICON_SIZE - Math.round(getTextHeight() * META_SCALE)) / 2;
            for (MarketRow row : marketRows) {
                int factorX = INNER_PAD + ICON_SIZE + spacing;
                int nameX = factorX + factorColumnW + 2 * spacing;
                drawText(row.factorText(), factorX, y + textOffsetY,
                        row.factorColor(), META_SCALE);
                drawText(NewsUiText.truncate(this, row.name(), rightLimit - nameX, META_SCALE),
                        nameX, y + textOffsetY, COLOR_TEXT_SECONDARY, META_SCALE);
                y += MARKET_ROW_H;
            }
        }
    }
}

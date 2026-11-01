package net.kroia.stockmarket.client.company;

import net.kroia.banksystem.api.BankSystemAPI;
import net.kroia.banksystem.api.company.IBankSystemVisualLookup;
import net.kroia.banksystem.api.company.IShareIconRenderer;
import net.kroia.banksystem.api.company.ShareVisuals;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.StockMarketModBackend;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Null-safe client-side accessors for BankSystem's company {@link ShareVisuals}
 * (T-145, Phase B #3).
 * <p>
 * These helpers wrap {@link IBankSystemVisualLookup} — obtained lazily on every
 * call from {@link BankSystemAPI#getVisualLookup()} on the current
 * {@link StockMarketModBackend.ClientInstances#BANK_SYSTEM_API} — so callers do
 * not have to deal with the fail-closed shim that BankSystem returns on
 * server-only JVMs or during pre-join teardown/reload windows.
 * <p>
 * <b>Self-healing / re-query contract:</b> BankSystem's client cache returns
 * {@code null} on a cache miss and issues a background fetch so the next
 * per-frame lookup succeeds. Callers rendering per frame (icons, tooltips,
 * lists) should therefore simply skip drawing on {@code null} and re-invoke on
 * the next frame — do NOT cache a null result or negatively cache in the caller;
 * that would defeat the self-heal.
 * <p>
 * Deliberately does NOT stash a second reference to the visual lookup on
 * {@link StockMarketModBackend.ClientInstances}: BankSystem's accessor already
 * returns a stable client-side singleton and owns the lifecycle. A local mirror
 * would just double the invalidation surface.
 */
public final class ShareVisualHelpers {

    private ShareVisualHelpers() {
        // utility class — not instantiable
    }

    /**
     * Returns the {@link ShareVisuals} record for the given item id, or
     * {@code null} if it cannot be resolved right now.
     * <p>
     * Returns {@code null} when any of the following holds:
     * <ul>
     *   <li>The client instances holder has not been initialised yet
     *       (pre-join, or already torn down after disconnect).</li>
     *   <li>The cached {@link BankSystemAPI} reference is missing (should not
     *       happen post-join, but guarded defensively).</li>
     *   <li>BankSystem's {@link IBankSystemVisualLookup} returns {@code null} —
     *       either because the id is not a company share, or the client cache
     *       missed and is now backfilling asynchronously. Retry next frame.</li>
     * </ul>
     * Any unexpected exception from the underlying lookup is swallowed and
     * treated as a null result so a transient failure never crashes a render
     * pass.
     *
     * @param itemId the item id to look up; must not be null
     * @return the share visuals, or {@code null} if unavailable this frame
     */
    public static @Nullable ShareVisuals getShareVisualsOrNull(@NotNull ItemID itemId) {
        StockMarketModBackend.ClientInstances client = StockMarketModBackend.getClientInstances();
        if (client == null) return null;
        BankSystemAPI api = client.BANK_SYSTEM_API;
        if (api == null) return null;
        try {
            IBankSystemVisualLookup lookup = api.getVisualLookup();
            if (lookup == null) return null; // defensive — the API contract is non-null
            return lookup.getShareVisuals(itemId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Convenience predicate: {@code true} iff {@link #getShareVisualsOrNull}
     * currently resolves to a non-null value for {@code itemId} — i.e. the id
     * is known to BankSystem as a company share and the client cache is warm.
     * <p>
     * Note that this shares the self-healing contract of the underlying
     * lookup: a {@code false} return can flip to {@code true} once the client
     * cache backfills. Do not rely on this as a permanent classifier.
     *
     * @param itemId the item id to test; must not be null
     * @return {@code true} if share visuals are resolvable right now
     */
    public static boolean isCompanyShare(@NotNull ItemID itemId) {
        return getShareVisualsOrNull(itemId) != null;
    }

    /**
     * Preferred human-readable name for the given item id: if BankSystem has a
     * non-empty company display name (this id is a stamped share), return it;
     * otherwise return the supplied fallback (typically the raw ItemID name,
     * e.g. {@code itemId.getStack().getHoverName()}).
     * <p>
     * Callers should invoke this per frame — the underlying lookup is
     * self-healing (see class Javadoc): a fallback returned this frame can flip
     * to the company display name once BankSystem's client cache backfills.
     * <p>
     * "Non-empty" is defined as {@code !visuals.displayName().getString().isEmpty()}
     * so we honour BankSystem's convention of using {@link Component#empty()} to
     * mean "no display name set — fall back to raw ItemID name" (T-147).
     *
     * @param itemId   the item id to resolve; must not be null
     * @param fallback the raw name to return when no company display name is
     *                 available; must not be null
     * @return the company display name if present and non-empty, else the fallback
     */
    public static @NotNull Component getDisplayName(@NotNull ItemID itemId, @NotNull Component fallback) {
        ShareVisuals visuals = getShareVisualsOrNull(itemId);
        if (visuals == null) return fallback;
        Component displayName = visuals.displayName();
        if (displayName == null || displayName.getString().isEmpty()) return fallback;
        return displayName;
    }

    /**
     * Returns BankSystem's optional custom {@link IShareIconRenderer}, or
     * {@code null} if the API is unavailable or has not exposed one.
     * <p>
     * The returned reference must not be cached across frames: BankSystem may
     * swap its renderer at runtime (e.g. resource pack reload) and the
     * accessor is intentionally cheap so per-call queries are safe.
     *
     * @return the current share-icon renderer, or {@code null} when absent
     */
    public static @Nullable IShareIconRenderer getShareIconRenderer() {
        StockMarketModBackend.ClientInstances client = StockMarketModBackend.getClientInstances();
        if (client == null) return null;
        BankSystemAPI api = client.BANK_SYSTEM_API;
        if (api == null) return null;
        try {
            return api.getShareIconRenderer();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Attempts to draw a custom share icon for {@code id} into the given
     * bounds using BankSystem's optional {@link IShareIconRenderer}.
     * <p>
     * Renders only when both conditions hold:
     * <ul>
     *   <li>{@link #isCompanyShare(ItemID)} returns {@code true} for {@code id}, AND</li>
     *   <li>{@link #getShareIconRenderer()} returns a non-null renderer.</li>
     * </ul>
     * On success the icon is drawn and the method returns {@code true}; the
     * caller must skip its vanilla {@link net.minecraft.world.item.ItemStack}
     * fallback in that case. On {@code false} nothing was drawn and the caller
     * should render the vanilla icon.
     *
     * @param gfx the active {@link GuiGraphics}
     * @param x   left in gui-space pixels
     * @param y   top in gui-space pixels
     * @param w   width in pixels
     * @param h   height in pixels
     * @param id  the market/share item id (may be any id; non-shares return false)
     * @return {@code true} if a custom icon was drawn; {@code false} to fall back
     */
    public static boolean tryRenderShareIcon(@NotNull GuiGraphics gfx,
                                             int x, int y, int w, int h,
                                             @NotNull ItemID id) {
        if (!isCompanyShare(id)) return false;
        IShareIconRenderer renderer = getShareIconRenderer();
        if (renderer == null) return false;
        try {
            renderer.renderInto(gfx, x, y, w, h, id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

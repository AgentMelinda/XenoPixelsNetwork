package net.bullettrain.xenopixelsmod.client.hud;

import net.bullettrain.xenopixelsmod.client.config.DmzMenuMode;
import net.bullettrain.xenopixelsmod.client.config.XenoHudConfig;
import net.bullettrain.xenopixelsmod.mixin.compat.dmz.DmzScaledScreenWidthInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Selects generated XenoPixels chrome while a normal DragonMineZ V-menu renders. */
public final class DmzMenuThemeState {

    private static final ThreadLocal<Active> ACTIVE = new ThreadLocal<>();
    /**
     * Whether any themed menu is mid-render.
     *
     * <p>{@link #remap} is reached from the one blit every other overload funnels into, so it runs
     * for every textured draw in the game. While the rework is parked -- which is the default -- a
     * plain field read is all that costs, instead of a thread-local lookup per draw.
     */
    private static volatile boolean anyActive;

    private DmzMenuThemeState() {
    }

    /**
     * What is being drawn and how.
     *
     * <p>The mode is captured here rather than read per draw so a mid-render config change cannot
     * dress half a screen in one rework and half in the other. {@code uiWidth} is DragonMineZ's own
     * logical canvas width, which the left/right split needs and which is only reachable from the
     * screen instance.
     */
    private record Active(DmzMenuArt.Page page, DmzMenuMode mode, int uiWidth) {}

    public static void begin(Object screen) {
        // /xenohud menus stock leaves DMZ's own menus completely alone: nothing is marked active,
        // so remap returns every texture untouched and the multiplier column stays out of the way.
        DmzMenuMode mode = XenoHudConfig.dmzMenuMode;
        DmzMenuArt.Page page = mode.themed() && screen != null
                ? DmzMenuArt.pageOf(screen.getClass().getName())
                : null;
        if (page == null) {
            ACTIVE.remove();
            anyActive = false;
            return;
        }
        ACTIVE.set(new Active(page, mode, uiWidth(screen)));
        anyActive = true;
    }

    /**
     * DragonMineZ's logical canvas width, or 0 when it cannot be read.
     *
     * <p>Zero rather than a substitute: {@link DmzMenuArt#drawsLeftPanel} treats an implausible
     * width as "use the legacy threshold", and a made-up width would put panels on the wrong side
     * more confidently than no width at all. The guard matters because the invoker is a compat mixin
     * -- if DragonMineZ's class hierarchy ever moves, this returns 0 instead of throwing through
     * every GUI draw in the game.
     */
    private static int uiWidth(Object screen) {
        if (!(screen instanceof DmzScaledScreenWidthInvoker scaled)) {
            return 0;
        }
        try {
            return scaled.xeno$getUiWidth();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /**
     * True while a DragonMineZ menu is being drawn with Xeno chrome.
     *
     * <p>Asked by the pieces that redraw DMZ's own content rather than its textures -- the stats
     * multiplier column -- so they change nothing in stock mode, and nothing on a screen this theme
     * does not recognise.
     */
    public static boolean isThemed() {
        return anyActive && ACTIVE.get() != null;
    }

    public static void end() {
        ACTIVE.remove();
        anyActive = false;
    }

    public static ResourceLocation remap(ResourceLocation original, int drawX) {
        if (!anyActive) {
            return original;
        }
        Active active = ACTIVE.get();
        if (active == null || original == null || !"dragonminez".equals(original.getNamespace())) {
            return original;
        }
        String[] candidates = DmzMenuArt.sheets(active.page(), active.mode(), original.getPath(),
                drawX, active.uiWidth());
        for (String candidate : candidates) {
            ResourceLocation themed =
                    ResourceLocation.fromNamespaceAndPath("xenopixelsmod", candidate);
            if (Minecraft.getInstance().getResourceManager().getResource(themed).isPresent()) {
                return themed;
            }
        }
        // Every candidate absent means the sheet was never generated for this slot. DragonMineZ's
        // own art is the fallback, so a missing file shows as unthemed rather than as a magenta
        // missing-texture block.
        return original;
    }
}

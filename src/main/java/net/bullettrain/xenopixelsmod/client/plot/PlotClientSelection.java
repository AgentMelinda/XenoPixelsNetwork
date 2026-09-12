package net.bullettrain.xenopixelsmod.client.plot;

import net.bullettrain.xenopixelsmod.compat.worldedit.WorldEditBridge;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Reads the local player's WorldEdit selection for display.
 *
 * <p>Uses {@link WorldEditBridge} so no WorldEdit type is referenced at compile time. Only X and Z
 * are reported: a plot is a column extent and Y is discarded.</p>
 *
 * <p>Client-side only, and read-only — it never claims or mutates anything.</p>
 */
public final class PlotClientSelection {

    private PlotClientSelection() {
    }

    /** A plot selection's 2-D footprint. Y is deliberately absent. */
    public record Bounds(int minX, int minZ, int maxX, int maxZ) {

        /** Blocks wide on the X axis. */
        public int width() {
            return Math.abs(maxX - minX) + 1;
        }

        /** Blocks long on the Z axis. */
        public int length() {
            return Math.abs(maxZ - minZ) + 1;
        }
    }

    /**
     * @return a one-line description of the current selection, or {@code null} when there is
     *         nothing worth showing.
     */
    @Nullable
    public static Component describe(LocalPlayer player, ClientLevel level) {
        Bounds bounds = bounds(player, level);
        if (bounds == null) {
            return null;
        }
        int width = bounds.width();
        int length = bounds.length();
        return Component.literal("Plot selection: " + width + " x " + length
                + " (" + (width * length) + " blocks)");
    }

    /**
     * The current selection's footprint, or {@code null} when there is none.
     *
     * <p>Exposed separately from {@link #describe} so a renderer can draw the footprint rather
     * than only printing its size.</p>
     */
    @Nullable
    public static Bounds bounds(LocalPlayer player, ClientLevel level) {
        if (player == null || level == null || !WorldEditBridge.available()) {
            return null;
        }
        Object region = WorldEditBridge.getSelection(player, level);
        if (region == null) {
            return null;
        }
        Integer minX = component(region, "getMinimumPoint", "x");
        Integer minZ = component(region, "getMinimumPoint", "z");
        Integer maxX = component(region, "getMaximumPoint", "x");
        Integer maxZ = component(region, "getMaximumPoint", "z");
        if (minX == null || minZ == null || maxX == null || maxZ == null) {
            return null;
        }
        return new Bounds(minX, minZ, maxX, maxZ);
    }

    @Nullable
    private static Integer component(Object region, String pointMethod, String axis) {
        try {
            Object vector = invoke(region, pointMethod);
            if (vector == null) {
                return null;
            }
            Object value = invoke(vector, axis);
            return value instanceof Number number ? number.intValue() : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    @Nullable
    private static Object invoke(Object target, String name) throws ReflectiveOperationException {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 0) {
                return method.invoke(target);
            }
        }
        return null;
    }
}
package net.bullettrain.xenopixelsmod.plot;

import net.bullettrain.xenopixelsmod.compat.worldedit.WorldEditBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Turns a player's WorldEdit selection into a {@link PlotArea} claim.
 *
 * <p>The selection is read through {@link WorldEditBridge} and its minimum/maximum points are
 * resolved reflectively. Y is read and discarded: only X and Z reach {@link PlotArea}. Everything
 * here degrades to a no-op result when WorldEdit is absent.</p>
 */
public final class PlotSelectionHandler {

    private PlotSelectionHandler() {
    }

    /** Outcome of a claim attempt, for reporting back to the player. */
    public enum Result {
        /** The plot was claimed. */
        CLAIMED,
        /** WorldEdit is not installed. */
        NO_WORLDEDIT,
        /** No finished selection exists. */
        NO_SELECTION,
        /** The selection overlaps an existing claim. */
        OVERLAP,
        /** The selection sits entirely inside an existing claim. */
        ENCLOSED,
        /** The selection would swallow an existing claim. */
        ENCLOSING,
        /** The player was not on a server level. */
        NO_LEVEL
    }

    /**
     * Claims the selection made by {@code player}.
     *
     * @return the outcome; only {@link Result#CLAIMED} mutates stored state.
     */
    public static Result claimSelection(ServerPlayer player, int flags) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return Result.NO_LEVEL;
        }
        if (!WorldEditBridge.available()) {
            return Result.NO_WORLDEDIT;
        }
        PlotArea candidate = selectionToPlot(player, level, player.getUUID(), flags);
        if (candidate == null) {
            return Result.NO_SELECTION;
        }
        PlotManager manager = PlotManager.get(level.getServer());
        if (!manager.claim(candidate)) {
            return rejectionReason(manager, candidate);
        }
        // Mirror the claim into YAWP so one boundary protects the plot and defines what was sold.
        // Best-effort: with YAWP absent the claim still stands, just unprotected.
        PlotYaWP.sync(level, candidate);
        return Result.CLAIMED;
    }

    /**
     * Converts the player's current WorldEdit selection to a {@link PlotArea}, or {@code null}
     * when there is no usable selection. Public so the conversion can be exercised independently
     * of a claim.
     */
    @Nullable
    public static PlotArea selectionToPlot(ServerPlayer player, ServerLevel level, UUID owner, int flags) {
        Object region = WorldEditBridge.getSelection(player, level);
        if (region == null) {
            return null;
        }
        BlockPos min = point(region, "getMinimumPoint");
        BlockPos max = point(region, "getMaximumPoint");
        if (min == null || max == null) {
            return null;
        }
        ResourceLocation dimension = level.dimension().location();
        return PlotArea.of(dimension, min.getX(), min.getZ(), max.getX(), max.getZ(), owner, flags);
    }

    /**
     * Names why a claim was refused.
     *
     * <p>Containment is reported before a plain overlap because it is the case a player can act on:
     * a claim fully inside someone else's plot needs a different selection, while a claim that
     * would swallow one needs to be shrunk. An intersection that is neither is just a clash.</p>
     */
    private static Result rejectionReason(PlotManager manager, PlotArea candidate) {
        for (PlotArea plot : manager.all()) {
            if (!plot.overlaps(candidate)) {
                continue;
            }
            if (plot.encloses(candidate)) {
                return Result.ENCLOSED;
            }
            if (candidate.encloses(plot)) {
                return Result.ENCLOSING;
            }
        }
        return Result.OVERLAP;
    }

    @Nullable
    private static BlockPos point(Object region, String methodName) {
        Method method = findMethod(region.getClass(), methodName);
        if (method == null) {
            return null;
        }
        try {
            Object vector = method.invoke(region);
            if (vector == null) {
                return null;
            }
            // WorldEdit's BlockVector3 exposes x()/y()/z(); resolve them reflectively so no
            // WorldEdit type is referenced at compile time. Only X and Z survive.
            return new BlockPos(component(vector, "x"), component(vector, "y"), component(vector, "z"));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static int component(Object vector, String axis) throws ReflectiveOperationException {
        Method method = findMethod(vector.getClass(), axis);
        if (method == null) {
            throw new NoSuchMethodException(vector.getClass().getName() + "." + axis);
        }
        return ((Number) method.invoke(vector)).intValue();
    }

    @Nullable
    private static Method findMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 0) {
                return method;
            }
        }
        return null;
    }
}
package net.bullettrain.xenopixelsmod.compat.worldedit;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Optional reflection bridge for WorldEdit's player selection.
 *
 * <p>WorldEdit is a {@code compileOnly} dependency and optional at runtime; the class is loaded
 * only when {@link #available()} is true. Everything is resolved reflectively so the mod loads and
 * the plot feature reports "unavailable" when WorldEdit is absent.</p>
 *
 * <p>Only the selection read path is used: {@code WorldEdit.getInstance()},
 * {@code getSessionManager()}, {@code SessionManager.get(Player)} and
 * {@code LocalSession.getSelection(World)}. The selection's minimum/maximum points supply X/Z and
 * Y is discarded by {@code PlotArea}. Signatures are recorded in {@code docs/shops-and-plots.md}.</p>
 */
public final class WorldEditBridge {

    private static final String WORLD_EDIT = "com.sk89q.worldedit.WorldEdit";
    private static final String SESSION_MANAGER = "com.sk89q.worldedit.session.SessionManager";
    private static final String INCOMPLETE_REGION = "com.sk89q.worldedit.IncompleteRegionException";
    private static final String SESSION_OWNER = "com.sk89q.worldedit.session.SessionOwner";
    private static final String WORLD = "com.sk89q.worldedit.world.World";
    /** WorldEdit 7.3.8: {@code adaptPlayer(ServerPlayer)} and {@code adapt(ServerLevel)}. */
    private static final String ADAPTER = "com.sk89q.worldedit.neoforge.NeoForgeAdapter";
    /** One stack trace per session: this is reached every frame from the plot HUD. */
    private static volatile boolean failureLogged;

    private WorldEditBridge() {
    }

    /** True when WorldEdit is present and its entry points resolve. */
    public static boolean available() {
        Class<?> worldEdit = load(WORLD_EDIT);
        if (worldEdit == null) {
            return false;
        }
        Method instance = findMethod(worldEdit, "getInstance", 0);
        return instance != null && findMethod(load(SESSION_MANAGER), "get", 1) != null;
    }

    /**
     * @return the player's selected region, or {@code null} when WorldEdit is absent, the player
     *         has no session, or the selection is incomplete. An incomplete selection is a normal
     *         control path, not an error.
     */
    @Nullable
    public static Object getSelection(Object player, Object world) {
        if (player == null || world == null || !available()) {
            return null;
        }
        // WorldEdit's session manager and selection take WorldEdit's own player and world, not
        // Minecraft's. Handing it a Minecraft object threw "argument type mismatch" on every call
        // (2026-10-02: 7,198 stack traces in four minutes of play from the plot HUD). A client
        // player or level has no WorldEdit counterpart: that is "no selection", not an error.
        Object owner = adapt(player, "adaptPlayer", SESSION_OWNER);
        Object weWorld = adapt(world, "adapt", WORLD);
        if (owner == null || weWorld == null) {
            return null;
        }
        Object session = session(owner);
        if (session == null) {
            return null;
        }
        try {
            Method getSelection = findMethod(session.getClass(), "getSelection", 1);
            if (getSelection == null) {
                return null;
            }
            return getSelection.invoke(session, weWorld);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (isIncompleteRegion(exception)) {
                return null;
            }
            logOnce("reading the selection", exception);
            return null;
        }
    }

    /**
     * Full 3D block bounds of the player's WorldEdit selection (min/max inclusive).
     * Used by tournament arena setup; plots still discard Y via {@code PlotSelectionHandler}.
     */
    @Nullable
    public static Bounds3d getSelectionBounds3d(Object player, Object world) {
        Object region = getSelection(player, world);
        if (region == null) {
            return null;
        }
        BlockPos min = point(region, "getMinimumPoint");
        BlockPos max = point(region, "getMaximumPoint");
        if (min == null || max == null) {
            return null;
        }
        return new Bounds3d(
                new BlockPos(Math.min(min.getX(), max.getX()), Math.min(min.getY(), max.getY()),
                        Math.min(min.getZ(), max.getZ())),
                new BlockPos(Math.max(min.getX(), max.getX()), Math.max(min.getY(), max.getY()),
                        Math.max(min.getZ(), max.getZ())));
    }

    /** Inclusive WorldEdit cuboid corners. */
    public record Bounds3d(BlockPos min, BlockPos max) {
    }

    @Nullable
    private static BlockPos point(Object region, String methodName) {
        Method method = findMethod(region.getClass(), methodName, 0);
        if (method == null) {
            return null;
        }
        try {
            Object vector = method.invoke(region);
            if (vector == null) {
                return null;
            }
            return new BlockPos(component(vector, "x"), component(vector, "y"), component(vector, "z"));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static int component(Object vector, String axis) throws ReflectiveOperationException {
        Method method = findMethod(vector.getClass(), axis, 0);
        if (method == null) {
            throw new NoSuchMethodException(vector.getClass().getName() + "." + axis);
        }
        return ((Number) method.invoke(vector)).intValue();
    }

    /**
     * {@code value} as the WorldEdit type {@code targetType}: itself when it already is one,
     * otherwise through the {@code NeoForgeAdapter} method of that name that accepts it.
     */
    @Nullable
    private static Object adapt(Object value, String adapterMethod, String targetType) {
        Class<?> target = load(targetType);
        if (target == null) {
            return null;
        }
        if (target.isInstance(value)) {
            return value;
        }
        Class<?> adapter = load(ADAPTER);
        if (adapter == null) {
            return null;
        }
        for (Method method : adapter.getMethods()) {
            if (!method.getName().equals(adapterMethod) || method.getParameterCount() != 1
                    || !method.getParameterTypes()[0].isInstance(value)
                    || !target.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            try {
                return method.invoke(null, value);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                logOnce("adapting " + value.getClass().getSimpleName(), exception);
                return null;
            }
        }
        return null;
    }

    private static void logOnce(String what, Throwable exception) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        XenoPixelsMod.LOGGER.error("WorldEdit bridge failed {} (logged once)", what, exception);
    }

    @Nullable
    private static Object session(Object player) {
        Object worldEdit = invokeStatic(WORLD_EDIT, "getInstance");
        if (worldEdit == null) {
            return null;
        }
        Object sessionManager = invoke(worldEdit, worldEdit.getClass().getName(), "getSessionManager");
        if (sessionManager == null) {
            return null;
        }
        return invoke(sessionManager, SESSION_MANAGER, "get", player);
    }

    private static boolean isIncompleteRegion(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause.getClass().getName().equals(INCOMPLETE_REGION)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static Object invokeStatic(String typeName, String name, Object... args) {
        Class<?> type = load(typeName);
        if (type == null) {
            return null;
        }
        Method method = findMethod(type, name, args.length);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(null, args);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logOnce("calling " + name, exception);
            return null;
        }
    }

    @Nullable
    private static Object invoke(Object target, String typeName, String name, Object... args) {
        Class<?> type = load(typeName);
        if (type == null) {
            return null;
        }
        Method method = findMethod(type, name, args.length);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logOnce("calling " + name, exception);
            return null;
        }
    }

    @Nullable
    private static Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | LinkageError exception) {
            return null;
        }
    }

    @Nullable
    private static Method findMethod(Class<?> type, String name, int parameterCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        return null;
    }
}
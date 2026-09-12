package net.bullettrain.xenopixelsmod.compat.worldedit;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;

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
        Object session = session(player);
        if (session == null) {
            return null;
        }
        try {
            Method getSelection = findMethod(session.getClass(), "getSelection", 1);
            if (getSelection == null) {
                return null;
            }
            return getSelection.invoke(session, world);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (isIncompleteRegion(exception)) {
                return null;
            }
            XenoPixelsMod.LOGGER.error("WorldEdit bridge failed reading the selection", exception);
            return null;
        }
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
            XenoPixelsMod.LOGGER.error("WorldEdit bridge failed calling {}", name, exception);
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
            XenoPixelsMod.LOGGER.error("WorldEdit bridge failed calling {}", name, exception);
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
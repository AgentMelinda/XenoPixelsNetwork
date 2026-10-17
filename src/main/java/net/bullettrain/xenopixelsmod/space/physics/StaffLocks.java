package net.bullettrain.xenopixelsmod.space.physics;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Method;

/**
 * Reflective bridge to Create Simulated's physics staff locks (shipped inside Create Aeronautics; not a
 * compile dependency). A staff lock is a fixed joint from the body to the world, saved with the level: a
 * locked planet cannot move at all, so its orbit controller can never bring it home. Signatures checked
 * with javap against simulated-neoforge-1.21.1-1.3.0: {@code PhysicsStaffServerHandler.get(ServerLevel)},
 * {@code isLocked(SubLevel)}, {@code removeLock(SubLevel)}.
 */
final class StaffLocks {
    private static final String HANDLER = "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler";
    private static boolean resolved;
    private static Method get, isLocked, removeLock;

    private StaffLocks() {}

    private static boolean resolve() {
        if (!resolved) {
            resolved = true;
            try {
                Class<?> type = Class.forName(HANDLER);
                get = type.getMethod("get", ServerLevel.class);
                isLocked = type.getMethod("isLocked", SubLevel.class);
                removeLock = type.getMethod("removeLock", SubLevel.class);
            } catch (ClassNotFoundException absent) {
                get = null;
            } catch (ReflectiveOperationException | LinkageError changed) {
                get = null;
                XenoPixelsMod.LOGGER.warn("Simulated physics staff API changed; planet lock release disabled: {}", changed.toString());
            }
        }
        return get != null;
    }

    static boolean isLocked(ServerLevel level, SubLevel body) {
        if (!resolve()) return false;
        try { return (boolean) isLocked.invoke(get.invoke(null, level), body); }
        catch (ReflectiveOperationException | RuntimeException failure) { return false; }
    }

    /** Removes a staff lock from {@code body}; returns true when one was removed. */
    static boolean release(ServerLevel level, SubLevel body) {
        if (!resolve()) return false;
        try {
            Object handler = get.invoke(null, level);
            if (!(boolean) isLocked.invoke(handler, body)) return false;
            removeLock.invoke(handler, body);
            return true;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            XenoPixelsMod.LOGGER.warn("Could not release a physics staff lock on a managed planet: {}", failure.toString());
            return false;
        }
    }
}

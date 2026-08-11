package net.bullettrain.xenopixelsmod.compat.yawp;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Resolves which YAWP region governs a position, by reflection.
 *
 * <p><b>Why reflection.</b> YAWP's region API is not stable across the 0.6 line:
 * {@code RegionManager.getDimRegionApi} returns {@code Optional<IDimensionRegionApi>} on
 * 0.6.2-beta1 and {@code Optional<ILevelRegionApi>} on 0.6.3-beta3 — the interface was
 * renamed — and the save methods differ too. Linking against either signature would throw
 * {@link NoClassDefFoundError} on the other version at runtime.
 *
 * <p>That is precisely the failure mode that took this server down through AeroStar, which
 * pinned one Northstar version and died when a class moved. Reflection keeps this working on
 * any 0.6.x, at the cost of compile-time checking.
 *
 * <p>Deliberately holds <b>no YAWP imports</b>, so it is safe to load even when YAWP is
 * absent — every lookup simply returns empty.
 */
public final class YawpRegionLookup {
    private static final String REGION_MANAGER = "de.z0rdak.yawp.api.core.RegionManager";

    private static boolean unavailableLogged;
    /** Cached reflective handles; resolved once on first successful use. */
    private static Method getInstance;
    private static Method getDimRegionApi;
    private static boolean resolved;

    private YawpRegionLookup() {
    }

    /**
     * Highest-priority active region containing {@code pos}.
     *
     * @return the region's name, or empty when YAWP is absent, the dimension is untracked, or
     *         no active region covers the position
     */
    public static Optional<String> regionNameAt(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel) || pos == null) return Optional.empty();
        try {
            if (!resolve()) return Optional.empty();

            Object manager = getInstance.invoke(null);
            if (manager == null) return Optional.empty();

            ResourceKey<Level> dim = serverLevel.dimension();
            Object apiOptional = getDimRegionApi.invoke(manager, dim);
            Object api = unwrap(apiOptional);
            if (api == null) return Optional.empty();

            // Declared on the dimension API interface under both names; resolved off the
            // concrete class so the rename does not matter.
            Method involved = api.getClass().getMethod("getInvolvedRegionFor", BlockPos.class);
            involved.setAccessible(true);
            Object region = unwrap(involved.invoke(api, pos));
            if (region == null) return Optional.empty();

            Method getName = region.getClass().getMethod("getName");
            getName.setAccessible(true);
            Object name = getName.invoke(region);
            return name instanceof String text && !text.isBlank()
                    ? Optional.of(text) : Optional.empty();
        } catch (Throwable t) {
            logUnavailableOnce(t);
            return Optional.empty();
        }
    }

    private static synchronized boolean resolve() throws ReflectiveOperationException {
        if (resolved) return getInstance != null && getDimRegionApi != null;
        resolved = true;
        Class<?> managerClass = Class.forName(REGION_MANAGER, false,
                YawpRegionLookup.class.getClassLoader());
        getInstance = managerClass.getMethod("get");
        getDimRegionApi = managerClass.getMethod("getDimRegionApi", ResourceKey.class);
        return true;
    }

    private static Object unwrap(Object maybeOptional) {
        if (maybeOptional instanceof Optional<?> optional) return optional.orElse(null);
        return maybeOptional;
    }

    /**
     * Warn once and then stay quiet — this runs on the ki-griefing hot path, and a YAWP
     * version we cannot read must degrade silently rather than spam the log every blast.
     */
    private static void logUnavailableOnce(Throwable t) {
        if (unavailableLogged) return;
        unavailableLogged = true;
        XenoPixelsMod.LOGGER.warn(
                "YAWP region lookup unavailable; per-region ki-griefing flags will be ignored "
                        + "and the configured YAWP flag mapping used instead", t);
    }
}

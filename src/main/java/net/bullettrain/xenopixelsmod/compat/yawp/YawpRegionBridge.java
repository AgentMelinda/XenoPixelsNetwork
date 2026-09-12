package net.bullettrain.xenopixelsmod.compat.yawp;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Creates and removes the YAWP local region that backs a plot, by reflection.
 *
 * <p><b>Why reflection, when {@code YawpKiGriefing} imports YAWP directly.</b> The two compat
 * classes target different parts of YAWP's surface. {@code YawpKiGriefing} uses
 * {@code FlagPermissions} and the {@code RegionFlag} enum, which have kept their names across the
 * 0.6 line. Region <i>management</i> has not:
 * {@code RegionManager.getDimRegionApi} returned {@code Optional<IDimensionRegionApi>} on
 * 0.6.2-beta1 and {@code Optional<ILevelRegionApi>} on 0.6.3-beta3, and the save methods differ.
 * Linking this class against {@code ILevelRegionApi} would throw {@link NoClassDefFoundError} on
 * the older build — the exact failure {@link YawpRegionLookup} exists to avoid. This bridge
 * therefore holds <b>no YAWP imports</b> and mirrors {@code YawpRegionLookup}'s pattern.</p>
 *
 * <p>Every entry point degrades to a no-op when YAWP is absent, when the classes moved, or when a
 * call throws. A plot claim must still succeed when its YAWP mirror cannot be written; the plot
 * is then simply not protected by YAWP.</p>
 *
 * <p>All signatures were read from {@code yawp-1.21.1-0.6.3-beta3.jar} with {@code javap} and are
 * recorded in {@code docs/shops-and-plots.md}.</p>
 */
public final class YawpRegionBridge {

    private static final String MOD_ID = "yawp";
    private static final String REGION_MANAGER = "de.z0rdak.yawp.api.core.RegionManager";
    private static final String CUBOID_AREA_BUILDER = "de.z0rdak.yawp.api.core.area.CuboidBuilder";
    private static final String CUBOID_REGION_BUILDER = "de.z0rdak.yawp.api.core.region.CuboidRegionBuilder";
    private static final String CUBOID_AREA = "de.z0rdak.yawp.core.area.CuboidArea";
    private static final String I_MARKABLE_REGION = "de.z0rdak.yawp.core.region.IMarkableRegion";
    private static final String RESOURCE_KEY = "net.minecraft.resources.ResourceKey";
    private static final String BLOCK_POS = "net.minecraft.core.BlockPos";

    private static boolean unavailableLogged;

    private YawpRegionBridge() {
    }

    /** True when YAWP is loaded and its region manager resolves. */
    public static boolean available() {
        return ModList.get().isLoaded(MOD_ID) && regionManagerClass() != null;
    }

    /**
     * Creates or refreshes the YAWP local region named {@code name} covering the full column of
     * {@code (minX,minZ)-(maxX,maxZ)} in {@code dimension}.
     *
     * <p>An existing region of the same name is removed first rather than re-added. YAWP's
     * duplicate-name behaviour is not verified, so reusing a name must not depend on it.</p>
     *
     * @return true when a region now exists under {@code name}.
     */
    public static boolean ensureRegion(String name, ResourceKey<Level> dimension,
                                       int minX, int minZ, int maxX, int maxZ,
                                       int minY, int maxY) {
        if (name == null || name.isBlank() || dimension == null) {
            return false;
        }
        try {
            Object manager = regionManager();
            Object api = levelApi(manager, dimension);
            if (api == null) {
                return false;
            }
            Object existing = unwrap(invokeWithArgs(api, "getLocalRegion", "java.lang.String", name));
            if (existing != null) {
                invokeWithArgs(api, "removeLocalRegion", "java.lang.String", name);
            }
            Object area = cuboidArea(minX, minY, minZ, maxX, maxY, maxZ);
            Object region = cuboidRegion(name, area, dimension);
            if (area == null || region == null) {
                return false;
            }
            Object added = invokeWithArgs(api, "addLocalRegion", I_MARKABLE_REGION, region);
            if (added instanceof Boolean ok && !ok) {
                return false;
            }
            save(manager, dimension);
            return true;
        } catch (Throwable t) {
            logUnavailableOnce(t);
            return false;
        }
    }

    /**
     * Removes the YAWP region named {@code name}.
     *
     * @return true when a region was removed, or when none existed to begin with.
     */
    public static boolean removeRegion(String name, ResourceKey<Level> dimension) {
        if (name == null || name.isBlank() || dimension == null) {
            return false;
        }
        try {
            Object manager = regionManager();
            Object api = levelApi(manager, dimension);
            if (api == null) {
                return false;
            }
            Object existing = unwrap(invokeWithArgs(api, "getLocalRegion", "java.lang.String", name));
            if (existing == null) {
                return true;
            }
            invokeWithArgs(api, "removeLocalRegion", "java.lang.String", name);
            save(manager, dimension);
            return true;
        } catch (Throwable t) {
            logUnavailableOnce(t);
            return false;
        }
    }

    /** True when a local region named {@code name} exists in {@code dimension}. */
    public static boolean hasRegion(String name, ResourceKey<Level> dimension) {
        if (name == null || name.isBlank() || dimension == null) {
            return false;
        }
        try {
            Object api = levelApi(regionManager(), dimension);
            return api != null && unwrap(invokeWithArgs(api, "getLocalRegion", "java.lang.String", name)) != null;
        } catch (Throwable t) {
            logUnavailableOnce(t);
            return false;
        }
    }

    /**
     * Adds {@code player} to the region's {@code owners} group, so a sold plot's new owner is also
     * recognised by YAWP.
     *
     * @return true when the call was made without error.
     */
    public static boolean addOwner(String name, ResourceKey<Level> dimension, Object player) {
        if (name == null || name.isBlank() || dimension == null || player == null) {
            return false;
        }
        try {
            Object api = levelApi(regionManager(), dimension);
            if (api == null) {
                return false;
            }
            Object region = unwrap(invokeWithArgs(api, "getLocalRegion", "java.lang.String", name));
            if (region == null) {
                return false;
            }
            invokeWithArgs(region, "addPlayer", "net.minecraft.world.entity.player.Player", "java.lang.String",
                    player, "owners");
            save(regionManager(), dimension);
            return true;
        } catch (Throwable t) {
            logUnavailableOnce(t);
            return false;
        }
    }

    @Nullable
    private static Object cuboidArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        Class<?> builderClass = load(CUBOID_AREA_BUILDER);
        if (builderClass == null) {
            return null;
        }
        try {
            Object builder = builderClass.getDeclaredConstructor().newInstance();
            Object withArea = invokeWithArgs(builder, "area", BLOCK_POS + "," + BLOCK_POS,
                    new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
            if (withArea == null) {
                return null;
            }
            return invokeNoArgs(withArea, "build");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logUnavailableOnce(exception);
            return null;
        }
    }

    @Nullable
    private static Object cuboidRegion(String name, Object area, ResourceKey<Level> dimension) {
        Class<?> builderClass = load(CUBOID_REGION_BUILDER);
        if (builderClass == null) {
            return null;
        }
        try {
            Constructor<?> constructor = builderClass.getConstructor(String.class);
            Object builder = constructor.newInstance(name);
            builder = invokeWithArgs(builder, "setArea", CUBOID_AREA, area);
            if (builder == null) {
                return null;
            }
            // inDim is overloaded (ResourceKey and Level); match the ResourceKey form by type.
            builder = invokeWithArgs(builder, "inDim", RESOURCE_KEY, dimension);
            if (builder == null) {
                return null;
            }
            Object active = invokeNoArgs(builder, "on");
            if (active != null) {
                builder = active;
            }
            return invokeNoArgs(builder, "build");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logUnavailableOnce(exception);
            return null;
        }
    }

    @Nullable
    private static Object regionManager() {
        Class<?> type = regionManagerClass();
        if (type == null) {
            return null;
        }
        return invokeStaticNoArgs(type, "get");
    }

    @Nullable
    private static Class<?> regionManagerClass() {
        return load(REGION_MANAGER);
    }

    /** Resolves {@code getDimRegionApi(ResourceKey)} and unwraps its Optional. */
    @Nullable
    private static Object levelApi(@Nullable Object manager, ResourceKey<Level> dimension) {
        if (manager == null) {
            return null;
        }
        return unwrap(invokeWithArgs(manager, "getDimRegionApi", RESOURCE_KEY, dimension));
    }

    private static void save(@Nullable Object manager, ResourceKey<Level> dimension) {
        if (manager == null) {
            return;
        }
        // save is overloaded; the ResourceKey form is the one that takes the dimension we changed.
        invokeWithArgs(manager, "save", RESOURCE_KEY, dimension);
    }

    @Nullable
    private static Class<?> load(String name) {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return null;
        }
        try {
            return Class.forName(name, false, YawpRegionBridge.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError exception) {
            return null;
        }
    }

    @Nullable
    private static Object invokeStaticNoArgs(Class<?> type, String name) {
        Method method = method(type, name, new String[0]);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logUnavailableOnce(exception);
            return null;
        }
    }

    /** Invokes the no-argument method {@code name} on {@code target}. */
    @Nullable
    private static Object invokeNoArgs(@Nullable Object target, String name) {
        if (target == null) {
            return null;
        }
        Method method = method(target.getClass(), name, new String[0]);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logUnavailableOnce(exception);
            return null;
        }
    }

    /**
     * Invokes {@code name} with arguments, matching the overload by parameter type names.
     *
     * <p>Type-name matching is required rather than a parameter count: {@code RegionManager.save}
     * has three one-argument overloads and {@code LocalRegionBuilder.inDim} has two, so a
     * count-only lookup would be ambiguous.</p>
     */
    @Nullable
    private static Object invokeWithArgs(@Nullable Object target, String name, String paramTypes,
                                 Object... args) {
        if (target == null) {
            return null;
        }
        Method method = method(target.getClass(), name, paramTypes.split(","));
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logUnavailableOnce(exception);
            return null;
        }
    }

    @Nullable
    private static Method method(Class<?> type, String name, String[] paramTypes) {
        for (Method candidate : type.getMethods()) {
            if (!candidate.getName().equals(name)
                    || candidate.getParameterCount() != paramTypes.length) {
                continue;
            }
            Class<?>[] actual = candidate.getParameterTypes();
            boolean matches = true;
            for (int i = 0; i < actual.length; i++) {
                if (!actual[i].getName().equals(paramTypes[i])) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return candidate;
            }
        }
        return null;
    }

    private static Object unwrap(Object maybeOptional) {
        if (maybeOptional instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        return maybeOptional;
    }

    /** Warn once and stay quiet; this runs on the claim path and must not spam the log. */
    private static void logUnavailableOnce(Throwable t) {
        if (unavailableLogged) {
            return;
        }
        unavailableLogged = true;
        XenoPixelsMod.LOGGER.warn(
                "YAWP region bridge unavailable; plots will claim and sell without YAWP "
                        + "protection", t);
    }
}
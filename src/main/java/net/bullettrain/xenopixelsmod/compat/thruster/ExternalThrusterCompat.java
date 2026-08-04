package net.bullettrain.xenopixelsmod.compat.thruster;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dependency-free bridge for propulsion blocks owned by other VS/Create mods.
 *
 * <p>External engines keep applying their own native VS force. Missile guidance applies
 * its stabilised center-of-mass force separately, so importing another mod's private
 * physics attachment would double thrust and torque the craft. This bridge supplies
 * discovery, initial direction and best-effort visual/throttle control only.</p>
 */
public final class ExternalThrusterCompat {
    private static final Set<String> KNOWN_NAMESPACES = Set.of(
            "createpropulsion", "vsch", "vs_clockwork", "clockwork",
            "genesis", "vsgenesis", "zps", "zeropointsystems",
            "warium", "cbcwarium", "cbc_warium");
    private static final String[] ENGINE_WORDS = {
            "thruster", "propeller", "rocket_engine", "jet_engine",
            "ion_engine", "engine_nozzle", "propulsion"
    };
    private static final String[] THROTTLE_METHODS = {
            "setGuidanceThrottle", "setThrottle", "setThrottleLevel", "setPower", "setPowerLevel"
    };
    private static final Map<Class<?>, ThrottleSetter> SETTERS = new ConcurrentHashMap<>();
    private static final ThrottleSetter NO_SETTER = (target, throttle) -> false;

    private ExternalThrusterCompat() {
    }

    public static boolean isCompatible(BlockState state) {
        if (state == null || state.isAir()) return false;
        if (state.is(Tags.COMPATIBLE_THRUSTERS)) return true;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return isKnownNamespacePath(id.getNamespace(), id.getPath());
    }

    /** Kept independent of registries so compatibility matching can be unit tested. */
    static boolean isKnownNamespacePath(String namespace, String path) {
        String ns = namespace == null ? "" : namespace.toLowerCase(Locale.ROOT);
        String compactNs = ns.replace("_", "").replace("-", "");
        String name = path == null ? "" : path.toLowerCase(Locale.ROOT);
        boolean known = KNOWN_NAMESPACES.contains(ns)
                || compactNs.contains("starlance") || compactNs.contains("clockwork")
                || compactNs.contains("genesis") || compactNs.contains("warium")
                || compactNs.contains("propulsion") || compactNs.contains("zeropoint");
        if (!known) return false;
        for (String word : ENGINE_WORDS) {
            if (name.contains(word)) return true;
        }
        return false;
    }

    /**
     * Finds a directional state property without importing the other mod. Most VS
     * propulsion blocks expose facing/direction as a normal Minecraft property.
     * The returned direction is opposite the nozzle/exhaust-facing direction.
     */
    public static @Nullable Direction thrustDirection(BlockState state) {
        if (state == null) return null;
        Direction fallback = null;
        for (Property<?> property : state.getProperties()) {
            Comparable<?> value = state.getValue(property);
            if (!(value instanceof Direction direction)) continue;
            String name = property.getName().toLowerCase(Locale.ROOT);
            if (name.contains("facing") || name.contains("direction") || name.contains("orientation")) {
                return direction.getOpposite();
            }
            fallback = direction.getOpposite();
        }
        return fallback;
    }

    /**
     * Best-effort control for public BE throttle APIs. Missing or changed APIs are
     * cached as unsupported and never prevent launch. Native redstone remains valid.
     */
    public static boolean setThrottle(Level level, BlockPos pos, double throttle) {
        if (level == null || pos == null) return false;
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null) return false;
        double safe = Math.max(0.0, Math.min(1.0, throttle));
        ThrottleSetter setter = SETTERS.computeIfAbsent(entity.getClass(), ExternalThrusterCompat::findSetter);
        try {
            return setter.set(entity, safe);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static ThrottleSetter findSetter(Class<?> type) {
        for (String name : THROTTLE_METHODS) {
            for (Class<?> parameter : new Class<?>[]{double.class, float.class, int.class}) {
                try {
                    Method method = type.getMethod(name, parameter);
                    return (target, throttle) -> {
                        Object value = parameter == float.class ? (float) throttle
                                : parameter == int.class ? (int) Math.round(throttle * 15.0) : throttle;
                        method.invoke(target, value);
                        return true;
                    };
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        return NO_SETTER;
    }

    @FunctionalInterface
    private interface ThrottleSetter {
        boolean set(Object target, double throttle) throws Exception;
    }

    /** Avoid bootstrapping Minecraft registries when pure matching tests load this class. */
    private static final class Tags {
        private static final TagKey<Block> COMPATIBLE_THRUSTERS = BlockTags.create(
                new ResourceLocation(XenoPixelsMod.MOD_ID, "compatible_thrusters"));
    }
}

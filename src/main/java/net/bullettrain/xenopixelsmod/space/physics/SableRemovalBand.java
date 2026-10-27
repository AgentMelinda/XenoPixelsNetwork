package net.bullettrain.xenopixelsmod.space.physics;

import dev.ryanhcode.sable.SableConfig;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.minecraft.resources.ResourceLocation;

/** Single policy source for the Y bounds Sable uses when deciding whether to remove a sub-level. */
public final class SableRemovalBand {
    private SableRemovalBand() {}

    public record Band(double minY, double maxY, boolean voidseaExtended) {
        public Band {
            // Sable's own DoubleValue bounds explicitly permit +/-Infinity. Preserve that supported
            // configuration outside the Voidsea host while still rejecting NaN and inverted bands.
            if (Double.isNaN(minY) || Double.isNaN(maxY) || minY > maxY)
                throw new IllegalArgumentException("Sable removal band must be ordered and not NaN");
        }
    }

    /** Resolve the current configured Sable band and the Voidsea-only extension switch. */
    public static Band forDimension(ResourceLocation dimension) {
        return resolve(dimension, PhysicalEarth.HOST, XenoSpaceConfig.voidseaUnboundedHeight(),
                SableConfig.SUB_LEVEL_REMOVE_MIN.getAsDouble(), SableConfig.SUB_LEVEL_REMOVE_MAX.getAsDouble(),
                XenoSpaceConfig.VOIDSEA_HEIGHT_LIMIT);
    }

    /** Allocation-free redirect helpers for Sable's per-sublevel tick checks. */
    public static double minimumForDimension(ResourceLocation dimension, double configuredMin) {
        return resolveMinimum(dimension, PhysicalEarth.HOST, XenoSpaceConfig.voidseaUnboundedHeight(),
                configuredMin, XenoSpaceConfig.VOIDSEA_HEIGHT_LIMIT);
    }

    public static double maximumForDimension(ResourceLocation dimension, double configuredMax) {
        return resolveMaximum(dimension, PhysicalEarth.HOST, XenoSpaceConfig.voidseaUnboundedHeight(),
                configuredMax, XenoSpaceConfig.VOIDSEA_HEIGHT_LIMIT);
    }

    /** Pure dimension gate, including the condition that only the configured Voidsea host is extended. */
    public static Band resolve(ResourceLocation dimension, ResourceLocation voidseaHost, boolean extensionEnabled,
                               double configuredMin, double configuredMax, double limit) {
        boolean extended = extensionEnabled && voidseaHost != null && voidseaHost.equals(dimension);
        return resolve(extended, configuredMin, configuredMax, limit);
    }

    public static double resolveMinimum(ResourceLocation dimension, ResourceLocation voidseaHost,
                                        boolean extensionEnabled, double configuredMin, double limit) {
        if (Double.isNaN(configuredMin) || !Double.isFinite(limit) || limit <= 0.0)
            throw new IllegalArgumentException("invalid minimum Sable removal-band inputs");
        return isExtendedDimension(dimension, voidseaHost, extensionEnabled) ? -limit : configuredMin;
    }

    public static double resolveMaximum(ResourceLocation dimension, ResourceLocation voidseaHost,
                                        boolean extensionEnabled, double configuredMax, double limit) {
        if (Double.isNaN(configuredMax) || !Double.isFinite(limit) || limit <= 0.0)
            throw new IllegalArgumentException("invalid maximum Sable removal-band inputs");
        return isExtendedDimension(dimension, voidseaHost, extensionEnabled) ? limit : configuredMax;
    }

    private static boolean isExtendedDimension(ResourceLocation dimension, ResourceLocation voidseaHost,
                                               boolean extensionEnabled) {
        return extensionEnabled && voidseaHost != null && voidseaHost.equals(dimension);
    }

    /** Pure policy function shared with tests. */
    public static Band resolve(boolean voidseaExtended, double configuredMin, double configuredMax, double limit) {
        if (Double.isNaN(configuredMin) || Double.isNaN(configuredMax) || configuredMin > configuredMax
                || !Double.isFinite(limit) || limit <= 0.0)
            throw new IllegalArgumentException("invalid Sable removal-band inputs");
        return voidseaExtended ? new Band(-limit, limit, true) : new Band(configuredMin, configuredMax, false);
    }
}

package com.lightning.northstar.world.dimension;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * <b>Compatibility shim — this is not XenoPixels API and nothing in this mod should call it.</b>
 *
 * <p>It exists only so that <b>AeroStar</b> ({@code aerostarcomp}) can load on <b>Northstar
 * Redux 0.6.x</b>. AeroStar 1.0.1 — its newest release — pins Redux 0.5.4 and references
 * {@code com.lightning.northstar.world.dimension.NorthstarDimensions}. Redux's "Data-driven
 * planets" change moved that class to {@code com.lightning.northstar.content.world}, so on
 * 0.6.x AeroStar throws {@link NoClassDefFoundError} inside Sable's pre-physics-tick event and
 * kills the server thread on the first tick any sub-level exists — an unrecoverable crash loop.
 *
 * <p>Supplying the class at its old path fixes that at the source. Two earlier attempts to
 * cancel AeroStar's handler with a mixin both failed, because they depended on guessing a
 * method signature in a jar we cannot inspect. This does not: every member below is a plain
 * {@link ResourceKey} or {@link ResourceLocation} over the {@code northstar} namespace, so the
 * values are fully determined.
 *
 * <p><b>Provenance.</b> Reproduced from Northstar Redux commit {@code 4f430cd1}
 * (github.com/Astronauts-of-Create/Northstar-Redux). That file was last modified 2025-09-29 and
 * not touched again until the 2026-05-21 move, so it is exactly what shipped in the 0.5.4
 * release of 2026-01-17 that AeroStar targets. Keys are built with
 * {@link ResourceLocation#fromNamespaceAndPath} rather than northstar's own {@code asResource}
 * helper, so this shim depends only on vanilla types and cannot break on a future refactor.
 *
 * <p><b>Delete this class when either becomes true:</b>
 * <ul>
 *   <li>AeroStar ships a build supporting Redux 0.6+, or</li>
 *   <li>Redux restores a class at this package path — at which point leaving this here would
 *       put two classes at the same fully-qualified name.</li>
 * </ul>
 *
 * <p><b>Known hazard:</b> installing Redux <b>0.5.4</b> alongside this mod creates exactly that
 * duplicate-class situation, because 0.5.4 still provides this class itself. On 0.6.x there is
 * no conflict. This shim is intended for servers on the 0.6 line.
 */
public class NorthstarDimensions {

    private static ResourceLocation northstar(String path) {
        return ResourceLocation.fromNamespaceAndPath("northstar", path);
    }

    public static final ResourceLocation SPACE_EFFECTS = northstar("space");

    // earth orbit
    public static final ResourceKey<Level> EARTH_ORBIT_DIM_KEY =
            ResourceKey.create(Registries.DIMENSION, northstar("earth_orbit"));
    public static final ResourceKey<DimensionType> EARTH_ORBIT_DIM_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, northstar("earth_orbit"));

    // mars
    public static final ResourceKey<Level> MARS_DIM_KEY =
            ResourceKey.create(Registries.DIMENSION, northstar("mars"));
    public static final ResourceKey<DimensionType> MARS_DIM_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, northstar("mars"));
    public static final ResourceLocation MARS_EFFECTS = northstar("mars");

    // venus
    public static final ResourceKey<Level> VENUS_DIM_KEY =
            ResourceKey.create(Registries.DIMENSION, northstar("venus"));
    public static final ResourceKey<DimensionType> VENUS_DIM_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, northstar("venus"));
    public static final ResourceLocation VENUS_EFFECTS = northstar("venus");

    // moon
    public static final ResourceKey<Level> MOON_DIM_KEY =
            ResourceKey.create(Registries.DIMENSION, northstar("moon"));
    public static final ResourceKey<DimensionType> MOON_DIM_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, northstar("moon"));

    // mercury
    public static final ResourceKey<Level> MERCURY_DIM_KEY =
            ResourceKey.create(Registries.DIMENSION, northstar("mercury"));
    public static final ResourceKey<DimensionType> MERCURY_DIM_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, northstar("mercury"));

    /** No-op in the original too; present so callers linking against it still resolve. */
    public static void register() {
    }
}

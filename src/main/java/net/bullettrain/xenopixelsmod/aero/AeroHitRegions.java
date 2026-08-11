package net.bullettrain.xenopixelsmod.aero;

import com.google.gson.Gson;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Clickable regions on the flight controller model.
 *
 * <p>Generated from the model's own cubes by {@code tools/gen_flight_controller_model.py} and
 * shipped as {@code data/xenopixelsmod/aero/flight_controller_hitboxes.json}. This is the
 * point of the exercise: click detection is derived from the geometry rather than from
 * hand-tuned percentages, so moving a button in the generator moves its hitbox with it and
 * there are no magic numbers to drift.
 *
 * <p>Coordinates are block-local ({@code [0,1]} with the origin at the block corner), matching
 * what {@code BlockHitResult.getLocation()} yields after subtracting the block position, so no
 * conversion maths lives on this side.
 */
public final class AeroHitRegions {
    private static final String PATH = "/data/xenopixelsmod/aero/flight_controller_hitboxes.json";
    private static final Gson GSON = new Gson();

    private static List<Region> regions = List.of();
    private static boolean loaded;

    private AeroHitRegions() {
    }

    /**
     * @param name   originating bone, shown by the debug overlay
     * @param action action id agreed with the generator
     */
    public record Region(String name, String action, double[] min, double[] max) {
        public boolean contains(double x, double y, double z) {
            return x >= min[0] && x <= max[0]
                    && y >= min[1] && y <= max[1]
                    && z >= min[2] && z <= max[2];
        }

        public Vec3 center() {
            return new Vec3((min[0] + max[0]) * 0.5, (min[1] + max[1]) * 0.5, (min[2] + max[2]) * 0.5);
        }

        public double distanceSqrToCenter(double x, double y, double z) {
            Vec3 c = center();
            double dx = c.x - x;
            double dy = c.y - y;
            double dz = c.z - z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    public static synchronized List<Region> all() {
        if (!loaded) {
            loaded = true;
            regions = load();
        }
        return regions;
    }

    private static List<Region> load() {
        try (InputStream in = AeroHitRegions.class.getResourceAsStream(PATH)) {
            if (in == null) {
                XenoPixelsMod.LOGGER.warn("Aero hit regions missing at {}; panel clicks disabled", PATH);
                return List.of();
            }
            Data data = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Data.class);
            if (data == null || data.regions == null) return List.of();
            List<Region> valid = new ArrayList<>();
            for (Region region : data.regions) {
                if (region.min() != null && region.max() != null
                        && region.min().length == 3 && region.max().length == 3) {
                    valid.add(region);
                }
            }
            return List.copyOf(valid);
        } catch (Exception e) {
            XenoPixelsMod.LOGGER.warn("Failed to load Aero hit regions; panel clicks disabled", e);
            return List.of();
        }
    }

    /**
     * Region under a block-local hit point, or null.
     *
     * <p>Regions are inflated slightly by the generator so small controls are clickable, which
     * lets neighbours overlap. Nearest-centre wins, so the result is deterministic rather than
     * dependent on declaration order.
     */
    public static Region at(double localX, double localY, double localZ) {
        Region best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Region region : all()) {
            if (!region.contains(localX, localY, localZ)) continue;
            double distance = region.distanceSqrToCenter(localX, localY, localZ);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = region;
            }
        }
        return best;
    }

    /**
     * Rotate a block-local hit point out of world orientation into model space.
     *
     * <p>The model is authored facing north; the block's {@code FACING} turns it. Vertical
     * facings are treated as north because the panel is only meaningful stood upright.
     */
    public static Vec3 toModelSpace(Vec3 local, Direction facing) {
        double x = local.x - 0.5;
        double z = local.z - 0.5;
        double rx;
        double rz;
        switch (facing) {
            case SOUTH -> { rx = -x; rz = -z; }
            case WEST -> { rx = -z; rz = x; }
            case EAST -> { rx = z; rz = -x; }
            default -> { rx = x; rz = z; }
        }
        return new Vec3(rx + 0.5, local.y, rz + 0.5);
    }

    private static final class Data {
        private List<Region> regions;
    }
}

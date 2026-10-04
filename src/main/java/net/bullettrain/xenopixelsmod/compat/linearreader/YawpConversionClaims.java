package net.bullettrain.xenopixelsmod.compat.linearreader;

import de.z0rdak.yawp.api.core.RegionManager;
import de.z0rdak.yawp.core.area.CuboidArea;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashMap;

/** Loaded only with verified YAWP 0.6.3-beta3. No dimension/global fallback is a local claim. */
public final class YawpConversionClaims {
    private YawpConversionClaims() {}

    public static void refresh(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Claim snapshots require the server thread");
        long generation = LinearClaimCoverage.generation();
        var dimensions = new HashMap<String, LinearClaimCoverage.Dimension>();
        var manager = RegionManager.get();
        for (var level : server.getAllLevels()) {
            var api = manager.getDimRegionApi(level.dimension());
            if (api.isEmpty()) continue;
            var claims = new ArrayList<LinearClaimCoverage.Box>();
            for (var region : api.get().getAllLocalRegions()) {
                // Conservative proof: one active cuboid must contain the entire region volume.
                // Partial heights, other shapes and unions remain Anvil rather than guessing.
                if (!region.isActive() || !(region.getArea() instanceof CuboidArea cuboid)) continue;
                var box = cuboid.getArea();
                claims.add(new LinearClaimCoverage.Box(box.minX(), box.minY(), box.minZ(),
                        box.maxX(), box.maxY(), box.maxZ()));
            }
            dimensions.put(level.dimension().location().toString(),
                    new LinearClaimCoverage.Dimension(level.getMinBuildHeight(), level.getMaxBuildHeight() - 1, claims));
        }
        LinearClaimCoverage.publish(generation, dimensions);
    }
}

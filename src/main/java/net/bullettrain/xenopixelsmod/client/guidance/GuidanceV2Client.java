package net.bullettrain.xenopixelsmod.client.guidance;

import net.bullettrain.xenopixelsmod.aero.GuidanceVersion;
import net.bullettrain.xenopixelsmod.aero.v2.GuidanceV2Bus;
import net.bullettrain.xenopixelsmod.aero.v2.GuidanceV2SurfaceMode;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public final class GuidanceV2Client {
    private static GuidanceVersion version = GuidanceVersion.V1;
    private static final Map<BlockPos, GuidanceV2SurfaceMode> SURFACE = new ConcurrentHashMap<>();
    private static @Nullable GuidanceV2Bus bus;
    private static long busReceivedAtMillis;

    private GuidanceV2Client() {}

    public static void acceptVersion(GuidanceVersion next) {
        version = next == null ? GuidanceVersion.V1 : next;
    }

    public static GuidanceVersion version() {
        return version;
    }

    public static boolean isV2() {
        return version.isV2();
    }

    public static void acceptSurfaceMode(BlockPos pos, GuidanceV2SurfaceMode mode) {
        if (pos != null) {
            SURFACE.put(pos.immutable(), mode == null ? GuidanceV2SurfaceMode.THRUST_AND_FLAPS : mode);
        }
    }

    public static GuidanceV2SurfaceMode surfaceMode(BlockPos pos) {
        if (pos == null) {
            return GuidanceV2SurfaceMode.THRUST_AND_FLAPS;
        }
        return SURFACE.getOrDefault(pos, GuidanceV2SurfaceMode.THRUST_AND_FLAPS);
    }

    public static void acceptBus(GuidanceV2Bus next) {
        bus = next;
        busReceivedAtMillis = System.currentTimeMillis();
        if (next != null && next.hostPos() != null) {
            SURFACE.put(next.hostPos().immutable(), next.mode());
        }
    }

    public static @Nullable GuidanceV2Bus bus() {
        if (bus == null) {
            return null;
        }
        if (System.currentTimeMillis() - busReceivedAtMillis > 2_000L) {
            return null;
        }
        return bus;
    }
}

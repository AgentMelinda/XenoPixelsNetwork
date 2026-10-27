package net.bullettrain.xenopixelsmod.client.space;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.event.StellarStage;
import net.bullettrain.xenopixelsmod.space.event.StellarStageSnapshot;
import net.bullettrain.xenopixelsmod.space.event.StellarStageVisual;
import net.bullettrain.xenopixelsmod.space.event.StellarWarning;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Client cache for server-authored chart-star projection appearance; cleared on disconnect. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class StellarStageClient {
    private static Map<String, StellarStageSnapshot.Entry> stars = Map.of();
    private static boolean enabled;
    private static long receivedClientTick;
    /** True when a snapshot arrived with no client level to read the clock from; the first query anchors it. */
    private static boolean anchorPending;
    private static int rampTicks = 1;

    private StellarStageClient() {}

    public static void apply(StellarStageSnapshot packet) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        applyAt(packet, level == null ? null : level.getGameTime());
    }

    /**
     * Stores a snapshot received at {@code clientTickAtReceipt}. With no client level (null) the anchor is taken
     * at the first query instead, so stage ages are not extrapolated from tick zero.
     */
    static void applyAt(StellarStageSnapshot packet, Long clientTickAtReceipt) {
        var copy = new java.util.HashMap<String, StellarStageSnapshot.Entry>();
        for (var entry : packet.stars()) copy.put(entry.id(), entry);
        stars = Map.copyOf(copy);
        enabled = packet.enabled();
        rampTicks = packet.rampTicks();
        anchorPending = clientTickAtReceipt == null;
        receivedClientTick = anchorPending ? 0L : clientTickAtReceipt;
    }

    /** Ticks since the snapshot arrived, by the client clock; anchors a pending snapshot at this tick. */
    private static long elapsedSinceSync(long clientGameTick) {
        if (anchorPending) {
            receivedClientTick = clientGameTick;
            anchorPending = false;
        }
        return Math.max(0L, clientGameTick - receivedClientTick);
    }

    public static StellarStageVisual.Appearance appearance(String bodyId, long clientGameTick) {
        if (!enabled) return StellarStageVisual.at(StellarStage.STABLE, 0.0);
        var entry = stars.get(bodyId);
        if (entry == null) return StellarStageVisual.at(StellarStage.STABLE, 0.0);
        long stageAge = entry.stageAgeTicks() + elapsedSinceSync(clientGameTick);
        return StellarStageVisual.at(entry.stage(), stageAge / (double) rampTicks);
    }

    /**
     * One flagged chart star for the Galaxy Atlas.
     *
     * @param displayName the server's chart display name, or empty when the snapshot carried none
     */
    public record ClientWarning(String bodyId, String displayName, StellarWarning.Warning warning) {
        /** The chart display name, or an id-derived label when none was sent. */
        public String name() {
            return displayName == null || displayName.isEmpty() ? StellarWarning.label(bodyId) : displayName;
        }
    }

    /** Stars at an announced stage, sorted by id; empty while the server lifecycle toggle is off. */
    public static List<ClientWarning> warnings(long clientGameTick) {
        if (!enabled || stars.isEmpty()) return List.of();
        long elapsedSinceSync = elapsedSinceSync(clientGameTick);
        List<ClientWarning> out = new ArrayList<>();
        for (var entry : stars.values()) {
            var warning = StellarWarning.of(entry.stage(), entry.stageAgeTicks() + elapsedSinceSync, rampTicks);
            if (warning.level() != StellarWarning.Level.NONE)
                out.add(new ClientWarning(entry.id(), entry.displayName(), warning));
        }
        out.sort(Comparator.comparing(ClientWarning::bodyId));
        return List.copyOf(out);
    }

    static void clear() {
        stars = Map.of();
        enabled = false;
        receivedClientTick = 0L;
        anchorPending = false;
        rampTicks = 1;
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
}

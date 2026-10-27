package net.bullettrain.xenopixelsmod.space;

import net.minecraft.world.phys.Vec3;

/** Evaluates the per-dimension downward-speed threshold for a re-entry assist prompt. */
final class SpaceEntrySpeed {
    private SpaceEntrySpeed() {}

    static boolean requiresAssistPrompt(Vec3 velocity, Double thresholdBlocksPerTick) {
        return velocity != null && finite(velocity)
                && thresholdBlocksPerTick != null && Double.isFinite(thresholdBlocksPerTick)
                && thresholdBlocksPerTick >= 0 && velocity.y < -thresholdBlocksPerTick;
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}

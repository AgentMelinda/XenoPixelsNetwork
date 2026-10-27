package net.bullettrain.xenopixelsmod.space;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** Player-persistent body latch used to separate outbound surface travel from a later intentional re-entry. */
public final class HandoffOutboundClearance {
    private static final String NBT_KEY = "OutboundClearanceBody";

    private HandoffOutboundClearance() {}

    public static void arm(Player player, String bodyId) {
        if (player == null) return;
        CompoundTag persisted = PlanetTravelMemory.persisted(player);
        arm(persisted, bodyId);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    static void arm(CompoundTag persisted, String bodyId) {
        String validBody = bodyId != null && ResourceLocation.tryParse(bodyId) != null ? bodyId : null;
        write(persisted, validBody);
    }

    public static String armedBody(Player player) {
        return player == null ? null : armedBody(PlanetTravelMemory.persisted(player));
    }

    public static boolean blocksCandidate(Player player, String candidateBody) {
        String armed = armedBody(player);
        return armed != null && armed.equals(candidateBody);
    }

    static String armedBody(CompoundTag persisted) {
        String stored = persisted.getCompound(PlanetTravelMemory.ROOT).getString(NBT_KEY);
        return !stored.isBlank() && ResourceLocation.tryParse(stored) != null ? stored : null;
    }

    /** Updates/clears the latch with a measured gap and returns whether the candidate same body must be suppressed. */
    public static boolean suppressCandidate(Player player, String candidateBody, double surfaceGap,
                                            double requiredClearanceGap) {
        if (player == null) return false;
        CompoundTag persisted = PlanetTravelMemory.persisted(player);
        String before = persisted.getCompound(PlanetTravelMemory.ROOT).getString(NBT_KEY);
        HandoffOutboundClearancePolicy.Decision decision = evaluate(persisted, candidateBody, surfaceGap,
                requiredClearanceGap);
        if (!java.util.Objects.equals(before, decision.armedBody()))
            player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        return decision.suppressCandidate();
    }

    static HandoffOutboundClearancePolicy.Decision evaluate(CompoundTag persisted, String candidateBody,
                                                             double surfaceGap, double requiredClearanceGap) {
        String stored = persisted.getCompound(PlanetTravelMemory.ROOT).getString(NBT_KEY);
        String armed = !stored.isBlank() && ResourceLocation.tryParse(stored) != null ? stored : null;
        var decision = HandoffOutboundClearancePolicy.evaluate(armed, candidateBody, surfaceGap,
                requiredClearanceGap);
        if (!java.util.Objects.equals(stored, decision.armedBody())) write(persisted, decision.armedBody());
        return decision;
    }

    private static void write(CompoundTag persisted, String bodyId) {
        CompoundTag root = persisted.getCompound(PlanetTravelMemory.ROOT);
        if (bodyId == null) root.remove(NBT_KEY);
        else root.putString(NBT_KEY, bodyId);
        if (root.isEmpty()) persisted.remove(PlanetTravelMemory.ROOT);
        else persisted.put(PlanetTravelMemory.ROOT, root);
    }
}

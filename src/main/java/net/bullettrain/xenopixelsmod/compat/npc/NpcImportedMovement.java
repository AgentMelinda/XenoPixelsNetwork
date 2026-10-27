package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** The source NPC's authored walking speed, independent of its DragonMineZ stat fingerprint. */
public final class NpcImportedMovement {
    private NpcImportedMovement() {}

    /** CNPC EntityNPCInterface.getSpeed() is getWalkingSpeed()/20.0f in the exact supplied jar. */
    public static double baseSpeed(int sourceSpeed) {
        return Math.max(0, Math.min(100, sourceSpeed)) / 20.0f;
    }

    public static void apply(Entity entity, NpcCombatProfile profile) {
        if (!(entity instanceof XenoNpcEntity npc) || npc.level().isClientSide()
                || profile == null || profile.importedMoveSpeed < 0) return;
        var attribute = npc.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) return;
        double desired = baseSpeed(profile.importedMoveSpeed);
        // Only the base changes: sprint and form AttributeModifiers retain their native effect.
        if (Double.compare(attribute.getBaseValue(), desired) != 0) attribute.setBaseValue(desired);
    }
}

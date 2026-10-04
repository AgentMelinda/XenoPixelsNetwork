package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Shared gate for combat-driven NPC flight. */
public final class NpcFlightPolicy {
    private NpcFlightPolicy() {}

    /** A player who turned DMZ Fly off and is standing on a block has landed even when
     * the movement packet has not yet refreshed {@code onGround()} for this server tick. */
    public static boolean targetGrounded(LivingEntity target) {
        if (target == null) return false;
        if (target.onGround()) return true;
        if (!(target instanceof ServerPlayer player)) return false;
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        if (data == null || data.getSkills() == null) return false;
        boolean flyActive = data.getSkills().isSkillActive("fly") || player.getAbilities().flying;
        net.minecraft.world.phys.AABB box = target.getBoundingBox();
        // Probe only below the feet. Moving the whole body box also found side walls.
        boolean supported = target.level().getBlockCollisions(target,
                new net.minecraft.world.phys.AABB(box.minX + 0.001, box.minY - 0.125,
                        box.minZ + 0.001, box.maxX - 0.001, box.minY, box.maxZ - 0.001))
                .iterator().hasNext();
        return walkingOnSupport(false, flyActive, supported, target.getDeltaMovement().y);
    }

    static boolean walkingOnSupport(boolean onGround, boolean flyActive, boolean supported,
                                    double verticalSpeed) {
        // A Fly skill left enabled is a capability, not proof that the player is airborne.
        // Physical support also counts while DMZ's movement packets leave onGround stale.
        return onGround || (supported && verticalSpeed <= 0.08);
    }

    /**
     * Combat flight needs the DMZ Fly skill. Brain versions that hide action controls follow the
     * DMZ decision tree and therefore do not consult the hidden Brain Fly toggle; toggle-aware
     * versions still require that action to be enabled.
     */
    public static boolean canCombatFly(NpcCombatProfile profile) {
        if (profile == null || !profile.flySkillOn || profile.flySkillLevel < 1
                || !profile.canUseFlight) {
            return false;
        }
        boolean dmzTreeOwnsActions = profile.brainVersion != null
                && !profile.brainVersion.honoursToggles();
        if (!dmzTreeOwnsActions && (!profile.brainFly || profile.brainChance("fly") <= 0)) {
            return false;
        }
        // Older profiles stored only the dedicated flight fields. Keep those working until a skill
        // entry is authored, while respecting an explicit disabled Fly entry from the new editor.
        return !profile.skills.has(NpcSkillSet.FLY)
                || (profile.skills.isActive(NpcSkillSet.FLY)
                && profile.skills.level(NpcSkillSet.FLY) > 0);
    }

    /**
     * The optional Xeno chase move teleports to a ground landing point. Once the DMZ air chase
     * owns movement, that move must yield so a decision tick cannot cancel flight velocity.
     */
    public static boolean canUseGroundChase(NpcCombatProfile profile, boolean airChaseActive) {
        return profile != null && profile.brainChase && !airChaseActive;
    }
}

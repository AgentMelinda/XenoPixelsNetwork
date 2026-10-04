package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * DragonMineZ's Sprint skill for NPCs.
 *
 * <p>For players, DMZ's {@code MovementSkillsHandler} adds a transient movement-speed modifier of
 * {@code level * 0.1} ({@code ADD_MULTIPLIED_TOTAL}) while sprinting (checked with javap against the
 * pinned 2.1.3 jar). An NPC "sprints" while it chases a target, so the same modifier applies then,
 * and the sprinting flag is raised so the Full-DMZ proxy plays DMZ's run animation. DMZ's sprint
 * does not change attack speed; the NPC's attack cadence is Melee Props &gt; Melee Speed.
 */
public final class NpcSprintSkill {
    public static final String SKILL = "sprint";
    static final ResourceLocation MODIFIER =
            ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "npc_sprint_skill");

    private NpcSprintSkill() {}

    /** The multiplier DMZ gives a player at this sprint level: {@code level * 0.1}. */
    static double bonusFor(int level) {
        return Math.max(0, level) * 0.1;
    }

    /** Applies or removes the sprint boost for a mob that is, or is not, chasing right now. */
    public static void apply(Mob mob, NpcCombatProfile profile, boolean chasing) {
        if (mob == null || mob.level().isClientSide()) {
            return;
        }
        boolean has = profile != null && profile.skills.isActive(SKILL) && profile.skills.level(SKILL) > 0;
        boolean on = has && chasing;
        mob.setSprinting(on);
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        double bonus = on ? bonusFor(profile.skills.level(SKILL)) : 0.0;
        AttributeModifier current = speed.getModifier(MODIFIER);
        if (bonus <= 0.0) {
            if (current != null) speed.removeModifier(MODIFIER);
            return;
        }
        if (current == null || current.amount() != bonus) {
            speed.removeModifier(MODIFIER);
            speed.addTransientModifier(new AttributeModifier(MODIFIER, bonus,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}

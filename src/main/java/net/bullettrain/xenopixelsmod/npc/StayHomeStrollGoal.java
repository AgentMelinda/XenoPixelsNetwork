package net.bullettrain.xenopixelsmod.npc;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;

/**
 * The wander goal, with a switch.
 *
 * <p>Every Xeno NPC used to get a plain {@code WaterAvoidingRandomStrollGoal}, which is why one
 * placed at a shop counter was eventually found three blocks away in a flowerbed. A shopkeeper, a
 * teller or a guard on a post should stand where it was put.
 *
 * <p><b>A gate, not a removal.</b> {@code registerGoals} runs once when the entity is constructed,
 * so a goal not added there could never come back when the toggle is turned off. Subclassing and
 * refusing to start is the only shape that lets the setting change at runtime.
 *
 * <p>Pairs with {@code XenoNpcBehaviour.tickLeash}: this stops the NPC wandering off under its own
 * power, and the leash walks it back if something else - knockback, a piston, a shove - moves it.
 * Neither replaces the other.
 */
public final class StayHomeStrollGoal extends WaterAvoidingRandomStrollGoal {

    private final PathfinderMob mob;

    public StayHomeStrollGoal(PathfinderMob mob, double speedModifier) {
        super(mob, speedModifier);
        this.mob = mob;
    }

    /**
     * Whether this NPC has been told to hold its spot.
     *
     * <p>Read from the profile on each check rather than cached, so the editor's toggle takes
     * effect on the next wander attempt instead of on the next reload.
     */
    private boolean anchored() {
        return NpcCombatProfile.readCached(mob).stayHome;
    }

    @Override
    public boolean canUse() {
        return !anchored() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        // Checked as well as canUse: turning the toggle on should stop a stroll already under way,
        // not wait for it to finish wandering to wherever it had picked.
        return !anchored() && super.canContinueToUse();
    }
}

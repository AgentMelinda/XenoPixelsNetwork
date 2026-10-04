package net.bullettrain.xenopixelsmod.combat.clone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which target a multi-form copy picks, and in what order.
 *
 * <p>Order is the whole content of this class, and it is not obvious from anywhere else: the four
 * candidates are resolved in {@code XenoCloneSystem.fightTarget} against live entities, which cannot
 * be tested, so the priority lives here on its own as plain booleans.
 */
class CloneFightPolicyTest {

    /** The order the copies actually fight in, once every slot is available at once. */
    @Test
    void lookBeatsLockWhichBeatsRetaliateWhichBeatsHostile() {
        assertEquals(CloneFightPolicy.Slot.LOOK,
                CloneFightPolicy.pick(true, true, true, true, true, true, true));
        assertEquals(CloneFightPolicy.Slot.LOCK,
                CloneFightPolicy.pick(true, false, true, true, true, true, true));
        assertEquals(CloneFightPolicy.Slot.RETALIATE,
                CloneFightPolicy.pick(true, false, false, true, true, true, true));
        assertEquals(CloneFightPolicy.Slot.HOSTILE,
                CloneFightPolicy.pick(true, false, false, true, false, true, true));
        assertEquals(CloneFightPolicy.Slot.NONE,
                CloneFightPolicy.pick(true, false, false, true, false, true, false));
    }

    /**
     * Looking at something outranks a lock-on.
     *
     * <p>Deliberate, and the reason a horse was unkillable: a DragonMineZ lock needs the
     * {@code kisense} skill and is dropped again within a few ticks without it, so for most
     * characters the lock slot is simply never filled. Aiming is the instruction that always works.
     */
    @Test
    void whatTheFighterIsAimingAtWinsOverTheLockOn() {
        assertEquals(CloneFightPolicy.Slot.LOOK,
                CloneFightPolicy.pick(true, true, true, false, false, false, false));
    }

    @Test
    void theLookSlotCanBeTurnedOffWithoutDisturbingTheRest() {
        assertEquals(CloneFightPolicy.Slot.LOCK,
                CloneFightPolicy.pick(false, true, true, true, true, true, true));
        assertEquals(CloneFightPolicy.Slot.RETALIATE,
                CloneFightPolicy.pick(false, true, false, true, true, true, true));
        assertEquals(CloneFightPolicy.Slot.NONE,
                CloneFightPolicy.pick(false, true, false, false, true, false, true));
    }

    /** A slot whose entity did not resolve is skipped, not treated as "nothing to fight". */
    @Test
    void anEmptySlotFallsThroughToTheNextOne() {
        assertEquals(CloneFightPolicy.Slot.RETALIATE,
                CloneFightPolicy.pick(true, false, false, true, true, false, false));
        assertEquals(CloneFightPolicy.Slot.HOSTILE,
                CloneFightPolicy.pick(true, false, false, false, true, true, true));
    }

    /** An enabled flag with nothing under the crosshair is not a target. */
    @Test
    void enablingASlotIsNotEnoughOnItsOwn() {
        assertEquals(CloneFightPolicy.Slot.NONE,
                CloneFightPolicy.pick(true, false, false, true, false, true, false));
        assertEquals(CloneFightPolicy.Slot.NONE,
                CloneFightPolicy.pick(false, true, false, false, true, false, true));
    }
}

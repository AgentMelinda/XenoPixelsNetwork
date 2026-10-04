package net.bullettrain.xenopixelsmod.fx.effek;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class XenoEffectsTest {
    private final XenoServerConfig.Data saved = XenoServerConfig.snapshot();
    private final List<EffekSender.Request> sent = new ArrayList<>();

    @AfterEach
    void restore() { XenoServerConfig.apply(saved); XenoEffects.resetForTest(); }

    @Test
    void playsTheSlotsFileAtThePointWithRangeAndScale() {
        XenoEffects.useSender((level, r) -> sent.add(r));
        assertTrue(XenoEffects.play(null, EffectSlot.PUNCH_HEAVY, new Vec3(1, 2, 3), new Vec3(0, 0, 1), 1.0f, 9));
        EffekSender.Request r = sent.get(0);
        assertEquals("xenopixelsmod:punch_heavy/punch_heavy", r.id().toString());
        assertEquals(new Vec3(1, 2, 3), r.pos());
        assertEquals(1.6f * XenoServerConfig.effekseerPunchScale, r.scale(), 1e-6,
                "slot default x requested x punch scale");
        assertEquals(64, r.range());
    }

    @Test
    void aClosedGateSendsNothingSoTheCallerDrawsVanilla() {
        XenoEffects.useSender((level, r) -> sent.add(r));
        XenoServerConfig.effekseerEnabled = false;
        assertFalse(XenoEffects.play(null, EffectSlot.MISSILE_EXPLOSION, Vec3.ZERO, new Vec3(0, 1, 0), 2f, -1));
        assertTrue(sent.isEmpty());
    }

    @Test
    void aSenderFailureFallsBackToVanilla() {
        XenoEffects.useSender((level, r) -> { throw new IllegalStateException("library changed"); });
        assertFalse(XenoEffects.play(null, EffectSlot.MISSILE_EXPLOSION, Vec3.ZERO, new Vec3(0, 1, 0), 1f, -1));
    }

    /**
     * Final review 2026-09-28: AAA Particles only range-checks effects that carry a position, so a
     * bound (entity-following) effect would reach every player in the dimension. Bound effects
     * (2026-09-29, the missile plume) are therefore sent player by player, only within range.
     */
    @Test
    void boundEffectsStillHonourTheRange() {
        Vec3 missile = new Vec3(0, 100, 0);
        assertTrue(AaaEffekSender.inRange(new Vec3(0, 100, 250), missile, 256));
        assertFalse(AaaEffekSender.inRange(new Vec3(0, 100, 300), missile, 256));
        assertFalse(AaaEffekSender.inRange(new Vec3(5000, 64, 0), missile, 256), "far players get nothing");
    }

    /** 2026-09-28 owner: the punch burst filled the screen; it is a small circle at the hit. */
    @Test
    void punchEffectsAreScaledByTheServerPunchScale() {
        XenoEffects.useSender((level, r) -> sent.add(r));
        assertEquals(0.3f, new XenoServerConfig.Data().effekseerPunchScale, 1e-6, "small by default");
        XenoServerConfig.effekseerPunchScale = 0.3f;
        XenoEffects.play(null, EffectSlot.PUNCH_IMPACT, Vec3.ZERO, new Vec3(1, 0, 0), 1.0f, 1);
        XenoEffects.play(null, EffectSlot.PUNCH_HEAVY, Vec3.ZERO, new Vec3(1, 0, 0), 1.0f, 2);
        XenoEffects.play(null, EffectSlot.MISSILE_EXPLOSION, Vec3.ZERO, new Vec3(0, 1, 0), 1.0f, -1);
        assertEquals(0.3f, sent.get(0).scale(), 1e-6);
        assertEquals(0.3f * 1.6f, sent.get(1).scale(), 1e-6, "heavy stays 1.6x the light one");
        assertEquals(XenoServerConfig.effekseerExplosionScale, sent.get(2).scale(), 1e-6,
                "only punches use the punch scale");
    }

    /** 2026-09-28 owner: "the size of animations from xenoset?" - every category has its own size. */
    @Test
    void hakaiAndMissileEffectsUseTheirOwnServerScale() {
        XenoEffects.useSender((level, r) -> sent.add(r));
        XenoServerConfig.effekseerHakaiScale = 0.5f;
        XenoServerConfig.effekseerMissileScale = 2.0f;
        XenoEffects.play(null, EffectSlot.HAKAI_ERASE, Vec3.ZERO, new Vec3(0, 1, 0), 1.0f, 7);
        XenoEffects.play(null, EffectSlot.MISSILE_EXPLOSION, Vec3.ZERO, new Vec3(0, 1, 0), 1.0f, -1);
        assertEquals(0.5f, sent.get(0).scale(), 1e-6);
        assertEquals(2.0f * XenoServerConfig.effekseerExplosionScale, sent.get(1).scale(), 1e-6,
                "the explosion also carries its own size (effekseerExplosionScale)");
    }

    /**
     * 2026-09-29 owner: "the hakai animation is not rotating compared to the players camera angle".
     * AAA's rotationFromForward(0,1,0) is rotX -90 degrees, which laid the Hakai effects on their
     * side. They are authored upright in world space, so they are sent with no rotation at all.
     */
    @Test
    void hakaiEffectsAreSentUpright() {
        XenoEffects.useSender((level, r) -> sent.add(r));
        for (EffectSlot slot : new EffectSlot[] {EffectSlot.HAKAI_CHANNEL, EffectSlot.HAKAI_CRUMBLE,
                EffectSlot.HAKAI_ERASE, EffectSlot.HAKAI_PALM}) {
            XenoEffects.play(null, slot, Vec3.ZERO, new Vec3(0, 1, 0), 1.0f, -1);
        }
        XenoEffects.play(null, EffectSlot.MISSILE_THRUSTER, Vec3.ZERO, new Vec3(1, 0, 0), 1.0f, -1);
        for (int i = 0; i < 4; i++) assertNull(sent.get(i).forward(), "Hakai " + i + " is upright");
        assertEquals(new Vec3(1, 0, 0), sent.get(4).forward(), "directional effects keep their facing");
    }

    /** 2026-09-29 owner: Sparking and the ship thruster play generated Effekseer effects too. */
    @Test
    void sparkingIsUprightAndThrustersKeepTheirFacing() {
        XenoEffects.useSender((level, r) -> sent.add(r));
        XenoEffects.play(null, EffectSlot.SPARKING_AURA, Vec3.ZERO, new Vec3(0, 1, 0), 1.0f, -1);
        XenoEffects.play(null, EffectSlot.SPARKING_BURST, Vec3.ZERO, new Vec3(0, 1, 0), 1.0f, -1);
        XenoEffects.play(null, EffectSlot.SHIP_THRUSTER, Vec3.ZERO, new Vec3(0, 0, -1), 1.0f, -1);
        assertNull(sent.get(0).forward());
        assertNull(sent.get(1).forward());
        assertEquals(new Vec3(0, 0, -1), sent.get(2).forward(), "the plume points along the exhaust");
        assertTrue(EffectSlot.HAKAI_CHANNEL.upright() && EffectSlot.SPARKING_AURA.upright());
        assertFalse(EffectSlot.MISSILE_THRUSTER.upright() || EffectSlot.SHIP_THRUSTER.upright()
                || EffectSlot.PUNCH_IMPACT.upright());
    }

    @Test
    void sparkingAndShipThrusterHaveTheirOwnSwitchAndSize() {
        XenoEffects.useSender((level, r) -> sent.add(r));
        XenoServerConfig.effekseerSparkingScale = 1.5f;
        XenoServerConfig.effekseerThrusterScale = 0.5f;
        XenoEffects.play(null, EffectSlot.SPARKING_AURA, Vec3.ZERO, null, 1.0f, -1);
        XenoEffects.play(null, EffectSlot.SHIP_THRUSTER, Vec3.ZERO, new Vec3(1, 0, 0), 1.0f, -1);
        assertEquals(1.5f, sent.get(0).scale(), 1e-6);
        assertEquals(0.5f, sent.get(1).scale(), 1e-6);
        XenoServerConfig.effekseerSparking = false;
        XenoServerConfig.effekseerShipThrusters = false;
        assertEquals(XenoEffects.Outcome.UNAVAILABLE,
                XenoEffects.attempt(null, EffectSlot.SPARKING_AURA, Vec3.ZERO, null, 1.0f, -1),
                "off means the vanilla aura draws instead");
        assertEquals(XenoEffects.Outcome.UNAVAILABLE,
                XenoEffects.attempt(null, EffectSlot.SHIP_THRUSTER, Vec3.ZERO, new Vec3(1, 0, 0), 1.0f, -1));
        assertEquals(2, sent.size());
    }

    /**
     * 2026-09-29 owner: "missile exploded where green area main is the middle and particles went
     * left side". The explosion was sent facing up, which AAA turns into a 90-degree tip.
     */
    @Test
    void theExplosionIsSentUpright() {
        XenoEffects.useSender((level, r) -> sent.add(r));
        XenoEffects.play(null, EffectSlot.MISSILE_EXPLOSION, new Vec3(5, 70, 5), new Vec3(0, 1, 0), 1.0f, -1);
        assertNull(sent.get(0).forward());
        assertEquals(new Vec3(5, 70, 5), sent.get(0).pos(), "centred on the blast");
    }

    /**
     * 2026-09-29 owner: the plume "doesn't rotate well or do the fx in the right place". Each pulse
     * was dropped at a fixed point while the missile flew on; now it is bound to the missile, so
     * AAA moves it with the missile and turns it along the missile's velocity every frame.
     */
    @Test
    void aBoundEffectCarriesItsEntityAndNoFacing() {
        XenoEffects.useSender((level, r) -> sent.add(r));
        assertTrue(XenoEffects.playBound(null, EffectSlot.MISSILE_THRUSTER, new Vec3(1, 2, 3), 42, 1.0f));
        EffekSender.Request r = sent.get(0);
        assertEquals(42, r.boundEntity());
        assertNull(r.forward(), "AAA takes the rotation from the entity's velocity");
        assertEquals(new Vec3(1, 2, 3), r.pos(), "still used for our range check");
        XenoEffects.play(null, EffectSlot.PUNCH_IMPACT, Vec3.ZERO, new Vec3(1, 0, 0), 1.0f, 7);
        assertEquals(-1, sent.get(1).boundEntity(), "ordinary effects are positional");
    }

    /**
     * 2026-09-29 owner: "the sparking particles when i move it lags behind me ... its need to be
     * smooth on moving". The aura is bound to the player so AAA moves it every frame; upright
     * effects follow position only, only the plume turns with the velocity.
     */
    @Test
    void boundUprightEffectsFollowWithoutTurning() {
        XenoEffects.useSender((level, r) -> sent.add(r));
        XenoEffects.playBound(null, EffectSlot.SPARKING_AURA, new Vec3(0, 64, 0), 9, 1.0f);
        XenoEffects.playBound(null, EffectSlot.MISSILE_THRUSTER, new Vec3(0, 90, 0), 10, 1.0f);
        assertEquals(9, sent.get(0).boundEntity());
        assertEquals(EffekSender.Follow.POSITION, sent.get(0).follow(), "the aura stays upright while it follows");
        assertEquals(EffekSender.Follow.VELOCITY, sent.get(1).follow(), "the plume points along the flight");
    }

    /** 2026-09-29 owner: "missile_explosion scale to 15". */
    @Test
    void theExplosionIsFifteenByDefault() {
        XenoEffects.useSender((level, r) -> sent.add(r));
        assertEquals(15.0f, new XenoServerConfig.Data().effekseerExplosionScale, 1e-6);
        XenoServerConfig.effekseerExplosionScale = 15.0f;
        XenoServerConfig.effekseerMissileScale = 1.0f;
        XenoEffects.play(null, EffectSlot.MISSILE_EXPLOSION, Vec3.ZERO, null, 1.0f, -1);
        assertEquals(15.0f, sent.get(0).scale(), 1e-6);
    }

    /**
     * The bound plume's nozzle offset lives inside the effect, so scaling the effect would move the
     * flame off the tail: its size (5x, 2026-09-29 owner) is built into the effect instead, and
     * runtime size settings are not applied to it.
     */
    @Test
    void theBoundPlumeIsNotRescaledAtRuntime() {
        XenoEffects.useSender((level, r) -> sent.add(r));
        XenoServerConfig.effekseerMissileScale = 3.0f;
        XenoEffects.playBound(null, EffectSlot.MISSILE_THRUSTER, Vec3.ZERO, 5, 1.2f);
        assertEquals(1.2f, sent.get(0).scale(), 1e-6, "0.4 x missile length keeps the nozzle on the tail");
    }

    /**
     * 2026-09-29 owner: Sparking while flying (DMZ fly) looked wrong - the upright aura stood above
     * the head while the body lay along the flight. The flight aura follows the player's look,
     * which is where a DMZ flyer points (and, unlike velocity, is synced for other players too).
     */
    @Test
    void theFlightAuraFollowsTheLook() {
        XenoEffects.useSender((level, r) -> sent.add(r));
        assertTrue(XenoEffects.playBoundLook(null, EffectSlot.SPARKING_FLIGHT, new Vec3(0, 100, 0), 4, 1.0f));
        assertEquals(EffekSender.Follow.LOOK, sent.get(0).follow());
        assertEquals(4, sent.get(0).boundEntity());
        assertNull(sent.get(0).forward());
        assertFalse(EffectSlot.SPARKING_FLIGHT.upright());
        assertEquals(EffekSender.Follow.NONE,
                new EffekSender.Request(null, Vec3.ZERO, null, 1f, 8).follow(), "plain effects are positional");
    }

    /** 2026-09-29 owner: "a way to set per fx its scale please? using xenoset". */
    @Test
    void everyEffectHasItsOwnScale() {
        XenoEffects.useSender((level, r) -> sent.add(r));
        XenoServerConfig.setSlotScale(EffectSlot.PUNCH_GUARD, 2.0f);
        XenoServerConfig.effekseerPunchScale = 0.3f;
        XenoEffects.play(null, EffectSlot.PUNCH_GUARD, Vec3.ZERO, new Vec3(1, 0, 0), 1.0f, 3);
        assertEquals(0.6f, sent.get(0).scale(), 1e-6, "category size x the effect's own");
        assertEquals(1.0f, XenoServerConfig.slotScale(EffectSlot.PUNCH_IMPACT), 1e-6);
        assertEquals(3.5f, new XenoServerConfig.Data().slotScale(EffectSlot.SPARKING_FLIGHT), 1e-6,
                "2026-09-29 owner: sparking_flight 3.5");
    }

    /**
     * 2026-09-29 owner: the flight aura sat "a bit behind the player". It is anchored at the body's
     * centre (0.9 behind the eyes, see AaaHeadSpaceOffsetTest) and authored around that point, so
     * its scale grows it around the flyer instead of pushing it back.
     */
    @Test
    void theFlightAuraIsAnchoredAtTheBody() {
        XenoEffects.useSender((level, r) -> sent.add(r));
        XenoEffects.playBoundLook(null, EffectSlot.SPARKING_FLIGHT, Vec3.ZERO, 4, 1.0f);
        assertEquals(0.9, sent.get(0).lookAnchor(), 1e-9);
    }
}

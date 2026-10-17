package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.hair.CustomHair;
import com.dragonminez.common.stats.character.Character;
import net.bullettrain.xenopixelsmod.api.dmz.DmzAppearance;
import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The borrow ledger: what it stores, when it says no, and the two ways an entry can go wrong.
 *
 * <p>Runs without a player or a server, which is the point of keeping the bookkeeping apart from the
 * attachment and packet code.
 */
class PlayerAppearanceStealsLedgerTest {
    private static final String NPC = "8b3f5f2a-2d6b-4d1e-9a1c-6f1a2b3c4d5e";

    @Test void beginStoresTheWayBackAndEndGivesTheLookExactlyBack() {
        Character target = owner();
        CompoundTag store = new CompoundTag();
        CompoundTag before = target.save();

        assertEquals(NpcAppearanceMapping.Result.OK, begin(store, target, 600L));
        assertTrue(PlayerAppearanceSteals.Ledger.active(store));
        assertEquals(NPC, PlayerAppearanceSteals.Ledger.source(store));
        assertEquals("saiyan", target.getRace());
        assertEquals(600L, PlayerAppearanceSteals.Ledger.remaining(store, 1_000L));
        assertEquals(200L, PlayerAppearanceSteals.Ledger.remaining(store, 1_400L));
        assertEquals(0L, PlayerAppearanceSteals.Ledger.remaining(store, 1_600L),
                "the deadline itself is not a grace period");

        assertEquals(NpcAppearanceMapping.Result.OK, PlayerAppearanceSteals.Ledger.end(store, target));
        assertFalse(PlayerAppearanceSteals.Ledger.active(store));
        assertEquals(before, target.save());
        assertEquals(0L, PlayerAppearanceSteals.Ledger.remaining(store, 1_700L));
    }

    @Test void oneBorrowAtATime() {
        Character target = owner();
        CompoundTag store = new CompoundTag();
        assertEquals(NpcAppearanceMapping.Result.OK, begin(store, target, 600L));
        assertEquals(NpcAppearanceMapping.Result.ALREADY_ACTIVE, begin(store, target, 600L));
        assertEquals(NpcAppearanceMapping.Result.NOT_ACTIVE,
                PlayerAppearanceSteals.Ledger.end(new CompoundTag(), target));
        assertEquals(NpcAppearanceMapping.Result.OK, PlayerAppearanceSteals.Ledger.end(store, target));
        assertEquals(NpcAppearanceMapping.Result.OK, begin(store, target, 600L),
                "returning a look makes the next borrow possible");
    }

    @Test void tickCountIsClampedIntoRange() {
        Character target = owner();
        CompoundTag immediate = new CompoundTag();
        assertEquals(NpcAppearanceMapping.Result.OK, begin(immediate, target, 0L));
        // the deadline is stamped from the borrow's start time, so it is measured from there too
        assertEquals(1L, PlayerAppearanceSteals.Ledger.remaining(immediate, 1_000L),
                "a zero-tick borrow still gets one tick so the deadline can actually be observed");

        CompoundTag forever = new CompoundTag();
        assertEquals(NpcAppearanceMapping.Result.OK,
                begin(forever, owner(), PlayerAppearanceSteals.Ledger.MAX_TICKS * 10L));
        assertEquals(PlayerAppearanceSteals.Ledger.MAX_TICKS,
                PlayerAppearanceSteals.Ledger.remaining(forever, 1_000L));
    }

    /** A borrow whose snapshot is gone cannot ever be returned, so it must not be retried forever. */
    @Test void unreadableEntryIsDroppedRatherThanRetried() {
        Character target = owner();
        CompoundTag store = new CompoundTag();
        CompoundTag broken = new CompoundTag();
        broken.putLong("EndsAt", 10L);
        store.put("Active", broken);

        assertEquals(NpcAppearanceMapping.Result.INTERNAL,
                PlayerAppearanceSteals.Ledger.end(store, target));
        assertFalse(PlayerAppearanceSteals.Ledger.active(store));
    }

    /**
     * Data not loaded yet is temporary - right after a death or a login - so the entry has to survive
     * the attempt. Losing it is how a player ends up kept in a borrowed body.
     */
    @Test void waitingForDataKeepsTheSnapshot() {
        Character target = owner();
        CompoundTag before = target.save();
        CompoundTag store = new CompoundTag();
        assertEquals(NpcAppearanceMapping.Result.OK, begin(store, target, 600L));

        assertEquals(NpcAppearanceMapping.Result.NO_DATA,
                PlayerAppearanceSteals.Ledger.end(store, null));
        assertTrue(PlayerAppearanceSteals.Ledger.active(store));

        assertEquals(NpcAppearanceMapping.Result.OK, PlayerAppearanceSteals.Ledger.end(store, target),
                "a failed return must not consume the way back");
        assertEquals(before, target.save());
    }

    @Test void refusesAnUnreadableLookBeforeWritingAnything() {
        Character target = owner();
        CompoundTag store = new CompoundTag();
        CompoundTag before = target.save();
        NpcAppearanceSource bad = withColor(look(), "not-a-color");

        assertEquals(NpcAppearanceMapping.Result.INVALID_COLOR,
                PlayerAppearanceSteals.Ledger.begin(store, target, bad, NPC, 1_000L, 600L));
        assertFalse(PlayerAppearanceSteals.Ledger.active(store));
        assertEquals(before, target.save(), "a refused borrow must not paint anything");
    }

    /** The saved player still owes the look after a restart, a death, or a logout mid-borrow. */
    @Test void theBorrowSurvivesThePlayerDataRoundTrip() {
        XenoPlayerData data = new XenoPlayerData();
        Character target = owner();
        CompoundTag before = target.save();
        assertEquals(NpcAppearanceMapping.Result.OK, begin(data.appearanceSteals(), target, 600L));

        CompoundTag saved = new CompoundTag();
        data.saveNBT(saved);
        XenoPlayerData reloaded = new XenoPlayerData();
        reloaded.loadNBT(saved);
        assertTrue(PlayerAppearanceSteals.Ledger.active(reloaded.appearanceSteals()));
        assertEquals(data.appearanceSteals(), reloaded.appearanceSteals());
        assertEquals(NPC, PlayerAppearanceSteals.Ledger.source(reloaded.appearanceSteals()));

        XenoPlayerData carried = new XenoPlayerData();
        carried.copyFrom(data);
        assertTrue(PlayerAppearanceSteals.Ledger.active(carried.appearanceSteals()));

        Character restored = new Character();
        // the player who comes back is a fresh character; the snapshot is the only memory of their look
        assertTrue(DmzAppearance.restore(restored, reloaded.appearanceSteals().getCompound("Active")
                .getCompound("Snapshot")));
        assertEquals(before, restored.save());
    }

    private static NpcAppearanceMapping.Result begin(CompoundTag store, Character target, long ticks) {
        return PlayerAppearanceSteals.Ledger.begin(store, target, look(), NPC, 1_000L, ticks);
    }

    private static Character owner() {
        Character character = new Character();
        character.setRace("human");
        character.setBodyColor("#FFD3C9");
        character.setHairId(3);
        return character;
    }

    private static NpcAppearanceSource look() {
        CustomHair hair = new CustomHair();
        hair.getStrands(CustomHair.HairFace.TOP)[0].addCube();
        CustomHair[] slots = {hair, hair, hair, hair};
        return new NpcAppearanceSource("saiyan", "female", "warrior",
                1, 2, 3, 4, 5, 1.0f,
                "#112233", "#445566", "#778899", "#AABBCC", "#DDEEFF",
                "#FFE89E", "#FF5500", "head", true, true, 0, "", slots);
    }

    private static NpcAppearanceSource withColor(NpcAppearanceSource base, String bodyColor) {
        return new NpcAppearanceSource(base.race(), base.gender(), base.characterClass(),
                base.bodyType(), base.eyesType(), base.noseType(), base.mouthType(),
                base.tattooType(), base.boobScale(), bodyColor, base.bodyColor2(),
                base.bodyColor3(), base.eye1Color(), base.eye2Color(), base.hairColor(),
                base.auraColor(), base.activeHeadBone(), base.saiyanTail(),
                base.renderHairBase(), base.hairId(), base.hairCode(), base.hair());
    }
}

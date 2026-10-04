package net.bullettrain.xenopixelsmod.missile;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 2026-09-28 owner request: other mods' explosives as missile warheads (sable_tournament
 * explosives, every Ballistix explosive, Create Big Cannons shells), each going off as itself.
 */
class MissileWarheadCompatTest {
    @AfterEach
    void clear() {
        MissileWarhead.clearDetonatorsForTest();
    }

    private static WarheadDetonator accepting(ItemStack match, AtomicInteger fired, boolean result) {
        return new WarheadDetonator() {
            @Override public String id() { return "test"; }
            @Override public boolean accepts(ItemStack stack) { return stack.is(match.getItem()); }
            @Override public boolean detonate(Context context) { fired.incrementAndGet(); return result; }
        };
    }

    @Test
    void aDetonatorMakesItsItemAWarheadAndSetsItOff() {
        ItemStack bomb = new ItemStack(Items.FIRE_CHARGE);
        assertFalse(MissileWarhead.isWarhead(bomb));
        AtomicInteger fired = new AtomicInteger();
        MissileWarhead.registerDetonator(accepting(bomb, fired, true));
        assertTrue(MissileWarhead.isWarhead(bomb));
        assertTrue(MissileWarhead.explosionPower(bomb, MissileSize.MEDIUM) > 0, "a vanilla fallback power exists");
        assertTrue(MissileWarhead.detonateNative(new WarheadDetonator.Context(null, null, bomb, null, null)));
        assertEquals(1, fired.get());
    }

    @Test
    void aFailingDetonatorFallsBackToTheVanillaBlast() {
        ItemStack bomb = new ItemStack(Items.FIRE_CHARGE);
        AtomicInteger fired = new AtomicInteger();
        MissileWarhead.registerDetonator(accepting(bomb, fired, false));
        MissileWarhead.registerDetonator(new WarheadDetonator() {
            @Override public String id() { return "broken"; }
            @Override public boolean accepts(ItemStack stack) { return true; }
            @Override public boolean detonate(Context context) { throw new IllegalStateException("mod changed"); }
        });
        assertFalse(MissileWarhead.detonateNative(new WarheadDetonator.Context(null, null, bomb, null, null)),
                "nobody handled it, so the caller uses the vanilla explosion");
        assertEquals(1, fired.get());
    }

    @Test
    void tntStaysAVanillaWarheadAndOrdinaryItemsAreNot() {
        assertTrue(MissileWarhead.isWarhead(new ItemStack(Items.TNT)));
        assertFalse(MissileWarhead.isWarhead(new ItemStack(Items.DIRT)));
        assertFalse(MissileWarhead.detonateNative(
                new WarheadDetonator.Context(null, null, new ItemStack(Items.TNT), null, null)));
    }

    @Test
    void theWarheadTagListsTheOtherModsExplosivesAsOptional() throws Exception {
        String tag = Files.readString(Path.of(System.getProperty("xenopixels.projectDir"),
                "src/main/resources/data/xenopixelsmod/tags/item/missile_warheads.json"));
        for (String id : new String[] {"sable_tournament:explosive_instant_small",
                "sable_tournament:explosive_instant_medium", "sable_tournament:explosive_instant_large",
                "createbigcannons:he_shell", "createbigcannons:shrapnel_shell", "ballistix:nuclear"}) {
            assertTrue(tag.contains("\"" + id + "\""), id);
        }
        assertFalse(tag.contains("\"required\": true"), "a missing mod must not break the tag");
    }
}

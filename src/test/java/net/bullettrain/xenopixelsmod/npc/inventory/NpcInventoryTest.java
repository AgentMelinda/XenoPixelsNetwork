package net.bullettrain.xenopixelsmod.npc.inventory;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Inventory page: what an NPC wears, what it leaves behind, and what it is worth.
 *
 * <p>Nothing here resolves a stored tag back into an {@code ItemStack} - that needs a bootstrapped
 * registry, and it is not what can go wrong. What can go wrong is a value that survives the round
 * trip changed, a bound that is not enforced on the server side of the wire, and an NPC saved before
 * this shipped quietly acquiring today's defaults.
 *
 * <p>The tag is the storage and the stack is a view of it, which is exactly why these tests can
 * exist at all - see {@link NpcSlotStack}.
 */
class NpcInventoryTest {

    /** Stands in for a serialised item. Its shape does not matter; its survival does. */
    private static CompoundTag item(String id) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putInt("count", 1);
        return tag;
    }

    private static NpcSlotStack slot(String id) {
        return NpcSlotStack.load(item(id));
    }

    // ------------------------------------------------------------ the round trip

    @Test
    void wornGearSurvivesASaveAndLoad() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.gear.set(EquipmentSlot.HEAD, slot("minecraft:iron_helmet"));
        profile.gear.set(EquipmentSlot.MAINHAND, slot("minecraft:diamond_sword"));

        NpcCombatProfile read = NpcCombatProfile.fromTag(profile.toTag());
        assertEquals("minecraft:iron_helmet",
                read.gear.get(EquipmentSlot.HEAD).tag().getString("id"));
        assertEquals("minecraft:diamond_sword",
                read.gear.get(EquipmentSlot.MAINHAND).tag().getString("id"));
        assertTrue(read.gear.get(EquipmentSlot.OFFHAND).isEmpty());
    }

    @Test
    void everythingOnAStackSurvivesTheRoundTrip() {
        // The reason slots replaced typed item ids: an id could never carry an enchantment or a
        // custom name, so a Sharpness V sword arrived plain. The whole serialised form is kept, so
        // whatever the game put on the stack comes back.
        CompoundTag sword = item("minecraft:diamond_sword");
        CompoundTag components = new CompoundTag();
        components.putString("minecraft:custom_name", "\"Wind Cutter\"");
        CompoundTag enchantments = new CompoundTag();
        enchantments.putInt("minecraft:sharpness", 5);
        components.put("minecraft:enchantments", enchantments);
        sword.put("components", components);

        NpcCombatProfile profile = new NpcCombatProfile();
        profile.gear.set(EquipmentSlot.MAINHAND, NpcSlotStack.load(sword));

        CompoundTag back = NpcCombatProfile.fromTag(profile.toTag())
                .gear.get(EquipmentSlot.MAINHAND).tag();
        assertEquals(sword, back, "the stack must come back exactly as it went in");
    }

    @Test
    void anItemWhoseModIsGoneIsStillWrittenBackOut() {
        // A stack that cannot be parsed has nothing left to write, so the item would be lost the
        // first time the world loaded without its mod. Keeping the tag means the sword returns when
        // the mod does - the property item ids gave NpcTrade, without giving up enchantments.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.gear.set(EquipmentSlot.CHEST, slot("somemod:unobtainium_plate"));

        CompoundTag saved = profile.toTag();
        NpcCombatProfile read = NpcCombatProfile.fromTag(saved);
        assertEquals("somemod:unobtainium_plate",
                read.gear.get(EquipmentSlot.CHEST).tag().getString("id"));
        // And again, because a single round trip could hide a loss on the second write.
        assertEquals(saved.getCompound("Gear"),
                NpcCombatProfile.fromTag(read.toTag()).toTag().getCompound("Gear"));
    }

    @Test
    void dropsSurviveASaveAndLoadInAuthorOrder() {
        // Order is what an operator typed, and the page numbers the chances against it. A list that
        // reshuffled itself between openings could not be edited.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.drops.add(new NpcDrop(slot("minecraft:bread"), 50.0f));
        profile.drops.add(new NpcDrop(slot("minecraft:emerald"), 12.5f));

        NpcCombatProfile read = NpcCombatProfile.fromTag(profile.toTag());
        assertEquals(2, read.drops.size());
        assertEquals("minecraft:bread", read.drops.get(0).item().tag().getString("id"));
        assertEquals(50.0f, read.drops.get(0).chance(), 0.001f);
        assertEquals("minecraft:emerald", read.drops.get(1).item().tag().getString("id"));
    }

    @Test
    void theLootModeAndExperienceRangeSurviveASaveAndLoad() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.drops.setLootMode(NpcLootMode.AUTO_PICKUP);
        profile.drops.setMinExp(5);
        profile.drops.setMaxExp(40);

        NpcCombatProfile read = NpcCombatProfile.fromTag(profile.toTag());
        assertEquals(NpcLootMode.AUTO_PICKUP, read.drops.lootMode());
        assertEquals(5, read.drops.minExp());
        assertEquals(40, read.drops.maxExp());
    }

    @Test
    void anNpcSavedBeforeThisShippedIsUnchanged() {
        // The same asymmetry SocialClipsTest documents. An NPC already in a world has a profile tag
        // but none of these keys, and it must come back wearing nothing, dropping nothing and worth
        // whatever its type is - not wearing today's sample gear.
        //
        // The tag must be non-empty: fromTag short-circuits an empty one to fresh defaults, because
        // "no profile at all" means a brand new NPC rather than an old one.
        CompoundTag existing = new NpcCombatProfile().toTag();
        existing.remove("Gear");
        existing.remove("NpcDrops");
        existing.remove("LootMode");
        existing.remove("MinExp");
        existing.remove("MaxExp");

        NpcCombatProfile old = NpcCombatProfile.fromTag(existing);
        assertTrue(old.gear.isEmpty());
        assertEquals(0, old.drops.size());
        assertEquals(NpcLootMode.NORMAL, old.drops.lootMode());
        assertEquals(0, old.drops.minExp());
        assertEquals(0, old.drops.maxExp());
    }

    @Test
    void anOrdinaryNpcCarriesNoneOfTheseKeys() {
        // Written only when set, so a world full of untouched NPCs does not grow five keys each.
        CompoundTag fresh = new NpcCombatProfile().toTag();
        assertFalse(fresh.contains("Gear"));
        assertFalse(fresh.contains("NpcDrops"));
        assertFalse(fresh.contains("LootMode"));
    }

    // ------------------------------------------------------------ the slot itself

    @Test
    void aSlotCopiesItsTagInAndOut() {
        // A CompoundTag handed to two NPCs and then mutated is a bug that surfaces far from where
        // it was caused.
        CompoundTag source = item("minecraft:bread");
        NpcSlotStack held = NpcSlotStack.load(source);
        source.putString("id", "minecraft:stone");
        assertEquals("minecraft:bread", held.tag().getString("id"));

        held.saved().putString("id", "minecraft:dirt");
        assertEquals("minecraft:bread", held.tag().getString("id"));
    }

    @Test
    void anEmptyTagIsAnEmptySlot() {
        assertTrue(NpcSlotStack.load(null).isEmpty());
        assertTrue(NpcSlotStack.load(new CompoundTag()).isEmpty());
        assertNull(NpcSlotStack.EMPTY.saved());
        assertNotNull(NpcSlotStack.load(item("minecraft:bread")).saved());
    }

    // ------------------------------------------------------------ bounds

    @Test
    void anOversizedItemIsRefusedRatherThanAllocated() {
        // An unbounded tag off the wire is an allocation the client gets to choose.
        CompoundTag huge = item("minecraft:bread");
        huge.putString("padding", "x".repeat(NpcSlotStack.MAX_TAG_BYTES * 4));
        assertFalse(NpcSlotStack.withinBounds(huge));
        assertTrue(NpcSlotStack.withinBounds(item("minecraft:bread")));
        assertTrue(NpcSlotStack.withinBounds(null));
    }

    @Test
    void aChanceOutsideZeroToOneHundredIsClamped() {
        // A fat-fingered 1000 means "certain", not "disabled".
        assertEquals(100.0f, new NpcDrop(slot("minecraft:bread"), 1000f).chance(), 0.001f);
        assertEquals(0.0f, new NpcDrop(slot("minecraft:bread"), -5f).chance(), 0.001f);
        assertEquals(0.0f, new NpcDrop(slot("minecraft:bread"), Float.NaN).chance(), 0.001f);
    }

    @Test
    void experienceIsCappedAtTheFieldsOwnLimit() {
        NpcDropList drops = new NpcDropList();
        drops.setMaxExp(90_000);
        drops.setMinExp(-4);
        assertEquals(NpcDropList.MAX_EXP, drops.maxExp());
        assertEquals(0, drops.minExp());
    }

    @Test
    void theListRefusesToGrowPastTheRowsThePageDraws() {
        NpcDropList drops = new NpcDropList();
        for (int i = 0; i < NpcDropList.MAX_DROPS; i++) {
            assertTrue(drops.add(NpcDrop.empty()));
        }
        assertFalse(drops.add(NpcDrop.empty()));
        assertEquals(NpcDropList.MAX_DROPS, drops.size());
    }

    @Test
    void aTagCarryingMoreDropsThanThePageHasIsTruncatedOnLoad() {
        // The server does not trust a count off the wire any more than it trusts an item.
        CompoundTag tag = new NpcCombatProfile().toTag();
        ListTag list = new ListTag();
        for (int i = 0; i < 40; i++) {
            list.add(new NpcDrop(slot("minecraft:bread"), 100f).save());
        }
        tag.put("NpcDrops", list);

        assertEquals(NpcDropList.MAX_DROPS, NpcCombatProfile.fromTag(tag).drops.size());
    }

    // ------------------------------------------------------------ what happens on death

    @Test
    void aRowLeftBlankDropsNothing() {
        NpcDropList drops = new NpcDropList();
        drops.add(NpcDrop.empty());
        assertFalse(drops.get(0).usable());
        assertEquals(0, drops.usableCount());
    }

    @Test
    void aZeroChanceRowNeverDrops() {
        NpcDropList drops = new NpcDropList();
        drops.add(new NpcDrop(slot("minecraft:bread"), 0f));
        assertFalse(drops.get(0).usable());
        assertEquals(0, drops.usableCount());
    }

    @Test
    void aFilledRowCounts() {
        NpcDropList drops = new NpcDropList();
        drops.add(new NpcDrop(slot("minecraft:bread"), 100f));
        drops.add(NpcDrop.empty());
        assertEquals(1, drops.usableCount());
    }

    @Test
    void anExperienceRangeWithMaxBelowMinIsReadAsReversed() {
        // Somebody who lowered Max meant a smaller range, not "no experience at all".
        NpcDropList drops = new NpcDropList();
        drops.setMinExp(40);
        drops.setMaxExp(10);
        RandomSource random = RandomSource.create(7);
        for (int i = 0; i < 50; i++) {
            int rolled = drops.rollExperience(random);
            assertTrue(rolled >= 10 && rolled <= 40, "rolled " + rolled);
        }
    }

    @Test
    void anUnsetExperienceRangeRollsZero() {
        // The entity reads this and falls back to its type's own reward, which is what an operator
        // who never opened the page expects.
        assertEquals(0, new NpcDropList().rollExperience(RandomSource.create(3)));
    }

    // ------------------------------------------------------------ the wire

    @Test
    void lootModeOrdinalsAreAppendOnly() {
        // The editor cycles by index and the save carries that index. Inserting a mode would
        // silently change what every already-saved NPC does.
        assertEquals(0, NpcLootMode.NORMAL.ordinal());
        assertEquals(1, NpcLootMode.AUTO_PICKUP.ordinal());
        assertEquals(NpcLootMode.NORMAL, NpcLootMode.byIndex(-1));
        assertEquals(NpcLootMode.NORMAL, NpcLootMode.byIndex(99));
    }

    @Test
    void theGearSlotsAreTheSixTheEditorDraws() {
        // Seven rows in the reference, six here: Projectile has no consumer, so it is not a slot.
        assertEquals(6, NpcGear.SLOTS.length);
        assertEquals(NpcGear.SLOTS.length, NpcGear.LABELS.length,
                "every slot needs a name for the screen to show");
        for (EquipmentSlot slot : NpcGear.SLOTS) {
            assertTrue(slot != EquipmentSlot.BODY, "BODY is not an NPC-editable slot");
        }
    }

    @Test
    void theWornSlotsFitInsideThePanel() {
        // The Curios row ran off the right edge of the 176-wide panel when an NPC had all twelve
        // slots - drawn outside the frame and still clickable. Worn and drops must fit on it; the
        // Curios grid moved to the second panel and wraps.
        int pitch = 18;
        int margin = 8;
        assertTrue(margin + XenoNpcInventoryMenu.GEAR_SLOTS * pitch
                <= XenoNpcInventoryMenu.MAIN_PANEL_WIDTH, "the worn row must fit");
        assertTrue(margin + XenoNpcInventoryMenu.DROP_SLOTS * pitch
                <= XenoNpcInventoryMenu.MAIN_PANEL_WIDTH, "the drop row must fit");
        // The side panel is 141 wide, so six across is the most that fits with a margin either side.
        assertTrue(margin + XenoNpcInventoryMenu.CURIOS_PER_ROW * pitch <= 141,
                "the Curios grid must fit the side panel");
    }

    @Test
    void everyCuriosSlotWrapsOntoTheSidePanel() {
        // Twelve in one line is 224 pixels, wider than either panel. Two rows of six is 116.
        int slots = NpcCurios.ALL_SLOTS.size();
        int rows = (slots + XenoNpcInventoryMenu.CURIOS_PER_ROW - 1)
                / XenoNpcInventoryMenu.CURIOS_PER_ROW;
        assertEquals(12, slots);
        assertEquals(2, rows, "twelve slots wrap onto two rows");
    }

    @Test
    void theMenuAgreesWithTheStorageAboutHowManySlotsThereAre() {
        // A menu built with more slots than the profile can hold would drop items on close.
        assertEquals(NpcGear.SLOTS.length, XenoNpcInventoryMenu.GEAR_SLOTS);
        assertEquals(NpcDropList.MAX_DROPS, XenoNpcInventoryMenu.DROP_SLOTS);
    }
}

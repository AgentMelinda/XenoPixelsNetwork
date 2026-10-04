package net.bullettrain.xenopixelsmod.npc;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The NPC's saved tag is flat, and nothing in it collides with vanilla.
 *
 * <p>My NPCs and CustomNPCs both write their NPC data as ~180 keys at the <em>root</em> of the
 * entity tag — {@code readAdditionalSaveData} passes the same root compound to every sub-object, so
 * the {@code "display"}/{@code "stats"} labels in their code are error-log labels, not
 * sub-compounds. Matching that shape is what makes a future import a rename rather than a
 * restructure.
 *
 * <p>The cost of going flat is that our keys become siblings of vanilla's, and a clash is silent:
 * one side overwrites the other and the entity stops round-tripping. This is not hypothetical —
 * {@code XenoNpcData} wrote {@code "Brain"} as an int while {@code LivingEntity} writes
 * {@code "Brain"} as a compound. Nested, they never met. Flat, they would have.
 *
 * <p>So this test exists to fail loudly the next time somebody adds a field.
 */
class XenoNpcFlatSchemaTest {

    /**
     * Every key vanilla itself writes into an entity tag for a {@code PathfinderMob}.
     *
     * <p>Extracted from {@code Entity}, {@code LivingEntity} and {@code Mob} in
     * {@code neoforge-21.1.248-sources.jar} rather than recalled. If the mapping moves in a future
     * version this list moves with it.
     */
    private static final Set<String> VANILLA = Set.of(
            "AbsorptionAmount", "Air", "ArmorDropChances", "ArmorItems", "Brain", "CanPickUpLoot",
            "CustomName", "CustomNameVisible", "DeathLootTable", "DeathLootTableSeed", "DeathTime",
            "FallDistance", "FallFlying", "Fire", "Glowing", "HandDropChances", "HandItems",
            "HasVisualFire", "Health", "HurtByTimestamp", "HurtTime", "Invulnerable", "LeftHanded",
            "Motion", "NeoForgeData", "NoAI", "NoGravity", "OnGround", "Passengers",
            "PersistenceRequired", "PortalCooldown", "Pos", "Rotation", "Silent", "SleepingX",
            "SleepingY", "SleepingZ", "Tags", "TicksFrozen", "UUID", "active_effects", "attributes",
            "body_armor_drop_chance", "body_armor_item", "id", "neoforge:spawn_type");

    private static Set<String> ourKeys() {
        Set<String> keys = new TreeSet<>(new XenoNpcData(XenoNpcRole.HUMANOID).toTag().getAllKeys());
        keys.addAll(new NpcCombatProfile().toTag().getAllKeys());
        return keys;
    }

    @Test
    void nothingWeWriteSharesANameWithVanilla() {
        List<String> clashes = new ArrayList<>();
        for (String key : ourKeys()) {
            if (VANILLA.contains(key)) {
                clashes.add(key);
            }
        }
        assertTrue(clashes.isEmpty(),
                "these keys would overwrite vanilla's once the tag is flat: " + clashes);
    }

    @Test
    void theIdentityTagCarriesNoBrainKeyAtAll() {
        // It used to write "Brain", and flattening exposed that twice over: the name collides with
        // LivingEntity's brain-memory compound, and the obvious rename to "BrainVersion" collides
        // with NpcCombatProfile's - which is the live one, the one the editor writes and the save
        // whitelist knows.
        //
        // The identity copy was dead either way: fromTag assigned V5 unconditionally and never
        // looked at the stored value. So it is gone rather than renamed a second time.
        CompoundTag identity = new XenoNpcData(XenoNpcRole.HUMANOID).toTag();
        assertTrue(!identity.contains("Brain"), "collides with LivingEntity's brain memories");
        assertTrue(!identity.contains("BrainVersion"), "collides with the profile's real one");
        assertTrue(new NpcCombatProfile().toTag().contains("BrainVersion"),
                "the profile still owns the one that means something");
    }

    @Test
    void ourOwnTwoHalvesDoNotCollideEither() {
        // The identity tag and the combat profile become siblings too, so a name used by both is
        // just as fatal as one shared with vanilla — and far easier to introduce by accident.
        Set<String> identity = new TreeSet<>(new XenoNpcData(XenoNpcRole.HUMANOID).toTag().getAllKeys());
        Set<String> profile = new TreeSet<>(new NpcCombatProfile().toTag().getAllKeys());
        List<String> shared = new ArrayList<>(identity);
        shared.retainAll(profile);
        assertTrue(shared.isEmpty(), "identity and profile both write: " + shared);
    }

    @Test
    void aWorldSavedBeforeTheFlatteningStillLoads() {
        // The failure this prevents is the quiet one. Reading from the root of an old tag finds
        // nothing, and fromTag hands back a default NPC - name, title, faction, home and leash all
        // reset. Nothing errors; the NPC is just somebody else when the chunk next loads.
        CompoundTag nested = new CompoundTag();
        nested.putInt("Schema", 1);
        nested.putString("Role", XenoNpcRole.GUARD.id());
        nested.putInt("Brain", 5);
        nested.putString("Name", "Nail");
        nested.putString("Title", "Guardian");
        nested.putString("Faction", "namek");
        nested.putInt("Revision", 7);
        nested.putDouble("HomeX", 10.0);
        nested.putDouble("HomeY", 64.0);
        nested.putDouble("HomeZ", -20.0);
        nested.putDouble("LeashRadius", 12.0);

        CompoundTag old = new CompoundTag();
        old.put(XenoNpcData.LEGACY_ROOT, nested);
        // Whatever vanilla had written alongside it, to prove the unwrap does not disturb it.
        old.putFloat("Health", 20.0f);

        XenoNpcData loaded = XenoNpcData.fromTag(old, XenoNpcRole.HUMANOID);
        assertEquals(XenoNpcRole.GUARD, loaded.role());
        assertEquals("Nail", loaded.displayName());
        assertEquals("Guardian", loaded.title());
        assertEquals("namek", loaded.faction());
        assertEquals(7, loaded.revision());
        assertTrue(loaded.hasHome());
        assertEquals(10.0, loaded.homeX());
        assertEquals(12.0, loaded.leashRadius());
    }

    @Test
    void theLegacyUnwrapRenamesTheVersionAndDropsTheDeadBrainKey() {
        CompoundTag nested = new CompoundTag();
        nested.putInt("Schema", 1);
        nested.putInt("Brain", 5);
        nested.putString("Role", XenoNpcRole.QUEST.id());
        CompoundTag old = new CompoundTag();
        old.put(XenoNpcData.LEGACY_ROOT, nested);

        CompoundTag lifted = XenoNpcData.unwrapLegacy(old);
        assertTrue(lifted.contains(XenoNpcData.TAG_SCHEMA), "the version moves to its new name");
        assertTrue(!lifted.contains("Schema"), "and leaves the profile's name alone");
        assertTrue(!lifted.contains("Brain"), "the dead brain key does not come along");
        assertTrue(!lifted.contains("BrainVersion"), "and is not resurrected under the new name");
    }

    @Test
    void aFlatTagIsLeftAlone() {
        // unwrapLegacy runs on every read, so it has to be a no-op for tags that are already flat.
        CompoundTag flat = new XenoNpcData(XenoNpcRole.TRADER).toTag();
        assertEquals(flat, XenoNpcData.unwrapLegacy(flat));
        assertEquals(null, XenoNpcData.unwrapLegacy(null));
    }

    @Test
    void aFlattenedTagRoundTripsThroughTheRoot() {
        // What the entity actually does: merge the identity into the root beside vanilla's keys,
        // then read it back from there.
        XenoNpcData data = new XenoNpcData(XenoNpcRole.COMPANION);
        data.setDisplayName("Bubbles");
        data.setFaction("kai");
        data.setHome(1.0, 2.0, 3.0);

        CompoundTag root = new CompoundTag();
        root.putFloat("Health", 20.0f);
        root.putString("CustomName", "\"ignored\"");
        CompoundTag identity = data.toTag();
        for (String key : identity.getAllKeys()) {
            root.put(key, identity.get(key).copy());
        }

        XenoNpcData loaded = XenoNpcData.fromTag(root, XenoNpcRole.HUMANOID);
        assertEquals(XenoNpcRole.COMPANION, loaded.role());
        assertEquals("Bubbles", loaded.displayName());
        assertEquals("kai", loaded.faction());
        assertTrue(loaded.hasHome());
        assertEquals(20.0f, root.getFloat("Health"), "vanilla's own keys are untouched");
    }

    @Test
    void theEntityWritesFlatRatherThanNested() throws java.io.IOException {
        String entity = java.nio.file.Files.readString(
                net.bullettrain.xenopixelsmod.RepoRoot.of(
                        "src/main/java/net/bullettrain/xenopixelsmod/npc", "XenoNpcEntity.java"),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(!entity.contains("tag.put(DATA_KEY, npcData.toTag())"),
                "the nested write is what this replaced");
        assertTrue(entity.contains("XenoNpcData.fromTag(tag, registeredRole)"),
                "and the read comes from the root");
    }

    @Test
    void theSchemaVersionIsDeclaredAndFlat() {
        // Renamed from "Schema" because the profile writes that key too, with its own numbering -
        // currently 17 against this one's 2. Flat, each would have read the other's version, and
        // the profile would have re-run its whole migration chain over an already-migrated tag.
        CompoundTag tag = new XenoNpcData(XenoNpcRole.HUMANOID).toTag();
        assertTrue(tag.contains(XenoNpcData.TAG_SCHEMA),
                "a versioned tag is what makes the next change safe");
        assertEquals(XenoNpcData.SCHEMA_VERSION, tag.getInt(XenoNpcData.TAG_SCHEMA));
        assertTrue(!tag.contains("Schema"), "that name belongs to the profile");
    }
}

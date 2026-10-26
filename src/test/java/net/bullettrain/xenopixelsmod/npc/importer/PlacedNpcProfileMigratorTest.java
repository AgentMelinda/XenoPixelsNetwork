package net.bullettrain.xenopixelsmod.npc.importer;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacedNpcProfileMigratorTest {
    @Test
    void carriesAuthoredInventoryGearDropChancesAndExperienceWithoutMutatingSource() {
        CompoundTag source = new CompoundTag();
        ListTag items = new ListTag();
        CompoundTag item = new CompoundTag();
        item.putByte("Slot", (byte) 4);
        item.putString("id", "minecraft:diamond");
        item.putInt("count", 3);
        CompoundTag components = new CompoundTag();
        components.putString("minecraft:custom_name", "\"Prize\"");
        item.put("components", components);
        items.add(item);
        source.put("NpcInv", items);
        ListTag chance = new ListTag();
        CompoundTag row = new CompoundTag();
        row.putInt("Slot", 4);
        row.putFloat("Integer", 42.5f);
        chance.add(row);
        source.put("DropChance", chance);
        source.putInt("MinExp", 7);
        source.putInt("MaxExp", 25);
        source.putInt("LootMode", 1);
        ListTag armor = new ListTag();
        CompoundTag helmet = item.copy();
        helmet.putByte("Slot", (byte) 0);
        helmet.putString("id", "minecraft:diamond_helmet");
        armor.add(helmet);
        source.put("Armor", armor);
        ListTag weapons = new ListTag();
        CompoundTag offhand = item.copy();
        offhand.putByte("Slot", (byte) 2);
        offhand.putString("id", "minecraft:shield");
        weapons.add(offhand);
        source.put("Weapons", weapons);
        CompoundTag original = source.copy();
        CompoundTag profile = PlacedNpcProfileMigrator.profileTag(source);
        var drops = new net.bullettrain.xenopixelsmod.npc.inventory.NpcDropList();
        drops.loadFrom(profile);
        assertEquals(42.5f, drops.get(4).chance());
        assertEquals("minecraft:diamond", drops.get(4).item().saved().getString("id"));
        assertEquals(components, drops.get(4).item().saved().getCompound("components"));
        assertFalse(drops.get(4).item().saved().contains("Slot"));
        assertEquals(7, drops.minExp());
        assertEquals(25, drops.maxExp());
        assertEquals(net.bullettrain.xenopixelsmod.npc.inventory.NpcLootMode.AUTO_PICKUP, drops.lootMode());
        assertEquals("minecraft:diamond_helmet", profile.getCompound("Gear").getCompound("Head").getString("id"));
        assertEquals("minecraft:shield", profile.getCompound("Gear").getCompound("Off").getString("id"));
        assertEquals(original, source);
    }

    @Test
    void mapsDmzCharacterStatsFormsAppearanceAndEquipment() {
        CompoundTag source = new CompoundTag();
        source.putString("Texture", "mynpcs:textures/entity/humanmale/steve.png");
        source.putString("ModelId", "mynpcs:human");
        source.putString("ModelAnimation", "mynpcs:animations/human_combat.animation.json");
        source.putInt("ModelType", 2);
        source.putInt("ModelTint", 0x123456);
        source.putBoolean("ModelGlowing", true);
        source.putInt("Size", 5);
        CompoundTag data = new CompoundTag();
        CompoundTag dmz = new CompoundTag();
        CompoundTag character = new CompoundTag();
        character.putString("Race", "saiyan");
        character.putString("Class", "spiritualist");
        character.putString("Gender", "female");
        character.putInt("BodyType", 2);
        character.putString("BodyColor", "#123456");
        character.putString("CurrentFormGroup", "ssj");
        character.putString("CurrentForm", "ssj1");
        dmz.put("Character", character);
        CompoundTag stats = new CompoundTag();
        stats.putInt("STR", 101);
        stats.putInt("SKP", 102);
        stats.putInt("RES", 103);
        stats.putInt("VIT", 10_000);
        stats.putInt("PWR", 105);
        stats.putInt("ENE", 106);
        dmz.put("Stats", stats);
        CompoundTag resources = new CompoundTag();
        resources.putInt("Release", 80);
        dmz.put("Resources", resources);
        data.put("xenopixels:npc_dmz_stats", dmz);
        source.put("NeoForgeData", data);
        ListTag hands = new ListTag();
        CompoundTag sword = new CompoundTag();
        sword.putString("id", "minecraft:iron_sword");
        hands.add(sword);
        hands.add(new CompoundTag());
        source.put("HandItems", hands);

        CompoundTag profile = PlacedNpcProfileMigrator.profileTag(source);
        assertEquals("saiyan", profile.getString("Race"));
        assertEquals("ENTITY", profile.getString("ModelKind"));
        assertEquals("mynpcs:human", profile.getString("ModelId"));
        assertEquals("mynpcs:animations/human_combat.animation.json",
                profile.getString("ModelAnimation"));
        assertEquals(0x123456, profile.getInt("ModelTint"));
        assertTrue(profile.getBoolean("ModelGlowing"));
        assertEquals(10_000, profile.getInt("Vitality"));
        assertEquals(80, profile.getInt("PowerReleasePercent"));
        assertEquals("FULL", profile.getCompound("DmzAppearance").getString("Mode"));
        assertEquals("#123456", profile.getCompound("DmzAppearance").getString("BodyColor"));
        assertEquals("ssj1", profile.getString("Form"));
        assertTrue(profile.contains("Gear", Tag.TAG_COMPOUND));
        assertEquals("minecraft:iron_sword", profile.getCompound("Gear")
                .getCompound("Main").getString("id"));
    }

    @Test
    void conversionIsIdempotentAndPreservesOpaqueSource() {
        CompoundTag source = new CompoundTag();
        source.putString("FuturePlayerOnlyField", "keep-me");
        assertTrue(PlacedNpcProfileMigrator.migrate(source, "mynpcs"));
        assertTrue(source.getCompound("NeoForgeData").contains("XenoPixelsSource", Tag.TAG_COMPOUND));
        assertEquals("keep-me", source.getCompound("NeoForgeData").getCompound("XenoPixelsSource")
                .getCompound("OriginalEntity").getString("FuturePlayerOnlyField"));
        assertTrue(!PlacedNpcProfileMigrator.migrate(source, "mynpcs"));
    }

    @Test
    void readsDmzStatsFromTheLiveNeoForgeAttachmentContainer() {
        // A placed CNPC entity carries its DMZ stats where NeoForge persists attachments, under the
        // registered id. Reading only the old NeoForgeData shape silently produced a profile with no
        // race, stats or forms - and then NpcCounterpartSync wrote that empty profile back over the
        // attachment, so the loss was permanent. This test is the regression for that.
        CompoundTag source = new CompoundTag();
        CompoundTag attachments = new CompoundTag();
        CompoundTag dmz = new CompoundTag();
        CompoundTag character = new CompoundTag();
        character.putString("Race", "namekian");
        character.putString("Gender", "male");
        character.putString("CurrentForm", "orange");
        dmz.put("Character", character);
        CompoundTag stats = new CompoundTag();
        stats.putInt("VIT", 4_321);
        dmz.put("Stats", stats);
        attachments.put("xenopixelsmod:npc_dmz_stats", dmz);
        source.put("neoforge:attachments", attachments);

        CompoundTag profile = PlacedNpcProfileMigrator.profileTag(source);
        assertEquals("namekian", profile.getString("Race"));
        assertEquals("male", profile.getCompound("DmzAppearance").getString("Gender"));
        assertEquals("orange", profile.getString("Form"));
        assertEquals(4_321, profile.getInt("Vitality"));
    }

    @Test
    void absentAttachmentLeavesNativeDefaultsUntouched() {
        CompoundTag profile = PlacedNpcProfileMigrator.profileTag(new CompoundTag());
        // The documented default race, not something the source supplied.
        assertEquals("human", profile.getString("Race"));
        // Defaults that matter because they are true: a missing key must not read as false.
        assertTrue(profile.getBoolean("CanDrown"));
        assertTrue(profile.getBoolean("CobwebAffected"));
    }

    @Test
    void convertsCnpcResistanceMultipliersIntoReductionPercentages() {
        // CNPC stores damage multipliers (damage *= 2 - r), so 1.0 is "no change", 1.5 is 50% reduced
        // and 2.0 is immune. Below 1.0 the source amplifies damage, which the native field cannot say.
        CompoundTag source = new CompoundTag();
        CompoundTag resistances = new CompoundTag();
        resistances.putFloat("Melee", 1.5f);
        resistances.putFloat("Arrow", 2.0f);
        resistances.putFloat("Explosion", 1.0f);
        resistances.putFloat("Knockback", 0.5f);
        source.put("Resistances", resistances);

        CompoundTag props = PlacedNpcProfileMigrator.profileTag(source)
                .getCompound("NpcResistanceProps");
        assertEquals(50, props.getInt("Melee"));
        assertEquals(100, props.getInt("Arrow"));
        assertEquals(0, props.getInt("Explosion"));
        assertEquals(0, props.getInt("Knockback"));
    }

    @Test
    void mapsProtectionFlagsAndInvertsCobwebIgnoring() {
        CompoundTag source = new CompoundTag();
        source.putBoolean("ImmuneToFire", true);
        source.putBoolean("PotionImmune", true);
        source.putBoolean("NoFallDamage", true);
        source.putBoolean("CanDrown", false);
        source.putBoolean("IgnoreCobweb", true);
        source.putBoolean("BossBar", true);

        CompoundTag profile = PlacedNpcProfileMigrator.profileTag(source);
        assertTrue(profile.getBoolean("ImmuneToFire"));
        assertTrue(profile.getBoolean("PotionImmune"));
        assertTrue(profile.getBoolean("NoFallDamage"));
        assertTrue(!profile.getBoolean("CanDrown"));
        // CNPC asks "does this NPC ignore cobwebs"; the native field is the other way round.
        assertTrue(!profile.getBoolean("CobwebAffected"));
        assertTrue(profile.getBoolean("BossBar"));
    }

    @Test
    void mapsJobOrdinalsAndRefusesToInventJobsOutsideTheEnum() {
        CompoundTag guard = new CompoundTag();
        guard.putInt("NpcJob", 3);
        assertEquals("guard", PlacedNpcProfileMigrator.profileTag(guard).getString("Job"));

        CompoundTag future = new CompoundTag();
        future.putInt("NpcJob", 99);
        assertTrue(!PlacedNpcProfileMigrator.profileTag(future).contains("Job"));
        assertTrue(!PlacedNpcProfileMigrator.profileTag(new CompoundTag()).contains("Job"));
        assertEquals("", PlacedNpcProfileMigrator.jobIdFor(0));
        assertEquals("item_giver", PlacedNpcProfileMigrator.jobIdFor(4));
        assertEquals("", PlacedNpcProfileMigrator.jobIdFor(-1));
    }

    @Test
    void copiesEveryLineGroupIntoItsNativeCategoryAndAppliesTheCaps() {
        CompoundTag source = new CompoundTag();
        source.put("NpcInteractLines", lineGroup("Hello there", "  ", "Farewell"));
        source.put("NpcKilledLines", lineGroup("You were not so tough"));
        StringBuilder longLine = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            longLine.append('x');
        }
        ListTag many = new ListTag();
        for (int i = 0; i < 20; i++) {
            many.add(line(i == 0 ? longLine.toString() : "Line " + i));
        }
        CompoundTag attack = new CompoundTag();
        attack.put("Lines", many);
        source.put("NpcAttackLines", attack);

        CompoundTag lines = PlacedNpcProfileMigrator.profileTag(source).getCompound("NpcLines");
        assertEquals(List.of("Hello there", "Farewell"), strings(lines, "INTERACT"));
        assertEquals(List.of("You were not so tough"), strings(lines, "KILLED"));
        // 20 source lines fit the sixteen-per-category cap; the 300-character line fits 256.
        List<String> attackLines = strings(lines, "ATTACK");
        assertEquals(16, attackLines.size());
        assertEquals(256, attackLines.get(0).length());
    }

    @Test
    void bindsTheScriptReferenceWithoutCopyingScriptText() {
        CompoundTag profile = PlacedNpcProfileMigrator.profileTag(new CompoundTag(),
                "mynpcs_00000000-0000-0000-0000-000000000000_s1");
        assertEquals("mynpcs_00000000-0000-0000-0000-000000000000_s1", profile.getString("ScriptId"));
        assertTrue(!PlacedNpcProfileMigrator.profileTag(new CompoundTag()).contains("ScriptId"));
    }

    @Test
    void mapsAiFlagsAndEnumSelectorsWithVerifiedOrdinalSemantics() {
        // Ordinal semantics read out of the shipped CNPC jar: OnAttack and FindShelter share the
        // native order, DoorInteract is reversed (CNPC {Break, Open, Disabled} vs native
        // {Disabled, Open, Break}), so CNPC 0 (Break) must land on native BREAK.
        CompoundTag source = new CompoundTag();
        source.putBoolean("CanSwim", false);
        source.putBoolean("AvoidsWater", true);
        source.putBoolean("CanLeap", true);
        source.putBoolean("ReturnToStart", false);
        source.putBoolean("DirectLOS", false);
        source.putBoolean("AttackInvisible", true);
        source.putBoolean("MountControl", true);
        source.putInt("OnAttack", 1);
        source.putInt("DoorInteract", 0);
        source.putInt("FindShelter", 1);

        NpcCombatProfile profile = NpcCombatProfile.fromTag(
                PlacedNpcProfileMigrator.profileTag(source));
        assertTrue(!profile.aiCanSwim);
        assertTrue(profile.aiAvoidsWater);
        assertTrue(profile.aiLeapAtTarget);
        assertTrue(!profile.aiReturnToStart);
        assertTrue(!profile.aiMustSeeTarget);
        assertTrue(profile.aiAttackInvisible);
        assertTrue(profile.aiMountControl);
        assertEquals(net.bullettrain.xenopixelsmod.npc.NpcOnFoundEnemy.PANIC, profile.aiOnFoundEnemy);
        assertEquals(net.bullettrain.xenopixelsmod.npc.NpcDoorInteract.BREAK, profile.aiDoorInteract);
        assertEquals(net.bullettrain.xenopixelsmod.npc.NpcShelterFrom.SUNLIGHT, profile.aiShelterFrom);
    }

    @Test
    void absentAiKeysKeepTheNativeDefaultsIncludingTheOnesThatAreTrue() {
        NpcCombatProfile profile = NpcCombatProfile.fromTag(
                PlacedNpcProfileMigrator.profileTag(new CompoundTag()));
        assertTrue(profile.aiCanSwim);
        assertTrue(profile.aiReturnToStart);
        assertTrue(profile.aiMustSeeTarget);
        assertTrue(!profile.aiAvoidsWater);
        assertEquals(net.bullettrain.xenopixelsmod.npc.NpcDoorInteract.DISABLED, profile.aiDoorInteract);
    }

    @Test
    void mapsStatsHealthRegenCreatureFamilyAndDisplayKnobs() {
        CompoundTag source = new CompoundTag();
        source.putBoolean("BurnInSun", true);
        // The absurd value is what real clone data carries (MaxHealth: 1000002999); the importer
        // copies it and the native side clamps where it applies.
        source.putInt("MaxHealth", 1000002999);
        source.putInt("HealthRegen", 2);
        source.putInt("CombatRegen", 5);
        source.putInt("CreatureType", 2);
        source.putInt("BossColor", 3);
        source.putInt("NpcVisible", 1);

        NpcCombatProfile profile = NpcCombatProfile.fromTag(
                PlacedNpcProfileMigrator.profileTag(source));
        assertTrue(profile.burnsInSun);
        assertEquals(1000002999, profile.maxHealthOverride);
        // CNPC heals these many HP every 20 ticks, which is the native per-second meaning.
        assertEquals(2.0f, profile.healthRegen, 0.0f);
        assertEquals(5.0f, profile.combatRegen, 0.0f);
        assertEquals("arthropod", profile.creatureType);
        assertEquals("GREEN", profile.bossBarColor);
        assertTrue(!profile.visible);
    }

    @Test
    void mapsMeleeAndRangedAttackModelIntoTheNativeProfile() {
        CompoundTag source = new CompoundTag();
        source.putInt("AttackStrenght", 4001);
        source.putInt("AttackRange", 3);
        source.putInt("AttackSpeed", 10);
        source.putInt("KnockBack", 2);
        source.putInt("Accuracy", 60);
        source.putInt("MaxFiringRange", 15);
        source.putInt("DistanceToMelee", 5);
        source.putInt("minDelay", 20);
        source.putInt("maxDelay", 40);
        source.putInt("ShotCount", 3);
        source.putInt("BurstCount", 2);
        source.putInt("FireRate", 5);
        source.putInt("FireIndirect", 1);
        source.putBoolean("AimWhileShooting", false);
        source.putString("FiringSound", "minecraft:entity.arrow.shoot");

        NpcCombatProfile profile = NpcCombatProfile.fromTag(
                PlacedNpcProfileMigrator.profileTag(source));
        // 4001 exceeds the native melee damage ceiling and is clamped, not dropped.
        assertEquals(2048.0f, profile.npcMeleeDamage, 0.0f);
        assertEquals(3.0f, profile.npcMeleeRange, 0.0f);
        // CNPC counts 10 ticks per swing; native 1.0 is one swing per second, so this is 2x.
        assertEquals(2.0f, profile.npcMeleeSpeed, 0.0f);
        assertEquals(2.0f, profile.npcMeleeKnockback, 0.0f);
        // CNPC accuracy is a 0..100 percent; native is a 0..1 blend.
        assertEquals(0.6f, profile.npcRangedAccuracy, 0.0f);
        assertEquals(15.0f, profile.npcRangedRange, 0.0f);
        assertEquals(5.0f, profile.npcRangedMinRange, 0.0f);
        assertEquals(20, profile.npcRangedMinDelay);
        assertEquals(40, profile.npcRangedMaxDelay);
        assertEquals(3, profile.npcRangedShotCount);
        assertEquals(2, profile.npcRangedBurstCount);
        assertEquals(5, profile.npcRangedBurstRate);
        assertTrue(profile.npcRangedIndirect);
        assertEquals("no", profile.npcRangedAimMode);
        assertEquals("minecraft:entity.arrow.shoot", profile.npcRangedFireSound);
    }

    /**
     * Three DataDisplay booleans whose source key and native control are the same switch. An absent
     * key must leave the native default alone: {@code displayOuterLayers} is on, so reading a missing
     * key as false would strip the skin layer off every NPC whose save predates the field.
     */
    @Test
    void carriesTheDisplayBooleansThatNameTheNativeSwitch() {
        CompoundTag source = new CompoundTag();
        source.putBoolean("ShowLayers", false);
        source.putBoolean("OverlayGlowing", false);
        source.putBoolean("HideBodyWhenKilled", true);

        NpcCombatProfile set = NpcCombatProfile.fromTag(PlacedNpcProfileMigrator.profileTag(source));
        assertFalse(set.displayOuterLayers, "Showing Layers off arrives off");
        assertFalse(set.displayOverlayGlow, "Overlay glows off arrives off");
        assertTrue(set.hideDeadBody, "a corpse the author hides stays hidden");

        NpcCombatProfile absent = NpcCombatProfile.fromTag(
                PlacedNpcProfileMigrator.profileTag(new CompoundTag()));
        assertTrue(absent.displayOuterLayers, "no key is not the same as an off key");
        assertFalse(absent.displayOverlayGlow);
        assertFalse(absent.hideDeadBody);
    }

    @Test
    void mapsTheSoundKitAndSkipsBlankSourceEntries() {
        CompoundTag source = new CompoundTag();
        source.putString("NpcIdleSound", "");
        source.putString("NpcHurtSound", "minecraft:entity.player.hurt");
        source.putString("NpcAngrySound", "minecraft:entity.zombie.ambient");
        source.putBoolean("DisablePitch", true);

        NpcCombatProfile profile = NpcCombatProfile.fromTag(
                PlacedNpcProfileMigrator.profileTag(source));
        assertEquals("minecraft:entity.player.hurt", profile.soundHurt);
        assertEquals("minecraft:entity.zombie.ambient", profile.soundAngry);
        // A blank source entry means "no custom sound", same as the native default.
        assertEquals("", profile.soundLiving);
        assertTrue(!profile.soundHasPitch);
    }

    private static List<String> strings(CompoundTag tag, String key) {
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            out.add(list.get(i).getAsString());
        }
        return out;
    }

    private static CompoundTag lineGroup(String... texts) {
        ListTag list = new ListTag();
        for (String text : texts) {
            list.add(line(text));
        }
        CompoundTag group = new CompoundTag();
        group.put("Lines", list);
        return group;
    }

    private static CompoundTag line(String text) {
        CompoundTag entry = new CompoundTag();
        entry.putString("Line", text);
        return entry;
    }
}

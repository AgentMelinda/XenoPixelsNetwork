package net.bullettrain.xenopixelsmod.npc.importer;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The point of the audit is that no source key can go missing without a line in the report, while the
 * report stays short enough that someone reads it.
 */
class NpcSourceKeysTest {

    @Test
    void keysTheImporterHandlesProduceNoNoise() {
        NpcImportReport report = new NpcImportReport();
        CompoundTag source = new CompoundTag();
        source.putString("Name", "Dodoria");
        source.putInt("NpcJob", 3);
        source.putString("Texture", "mynpcs:dodoria.png");
        source.put("Resistances", new CompoundTag());

        NpcSourceKeys.audit(source, "mynpcs/abc", report);
        assertEquals(0, report.notes().size(), report.notes().toString());
    }

    @Test
    void aFamilyIsNamedByItsExactKeysInOneFoldedLine() {
        NpcImportReport report = new NpcImportReport();
        CompoundTag source = new CompoundTag();
        source.putBoolean("AvoidsSun", true);
        source.putBoolean("ReactsToFire", true);
        source.putBoolean("Invulnerable", false);

        NpcSourceKeys.audit(source, "mynpcs/abc", report);

        assertEquals(1, report.notes().size(), report.notes().toString());
        String line = report.notes().get(0);
        assertTrue(line.contains("\"AvoidsSun\"") && line.contains("\"ReactsToFire\"")
                        && line.contains("\"Invulnerable\""),
                line);
        assertTrue(line.contains("mynpcs/abc"), line);
    }

    /**
     * Every key Phase N started converting must be consumed, and consumed exactly once: the audit
     * checks the unmapped families before the consumed set, so a key left in both buckets would be
     * reported even though it did arrive. This is the double-reporting regression test.
     */
    @Test
    void convertedAiStatsCombatAndSoundKeysProduceNoNoise() {
        NpcImportReport report = new NpcImportReport();
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
        source.putBoolean("BurnInSun", true);
        source.putInt("MaxHealth", 1000002999);
        source.putInt("HealthRegen", 2);
        source.putInt("CombatRegen", 5);
        source.putInt("CreatureType", 2);
        source.putInt("BossColor", 3);
        source.putInt("NpcVisible", 1);
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
        source.putString("HitSound", "");
        source.putString("GroundSound", "");
        source.putString("NpcIdleSound", "");
        source.putString("NpcAngrySound", "minecraft:entity.zombie.ambient");
        source.putString("NpcHurtSound", "minecraft:entity.player.hurt");
        source.putString("NpcDeathSound", "minecraft:entity.player.hurt");
        source.putString("NpcStepSound", "");
        source.putBoolean("DisablePitch", true);

        NpcSourceKeys.audit(source, "mynpcs/abc", report);
        assertEquals(0, report.notes().size(), report.notes().toString());
    }

    @Test
    void aKeyNobodyRecognisesIsReportedByName() {
        NpcImportReport report = new NpcImportReport();
        CompoundTag source = new CompoundTag();
        source.putString("NpcSomethingNewInVersion9", "value");

        NpcSourceKeys.audit(source, "mynpcs/abc", report);

        assertEquals(1, report.notes().size(), report.notes().toString());
        assertTrue(report.notes().get(0).contains("NpcSomethingNewInVersion9"),
                report.notes().toString());
        assertTrue(report.notes().get(0).contains("not understood"), report.notes().toString());
    }

    @Test
    void anotherModsRuntimeStateIsNotReportedPerKey() {
        NpcImportReport report = new NpcImportReport();
        CompoundTag source = new CompoundTag();
        // DragonMineZ technique cooldowns and vanilla bookkeeping: regenerated by the live entity.
        source.putInt("dragonminez:technique_cooldown", 20);
        source.putFloat("FallDistance", 1.5f);
        source.putInt("Air", 300);

        NpcSourceKeys.audit(source, "mynpcs/abc", report);
        assertEquals(0, report.notes().size(), report.notes().toString());
    }

    @Test
    void aLineThatWantsItsOwnSoundSaysSoBecauseTheNativeProfileCarriesTextOnly() {
        NpcImportReport report = new NpcImportReport();
        CompoundTag source = new CompoundTag();
        CompoundTag group = new CompoundTag();
        net.minecraft.nbt.ListTag lines = new net.minecraft.nbt.ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putString("Line", "Feel my power!");
        entry.putString("Song", "mynpcs:dodoria_theme");
        lines.add(entry);
        group.put("Lines", lines);
        source.put("NpcInteractLines", group);

        NpcSourceKeys.audit(source, "mynpcs/abc", report);

        assertTrue(report.notes().stream().anyMatch(n -> n.contains("per-line sound")),
                report.notes().toString());
    }

    @Test
    void aResistanceThatAsksForExtraDamageIsSaidOutLoud() {
        NpcImportReport report = new NpcImportReport();
        CompoundTag source = new CompoundTag();
        CompoundTag resistances = new CompoundTag();
        resistances.putFloat("Melee", 0.5f);
        resistances.putFloat("Arrow", 1.0f);
        resistances.putFloat("Explosion", 1.5f);
        source.put("Resistances", resistances);

        NpcSourceKeys.audit(source, "mynpcs/abc", report);

        assertEquals(1, report.notes().size(), report.notes().toString());
        assertTrue(report.notes().get(0).contains("\"Melee\""), report.notes().toString());
        assertTrue(report.notes().get(0).contains("extra damage"), report.notes().toString());
    }

    @Test
    void aNullSourceIsNotAnError() {
        NpcImportReport report = new NpcImportReport();
        NpcSourceKeys.audit(null, "mynpcs/abc", report);
        assertEquals(0, report.notes().size());
    }

    /**
     * The buckets have to cover real data, not just the keys someone thought of.
     *
     * <p>Every top-level key on a shipped MyNPCs/CustomNPCs clone must be consumed, explained by a
     * family, dismissed as state, or reported as unknown. The last of those is what this test forbids:
     * if the source mod adds a field, the sweep starts naming it, and the fix is to decide which bucket
     * it belongs in rather than to let a converted NPC lose something nobody was told about.
     */
    @Test
    void everyKeyOnRealCloneDataLandsInABucket() {
        for (String name : List.of("goku_customnpcs.snbt", "mable_mynpcs.snbt",
                "vegeta_customnpcs.snbt")) {
            NpcImportReport report = new NpcImportReport();
            NpcSourceKeys.audit(fixture(name), "fixture/" + name, report);
            List<String> unexplained = new ArrayList<>();
            for (String note : report.notes()) {
                if (note.contains("not understood")) {
                    unexplained.add(note);
                }
            }
            assertEquals(List.of(), unexplained, name + " carries keys no bucket explains");
            assertTrue(report.notes().size() > 3,
                    name + " produced almost no notes, which means the audit is not looking: "
                            + report.notes());
        }
    }

    private static CompoundTag fixture(String name) {
        try (java.io.InputStream in = NpcSourceKeysTest.class.getResourceAsStream("/clones/" + name)) {
            assertTrue(in != null, "missing fixture: " + name);
            return net.minecraft.nbt.TagParser.parseTag(
                    new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException | com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("could not read " + name, e);
        }
    }
}

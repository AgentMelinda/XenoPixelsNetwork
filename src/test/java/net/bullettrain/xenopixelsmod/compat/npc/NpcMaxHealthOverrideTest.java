package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An explicit max health that wins over the vitality calculation.
 *
 * <p>NPCs normally start from the same 20-point health attribute as players and apply the live
 * {@code StatsData.getHealthBonus()} result. The override is an explicit per-NPC exception for
 * operators who need a fixed max HP independent of vitality.
 *
 * <p>It is, however, often not what an operator wants: vitality set for damage or resistance drags
 * a matching health pool along with it, and the only way down was to lower the stat that was set
 * for another reason. This field breaks that coupling for one NPC without forking the stat model.
 */
class NpcMaxHealthOverrideTest {

    private static String source(String dir, String file) throws IOException {
        return Files.readString(RepoRoot.of(dir, file), StandardCharsets.UTF_8);
    }

    private static String code(String dir, String file) throws IOException {
        return source(dir, file)
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    @Test
    void zeroMeansUnset() {
        // The default has to be the old behaviour, or every NPC already in a world changes health
        // the moment this ships.
        assertEquals(0, new NpcCombatProfile().maxHealthOverride);
    }

    @Test
    void itSurvivesASaveAndLoad() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.maxHealthOverride = 500;
        assertEquals(500, NpcCombatProfile.fromTag(profile.toTag()).maxHealthOverride);
    }

    @Test
    void aNegativeOverrideReadsAsUnset() {
        // Rather than as a negative max health, which would clamp to 1 and make the NPC unkillable
        // in a way nobody asked for.
        CompoundTag tag = new NpcCombatProfile().toTag();
        tag.putInt("MaxHealthOverride", -50);
        assertEquals(0, NpcCombatProfile.fromTag(tag).maxHealthOverride);
    }

    @Test
    void aTagWrittenBeforeTheFieldExistedReadsAsUnset() {
        // The compatibility invariant for every NPC already placed.
        CompoundTag old = new NpcCombatProfile().toTag();
        old.remove("MaxHealthOverride");
        assertEquals(0, NpcCombatProfile.fromTag(old).maxHealthOverride);
    }

    @Test
    void theSyncKeepsPlayerBaseAndUsesDmzHealthBonusUnlessOverridden() throws IOException {
        String sync = code("src/main/java/net/bullettrain/xenopixelsmod/compat/npc",
                "NpcVitalitySync.java");
        int branch = sync.indexOf("profile.maxHealthOverride > 0");
        assertTrue(branch >= 0, "the sync must branch on the override");
        int bonus = sync.indexOf("NpcDmzStats.healthBonus(living, profile)");
        assertTrue(bonus >= 0, "NPC HP must use the attached DMZ StatsData calculation");
        assertTrue(sync.contains("PLAYER_BASE_HEALTH = 20.0"),
                "DMZ player HP starts from a base of 20");
        assertTrue(sync.contains("AttributeMods.id(StatsEvents.DMZ_HEALTH_MODIFIER_UUID)"),
                "NPC health must use the same modifier id as DMZ players");
        assertTrue(sync.indexOf("profile.maxHealthOverride > 0", sync.indexOf("double attributeBase"))
                        < sync.indexOf("XenoServerConfig.npcDmzStatsAuthoritative ? PLAYER_BASE_HEALTH"),
                "an explicit override wins; otherwise authoritative NPCs use the 20-point player base");
    }

    @Test
    void theFieldIsEditableAndHasARowThatFeedsIt() throws IOException {
        // The standing rule: a whitelist key goes in only once something reads the value, and a
        // row goes in only once it has somewhere to write.
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                "XenoNpcSavePolicy.java").contains("\"MaxHealthOverride\""));
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/client/npc",
                "XenoNpcEditorScreen.java").contains("profile.maxHealthOverride"));
    }

    @Test
    void theVitalityMathDocumentsTheReductionRatherThanClaimingExactParity() throws IOException {
        // The earlier comment read as a claim of exact parity and sent a reader hunting a bug that
        // did not exist. It now names the terms it leaves out and why they are zero for an NPC.
        String math = source("src/main/java/net/bullettrain/xenopixelsmod/compat/npc",
                "NpcVitalityMath.java");
        assertTrue(math.contains("no {@code StatsData}"),
                "the javadoc should say which terms cannot apply to an NPC");
        assertTrue(math.contains("secondaryStatEffects"),
                "and name them, so the next reader can check the claim against the jar");
        assertFalse(math.contains("DMZ {@code StatsData.getHealthBonus}: a {@code float} of"),
                "the old one-line claim is what this replaced");
    }
}

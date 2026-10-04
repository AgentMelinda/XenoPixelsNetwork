package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The assumption the NPC stats blob is built on, checked against DragonMineZ's own source.
 *
 * <p>{@link NpcDmzStats} hands DMZ a {@code StatsData} with no player behind it. That is only safe
 * because DMZ null-checks its player nearly everywhere, and the handful of methods that do not are
 * ones an NPC has no business calling — armour, armour enchantments, gravity training and the
 * Hyperbolic Time Chamber. If a DMZ update moves a method across that line, nothing at compile time
 * would say so and the first sign would be an NPC throwing mid-fight.
 *
 * <p>So the list is not trusted: it is re-derived here from the decompiled 2.1.3 source that ships
 * in the repository and compared with what {@link NpcDmzStats#safeForNpc} claims.
 */
class NpcDmzStatsTest {

    private static final Path STATS_DATA = RepoRoot.of(
            "tools/generated/dmz_decompiled_full/com/dragonminez/common/stats/StatsData.java");

    /**
     * {@code this.player} used as a value, rather than {@code this.playerQuestData} — the field
     * whose name starts with the same eleven characters and which is null-safe.
     */
    private static final Pattern DEREFERENCE = Pattern.compile("this\\.player(?![A-Za-z0-9_])");
    private static final Pattern NULL_CHECK =
            Pattern.compile("this\\.player\\s*(==|!=)\\s*null|this\\.player\\s+instanceof");
    private static final Pattern METHOD =
            Pattern.compile("^ {3}(?:public|private|protected)\\s+[\\w<>\\[\\],. ]+\\s+(\\w+)\\(");

    /** Methods that read the player without first checking it is there. */
    private static Set<String> unguardedMethods() throws IOException {
        List<String> lines = Files.readAllLines(STATS_DATA);
        Set<String> touched = new TreeSet<>();
        Set<String> guarded = new TreeSet<>();
        String method = "<constructor>";
        for (String line : lines) {
            Matcher declaration = METHOD.matcher(line);
            if (declaration.find()) {
                method = declaration.group(1);
            }
            if (!DEREFERENCE.matcher(line).find()) continue;
            if (NULL_CHECK.matcher(line).find()) {
                guarded.add(method);
            } else {
                touched.add(method);
            }
        }
        touched.removeAll(guarded);
        // The constructor's own assignment is not a dereference, and a getter that hands the field
        // out cannot NPE by itself; both are excluded from the callable surface this is about.
        touched.remove("<constructor>");
        return touched;
    }

    @Test
    void theDecompiledSourceIsWhereItIsExpectedToBe() {
        assertTrue(Files.exists(STATS_DATA),
                "the decompiled DragonMineZ source this check reads is missing: " + STATS_DATA);
    }

    /**
     * Every method DMZ leaves unguarded is one this refuses for an NPC, and vice versa.
     *
     * <p>An equality rather than a subset on purpose. Too few entries means an NPC can reach a
     * method that will throw; too many means we are refusing a method that would have worked, and
     * the NPC quietly loses a stat it could have had.
     */
    @Test
    void theRefusedMethodsAreExactlyTheOnesDragonMineZLeavesUnguarded() throws IOException {
        Set<String> unguarded = unguardedMethods();
        Set<String> refused = new TreeSet<>();
        for (String method : unguarded) {
            if (!NpcDmzStats.safeForNpc(method)) refused.add(method);
        }
        assertEquals(unguarded, refused,
                "NpcDmzStats.safeForNpc disagrees with DragonMineZ's source. Unguarded there: "
                        + unguarded + "; refused here: " + refused);
    }

    @Test
    void theOrdinaryStatAccessorsAreAllowed() {
        for (String method : List.of("getHealthBonus", "getMeleeDamage", "getStrikeDamage",
                "getKiDamage", "getKiDamageNoForms", "getStats",
                "getResources", "getCharacter", "save", "load", "getLevel")) {
            assertTrue(NpcDmzStats.safeForNpc(method),
                    method + " is refused, but an NPC needs it and DragonMineZ null-checks it");
        }
    }

    @Test
    void npcCombatReadsDmzsOwnPlayerStatCalculations() throws IOException {
        String statsData = Files.readString(STATS_DATA);
        assertTrue(statsData.contains("public float getHealthBonus()"));
        assertTrue(statsData.contains("public double getMeleeDamage()"));
        assertTrue(statsData.contains("public double getStrikeDamage()"));

        String npcStats = Files.readString(RepoRoot.of(
                "src/main/java/net/bullettrain/xenopixelsmod/compat/npc", "NpcDmzStats.java"));
        assertTrue(npcStats.contains("data.getHealthBonus()"));
        assertTrue(npcStats.contains("data.getMeleeDamage()"));
        assertTrue(npcStats.contains("data.getStrikeDamage()"));
    }

    @Test
    void nativeNpcUsesPlayerHealthAndMeleeBaselinesBeforeDmzStatFormulas() throws IOException {
        String entity = Files.readString(RepoRoot.of(
                "src/main/java/net/bullettrain/xenopixelsmod/npc", "XenoNpcEntity.java"));
        assertTrue(entity.contains("add(Attributes.MAX_HEALTH, 20.0)"),
                "native NPCs must start at the vanilla player 20 HP baseline");
        assertTrue(entity.contains("add(Attributes.ATTACK_DAMAGE, 1.0)"),
                "native NPCs must start at DMZ's one-damage melee baseline");

        String melee = Files.readString(RepoRoot.of(
                "src/main/java/net/bullettrain/xenopixelsmod/compat/npc", "NpcMeleeDamage.java"));
        assertTrue(melee.contains("attacker instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity"),
                "fresh native NPCs need DMZ melee calculation even before their first editor save");
        assertTrue(melee.contains("NpcDmzStats.meleeDamage(attacker, profile)"),
                "configured native NPCs must use the attached DMZ StatsData formula");

        String vitality = Files.readString(RepoRoot.of(
                "src/main/java/net/bullettrain/xenopixelsmod/compat/npc", "NpcVitalitySync.java"));
        assertTrue(vitality.contains("NpcDmzStats.healthBonus(living, profile)"),
                "health must add DMZ's own computed VIT bonus above the 20 base");
        assertTrue(vitality.contains("PLAYER_BASE_HEALTH = 20.0"));
    }

    /**
     * {@code save()} and {@code load()} in particular have to be safe.
     *
     * <p>They are how the attachment persists, so if either grew a player dereference the blob
     * would throw on every world save rather than in some corner of combat.
     */
    @Test
    void persistenceDoesNotNeedAPlayer() throws IOException {
        Set<String> unguarded = unguardedMethods();
        assertFalse(unguarded.contains("save"),
                "StatsData.save() now needs a player; the NPC attachment cannot serialise");
        assertFalse(unguarded.contains("load"),
                "StatsData.load() now needs a player; the NPC attachment cannot deserialise");
    }

    /**
     * Power release is a percentage DMZ divides by 100, so zero silently zeroes ki damage.
     *
     * <p>The profile spells "unset" as a non-positive number, which is exactly the value that would
     * make an NPC's attacks do nothing at all if it were passed through.
     */
    @Test
    void anUnsetPowerReleaseBecomesFullRatherThanZero() {
        assertEquals(100, NpcDmzStats.clampRelease(0));
        assertEquals(100, NpcDmzStats.clampRelease(-40));
        assertEquals(100, NpcDmzStats.clampRelease(100));
        assertEquals(1, NpcDmzStats.clampRelease(1));
        assertEquals(60, NpcDmzStats.clampRelease(60));
    }

    @Test
    void releaseAboveFullIsCappedRatherThanMultiplyingDamage() {
        assertEquals(100, NpcDmzStats.clampRelease(400));
        assertEquals(100, NpcDmzStats.clampRelease(Integer.MAX_VALUE));
    }
}

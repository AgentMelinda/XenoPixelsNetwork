package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The assumption the NPC stats blob is built on, checked against DragonMineZ's own bytecode.
 *
 * <p>{@link NpcDmzStats} hands DMZ a {@code StatsData} with no player behind it. That is only safe
 * because DMZ null-checks its player nearly everywhere, and the handful of methods that do not are
 * ones an NPC has no business calling - armour enchantments, gravity training and the Hyperbolic
 * Time Chamber. If a DMZ update moves a method across that line, nothing at compile time would say
 * so and the first sign would be an NPC throwing mid-fight.
 *
 * <p>So the list is not trusted: it is re-derived here from the pinned jar the game actually loads.
 * It used to be derived from {@code tools/generated/dmz_decompiled_full}, which is a 2.1.3 snapshot,
 * and 2.2 added {@code getFlatDefense} and {@code getMaxFlatDefense} on top of that. The stale copy
 * could not see them, so the equality below passed while the list was wrong.
 */
class NpcDmzStatsTest {

    private static final String STATS_DATA_ENTRY = "com/dragonminez/common/stats/StatsData.class";
    private static final String PLAYER = "net/minecraft/world/entity/player/Player";
    private static final String PLAYER_DESCRIPTOR = "L" + PLAYER + ";";

    /** What the pinned {@code StatsData} needs a player for. */
    private record PlayerBound(Set<String> unguarded, Map<String, Set<String>> uses, Set<String> all) {}

    /**
     * Every use of {@code this.player} that the method does not first compare against null.
     *
     * <p>Recorded as {@code armor} for {@code Player.getArmorValue()} - the one dereference
     * {@code StatsDataNpcHostMixin} redirects onto the NPC - and otherwise as the call the field is
     * fed into, which nothing outside DMZ's own player-bound code can neutralize.
     *
     * <p>A method that checks the player anywhere counts as checked for all of its uses, which is
     * what DMZ's own shape relies on: {@code if (this.player == null) return fallback;} then read.
     * The approximation is deliberate - the alternative is dominance analysis in a test - and it
     * errs in the direction that only ever over-refuses an NPC read, never under.
     */
    private static PlayerBound needsAPlayer() throws IOException {
        Map<String, Set<String>> uses = new TreeMap<>();
        Set<String> checked = new TreeSet<>();
        Set<String> all = new TreeSet<>();
        try (var jar = new ZipFile(RepoRoot.of("libs/dragonminez-2.2.jar").toFile());
             var input = jar.getInputStream(jar.getEntry(STATS_DATA_ENTRY))) {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            for (MethodNode method : node.methods) {
                if ("<init>".equals(method.name)) continue;
                all.add(method.name);
                List<AbstractInsnNode> code = new ArrayList<>();
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
                    if (insn.getOpcode() >= 0) code.add(insn);
                for (int i = 0; i + 1 < code.size(); i++) {
                    if (!(code.get(i) instanceof FieldInsnNode read) || !"player".equals(read.name)
                            || !PLAYER_DESCRIPTOR.equals(read.desc)) continue;
                    AbstractInsnNode next = code.get(i + 1);
                    if (branchesOnNull(next.getOpcode()) || next.getOpcode() == Opcodes.INSTANCEOF
                            || next instanceof InsnNode pushedNull && pushedNull.getOpcode() == Opcodes.ACONST_NULL
                                    && i + 2 < code.size() && branchesOnNull(code.get(i + 2).getOpcode())) {
                        checked.add(method.name); // this.player == null / != null / instanceof
                        continue;
                    }
                    uses.computeIfAbsent(method.name, ignored -> new TreeSet<>()).add(describe(next));
                }
            }
        }
        uses.keySet().removeAll(checked);
        return new PlayerBound(uses.keySet(), uses, all);
    }

    /** What the player field is used for, from the instruction that consumes it. */
    private static String describe(AbstractInsnNode next) {
        if (!(next instanceof MethodInsnNode call)) return "handed out";
        if (PLAYER.equals(call.owner) && "getArmorValue".equals(call.name)) return "armor";
        return call.owner.substring(call.owner.lastIndexOf('/') + 1) + "#" + call.name;
    }

    private static boolean branchesOnNull(int opcode) {
        return opcode == Opcodes.IFNULL || opcode == Opcodes.IFNONNULL
                || opcode == Opcodes.IF_ACMPEQ || opcode == Opcodes.IF_ACMPNE;
    }

    /** The methods {@code StatsDataNpcHostMixin}'s {@code @Redirect} answers for an NPC. */
    private static Set<String> redirectedToTheHost() throws IOException {
        try (var input = NpcDmzStatsTest.class.getClassLoader().getResourceAsStream(
                "net/bullettrain/xenopixelsmod/mixin/common/StatsDataNpcHostMixin.class")) {
            assertNotNull(input, "the host mixin is not on the test classpath");
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            for (MethodNode method : node.methods)
                if (method.visibleAnnotations != null)
                    for (var annotation : method.visibleAnnotations)
                        if (annotation.desc.endsWith("/Redirect;") && annotation.values != null)
                            for (int i = 0; i < annotation.values.size(); i += 2)
                                if ("method".equals(annotation.values.get(i)))
                                    return new TreeSet<>(asStrings(annotation.values.get(i + 1)));
        }
        throw new IllegalStateException("StatsDataNpcHostMixin has no @Redirect for the armor read");
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStrings(Object value) {
        return value instanceof List<?> list ? (List<String>) list : List.of((String) value);
    }

    /**
     * Every method DMZ uses the player for without checking it is refused for an NPC, except the
     * ones the host redirect answers - and nothing else is refused.
     *
     * <p>An equality rather than a subset on purpose. Too few entries means an NPC can reach a
     * method that will throw; too many means we are refusing a method that would have worked, and
     * the NPC quietly loses a stat it could have had.
     */
    @Test
    void theRefusedMethodsAreExactlyTheOnesDragonMineZNeedsAPlayerFor() throws IOException {
        PlayerBound bound = needsAPlayer();
        Set<String> covered = redirectedToTheHost();
        for (String method : covered)
            assertEquals(Set.of("armor"), bound.uses().get(method),
                    method + " is redirected for its armor read, but DMZ uses the player there too");
        Set<String> expected = new TreeSet<>(bound.unguarded());
        expected.removeAll(covered);
        Set<String> refused = new TreeSet<>();
        for (String method : bound.all())
            if (!NpcDmzStats.safeForNpc(method)) refused.add(method);
        assertEquals(expected, refused, "NpcDmzStats.safeForNpc disagrees with the pinned DragonMineZ 2.2 jar."
                + " Needs a player: " + bound.uses() + "; redirected onto the NPC host: " + covered);
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

    /**
     * The formulas an NPC's combat numbers come from still exist, and an NPC can still be asked.
     */
    @Test
    void npcCombatReadsDmzsOwnPlayerStatCalculations() throws IOException {
        PlayerBound bound = needsAPlayer();
        for (String formula : List.of("getHealthBonus", "getMeleeDamage", "getStrikeDamage")) {
            assertTrue(bound.all().contains(formula),
                    "DragonMineZ 2.2 no longer has " + formula + ", which NPC combat reads through");
            assertFalse(bound.uses().containsKey(formula),
                    formula + " now needs a player, so an NPC blob cannot answer it");
        }

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
        Map<String, Set<String>> uses = needsAPlayer().uses();
        assertFalse(uses.containsKey("save"),
                "StatsData.save() now needs a player; the NPC attachment cannot serialise: " + uses.get("save"));
        assertFalse(uses.containsKey("load"),
                "StatsData.load() now needs a player; the NPC attachment cannot deserialise: " + uses.get("load"));
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

package net.bullettrain.xenopixelsmod.combat.technique;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The view during a Xeno rush strike: where the attacker lands, and that the three DragonMineZ
 * methods the view fix hooks are still what it was written against.
 */
class RushStrikeViewTest {

    private static final String HANDLER = "com/dragonminez/server/events/players/combat/StrikeAttackHandler";
    private static final String PLAYER = "Lnet/minecraft/server/level/ServerPlayer;";
    private static final String LIVING = "Lnet/minecraft/world/entity/LivingEntity;";
    private static final String PART = "Lnet/neoforged/neoforge/entity/PartEntity;";

    // ---- arrival ----

    @Test
    void theAttackerLandsOnTheirOwnSideOfTheTarget() {
        // Attacker five blocks south of a target at the origin.
        double[] spot = RushStrikeArrival.nearSide(0, 5, 0, 0, 180f, RushStrikeArrival.DISTANCE);
        assertEquals(0.0, spot[0], 1.0e-9);
        assertEquals(RushStrikeArrival.DISTANCE, spot[1], 1.0e-9);
    }

    /**
     * The point of the change: the line from where the attacker lands to the target is the line
     * they were already looking along, so the view has nothing to swing round to.
     */
    @Test
    void theBearingToTheTargetDoesNotChange() {
        double[][] attackers = {{4, 3}, {-2, 6}, {0.5, -7}, {-9, -1}};
        for (double[] a : attackers) {
            double[] spot = RushStrikeArrival.nearSide(a[0], a[1], 1, 1, 0f, RushStrikeArrival.DISTANCE);
            double before = Math.atan2(1 - a[1], 1 - a[0]);
            double after = Math.atan2(1 - spot[1], 1 - spot[0]);
            assertEquals(before, after, 1.0e-9, "bearing changed for attacker at " + a[0] + "," + a[1]);
            assertEquals(RushStrikeArrival.DISTANCE, Math.hypot(spot[0] - 1, spot[1] - 1), 1.0e-9);
        }
    }

    @Test
    void anAttackerStandingInTheTargetBacksOffToFaceIt() {
        // Yaw 0 looks along +Z, so backing off puts the attacker at -Z, looking at the target.
        double[] spot = RushStrikeArrival.nearSide(2, 2, 2, 2, 0f, RushStrikeArrival.DISTANCE);
        assertEquals(2.0, spot[0], 1.0e-9);
        assertEquals(2.0 - RushStrikeArrival.DISTANCE, spot[1], 1.0e-9);
    }

    @Test
    void anAttackerAlreadyAtArmsLengthIsNotMoved() {
        assertTrue(RushStrikeArrival.alreadyInPlace(1.3, 0.0));
        assertTrue(RushStrikeArrival.alreadyInPlace(1.0, -0.2));
        assertFalse(RushStrikeArrival.alreadyInPlace(0.4, 0.0), "inside the target");
        assertFalse(RushStrikeArrival.alreadyInPlace(3.0, 0.0), "out of reach");
        assertFalse(RushStrikeArrival.alreadyInPlace(1.3, 1.0), "a block above it");
    }

    @Test
    void closerSpotsAreTriedWhenTheUsualOneIsBlocked() {
        double[] d = RushStrikeArrival.DISTANCES;
        assertEquals(RushStrikeArrival.DISTANCE, d[0], 1.0e-9, "the usual distance is tried first");
        for (int i = 1; i < d.length; i++) {
            assertTrue(d[i] < d[i - 1] && d[i] > 0.0);
        }
    }

    // ---- only at a locked target ----

    /**
     * The view handling applies only to a strike thrown at the caster's lock, and the server
     * learns of the lock from the number DragonMineZ's own client sends with the strike request.
     * This pins that the number is what it is taken to be.
     */
    @Test
    void dragonMineZSendsTheLockedTargetWithEveryStrikeRequest() throws Exception {
        String client = Files.readString(RepoRoot.of("tools/generated/dmz_decompiled_full",
                "com/dragonminez/client/events/ClientStatsEvents.java"), StandardCharsets.UTF_8);
        assertTrue(client.contains("LivingEntity lockedTarget = LockOnEvent.getLockedTarget();"));
        assertTrue(client.contains("int targetId = lockedTarget != null ? lockedTarget.getId() : -1;"));
        assertTrue(client.contains("NetworkHandler.sendToServer(new StrikeAttackC2S(targetId));"));
        String packet = Files.readString(RepoRoot.of("tools/generated/dmz_decompiled_full",
                "com/dragonminez/common/network/C2S/StrikeAttackC2S.java"), StandardCharsets.UTF_8);
        assertTrue(packet.contains("StrikeAttackHandler.requestStrike(player, this.targetId);"));
    }

    @Test
    void theStrikeRequestIsReadWhereTheMixinExpectsIt() throws Exception {
        MethodNode request = method(handler(), "requestStrike", "(" + PLAYER + "I)V");
        assertNotNull(request, "requestStrike(ServerPlayer, int) is gone: the lock at cast can no longer be read");
        assertTrue((request.access & Opcodes.ACC_STATIC) != 0);
    }

    // ---- what the mixin hooks ----

    private static ClassNode handler() throws Exception {
        try (var stream = RushStrikeViewTest.class.getClassLoader().getResourceAsStream(HANDLER + ".class")) {
            assertNotNull(stream, HANDLER);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    private static MethodNode method(ClassNode node, String name, String desc) {
        return node.methods.stream()
                .filter(m -> m.name.equals(name) && m.desc.equals(desc))
                .findFirst().orElse(null);
    }

    private static boolean calls(MethodNode method, String name) {
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode call && call.name.equals(name)) return true;
        }
        return false;
    }

    @Test
    void theHookedMethodsExistInThePinnedDragonMineZ() throws Exception {
        ClassNode node = handler();
        String[][] hooked = {
                {"teleportToTargetFront", "(" + PLAYER + LIVING + "Z)V"},
                {"teleportToPartFront", "(" + PLAYER + PART + LIVING + "Z)V"},
                {"faceStrikeTarget", "(" + PLAYER + LIVING + ")V"},
        };
        for (String[] h : hooked) {
            MethodNode m = method(node, h[0], h[1]);
            assertNotNull(m, h[0] + h[1] + " is gone: StrikeAttackRushViewMixin no longer applies");
            assertTrue((m.access & Opcodes.ACC_STATIC) != 0, h[0] + " must be static for a static handler");
        }
    }

    /**
     * Why the hooks exist at all: these are the places DragonMineZ snaps the attacker's view from
     * the server. If a later DragonMineZ stops doing so, the fix is no longer needed and this
     * says where to look.
     */
    @Test
    void dragonMineZStillAimsTheAttackerFromTheServerInTheHookedMethods() throws Exception {
        ClassNode node = handler();
        MethodNode arrive = method(node, "teleportToTargetFront", "(" + PLAYER + LIVING + "Z)V");
        assertTrue(calls(arrive, "teleportTo"), "the opening move is no longer a teleport");
        assertTrue(calls(arrive, "lookAt"), "the opening teleport no longer turns the view");
        assertTrue(calls(method(node, "faceStrikeTarget", "(" + PLAYER + LIVING + ")V"), "lookAt"),
                "the per-tick facing no longer turns the view");
    }

    @Test
    void theMixinIsRegisteredAndNamesTheMethodsItHooks() throws Exception {
        String config = Files.readString(RepoRoot.of("src/main/resources", "xenopixelsmod.compat.mixins.json"),
                StandardCharsets.UTF_8);
        assertTrue(config.contains("\"compat.dmz.StrikeAttackRushViewMixin\""));
        String mixin = Files.readString(RepoRoot.of(
                "src/main/java/net/bullettrain/xenopixelsmod/mixin/compat/dmz", "StrikeAttackRushViewMixin.java"),
                StandardCharsets.UTF_8);
        for (String name : new String[]{"requestStrike", "teleportToTargetFront", "teleportToPartFront",
                "faceStrikeTarget"}) {
            assertTrue(mixin.contains("method = \"" + name + "\""), name);
        }
        assertTrue(mixin.contains("targets = \"" + HANDLER.replace('/', '.') + "\""));
    }
}

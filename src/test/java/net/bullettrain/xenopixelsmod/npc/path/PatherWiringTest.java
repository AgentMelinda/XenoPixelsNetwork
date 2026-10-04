package net.bullettrain.xenopixelsmod.npc.path;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.npc.XenoNpcJob;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Pather and the Guard job, end to end.
 *
 * <p>Source-level, because walking needs a level and a navigation and the tool needs a player.
 * What is checkable here is the shape of the decisions, and one of them is the likeliest bug in
 * the whole task: {@link #aPatrollingNpcIsNotDraggedHomeByTheLeash()}.
 */
class PatherWiringTest {

    private static final String PATH = "src/main/java/net/bullettrain/xenopixelsmod/npc/path";

    private static String raw(String dir, String file) throws IOException {
        return Files.readString(RepoRoot.of(dir, file), StandardCharsets.UTF_8);
    }

    private static String code(String dir, String file) throws IOException {
        return raw(dir, file).replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    // ------------------------------------------------------------ the leash

    @Test
    void aPatrollingNpcIsNotDraggedHomeByTheLeash() throws IOException {
        // The trap. tickLeash hauls an NPC back when it leaves its home radius; a route that goes
        // further would be fought every twenty ticks, which in play reads as an NPC shuddering in
        // place instead of walking its beat.
        String behaviour = code("src/main/java/net/bullettrain/xenopixelsmod/npc",
                "XenoNpcBehaviour.java");
        int guard = behaviour.indexOf("NpcPathWalker.patrolling(npc)");
        // leashRadiusFor, not data.leashRadius(): the radius now resolves through a fallback to
        // the role's own leash, because XenoNpcBrainV5.returnHome used to enforce that separately
        // and two home pulls disagreeing about when to stop was its own shudder.
        int radius = behaviour.indexOf("leashRadiusFor(npc)");
        assertTrue(guard > 0, "the leash must ask whether the NPC is patrolling");
        assertTrue(radius > 0, "the leash must still decide on a radius");
        assertTrue(guard < radius, "and it must ask before it decides to pull");
    }

    @Test
    void aFightingNpcIsLeashableAgain() throws IOException {
        // Otherwise an NPC that chased something over a hill would never be brought back, because
        // it would still count as patrolling.
        String walker = code(PATH, "NpcPathWalker.java");
        assertTrue(walker.contains("npc.getTarget() == null"),
                "patrolling() must be false while it has a target");
    }

    @Test
    void deletingARouteLetsTheLeashResume() throws IOException {
        // The marker has to be cleared, or an NPC whose route was just deleted stays permanently
        // unleashed.
        String walker = code(PATH, "NpcPathWalker.java");
        int noRoute = walker.indexOf("!profile.path.walkable()");
        assertTrue(noRoute > 0);
        assertTrue(walker.indexOf("setPathIndex(-1)", noRoute) > noRoute);
    }

    // ------------------------------------------------------------ walking

    @Test
    void theWalkerUsesTheSameNavigationCallTheLeashDoes() throws IOException {
        // One movement path, not two.
        assertTrue(code(PATH, "NpcPathWalker.java").contains("getNavigation().moveTo("));
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/npc",
                "XenoNpcBehaviour.java").contains("getNavigation().moveTo("));
    }

    @Test
    void aWedgedNpcGivesUpOnAPointRatherThanStallingForever() throws IOException {
        // isDone() alone is not enough: a navigation that never started is done immediately, and
        // one inching sideways around a wall never is.
        String walker = code(PATH, "NpcPathWalker.java");
        assertTrue(walker.contains("POINT_TIMEOUT_TICKS"));
        assertTrue(walker.contains("pathPointTicks"));
        assertTrue(walker.contains("getNavigation().isDone()"),
                "and a navigation that gave up is re-issued rather than left standing");
    }

    @Test
    void anNpcJoinsItsRouteAtTheNearestPoint() throws IOException {
        // So one placed halfway along its beat does not walk back to the start first.
        assertTrue(code(PATH, "NpcPathWalker.java").contains("nearestPoint(npc, path)"));
    }

    @Test
    void theWalkerIsStaggeredAndOffThePerTickPath() throws IOException {
        // aiStep runs for every NPC every tick, so the early return is the whole cost for the
        // overwhelming majority that have no route.
        String walker = code(PATH, "NpcPathWalker.java");
        assertTrue(walker.contains("% CHECK_TICKS != 0"));
        assertTrue(walker.contains("npc.getId()"), "staggered by entity id, like the bard");
    }

    @Test
    void patrolPositionIsNeverSaved() throws IOException {
        // Which point an NPC was heading for is only meaningful inside one server run. The route
        // itself - the part worth keeping - is on the profile.
        String entity = code("src/main/java/net/bullettrain/xenopixelsmod/npc",
                "XenoNpcEntity.java");
        assertTrue(entity.contains("private int pathIndex"));
        assertFalse(entity.contains("putInt(\"PathIndex\""));
    }

    @Test
    void theProfileIsReadOncePerTickNotOncePerJob() throws IOException {
        // readCached() answers from the tag-identity cache; either way, one fetch per tick is
        // shared by the three jobs instead of each re-deserialising for itself.
        String entity = code("src/main/java/net/bullettrain/xenopixelsmod/npc",
                "XenoNpcEntity.java");
        assertTrue(entity.contains("NpcCombatProfile jobProfile = NpcCombatProfile.readCached(this)"));
        assertTrue(entity.contains("NpcBardJob.tick(this, jobProfile)"));
        assertTrue(entity.contains("NpcGuardJob.tick(this, jobProfile)"));
        assertTrue(entity.contains("NpcPathWalker.tick(this, jobProfile)"));
    }

    // ------------------------------------------------------------ the tool

    @Test
    void npcHandlesPathToolBeforeItsOwnInteractionConsumesTheClick() throws IOException {
        String entity = code("src/main/java/net/bullettrain/xenopixelsmod/npc",
                "XenoNpcEntity.java");
        int interact = entity.indexOf("protected InteractionResult mobInteract(");
        // The per-tool if-block became one dispatch - XenoNpcTool.resolve - once there were four
        // tools each repeating the same main-hand-then-off-hand check. The requirement is unchanged:
        // a tool acts before the wand, the shop, the dialogue or the speech consumes the click.
        int dispatch = entity.indexOf("XenoNpcTool", interact);
        int wand = entity.indexOf("if (holdsWand(player))", interact);
        assertTrue(interact >= 0 && dispatch > interact && dispatch < wand,
                "tools must act before wand, shop, dialogue or speech consumes the click");

        // The off-hand check moved into the dispatch with everything else, so that is where it is
        // asserted - it is the half that silently breaks, because vanilla runs mobInteract for the
        // main hand first and stops as soon as it consumes the interaction.
        String tools = code("src/main/java/net/bullettrain/xenopixelsmod/item/custom",
                "XenoNpcTool.java");
        assertTrue(tools.contains("getOffhandItem()"),
                "an off-hand tool must work when the main-hand pass consumes the click");
    }

    @Test
    void theToolDecidesNothingOnTheClient() throws IOException {
        String tool = code("src/main/java/net/bullettrain/xenopixelsmod/item/custom",
                "XenoNpcPathToolItem.java");
        assertEquals(3, tool.split("hasPermissions\\(2\\)", -1).length - 1,
                "bind, add and clear each re-check the operator permission");
        assertTrue(tool.contains("MAX_PLACE_DISTANCE"),
                "and a point cannot be placed somewhere the NPC could never walk to");
        assertTrue(tool.contains("server.getEntity(id)"),
                "the stack holds a UUID, which is a request to find an entity - not permission");
    }

    @Test
    void addingAPointRejoinsTheRouteRatherThanKeepingAStaleIndex() throws IOException {
        // The list changed under the walker, so an index may now mean a different point.
        String tool = code("src/main/java/net/bullettrain/xenopixelsmod/item/custom",
                "XenoNpcPathToolItem.java");
        assertTrue(tool.contains("npc.setPathIndex(-1)"));
    }

    @Test
    void theToolIsRegisteredWithAModelAndAName() throws IOException {
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/item", "ModsItems.java")
                .contains("xeno_npc_path_tool"));
        assertTrue(Files.exists(RepoRoot.of(
                "src/generated/resources/assets/xenopixelsmod/models/item", "xeno_npc_path_tool.json")),
                "an item with no model is a purple cube");
        assertTrue(Files.exists(RepoRoot.of(
                "src/main/resources/assets/xenopixelsmod/textures/item", "xeno_npc_path_tool.png")),
                "and one with no texture is a missing-texture cube");
        assertTrue(raw("src/main/resources/assets/xenopixelsmod/lang", "en_us.json")
                .contains("item.xenopixelsmod.xeno_npc_path_tool"));
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/item",
                "ModCreativeModTabs.java").contains("XENO_NPC_PATH_TOOL"),
                "an item in no tab can only be reached by command");
    }

    @Test
    void theIconIsARealSixteenBySixteenRgbaPng() throws IOException {
        // Hand-authored pixel by pixel: this host has no image generation and no PIL. Checked
        // rather than assumed, because a malformed PNG fails at texture-stitch time with a stack
        // trace that does not name the file.
        byte[] png = Files.readAllBytes(RepoRoot.of(
                "src/main/resources/assets/xenopixelsmod/textures/item", "xeno_npc_path_tool.png"));
        assertEquals((byte) 0x89, png[0]);
        assertEquals('P', png[1]);
        assertEquals('N', png[2]);
        assertEquals('G', png[3]);
        int width = ((png[16] & 0xFF) << 24) | ((png[17] & 0xFF) << 16)
                | ((png[18] & 0xFF) << 8) | (png[19] & 0xFF);
        int height = ((png[20] & 0xFF) << 24) | ((png[21] & 0xFF) << 16)
                | ((png[22] & 0xFF) << 8) | (png[23] & 0xFF);
        assertEquals(16, width);
        assertEquals(16, height);
        assertEquals(8, png[24], "bit depth");
        assertEquals(6, png[25], "colour type 6 = RGBA, so the background is transparent");
    }

    // ------------------------------------------------------------ the guard

    @Test
    void theGuardJobIsLiveAndSaysSo() {
        assertTrue(XenoNpcJob.GUARD.implemented());
    }

    @Test
    void theGuardNeverTargetsPlayersOrOtherNpcs() throws IOException {
        // A toggle called "Attack Monsters" turning an NPC on its owner would be a surprise.
        // Hostility toward players is the faction system's decision.
        String guard = code("src/main/java/net/bullettrain/xenopixelsmod/npc/job",
                "NpcGuardJob.java");
        int wants = guard.indexOf("static boolean wants(");
        assertTrue(wants > 0);
        String body = guard.substring(wants, Math.min(guard.length(), wants + 420));
        assertTrue(body.contains("instanceof Player"));
        assertTrue(body.contains("XenoNpcEntity"));
    }

    @Test
    void creepersAreCheckedBeforeTheGeneralMonsterTest() throws IOException {
        // A Creeper is an Enemy, so the order is what makes its own toggle mean anything.
        String guard = code("src/main/java/net/bullettrain/xenopixelsmod/npc/job",
                "NpcGuardJob.java");
        assertTrue(guard.indexOf("instanceof Creeper") < guard.indexOf("instanceof Enemy"));
    }

    @Test
    void anExistingTargetIsRecheckedSoAToggleTakesEffectMidFight() throws IOException {
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/npc/job", "NpcGuardJob.java")
                .contains("stillValid(npc.getTarget(), profile)"));
    }

    @Test
    void aGuardWithEveryToggleOffDoesNotEvenScan() throws IOException {
        String guard = code("src/main/java/net/bullettrain/xenopixelsmod/npc/job",
                "NpcGuardJob.java");
        assertTrue(guard.contains("!profile.guardAnimals && !profile.guardMonsters"),
                "walking the entity list to reject everything in it would be free work");
    }

    // ------------------------------------------------------------ the editor and the whitelist

    @Test
    void theDisabledMovementRowIsNowALiveLink() throws IOException {
        // The placeholder this task discharges: aiRows() used to say "native navigation schemas
        // are not built".
        String editor = code("src/main/java/net/bullettrain/xenopixelsmod/client/npc",
                "XenoNpcEditorScreen.java");
        assertFalse(editor.contains("disabledAction(\"Movement\")"));
        assertTrue(editor.contains("openPage(ScreenId.ADVANCED_PATH)"));
        assertFalse(editor.contains("native navigation schemas are not built"));
    }

    @Test
    void everyNewKeyIsWhitelistedAndInTheShapeSample() throws IOException {
        // The PROFILE_SHAPE trap, which has bitten three times: a whitelisted key absent from the
        // sample passes the whitelist and is then rejected at the type check.
        String policy = code("src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                "XenoNpcSavePolicy.java");
        for (String key : new String[]{"Path", "GuardAnimals", "GuardMonsters", "GuardCreepers"}) {
            assertTrue(policy.contains('"' + key + '"'), key + " must be whitelisted to save");
        }
        assertTrue(policy.contains("sample.path.add("),
                "Path is written only when the route has points, so the sample needs one");
    }
}

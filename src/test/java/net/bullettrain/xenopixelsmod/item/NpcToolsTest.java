package net.bullettrain.xenopixelsmod.item;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The NPC tool items, and the wiring that decides whether they are reachable at all.
 *
 * <p>Source-level, because a tool needs a level, a player and a live entity to exercise. What is
 * checkable here is the set of failures that make a tool look broken rather than missing — and both
 * have already happened once each in this codebase:
 *
 * <ul>
 *   <li>the <b>Cloner shipped unrouted</b>: registered, textured, compiling, and never called by
 *       {@code mobInteract}, so right-clicking an NPC opened the editor instead;
 *   <li>the <b>Moving Path tool was never in the creative tab</b>, so the only way to get one was
 *       {@code /give}.
 * </ul>
 *
 * <p>Neither shows up in a compile, and neither shows up in a unit test of the item's own logic.
 * They show up here.
 */
class NpcToolsTest {

    private static final String ITEM = "src/main/java/net/bullettrain/xenopixelsmod/item";
    private static final String CUSTOM = ITEM + "/custom";
    private static final String ASSETS = "src/main/resources/assets/xenopixelsmod";

    /** Every tool that acts on an NPC, by item class and registry name. */
    private static final List<String[]> TOOLS = List.of(
            new String[] {"XenoNpcWandItem", "xeno_npc_wand", "XENO_NPC_WAND"},
            new String[] {"XenoNpcPathToolItem", "xeno_npc_path_tool", "XENO_NPC_PATH_TOOL"},
            new String[] {"XenoNpcClonerItem", "xeno_npc_cloner", "XENO_NPC_CLONER"},
            new String[] {"XenoNpcJarItem", "xeno_npc_jar", "XENO_NPC_JAR"},
            new String[] {"XenoNpcMounterItem", "xeno_npc_mounter", "XENO_NPC_MOUNTER"},
            new String[] {"XenoNpcTeleporterItem", "xeno_npc_teleporter", "XENO_NPC_TELEPORTER"},
            new String[] {"XenoNpcScriptToolItem", "xeno_npc_script_tool", "XENO_NPC_SCRIPT_TOOL"});

    /** Tools that act when used ON an NPC, so they must be routed through the dispatch. */
    private static final List<String> NPC_TOOLS = List.of(
            "XenoNpcPathToolItem", "XenoNpcClonerItem", "XenoNpcJarItem",
            "XenoNpcMounterItem", "XenoNpcTeleporterItem");

    private static String read(String dir, String file) throws IOException {
        return Files.readString(RepoRoot.of(dir, file), StandardCharsets.UTF_8);
    }

    private static String code(String dir, String file) throws IOException {
        return read(dir, file).replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    // ------------------------------------------------------------ reachability

    @Test
    void everyToolIsRegistered() throws IOException {
        String items = code(ITEM, "ModsItems.java");
        for (String[] tool : TOOLS) {
            assertTrue(items.contains('"' + tool[1] + '"'), tool[1] + " is not registered");
        }
    }

    @Test
    void everyToolIsInTheCreativeTab() throws IOException {
        // The Moving Path tool was registered for a long time and never listed here, so the only
        // way to obtain one was /give. A tool nobody can find is a tool nobody has.
        String tabs = code(ITEM, "ModCreativeModTabs.java");
        for (String[] tool : TOOLS) {
            assertTrue(tabs.contains(tool[2] + ".get()"),
                    tool[1] + " is registered but not in any creative tab");
        }
    }

    @Test
    void everyToolActingOnAnNpcIsRoutedThroughTheDispatch() throws IOException {
        // The failure this exists for. mobInteract used to test for each tool by hand, and a tool
        // missing from that chain was never called - which is indistinguishable from the item being
        // broken. Implementing XenoNpcTool is now the only thing needed, so this checks each does.
        for (String tool : NPC_TOOLS) {
            assertTrue(code(CUSTOM, tool + ".java").contains("implements")
                            && code(CUSTOM, tool + ".java").contains("XenoNpcTool"),
                    tool + " must implement XenoNpcTool or the NPC will never call it");
            assertTrue(code(CUSTOM, tool + ".java").contains("useOnNpc"),
                    tool + " must implement useOnNpc");
        }
    }

    @Test
    void theEntityAsksTheDispatchRatherThanNamingToolsItself() throws IOException {
        String entity = code("src/main/java/net/bullettrain/xenopixelsmod/npc",
                "XenoNpcEntity.java");
        assertTrue(entity.contains("XenoNpcTool"), "mobInteract must consult the dispatch");
        // A per-tool if-block creeping back in is the regression; the wand is the one exception,
        // because it opens the editor rather than acting as a tool on the NPC.
        for (String tool : NPC_TOOLS) {
            assertFalse(entity.contains(tool),
                    entity.contains(tool) ? tool + " is named directly in XenoNpcEntity; it should "
                            + "go through XenoNpcTool.resolve" : "");
        }
    }

    // ------------------------------------------------------------ assets

    @Test
    void everyToolHasAModelATextureAndAName() throws IOException {
        String lang = read(ASSETS + "/lang", "en_us.json");
        for (String[] tool : TOOLS) {
            Path model = RepoRoot.of("src/generated/resources/assets/xenopixelsmod/models/item",
                    tool[1] + ".json");
            assertTrue(Files.exists(model), tool[1] + " has no 3D item model");
            assertTrue(Files.readString(model).contains("\"elements\""),
                    tool[1] + " must keep its cuboid geometry");
            assertTrue(Files.exists(RepoRoot.of(ASSETS + "/textures/item", tool[1] + ".png")),
                    tool[1] + " has no texture");
            assertTrue(lang.contains("item.xenopixelsmod." + tool[1]),
                    tool[1] + " has no translation, so it renders as its raw key");
        }
        assertTrue(Files.exists(RepoRoot.of("src/generated/resources/assets/xenopixelsmod/textures/item",
                "xeno_npc_tool_materials.png")));
    }

    /**
     * The Wand's texture is 1284x1225 and 440KB.
     *
     * <p>Found by this test rather than by looking. It predates this work and is not being changed
     * unasked - it is somebody's artwork, and replacing it is a decision rather than a fix - but it
     * is recorded here so it is visible rather than forgotten. The game scales it into a 16x16 slot,
     * so it renders soft rather than crisp, it is not a power of two, and it is roughly three
     * thousand times the size of every other tool icon in the jar.
     */
    private static final String OVERSIZED_LEGACY_TEXTURE = "xeno_npc_wand";

    /** One PNG's dimensions, straight out of its IHDR. */
    private static int[] size(String name) throws IOException {
        byte[] head = Files.readAllBytes(RepoRoot.of(ASSETS + "/textures/item", name + ".png"));
        assertTrue(head.length > 24, name + " texture is truncated");
        int width = ((head[16] & 0xFF) << 24) | ((head[17] & 0xFF) << 16)
                | ((head[18] & 0xFF) << 8) | (head[19] & 0xFF);
        int height = ((head[20] & 0xFF) << 24) | ((head[21] & 0xFF) << 16)
                | ((head[22] & 0xFF) << 8) | (head[23] & 0xFF);
        return new int[] {width, height};
    }

    @Test
    void everyToolTextureIsSixteenSquare() throws IOException {
        // Generated at the size rather than scaled: an item texture that is not 16x16 is resampled
        // by the game and looks it.
        for (String[] tool : TOOLS) {
            if (tool[1].equals(OVERSIZED_LEGACY_TEXTURE)) {
                continue;
            }
            int[] wh = size(tool[1]);
            assertEquals(16, wh[0], tool[1] + " width");
            assertEquals(16, wh[1], tool[1] + " height");
        }
    }

    @Test
    void theOversizedLegacyTextureIsStillOversized() throws IOException {
        // Deliberately asserts the known-bad state rather than skipping it, so that fixing the art
        // fails this test and forces the exception above to be deleted. A skipped check is a check
        // nobody ever revisits.
        int[] wh = size(OVERSIZED_LEGACY_TEXTURE);
        assertTrue(wh[0] > 16 || wh[1] > 16,
                OVERSIZED_LEGACY_TEXTURE + " is 16x16 now - delete OVERSIZED_LEGACY_TEXTURE and "
                        + "its exception in everyToolTextureIsSixteenSquare");
    }

    // ------------------------------------------------------------ what each one promises

    @Test
    void theClonerLeavesTheOriginalAndTheJarDoesNot() throws IOException {
        // The one real difference between two items that share all their machinery. Getting it
        // backwards would either duplicate NPCs or delete them.
        String cloner = code(CUSTOM, "XenoNpcClonerItem.java");
        String jar = code(CUSTOM, "XenoNpcJarItem.java");
        assertFalse(cloner.contains(".discard()"), "the Cloner must leave the original standing");
        assertTrue(jar.contains("npc.discard()"), "the Jar must take the NPC out of the world");
    }

    @Test
    void theJarCapturesRatherThanKills() throws IOException {
        // discard(), not kill(): kill runs die(), which schedules a respawn for an NPC that has one
        // - and a jarred NPC that respawned behind you is a duplicate rather than a move.
        String jar = code(CUSTOM, "XenoNpcJarItem.java");
        assertFalse(jar.contains(".kill()"), "kill would trigger the respawn handler");
        assertFalse(jar.contains(".hurt("), "nor may it damage the NPC");
    }

    @Test
    void theJarRefusesToOverwriteAnOccupiedJar() throws IOException {
        // The jarred NPC exists nowhere else, so overwriting deletes it outright - far too much for
        // a right-click.
        assertTrue(code(CUSTOM, "XenoNpcJarItem.java").contains("XenoNpcPayload.present(stack)"),
                "the Jar must check whether it is already full");
    }

    @Test
    void theJarEmptiesOnlyAfterTheNpcIsInTheWorld() throws IOException {
        String jar = code(CUSTOM, "XenoNpcJarItem.java");
        int spawn = jar.indexOf("addFreshEntity");
        int clear = jar.indexOf("XenoNpcPayload.clear");
        assertTrue(spawn > 0 && clear > spawn,
                "a failed spawn must not empty the jar - it is the only copy");
    }

    @Test
    void bothCarriersShareOnePayloadFormat() throws IOException {
        // Two copies of "what a stored NPC looks like" would drift, and the drift shows up as a
        // clone missing something its original had.
        for (String tool : List.of("XenoNpcClonerItem", "XenoNpcJarItem")) {
            String body = code(CUSTOM, tool + ".java");
            assertTrue(body.contains("XenoNpcPayload"), tool + " must use the shared payload");
            assertFalse(body.contains("editorPayload"),
                    tool + " must not build its own payload");
        }
    }

    @Test
    void aClonedNpcIsANewOneAndAReleasedOneIsNot() throws IOException {
        // keepOwner is the flag that says which. A clone belongs to whoever placed it; a released
        // NPC is the same one that was captured and keeps its owner and provenance.
        assertTrue(code(CUSTOM, "XenoNpcClonerItem.java").contains("x, y, z, false"),
                "a clone takes a new owner");
        assertTrue(code(CUSTOM, "XenoNpcJarItem.java").contains("x, y, z, true"),
                "a released NPC keeps its own");
    }

    @Test
    void theMounterWillNotPutAnNpcOnAnotherPlayer() throws IOException {
        // Otherwise anyone holding the item could dump an NPC on anyone else. "Mount Player" means
        // me, and that action is reachable only through the holder's own sneak-use.
        assertTrue(code(CUSTOM, "XenoNpcMounterItem.java")
                        .contains("target instanceof Player && target != player"),
                "the Mounter must refuse another player");
    }

    @Test
    void theMounterAllowsItsOperatorToRideAnNpcOnlyWhenEnabled() throws IOException {
        String mounter = code(CUSTOM, "XenoNpcMounterItem.java");
        assertTrue(mounter.contains("mountControlEnabled()"),
                "the profile toggle must gate player steering");
        assertTrue(mounter.contains("player.startRiding(npc, true)"),
                "the explicit Mounter action must put the operator on the NPC");
        assertTrue(mounter.contains("Mount Control is disabled"));
    }

    @Test
    void theTeleporterTreatsADimensionAsARequestRatherThanAnAuthority() throws IOException {
        // The id lives on a stack, which can be carried in from another world entirely.
        String tp = code(CUSTOM, "XenoNpcTeleporterItem.java");
        assertTrue(tp.contains("getServer().getLevel("),
                "the destination must be re-resolved against the running server");
    }

    @Test
    void theTeleporterFollowsTheEntityAcrossADimension() throws IOException {
        // changeDimension returns a DIFFERENT entity - the original is removed and a copy made in
        // the destination. Using the old reference afterwards silently edits a dead entity.
        String tp = code(CUSTOM, "XenoNpcTeleporterItem.java");
        assertTrue(tp.contains("Entity moved = move("), "the returned entity must be kept");
        assertTrue(tp.contains("moved instanceof XenoNpcEntity"),
                "and used for anything done after the move");
    }

    @Test
    void everyToolChecksOperatorPermission() throws IOException {
        // These edit, duplicate, delete and relocate authored content. Every one is behind the same
        // bar the editor is.
        for (String tool : NPC_TOOLS) {
            assertTrue(code(CUSTOM, tool + ".java").contains("hasPermissions(2)"),
                    tool + " must check operator permission");
        }
    }

    @Test
    void everyToolIsUnstackable() throws IOException {
        // Each carries per-stack state - a payload, a bound entity, a chosen dimension. A stack of
        // sixty-four sharing one component is a bug waiting to be reported as item loss.
        String items = code(ITEM, "ModsItems.java");
        for (String[] tool : TOOLS) {
            int at = items.indexOf('"' + tool[1] + '"');
            assertTrue(at > 0);
            int next = items.indexOf("ITEMS.register(", at);
            String block = next > 0 ? items.substring(at, next) : items.substring(at);
            assertTrue(block.contains("stacksTo(1)"), tool[1] + " must not stack");
        }
    }
}

package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Why a native Xeno NPC's battle power did not move when its stats changed.
 *
 * <p>Four separate breaks in one chain, each of which alone was enough to flatten it:
 *
 * <ol>
 *   <li>{@code NpcCounterpartSync.apply} returned on its first line for anything that was not a
 *       CustomNPCs entity, so none of the sync ran for a native NPC at all.</li>
 *   <li>{@code NpcDmzStats.eligible} likewise accepted only CustomNPCs, so no DragonMineZ stats
 *       blob was ever attached.</li>
 *   <li>Nothing called {@code xenopixels$setNpcHost}, so {@code Stats.attributesReady()} was false
 *       and {@code Stats.applyToAttributes()} returned immediately - the routing mixins were
 *       present but inert.</li>
 *   <li>{@code XenoNpcEntity.createAttributes} declared none of DragonMineZ's main attributes, so
 *       even a write that got that far landed on a null {@code AttributeInstance}.</li>
 * </ol>
 *
 * <p>These are source-shape assertions rather than behavioural ones: every link needs a live server
 * and a registered entity type, which a unit test has neither of. They exist so that removing any
 * one link again fails here rather than in game, where the only symptom is a number that does not
 * change.
 */
class NpcBattlePowerWiringTest {

    private static String source(String relative) throws IOException {
        // RepoRoot, not a bare relative path: the test JVM's working directory is not the
        // repository root, which is why every assertion here failed on the first run.
        Path path = RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod").resolve(relative);
        assertTrue(Files.exists(path), path + " should exist");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void theSyncNoLongerTurnsNativeNpcsAwayAtTheDoor() throws IOException {
        String sync = source("compat/npc/NpcCounterpartSync.java");
        assertTrue(sync.contains("!isManagedNpc(entity)"),
                "apply() must gate on isManagedNpc, not isCustomNpc - a native NPC has to get in");
        assertTrue(sync.contains("instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity"),
                "isManagedNpc must actually admit the native NPC");
    }

    @Test
    void aNativeNpcCanCarryADragonMineZStatsBlob() throws IOException {
        String stats = source("compat/npc/NpcDmzStats.java");
        assertTrue(stats.contains("instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity"),
                "eligible() must admit the native NPC, or syncFromProfile returns null at once");
    }

    @Test
    void theAttributeRoutingIsActuallyArmed() throws IOException {
        String stats = source("compat/npc/NpcDmzStats.java");
        assertTrue(stats.contains("xenopixels$setNpcHost"),
                "the blob must bind its host, or attributesReady() stays false");
        assertTrue(stats.contains("xenopixels$markLoaded"),
                "the blob must be marked loaded, or DMZ answers defaults");
        assertTrue(stats.contains("applyToAttributes()"),
                "syncFromProfile must push the stats onto the entity's attributes");
    }

    @Test
    void theNpcDeclaresTheAttributesDragonMineZWritesTo() throws IOException {
        String entity = source("npc/XenoNpcEntity.java");
        // Exactly the six Stats.applyToAttributes() writes, read off the decompiled method. An
        // attribute the entity does not declare answers null, and the write is dropped in silence.
        for (String attribute : new String[]{"STRENGTH", "STRIKE_POWER", "RESISTANCE", "VITALITY",
                "KI_POWER", "ENERGY"}) {
            assertTrue(entity.contains("MainAttributes." + attribute),
                    "createAttributes must declare MainAttributes." + attribute);
        }
    }

    @Test
    void theNpcReportsItsOwnBattlePowerRatherThanTheMobApproximation() throws IOException {
        String entity = source("npc/XenoNpcEntity.java");
        assertTrue(entity.contains("IBattlePower"),
                "the NPC must implement IBattlePower, which is what the scouter and ki sense read");
        assertTrue(entity.contains("getBattlePower()"),
                "and answer it from its own DragonMineZ stats");
        // DMZ pushes its cached approximation through the setter every tick. Storing it would let
        // that value win, and the scouter would flip between the two readings.
        assertTrue(entity.contains("public void setBattlePower(int"),
                "setBattlePower must be overridden so the cached value cannot overwrite the real one");
    }

    @Test
    void theBattlePowerAnswerIsDragonMineZsOwnFormula() throws IOException {
        // Not a second formula of ours. StatsData.getBattlePower() is the one DMZ uses for a
        // character, and an NPC with real DMZ stats should read the same way a character does.
        String entity = source("npc/XenoNpcEntity.java");
        assertTrue(entity.contains("NpcDmzStats.stats(this)"),
                "battle power should come from the NPC's DMZ blob");
        assertTrue(entity.contains("data.getBattlePower()"),
                "and use DMZ's own getBattlePower rather than arithmetic of our own");
    }

    @Test
    void battlePowerIsSyncedBecauseTheStatsBlobIsNot() throws IOException {
        // The regression this guards: the scouter and ki sense are client-side, but the DMZ stats
        // blob lives in a server-only attachment. Reading it from the client answers null, and an
        // override that turned null into 0 made every NPC read a flat zero.
        String stats = source("compat/npc/NpcDmzStats.java");
        assertTrue(stats.contains("AttachmentType.serializable("),
                "the blob is serializable-only; if it ever gains .sync() this test should be revisited");
        assertTrue(!stats.contains(".sync("),
                "the blob is still not synced, so the entity has to publish the number itself");

        String entity = source("npc/XenoNpcEntity.java");
        assertTrue(entity.contains("DATA_BATTLE_POWER"),
                "battle power must ride synced entity data so clients can see it");
        assertTrue(entity.contains("builder.define(DATA_BATTLE_POWER"),
                "and be registered in defineSynchedData, or reading it throws");
    }

    @Test
    void aMissingBlobLeavesTheLastNumberRatherThanZeroingIt() throws IOException {
        // "No stats blob yet" means not configured, not powerless. Forcing zero there is exactly
        // how this broke the first time.
        String entity = source("npc/XenoNpcEntity.java");
        int refresh = entity.indexOf("private void refreshBattlePower()");
        assertTrue(refresh >= 0, "the server-side recompute should exist");
        String body = entity.substring(refresh, Math.min(entity.length(), refresh + 900));
        int nullCheck = body.indexOf("data == null");
        assertTrue(nullCheck >= 0, "a missing blob has to be handled");
        String afterNull = body.substring(nullCheck, Math.min(body.length(), nullCheck + 120));
        assertTrue(afterNull.contains("return;"),
                "a missing blob must return, not publish a zero");
        assertTrue(!afterNull.contains("set(DATA_BATTLE_POWER, 0)"),
                "and must never write zero on the way out");
    }

    @Test
    void theRecomputeIsStaggeredRatherThanEveryTick() throws IOException {
        // Stats change on a save, not on a tick, and a crowd of NPCs all recomputing on the same
        // tick is the kind of thing that only shows up on a populated server.
        String entity = source("npc/XenoNpcEntity.java");
        assertTrue(entity.contains("BATTLE_POWER_INTERVAL"),
                "the recompute should be on an interval");
        assertTrue(entity.contains("(tickCount + getId()) % BATTLE_POWER_INTERVAL"),
                "and staggered by entity id so they do not all land together");
    }

    @Test
    void theNpcStartsWithTheSameTwentyHealthBaseAsAPlayer() throws IOException {
        String entity = source("npc/XenoNpcEntity.java");
        assertTrue(entity.contains(".add(Attributes.MAX_HEALTH, 20.0)"),
                "vitality must build from the player's vanilla 20 HP base");
    }
}

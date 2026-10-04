package net.bullettrain.xenopixelsmod.npc;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What survives a restart, and what used to not.
 *
 * <p>Three of the four things checked here were live bugs with the same root cause in two of them:
 * a value that only means something within one run of the server, written to disk as though it
 * meant something across runs.
 */
class XenoNpcPersistenceTest {

    private static String source(String relative) throws IOException {
        Path path = RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod").resolve(relative);
        assertTrue(Files.exists(path), path + " should exist");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /**
     * Source with comments stripped.
     *
     * <p>A check for "this call is gone" that reads the comments too fires on the paragraph
     * explaining why it is gone - which is exactly backwards, and has caught this suite out before.
     */
    private static String code(String relative) throws IOException {
        String text = source(relative);
        text = text.replaceAll("(?s)/\\*.*?\\*/", " ");
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            int at = line.indexOf("//");
            out.append(at >= 0 ? line.substring(0, at) : line).append('\n');
        }
        return out.toString();
    }

    // ------------------------------------------------------------ despawning

    @Test
    void anNpcNeverDespawns() throws IOException {
        // Every Xeno NPC is placed deliberately; none is ever naturally spawned. Without this they
        // were ordinary wildlife: Mob.checkDespawn discards a mob when neither isPersistenceRequired
        // nor requiresCustomPersistence is true and the nearest player is past the category's
        // despawn distance. ModEntities registers ours as MobCategory.CREATURE, whose despawn
        // distance is 128, and ServerLevel calls checkDespawn every tick.
        //
        // So an operator placed an NPC, rode away, and it was gone - discarded, not killed, which
        // means the respawn store never heard about it either.
        String entity = source("npc/XenoNpcEntity.java");
        assertTrue(entity.contains("public boolean requiresCustomPersistence()"),
                "the NPC must opt out of despawning");
        assertTrue(entity.contains("public boolean removeWhenFarAway(double"),
                "and answer consistently when something else asks");

        int at = entity.indexOf("public boolean requiresCustomPersistence()");
        assertTrue(entity.substring(at, at + 120).contains("return true;"));
        at = entity.indexOf("public boolean removeWhenFarAway(double");
        assertTrue(entity.substring(at, at + 140).contains("return false;"));
    }

    @Test
    void theFixIsAnOverrideRatherThanAnNbtFlag() throws IOException {
        // setPersistenceRequired() writes a PersistenceRequired flag into the entity's NBT, so it
        // would only protect NPCs placed after the fix. Every NPC already standing in an existing
        // world would still be liable to vanish. An override applies the moment a chunk loads.
        assertFalse(code("npc/XenoNpcEntity.java").contains("setPersistenceRequired()"),
                "a constructor flag would leave existing worlds broken");
    }

    // ------------------------------------------------------------ the respawn clock

    @Test
    void aRespawnDeadlineIsAGameTimeNotATickCount() throws IOException {
        // MinecraftServer.tickCount is a plain private int, incremented each tick and never written
        // to disk - it restarts at 0 every launch. A deadline saved after two hours of uptime was
        // then compared, after a restart, against a clock that had gone back to zero, so the NPC
        // did not come back until the server had run at least as long again.
        assertFalse(code("npc/XenoNpcEntity.java").contains("getTickCount()"),
                "the scheduler must not use the run-local counter");
        assertFalse(code("npc/XenoNpcRespawnHandler.java").contains("getTickCount()"),
                "and neither must the handler");
        assertTrue(code("npc/XenoNpcRespawnHandler.java").contains("overworld().getGameTime()"),
                "game time is persisted in level.dat, which is the point");
    }

    @Test
    void anEntryWrittenUnderTheOldSchemeIsDueImmediately() {
        // Its stored number was a tick count and means nothing now. Due at once is right: it was
        // already overdue, possibly by a very long way.
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putUUID("Anchor", UUID.nameUUIDFromBytes(new byte[] {1}));
        entry.putString("Dimension", "minecraft:overworld");
        entry.putDouble("X", 1.0);
        entry.putDouble("Y", 2.0);
        entry.putDouble("Z", 3.0);
        entry.putInt("RespawnTick", 144_000);
        entry.putString("Role", XenoNpcRole.HUMANOID.id());
        entry.put("NpcData", new CompoundTag());
        list.add(entry);
        tag.put("Entries", list);
        // No Version key at all: that is what a version-1 file looks like.

        XenoNpcRespawnData data = XenoNpcRespawnData.load(tag, null);
        assertEquals(1, data.entries().size());
        assertEquals(0L, data.entries().get(0).respawnTick(),
                "an unreadable old deadline should read as due, not as far in the future");
    }

    @Test
    void aDeadlineRoundTripsAsALong() {
        // Game time is a long and an int would overflow at about 3.4 years of world time.
        XenoNpcRespawnData data = new XenoNpcRespawnData();
        long due = 5_000_000_000L;
        data.schedule(new XenoNpcRespawnData.Entry(UUID.nameUUIDFromBytes(new byte[] {2}),
                ResourceLocation.parse("minecraft:overworld"), 1.0, 2.0, 3.0, 0f, 0f,
                due, XenoNpcRole.GUARD, due - 100L, new CompoundTag()));

        XenoNpcRespawnData reloaded =
                XenoNpcRespawnData.load(data.save(new CompoundTag(), null), null);
        assertEquals(1, reloaded.entries().size());
        assertEquals(due, reloaded.entries().get(0).respawnTick());
        assertEquals(XenoNpcRole.GUARD, reloaded.entries().get(0).role());
    }

    @Test
    void theStoreIsVersionedSoTheNextChangeIsNotGuesswork() {
        CompoundTag saved = new XenoNpcRespawnData().save(new CompoundTag(), null);
        assertTrue(saved.contains("Version", Tag.TAG_INT));
        assertEquals(2, saved.getInt("Version"));
    }

    // ------------------------------------------------------------ unbounded growth

    @Test
    void staleEntriesAreDroppedRatherThanKeptForever() {
        // An entry is only ever consumed when its chunk happens to be loaded, so an NPC that died
        // somewhere nobody goes back to left a record that nothing would ever remove.
        XenoNpcRespawnData data = new XenoNpcRespawnData();
        data.schedule(entry(1, 1L));
        data.schedule(entry(2, 1_000_000L));

        long now = XenoNpcRespawnData.ENTRY_LIFETIME + 500_000L;
        assertEquals(1, data.prune(now), "the older one is past its lifetime");
        assertEquals(1, data.entries().size());
    }

    @Test
    void anEntryWithNoRecordedScheduleTimeIsNotExpiredOutOfExistence() {
        // A version-1 entry has no ScheduledAt. Guessing one would delete a legitimate pending
        // respawn on the first tick after the upgrade.
        XenoNpcRespawnData data = new XenoNpcRespawnData();
        data.schedule(entry(3, 0L));
        assertEquals(0, data.prune(Long.MAX_VALUE / 2));
        assertEquals(1, data.entries().size());
    }

    @Test
    void theListIsCapped() {
        XenoNpcRespawnData data = new XenoNpcRespawnData();
        for (int i = 0; i < XenoNpcRespawnData.MAX_ENTRIES + 50; i++) {
            data.schedule(entry(i + 10, 1L));
        }
        data.prune(2L);
        assertEquals(XenoNpcRespawnData.MAX_ENTRIES, data.entries().size());
    }

    @Test
    void aFileHoldingMoreThanTheCapIsTrimmedOnRead() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (int i = 0; i < XenoNpcRespawnData.MAX_ENTRIES + 100; i++) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Anchor", UUID.nameUUIDFromBytes(new byte[] {(byte) i, (byte) (i >> 8)}));
            entry.putString("Dimension", "minecraft:overworld");
            entry.putLong("RespawnTick", 1L);
            entry.putString("Role", XenoNpcRole.HUMANOID.id());
            entry.put("NpcData", new CompoundTag());
            list.add(entry);
        }
        tag.put("Entries", list);
        tag.putInt("Version", 2);
        assertEquals(XenoNpcRespawnData.MAX_ENTRIES,
                XenoNpcRespawnData.load(tag, null).entries().size());
    }

    private static XenoNpcRespawnData.Entry entry(int seed, long scheduledAt) {
        return new XenoNpcRespawnData.Entry(
                UUID.nameUUIDFromBytes(new byte[] {(byte) seed, (byte) (seed >> 8)}),
                ResourceLocation.parse("minecraft:overworld"), 0.0, 0.0, 0.0, 0f, 0f,
                scheduledAt + 100L, XenoNpcRole.HUMANOID, scheduledAt, new CompoundTag());
    }

    // ------------------------------------------------------------ the lease clock

    @Test
    void aLeaseDeadlineIsAGameTimeToo() throws IOException {
        // Identical bug, identical fix: after a restart every stored deadline sat in the future and
        // no existing lease was charged again.
        assertTrue(code("plot/PlotLeaseTicker.java").contains("settleDue(server, server.overworld().getGameTime())"),
                "the deadline comparison must use the persisted clock");
        // The ticker's other getTickCount() is a "run this every N ticks" throttle, which is
        // exactly what a run-local counter is for. Only stored deadlines were ever the bug.
        assertFalse(code("plot/PlotLeaseTicker.java").contains("settleDue(server, server.getTickCount())"));
        // Writing the first deadline, reading it back, and showing "due in" must all use one clock.
        // The readout was the last one still on the old counter, so it displayed a huge remaining
        // time right after a restart even once the charging side was fixed.
        assertFalse(code("command/PlotCommands.java").contains("getTickCount()"),
                "every place that touches a lease deadline must agree on the clock");
    }

    // ------------------------------------------------------------ player pools

    @Test
    void aTagWithoutPoolsKeepsTheDefaultsRatherThanZeroing() {
        // Unguarded, a missing MaxKi read as 0 and setKi then clamped ki to 0 as well: the player
        // silently lost both pools. Every field below these was already guarded.
        XenoPlayerData data = new XenoPlayerData();
        float ki = data.getKi();
        float maxKi = data.getMaxKi();

        data.loadNBT(new CompoundTag());

        assertEquals(maxKi, data.getMaxKi(), "max ki should survive a tag that never mentioned it");
        assertEquals(ki, data.getKi());
        assertTrue(data.getMaxStamina() > 0f);
    }

    @Test
    void poolsStillRoundTripWhenTheyArePresent() {
        XenoPlayerData data = new XenoPlayerData();
        data.setMaxKi(250f);
        data.setKi(120f);

        CompoundTag tag = new CompoundTag();
        data.saveNBT(tag);
        XenoPlayerData reloaded = new XenoPlayerData();
        reloaded.loadNBT(tag);

        assertEquals(250f, reloaded.getMaxKi());
        assertEquals(120f, reloaded.getKi());
    }
}

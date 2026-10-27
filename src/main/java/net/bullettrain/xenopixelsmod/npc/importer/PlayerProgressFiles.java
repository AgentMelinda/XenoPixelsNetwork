package net.bullettrain.xenopixelsmod.npc.importer;

import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.npc.store.*;
import net.bullettrain.xenopixelsmod.features.progression.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import java.nio.file.*;
import java.util.*;

/** Explicit operator-only migration of one exact UUID; never resolves by display name. */
public final class PlayerProgressFiles {
    private PlayerProgressFiles() {}
    /** File-only migration for a stopped server; exercises the same backup/write path as the command. */
    static NpcImportReport migrateOffline(Path world, Path game, XenoNpcWorldStore store,
                                          UUID uuid, boolean dryRun) {
        return migrate(null, world, game, store, uuid, dryRun);
    }
    public static NpcImportReport migrate(MinecraftServer server, Path world, Path game,
                                         XenoNpcWorldStore store, UUID uuid, boolean dryRun) {
        NpcImportReport report = new NpcImportReport();
        Map<String, ParallelQuests.QuestDef> definitions = new HashMap<>();
        for (var category : List.of(XenoNpcStoreCategory.QUESTS, XenoNpcStoreCategory.DIALOGS, XenoNpcStoreCategory.FACTIONS)) {
            for (var entry : store.list(category)) {
                var tag = entry.tag();
                if (!tag.contains("SourceSlot") || tag.getString("SourceMod").isBlank()) continue;
                String mod = tag.getString("SourceMod");
                int slot = tag.getInt("SourceSlot");
                if (category == XenoNpcStoreCategory.DIALOGS) report.mapDialog(mod, slot, new DialogTreeImport.Ref(entry.group(), entry.id()));
                if (category == XenoNpcStoreCategory.FACTIONS) report.mapFaction(mod, slot, entry.id());
                if (category == XenoNpcStoreCategory.QUESTS) {
                    report.mapQuest(mod, slot, entry.id());
                    try { definitions.put(entry.id(), XenoQuests.parse(entry.id(), com.google.gson.JsonParser.parseString(tag.getString("DefinitionJson")))); }
                    catch (RuntimeException e) { report.failed("playerdata", entry.id(), "invalid destination quest: " + e.getMessage()); }
                }
            }
        }
        for (var root : ForeignNpcRoots.discover(world, game)) {
            if (root.mod().endsWith("-clones")) continue;
            Path source = root.path().resolve("playerdata").resolve(uuid + ".json");
            if (!Files.isRegularFile(source)) continue;
            try {
                if (Files.size(source) > 2 * 1024 * 1024) throw new java.io.IOException("source exceeds 2 MiB");
                CompoundTag raw = ForeignSnbt.parse(Files.readString(source));
                if (!raw.getString("UUID").equalsIgnoreCase(uuid.toString())) throw new java.io.IOException("source UUID does not match requested filename UUID");
                var online = server == null ? null : server.getPlayerList().getPlayer(uuid);
                Path destination = world.resolve("playerdata").resolve(uuid + ".dat");
                CompoundTag player = online == null && Files.isRegularFile(destination)
                        ? NbtIo.readCompressed(destination, NbtAccounter.create(16 * 1024 * 1024)) : null;
                if (online == null && player == null) throw new java.io.IOException("no vanilla player save for UUID; log in once before migration");
                CompoundTag existing = new CompoundTag();
                if (online != null) XenoCapabilities.get(online).orElseThrow().saveNBT(existing);
                else existing = player.getCompound("neoforge:attachments").getCompound("xenopixelsmod:xeno_data").copy();
                CompoundTag merged = PlayerProgressImport.merge(raw, existing, root.mod(), report, definitions::get);
                if (merged.equals(existing)) { report.note("player " + uuid + " already merged; no state changed"); continue; }
                if (dryRun) { report.imported("playerdata (dry run)", root.mod() + "/" + uuid); continue; }
                Path backup = world.resolve("XenoNpcs/import-backups/playerdata").resolve(root.mod()).resolve(uuid.toString())
                        .resolve(java.time.Instant.now().toEpochMilli() + "_" + UUID.randomUUID());
                Files.createDirectories(backup);
                Files.copy(source, backup.resolve("source.json"));
                SnbtFiles.write(backup.resolve("xeno-before.snbt"), existing);
                if (Files.isRegularFile(destination)) Files.copy(destination, backup.resolve("player-before.dat"));
                if (online != null) {
                    XenoCapabilities.get(online).orElseThrow().loadNBT(merged);
                    server.getPlayerList().saveAll();
                } else {
                    CompoundTag attachments = player.getCompound("neoforge:attachments").copy();
                    attachments.put("xenopixelsmod:xeno_data", merged);
                    player.put("neoforge:attachments", attachments);
                    Path temp = Files.createTempFile(destination.getParent(), uuid.toString(), ".tmp");
                    try {
                        NbtIo.writeCompressed(player, temp);
                        try { Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                        catch (AtomicMoveNotSupportedException e) { Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING); }
                    } finally { Files.deleteIfExists(temp); }
                }
                report.imported("playerdata", root.mod() + "/" + uuid);
                report.note("player backup: " + backup);
            } catch (Exception e) { report.failed("playerdata", source.toString(), e.toString()); }
        }
        if (report.imported() == 0 && report.failures().isEmpty()) report.note("no new player progress for UUID " + uuid);
        return report;
    }
}

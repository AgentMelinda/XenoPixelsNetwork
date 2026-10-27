package net.bullettrain.xenopixelsmod.npc.importer;

import net.bullettrain.xenopixelsmod.npc.store.SnbtFiles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;

/** Explicit shared world storeddata migration; existing native keys always win. */
public final class WorldScriptDataImport {
    private WorldScriptDataImport() {}

    /** Caller supplies the live native view and persists it only after all backup writes succeed. */
    public static void importRoots(Path world, Path game, CompoundTag existing,
                                   Consumer<CompoundTag> write, boolean dryRun, NpcImportReport report) {
        CompoundTag current = existing.copy();
        for (var root : ForeignNpcRoots.discover(world, game)) {
            if (root.mod().endsWith("-clones")) continue;
            Path source = root.path().resolve("scripts/world_data.json");
            if (!Files.isRegularFile(source)) continue;
            try {
                if (Files.size(source) > 2 * 1024 * 1024) throw new IOException("world storeddata exceeds 2 MiB");
                CompoundTag raw = ForeignSnbt.parse(Files.readString(source, StandardCharsets.UTF_8));
                CompoundTag merged = merge(raw, current, root.mod(), report);
                if (merged.equals(current)) continue;
                if (!dryRun) {
                    Path backup = world.resolve("XenoNpcs/import-backups/worlddata").resolve(root.mod())
                            .resolve(System.currentTimeMillis() + "_" + UUID.randomUUID());
                    Files.createDirectories(backup);
                    Files.copy(source, backup.resolve("source.json"));
                    SnbtFiles.write(backup.resolve("xeno-before.snbt"), current);
                    write.accept(merged.copy());
                    report.note("world script data backup: " + backup);
                }
                // Preview simulates the same root order and conflict rules without writing.
                current = merged;
                report.imported(dryRun ? "world script data (dry run)" : "world script data", root.mod());
            } catch (IOException | RuntimeException e) { report.failed("world script data", root.mod(), e.toString()); }
        }
    }

    /** Mirrors XenoBoundedData stored values (64 keys, key <=64, string <=1024, finite numbers). */
    public static CompoundTag merge(CompoundTag source, CompoundTag existing, String mod, NpcImportReport report) {
        CompoundTag result = existing.copy();
        for (String key : source.getAllKeys().stream().sorted().toList()) {
            Tag raw = source.get(key);
            if (result.contains(key)) {
                if (!result.get(key).equals(raw)) report.noteGrouped("world script conflict: kept native key " + key, mod);
                continue;
            }
            if (key.isBlank() || key.length() > 64 || result.size() >= 64
                    || !(raw instanceof NumericTag number && Double.isFinite(number.getAsDouble())
                    || raw instanceof StringTag text && text.getAsString().length() <= 1024)) {
                report.noteGrouped("world script key outside native bounds/type: " + key + "; source preserved", mod);
                continue;
            }
            if (raw instanceof NumericTag number) result.putDouble(key, number.getAsDouble());
            else result.put(key, raw.copy());
        }
        return result;
    }
}

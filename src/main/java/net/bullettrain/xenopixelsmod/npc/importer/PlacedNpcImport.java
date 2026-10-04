package net.bullettrain.xenopixelsmod.npc.importer;

import net.bullettrain.xenopixelsmod.missile.ModEntities;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.XenoNpcRole;
import net.bullettrain.xenopixelsmod.npc.dialog.NpcDialogSlots;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Converts source NPC entities currently loaded by the world's normal entity manager. */
public final class PlacedNpcImport {
    private PlacedNpcImport() {}

    public static void convertLoaded(MinecraftServer server, Path worldPath, boolean dryRun,
                                     NpcImportReport report) {
        int total = 0;
        int changed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            List<Entity> entities = new ArrayList<>();
            level.getAllEntities().forEach(entities::add);
            for (Entity source : entities) {
                ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(source.getType());
                if (!isSourceNpc(typeId)) continue;
                total++;
                String mod = typeId.getNamespace();
                if (!source.getPassengers().isEmpty() || source.isPassenger()) {
                    report.failed("placed NPC", source.getUUID().toString(),
                            "NPC is riding or carrying another entity; left unchanged");
                    continue;
                }
                if (dryRun) {
                    report.imported("placed NPC (dry run)", mod + "/" + source.getUUID());
                    continue;
                }
                if (replaceOne(source, mod, worldPath, report)) changed++;
            }
        }
        if (total == 0) {
            report.note("no loaded placed MyNPCs or CustomNPCs entities found; load their chunks and run the command again");
        } else {
            report.note("placed NPCs converted: " + changed + "; found in loaded chunks: " + total
                    + ". Unloaded chunks were not scanned by this command run.");
        }
    }

    private static boolean replaceOne(Entity source, String sourceMod, Path worldPath,
                                     NpcImportReport report) {
        CompoundTag original = new CompoundTag();
        source.saveWithoutId(original);
        ResourceLocation oldType = BuiltInRegistries.ENTITY_TYPE.getKey(source.getType());
        if (oldType == null) {
            report.failed("placed NPC", source.getUUID().toString(), "source entity has no registry id");
            return false;
        }
        original.putString("id", oldType.toString());
        CompoundTag converted = original.copy();
        if (sourceMod.equals("customnpcs")) {
            if (!net.bullettrain.xenopixelsmod.compat.npc.clone.NpcCloneConverter
                    .migrateEntityInPlace(converted)) {
                report.failed("placed NPC", source.getUUID().toString(), "CustomNPC entity tag did not match the verified source format");
                return false;
            }
        }

        XenoNpcRole role = role(converted.getInt("Role"));
        converted.putString("Role", role.id());
        converted.putInt("NpcSchema", net.bullettrain.xenopixelsmod.npc.XenoNpcData.SCHEMA_VERSION);
        converted.putString("Name", bounded(converted.getString("Name"), 64, "Xeno NPC"));
        converted.putString("SourceMod", sourceMod);
        converted.putUUID("SourceUuid", source.getUUID());
        if (converted.contains("FactionID")) {
            String faction = report.faction(sourceMod, converted.getInt("FactionID"));
            if (faction == null) {
                converted.putString("Faction", "");
                report.note("placed NPC " + source.getUUID() + " references missing faction slot "
                        + converted.getInt("FactionID") + "; faction left blank");
            } else converted.putString("Faction", faction);
            converted.remove("FactionID");
        }
        mapDialogSlots(converted, sourceMod, source.getUUID().toString(), report);
        // Scripts become world-store entries with the NPC holding only a reference, so this happens
        // before the profile is built: the profile needs the id to bind. The source text is read from
        // the untouched snapshot and removed from the live tag, the same way the faction id and dialog
        // options are, so the converted NPC carries one copy of each fact.
        net.bullettrain.xenopixelsmod.npc.script.NpcScriptContainer scripts = NpcScriptImport.importContainer(original, sourceMod,
                source.getUUID().toString(),
                net.bullettrain.xenopixelsmod.npc.store.XenoNpcStores.get(), report);
        NpcScriptImport.consume(converted);
        boolean profileMigrated = PlacedNpcProfileMigrator.migrate(converted, sourceMod, original,
                scripts);
        NpcSourceKeys.audit(original, sourceMod + "/" + source.getUUID(), report);
        if (profileMigrated) {
            report.note("placed NPC " + source.getUUID()
                    + " received a native Xeno profile; unsupported source data is retained under "
                    + "NeoForgeData/XenoPixelsSource");
        }
        int oldRole = original.getInt("Role");
        if (oldRole == 2 || oldRole == 5 || oldRole == 7) {
            report.note("placed NPC " + source.getUUID() + " uses source role ordinal " + oldRole
                    + " with no exact Xeno role equivalent; imported as Humanoid");
        } else if (oldRole < 0 || oldRole > 7) {
            report.note("placed NPC " + source.getUUID() + " has unknown source role ordinal "
                    + oldRole + "; imported as Humanoid");
        }
        String targetId = BuiltInRegistries.ENTITY_TYPE.getKey(ModEntities.xenoNpcType(role)).toString();
        converted.putString("id", targetId);

        Entity replacement = ModEntities.xenoNpcType(role).create(source.level());
        if (replacement == null || !(replacement instanceof XenoNpcEntity)) {
            report.failed("placed NPC", source.getUUID().toString(), "could not create native entity type " + targetId);
            return false;
        }
        try {
            replacement.load(converted);
        } catch (RuntimeException e) {
            report.failed("placed NPC", source.getUUID().toString(), "native entity rejected source data: " + e.getMessage());
            return false;
        }
        Path backup = worldPath.resolve("XenoNpcs/import-backups/placed")
                .resolve(sourceMod + "_" + source.getUUID() + ".nbt.gz");
        try {
            Files.createDirectories(backup.getParent());
            if (Files.exists(backup)) {
                report.failed("placed NPC", source.getUUID().toString(), "backup already exists; source left unchanged");
                return false;
            }
            NbtIo.writeCompressed(original, backup);
        } catch (IOException e) {
            report.failed("placed NPC", source.getUUID().toString(), "could not back up source entity: " + e.getMessage());
            return false;
        }

        // Give the replacement a fresh UUID so it can be added before removing the source.
        UUID migratedFrom = source.getUUID();
        replacement.setUUID(UUID.randomUUID());
        if (!source.level().addFreshEntity(replacement)) {
            try { Files.deleteIfExists(backup); } catch (IOException ignored) { }
            report.failed("placed NPC", migratedFrom.toString(), "world refused the native replacement; source left intact");
            return false;
        }
        source.remove(Entity.RemovalReason.DISCARDED);
        if (replacement instanceof XenoNpcEntity nativeNpc) {
            net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile profile =
                    net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.read(nativeNpc);
            net.bullettrain.xenopixelsmod.compat.npc.NpcCounterpartSync.apply(nativeNpc, profile, true);
            net.bullettrain.xenopixelsmod.compat.npc.NpcAppearanceFx.sync(nativeNpc);
        }
        report.imported("placed NPC", sourceMod + "/" + migratedFrom);
        return true;
    }

    private static void mapDialogSlots(CompoundTag tag, String sourceMod, String npcId,
                                       NpcImportReport report) {
        net.minecraft.nbt.ListTag sourceSlots = tag.getList("NPCDialogOptions",
                net.minecraft.nbt.Tag.TAG_COMPOUND);
        NpcDialogSlots slots = new NpcDialogSlots();
        int unresolved = 0;
        for (int i = 0; i < sourceSlots.size(); i++) {
            CompoundTag source = sourceSlots.getCompound(i);
            int npcSlot = source.getInt("DialogSlot");
            int dialogId = source.getCompound("NPCDialog").getInt("Dialog");
            DialogTreeImport.Ref target = report.dialog(sourceMod, dialogId);
            if (target == null) {
                unresolved++;
                continue;
            }
            slots.set(npcSlot, target.group(), target.id());
        }
        tag.remove("NPCDialogOptions");
        slots.saveTo(tag);
        if (unresolved > 0) report.note("placed NPC " + npcId + " has " + unresolved
                + " dialog link(s) whose source slot was not imported");
    }

    private static boolean isSourceNpc(ResourceLocation id) {
        return id != null && (id.getNamespace().equals("customnpcs")
                || id.getNamespace().equals("mynpcs")) && id.getPath().equals("customnpc");
    }

    private static XenoNpcRole role(int sourceRole) {
        return switch (sourceRole) {
            case 1 -> XenoNpcRole.TRADER;
            case 3 -> XenoNpcRole.BANK;
            case 4 -> XenoNpcRole.TRANSPORTER;
            case 6 -> XenoNpcRole.COMPANION;
            default -> XenoNpcRole.HUMANOID;
        };
    }

    private static String bounded(String value, int max, String fallback) {
        String result = value == null || value.isBlank() ? fallback : value.trim();
        return result.substring(0, Math.min(result.length(), max));
    }
}

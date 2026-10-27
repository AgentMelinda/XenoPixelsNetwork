package net.bullettrain.xenopixelsmod.npc.importer;

import net.bullettrain.xenopixelsmod.npc.store.XenoNpcScripts;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Imports source libraries and global tabs without modifying the source directory. */
public final class ScriptFileImport {
    private static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    private ScriptFileImport() {}

    public static void importRoot(ForeignNpcRoots.Root root, XenoNpcWorldStore store,
                                  boolean dryRun, NpcImportReport report) {
        Path scripts = root.path().resolve("scripts");
        if (!Files.isDirectory(scripts)) return;
        Map<String, CompoundTag> libraries = new LinkedHashMap<>();
        try (var walk = Files.walk(scripts, 8)) {
            for (Path file : walk.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".js")).sorted().toList()) {
                String relative = scripts.relativize(file).toString().replace('\\', '/');
                try {
                    if (!file.toRealPath().startsWith(scripts.toRealPath())) throw new IOException("script escapes source folder");
                    String text = read(file);
                    String language = relative.contains("/") ? relative.substring(0, relative.indexOf('/')) : "ecmascript";
                    CompoundTag payload = payload(root.mod(), relative, language, text, true);
                    String bad = XenoNpcScripts.rejectPayload(payload);
                    if (bad != null) throw new IOException(bad);
                    String canonical = relative.toLowerCase(Locale.ROOT);
                    if (libraries.putIfAbsent(canonical, payload) != null) {
                        report.failed("scripts", root.mod() + "/" + relative,
                                "case-insensitive source library collision; left unchanged");
                        continue;
                    }
                    write(store, XenoNpcStoreCategory.SCRIPTS, id(root.mod(), canonical), payload, dryRun, report);
                } catch (IOException | RuntimeException e) { report.failed("scripts", root.mod() + "/" + relative, e.getMessage()); }
            }
        } catch (IOException e) { report.failed("scripts", root.mod(), e.getMessage()); }
        importGlobal(root, scripts, "player_scripts.json", XenoNpcStoreCategory.PLAYER_SCRIPTS,
                libraries, store, dryRun, report);
        importGlobal(root, scripts, "forge_scripts.json", XenoNpcStoreCategory.FORGE_SCRIPTS,
                libraries, store, dryRun, report);
        Path data = scripts.resolve("world_data.json");
        if (Files.isRegularFile(data, LinkOption.NOFOLLOW_LINKS)) {
            try {
                CompoundTag raw = ForeignSnbt.parse(read(data));
                if (!raw.isEmpty()) report.note("scripts " + root.mod()
                        + "/world_data.json shared world state is handled by the live import command's backed-up merge");
            } catch (IOException | RuntimeException e) { report.failed("scripts", root.mod() + "/world_data.json", e.getMessage()); }
        }
    }

    private static void importGlobal(ForeignNpcRoots.Root root, Path scripts, String filename,
                                     XenoNpcStoreCategory category, Map<String, CompoundTag> libraries,
                                     XenoNpcWorldStore store, boolean dryRun, NpcImportReport report) {
        Path file = scripts.resolve(filename);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            CompoundTag source = ForeignSnbt.parse(read(file));
            String language = language(source);
            var tabs = source.getList("Scripts", Tag.TAG_COMPOUND);
            boolean enabled = !source.contains("ScriptEnabled") || source.getBoolean("ScriptEnabled");
            for (int i = 0; i < tabs.size(); i++) {
                String subject = root.mod() + "/" + filename + " tab " + (i + 1);
                if (i >= net.bullettrain.xenopixelsmod.npc.script.NpcScriptContainer.MAX_TABS) {
                    report.failed("scripts", subject, "native container tab limit exceeded; source left unchanged");
                    continue;
                }
                CompoundTag tab = tabs.getCompound(i);
                StringBuilder text = new StringBuilder();
                net.minecraft.nbt.ListTag loaded = new net.minecraft.nbt.ListTag();
                boolean missing = false;
                for (String reference : references(tab)) {
                    String path = libraryPath(language, reference);
                    CompoundTag library = path == null ? null : libraries.get(path);
                    if (library == null || loaded.size() >= net.bullettrain.xenopixelsmod.npc.script.NpcScriptContainer.MAX_LOADED) {
                        report.blocked(subject, "missing, refused or excessive script library " + reference); missing = true;
                    }
                    else {
                        text.append(library.getString("Script")).append('\n');
                        loaded.add(net.minecraft.nbt.StringTag.valueOf(id(root.mod(), path)));
                    }
                }
                text.append(tab.getString("Script"));
                if (text.toString().isBlank()) continue;
                CompoundTag payload = payload(root.mod(), filename + "#" + (i + 1), language,
                        text.toString(), enabled && !missing);
                payload.put("SourceTab", tab.copy());
                String bad = XenoNpcScripts.rejectPayload(payload);
                if (bad != null) { report.failed("scripts", subject, bad + "; source left unchanged"); continue; }
                // Global hosts compose these live references when a generation change rebuilds them.
                String inline = tab.getString("Script");
                payload.putString("Script", inline.isBlank() ? "// Source tab loads library scripts only.\n" : inline);
                payload.put("Loaded", loaded);
                String tabId = root.mod() + "_" + filename.replace(".json", "") + "_"
                        + String.format(Locale.ROOT, "%03d", i + 1);
                write(store, category, tabId, payload, dryRun, report);
            }
        } catch (IOException | RuntimeException e) { report.failed("scripts", root.mod() + "/" + filename, e.getMessage()); }
    }

    /** Source saves use compound {Line: filename}; accept the older string shape too. */
    static List<String> references(CompoundTag tab) {
        List<String> out = new ArrayList<>();
        Tag raw = tab.get("ScriptList");
        if (raw instanceof net.minecraft.nbt.ListTag list) for (Tag value : list) {
            String name = value instanceof CompoundTag entry ? entry.getString("Line") : value.getAsString();
            if (!name.isBlank()) out.add(name);
        }
        return out;
    }

    static String language(CompoundTag source) {
        String value = source.getString("ScriptLanguage").trim().toLowerCase(Locale.ROOT);
        return value.isEmpty() ? XenoNpcScripts.DEFAULT_LANGUAGE : value;
    }

    /** Reject traversal and absolute paths before deriving any store reference. */
    static String libraryPath(String language, String reference) {
        String value = reference.replace('\\', '/');
        if (value.startsWith("/") || value.contains(":") || value.indexOf('\0') >= 0) return null;
        for (String part : value.split("/", -1)) if (part.equals("..") || part.equals(".") || part.isEmpty()) return null;
        return (language + "/" + value).toLowerCase(Locale.ROOT);
    }

    static String id(String mod, String path) {
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((mod + "/" + path).getBytes(StandardCharsets.UTF_8))).substring(0, 24);
            return new SlotIndex().assign(0, mod + "_script_" + hash);
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static CompoundTag payload(String mod, String path, String language, String text, boolean enabled) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", (mod + "/" + path).substring(0, Math.min(128, mod.length() + 1 + path.length())));
        tag.putString("Language", language);
        tag.putBoolean("Enabled", enabled);
        tag.putString("Script", text);
        tag.putString("SourceMod", mod);
        tag.putString("SourcePath", path);
        return tag;
    }

    private static String read(Path path) throws IOException {
        if (Files.size(path) > MAX_FILE_BYTES) throw new IOException("file exceeds 2 MiB limit");
        try (var stream = Files.newInputStream(path)) {
            byte[] bytes = stream.readNBytes(MAX_FILE_BYTES + 1);
            if (bytes.length > MAX_FILE_BYTES) throw new IOException("file exceeds 2 MiB limit");
            return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        }
    }

    private static void write(XenoNpcWorldStore store, XenoNpcStoreCategory category, String id,
                              CompoundTag payload, boolean dryRun, NpcImportReport report) {
        if (store.get(category, "", id) != null) {
            report.failed("scripts", id, "Xeno entry already exists; left unchanged"); return;
        }
        if (dryRun) { report.imported("scripts (dry run)", id); return; }
        String refusal = store.put(category, "", id, payload);
        if (refusal != null) report.failed("scripts", id, refusal);
        else { report.imported("scripts", id); XenoNpcScripts.changed(); }
    }
}

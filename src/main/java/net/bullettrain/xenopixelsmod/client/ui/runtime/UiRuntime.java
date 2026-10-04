package net.bullettrain.xenopixelsmod.client.ui.runtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.ui.DmzMenuAssignments;
import net.bullettrain.xenopixelsmod.ui.DmzMenuPage;
import net.bullettrain.xenopixelsmod.ui.UiDocument;
import net.bullettrain.xenopixelsmod.ui.UiDocumentIO;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client store for Studio documents. Packs live under {@code config/xenopixelsmod/ui/}.
 * Overlay is off until enabled.
 */
@OnlyIn(Dist.CLIENT)
public final class UiRuntime {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIR = FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod").resolve("ui");
    private static final Path SETTINGS = DIR.resolve("settings.json");
    private static final String[] BUNDLED = {
            "demo_hud", "demo_screen", "xeno_stats", "dmz_menu_blank", "dmz_hud_blank"};
    private static final DmzMenuAssignments ASSIGNMENTS = new DmzMenuAssignments();

    private static boolean enabled;
    private static String hudId = "demo_hud";
    private static final Map<String, UiDocument> documents = new LinkedHashMap<>();
    private static String lastError = "";

    private UiRuntime() {
    }

    public static void reload() {
        lastError = "";
        documents.clear();
        try {
            Files.createDirectories(DIR);
            seedBundled();
            loadSettings();
            try (var stream = Files.list(DIR)) {
                stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                        .filter(path -> !path.getFileName().toString().equals("settings.json"))
                        .forEach(UiRuntime::loadFile);
            }
            indexAssignments();
        } catch (IOException ex) {
            lastError = "ui dir: " + ex.getMessage();
        }
    }

    public static DmzMenuAssignments assignments() {
        if (documents.isEmpty()) {
            reload();
        }
        return ASSIGNMENTS;
    }

    private static void indexAssignments() {
        for (DmzMenuPage page : DmzMenuPage.values()) {
            ASSIGNMENTS.set(page, null);
        }
        for (UiDocument document : documents.values()) {
            if (document == null || !"dmz_menu".equals(document.kind)) continue;
            DmzMenuPage page = DmzMenuPage.parse(document.page);
            if (page != null && document.id != null && !document.id.isBlank()) {
                ASSIGNMENTS.set(page, document.id);
            }
        }
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        saveSettings();
        XenoPixelsMod.LOGGER.info("UI pack HUD enabled={}", value);
    }

    public static String hudId() {
        return hudId;
    }

    public static void setHudId(String id) {
        if (id != null && !id.isBlank()) {
            hudId = id;
            saveSettings();
        }
    }

    public static String lastError() {
        return lastError;
    }

    public static UiDocument document(String id) {
        if (documents.isEmpty()) {
            reload();
        }
        return id == null ? null : documents.get(id);
    }

    public static UiDocument hudDocument() {
        return document(hudId);
    }

    public static Map<String, UiDocument> documents() {
        if (documents.isEmpty()) {
            reload();
        }
        return documents;
    }

    public static void put(UiDocument document) {
        if (document == null || document.id == null || document.id.isBlank()) {
            return;
        }
        documents.put(document.id, document);
        save(document);
        indexAssignments();
    }

    public static Path directory() {
        return DIR;
    }

    private static void loadFile(Path path) {
        try {
            UiDocumentIO.UiLoadResult result = UiDocumentIO.fromJson(Files.readString(path));
            if (!result.ok()) {
                lastError = path.getFileName() + ": " + String.join("; ", result.errors());
                return;
            }
            documents.put(result.document().id, result.document());
        } catch (IOException ex) {
            lastError = path.getFileName() + ": " + ex.getMessage();
        }
    }

    public static void save(UiDocument document) {
        if (document == null || document.id == null || document.id.isBlank()) {
            return;
        }
        try {
            Files.createDirectories(DIR);
            Files.writeString(DIR.resolve(document.id + ".json"), UiDocumentIO.toJson(document));
        } catch (IOException ex) {
            lastError = "save " + document.id + ": " + ex.getMessage();
        }
    }

    private static void seedBundled() throws IOException {
        for (String id : BUNDLED) {
            Path dest = DIR.resolve(id + ".json");
            if (Files.exists(dest)) {
                continue;
            }
            String resource = "/assets/" + XenoPixelsMod.MOD_ID + "/ui/" + id + ".json";
            try (InputStream in = UiRuntime.class.getResourceAsStream(resource)) {
                if (in == null) {
                    continue;
                }
                Files.writeString(dest, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    private static void loadSettings() {
        if (!Files.exists(SETTINGS)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(SETTINGS)) {
            Settings settings = GSON.fromJson(reader, Settings.class);
            if (settings != null) {
                enabled = settings.enabled;
                if (settings.hudId != null && !settings.hudId.isBlank()) {
                    hudId = settings.hudId;
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void saveSettings() {
        try {
            Files.createDirectories(DIR);
            try (Writer writer = Files.newBufferedWriter(SETTINGS)) {
                Settings settings = new Settings();
                settings.enabled = enabled;
                settings.hudId = hudId;
                GSON.toJson(settings, writer);
            }
        } catch (IOException ignored) {
        }
    }

    private static final class Settings {
        boolean enabled;
        String hudId = "demo_hud";
    }
}

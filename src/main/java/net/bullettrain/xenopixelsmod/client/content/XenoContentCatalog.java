package net.bullettrain.xenopixelsmod.client.content;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.features.transformation.XenoFormRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@OnlyIn(Dist.CLIENT)
public final class XenoContentCatalog {
    /**
     * ARGB accents in JSON are often written as unsigned 32-bit ints (e.g. 4280391411).
     * Gson's default {@code int} mapping rejects those as out of signed range — parse via long.
     */
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Entry.class, new EntryJsonAdapter())
            .create();
    private static final String[] CLASSPATH_PATHS = {
            "/data/xenopixelsmod/dmz/content_catalog.json",
            "/assets/xenopixelsmod/dmz/content_catalog.json",
            "data/xenopixelsmod/dmz/content_catalog.json",
            "assets/xenopixelsmod/dmz/content_catalog.json"
    };
    private static final ResourceLocation PACK_ID =
            ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "dmz/content_catalog.json");

    private static Catalog DATA;

    private XenoContentCatalog() {}

    public static synchronized Catalog get() {
        if (DATA == null || DATA.isEmpty()) {
            DATA = load();
        }
        return DATA;
    }

    /** Force reload (e.g. after resource reload). */
    public static synchronized void reload() {
        DATA = load();
    }

    private static Catalog load() {
        // 1) Minecraft resource manager (preferred in-game)
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getResourceManager() != null) {
                Optional<Resource> res = mc.getResourceManager().getResource(PACK_ID);
                if (res.isPresent()) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(res.get().open(), StandardCharsets.UTF_8))) {
                        Catalog c = GSON.fromJson(reader, Catalog.class);
                        if (c != null && !c.isEmpty()) {
                            XenoPixelsMod.LOGGER.info("Loaded XenoPixels content catalog from pack ({}+{}+{} entries)",
                                    size(c.character), size(c.transforms), size(c.skills));
                            return normalize(c);
                        }
                    }
                }
            }
        } catch (Exception e) {
            XenoPixelsMod.LOGGER.warn("Pack catalog load failed: {}", e.toString());
        }

        // 2) Classpath fallbacks
        ClassLoader cl = XenoContentCatalog.class.getClassLoader();
        for (String path : CLASSPATH_PATHS) {
            try (InputStream in = open(path, cl)) {
                if (in == null) continue;
                Catalog c = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Catalog.class);
                if (c != null && !c.isEmpty()) {
                    XenoPixelsMod.LOGGER.info("Loaded XenoPixels content catalog from {} ({}+{}+{} entries)",
                            path, size(c.character), size(c.transforms), size(c.skills));
                    return normalize(c);
                }
            } catch (Exception e) {
                XenoPixelsMod.LOGGER.warn("Catalog load failed for {}: {}", path, e.toString());
            }
        }

        XenoPixelsMod.LOGGER.error("XenoPixels content catalog missing/empty — using built-in fallback");
        return builtInFallback();
    }

    private static InputStream open(String path, ClassLoader cl) {
        InputStream in = XenoContentCatalog.class.getResourceAsStream(path.startsWith("/") ? path : "/" + path);
        if (in != null) return in;
        return cl != null ? cl.getResourceAsStream(path.startsWith("/") ? path.substring(1) : path) : null;
    }

    private static Catalog normalize(Catalog c) {
        if (c.character == null) c.character = new ArrayList<>();
        if (c.transforms == null) c.transforms = new ArrayList<>();
        if (c.skills == null) c.skills = new ArrayList<>();
        // Merge live form-registry groups so menu always lists every registered group
        mergeFormRegistry(c);
        return c;
    }

    /** Append missing form-group / form rows from {@link XenoFormRegistry}. */
    private static void mergeFormRegistry(Catalog c) {
        try {
            // Ensure registry is populated (client menu may open before FeatureManager)
            if (XenoFormRegistry.getGroups().isEmpty()) {
                XenoFormRegistry.registerAll();
            }
            java.util.HashSet<String> have = new java.util.HashSet<>();
            for (Entry e : c.transforms) {
                if (e != null && e.id != null) have.add(e.id);
            }
            for (XenoFormRegistry.MenuFormEntry fe : XenoFormRegistry.buildMenuEntries()) {
                if (fe == null || fe.id() == null || have.contains(fe.id())) continue;
                c.transforms.add(entry(
                        fe.id(),
                        fe.title(),
                        fe.subtitle() != null ? fe.subtitle() : "",
                        fe.detail() != null ? fe.detail() : "",
                        fe.accent()));
                have.add(fe.id());
            }
        } catch (Throwable t) {
            XenoPixelsMod.LOGGER.warn("Could not merge form registry into catalog: {}", t.toString());
        }
    }

    private static int size(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private static Catalog builtInFallback() {
        Catalog c = new Catalog();
        c.character = new ArrayList<>();
        c.transforms = new ArrayList<>();
        c.skills = new ArrayList<>();

        c.character.add(entry("overview", "XenoPixels Overview", "What this mod adds",
                "Custom DMZ forms, XV2 HUD, BT3/Sparking combat, KI overcharge, and configs. Server: xpn.co.il",
                0xFF1E88E5));
        c.character.add(entry("hud", "XenoPixels HUD", "XV2-style combat UI",
                "Portrait, HP/KI/STM, release %, transform border. /xenohud edit · default scale 0.50x",
                0xFF42A5F5));
        c.character.add(entry("config", "Configs", "Client + server flags",
                "xenopixelsmod-client.json / xenopixelsmod-server.json. /xenoclient and /xenoserver commands.",
                0xFF78909C));

        c.transforms.add(entry("group_legend", "Super Saiyan Legend (SSJ5–10)", "superforms skill · TP priced",
                "Custom Saiyan legend chain past SSJ4. Superforms levels 9–14. Prices 120k–300k TP.",
                0xFFE1BEE7));
        c.transforms.add(entry("group_fan", "Fan Super Saiyan (SSJ5–10)", "xenopixels_fan_ss · Goku/Vegeta",
                "Separate fan skill group. Buy from Goku or Vegeta masters.",
                0xFFFFD54F));
        c.transforms.add(entry("group_god", "XenoPixels God Forms", "Beerus / Whis only",
                "SSG, SSB, SSBE, Rose, Rose Evolution, UI Sign, MUI, Ultra Ego.",
                0xFFFF69B4));
        c.transforms.add(entry("group_legendary", "XenoPixels Legendary", "Trunks master",
                "Trunks Ikari and legendaryforms skill path.",
                0xFF90CAF9));
        c.transforms.add(entry("trunks_ikari", "Trunks Ikari", "Legendary · 95k TP",
                "Silver hair, blue aura, buffed model. Master Trunks only.",
                0xFF90CAF9));

        c.skills.add(entry("vanish", "Vanish", "Double-tap A/D · lock-on",
                "Snap behind lock-on (A left / D right). Costs KI.", 0xFF1E88E5));
        c.skills.add(entry("chase", "Chase Dash", "Double-tap W · lock-on",
                "Mid-range rush-in. Probabilistic success.", 0xFFFF7043));
        c.skills.add(entry("backstep", "Backstep", "Double-tap S · lock-on",
                "Step away from lock-on target.", 0xFF90A4AE));
        c.skills.add(entry("combo", "Combo", "Attack mash · freelook or lock",
                "Free-running counter; finisher every maxComboSteps. No auto pull.", 0xFFEF5350));
        c.skills.add(entry("kick", "Charge Kick", "Hold Middle Mouse",
                "Chain kicks + particles. W=up / S=down bias. No self lunge.", 0xFFFF69B4));
        c.skills.add(entry("fist", "Charge Fist", "Hold R",
                "Charge punch chain + particles. No self lunge.", 0xFFFFB74D));
        c.skills.add(entry("dragon", "Dragon Dash", "Hold N · lock-on",
                "Launch target then chase. Stamina + KI.", 0xFFFFD54F));
        c.skills.add(entry("overcharge", "KI Overcharge", "Release > 175%",
                "Bigger size/damage/explosion per excess % release.", 0xFFFFEE58));
        c.skills.add(entry("chargeovercharge", "Charge Overcharge", "Hold past 175% · cap 1000%",
                "Keep charging ki techniques (including New Skills) past DMZ's cap. Form scales size. Grief off by default.",
                0xFFFF8A80));
        c.skills.add(entry("config", "Client Config", "xenopixelsmod-client.json",
                "Toggles: bt3CombatAnims, bt3KickChainAnims, bt3CombatParticles, bt3ChargeGlow, techniqueHotbarHideInChat, …",
                0xFF78909C));

        return normalize(c);
    }

    private static Entry entry(String id, String title, String subtitle, String detail, int accent) {
        Entry e = new Entry();
        e.id = id;
        e.title = title;
        e.subtitle = subtitle;
        e.detail = detail;
        e.accent = accent;
        return e;
    }

    public static final class Catalog {
        @SerializedName("character")
        public List<Entry> character = new ArrayList<>();
        @SerializedName("transforms")
        public List<Entry> transforms = new ArrayList<>();
        @SerializedName("skills")
        public List<Entry> skills = new ArrayList<>();

        boolean isEmpty() {
            return size(character) == 0 && size(transforms) == 0 && size(skills) == 0;
        }

        static Catalog empty() {
            Catalog c = new Catalog();
            c.character = Collections.emptyList();
            c.transforms = Collections.emptyList();
            c.skills = Collections.emptyList();
            return c;
        }
    }

    public static final class Entry {
        public String id = "";
        public String title = "";
        public String subtitle = "";
        public String detail = "";
        /** Packed ARGB color (may be set from unsigned JSON numbers). */
        public int accent = 0xFF42A5F5;

        /** Accent as full-opaque ARGB for fill/draw calls. */
        public int accentOpaque() {
            return accent | 0xFF000000;
        }
    }

    /** Gson adapter so unsigned ARGB numbers / hex strings deserialize into {@link Entry#accent}. */
    private static final class EntryJsonAdapter extends com.google.gson.TypeAdapter<Entry> {
        @Override
        public void write(com.google.gson.stream.JsonWriter out, Entry value) throws java.io.IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.beginObject();
            out.name("id").value(value.id);
            out.name("title").value(value.title);
            out.name("subtitle").value(value.subtitle);
            out.name("detail").value(value.detail);
            // Write as unsigned long so re-export stays stable.
            out.name("accent").value(Integer.toUnsignedLong(value.accent));
            out.endObject();
        }

        @Override
        public Entry read(com.google.gson.stream.JsonReader in) throws java.io.IOException {
            Entry e = new Entry();
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                switch (name) {
                    case "id" -> e.id = nullToEmpty(in.nextString());
                    case "title" -> e.title = nullToEmpty(in.nextString());
                    case "subtitle" -> e.subtitle = nullToEmpty(in.nextString());
                    case "detail" -> e.detail = nullToEmpty(in.nextString());
                    case "accent" -> e.accent = readAccent(in);
                    default -> in.skipValue();
                }
            }
            in.endObject();
            return e;
        }

        private static String nullToEmpty(String s) {
            return s == null ? "" : s;
        }

        private static int readAccent(com.google.gson.stream.JsonReader in) throws java.io.IOException {
            return switch (in.peek()) {
                case NULL -> {
                    in.nextNull();
                    yield 0xFF42A5F5;
                }
                case STRING -> parseAccentString(in.nextString());
                case NUMBER -> (int) in.nextLong(); // accepts unsigned ARGB like 4280391411
                default -> {
                    in.skipValue();
                    yield 0xFF42A5F5;
                }
            };
        }

        private static int parseAccentString(String raw) {
            if (raw == null || raw.isBlank()) return 0xFF42A5F5;
            String s = raw.trim();
            try {
                if (s.startsWith("#")) {
                    s = s.substring(1);
                    if (s.length() == 6) s = "FF" + s;
                    return (int) Long.parseLong(s, 16);
                }
                if (s.startsWith("0x") || s.startsWith("0X")) {
                    return Long.decode(s).intValue();
                }
                // Decimal string that may exceed signed int
                return (int) Long.parseLong(s);
            } catch (NumberFormatException ex) {
                return 0xFF42A5F5;
            }
        }
    }
}

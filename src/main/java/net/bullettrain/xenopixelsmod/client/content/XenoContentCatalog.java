package net.bullettrain.xenopixelsmod.client.content;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class XenoContentCatalog {
    private static final Gson GSON = new Gson();
    private static Catalog DATA;

    private XenoContentCatalog() {}

    public static synchronized Catalog get() {
        if (DATA == null) {
            DATA = load();
        }
        return DATA;
    }

    private static Catalog load() {
        String path = "/data/xenopixelsmod/dmz/content_catalog.json";
        try (InputStream in = XenoContentCatalog.class.getResourceAsStream(path)) {
            if (in == null) {
                XenoPixelsMod.LOGGER.warn("Missing content catalog {}", path);
                return Catalog.empty();
            }
            Catalog c = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Catalog.class);
            return c != null ? c : Catalog.empty();
        } catch (Exception e) {
            XenoPixelsMod.LOGGER.error("Failed loading content catalog", e);
            return Catalog.empty();
        }
    }

    public static final class Catalog {
        @SerializedName("character")
        public List<Entry> character = new ArrayList<>();
        @SerializedName("transforms")
        public List<Entry> transforms = new ArrayList<>();
        @SerializedName("skills")
        public List<Entry> skills = new ArrayList<>();

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
        public int accent = 0xFF42A5F5;
    }
}

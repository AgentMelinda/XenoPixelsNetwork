package net.bullettrain.xenopixelsmod.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server-stored HUD / menu part layouts. JSON only — no Minecraft types — so the dedicated
 * server can keep the blob without loading client configs.
 */
public final class HudPartsBundle {
    public static final int VERSION = 1;
    public static final int MAX_JSON = 32_000;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public int version = VERSION;
    public Map<String, Surface> surfaces = new LinkedHashMap<>();

    public static final class Surface {
        public boolean customLayout = true;
        public int[] partX;
        public int[] partY;
        public float[] partScale;
        public int[] partColor;
        public boolean[] partBold;
        public String[] partFont;
        public boolean[] partHidden;
    }

    public HudPartsBundle() {}

    public static String toJson(HudPartsBundle bundle) {
        return GSON.toJson(bundle == null ? new HudPartsBundle() : bundle);
    }

    public static HudPartsBundle fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            HudPartsBundle bundle = GSON.fromJson(json, HudPartsBundle.class);
            if (bundle == null || bundle.surfaces == null || bundle.surfaces.isEmpty()) return null;
            if (bundle.version < 1) bundle.version = VERSION;
            return bundle;
        } catch (JsonParseException e) {
            return null;
        }
    }
}

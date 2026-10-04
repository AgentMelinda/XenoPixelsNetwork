package net.bullettrain.xenopixelsmod.ui;

/**
 * Versioned UI pack document. JSON only — dedicated servers can parse it without
 * loading client screens. Not {@link net.bullettrain.xenopixelsmod.hud.HudPartsBundle}.
 */
public final class UiDocument {
    public static final int VERSION = 1;

    public int version = VERSION;
    public String id = "";
    public String kind = "hud";
    /** For {@code kind=dmz_menu}: stats|skills|quests|party|options|custom. */
    public String page = "";
    public int canvasW = 1920;
    public int canvasH = 1080;
    public UiNode root = new UiNode();

    public UiDocument() {
        root.id = "root";
        root.type = "ROOT";
        root.w = 280;
        root.h = 96;
    }
}

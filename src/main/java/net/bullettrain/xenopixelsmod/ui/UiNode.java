package net.bullettrain.xenopixelsmod.ui;

import java.util.ArrayList;
import java.util.List;

/** One widget in a Studio document. Gson-friendly; no Minecraft types. */
public final class UiNode {
    public String id = "";
    public String type = "PANEL";
    public int x;
    public int y;
    public int w = 80;
    public int h = 20;
    public int minW;
    public int minH;
    public int maxW;
    public int maxH;
    public String anchor = "TOP_LEFT";
    public boolean visible = true;
    /** Hidden only inside Studio; runtime visibility remains {@link #visible}. */
    public boolean editorHidden;
    /** Prevents canvas manipulation while still allowing layer-panel selection. */
    public boolean editorLocked;
    public String text = "";
    public String bind = "";
    public String action = "";
    public String color = "";
    public String texture = "";
    /** Design-pixel scroll offset for {@code SCROLL} nodes. Ignored on other types. */
    public int scroll;
    public List<UiNode> children = new ArrayList<>();

    public UiNode copyShallow() {
        UiNode next = new UiNode();
        next.id = id;
        next.type = type;
        next.x = x;
        next.y = y;
        next.w = w;
        next.h = h;
        next.minW = minW;
        next.minH = minH;
        next.maxW = maxW;
        next.maxH = maxH;
        next.anchor = anchor;
        next.visible = visible;
        next.editorHidden = editorHidden;
        next.editorLocked = editorLocked;
        next.text = text;
        next.bind = bind;
        next.action = action;
        next.color = color;
        next.texture = texture;
        next.scroll = scroll;
        return next;
    }
}

package net.bullettrain.xenopixelsmod.ui;

import java.util.ArrayList;
import java.util.List;

/** Resolved screen-pixel box. Pure numbers — tests do not need GuiGraphics. */
public final class UiLaidOut {
    public final UiNode source;
    public final int x;
    public final int y;
    public final int w;
    public final int h;
    public final List<UiLaidOut> children = new ArrayList<>();

    public UiLaidOut(UiNode source, int x, int y, int w, int h) {
        this.source = source;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public boolean contains(int px, int py) {
        return px >= x && py >= y && px < x + w && py < y + h;
    }
}

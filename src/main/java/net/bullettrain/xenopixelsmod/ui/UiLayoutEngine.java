package net.bullettrain.xenopixelsmod.ui;

/**
 * Design pixels → screen pixels. Rebuild when the scaled window or tree changes,
 * not every frame from scratch of JSON.
 */
public final class UiLayoutEngine {
    private UiLayoutEngine() {
    }

    public static UiLaidOut layout(UiDocument document, int screenW, int screenH) {
        if (document == null || document.root == null) {
            return new UiLaidOut(new UiNode(), 0, 0, screenW, screenH);
        }
        int canvasW = Math.max(1, document.canvasW);
        int canvasH = Math.max(1, document.canvasH);
        float sx = screenW / (float) canvasW;
        float sy = screenH / (float) canvasH;
        return layoutNode(document.root, 0, 0, screenW, screenH, sx, sy);
    }

    private static UiLaidOut layoutNode(UiNode node, int parentX, int parentY, int parentW, int parentH,
                                        float sx, float sy) {
        int w = Math.round(Math.max(0, node.w) * sx);
        int h = Math.round(Math.max(0, node.h) * sy);
        if (node.minW > 0) {
            w = Math.max(w, node.minW);
        }
        if (node.minH > 0) {
            h = Math.max(h, node.minH);
        }
        if (node.maxW > 0) {
            w = Math.min(w, node.maxW);
        }
        if (node.maxH > 0) {
            h = Math.min(h, node.maxH);
        }
        UiAnchor anchor = UiAnchor.byName(node.anchor);
        if (anchor == null) {
            anchor = UiAnchor.TOP_LEFT;
        }
        int ox = Math.round(node.x * sx);
        int oy = Math.round(node.y * sy);
        int x = parentX + ox;
        int y = parentY + oy;
        switch (anchor) {
            case TOP -> x = parentX + parentW / 2 + ox - w / 2;
            case TOP_RIGHT -> x = parentX + parentW + ox - w;
            case LEFT -> y = parentY + parentH / 2 + oy - h / 2;
            case CENTER -> {
                x = parentX + parentW / 2 + ox - w / 2;
                y = parentY + parentH / 2 + oy - h / 2;
            }
            case RIGHT -> {
                x = parentX + parentW + ox - w;
                y = parentY + parentH / 2 + oy - h / 2;
            }
            case BOTTOM_LEFT -> y = parentY + parentH + oy - h;
            case BOTTOM -> {
                x = parentX + parentW / 2 + ox - w / 2;
                y = parentY + parentH + oy - h;
            }
            case BOTTOM_RIGHT -> {
                x = parentX + parentW + ox - w;
                y = parentY + parentH + oy - h;
            }
            case TOP_LEFT -> {
            }
        }
        UiLaidOut box = new UiLaidOut(node, x, y, w, h);
        if (node.children == null || node.children.isEmpty()) {
            return box;
        }
        UiNodeType type = UiNodeType.byName(node.type);
        if (type == UiNodeType.HBOX) {
            int cursor = x;
            for (UiNode child : node.children) {
                UiLaidOut childBox = layoutNode(child, cursor, y, w, h, sx, sy);
                box.children.add(childBox);
                cursor = childBox.x + childBox.w;
            }
            return box;
        }
        if (type == UiNodeType.VBOX) {
            int cursor = y;
            for (UiNode child : node.children) {
                UiLaidOut childBox = layoutNode(child, x, cursor, w, h, sx, sy);
                box.children.add(childBox);
                cursor = childBox.y + childBox.h;
            }
            return box;
        }
        int childY = y;
        if (type == UiNodeType.SCROLL) {
            childY -= Math.round(node.scroll * sy);
        }
        for (UiNode child : node.children) {
            box.children.add(layoutNode(child, x, childY, w, h, sx, sy));
        }
        return box;
    }

    public static UiLaidOut hit(UiLaidOut root, int px, int py) {
        if (root == null || !root.contains(px, py)) {
            return null;
        }
        for (int i = root.children.size() - 1; i >= 0; i--) {
            UiLaidOut child = hit(root.children.get(i), px, py);
            if (child != null) {
                return child;
            }
        }
        return root;
    }
}

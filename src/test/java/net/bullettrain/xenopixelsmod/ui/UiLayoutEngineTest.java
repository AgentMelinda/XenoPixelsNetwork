package net.bullettrain.xenopixelsmod.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiLayoutEngineTest {

    @Test
    void topLeftScalesWithTwoWindowSizes() {
        UiDocument document = new UiDocument();
        document.canvasW = 400;
        document.canvasH = 200;
        document.root.x = 10;
        document.root.y = 10;
        document.root.w = 80;
        document.root.h = 20;
        document.root.anchor = "TOP_LEFT";

        UiLaidOut a = UiLayoutEngine.layout(document, 400, 200);
        assertEquals(10, a.x);
        assertEquals(10, a.y);
        assertEquals(80, a.w);
        assertEquals(20, a.h);

        UiLaidOut b = UiLayoutEngine.layout(document, 800, 400);
        assertEquals(20, b.x);
        assertEquals(20, b.y);
        assertEquals(160, b.w);
        assertEquals(40, b.h);
    }

    @Test
    void centerAnchorUsesParentMidpoint() {
        UiDocument document = new UiDocument();
        document.canvasW = 400;
        document.canvasH = 200;
        document.root.x = 0;
        document.root.y = 0;
        document.root.w = 40;
        document.root.h = 20;
        document.root.anchor = "CENTER";
        UiLaidOut box = UiLayoutEngine.layout(document, 400, 200);
        assertEquals(180, box.x);
        assertEquals(90, box.y);
    }

    @Test
    void scrollOffsetsChildren() {
        UiDocument document = new UiDocument();
        document.canvasW = 400;
        document.canvasH = 200;
        document.root.type = "SCROLL";
        document.root.x = 0;
        document.root.y = 0;
        document.root.w = 80;
        document.root.h = 40;
        document.root.scroll = 10;
        UiNode child = new UiNode();
        child.id = "inner";
        child.x = 0;
        child.y = 0;
        child.w = 20;
        child.h = 10;
        document.root.children.add(child);
        UiLaidOut box = UiLayoutEngine.layout(document, 400, 200);
        assertEquals(1, box.children.size());
        assertEquals(-10, box.children.getFirst().y);
    }

    @Test
    void missingPercentIsNanNotZero() {
        UiNode bar = new UiNode();
        bar.bind = "player.hpPercent";
        UiBindingSource empty = new UiBindingSource() {
            @Override
            public Double number(String key) {
                return null;
            }

            @Override
            public String text(String key) {
                return null;
            }
        };
        double value = UiBindResolve.bar(bar, empty);
        assertTrue(Double.isNaN(value));
        assertTrue(UiBindResolve.label(bar, empty).contains("[unbound: player.hpPercent]"));
    }
}

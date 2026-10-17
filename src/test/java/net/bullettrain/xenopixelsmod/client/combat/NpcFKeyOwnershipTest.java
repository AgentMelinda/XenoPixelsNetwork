package net.bullettrain.xenopixelsmod.client.combat;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.client.KeyMapping;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

class NpcFKeyOwnershipTest {
    /** Queue fixture: native KeyMapping.click consults the running client's modifiers. */
    private static final class QueuedKey extends KeyMapping {
        private final int code;
        private int clicks = 2;
        QueuedKey(int code) { super("test.xeno." + code, code, "test.xeno"); this.code = code; }
        @Override public boolean matches(int key, int scan) { return code == key; }
        @Override public boolean consumeClick() { if (clicks == 0) return false; clicks--; return true; }
    }
    @Test void drainsQueuedFActionAndHeldStateWhileLeavingDashNIntact() {
        var fly = new QueuedKey(GLFW.GLFW_KEY_F);
        var dash = new QueuedKey(GLFW.GLFW_KEY_N);
        fly.setDown(true); dash.setDown(true);
        NpcFKeyOwnership.drain(new KeyMapping[]{fly,dash}, GLFW.GLFW_KEY_F, 0);
        assertFalse(fly.isDown()); assertFalse(fly.consumeClick());
        assertTrue(dash.isDown()); assertTrue(dash.consumeClick());
    }
}

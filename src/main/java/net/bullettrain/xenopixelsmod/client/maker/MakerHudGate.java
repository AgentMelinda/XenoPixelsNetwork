package net.bullettrain.xenopixelsmod.client.maker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Full-screen Unified Maker hides world HUD so HP/KI/STM and technique bars
 * cannot sit on race cards.
 */
public final class MakerHudGate {
    private static final String PACKAGE = "net.bullettrain.xenopixelsmod.client.maker";

    private MakerHudGate() {
    }

    public static boolean hideWorldHud() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && isMakerScreen(mc.screen);
    }

    public static boolean isMakerScreen(Screen screen) {
        return screen != null && isMakerScreenClass(screen.getClass());
    }

    public static boolean isMakerScreenClass(Class<?> type) {
        if (type == null) {
            return false;
        }
        Package pack = type.getPackage();
        return pack != null && PACKAGE.equals(pack.getName());
    }
}

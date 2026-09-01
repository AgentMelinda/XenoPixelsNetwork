package net.bullettrain.xenopixelsmod.compat.sable;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Client-only actionbar flush for {@link SableContraptionCull} debug.
 * Kept out of the common class so a dedicated server never resolves Minecraft.
 */
public final class SableCullClientDebug {

    private SableCullClientDebug() {
    }

    public static void flush(String line) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.displayClientMessage(Component.literal("sablecull " + line), true);
    }
}

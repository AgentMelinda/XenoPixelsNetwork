package net.bullettrain.xenopixelsmod.client.combat;

import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * First click on the Skills delete X arms a confirm; the second click within the window
 * actually sends {@code DeleteTechniqueC2S}. The button has no stock confirmation and sits
 * next to Bind to Slot, so a missed bind click otherwise wipes the technique.
 */
public final class DeleteTechniqueConfirm {

    private static final long WINDOW_DEFAULT_MS = 3_000L;

    private static String armedId;
    private static long armedUntilMs;

    private DeleteTechniqueConfirm() {
    }

    /**
     * @return {@code true} if this click should send the delete packet
     */
    public static boolean tryConfirm(String techniqueId) {
        if (techniqueId == null || techniqueId.isEmpty()) return false;
        long window = confirmWindowMs();
        if (window <= 0L) return true;
        long now = System.currentTimeMillis();
        if (techniqueId.equals(armedId) && now <= armedUntilMs) {
            armedId = null;
            armedUntilMs = 0L;
            return true;
        }
        armedId = techniqueId;
        armedUntilMs = now + window;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.literal("Click delete again to remove this technique")
                            .withStyle(ChatFormatting.GOLD),
                    true);
        }
        return false;
    }

    private static long confirmWindowMs() {
        int configured = XenoClientConfig.deleteConfirmMs;
        if (configured < 0) return WINDOW_DEFAULT_MS;
        return configured;
    }
}

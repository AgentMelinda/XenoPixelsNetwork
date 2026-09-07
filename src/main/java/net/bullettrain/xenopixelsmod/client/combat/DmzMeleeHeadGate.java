package net.bullettrain.xenopixelsmod.client.combat;

import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * Head look is stripped for our R-mash string and charged kick/dragon, so hair follows
 * {@code root}. Not {@code dragonminez$isPlayingCombatAnimation()} — that is stock DMZ punches too.
 */
public final class DmzMeleeHeadGate {

    private DmzMeleeHeadGate() {
    }

    public static boolean active(Object animatable) {
        if (!(animatable instanceof Player player)) {
            return false;
        }
        if (!XenoClientConfig.bt3MashHeadFollow) {
            return false;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || player != mc.player) {
                return false;
            }
            return Bt3CombatClient.mashBodyYawOwnsHead();
        } catch (Throwable t) {
            return false;
        }
    }
}

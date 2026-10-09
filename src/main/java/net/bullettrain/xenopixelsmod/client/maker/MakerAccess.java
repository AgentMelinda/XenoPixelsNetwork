package net.bullettrain.xenopixelsmod.client.maker;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Client gates for Unified Maker Studio.
 *
 * <ul>
 *   <li>Singleplayer (integrated server): Race / Hair / Taotto / Ki open without OP.</li>
 *   <li>Form Maker: singleplayer only (cheats not required).</li>
 *   <li>Remote multiplayer / dedicated: OP level 2 still required for those makers.</li>
 * </ul>
 */
public final class MakerAccess {
    private MakerAccess() {
    }

    public static boolean isSingleplayerWorld() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.hasSingleplayerServer();
    }

    public static boolean isRemoteMultiplayer() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.getConnection() != null && !mc.hasSingleplayerServer();
    }

    /** Race, Hair, Taotto, Ki Profiles. */
    public static boolean canOpenCosmeticMaker() {
        if (isSingleplayerWorld()) return true;
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null && mc.player.hasPermissions(2);
    }

    /** Form Maker — SP only; cheats not required. */
    public static boolean canOpenFormMaker() {
        return isSingleplayerWorld();
    }

    public static Component denyCosmetic() {
        return Component.literal("On multiplayer servers, Maker editors require OP (level 2).");
    }

    public static Component denyForm() {
        return Component.literal("Form Maker is singleplayer-only (cheats not required).");
    }
}

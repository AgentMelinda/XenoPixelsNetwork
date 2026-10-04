package net.bullettrain.xenopixelsmod.client.npc;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;

/**
 * The extra factor NPC wand screens add on top of the GUI Scale, through DragonMineZ's
 * {@code ScaledScreen#computeDynamicScale} hook. DMZ's own answer is {@code sqrt(available)} (the
 * GUI-scaled window over 320x240); after its floor that doubled the screen at low GUI Scale, so GUI
 * Scale 1 and 2 drew the editor the same size. Returning 1 leaves the size to the GUI Scale; DMZ still
 * applies its user "menu scale" setting and still shrinks a screen that does not fit.
 * {@link XenoServerConfig#npcGuiFollowsGuiScale} off restores DMZ's adaptive factor.
 */
public final class NpcGuiScale {
    private NpcGuiScale() {}

    /** For a screen's {@code computeDynamicScale(available)} override; {@code dmzScale} is super's answer. */
    public static float dynamicScale(float dmzScale) {
        return dynamicScale(XenoServerConfig.npcGuiFollowsGuiScale, dmzScale);
    }

    static float dynamicScale(boolean followGuiScale, float dmzScale) {
        return followGuiScale ? 1.0f : dmzScale;
    }
}

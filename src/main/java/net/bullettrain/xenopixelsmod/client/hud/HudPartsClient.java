package net.bullettrain.xenopixelsmod.client.hud;

import net.bullettrain.xenopixelsmod.client.screen.HudSurfaces;
import net.bullettrain.xenopixelsmod.client.config.PartLayout;
import net.bullettrain.xenopixelsmod.hud.HudPartsBundle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Snapshots the live editor layouts for publish, and applies a server blob on join.
 */
@OnlyIn(Dist.CLIENT)
public final class HudPartsClient {
    private HudPartsClient() {}

    public static String snapshotJson() {
        HudPartsBundle bundle = new HudPartsBundle();
        for (PartLayout surface : HudSurfaces.ALL) {
            HudPartsBundle.Surface snap = new HudPartsBundle.Surface();
            snap.customLayout = surface.customLayout();
            snap.partX = surface.x().clone();
            snap.partY = surface.y().clone();
            snap.partScale = surface.scale().clone();
            snap.partColor = surface.color().clone();
            snap.partBold = surface.bold().clone();
            snap.partFont = surface.font().clone();
            snap.partHidden = surface.hidden().clone();
            bundle.surfaces.put(surface.name(), snap);
        }
        return HudPartsBundle.toJson(bundle);
    }

    public static void apply(String json) {
        HudPartsBundle bundle = HudPartsBundle.fromJson(json);
        if (bundle == null) return;
        for (PartLayout surface : HudSurfaces.ALL) {
            HudPartsBundle.Surface snap = bundle.surfaces.get(surface.name());
            if (snap == null) continue;
            copy(snap.partX, surface.x());
            copy(snap.partY, surface.y());
            copy(snap.partScale, surface.scale());
            copy(snap.partColor, surface.color());
            copy(snap.partBold, surface.bold());
            copy(snap.partFont, surface.font());
            copy(snap.partHidden, surface.hidden());
            surface.setCustomLayout(snap.customLayout);
            surface.save();
        }
    }

    private static void copy(int[] from, int[] into) {
        if (from != null) System.arraycopy(from, 0, into, 0, Math.min(from.length, into.length));
    }

    private static void copy(float[] from, float[] into) {
        if (from != null) System.arraycopy(from, 0, into, 0, Math.min(from.length, into.length));
    }

    private static void copy(boolean[] from, boolean[] into) {
        if (from != null) System.arraycopy(from, 0, into, 0, Math.min(from.length, into.length));
    }

    private static void copy(String[] from, String[] into) {
        if (from == null) return;
        for (int i = 0; i < Math.min(from.length, into.length); i++) {
            if (from[i] != null) into[i] = from[i];
        }
    }
}

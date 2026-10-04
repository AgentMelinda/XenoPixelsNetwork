package net.bullettrain.xenopixelsmod.client.hud;

import net.bullettrain.xenopixelsmod.api.dmz.DmzAccess;
import net.bullettrain.xenopixelsmod.features.transformation.passive.FormPassives;
import net.minecraft.client.Minecraft;

/**
 * The HUD bars in a form's own colour (2026-09-29 owner: "when hakaishin transform ... his ki bars
 * and hp turns purple or the color of the aura"). A form marked {@code hudTint} in
 * {@code form_passives.json} colours the HP and ki fills with its DragonMineZ aura colour, the way
 * Sparking turns the ki bar gold. Drawn over the neutral (greyscale) copy of the bar art so the
 * colour comes out clean; see {@code tools/gen_hud_neutral_atlas.py}.
 */
public final class FormHudTint {
    /** When the form has no aura colour: Hakai violet. */
    public static final int FALLBACK = 0xFFB030E0;

    private FormHudTint() {
    }

    /** The tint for the local player's bars right now, or 0 for none. */
    public static int current() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !FormPassives.of(mc.player).hudTint()) return 0;
        String hex = DmzAccess.stats(mc.player)
                .map(s -> s.getCharacter() == null || s.getCharacter().getActiveFormData() == null
                        ? null : s.getCharacter().getActiveFormData().getAuraColor())
                .orElse(null);
        return readable(parse(hex));
    }

    /** {@code #RRGGBB} (or without the {@code #}) to opaque ARGB; anything else is {@link #FALLBACK}. */
    public static int parse(String hex) {
        if (hex == null) return FALLBACK;
        String h = hex.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.length() != 6) return FALLBACK;
        try {
            return 0xFF000000 | Integer.parseInt(h, 16);
        } catch (NumberFormatException e) {
            return FALLBACK;
        }
    }

    /**
     * A dark aura colour (Hakaishin's is a deep violet) would make a near-black bar on the dark HUD;
     * lift it until its brightest channel is at least 210 (as bright as the gold), keeping the hue.
     */
    public static int readable(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        if (max >= 210) return argb;
        float k = max <= 0 ? 0.0f : 210.0f / max;
        if (max <= 0) return FALLBACK;
        return 0xFF000000 | (Math.min(255, Math.round(r * k)) << 16)
                | (Math.min(255, Math.round(g * k)) << 8) | Math.min(255, Math.round(b * k));
    }
}

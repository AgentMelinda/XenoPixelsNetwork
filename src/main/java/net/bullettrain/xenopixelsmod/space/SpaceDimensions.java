package net.bullettrain.xenopixelsmod.space;

import net.bullettrain.xenopixelsmod.space.chart.ChartRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * Which dimensions §19.1 calls <b>space</b> — the one question the void rule, the LOD ladder's observer
 * set, and the planet snapshot all have to answer the same way.
 *
 * <p>A dimension is space when a chart body is bound to it, which is "you are standing on a charted
 * world", or when an operator listed it in {@code space.spaceDimensions}. The second half exists because
 * the shipped spike host {@code xenospace_spike:orbit} is a test world with no chart row of its own:
 * keyed on chart bindings alone, both "am I protected from the void here" and "do planets promote around
 * the player here" would answer no in the only dimension anyone can currently fly in. That is not a
 * hypothetical — it is exactly how the groundwork shipped with nothing visible in the sky.
 *
 * <p>The {@link ResourceLocation} form is the per-tick path: it reads the index {@code ChartRegistry}
 * built at load and a config set, so it allocates nothing. The {@code String} form is for command and
 * test callers holding an authored id rather than a live level's, and parses once.
 */
public final class SpaceDimensions {
    private SpaceDimensions() {}

    public static boolean isSpace(ChartRegistry chart, ResourceLocation dimension,
                                  Set<ResourceLocation> listed) {
        if (dimension == null) return false;
        return (chart != null && chart.slotFor(dimension) >= 0) || listed.contains(dimension);
    }

    /** The same question for a caller holding an authored {@code namespace:path} string. */
    public static boolean isSpace(ChartRegistry chart, String dimension, Set<ResourceLocation> listed) {
        if (dimension == null) return false;
        if (chart != null && chart.slotForDimension(dimension) >= 0) return true;
        ResourceLocation parsed = ResourceLocation.tryParse(dimension);
        return parsed != null && listed.contains(parsed);
    }
}

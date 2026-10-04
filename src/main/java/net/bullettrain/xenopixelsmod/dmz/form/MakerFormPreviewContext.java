package net.bullettrain.xenopixelsmod.dmz.form;

import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.stats.character.Character;

/** A draft belongs only to the character being drawn on this thread, never the config registry. */
public final class MakerFormPreviewContext {
    private static final ThreadLocal<Override> CURRENT = new ThreadLocal<>();
    private MakerFormPreviewContext() { }

    public static FormConfig.FormData get(Character character) {
        Override override = CURRENT.get();
        return override != null && override.character == character ? override.data : null;
    }

    public static void draw(Character character, FormConfig.FormData data, Runnable draw) {
        Override previous = CURRENT.get();
        CURRENT.set(new Override(character, data));
        try { draw.run(); }
        finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    private record Override(Character character, FormConfig.FormData data) { }
}

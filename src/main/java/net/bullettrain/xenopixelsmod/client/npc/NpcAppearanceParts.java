package net.bullettrain.xenopixelsmod.client.npc;

import com.dragonminez.client.util.TextureCounter;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.RaceCharacterConfig;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDmzAppearance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Per-race appearance part counts, read from DragonMineZ rather than guessed.
 *
 * <p>The native editor previously offered part indices as open-ended steppers because I could not
 * find a way to enumerate them. There is one: {@code TextureCounter} counts the numbered texture
 * files a race actually ships, and {@code GuiNpcDmzAppearance} - this project's own DMZ tab for
 * MyNPCs - has been using it all along. This class is that logic shared, so the native editor and
 * the MyNPCs tab agree about what a race offers.
 *
 * <p>The race-to-texture-set mapping matters just as much as the counts. A race can declare a
 * custom model in its {@code RaceCharacterConfig}, and when it does, its parts come from that model
 * base rather than from the race id - which is why a saiyan NPC left on the raw race name was not
 * picking up saiyan textures.
 *
 * <p>Client-side: {@code TextureCounter} counts resources, which only exist on a client.
 */
public final class NpcAppearanceParts {

    /** Used when a race is unset or unknown, matching the reference's own fallback. */
    private static final String DEFAULT_RACE = "human";

    private NpcAppearanceParts() {
    }

    /**
     * The texture set a race draws its parts from.
     *
     * <p>A race whose config declares a custom model uses that model's textures; everything else
     * uses its own race id. Same rule as {@code GuiNpcDmzAppearance.effectiveModelBase}.
     */
    public static String modelBase(String raceId) {
        String race = raceId == null ? DEFAULT_RACE : raceId.trim().toLowerCase(Locale.ROOT);
        if (race.isBlank()) {
            race = DEFAULT_RACE;
        }
        try {
            RaceCharacterConfig config = ConfigManager.getRaceCharacter(race);
            if (config != null && Boolean.TRUE.equals(config.hasCustomModel())
                    && config.getCustomModel() != null && !config.getCustomModel().isBlank()) {
                return config.getCustomModel().toLowerCase(Locale.ROOT);
            }
        } catch (Throwable ignored) {
            // DMZ config may not be loaded yet; the race id is a safe stand-in.
        }
        return race;
    }

    /** Highest valid body type for this race and gender, or 0 when nothing can be counted. */
    public static int maxBodyType(String raceId, NpcDmzAppearance appearance) {
        String gender = appearance == null || appearance.gender == null
                ? "male" : appearance.gender;
        return count(() -> TextureCounter.getMaxBodyTypes(modelBase(raceId), gender));
    }

    public static int maxEyesType(String raceId) {
        return count(() -> TextureCounter.getMaxEyesTypes(modelBase(raceId)));
    }

    public static int maxNoseType(String raceId) {
        return count(() -> TextureCounter.getMaxNoseTypes(modelBase(raceId)));
    }

    public static int maxMouthType(String raceId) {
        return count(() -> TextureCounter.getMaxMouthTypes(modelBase(raceId)));
    }

    public static int maxTattooType(String raceId) {
        return count(() -> TextureCounter.getMaxTattooTypes(modelBase(raceId)));
    }

    /**
     * Whether this race has eyebrow textures separate from its eyes.
     *
     * <p>The same set {@code GuiNpcDmzAppearance} checks; other races link brows to the eye choice,
     * so offering a separate control for them would be offering a control that does nothing.
     */
    public static boolean supportsSeparateBrows(String raceId) {
        String model = modelBase(raceId);
        return "human".equals(model) || "saiyan".equals(model)
                || "halfsaiyan".equals(model) || "humansaiyan".equals(model);
    }

    /**
     * Part indices as a cycle's value list: {@code 0 .. max}.
     *
     * <p>Empty when nothing could be counted, which callers treat as "keep a plain number field"
     * rather than showing a cycle with one fake entry.
     */
    public static List<String> indices(int max) {
        if (max <= 0) {
            return List.of();
        }
        List<String> values = new ArrayList<>(max + 1);
        for (int i = 0; i <= max; i++) {
            values.add(Integer.toString(i));
        }
        return List.copyOf(values);
    }

    private static int count(java.util.function.IntSupplier supplier) {
        try {
            return Math.max(0, supplier.getAsInt());
        } catch (Throwable ignored) {
            // Counting walks the resource pack; a pack mid-reload should not break the editor.
            return 0;
        }
    }
}

package net.bullettrain.xenopixelsmod.compat.npc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads optional Xeno-owned NPC display tuning from DragonMineZ form JSON. */
public final class NpcFormDisplayTuning {
    private static final String DISPLAY_SIZE = "xenopixelsNpcDisplaySize";
    private static final String AURA_SCALE = "xenopixelsNpcAuraScale";
    private static final Tuning NONE = new Tuning(0, Float.NaN);

    private NpcFormDisplayTuning() {
    }

    public static Tuning forProfile(NpcCombatProfile profile) {
        if (profile == null || blank(profile.raceId) || blank(profile.formGroup) || blank(profile.formId)) {
            return NONE;
        }
        for (String tree : new String[] {"dragonminez", "dragonminez1"}) {
            Path path = FMLPaths.CONFIGDIR.get().resolve(tree).resolve("races")
                    .resolve(profile.raceId).resolve("forms").resolve(profile.formGroup + ".json");
            Tuning tuning = read(path, profile.formId);
            if (tuning != NONE) {
                return tuning;
            }
        }
        return NONE;
    }

    public static float effectiveAuraScale(NpcCombatProfile profile) {
        Tuning tuning = forProfile(profile);
        return Float.isFinite(tuning.auraScale())
                ? tuning.auraScale()
                : NpcCombatProfile.clampAuraScale(profile.auraScale);
    }

    public static void applyCurrentSize(net.minecraft.world.entity.LivingEntity npc, NpcCombatProfile profile) {
        Tuning tuning = forProfile(profile);
        if (tuning.displaySize() > 0 && NpcDisplayApply.getSize(npc) != tuning.displaySize()) {
            NpcDisplayApply.setSize(npc, tuning.displaySize());
        }
    }

    public static void applyTransition(net.minecraft.world.entity.LivingEntity npc, NpcCombatProfile profile,
                                       Tuning previous) {
        Tuning current = forProfile(profile);
        if (current.displaySize() > 0) {
            NpcDisplayApply.setSize(npc, current.displaySize());
        } else if (previous.displaySize() > 0 && profile.baseSize > 0) {
            NpcDisplayApply.setSize(npc, profile.baseSize);
        }
    }

    private static Tuning read(Path path, String formId) {
        if (!Files.isRegularFile(path)) {
            return NONE;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject forms = root.getAsJsonObject("forms");
            JsonObject form = forms == null ? null : forms.getAsJsonObject(formId);
            if (form == null || (!form.has(DISPLAY_SIZE) && !form.has(AURA_SCALE))) {
                return NONE;
            }
            int size = positiveInt(form.get(DISPLAY_SIZE));
            float aura = positiveFloat(form.get(AURA_SCALE));
            return new Tuning(size, aura);
        } catch (Exception ignored) {
            return NONE;
        }
    }

    private static int positiveInt(JsonElement value) {
        try {
            int parsed = value == null ? 0 : value.getAsInt();
            return parsed > 0 ? parsed : 0;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static float positiveFloat(JsonElement value) {
        try {
            float parsed = value == null ? Float.NaN : value.getAsFloat();
            return Float.isFinite(parsed) && parsed > 0.0f ? parsed : Float.NaN;
        } catch (RuntimeException ignored) {
            return Float.NaN;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record Tuning(int displaySize, float auraScale) {
    }
}

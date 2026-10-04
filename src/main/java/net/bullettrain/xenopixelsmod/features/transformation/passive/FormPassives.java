package net.bullettrain.xenopixelsmod.features.transformation.passive;

import com.dragonminez.common.stats.StatsData;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.api.dmz.DmzAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Which DMZ form has which {@link FormPassive}, and the rules they share.
 *
 * <p>The table is {@code data/xenopixelsmod/form_passives.json} in the jar, keyed
 * {@code "group.form"}; a {@code config/xenopixelsmod/form_passives.json} overrides or adds entries,
 * which is how another race's form gets a passive without code. A player's form is DMZ's own
 * active form; an NPC's is its profile's {@code formGroup}/{@code formId}.
 */
public final class FormPassives {
    private static final String RESOURCE = "/data/xenopixelsmod/form_passives.json";
    private static volatile Map<String, FormPassive> table;

    private FormPassives() {
    }

    // ---- rules (pure) ----

    /** The attacker is weaker when its power is below the defender's times {@code ratio}. */
    public static boolean weaker(double attackerPower, double defenderPower, float ratio) {
        // Through its decimal form: 0.8f widened straight to double is 0.80000001.
        return defenderPower > 0.0 && attackerPower > 0.0
                && attackerPower < defenderPower * Double.parseDouble(Float.toString(ratio));
    }

    /** Dodge chance from {@code min} at no mastery to {@code max} at full, linearly. */
    public static float dodgeChance(float min, float max, double mastery, double maxMastery) {
        if (!(maxMastery > 0.0)) return min;
        double t = Math.max(0.0, Math.min(1.0, mastery / maxMastery));
        return (float) (min + (max - min) * t);
    }

    /** "if 2 ppl have UI their dodge chances are gone": no dodge against another dodger. */
    public static boolean mayDodge(FormPassive defender, FormPassive attacker) {
        return defender.hasDodge() && !attacker.hasDodge();
    }

    // ---- table ----

    public static Map<String, FormPassive> parse(Reader reader) {
        Map<String, FormPassive> out = new HashMap<>();
        try {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                if (!e.getValue().isJsonObject() || e.getKey().startsWith("_")) continue;
                JsonObject o = e.getValue().getAsJsonObject();
                FormPassive.Builder b = FormPassive.builder()
                        .weakerImmunity(bool(o, "weakerImmunity"))
                        .weakerRatio(num(o, "weakerRatio", 0.8f))
                        .defPenBonus(num(o, "defPenBonus", 0.0f))
                        .deleteWeakProjectiles(bool(o, "deleteWeakProjectiles"))
                        .punchBreaksWeakKi(bool(o, "punchBreaksWeakKi"))
                        .hakaiMantle(bool(o, "hakaiMantle"))
                        .dodge(num(o, "dodgeMin", 0.0f), num(o, "dodgeMax", 0.0f))
                        .hudTint(bool(o, "hudTint"));
                out.put(e.getKey().toLowerCase(Locale.ROOT), b.build());
            }
        } catch (RuntimeException ignored) {
            // A broken file gives no passives rather than a crash.
        }
        return out;
    }

    private static boolean bool(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() && o.get(k).getAsBoolean();
    }

    private static float num(JsonObject o, String k, float fallback) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsFloat() : fallback;
    }

    public static FormPassive lookup(Map<String, FormPassive> table, String group, String form) {
        if (group == null || form == null || group.isBlank() || form.isBlank()) return FormPassive.NONE;
        return table.getOrDefault((group + "." + form).toLowerCase(Locale.ROOT), FormPassive.NONE);
    }

    /** The bundled table with the config overrides on top. Reloaded by {@link #reload}. */
    public static Map<String, FormPassive> table() {
        Map<String, FormPassive> t = table;
        if (t == null) {
            t = reload();
        }
        return t;
    }

    public static synchronized Map<String, FormPassive> reload() {
        Map<String, FormPassive> t = new HashMap<>();
        try (InputStream in = FormPassives.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                t.putAll(parse(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (Exception e) {
            XenoPixelsMod.LOGGER.warn("Form passives: bundled table unreadable", e);
        }
        try {
            Path override = FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod").resolve("form_passives.json");
            if (Files.exists(override)) {
                try (Reader r = Files.newBufferedReader(override, StandardCharsets.UTF_8)) {
                    t.putAll(parse(r));
                }
            }
        } catch (Exception | LinkageError ignored) {
            // No FML (unit tests) or an unreadable override: the bundled table stands.
        }
        table = t;
        return t;
    }

    // ---- entities ----

    /** The passives of whatever form this entity is in now, or {@link FormPassive#NONE}. */
    public static FormPassive of(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return FormPassive.NONE;
        Optional<StatsData> stats = DmzAccess.stats(living);
        if (stats.isPresent() && stats.get().getCharacter() != null
                && stats.get().getCharacter().hasActiveForm()) {
            var c = stats.get().getCharacter();
            return lookup(table(), c.getActiveFormGroup(), c.getActiveForm());
        }
        if (!(living instanceof net.minecraft.world.entity.player.Player)) {
            var profile = net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.readCached(living);
            return lookup(table(), profile.formGroup, profile.formId);
        }
        return FormPassive.NONE;
    }

    /** Dodge chance for this entity in its current form, with its DMZ mastery (players). */
    public static float dodgeChanceOf(LivingEntity entity, FormPassive passive) {
        if (!passive.hasDodge()) return 0.0f;
        Optional<StatsData> stats = DmzAccess.stats(entity);
        if (stats.isPresent() && stats.get().getCharacter() != null) {
            var c = stats.get().getCharacter();
            try {
                double mastery = c.getFormMasteries().getMastery(c.getActiveFormGroup(), c.getActiveForm());
                Double max = c.getActiveFormData() == null ? null : c.getActiveFormData().getMaxMastery();
                return dodgeChance(passive.dodgeMin(), passive.dodgeMax(), mastery, max == null ? 0.0 : max);
            } catch (RuntimeException ignored) {
                return passive.dodgeMin();
            }
        }
        // NPCs have no DMZ mastery: halfway.
        return (passive.dodgeMin() + passive.dodgeMax()) * 0.5f;
    }

    /**
     * How strong each side is, for "weaker": the DragonMineZ power level ki sense shows, for players,
     * NPCs and mobs alike ({@link DmzAccess#powerLevel}).
     */
    public static double[] powers(LivingEntity attacker, LivingEntity defender) {
        return new double[]{DmzAccess.powerLevel(attacker), DmzAccess.powerLevel(defender)};
    }

    public static boolean attackerWeaker(LivingEntity attacker, LivingEntity defender, float ratio) {
        double[] p = powers(attacker, defender);
        return weaker(p[0], p[1], ratio);
    }
}

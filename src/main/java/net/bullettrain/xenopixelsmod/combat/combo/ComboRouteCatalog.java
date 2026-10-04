package net.bullettrain.xenopixelsmod.combat.combo;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;
import net.bullettrain.xenopixelsmod.features.progression.CombatSkills;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Built-in grant-gated combo routes. Data only; no Minecraft world types. */
public final class ComboRouteCatalog {

    public static final String RUSH_COMBO_STRIKE = XenoPixelsMod.MOD_ID + ":rush_combo";
    public static final String LIFT_COMBO_STRIKE = XenoPixelsMod.MOD_ID + ":lift_combo";

    private static final Map<String, ComboRoute> BY_SKILL = new LinkedHashMap<>();

    static {
        BY_SKILL.put(CombatSkills.RUSHCOMBO, new ComboRoute(
                CombatSkills.RUSHCOMBO,
                RUSH_COMBO_STRIKE,
                new Bt3AnimationIntent[]{
                        Bt3AnimationIntent.JAB_LEFT,
                        Bt3AnimationIntent.JAB_RIGHT,
                        Bt3AnimationIntent.CROSS_LEFT,
                        Bt3AnimationIntent.HEAVY_FINISH
                },
                new KnockbackSpec(1.2, 0.35, 0.8),
                true,
                false,
                new Bt3AnimationIntent[]{
                        Bt3AnimationIntent.JAB_RIGHT,
                        Bt3AnimationIntent.CROSS_RIGHT,
                        Bt3AnimationIntent.MID_KICK_LEFT,
                        Bt3AnimationIntent.HEAVY_FINISH
                },
                new String[]{
                        XenoPixelsMod.MOD_ID + ":rush_left",
                        XenoPixelsMod.MOD_ID + ":rush_right",
                        XenoPixelsMod.MOD_ID + ":rush_breaker",
                        XenoPixelsMod.MOD_ID + ":rush_finisher"
                }));
        BY_SKILL.put(CombatSkills.LIFTCOMBO, new ComboRoute(
                CombatSkills.LIFTCOMBO,
                LIFT_COMBO_STRIKE,
                new Bt3AnimationIntent[]{
                        Bt3AnimationIntent.JAB_LEFT,
                        Bt3AnimationIntent.CROSS_RIGHT,
                        Bt3AnimationIntent.UPPERCUT_RIGHT,
                        Bt3AnimationIntent.FLYING_KICK
                },
                new KnockbackSpec(0.4, 0.9, 0.8),
                false,
                true,
                new Bt3AnimationIntent[]{
                        Bt3AnimationIntent.FLYING_KICK
                },
                new String[]{
                        XenoPixelsMod.MOD_ID + ":rush_left",
                        XenoPixelsMod.MOD_ID + ":rush_right",
                        XenoPixelsMod.MOD_ID + ":rush_breaker",
                        XenoPixelsMod.MOD_ID + ":rush_finisher"
                }));
    }

    private ComboRouteCatalog() {
    }

    public static ComboRoute bySkillId(String skillId) {
        if (skillId == null) return null;
        return BY_SKILL.get(skillId.toLowerCase(Locale.ROOT));
    }

    public static ComboRoute byStrikeId(String strikeId) {
        if (strikeId == null) return null;
        for (ComboRoute route : BY_SKILL.values()) {
            if (route.strikeId().equals(strikeId)) return route;
        }
        return null;
    }

    public static Iterable<ComboRoute> all() {
        return BY_SKILL.values();
    }
}

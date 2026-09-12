package net.bullettrain.xenopixelsmod.effect;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Status effects shown in inventory (E) for Super Souls, Sparking, and combat skills.
 * Gameplay numbers still come from capability / combat code — these are the vanilla HUD icons.
 */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, XenoPixelsMod.MOD_ID);

    // --- Super Souls (one each so inventory shows the correct name/icon) ---
    public static final DeferredHolder<MobEffect, MobEffect> SUPER_SOUL_WARRIOR = EFFECTS.register(
            "super_soul_warrior", () -> new XenoMobEffect(MobEffectCategory.BENEFICIAL, 0xDC3730));
    public static final DeferredHolder<MobEffect, MobEffect> SUPER_SOUL_IRON = EFFECTS.register(
            "super_soul_iron", () -> new XenoMobEffect(MobEffectCategory.BENEFICIAL, 0x96AAC3));
    public static final DeferredHolder<MobEffect, MobEffect> SUPER_SOUL_SPARK = EFFECTS.register(
            "super_soul_spark", () -> new XenoMobEffect(MobEffectCategory.BENEFICIAL, 0xFFD228));
    public static final DeferredHolder<MobEffect, MobEffect> SUPER_SOUL_FINISHER = EFFECTS.register(
            "super_soul_finisher", () -> new XenoMobEffect(MobEffectCategory.BENEFICIAL, 0xC846FF));
    public static final DeferredHolder<MobEffect, MobEffect> SUPER_SOUL_BALANCED = EFFECTS.register(
            "super_soul_balanced", () -> new XenoMobEffect(MobEffectCategory.BENEFICIAL, 0x32C88C));

    /** Active Sparking mode (timed). */
    public static final DeferredHolder<MobEffect, MobEffect> SPARKING = EFFECTS.register(
            "sparking", () -> new XenoMobEffect(MobEffectCategory.BENEFICIAL, 0xFFAA00));

    /**
     * Slim skill tree passive summary. Amplifier = total skill levels − 1 (I…XII max).
     */
    public static final DeferredHolder<MobEffect, MobEffect> COMBAT_TRAINING = EFFECTS.register(
            "combat_training", () -> new XenoMobEffect(MobEffectCategory.BENEFICIAL, 0x55CCFF));

    /** Full sparking meter ready to activate (indicator only). */
    public static final DeferredHolder<MobEffect, MobEffect> SPARKING_READY = EFFECTS.register(
            "sparking_ready", () -> new XenoMobEffect(MobEffectCategory.BENEFICIAL, 0xFFEE88));

    /**
     * Hidden Hakai dissolve. Amplifier 0..255 is erase progress; vanilla syncs it to tracker
     * clients and the victim. No icon, no particles — the body fade is the readout.
     */
    public static final DeferredHolder<MobEffect, MobEffect> HAKAI_DISSOLVE = EFFECTS.register(
            "hakai_dissolve", () -> new XenoMobEffect(MobEffectCategory.HARMFUL, 0xB266FF));

    private ModEffects() {}

    public static void register(IEventBus bus) {
        EFFECTS.register(bus);
    }

    public static Holder<MobEffect> soulEffect(String soulId) {
        if (soulId == null) return null;
        return switch (soulId.toLowerCase()) {
            case "warrior" -> SUPER_SOUL_WARRIOR;
            case "iron" -> SUPER_SOUL_IRON;
            case "spark" -> SUPER_SOUL_SPARK;
            case "finisher" -> SUPER_SOUL_FINISHER;
            case "balanced" -> SUPER_SOUL_BALANCED;
            default -> null;
        };
    }
}

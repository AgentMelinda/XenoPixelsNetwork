package net.bullettrain.xenopixelsmod.effect;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Status effects shown in inventory (E) for Super Souls, Sparking, and combat skills.
 * Gameplay numbers still come from capability / combat code — these are the vanilla HUD icons.
 */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, XenoPixelsMod.MOD_ID);

    // --- Super Souls (one each so inventory shows the correct name/icon) ---
    public static final RegistryObject<MobEffect> SUPER_SOUL_WARRIOR = EFFECTS.register(
            "super_soul_warrior", () -> new XenoMobEffect(MobEffectCategory.BENEFICIAL, 0xDC3730));
    public static final RegistryObject<MobEffect> SUPER_SOUL_IRON = EFFECTS.register(
            "super_soul_iron", () -> new XenoMobEffect(MobEffectCategory.BENEFICIAL, 0x96AAC3));
    public static final RegistryObject<MobEffect> SUPER_SOUL_SPARK = EFFECTS.register(
            "super_soul_spark", () -> new XenoMobEffect(MobEffectCategory.BENEFICIAL, 0xFFD228));
    public static final RegistryObject<MobEffect> SUPER_SOUL_FINISHER = EFFECTS.register(
            "super_soul_finisher", () -> new XenoMobEffect(MobEffectCategory.BENEFICIAL, 0xC846FF));
    public static final RegistryObject<MobEffect> SUPER_SOUL_BALANCED = EFFECTS.register(
            "super_soul_balanced", () -> new XenoMobEffect(MobEffectCategory.BENEFICIAL, 0x32C88C));

    /** Active Sparking mode (timed). */
    public static final RegistryObject<MobEffect> SPARKING = EFFECTS.register(
            "sparking", () -> new XenoMobEffect(MobEffectCategory.BENEFICIAL, 0xFFAA00));

    /**
     * Slim skill tree passive summary. Amplifier = total skill levels − 1 (I…XII max).
     */
    public static final RegistryObject<MobEffect> COMBAT_TRAINING = EFFECTS.register(
            "combat_training", () -> new XenoMobEffect(MobEffectCategory.BENEFICIAL, 0x55CCFF));

    /** Full sparking meter ready to activate (indicator only). */
    public static final RegistryObject<MobEffect> SPARKING_READY = EFFECTS.register(
            "sparking_ready", () -> new XenoMobEffect(MobEffectCategory.BENEFICIAL, 0xFFEE88));

    private ModEffects() {}

    public static void register(IEventBus bus) {
        EFFECTS.register(bus);
    }

    public static MobEffect soulEffect(String soulId) {
        if (soulId == null) return null;
        return switch (soulId.toLowerCase()) {
            case "warrior" -> SUPER_SOUL_WARRIOR.get();
            case "iron" -> SUPER_SOUL_IRON.get();
            case "spark" -> SUPER_SOUL_SPARK.get();
            case "finisher" -> SUPER_SOUL_FINISHER.get();
            case "balanced" -> SUPER_SOUL_BALANCED.get();
            default -> null;
        };
    }
}

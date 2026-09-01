package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.stats.techniques.KiAttackData;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces DMZ's small per-type creator caps with synchronized server limits.
 *
 * <p><b>The raised ceiling is an authoring limit only.</b> {@code getMaxSizeForType} and
 * {@code getMaxSpeedForType} have four consumers in DMZ, and they want different answers:
 *
 * <ul>
 *   <li>{@code TechniqueCreatorScreen} slider bounds and {@code normalizeStatsForType}'s clamp —
 *       these are the ceiling, and should see the configured maximum.</li>
 *   <li>{@code sizeComplexityRatio} and {@code getDefaultSpeedForType} — these are <em>balance</em>
 *       maths, and must keep DMZ's original per-type bounds.</li>
 * </ul>
 *
 * <p>Widening all four broke the economy server-wide rather than just raising a slider.
 * {@code sizeComplexityRatio} is {@code (size - min) / (max - min)} and feeds
 * {@code getWeightedComplexity} into both {@code tpCost} and {@code getUpgradeXpCost}: with the max
 * at 320 a GIANT_BALL of size 20 scored 0.016 instead of 1.0, making <em>every</em> technique on the
 * server drastically cheaper in TP and XP. {@code getDefaultSpeedForType} is
 * {@code min(1.0F, getMaxSpeedForType(type))}, so a widened max also silently forced every type's
 * default speed to 1.0.
 *
 * <p>So the two balance call sites are injected back onto the vanilla numbers below.
 */
@Mixin(value = KiAttackData.class, remap = false)
public abstract class KiAttackDataLimitsMixin {

    @Inject(method = "getMaxSizeForType", at = @At("HEAD"), cancellable = true)
    private static void xenopixels$maxSize(KiAttackData.KiType type,
                                            CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(XenoServerConfig.kiProjectileMaxSize);
    }

    @Inject(method = "getMaxSpeedForType", at = @At("HEAD"), cancellable = true)
    private static void xenopixels$maxSpeed(KiAttackData.KiType type,
                                             CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(XenoServerConfig.kiProjectileMaxSpeed);
    }

    /** Cost complexity must normalise against DMZ's bounds, not the authoring ceiling. */
    @Inject(method = "sizeComplexityRatio", at = @At("HEAD"), cancellable = true)
    private static void xenopixels$sizeComplexityRatio(KiAttackData.KiType type, float size,
                                                       CallbackInfoReturnable<Float> cir) {
        KiAttackData.KiType resolved = type != null ? type : KiAttackData.KiType.SMALL_BALL;
        // The non-custom-size branch does not consult the max at all, so leave it to DMZ.
        if (!KiAttackData.usesCustomSize(resolved)) return;
        float min = KiAttackData.getMinSizeForType(resolved);
        float max = vanillaMaxSize(resolved);
        cir.setReturnValue(max <= min ? 0.0F : Mth.clamp((size - min) / (max - min), 0.0F, 1.0F));
    }

    /** Default speed is derived from the vanilla cap; the authoring ceiling must not move it. */
    @Inject(method = "getDefaultSpeedForType", at = @At("HEAD"), cancellable = true)
    private static void xenopixels$defaultSpeed(KiAttackData.KiType type,
                                                 CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(Math.min(1.0F, vanillaMaxSpeed(type)));
    }

    /**
     * Mirrors {@code KiAttackData.getMaxSizeForType} as it exists in DragonMineZ.
     *
     * <p>Duplicated rather than captured because the accessor above is unconditionally overridden,
     * so there is no longer a way to ask DMZ for its own answer. <b>Keep in sync on a DMZ bump.</b>
     */
    private static float vanillaMaxSize(KiAttackData.KiType type) {
        return switch (type) {
            case MEDIUM_BALL -> 12.5F;
            case SMALL_BALL -> 5.0F;
            default -> 20.0F;
        };
    }

    /** Mirrors {@code KiAttackData.getMaxSpeedForType}. Keep in sync on a DMZ bump. */
    private static float vanillaMaxSpeed(KiAttackData.KiType type) {
        return switch (type) {
            case SMALL_BALL, LASER -> 2.0F;
            case DISK -> 1.75F;
            case MEDIUM_BALL, BEAM, BARRAGE -> 1.5F;
            case WAVE -> 1.25F;
            case GIANT_BALL -> 0.75F;
            default -> 1.0F;
        };
    }
}

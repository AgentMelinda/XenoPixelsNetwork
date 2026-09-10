package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.bullettrain.xenopixelsmod.combat.XenoStatCeiling;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets a server raise or lift DragonMineZ's per-stat ceiling from XenoPixels.
 *
 * <p><b>Target:</b> {@code GeneralServerConfig$GameplayConfig#getMaxValue()}.
 * <b>Written for:</b> Minecraft 1.21.1 / NeoForge 21.1.238, DragonMineZ 2.1.3, MixinExtras 0.5.3.
 * <b>Side:</b> both — the cap is applied wherever stats are written or budgeted.
 *
 * <p><b>Why this method and no other.</b> Every per-stat cap in DragonMineZ funnels through this one
 * getter, verified in its bytecode:
 * <ul>
 *   <li>{@code Stats#clampStatValue(int)} — which every {@code setStrength}/{@code setStat} call
 *       runs through — reads it directly;
 *   <li>{@code StatsData#getConfiguredMaxValue()} returns it verbatim, and
 *       {@code clampStatToConfiguredMax} and {@code getMaxAllowedIncreaseForStat} (the {@code +}
 *       button's budget) are built on that.
 * </ul>
 * So one return value covers the setters, the command path and the menu alike, and none of
 * DragonMineZ's own arithmetic is rewritten — it simply gets a different number for its own limit.
 *
 * <p><b>The ceiling this cannot lift.</b> DragonMineZ's own getter already clamps into
 * {@code [1000, Integer.MAX_VALUE]}, and {@code Stats} stores each stat in an {@code int} field, so
 * {@link XenoStatCeiling#MAX} is a wall in DragonMineZ's data model rather than a policy either mod
 * chose. {@code /xenostats limit off} means "as high as DragonMineZ can hold", and says so.
 *
 * <p><b>Guideline notes</b> (§18): {@code @ModifyReturnValue} is the narrowest injector that can
 * change a getter's answer without touching how it is computed. The body is one field read and, when
 * the override is off, returns the original object untouched — so a server that has not asked for
 * this pays a comparison and keeps DragonMineZ's exact behaviour. {@code require = 0} because the
 * target belongs to another mod: a DragonMineZ update that renames it loses the override rather than
 * failing to load. DragonMineZ is not modified.
 */
@Mixin(targets = "com.dragonminez.common.config.GeneralServerConfig$GameplayConfig", remap = false)
public abstract class DmzStatMaxOverrideMixin {

    @ModifyReturnValue(method = "getMaxValue", at = @At("RETURN"), remap = false, require = 0)
    private Integer xenopixels$raiseStatCeiling(Integer original) {
        int setting = XenoServerConfig.statMaxOverride;
        if (XenoStatCeiling.deferring(setting)) {
            return original;
        }
        return XenoStatCeiling.effective(setting, original == null ? 0 : original);
    }
}

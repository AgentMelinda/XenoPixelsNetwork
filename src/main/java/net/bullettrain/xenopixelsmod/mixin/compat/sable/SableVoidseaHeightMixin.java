package net.bullettrain.xenopixelsmod.mixin.compat.sable;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Unbounded Voidsea height (switch {@link XenoSpaceConfig#voidseaUnboundedHeight()}, default off). Sable 2.0.3
 * {@code ServerSubLevel.tick()} removes a body whose bounds leave [sub_level_remove_min, sub_level_remove_max]
 * (defaults -10,000 / 100,000), read through {@code ModConfigSpec.DoubleValue.getAsDouble()} at exactly two call sites
 * (javap offsets 26-29 and 42-45). In the Voidsea host, with the switch on, those reads return
 * -/+{@link XenoSpaceConfig#VOIDSEA_HEIGHT_LIMIT} instead; every other dimension keeps Sable's configured values. The
 * limit stays far below the +-2.1e9 empty-bounds sentinel, so bodies with broken bounds are still removed (S11 QF-007).
 */
@Mixin(value = ServerSubLevel.class, remap = false)
public abstract class SableVoidseaHeightMixin {
    @Redirect(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/common/ModConfigSpec$DoubleValue;getAsDouble()D", ordinal = 0), require = 1)
    private double xenopixels$voidseaRemoveMin(ModConfigSpec.DoubleValue value) {
        return unbounded() ? -XenoSpaceConfig.VOIDSEA_HEIGHT_LIMIT : value.getAsDouble();
    }

    @Redirect(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/common/ModConfigSpec$DoubleValue;getAsDouble()D", ordinal = 1), require = 1)
    private double xenopixels$voidseaRemoveMax(ModConfigSpec.DoubleValue value) {
        return unbounded() ? XenoSpaceConfig.VOIDSEA_HEIGHT_LIMIT : value.getAsDouble();
    }

    private boolean unbounded() {
        if (!XenoSpaceConfig.voidseaUnboundedHeight()) return false;
        var level = ((ServerSubLevel) (Object) this).getLevel();
        return level != null && PhysicalEarth.HOST.equals(level.dimension().location());
    }
}

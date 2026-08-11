package net.bullettrain.xenopixelsmod.mixin.compat.yawp;

import com.dragonminez.common.init.MainGameRules;
import net.bullettrain.xenopixelsmod.compat.yawp.YawpKiGriefing;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds YAWP regions to DragonMineZ's ki-griefing decision.
 *
 * <p>{@link MainGameRules#canKiGrief} is the single choke point every ki-caused block break
 * goes through (ki projectiles, momentum impacts), and it already chains the three
 * {@code allowKiGriefing*} gamerules and WorldGuard. This appends the YAWP check to that
 * chain: it can only ever turn an allow into a deny, so the gamerules and WorldGuard keep
 * the final say on denial and nothing becomes more permissive than the server configured.
 *
 * <p>Only applied when YAWP is installed - see {@code ConditionalMixinPlugin}.
 */
@Mixin(value = MainGameRules.class, remap = false)
public class MainGameRulesMixin {

    // require = 1: DragonMineZ is a hard dependency, so losing this hook means regions are
    // silently unprotected. Better to fail loudly at boot than to ship broken protection.
    @Inject(method = "canKiGrief", at = @At("RETURN"), cancellable = true, remap = false, require = 1)
    private static void xeno$yawpKiGriefingCheck(Level level, BlockPos pos, Entity source,
                                                 CallbackInfoReturnable<Boolean> cir) {
        // Already denied by a gamerule, a master structure or WorldGuard - nothing to add.
        if (!cir.getReturnValueZ()) {
            return;
        }
        if (!YawpKiGriefing.canGrief(level, pos, source)) {
            cir.setReturnValue(false);
        }
    }
}

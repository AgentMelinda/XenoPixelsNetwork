package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.client.events.LockOnEvent;
import net.bullettrain.xenopixelsmod.compat.npc.NpcKiAim;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Target: {@code LockOnEvent} acquire/persist/HUD.
 * Reason: CustomNPCs (and My NPCs) fail DMZ Z-lock even though {@code canTarget} already
 *         accepts non-players. CNPC {@code isInvisible()} is a visibility flag, not potion
 *         invisibility; {@code hitboxState==1} makes the AABB paper-thin so the look-ray
 *         misses; kisense acquire/hold is {@code 15+5*level} (20 at level 1).
 * Version: NeoForge 1.21.1 / DMZ 2.1.3
 * Side: client.
 */
@Mixin(value = LockOnEvent.class, remap = false)
public abstract class DmzLockOnNpcMixin {

    @Redirect(
            method = "findTargetInFront",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;isInvisible()Z",
                    remap = true
            )
    )
    private static boolean xenopixels$npcNotPotionInvisible(LivingEntity entity) {
        if (isNpc(entity)) {
            return false;
        }
        return entity.isInvisible();
    }

    @Redirect(
            method = "lambda$findTargetInFront$4",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;isPickable()Z",
                    remap = true
            )
    )
    private static boolean xenopixels$npcPickable(LivingEntity entity) {
        return entity.isPickable() || isNpc(entity);
    }

    @Redirect(
            method = "findTargetInFront",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getBoundingBox()Lnet/minecraft/world/phys/AABB;",
                    remap = true
            )
    )
    private static AABB xenopixels$npcLockBox(LivingEntity entity) {
        AABB box = entity.getBoundingBox();
        if (isNpc(entity) && box.getXsize() < 0.2) {
            return box.inflate(0.4, 0.0, 0.4);
        }
        return box;
    }

    @ModifyVariable(method = "findTargetInFront", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static double xenopixels$lockScanRange(double range) {
        return Math.max(range, NpcKiAim.LOCK_RANGE);
    }

    @ModifyConstant(
            method = {"lambda$toggleLock$2", "lambda$onClientTick$3"},
            constant = @Constant(doubleValue = 15.0)
    )
    private static double xenopixels$lockRangeBase(double original) {
        return NpcKiAim.LOCK_RANGE;
    }

    @ModifyConstant(method = "lambda$static$0", constant = @Constant(doubleValue = 24.0))
    private static double xenopixels$lockIconSpinRange(double original) {
        return NpcKiAim.LOCK_RANGE;
    }

    private static boolean isNpc(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        String name = entity.getClass().getName();
        return name.startsWith("noppes.npcs.entity.") || name.startsWith("espi.mynpcs.entity.");
    }
}

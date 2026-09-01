package net.bullettrain.xenopixelsmod.mixin.compat.cosmonautics;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bullettrain.xenopixelsmod.compat.cosmonautics.CosmoWarpSections;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Target: Cosmonautics {@code SubLevelWarper.WarpSubLevels}
 *         (javap of cosmonautics-26.08.307).
 * Reason: warp used a world AABB that missed seated players, then
 *         {@code TeleportEntity} {@code unRide}s them. Also wrap template
 *         load so one palette/section bug cannot kill the dedicated server.
 * Version: 26.08.307. {@code getEntities(Entity,AABB)} then {@code SubLevelTemplate.load}.
 * Side: server. Gated on rocketnautics.
 */
@Mixin(targets = "dev.egg.SubLevelWarper", remap = false)
public abstract class CosmoSubLevelWarperMixin {

    @Redirect(
            method = "WarpSubLevels",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;",
                    remap = true
            ),
            require = 0
    )
    private static List<Entity> xenopixels$includeSitters(ServerLevel level, Entity except, AABB box) {
        return CosmoWarpSections.collectWarpEntities(level, except, box);
    }

    @WrapOperation(
            method = "WarpSubLevels",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/egg/SubLevelTemplate;load(Ldev/ryanhcode/sable/sublevel/plot/ServerLevelPlot;Lnet/minecraft/nbt/CompoundTag;Ldev/egg/registries/BlockEntityRegistry$MoveInfo;)V"
            ),
            require = 0
    )
    private static void xenopixels$safeLoad(Object plot, Object tag, Object moveInfo, Operation<Void> original) {
        try {
            original.call(plot, tag, moveInfo);
        } catch (Throwable t) {
            CosmoWarpSections.logLoadFailure(t);
        }
    }
}

package net.bullettrain.xenopixelsmod.mixin.compat.sable;

import net.bullettrain.xenopixelsmod.compat.sable.SableContraptionCull;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.IdentityHashMap;
import java.util.function.Consumer;

/**
 * Target: Sable {@code SubLevelInclusiveLevelEntityGetter} (common source,
 *         {@code sable-fork/.../SubLevelInclusiveLevelEntityGetter.java}).
 * Reason: every client {@code getEntities*} pose-transforms the box and then
 *         walks every intersecting ship plot. A big hull turns one Create
 *         {@code getEntitiesOfClass} into 1 + 1 + N lookups.
 * Version: Sable 2.0.3. {@code getIgnoringSubLevels} is public on that class.
 * Side: common. Sable gated. Ships still collide via {@code SubLevelEntityCollision}.
 *
 * <p>The same getter also walks the world box, then the ship-pose-transformed box, then every
 * other intersecting plot. Those AABBs overlap for anyone standing on a hull, so the
 * consumer is invoked twice with the <i>same</i> entity instance. DragonMineZ ki blasts and
 * GeckoLib mobs show that as a duplicated entity. The {@code @ModifyVariable} wrappers
 * drop the second sighting.
 */
@Mixin(targets = "dev.ryanhcode.sable.util.SubLevelInclusiveLevelEntityGetter", remap = false)
public abstract class SableEntityGetterCullMixin {

    @Shadow
    @Final
    private Level level;

    @Shadow
    public abstract void getIgnoringSubLevels(AABB box, Consumer<?> consumer);

    @Shadow
    public abstract void getIgnoringSubLevels(EntityTypeTest<?, ?> test, AABB box,
                                              AbortableIterationConsumer<?> consumer);

    @ModifyVariable(
            method = "get(Lnet/minecraft/world/phys/AABB;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private Consumer<?> xenopixels$dedupeConsumer(Consumer<?> consumer) {
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        return entity -> {
            if (seen.put(entity, Boolean.TRUE) != null) return;
            @SuppressWarnings("unchecked")
            Consumer<Object> raw = (Consumer<Object>) consumer;
            raw.accept(entity);
        };
    }

    @ModifyVariable(
            method = "get(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)V",
            at = @At("HEAD"),
            argsOnly = true,
            require = 0
    )
    private AbortableIterationConsumer<?> xenopixels$dedupeTypedConsumer(AbortableIterationConsumer<?> consumer) {
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        return entity -> {
            if (seen.put(entity, Boolean.TRUE) != null) {
                return AbortableIterationConsumer.Continuation.CONTINUE;
            }
            @SuppressWarnings("unchecked")
            AbortableIterationConsumer<Object> raw = (AbortableIterationConsumer<Object>) consumer;
            return raw.accept(entity);
        };
    }

    @Inject(
            method = "get(Lnet/minecraft/world/phys/AABB;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void xenopixels$skipHugeFanOut(AABB box, Consumer<?> consumer, CallbackInfo ci) {
        if (!SableContraptionCull.shouldSkipSableFanOut(this.level, box)) return;
        this.getIgnoringSubLevels(box, consumer);
        ci.cancel();
    }

    @Inject(
            method = "get(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void xenopixels$skipHugeTypedFanOut(EntityTypeTest<?, ?> test, AABB box,
                                                AbortableIterationConsumer<?> consumer, CallbackInfo ci) {
        if (!SableContraptionCull.shouldSkipSableFanOut(this.level, box)) return;
        this.getIgnoringSubLevels(test, box, consumer);
        ci.cancel();
    }
}

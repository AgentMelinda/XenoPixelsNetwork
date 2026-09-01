package net.bullettrain.xenopixelsmod.mixin.compat.sable;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionCollider;
import net.bullettrain.xenopixelsmod.compat.sable.SableContraptionCull;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.Predicate;

/**
 * Target: {@code ContraptionCollider#collideEntities}
 * Reason: Create 6 scans with {@code Level.getEntitiesOfClass(Class, AABB, Predicate)}
 *         (javap of create-1.21.1-6.0.10). Older {@code getEntities(Entity, AABB)}
 *         redirects matched nothing.
 * Version: NeoForge 1.21.1 / Create 6.0.10 / Sable 2.0.3
 * Side: common. Create+Sable gated.
 */
@Mixin(value = ContraptionCollider.class, remap = false)
public abstract class SableContraptionColliderMixin {

    @Redirect(
            method = "collideEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
                    remap = true
            ),
            require = 0
    )
    private static <T extends Entity> List<T> xenopixels$clampEntityClassQuery(
            Level level, Class<T> type, AABB box, Predicate<? super T> predicate,
            AbstractContraptionEntity contraption) {
        AABB clamped = SableContraptionCull.clampQuery(contraption, box);
        SableContraptionCull.noteCreateScan(clamped);
        return level.getEntitiesOfClass(type, clamped, predicate);
    }
}

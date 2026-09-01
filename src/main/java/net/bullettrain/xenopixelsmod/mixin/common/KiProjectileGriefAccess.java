package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.UUID;

@Mixin(value = AbstractKiProjectile.class, remap = false)
public interface KiProjectileGriefAccess {
    @Invoker("destroyKiBlock")
    boolean xenopixels$destroyKiBlock(BlockPos pos, boolean dropBlock);

    @Invoker("scaledDestructionRadius")
    float xenopixels$scaledDestructionRadius(float baseRadius);

    @Accessor("cachedOwnerUUID")
    UUID xenopixels$cachedOwnerUUID();
}

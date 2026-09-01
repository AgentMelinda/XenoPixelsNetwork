package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.KiLaserEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = KiLaserEntity.class, remap = false)
public interface KiLaserAccess {
    @Invoker("setBeamLength")
    void xenopixels$setBeamLength(float length);

    @Invoker("damageEntitiesInBeam")
    void xenopixels$damageEntitiesInBeam(Vec3 start, Vec3 dir, float length);
}

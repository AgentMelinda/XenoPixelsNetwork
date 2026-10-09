package net.bullettrain.xenopixelsmod.mixin.client;

import net.bullettrain.xenopixelsmod.client.camera.UltimateFinisherCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** MC 1.21.1 Camera.setup tail: apply the caster's cinematic after native offsets. Client only. */
@Mixin(value = Camera.class, priority = 900)
public abstract class UltimateFinisherCameraMixin {
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void setRotation(float yaw, float pitch, float roll);

    @Inject(method = "setup", at = @At("TAIL"))
    private void xenopixels$ultimateShot(BlockGetter level, Entity entity, boolean detached,
                                         boolean reverse, float partialTick, CallbackInfo ci) {
        UltimateFinisherCamera.Shot shot = UltimateFinisherCamera.shot(partialTick);
        if (shot != null && entity == net.minecraft.client.Minecraft.getInstance().player) {
            setPosition(shot.position());
            setRotation(shot.yaw(), shot.pitch(), 0);
        } else if (entity == net.minecraft.client.Minecraft.getInstance().player) {
            var v3 = net.bullettrain.xenopixelsmod.client.camera.V3TechniqueCamera.shot(partialTick);
            if (v3 != null) {
                setPosition(v3.position());
                setRotation(v3.yaw(), v3.pitch(), 0);
            }
        }
    }
}

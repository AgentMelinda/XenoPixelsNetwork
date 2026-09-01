package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.client.events.EffectsEvents;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Target: {@code EffectsEvents#onCameraSetup}
 * Reason: DMZ adds a fresh {@code nextFloat()} yaw/pitch/roll offset every render frame for
 *         any {@code KiBlastEntity} (always treated as firing) or firing wave inside 50 blocks.
 *         A barrage is dozens of blasts; a surged beam lives for seconds. The result is a
 *         continuous jitter, not a hit kick. Xeno already has decaying impact shake in
 *         {@code CombatFxClient}.
 * Version: NeoForge 1.21.1 / DMZ 2.1.x
 * Side: client. One class check; the NPC / stagger / form-charge paths are left alone.
 */
@Mixin(value = EffectsEvents.class, remap = false)
public abstract class DmzKiCameraShakeMixin {

    @Redirect(
            method = "onCameraSetup",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;",
                    remap = true
            )
    )
    private static List<?> xenopixels$noAmbientKiShake(Level level, Class<?> type, AABB box) {
        if (AbstractKiProjectile.class.isAssignableFrom(type)) {
            return List.of();
        }
        return level.getEntitiesOfClass(unchecked(type), box);
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> unchecked(Class<?> type) {
        return (Class<T>) type;
    }
}

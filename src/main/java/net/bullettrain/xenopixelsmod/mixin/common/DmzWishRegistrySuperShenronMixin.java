package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.wish.DragonWishRegistry;
import com.google.gson.JsonElement;
import net.bullettrain.xenopixelsmod.compat.dmz.SuperShenronDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Adds the Super Shenron wish list after DMZ builds its server wishes. WishManager.loadWishes starts from this map,
 * so the list also survives the world-config merge; DMZ's datapack sync carries it to clients.
 */
@Mixin(value = DragonWishRegistry.class, remap = false)
public abstract class DmzWishRegistrySuperShenronMixin {
    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("TAIL"))
    private void xenopixels$addSuperShenronWishes(Map<ResourceLocation, JsonElement> resources, ResourceManager manager,
                                                  ProfilerFiller profiler, CallbackInfo callbackInfo) {
        var current = DragonWishRegistry.getServerWishes();
        var merged = SuperShenronDefinition.withWishes(current);
        if (merged != current) DragonWishRegistry.setServerWishes(merged);
    }
}

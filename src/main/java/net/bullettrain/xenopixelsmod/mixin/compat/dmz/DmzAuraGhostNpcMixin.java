package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcFullDmzRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Full DMZ NPCs share the CustomNPC entity id with their synthetic player proxy.
 * {@code AuraRenderer.processGhostAuras} then looks that id up, sees a non-Player
 * NPC, and evicts {@code AURA_CACHE} — the next visible frame restarts the ~3s fade.
 *
 * <p>When the looked-up entity is a Full aura-on NPC, hand back the proxy so DMZ
 * treats it as a still-active player. <b>Written for:</b> Minecraft 1.21.1 /
 * NeoForge 21.1.248, DragonMineZ 2.1.3, MixinExtras 0.5.3. <b>Side:</b> client.
 */
@Mixin(targets = "com.dragonminez.client.render.effects.AuraRenderer", remap = false)
public abstract class DmzAuraGhostNpcMixin {

    @WrapOperation(
            method = "processGhostAuras(Lnet/minecraft/client/Minecraft;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FLjava/util/Set;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;getEntity(I)"
                            + "Lnet/minecraft/world/entity/Entity;"
            ),
            require = 0
    )
    private static Entity xenopixels$keepFullNpcAura(ClientLevel level, int entityId,
                                                     Operation<Entity> original) {
        Entity entity = original.call(level, entityId);
        Player proxy = NpcFullDmzRenderer.auraPlayerForEntity(entity);
        return proxy != null ? proxy : entity;
    }
}

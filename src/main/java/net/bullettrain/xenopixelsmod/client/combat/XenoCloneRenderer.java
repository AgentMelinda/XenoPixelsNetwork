package net.bullettrain.xenopixelsmod.client.combat;

import net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Draws a copy with the fighter's own skin on a vanilla player model.
 *
 * <p><b>What this deliberately no longer does.</b> The previous version teleported the fighter's
 * live player entity to each copy's position for a single draw call and put it back. DragonMineZ's
 * aura pass is deferred — it runs later in the same frame and reads the entity again — so it saw a
 * position that had been mutated and restored underneath it, and painted aura geometry between the
 * two. On screen that was pink streaking across the sky and the hotbar, flickering copies, and the
 * fighter's own aura breaking the moment they divided. Rendering one entity as several was the
 * mistake; it also could never give a copy its own aura, because that queue de-duplicates by
 * entity id.
 *
 * <p>This draws the copy as itself, touching nothing else in the frame. It costs the DragonMineZ
 * appearance — no hair, form or aura — which is the honest trade until each copy carries its own
 * proxy identity the way {@code NpcFullDmzRenderer} gives one to every NPC.
 */
public class XenoCloneRenderer extends LivingEntityRenderer<XenoCloneEntity, PlayerModel<XenoCloneEntity>> {

    private static final ResourceLocation FALLBACK =
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    public XenoCloneRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public void render(XenoCloneEntity clone, float entityYaw, float partialTick,
                       com.mojang.blaze3d.vertex.PoseStack pose,
                       net.minecraft.client.renderer.MultiBufferSource buffers, int packedLight) {
        // The fighter's real DragonMineZ body, drawn through their own renderer against a proxy
        // identity of this copy's own. Falls straight back to the vanilla model below if the
        // bridge declines, so a copy is never invisible and the frame is never at risk.
        if (net.bullettrain.xenopixelsmod.client.config.XenoClientConfig.cloneDmzAppearance) {
            Minecraft mc = Minecraft.getInstance();
            Entity owner = mc.level == null ? null : mc.level.getEntity(clone.ownerId());
            if (owner instanceof net.minecraft.world.entity.player.Player player
                    && net.bullettrain.xenopixelsmod.client.compat.npc.NpcFullDmzRenderer
                            .renderPlayerCopy(player, clone, entityYaw, partialTick, pose,
                                    buffers, packedLight)) {
                return;
            }
        }
        super.render(clone, entityYaw, partialTick, pose, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(XenoCloneEntity clone) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return FALLBACK;
        Entity owner = mc.level.getEntity(clone.ownerId());
        // A copy wears the fighter's face. Their skin is the one thing that can be borrowed
        // without reaching into anyone else's renderer.
        return owner instanceof AbstractClientPlayer player ? player.getSkin().texture() : FALLBACK;
    }
}

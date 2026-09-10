package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import net.bullettrain.xenopixelsmod.compat.npc.NpcKiAim;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.item.ItemStack;
import espi.mynpcs.client.renderer.RenderCustomNpc;
import espi.mynpcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Raises an NPC's right arm to an aiming pose while it is mid ki-cast
 * ({@link NpcKiAim#lockedTarget}), purely at render time. CustomNPCs' own arm-raise pose
 * ({@code AnimationType.AIM}) only actually renders when both {@code stats.ranged
 * .getHasAimAnimation()} and the synced {@code isAttacking()} flag are true, and forcing
 * {@code isAttacking()} has side effects elsewhere in {@code EntityNPCInterface} (health regen
 * suppression, faction attack-state, interaction results) -- so this bypasses that system
 * entirely rather than fighting it, redirecting only the main-hand {@code getPose} call
 * {@code RenderCustomNpc.render} already makes to set {@code rightArmPose}.
 *
 * <p>The My NPCs twin of the CustomNPCs mixin of the same name. My NPCs is CustomNPCs with
 * its root package renamed, so the two are identical but for the types they name; this one
 * is gated on the {@code mynpcs} mod id and its twin on {@code customnpcs}, so exactly one
 * applies. Fix bugs in both.
 */
@Mixin(value = RenderCustomNpc.class, remap = false)
public abstract class RenderCustomNpcKiPoseMixin {
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Redirect(
            method = "render(Lespi/mynpcs/entity/EntityCustomNpc;FFLcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lespi/mynpcs/client/renderer/RenderCustomNpc;getPose(Lespi/mynpcs/entity/EntityCustomNpc;"
                            + "Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
                    ordinal = 0),
            require = 1
    )
    private HumanoidModel.ArmPose xenopixels$forceKiAimPose(RenderCustomNpc renderer, EntityCustomNpc npc, ItemStack item) {
        if (NpcKiAim.lockedTarget(npc) != null) {
            return HumanoidModel.ArmPose.BOW_AND_ARROW;
        }
        return renderer.getPose(npc, item);
    }
}

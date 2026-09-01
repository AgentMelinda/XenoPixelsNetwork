package net.bullettrain.xenopixelsmod.mixin.compat.customnpcs;

import net.bullettrain.xenopixelsmod.compat.npc.NpcKiAim;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.client.renderer.RenderCustomNpc;
import noppes.npcs.entity.EntityCustomNpc;
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
 */
@Mixin(value = RenderCustomNpc.class, remap = false)
public abstract class RenderCustomNpcKiPoseMixin {
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Redirect(
            method = "render(Lnoppes/npcs/entity/EntityCustomNpc;FFLcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnoppes/npcs/client/renderer/RenderCustomNpc;getPose(Lnoppes/npcs/entity/EntityCustomNpc;"
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

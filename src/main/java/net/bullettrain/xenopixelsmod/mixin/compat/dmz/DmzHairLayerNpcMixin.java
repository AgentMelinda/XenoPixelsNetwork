package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.common.hair.HairManager;
import com.dragonminez.common.stats.character.Character;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcFullDmzRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets an NPC wear the hair a builder gave it, whatever race it is.
 *
 * <p>{@code HairManager.canUseHair} allows custom hair only for {@code human} and {@code saiyan}
 * (its {@code DEFAULT_HAIR_RACES}) plus {@code majin} + {@code female}. That is a rule about what a
 * <em>player</em> may pick for their own character during creation. An NPC is not choosing anything
 * — a builder already chose for it — so applying the rule there meant a Namekian or Frieza-race NPC
 * silently rendered with no hair at all in FULL appearance mode.
 *
 * <p>It only bit FULL mode because the two appearance modes draw hair through different code.
 * OVERLAY draws it in this mod ({@code NpcHairVis.render}) and never consults DragonMineZ's gate;
 * FULL hands the character to DragonMineZ and {@code DMZHairLayer} does the drawing, gate included.
 *
 * <p>Scoped to the synthetic NPC character being rendered right now, so a real player's hair keeps
 * DragonMineZ's own rules exactly. {@code isRenderingCharacter} compares by identity against the
 * character the NPC renderer set up, and is only non-null for the duration of that render.
 */
@Mixin(targets = "com.dragonminez.client.render.layer.DMZHairLayer", remap = false)
public abstract class DmzHairLayerNpcMixin {

    @Redirect(
            method = "renderHair(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/player/AbstractClientPlayer;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;FII)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/dragonminez/common/hair/HairManager;canUseHair("
                            + "Lcom/dragonminez/common/stats/character/Character;)Z"),
            require = 0)
    private boolean xenopixels$allowNpcHair(Character character) {
        if (NpcFullDmzRenderer.isRenderingCharacter(character)) return true;
        return HairManager.canUseHair(character);
    }
}

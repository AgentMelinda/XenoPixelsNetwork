package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.techniques.Techniques;
import com.mojang.blaze3d.platform.InputConstants;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.UnbindTechniqueSlotPacket;
import net.minecraft.client.gui.components.Button;
import com.dragonminez.client.gui.character.util.ScaledScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds the unbind DragonMineZ's binding mode never had.
 *
 * <p>Declared {@code extends ScaledScreen} rather than {@code Screen} on purpose. The target's
 * real ancestry is {@code SkillsMenuScreen -> BaseMenuScreen -> ScaledScreen -> Screen}, and
 * {@code getUiWidth}/{@code getUiHeight} live on {@code ScaledScreen}. Declaring the shallower
 * superclass and reaching for them with {@code @Shadow} threw
 * {@code InvalidMixinException: @Shadow method getUiWidth ... was not located in the target class}
 * and took the client down the moment anything loaded the skills menu — {@code @Shadow} resolves
 * against the target class alone and does not walk the hierarchy. Matching the real ancestry makes
 * them ordinary inherited methods and needs no shadow at all.
 *
 * <p>DMZ draws eight numbered slot buttons and no way to empty one. Its own
 * {@code Techniques.equipOrSwapTechnique} cannot express it either: an empty id is read as
 * "already equipped", so it finds the first vacant slot and swaps, moving the technique instead of
 * removing it. Both the button and the Delete shortcut therefore go through
 * {@link UnbindTechniqueSlotPacket}, which writes the slot directly.
 */
@Mixin(targets = "com.dragonminez.client.gui.character.SkillsMenuScreen")
public abstract class DmzTechniqueUnbindMixin extends ScaledScreen {
    @Shadow private StatsData statsData;
    @Shadow private String selectedSkill;
    @Shadow private boolean isBinding;

    protected DmzTechniqueUnbindMixin() {
        super(null);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void xenopixels$unbindKey(int keyCode, int scanCode, int modifiers,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (keyCode != InputConstants.KEY_DELETE) return;
        if (xenopixels$unbindSelected()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "initBindButtons", at = @At("RETURN"))
    private void xenopixels$addUnbindButton(CallbackInfo ci) {
        if (!isBinding || selectedSkill == null || statsData == null) return;
        if (xenopixels$slotsHolding().length == 0) return;

        // Under DMZ's two rows of slot buttons, which sit at rightPanelY + 175 and + 186.
        int rightPanelX = getUiWidth() - 158;
        int rightPanelY = getUiHeight() / 2 - 105;
        addRenderableWidget(Button.builder(
                        Component.literal("Unbind"),
                        btn -> xenopixels$unbindSelected())
                .bounds(rightPanelX + 50, rightPanelY + 198, 46, 12)
                .build());
    }

    private int[] xenopixels$slotsHolding() {
        if (statsData == null || selectedSkill == null) return new int[0];
        Techniques techniques = statsData.getTechniques();
        String[] slots = techniques == null ? null : techniques.getEquippedSlots();
        if (slots == null) return new int[0];
        int[] found = new int[slots.length];
        int count = 0;
        for (int slot = 0; slot < slots.length; slot++) {
            if (selectedSkill.equals(slots[slot])) {
                found[count++] = slot;
            }
        }
        int[] trimmed = new int[count];
        System.arraycopy(found, 0, trimmed, 0, count);
        return trimmed;
    }

    private boolean xenopixels$unbindSelected() {
        int[] slots = xenopixels$slotsHolding();
        for (int slot : slots) {
            ModNetwork.CHANNEL.sendToServer(new UnbindTechniqueSlotPacket(slot));
        }
        return slots.length > 0;
    }
}

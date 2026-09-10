package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.client.gui.character.SkillsMenuScreen;
import com.dragonminez.client.gui.character.util.ScaledScreen;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bullettrain.xenopixelsmod.client.config.XenoHudConfig;
import net.bullettrain.xenopixelsmod.client.screen.DmzSkillsHitboxes;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Objects;

/** Corrects themed skills-list hitboxes and makes its selection state visually unambiguous. */
@Mixin(value = SkillsMenuScreen.class, remap = false)
public abstract class DmzSkillsInteractionMixin extends ScaledScreen {

    @Shadow private int currentLeftX;
    @Shadow private float currentScroll;
    @Shadow private String selectedSkill;
    @Shadow
    private List<String> getVisibleSkillNames() {
        throw new AssertionError();
    }

    protected DmzSkillsInteractionMixin() {
        super(null);
    }

    @ModifyConstant(
            method = {"renderSkillsList", "mouseClicked"},
            constant = @Constant(intValue = 100),
            require = 2
    )
    private int xenopixels$useFullVisibleSkillRow(int original) {
        return DmzSkillsHitboxes.rowRightOffset(XenoHudConfig.dmzMenusThemed(), original);
    }

    @WrapOperation(
            method = "mouseClicked",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/dragonminez/client/gui/character/util/BaseMenuScreen;mouseClicked(DDI)Z"
            )
    )
    private boolean xenopixels$ignoreClippedTabClicks(SkillsMenuScreen screen, double mouseX,
                                                       double mouseY, int button,
                                                       Operation<Boolean> original) {
        int panelY = getUiHeight() / 2 - 105;
        if (DmzSkillsHitboxes.insidePanel(XenoHudConfig.dmzMenusThemed(),
                toUiX(mouseX), toUiY(mouseY), currentLeftX, panelY)) {
            return false;
        }
        return original.call(screen, mouseX, mouseY, button);
    }

    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/dragonminez/client/gui/character/util/BaseMenuScreen;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
            )
    )
    private void xenopixels$ignoreClippedTabHover(SkillsMenuScreen screen, GuiGraphics graphics,
                                                   int mouseX, int mouseY, float partialTick,
                                                   Operation<Void> original) {
        int panelY = getUiHeight() / 2 - 105;
        if (DmzSkillsHitboxes.insidePanel(XenoHudConfig.dmzMenusThemed(),
                mouseX, mouseY, currentLeftX, panelY)) {
            original.call(screen, graphics, Integer.MIN_VALUE, Integer.MIN_VALUE, partialTick);
            return;
        }
        original.call(screen, graphics, mouseX, mouseY, partialTick);
    }

    @Inject(
            method = "renderSkillsList",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;enableScissor(IIII)V",
                    shift = At.Shift.AFTER
            )
    )
    private void xenopixels$renderSkillRowFeedback(GuiGraphics graphics, int panelX, int panelY,
                                                    int mouseX, int mouseY, CallbackInfo ci) {
        if (!XenoHudConfig.dmzMenusThemed()) return;
        List<String> skills = getVisibleSkillNames();
        int startY = panelY + 30;
        int viewBottom = startY + 160;
        int left = panelX + DmzSkillsHitboxes.ROW_LEFT;
        int right = panelX + DmzSkillsHitboxes.ROW_RIGHT_THEMED;
        for (int index = 0; index < skills.size(); index++) {
            int top = Math.round(startY + index * 20 - currentScroll);
            int bottom = top + 20;
            if (bottom <= startY || top >= viewBottom) continue;
            boolean selected = Objects.equals(skills.get(index), selectedSkill);
            boolean hovered = DmzSkillsHitboxes.insideRowX(mouseX, panelX)
                    && mouseY >= top && mouseY < bottom;
            if (selected) {
                graphics.fill(left, top + 1, right, bottom - 1, 0x553B2C00);
                graphics.fill(left, top + 1, left + 2, bottom - 1, 0xFFFECC22);
            } else if (hovered) {
                graphics.fill(left, top + 1, right, bottom - 1, 0x3038D8FF);
                graphics.fill(left, top + 1, left + 2, bottom - 1, 0xFF38D8FF);
            }
            if (selected && hovered) {
                graphics.fill(left, top + 1, right, top + 2, 0xFF38D8FF);
                graphics.fill(left, bottom - 2, right, bottom - 1, 0xFF38D8FF);
            }
        }
    }
}

package net.bullettrain.xenopixelsmod.mixin.compat.customnpcs;

import java.util.Arrays;
import net.bullettrain.xenopixelsmod.client.compat.npc.gui.GuiNpcDmzSkillMaster;
import net.bullettrain.xenopixelsmod.dmz.form.DmzSkillMaster;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import noppes.npcs.client.gui.mainmenu.GuiNpcAdvanced;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataAdvanced;
import noppes.npcs.shared.client.gui.components.GuiButtonBiDirectional;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Appends a XenoPixels skill-master entry to the native CustomNPCs role picker.
 *
 * <p>{@code GuiNpcAdvanced} builds that picker from a fixed eight-label array, so the sentinel role
 * id has no position a player can reach, and {@code DataAdvanced.setRole} would normalize any value
 * it was handed back into the native {@code 0..7} range. Both problems are fixed here without
 * duplicating the mod's own list: the constructor call is redirected so the stock labels are kept
 * and one is appended, and the native {@code setRole} argument is translated back to the raw
 * sentinel.
 *
 * <p>Native positions, and the native save/packet sequence in {@code buttonEvent}, are left
 * untouched, so the picker behaves exactly like the stock one for every role that is not a skill
 * master.
 */
@Mixin(value = GuiNpcAdvanced.class, remap = false)
public abstract class GuiNpcAdvancedRoleMixin {

    /** Widget id CustomNPCs gives its role picker inside {@code GuiNpcAdvanced}. */
    @Unique
    private static final int XENOPIXELS_ROLE_BUTTON = 8;

    /** Widget id of the role Edit button ({@code selectServer.edit}). */
    @Unique
    private static final int XENOPIXELS_ROLE_EDIT = 3;

    /** Stock entry count of the picker that was just built, captured for the click path. */
    @Unique
    private int xenopixels$nativeRoleCount;

    /**
     * Extends the role picker's label array and shows the appended position for a stored sentinel.
     *
     * <p>The {@code NEW} redirect fires for every {@code GuiButtonBiDirectional} the screen builds,
     * including the job picker, so anything that is not the role button is returned unchanged.
     */
    @Redirect(method = "m_7856_", require = 1, at = @At(value = "NEW",
            target = "noppes/npcs/shared/client/gui/components/GuiButtonBiDirectional"))
    private GuiButtonBiDirectional xenopixels$extendRolePicker(IGuiInterface gui, int id, int x, int y,
                                                              int width, int height, String[] display,
                                                              int value) {
        if (id != XENOPIXELS_ROLE_BUTTON) {
            return new GuiButtonBiDirectional(gui, id, x, y, width, height, display, value);
        }
        xenopixels$nativeRoleCount = display.length;
        String[] extended = Arrays.copyOf(display, display.length + 1);
        extended[display.length] = I18n.get(DmzSkillMaster.SELECTOR_LABEL_KEY);
        EntityNPCInterface npc = xenopixels$npc();
        int stored = npc != null && npc.role != null ? npc.role.getType() : value;
        int shown = DmzSkillMaster.shownSelectorIndex(stored, value, display.length);
        return new GuiButtonBiDirectional(gui, id, x, y, width, height, extended, shown);
    }

    /**
     * Opens the skill-master editor when Edit is pressed on a sentinel role.
     *
     * <p>CustomNPCs enables that button for every type other than None and mailman, then walks
     * {@code getType()} into native {@code GuiRole*} constructors. Sentinel {@code 9001} matches
     * none of those branches, so the click was a no-op.
     */
    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true, require = 1)
    private void xenopixels$openSkillMasterEditor(GuiButtonNop button, CallbackInfo ci) {
        if (button == null || button.id != XENOPIXELS_ROLE_EDIT) return;
        EntityNPCInterface npc = xenopixels$npc();
        if (npc == null || npc.role == null || !DmzSkillMaster.isSentinel(npc.role.getType())) {
            return;
        }
        Minecraft.getInstance().setScreen(new GuiNpcDmzSkillMaster(npc));
        ci.cancel();
    }

    /**
     * Replaces the normalized native role id with the raw sentinel when the appended entry is picked.
     *
     * <p>Redirecting the call rather than cancelling the handler keeps CustomNPCs' own save and
     * {@code SPacketNpcRoleGet} sequence, and the button-enabled bookkeeping that follows it,
     * running exactly as written.
     */
    @Redirect(method = "buttonEvent", require = 1, at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/data/DataAdvanced;setRole(I)V"))
    private void xenopixels$mapSelectorToRoleId(DataAdvanced advanced, int selectorIndex) {
        advanced.setRole(DmzSkillMaster.roleIdForSelector(selectorIndex, xenopixels$nativeRoleCount));
    }

    /**
     * {@code npc} lives on {@code GuiNPCInterface}, not on this target. Mixin @Shadow of that
     * inherited field failed to apply on the MyNPCs twin (2026-09-11: "was not located in the
     * target class") and dropped the whole mixin, so the role vanished from the picker.
     */
    @Unique
    private EntityNPCInterface xenopixels$npc() {
        return ((GuiNpcAdvanced) (Object) this).npc;
    }
}
package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import java.util.Arrays;
import espi.mynpcs.client.gui.mainmenu.GuiNpcAdvanced;
import espi.mynpcs.entity.EntityNPCInterface;
import espi.mynpcs.shared.client.gui.components.GuiButtonBiDirectional;
import espi.mynpcs.shared.client.gui.components.GuiButtonNop;
import espi.mynpcs.shared.client.gui.listeners.IGuiInterface;
import net.bullettrain.xenopixelsmod.client.compat.npc.mynpcs.gui.GuiNpcDmzSkillMaster;
import net.bullettrain.xenopixelsmod.dmz.form.DmzSkillMaster;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * My NPCs twin of {@code mixin.compat.customnpcs.GuiNpcAdvancedRoleMixin}.
 *
 * <p>The shape differs from CustomNPCs in two ways that matter. My NPCs resolves a picker position
 * through a static {@code ROLE_TYPE_IDS} table rather than treating the position as the role id,
 * and it does not normalize unknown ids, so reading past the end of that table would throw instead
 * of silently aliasing a native role. The picker is therefore extended on both ends: one label is
 * appended for display, and the id table is replaced by a copy with the sentinel in the new slot
 * so the native {@code setRole} call, and everything after it, still runs.
 */
@Mixin(value = GuiNpcAdvanced.class, remap = false)
public abstract class GuiNpcAdvancedRoleMixin {

    /** Widget id MyNPCs gives its role picker inside {@code GuiNpcAdvanced}. */
    @Unique
    private static final int XENOPIXELS_ROLE_BUTTON = 8;

    /** Widget id of the role Edit button. Sends {@code SPacketNpcRoleGet} for native roles. */
    @Unique
    private static final int XENOPIXELS_ROLE_EDIT = 3;

    @Shadow
    @Final
    private static int[] ROLE_TYPE_IDS;

    /** The stock id table plus the sentinel in the appended slot, or null before the screen opens. */
    @Unique
    private static int[] xenopixels$roleIds;

    /**
     * Extends the role picker's label array and builds the matching id table.
     *
     * <p>The {@code NEW} redirect fires for every {@code GuiButtonBiDirectional} the screen builds,
     * including the job picker, so anything that is not the role button is returned unchanged.
     */
    @Redirect(method = "init", require = 1, at = @At(value = "NEW",
            target = "espi/mynpcs/shared/client/gui/components/GuiButtonBiDirectional"))
    private GuiButtonBiDirectional xenopixels$extendRolePicker(IGuiInterface gui, int id, int x, int y,
                                                              int width, int height, String[] display,
                                                              int value) {
        if (id != XENOPIXELS_ROLE_BUTTON) {
            return new GuiButtonBiDirectional(gui, id, x, y, width, height, display, value);
        }
        int[] extendedIds = Arrays.copyOf(ROLE_TYPE_IDS, ROLE_TYPE_IDS.length + 1);
        extendedIds[ROLE_TYPE_IDS.length] = DmzSkillMaster.SENTINEL_ROLE_ID;
        xenopixels$roleIds = extendedIds;

        String[] extendedLabels = Arrays.copyOf(display, display.length + 1);
        extendedLabels[display.length] = I18n.get(DmzSkillMaster.SELECTOR_LABEL_KEY);
        EntityNPCInterface npc = xenopixels$npc();
        int stored = npc != null && npc.role != null ? npc.role.getType() : value;
        int shown = DmzSkillMaster.shownSelectorIndex(stored, value, display.length);
        return new GuiButtonBiDirectional(gui, id, x, y, width, height, extendedLabels, shown);
    }

    /**
     * Opens the skill-master editor. MyNPCs' Edit button only sends {@code SPacketNpcRoleGet};
     * the server has no handler for sentinel {@code 9001}, so the native path never opens a GUI.
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
     * Swaps the native id table for the extended copy so the appended position resolves.
     *
     * <p>Returning the extended table, rather than intercepting the click, means MyNPCs' own
     * {@code setRole} call, save packet, and button-enabled bookkeeping all still run. The fallback
     * covers the theoretical case of a click before {@code init} populated the copy.
     */
    @Redirect(method = "buttonEvent", require = 1, at = @At(value = "FIELD",
            opcode = Opcodes.GETSTATIC,
            target = "Lespi/mynpcs/client/gui/mainmenu/GuiNpcAdvanced;ROLE_TYPE_IDS:[I"))
    private int[] xenopixels$extendedRoleIds() {
        // Do not read ROLE_TYPE_IDS here: this handler *is* the GETSTATIC redirect, so a Shadow
        // read would recurse. init()'s NEW redirect always fills xenopixels$roleIds first.
        return xenopixels$roleIds == null ? new int[0] : xenopixels$roleIds;
    }

    /**
     * {@code npc} lives on {@code GuiNPCInterface}, not on this target. Mixin @Shadow of an
     * inherited field failed to apply (2026-09-11 log: "was not located in the target class"),
     * which dropped the whole mixin and the role vanished from the picker.
     */
    @Unique
    private EntityNPCInterface xenopixels$npc() {
        return ((GuiNpcAdvanced) (Object) this).npc;
    }
}
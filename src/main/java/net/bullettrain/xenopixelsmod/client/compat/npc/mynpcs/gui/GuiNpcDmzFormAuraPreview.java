package net.bullettrain.xenopixelsmod.client.compat.npc.mynpcs.gui;

import net.bullettrain.xenopixelsmod.client.compat.npc.gui.NpcPreviewOwner;

import net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient;
import net.bullettrain.xenopixelsmod.compat.npc.NpcAuraStyle;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormDocument;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormKind;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import espi.mynpcs.client.gui.util.GuiNPCInterface2;
import espi.mynpcs.entity.EntityNPCInterface;
import espi.mynpcs.shared.client.gui.components.GuiButtonNop;
import espi.mynpcs.shared.client.gui.components.GuiButtonYesNo;
import espi.mynpcs.shared.client.gui.components.GuiLabel;
import espi.mynpcs.shared.client.gui.components.GuiTextFieldNop;
import espi.mynpcs.shared.client.gui.listeners.ITextfieldListener;

/** Preview-only native aura details for one form-studio draft. */
public final class GuiNpcDmzFormAuraPreview extends GuiNPCInterface2 implements ITextfieldListener, NpcPreviewOwner {
    private static final int OVERRIDE = 1;
    private static final int PRIMARY_COLOR = 10;
    private static final int PRIMARY_TYPE = 11;
    private static final int PRIMARY_LAYER = 12;
    private static final int EXTRA_CONFIGURED = 20;
    private static final int EXTRA_ENABLED = 21;
    private static final int EXTRA_COLOR = 22;
    private static final int EXTRA_TYPE = 23;
    private static final int EXTRA_LAYER = 24;
    private static final int LIGHTNING_CONFIGURED = 30;
    private static final int LIGHTNING_ENABLED = 31;
    private static final int LIGHTNING_COLOR = 32;
    private static final int DONE = 40;
    private static final int RESET = 41;

    private final DmzFormDocument document;
    private final String sessionId;
    private final boolean previewing;
    private final NpcCombatProfile original;
    private NpcCombatProfile draft;

    public GuiNpcDmzFormAuraPreview(EntityNPCInterface npc, DmzFormDocument document,
                                    String sessionId, NpcCombatProfile source,
                                    boolean previewing) {
        super(npc, GuiNpcDmzMenuButton.MENU_ID);
        this.document = document;
        this.sessionId = sessionId;
        this.previewing = previewing;
        this.original = copy(source);
        this.draft = copy(source);
    }

    @Override
    public void init() {
        super.init();
        NpcAuraStyle style = style();
        int x = guiLeft + 10;
        int y = guiTop + 7;
        addLabel(new GuiLabel(100, "Preview Aura Style", x, y + 4, 0xFFD36A));
        addLabel(new GuiLabel(101, clip(document.group() + "/" + document.form(), 25),
                x + 130, y + 4, 0xFFE2C078));
        addButton(new GuiButtonYesNo(this, OVERRIDE, x + 316, y, 48, 18, style.enabled));
        addLabel(new GuiLabel(102, "Override", x + 367, y + 4, 0xFFFFFFFF));

        int left = x;
        int right = x + 205;
        addLabel(new GuiLabel(110, "Primary aura", left, y + 28, 0xFFD36A));
        field(PRIMARY_COLOR, "Color", left, y + 44, style.primaryColor);
        field(PRIMARY_TYPE, "Type", left, y + 64, style.primaryType);
        field(PRIMARY_LAYER, "Layer (-1 inherit)", left, y + 84,
                Integer.toString(style.primaryLayer));

        addLabel(new GuiLabel(120, "Secondary / extra aura", right, y + 28, 0xFFD36A));
        yesNo(EXTRA_CONFIGURED, "Custom", right, y + 44, style.extraConfigured);
        yesNo(EXTRA_ENABLED, "Visible", right + 100, y + 44, style.extraEnabled);
        field(EXTRA_COLOR, "Color", right, y + 64, style.extraColor);
        field(EXTRA_TYPE, "Type", right, y + 84, style.extraType);
        field(EXTRA_LAYER, "Layer", right, y + 104, Integer.toString(style.extraLayer));

        addLabel(new GuiLabel(130, "Lightning", left, y + 110, 0xFFD36A));
        yesNo(LIGHTNING_CONFIGURED, "Custom", left, y + 126, style.lightningConfigured);
        yesNo(LIGHTNING_ENABLED, "Visible", left + 100, y + 126, style.lightningEnabled);
        field(LIGHTNING_COLOR, "Color", left, y + 146, style.lightningColor);

        addLabel(new GuiLabel(140, "Preview only - form and NPC data are not changed",
                x, guiTop + 179, 0xFFAAAAAA));
        addButton(new GuiButtonNop(this, RESET, guiLeft + 300, guiTop + 174, 52, 18, "Reset"));
        addButton(new GuiButtonNop(this, DONE, guiLeft + 356, guiTop + 174, 56, 18, "Done"));
    }

    private void field(int id, String label, int x, int y, String value) {
        addLabel(new GuiLabel(id + 200, label, x, y + 3, 0xFFFFFFFF));
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, x + 88, y, 100, 16,
                value == null ? "" : value);
        field.setMaxLength(64);
        addTextField(field);
    }

    private void yesNo(int id, String label, int x, int y, boolean value) {
        addButton(new GuiButtonYesNo(this, id, x, y, 48, 16, value));
        addLabel(new GuiLabel(id + 300, label, x + 52, y + 3, 0xFFFFFFFF));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        pull();
        NpcAuraStyle style = style();
        if (button.id == OVERRIDE && button instanceof GuiButtonYesNo yes) {
            style.enabled = yes.getBoolean();
        } else if (button.id == EXTRA_CONFIGURED && button instanceof GuiButtonYesNo yes) {
            style.extraConfigured = yes.getBoolean();
        } else if (button.id == EXTRA_ENABLED && button instanceof GuiButtonYesNo yes) {
            style.extraEnabled = yes.getBoolean();
        } else if (button.id == LIGHTNING_CONFIGURED && button instanceof GuiButtonYesNo yes) {
            style.lightningConfigured = yes.getBoolean();
        } else if (button.id == LIGHTNING_ENABLED && button instanceof GuiButtonYesNo yes) {
            style.lightningEnabled = yes.getBoolean();
        } else if (button.id == RESET) {
            draft = copy(original);
            apply();
            init();
            return;
        } else if (button.id == DONE) {
            goBack(draft);
            return;
        }
        apply();
        init();
    }

    @Override
    public void unFocused(GuiTextFieldNop field) {
        pull();
        apply();
    }

    private void pull() {
        NpcAuraStyle style = style();
        style.primaryColor = colorText(PRIMARY_COLOR, style.primaryColor);
        style.primaryType = text(PRIMARY_TYPE, style.primaryType);
        style.primaryLayer = NpcAuraStyle.clampLayer(integer(PRIMARY_LAYER, style.primaryLayer));
        style.extraColor = colorText(EXTRA_COLOR, style.extraColor);
        style.extraType = text(EXTRA_TYPE, style.extraType);
        style.extraLayer = NpcAuraStyle.clampLayer(integer(EXTRA_LAYER, style.extraLayer));
        style.lightningColor = colorText(LIGHTNING_COLOR, style.lightningColor);
        GuiButtonNop override = getButton(OVERRIDE);
        if (override instanceof GuiButtonYesNo yes) style.enabled = yes.getBoolean();
        GuiButtonNop configured = getButton(EXTRA_CONFIGURED);
        if (configured instanceof GuiButtonYesNo yes) style.extraConfigured = yes.getBoolean();
        GuiButtonNop extra = getButton(EXTRA_ENABLED);
        if (extra instanceof GuiButtonYesNo yes) style.extraEnabled = yes.getBoolean();
        GuiButtonNop lightningConfigured = getButton(LIGHTNING_CONFIGURED);
        if (lightningConfigured instanceof GuiButtonYesNo yes) {
            style.lightningConfigured = yes.getBoolean();
        }
        GuiButtonNop lightning = getButton(LIGHTNING_ENABLED);
        if (lightning instanceof GuiButtonYesNo yes) style.lightningEnabled = yes.getBoolean();
    }

    private NpcAuraStyle style() {
        return draft.auraStyle(document.kind() == DmzFormKind.STACK,
                document.group(), document.form(), true);
    }

    private void apply() {
        NpcAppearanceClient.applyProfile(((Entity) npc).getUUID(), draft);
    }

    private void goBack(NpcCombatProfile profile) {
        Minecraft.getInstance().setScreen(new GuiNpcDmzFormEditor(npc, document,
                sessionId, profile, previewing));
    }

    private String text(int id, String fallback) {
        GuiTextFieldNop field = getTextField(id);
        return field == null || field.getValue() == null ? (fallback == null ? "" : fallback)
                : field.getValue().trim();
    }

    private int integer(int id, int fallback) {
        try {
            return Integer.parseInt(text(id, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String colorText(int id, String fallback) {
        String raw = text(id, fallback);
        if (raw.isBlank()) return "";
        return NpcCombatProfile.parseHexColor(raw).isPresent()
                ? NpcCombatProfile.formatHex(NpcCombatProfile.parseHexColor(raw).getAsInt())
                : fallback;
    }

    private static NpcCombatProfile copy(NpcCombatProfile source) {
        return source == null ? new NpcCombatProfile()
                : NpcCombatProfile.fromTag(source.toTag());
    }

    private static String clip(String value, int max) {
        if (value == null || value.isBlank()) return "-";
        return value.length() <= max ? value : value.substring(0, max);
    }

    @Override
    public void onClose() {
        goBack(draft);
    }

    @Override
    public void save() {
        // The parent form editor owns persistence; every value here is preview-only.
    }
}

package net.bullettrain.xenopixelsmod.client.compat.npc.mynpcs.gui;

import net.bullettrain.xenopixelsmod.client.compat.npc.gui.NpcPreviewPanel;
import net.bullettrain.xenopixelsmod.client.compat.npc.gui.NpcPreviewOwner;

import net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcSkillSet;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.NpcProfileSavePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import espi.mynpcs.client.gui.util.GuiNPCInterface2;
import espi.mynpcs.entity.EntityNPCInterface;
import espi.mynpcs.shared.client.gui.components.GuiButtonNop;
import espi.mynpcs.shared.client.gui.components.GuiButtonYesNo;
import espi.mynpcs.shared.client.gui.components.GuiTextFieldNop;
import espi.mynpcs.shared.client.gui.listeners.ITextfieldListener;

import java.util.List;

/**
 * Per-NPC DragonMineZ skill editor: a toggle and a level for every skill DMZ knows about.
 *
 * <p>The appearance panel has no room left for 45 skills, hence a sub-screen, following
 * {@link GuiNpcDmzAuraEditor}'s Apply/Cancel transaction shape. The id list is read from DMZ's
 * own config through {@link NpcSkillSet#knownIds()} rather than hard-coded, so a pack that adds
 * or removes skills is reflected here automatically.
 */
public final class GuiNpcDmzSkills extends GuiNPCInterface2 implements ITextfieldListener, NpcPreviewOwner {
    private static final int PREV_PAGE = 1;
    private static final int NEXT_PAGE = 2;
    private static final int APPLY = 3;
    private static final int CANCEL = 4;
    /** Toggle button ids are {@code TOGGLE_BASE + row}; level fields {@code LEVEL_BASE + row}. */
    private static final int TOGGLE_BASE = 100;
    private static final int LEVEL_BASE = 200;

    private static final int ROWS_PER_PAGE = 7;

    /** Text scale for every label on this screen; the raw skill ids run long at 1x. */
    private static final float TEXT_SCALE = 0.8F;

    private final java.util.List<LabelSpec> scaledLabels = new java.util.ArrayList<>();

    private final NpcCombatProfile original;
    private NpcCombatProfile draft;
    private int page;
    private final NpcPreviewPanel previewPanel = new NpcPreviewPanel(npc, null);

    public GuiNpcDmzSkills(EntityNPCInterface npc, NpcCombatProfile source) {
        super(npc, GuiNpcDmzMenuButton.MENU_ID);
        this.original = copy(source);
        this.draft = copy(source);
    }

    @Override
    public void init() {
        super.init();
        List<String> ids = NpcSkillSet.knownIds();
        int pages = Math.max(1, (ids.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        page = Math.max(0, Math.min(pages - 1, page));

        int x = guiLeft + 10;
        int y = guiTop + 7;
        scaledLabels.clear();
        scaledLabels.add(new LabelSpec("DragonMineZ Skills", x, y + 4, 0xFFD36A));
        scaledLabels.add(new LabelSpec("Page " + (page + 1) + " / " + pages, x + 150, y + 4, 0xFFFFFF));
        addButton(new GuiButtonNop(this, PREV_PAGE, x + 220, y, 20, 18, "<"));
        addButton(new GuiButtonNop(this, NEXT_PAGE, x + 244, y, 20, 18, ">"));
        addButton(new GuiButtonNop(this, APPLY, guiLeft + 300, guiTop + 174, 52, 18, "Apply"));
        addButton(new GuiButtonNop(this, CANCEL, guiLeft + 356, guiTop + 174, 56, 18, "Cancel"));

        if (ids.isEmpty()) {
            scaledLabels.add(new LabelSpec("DragonMineZ skill config unavailable", x, y + 40, 0xFF8080));
            return;
        }

        previewPanel.profile(draft);
        int rowY = y + 26;
        for (int row = 0; row < ROWS_PER_PAGE; row++) {
            int index = page * ROWS_PER_PAGE + row;
            if (index >= ids.size()) {
                break;
            }
            String id = ids.get(index);
            int max = NpcSkillSet.maxLevelOf(id);
            scaledLabels.add(new LabelSpec(id, x, rowY + 5, 0xFFFFFF));
            addButton(new GuiButtonYesNo(this, TOGGLE_BASE + row, x + 150, rowY, 48, 18,
                    draft.skills.isActive(id)));
            GuiTextFieldNop level = new GuiTextFieldNop(LEVEL_BASE + row, this, x + 204, rowY + 1,
                    28, 16, Integer.toString(Math.max(1, draft.skills.level(id))));
            level.setNumbersOnly();
            level.setMaxLength(3);
            addTextField(level);
            scaledLabels.add(new LabelSpec("/ " + max, x + 238, rowY + 5, 0xAAAAAA));
            rowY += 21;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!hasSubGui()) {
            renderScaledLabels(graphics);
            previewPanel.render(graphics, getFontRenderer(), guiLeft, guiTop, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!hasSubGui() && previewPanel.mouseClicked(guiLeft, guiTop, mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (!hasSubGui() && previewPanel.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!hasSubGui() && previewPanel.mouseReleased(button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!hasSubGui() && previewPanel.mouseScrolled(guiLeft, guiTop, mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        pull();
        if (button.id == PREV_PAGE) {
            page--;
        } else if (button.id == NEXT_PAGE) {
            page++;
        } else if (button.id == APPLY) {
            persist(draft);
            goBack(draft);
            return;
        } else if (button.id == CANCEL) {
            NpcAppearanceClient.applyProfile(((Entity) npc).getUUID(), original);
            persist(original);   // changes were autosaved; Cancel restores the server copy too
            goBack(original);
            return;
        }
        persist(draft);
        reinit();
    }

    @Override
    public void unFocused(GuiTextFieldNop field) {
        pull();
        persist(draft);
    }

    /** Reads every visible row back into the draft, so paging never loses an edit. */
    private void pull() {
        List<String> ids = NpcSkillSet.knownIds();
        for (int row = 0; row < ROWS_PER_PAGE; row++) {
            int index = page * ROWS_PER_PAGE + row;
            if (index >= ids.size()) {
                break;
            }
            String id = ids.get(index);
            boolean on = getButton(TOGGLE_BASE + row) instanceof GuiButtonYesNo yes
                    ? yes.getBoolean() : draft.skills.isActive(id);
            int level = integer(LEVEL_BASE + row, Math.max(1, draft.skills.level(id)));
            // Only store skills an author actually touched, so an NPC's NBT does not carry an
            // entry for every skill DMZ ships. An untouched row that is switched off clears
            // instead of being ignored, otherwise the toggle reads as dead for the 40+ ids DMZ
            // ships that this NPC never configured.
            if (on) {
                draft.skills.set(id, true, level);
            } else if (draft.skills.has(id)) {
                draft.skills.remove(id);
            }
            if (NpcSkillSet.FLY.equals(id)) {
                // Fly also drives CustomNPCs' navigator through NpcFlightBridge, which reads the
                // dedicated fields rather than the map.
                draft.flySkillOn = on;
                draft.flySkillLevel = NpcCombatProfile.clampFlySkillLevel(level);
            }
        }
    }

    /** Rebuilds the screen on the current page, carrying the draft across. */
    private void reinit() {
        GuiNpcDmzSkills next = new GuiNpcDmzSkills(npc, draft);
        next.page = page;
        Minecraft.getInstance().setScreen(next);
    }

    private void persist(NpcCombatProfile profile) {
        profile.write(npc);
        net.bullettrain.xenopixelsmod.client.npc.ClientNpcProfiles.save(((Entity) npc).getId(), profile.toTag(),
                NpcProfileSavePacket.Action.SAVE, profile.selectedFormGroup, profile.selectedFormId);
    }

    private void goBack(NpcCombatProfile profile) {
        Minecraft.getInstance().setScreen(new GuiNpcDmzAppearance(npc, profile));
    }

    @Override
    public void save() {
        // Autosave: closing or switching tabs keeps the draft (MyNPCs behaviour).
        persist(draft);
    }

    private int integer(int id, int fallback) {
        GuiTextFieldNop field = getTextField(id);
        if (field == null || field.getValue() == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(field.getValue().trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static NpcCombatProfile copy(NpcCombatProfile source) {
        return source == null ? new NpcCombatProfile() : NpcCombatProfile.fromTag(source.toTag());
    }

    /**
     * Draws the queued label specs at {@link #TEXT_SCALE}. The third-party {@code GuiLabel} has no
     * font or scale API and cannot be subclassed safely (verified against the pinned jars: the
     * CNPC variant leaves {@code renderWidget} abstract, the MyNpcs variant marks it final, and
     * {@code AbstractWidget.render} is final), so every label row is kept as a plain spec and
     * painted here through a pose scale anchored on the label's own top-left. The text shrinks
     * toward its origin and stays aligned with the toggle and level widgets on the same row.
     */
    private void renderScaledLabels(GuiGraphics graphics) {
        if (scaledLabels.isEmpty()) {
            return;
        }
        var font = Minecraft.getInstance().font;
        float shift = 1.0F / TEXT_SCALE - 1.0F;
        for (LabelSpec label : scaledLabels) {
            graphics.pose().pushPose();
            graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
            graphics.pose().translate(label.x() * shift, label.y() * shift, 0.0F);
            graphics.drawString(font, label.text(), label.x(), label.y(), label.color());
            graphics.pose().popPose();
        }
    }

    /** One scaled label row captured during {@link #init()}. */
    private record LabelSpec(String text, int x, int y, int color) {
    }
}

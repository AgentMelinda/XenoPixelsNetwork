package net.bullettrain.xenopixelsmod.client.compat.npc.gui;

import net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcSkillSet;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.NpcProfileSavePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

import java.util.List;

/**
 * Per-NPC DragonMineZ skill editor: a toggle and a level for every skill DMZ knows about.
 *
 * <p>The appearance panel has no room left for 45 skills, hence a sub-screen, following
 * {@link GuiNpcDmzAuraEditor}'s Apply/Cancel transaction shape. The id list is read from DMZ's
 * own config through {@link NpcSkillSet#knownIds()} rather than hard-coded, so a pack that adds
 * or removes skills is reflected here automatically.
 */
public final class GuiNpcDmzSkills extends GuiNPCInterface2 implements ITextfieldListener {
    private static final int PREV_PAGE = 1;
    private static final int NEXT_PAGE = 2;
    private static final int APPLY = 3;
    private static final int CANCEL = 4;
    /** Toggle button ids are {@code TOGGLE_BASE + row}; level fields {@code LEVEL_BASE + row}. */
    private static final int TOGGLE_BASE = 100;
    private static final int LEVEL_BASE = 200;

    private static final int ROWS_PER_PAGE = 7;

    private final NpcCombatProfile original;
    private NpcCombatProfile draft;
    private int page;

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
        addLabel(new GuiLabel(90, "DragonMineZ Skills", x, y + 4, 0xFFD36A));
        addLabel(new GuiLabel(91, "Page " + (page + 1) + " / " + pages, x + 150, y + 4, 0xFFFFFF));
        addButton(new GuiButtonNop(this, PREV_PAGE, x + 220, y, 20, 18, "<"));
        addButton(new GuiButtonNop(this, NEXT_PAGE, x + 244, y, 20, 18, ">"));
        addButton(new GuiButtonNop(this, APPLY, guiLeft + 300, guiTop + 174, 52, 18, "Apply"));
        addButton(new GuiButtonNop(this, CANCEL, guiLeft + 356, guiTop + 174, 56, 18, "Cancel"));

        if (ids.isEmpty()) {
            addLabel(new GuiLabel(92, "DragonMineZ skill config unavailable", x, y + 40, 0xFF8080));
            return;
        }

        int rowY = y + 26;
        for (int row = 0; row < ROWS_PER_PAGE; row++) {
            int index = page * ROWS_PER_PAGE + row;
            if (index >= ids.size()) {
                break;
            }
            String id = ids.get(index);
            int max = NpcSkillSet.maxLevelOf(id);
            addLabel(new GuiLabel(300 + row, id, x, rowY + 5, 0xFFFFFF));
            addButton(new GuiButtonYesNo(this, TOGGLE_BASE + row, x + 150, rowY, 48, 18,
                    draft.skills.isActive(id)));
            GuiTextFieldNop level = new GuiTextFieldNop(LEVEL_BASE + row, this, x + 204, rowY + 1,
                    28, 16, Integer.toString(Math.max(1, draft.skills.level(id))));
            level.setNumbersOnly();
            level.setMaxLength(3);
            addTextField(level);
            addLabel(new GuiLabel(400 + row, "/ " + max, x + 238, rowY + 5, 0xAAAAAA));
            rowY += 21;
        }
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
            goBack(original);
            return;
        }
        reinit();
    }

    @Override
    public void unFocused(GuiTextFieldNop field) {
        pull();
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
            // entry for every skill DMZ ships.
            if (on || draft.skills.has(id)) {
                draft.skills.set(id, on, level);
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
        ModNetwork.sendToServer(new NpcProfileSavePacket(((Entity) npc).getId(), profile.toTag(),
                NpcProfileSavePacket.Action.SAVE, profile.selectedFormGroup, profile.selectedFormId));
    }

    private void goBack(NpcCombatProfile profile) {
        Minecraft.getInstance().setScreen(new GuiNpcDmzAppearance(npc, profile));
    }

    @Override
    public void save() {
        // Apply/Cancel own this editor transaction.
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
}

package net.bullettrain.xenopixelsmod.client.compat.npc.gui;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcFormLookup;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormDocument;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormKind;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

import java.util.List;
import java.util.Locale;

/** Group and form picker in front of {@link GuiNpcDmzFormEditor}. */
public final class GuiNpcDmzForms extends GuiNPCInterface2 implements ITextfieldListener, NpcPreviewOwner {
    private static final int MODE = 1;
    private static final int RACE = 2;
    private static final int GROUP_PREV = 3;
    private static final int GROUP_NEXT = 4;
    private static final int FORM_PREV = 5;
    private static final int FORM_NEXT = 6;
    private static final int EDIT = 7;
    private static final int CREATE = 8;
    private static final int BACK = 9;
    private static final int GROUP_SEARCH = 10;
    private static final int FORM_SEARCH = 11;

    private DmzFormKind kind = DmzFormKind.NORMAL;
    private String race;
    private String group = "";
    private String form = "";
    private String groupSearch = "";
    private String formSearch = "";
    // No listener: this screen has no aura field to route a colour edit into, so the panel
    // renders the Aura toggle only.
    private final NpcPreviewPanel previewPanel = new NpcPreviewPanel(npc, null);

    public GuiNpcDmzForms(EntityNPCInterface npc) {
        super(npc, GuiNpcDmzMenuButton.MENU_ID);
        race = NpcCombatProfile.read(npc).raceId;
        if (race == null || race.isBlank()) race = "human";
        ensureSelection();
    }

    @Override
    public void init() {
        super.init();
        int x = guiLeft + 20;
        int y = guiTop + 18;
        addLabel(new GuiLabel(100, "DMZ Form Studio", x, y, 0xFFE2C078));
        addButton(new GuiButtonNop(this, MODE, x + 250, y - 5, 120, 18,
                kind == DmzFormKind.NORMAL ? "Normal Forms" : "Stack Forms"));
        y += 26;
        addLabel(new GuiLabel(101, "Race", x, y + 4, 0xFFFFFF));
        GuiTextFieldNop raceField = new GuiTextFieldNop(RACE, this, x + 72, y, 180, 18, race);
        raceField.setValue(race);
        raceField.setEditable(kind == DmzFormKind.NORMAL);
        addTextField(raceField);
        y += 26;
        row("Group", GROUP_PREV, GROUP_NEXT, GROUP_SEARCH, group, groupSearch, groups(), x, y);
        y += 44;
        row("Form", FORM_PREV, FORM_NEXT, FORM_SEARCH, form, formSearch, forms(), x, y);
        y += 50;
        addButton(new GuiButtonNop(this, EDIT, x, y, 110, 20, "Edit Selected"));
        addButton(new GuiButtonNop(this, CREATE, x + 120, y, 110, 20, "Create New"));
        addButton(new GuiButtonNop(this, BACK, x + 260, y, 110, 20, "Back"));
        addLabel(new GuiLabel(102,
                "All DragonMineZ 2.1.3 form fields are available in the paged editor.",
                x, y + 26, 0xFFAAAAAA));
        previewPanel.profile(NpcCombatProfile.read(npc));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!hasSubGui()) {
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

    private void row(String label, int previous, int next, int search, String value,
                     String filter, List<String> matches, int x, int y) {
        addLabel(new GuiLabel(previous + 100, label, x, y + 4, 0xFFFFFF));
        addButton(new GuiButtonNop(this, previous, x + 72, y, 24, 18, "<"));
        addLabel(new GuiLabel(previous + 200, clip(value), x + 108, y + 4, 0xFFFFD36A));
        addButton(new GuiButtonNop(this, next, x + 346, y, 24, 18, ">"));
        GuiTextFieldNop box = new GuiTextFieldNop(search, this, x + 72, y + 20, 200, 16, "");
        box.setValue(filter);
        addTextField(box);
        addLabel(new GuiLabel(previous + 300, "search  " + matches.size() + " match"
                + (matches.size() == 1 ? "" : "es"), x + 278, y + 24, 0xFFAAAAAA));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        pullFields();
        if (button.id == MODE) {
            kind = kind == DmzFormKind.NORMAL ? DmzFormKind.STACK : DmzFormKind.NORMAL;
            group = "";
            form = "";
            ensureSelection();
            init();
        } else if (button.id == GROUP_PREV || button.id == GROUP_NEXT) {
            group = NpcFormLookup.step(groups(), group, button.id == GROUP_NEXT ? 1 : -1);
            form = "";
            ensureSelection();
            init();
        } else if (button.id == FORM_PREV || button.id == FORM_NEXT) {
            form = NpcFormLookup.step(forms(), form, button.id == FORM_NEXT ? 1 : -1);
            init();
        } else if (button.id == EDIT) {
            if (!group.isBlank() && !form.isBlank()) {
                Minecraft.getInstance().setScreen(new GuiNpcDmzFormEditor(npc,
                        DmzFormDocument.load(kind, race, group, form)));
            }
        } else if (button.id == CREATE) {
            Minecraft.getInstance().setScreen(new GuiNpcDmzFormEditor(npc,
                    DmzFormDocument.create(kind, race)));
        } else if (button.id == BACK) {
            Minecraft.getInstance().setScreen(new GuiNpcDmz(npc));
        }
    }

    @Override
    public void unFocused(GuiTextFieldNop field) {
        pullFields();
        ensureSelection();
        init();
    }

    private void pullFields() {
        GuiTextFieldNop raceField = getTextField(RACE);
        if (raceField != null && kind == DmzFormKind.NORMAL && !raceField.getValue().isBlank()) {
            String next = raceField.getValue().trim().toLowerCase(Locale.ROOT);
            if (!next.equals(race)) {
                race = next;
                group = "";
                form = "";
            }
        }
        GuiTextFieldNop groupField = getTextField(GROUP_SEARCH);
        if (groupField != null) groupSearch = groupField.getValue().trim().toLowerCase(Locale.ROOT);
        GuiTextFieldNop formField = getTextField(FORM_SEARCH);
        if (formField != null) formSearch = formField.getValue().trim().toLowerCase(Locale.ROOT);
    }

    private void ensureSelection() {
        List<String> groups = groups();
        if (!groups.contains(group)) group = groups.isEmpty() ? "" : groups.get(0);
        List<String> forms = forms();
        if (!forms.contains(form)) form = forms.isEmpty() ? "" : forms.get(0);
    }

    private List<String> groups() {
        return DmzFormSearch.filter(kind == DmzFormKind.STACK
                ? NpcFormLookup.stackGroups() : NpcFormLookup.groups(race), groupSearch);
    }

    private List<String> forms() {
        return DmzFormSearch.filter(kind == DmzFormKind.STACK
                ? NpcFormLookup.stackForms(group) : NpcFormLookup.forms(race, group), formSearch);
    }

    private static String clip(String value) {
        if (value == null || value.isBlank()) return "None loaded";
        return value.length() <= 34 ? value : value.substring(0, 34);
    }

    @Override
    public void save() {
    }
}

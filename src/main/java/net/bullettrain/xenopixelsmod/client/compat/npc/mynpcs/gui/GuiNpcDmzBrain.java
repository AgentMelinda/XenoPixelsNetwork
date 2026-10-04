package net.bullettrain.xenopixelsmod.client.compat.npc.mynpcs.gui;

import net.bullettrain.xenopixelsmod.client.compat.npc.gui.NpcPreviewPanel;
import net.bullettrain.xenopixelsmod.client.compat.npc.gui.NpcPreviewOwner;

import net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.NpcProfileSavePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import espi.mynpcs.client.gui.mainmenu.GuiNpcStats;
import espi.mynpcs.client.gui.util.GuiNPCInterface2;
import espi.mynpcs.entity.EntityNPCInterface;
import espi.mynpcs.shared.client.gui.components.GuiButtonNop;
import espi.mynpcs.shared.client.gui.components.GuiButtonYesNo;
import espi.mynpcs.shared.client.gui.components.GuiLabel;
import espi.mynpcs.shared.client.gui.components.GuiTextFieldNop;
import espi.mynpcs.shared.client.gui.listeners.ITextfieldListener;

/**
 * Per-NPC combat brain editor: melee-first specials, ki types, fly/land, and aim tuning.
 *
 * <p>"Aim*" is greyed on purpose: AimAccuracy is stored and synced but nothing reads it. The ki
 * lead uses Ranged Props &gt; Accuracy ({@code npcRangedAccuracy}), as the native editor says.
 *
 * <p>Yes/No rows write immediately so Charge Off is live without Apply. Cancel restores the
 * snapshot from when the screen opened. Opened from the DMZ hub or the native Stats tab.
 */
public final class GuiNpcDmzBrain extends GuiNPCInterface2 implements ITextfieldListener, NpcPreviewOwner {
    public enum Origin { HUB, STATS }

    private static final int PREV_PAGE = 1;
    private static final int NEXT_PAGE = 2;
    private static final int APPLY = 3;
    private static final int CANCEL = 4;
    private static final int MASTER = 5;
    private static final int VERSION = 6;
    private static final int AIM = 10;
    private static final int AGGRO = 11;
    private static final int SPECIAL_CD = 12;
    private static final int TOGGLE_BASE = 100;

    private static final int ROWS_PER_PAGE = 5;
    private static final int CHANCE_BASE = 200;
    private static final int MOD_BASE = 250;

    private enum Flag {
        STRIKE("Strike techs", "strike"),
        CHARGE("Charge punch/kick", "charge"),
        FLYING_FIST("Flying Fist", "flyingFist"),
        HEAVY_HIT("Heavy Hit", "heavyHit"),
        BONE_CRUSHER("Bone Crusher", "boneCrusher"),
        KIAI("Kiai", "kiai"),
        RANDOM_KI("Random ki", "randomKi"),
        KI_BLAST("Ki blast", "kiBlast"),
        KI_WAVE("Ki wave", "kiWave"),
        KI_DISK("Ki disk", "kiDisk"),
        KI_NAMED("Named techs", "kiNamed"),
        FLY("Fly search", "fly"),
        LAND("Land on ground", "land"),
        ASCEND("Ascend when hurt", "ascend"),
        DISENGAGE("Disengage/guard", "disengage"),
        VANISH("Vanish in ki band", "vanish"),
        ZANZOKEN("Zanzoken fool", "zanzoken"),
        CHASE("Chase out of range", "chase"),
        DEFLECT_BLAST("Deflect blasts", "deflectBlast"),
        DEFLECT_WAVE("Deflect waves", "deflectWave");

        final String label;
        final String key;

        Flag(String label, String key) {
            this.label = label;
            this.key = key;
        }

        boolean get(NpcCombatProfile p) {
            return p.brainFlag(key);
        }

        void set(NpcCombatProfile p, boolean on) {
            p.setBrainFlag(key, on);
        }
    }

    private static final Flag[] FLAGS = Flag.values();

    private final NpcCombatProfile original;
    private NpcCombatProfile draft;
    private final Origin origin;
    private int page;
    private final NpcPreviewPanel previewPanel = new NpcPreviewPanel(npc, null);

    public GuiNpcDmzBrain(EntityNPCInterface npc, NpcCombatProfile source, Origin origin) {
        this(npc, copy(source), copy(source), origin, 0);
    }

    private GuiNpcDmzBrain(EntityNPCInterface npc, NpcCombatProfile original, NpcCombatProfile draft,
                           Origin origin, int page) {
        super(npc, GuiNpcDmzBrainMenuButton.MENU_ID);
        this.original = original;
        this.draft = draft;
        this.origin = origin == null ? Origin.HUB : origin;
        this.page = page;
    }

    @Override
    public void init() {
        super.init();
        int pages = Math.max(1, (FLAGS.length + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        page = Math.max(0, Math.min(pages - 1, page));

        int x = guiLeft + 10;
        int y = guiTop + 4;
        addLabel(new GuiLabel(90, "Combat Brain", x, y + 4, 0xFFD36A));
        addButton(new GuiButtonYesNo(this, MASTER, x + 90, y, 48, 18, draft.combatBrain));
        addButton(new GuiButtonNop(this, VERSION, x + 142, y, 28, 18, draft.brainVersion.label()));
        addLabel(new GuiLabel(91, "Page " + (page + 1) + " / " + pages, x + 174, y + 4, 0xFFFFFF));
        addButton(new GuiButtonNop(this, PREV_PAGE, x + 220, y, 20, 18, "<"));
        addButton(new GuiButtonNop(this, NEXT_PAGE, x + 244, y, 20, 18, ">"));
        addButton(new GuiButtonNop(this, APPLY, guiLeft + 300, guiTop + 174, 52, 18, "Apply"));
        addButton(new GuiButtonNop(this, CANCEL, guiLeft + 356, guiTop + 174, 56, 18, "Cancel"));

        y += 22;
        addLabel(new GuiLabel(92, "Aim*", x, y + 5, 0x888888));
        text(AIM, x + 36, y, 40, Float.toString(draft.aimAccuracy));
        addLabel(new GuiLabel(93, "Aggro", x + 86, y + 5, 0xFFFFFF));
        text(AGGRO, x + 128, y, 40, Float.toString(draft.aggroMultiplier));
        addLabel(new GuiLabel(94, "Special CD", x + 176, y + 5, 0xFFFFFF));
        text(SPECIAL_CD, x + 244, y, 36, Integer.toString(draft.brainSpecialCooldown));

        previewPanel.profile(draft);
        addLabel(new GuiLabel(95, "%", x + 202, y + 5, 0xAAAAAA));
        addLabel(new GuiLabel(96, "x", x + 242, y + 5, 0xAAAAAA));
        int rowY = y + 22;
        for (int row = 0; row < ROWS_PER_PAGE; row++) {
            int index = page * ROWS_PER_PAGE + row;
            if (index >= FLAGS.length) {
                break;
            }
            Flag flag = FLAGS[index];
            addLabel(new GuiLabel(300 + row, flag.label, x, rowY + 5, 0xFFFFFF));
            addButton(new GuiButtonYesNo(this, TOGGLE_BASE + row, x + 118, rowY, 40, 18, flag.get(draft)));
            text(CHANCE_BASE + row, x + 164, rowY, 32, Integer.toString(draft.brainChance(flag.key)));
            text(MOD_BASE + row, x + 202, rowY, 36, Float.toString(draft.brainModifier(flag.key)));
            rowY += 18;
        }
    }

    private void text(int id, int x, int y, int w, String value) {
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, x, y + 1, w, 16, value);
        field.setMaxLength(8);
        addTextField(field);
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

    @Override
    public void buttonEvent(GuiButtonNop button) {
        pull();
        // Turning a page commits first. pull() has already folded this page's widgets into the
        // draft, but the draft is client-side only: without this, editing a page and then leaving
        // by any route other than Apply -- closing with Escape, most often -- threw the edit away,
        // while persist()'s local write could still make it look applied until the next resync.
        if (button.id == PREV_PAGE) {
            persist(draft);
            page--;
            reinit();
            return;
        }
        if (button.id == NEXT_PAGE) {
            persist(draft);
            page++;
            reinit();
            return;
        }
        if (button.id == APPLY) {
            persist(draft);
            goBack(draft);
            return;
        }
        if (button.id == CANCEL) {
            persist(original);
            NpcAppearanceClient.applyProfile(((Entity) npc).getUUID(), original);
            goBack(original);
            return;
        }
        if (button.id == VERSION) {
            draft.brainVersion = draft.brainVersion.next();
            persist(draft);
            reinit();
            return;
        }
        persist(draft);
    }

    @Override
    public void unFocused(GuiTextFieldNop field) {
        pull();
    }

    private void pull() {
        if (getButton(MASTER) instanceof GuiButtonYesNo yes) {
            draft.combatBrain = yes.getBoolean();
        }
        draft.aimAccuracy = NpcCombatProfile.clampAimAccuracy(flo(AIM, draft.aimAccuracy));
        draft.aggroMultiplier = NpcCombatProfile.clampAggroMultiplier(flo(AGGRO, draft.aggroMultiplier));
        draft.brainSpecialCooldown = NpcCombatProfile.clampSpecialCooldown(
                integer(SPECIAL_CD, draft.brainSpecialCooldown));
        for (int row = 0; row < ROWS_PER_PAGE; row++) {
            int index = page * ROWS_PER_PAGE + row;
            if (index >= FLAGS.length) {
                break;
            }
            Flag flag = FLAGS[index];
            boolean on = getButton(TOGGLE_BASE + row) instanceof GuiButtonYesNo yes
                    ? yes.getBoolean() : flag.get(draft);
            flag.set(draft, on);
            draft.setBrainChance(flag.key, integer(CHANCE_BASE + row, draft.brainChance(flag.key)));
            draft.setBrainModifier(flag.key, flo(MOD_BASE + row, draft.brainModifier(flag.key)));
        }
    }

    private void reinit() {
        Minecraft.getInstance().setScreen(new GuiNpcDmzBrain(npc, original, draft, origin, page));
    }

    private void persist(NpcCombatProfile profile) {
        profile.write(npc);
        net.bullettrain.xenopixelsmod.client.npc.ClientNpcProfiles.save(((Entity) npc).getId(), profile.toTag(),
                NpcProfileSavePacket.Action.SAVE, profile.selectedFormGroup, profile.selectedFormId);
    }

    private void goBack(NpcCombatProfile profile) {
        if (origin == Origin.STATS) {
            Minecraft.getInstance().setScreen(new GuiNpcStats(npc));
            return;
        }
        profile.write(npc);
        Minecraft.getInstance().setScreen(new GuiNpcDmz(npc));
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

    private float flo(int id, float fallback) {
        GuiTextFieldNop field = getTextField(id);
        if (field == null || field.getValue() == null) {
            return fallback;
        }
        try {
            return Float.parseFloat(field.getValue().trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static NpcCombatProfile copy(NpcCombatProfile source) {
        return source == null ? new NpcCombatProfile() : NpcCombatProfile.fromTag(source.toTag());
    }
}

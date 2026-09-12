package net.bullettrain.xenopixelsmod.client.compat.npc.gui;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;

import java.util.WeakHashMap;

/**
 * One {@link NpcPreviewPanel} per native wand screen, used by the GuiNPCInterface2 mixins.
 *
 * <p>Weak keys so a closed screen does not keep a panel (or its profile) alive. Aura stays
 * visualizer-local: {@link NpcPreviewPanel} never writes the NPC's persisted aura flag.
 */
public final class NpcWandPreview {
    private static final WeakHashMap<Object, NpcPreviewPanel> PANELS = new WeakHashMap<>();

    private NpcWandPreview() {
    }

    public static boolean skip(Object screen) {
        return screen instanceof NpcPreviewOwner;
    }

    public static NpcPreviewPanel panel(Object screen, LivingEntity npc) {
        NpcPreviewPanel existing = PANELS.get(screen);
        if (existing != null) return existing;
        NpcPreviewPanel created = new NpcPreviewPanel(npc, null);
        if (npc != null) created.profile(NpcCombatProfile.read(npc));
        PANELS.put(screen, created);
        return created;
    }

    public static void render(Object screen, LivingEntity npc, GuiGraphics graphics,
                              int guiLeft, int guiTop, float partialTick) {
        if (skip(screen) || npc == null || graphics == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft == null ? null : minecraft.font;
        if (font == null) return;
        NpcPreviewPanel preview = panel(screen, npc);
        if (preview.profile() == null) preview.profile(NpcCombatProfile.read(npc));
        preview.render(graphics, font, guiLeft, guiTop, partialTick);
    }

    public static boolean mouseClicked(Object screen, LivingEntity npc, int guiLeft, int guiTop,
                                       double mouseX, double mouseY, int button) {
        if (skip(screen) || npc == null) return false;
        return panel(screen, npc).mouseClicked(guiLeft, guiTop, mouseX, mouseY, button);
    }

    public static boolean mouseDragged(Object screen, LivingEntity npc, double mouseX, double mouseY,
                                       int button, double dragX, double dragY) {
        if (skip(screen) || npc == null) return false;
        return panel(screen, npc).mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public static boolean mouseReleased(Object screen, LivingEntity npc, int button) {
        if (skip(screen) || npc == null) return false;
        return panel(screen, npc).mouseReleased(button);
    }

    public static boolean mouseScrolled(Object screen, LivingEntity npc, int guiLeft, int guiTop,
                                        double mouseX, double mouseY, double scrollY) {
        if (skip(screen) || npc == null) return false;
        return panel(screen, npc).mouseScrolled(guiLeft, guiTop, mouseX, mouseY, scrollY);
    }
}

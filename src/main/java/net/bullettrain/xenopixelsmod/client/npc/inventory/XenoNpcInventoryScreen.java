package net.bullettrain.xenopixelsmod.client.npc.inventory;

import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.bullettrain.xenopixelsmod.npc.inventory.NpcGear;
import net.bullettrain.xenopixelsmod.npc.inventory.XenoNpcInventoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * An NPC's gear, drops and Curios, in the same blue frame as the rest of the editor.
 *
 * <p>Both panels are blitted at their shipped size and nothing is stretched. The 176x222
 * {@code mynpcs_small_panel} carries the worn row, the drop row and the player's inventory at
 * vanilla's own geometry; the 141x213 {@code panel_tall} beside it carries the Curios grid.
 *
 * <p><b>Why two.</b> The main panel fits eight slots across at an 18-pixel pitch. Worn needs six and
 * drops need nine, both of which fit; Curios needs up to twelve, which at 224 pixels ran clean off
 * the right-hand edge of the frame — slots drawn outside the panel, still clickable. Moving that one
 * group onto a second panel is what {@code XenoNpcBankScreen} already does, and it costs no new art.
 *
 * <p>The side panel is <b>absent</b>, not empty, when the NPC has no Curios slots: the menu is built
 * with none, so the screen is exactly one panel wide. A second frame with nothing in it would read
 * as something broken.
 */
public class XenoNpcInventoryScreen extends AbstractContainerScreen<XenoNpcInventoryMenu> {

    private static final String MAIN_PANEL = "xeno_npc_gear_panel";
    private static final String SIDE_PANEL = "xeno_npc_curios_panel";
    private static final String SLOT = "icon_slot_sm";

    private static final int LABEL = 0xFFBBD5E6;
    private static final int MUTED = 0xFF8AA4B8;

    /** Where the second panel starts, whether or not it is drawn. */
    private final int sidePanelX;

    private final boolean hasCurios;

    public XenoNpcInventoryScreen(XenoNpcInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.hasCurios = !menu.curioSlots().isEmpty();
        this.sidePanelX = XenoNpcInventoryMenu.MAIN_PANEL_WIDTH + XenoNpcInventoryMenu.PANEL_GAP;
        this.imageWidth = hasCurios
                ? sidePanelX + XenoAtlasSprites.get(SIDE_PANEL).width()
                : XenoAtlasSprites.get(MAIN_PANEL).width();
        this.imageHeight = XenoAtlasSprites.get(MAIN_PANEL).height();
        // The labels belong to the left panel, which is only part of the total width when the side
        // panel is drawn; without this they centre against the composite and drift over it.
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        XenoAtlasSprites.blit(graphics, MAIN_PANEL, leftPos, topPos);
        if (hasCurios) {
            XenoAtlasSprites.blit(graphics, SIDE_PANEL, leftPos + sidePanelX, topPos);
        }
        // Drawn under every slot the menu built, so the Curios sockets appear only where that panel
        // is and only when the NPC actually has those slots.
        for (Slot slot : menu.slots) {
            XenoAtlasSprites.blit(graphics, SLOT, leftPos + slot.x - 2, topPos + slot.y - 2);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        // Positioned from the menu's own slot list rather than from copied constants, so a layout
        // change in the menu cannot leave a heading floating over the wrong row.
        heading(graphics, "Worn", 0);
        heading(graphics, "Drops", XenoNpcInventoryMenu.GEAR_SLOTS);
        if (hasCurios) {
            heading(graphics, "Curios",
                    XenoNpcInventoryMenu.GEAR_SLOTS + XenoNpcInventoryMenu.DROP_SLOTS);
        }
    }

    /** One group's label, sitting just above its first slot. */
    private void heading(GuiGraphics graphics, String text, int firstSlot) {
        if (firstSlot >= menu.slots.size()) {
            return;
        }
        Slot slot = menu.slots.get(firstSlot);
        graphics.drawString(font, text, slot.x - 2, slot.y - 12, LABEL, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    /**
     * Says what a row means when the mouse is over it.
     *
     * <p>Rendered as a tooltip rather than a fixed caption line: the main panel has no free strip
     * wide enough for a sentence, and a caption written across the drop row would sit on top of the
     * sockets.
     *
     * <p>Two of these carry information the slot itself cannot — worn gear does not drop when the
     * NPC dies, and weights in a Curios slot render without training it, because DragonMineZ's
     * training runs off player packets with no NPC path into it. Both are the kind of thing somebody
     * otherwise discovers hours later.
     */
    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        if (hoveredSlot != null && hoveredSlot.hasItem()) {
            // The item's own tooltip is the more useful one; two stacked would obscure the grid.
            return;
        }
        int index = hoveredIndex();
        if (index < 0) {
            return;
        }
        Component text;
        if (index < XenoNpcInventoryMenu.GEAR_SLOTS) {
            text = Component.literal(NpcGear.LABELS[index] + " — worn, never dropped");
        } else if (index < XenoNpcInventoryMenu.GEAR_SLOTS + XenoNpcInventoryMenu.DROP_SLOTS) {
            int drop = index - XenoNpcInventoryMenu.GEAR_SLOTS + 1;
            text = Component.literal("Drop " + drop + " — set its chance on the Inventory page");
        } else {
            int curio = index - XenoNpcInventoryMenu.GEAR_SLOTS - XenoNpcInventoryMenu.DROP_SLOTS;
            String slot = menu.curioSlots().get(curio);
            text = Component.literal("weights".equals(slot)
                    ? "weights — worn and shown, but it does not train an NPC"
                    : slot);
        }
        graphics.renderTooltip(font, text, mouseX, mouseY);
    }

    /** Which of the NPC's own slots the mouse is over, or -1. */
    private int hoveredIndex() {
        Slot slot = hoveredSlot;
        if (slot == null) {
            return -1;
        }
        int index = menu.slots.indexOf(slot);
        return index >= 0 && index < menu.npcSlotCount() ? index : -1;
    }

}

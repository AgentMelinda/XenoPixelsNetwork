package net.bullettrain.xenopixelsmod.client.npc.quest;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Factions and Quests, as tabs beside the vanilla inventory.
 *
 * <p>Attached through {@code ScreenEvent} and anchored on {@code getGuiLeft()} /
 * {@code getGuiTop()} exactly as {@code XenoInventoryEffects} anchors its effect rail - a hook this
 * repo has already proven, rather than a mixin into the inventory screen.
 *
 * <p>The strip is drawn to the <em>left</em> of the window because the right is where the effect
 * rail already lives.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class XenoInventoryTabs {

    /** Drawn top to bottom in declaration order; the reference groups them this way. */
    public enum Tab {
        INVENTORY,
        FACTIONS,
        QUESTS
    }

    private static final int TAB_WIDTH = 28;
    private static final int TAB_HEIGHT = 26;

    /** A gap, so two tabs never share an edge and a click can never be ambiguous. */
    private static final int TAB_GAP = 2;

    /** Where the strip starts, relative to the window top. */
    private static final int TOP_INSET = 4;

    private static Tab open = Tab.INVENTORY;

    private XenoInventoryTabs() {
    }

    /** Which tab is showing. Starts on the inventory, so opening it shows the player's items. */
    public static Tab open() {
        return open;
    }

    /** True while the inventory's quest journal owns the panel area. */
    public static boolean questLogOpen() {
        return open == Tab.QUESTS && Minecraft.getInstance().screen instanceof InventoryScreen;
    }

    /** True while either Xeno side panel is open over the inventory background. */
    public static boolean sidePanelOpen() {
        return (open == Tab.FACTIONS || open == Tab.QUESTS)
                && Minecraft.getInstance().screen instanceof InventoryScreen;
    }

    /** {@code {x, y, width, height}} of one tab. */
    public static int[] tabRect(int guiLeft, int guiTop, int index) {
        int x = guiLeft - TAB_WIDTH;
        int y = guiTop + TOP_INSET + index * (TAB_HEIGHT + TAB_GAP);
        return new int[]{x, y, TAB_WIDTH, TAB_HEIGHT};
    }

    /**
     * The tab under this point, or null.
     *
     * <p>Half-open on both axes, so two stacked tabs can never both claim one pixel row.
     */
    public static Tab hit(int guiLeft, int guiTop, double mouseX, double mouseY) {
        for (int index = 0; index < Tab.values().length; index++) {
            int[] rect = tabRect(guiLeft, guiTop, index);
            if (mouseX >= rect[0] && mouseX < rect[0] + rect[2]
                    && mouseY >= rect[1] && mouseY < rect[1] + rect[3]) {
                return Tab.values()[index];
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen inventory) || event.getButton() != 0) {
            return;
        }
        Tab clicked = hit(inventory.getGuiLeft(), inventory.getGuiTop(),
                event.getMouseX(), event.getMouseY());
        if (clicked != null) {
            open = clicked;
            // Cancelled only when a tab was actually hit, so every other click still reaches the
            // inventory's own slot handling.
            event.setCanceled(true);
            return;
        }
        if (open == Tab.QUESTS && QuestLogPanel.click(inventory,
                event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (open != Tab.QUESTS || !(event.getScreen() instanceof InventoryScreen inventory)) {
            return;
        }
        if (QuestLogPanel.scroll(inventory, event.getMouseX(), event.getMouseY(),
                event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen inventory)) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int left = inventory.getGuiLeft();
        int top = inventory.getGuiTop();

        for (int index = 0; index < Tab.values().length; index++) {
            int[] rect = tabRect(left, top, index);
            boolean active = Tab.values()[index] == open;
            XenoAtlasSprites.blit(graphics, "tab_docked_w28",
                    active ? XenoAtlasSprites.Theme.GOLD : XenoAtlasSprites.Theme.BLUE,
                    rect[0], rect[1]);
            graphics.drawCenteredString(Minecraft.getInstance().font,
                    Component.literal(label(Tab.values()[index])), rect[0] + rect[2] / 2,
                    rect[1] + (rect[3] - 8) / 2,
                    active ? 0xFFFFFFFF : 0xFFBBD5E6);
        }

        if (open == Tab.FACTIONS) {
            FactionPanel.render(graphics, inventory, event.getMouseX(), event.getMouseY());
        } else if (open == Tab.QUESTS) {
            QuestLogPanel.render(graphics, inventory, event.getMouseX(), event.getMouseY());
        }
    }

    private static String label(Tab tab) {
        return switch (tab) {
            case INVENTORY -> "I";
            case FACTIONS -> "F";
            case QUESTS -> "Q";
        };
    }
}

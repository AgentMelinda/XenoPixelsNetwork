package net.bullettrain.xenopixelsmod.client.npc.quest;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;

import java.util.List;

/**
 * "New Quest", top-right, for four seconds.
 *
 * <p>Raised by {@link ClientQuests} when a quest id appears that was not in the previous sync - not
 * on every sync, because progress pushes one per kill and a toast that re-fired on each would never
 * leave the screen.
 */
public final class QuestToastOverlay {

    /** Long enough to read, short enough not to become furniture - the editor notice's reasoning. */
    private static final long LIFETIME_MS = 4000L;

    private static final int WIDTH = 220;
    private static final int HEIGHT = 56;
    private static final int MARGIN = 8;

    private static volatile String title = "";
    private static volatile long shownAt;

    private QuestToastOverlay() {
    }

    /** Raises the toast. */
    public static void show(String questTitle) {
        title = questTitle == null ? "" : questTitle;
        shownAt = System.currentTimeMillis();
    }

    /** What the toast last announced. Empty once it has been reset. */
    public static String lastShown() {
        return title;
    }

    /**
     * Takes the toast down.
     *
     * <p>Called on disconnect, so a stale announcement from the last world does not greet the
     * player in the next one.
     */
    public static void reset() {
        title = "";
        shownAt = 0L;
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        String shown = title;
        if (shown.isEmpty() || System.currentTimeMillis() - shownAt > LIFETIME_MS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int x = graphics.guiWidth() - WIDTH - MARGIN;
        int y = MARGIN;

        XenoAtlasSprites.blit(graphics, "xeno_quest_toast",
                XenoAtlasSprites.Theme.BLUE, x, y);

        graphics.drawString(minecraft.font, Component.literal("New Quest"),
                x + 8, y + 7, 0xFFFFC928, false);
        List<FormattedCharSequence> titleLines = minecraft.font.split(
                Component.literal(shown), WIDTH - 16);
        for (int i = 0; i < Math.min(2, titleLines.size()); i++) {
            graphics.drawString(minecraft.font, titleLines.get(i),
                    x + 8, y + 23 + i * 10, 0xFFFFFFFF, false);
        }
    }
}

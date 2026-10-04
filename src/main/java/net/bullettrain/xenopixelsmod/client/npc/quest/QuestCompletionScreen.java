package net.bullettrain.xenopixelsmod.client.npc.quest;

import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.bullettrain.xenopixelsmod.client.screen.UnblurredScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Centered Xeno completion card, shown only after the server confirms the reward was paid. */
public final class QuestCompletionScreen extends UnblurredScreen {
    private final String questTitle;
    private final String description;
    private final String reward;
    private final XenoAtlasSprites.Theme palette;
    private final String frame;

    public QuestCompletionScreen(String questTitle, String description, String reward,
                                 String palette, String frame) {
        super(Component.literal("Quest complete"));
        this.questTitle = safe(questTitle, "Quest complete");
        this.description = safe(description, "");
        this.reward = safe(reward, "");
        this.palette = parsePalette(palette);
        this.frame = "banner".equalsIgnoreCase(frame)
                ? "xeno_quest_complete_banner" : "xeno_quest_complete_rounded";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = (width - 360) / 2;
        int top = (height - 180) / 2;
        XenoAtlasSprites.blit(graphics, frame, palette, left, top);
        graphics.drawCenteredString(font, Component.literal("QUEST COMPLETE"), width / 2,
                top + 24, color());
        int titleLines = drawWrapped(graphics, questTitle, left + 24, top + 46,
                312, 3, 0xFFFFFFFF);
        int descriptionY = top + 52 + titleLines * 12;
        drawWrapped(graphics, description, left + 30, descriptionY, 300, 3, 0xFFEAF4FA);
        if (!reward.isBlank()) {
            graphics.drawCenteredString(font, Component.literal("Rewards: " + reward), width / 2,
                    top + 128, 0xFFFFD56A);
        }
        graphics.drawCenteredString(font, Component.literal("Click or press Esc to continue"),
                width / 2, top + 154, 0xFF9EB4C5);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        onClose();
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() { return true; }

    private int drawWrapped(GuiGraphics graphics, String text, int x, int y, int maxWidth,
                            int maxLines, int color) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), maxWidth);
        int visibleLines = Math.min(maxLines, lines.size());
        for (int i = 0; i < visibleLines; i++) {
            graphics.drawCenteredString(font, lines.get(i), width / 2, y + i * 12, color);
        }
        return Math.max(1, visibleLines);
    }

    private int color() {
        return switch (palette) {
            case BLUE -> 0xFF35D7FF;
            case GOLD -> 0xFFFFC928;
            case GREEN -> 0xFF7CE08A;
            case RED -> 0xFFFF6B6B;
        };
    }

    private static XenoAtlasSprites.Theme parsePalette(String value) {
        try { return XenoAtlasSprites.Theme.valueOf(value == null ? "BLUE" : value.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return XenoAtlasSprites.Theme.BLUE; }
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

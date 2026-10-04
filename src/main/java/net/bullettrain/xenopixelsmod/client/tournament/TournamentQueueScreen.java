package net.bullettrain.xenopixelsmod.client.tournament;

import com.dragonminez.client.gui.character.util.ScaledScreen;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasButton;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasNotice;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasPanel;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.bullettrain.xenopixelsmod.features.tournament.TournamentNetwork;
import net.bullettrain.xenopixelsmod.features.tournament.TournamentService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.UUID;

/**
 * Read-only tournament queue + active-match screen on the green atlas kit.
 *
 * <p>State comes only from the last S2C {@link TournamentNetwork.QueueSnapshotPacket}. Sprites are
 * blitted at native size with {@link XenoAtlasSprites.Theme#GREEN}; no new PanelSpecs — reuses
 * {@code xeno_editor_panel}, {@code header_strip}, {@code panel_wide}, and {@code pill_button}.
 *
 * <p><b>Not verified in a running game.</b>
 */
public final class TournamentQueueScreen extends ScaledScreen {
    private static final String FRAME = "xeno_editor_panel";
    private static final String ROW = "header_strip";
    private static final String MATCH = "panel_wide";
    private static final String CLOSE = "pill_button";
    private static final XenoAtlasSprites.Theme THEME = XenoAtlasSprites.Theme.GREEN;

    private static final int ROW_PITCH = 24;
    private static final int MAX_VISIBLE = 10;

    private final Screen parent;
    private int frameX;
    private int frameY;
    private int frameW;
    private int frameH;
    private int listX;
    private int listY;
    private int rowW;
    private int matchX;
    private int matchY;

    public TournamentQueueScreen(Screen parent) {
        super(Component.literal("Tournament"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int canvasW = getUiWidth();
        int canvasH = getUiHeight();
        int[] fitted = XenoAtlasSprites.fittedSize(FRAME, canvasW - 8, canvasH - 8);
        frameW = fitted[0];
        frameH = fitted[1];
        frameX = (canvasW - frameW) / 2;
        frameY = (canvasH - frameH) / 2;

        rowW = XenoAtlasSprites.get(ROW).width();
        listX = frameX + 24;
        listY = frameY + 78;
        matchX = listX + rowW + 28;
        matchY = frameY + 78;

        int closeW = AtlasButton.nativeWidth(CLOSE);
        addRenderableWidget(new AtlasButton(frameX + frameW - closeW - 24, frameY + 18,
                Component.literal("Close"), CLOSE, b -> onClose()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xCC030712);

        XenoAtlasSprites.Theme previous = XenoAtlasSprites.theme();
        XenoAtlasSprites.setTheme(THEME);
        try {
            beginUiScale(graphics);
            AtlasPanel.fittedInto(FRAME, frameX, frameY, getUiWidth() - 8, getUiHeight() - 8)
                    .render(graphics);

            graphics.drawString(font, "TOURNAMENT", frameX + 24, frameY + 18, 0xFF9AFFB0, false);

            boolean enabled = TournamentNetwork.clientEnabled();
            UUID king = TournamentNetwork.clientKing();
            String matchId = TournamentNetwork.clientActiveMatch();
            List<UUID> queue = TournamentNetwork.clientQueue();

            String status = "Enabled: " + (enabled ? "yes" : "no")
                    + "   King: " + nameOf(king)
                    + "   Queue " + queue.size() + "/" + TournamentService.MAX_QUEUED;
            graphics.drawString(font, status, frameX + 24, frameY + 48, 0xFFB8E8C8, false);

            graphics.drawString(font, "Queue", listX, listY - 14, 0xFFFFC14A, false);
            renderQueue(graphics, queue);

            graphics.drawString(font, "Active match", matchX, matchY - 14, 0xFFFFC14A, false);
            renderMatch(graphics, matchId, king);

            super.render(graphics, (int) toUiX(mouseX), (int) toUiY(mouseY), partialTick);
            endUiScale(graphics);
        } finally {
            XenoAtlasSprites.setTheme(previous);
        }
    }

    private void renderQueue(GuiGraphics graphics, List<UUID> queue) {
        if (queue.isEmpty()) {
            new AtlasNotice("Queue is empty", listX, listY).render(graphics, font);
            graphics.drawString(font, "Join with /xenotourney join", listX, listY + 40,
                    0xFF6E9680, false);
            return;
        }
        int y = listY;
        int shown = 0;
        for (UUID id : queue) {
            if (shown >= MAX_VISIBLE) {
                graphics.drawString(font, "+" + (queue.size() - shown) + " more",
                        listX + 4, y + 4, 0xFF6E9680, false);
                break;
            }
            XenoAtlasSprites.blit(graphics, ROW, THEME, listX, y);
            String line = (shown + 1) + ". " + nameOf(id);
            graphics.drawString(font, line, listX + 8, y + 7, 0xFFFFFFFF, false);
            y += ROW_PITCH;
            shown++;
        }
    }

    private void renderMatch(GuiGraphics graphics, String matchId, UUID king) {
        XenoAtlasSprites.blit(graphics, MATCH, THEME, matchX, matchY);
        int textX = matchX + 12;
        int textY = matchY + 14;
        if (matchId == null || matchId.isBlank()) {
            graphics.drawString(font, "(none)", textX, textY, 0xFF6E9680, false);
            graphics.drawString(font, "No challenge in progress", textX, textY + 16,
                    0xFF6E9680, false);
            return;
        }
        graphics.drawString(font, matchId, textX, textY, 0xFFFFFFFF, false);
        graphics.drawString(font, "King: " + nameOf(king), textX, textY + 16, 0xFFB8E8C8, false);
        graphics.drawString(font, "Admin result only in v1", textX, textY + 32, 0xFF6E9680, false);
    }

    /** Resolves a queued UUID to an online display name, else a short id. */
    static String nameOf(UUID id) {
        if (id == null) {
            return "(none)";
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(id);
            if (info != null && info.getProfile() != null && info.getProfile().getName() != null) {
                return info.getProfile().getName();
            }
        }
        if (mc.level != null) {
            Player player = mc.level.getPlayerByUUID(id);
            if (player != null) {
                return player.getGameProfile().getName();
            }
        }
        String raw = id.toString();
        return raw.length() > 8 ? raw.substring(0, 8) + "…" : raw;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

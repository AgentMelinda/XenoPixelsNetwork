package net.bullettrain.xenopixelsmod.client.screen;

import com.dragonminez.client.gui.character.util.ScaledScreen;
import net.bullettrain.xenopixelsmod.client.ClientParty;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasButton;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasNotice;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasPanel;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasToggle;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.PartyActionPacket;
import net.bullettrain.xenopixelsmod.network.packet.PartySyncPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * The XenoParty social screen, drawn on the shared atlas kit.
 *
 * <p>Only the chrome changed from the flat-fill version: every {@link PartyActionPacket.Action} is
 * still sent from the same control, {@link ClientParty} is still the only state source, Enter still
 * submits whichever of the two boxes has focus, and member selection still drives Promote and Kick.
 *
 * <p>Sprites are blitted at their shipped sizes, so the layout is built around those sizes rather
 * than the other way round: member rows are {@code header_strip} (180x22), inline actions are
 * {@code mynpcs_button_row} (64x22), and the primary actions are {@code pill_button} (90x24).
 */
public final class XenoPartyScreen extends ScaledScreen {
    private static final String FRAME = "xeno_editor_panel";
    private static final String ROW = "header_strip";
    private static final String ACTION = "mynpcs_button_row";
    private static final String PRIMARY = "pill_button";

    private static final int ROW_PITCH = 26;
    private static final int MAX_VISIBLE_MEMBERS = 9;

    private final Screen parent;
    private EditBox inviteBox;
    private EditBox chatBox;
    private UUID selectedMember;

    private int frameX;
    private int frameY;
    private int frameW;
    private int frameH;
    private int listX;
    private int listY;
    private int rowW;

    public XenoPartyScreen(Screen parent) {
        super(Component.literal("XenoParty"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        // Laid out on the DMZ virtual canvas, not in window pixels.
        int canvasW = getUiWidth();
        int canvasH = getUiHeight();
        int[] fitted = XenoAtlasSprites.fittedSize(FRAME, canvasW - 8, canvasH - 8);
        frameW = fitted[0];
        frameH = fitted[1];
        frameX = (canvasW - frameW) / 2;
        frameY = (canvasH - frameH) / 2;

        rowW = XenoAtlasSprites.get(ROW).width();
        listX = frameX + 24;
        listY = frameY + 88;

        int rightX = listX + rowW + 28;
        int actionW = AtlasButton.nativeWidth(ACTION);
        int primaryW = AtlasButton.nativeWidth(PRIMARY);

        addRenderableWidget(new AtlasButton(frameX + frameW - primaryW - 24, frameY + 18,
                Component.literal("HUD layout"), PRIMARY,
                b -> minecraft.setScreen(new XenoPartyHudEditScreen(this))));

        // Invite row.
        inviteBox = new EditBox(font, rightX, frameY + 92, 220, 20,
                Component.literal("Player name"));
        inviteBox.setHint(Component.literal("Player name"));
        inviteBox.setMaxLength(64);
        addRenderableWidget(inviteBox);
        addRenderableWidget(new AtlasButton(rightX + 228, frameY + 90,
                Component.literal("Invite"), PRIMARY,
                b -> sendText(PartyActionPacket.Action.INVITE, inviteBox)));

        // Party actions, laid out on the native action-cell width.
        int actionY = frameY + 128;
        int col = rightX;
        addRenderableWidget(new AtlasButton(col, actionY, Component.literal("Refresh"), ACTION,
                b -> send(PartyActionPacket.Action.REQUEST_SYNC)));
        col += actionW + 6;
        addRenderableWidget(new AtlasButton(col, actionY, Component.literal("Leave"), ACTION,
                b -> send(PartyActionPacket.Action.LEAVE)));
        col += actionW + 6;
        addRenderableWidget(new AtlasButton(col, actionY, Component.literal("PvP"), ACTION,
                b -> send(PartyActionPacket.Action.TOGGLE_PVP)));
        col += actionW + 6;
        addRenderableWidget(new AtlasButton(col, actionY, Component.literal("Disband"), ACTION,
                b -> send(PartyActionPacket.Action.DISBAND)));

        int memberY = actionY + 30;
        addRenderableWidget(new AtlasButton(rightX, memberY, Component.literal("Promote"), ACTION,
                b -> memberAction(PartyActionPacket.Action.PROMOTE)));
        addRenderableWidget(new AtlasButton(rightX + actionW + 6, memberY,
                Component.literal("Kick"), ACTION,
                b -> memberAction(PartyActionPacket.Action.KICK)));

        // Quest share reads its current value from ClientParty and only ever asks the server to
        // toggle; the server reply is what actually changes the state this screen renders.
        addRenderableWidget(new AtlasToggle(rightX, memberY + 30, 190,
                ClientParty.state().shareQuests(), Component.literal("Quest share"),
                v -> send(PartyActionPacket.Action.TOGGLE_SHARE_QUESTS)));

        String pending = ClientParty.state().pendingInviteFrom();
        if (pending != null && !pending.isBlank()) {
            int inviteY = memberY + 62;
            addRenderableWidget(new AtlasButton(rightX, inviteY, Component.literal("Accept"),
                    PRIMARY, b -> send(PartyActionPacket.Action.ACCEPT)));
            addRenderableWidget(new AtlasButton(rightX + primaryW + 6, inviteY,
                    Component.literal("Accept + match"), "pill_button_lg",
                    b -> send(PartyActionPacket.Action.ACCEPT_CONFIRM)));
            addRenderableWidget(new AtlasButton(rightX, inviteY + 30, Component.literal("Decline"),
                    PRIMARY, b -> send(PartyActionPacket.Action.DECLINE)));
        }

        // Chat row along the bottom of the frame.
        int chatY = frameY + frameH - 40;
        chatBox = new EditBox(font, listX, chatY, Math.max(120, frameW - 180), 20,
                Component.literal("Party message"));
        chatBox.setHint(Component.literal("Party message"));
        chatBox.setMaxLength(256);
        addRenderableWidget(chatBox);
        addRenderableWidget(new AtlasButton(frameX + frameW - primaryW - 24, chatY - 2,
                Component.literal("Send"), PRIMARY,
                b -> sendText(PartyActionPacket.Action.CHAT, chatBox)));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xCC030712);

        beginUiScale(graphics);
        AtlasPanel.fittedInto(FRAME, frameX, frameY, getUiWidth() - 8, getUiHeight() - 8)
                .render(graphics);

        graphics.drawString(font, "XENOPARTY", frameX + 24, frameY + 18, 0xFF80D8FF, false);

        ClientParty.State state = ClientParty.state();
        long remaining = state.expiresAtMs() <= 0
                ? 0 : Math.max(0, state.expiresAtMs() - System.currentTimeMillis());
        String status = state.partyId() == null ? "Not currently in a party"
                : "Friendly fire: " + (state.friendlyFire() ? "ON" : "OFF")
                + "   Quest share: " + (state.shareQuests() ? "ON" : "OFF")
                + "   idle expiry " + (remaining / 60000) + ":"
                + String.format("%02d", (remaining / 1000) % 60);
        graphics.drawString(font, status, frameX + 24, frameY + 62, 0xFFB8D8EA, false);

        graphics.drawString(font, "Members", listX, listY - 14, 0xFFFFC14A, false);
        renderMembers(graphics, state);
        renderObjective(graphics, state);

        // Widgets sit in UI-canvas units, so hand them the mouse in the same units.
        super.render(graphics, (int) toUiX(mouseX), (int) toUiY(mouseY), partialTick);
        endUiScale(graphics);
    }

    private void renderMembers(GuiGraphics graphics, ClientParty.State state) {
        int y = listY;
        int shown = 0;
        for (PartySyncPacket.Member member : state.members()) {
            if (shown >= MAX_VISIBLE_MEMBERS) {
                graphics.drawString(font, "+" + (state.members().size() - shown) + " more",
                        listX + 4, y + 4, 0xFF6E8296, false);
                break;
            }
            boolean selected = member.id().equals(selectedMember);
            // The strip is one fixed-size sprite; selection is a lit outline over it rather than a
            // second, larger sprite, so the row never changes size as you click through the list.
            XenoAtlasSprites.blit(graphics, ROW, listX, y);
            if (selected) {
                graphics.renderOutline(listX, y, rowW, XenoAtlasSprites.get(ROW).height(),
                        0xFF80D8FF);
            }
            String prefix = member.leader() ? "§6* " : "§7  ";
            String line = prefix + (member.online() ? "§f" : "§8") + member.name()
                    + " §7Lv." + member.level()
                    + (member.form().isBlank() ? "" : " §b" + member.form());
            graphics.drawString(font, line, listX + 8, y + 7, 0xFFFFFFFF, false);
            y += ROW_PITCH;
            shown++;
        }
        if (shown == 0) {
            graphics.drawString(font, "No party members", listX + 8, listY + 7, 0xFF6E8296, false);
        }
    }

    private void renderObjective(GuiGraphics graphics, ClientParty.State state) {
        int y = listY + (MAX_VISIBLE_MEMBERS + 1) * ROW_PITCH;
        if (y > frameY + frameH - 90) {
            return;
        }
        if (state.objective() != null && state.objective().present()) {
            graphics.drawString(font, "Party Objective: " + state.objective().title(),
                    listX, y, 0xFFE5B8FF, false);
            graphics.drawString(font, state.objective().progress() + " / " + state.objective().goal()
                    + "  " + state.objective().state(), listX, y + 13, 0xFFCDB8DE, false);
        } else {
            new AtlasNotice("Party objective: not installed", listX, y).render(graphics, font);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            double uiX = toUiX(mouseX);
            double uiY = toUiY(mouseY);
            int y = listY;
            int rowH = XenoAtlasSprites.get(ROW).height();
            int shown = 0;
            for (PartySyncPacket.Member member : ClientParty.members()) {
                if (shown >= MAX_VISIBLE_MEMBERS) {
                    break;
                }
                if (uiX >= listX && uiX < listX + rowW && uiY >= y && uiY < y + rowH) {
                    selectedMember = member.id();
                    return true;
                }
                y += ROW_PITCH;
                shown++;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            if (chatBox != null && chatBox.isFocused() && !chatBox.getValue().isBlank()) {
                sendText(PartyActionPacket.Action.CHAT, chatBox);
                return true;
            }
            if (inviteBox != null && inviteBox.isFocused() && !inviteBox.getValue().isBlank()) {
                sendText(PartyActionPacket.Action.INVITE, inviteBox);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void send(PartyActionPacket.Action action) {
        ModNetwork.sendToServer(new PartyActionPacket(action));
    }

    private void sendText(PartyActionPacket.Action action, EditBox box) {
        String value = box.getValue().strip();
        if (value.isEmpty()) {
            return;
        }
        ModNetwork.sendToServer(new PartyActionPacket(action, value));
        box.setValue("");
    }

    private void memberAction(PartyActionPacket.Action action) {
        if (selectedMember != null) {
            ModNetwork.sendToServer(new PartyActionPacket(action, selectedMember));
        }
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

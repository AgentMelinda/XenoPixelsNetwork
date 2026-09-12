package net.bullettrain.xenopixelsmod.client.screen;

import net.bullettrain.xenopixelsmod.client.ClientParty;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.PartyActionPacket;
import net.bullettrain.xenopixelsmod.network.packet.PartySyncPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** Responsive social/management screen for the DMZ-backed XenoParty. */
public final class XenoPartyScreen extends UnblurredScreen {
    private final Screen parent;
    private EditBox inviteBox;
    private EditBox chatBox;
    private UUID selectedMember;

    public XenoPartyScreen(Screen parent) {
        super(Component.literal("XenoParty"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int panelW = Math.min(520, width - 30);
        int left = (width - panelW) / 2;
        int bottom = height - 34;

        addRenderableWidget(Button.builder(Component.literal("HUD layout"), b ->
                minecraft.setScreen(new XenoPartyHudEditScreen(this)))
                .bounds(left + 14, 22, 82, 20).build());

        inviteBox = new EditBox(font, left + 14, 48, Math.max(80, panelW - 112), 20,
                Component.literal("Player name"));
        inviteBox.setHint(Component.literal("Player name"));
        inviteBox.setMaxLength(64);
        addRenderableWidget(inviteBox);
        addRenderableWidget(Button.builder(Component.literal("Invite"), b -> sendText(
                PartyActionPacket.Action.INVITE, inviteBox)).bounds(left + panelW - 90, 48, 76, 20).build());

        chatBox = new EditBox(font, left + 14, bottom, Math.max(80, panelW - 112), 20,
                Component.literal("Party message"));
        chatBox.setHint(Component.literal("Party message"));
        chatBox.setMaxLength(256);
        addRenderableWidget(chatBox);
        addRenderableWidget(Button.builder(Component.literal("Send"), b -> sendText(
                PartyActionPacket.Action.CHAT, chatBox)).bounds(left + panelW - 90, bottom, 76, 20).build());

        int actionY = height - 60;
        addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> send(
                PartyActionPacket.Action.REQUEST_SYNC)).bounds(left + 14, actionY, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Leave"), b -> send(
                PartyActionPacket.Action.LEAVE)).bounds(left + 90, actionY, 62, 20).build());
        addRenderableWidget(Button.builder(Component.literal("PvP"), b -> send(
                PartyActionPacket.Action.TOGGLE_PVP)).bounds(left + 158, actionY, 40, 20).build());
        addRenderableWidget(Button.builder(
                Component.literal(ClientParty.state().shareQuests() ? "Quest share ON" : "Quest share OFF"),
                b -> send(PartyActionPacket.Action.TOGGLE_SHARE_QUESTS))
                .bounds(left + 202, actionY, 108, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Promote"), b -> memberAction(
                PartyActionPacket.Action.PROMOTE)).bounds(left + 314, actionY, 64, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Kick"), b -> memberAction(
                PartyActionPacket.Action.KICK)).bounds(left + 382, actionY, 44, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Disband"), b -> send(
                PartyActionPacket.Action.DISBAND)).bounds(left + panelW - 84, actionY, 70, 20).build());

        String pending = ClientParty.state().pendingInviteFrom();
        if (pending != null && !pending.isBlank()) {
            addRenderableWidget(Button.builder(Component.literal("Accept " + pending), b -> send(
                    PartyActionPacket.Action.ACCEPT)).bounds(left + 14, 74, 112, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Accept + match"), b -> send(
                    PartyActionPacket.Action.ACCEPT_CONFIRM)).bounds(left + 132, 74, 104, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Decline"), b -> send(
                    PartyActionPacket.Action.DECLINE)).bounds(left + 242, 74, 70, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xCC030712);
        int panelW = Math.min(520, width - 30);
        int left = (width - panelW) / 2;
        int top = 18;
        graphics.fill(left, top, left + panelW, height - 8, 0xE80A1428);
        graphics.renderOutline(left, top, panelW, height - top - 8, 0xFF1C8FEA);
        graphics.drawCenteredString(font, "XENOPARTY", width / 2, 26, 0xFF80D8FF);

        ClientParty.State state = ClientParty.state();
        long remaining = state.expiresAtMs() <= 0 ? 0 : Math.max(0, state.expiresAtMs() - System.currentTimeMillis());
        String status = state.partyId() == null ? "Not currently in a party"
                : "Friendly fire: " + (state.friendlyFire() ? "ON" : "OFF")
                + "  •  Quest share: " + (state.shareQuests() ? "ON" : "OFF")
                + "  •  idle expiry " + (remaining / 60000) + ":" + String.format("%02d", (remaining / 1000) % 60);
        graphics.drawString(font, status, left + 14, 100, 0xFFB8D8EA, false);

        int y = 118;
        for (PartySyncPacket.Member member : state.members()) {
            boolean selected = member.id().equals(selectedMember);
            int color = selected ? 0xBB174B70 : 0x99101E35;
            graphics.fill(left + 14, y, left + panelW - 14, y + 22, color);
            String prefix = member.leader() ? "§6★ " : "§7  ";
            String line = prefix + (member.online() ? "§f" : "§8") + member.name()
                    + " §7Lv." + member.level()
                    + (member.form().isBlank() ? "" : " §b" + member.form());
            graphics.drawString(font, line, left + 22, y + 7, 0xFFFFFFFF, false);
            y += 26;
        }

        if (state.objective() != null && state.objective().present()) {
            y += 4;
            graphics.fill(left + 14, y, left + panelW - 14, y + 34, 0xAA24133A);
            graphics.drawString(font, "Party Objective: " + state.objective().title(), left + 22, y + 6,
                    0xFFE5B8FF, false);
            graphics.drawString(font, state.objective().progress() + " / " + state.objective().goal()
                    + "  " + state.objective().state(), left + 22, y + 19, 0xFFCDB8DE, false);
        } else {
            graphics.drawString(font, "Quest/CustomNPC party objective integration: not installed",
                    left + 14, Math.min(y + 8, height - 90), 0xFF6E8296, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int panelW = Math.min(520, width - 30);
            int left = (width - panelW) / 2;
            int y = 118;
            for (PartySyncPacket.Member member : ClientParty.members()) {
                if (mouseX >= left + 14 && mouseX <= left + panelW - 14 && mouseY >= y && mouseY <= y + 22) {
                    selectedMember = member.id();
                    return true;
                }
                y += 26;
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
        if (value.isEmpty()) return;
        ModNetwork.sendToServer(new PartyActionPacket(action, value));
        box.setValue("");
    }

    private void memberAction(PartyActionPacket.Action action) {
        if (selectedMember != null) ModNetwork.sendToServer(new PartyActionPacket(action, selectedMember));
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

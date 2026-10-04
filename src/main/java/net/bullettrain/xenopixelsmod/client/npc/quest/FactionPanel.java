package net.bullettrain.xenopixelsmod.client.npc.quest;

import net.bullettrain.xenopixelsmod.client.npc.faction.ClientFactions;
import net.bullettrain.xenopixelsmod.client.npc.faction.ClientStandings;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.bullettrain.xenopixelsmod.npc.faction.XenoFaction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/**
 * The Factions tab body: a read-out of where the player stands.
 *
 * <p>Read-only on purpose. Standings move on NPC death, in combat, and through quest rewards - all
 * server-side - so a control here would be a widget with nothing behind it.
 *
 * <p>Built alongside the quest log rather than later so the tab group is right the first time,
 * instead of Quests sitting in slot two and moving once Factions arrives.
 */
public final class FactionPanel {

    private static final int PANEL_WIDTH = 248;
    private static final int PANEL_HEIGHT = 166;
    private static final int PADDING = 6;
    private static final int LINE_HEIGHT = 10;

    private FactionPanel() {
    }

    public static void render(GuiGraphics graphics, InventoryScreen inventory,
                              int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        int left = QuestLogLayout.panelLeft(inventory.getGuiLeft());
        int top = inventory.getGuiTop();

        XenoAtlasSprites.blit(graphics, "xeno_inventory_panel", left, top);
        graphics.drawString(minecraft.font, Component.literal("Faction Standings"),
                left + PADDING, top + PADDING, 0xFF35D7FF, false);

        if (ClientFactions.isEmpty()) {
            // Covers both "the pack defines none" and "the sync has not arrived". Either way there
            // is nothing to show, and saying so beats an empty rectangle that reads as broken.
            graphics.drawString(minecraft.font,
                    Component.literal("You have no standings with any faction."),
                    left + PADDING, top + PADDING + 16, 0xFF8FA3B4, false);
            return;
        }

        int y = top + PADDING + 14;
        for (ClientFactions.Entry faction : ClientFactions.all()) {
            // Bounded by the panel, not by the list: a pack with two hundred factions must not
            // draw rows down over the hotbar and the chat.
            if (y > top + PANEL_HEIGHT - LINE_HEIGHT) {
                break;
            }
            // The player's own standing, falling back to the faction's default for one they have
            // no stored standing with - the same rule XenoPlayerData.getFactionStanding applies on
            // the server. Reading defaultStanding() directly would show every player the same
            // numbers, which is wrong in a way nobody would notice.
            int standing = ClientStandings.of(faction.id(), faction.defaultStanding());
            XenoFaction.Attitude attitude = XenoFaction.attitudeAt(standing);
            graphics.drawString(minecraft.font, Component.literal(faction.name()),
                    left + PADDING, y, 0xFF000000 | faction.color(), false);
            graphics.drawString(minecraft.font, Component.literal(Integer.toString(standing)),
                    left + PANEL_WIDTH - 110, y, 0xFFD8E4EE, false);
            graphics.drawString(minecraft.font, Component.literal(name(attitude)),
                    left + PANEL_WIDTH - 70, y, colour(attitude), false);
            y += LINE_HEIGHT;
        }
    }

    private static String name(XenoFaction.Attitude attitude) {
        return switch (attitude) {
            case FRIENDLY -> "Friendly";
            case NEUTRAL -> "Neutral";
            case HOSTILE -> "Hostile";
        };
    }

    private static int colour(XenoFaction.Attitude attitude) {
        return switch (attitude) {
            case FRIENDLY -> 0xFF7CE08A;
            case NEUTRAL -> 0xFF8FA3B4;
            case HOSTILE -> 0xFFE06C6C;
        };
    }
}

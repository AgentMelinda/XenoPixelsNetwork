package net.bullettrain.xenopixelsmod.client.npc;

import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasButton;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The scripter tool's air menu: the CustomNPCs / MyNPCs scripter screen 1:1 - the 176x222 small
 * panel with Players and Forge at the top - in the blue DMZ atlas (mynpcs_small_panel,
 * mynpcs_button_100x20). Each opens the script editor on that store category, where "+" makes a
 * new tab. Saving still needs the scripter permission (level 4). The NPC script library stays in
 * the NPC editor's Global tab.
 */
public final class XenoScriptHubScreen extends Screen {
    private static final String PANEL = "mynpcs_small_panel";
    private static final String BUTTON = "mynpcs_button_100x20";
    private static final int W = 176;
    private static final int H = 222;

    private int left;
    private int top;

    public XenoScriptHubScreen() {
        super(Component.literal("Scripts"));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int bx = left + (W - 100) / 2;
        addRenderableWidget(new AtlasButton(bx, top + 19, Component.literal("Players"), BUTTON,
                b -> open(XenoNpcScriptScreen.playerScripts(this))));
        addRenderableWidget(new AtlasButton(bx, top + 49, Component.literal("Forge"), BUTTON,
                b -> open(XenoNpcScriptScreen.forgeScripts(this))));
    }

    private void open(Screen screen) {
        if (minecraft != null) minecraft.setScreen(screen);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        XenoAtlasSprites.blit(g, PANEL, left, top);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

package net.bullettrain.xenopixelsmod.client.screen;

import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.content.XenoContentCatalog;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class XenoMenuScreen extends Screen {
    private static final String SERVER_HOST = "xpn.co.il";

    private final Screen parent;

    public XenoMenuScreen(Screen parent) {
        super(Component.literal("XenoPixels"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (!XenoClientConfig.xenoMenuEnabled) {
            this.minecraft.setScreen(parent);
            return;
        }

        int cx = this.width / 2;
        int cy = this.height / 2;
        int y = cy - 84;
        XenoContentCatalog.Catalog catalog = XenoContentCatalog.get();

        if (XenoClientConfig.contentScreensEnabled) {
            this.addRenderableWidget(Button.builder(Component.literal("§bCHARACTER"), b ->
                            this.minecraft.setScreen(new XenoContentListScreen(this, "CHARACTER", catalog.character)))
                    .bounds(cx - 100, y, 200, 24).build());
            y += 30;
            this.addRenderableWidget(Button.builder(Component.literal("§eTRANSFORMS"), b ->
                            this.minecraft.setScreen(new XenoContentListScreen(this, "TRANSFORMS", catalog.transforms)))
                    .bounds(cx - 100, y, 200, 24).build());
            y += 30;
            this.addRenderableWidget(Button.builder(Component.literal("§aSKILLS"), b ->
                            this.minecraft.setScreen(new XenoContentListScreen(this, "SKILLS", catalog.skills)))
                    .bounds(cx - 100, y, 200, 24).build());
            y += 30;
        }

        if (XenoClientConfig.hudEditEnabled) {
            this.addRenderableWidget(Button.builder(Component.literal("§dHUD LAYOUT"), b ->
                            this.minecraft.setScreen(new XenoHudEditScreen(this)))
                    .bounds(cx - 100, y, 200, 24).build());
            y += 30;
            this.addRenderableWidget(Button.builder(Component.literal("§9TECH HUD LAYOUT"), b ->
                            this.minecraft.setScreen(new XenoHotbarEditScreen(this)))
                    .bounds(cx - 100, y, 200, 24).build());
            y += 30;
        }

        if (XenoClientConfig.joinServerButton) {
            this.addRenderableWidget(Button.builder(Component.literal("§6JOIN XenoPixels  §7(" + SERVER_HOST + ")"), b ->
                            connectToServer())
                    .bounds(cx - 100, y, 200, 24).build());
            y += 30;
        }

        this.addRenderableWidget(Button.builder(Component.literal("§cRETURN"), b -> this.minecraft.setScreen(parent))
                .bounds(cx - 100, y, 200, 24).build());
    }

    private void connectToServer() {
        ServerAddress address = ServerAddress.parseString(SERVER_HOST);
        ServerData data = new ServerData("XenoPixels", SERVER_HOST, false);
        ConnectScreen.startConnecting(this, this.minecraft, address, data, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xEE050510);
        graphics.fill(0, 0, 8, this.height, 0xFF1E88E5);
        graphics.fill(this.width - 8, 0, this.width, this.height, 0xFFE53935);
        graphics.drawCenteredString(this.font, "§lXenoPixels", this.width / 2, 30, 0xFF42A5F5);
        graphics.drawCenteredString(this.font, "§7Forms · Combat · HUD · Config — browse each category", this.width / 2, 48, 0xFFAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}

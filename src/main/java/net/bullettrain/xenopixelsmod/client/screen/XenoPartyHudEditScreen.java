package net.bullettrain.xenopixelsmod.client.screen;

import net.bullettrain.xenopixelsmod.client.XenoPartyOverlay;
import net.bullettrain.xenopixelsmod.client.config.XenoPartyHudConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Independent move/scale editor for the nearby-party card stack. */
public final class XenoPartyHudEditScreen extends UnblurredScreen {
    private enum DragTarget { NONE, CARDS, RESIZE }

    private final Screen parent;
    private DragTarget dragTarget = DragTarget.NONE;
    private int dragOffsetX;
    private int dragOffsetY;
    private int resizeStartX;
    private int resizeStartY;
    private float resizeStartScale;

    public XenoPartyHudEditScreen(Screen parent) {
        super(Component.literal("Edit Party HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        XenoPartyHudConfig.clampToScreen(width, height, 1);
        int cx = width / 2;
        addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
            XenoPartyHudConfig.reset();
            XenoPartyHudConfig.clampToScreen(width, height, 1);
        }).bounds(cx - 155, height - 28, 70, 20).build());
        addRenderableWidget(Button.builder(
                Component.literal(XenoPartyHudConfig.visible ? "Hide" : "Show"), b -> {
                    XenoPartyHudConfig.visible = !XenoPartyHudConfig.visible;
                    b.setMessage(Component.literal(XenoPartyHudConfig.visible ? "Hide" : "Show"));
                }).bounds(cx - 75, height - 28, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
            XenoPartyHudConfig.clampToScreen(width, height, 1);
            XenoPartyHudConfig.save();
            minecraft.setScreen(parent);
        }).bounds(cx + 5, height - 28, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
            XenoPartyHudConfig.load();
            minecraft.setScreen(parent);
        }).bounds(cx + 85, height - 28, 70, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xCC030712);
        XenoPartyOverlay.renderCards(graphics, true);
        graphics.drawCenteredString(font, "Drag party card to move • blue corner or wheel resizes",
                width / 2, 12, 0xFFB8D8EA);
        graphics.drawCenteredString(font, String.format("Pos %d,%d  Scale %.2fx",
                XenoPartyHudConfig.x, XenoPartyHudConfig.y, XenoPartyHudConfig.scale),
                width / 2, 26, 0xFF42A5F5);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hit((int) mouseX, (int) mouseY)) {
            if (hitResize((int) mouseX, (int) mouseY)) {
                dragTarget = DragTarget.RESIZE;
                resizeStartX = (int) mouseX;
                resizeStartY = (int) mouseY;
                resizeStartScale = XenoPartyHudConfig.scale;
            } else {
                dragTarget = DragTarget.CARDS;
                dragOffsetX = (int) mouseX - XenoPartyHudConfig.x;
                dragOffsetY = (int) mouseY - XenoPartyHudConfig.y;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button == 0 && dragTarget == DragTarget.CARDS) {
            XenoPartyHudConfig.x = (int) mouseX - dragOffsetX;
            XenoPartyHudConfig.y = (int) mouseY - dragOffsetY;
            XenoPartyHudConfig.clampToScreen(width, height, 1);
            return true;
        }
        if (button == 0 && dragTarget == DragTarget.RESIZE) {
            float delta = ((float) mouseX - resizeStartX + (float) mouseY - resizeStartY) * 0.0025f;
            XenoPartyHudConfig.scale = XenoPartyHudConfig.clampScale(resizeStartScale + delta);
            XenoPartyHudConfig.clampToScreen(width, height, 1);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) dragTarget = DragTarget.NONE;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (hit((int) mouseX, (int) mouseY)) {
            XenoPartyHudConfig.scale = XenoPartyHudConfig.clampScale(
                    XenoPartyHudConfig.scale + (float) scrollY * 0.02f);
            XenoPartyHudConfig.clampToScreen(width, height, 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        XenoPartyHudConfig.save();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private boolean hit(int mx, int my) {
        return mx >= XenoPartyHudConfig.x && my >= XenoPartyHudConfig.y
                && mx <= XenoPartyHudConfig.x + XenoPartyHudConfig.scaledWidth()
                && my <= XenoPartyHudConfig.y + XenoPartyHudConfig.scaledCardHeight();
    }

    private boolean hitResize(int mx, int my) {
        int right = XenoPartyHudConfig.x + XenoPartyHudConfig.scaledWidth();
        int bottom = XenoPartyHudConfig.y + XenoPartyHudConfig.scaledCardHeight();
        int handle = 14;
        return mx >= right - handle && my >= bottom - handle;
    }
}

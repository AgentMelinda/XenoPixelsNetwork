package net.bullettrain.xenopixelsmod.client.screen;

import net.bullettrain.xenopixelsmod.client.XenoHudOverlay;
import net.bullettrain.xenopixelsmod.client.config.XenoHudConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class XenoHudEditScreen extends UnblurredScreen {
    private enum DragTarget {
        NONE, HUD, RESIZE
    }

    private final Screen parent;
    private DragTarget dragTarget = DragTarget.NONE;
    private int dragOffsetX;
    private int dragOffsetY;
    private float resizeStartScale;
    private int resizeStartMouseX;
    private int resizeStartMouseY;

    public XenoHudEditScreen(Screen parent) {
        super(Component.literal("Edit HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        XenoHudConfig.clampToScreen(this.width, this.height);
        int cx = this.width / 2;

        this.addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
            XenoHudConfig.reset();
            XenoHudConfig.clampToScreen(this.width, this.height);
        }).bounds(cx - 155, this.height - 28, 70, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal(XenoHudConfig.visible ? "Hide HUD" : "Show HUD"),
                b -> {
                    XenoHudConfig.toggleVisible();
                    b.setMessage(Component.literal(XenoHudConfig.visible ? "Hide HUD" : "Show HUD"));
                }).bounds(cx - 75, this.height - 28, 70, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
            XenoHudConfig.clampToScreen(this.width, this.height);
            XenoHudConfig.save();
            this.minecraft.setScreen(parent);
        }).bounds(cx + 5, this.height - 28, 70, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
            XenoHudConfig.load();
            this.minecraft.setScreen(parent);
        }).bounds(cx + 85, this.height - 28, 70, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xAA050510);
        XenoHudOverlay.renderHud(graphics, this.width, this.height, true);

        graphics.drawCenteredString(this.font, "Drag HUD to move  |  Drag blue corner or scroll to resize",
                this.width / 2, 12, 0xFFAAAAAA);
        graphics.drawCenteredString(this.font,
                String.format("Pos %d,%d  Scale %.2fx", XenoHudConfig.x, XenoHudConfig.y, XenoHudConfig.scale),
                this.width / 2, 26, 0xFF42A5F5);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int mx = (int) mouseX;
            int my = (int) mouseY;

            if (hitResizeHandle(mx, my)) {
                dragTarget = DragTarget.RESIZE;
                resizeStartScale = XenoHudConfig.scale;
                resizeStartMouseX = mx;
                resizeStartMouseY = my;
                return true;
            }
            if (hitHud(mx, my)) {
                dragTarget = DragTarget.HUD;
                dragOffsetX = mx - XenoHudConfig.x;
                dragOffsetY = my - XenoHudConfig.y;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && dragTarget != DragTarget.NONE) {
            int mx = (int) mouseX;
            int my = (int) mouseY;

            if (dragTarget == DragTarget.HUD) {
                XenoHudConfig.x = mx - dragOffsetX;
                XenoHudConfig.y = my - dragOffsetY;
                XenoHudConfig.clampToScreen(this.width, this.height);
                return true;
            }
            if (dragTarget == DragTarget.RESIZE) {
                float scrollY = ((mx - resizeStartMouseX) + (my - resizeStartMouseY)) * 0.005f;
                XenoHudConfig.scale = XenoHudConfig.clampScale(resizeStartScale + scrollY);
                XenoHudConfig.clampToScreen(this.width, this.height);
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            dragTarget = DragTarget.NONE;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (hitHud((int) mouseX, (int) mouseY) || hitResizeHandle((int) mouseX, (int) mouseY)) {
            XenoHudConfig.scale = XenoHudConfig.clampScale(XenoHudConfig.scale + (float) scrollY * 0.05f);
            XenoHudConfig.clampToScreen(this.width, this.height);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        XenoHudConfig.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private boolean hitHud(int mx, int my) {
        return mx >= XenoHudConfig.x
                && my >= XenoHudConfig.y
                && mx <= XenoHudConfig.x + XenoHudConfig.scaledWidth()
                && my <= XenoHudConfig.y + XenoHudConfig.scaledHeight();
    }

    private boolean hitResizeHandle(int mx, int my) {
        int handle = Math.max(10, Math.round(10 * XenoHudConfig.scale));
        int right = XenoHudConfig.x + XenoHudConfig.scaledWidth();
        int bottom = XenoHudConfig.y + XenoHudConfig.scaledHeight();
        return mx >= right - handle && mx <= right && my >= bottom - handle && my <= bottom;
    }
}

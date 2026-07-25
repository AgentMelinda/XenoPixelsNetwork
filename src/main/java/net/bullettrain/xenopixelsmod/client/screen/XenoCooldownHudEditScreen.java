package net.bullettrain.xenopixelsmod.client.screen;

import net.bullettrain.xenopixelsmod.client.XenoCooldownHudOverlay;
import net.bullettrain.xenopixelsmod.client.config.XenoCooldownHudConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Drag / scroll editor for the combat cooldown HUD.
 */
@OnlyIn(Dist.CLIENT)
public class XenoCooldownHudEditScreen extends Screen {
    private final Screen parent;
    private boolean dragging;
    private int dragOffX;
    private int dragOffY;
    private boolean resizing;
    private float resizeStartScale;
    private int resizeStartMouseY;

    public XenoCooldownHudEditScreen(Screen parent) {
        super(Component.literal("Edit Cooldown HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int by = this.height - 52;

        // Row: Layout | Hide idle | Shape | Reset | Save | Cancel
        this.addRenderableWidget(Button.builder(
                        Component.literal(XenoCooldownHudConfig.horizontal ? "Layout: H" : "Layout: V"),
                        b -> {
                            XenoCooldownHudConfig.horizontal = !XenoCooldownHudConfig.horizontal;
                            b.setMessage(Component.literal(
                                    XenoCooldownHudConfig.horizontal ? "Layout: H" : "Layout: V"));
                        })
                .bounds(cx - 230, by, 68, 20).build());

        this.addRenderableWidget(Button.builder(
                        Component.literal(XenoCooldownHudConfig.showOnlyWhenActive ? "Hide idle: ON" : "Hide idle: OFF"),
                        b -> {
                            XenoCooldownHudConfig.showOnlyWhenActive = !XenoCooldownHudConfig.showOnlyWhenActive;
                            b.setMessage(Component.literal(
                                    XenoCooldownHudConfig.showOnlyWhenActive ? "Hide idle: ON" : "Hide idle: OFF"));
                        })
                .bounds(cx - 157, by, 90, 20).build());

        this.addRenderableWidget(Button.builder(
                        Component.literal(XenoCooldownHudConfig.squareShape ? "Shape: Sq" : "Shape: Pa"),
                        b -> {
                            XenoCooldownHudConfig.toggleSquareShape();
                            b.setMessage(Component.literal(
                                    XenoCooldownHudConfig.squareShape ? "Shape: Sq" : "Shape: Pa"));
                        })
                .bounds(cx - 62, by, 70, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
            XenoCooldownHudConfig.reset();
            rebuildWidgets();
        }).bounds(cx + 13, by, 50, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
            XenoCooldownHudConfig.save();
            this.minecraft.setScreen(parent);
        }).bounds(cx + 68, by, 50, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
            XenoCooldownHudConfig.load();
            this.minecraft.setScreen(parent);
        }).bounds(cx + 123, by, 55, 20).build());

        // Slot toggles row
        int ty = this.height - 28;
        addToggle(cx - 200, ty, 70, "Vanish", () -> XenoCooldownHudConfig.showVanish,
                v -> XenoCooldownHudConfig.showVanish = v);
        addToggle(cx - 125, ty, 70, "Chase", () -> XenoCooldownHudConfig.showChase,
                v -> XenoCooldownHudConfig.showChase = v);
        addToggle(cx - 50, ty, 70, "Back", () -> XenoCooldownHudConfig.showBackstep,
                v -> XenoCooldownHudConfig.showBackstep = v);
        addToggle(cx + 25, ty, 70, "Combo", () -> XenoCooldownHudConfig.showCombo,
                v -> XenoCooldownHudConfig.showCombo = v);
        addToggle(cx + 100, ty, 70, "Charge", () -> XenoCooldownHudConfig.showCharge,
                v -> XenoCooldownHudConfig.showCharge = v);
    }

    private void addToggle(int x, int y, int w, String name, BooleanSupplier get, BooleanConsumer set) {
        this.addRenderableWidget(Button.builder(
                        Component.literal(name + (get.getAsBoolean() ? " ✓" : " ✗")),
                        b -> {
                            set.accept(!get.getAsBoolean());
                            b.setMessage(Component.literal(name + (get.getAsBoolean() ? " ✓" : " ✗")));
                        })
                .bounds(x, y, w, 20).build());
    }

    @FunctionalInterface
    private interface BooleanSupplier {
        boolean getAsBoolean();
    }

    @FunctionalInterface
    private interface BooleanConsumer {
        void accept(boolean v);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xAA050510);
        XenoCooldownHudOverlay.renderEditorPreview(graphics, this.width, this.height);

        graphics.drawCenteredString(this.font,
                "Drag panel to move  |  Scroll or drag bottom edge to resize",
                this.width / 2, 12, 0xFFAAAAAA);
        graphics.drawCenteredString(this.font,
                String.format("Cooldown HUD  x=%d y=%d  scale=%.2fx  %s",
                        XenoCooldownHudConfig.x, XenoCooldownHudConfig.y, XenoCooldownHudConfig.scale,
                        XenoCooldownHudConfig.horizontal ? "horizontal" : "vertical"),
                this.width / 2, 26, 0xFF42A5F5);

        // Resize handle
        int[] b = XenoCooldownHudOverlay.bounds();
        if (b[2] > 0 && b[3] > 0) {
            graphics.fill(b[0] + b[2] - 10, b[1] + b[3] - 10, b[0] + b[2], b[1] + b[3], 0xFFFF9800);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int[] b = XenoCooldownHudOverlay.bounds();
            int mx = (int) mouseX;
            int my = (int) mouseY;
            if (b[2] > 0 && mx >= b[0] + b[2] - 12 && mx <= b[0] + b[2] + 4
                    && my >= b[1] + b[3] - 12 && my <= b[1] + b[3] + 4) {
                resizing = true;
                resizeStartScale = XenoCooldownHudConfig.scale;
                resizeStartMouseY = my;
                return true;
            }
            if (b[2] > 0 && mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3]) {
                dragging = true;
                dragOffX = mx - XenoCooldownHudConfig.x;
                dragOffY = my - XenoCooldownHudConfig.y;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        resizing = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            XenoCooldownHudConfig.x = (int) mouseX - dragOffX;
            XenoCooldownHudConfig.y = (int) mouseY - dragOffY;
            XenoCooldownHudConfig.clampToScreen(this.width, this.height);
            return true;
        }
        if (resizing) {
            float delta = ((int) mouseY - resizeStartMouseY) * 0.01f;
            XenoCooldownHudConfig.scale = XenoCooldownHudConfig.clampScale(resizeStartScale + delta);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int[] b = XenoCooldownHudOverlay.bounds();
        if (b[2] > 0 && mouseX >= b[0] && mouseX <= b[0] + b[2]
                && mouseY >= b[1] && mouseY <= b[1] + b[3]) {
            XenoCooldownHudConfig.scale = XenoCooldownHudConfig.clampScale(
                    XenoCooldownHudConfig.scale + (float) delta * 0.05f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

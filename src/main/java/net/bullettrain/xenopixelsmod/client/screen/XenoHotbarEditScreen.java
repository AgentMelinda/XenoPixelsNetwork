package net.bullettrain.xenopixelsmod.client.screen;

import net.bullettrain.xenopixelsmod.client.XenoTechniqueHotbarOverlay;
import net.bullettrain.xenopixelsmod.client.config.XenoHotbarConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Editor for the technique ("KI") hotbar panel and its ki attack charge meter —
 * same drag-to-move / scroll-or-corner-to-resize interaction as
 * {@link XenoHudEditScreen}, but driving {@link XenoHotbarConfig} and covering
 * two independently moveable/resizeable regions instead of one.
 */
@OnlyIn(Dist.CLIENT)
public class XenoHotbarEditScreen extends UnblurredScreen {
    private enum DragTarget {
        NONE, HOTBAR, HOTBAR_RESIZE, CHARGE, CHARGE_RESIZE
    }

    private final Screen parent;
    private DragTarget dragTarget = DragTarget.NONE;
    private int dragOffsetX;
    private int dragOffsetY;
    private float resizeStartScale;
    private int resizeStartMouseX;
    private int resizeStartMouseY;

    public XenoHotbarEditScreen(Screen parent) {
        super(Component.literal("Edit Technique HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        this.addRenderableWidget(Button.builder(Component.literal("Reset"), b -> XenoHotbarConfig.reset())
                .bounds(cx - 155, this.height - 28, 70, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
            XenoHotbarConfig.save();
            this.minecraft.setScreen(parent);
        }).bounds(cx - 75, this.height - 28, 70, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
            XenoHotbarConfig.load();
            this.minecraft.setScreen(parent);
        }).bounds(cx + 5, this.height - 28, 70, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xAA050510);

        // Force-render a preview of the hotbar panel + a fake charging meter so both can be
        // positioned without needing to actually hold Alt/Ctrl or be mid-technique-charge.
        XenoTechniqueHotbarOverlay.renderEditorPreview(graphics, this.width, this.height);

        graphics.drawCenteredString(this.font, "Drag panels to move  |  Drag orange corner or scroll to resize",
                this.width / 2, 12, 0xFFAAAAAA);
        graphics.drawCenteredString(this.font,
                String.format("Hotbar offset %d,%d  scale %.2fx", XenoHotbarConfig.hotbarOffsetX,
                        XenoHotbarConfig.hotbarOffsetY, XenoHotbarConfig.hotbarScale),
                this.width / 2, 26, 0xFF42A5F5);
        graphics.drawCenteredString(this.font,
                String.format("Ki charge meter offset %d,%d  scale %.2fx", XenoHotbarConfig.chargeOffsetX,
                        XenoHotbarConfig.chargeOffsetY, XenoHotbarConfig.chargeScale),
                this.width / 2, 38, 0xFFFFB74D);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int mx = (int) mouseX;
            int my = (int) mouseY;

            if (hitResizeHandle(mx, my, chargeBounds())) {
                dragTarget = DragTarget.CHARGE_RESIZE;
                resizeStartScale = XenoHotbarConfig.chargeScale;
                resizeStartMouseX = mx;
                resizeStartMouseY = my;
                return true;
            }
            if (hitResizeHandle(mx, my, hotbarBounds())) {
                dragTarget = DragTarget.HOTBAR_RESIZE;
                resizeStartScale = XenoHotbarConfig.hotbarScale;
                resizeStartMouseX = mx;
                resizeStartMouseY = my;
                return true;
            }
            if (hit(mx, my, chargeBounds())) {
                dragTarget = DragTarget.CHARGE;
                dragOffsetX = mx - XenoHotbarConfig.chargeOffsetX;
                dragOffsetY = my - XenoHotbarConfig.chargeOffsetY;
                return true;
            }
            if (hit(mx, my, hotbarBounds())) {
                dragTarget = DragTarget.HOTBAR;
                dragOffsetX = mx - XenoHotbarConfig.hotbarOffsetX;
                dragOffsetY = my - XenoHotbarConfig.hotbarOffsetY;
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

            switch (dragTarget) {
                case HOTBAR -> {
                    XenoHotbarConfig.hotbarOffsetX = mx - dragOffsetX;
                    XenoHotbarConfig.hotbarOffsetY = my - dragOffsetY;
                    return true;
                }
                case CHARGE -> {
                    XenoHotbarConfig.chargeOffsetX = mx - dragOffsetX;
                    XenoHotbarConfig.chargeOffsetY = my - dragOffsetY;
                    return true;
                }
                case HOTBAR_RESIZE -> {
                    float scrollY = ((mx - resizeStartMouseX) + (my - resizeStartMouseY)) * 0.005f;
                    XenoHotbarConfig.hotbarScale = XenoHotbarConfig.clampScale(resizeStartScale + scrollY);
                    return true;
                }
                case CHARGE_RESIZE -> {
                    float scrollY = ((mx - resizeStartMouseX) + (my - resizeStartMouseY)) * 0.005f;
                    XenoHotbarConfig.chargeScale = XenoHotbarConfig.clampScale(resizeStartScale + scrollY);
                    return true;
                }
                default -> {
                }
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
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (hit(mx, my, chargeBounds()) || hitResizeHandle(mx, my, chargeBounds())) {
            XenoHotbarConfig.chargeScale = XenoHotbarConfig.clampScale(XenoHotbarConfig.chargeScale + (float) scrollY * 0.05f);
            return true;
        }
        if (hit(mx, my, hotbarBounds()) || hitResizeHandle(mx, my, hotbarBounds())) {
            XenoHotbarConfig.hotbarScale = XenoHotbarConfig.clampScale(XenoHotbarConfig.hotbarScale + (float) scrollY * 0.05f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        XenoHotbarConfig.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private static int[] hotbarBounds() {
        return XenoTechniqueHotbarOverlay.hotbarBounds();
    }

    private static int[] chargeBounds() {
        return XenoTechniqueHotbarOverlay.chargeBounds();
    }

    private static boolean hit(int mx, int my, int[] bounds) {
        return mx >= bounds[0] && my >= bounds[1] && mx <= bounds[0] + bounds[2] && my <= bounds[1] + bounds[3];
    }

    private static boolean hitResizeHandle(int mx, int my, int[] bounds) {
        int handle = 12;
        int right = bounds[0] + bounds[2];
        int bottom = bounds[1] + bounds[3];
        return mx >= right - handle && mx <= right && my >= bottom - handle && my <= bottom;
    }
}

package net.bullettrain.xenopixelsmod.client.screen;

import net.bullettrain.xenopixelsmod.client.XenoPartyOverlay;
import net.bullettrain.xenopixelsmod.client.config.XenoPartyHudConfig;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasButton;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasNotice;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Independent move/scale editor for the nearby-party card stack.
 *
 * <h2>Why this one stays in real screen space</h2>
 * Unlike the NPC editor and the party screen, this screen is deliberately <em>not</em> built on
 * DragonMineZ's {@code ScaledScreen}. It edits {@link XenoPartyHudConfig#x} / {@code y} / {@code
 * scale}, which are real HUD coordinates, and it previews them by calling the live
 * {@link XenoPartyOverlay#renderCards} at those same coordinates. Introducing a virtual canvas here
 * would put the preview, the drag maths and the saved values in three different coordinate spaces.
 *
 * <p>So only the chrome changed: the four vanilla buttons became atlas buttons and the resize
 * corner got a real sprite. Drag, resize, wheel scaling and every
 * {@link XenoPartyHudConfig#clampToScreen} call are byte-for-byte what they were.
 */
public final class XenoPartyHudEditScreen extends UnblurredScreen {
    private static final String PRIMARY = "pill_button";
    private static final String HANDLE = "icon_slot_sm";

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

        int buttonW = AtlasButton.nativeWidth(PRIMARY);
        int gap = 8;
        int totalW = buttonW * 4 + gap * 3;
        int x = (width - totalW) / 2;
        int y = height - AtlasButton.nativeHeight(PRIMARY) - 10;

        addRenderableWidget(new AtlasButton(x, y, Component.literal("Reset"), PRIMARY, b -> {
            XenoPartyHudConfig.reset();
            XenoPartyHudConfig.clampToScreen(width, height, 1);
        }));
        x += buttonW + gap;

        addRenderableWidget(new AtlasButton(x, y,
                Component.literal(XenoPartyHudConfig.visible ? "Hide" : "Show"), PRIMARY, b -> {
            XenoPartyHudConfig.visible = !XenoPartyHudConfig.visible;
            b.setMessage(Component.literal(XenoPartyHudConfig.visible ? "Hide" : "Show"));
        }));
        x += buttonW + gap;

        addRenderableWidget(new AtlasButton(x, y, Component.literal("Save"), PRIMARY, b -> {
            XenoPartyHudConfig.clampToScreen(width, height, 1);
            XenoPartyHudConfig.save();
            minecraft.setScreen(parent);
        }));
        x += buttonW + gap;

        addRenderableWidget(new AtlasButton(x, y, Component.literal("Cancel"), PRIMARY, b -> {
            XenoPartyHudConfig.load();
            minecraft.setScreen(parent);
        }));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xCC030712);
        XenoPartyOverlay.renderCards(graphics, true);

        drawEditAffordances(graphics);

        new AtlasNotice(String.format("Pos %d,%d  Scale %.2fx", XenoPartyHudConfig.x,
                XenoPartyHudConfig.y, XenoPartyHudConfig.scale),
                (width - AtlasNotice.width()) / 2, 8).render(graphics, font);
        graphics.drawCenteredString(font, "Drag the card to move - blue corner or wheel resizes",
                width / 2, 8 + AtlasNotice.height() + 4, 0xFFB8D8EA);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** Outlines the draggable card and draws the resize corner the hint line refers to. */
    private void drawEditAffordances(GuiGraphics graphics) {
        int left = XenoPartyHudConfig.x;
        int top = XenoPartyHudConfig.y;
        int w = XenoPartyHudConfig.scaledWidth();
        int h = XenoPartyHudConfig.scaledCardHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        graphics.renderOutline(left, top, w, h, 0x8042A5F5);

        // The handle sprite is drawn at its native size, anchored to the same corner hitResize uses.
        int handleW = XenoAtlasSprites.get(HANDLE).width();
        int handleH = XenoAtlasSprites.get(HANDLE).height();
        XenoAtlasSprites.blit(graphics, HANDLE, left + w - handleW, top + h - handleH);
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
        if (button == 0) {
            dragTarget = DragTarget.NONE;
        }
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
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

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

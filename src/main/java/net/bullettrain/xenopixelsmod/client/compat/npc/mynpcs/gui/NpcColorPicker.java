package net.bullettrain.xenopixelsmod.client.compat.npc.mynpcs.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import espi.mynpcs.client.gui.SubGuiColorSelector;
import espi.mynpcs.shared.client.gui.components.GuiTextFieldNop;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/** CNPC's color palette with a precise cursor and a live selected-color swatch. */
public final class NpcColorPicker extends SubGuiColorSelector {
    private static final ResourceLocation PALETTE =
            ResourceLocation.fromNamespaceAndPath("customnpcs", "textures/gui/color.png");
    private static final int PALETTE_SIZE = 120;
    private static final int CLICKABLE_SIZE = 118;

    private int pointerX = PALETTE_SIZE / 2;
    private int pointerY = PALETTE_SIZE / 2;
    private int resolvedColor = -1;

    public NpcColorPicker(int color) {
        super(color & 0xFFFFFF);
    }

    @Override
    public void init() {
        super.init();
        resolvePointer(color);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if ((color & 0xFFFFFF) != resolvedColor) resolvePointer(color);

        int paletteX = guiLeft + 30;
        int paletteY = guiTop + 50;
        graphics.enableScissor(paletteX, paletteY,
                paletteX + PALETTE_SIZE, paletteY + PALETTE_SIZE);
        drawCursor(graphics, paletteX + pointerX, paletteY + pointerY, color & 0xFFFFFF);
        graphics.disableScissor();

        int previewY = guiTop + 176;
        graphics.fill(guiLeft + 29, previewY - 1, guiLeft + 147, previewY + 19, 0xCC15191F);
        graphics.fill(guiLeft + 32, previewY + 2, guiLeft + 56, previewY + 16, 0xFF000000);
        graphics.fill(guiLeft + 34, previewY + 4, guiLeft + 54, previewY + 14,
                0xFF000000 | color & 0xFFFFFF);
        graphics.drawString(Minecraft.getInstance().font,
                String.format("Selected #%06X", color & 0xFFFFFF),
                guiLeft + 62, previewY + 5, 0xFFF2F4F8, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int paletteX = guiLeft + 30;
        int paletteY = guiTop + 50;
        boolean paletteClick = mouseX >= paletteX && mouseX <= paletteX + CLICKABLE_SIZE - 1
                && mouseY >= paletteY && mouseY <= paletteY + CLICKABLE_SIZE - 1;
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (paletteClick) {
            pointerX = Math.max(0, Math.min(CLICKABLE_SIZE - 1, (int) mouseX - paletteX));
            pointerY = Math.max(0, Math.min(CLICKABLE_SIZE - 1, (int) mouseY - paletteY));
            resolvedColor = color & 0xFFFFFF;
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) {
            int paletteX = guiLeft + 30;
            int paletteY = guiTop + 50;
            if (mouseX >= paletteX && mouseX <= paletteX + CLICKABLE_SIZE - 1
                    && mouseY >= paletteY && mouseY <= paletteY + CLICKABLE_SIZE - 1) {
                return mouseClicked(mouseX, mouseY, button);
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        boolean handled = super.charTyped(codePoint, modifiers);
        if ((color & 0xFFFFFF) != resolvedColor) resolvePointer(color);
        return handled;
    }

    @Override
    public void unFocused(GuiTextFieldNop textField) {
        super.unFocused(textField);
        resolvePointer(color);
    }

    private void resolvePointer(int targetColor) {
        resolvedColor = targetColor & 0xFFFFFF;
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(PALETTE);
        if (resource.isEmpty()) return;
        try (InputStream stream = resource.get().open()) {
            BufferedImage image = ImageIO.read(stream);
            if (image == null) return;
            long bestDistance = Long.MAX_VALUE;
            int bestX = pointerX;
            int bestY = pointerY;
            int targetR = resolvedColor >>> 16 & 0xFF;
            int targetG = resolvedColor >>> 8 & 0xFF;
            int targetB = resolvedColor & 0xFF;
            for (int y = 0; y < CLICKABLE_SIZE; y++) {
                int sampleY = Math.min(image.getHeight() - 1, y * image.getHeight() / PALETTE_SIZE);
                for (int x = 0; x < CLICKABLE_SIZE; x++) {
                    int sampleX = Math.min(image.getWidth() - 1, x * image.getWidth() / PALETTE_SIZE);
                    int sample = image.getRGB(sampleX, sampleY);
                    int dr = (sample >>> 16 & 0xFF) - targetR;
                    int dg = (sample >>> 8 & 0xFF) - targetG;
                    int db = (sample & 0xFF) - targetB;
                    long distance = 2L * dr * dr + 4L * dg * dg + 3L * db * db;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestX = x;
                        bestY = y;
                    }
                }
            }
            pointerX = bestX;
            pointerY = bestY;
        } catch (IOException ignored) {
        }
    }

    private static void drawCursor(GuiGraphics graphics, int centerX, int centerY, int rgb) {
        drawDisc(graphics, centerX, centerY, 5, 0xD9000000);
        drawDisc(graphics, centerX, centerY, 4, 0xFFF7F8FA);
        drawDisc(graphics, centerX, centerY, 2, 0xFF000000 | rgb & 0xFFFFFF);
        graphics.fill(centerX, centerY, centerX + 1, centerY + 1, 0xFFFFFFFF);
    }

    private static void drawDisc(GuiGraphics graphics, int centerX, int centerY,
                                 int radius, int argb) {
        for (int dy = -radius; dy <= radius; dy++) {
            int halfWidth = (int) Math.floor(Math.sqrt(radius * radius - dy * dy));
            graphics.fill(centerX - halfWidth, centerY + dy,
                    centerX + halfWidth + 1, centerY + dy + 1, argb);
        }
    }
}

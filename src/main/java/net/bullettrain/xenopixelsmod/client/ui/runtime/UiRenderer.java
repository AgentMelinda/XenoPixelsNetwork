package net.bullettrain.xenopixelsmod.client.ui.runtime;

import net.bullettrain.xenopixelsmod.client.hud.HudDraw;
import net.bullettrain.xenopixelsmod.ui.UiBindResolve;
import net.bullettrain.xenopixelsmod.ui.UiBindingSource;
import net.bullettrain.xenopixelsmod.ui.UiColors;
import net.bullettrain.xenopixelsmod.ui.UiLaidOut;
import net.bullettrain.xenopixelsmod.ui.UiNode;
import net.bullettrain.xenopixelsmod.ui.UiNodeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class UiRenderer {
    private static final int COLOR_PANEL = 0xE6080B12;
    private static final int COLOR_TEXT = 0xFFE8F6FF;
    private static final int COLOR_BAR = 0xFFE53935;
    private static final int COLOR_BAR_EMPTY = 0xFF3A0808;
    private static final int COLOR_BUTTON = 0xFF122038;
    private static final int COLOR_ERROR = 0xFFFF5D5D;

    private UiRenderer() {
    }

    public static void draw(GuiGraphics graphics, UiLaidOut root, UiBindingSource source, boolean editor) {
        if (root == null || (root.source != null && !root.source.visible)) {
            return;
        }
        drawNode(graphics, root, source, editor);
    }

    private static void drawNode(GuiGraphics graphics, UiLaidOut box, UiBindingSource source, boolean editor) {
        UiNode node = box.source;
        if (node == null || !node.visible) {
            return;
        }
        UiNodeType type = UiNodeType.byName(node.type);
        int color = UiColors.parse(node.color, defaultColor(type));
        Font font = Minecraft.getInstance().font;
        switch (type == null ? UiNodeType.PANEL : type) {
            case PANEL, ROOT -> {
                if ((color >>> 24) != 0) {
                    HudDraw.fillRect(graphics, box.x, box.y, box.w, box.h, color);
                    HudDraw.borderRect(graphics, box.x, box.y, box.w, box.h, 0xFF3DE0FF, 1);
                }
            }
            case TEXT -> graphics.drawString(font, UiBindResolve.label(node, source),
                    box.x, box.y, color, false);
            case PROGRESS_BAR -> drawBar(graphics, font, box, node, source, color);
            case BUTTON -> {
                HudDraw.fillRect(graphics, box.x, box.y, box.w, box.h, color);
                HudDraw.borderRect(graphics, box.x, box.y, box.w, box.h, 0xFF5CE1FF, 1);
                graphics.drawString(font, UiBindResolve.label(node, source),
                        box.x + 4, box.y + Math.max(1, (box.h - 8) / 2), COLOR_TEXT, false);
            }
            case IMAGE, ICON -> drawImage(graphics, box, node, color);
            case PORTRAIT -> drawPortrait(graphics, box, color);
            case HBOX, VBOX -> {
            }
            case SCROLL -> {
                HudDraw.borderRect(graphics, box.x, box.y, box.w, box.h, 0xFF3DE0FF, 1);
            }
            case NAV_TAB -> {
                HudDraw.fillRect(graphics, box.x, box.y, box.w, box.h, 0xFF1A2438);
                HudDraw.borderRect(graphics, box.x, box.y, box.w, box.h, 0xFFFFC14A, 1);
                graphics.drawString(font, UiBindResolve.label(node, source),
                        box.x + 3, box.y + Math.max(1, (box.h - 8) / 2), 0xFFFFC14A, false);
            }
            case STAT_ROW -> {
                graphics.drawString(font, node.text == null ? "" : node.text,
                        box.x, box.y, 0xFF8AA4B8, false);
                graphics.drawString(font, UiBindResolve.label(node, source),
                        box.x + Math.max(48, box.w / 2), box.y, COLOR_TEXT, false);
            }
            case SKILL_SLOT -> {
                HudDraw.fillRect(graphics, box.x, box.y, box.w, box.h, 0xFF122038);
                HudDraw.borderRect(graphics, box.x, box.y, box.w, box.h, 0xFF5CE1FF, 1);
            }
        }
        if (editor) {
            HudDraw.borderRect(graphics, box.x, box.y, box.w, box.h, 0x66FFFFFF, 1);
        }
        if (type == UiNodeType.SCROLL) {
            graphics.enableScissor(box.x, box.y, box.x + box.w, box.y + box.h);
            for (UiLaidOut child : box.children) {
                drawNode(graphics, child, source, editor);
            }
            graphics.disableScissor();
            return;
        }
        for (UiLaidOut child : box.children) {
            drawNode(graphics, child, source, editor);
        }
    }

    private static void drawBar(GuiGraphics graphics, Font font, UiLaidOut box, UiNode node,
                                UiBindingSource source, int color) {
        HudDraw.fillRect(graphics, box.x, box.y, box.w, box.h, COLOR_BAR_EMPTY);
        double fill = UiBindResolve.bar(node, source);
        if (Double.isNaN(fill)) {
            graphics.drawString(font, UiBindResolve.label(node, source), box.x + 2, box.y + 1, COLOR_ERROR, false);
            return;
        }
        int inner = Math.max(0, (int) Math.round(box.w * Math.max(0.0, Math.min(1.0, fill))));
        HudDraw.fillRect(graphics, box.x, box.y, inner, box.h, color);
    }

    private static void drawPortrait(GuiGraphics graphics, UiLaidOut box, int fallback) {
        if (Minecraft.getInstance().player instanceof AbstractClientPlayer player) {
            int size = Math.max(8, Math.min(box.w, box.h));
            PlayerFaceRenderer.draw(graphics, player.getSkin().texture(), box.x, box.y, size);
            return;
        }
        HudDraw.fillRect(graphics, box.x, box.y, box.w, box.h, fallback);
    }

    private static void drawImage(GuiGraphics graphics, UiLaidOut box, UiNode node, int fallback) {
        ResourceLocation texture = node.texture == null || node.texture.isBlank()
                ? null : ResourceLocation.tryParse(node.texture);
        if (texture == null) {
            HudDraw.fillRect(graphics, box.x, box.y, box.w, box.h, fallback);
            return;
        }
        if (texture.getPath().contains("menunpc")) {
            graphics.blit(texture, box.x, box.y, 0.0F, 0.0F, box.w, box.h, 512, 512);
            return;
        }
        graphics.blit(texture, box.x, box.y, box.w, box.h, 0, 0, box.w, box.h, box.w, box.h);
    }

    private static int defaultColor(UiNodeType type) {
        if (type == null) {
            return COLOR_PANEL;
        }
        return switch (type) {
            case BUTTON -> COLOR_BUTTON;
            case PROGRESS_BAR -> COLOR_BAR;
            case TEXT -> COLOR_TEXT;
            default -> COLOR_PANEL;
        };
    }
}

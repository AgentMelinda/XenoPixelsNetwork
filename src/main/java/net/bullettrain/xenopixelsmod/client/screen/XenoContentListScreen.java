package net.bullettrain.xenopixelsmod.client.screen;

import net.bullettrain.xenopixelsmod.client.content.XenoContentCatalog;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class XenoContentListScreen extends UnblurredScreen {
    private final Screen parent;
    private final String heading;
    private final List<XenoContentCatalog.Entry> entries;
    private int selected;
    private int scroll;

    public XenoContentListScreen(Screen parent, String heading, List<XenoContentCatalog.Entry> entries) {
        super(Component.literal(heading));
        this.parent = parent;
        this.heading = heading;
        this.entries = entries == null ? List.of() : entries;
        this.selected = entries != null && !entries.isEmpty() ? 0 : -1;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.literal("§cRETURN"), b -> this.minecraft.setScreen(parent))
                .bounds(this.width / 2 - 60, this.height - 28, 120, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xEE050510);
        graphics.fill(0, 0, 8, this.height, 0xFF1E88E5);
        graphics.fill(this.width - 8, 0, this.width, this.height, 0xFFE53935);

        graphics.drawCenteredString(this.font, "§l" + heading, this.width / 2, 14, 0xFF42A5F5);
        graphics.drawCenteredString(this.font, "§7Custom DMZ server content", this.width / 2, 28, 0xFFAAAAAA);

        int listX = 24;
        int listY = 48;
        int listW = Math.min(220, this.width / 3);
        int rowH = 22;
        int visible = Math.max(1, (this.height - 90) / rowH);

        if (selected >= 0) {
            scroll = Math.max(0, Math.min(scroll, Math.max(0, entries.size() - visible)));
        }

        graphics.fill(listX - 4, listY - 4, listX + listW + 4, listY + visible * rowH + 4, 0xAA0A1020);

        for (int i = 0; i < visible; i++) {
            int idx = scroll + i;
            if (idx >= entries.size()) break;
            XenoContentCatalog.Entry e = entries.get(idx);
            int y = listY + i * rowH;
            boolean sel = idx == selected;
            boolean hover = mouseX >= listX && mouseX <= listX + listW && mouseY >= y && mouseY < y + rowH;
            int bg = sel ? 0xCC1E3A6A : (hover ? 0xAA152040 : 0x660A1228);
            graphics.fill(listX, y, listX + listW, y + rowH - 2, bg);
            graphics.fill(listX, y, listX + 3, y + rowH - 2, e.accent | 0xFF000000);
            graphics.drawString(this.font, e.title, listX + 8, y + 6, sel ? 0xFFFFFFFF : 0xFFDDDDDD, false);
        }

        // Detail panel
        int dx = listX + listW + 20;
        int dw = this.width - dx - 24;
        int dy = listY - 4;
        int dh = this.height - dy - 40;
        graphics.fill(dx, dy, dx + dw, dy + dh, 0xAA0A1020);
        graphics.fill(dx, dy, dx + 4, dy + dh, selected >= 0 ? (entries.get(selected).accent | 0xFF000000) : 0xFF42A5F5);

        if (selected >= 0 && selected < entries.size()) {
            XenoContentCatalog.Entry e = entries.get(selected);
            graphics.drawString(this.font, e.title, dx + 14, dy + 12, 0xFFFFFFFF, false);
            graphics.drawString(this.font, e.subtitle, dx + 14, dy + 28, e.accent | 0xFF000000, false);

            List<FormattedCharSequence> lines = this.font.split(Component.literal(e.detail), dw - 28);
            int ty = dy + 50;
            for (FormattedCharSequence line : lines) {
                if (ty > dy + dh - 16) break;
                graphics.drawString(this.font, line, dx + 14, ty, 0xFFCCCCCC);
                ty += 12;
            }
        } else {
            graphics.drawString(this.font, "No entries", dx + 14, dy + 14, 0xFF888888, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int listX = 24;
            int listY = 48;
            int listW = Math.min(220, this.width / 3);
            int rowH = 22;
            int visible = Math.max(1, (this.height - 90) / rowH);
            for (int i = 0; i < visible; i++) {
                int idx = scroll + i;
                if (idx >= entries.size()) break;
                int y = listY + i * rowH;
                if (mouseX >= listX && mouseX <= listX + listW && mouseY >= y && mouseY < y + rowH) {
                    selected = idx;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int visible = Math.max(1, (this.height - 90) / 22);
        if (entries.size() > visible) {
            scroll = (int) Math.max(0, Math.min(entries.size() - visible, scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}

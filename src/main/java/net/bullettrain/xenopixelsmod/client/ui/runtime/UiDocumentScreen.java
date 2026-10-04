package net.bullettrain.xenopixelsmod.client.ui.runtime;

import net.bullettrain.xenopixelsmod.client.XenoHudSnapshotFactory;
import net.bullettrain.xenopixelsmod.client.screen.UnblurredScreen;
import net.bullettrain.xenopixelsmod.ui.UiDocument;
import net.bullettrain.xenopixelsmod.ui.UiLaidOut;
import net.bullettrain.xenopixelsmod.ui.UiLayoutEngine;
import net.bullettrain.xenopixelsmod.ui.UiNodeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class UiDocumentScreen extends UnblurredScreen {
    private final UiDocument document;
    private UiLaidOut laidOut;

    public UiDocumentScreen(UiDocument document) {
        super(Component.literal(document == null || document.id == null ? "UI" : document.id));
        this.document = document;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        if (document == null) {
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        laidOut = UiLayoutEngine.layout(document, width, height);
        UiRenderer.draw(graphics, laidOut,
                new UiSnapshotBindings(XenoHudSnapshotFactory.capture(minecraft)), false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && laidOut != null) {
            UiLaidOut hit = UiLayoutEngine.hit(laidOut, (int) mouseX, (int) mouseY);
            if (hit != null && hit.source != null) {
                UiNodeType type = UiNodeType.byName(hit.source.type);
                if (type != null && type.firesClickAction()) {
                    UiActions.fire(hit.source.action);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (laidOut != null && document != null) {
            UiLaidOut hit = UiLayoutEngine.hit(laidOut, (int) mouseX, (int) mouseY);
            if (hit != null && hit.source != null && UiNodeType.byName(hit.source.type) == UiNodeType.SCROLL) {
                hit.source.scroll = Math.max(0, hit.source.scroll - (int) Math.round(scrollY * 8));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

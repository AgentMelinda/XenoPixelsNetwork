package net.bullettrain.xenopixelsmod.client.ui.atlas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * A previous / value / next control built from three native-size atlas cells.
 *
 * <p>The two arrows are {@code mynpcs_button_arrow} at 22x20 and the value face is
 * {@code mynpcs_button_row} at 64x22, each blitted at its own size and centred against the taller
 * of the two. Nothing is stretched, so the widget has one fixed width of 108px; callers lay out
 * around that rather than passing a width for it to scale into.
 */
public final class AtlasCycle extends AbstractWidget {
    private static final String ARROW = "mynpcs_button_arrow";
    private static final String FACE = "mynpcs_button_row";

    private final int arrowW;
    private final int arrowH;
    private final int faceW;
    private final int faceH;
    private final List<String> values;
    private Component narrationLabel;
    private final Consumer<String> onChange;
    private final IntConsumer onIndex;
    private int index;
    private float groupScale = Float.NaN;
    private String generatedOverflowTooltip;

    public AtlasCycle(int x, int y, Component label, List<String> values, int initialIndex,
                      Consumer<String> onChange) {
        this(x, y, label, values, initialIndex, onChange, null);
    }

    /**
     * Index-based variant. Prefer this when the value list can contain duplicates: looking the
     * chosen string back up with {@code indexOf} would resolve every duplicate to the first match.
     */
    public AtlasCycle(int x, int y, Component label, List<String> values, int initialIndex,
                      IntConsumer onIndex) {
        this(x, y, label, values, initialIndex, null, onIndex);
    }

    private AtlasCycle(int x, int y, Component label, List<String> values, int initialIndex,
                       Consumer<String> onChange, IntConsumer onIndex) {
        super(x, y,
                XenoAtlasSprites.get(ARROW).width() * 2 + XenoAtlasSprites.get(FACE).width(),
                Math.max(XenoAtlasSprites.get(ARROW).height(), XenoAtlasSprites.get(FACE).height()),
                label);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("AtlasCycle requires values");
        }
        this.arrowW = XenoAtlasSprites.get(ARROW).width();
        this.arrowH = XenoAtlasSprites.get(ARROW).height();
        this.faceW = XenoAtlasSprites.get(FACE).width();
        this.faceH = XenoAtlasSprites.get(FACE).height();
        this.values = List.copyOf(values);
        this.index = Math.floorMod(initialIndex, values.size());
        this.narrationLabel = label;
        this.onChange = onChange;
        this.onIndex = onIndex;
        refreshNarrationMessage();
    }

    /** Fixed on-screen width of the control, since it never scales. */
    public static int nativeWidth() {
        return XenoAtlasSprites.get(ARROW).width() * 2 + XenoAtlasSprites.get(FACE).width();
    }

    public String value() {
        return values.get(index);
    }

    public AtlasCycle groupTextScale(float scale) {
        this.groupScale = scale;
        return this;
    }

    public AtlasCycle narrationLabel(Component label) {
        this.narrationLabel = label;
        refreshNarrationMessage();
        return this;
    }

    public int index() {
        return index;
    }

    public void cycle(int delta) {
        index = Math.floorMod(index + delta, values.size());
        refreshNarrationMessage();
        if (onChange != null) {
            onChange.accept(value());
        }
        if (onIndex != null) {
            onIndex.accept(index);
        }
    }

    private void refreshNarrationMessage() {
        String label = narrationLabel.getString();
        setMessage(Component.literal(label.isBlank() ? value() : label + ": " + value()));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        int arrowY = getY() + (getHeight() - arrowH) / 2;
        int faceY = getY() + (getHeight() - faceH) / 2;
        int faceX = getX() + arrowW;
        int rightX = getX() + arrowW + faceW;

        XenoAtlasSprites.blit(graphics, ARROW, getX(), arrowY);
        XenoAtlasSprites.blit(graphics, FACE, faceX, faceY);
        XenoAtlasSprites.blit(graphics, ARROW, rightX, arrowY);

        graphics.drawCenteredString(font, "<", getX() + arrowW / 2, arrowY + (arrowH - 8) / 2,
                0xFFFFFFFF);
        graphics.drawCenteredString(font, ">", rightX + arrowW / 2, arrowY + (arrowH - 8) / 2,
                0xFFFFFFFF);

        int available = faceW - 8;
        float scale = Float.isNaN(groupScale) ? AtlasTextFit.scale(font.width(value()), available)
                : groupScale;
        String text = AtlasTextFit.fit(value(), available, scale, font::width);
        if (!text.equals(value()) && (getTooltip() == null || generatedOverflowTooltip != null)) {
            setTooltip(Tooltip.create(Component.literal(value())));
            generatedOverflowTooltip = value();
        } else if (text.equals(value()) && generatedOverflowTooltip != null) {
            setTooltip(null);
            generatedOverflowTooltip = null;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(faceX + faceW / 2.0, faceY + (faceH - 8) / 2.0, 0.0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawCenteredString(font, text, 0, 0, 0xFFE0F0FF);
        graphics.pose().popPose();
    }


    /**
     * Silences vanilla's click.
     *
     * <p>{@code AbstractWidget.mouseClicked} plays {@code UI_BUTTON_CLICK} before {@code onClick}
     * runs, so without this every control would click twice - vanilla's, then DragonMineZ's. The
     * DMZ sound is played from {@code onClick} instead, via {@link AtlasSound}.
     */
    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager handler) {
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        // Only the arrow cells step the value and make a sound; the face reads as a display,
        // not a button, so clicking it is deliberately inert.
        double local = mouseX - getX();
        if (local < arrowW) {
            AtlasSound.click();
            cycle(-1);
        } else if (local >= arrowW + faceW) {
            AtlasSound.click();
            cycle(1);
        }
    }
}

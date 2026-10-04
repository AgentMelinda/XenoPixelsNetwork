package net.bullettrain.xenopixelsmod.client.ui.atlas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/**
 * Arrow control for a bounded whole number.
 *
 * <p>Looks and behaves like {@link AtlasCycle} - the same native arrow and face cells, so nothing is
 * stretched - but steps an integer rather than walking a list of known values.
 *
 * <p>Use it only where a real list genuinely cannot be obtained. Appearance parts are no longer such
 * a case - {@code NpcAppearanceParts} counts them through DragonMineZ's {@code TextureCounter}, so
 * those are proper cycles - but this remains the honest control for a bounded number whose options
 * cannot be enumerated, such as a part count during a resource reload.
 */
public final class AtlasStepper extends AbstractWidget {
    private static final String ARROW = "mynpcs_button_arrow";
    private static final String FACE = "mynpcs_button_row";

    private final int arrowW;
    private final int arrowH;
    private final int faceW;
    private final int faceH;
    private final int min;
    private final int max;
    private Component narrationLabel;
    private final IntConsumer onChange;
    private int value;
    private float groupScale = Float.NaN;
    private String generatedOverflowTooltip;

    public AtlasStepper(int x, int y, Component label, int value, int min, int max,
                        IntConsumer onChange) {
        super(x, y,
                XenoAtlasSprites.get(ARROW).width() * 2 + XenoAtlasSprites.get(FACE).width(),
                Math.max(XenoAtlasSprites.get(ARROW).height(), XenoAtlasSprites.get(FACE).height()),
                label);
        this.arrowW = XenoAtlasSprites.get(ARROW).width();
        this.arrowH = XenoAtlasSprites.get(ARROW).height();
        this.faceW = XenoAtlasSprites.get(FACE).width();
        this.faceH = XenoAtlasSprites.get(FACE).height();
        this.min = min;
        this.max = Math.max(min, max);
        this.value = clamp(value);
        this.narrationLabel = label;
        this.onChange = onChange;
        refreshNarrationMessage();
    }

    /** Fixed on-screen width, matching {@link AtlasCycle} so the two line up in a column. */
    public static int nativeWidth() {
        return AtlasCycle.nativeWidth();
    }

    public int value() {
        return value;
    }

    public AtlasStepper groupTextScale(float scale) {
        this.groupScale = scale;
        return this;
    }

    public AtlasStepper narrationLabel(Component label) {
        this.narrationLabel = label;
        refreshNarrationMessage();
        return this;
    }

    private int clamp(int candidate) {
        return Math.max(min, Math.min(max, candidate));
    }

    public void step(int delta) {
        int next = clamp(value + delta);
        if (next == value) {
            return;
        }
        value = next;
        refreshNarrationMessage();
        if (onChange != null) {
            onChange.accept(value);
        }
    }

    private void refreshNarrationMessage() {
        String label = narrationLabel.getString();
        setMessage(Component.literal(label.isBlank() ? Integer.toString(value)
                : label + ": " + value));
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

        // A bound that has been reached is dimmed, so the control says when it will not move.
        graphics.drawCenteredString(font, "<", getX() + arrowW / 2, arrowY + (arrowH - 8) / 2,
                value > min ? 0xFFFFFFFF : 0xFF6E8296);
        graphics.drawCenteredString(font, ">", rightX + arrowW / 2, arrowY + (arrowH - 8) / 2,
                value < max ? 0xFFFFFFFF : 0xFF6E8296);
        String fullValue = Integer.toString(value);
        int available = faceW - 8;
        float scale = Float.isNaN(groupScale) ? AtlasTextFit.scale(font.width(fullValue), available)
                : groupScale;
        String shownValue = AtlasTextFit.fit(fullValue, available, scale, font::width);
        if (!shownValue.equals(fullValue)
                && (getTooltip() == null || generatedOverflowTooltip != null)) {
            setTooltip(Tooltip.create(Component.literal(fullValue)));
            generatedOverflowTooltip = fullValue;
        } else if (shownValue.equals(fullValue) && generatedOverflowTooltip != null) {
            setTooltip(null);
            generatedOverflowTooltip = null;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(faceX + faceW / 2.0, faceY + (faceH - 8) / 2.0, 0.0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawCenteredString(font, shownValue, 0, 0, 0xFFE0F0FF);
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
        double local = mouseX - getX();
        if (local < arrowW) {
            AtlasSound.click();
            step(-1);
        } else if (local >= arrowW + faceW) {
            AtlasSound.click();
            step(1);
        }
    }
}

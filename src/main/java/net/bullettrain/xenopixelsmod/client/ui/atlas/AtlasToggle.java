package net.bullettrain.xenopixelsmod.client.ui.atlas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * A boolean control drawn as one native-size atlas cell plus an optional trailing label.
 *
 * <p>State is carried by palette, not by geometry. The badge is always {@code mynpcs_button_row} at
 * its native 64x22 and only its colour cut changes - green for yes, red for no. Two earlier
 * approaches were wrong for this art: swapping {@code hex_badge} (40x40) for {@code hex_badge_lg}
 * (64x64) made the control resize as you clicked it, and stretching either badge into an arbitrary
 * row height distorts a border whose proportions are baked into the PNG at generation time.
 */
public final class AtlasToggle extends AbstractWidget {
    private static final String CELL = "mynpcs_button_row";

    private final int cellW;
    private final int cellH;
    private final Component visualLabel;
    private Component narrationLabel;
    private boolean value;
    private final Consumer<Boolean> onChange;
    private float groupScale = Float.NaN;

    public AtlasToggle(int x, int y, boolean initial, Component message, Consumer<Boolean> onChange) {
        this(x, y, XenoAtlasSprites.get(CELL).width(), initial, message, onChange);
    }

    /**
     * @param width total clickable width. The badge itself always renders at its native size; any
     *              extra width is label area, so the widget stays easy to hit without the art
     *              being scaled.
     */
    public AtlasToggle(int x, int y, int width, boolean initial, Component message,
                       Consumer<Boolean> onChange) {
        super(x, y, Math.max(XenoAtlasSprites.get(CELL).width(), width),
                XenoAtlasSprites.get(CELL).height(), message);
        this.cellW = XenoAtlasSprites.get(CELL).width();
        this.cellH = XenoAtlasSprites.get(CELL).height();
        this.value = initial;
        this.visualLabel = message;
        this.narrationLabel = message;
        this.onChange = onChange;
        refreshNarrationMessage();
    }

    public boolean value() {
        return value;
    }

    public AtlasToggle groupTextScale(float scale) {
        this.groupScale = scale;
        return this;
    }

    /** Sets a screen's external row label for accessibility without drawing it a second time. */
    public AtlasToggle narrationLabel(Component label) {
        this.narrationLabel = label;
        refreshNarrationMessage();
        return this;
    }

    /** Sets the value without firing the callback - for rebuilding a screen from current state. */
    public void setValueQuietly(boolean next) {
        this.value = next;
        refreshNarrationMessage();
    }

    public void setValue(boolean next) {
        this.value = next;
        refreshNarrationMessage();
        if (onChange != null) {
            onChange.accept(next);
        }
    }

    private void refreshNarrationMessage() {
        String label = narrationLabel.getString();
        String state = value ? "Yes" : "No";
        setMessage(Component.literal(label.isBlank() ? state : label + ": " + state));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        XenoAtlasSprites.Theme palette =
                value ? XenoAtlasSprites.Theme.GREEN : XenoAtlasSprites.Theme.RED;
        XenoAtlasSprites.blit(graphics, CELL, palette, getX(), getY());
        String state = value ? "Yes" : "No";
        int labelAvailable = Math.max(0, getWidth() - cellW - 10);
        int stateAvailable = cellW - 8;
        float scale = Float.isNaN(groupScale)
                ? AtlasTextFit.groupScale(java.util.List.of(
                    new AtlasTextFit.Measure(font.width(state), stateAvailable),
                    new AtlasTextFit.Measure(font.width(visualLabel), labelAvailable)),
                    AtlasTextFit.MIN_SCALE)
                : groupScale;
        String visibleState = AtlasTextFit.fit(state, stateAvailable, scale, font::width);
        String visibleLabel = AtlasTextFit.fit(visualLabel.getString(), labelAvailable, scale,
                font::width);
        if (!visibleLabel.equals(visualLabel.getString()) && getTooltip() == null) {
            setTooltip(Tooltip.create(visualLabel));
        }
        graphics.pose().pushPose();
        graphics.pose().translate(getX() + cellW / 2.0, getY() + (cellH - 8) / 2.0, 0.0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawCenteredString(font, visibleState, 0, 0,
                isHoveredOrFocused() ? 0xFFFFFFFF : 0xFFEFF6FB);
        graphics.pose().popPose();

        if (getWidth() > cellW + 8 && !visualLabel.getString().isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(getX() + cellW + 6, getY() + (cellH - 8) / 2.0, 0.0);
            graphics.pose().scale(scale, scale, 1.0f);
            graphics.drawString(font, visibleLabel, 0, 0, 0xFFE0F0FF, false);
            graphics.pose().popPose();
        }
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
        AtlasSound.click();
        setValue(!value);
    }
}

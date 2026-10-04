package net.bullettrain.xenopixelsmod.client.npc;

import com.dragonminez.client.gui.character.util.ScaledScreen;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasButton;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasTextFit;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasPanel;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/** Registry-backed sound selector for the native NPC editor. */
public final class XenoNpcSoundPickerScreen extends ScaledScreen {

    /** Drawn at the vanilla GUI Scale; see {@link NpcGuiScale}. */
    @Override
    protected float computeDynamicScale(float available) {
        return NpcGuiScale.dynamicScale(super.computeDynamicScale(available));
    }
    private static final String FRAME = "xeno_editor_panel";
    private static final String SOUND_BUTTON = "pill_button_lg";
    private static final String PRIMARY = "pill_button";
    private static final String ARROW = "mynpcs_button_arrow";
    private static final int COLUMNS = 3;
    private static final int ROWS = 5;
    private static final int PAGE_SIZE = COLUMNS * ROWS;
    private static final int GOLD = 0xFFFFC14A;
    private static final int LIGHT = 0xFFE7EDF3;
    private static final int MUTED = 0xFF8AA4B8;

    private final Screen parent;
    private final String initial;
    private final Consumer<String> onSelected;
    private final List<String> soundIds;

    private int frameX;
    private int frameY;
    private int frameW;
    private int frameH;
    private int page;
    private int pageCount = 1;

    public XenoNpcSoundPickerScreen(Screen parent, String initial, Consumer<String> onSelected) {
        super(Component.literal("Select Sound"));
        this.parent = parent;
        this.initial = initial == null ? "" : initial;
        this.onSelected = onSelected == null ? ignored -> { } : onSelected;
        this.soundIds = NpcSoundCatalog.registryIds();
    }

    @Override
    protected void init() {
        super.init();
        int[] fitted = XenoAtlasSprites.fittedSize(FRAME, getUiWidth() - 8, getUiHeight() - 8);
        frameW = fitted[0];
        frameH = fitted[1];
        frameX = (getUiWidth() - frameW) / 2;
        frameY = (getUiHeight() - frameH) / 2;
        pageCount = Math.max(1, (soundIds.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pageCount - 1));
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        int buttonW = XenoAtlasSprites.get(SOUND_BUTTON).width();
        int buttonH = XenoAtlasSprites.get(SOUND_BUTTON).height();
        int columnGap = 12;
        int rowGap = 5;
        int gridW = COLUMNS * buttonW + (COLUMNS - 1) * columnGap;
        int startX = frameX + (frameW - gridW) / 2;
        int startY = frameY + 42;
        int start = page * PAGE_SIZE;
        int end = Math.min(soundIds.size(), start + PAGE_SIZE);

        java.util.List<AtlasTextFit.Measure> soundMeasures = soundIds.subList(start, end).stream()
                .map(id -> new AtlasTextFit.Measure(font.width(shortLabel(id)), buttonW - 8))
                .toList();
        float soundScale = AtlasTextFit.groupScale(soundMeasures, AtlasTextFit.MIN_SCALE);

        for (int index = start; index < end; index++) {
            String soundId = soundIds.get(index);
            int slot = index - start;
            int x = startX + (slot % COLUMNS) * (buttonW + columnGap);
            int y = startY + (slot / COLUMNS) * (buttonH + rowGap);
            AtlasButton button = new AtlasButton(x, y, Component.literal(shortLabel(soundId)),
                    SOUND_BUTTON, ignored -> select(soundId), soundScale);
            button.setTooltip(Tooltip.create(Component.literal(soundId)));
            addRenderableWidget(button);
        }

        int footerY = frameY + frameH - 32;
        addRenderableWidget(new AtlasButton(frameX + 18, footerY, Component.literal("Back"),
                PRIMARY, ignored -> onClose()));
        addRenderableWidget(new AtlasButton(frameX + 114, footerY, Component.literal("Clear"),
                PRIMARY, ignored -> select("")));

        if (pageCount > 1) {
            int arrowW = XenoAtlasSprites.get(ARROW).width();
            int nextX = frameX + frameW - 18 - arrowW;
            addRenderableWidget(new AtlasButton(nextX - arrowW - 4, footerY + 2,
                    Component.literal("<"), ARROW, ignored -> turnPage(-1)));
            addRenderableWidget(new AtlasButton(nextX, footerY + 2,
                    Component.literal(">"), ARROW, ignored -> turnPage(1)));
        }
    }

    private static String shortLabel(String soundId) {
        return soundId.length() <= 23 ? soundId : soundId.substring(0, 20) + "...";
    }

    private void select(String soundId) {
        onSelected.accept(soundId);
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private void turnPage(int delta) {
        page = Math.max(0, Math.min(page + delta, pageCount - 1));
        rebuild();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA0000000);
        beginUiScale(graphics);
        AtlasPanel.fittedInto(FRAME, frameX, frameY, getUiWidth() - 8, getUiHeight() - 8)
                .render(graphics);
        graphics.drawString(font, "Select registered sound", frameX + 18, frameY + 10,
                GOLD, false);
        graphics.drawString(font, initial.isBlank() ? "Current: entity default"
                        : "Current: " + initial,
                frameX + 178, frameY + 10, LIGHT, false);
        graphics.drawCenteredString(font, "Page " + (page + 1) + " / " + pageCount,
                frameX + frameW - 92, frameY + frameH - 25, MUTED);
        if (soundIds.isEmpty()) {
            graphics.drawCenteredString(font, "No registered sounds", frameX + frameW / 2,
                    frameY + frameH / 2, MUTED);
        }
        super.render(graphics, (int) toUiX(mouseX), (int) toUiY(mouseY), partialTick);
        endUiScale(graphics);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (pageCount > 1) {
            turnPage(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}

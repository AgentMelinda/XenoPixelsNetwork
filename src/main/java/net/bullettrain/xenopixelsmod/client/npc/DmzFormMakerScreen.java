package net.bullettrain.xenopixelsmod.client.npc;

import com.dragonminez.client.gui.character.util.ScaledScreen;
import net.bullettrain.xenopixelsmod.client.compat.npc.mynpcs.gui.GuiNpcDmzFormEditor;
import net.bullettrain.xenopixelsmod.client.npc.editor.EditorFooter;
import net.bullettrain.xenopixelsmod.client.npc.editor.EditorLayout;
import net.bullettrain.xenopixelsmod.client.npc.editor.EditorRow;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasButton;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasCycle;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasPanel;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormDocument;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormEditorClientState;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormKind;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormSaveGate;
import net.bullettrain.xenopixelsmod.network.form.FormEditorNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Native form maker for DragonMineZ form definitions, reached from the NPC editor's Forms tab.
 *
 * It edits a {@link DmzFormDocument} in place and saves through the existing
 * {@link FormEditorNetwork} channel with the same debounced, revision-checked flow the compat
 * {@link GuiNpcDmzFormEditor} uses: every keystroke touches the save gate, {@link #tick()} polls
 * {@link DmzFormEditorClientState} for the server result, and the server keeps one pre-edit backup
 * per file for the lifetime of this screen's session id. Saving requires operator level 2; the
 * server's refusal is surfaced verbatim on the status line.
 */
public final class DmzFormMakerScreen extends ScaledScreen {

    /** Drawn at the vanilla GUI Scale; see {@link NpcGuiScale}. */
    @Override
    protected float computeDynamicScale(float available) {
        return NpcGuiScale.dynamicScale(super.computeDynamicScale(available));
    }
    /** Inline colour picker shared by every colour row on this screen; opens over it. */
    private final net.bullettrain.xenopixelsmod.client.ui.atlas.InlineColorPicker colorPicker = new net.bullettrain.xenopixelsmod.client.ui.atlas.InlineColorPicker();
    private static final String FRAME = "xeno_editor_panel_w420";
    private static final String PRIMARY = "pill_button";
    private static final String ARROW = "mynpcs_button_arrow";

    private static final int ROW_H = 24;
    private static final int COLUMN_GAP = 6;
    private static final int CONTROL_DX = 78;
    private static final int FIELD_H = 18;
    private static final int HEADER_H = 46;
    private static final int FOOTER_H = 38;

    private static final int GOLD = 0xFFFFC14A;
    private static final int CYAN = 0xFF80D8FF;
    private static final int MUTED = 0xFF8AA4B8;
    private static final int LIGHT = 0xFFE7EDF3;
    private static final int WARN = 0xFFFF8A80;

    private final Screen parent;
    private final DmzFormDocument document;
    /** One snapshot per edited file per screen instance, matching the compat editor's contract. */
    private final String sessionId = "xenonpc-form-editor-" + UUID.randomUUID();
    private final DmzFormSaveGate saveGate =
            new DmzFormSaveGate(GuiNpcDmzFormEditor.SAVE_DEBOUNCE_MS);

    private int seenResult;
    private String status = "Edits save automatically after a short pause.";
    private int statusColor = MUTED;

    private List<EditorLayout.Placed> placed = List.of();
    private int page;
    private int pageCount = 1;
    private int frameX;
    private int frameY;
    private int frameW;
    private int frameH;

    public DmzFormMakerScreen(DmzFormDocument document, Screen parent) {
        super(Component.literal("DMZ Form Maker"));
        this.document = document;
        this.parent = parent;
        // Ignore any result left over from a previous editor so tick() does not re-apply it.
        this.seenResult = DmzFormEditorClientState.sequence();
    }

    @Override
    protected void init() {
        super.init();
        int[] fitted = XenoAtlasSprites.fittedSize(FRAME, getUiWidth() - 8, getUiHeight() - 8);
        frameW = fitted[0];
        frameH = fitted[1];
        frameX = Math.max(4, (getUiWidth() - frameW) / 2);
        frameY = Math.max(4, (getUiHeight() - frameH) / 2);
        rebuild();
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    public void tick() {
        super.tick();
        int seq = DmzFormEditorClientState.sequence();
        if (seq != seenResult) {
            seenResult = seq;
            if (DmzFormEditorClientState.success()) {
                document.revision(DmzFormEditorClientState.revision());
                document.markCreated();
                status = "Saved. Revision " + document.revision() + ".";
                statusColor = CYAN;
                rebuild();
            } else {
                String message = DmzFormEditorClientState.message();
                status = message == null || message.isBlank()
                        ? "The server rejected the save." : message;
                statusColor = WARN;
            }
        }
        if (DmzFormEditorClientState.inFlight()) {
            status = "Waiting for the server...";
            statusColor = GOLD;
        }
        long now = System.currentTimeMillis();
        if (saveGate.shouldSend(now, DmzFormEditorClientState.inFlight(), document.created())) {
            saveNow();
        }
    }

    private void saveNow() {
        String identity = document.identityError();
        if (identity != null) {
            status = identity;
            statusColor = WARN;
            return;
        }
        if (DmzFormEditorClientState.inFlight()) {
            return;
        }
        DmzFormEditorClientState.begin();
        FormEditorNetwork.save(document.kind(), document.race(), document.group(),
                document.formJson(), document.metadataJson(), document.revision(), sessionId);
        saveGate.sent();
    }

    private void duplicate() {
        minecraft.setScreen(new DmzFormMakerScreen(document.copyAsNewDraft(), parent));
    }

    // ---------------------------------------------------------------- layout

    private void rebuild() {
        clearWidgets();

        int bodyX = frameX + 18;
        int bodyY = frameY + HEADER_H + 4;
        int bodyW = frameW - 36;
        int bodyH = Math.max(ROW_H, (frameY + frameH - FOOTER_H) - bodyY);

        EditorLayout layout = new EditorLayout(bodyX, bodyY, bodyW, bodyH, ROW_H, COLUMN_GAP);
        List<EditorLayout.Page> pages = layout.place(rows());
        pageCount = pages.size();
        page = EditorLayout.clampPage(page, pageCount);
        placed = pages.get(page).rows();
        for (EditorLayout.Placed p : placed) {
            materialise(p);
        }

        int footerY = frameY + frameH - 32;
        addRenderableWidget(new AtlasButton(frameX + 16, footerY,
                Component.literal("Back"), PRIMARY, b -> onClose()));
        addRenderableWidget(new AtlasButton(frameX + 16 + 58, footerY,
                Component.literal("Save Now"), PRIMARY, b -> saveNow()));
        addRenderableWidget(new AtlasButton(frameX + 16 + 134, footerY,
                Component.literal("New Copy"), PRIMARY, b -> duplicate()));

        if (pageCount > 1) {
            EditorFooter pager = EditorFooter.of(frameX + frameW - 18,
                    XenoAtlasSprites.get(ARROW).width());
            addRenderableWidget(new AtlasButton(pager.prevX(), footerY + 2,
                    Component.literal("<"), ARROW, b -> turnPage(-1)));
            addRenderableWidget(new AtlasButton(pager.nextX(), footerY + 2,
                    Component.literal(">"), ARROW, b -> turnPage(1)));
        }
    }

    private void turnPage(int delta) {
        page = EditorLayout.clampPage(page + delta, pageCount);
        rebuild();
    }

    private List<EditorRow> rows() {
        List<EditorRow> rows = new ArrayList<>();
        rows.add(new EditorRow.Heading("Revision " + document.revision()
                + (document.created() ? "" : " (new draft)")));
        for (DmzFormDocument.Field field : document.fields()) {
            rows.add(rowFor(field));
        }
        return rows;
    }

    /** Maps one document field onto the editor row that fits its kind. */
    private EditorRow rowFor(DmzFormDocument.Field field) {
        return switch (field.kind()) {
            case COLOR -> new EditorRow.Color(field.label(), field.value(), v -> edit(field, v));
            case BOOL -> new EditorRow.Cycle(field.label(), List.of("No", "Yes"),
                    isOn(field.value()) ? 1 : 0, i -> {
                        edit(field, i == 1 ? "true" : "false");
                        rebuild();
                    });
            case JSON -> new EditorRow.Field(field.label(), field.value(), 8192,
                    v -> edit(field, v), true);
            default -> new EditorRow.Field(field.label(), field.value(), 256,
                    v -> edit(field, v));
        };
    }

    private void edit(DmzFormDocument.Field field, String raw) {
        document.update(field.key(), raw);
        saveGate.touch(System.currentTimeMillis());
    }

    private static boolean isOn(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private void materialise(EditorLayout.Placed p) {
        int controlX = p.x() + CONTROL_DX;
        int controlW = Math.max(40, p.columnWidth() - CONTROL_DX);
        // instanceof chain rather than a pattern switch: the same source compiles for Java 17 (1.20.1).
        EditorRow row = p.row();
        if (row instanceof EditorRow.Field f) {
            {
                int boxX = f.fullWidth() ? p.x() : controlX;
                int boxW = f.fullWidth() ? p.columnWidth() : controlW;
                EditBox box = new EditBox(font, boxX, p.y() + 2, boxW, FIELD_H,
                        Component.literal(f.label()));
                box.setMaxLength(f.maxLength());
                box.setValue(f.initial() == null ? "" : f.initial());
                box.setResponder(f.sink());
                addRenderableWidget(box);
            }
        } else if (row instanceof EditorRow.Cycle c) {
            addRenderableWidget(new AtlasCycle(controlX, p.y(),
                    Component.empty(), c.values(), c.selected(), c.sink())
                    .narrationLabel(Component.literal(c.label())));
        } else if (row instanceof EditorRow.Color c) {
            {
                int swatchW = 16;
                int boxW = Math.max(40, controlW - swatchW - 3);
                EditBox box = new EditBox(font, controlX, p.y() + 2, boxW, FIELD_H,
                        Component.literal(c.label()));
                box.setMaxLength(9);
                box.setValue(c.initial() == null ? "" : c.initial());
                box.setResponder(c.sink());
                addRenderableWidget(box);
                addRenderableWidget(new net.bullettrain.xenopixelsmod.client.ui.atlas.ColorSwatch(controlX + boxW + 3, p.y() + 1, box::getValue,
                        () -> openColorPicker(box), () -> colorPicker.isOpenFor(box)));
            }
        }
    }

    private void openColorPicker(EditBox box) {
        int rgb = NpcCombatProfile.parseHexColor(box.getValue()).orElse(0xFFFFFF);
        colorPicker.open(box, box.getX() + box.getWidth() + net.bullettrain.xenopixelsmod.client.ui.atlas.ColorSwatch.W + 6, box.getY(),
                getUiWidth(), getUiHeight(), box.getValue(), box::setValue);
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA0000000);
        beginUiScale(graphics);
        AtlasPanel.fittedInto(FRAME, frameX, frameY, getUiWidth() - 8, getUiHeight() - 8)
                .render(graphics);

        graphics.drawString(font, "Form Maker", frameX + 18, frameY + 10, GOLD, false);
        String kindLabel = document.kind() == DmzFormKind.STACK ? "Stack Form" : "Normal Form";
        graphics.drawString(font, kindLabel,
                frameX + frameW - 18 - font.width(kindLabel), frameY + 10, CYAN, false);
        graphics.drawString(font,
                document.race() + " / " + document.group() + " / " + document.form(),
                frameX + 18, frameY + 24, LIGHT, false);
        graphics.drawString(font, status, frameX + 18, frameY + 34, statusColor, false);

        for (EditorLayout.Placed p : placed) {
            int textY = p.y() + 6;
            { // was a pattern switch; if/else keeps it Java 17 source
                Object __switched1 = p.row();
                if (__switched1 instanceof EditorRow.Heading h) {
                    graphics.drawString(font, h.text(), p.x(), p.y() + 3, CYAN, false);
                    graphics.fill(p.x(), p.y() + 13, p.x() + p.columnWidth(), p.y() + 14,
                            0x30FFFFFF);
                }
                else if (__switched1 instanceof EditorRow.Text t) { graphics.drawString(font, t.value(), p.x(), textY, t.color(), false); }
                else if (__switched1 instanceof EditorRow.Field f) { graphics.drawString(font, f.label(), p.x(), textY, MUTED, false); }
                else if (__switched1 instanceof EditorRow.Color c) { graphics.drawString(font, c.label(), p.x(), textY, MUTED, false); }
                else if (__switched1 instanceof EditorRow.Cycle c) { graphics.drawString(font, c.label(), p.x(), textY, MUTED, false); }
                else { }
            }
        }

        if (pageCount > 1) {
            EditorFooter pager = EditorFooter.of(frameX + frameW - 18,
                    XenoAtlasSprites.get(ARROW).width());
            graphics.drawCenteredString(font, "Page " + (page + 1) + " / " + pageCount,
                    pager.labelCentreX(), frameY + frameH - 26, LIGHT);
        }

        super.render(graphics, (int) toUiX(mouseX), (int) toUiY(mouseY), partialTick);
        // Drawn last, above every widget, inside the same UI scale.
        colorPicker.render(graphics, (int) toUiX(mouseX), (int) toUiY(mouseY), partialTick);
        endUiScale(graphics);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (colorPicker.isOpen()) return true;
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Inline picker first: while it is open it takes every event, so nothing under it reacts.

    private boolean pickerClicked(double mouseX, double mouseY, int button) {
        return colorPicker.isOpen() && colorPicker.mouseClicked(toUiX(mouseX), toUiY(mouseY), button);
    }

    private boolean pickerDragged(double mouseX, double mouseY, int button) {
        return colorPicker.isOpen() && colorPicker.mouseDragged(toUiX(mouseX), toUiY(mouseY), button);
    }

    private boolean pickerReleased(double mouseX, double mouseY, int button) {
        return colorPicker.isOpen() && colorPicker.mouseReleased(toUiX(mouseX), toUiY(mouseY), button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return pickerClicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return pickerReleased(mouseX, mouseY, button) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        return pickerDragged(mouseX, mouseY, button) || super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return colorPicker.keyPressed(keyCode, scanCode, modifiers)
                || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return colorPicker.charTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
    }
}

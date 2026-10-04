package net.bullettrain.xenopixelsmod.client.npc;

import com.dragonminez.client.gui.character.util.ScaledScreen;
import net.bullettrain.xenopixelsmod.client.npc.editor.EditorFooter;
import net.bullettrain.xenopixelsmod.client.npc.editor.EditorLayout;
import net.bullettrain.xenopixelsmod.client.npc.editor.NpcLivePreview;
import net.bullettrain.xenopixelsmod.client.npc.editor.EditorRow;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasButton;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasTextFit;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasCycle;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasPanel;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasStepper;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasToggle;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDmzAppearance;
import net.bullettrain.xenopixelsmod.compat.npc.NpcHairBridge;
import net.bullettrain.xenopixelsmod.client.npc.NpcAppearanceParts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * The per-part appearance editor, reached from the NPC editor's DMZ tab.
 *
 * <p>Every control here edits a field that already exists on {@link NpcDmzAppearance} and already
 * round-trips through {@code NpcCombatProfile.appearance}, so this screen needs no packet of its
 * own: it mutates the parent editor's profile in place and the parent's Save sends the whole
 * {@code DmzAppearance} compound as one dirty key.
 *
 * <p>It reuses {@link EditorLayout}, so it pages the same way the main editor does and can never
 * push a control outside its frame.
 */
public final class XenoNpcAppearanceScreen extends ScaledScreen {

    /** Drawn at the vanilla GUI Scale; see {@link NpcGuiScale}. */
    @Override
    protected float computeDynamicScale(float available) {
        return NpcGuiScale.dynamicScale(super.computeDynamicScale(available));
    }
    /** Inline colour picker shared by every colour row on this screen; opens over it. */
    private final net.bullettrain.xenopixelsmod.client.ui.atlas.InlineColorPicker colorPicker = new net.bullettrain.xenopixelsmod.client.ui.atlas.InlineColorPicker();
    /** The narrow frame, so the visualizer fits beside it the way it does in the editor. */
    private static final String FRAME = "xeno_editor_panel_w420";
    private static final String PREVIEW_PANEL = "mynpcs_small_panel";
    private static final int PREVIEW_GAP = 8;

    /** Sub-tab row geometry. Three buttons across the top of the body. */
    private static final int SUBTAB_H = 20;
    private static final int SUBTAB_W = 74;
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

    private static final List<String> GENDERS = List.of("male", "female");

    /** The three appearance modes, in the order the cycle walks them. */
    private static final List<String> MODES = List.of(
            NpcDmzAppearance.Mode.OFF.name(),
            NpcDmzAppearance.Mode.OVERLAY.name(),
            NpcDmzAppearance.Mode.FULL.name());
    private static final List<String> CLASSES =
            List.of("warrior", "spiritualist", "martial_artist");

    private final Screen parent;
    private final NpcDmzAppearance appearance;
    private final Runnable onChanged;

    /**
     * The NPC's race.
     *
     * <p>Needed because part counts and the texture set are per race: a saiyan and a namekian do
     * not offer the same body types, and a race declaring a custom model draws its parts from that
     * model instead of its own name.
     */
    private final String raceId;

    /**
     * The whole profile, because hair is not part of {@code NpcDmzAppearance}.
     *
     * <p>Hair enabled/style/colour/code live directly on the profile, so the hair rows edit it
     * rather than the appearance block. The parent's save marks both dirty.
     */
    private final NpcCombatProfile profile;

    /** The NPC being previewed. The profile alone cannot be drawn; the live entity can. */
    /**
     * The three sub-tabs the reference screen splits appearance across.
     *
     * <p>Everything used to live on one long list under headings of our own invention
     * (Mode/Identity/Parts/Colours/Hair/Options). Same fields, different shape - and the reference
     * groups them by what part of the NPC they affect, which is easier to work with than one list
     * of eighteen.
     */
    private enum Section {
        BODY("Body"),
        FACE("Face"),
        HAIR("Hair / Aura");

        private final String label;

        Section(String label) {
            this.label = label;
        }
    }

    private Section section = Section.BODY;

    private final int entityId;

    /** Same visualizer the editor uses, so the two cannot drift apart. */
    private final NpcLivePreview preview = new NpcLivePreview();

    private int frameX;
    private int frameY;
    private int frameW;
    private int frameH;
    private int page;
    private int pageCount = 1;
    private List<EditorLayout.Placed> placed = List.of();

    public XenoNpcAppearanceScreen(Screen parent, int entityId, NpcCombatProfile profile,
                                   Runnable onChanged) {
        super(Component.literal("Appearance"));
        this.parent = parent;
        this.entityId = entityId;
        this.profile = profile == null ? new NpcCombatProfile() : profile;
        this.appearance = this.profile.appearance == null
                ? new NpcDmzAppearance() : this.profile.appearance;
        String race = this.profile.raceId;
        this.raceId = race == null || race.isBlank() ? "human" : race;
        this.onChanged = onChanged == null ? () -> { } : onChanged;
    }

    @Override
    protected void init() {
        super.init();
        // Frame and visualizer are laid out as one centred block, exactly as the editor does it -
        // centring the frame alone would push the panel off the right of the canvas.
        int canvasW = getUiWidth();
        int canvasH = getUiHeight();
        int[] previewFitted = XenoAtlasSprites.fittedSize(PREVIEW_PANEL,
                Math.max(40, canvasW / 3), Math.max(40, canvasH - 40));
        int previewW = previewFitted[0];
        int previewH = previewFitted[1];

        int[] fitted = XenoAtlasSprites.fittedSize(FRAME,
                canvasW - 8 - previewW - PREVIEW_GAP, canvasH - 8);
        frameW = fitted[0];
        frameH = fitted[1];

        int blockW = frameW + PREVIEW_GAP + previewW;
        frameX = Math.max(4, (canvasW - blockW) / 2);
        frameY = (canvasH - frameH) / 2;

        int previewX = Math.min(frameX + frameW + PREVIEW_GAP, canvasW - previewW - 4);
        preview.place(previewX, frameY, previewW, previewH);
        rebuild();
    }

    private void rebuild() {
        clearWidgets();

        int bodyX = frameX + 18;
        // The sub-tab row sits between the title and the first field, as the reference has it.
        int bodyY = frameY + HEADER_H + SUBTAB_H + 4;
        int bodyW = frameW - 36;
        int bodyH = Math.max(ROW_H, (frameY + frameH - FOOTER_H) - bodyY);

        EditorLayout layout = new EditorLayout(bodyX, bodyY, bodyW, bodyH, ROW_H, COLUMN_GAP);
        List<EditorLayout.Page> pages = layout.place(rows());
        pageCount = pages.size();
        page = EditorLayout.clampPage(page, pageCount);
        placed = pages.get(page).rows();

        java.util.Map<Integer, Float> lineScales = appearanceLineScales();
        for (EditorLayout.Placed p : placed) {
            materialise(p, lineScales.getOrDefault(p.y(), 1.0f));
        }

        // Body | Face | Hair / Aura. Switching one resets to its first page, because page 3 of
        // Body means nothing on Face and landing there would look like an empty screen.
        int tabX = bodyX;
        float tabScale = AtlasTextFit.groupScale(java.util.Arrays.stream(Section.values())
                .map(entry -> new AtlasTextFit.Measure(font.width(entry.label), SUBTAB_W - 8))
                .toList(), AtlasTextFit.MIN_SCALE);
        for (Section entry : Section.values()) {
            final Section target = entry;
            addRenderableWidget(new AtlasButton(tabX, frameY + HEADER_H - 2,
                    Component.literal(entry.label),
                    entry == section ? PRIMARY : "mynpcs_button_row",
                    b -> {
                        if (section != target) {
                            section = target;
                            page = 0;
                            rebuild();
                        }
                    }, tabScale));
            tabX += SUBTAB_W + 4;
        }

        int footerY = frameY + frameH - 32;
        addRenderableWidget(new AtlasButton(frameX + 16, footerY,
                Component.literal("Back"), PRIMARY, b -> onClose()));

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
        return switch (section) {
            case FACE -> faceRows();
            case HAIR -> hairRows();
            default -> bodyRows();
        };
    }

    /**
     * The Body sub-tab, in the reference's order.
     *
     * <p>Mode leads because it decides whether anything else here does anything: both
     * {@code NpcFullDmzRenderer} and {@code NpcDmzAnim} require FULL before the DragonMineZ model
     * and its parts are used at all. An NPC left on OFF renders as a plain humanoid whatever is set
     * below, which is exactly how "body type does nothing" looked.
     */
    private List<EditorRow> bodyRows() {
        List<EditorRow> r = new ArrayList<>();
        r.add(cycle("Appearance Mode", MODES, Math.max(0, MODES.indexOf(appearance.mode.name())),
                v -> appearance.mode = NpcDmzAppearance.Mode.parse(MODES.get(v))));
        r.add(new EditorRow.Text("", modeHint(), MUTED));

        r.add(cycle("Gender", GENDERS, GENDERS.indexOf(appearance.gender),
                v -> appearance.gender = GENDERS.get(v)));
        r.add(toggle("Tail", appearance.saiyanTail, v -> appearance.saiyanTail = v));
        r.add(cycle("Class", CLASSES, CLASSES.indexOf(appearance.characterClass),
                v -> appearance.characterClass = CLASSES.get(v)));

        // Counts come from DragonMineZ itself via TextureCounter, so the cycle offers exactly the
        // body types this race ships.
        r.add(partRow("Body type", NpcAppearanceParts.maxBodyType(raceId, appearance),
                appearance.bodyType, v -> appearance.bodyType = v));
        r.add(floatField("Bust scale", appearance.boobScale, v -> appearance.boobScale = v));

        r.add(colorField("Body color 1", appearance.bodyColor, v -> appearance.bodyColor = v));
        r.add(colorField("Body color 2", appearance.bodyColor2, v -> appearance.bodyColor2 = v));
        r.add(colorField("Body color 3", appearance.bodyColor3, v -> appearance.bodyColor3 = v));
        r.add(colorField("Tail color", appearance.tailColor, v -> appearance.tailColor = v));
        r.add(toggle("Tail: Race", appearance.tailUseRaceColor,
                v -> appearance.tailUseRaceColor = v));
        return r;
    }

    /** The Face sub-tab: the head parts and the eye colours that go with them. */
    private List<EditorRow> faceRows() {
        List<EditorRow> r = new ArrayList<>();
        r.add(partRow("Eyes", NpcAppearanceParts.maxEyesType(raceId),
                appearance.eyesType, v -> appearance.eyesType = v));
        if (NpcAppearanceParts.supportsSeparateBrows(raceId)) {
            r.add(partRow("Eyebrows", NpcAppearanceParts.maxEyesType(raceId),
                    appearance.eyebrowsType, v -> appearance.eyebrowsType = v));
        } else {
            // Informational, not a disabled control: for this race there is nothing to set.
            r.add(new EditorRow.Text("Eyebrows", "linked to eyes for this race", MUTED));
        }
        r.add(partRow("Nose", NpcAppearanceParts.maxNoseType(raceId),
                appearance.noseType, v -> appearance.noseType = v));
        r.add(partRow("Mouth", NpcAppearanceParts.maxMouthType(raceId),
                appearance.mouthType, v -> appearance.mouthType = v));
        r.add(partRow("Tattoo", NpcAppearanceParts.maxTattooType(raceId),
                appearance.tattooType, v -> appearance.tattooType = v));

        r.add(colorField("Eye color 1", appearance.eye1Color, v -> appearance.eye1Color = v));
        r.add(colorField("Eye color 2", appearance.eye2Color, v -> appearance.eye2Color = v));

        r.add(new EditorRow.Text("", "Parts come from " + NpcAppearanceParts.modelBase(raceId)
                + " textures.", MUTED));
        return r;
    }

    /**
     * The Hair / Aura sub-tab.
     *
     * <p>These rows used to sit on the editor's Display tab; moving them to match the reference
     * dropped them entirely at one point, which is why the hair cycler went missing. Aura itself
     * lives on the DMZ tab, where the reference keeps it.
     */
    private List<EditorRow> hairRows() {
        List<EditorRow> r = new ArrayList<>();
        r.add(toggle("Hair on", profile.hairEnabled, v -> profile.hairEnabled = v));
        r.add(cycle("Hair style", hairStyles(),
                Math.max(0, Math.min(hairStyles().size() - 1, profile.hairStyleId)),
                v -> profile.hairStyleId = v));
        r.add(colorField("Hair color", profile.hairColor, v -> profile.hairColor = v));
        r.add(field("Hair code", profile.hairCode,
                NpcCombatProfile.HAIR_CODE_CHUNK * 4, v -> profile.hairCode = v));
        r.add(toggle("Base hair", appearance.renderHairBase, v -> appearance.renderHairBase = v));
        r.add(field("Head bone", appearance.activeHeadBone, v -> appearance.activeHeadBone = v));
        r.add(new EditorRow.Text("", hairHint(), MUTED));
        return r;
    }

    private java.util.Map<Integer, Float> appearanceLineScales() {
        java.util.Map<Integer, java.util.List<AtlasTextFit.Measure>> measures = new java.util.HashMap<>();
        for (EditorLayout.Placed p : placed) {
            String text = null;
            int available = XenoAtlasSprites.get("mynpcs_button_row").width() - 8;
            // instanceof chain rather than a pattern switch: the same source compiles for Java 17 (1.20.1).
            EditorRow row = p.row();
            if (row instanceof EditorRow.Toggle) {
                text = "Yes";
            } else if (row instanceof EditorRow.Cycle c) {
                text = c.values().stream()
                        .max(java.util.Comparator.comparingInt(font::width)).orElse("");
            } else if (row instanceof EditorRow.Stepper st) {
                text = java.util.stream.Stream.of(
                                Integer.toString(st.min()), Integer.toString(st.max()),
                                Integer.toString(st.value()))
                        .max(java.util.Comparator.comparingInt(font::width)).orElse("");
            }
            if (text != null) measures.computeIfAbsent(p.y(), ignored -> new java.util.ArrayList<>())
                    .add(new AtlasTextFit.Measure(font.width(text), available));
        }
        java.util.Map<Integer, Float> result = new java.util.HashMap<>();
        measures.forEach((y, group) -> result.put(y, AtlasTextFit.groupScale(group, AtlasTextFit.MIN_SCALE)));
        return result;
    }

    private void materialise(EditorLayout.Placed p, float textScale) {
        int controlX = p.x() + CONTROL_DX;
        int controlW = Math.max(40, p.columnWidth() - CONTROL_DX);
        { // was a pattern switch; if/else keeps it Java 17 source
            Object __switched1 = p.row();
            if (__switched1 instanceof EditorRow.Field f) {
                EditBox box = new EditBox(font, controlX, p.y() + 2, controlW, FIELD_H,
                        Component.literal(f.label()));
                box.setMaxLength(f.maxLength());
                box.setValue(f.initial() == null ? "" : f.initial());
                box.setResponder(f.sink());
                addRenderableWidget(box);
            }
            else if (__switched1 instanceof EditorRow.Toggle t) { addRenderableWidget(new AtlasToggle(controlX, p.y(),
                    controlW, t.value(), Component.empty(), t.sink()).groupTextScale(textScale)
                    .narrationLabel(Component.literal(t.label()))); }
            else if (__switched1 instanceof EditorRow.Cycle c) { addRenderableWidget(new AtlasCycle(controlX, p.y(),
                    Component.empty(), c.values(), c.selected(), c.sink()).groupTextScale(textScale)
                    .narrationLabel(Component.literal(c.label()))); }
            else if (__switched1 instanceof EditorRow.Stepper st) { addRenderableWidget(new AtlasStepper(controlX, p.y(),
                    Component.empty(), st.value(), st.min(), st.max(), st.sink())
                    .groupTextScale(textScale).narrationLabel(Component.literal(st.label()))); }
            else if (__switched1 instanceof EditorRow.Color c) {
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
            else { }
        }
    }

    // ---------------------------------------------------------------- row helpers

    private EditorRow field(String label, String value, Consumer<String> sink) {
        return field(label, value, 32, sink);
    }

    private EditorRow field(String label, String value, int maxLength, Consumer<String> sink) {
        return new EditorRow.Field(label, value == null ? "" : value, maxLength, v -> {
            sink.accept(v);
            onChanged.run();
        });
    }

    /** A hex field plus a swatch that opens the shared picker; same write path as {@link #field}. */
    private EditorRow colorField(String label, String value, Consumer<String> sink) {
        return new EditorRow.Color(label, value == null ? "" : value, v -> {
            sink.accept(v);
            onChanged.run();
        });
    }

    private void openColorPicker(EditBox box) {
        int rgb = NpcCombatProfile.parseHexColor(box.getValue()).orElse(0xFFFFFF);
        colorPicker.open(box, box.getX() + box.getWidth() + net.bullettrain.xenopixelsmod.client.ui.atlas.ColorSwatch.W + 6, box.getY(),
                getUiWidth(), getUiHeight(), box.getValue(), box::setValue);
    }

    /**
     * Upper bound for a part index.
     *
     * <p>DragonMineZ does not expose how many presets a part has - body types are numbered texture
     * files but the face parts share one {@code faces} atlas - so this is a range the arrows will
     * walk, not a claim about how many exist. An index past the end falls back to the race default,
     * which the screen says.
     */
    private static final int MAX_PART_INDEX = 31;

    /**
     * Hair styles: H0 is the custom hair code, H1 and up are DragonMineZ's own preset styles.
     *
     * <p>The count comes from {@code NpcHairBridge.presetCount()} rather than a number written
     * here, so the list follows whatever DMZ actually ships.
     */
    private static List<String> hairStyles() {
        int presets = NpcHairBridge.presetCount();
        List<String> styles = new ArrayList<>(presets + 1);
        styles.add("H0 (code)");
        for (int i = 1; i <= presets; i++) {
            styles.add("H" + i);
        }
        return styles;
    }

    private String hairHint() {
        return profile.hairStyleId <= 0
                ? "H0 uses the hair code below."
                : "Preset style; the hair code is ignored.";
    }

    /** Says plainly what the selected mode does, since it gates the rest of the screen. */
    private String modeHint() {
        return switch (appearance.mode) {
            case FULL -> "FULL: DragonMineZ model. Everything below applies.";
            case OVERLAY -> "OVERLAY: vanilla model with DMZ layers over it.";
            default -> "OFF: vanilla model. Nothing below this is used.";
        };
    }

    /**
     * A part index as a cycle over the race's real options, or a stepper when none can be counted.
     *
     * <p>Counting walks the resource pack, so a pack mid-reload can legitimately answer zero. The
     * control stays usable in that case rather than collapsing to a single fake entry.
     */
    private EditorRow partRow(String label, int max, int value, IntConsumer sink) {
        List<String> indices = NpcAppearanceParts.indices(max);
        if (indices.isEmpty()) {
            return stepper(label, value, sink);
        }
        // "2 / 2" rather than a bare "2", the way the reference labels its body-type cycler. A
        // lone index says nothing about whether there are more to try.
        List<String> options = new ArrayList<>(indices.size());
        for (String index : indices) {
            options.add(index + " / " + max);
        }
        return new EditorRow.Cycle(label, options, Math.max(0, Math.min(max, value)), v -> {
            sink.accept(v);
            onChanged.run();
        });
    }

    private EditorRow stepper(String label, int value, IntConsumer sink) {
        return new EditorRow.Stepper(label, value, 0, MAX_PART_INDEX, v -> {
            sink.accept(v);
            onChanged.run();
        });
    }

    private EditorRow intField(String label, int value, IntConsumer sink) {
        return new EditorRow.Field(label, String.valueOf(value), 4, text -> {
            try {
                sink.accept(Integer.parseInt(text.trim()));
                onChanged.run();
            } catch (NumberFormatException ignored) {
                // Mid-typing is not an error.
            }
        });
    }

    private EditorRow floatField(String label, float value, Consumer<Float> sink) {
        return new EditorRow.Field(label, String.format(Locale.ROOT, "%.2f", value), 6, text -> {
            try {
                sink.accept(Float.parseFloat(text.trim()));
                onChanged.run();
            } catch (NumberFormatException ignored) {
                // As above.
            }
        });
    }

    private EditorRow toggle(String label, boolean value, Consumer<Boolean> sink) {
        return new EditorRow.Toggle(label, value, v -> {
            sink.accept(v);
            onChanged.run();
        });
    }

    private EditorRow cycle(String label, List<String> values, int selected, IntConsumer sink) {
        return new EditorRow.Cycle(label, values, Math.max(0, selected), v -> {
            sink.accept(v);
            onChanged.run();
        });
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA0000000);
        beginUiScale(graphics);
        AtlasPanel.fittedInto(FRAME, frameX, frameY, getUiWidth() - 8, getUiHeight() - 8)
                .render(graphics);

        preview.render(graphics, font, entityId, profile.auraOn, partialTick);

        graphics.drawString(font, "Appearance", frameX + 18, frameY + 10, GOLD, false);
        graphics.drawString(font, "Edits apply on the editor's Save", frameX + 122, frameY + 10,
                MUTED, false);

        for (EditorLayout.Placed p : placed) {
            int textY = p.y() + 6;
            { // was a pattern switch; if/else keeps it Java 17 source
                Object __switched2 = p.row();
                if (__switched2 instanceof EditorRow.Heading h) {
                    graphics.drawString(font, h.text(), p.x(), p.y() + 3, CYAN, false);
                    graphics.fill(p.x(), p.y() + 13, p.x() + p.columnWidth(), p.y() + 14,
                            0x30FFFFFF);
                }
                else if (__switched2 instanceof EditorRow.Text t) { graphics.drawString(font, t.value(), p.x(), textY, t.color(), false); }
                else if (__switched2 instanceof EditorRow.Field f) { graphics.drawString(font, f.label(), p.x(), textY, MUTED, false); }
                else if (__switched2 instanceof EditorRow.Color c) { graphics.drawString(font, c.label(), p.x(), textY, MUTED, false); }
                else if (__switched2 instanceof EditorRow.Toggle t) { graphics.drawString(font, t.label(), p.x(), textY, MUTED, false); }
                else if (__switched2 instanceof EditorRow.Cycle c) { graphics.drawString(font, c.label(), p.x(), textY, MUTED, false); }
                else if (__switched2 instanceof EditorRow.Stepper st) { graphics.drawString(font, st.label(), p.x(), textY, MUTED, false); }
                else if (__switched2 instanceof EditorRow.BrainAction ignored) { }
                else if (__switched2 instanceof EditorRow.Spacer ignored) { }
                else if (__switched2 instanceof EditorRow.Action ignored) { }
                else if (__switched2 instanceof EditorRow.Slot ignored) { }
            }
        }

        if (pageCount > 1) {
            EditorFooter pager = EditorFooter.of(frameX + frameW - 18,
                    XenoAtlasSprites.get(ARROW).width());
            graphics.drawCenteredString(font, "Page " + (page + 1) + " / " + pageCount,
                    pager.labelCentreX(), frameY + frameH - 26, 0xFFE7EDF3);
        }

        super.render(graphics, (int) toUiX(mouseX), (int) toUiY(mouseY), partialTick);
        // Drawn last, above every widget, inside the same UI scale.
        colorPicker.render(graphics, (int) toUiX(mouseX), (int) toUiY(mouseY), partialTick);
        endUiScale(graphics);
    }

    // The visualizer is not a widget, so ScaledScreen does not convert for it: every bounds test
    // below goes through toUiX/toUiY first. See docs/atlas-ui-doco.md.

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pickerClicked(mouseX, mouseY, button)) return true;
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (preview.contains(toUiX(mouseX), toUiY(mouseY))) {
            preview.beginOrbit(button == 1);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (pickerReleased(mouseX, mouseY, button)) return true;
        preview.endDrag();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (pickerDragged(mouseX, mouseY, button)) return true;
        if (preview.drag(dx / getUiScale(), dy / getUiScale())) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (colorPicker.isOpen()) return true;
        // The wheel zooms the model when it is over the panel, and pages the body otherwise. Before
        // the visualizer was here the wheel always paged, so this has to check the panel first.
        if (preview.contains(toUiX(mouseX), toUiY(mouseY))) {
            preview.scroll(scrollY);
            return true;
        }
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return colorPicker.keyPressed(keyCode, scanCode, modifiers)
                || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return colorPicker.charTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
    }
}

package net.bullettrain.xenopixelsmod.client.maker;

import com.dragonminez.client.gui.character.util.ScaledScreen;
import com.dragonminez.common.config.ConfigManager;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasButton;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasCycle;
import net.bullettrain.xenopixelsmod.client.ui.atlas.ColorSwatch;
import net.bullettrain.xenopixelsmod.client.ui.atlas.InlineColorPicker;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.bullettrain.xenopixelsmod.command.XenoPermissions;
import net.bullettrain.xenopixelsmod.dmz.race.RaceLabelRegistry;
import net.bullettrain.xenopixelsmod.dmz.race.RaceLabels;
import net.bullettrain.xenopixelsmod.dmz.race.RacePackService;
import net.bullettrain.xenopixelsmod.dmz.race.RacePackTemplate;
import net.bullettrain.xenopixelsmod.network.race.RaceLabelNetwork;
import net.bullettrain.xenopixelsmod.hair.HairMakerDocument;
import net.bullettrain.xenopixelsmod.hair.HairPresetImport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Exact-layout Race Character Creator (PR-D6d / KD15 r3 Image 1) + 2026-10-03 chrome upgrade.
 *
 * <p>Chrome: gold {@code banner_top} title strip; inner category / part grid / preview keep green
 * Task-9 sprites ({@code xeno_maker_*}). Controls: {@link AtlasCycle} for category + presets
 * ({@link MakerPresetCatalog}), gender Male/Female cycle, category-scoped
 * {@link ColorSwatch} + {@link InlineColorPicker} (Body Skin/Skin2/Skin3, Eyes Eye1/Eye2,
 * Hair, Aura). Preview mutates Character for this frame only. Save merges verified
 * colour keys into an existing custom pack.
 *
 * <p>Create Race enabled (Task 7 READY): {@link RacePackService#createRacePack} then
 * {@code ConfigManager.reload()}.
 *
 * <p><b>Not verified in a running game.</b>
 */
public final class RaceCharacterMakerScreen extends ScaledScreen {
    private static final String BANNER = "banner_top";
    private static final String RACE_CARD = "xeno_maker_race_card";
    private static final String CATEGORY_COL = "xeno_maker_category_col";
    private static final String PART_GRID = "xeno_maker_part_grid";
    /** Large green well for the live player / race visualizer. */
    private static final String PREVIEW = "xeno_maker_hair_preview";
    private static final String TILE = "icon_slot_lg";
    private static final String TOOL = "mynpcs_button_row";
    private static final String ARROW = "mynpcs_button_arrow";

    private static final XenoAtlasSprites.Theme CHROME = XenoAtlasSprites.Theme.GOLD;
    private static final XenoAtlasSprites.Theme INNER = XenoAtlasSprites.Theme.GREEN;

    private static final int GOLD = 0xFFFFC14A;
    private static final int LIGHT = 0xFFE7EDF3;
    private static final int MUTED = 0xFF6E9680;
    private static final int OK = 0xFF9AFFB0;
    private static final int WARN = 0xFFFF8A80;
    private static final int GLOW = 0x9900C853;
    private static final int COLOR_SLOT_W = 92;
    private static final int COLOR_BOX_W = 70;
    private static final int COLOR_LABEL_H = 10;
    private static final int COLOR_STRIP_H = 28;

    /** Task 7 evidence READY — Create Race is enabled. */
    public static final boolean CREATE_RACE_READY = true;
    public static final String CREATE_RACE_EVIDENCE =
            "docs/superpowers/evidence/2026-10-03-race-registration-path.md";

    private static final String[] BUILTIN_ORDER = {
            "saiyan", "namekian", "human", "frostdemon", "majin", "bioandroid"
    };

    private final Screen parent;
    private final MakerPreviewController preview = new MakerPreviewController();
    private final InlineColorPicker colorPicker = new InlineColorPicker();

    private String selectedRace = "saiyan";
    private String gender = "male";
    private RaceMakerParts.Category category = RaceMakerParts.Category.BODY;
    private String selectedPartId = "";
    private int bodyType;
    private int eyesType;
    private int mouthType;
    private int hairPreset = 1;
    private int noseType;
    private int tattooType;
    private List<String> raceIds = List.of();
    private List<String> partIds = List.of();
    private List<String> presetLabels = List.of();
    private String status = "Select race, category, and part. Preview = true local player.";
    private int statusColor = MUTED;

    private String skinColorHex = "#F4C7A1";
    private String skin2Hex = "#F4C7A1";
    private String skin3Hex = "#F4C7A1";
    private String eyeColorHex = "#000000";
    private String eye2Hex = "#000000";
    private String hairColorHex = "#2B1B0E";
    private String auraHex = "#7FFFFF";
    private String createIdText = "";
    private String displayNameText = "";
    private String descriptionText = "";

    private int originX;
    private int originY;
    private int bannerX;
    private int bannerY;
    private int bannerW;
    private int bannerH;
    private int raceRowX;
    private int raceRowY;
    private int raceCardW;
    private int raceCardH;
    private int raceScroll;
    private int[] cycleX = new int[3];
    private int cycleY;
    private int cycleLabelY;
    private int categoryX;
    private int categoryY;
    private int categoryW;
    private int categoryH;
    private int gridX;
    private int gridY;
    private int gridW;
    private int gridH;
    private int previewX;
    private int previewY;
    private int previewW;
    private int previewH;
    private int colorY;
    private int footerY;
    private int tileW;
    private int tileH;
    private EditBox createIdBox;
    private EditBox displayNameBox;
    private EditBox descriptionBox;

    public RaceCharacterMakerScreen(Screen parent) {
        super(Component.literal("Race Character Maker"));
        this.parent = parent;
    }

    @Override
    protected int getMinGuiWidth() {
        return 640;
    }

    @Override
    protected int getMinGuiHeight() {
        return 528;
    }

    @Override
    protected void init() {
        super.init();
        preview.bindLocalPlayer(minecraft);

        bannerW = XenoAtlasSprites.get(BANNER).width();
        bannerH = XenoAtlasSprites.get(BANNER).height();
        raceCardW = XenoAtlasSprites.get(RACE_CARD).width();
        raceCardH = XenoAtlasSprites.get(RACE_CARD).height();
        categoryW = XenoAtlasSprites.get(CATEGORY_COL).width();
        categoryH = XenoAtlasSprites.get(CATEGORY_COL).height();
        gridW = XenoAtlasSprites.get(PART_GRID).width();
        gridH = XenoAtlasSprites.get(PART_GRID).height();
        previewW = XenoAtlasSprites.get(PREVIEW).width();
        previewH = XenoAtlasSprites.get(PREVIEW).height();
        tileW = XenoAtlasSprites.get(TILE).width();
        tileH = XenoAtlasSprites.get(TILE).height();

        int contentW = categoryW + 8 + gridW + 8 + previewW;
        int colorStackH = 64;
        int labelRowH = 22;
        int footerH = 24;
        int contentH = bannerH + 8 + raceCardH + 30
                + Math.max(categoryH, Math.max(gridH, previewH))
                + colorStackH + labelRowH + footerH + 24;
        originX = Math.max(8, (getUiWidth() - contentW) / 2);
        originY = Math.max(4, (getUiHeight() - contentH) / 2);

        bannerX = originX + (contentW - bannerW) / 2;
        bannerY = originY;
        raceRowY = bannerY + bannerH + 6;
        int[] pager = racePagerXs(originX, originX + categoryW + 8 + gridW + 8,
                AtlasButton.nativeWidth(ARROW), 4, raceCardW);
        raceRowX = pager[1];
        // Gender / Category / Race sit on one packed row with labels above.
        // AtlasCycle is 108px; stacking two at the same x produced EyeEyes / MouMouth.
        cycleX = cycleRowXs(originX, AtlasCycle.nativeWidth(), 12);
        cycleLabelY = raceRowY + raceCardH + 4;
        cycleY = cycleLabelY + 10;
        categoryX = originX;
        categoryY = cycleY + 26;
        gridX = categoryX + categoryW + 8;
        gridY = categoryY;
        previewX = gridX + gridW + 8;
        previewY = categoryY;

        refreshRaces();
        refreshParts();
        ensureRaceCardVisible(selectedRace);

        clearWidgets();

        int[] pagerBtns = racePagerXs(originX, previewX, AtlasButton.nativeWidth(ARROW), 4, raceCardW);
        int arrowY = raceRowY + (raceCardH - AtlasButton.nativeHeight(ARROW)) / 2;
        addRenderableWidget(new AtlasButton(pagerBtns[0], arrowY,
                Component.literal("<"), ARROW, b -> scrollRaces(-1)));
        addRenderableWidget(new AtlasButton(pagerBtns[2], arrowY,
                Component.literal(">"), ARROW, b -> scrollRaces(1)));

        int genderIndex = "female".equalsIgnoreCase(gender) ? 1 : 0;
        addRenderableWidget(new AtlasCycle(cycleX[0], cycleY, Component.literal("Gender"),
                List.of("Male", "Female"), genderIndex, this::onGenderCycle));

        List<String> catLabels = MakerPresetCatalog.categoryLabels();
        int catIndex = Math.max(0, catLabels.indexOf(category.label()));
        addRenderableWidget(new AtlasCycle(cycleX[1], cycleY,
                Component.literal("Category"), catLabels, catIndex, this::onCategoryCycle));

        // Full race list (custom packs first) — green cards only show ~4 at a time.
        List<String> raceLabels = new ArrayList<>();
        for (String id : raceIds) {
            raceLabels.add(cycleRaceLabel(id));
        }
        if (raceLabels.isEmpty()) {
            raceLabels = List.of(displayRace(selectedRace));
        }
        int raceIndex = Math.max(0, raceIds.indexOf(selectedRace));
        addRenderableWidget(new AtlasCycle(cycleX[2], cycleY,
                Component.literal("Race"), raceLabels, raceIndex, this::onRaceListCycle));

        colorY = previewY + previewH + 8;
        addCategoryColorSlots(colorY);
        int labelY = Math.max(categoryY + categoryH, colorY + COLOR_STRIP_H) + 6;
        int contentRight = previewX + previewW;
        displayNameBox = new EditBox(font, originX, labelY, 132, 16,
                Component.literal("displayName"));
        displayNameBox.setMaxLength(RaceLabelRegistry.MAX_NAME);
        displayNameBox.setHint(Component.literal("Display name"));
        displayNameBox.setValue(displayNameText == null ? "" : displayNameText);
        displayNameBox.setResponder(v -> displayNameText = v == null ? "" : v);
        addRenderableWidget(displayNameBox);
        int descX = originX + 136;
        int descW = Math.max(80, contentRight - descX);
        descriptionBox = new EditBox(font, descX, labelY, descW, 16,
                Component.literal("description"));
        descriptionBox.setMaxLength(RaceLabelRegistry.MAX_DESC);
        descriptionBox.setHint(Component.literal("Description (race picker)"));
        descriptionBox.setValue(descriptionText == null ? "" : descriptionText);
        descriptionBox.setResponder(v -> descriptionText = v == null ? "" : v);
        addRenderableWidget(descriptionBox);

        footerY = labelY + 20;
        int compact = AtlasButton.nativeWidth(TOOL);
        int g = 4;
        createIdBox = new EditBox(font, originX, footerY, 88, 16,
                Component.literal("newRaceId"));
        createIdBox.setMaxLength(32);
        createIdBox.setHint(Component.literal("new_race_id"));
        createIdBox.setValue(createIdText == null ? "" : createIdText);
        createIdBox.setResponder(v -> createIdText = v == null ? "" : v);
        addRenderableWidget(createIdBox);

        AtlasButton create = new AtlasButton(originX + 92, footerY,
                Component.literal("Create"), TOOL, b -> createRace());
        create.active = CREATE_RACE_READY && mayCreateRace();
        if (!CREATE_RACE_READY) {
            create.setTooltip(Tooltip.create(Component.literal(
                    "Create Race disabled — see " + CREATE_RACE_EVIDENCE)));
        } else if (!mayCreateRace()) {
            create.setTooltip(Tooltip.create(Component.literal(
                    "Requires " + XenoPermissions.MAKER_RACE_CREATE.getNodeName())));
        } else {
            create.setTooltip(Tooltip.create(Component.literal(
                    "Writes races/<id>/character.json plus Display name / Description literals (not en_us keys).")));
        }
        addRenderableWidget(create);

        boolean custom = RacePackService.isCustomPack(selectedRace);
        AtlasButton save = new AtlasButton(originX + 92 + compact + g, footerY,
                Component.literal("Save"), TOOL, b -> saveRace());
        save.active = CREATE_RACE_READY && mayCreateRace() && custom;
        save.setTooltip(Tooltip.create(Component.literal(custom
                ? "Merge colours, parts, and Display name / Description into races/"
                + selectedRace + "/."
                : "Select a created (custom) race pack to edit and save.")));
        addRenderableWidget(save);

        addRenderableWidget(new AtlasButton(originX + 92 + (compact + g) * 2, footerY,
                Component.literal("Close"), TOOL, b -> onClose()));

        AtlasButton hairEditor = new AtlasButton(originX + 92 + (compact + g) * 3, footerY,
                Component.literal("Hair Editor"), TOOL, b -> openHairEditor());
        hairEditor.setTooltip(Tooltip.create(Component.literal(
                "Opens Hair Studio on the live player mesh. Does not write this pack's character.json.")));
        addRenderableWidget(hairEditor);

        schedulePreview();
    }

    private int raceCardMaxVisible() {
        return racePagerXs(originX, previewX, AtlasButton.nativeWidth(ARROW), 4, raceCardW)[3];
    }

    /**
     * {@code [leftArrowX, cardsX, rightArrowX, maxVisible]}. Four 72px cards fit between
     * 22px gutters when {@code previewX = originX + 352}.
     */
    static int[] racePagerXs(int originX, int previewX, int arrowW, int gutter, int cardW) {
        int leftX = originX;
        int cardsX = originX + Math.max(1, arrowW) + Math.max(0, gutter);
        int rightX = previewX - Math.max(1, arrowW);
        int stride = Math.max(1, cardW) + Math.max(0, gutter);
        int maxVisible = Math.max(1, (rightX - cardsX) / stride);
        return new int[] {leftX, cardsX, rightX, maxVisible};
    }

    static List<String> colorSlotLabels(RaceMakerParts.Category cat) {
        if (cat == null) {
            return List.of();
        }
        return switch (cat) {
            case BODY -> List.of("Skin", "Skin 2", "Skin 3");
            case EYES -> List.of("Eye 1", "Eye 2");
            case HAIR -> List.of("Hair");
            case AURA -> List.of("Aura");
            default -> List.of();
        };
    }

    private void addCategoryColorSlots(int y) {
        List<String> labels = colorSlotLabels(category);
        int boxY = y + COLOR_LABEL_H;
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            int x = previewX + i * COLOR_SLOT_W;
            EditBox box = colorField(x, boxY, COLOR_BOX_W, hexForSlot(label), text -> {
                setHexForSlot(label, text);
                markColourPreview(label);
            });
            addRenderableWidget(box);
            addRenderableWidget(new ColorSwatch(x + COLOR_BOX_W + 2, boxY,
                    box::getValue,
                    () -> openColorPicker(box, h -> {
                        box.setValue(h);
                        setHexForSlot(label, h);
                        markColourPreview(label);
                    }),
                    () -> colorPicker.isOpenFor(box)));
        }
    }

    private String hexForSlot(String label) {
        if (label == null) {
            return skinColorHex;
        }
        return switch (label) {
            case "Skin 2" -> skin2Hex;
            case "Skin 3" -> skin3Hex;
            case "Eye 1" -> eyeColorHex;
            case "Eye 2" -> eye2Hex;
            case "Hair" -> hairColorHex;
            case "Aura" -> auraHex;
            default -> skinColorHex;
        };
    }

    private void setHexForSlot(String label, String hex) {
        String value = hex == null ? "" : hex;
        if (label == null) {
            skinColorHex = value;
            return;
        }
        switch (label) {
            case "Skin 2" -> skin2Hex = value;
            case "Skin 3" -> skin3Hex = value;
            case "Eye 1" -> eyeColorHex = value;
            case "Eye 2" -> eye2Hex = value;
            case "Hair" -> hairColorHex = value;
            case "Aura" -> auraHex = value;
            default -> skinColorHex = value;
        }
    }

    private void scrollRaces(int delta) {
        int maxVisible = raceCardMaxVisible();
        int maxScroll = Math.max(0, raceIds.size() - maxVisible);
        raceScroll = Math.max(0, Math.min(maxScroll, raceScroll + delta));
    }

    private void ensureRaceCardVisible(String raceId) {
        int idx = raceIds.indexOf(raceId);
        if (idx < 0) {
            return;
        }
        int maxVisible = raceCardMaxVisible();
        if (idx < raceScroll) {
            raceScroll = idx;
        } else if (idx >= raceScroll + maxVisible) {
            raceScroll = Math.max(0, idx - maxVisible + 1);
        }
    }

    private EditBox colorField(int x, int y, int w, String initial, java.util.function.Consumer<String> sink) {
        EditBox box = new EditBox(font, x, y, w, 16, Component.literal("colour"));
        box.setMaxLength(9);
        box.setValue(initial == null ? "" : initial);
        box.setResponder(text -> {
            if (text != null) {
                sink.accept(text);
            }
        });
        return box;
    }

    private void openColorPicker(EditBox box, java.util.function.Consumer<String> onConfirm) {
        colorPicker.open(box, box.getX() + box.getWidth() + ColorSwatch.W + 6, box.getY(),
                getUiWidth(), getUiHeight(), box.getValue(), onConfirm);
    }

    private void markColourPreview(String which) {
        status = which + " colour live on preview (Character restore each frame; Apply path TBD).";
        statusColor = MUTED;
        schedulePreview();
    }

    private void schedulePreview() {
        MakerPreviewAppearance appearance = new MakerPreviewAppearance()
                .bodyColor(skinColorHex)
                .bodyColor2(skin2Hex)
                .bodyColor3(skin3Hex)
                .eye1Color(eyeColorHex)
                .eye2Color(eye2Hex)
                .hairColor(hairColorHex)
                .auraColor(auraHex)
                .gender(gender)
                .bodyType(bodyType)
                .eyesType(eyesType)
                .mouthType(mouthType)
                .noseType(noseType)
                .tattooType(tattooType);
        if (hairPreset > 0) {
            var hair = HairPresetImport.fetchPreset(hairPreset, "Base");
            if (hair != null) {
                appearance.hair(hair);
            }
        }
        preview.setAppearance(appearance);
        preview.markDirty();
    }

    /**
     * Three AtlasCycle slots on one row. Each starts {@code cycleW + gap} after the last
     * so Gender / Category / Race cannot share pixels ({@code EyeEyes} / {@code MouMouth}).
     */
    static int[] cycleRowXs(int originX, int cycleW, int gap) {
        int stride = Math.max(1, cycleW) + Math.max(0, gap);
        return new int[] {originX, originX + stride, originX + stride * 2};
    }

    static boolean cycleSlotsOverlap(int[] xs, int cycleW) {
        if (xs == null || xs.length < 2 || cycleW <= 0) {
            return false;
        }
        for (int i = 1; i < xs.length; i++) {
            if (xs[i] < xs[i - 1] + cycleW) {
                return true;
            }
        }
        return false;
    }

    private void onRaceListCycle(int index) {
        if (index >= 0 && index < raceIds.size()) {
            selectRace(raceIds.get(index));
        }
    }

    private void onGenderCycle(int index) {
        setGender(index == 1 ? "female" : "male");
    }

    private void onCategoryCycle(int index) {
        RaceMakerParts.Category[] cats = RaceMakerParts.Category.values();
        if (index >= 0 && index < cats.length) {
            selectCategory(cats[index]);
        }
    }

    private void onPresetCycle(int index) {
        if (index >= 0 && index < partIds.size()) {
            selectPart(partIds.get(index), false);
        }
    }

    private void refreshRaces() {
        LinkedHashSet<String> known = new LinkedHashSet<>(RacePackService.knownRaceIds());
        try {
            List<String> loaded = ConfigManager.getLoadedRaces();
            if (loaded != null) {
                for (String id : loaded) {
                    if (id != null && !id.isBlank()) {
                        known.add(id.trim().toLowerCase(Locale.ROOT));
                    }
                }
            }
        } catch (Throwable ignored) {
            // ConfigManager is unavailable in unit tests; folder scan still lists custom packs.
        }
        List<String> sorted = new ArrayList<>(known);
        sorted.sort(raceComparator());
        raceIds = List.copyOf(sorted);
        if (raceIds.isEmpty()) {
            raceIds = List.of("saiyan", "namekian", "human", "frostdemon", "majin", "bioandroid");
        }
        if (!raceIds.contains(selectedRace)) {
            selectedRace = raceIds.get(0);
        }
    }

    private static Comparator<String> raceComparator() {
        return (a, b) -> {
            int ia = builtinIndex(a);
            int ib = builtinIndex(b);
            if (ia >= 0 || ib >= 0) {
                // Custom packs first so a newly created race is on the first card page.
                if (ia < 0) {
                    return -1;
                }
                if (ib < 0) {
                    return 1;
                }
                return Integer.compare(ia, ib);
            }
            return a.compareTo(b);
        };
    }

    private static int builtinIndex(String id) {
        for (int i = 0; i < BUILTIN_ORDER.length; i++) {
            if (BUILTIN_ORDER[i].equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private void refreshParts() {
        partIds = MakerPresetCatalog.partIds(category, selectedRace, gender);
        presetLabels = MakerPresetCatalog.labels(category, selectedRace, gender);
        if (!selectedPartId.isEmpty() && !partIds.contains(selectedPartId)) {
            selectedPartId = "";
        }
        if (selectedPartId.isEmpty() && !partIds.isEmpty()) {
            selectedPartId = partIds.get(0);
            preview.setGlow(MakerPreviewController.GlowTarget.PART_CATEGORY, selectedPartId);
        } else if (selectedPartId.isEmpty()) {
            preview.setGlow(MakerPreviewController.GlowTarget.RACE_CARD, selectedRace);
        }
    }

    private void setGender(String next) {
        gender = "female".equalsIgnoreCase(next) ? "female" : "male";
        status = "Body type " + gender;
        statusColor = MUTED;
        refreshParts();
        schedulePreview();
        rebuild();
    }

    private void selectRace(String race) {
        selectRace(race, true);
    }

    private void selectRace(String race, boolean announce) {
        selectedRace = race == null ? "saiyan" : race.trim().toLowerCase(Locale.ROOT);
        selectedPartId = "";
        boolean custom = RacePackService.isCustomPack(selectedRace);
        if (custom) {
            applyLoadedPack(selectedRace);
            if (announce) {
                status = "Editing custom pack " + selectedRace + " ("
                        + RacePackService.characterRelativePath(selectedRace) + "). Save writes this file.";
                statusColor = OK;
            }
        } else if (announce) {
            status = "Race " + displayRace(selectedRace)
                    + " (DMZ default — pick a * custom pack to edit, or Create one).";
            statusColor = MUTED;
        }
        preview.setGlow(MakerPreviewController.GlowTarget.RACE_CARD, selectedRace);
        refreshParts();
        ensureRaceCardVisible(selectedRace);
        schedulePreview();
        rebuild();
    }

    private void applyLoadedPack(String raceId) {
        RacePackService.readPack(raceId).ifPresent(loaded -> {
            RacePackTemplate t = loaded.template();
            if (t.defaultBodyColor() != null && !t.defaultBodyColor().isBlank()) {
                skinColorHex = t.defaultBodyColor();
            }
            if (t.defaultBodyColor2() != null && !t.defaultBodyColor2().isBlank()) {
                skin2Hex = t.defaultBodyColor2();
            }
            if (t.defaultBodyColor3() != null && !t.defaultBodyColor3().isBlank()) {
                skin3Hex = t.defaultBodyColor3();
            }
            if (t.defaultEye1Color() != null && !t.defaultEye1Color().isBlank()) {
                eyeColorHex = t.defaultEye1Color();
            }
            if (t.defaultEye2Color() != null && !t.defaultEye2Color().isBlank()) {
                eye2Hex = t.defaultEye2Color();
            }
            if (t.defaultHairColor() != null && !t.defaultHairColor().isBlank()) {
                hairColorHex = t.defaultHairColor();
            }
            if (t.defaultAuraColor() != null && !t.defaultAuraColor().isBlank()) {
                auraHex = t.defaultAuraColor();
            }
            RacePackService.RacePartDefaults parts = loaded.parts();
            bodyType = parts.bodyType();
            hairPreset = Math.max(1, parts.hairType());
            eyesType = parts.eyesType();
            noseType = parts.noseType();
            mouthType = parts.mouthType();
            tattooType = parts.tattooType();
            RaceLabels labels = loaded.labels();
            displayNameText = labels.displayName() == null ? "" : labels.displayName();
            descriptionText = labels.description() == null ? "" : labels.description();
        });
    }

    private void selectCategory(RaceMakerParts.Category next) {
        category = next == null ? RaceMakerParts.Category.BODY : next;
        selectedPartId = "";
        refreshParts();
        boolean empty = partIds.isEmpty();
        boolean live = MakerPresetCatalog.isLive(category, selectedRace, gender);
        status = category.label() + " — "
                + (empty ? RaceMakerParts.emptyReason(category)
                : (live ? "live catalog" : "FALLBACK catalog") + "; pick a part / cycle");
        statusColor = empty && (category == RaceMakerParts.Category.AURA
                || category == RaceMakerParts.Category.CLOTHES) ? WARN : MUTED;
        schedulePreview();
        rebuild();
    }

    private void selectPart(String partId) {
        selectPart(partId, true);
    }

    private void selectPart(String partId, boolean rebuildToSyncCycle) {
        selectedPartId = partId == null ? "" : partId;
        applyPartToSlots(selectedPartId);
        preview.setGlow(MakerPreviewController.GlowTarget.PART_CATEGORY, selectedPartId);
        status = "Part " + selectedPartId + " live on preview.";
        statusColor = OK;
        schedulePreview();
        if (rebuildToSyncCycle) {
            rebuild();
        }
    }

    private void applyPartToSlots(String partId) {
        int index = RaceMakerParts.parseIndex(partId);
        if (index < 0) {
            return;
        }
        switch (category) {
            case BODY -> bodyType = index;
            case EYES -> eyesType = index;
            case MOUTH -> mouthType = index;
            case HAIR -> hairPreset = Math.max(1, index);
            case EXTRA -> {
                if (partId != null && partId.contains("nose")) {
                    noseType = index;
                } else if (partId != null && partId.contains("tattoo")) {
                    tattooType = index;
                }
            }
            default -> {
            }
        }
    }

    private void saveRace() {
        if (!mayCreateRace()) {
            status = "Missing permission " + XenoPermissions.MAKER_RACE_CREATE.getNodeName();
            statusColor = WARN;
            return;
        }
        if (!RacePackService.isCustomPack(selectedRace)) {
            status = "Pick a created race pack to edit (default DMZ races are read-only here).";
            statusColor = WARN;
            return;
        }
        RacePackService.CreateResult result =
                RacePackService.updateRacePack(selectedRace, currentTemplate(), currentParts(),
                        currentLabels(selectedRace));
        if (!result.ok()) {
            status = result.message();
            statusColor = WARN;
            return;
        }
        pushLabelsToServer(result.raceId());
        boolean loaded = reloadDmz("Saved " + result.raceId());
        if (!loaded) {
            return;
        }
        status = "Updated " + result.raceId() + " at "
                + RacePackService.characterRelativePath(result.raceId())
                + (dmzHasRace(result.raceId()) ? " — DMZ has it loaded." : " — folder written.");
        statusColor = OK;
    }

    private void createRace() {
        if (!CREATE_RACE_READY) {
            status = "Create Race blocked — " + CREATE_RACE_EVIDENCE;
            statusColor = WARN;
            return;
        }
        if (!mayCreateRace()) {
            status = "Missing permission " + XenoPermissions.MAKER_RACE_CREATE.getNodeName();
            statusColor = WARN;
            return;
        }
        String id = createIdBox == null ? createIdText : createIdBox.getValue();
        RacePackService.CreateResult result =
                RacePackService.createRacePack(id, currentTemplate(), currentParts(), currentLabels(id));
        if (!result.ok()) {
            status = result.message();
            statusColor = WARN;
            return;
        }
        pushLabelsToServer(result.raceId());
        String reloadNote = "";
        try {
            ConfigManager.reload();
            reloadNote = dmzHasRace(result.raceId())
                    ? " DMZ loaded it."
                    : " Folder written; DMZ list did not include it yet.";
        } catch (Throwable t) {
            reloadNote = " Pack written but ConfigManager.reload failed: " + t.getMessage();
        }
        String shown = RaceLabelRegistry.displayName(result.raceId());
        status = "Created " + result.raceId()
                + (shown.isBlank() ? "" : " (“" + shown + "”)")
                + " at " + RacePackService.characterRelativePath(result.raceId()) + "."
                + reloadNote + " Save is on — this is a custom pack.";
        statusColor = OK;
        refreshRaces();
        selectRace(result.raceId(), false);
    }

    private RacePackTemplate currentTemplate() {
        return new RacePackTemplate(
                true, true, true, "human", "kakarot",
                skinColorHex, skin2Hex, skin3Hex,
                hairColorHex, eyeColorHex, eye2Hex, auraHex);
    }

    private void openHairEditor() {
        HairMakerDocument doc = HairMakerDocument.oneStrandDemo();
        try {
            HairMakerDocument seeded = new HairMakerDocument();
            if (hairPreset > 0 && HairPresetImport.loadIntoDocument(seeded, hairPreset, "Base")) {
                doc = seeded;
            }
        } catch (Throwable ignored) {
            // HairManager is only live inside a DMZ client.
        }
        if (hairColorHex != null && !hairColorHex.isBlank()) {
            doc.globalColor(hairColorHex);
        }
        if (minecraft != null) {
            minecraft.setScreen(new HairMakerScreen(this, doc));
        }
    }

    private RacePackService.RacePartDefaults currentParts() {
        return new RacePackService.RacePartDefaults(
                bodyType, Math.max(1, hairPreset), eyesType, noseType, mouthType, tattooType);
    }

    private RaceLabels currentLabels(String raceId) {
        String name = displayNameBox == null ? displayNameText : displayNameBox.getValue();
        String desc = descriptionBox == null ? descriptionText : descriptionBox.getValue();
        return new RaceLabels(raceId == null ? "" : raceId,
                name == null ? "" : name, desc == null ? "" : desc);
    }

    private void pushLabelsToServer(String raceId) {
        RaceLabels labels = currentLabels(raceId);
        if (labels.hasLiteral()) {
            try {
                RaceLabelNetwork.save(raceId, labels.displayName(), labels.description());
            } catch (Throwable ignored) {
                // Dedicated-server sync is best-effort; sidecar already landed locally.
            }
        }
    }

    private boolean reloadDmz(String prefix) {
        try {
            ConfigManager.reload();
            return true;
        } catch (Throwable t) {
            status = prefix + " but ConfigManager.reload failed: " + t.getMessage();
            statusColor = WARN;
            return false;
        }
    }

    private static boolean dmzHasRace(String raceId) {
        try {
            return ConfigManager.isRaceLoaded(raceId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean mayCreateRace() {
        Minecraft mc = minecraft != null ? minecraft : Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return true;
        }
        if (mc.hasSingleplayerServer()) {
            var server = mc.getSingleplayerServer();
            if (server != null) {
                ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (sp != null) {
                    return XenoPermissions.hasPermission(sp, XenoPermissions.MAKER_RACE_CREATE);
                }
            }
        }
        return mc.player.hasPermissions(2);
    }

    private void rebuild() {
        if (createIdBox != null) {
            createIdText = createIdBox.getValue();
        }
        if (displayNameBox != null) {
            displayNameText = displayNameBox.getValue();
        }
        if (descriptionBox != null) {
            descriptionText = descriptionBox.getValue();
        }
        colorPicker.close();
        init();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xCC030712);
        XenoAtlasSprites.Theme previous = XenoAtlasSprites.theme();
        try {
            beginUiScale(graphics);
            int uiMx = (int) toUiX(mouseX);
            int uiMy = (int) toUiY(mouseY);

            XenoAtlasSprites.setTheme(CHROME);
            XenoAtlasSprites.blit(graphics, BANNER, CHROME, bannerX, bannerY);
            graphics.drawCenteredString(font, "RACE CHARACTER MAKER",
                    bannerX + bannerW / 2, bannerY + bannerH / 2 - 4, GOLD);

            XenoAtlasSprites.setTheme(INNER);
            int statusY = footerY + 22;
            graphics.drawString(font, clip(status, 96), originX, statusY, statusColor, false);

            graphics.drawString(font, "Gender", cycleX[0] + 4, cycleLabelY, MUTED, false);
            graphics.drawString(font, "Category", cycleX[1] + 4, cycleLabelY, MUTED, false);
            graphics.drawString(font, "Race", cycleX[2] + 4, cycleLabelY, MUTED, false);

            renderRaceCards(graphics, uiMx, uiMy);
            XenoAtlasSprites.blit(graphics, CATEGORY_COL, INNER, categoryX, categoryY);
            renderCategories(graphics, uiMx, uiMy);
            XenoAtlasSprites.blit(graphics, PART_GRID, INNER, gridX, gridY);
            renderPartTiles(graphics, uiMx, uiMy);
            XenoAtlasSprites.blit(graphics, PREVIEW, INNER, previewX, previewY);
            graphics.drawString(font, "Preview", previewX + 10, previewY + 8, GOLD, false);
            renderColorSlotLabels(graphics);

            super.render(graphics, uiMx, uiMy, partialTick);
            // Player model last so atlas widgets never cover it.
            preview.render(graphics, previewX + 8, previewY + 24, previewW - 16, previewH - 32,
                    partialTick);
            colorPicker.render(graphics, uiMx, uiMy, partialTick);
            endUiScale(graphics);
        } finally {
            XenoAtlasSprites.setTheme(previous);
        }
    }

    private void renderColorSlotLabels(GuiGraphics graphics) {
        List<String> labels = colorSlotLabels(category);
        for (int i = 0; i < labels.size(); i++) {
            graphics.drawString(font, labels.get(i),
                    previewX + i * COLOR_SLOT_W, colorY, MUTED, false);
        }
    }

    private void renderRaceCards(GuiGraphics graphics, int mouseX, int mouseY) {
        int maxVisible = raceCardMaxVisible();
        if (raceScroll > Math.max(0, raceIds.size() - maxVisible)) {
            raceScroll = Math.max(0, raceIds.size() - maxVisible);
        }
        int x = raceRowX;
        for (int i = raceScroll; i < raceIds.size() && (i - raceScroll) < maxVisible; i++) {
            String id = raceIds.get(i);
            boolean selected = id.equals(selectedRace);
            boolean hover = mouseX >= x && mouseX < x + raceCardW
                    && mouseY >= raceRowY && mouseY < raceRowY + raceCardH;
            if (selected) {
                graphics.fill(x - 2, raceRowY - 2, x + raceCardW + 2, raceRowY + raceCardH + 2, GLOW);
            }
            XenoAtlasSprites.blit(graphics, RACE_CARD,
                    hover ? CHROME : INNER, x, raceRowY);
            graphics.drawCenteredString(font, cardRaceLabel(id), x + raceCardW / 2,
                    raceRowY + raceCardH / 2 - 4, selected ? OK : LIGHT);
            x += raceCardW + 4;
        }
    }

    private void renderCategories(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = categoryY + 12;
        for (RaceMakerParts.Category cat : RaceMakerParts.Category.values()) {
            boolean selected = cat == category;
            boolean hover = mouseX >= categoryX + 6 && mouseX < categoryX + categoryW - 6
                    && mouseY >= y - 2 && mouseY < y + 14;
            if (selected) {
                graphics.fill(categoryX + 4, y - 2, categoryX + categoryW - 4, y + 14, GLOW);
            }
            int color = selected ? OK : (hover ? GOLD : LIGHT);
            graphics.drawString(font, cat.label(), categoryX + 12, y, color, false);
            y += 18;
        }
    }

    private void renderPartTiles(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, category.label(), gridX + 10, gridY + 8, GOLD, false);
        if (partIds.isEmpty()) {
            graphics.drawWordWrap(font, Component.literal(RaceMakerParts.emptyReason(category)),
                    gridX + 10, gridY + 28, gridW - 20, MUTED);
            return;
        }
        int cols = Math.max(1, (gridW - 16) / (tileW + 4));
        int x0 = gridX + 10;
        int y0 = gridY + 26;
        for (int i = 0; i < partIds.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int x = x0 + col * (tileW + 4);
            int y = y0 + row * (tileH + 4);
            if (y + tileH > gridY + gridH - 8) {
                break;
            }
            String id = partIds.get(i);
            boolean selected = id.equals(selectedPartId);
            boolean hover = mouseX >= x && mouseX < x + tileW && mouseY >= y && mouseY < y + tileH;
            if (selected) {
                graphics.fill(x - 2, y - 2, x + tileW + 2, y + tileH + 2, GLOW);
            }
            XenoAtlasSprites.blit(graphics, TILE,
                    hover || selected ? CHROME : INNER, x, y);
            String shortLabel = shortPartLabel(id, i);
            graphics.drawCenteredString(font, shortLabel, x + tileW / 2, y + tileH / 2 - 4, LIGHT);
        }
    }

    private String shortPartLabel(String partId, int index) {
        int n = RaceMakerParts.parseIndex(partId);
        if (n >= 0) {
            return Integer.toString(n);
        }
        if (index >= 0) {
            return Integer.toString(index);
        }
        return "?";
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }

    static String cycleRaceLabel(String id) {
        boolean custom = false;
        try {
            custom = RacePackService.isCustomPack(id);
        } catch (Throwable ignored) {
            // FML config dir is missing in unit tests.
        }
        return cycleRaceLabel(id, custom);
    }

    static String cycleRaceLabel(String id, boolean custom) {
        String name = displayRace(id);
        return custom ? "*" + name : name;
    }

    static String cardRaceLabel(String id) {
        return cycleRaceLabel(id);
    }

    static String displayRace(String id) {
        if (id == null || id.isBlank()) {
            return "?";
        }
        try {
            String labeled = RaceLabelRegistry.displayName(id);
            if (labeled != null && !labeled.isBlank()) {
                return labeled;
            }
        } catch (Throwable ignored) {
            // Registry is empty in unit tests without a snapshot.
        }
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "frostdemon" -> "Arcosian";
            case "bioandroid" -> "Bio-Android";
            case "saiyan" -> "Saiyan";
            case "namekian" -> "Namekian";
            case "human" -> "Human";
            case "majin" -> "Majin";
            default -> {
                String raw = id.replace('_', ' ');
                yield raw.isEmpty() ? id
                        : Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
            }
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (colorPicker.isOpen() && colorPicker.mouseClicked(toUiX(mouseX), toUiY(mouseY), button)) {
            return true;
        }
        double uiMx = toUiX(mouseX);
        double uiMy = toUiY(mouseY);
        if (button == 0) {
            int maxVisible = raceCardMaxVisible();
            int x = raceRowX;
            for (int i = raceScroll; i < raceIds.size() && (i - raceScroll) < maxVisible; i++) {
                if (uiMx >= x && uiMx < x + raceCardW
                        && uiMy >= raceRowY && uiMy < raceRowY + raceCardH) {
                    selectRace(raceIds.get(i));
                    return true;
                }
                x += raceCardW + 4;
            }

            int cy = categoryY + 12;
            for (RaceMakerParts.Category cat : RaceMakerParts.Category.values()) {
                if (uiMx >= categoryX + 6 && uiMx < categoryX + categoryW - 6
                        && uiMy >= cy - 2 && uiMy < cy + 14) {
                    selectCategory(cat);
                    return true;
                }
                cy += 18;
            }

            if (!partIds.isEmpty()) {
                int cols = Math.max(1, (gridW - 16) / (tileW + 4));
                int x0 = gridX + 10;
                int y0 = gridY + 26;
                for (int i = 0; i < partIds.size(); i++) {
                    int col = i % cols;
                    int row = i / cols;
                    int px = x0 + col * (tileW + 4);
                    int py = y0 + row * (tileH + 4);
                    if (py + tileH > gridY + gridH - 8) {
                        break;
                    }
                    if (uiMx >= px && uiMx < px + tileW
                            && uiMy >= py && uiMy < py + tileH) {
                        selectPart(partIds.get(i));
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (colorPicker.isOpen() && colorPicker.mouseDragged(toUiX(mouseX), toUiY(mouseY), button)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (colorPicker.isOpen() && colorPicker.mouseReleased(toUiX(mouseX), toUiY(mouseY), button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (colorPicker.isOpen()) {
            return true;
        }
        double uiMy = toUiY(mouseY);
        if (uiMy >= raceRowY && uiMy < raceRowY + raceCardH) {
            int maxVisible = raceCardMaxVisible();
            int maxScroll = Math.max(0, raceIds.size() - maxVisible);
            if (scrollY > 0) {
                raceScroll = Math.max(0, raceScroll - 1);
            } else if (scrollY < 0) {
                raceScroll = Math.min(maxScroll, raceScroll + 1);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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

    public MakerPreviewController previewController() {
        return preview;
    }

    public String selectedRace() {
        return selectedRace;
    }

    public RaceMakerParts.Category selectedCategory() {
        return category;
    }

    public String skinColorHex() {
        return skinColorHex;
    }

    public String eyeColorHex() {
        return eyeColorHex;
    }

    public String hairColorHex() {
        return hairColorHex;
    }

    public static RaceCharacterMakerScreen create(Screen parent) {
        return new RaceCharacterMakerScreen(parent);
    }
}

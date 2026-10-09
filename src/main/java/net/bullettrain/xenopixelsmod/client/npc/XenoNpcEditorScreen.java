package net.bullettrain.xenopixelsmod.client.npc;

import com.dragonminez.client.gui.character.util.ScaledScreen;
import com.google.gson.JsonObject;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcFullDmzRenderer;
import net.bullettrain.xenopixelsmod.client.npc.editor.EditorFooter;
import net.bullettrain.xenopixelsmod.client.npc.editor.EditorLayout;
import net.bullettrain.xenopixelsmod.client.npc.editor.DialogueDraft;
import net.bullettrain.xenopixelsmod.client.npc.dialog.QuestCatalogChoices;
import net.bullettrain.xenopixelsmod.network.packet.XenoNpcStoreWritePacket;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStore;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStorePaths;
import net.bullettrain.xenopixelsmod.client.npc.store.ClientNpcStoreIndex;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcDataSource;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogueNbt;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue;
import net.bullettrain.xenopixelsmod.client.npc.editor.EditorRow;
import net.bullettrain.xenopixelsmod.client.npc.faction.ClientFactions;
import net.bullettrain.xenopixelsmod.client.npc.editor.NpcLivePreview;
import net.bullettrain.xenopixelsmod.client.npc.editor.NpcEditorScreenManifest;
import net.bullettrain.xenopixelsmod.client.npc.editor.NpcEditorScreenManifest.ScreenId;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasButton;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasCycle;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasNotice;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasPanel;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasSound;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasStepper;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasTabStrip;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasTextFit;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasToggle;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.bullettrain.xenopixelsmod.compat.npc.NpcBrainPolicy;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatBrainVersion;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcFormLookup;
import net.bullettrain.xenopixelsmod.compat.npc.NpcFlightPolicy;
import net.bullettrain.xenopixelsmod.compat.npc.NpcHairBridge;
import net.bullettrain.xenopixelsmod.compat.npc.NpcSkillSet;
import net.bullettrain.xenopixelsmod.compat.npc.NpcTechniqueLevels;
import net.bullettrain.xenopixelsmod.compat.npc.PredefinedTechniqueLookup;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormDocument;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormKind;
import net.bullettrain.xenopixelsmod.features.progression.QuestAvailability;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.npc.XenoNpcBehaviour;
import net.bullettrain.xenopixelsmod.network.packet.XenoNpcActionPacket;
import net.bullettrain.xenopixelsmod.network.packet.XenoNpcDeletePacket;
import net.bullettrain.xenopixelsmod.network.packet.XenoNpcEditorLockPacket;
import net.bullettrain.xenopixelsmod.network.packet.XenoNpcSavePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * The native Xeno NPC editor.
 *
 * <h2>Layout</h2>
 * Rows are declared as {@link EditorRow} data and placed by {@link EditorLayout}, which pairs them
 * two per line and splits them across pages against a real body-height budget. The footer strip is
 * reserved out of that budget, so content can no longer draw underneath the Save button or past the
 * bottom of the frame the way it did before.
 *
 * <p>Widgets are built only once the layout has resolved their rectangle. That ordering fixes a
 * second bug: fields used to be constructed at width 1 and resized afterwards, but
 * {@code EditBox.setValue} immediately runs {@code scrollTo}, and at width 1 the inner width is
 * negative, so {@code displayPos} was pushed to the end of the string and the field rendered blank
 * even though the value was present.
 *
 * <h2>Why saves send a subset</h2>
 * {@code NpcCombatProfile.withKiWeapon} documents that a screen saving the whole profile overwrites
 * fields owned by other DMZ screens. {@code XenoNpcSavePacket} merges incoming keys over the NPC's
 * current profile, which is only safe while the client sends a subset, so this screen tracks which
 * tag keys it actually changed and sends only those. Sending everything would clobber a transform
 * or mastery that happened while the editor was open - and the revision guard does not catch that,
 * because {@code XenoNpcData.revision()} bumps on name/faction/owner only.
 */
public final class XenoNpcEditorScreen extends ScaledScreen implements ClientNpcProfiles.Refreshable {

    /** Drawn at the vanilla GUI Scale; see {@link NpcGuiScale}. */
    @Override
    protected float computeDynamicScale(float available) {
        return NpcGuiScale.dynamicScale(super.computeDynamicScale(available));
    }
    /** Inline colour picker shared by every colour row on this screen; opens over it. */
    private final net.bullettrain.xenopixelsmod.client.ui.atlas.InlineColorPicker colorPicker = new net.bullettrain.xenopixelsmod.client.ui.atlas.InlineColorPicker();
    /**
     * The editor frame, narrowed from 600 so the visualizer fits beside it.
     *
     * <p>The canvas is about 640x360, and the old 600-wide frame filled it - a panel placed
     * beside it was clamped straight back on top. 420 + 8 + the 176-wide preview is 604.
     * {@code xeno_editor_panel} is still registered and still used by XenoPartyScreen.
     */
    private static final String FRAME = "xeno_editor_panel_w420";
    private static final String PREVIEW_PANEL = "mynpcs_small_panel";

    /** Gap between the editor frame and the visualizer beside it. Matches {@code NpcPreviewPanel.GAP}. */
    private static final int PREVIEW_GAP = 8;

    /**
     * Height the frame would be if every page filled it, and the sprite actually drawn.
     *
     * <p>Pagination is computed against the full height so page breaks never move between tabs;
     * the drawn frame then shrinks to whatever the current page needs, which is what removes the
     * empty band above the footer.
     */
    private int frameFullH;
    private String frameSprite = FRAME;
    private static final String PRIMARY = "pill_button";
    private static final String ARROW = "mynpcs_button_arrow";

    private static final int ROW_H = 24;
    private static final int COLUMN_GAP = 6;
    private static final int CONTROL_DX = 78;
    private static final int FIELD_H = 18;
    private static final int HEADER_H = 54;
    private static final int FOOTER_H = 38;

    private static final int GOLD = 0xFFFFC14A;
    private static final int CYAN = 0xFF80D8FF;
    private static final int LIGHT = 0xFFE7EDF3;
    private static final int MUTED = 0xFF8AA4B8;
    private static final int WARN = 0xFFFF8A80;

    private static final List<String> KI_WEAPONS = List.of("blade", "scythe", "staff", "claw");

    private static final List<Component> TABS = NpcEditorScreenManifest.tabs().stream()
            .<Component>map(Component::literal)
            .toList();
    private static final int TAB_DELETE = 8;
    private static final int TAB_CLOSE = 9;

    private final int entityId;
    private final CompoundTag data;
    private NpcCombatProfile profile;
    private int revision;
    /** MyNPCs-style autosave: a change saves itself after half a second without further edits. */
    private final AutoSaveScheduler autoSave = new AutoSaveScheduler(10);
    private long editorTicks;
    /** The Save button or closing asked to close once the in-flight save is confirmed. */
    private boolean closeAfterSave;
    /** The NPC changed elsewhere (stale revision): autosave stops until the editor is reopened. */
    private boolean autoSaveStopped;
    private Set<String> sentKeys = Set.of();
    private Set<String> sentDataKeys = Set.of();
    private boolean npcSavePending;
    private boolean refreshPending;
    /** The visualizer. Native and self-contained; see NpcLivePreview for why not the compat one. */
    private final NpcLivePreview preview = new NpcLivePreview();

    /** Tag keys this screen actually changed; only these are sent. */
    private final Set<String> dirtyKeys = new LinkedHashSet<>();

    /**
     * Dirty keys for the NPC's data tag, tracked apart from the profile's.
     *
     * <p>Two sets because there are two destinations. Home, leash and respawn live on
     * {@code XenoNpcData}; everything else on this screen lives on {@code NpcCombatProfile}. One
     * shared set would send data keys in the profile payload, where they would pass no whitelist
     * and be dropped without a word.
     */
    private final Set<String> dataDirtyKeys = new LinkedHashSet<>();

    /** Page index per tab, so leaving a tab and coming back keeps the page. */
    private final int[] tabPage = new int[TABS.size()];

    private int selectedTab;
    private ScreenId subPage;
    private int frameX;
    private int frameY;
    private int frameW;
    private int frameH;
    private int previewX;
    private int previewY;
    private int previewW;
    private int previewH;
    private int bodyX;
    private int bodyY;
    private int bodyW;
    private int bodyH;

    private int pageCount = 1;
    private boolean deleteArmed;
    private long deleteArmedAt;

    /** How long a primed delete stays primed. */
    private static final long DELETE_ARM_MS = 5000L;
    private String notice = "";
    /** When the notice was set. It clears itself so it cannot sit on the footer forever. */
    private long noticeSetAt;

    /** How long a notice stays up. Long enough to read, short enough not to become furniture. */
    private static final long NOTICE_MS = 4000L;

    private String editedName;
    private String editedTitle;
    private String editedFaction;

    /**
     * This NPC's own conversation, while it is being written.
     *
     * <p>Held as a draft rather than as the immutable {@code XenoDialogue}: a dialogue mid-edit is
     * routinely invalid - a node just added and not yet named, a start pointing at a node about to
     * be renamed - and the record will not hold that state.
     */
    private DialogueDraft dialogueDraft;
    private boolean editingStoreDialogue;
    private boolean returnToAdvancedDialogs;
    private boolean storeDialogueDirty;
    private boolean awaitingStoreDialogueSave;
    private CompoundTag submittedStoreDialogue;
    private int storeDialogueRevision;
    private String selectedStoreDialogGroup = XenoNpcDataSource.DEFAULT_GROUP;
    private String questGroup = XenoNpcDataSource.DEFAULT_GROUP;
    private String questId = "";
    private String questTitle = "";
    private String questDescription = "";
    private String questLogText = "";
    private String questCategory = "";
    private String questParameter = "";
    private String questCommand = "";
    private String questComplete = "";
    private int questObjective;
    private int questRepeat;
    private boolean questSaveAwaiting;
    private boolean questSkillPointsEnabled = xenoSkillPointsOffered();

    /**
     * XenoSkill points are XenoPixels progression. XenoNPCs is exported from this source with mod id
     * "xenonpcs" and has no use for them, so there the quest reward is neither offered nor paid.
     * 2026-09-30 owner: "remove give xenoskillpoints".
     */
    static boolean xenoSkillPointsOffered(String modId) {
        return !"xenonpcs".equals(modId);
    }

    private static boolean xenoSkillPointsOffered() {
        return xenoSkillPointsOffered(net.bullettrain.xenopixelsmod.XenoPixelsMod.MOD_ID);
    }

    /** Points the quest pays when the toggle is on: the loaded quest's own count, else 2. */
    private int questSkillPoints = 2;
    private boolean questRewardActionsEnabled;
    private boolean questRandomReward;
    /** Kill NPC quests: the named target hunts the player from quest start (todolist #14). */
    private boolean questTargetHunts;
    private int questCompletionPalette;
    private int questCompletionFrame;
    private int questTarget = 1;
    private String questEditingId = "";
    private String questSelectGroup = "";
    private String questSelectId = "";
    private int storeQuestRevision;
    private boolean questLoadAwaiting;
    private JsonObject loadedQuestDefinition;
    private final java.util.Map<String, CompoundTag> sceneDrafts = new java.util.LinkedHashMap<>();
    private String sceneDraftId = "";
    private String sceneStepValue = "";
    private int sceneStepTime;

    /** Which node and option the sub-screens are showing. Indices, so a rebuild keeps them. */
    private int dialogueNode = -1;
    private int dialogueOption = -1;

    /** Rows placed on the current page, kept for text drawing. */
    private List<EditorLayout.Placed> placed = List.of();
    /** Shared scale per row so labels and their controls shrink together. */
    private java.util.Map<Integer, Float> lineTextScales = java.util.Map.of();

    public XenoNpcEditorScreen(int entityId, CompoundTag data) {
        super(Component.literal("Xeno NPC Editor"));
        this.entityId = entityId;
        this.data = data == null ? new CompoundTag() : data.copy();
        this.revision = revisionFromPayload(this.data);
        this.profile = NpcCombatProfile.fromTag(this.data.getCompound("Profile"));
        this.dialogueDraft = DialogueDraft.of(this.profile.dialogueTag);
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void init() {
        super.init();

        int canvasW = getUiWidth();
        int canvasH = getUiHeight();

        // The visualizer sits BESIDE the frame, the way My NPCs arranges its own, so the two are
        // laid out as one block and that block is centred - otherwise centring the frame alone
        // would push the panel off the right of the canvas.
        int[] previewFitted = XenoAtlasSprites.fittedSize(PREVIEW_PANEL,
                Math.max(40, canvasW / 3), Math.max(40, canvasH - 40));
        previewW = previewFitted[0];
        previewH = previewFitted[1];

        int[] fitted = XenoAtlasSprites.fittedSize(FRAME,
                canvasW - 8 - previewW - PREVIEW_GAP, canvasH - 8);
        frameW = fitted[0];
        frameFullH = fitted[1];
        frameH = frameFullH;
        frameSprite = FRAME;

        int blockW = frameW + PREVIEW_GAP + previewW;
        frameX = Math.max(4, (canvasW - blockW) / 2);
        frameY = (canvasH - frameH) / 2;

        previewX = frameX + frameW + PREVIEW_GAP;
        previewY = frameY;
        // A very small canvas can still leave the panel hanging off the edge. Pull it back rather
        // than letting it draw off-screen, which is what "the visualizer is missing" looks like -
        // the same clamp NpcPreviewPanel applies on the My NPCs side.
        previewX = Math.min(previewX, canvasW - previewW - 4);
        preview.place(previewX, previewY, previewW, previewH);

        bodyX = frameX + 18;
        bodyY = frameY + HEADER_H;
        // The body now spans the whole frame: the preview no longer takes a column out of it, so
        // this stops subtracting one. Page breaks are the same on every tab as a result.
        bodyW = Math.max(120, (frameX + frameW - 18) - bodyX);
        // Deliberately the FULL height: a page never holds more rows than the tall frame fits, so
        // shrinking the drawn frame afterwards can only ever remove empty space.
        bodyH = Math.max(ROW_H, (frameY + frameFullH - FOOTER_H) - bodyY);

        rebuild();
    }

    private void selectTab(int tab) {
        if (editingStoreDialogue && storeDialogueDirty) {
            showNotice("Save or discard the global dialog before changing tabs");
            return;
        }
        if (tab == TAB_CLOSE) {
            onClose();
            return;
        }
        if (selectedTab != tab) {
            deleteArmed = false;
        }
        if (tab != 4) {
            returnToAdvancedDialogs = false;
        }
        selectedTab = Math.max(0, Math.min(TABS.size() - 1, tab));
        subPage = null;
        rebuild();
    }

    @Override
    public void onClose() {
        if (editingStoreDialogue && storeDialogueDirty) {
            showNotice("Save or discard the global dialog before closing");
            return;
        }
        if (npcSavePending) {
            // Close as soon as the save in flight is confirmed.
            closeAfterSave = true;
            showNotice("Saving NPC; closing when the server confirms");
            return;
        }
        if (autoSave.pending() && !autoSaveStopped) {
            closeAfterSave = true;
            sendNpcSave(true);
            return;
        }
        super.onClose();
    }

    /** Another editor, a script or a command changed this NPC's profile on the server. */
    @Override
    public void onServerProfile(int changedEntityId) {
        if (changedEntityId != entityId) return;
        CompoundTag server = ClientNpcProfiles.baselineCopy(entityId);
        if (server == null) return;
        // Keys edited here and not yet saved keep this editor's value; everything else follows
        // the server, so the editor shows script edits and never saves a stale copy back.
        CompoundTag mine = profile.toTag();
        java.util.Set<String> unsaved = new java.util.HashSet<>(dirtyKeys);
        unsaved.addAll(sentKeys);
        for (String key : unsaved) {
            if (mine.contains(key)) server.put(key, mine.get(key).copy());
            else server.remove(key);
        }
        if (server.equals(mine)) return;
        profile = NpcCombatProfile.fromTag(server);
        data.put("Profile", server.copy());
        scheduleRefresh();
    }

    private void rebuild() {
        refreshPending = false;
        clearWidgets();

        bodyW = Math.max(120, contentRight() - bodyX);

        // Budget and detach point, both from the reference menu. The strip has to be told how much
        // room it has: narrowing the frame to 420 left the ten tabs about one cell too wide, which
        // pushed X past the frame edge. TAB_DELETE starts the detached tail, so Delete and X read
        // as their own group instead of as two more content tabs.
        addRenderableWidget(new AtlasTabStrip(frameX + 8, frameY + 20, TABS, selectedTab,
                this::selectTab, frameW - 16, TAB_DELETE));

        List<EditorRow> rows = rowsFor(selectedTab);
        int rowHeight = switch (currentPage().id()) {
            case DISPLAY, INVENTORY, DMZ, BRAIN -> 22;
            default -> ROW_H;
        };
        EditorLayout layout = new EditorLayout(bodyX, bodyY, bodyW, bodyH, rowHeight, COLUMN_GAP);
        List<EditorLayout.Page> pages = layout.place(rows);

        pageCount = pages.size();
        tabPage[selectedTab] = EditorLayout.clampPage(tabPage[selectedTab], pageCount);
        placed = pages.get(tabPage[selectedTab]).rows();

        fitFrameToPage(rowHeight);

        lineTextScales = editorLineScales();
        for (EditorLayout.Placed p : placed) {
            materialise(p, lineTextScales.getOrDefault(p.y(), 1.0f));
        }

        buildFooter();
    }

    /** Recreate dependent rows after the current widget event, keeping an active text edit intact. */
    private void scheduleRefresh() {
        refreshPending = true;
    }

    @Override
    public void tick() {
        super.tick();
        editorTicks++;
        if (!autoSaveStopped && !npcSavePending && !editingStoreDialogue && autoSave.due(editorTicks)) {
            sendNpcSave(false);
        }
        if (!refreshPending) return;
        String focusedLabel = null;
        int cursor = 0;
        if (getFocused() instanceof EditBox box) {
            focusedLabel = box.getMessage().getString();
            cursor = box.getCursorPosition();
        }
        rebuild();
        if (focusedLabel != null) {
            for (var child : children()) {
                if (child instanceof EditBox box
                        && box.getMessage().getString().equals(focusedLabel)) {
                    setFocused(box);
                    box.setFocused(true);
                    box.setCursorPosition(Math.min(cursor, box.getValue().length()));
                    break;
                }
            }
        }
    }

    /**
     * Shrinks the drawn frame to the page it is about to draw.
     *
     * <p>A tab with four rows was drawing the same 320-tall frame as a tab with twenty, leaving a
     * visible empty band between the last row and Save/Close. The frame cannot simply be drawn
     * shorter - {@code AtlasPanel} resamples a size it was not generated at, and this art has its
     * border baked in - so the shorter heights are generated and the shortest that fits is chosen.
     *
     * <p>Only the bottom edge moves. {@code frameY} stays where the full frame would start, so the
     * rows, the tab strip and the visualizer beside it do not jump as pages change.
     */
    private void fitFrameToPage(int rowHeight) {
        int bottom = bodyY;
        for (EditorLayout.Placed p : placed) {
            bottom = Math.max(bottom, p.y() + rowHeight);
        }
        int needed = (bottom - frameY) + FOOTER_H;
        frameSprite = XenoAtlasSprites.editorFrame(needed);
        frameH = Math.min(frameFullH, XenoAtlasSprites.get(frameSprite).height());
    }

    /**
     * How wide a slot row's clear button asks to be, and the gap after it.
     *
     * <p>Asked for, not assumed: {@link XenoAtlasSprites#rowWithin} answers with the widest
     * generated row that fits, and the button is then sized from that sprite rather than from this
     * number. The two used to be allowed to disagree -- the layout reserved eighteen pixels and the
     * sprite came back sixty-four wide -- and the clear button drew over the pick button beside it.
     */
    private static final int SLOT_CLEAR_REQUEST = 18;
    private static final int SLOT_GAP = 4;

    /** The sprite a slot row's clear button uses, and therefore its real width. */
    private static String slotClearSprite() {
        return XenoAtlasSprites.rowWithin(SLOT_CLEAR_REQUEST);
    }

    /** The sprite a slot row's pick button uses, given the column it sits in. */
    private static String slotPickSprite(int columnWidth) {
        int clearWidth = XenoAtlasSprites.get(slotClearSprite()).width();
        return XenoAtlasSprites.rowWithin(Math.max(40, columnWidth - clearWidth - SLOT_GAP));
    }

    /** Keeps the long DMZ catalog rows readable while giving their labels a little more room. */
    private float dmzListTextScale() {
        return currentPage().id() == ScreenId.DMZ_SKILLS
                || currentPage().id() == ScreenId.DMZ_TECHNIQUES ? 0.80f : 1.0f;
    }

    /** Builds the live widget for a placed row, at its final rectangle. */
    private java.util.Map<Integer, Float> editorLineScales() {
        java.util.Map<Integer, java.util.List<AtlasTextFit.Measure>> measures = new java.util.HashMap<>();
        for (EditorLayout.Placed p : placed) {
            String text = null;
            int available = 0;
            // instanceof chain rather than a pattern switch: the same source compiles for Java 17 (1.20.1).
            EditorRow row = p.row();
            String rowLabel = row instanceof EditorRow.Field field ? field.label()
                    : row instanceof EditorRow.Color color ? color.label()
                    : row instanceof EditorRow.Toggle toggle ? toggle.label()
                    : row instanceof EditorRow.Cycle cycle ? cycle.label()
                    : row instanceof EditorRow.Stepper stepper ? stepper.label()
                    : row instanceof EditorRow.BrainAction action ? action.label()
                    : null;
            if (rowLabel != null) {
                measures.computeIfAbsent(p.y(), ignored -> new java.util.ArrayList<>()).add(
                        new AtlasTextFit.Measure(font.width(rowLabel), CONTROL_DX - 4));
            }
            { // was a pattern switch; if/else keeps it Java 17 source
                Object __switched1 = p.row();
                if (__switched1 instanceof EditorRow.Action a) {
                    String sprite = "mynpcs_button_row".equals(a.sprite())
                            ? XenoAtlasSprites.rowWithin(p.columnWidth()) : a.sprite();
                    text = a.label();
                    available = Math.max(4, XenoAtlasSprites.get(sprite).width() - 8);
                }
                else if (__switched1 instanceof EditorRow.Slot slot) {
                    measures.computeIfAbsent(p.y(), ignored -> new java.util.ArrayList<>()).add(
                            new AtlasTextFit.Measure(font.width("X"),
                                    Math.max(4, XenoAtlasSprites.get(slotClearSprite()).width() - 8)));
                    text = slot.label();
                    available = Math.max(4,
                            XenoAtlasSprites.get(slotPickSprite(p.columnWidth())).width() - 8);
                }
                else if (__switched1 instanceof EditorRow.Toggle ignored) {
                    text = "Yes";
                    available = XenoAtlasSprites.get("mynpcs_button_row").width() - 8;
                }
                else if (__switched1 instanceof EditorRow.Cycle c) {
                    text = c.values().stream().max(java.util.Comparator.comparingInt(font::width)).orElse("");
                    available = XenoAtlasSprites.get("mynpcs_button_row").width() - 8;
                }
                else if (__switched1 instanceof EditorRow.Stepper st) {
                    text = java.util.stream.Stream.of(Integer.toString(st.min()),
                                    Integer.toString(st.max()), Integer.toString(st.value()))
                            .max(java.util.Comparator.comparingInt(font::width)).orElse("");
                    available = XenoAtlasSprites.get("mynpcs_button_row").width() - 8;
                }
                else if (__switched1 instanceof EditorRow.BrainAction ignored) {
                    text = "Yes";
                    available = XenoAtlasSprites.get("mynpcs_button_row").width() - 8;
                }
                else { }
            }
            if (text != null) measures.computeIfAbsent(p.y(), ignored -> new java.util.ArrayList<>())
                    .add(new AtlasTextFit.Measure(font.width(text), available));
        }
        java.util.Map<Integer, Float> result = new java.util.HashMap<>();
        float pageScale = dmzListTextScale();
        measures.forEach((y, group) -> result.put(y,
                AtlasTextFit.groupScale(group, 0.55f) * pageScale));
        return result;
    }

    private void materialise(EditorLayout.Placed p, float textScale) {
        int controlX = p.x() + CONTROL_DX;
        int controlW = Math.max(40, p.columnWidth() - CONTROL_DX);

        { // was a pattern switch; if/else keeps it Java 17 source
            Object __switched2 = p.row();
            if (__switched2 instanceof EditorRow.Field f) {
                EditBox box = new EditBox(font, controlX, p.y() + 2, controlW, FIELD_H,
                        Component.literal(f.label()));
                box.setMaxLength(f.maxLength());
                // Width is already final here, so setValue's scrollTo computes a sane displayPos.
                box.setValue(f.initial() == null ? "" : f.initial());
                // Attack animation slots are ordered text fields. An empty EditBox is visually
                // indistinguishable from an unrendered placeholder, so show the expected clip
                // name directly until the author fills the slot.
                if (currentPage().id() == ScreenId.DMZ_ATTACKS
                        && (f.initial() == null || f.initial().isBlank())) {
                    box.setHint(Component.literal("Animation clip"));
                }
                if (f.sink() == null) {
                    disable(box);
                } else {
                    box.setResponder(f.sink());
                }
                addRenderableWidget(box);
            }
            else if (__switched2 instanceof EditorRow.Color c) {
                int swatchW = 16;
                int boxW = Math.max(40, controlW - swatchW - 3);
                EditBox box = new EditBox(font, controlX, p.y() + 2, boxW, FIELD_H,
                        Component.literal(c.label()));
                box.setMaxLength(9);
                box.setValue(c.initial() == null ? "" : c.initial());
                if (c.sink() == null) {
                    disable(box);
                } else {
                    box.setResponder(c.sink());
                }
                addRenderableWidget(box);
                net.bullettrain.xenopixelsmod.client.ui.atlas.ColorSwatch swatch = new net.bullettrain.xenopixelsmod.client.ui.atlas.ColorSwatch(controlX + boxW + 3, p.y() + 1, box::getValue,
                        c.sink() == null ? () -> { } : () -> openColorPicker(box),
                        () -> colorPicker.isOpenFor(box));
                if (c.sink() == null) disable(swatch);
                addRenderableWidget(swatch);
            }
            else if (__switched2 instanceof EditorRow.Toggle t) {
                AtlasToggle widget = new AtlasToggle(controlX, p.y(), controlW, t.value(),
                        Component.empty(), t.sink()).groupTextScale(textScale)
                        .narrationLabel(Component.literal(t.label()));
                if (t.sink() == null) disable(widget);
                addRenderableWidget(widget);
            }
            else if (__switched2 instanceof EditorRow.Cycle c) {
                AtlasCycle widget = new AtlasCycle(controlX, p.y(), Component.empty(), c.values(),
                        c.selected(), c.sink()).groupTextScale(textScale)
                        .narrationLabel(Component.literal(c.label()));
                if (c.sink() == null) disable(widget);
                addRenderableWidget(widget);
            }
            else if (__switched2 instanceof EditorRow.Stepper st) { addRenderableWidget(new AtlasStepper(controlX, p.y(),
                    Component.empty(), st.value(), st.min(), st.max(), st.sink())
                    .groupTextScale(textScale).narrationLabel(Component.literal(st.label()))); }
            else if (__switched2 instanceof EditorRow.Action a) {
                String sprite = "mynpcs_button_row".equals(a.sprite())
                        ? XenoAtlasSprites.rowWithin(p.columnWidth()) : a.sprite();
                int buttonWidth = XenoAtlasSprites.get(sprite).width();
                int buttonX = p.x() + Math.max(0, (p.columnWidth() - buttonWidth) / 2);
                AtlasButton widget = new AtlasButton(buttonX, p.y(), Component.literal(a.label()),
                        sprite, b -> {
                    if (a.onPress() != null) a.onPress().run();
                }, textScale);
                if (a.onPress() == null) disable(widget, disabledActionReason(a.label()));
                addRenderableWidget(widget);
            }
            else if (__switched2 instanceof EditorRow.Slot slot) {
                // number, then a narrow clear, then the wide pick - the anatomy of their row.
                // The clear button is measured from the sprite it actually got rather than from
                // the width it asked for, so the pick button beside it starts past its real edge.
                String clearSprite = slotClearSprite();
                int clearWidth = AtlasButton.nativeWidth(clearSprite);
                int gap = SLOT_GAP;
                AtlasButton clear = new AtlasButton(p.x(), p.y(), Component.literal("X"),
                        clearSprite, b -> {
                    if (slot.onClear() != null) slot.onClear().run();
                }, textScale);
                if (slot.onClear() == null) {
                    // Nothing assigned, so nothing to clear. Disabled rather than absent, so the
                    // twelve rows stay the same shape whether filled or not.
                    disable(clear);
                }
                addRenderableWidget(clear);

                AtlasButton pick = new AtlasButton(p.x() + clearWidth + gap, p.y(),
                        Component.literal(slot.label()),
                        slotPickSprite(p.columnWidth()), b -> {
                    if (slot.onPick() != null) slot.onPick().run();
                }, textScale);
                if (slot.onPick() == null) disable(pick);
                addRenderableWidget(pick);
            }
            else if (__switched2 instanceof EditorRow.BrainAction action) { materialiseBrainAction(p, action); }
            else if (__switched2 instanceof EditorRow.Spacer ignored) { }
            else if (__switched2 instanceof EditorRow.Text ignored) { }
            else if (__switched2 instanceof EditorRow.Heading ignored) { }
        }
    }

    private void materialiseBrainAction(EditorLayout.Placed placed, EditorRow.BrainAction action) {
        int toggleX = placed.x() + 62;
        int chanceX = placed.x() + 146;
        int modifierX = placed.x() + 254;
        addRenderableWidget(new AtlasToggle(toggleX, placed.y(), 72, action.enabled(),
                Component.empty(), action.enabledSink()));

        EditBox chance = new EditBox(font, chanceX, placed.y() + 2, 76, FIELD_H,
                Component.literal(action.label() + " chance"));
        chance.setMaxLength(3);
        chance.setValue(String.valueOf(action.chance()));
        chance.setResponder(text -> {
            try {
                action.chanceSink().accept(Integer.parseInt(text.trim()));
            } catch (NumberFormatException ignored) {
            }
        });
        addRenderableWidget(chance);

        EditBox modifier = new EditBox(font, modifierX, placed.y() + 2, 76, FIELD_H,
                Component.literal(action.label() + " modifier"));
        modifier.setMaxLength(8);
        modifier.setValue(fmt(action.modifier()));
        modifier.setResponder(text -> {
            try {
                action.modifierSink().accept(Float.parseFloat(text.trim()));
            } catch (NumberFormatException ignored) {
            }
        });
        addRenderableWidget(modifier);
    }

    private static void disable(net.minecraft.client.gui.components.AbstractWidget widget) {
        disable(widget, "Unavailable - requires a native server schema and runtime behavior");
    }

    private static void disable(net.minecraft.client.gui.components.AbstractWidget widget,
                                String reason) {
        widget.active = false;
        widget.setTooltip(Tooltip.create(Component.literal(reason)));
    }

    private String disabledActionReason(String label) {
        ScreenId page = currentPage().id();
        if ((page == ScreenId.GLOBAL_BANKS || page == ScreenId.GLOBAL_FACTIONS
                || page == ScreenId.GLOBAL_DIALOGS) && label.equals("Add")) {
            return "Enter a valid ID and category first";
        }
        if (page == ScreenId.GLOBAL_BANKS && label.equals("Add Tab")) {
            return "This bank has the maximum number of tabs";
        }
        if (page == ScreenId.GLOBAL_BANKS && label.equals("Remove Tab")) {
            return "A bank must keep at least one tab";
        }
        if (label.equals("Remove") && (page == ScreenId.GLOBAL_FACTIONS
                || page == ScreenId.GLOBAL_TRANSPORT)) {
            return "Select an entry to remove";
        }
        return "Unavailable - requires a native server schema and runtime behavior";
    }

    private void buildFooter() {
        int footerY = frameY + frameH - 32;
        AtlasButton saveButton = new AtlasButton(frameX + 16, footerY,
                Component.literal(npcSavePending ? "Saving..."
                        : autoSaveStopped ? "Save" : autoSave.pending() ? "Save" : "Saved"), PRIMARY, b -> save());
        saveButton.active = !npcSavePending;
        addRenderableWidget(saveButton);
        addRenderableWidget(new AtlasButton(frameX + 112, footerY,
                Component.literal(subPage == null ? "Close" : "Back"), PRIMARY,
                b -> {
                    if (subPage == null) onClose();
                    else if (editingStoreDialogue && storeDialogueDirty
                            && subPage == ScreenId.GLOBAL_DIALOG_LINES)
                        showNotice("Save the global dialog before leaving");
                    else if (editingStoreDialogue && subPage == ScreenId.ADVANCED_DIALOG_NODE)
                        openPage(ScreenId.GLOBAL_DIALOG_LINES);
                    else openPage(currentPage().parent());
                }));

        if (pageCount > 1) {
            EditorFooter pager = EditorFooter.of(contentRight(),
                    XenoAtlasSprites.get(ARROW).width());
            addRenderableWidget(new AtlasButton(pager.prevX(), footerY + 2,
                    Component.literal("<"), ARROW, b -> turnPage(-1)));
            addRenderableWidget(new AtlasButton(pager.nextX(), footerY + 2,
                    Component.literal(">"), ARROW, b -> turnPage(1)));
        }

        // Shown on every page now, the way My NPCs parks its visualizer on every tab - the only
        // question left is whether there is an entity to draw.
        if (NpcLivePreview.entity(entityId) != null) {
            addRenderableWidget(new AtlasToggle(previewX + 4, previewY + previewH + 4, 96,
                    preview.showAura(), Component.literal("Aura"), preview::showAura));
            addRenderableWidget(new AtlasButton(previewX + 104, previewY + previewH + 4,
                    Component.literal("Reset"), "mynpcs_button_row", b -> preview.reset()));
        }
    }

    private void turnPage(int delta) {
        tabPage[selectedTab] = EditorLayout.clampPage(tabPage[selectedTab] + delta, pageCount);
        rebuild();
    }

    // ---------------------------------------------------------------- row declarations

    private List<EditorRow> rowsFor(int tab) {
        return switch (currentPage().id()) {
            case DISPLAY -> displayRows();
            case STATS -> statsRows();
            case AI -> aiRows();
            case INVENTORY -> inventoryRows();
            case ADVANCED -> advancedRows();
            case GLOBAL -> globalRows();
            case ADVANCED_LINES -> advancedLinesRows();
            case ADVANCED_LINE_SELECTOR -> unavailableSelectorRows("Line Selector", 10);
            // The slot grid is the Dialogs page now, matching their editor. The per-NPC tree this
            // page used to be still exists, one level down: NPCs already carrying one must not
            // lose it, and a dialogue written for a single NPC is still a legitimate thing.
            case ADVANCED_DIALOGS -> dialogSlotRows();
            case ADVANCED_DIALOG_OWN -> dialogueRows();
            case ADVANCED_DIALOG_NODE -> dialogueNodeRows();
            case ADVANCED_DIALOG_OPTION -> dialogueOptionRows();
            case ADVANCED_SOUNDS -> soundRows();
            case ADVANCED_NIGHT -> nightRows();
            case ADVANCED_LINKED -> advancedLinkedRows();
            case ADVANCED_SCENES -> sceneRows();
            case ADVANCED_MARKS -> markRows();
            case STATS_RESPAWN -> respawnRows();
            case STATS_MELEE -> meleePropsRows();
            case STATS_RANGED -> rangedPropsRows();
            case STATS_PROJECTILE -> projectilePropsRows();
            case STATS_RESISTANCE -> resistancePropsRows();
            case ADVANCED_DIALOG_PICK -> dialogPickRows();
            case ADVANCED_BUBBLE_LINES -> bubbleLineRows();
            case ADVANCED_TRANSPORT -> transportRows();
            case ADVANCED_TRADER -> traderRows();
            case ADVANCED_BARD -> bardRows();
            case ADVANCED_HEALER -> healerRows();
            case ADVANCED_FOLLOWER -> followerRows();
            case ADVANCED_ITEM_GIVER -> itemGiverRows();
            case ADVANCED_ITEM_GIVER_AVAILABILITY -> itemGiverAvailabilityRows();
            case ADVANCED_FACTIONS -> npcFactionRows();
            case ADVANCED_BANK -> bankSetupRows();
            case ADVANCED_PATH -> pathRows();
            case ADVANCED_GUARD -> guardRows();
            case GLOBAL_BANKS -> bankRows();
            case GLOBAL_FACTIONS -> factionListRows();
            case GLOBAL_FACTION_EDITOR -> factionEditorRows();
            case GLOBAL_DIALOGS -> globalDialogRows();
            case GLOBAL_DIALOG_NAME -> dialogNameRows();
            case GLOBAL_DIALOG_LINES -> dialogueRows();
            case GLOBAL_QUESTS -> globalQuestRows();
            case GLOBAL_TRANSPORT -> transportRows();
            case GLOBAL_PLAYER_DATA -> playerDataRows();
            case GLOBAL_RECIPES -> unavailableListRows("Recipes (no bench)",
                    "The world store holds this; nothing reads it yet.",
                    "And nothing would consume a recipe even",
                    "if it did: the carpentry bench that reads",
                    "them does not exist, so authoring here",
                    "would write a file no craft could find.");
            case GLOBAL_NATURAL_SPAWNS -> naturalSpawnRows();
            case GLOBAL_LINKED -> globalLinkedRows();
            case DMZ -> dmzRows();
            case DMZ_SKILLS -> dmzSkillRows();
            case DMZ_TECHNIQUES -> dmzTechniqueRows();
            case DMZ_FORMS -> formRows();
            case DMZ_ATTACKS -> attackSlotRows();
            case DMZ_APPEARANCE -> List.of();
            case BRAIN -> brainRows();
            case DELETE -> deleteRows();
        };
    }

    private NpcEditorScreenManifest.Page currentPage() {
        return NpcEditorScreenManifest.page(subPage == null ? tabPageId(selectedTab) : subPage);
    }

    private static ScreenId tabPageId(int tab) {
        return switch (tab) {
            case 1 -> ScreenId.STATS;
            case 2 -> ScreenId.AI;
            case 3 -> ScreenId.INVENTORY;
            case 4 -> ScreenId.ADVANCED;
            case 5 -> ScreenId.GLOBAL;
            case 6 -> ScreenId.DMZ;
            case 7 -> ScreenId.BRAIN;
            case TAB_DELETE -> ScreenId.DELETE;
            default -> ScreenId.DISPLAY;
        };
    }

    private void openPage(ScreenId page) {
        if (page == ScreenId.ADVANCED_DIALOG_OWN) {
            editingStoreDialogue = false;
            returnToAdvancedDialogs = false;
            dialogueDraft = DialogueDraft.of(profile.dialogueTag);
        }
        subPage = page;
        tabPage[selectedTab] = 0;
        rebuild();
    }

    private int contentRight() {
        // Always the frame's own right edge. The visualizer used to eat a column out of the body
        // on pages that wanted it, which meant the same row landed on a different page depending
        // on which tab you came from. It now sits outside the frame, so this is constant.
        return frameX + frameW - 18;
    }

    /**
     * Identity and model, in the reference's row order.
     *
     * <p>Hair moved to the appearance sub-screen, where MyNPCs keeps it - the reference's Display
     * tab has no hair rows.
     */
    private List<EditorRow> displayRows() {
        List<EditorRow> r = new ArrayList<>();
        r.add(fullField("Name", currentName(), 64, v -> editedName = v, null));
        r.add(field("Title", currentTitle(), 64, v -> editedTitle = v, null));
        r.add(spacer());
        r.add(new EditorRow.Action("Model", "mynpcs_button_row", this::openAppearance));
        r.add(cycle("Model type", NpcCombatProfile.MODEL_KINDS,
                Math.max(0, NpcCombatProfile.MODEL_KINDS.indexOf(
                        NpcCombatProfile.normalizeModelKind(profile.modelKind))),
                index -> profile.modelKind = NpcCombatProfile.MODEL_KINDS.get(index), "ModelKind"));
        r.add(field("Model asset", profile.modelId, 256,
                value -> profile.modelId = value, "ModelId"));
        r.add(field("Animation asset", profile.modelAnimation, 256,
                value -> profile.modelAnimation = value, "ModelAnimation"));
        r.add(new EditorRow.Action("Select GeckoLib model", "mynpcs_button_row",
                () -> openGeckoModelPicker(profile.modelId, value -> {
                    profile.modelKind = NpcCombatProfile.MODEL_GECKOLIB;
                    profile.modelId = value;
                    profile.modelTexture = value;
                    profile.modelAnimation = "";
                    profile.skinPlayer = "";
                    markDirty("ModelKind");
                    markDirty("ModelTexture");
                    markDirty("ModelAnimation");
                    markDirty("SkinPlayer");
                }, "ModelId")));
        r.add(new EditorRow.Text("", modelHint(), MUTED));
        r.add(disabledToggle("Living animation", true));
        r.add(stepper("Size", profile.baseSize, 0, 30, v -> profile.baseSize = v, "BaseSize"));
        r.add(colorField("Tint", String.format(Locale.ROOT, "#%06X", profile.modelTint & 0xFFFFFF),
                v -> {
                    profile.modelTint = parseHex(v, profile.modelTint);
                    markDirty("ModelTint");
                }));
        r.add(spacer());
        r.add(toggle("Glowing", profile.modelGlowing,
                v -> profile.modelGlowing = v, "ModelGlowing"));
        r.add(field("Texture", profile.modelTexture, 256,
                v -> {
                    profile.modelTexture = v;
                    profile.skinPlayer = "";
                    markDirty("SkinPlayer");
                }, "ModelTexture"));
        r.add(new EditorRow.Action("Select Texture", "mynpcs_button_row",
                () -> openTexturePicker(profile.modelTexture,
                        value -> {
                            profile.modelTexture = value;
                            profile.skinPlayer = "";
                            markDirty("SkinPlayer");
                        }, "ModelTexture")));
        r.add(field("Player skin", profile.skinPlayer, 16,
                v -> profile.skinPlayer = v, "SkinPlayer"));
        r.add(new EditorRow.Text("", "Minecraft account name; blank uses Texture.", MUTED));
        r.add(field("Cape", profile.displayCape, 256, v -> profile.displayCape = v, "DisplayCape"));
        r.add(new EditorRow.Action("Select Cape", "mynpcs_button_row",
                () -> openTexturePicker(profile.displayCape, value -> profile.displayCape = value,
                        "DisplayCape")));
        r.add(field("Overlay", profile.displayOverlay, 256,
                v -> profile.displayOverlay = v, "DisplayOverlay"));
        r.add(new EditorRow.Action("Select Overlay", "mynpcs_button_row",
                () -> openTexturePicker(profile.displayOverlay, value -> profile.displayOverlay = value,
                        "DisplayOverlay")));
        r.add(toggle("Overlay glows", profile.displayOverlayGlow,
                v -> profile.displayOverlayGlow = v, "DisplayOverlayGlow"));
        r.add(toggle("Showing Layers", profile.displayOuterLayers,
                v -> profile.displayOuterLayers = v, "DisplayOuterLayers"));
        r.add(new EditorRow.Text("", "Cape, overlay and layers draw on the humanoid model.", MUTED));
        r.add(floatField("Hitbox", profile.hitboxScale,
                v -> profile.hitboxScale = v, "HitboxScale"));
        r.add(toggle("Visible", profile.visible, v -> profile.visible = v, "Visible"));
        r.add(disabledAction("Availability"));
        r.add(toggle("Boss Bar", profile.bossBar, v -> profile.bossBar = v, "BossBar"));
        r.add(cycle("Color", XenoNpcBehaviour.bossBarColors(),
                Math.max(0, XenoNpcBehaviour.bossBarColors().indexOf(profile.bossBarColor)),
                v -> profile.bossBarColor = XenoNpcBehaviour.bossBarColors().get(v),
                "BossBarColor"));
        return r;
    }

    private List<EditorRow> statsRows() {
        List<EditorRow> r = new ArrayList<>();
        r.add(intField("Health", profile.vitality, v -> profile.vitality = v, "Vitality"));
        r.add(intField("Max HP override", profile.maxHealthOverride,
                v -> profile.maxHealthOverride = Math.max(0, v), "MaxHealthOverride"));
        r.add(new EditorRow.Text("", "0 uses the vitality calculation. Above 0 wins outright,",
                MUTED));
        r.add(new EditorRow.Text("", "so a high VIT set for damage need not mean high HP.",
                MUTED));
        r.add(floatField("AggroRange", profile.aggroMultiplier,
                v -> profile.aggroMultiplier = v, "AggroMultiplier"));
        r.add(cycle("Creature Type", XenoNpcBehaviour.creatureTypes(),
                Math.max(0, XenoNpcBehaviour.creatureTypes().indexOf(profile.creatureType)),
                v -> profile.creatureType = XenoNpcBehaviour.creatureTypes().get(v),
                "CreatureType"));
        // Honest label: the value persists and syncs, but no behaviour reads it yet. The javadoc
        // on the field and on XenoNpcBehaviour.creatureTypes() both say the behaviour layer
        // interprets it; nothing does (2026-09-26 audit - see Phase M report).
        r.add(new EditorRow.Text("", "Recorded for DragonMineZ parity. Nothing in the", MUTED));
        r.add(new EditorRow.Text("", "behaviour layer reads it yet.", MUTED));
        r.add(new EditorRow.Action("Respawn", "mynpcs_button_row",
                () -> openPage(ScreenId.STATS_RESPAWN), false));
        r.add(new EditorRow.Action("Melee Props", "mynpcs_button_row",
                () -> openPage(ScreenId.STATS_MELEE)));
        r.add(new EditorRow.Action("Ranged Props", "mynpcs_button_row",
                () -> openPage(ScreenId.STATS_RANGED)));
        r.add(new EditorRow.Action("Projectile Type", "mynpcs_button_row",
                () -> openPage(ScreenId.STATS_PROJECTILE)));
        r.add(new EditorRow.Action("Resistance", "mynpcs_button_row",
                () -> openPage(ScreenId.STATS_RESISTANCE)));
        r.add(toggle("Immune To Fire", profile.fireImmune,
                v -> profile.fireImmune = v, "ImmuneToFire"));
        r.add(toggle("Burns In Sun", profile.burnsInSun,
                v -> profile.burnsInSun = v, "BurnsInSun"));
        r.add(toggle("Can Drown", profile.canDrown, v -> profile.canDrown = v, "CanDrown"));
        r.add(toggle("No Fall Damage", profile.noFallDamage,
                v -> profile.noFallDamage = v, "NoFallDamage"));
        r.add(toggle("Potion Immune", profile.potionImmune,
                v -> profile.potionImmune = v, "PotionImmune"));
        r.add(toggle("Cobweb Affected", profile.cobwebAffected,
                v -> profile.cobwebAffected = v, "CobwebAffected"));
        r.add(floatField("Health Regen", profile.healthRegen,
                v -> profile.healthRegen = net.bullettrain.xenopixelsmod.compat.npc
                        .NpcCombatProfile.clampRegen(v), "HealthRegen"));
        r.add(floatField("Combat Regen", profile.combatRegen,
                v -> profile.combatRegen = net.bullettrain.xenopixelsmod.compat.npc
                        .NpcCombatProfile.clampRegen(v), "CombatRegen"));
        r.add(new EditorRow.Text("", "Health healed per second, out of a fight and in one.", MUTED));
        r.add(new EditorRow.Text("", "Zero for both is no healing, which is what an NPC did", MUTED));
        r.add(new EditorRow.Text("", "before these existed. Being hit counts as a fight for", MUTED));
        r.add(new EditorRow.Text("", "five seconds, even with nothing targeted.", MUTED));
        r.add(toggle("KI Weapon", profile.kiWeaponOn, v -> profile.kiWeaponOn = v, "KiWeaponOn"));
        return r;
    }

    private List<EditorRow> meleePropsRows() {
        List<EditorRow> rows = new ArrayList<>();
        rows.add(floatField("Melee Strength", profile.npcMeleeDamage,
                v -> profile.npcMeleeDamage = NpcCombatProfile.clampNpcMeleeDamage(v), "NpcMeleeProps"));
        rows.add(floatField("Melee Range", profile.npcMeleeRange,
                v -> profile.npcMeleeRange = NpcCombatProfile.clampNpcMeleeRange(v), "NpcMeleeProps"));
        rows.add(floatField("Melee Speed", profile.npcMeleeSpeed,
                v -> profile.npcMeleeSpeed = NpcCombatProfile.clampNpcMeleeSpeed(v), "NpcMeleeProps"));
        rows.add(floatField("Knockback", profile.npcMeleeKnockback,
                v -> profile.npcMeleeKnockback = NpcCombatProfile.clampNpcProjectileKnockback(v), "NpcMeleeProps"));
        rows.add(field("Effect (registry id)", profile.npcMeleeEffect, 64,
                v -> profile.npcMeleeEffect = safeEffectId(v), "NpcMeleeProps"));
        rows.add(intField("Effect Duration", profile.npcMeleeEffectDuration,
                v -> profile.npcMeleeEffectDuration = NpcCombatProfile.clampNpcEffectDuration(v), "NpcMeleeProps"));
        rows.add(intField("Effect Amplifier", profile.npcMeleeEffectAmplifier,
                v -> profile.npcMeleeEffectAmplifier = NpcCombatProfile.clampNpcEffectAmplifier(v), "NpcMeleeProps"));
        rows.add(new EditorRow.Text("", "Damage 0 uses the DragonMineZ stat formula; range 0 uses the shared default.", MUTED));
        return rows;
    }

    private List<EditorRow> rangedPropsRows() {
        List<EditorRow> rows = new ArrayList<>();
        rows.add(floatField("Accuracy (0-1)", profile.npcRangedAccuracy,
                v -> profile.npcRangedAccuracy = NpcCombatProfile.clampNpcRangedAccuracy(v), "NpcRangedProps"));
        rows.add(floatField("Shoot Range", profile.npcRangedRange,
                v -> profile.npcRangedRange = NpcCombatProfile.clampNpcRangedRange(v), "NpcRangedProps"));
        rows.add(floatField("Minimum Range", profile.npcRangedMinRange,
                v -> profile.npcRangedMinRange = NpcCombatProfile.clampNpcRangedRange(v), "NpcRangedProps"));
        rows.add(intField("Min Delay (ticks)", profile.npcRangedMinDelay,
                v -> profile.npcRangedMinDelay = NpcCombatProfile.clampNpcRangedDelay(v), "NpcRangedProps"));
        rows.add(intField("Max Delay (ticks)", profile.npcRangedMaxDelay,
                v -> profile.npcRangedMaxDelay = Math.max(profile.npcRangedMinDelay,
                        NpcCombatProfile.clampNpcRangedDelay(v)), "NpcRangedProps"));
        rows.add(intField("Shot Count", profile.npcRangedShotCount,
                v -> profile.npcRangedShotCount = NpcCombatProfile.clampNpcShotCount(v), "NpcRangedProps"));
        rows.add(intField("Burst Count", profile.npcRangedBurstCount,
                v -> profile.npcRangedBurstCount = NpcCombatProfile.clampNpcBurstCount(v), "NpcRangedProps"));
        rows.add(intField("Burst Rate (ticks)", profile.npcRangedBurstRate,
                v -> profile.npcRangedBurstRate = NpcCombatProfile.clampNpcRangedDelay(v), "NpcRangedProps"));
        rows.add(toggle("Shoot Indirect", profile.npcRangedIndirect,
                v -> profile.npcRangedIndirect = v, "NpcRangedProps"));
        rows.add(cycle("Aim While Shooting", NpcCombatProfile.npcAimModes(),
                NpcCombatProfile.npcAimModes().indexOf(profile.npcRangedAimMode),
                v -> profile.npcRangedAimMode = NpcCombatProfile.npcAimModes().get(v), "NpcRangedProps"));
        rows.add(field("Fire Sound (registry id)", profile.npcRangedFireSound, 64,
                v -> profile.npcRangedFireSound = safeEffectId(v), "NpcRangedProps"));
        rows.add(field("Hit Sound (registry id)", profile.npcRangedHitSound, 64,
                v -> profile.npcRangedHitSound = safeEffectId(v), "NpcRangedProps"));
        rows.add(field("Ground Sound (registry id)", profile.npcRangedGroundSound, 64,
                v -> profile.npcRangedGroundSound = safeEffectId(v), "NpcRangedProps"));
        rows.add(new EditorRow.Text("", "Shot Count fires together as a spread; Burst Count repeats it.", MUTED));
        rows.add(new EditorRow.Text("", "These controls tune the native combat brain's basic ki blast.", MUTED));
        return rows;
    }

    private List<EditorRow> projectilePropsRows() {
        List<EditorRow> rows = new ArrayList<>();
        rows.add(floatField("Strength (damage)", profile.npcProjectileStrength,
                v -> profile.npcProjectileStrength = NpcCombatProfile.clampNpcProjectileStrength(v), "NpcProjectileProps"));
        rows.add(floatField("Knockback", profile.npcProjectileKnockback,
                v -> profile.npcProjectileKnockback = NpcCombatProfile.clampNpcProjectileKnockback(v), "NpcProjectileProps"));
        rows.add(floatField("Size", profile.npcProjectileSize,
                v -> profile.npcProjectileSize = NpcCombatProfile.clampNpcProjectileSize(v), "NpcProjectileProps"));
        rows.add(floatField("Speed", profile.npcProjectileSpeed,
                v -> profile.npcProjectileSpeed = NpcCombatProfile.clampNpcProjectileSpeed(v), "NpcProjectileProps"));
        rows.add(cycle("Gravity", NpcCombatProfile.projectileGravityModes(),
                NpcCombatProfile.projectileGravityModes().indexOf(profile.npcProjectileGravity),
                v -> profile.npcProjectileGravity = NpcCombatProfile.projectileGravityModes().get(v), "NpcProjectileProps"));
        rows.add(floatField("Explosion Radius", profile.npcProjectileExplosion,
                v -> profile.npcProjectileExplosion = NpcCombatProfile.clampNpcProjectileExplosion(v), "NpcProjectileProps"));
        rows.add(field("Effect (registry id)", profile.npcProjectileEffect, 64,
                v -> profile.npcProjectileEffect = safeEffectId(v), "NpcProjectileProps"));
        rows.add(intField("Effect Duration (ticks)", profile.npcProjectileEffectDuration,
                v -> profile.npcProjectileEffectDuration = NpcCombatProfile.clampNpcEffectDuration(v),
                "NpcProjectileProps"));
        rows.add(intField("Effect Amplifier (0-10)", profile.npcProjectileEffectAmplifier,
                v -> profile.npcProjectileEffectAmplifier = NpcCombatProfile.clampNpcEffectAmplifier(v),
                "NpcProjectileProps"));
        rows.add(cycle("Trail", NpcCombatProfile.projectileTrailModes(),
                NpcCombatProfile.projectileTrailModes().indexOf(profile.npcProjectileTrail),
                v -> profile.npcProjectileTrail = NpcCombatProfile.projectileTrailModes().get(v),
                "NpcProjectileProps"));
        rows.add(toggle("Spins", profile.npcProjectileSpins,
                v -> profile.npcProjectileSpins = v, "NpcProjectileProps"));
        rows.add(toggle("Sticks On Landing", profile.npcProjectileSticks,
                v -> profile.npcProjectileSticks = v, "NpcProjectileProps"));
        rows.add(toggle("Glows", profile.npcProjectileGlows,
                v -> profile.npcProjectileGlows = v, "NpcProjectileProps"));
        rows.add(new EditorRow.Text("", "Zero overrides keep DragonMineZ projectile defaults; block damage is disabled.", MUTED));
        // Their 2D/3D switch picks between a flat sprite and an item model. A DragonMineZ ki
        // projectile has neither - it draws its own layered ki sphere - so there is nothing here
        // for that control to select, and a toggle that selects nothing is worse than its absence.
        rows.add(new EditorRow.Text("", "2D/3D has no counterpart: ki projectiles draw their own model.", MUTED));
        return rows;
    }

    private List<EditorRow> resistancePropsRows() {
        List<EditorRow> rows = new ArrayList<>();
        rows.add(intField("Knockback Resistance %", profile.npcKnockbackResistance,
                v -> profile.npcKnockbackResistance = NpcCombatProfile.clampNpcResistance(v), "NpcResistanceProps"));
        rows.add(intField("Arrow Resistance %", profile.npcArrowResistance,
                v -> profile.npcArrowResistance = NpcCombatProfile.clampNpcResistance(v), "NpcResistanceProps"));
        rows.add(intField("Melee Resistance %", profile.npcMeleeResistance,
                v -> profile.npcMeleeResistance = NpcCombatProfile.clampNpcResistance(v), "NpcResistanceProps"));
        rows.add(intField("Explosion Resistance %", profile.npcExplosionResistance,
                v -> profile.npcExplosionResistance = NpcCombatProfile.clampNpcResistance(v), "NpcResistanceProps"));
        rows.add(new EditorRow.Text("", "Each value reduces matching damage from 0% to 100%.", MUTED));
        return rows;
    }

    private static String safeEffectId(String value) {
        if (value == null || value.isBlank()) return "";
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation
                .tryParse(value.trim().toLowerCase(Locale.ROOT));
        return id == null ? "" : id.toString();
    }

    private List<EditorRow> aiRows() {
        List<EditorRow> r = new ArrayList<>();
        r.add(cycle("On Found Enemy",
                net.bullettrain.xenopixelsmod.npc.NpcOnFoundEnemy.labels(),
                profile.aiOnFoundEnemy == null ? 0 : profile.aiOnFoundEnemy.ordinal(),
                index -> profile.aiOnFoundEnemy =
                        net.bullettrain.xenopixelsmod.npc.NpcOnFoundEnemy.byIndex(index),
                "AiOnFoundEnemy"));
        r.add(cycle("Shelter From",
                net.bullettrain.xenopixelsmod.npc.NpcShelterFrom.labels(),
                profile.aiShelterFrom == null ? 2 : profile.aiShelterFrom.ordinal(),
                index -> profile.aiShelterFrom =
                        net.bullettrain.xenopixelsmod.npc.NpcShelterFrom.byIndex(index),
                "AiShelterFrom"));
        r.add(toggle("Must See Target", profile.aiMustSeeTarget,
                v -> profile.aiMustSeeTarget = v, "AiMustSeeTarget"));
        r.add(new EditorRow.Cycle("Door Interact",
                net.bullettrain.xenopixelsmod.npc.NpcDoorInteract.labels(),
                profile.aiDoorInteract == null ? 0 : profile.aiDoorInteract.ordinal(),
                index -> {
                    profile.aiDoorInteract =
                            net.bullettrain.xenopixelsmod.npc.NpcDoorInteract.byIndex(index);
                    markDirty("AiDoorInteract");
                    rebuild();
                }));
        r.add(toggle("Can Swim", profile.aiCanSwim,
                v -> profile.aiCanSwim = v, "AiCanSwim"));
        r.add(toggle("Return To Start", profile.aiReturnToStart,
                v -> profile.aiReturnToStart = v, "AiReturnToStart"));
        r.add(toggle("Avoids Water", profile.aiAvoidsWater,
                v -> profile.aiAvoidsWater = v, "AiAvoidsWater"));
        r.add(toggle("Leap At Target", profile.aiLeapAtTarget,
                v -> profile.aiLeapAtTarget = v, "AiLeapAtTarget"));
        r.add(new EditorRow.Action("Movement", "mynpcs_button_row",
                () -> openPage(ScreenId.ADVANCED_PATH)));
        r.add(new EditorRow.Toggle("Stay Home", profile.stayHome, v -> {
            profile.stayHome = v;
            markDirty("StayHome");
        }));
        r.add(new EditorRow.Toggle("Gestures", profile.socialGestures, v -> {
            profile.socialGestures = v;
            markDirty("SocialGestures");
        }));
        r.add(toggle("Attack Invisible", profile.aiAttackInvisible,
                v -> profile.aiAttackInvisible = v, "AiAttackInvisible"));
        r.add(toggle("Mount Control", profile.aiMountControl,
                v -> profile.aiMountControl = v, "AiMountControl"));
        r.add(new EditorRow.Text("", "Combat choices belong on Brain. Movement is the patrol", MUTED));
        r.add(new EditorRow.Text("", "route; Stay Home keeps it on its spot; Gestures make", MUTED));
        r.add(new EditorRow.Text("", "it wave at people who come near.", MUTED));
        r.add(spacer());
        r.add(new EditorRow.Text("", "Return To Start is the leash home; off leaves it where", MUTED));
        r.add(new EditorRow.Text("", "it ends up. Break needs Hard difficulty, which is", MUTED));
        r.add(new EditorRow.Text("", "vanilla's own rule for breaking a door.", MUTED));
        r.add(new EditorRow.Text("", "Mount Control lets a player ride and steer this NPC", MUTED));
        r.add(new EditorRow.Text("", "using the operator-only Xeno NPC Mounter.", MUTED));
        return r;
    }

    /**
     * Inventory: what the NPC wears, what it leaves behind, and what it is worth.
     *
     * <p>The items themselves are slots you drag into, in their own screen - see
     * {@code XenoNpcInventoryMenu}. They were typed registry ids here, which could not carry an
     * enchantment or a custom name, so a Sharpness V sword arrived as a plain one. What stays on
     * this page is the numbers: the drop chances, the experience range and the loot mode.
     *
     * <p>The reference page interleaves seven slot buttons with nine drop chances as though each
     * slot had one. It does not - there are seven slots and nine chances, and the spec lists
     * "drop items/chances" separately from the weapon and armor slots.
     */
    private List<EditorRow> inventoryRows() {
        List<EditorRow> r = new ArrayList<>();
        var drops = profile.drops;

        r.add(new EditorRow.Heading("Items"));
        r.add(new EditorRow.Action("Open slots", "mynpcs_button_row", this::openInventorySlots));
        r.add(new EditorRow.Text("", "Gear, drops and Curios as slots you drag into.", MUTED));
        r.add(new EditorRow.Text("", "Worn gear never drops - author drops there too.", MUTED));
        r.add(new EditorRow.Text("", "Drawn only on the plain humanoid model; GeckoLib,", MUTED));
        r.add(new EditorRow.Text("", "entity and full-DMZ NPCs wear it without showing it.", MUTED));
        r.add(spacer());

        r.add(new EditorRow.Heading("Drop chances"));
        for (int i = 0; i < net.bullettrain.xenopixelsmod.npc.inventory.NpcDropList.MAX_DROPS; i++) {
            final int index = i;
            r.add(floatField("Drop chance " + (i + 1), drops.get(i).chance(),
                    v -> replaceDropChance(index, v), "NpcDrops"));
        }
        r.add(new EditorRow.Text("", "A percentage, one per drop slot. Each is rolled on its", MUTED));
        r.add(new EditorRow.Text("", "own, so an empty slot drops nothing whatever it says.", MUTED));
        r.add(spacer());

        r.add(new EditorRow.Heading("Experience"));
        r.add(intField("Min Exp", drops.minExp(), v -> drops.setMinExp(v), "MinExp"));
        r.add(intField("Max Exp", drops.maxExp(), v -> drops.setMaxExp(v), "MaxExp"));
        r.add(new EditorRow.Cycle("Loot Mode",
                net.bullettrain.xenopixelsmod.npc.inventory.NpcLootMode.labels(),
                drops.lootMode().ordinal(),
                index -> {
                    drops.setLootMode(
                            net.bullettrain.xenopixelsmod.npc.inventory.NpcLootMode.byIndex(index));
                    markDirty("LootMode");
                    rebuild();
                }));
        r.add(new EditorRow.Text("", "Both zero leaves this NPC worth whatever its type is.", MUTED));
        r.add(new EditorRow.Text("", "Auto Pickup gives drops to whoever landed the kill;", MUTED));
        r.add(new EditorRow.Text("", "what does not fit falls on the ground.", MUTED));
        r.add(spacer());

        r.add(new EditorRow.Heading("Projectile"));
        r.add(new EditorRow.Text("", "No slot yet: nothing would fire it. There is no ranged", MUTED));
        r.add(new EditorRow.Text("", "attack goal on these NPCs, so a projectile field would", MUTED));
        r.add(new EditorRow.Text("", "be a control with nothing behind it.", MUTED));
        r.add(spacer());
        return r;
    }

    /**
     * Changes one drop's chance, leaving its item alone.
     *
     * <p>The item lives in the slots screen now, so this page only ever rewrites the number - which
     * is why it takes the existing item across rather than accepting one.
     */
    private void replaceDropChance(int index, float chance) {
        var old = profile.drops.get(index);
        profile.drops.set(index,
                new net.bullettrain.xenopixelsmod.npc.inventory.NpcDrop(old.item(), chance));
        markDirty("NpcDrops");
    }

    /**
     * Asks the server to open the slots screen for this NPC.
     *
     * <p>A request, not an open: the menu carries the NPC's real gear and its Curios slots, and
     * both are the server's to decide. The editor closes first, because two screens over one NPC
     * would each save on close and the later one would win.
     */
    private void openInventorySlots() {
        net.bullettrain.xenopixelsmod.network.ModNetwork.sendToServer(
                new net.bullettrain.xenopixelsmod.network.packet.XenoNpcInventoryOpenPacket(
                        entityId));
    }

    /**
     * Opens the script library for this NPC. The callback writes the chosen id into the profile and
     * marks it dirty; the script screen itself knows nothing about profiles, which is why the binding
     * happens here rather than there.
     */
    private void openScriptEditor() {
        minecraft.setScreen(XenoNpcScriptScreen.forEditor(this, entityId, profile.scripts,
                data.getList("ScriptConsole", net.minecraft.nbt.Tag.TAG_STRING), tag -> {
            profile.scripts = net.bullettrain.xenopixelsmod.npc.script.NpcScriptContainer.read(tag);
            profile.scriptId = profile.scripts.firstScriptId();
            markDirty("ScriptTabs");
            markDirty("ScriptsEnabled");
            markDirty("ScriptLanguage");
            markDirty("ScriptId");
            rebuild();
        }));
    }

    /** The same editor over the whole library, reached from the Global tab. */
    private void openScriptLibrary() {
        minecraft.setScreen(XenoNpcScriptScreen.library(this));
    }

    private List<EditorRow> advancedRows() {
        List<EditorRow> r = new ArrayList<>();
        String role = data.getString("Role");
        var roles = java.util.Arrays.asList(net.bullettrain.xenopixelsmod.npc.XenoNpcRole.values());
        var selectedRole = net.bullettrain.xenopixelsmod.npc.XenoNpcRole.byId(role);
        r.add(new EditorRow.Cycle("Role", roles.stream()
                .map(net.bullettrain.xenopixelsmod.npc.XenoNpcRole::label).toList(),
                roles.indexOf(selectedRole), index -> {
                    var next = roles.get(Math.max(0, Math.min(roles.size() - 1, index)));
                    data.putString("Role", next.id());
                    markDataDirty("Role");
                    rebuild();
                }));
        // Live, and independent of the role: their editor lets one NPC be a Trader and a Bard.
        var jobs = java.util.Arrays.asList(net.bullettrain.xenopixelsmod.npc.XenoNpcJob.values());
        var currentJob = net.bullettrain.xenopixelsmod.npc.XenoNpcJob.byId(profile.job);
        r.add(new EditorRow.Cycle("Job",
                net.bullettrain.xenopixelsmod.npc.XenoNpcJob.labels(), jobs.indexOf(currentJob),
                index -> {
                    profile.job = jobs.get(Math.max(0, Math.min(jobs.size() - 1, index))).id();
                    markDirty("Job");
                    rebuild();
                }));
        // Routes to whichever page this NPC's role actually has. Transporter and Bank both had
        // pages with nothing pointing at them before this.
        ScreenId rolePage = switch (net.bullettrain.xenopixelsmod.npc.XenoNpcRole.byId(role)) {
            case TRADER -> ScreenId.ADVANCED_TRADER;
            case TRANSPORTER -> ScreenId.ADVANCED_TRANSPORT;
            case BANK -> ScreenId.ADVANCED_BANK;
            case QUEST -> ScreenId.ADVANCED_DIALOGS;
            case GUARD, COMPANION -> ScreenId.AI;
            case HUMANOID, CREATURE -> ScreenId.DISPLAY;
        };
        r.add(new EditorRow.Action("Edit Role", "mynpcs_button_row",
                () -> openPage(rolePage)));
        ScreenId jobPage = switch (currentJob) {
            case BARD -> ScreenId.ADVANCED_BARD;
            case HEALER -> ScreenId.ADVANCED_HEALER;
            case GUARD -> ScreenId.ADVANCED_GUARD;
            case FOLLOWER -> ScreenId.ADVANCED_FOLLOWER;
            case ITEM_GIVER -> ScreenId.ADVANCED_ITEM_GIVER;
            default -> null;
        };
        r.add(new EditorRow.Action("Edit Job", "mynpcs_button_row",
                jobPage == null ? null : () -> openPage(jobPage)));
        if (!currentJob.implemented()) {
            r.add(new EditorRow.Text("", "That job is a name only so far; nothing runs it.",
                    MUTED));
        }
        // Says what the role will actually do. It used to be a label over no behaviour at all:
        // Trader, Guard and Quest all behaved exactly like Humanoid.
        r.add(new EditorRow.Text("", net.bullettrain.xenopixelsmod.npc.XenoNpcRoleBehaviour
                .describe(net.bullettrain.xenopixelsmod.npc.XenoNpcRole.byId(role)), MUTED));
        r.add(toggle("Job Enabled", profile.jobEnabled,
                v -> profile.jobEnabled = v, "JobEnabled"));
        r.add(new EditorRow.Action("Lines", "mynpcs_button_row",
                () -> openPage(ScreenId.ADVANCED_LINES)));
        r.add(new EditorRow.Action("Factions", "mynpcs_button_row",
                () -> openPage(ScreenId.ADVANCED_FACTIONS)));
        r.add(new EditorRow.Action("Dialogs", "mynpcs_button_row",
                () -> openPage(ScreenId.ADVANCED_DIALOGS)));
        r.add(new EditorRow.Action("Sounds", "mynpcs_button_row",
                () -> openPage(ScreenId.ADVANCED_SOUNDS)));
        r.add(new EditorRow.Action("Night", "mynpcs_button_row",
                () -> openPage(ScreenId.ADVANCED_NIGHT)));
        r.add(new EditorRow.Action("Linked", "mynpcs_button_row",
                () -> openPage(ScreenId.ADVANCED_LINKED)));
        r.add(new EditorRow.Action("Scenes", "mynpcs_button_row",
                () -> openPage(ScreenId.ADVANCED_SCENES)));
        r.add(new EditorRow.Action("Scripts", "mynpcs_button_row", this::openScriptEditor));
        int scriptTabs = profile.scripts.tabs().size();
        r.add(new EditorRow.Text("", scriptTabs == 0 ? "No scripts on this NPC."
                : scriptTabs + " script tab" + (scriptTabs == 1 ? "" : "s")
                        + (profile.scripts.enabled() ? ", enabled." : ", disabled."), MUTED));
        r.add(new EditorRow.Action("Marks", "mynpcs_button_row",
                () -> openPage(ScreenId.ADVANCED_MARKS)));
        return r;
    }

    private List<EditorRow> globalRows() {
        List<EditorRow> r = new ArrayList<>();
        r.add(wideAction("Banks",
                () -> openPage(ScreenId.GLOBAL_BANKS)));
        r.add(wideAction("Factions",
                () -> openPage(ScreenId.GLOBAL_FACTIONS)));
        r.add(wideAction("Dialogs",
                () -> openPage(ScreenId.GLOBAL_DIALOGS)));
        r.add(wideAction("Quests",
                () -> openPage(ScreenId.GLOBAL_QUESTS)));
        r.add(wideAction("Transport",
                () -> openPage(ScreenId.GLOBAL_TRANSPORT)));
        r.add(wideAction("PlayerData",
                () -> openPage(ScreenId.GLOBAL_PLAYER_DATA)));
        r.add(wideAction("Scripts", this::openScriptLibrary));
        r.add(wideAction("Player Scripts", () -> minecraft.setScreen(
                XenoNpcScriptScreen.playerScripts(this))));
        r.add(wideAction("Recipes (no bench)",
                () -> openPage(ScreenId.GLOBAL_RECIPES)));
        r.add(wideAction("Natural Spawns",
                () -> openPage(ScreenId.GLOBAL_NATURAL_SPAWNS)));
        r.add(wideAction("Linked",
                () -> openPage(ScreenId.GLOBAL_LINKED)));
        return r;
    }

    private List<EditorRow> advancedLinesRows() {
        List<EditorRow> rows = new ArrayList<>();
        // These five reference rows have a native writer and runtime consumer already. Route them
        // through the same per-NPC editor used by the bubble page instead of the old disabled
        // selector. NPC Interact is a distinct NPC-to-NPC conversation feature and stays disabled.
        rows.add(new EditorRow.Action("World Lines", "mynpcs_button_row",
                () -> openLineCategory(net.bullettrain.xenopixelsmod.npc.lines.XenoNpcLines.Category.WORLD)));
        rows.add(new EditorRow.Action("Attack Lines", "mynpcs_button_row",
                () -> openLineCategory(net.bullettrain.xenopixelsmod.npc.lines.XenoNpcLines.Category.ATTACK)));
        rows.add(new EditorRow.Action("Interact Lines", "mynpcs_button_row",
                () -> openLineCategory(net.bullettrain.xenopixelsmod.npc.lines.XenoNpcLines.Category.INTERACT)));
        rows.add(new EditorRow.Action("Killed Lines", "mynpcs_button_row",
                () -> openLineCategory(net.bullettrain.xenopixelsmod.npc.lines.XenoNpcLines.Category.KILLED)));
        rows.add(new EditorRow.Action("Kill Lines", "mynpcs_button_row",
                () -> openLineCategory(net.bullettrain.xenopixelsmod.npc.lines.XenoNpcLines.Category.KILL)));
        rows.add(new EditorRow.Action("Random Lines", "mynpcs_button_row",
                () -> openLineCategory(net.bullettrain.xenopixelsmod.npc.lines.XenoNpcLines.Category.RANDOM)));
        rows.add(new EditorRow.Text("", "Random lines need the Display tab's ambient toggle on.",
                MUTED));
        rows.add(new EditorRow.Action("NPC Interact Lines", "mynpcs_button_row",
                () -> openLineCategory(net.bullettrain.xenopixelsmod.npc.lines.XenoNpcLines.Category.NPC)));
        rows.add(new EditorRow.Text("", "Said to an NPC within 6 blocks; {npc} is its name. A Xeno",
                MUTED));
        rows.add(new EditorRow.Text("", "NPC with its own NPC lines answers. Needs ambient on.", MUTED));
        return rows;
    }

    /** Assigns a shared timeline; operators author and start it with /xenoscene. */
    private List<EditorRow> sceneRows() {
        List<EditorRow> rows = new ArrayList<>();
        rows.add(new EditorRow.Heading("Assigned scene"));
        var scenes = ClientNpcStoreIndex.of(XenoNpcStoreCategory.SCENES);
        String current = profile.sceneId;
        if (scenes.isEmpty()) {
            rows.add(new EditorRow.Text("", "No scenes yet. Use /xenoscene create <id> <name>.", MUTED));
        }
        for (var entry : scenes) {
            boolean assigned = entry.id().equals(current);
            rows.add(new EditorRow.Slot(scenes.indexOf(entry),
                    (assigned ? "> " : "  ") + entry.label(),
                    () -> {
                        profile.sceneId = entry.id();
                        markDirty("SceneId");
                        rebuild();
                    }, assigned ? () -> {
                        profile.sceneId = "";
                        markDirty("SceneId");
                        rebuild();
                    } : null));
        }
        if (!current.isEmpty() && scenes.stream().noneMatch(entry -> entry.id().equals(current))) {
            rows.add(new EditorRow.Text("", "Missing scene: " + current, WARN));
        }

        // Shown only once a scene is assigned: a trigger on an NPC with no scene is a control with
        // nothing behind it, and the row would be answering a question nobody has asked yet.
        if (!current.isEmpty()) {
            rows.add(spacer());
            rows.add(new EditorRow.Heading("Plays when"));
            rows.add(new EditorRow.Cycle("Trigger",
                    net.bullettrain.xenopixelsmod.npc.scene.XenoNpcSceneTrigger.labels(),
                    profile.sceneTrigger == null ? 0 : profile.sceneTrigger.ordinal(),
                    index -> {
                        profile.sceneTrigger = net.bullettrain.xenopixelsmod.npc.scene
                                .XenoNpcSceneTrigger.byIndex(index);
                        markDirty("SceneTrigger");
                        rebuild();
                    }));
            rows.add(new EditorRow.Text("", sceneTriggerHint(), MUTED));
            rows.add(new EditorRow.Text("", "Manual plays only from the command. The others wait", MUTED));
            rows.add(new EditorRow.Text("", "at least ten seconds between plays, so a scene is not", MUTED));
            rows.add(new EditorRow.Text("", "restarted from step one on every hit.", MUTED));
        }
        rows.add(new EditorRow.Heading("Timeline"));
        rows.add(new EditorRow.Field("Scene id", sceneDraftId, 64, value -> sceneDraftId = value));
        rows.add(new EditorRow.Action("Create scene", "mynpcs_button_row", () -> saveSceneDraft(true)));
        CompoundTag draft = sceneDrafts.get(sceneDraftId);
        if (draft != null) {
            ListTag steps = draft.getList("Steps", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < steps.size(); i++) {
                CompoundTag step = steps.getCompound(i);
                int index = i;
                rows.add(new EditorRow.Action(step.getInt("Time") + " " + step.getString("Kind")
                        + " " + step.getString("Value"), "mynpcs_button_row", () -> {
                    steps.remove(index);
                    saveSceneDraft(false);
                }));
            }
            rows.add(new EditorRow.Field("At tick", String.valueOf(sceneStepTime), 8, value -> {
                try {
                    sceneStepTime = Math.max(0, Math.min(1200, Integer.parseInt(value.trim())));
                } catch (NumberFormatException ignored) {
                    sceneStepTime = 0;
                }
            }));
            rows.add(new EditorRow.Field("Line or clip", sceneStepValue, 256, value -> sceneStepValue = value));
            rows.add(new EditorRow.Action("Add say", "mynpcs_button_row", () -> addSceneStep("SAY")));
            rows.add(new EditorRow.Action("Add clip", "mynpcs_button_row", () -> addSceneStep("CLIP")));
        }
        rows.add(new EditorRow.Text("", "Playback stays on /xenoscene.", MUTED));
        return rows;
    }

    private void addSceneStep(String kind) {
        if (sceneStepValue.isBlank()) return;
        CompoundTag draft = sceneDrafts.computeIfAbsent(sceneDraftId, id -> {
            CompoundTag tag = new CompoundTag();
            tag.putString("Name", id);
            tag.put("Steps", new ListTag());
            return tag;
        });
        CompoundTag step = new CompoundTag();
        step.putInt("Time", sceneStepTime);
        step.putString("Kind", kind);
        step.putString("Value", sceneStepValue);
        draft.getList("Steps", net.minecraft.nbt.Tag.TAG_COMPOUND).add(step);
        saveSceneDraft(false);
    }

    private void saveSceneDraft(boolean create) {
        if (XenoNpcStorePaths.reject(sceneDraftId) != null) {
            showNotice("Scene id is not valid");
            return;
        }
        CompoundTag draft = sceneDrafts.computeIfAbsent(sceneDraftId, id -> {
            CompoundTag tag = new CompoundTag();
            tag.putString("Name", id);
            tag.put("Steps", new ListTag());
            return tag;
        });
        if (!draft.contains("Name")) draft.putString("Name", sceneDraftId);
        if (create && draft.getList("Steps", net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty()) {
            draft.putString("Name", sceneDraftId);
        }
        String invalid = net.bullettrain.xenopixelsmod.npc.scene.XenoNpcScene.rejectPayload(draft);
        if (invalid != null && !create) {
            showNotice(invalid);
            return;
        }
        if (create && invalid != null) {
            draft.putString("Name", sceneDraftId);
        }
        ModNetwork.sendToServer(new XenoNpcStoreWritePacket(
                XenoNpcStoreCategory.SCENES.ordinal(), "", sceneDraftId, false,
                indexedRevision(XenoNpcStoreCategory.SCENES, sceneDraftId), draft));
        showNotice("Saving scene");
        rebuild();
    }

    /** One line saying what the selected trigger actually watches for. */
    private String sceneTriggerHint() {
        var trigger = profile.sceneTrigger == null
                ? net.bullettrain.xenopixelsmod.npc.scene.XenoNpcSceneTrigger.MANUAL
                : profile.sceneTrigger;
        return switch (trigger) {
            case MANUAL -> "Only /xenoscene start.";
            case INTERACT -> "A player right-clicks it, before dialogue or lines.";
            case APPROACH -> "A player comes within six blocks.";
            case DAMAGED -> "It takes damage that actually lands.";
            case DEATH -> "It dies, before it leaves the world.";
            case TIMER -> "Every thirty seconds, unprompted.";
        };
    }

    private List<EditorRow> unavailableSelectorRows(String title, int count) {
        List<EditorRow> rows = new ArrayList<>();
        rows.add(new EditorRow.Heading(title));
        for (int index = 1; index <= count; index++) {
            rows.add(disabledAction(index + "  Select Option"));
        }
        // The old reason ("no native writable registry") stopped being true when the world store
        // landed. The real blocker is upstream: this page picks NPC-to-NPC interact lines, and no
        // server runtime reads that category yet — see the Lines page's honest note.
        rows.add(new EditorRow.Text("", "Shared line sets have no store; NPC-to-NPC lines are"
                + " under Lines > NPC Interact Lines.", MUTED));
        return rows;
    }

    /**
     * Advanced &gt; Dialogs: this NPC's own conversation.
     *
     * <p>Was twelve disabled "Select Option" rows reading "requires a native writable registry" -
     * which was true of the approach, not of the feature. Dialogues load from a datapack, and a
     * {@code SimpleJsonResourceReloadListener} reads a pack and has nothing to write back to, so an
     * in-game editor could never own one that way.
     *
     * <p>So it does not go there. The dialogue lives on the NPC's own profile, beside the attack
     * slots and the bubble palettes, and travels the save path they already use. A pack dialogue
     * and an NPC's own coexist: the NPC's wins, and an NPC without one falls back to whatever its
     * role names. Writing one here therefore never disturbs the rest of its role.
     */
    /** Which slot the picker is filling, and which category it is showing. */
    private int dialogSlotBeingPicked = -1;
    private String dialogPickCategory = "";

    /**
     * Advanced &gt; Dialogs: the twelve assignment slots.
     *
     * <p>My NPCs' grid, row for row. Dialogues live in a shared library — categories of them under
     * {@code dialogs/} in the world store — and an NPC points at up to twelve. A reference, never
     * a copy: twenty guards can share one greeting, and fixing its typo fixes all twenty.
     *
     * <p>Their screen lays the twelve out in two columns of six. This frame is narrower, so ours
     * run in one column with the same row anatomy — number, clear, pick.
     */
    private List<EditorRow> dialogSlotRows() {
        List<EditorRow> rows = new ArrayList<>();
        rows.add(new EditorRow.Heading("Dialogs"));
        if (net.bullettrain.xenopixelsmod.npc.XenoNpcRole.byId(data.getString("Role"))
                == net.bullettrain.xenopixelsmod.npc.XenoNpcRole.QUEST) {
            rows.add(new EditorRow.Text("", "Quest role stays passive; link shared dialogue below or",
                    MUTED));
            rows.add(new EditorRow.Text("", "author this NPC's own dialogue. QUEST answers offer quests.",
                    MUTED));
            rows.add(new EditorRow.Text("", "NPC hand-in uses the quest's configured completer.", MUTED));
        }

        var slots = profile.dialogSlots;
        for (int i = 0; i < net.bullettrain.xenopixelsmod.npc.dialog.NpcDialogSlots.MAX_SLOTS;
                i++) {
            int index = i;
            var slot = slots.get(i);
            rows.add(new EditorRow.Slot(i, labelFor(slot),
                    () -> {
                        dialogSlotBeingPicked = index;
                        dialogPickCategory = slot.group();
                        openPage(ScreenId.ADVANCED_DIALOG_PICK);
                    },
                    slot.assigned() ? () -> {
                        profile.dialogSlots.clear(index);
                        markDirty("DialogSlots");
                        rebuild();
                    } : null));
        }

        rows.add(new EditorRow.Spacer());
        rows.add(new EditorRow.Text("", "Dialogs live in the world store, shared between NPCs.",
                MUTED));
        rows.add(new EditorRow.Action("Create or edit shared dialogs", "mynpcs_button_row", () -> {
            returnToAdvancedDialogs = true;
            openPage(ScreenId.GLOBAL_DIALOGS);
        }, false));
        rows.add(new EditorRow.Action("This NPC's own dialogue", "mynpcs_button_row",
                () -> openPage(ScreenId.ADVANCED_DIALOG_OWN), false));
        return rows;
    }

    /** What a slot shows: the dialogue's label, or the placeholder their screen uses. */
    private String labelFor(net.bullettrain.xenopixelsmod.npc.dialog.NpcDialogSlots.Slot slot) {
        if (!slot.assigned()) {
            return "Select Option";
        }
        var entry = ClientNpcStoreIndex.find(
                net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory.DIALOGS,
                slot.group(), slot.id());
        // A reference whose dialogue was deleted reads as missing rather than as its raw id, so
        // an operator can tell "gone" from "named oddly".
        return entry == null ? slot.id() + " (missing)" : entry.label();
    }

    /**
     * Advanced &gt; Dialogs &gt; Select Dialog: their Categories | Dialogs picker.
     *
     * <p>Both lists come from {@code ClientNpcStoreIndex}, which the server already syncs on join
     * and after every {@code /reload} — so the picker needs no packet of its own.
     */
    private List<EditorRow> dialogPickRows() {
        List<EditorRow> rows = new ArrayList<>();
        var all = ClientNpcStoreIndex.of(
                net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory.DIALOGS);
        if (all.isEmpty()) {
            rows.add(new EditorRow.Heading("Categories"));
            rows.add(new EditorRow.Text("", "No dialogs in the world store yet.", MUTED));
            rows.add(new EditorRow.Text("", "Add one under Global > Dialogs, or ship JSON", MUTED));
            rows.add(new EditorRow.Text("", "under data/<pack>/npcs/dialogue/ and /reload.", MUTED));
            return rows;
        }

        // Natural order, so "DBZ 2" precedes "DBZ 10" the way a reader expects - the same rule the
        // quest log's category buttons follow.
        List<String> categories = new ArrayList<>();
        for (var entry : all) {
            if (!categories.contains(entry.group())) {
                categories.add(entry.group());
            }
        }
        categories.sort(net.bullettrain.xenopixelsmod.client.npc.quest.QuestLogLayout
                ::naturalCompare);
        if (!categories.contains(dialogPickCategory)) {
            dialogPickCategory = categories.get(0);
        }

        rows.add(new EditorRow.Heading("Categories"));
        for (String category : categories) {
            boolean open = category.equals(dialogPickCategory);
            rows.add(new EditorRow.Action((open ? "> " : "  ") + category, "mynpcs_button_row",
                    () -> {
                        dialogPickCategory = category;
                        rebuild();
                    }, false));
        }

        rows.add(new EditorRow.Heading("Dialogs"));
        for (var entry : all) {
            if (!entry.group().equals(dialogPickCategory)) {
                continue;
            }
            rows.add(new EditorRow.Action(entry.label(), "mynpcs_button_row", () -> {
                if (dialogSlotBeingPicked >= 0) {
                    profile.dialogSlots.set(dialogSlotBeingPicked, entry.group(), entry.id());
                    markDirty("DialogSlots");
                }
                openPage(ScreenId.ADVANCED_DIALOGS);
            }, false));
        }
        return rows;
    }

    private List<EditorRow> dialogueRows() {
        List<EditorRow> rows = new ArrayList<>();
        rows.add(new EditorRow.Heading(editingStoreDialogue ? "Global dialog lines" : "Lines"));

        List<DialogueDraft.Node> nodes = dialogueDraft.nodes();
        if (nodes.isEmpty()) {
            if (editingStoreDialogue) {
                rows.add(new EditorRow.Text("", "This global dialog has no lines yet.", MUTED));
                rows.add(new EditorRow.Text("", "Add a line, then save it to make it playable.", MUTED));
            } else {
                rows.add(new EditorRow.Text("", "This NPC has no dialogue of its own.", MUTED));
                rows.add(new EditorRow.Text("", "Add a line to write one. Without one it uses the",
                        MUTED));
                rows.add(new EditorRow.Text("", "dialogue its role names, if any.", MUTED));
            }
        }
        for (int i = 0; i < nodes.size(); i++) {
            final int index = i;
            DialogueDraft.Node node = nodes.get(i);
            String marker = node.id.equals(dialogueDraft.start()) ? "* " : "  ";
            rows.add(new EditorRow.Action(marker + (i + 1) + "  " + summary(node),
                    "mynpcs_button_row", () -> {
                        dialogueNode = index;
                        dialogueOption = -1;
                        openPage(ScreenId.ADVANCED_DIALOG_NODE);
                    }, true));
        }

        rows.add(new EditorRow.Heading("Dialogue"));
        if (nodes.size() < DialogueDraft.MAX_NODES) {
            rows.add(new EditorRow.Action("Add line", "mynpcs_button_row", () -> {
                int added = dialogueDraft.addNode();
                if (added >= 0) {
                    dialogueNode = added;
                    dialogueOption = -1;
                    commitDialogue();
                    openPage(ScreenId.ADVANCED_DIALOG_NODE);
                }
            }));
        } else {
            rows.add(disabledAction("Add line"));
            rows.add(new EditorRow.Text("", "At the " + DialogueDraft.MAX_NODES
                    + "-line limit.", MUTED));
        }

        if (!nodes.isEmpty()) {
            List<String> ids = dialogueDraft.nodeIds();
            rows.add(cycle("Opens on", ids,
                    Math.max(0, ids.indexOf(dialogueDraft.start())),
                    index -> {
                        dialogueDraft.setStart(ids.get(Math.floorMod(index, ids.size())));
                        commitDialogue();
                    },
                    "Dialogue"));
            rows.add(new EditorRow.Action("Clear dialogue", "mynpcs_button_row", () -> {
                dialogueDraft = DialogueDraft.of(null);
                dialogueNode = -1;
                dialogueOption = -1;
                commitDialogue();
                rebuild();
            }));
        }

        appendDialogueProblems(rows);
        if (editingStoreDialogue) {
            rows.add(new EditorRow.Action("Save global dialog", "mynpcs_button_row",
                    this::saveStoreDialogue));
            if (storeDialogueDirty) {
                rows.add(new EditorRow.Text("", "Unsaved global dialog changes", WARN));
                rows.add(new EditorRow.Action("Discard global changes", "mynpcs_button_row",
                        () -> {
                            editingStoreDialogue = false;
                            storeDialogueDirty = false;
                            awaitingStoreDialogueSave = false;
                            submittedStoreDialogue = null;
                            dialogueDraft = DialogueDraft.of(profile.dialogueTag);
                            openPage(ScreenId.GLOBAL_DIALOG_NAME);
                        }));
            }
        }
        return rows;
    }

    /** Advanced &gt; Dialogs &gt; one line: what the NPC says, and what can be answered. */
    private List<EditorRow> dialogueNodeRows() {
        DialogueDraft.Node node = dialogueDraft.node(dialogueNode);
        if (node == null) {
            return List.of(new EditorRow.Heading("Line"),
                    new EditorRow.Text("", "That line has been removed.", MUTED),
                    new EditorRow.Action("Back", "mynpcs_button_row",
                            () -> openPage(editingStoreDialogue ? ScreenId.GLOBAL_DIALOG_LINES
                                    : ScreenId.ADVANCED_DIALOG_OWN)));
        }
        final int nodeIndex = dialogueNode;

        List<EditorRow> rows = new ArrayList<>();
        rows.add(new EditorRow.Heading("Line"));
        rows.add(field("Name", node.id, 32, v -> {
            dialogueDraft.renameNode(nodeIndex, v);
            commitDialogue();
        }, "Dialogue"));
        rows.add(fullField("Says", node.text, XenoDialogueNbt.MAX_TEXT, v -> {
            node.text = v;
            commitDialogue();
        }, "Dialogue"));
        List<String> nodePalettes = new ArrayList<>();
        nodePalettes.add("");
        nodePalettes.addAll(NpcCombatProfile.PALETTES);
        rows.add(cycle("Palette", nodePalettes.stream()
                        .map(value -> value.isEmpty() ? "NPC default" : value).toList(),
                Math.max(0, nodePalettes.indexOf(node.palette)),
                index -> {
                    node.palette = nodePalettes.get(Math.floorMod(index, nodePalettes.size()));
                    commitDialogue();
                }, "Dialogue"));
        rows.add(new EditorRow.Text("", "{player} is replaced with the player's name.", MUTED));

        rows.add(new EditorRow.Heading("Answers"));
        if (node.options.isEmpty()) {
            rows.add(new EditorRow.Text("", "No answers. The conversation ends on this line.",
                    MUTED));
        }
        for (int i = 0; i < node.options.size(); i++) {
            final int optionIndex = i;
            DialogueDraft.Option option = node.options.get(i);
            rows.add(new EditorRow.Action((i + 1) + "  " + summary(option), "mynpcs_button_row",
                    () -> {
                        dialogueOption = optionIndex;
                        openPage(ScreenId.ADVANCED_DIALOG_OPTION);
                    }, true));
        }
        if (node.options.size() < DialogueDraft.MAX_OPTIONS) {
            rows.add(new EditorRow.Action("Add answer", "mynpcs_button_row", () -> {
                int added = dialogueDraft.addOption(nodeIndex);
                if (added >= 0) {
                    dialogueOption = added;
                    commitDialogue();
                    openPage(ScreenId.ADVANCED_DIALOG_OPTION);
                }
            }));
        } else {
            rows.add(disabledAction("Add answer"));
        }

        rows.add(new EditorRow.Heading("Line"));
        rows.add(new EditorRow.Action("Delete line", "mynpcs_button_row", () -> {
            dialogueDraft.removeNode(nodeIndex);
            dialogueNode = -1;
            dialogueOption = -1;
            commitDialogue();
            openPage(editingStoreDialogue ? ScreenId.GLOBAL_DIALOG_LINES
                    : ScreenId.ADVANCED_DIALOG_OWN);
        }));
        return rows;
    }

    /** Advanced &gt; Dialogs &gt; line &gt; one answer: the bubble a player can click. */
    private List<EditorRow> dialogueOptionRows() {
        DialogueDraft.Node node = dialogueDraft.node(dialogueNode);
        DialogueDraft.Option option = node == null || dialogueOption < 0
                || dialogueOption >= node.options.size() ? null : node.options.get(dialogueOption);
        if (option == null) {
            return List.of(new EditorRow.Heading("Answer"),
                    new EditorRow.Text("", "That answer has been removed.", MUTED),
                    new EditorRow.Action("Back", "mynpcs_button_row",
                            () -> openPage(ScreenId.ADVANCED_DIALOG_NODE)));
        }
        final int nodeIndex = dialogueNode;
        final int optionIndex = dialogueOption;

        List<EditorRow> rows = new ArrayList<>();
        rows.add(new EditorRow.Heading("Answer"));
        rows.add(fullField("Reads", option.text, XenoDialogueNbt.MAX_OPTION_TEXT, v -> {
            option.text = v;
            commitDialogue();
        }, "Dialogue"));

        List<String> types = DIALOGUE_OPTION_TYPES;
        rows.add(cycle("Does", types,
                Math.max(0, types.indexOf(option.type.name())),
                index -> {
                    option.type = XenoDialogue.OptionType.valueOf(
                            types.get(Math.floorMod(index, types.size())));
                    commitDialogue();
                    rebuild();
                },
                "Dialogue"));

        // Only the field the chosen type actually uses. Showing all four at once was the
        // alternative, and three of them would be controls writing somewhere nothing reads -
        // which is the shape this editor keeps out.
        switch (option.type) {
            case TEXT -> {
                List<String> ids = dialogueDraft.nodeIds();
                if (ids.isEmpty()) {
                    rows.add(new EditorRow.Text("", "No lines to go to yet.", MUTED));
                } else {
                    rows.add(cycle("Goes to", ids, Math.max(0, ids.indexOf(option.target)),
                            index -> {
                                option.target = ids.get(Math.floorMod(index, ids.size()));
                                commitDialogue();
                            },
                            "Dialogue"));
                }
                rows.add(new EditorRow.Text("", "Moves the conversation on. Resolved on the",
                        MUTED));
                rows.add(new EditorRow.Text("", "client, so it costs nothing.", MUTED));
            }
            case QUEST -> {
                List<QuestCatalogChoices.Choice> quests = QuestCatalogChoices.fromIndex(
                        ClientNpcStoreIndex.of(XenoNpcStoreCategory.QUESTS), option.quest);
                if (quests.isEmpty()) {
                    rows.add(new EditorRow.Text("Quest", "Waiting for server quest catalog...", MUTED));
                } else {
                    List<String> labels = quests.stream().map(QuestCatalogChoices.Choice::label).toList();
                    rows.add(new EditorRow.Cycle("Quest", labels,
                            QuestCatalogChoices.selectedIndex(quests, option.quest), index -> {
                        option.quest = quests.get(Math.floorMod(index, quests.size())).id();
                        commitDialogue();
                    }));
                }
                rows.add(new EditorRow.Text("", "Offers the quest, or hands it in when it is",
                        MUTED));
                rows.add(new EditorRow.Text("", "already complete. Choose from global quests.",
                        MUTED));
            }
            case COMMAND -> {
                rows.add(fullField("Command", option.command, XenoDialogueNbt.MAX_COMMAND, v -> {
                    option.command = v;
                    commitDialogue();
                }, "Dialogue"));
                rows.add(new EditorRow.Text("", "Runs as the NPC at permission 2, not as the",
                        MUTED));
                rows.add(new EditorRow.Text("", "player. Off unless the server enables it.", MUTED));
            }
            case QUIT -> rows.add(new EditorRow.Text("", "Closes the conversation.", MUTED));
            case ROLE -> {
                rows.add(new EditorRow.Text("", "Opens a job or role screen. Parsed so a pack",
                        MUTED));
                rows.add(new EditorRow.Text("", "written against the reference loads, but there",
                        MUTED));
                rows.add(new EditorRow.Text("", "are no economy roles yet - this does nothing.",
                        WARN));
            }
        }

        List<String> answerPalettes = new ArrayList<>();
        answerPalettes.add("NPC default");
        answerPalettes.addAll(NpcCombatProfile.PALETTES);
        int answerPalette = option.palette == null || option.palette.isBlank()
                ? 0 : Math.max(1, NpcCombatProfile.PALETTES.indexOf(
                        NpcCombatProfile.canonicalPalette(option.palette)) + 1);
        rows.add(new EditorRow.Cycle("Bubble colour", answerPalettes, answerPalette, index -> {
            int selected = Math.floorMod(index, answerPalettes.size());
            option.palette = selected == 0 ? ""
                    : NpcCombatProfile.PALETTES.get(selected - 1);
            commitDialogue();
        }));

        rows.add(new EditorRow.Heading("Answer"));
        rows.add(new EditorRow.Action("Delete answer", "mynpcs_button_row", () -> {
            dialogueDraft.removeOption(nodeIndex, optionIndex);
            dialogueOption = -1;
            commitDialogue();
            openPage(ScreenId.ADVANCED_DIALOG_NODE);
        }));
        return rows;
    }

    /** The five option types, by name, for the cycler. */
    private static final List<String> DIALOGUE_OPTION_TYPES = java.util.Arrays.stream(
            XenoDialogue.OptionType.values()).map(Enum::name).toList();

    /**
     * Pushes the draft onto the profile and marks it dirty.
     *
     * <p>On every edit rather than only on save: the draft is the live copy the sub-screens read
     * back, and the profile is what the save packet serialises. Letting the two drift is how a
     * field ends up looking edited and saving its old value.
     */
    private void commitDialogue() {
        if (editingStoreDialogue) {
            storeDialogueDirty = true;
            return;
        }
        profile.setDialogue(dialogueDraft.toTag());
        markDirty("Dialogue");
    }

    /**
     * States what would stop the dialogue opening, rather than refusing to save it.
     *
     * <p>A half-written conversation is the normal state of one being written, so saving has to
     * keep it. What must not happen is saving something broken silently: an NPC whose dialogue does
     * not resolve falls back to its role's, so without this the symptom would be an NPC saying
     * somebody else's lines.
     */
    private void appendDialogueProblems(List<EditorRow> rows) {
        if (dialogueDraft.isEmpty()) {
            return;
        }
        List<String> problems = dialogueDraft.problems();
        rows.add(new EditorRow.Heading(problems.isEmpty() ? "Ready" : "Unfinished"));
        if (problems.isEmpty()) {
            rows.add(new EditorRow.Text("", "This dialogue will open.", MUTED));
            return;
        }
        for (String problem : problems.subList(0, Math.min(6, problems.size()))) {
            rows.add(new EditorRow.Text("", problem, WARN));
        }
        if (problems.size() > 6) {
            rows.add(new EditorRow.Text("", "and " + (problems.size() - 6) + " more.", WARN));
        }
        rows.add(new EditorRow.Text("", "Saved either way; until it resolves the NPC uses its",
                MUTED));
        rows.add(new EditorRow.Text("", "role's dialogue instead.", MUTED));
    }

    private static String summary(DialogueDraft.Node node) {
        String text = node.text == null || node.text.isBlank() ? "(no line)" : node.text;
        return node.id + " - " + trimTo(text, 28);
    }

    private static String summary(DialogueDraft.Option option) {
        String text = option.text == null || option.text.isBlank() ? "(blank)" : option.text;
        return trimTo(text, 24) + "  [" + option.type.name().toLowerCase(Locale.ROOT) + "]";
    }

    private static String trimTo(String text, int max) {
        String flat = text.replace('\n', ' ');
        return flat.length() <= max ? flat : flat.substring(0, max - 1) + "…";
    }

    private List<EditorRow> soundRows() {
        List<EditorRow> rows = new ArrayList<>();
        rows.add(field("Living Sound", profile.soundLiving, 128,
                v -> profile.soundLiving = v, "SoundLiving"));
        rows.add(soundPicker(profile.soundLiving,
                v -> profile.soundLiving = v, "SoundLiving"));
        rows.add(field("Angry Sound", profile.soundAngry, 128,
                v -> profile.soundAngry = v, "SoundAngry"));
        rows.add(soundPicker(profile.soundAngry,
                v -> profile.soundAngry = v, "SoundAngry"));
        rows.add(field("Hurt Sound", profile.soundHurt, 128,
                v -> profile.soundHurt = v, "SoundHurt"));
        rows.add(soundPicker(profile.soundHurt,
                v -> profile.soundHurt = v, "SoundHurt"));
        rows.add(field("Death Sound", profile.soundDeath, 128,
                v -> profile.soundDeath = v, "SoundDeath"));
        rows.add(soundPicker(profile.soundDeath,
                v -> profile.soundDeath = v, "SoundDeath"));
        rows.add(field("Step Sound", profile.soundStep, 128,
                v -> profile.soundStep = v, "SoundStep"));
        rows.add(soundPicker(profile.soundStep,
                v -> profile.soundStep = v, "SoundStep"));
        rows.add(toggle("Has Pitch", profile.soundHasPitch,
                v -> profile.soundHasPitch = v, "SoundHasPitch"));
        rows.add(new EditorRow.Text("", "Use a registered sound id; blank keeps the entity default.", MUTED));
        return rows;
    }

    private List<EditorRow> nightRows() {
        List<EditorRow> rows = new ArrayList<>();

        // The one part of Night that is real. MyNPCs' Night screen is a whole alternate profile -
        // a second set of Display/Stats/AI/... values swapped in after dusk - and that schema does
        // not exist here. A night *texture* does, and both renderers read it through
        // NpcCombatProfile.textureFor, so this row is live while the rest below stays honest about
        // not being built.
        rows.add(new EditorRow.Heading("Night texture"));
        rows.add(field("Texture", profile.nightTexture, 256,
                v -> profile.nightTexture = v, "NightTexture"));
        rows.add(new EditorRow.Action("Select Texture", "mynpcs_button_row",
                () -> openTexturePicker(profile.nightTexture,
                        value -> profile.nightTexture = value, "NightTexture")));
        rows.add(new EditorRow.Text("", "Swapped in after dusk. Blank keeps the day texture.",
                MUTED));

        rows.add(new EditorRow.Heading("Alternate profile"));
        rows.add(disabledToggle("Editing Mode", false));
        for (String section : List.of("Display", "Stats", "AI", "Inventory", "Advanced", "Role", "Job")) {
            rows.add(disabledToggle(section, false));
        }
        rows.add(new EditorRow.Text("", "Night profiles need a complete alternate-state schema.", MUTED));
        return rows;
    }

    /** Draft UUID typed into the Linked screen, before it is added to the list. */
    private String linkDraft = "";

    /**
     * Advanced &gt; Linked: the NPCs this one's edits are copied to.
     *
     * <p>Live, unlike most of this hub, because the list now has a consumer -
     * {@code XenoNpcLinkPropagation} applies a save to everything listed here. It stayed disabled
     * for as long as nothing read the field.
     *
     * <p>A UUID is typed or pasted rather than picked from the world: the editor has no entity
     * picker, and inventing one that silently grabs "the nearest NPC" would be a worse control than
     * an explicit id. Sneak-clicking an NPC already prints its id.
     */
    private List<EditorRow> advancedLinkedRows() {
        List<EditorRow> rows = new ArrayList<>();
        rows.add(new EditorRow.Heading("Linked NPCs"));
        rows.add(field("UUID", linkDraft, 36, v -> linkDraft = v, null));
        rows.add(new EditorRow.Action("Add", "mynpcs_button_row", this::addLink));

        List<String> links = profile.linkedNpcs;
        if (links.isEmpty()) {
            rows.add(new EditorRow.Text("", "No linked NPCs. Saving this one affects only itself.",
                    MUTED));
        } else {
            for (String id : List.copyOf(links)) {
                rows.add(new EditorRow.Text("Linked", shortLink(id), LIGHT));
                rows.add(new EditorRow.Action("Remove " + shortLink(id), "mynpcs_button_row",
                        () -> removeLink(id)));
            }
        }
        rows.add(new EditorRow.Text("", "A save copies the edited fields to every NPC listed here.",
                MUTED));
        rows.add(new EditorRow.Text("", "Links are followed one hop, so two NPCs may link to each",
                MUTED));
        rows.add(new EditorRow.Text("", "other safely. A locked NPC still refuses the copy.", MUTED));
        return rows;
    }

    /** Adds the drafted UUID to the link list, if it is one and is not already there. */
    private void addLink() {
        String raw = linkDraft == null ? "" : linkDraft.trim();
        if (net.bullettrain.xenopixelsmod.npc.XenoNpcLinkPropagation.parse(raw) == null) {
            showNotice("Not a UUID");
            return;
        }
        if (profile.linkedNpcs.contains(raw)) {
            showNotice("Already linked");
            return;
        }
        profile.linkedNpcs.add(raw);
        linkDraft = "";
        markDirty("LinkedNpcs");
        rebuild();
    }

    private void removeLink(String id) {
        if (profile.linkedNpcs.remove(id)) {
            markDirty("LinkedNpcs");
            rebuild();
        }
    }

    /** First and last chunk of a UUID, so a row stays readable in one column. */
    private static String shortLink(String id) {
        if (id == null || id.length() < 13) {
            return id == null ? "" : id;
        }
        return id.substring(0, 8) + "…" + id.substring(id.length() - 4);
    }

    private List<EditorRow> unavailableListRows(String title, String... reasons) {
        List<EditorRow> rows = new ArrayList<>();
        rows.add(new EditorRow.Heading(title));
        rows.add(disabledField("Name", "New"));
        rows.add(disabledAction("Add"));
        rows.add(disabledAction("Remove"));
        rows.add(disabledAction("Edit"));
        // The reason used to read "no native server registry exists". That stopped being true the
        // moment the world store landed, and a disabled control giving a reason that is no longer
        // the reason is its own small lie.
        for (String reason : reasons) {
            rows.add(new EditorRow.Text("", reason, MUTED));
        }
        return rows;
    }

    /**
     * Advanced &gt; Marks: the floating icon above the NPC's head.
     *
     * <p>Live now that {@code NpcMarkRenderer} draws it and the atlas has the six glyphs; it was
     * correctly disabled for as long as the fields were stored and nothing rendered them.
     *
     * <p>Still one mark rather than the reference's list. Screens 42-44 show a list with an
     * availability condition per entry, and neither the list nor the availability system exists
     * yet - so those two rows stay disabled instead of pretending a single mark is a list of one.
     */
    /**
     * The ordered attack-animation slots.
     *
     * <p>{@code NpcCombatProfile} has carried these since melee animations existed and
     * {@link net.bullettrain.xenopixelsmod.compat.npc.NpcMeleeAnimCycle} walks them in order on
     * consecutive hits, skipping blanks - but nothing ever exposed them, so the only way to fill a
     * slot was a script. A working ordered-attack system with no way to author it is the same
     * dead-weight shape as a stored field nothing reads, just from the other side.
     *
     * <p>Rows are numbered from 1 and laid out in cycle order, because the order <em>is</em> the
     * feature: slot 1 plays, then 2, then 3. The pager handles the rest -
     * {@code MELEE_SLOT_COUNT} slots is more than one page by design.
     */
    private List<EditorRow> attackSlotRows() {
        List<EditorRow> rows = new ArrayList<>();
        rows.add(new EditorRow.Heading("Attack order"));
        rows.add(new EditorRow.Text("", "Played in this order on consecutive hits. Blank slots are",
                MUTED));
        rows.add(new EditorRow.Text("", "skipped, so gaps are fine.", MUTED));

        for (int i = 0; i < NpcCombatProfile.MELEE_SLOT_COUNT; i++) {
            final int slot = i;
            rows.add(field("Slot " + (slot + 1), profile.meleeSlotClip(slot), 128,
                    v -> profile.setMeleeSlotClip(slot, v), "MeleeAnimSlots"));
            rows.add(toggle("On", profile.meleeSlotOn(slot),
                    v -> profile.setMeleeSlotOn(slot, v), "MeleeAnimSlots"));
        }
        return rows;
    }

    private List<EditorRow> markRows() {
        List<EditorRow> rows = new ArrayList<>();

        List<String> marks = NpcCombatProfile.MARK_ICONS;
        rows.add(cycle("Mark",
                marks.stream().map(m -> m.isEmpty() ? "None" : m).toList(),
                Math.max(0, marks.indexOf(NpcCombatProfile.canonicalMarkIcon(profile.markIcon))),
                index -> profile.markIcon = marks.get(Math.floorMod(index, marks.size())),
                "MarkIcon"));
        rows.add(colorField("Color", String.format(Locale.ROOT, "#%06X", profile.markColor & 0xFFFFFF),
                v -> {
                    profile.markColor = parseHex(v, profile.markColor);
                    markDirty("MarkColor");
                }));
        rows.add(new EditorRow.Text("", "Shown above the head, tinted by the colour above.", MUTED));

        rows.add(new EditorRow.Heading("Bubble colour"));
        List<String> palettes = NpcCombatProfile.PALETTES;
        rows.add(cycle("Speech", palettes,
                Math.max(0, palettes.indexOf(
                        NpcCombatProfile.canonicalPalette(profile.bubblePalette))),
                index -> profile.bubblePalette = palettes.get(
                        Math.floorMod(index, palettes.size())),
                "BubblePalette"));
        rows.add(cycle("Options", palettes,
                Math.max(0, palettes.indexOf(
                        NpcCombatProfile.canonicalPalette(profile.optionPalette))),
                index -> profile.optionPalette = palettes.get(
                        Math.floorMod(index, palettes.size())),
                "OptionPalette"));
        rows.add(new EditorRow.Text("", "The whole bubble changes, not just its tint - every",
                MUTED));
        rows.add(new EditorRow.Text("", "sprite is generated in all four palettes.", MUTED));

        rows.add(new EditorRow.Heading("Bubble shape"));
        List<String> shapes = java.util.Arrays.stream(
                net.bullettrain.xenopixelsmod.npc.lines.BubbleShape.PICKABLE)
                .map(net.bullettrain.xenopixelsmod.npc.lines.BubbleShape::id).toList();
        rows.add(cycle("Outline", shapes,
                Math.max(0, shapes.indexOf(NpcCombatProfile.canonicalBubbleShape(profile.bubbleShape))),
                index -> profile.bubbleShape = shapes.get(Math.floorMod(index, shapes.size())),
                "BubbleShape"));
        rows.add(new EditorRow.Text("", "Scripts may pick another per line: npc.say(t, \"gold\", \"shout\").",
                MUTED));
        rows.add(floatField("Height", profile.bubbleHeight,
                v -> profile.bubbleHeight = Math.max(0.0f,
                        Math.min(NpcCombatProfile.MAX_BUBBLE_HEIGHT, v)),
                "BubbleHeight"));
        rows.add(new EditorRow.Text("", "Height offset from above the head. 0.7 is neutral.", MUTED));
        rows.add(intField("Duration (ticks)", profile.bubbleDurationTicks,
                v -> profile.bubbleDurationTicks = Math.max(1,
                        Math.min(NpcCombatProfile.MAX_BUBBLE_DURATION_TICKS, v)),
                "BubbleDurationTicks"));
        rows.add(new EditorRow.Text("", "How long a line stays up. 60 is three seconds.", MUTED));
        rows.add(intField("Max lines", profile.bubbleMaxLines,
                v -> profile.bubbleMaxLines = Math.max(0,
                        Math.min(NpcCombatProfile.MAX_BUBBLE_LINES, v)),
                "BubbleMaxLines"));
        rows.add(new EditorRow.Text("", "0 uses whatever the bubble sprite allows.", MUTED));

        rows.add(new EditorRow.Heading("Ambient lines"));
        rows.add(toggle("Speaks unprompted", profile.ambientLinesEnabled,
                v -> profile.ambientLinesEnabled = v, "AmbientLines"));
        rows.add(new EditorRow.Text("", "Off silences Random and World lines only.", MUTED));
        rows.add(new EditorRow.Text("", "Interact, attack and kill lines still answer.", MUTED));
        for (var category
                : net.bullettrain.xenopixelsmod.npc.lines.XenoNpcLines.Category.values()) {
            List<String> own = profile.linesFor(category);
            rows.add(new EditorRow.Action(
                    label(category) + " (" + own.size() + ")", "mynpcs_button_row",
                    () -> openLineCategory(category), false));
        }
        rows.add(new EditorRow.Text("", profile.hasOwnLines()
                ? "This NPC speaks only its own lines, in every category."
                : "Empty: this NPC speaks its role's lines.", MUTED));
        rows.add(new EditorRow.Text("", "Adding any line here replaces the role set entirely.",
                MUTED));

        rows.add(new EditorRow.Heading("Not built yet"));
        rows.add(disabledAction("Add"));
        rows.add(disabledAction("Availability Options"));
        rows.add(new EditorRow.Text("", "The mark catalog and per-mark availability conditions are not implemented.", MUTED));
        rows.add(new EditorRow.Text("", "Only the single mark above is rendered by the native NPC.", MUTED));
        return rows;
    }

    /**
     * Stats &gt; Respawn: where this NPC comes back, and whether it does.
     *
     * <p>Every field here already existed on {@code XenoNpcData} and none of them could be set.
     * {@code setHome} was called only by the wand at placement, so an NPC respawned wherever it was
     * first put and could never be moved; the leash had no row at all; and respawn itself had no
     * switch, every NPC returning after a hardcoded 100 ticks.
     *
     * <p>These write the screen's data tag rather than the profile, and travel in the save's second
     * payload - {@code XenoNpcData} is a different object from {@code NpcCombatProfile}, and a key
     * sent to the wrong one is silently discarded.
     */
    private List<EditorRow> respawnRows() {
        List<EditorRow> r = new ArrayList<>();
        r.add(new EditorRow.Heading("Respawn"));
        r.add(new EditorRow.Toggle("Respawns",
                !data.contains("RespawnEnabled") || data.getBoolean("RespawnEnabled"),
                v -> {
                    data.putBoolean("RespawnEnabled", v);
                    markDataDirty("RespawnEnabled");
                }));
        r.add(intField("Delay (ticks)",
                data.contains("RespawnDelayTicks") ? data.getInt("RespawnDelayTicks") : 100,
                v -> {
                    data.putInt("RespawnDelayTicks", v);
                    markDataDirty("RespawnDelayTicks");
                }, null));
        r.add(new EditorRow.Text("", "20 ticks is one second. The server clamps this to 1-72000.",
                MUTED));

        r.add(new EditorRow.Heading("Respawn point"));
        r.add(new EditorRow.Text("Current", homeText(), MUTED));
        r.add(floatField("Home X", (float) data.getDouble("HomeX"),
                v -> { data.putDouble("HomeX", v); markHomeDirty(); }, null));
        r.add(floatField("Home Y", (float) data.getDouble("HomeY"),
                v -> { data.putDouble("HomeY", v); markHomeDirty(); }, null));
        r.add(floatField("Home Z", (float) data.getDouble("HomeZ"),
                v -> { data.putDouble("HomeZ", v); markHomeDirty(); }, null));
        r.add(new EditorRow.Action("Set to my position", "mynpcs_button_row", () -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) {
                return;
            }
            data.putDouble("HomeX", mc.player.getX());
            data.putDouble("HomeY", mc.player.getY());
            data.putDouble("HomeZ", mc.player.getZ());
            markHomeDirty();
            rebuild();
        }, true));

        r.add(new EditorRow.Heading("Leash"));
        r.add(floatField("Radius",
                (float) (data.contains("LeashRadius") ? data.getDouble("LeashRadius") : 32.0),
                v -> { data.putDouble("LeashRadius", v); markDataDirty("LeashRadius"); }, null));
        r.add(new EditorRow.Text("", "Blocks from the respawn point before the NPC walks back.",
                MUTED));
        r.add(new EditorRow.Text("", "Zero disables it. The server clamps this to 0-512.", MUTED));
        return r;
    }

    /**
     * The three home coordinates move together.
     *
     * <p>{@code XenoNpcData.fromTag} only reads a home when all three keys are present, and
     * {@code setHome} takes all three at once — sending one alone would be dropped on the floor.
     */
    private void markHomeDirty() {
        markDataDirty("HomeX");
        markDataDirty("HomeY");
        markDataDirty("HomeZ");
    }

/** The category whose lines the Lines page is showing, or null when none is open. */
    private net.bullettrain.xenopixelsmod.npc.lines.XenoNpcLines.Category lineCategory;

    private static String label(net.bullettrain.xenopixelsmod.npc.lines.XenoNpcLines.Category category) {
        return switch (category) {
            case INTERACT -> "Interact";
            case ATTACK -> "Attack";
            case KILL -> "Kill";
            case KILLED -> "Killed";
            case RANDOM -> "Random";
            case WORLD -> "World";
            case NPC -> "NPC";
        };
    }

    private void openLineCategory(net.bullettrain.xenopixelsmod.npc.lines.XenoNpcLines.Category category) {
        lineCategory = category;
        openPage(ScreenId.ADVANCED_BUBBLE_LINES);
    }

    /**
     * Advanced &gt; Bubble &gt; one category's lines.
     *
     * <p>Lines used to come only from the role's datapack file, so every NPC of a role said the
     * same things and no operator could change it in game. These live on the NPC's own profile
     * instead, the way dialogue already does.
     *
     * <p>Writing any line here makes this NPC speak <em>only</em> its own, in every category - the
     * override is whole, not per category. The page says so, because it will otherwise surprise
     * somebody who set one greeting and lost their combat lines.
     */
    private List<EditorRow> bubbleLineRows() {
        List<EditorRow> rows = new ArrayList<>();
        if (lineCategory == null) {
            rows.add(new EditorRow.Text("", "No category selected.", MUTED));
            return rows;
        }
        rows.add(new EditorRow.Heading(label(lineCategory) + " lines"));
        List<String> own = new ArrayList<>(profile.linesFor(lineCategory));
        for (int i = 0; i < own.size(); i++) {
            int index = i;
            rows.add(new EditorRow.Field("Line " + (i + 1), own.get(i),
                    NpcCombatProfile.MAX_LINE_LENGTH, value -> {
                        List<String> next = new ArrayList<>(profile.linesFor(lineCategory));
                        while (next.size() <= index) {
                            next.add("");
                        }
                        next.set(index, value);
                        profile.setLines(lineCategory, next);
                        markDirty("NpcLines");
                    }));
        }
        if (own.size() < NpcCombatProfile.MAX_LINES_PER_CATEGORY) {
            rows.add(new EditorRow.Action("Add line", "mynpcs_button_row", () -> {
                List<String> next = new ArrayList<>(profile.linesFor(lineCategory));
                next.add("New line");
                profile.setLines(lineCategory, next);
                markDirty("NpcLines");
                rebuild();
            }, true));
        }
        if (!own.isEmpty()) {
            rows.add(new EditorRow.Action("Remove last", "mynpcs_button_row", () -> {
                List<String> next = new ArrayList<>(profile.linesFor(lineCategory));
                next.remove(next.size() - 1);
                profile.setLines(lineCategory, next);
                markDirty("NpcLines");
                rebuild();
            }, true));
        }
        rows.add(new EditorRow.Text("", "Any line here replaces the role's whole line set,",
                MUTED));
        rows.add(new EditorRow.Text("", "not just this category. Clear them all to go back.",
                MUTED));
        return rows;
    }

        /**
     * Advanced &gt; Trader: what this NPC will swap.
     *
     * <p>These eighteen rows were {@code disabledAction} placeholders for a long time. They are
     * real now because something reads what they write: {@code XenoNpcEntity} implements vanilla's
     * {@code Merchant}, so a trader with stock opens the ordinary trade screen.
     *
     * <p>Items are typed as registry ids. That is less friendly than a slot you drop an item into,
     * and it is what the editor's row vocabulary supports today — a real item slot needs a
     * container-backed widget this screen does not have. An id that names nothing is skipped at
     * offer time rather than substituted, so a typo costs one trade and not the wrong item.
     *
     * <p>Ignore damage, Ignore NBT and Linked Marketname stay disabled: nothing reads them, and a
     * control with no consumer is exactly what this page is being rescued from.
     */
    /**
     * Advanced &gt; Transporter: where this NPC can send a player.
     *
     * <p>Editable on any NPC, not only a TRANSPORTER. An operator usually fills the destinations
     * in before switching the role over, and hiding the page until then would make that
     * impossible; a line says plainly when the role is not set yet.
     */
    /** Which network the picker is filling in, kept while the sub-page is open. */
    private String editedNetwork = "";

    /**
     * Advanced &gt; Transporter: which network this NPC serves.
     *
     * <p>Destinations live in the world store under {@code transport/} and are shared: two
     * transporters pointed at one network offer the same places, and renaming a destination once
     * changes both. This page picks the network; the sub-page edits it.
     *
     * <p>The list comes from {@code ClientNpcStoreIndex}, which the server already syncs on join
     * and after {@code /reload} — no packet of its own was needed.
     */
    private List<EditorRow> transportRows() {
        List<EditorRow> rows = new ArrayList<>();
        rows.add(new EditorRow.Heading("Transport network"));

        var networks = ClientNpcStoreIndex.of(
                net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory.TRANSPORT);
        String current = profile.transportNetwork;

        if (networks.isEmpty()) {
            rows.add(new EditorRow.Text("", "No networks yet. Add one below, then put its", MUTED));
            rows.add(new EditorRow.Text("", "destinations in from the world you want them in.",
                    MUTED));
        }

        for (var entry : networks) {
            boolean serving = entry.id().equals(current);
            rows.add(new EditorRow.Slot(networks.indexOf(entry),
                    (serving ? "> " : "  ") + entry.label(),
                    () -> {
                        profile.transportNetwork = entry.id();
                        markDirty("TransportNetwork");
                        rebuild();
                    },
                    serving ? () -> {
                        profile.transportNetwork = "";
                        markDirty("TransportNetwork");
                        rebuild();
                    } : null));
        }

        if (!current.isEmpty() && networks.stream().noneMatch(e -> e.id().equals(current))) {
            // Pointing at something the store no longer has. Said out loud rather than shown as
            // "none", because those are different problems with different fixes.
            rows.add(new EditorRow.Text("", "Serving '" + current + "', which no longer exists.",
                    MUTED));
        }

        rows.add(new EditorRow.Spacer());
        rows.add(new EditorRow.Text("", "Destinations are shared. Editing a network changes", MUTED));
        rows.add(new EditorRow.Text("", "it for every NPC serving it.", MUTED));
        return rows;
    }

    private List<EditorRow> traderRows() {
        List<EditorRow> rows = new ArrayList<>();
        rows.add(new EditorRow.Heading("Trades"));
        rows.add(new EditorRow.Text("", "Item ids, like minecraft:emerald. Uses 0 is unlimited.",
                MUTED));

        var stock = profile.trades;
        for (int i = 0; i < stock.size(); i++) {
            int index = i;
            var trade = stock.get(i);
            rows.add(new EditorRow.Heading("Trade " + (i + 1)));
            rows.add(new EditorRow.Field("Costs", trade.costA(), 128,
                    value -> replaceTrade(index, value, null, null, null)));
            rows.add(intField("Cost count", trade.countA(),
                    v -> replaceTradeCount(index, v, -1, -1), null));
            rows.add(new EditorRow.Field("Second cost", trade.costB(), 128,
                    value -> replaceTrade(index, null, value, null, null)));
            rows.add(intField("Second count", trade.countB(),
                    v -> replaceTradeCount(index, -1, v, -1), null));
            rows.add(new EditorRow.Field("Gives", trade.result(), 128,
                    value -> replaceTrade(index, null, null, value, null)));
            rows.add(intField("Gives count", trade.resultCount(),
                    v -> replaceTradeCount(index, -1, -1, v), null));
            rows.add(intField("Uses", trade.maxUses(),
                    v -> replaceTradeUses(index, v), null));
            String tradeProblem = net.bullettrain.xenopixelsmod.npc.trade.NpcTradeOffers
                    .problem(trade);
            if (tradeProblem != null) {
                rows.add(new EditorRow.Text("", tradeProblem, WARN));
            }
            rows.add(new EditorRow.Action("Remove trade " + (i + 1), "mynpcs_button_row", () -> {
                profile.trades.remove(index);
                markDirty("Trades");
                rebuild();
            }, true));
        }

        if (stock.size() < net.bullettrain.xenopixelsmod.npc.trade.NpcTradeList.MAX_TRADES) {
            rows.add(new EditorRow.Action("Add trade", "mynpcs_button_row", () -> {
                profile.trades.add(net.bullettrain.xenopixelsmod.npc.trade.NpcTrade.empty());
                markDirty("Trades");
                rebuild();
            }, true));
        }
        if (stock.isEmpty()) {
            rows.add(new EditorRow.Text("", "No complete trades yet - this NPC will talk,", MUTED));
            rows.add(new EditorRow.Text("", "not trade, until one has a cost and a result.",
                    MUTED));
        }
        if (net.bullettrain.xenopixelsmod.npc.XenoNpcRole.byId(data.getString("Role"))
                != net.bullettrain.xenopixelsmod.npc.XenoNpcRole.TRADER) {
            rows.add(new EditorRow.Text("", "NPC role must be Trader to open this shop.", WARN));
        }

        rows.add(new EditorRow.Heading("Matching"));
        rows.add(toggle("Ignore damage", profile.tradeIgnoreDamage,
                v -> profile.tradeIgnoreDamage = v, "TradeIgnoreDamage"));
        rows.add(toggle("Ignore NBT", profile.tradeIgnoreNbt,
                v -> profile.tradeIgnoreNbt = v, "TradeIgnoreNbt"));
        rows.add(new EditorRow.Text("", "Off: worn, renamed or enchanted payment is refused.",
                MUTED));
        rows.add(disabledField("Linked Marketname", ""));
        rows.add(new EditorRow.Text("", "Shared markets have no store yet; each trader keeps",
                MUTED));
        rows.add(new EditorRow.Text("", "its own stock.", MUTED));
        return rows;
    }

    /** Replaces the id fields of one trade, leaving the others as they are. */
    private void replaceTrade(int index, String costA, String costB, String result, String unused) {
        var old = profile.trades.get(index);
        profile.trades.set(index, new net.bullettrain.xenopixelsmod.npc.trade.NpcTrade(
                costA == null ? old.costA() : costA, old.countA(),
                costB == null ? old.costB() : costB, old.countB(),
                result == null ? old.result() : result, old.resultCount(),
                old.maxUses()));
        markDirty("Trades");
    }

    /** Replaces the counts of one trade. A negative means "leave this one alone". */
    private void replaceTradeCount(int index, int countA, int countB, int resultCount) {
        var old = profile.trades.get(index);
        profile.trades.set(index, new net.bullettrain.xenopixelsmod.npc.trade.NpcTrade(
                old.costA(), countA < 0 ? old.countA() : countA,
                old.costB(), countB < 0 ? old.countB() : countB,
                old.result(), resultCount < 0 ? old.resultCount() : resultCount,
                old.maxUses()));
        markDirty("Trades");
    }

    private void replaceTradeUses(int index, int uses) {
        var old = profile.trades.get(index);
        profile.trades.set(index, new net.bullettrain.xenopixelsmod.npc.trade.NpcTrade(
                old.costA(), old.countA(), old.costB(), old.countB(),
                old.result(), old.resultCount(), uses));
        markDirty("Trades");
    }

    /**
     * The Bard job, live.
     *
     * <p>Their page row for row. On and off distance are separate numbers on purpose: one radius
     * makes an NPC at exactly that range stutter as a player drifts across it.
     */
    private List<EditorRow> bardRows() {
        List<EditorRow> rows = new ArrayList<>();
        rows.add(field("Sound", profile.bardSound, 128,
                v -> profile.bardSound = v, "BardSound"));
        rows.add(soundPicker(profile.bardSound, v -> profile.bardSound = v, "BardSound"));
        rows.add(new EditorRow.Cycle("Plays as", List.of("jukebox", "ambient"),
                profile.bardJukebox ? 0 : 1,
                index -> {
                    profile.bardJukebox = index == 0;
                    markDirty("BardJukebox");
                    rebuild();
                }));
        rows.add(new EditorRow.Toggle("Loops", profile.bardLoops, v -> {
            profile.bardLoops = v;
            markDirty("BardLoops");
        }));
        rows.add(new EditorRow.Stepper("On Distance", profile.bardOnDistance, 0,
                net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.MAX_BARD_DISTANCE,
                v -> {
                    profile.bardOnDistance = v;
                    markDirty("BardOnDistance");
                }));
        rows.add(new EditorRow.Toggle("Has Off Distance", profile.bardHasOffDistance, v -> {
            profile.bardHasOffDistance = v;
            markDirty("BardHasOffDistance");
            rebuild();
        }));
        if (profile.bardHasOffDistance) {
            rows.add(new EditorRow.Stepper("Off Distance", profile.bardOffDistance, 0,
                    net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.MAX_BARD_DISTANCE,
                    v -> {
                        profile.bardOffDistance = v;
                        markDirty("BardOffDistance");
                    }));
            if (profile.bardOffDistance < profile.bardOnDistance) {
                rows.add(new EditorRow.Text("", "Off is inside On, so the larger is used.", MUTED));
            }
        }
        if (net.bullettrain.xenopixelsmod.npc.XenoNpcJob.byId(profile.job)
                != net.bullettrain.xenopixelsmod.npc.XenoNpcJob.BARD) {
            rows.add(new EditorRow.Spacer());
            rows.add(new EditorRow.Text("", "This NPC's job is not Bard, so none of this", MUTED));
            rows.add(new EditorRow.Text("", "runs. Set it on the Advanced page.", MUTED));
        }
        return rows;
    }

    private String newHealerEffectId = "minecraft:regeneration";
    private int newHealerAmplifier;

    private List<EditorRow> healerRows() {
        List<EditorRow> rows = new ArrayList<>();
        rows.add(new EditorRow.Stepper("Range", profile.healerRange, 0, 64, v -> {
            profile.healerRange = v;
            markDirty("HealerRange");
        }));
        rows.add(new EditorRow.Cycle("Targets", List.of("Friendly", "Hostile", "Everyone"),
                profile.healerType, v -> {
                    profile.healerType = v;
                    markDirty("HealerType");
        }));
        rows.add(new EditorRow.Stepper("Interval (ticks)", profile.healerSpeed, 10, 1200, v -> {
            profile.healerSpeed = v;
            markDirty("HealerSpeed");
        }));
        rows.add(new EditorRow.Text("", "Effects last 100 ticks and refresh at this interval.", MUTED));
        rows.add(new EditorRow.Field("Effect ID", newHealerEffectId, 128,
                v -> newHealerEffectId = v));
        rows.add(new EditorRow.Stepper("Amplifier", newHealerAmplifier, 0, 9,
                v -> newHealerAmplifier = v));
        rows.add(new EditorRow.Action("Add Effect", "mynpcs_button_row", () -> {
            String id = newHealerEffectId.trim().toLowerCase(java.util.Locale.ROOT);
            if (id.length() > 128 || net.minecraft.resources.ResourceLocation.tryParse(id) == null) {
                showNotice("Enter a valid namespaced effect ID");
                return;
            }
            if (net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                    .getHolder(net.minecraft.resources.ResourceLocation.parse(id)).isEmpty()) {
                showNotice("Effect is not registered: " + id);
                return;
            }
            if (!profile.healerEffects.containsKey(id)
                    && profile.healerEffects.size() >=
                    net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.MAX_HEALER_EFFECTS) {
                showNotice("Healer effect limit reached");
                return;
            }
            profile.healerEffects.put(id, newHealerAmplifier);
            markDirty("HealerEffects");
            rebuild();
        }));
        for (var effect : List.copyOf(profile.healerEffects.entrySet())) {
            String id = effect.getKey();
            rows.add(new EditorRow.Stepper(id, effect.getValue(), 0, 9, v -> {
                profile.healerEffects.put(id, v);
                markDirty("HealerEffects");
            }));
            rows.add(new EditorRow.Action("Remove " + id, "mynpcs_button_row", () -> {
                profile.healerEffects.remove(id);
                markDirty("HealerEffects");
                rebuild();
            }));
        }
        if (net.bullettrain.xenopixelsmod.npc.XenoNpcJob.byId(profile.job)
                != net.bullettrain.xenopixelsmod.npc.XenoNpcJob.HEALER) {
            rows.add(new EditorRow.Text("", "Set Job to Healer on the Advanced page to run it.", MUTED));
        }
        return rows;
    }

    private List<EditorRow> followerRows() {
        List<EditorRow> rows = new ArrayList<>();
        rows.add(field("Follow NPC name", profile.followerName, 64,
                v -> profile.followerName = v, "FollowerName"));
        rows.add(new EditorRow.Text("", "Follows the nearest named Xeno, MyNPCs or CustomNPCs", MUTED));
        rows.add(new EditorRow.Text("", "NPC within 20 blocks. Names match without case.", MUTED));
        rows.add(new EditorRow.Text("", "Combat and scenes take priority over following.", MUTED));
        if (net.bullettrain.xenopixelsmod.npc.XenoNpcJob.byId(profile.job)
                != net.bullettrain.xenopixelsmod.npc.XenoNpcJob.FOLLOWER) {
            rows.add(new EditorRow.Text("", "Set Job to Follower on Advanced to run it.", MUTED));
        }
        return rows;
    }

    private int itemGiverSelectedSlot;
    private String newItemGiverLine = "";
    private String newItemGiverQuest = "";
    private String newItemGiverDialog = "";
    private String newItemGiverFaction = "";
    private String newItemGiverScore = "";

    private List<EditorRow> itemGiverRows() {
        List<EditorRow> rows = new ArrayList<>();
        rows.add(new EditorRow.Action("Availability", "mynpcs_button_row",
                () -> openPage(ScreenId.ADVANCED_ITEM_GIVER_AVAILABILITY)));
        rows.add(cycle("Giving", List.of("Random", "All", "Missing", "If None Owned", "Chained"),
                profile.itemGiverMethod, v -> profile.itemGiverMethod = v, "ItemGiverMethod"));
        rows.add(cycle("Cooldown", List.of("Seconds", "Once", "MC Daily"),
                profile.itemGiverCooldownType,
                v -> profile.itemGiverCooldownType = v, "ItemGiverCooldownType"));
        if (profile.itemGiverCooldownType == 0) {
            rows.add(new EditorRow.Stepper("Seconds", profile.itemGiverCooldown, 0, 86400, v -> {
                profile.itemGiverCooldown = v;
                markDirty("ItemGiverCooldown");
            }));
        }
        rows.add(new EditorRow.Stepper("Item slot", itemGiverSelectedSlot + 1, 1,
                NpcCombatProfile.ITEM_GIVER_SLOTS, v -> {
            itemGiverSelectedSlot = v - 1;
            scheduleRefresh();
        }));
        var saved = profile.itemGiverItems[itemGiverSelectedSlot];
        var stack = saved == null || minecraft == null || minecraft.level == null
                ? net.minecraft.world.item.ItemStack.EMPTY
                : saved.stack(minecraft.level.registryAccess());
        rows.add(new EditorRow.Text("", stack.isEmpty() ? "Slot is empty"
                : stack.getHoverName().getString() + " x" + stack.getCount(), MUTED));
        rows.add(new EditorRow.Action("Copy held item to slot", "mynpcs_button_row", () -> {
            if (minecraft == null || minecraft.player == null || minecraft.level == null
                    || minecraft.player.getMainHandItem().isEmpty()) {
                showNotice("Hold an item in the main hand first");
                return;
            }
            profile.itemGiverItems[itemGiverSelectedSlot] =
                    net.bullettrain.xenopixelsmod.npc.inventory.NpcSlotStack.of(
                            minecraft.player.getMainHandItem(), minecraft.level.registryAccess());
            markDirty("ItemGiverItems");
            rebuild();
        }));
        rows.add(new EditorRow.Action("Clear slot", "mynpcs_button_row", () -> {
            profile.itemGiverItems[itemGiverSelectedSlot] =
                    net.bullettrain.xenopixelsmod.npc.inventory.NpcSlotStack.EMPTY;
            markDirty("ItemGiverItems");
            rebuild();
        }));
        rows.add(new EditorRow.Field("New line", newItemGiverLine, 256,
                v -> newItemGiverLine = v));
        rows.add(new EditorRow.Action("Add line", "mynpcs_button_row", () -> {
            String line = newItemGiverLine.trim();
            if (line.isEmpty() || profile.itemGiverLines.size() >= 16) {
                showNotice("Enter a line; maximum is 16");
                return;
            }
            profile.itemGiverLines.add(line);
            newItemGiverLine = "";
            markDirty("ItemGiverLines");
            rebuild();
        }));
        for (int index = 0; index < profile.itemGiverLines.size(); index++) {
            int lineIndex = index;
            rows.add(new EditorRow.Text("", profile.itemGiverLines.get(index), MUTED));
            rows.add(new EditorRow.Action("Remove line " + (index + 1), "mynpcs_button_row", () -> {
                profile.itemGiverLines.remove(lineIndex);
                markDirty("ItemGiverLines");
                rebuild();
            }));
        }
        rows.add(new EditorRow.Text("", "Items go to players entering within 3 blocks.", MUTED));
        rows.add(new EditorRow.Text("", "Leave 10 blocks before the next entry attempt.", MUTED));
        if (net.bullettrain.xenopixelsmod.npc.XenoNpcJob.byId(profile.job)
                != net.bullettrain.xenopixelsmod.npc.XenoNpcJob.ITEM_GIVER) {
            rows.add(new EditorRow.Text("", "Set Job to Item Giver on Advanced to run it.", MUTED));
        }
        return rows;
    }

    private void setItemGiverAvailability(QuestAvailability next) {
        profile.itemGiverAvailability = next;
        markDirty("ItemGiverAvailability");
        scheduleRefresh();
    }

    private List<EditorRow> itemGiverAvailabilityRows() {
        List<EditorRow> rows = new ArrayList<>();
        QuestAvailability gate = profile.itemGiverAvailability == null
                ? QuestAvailability.NONE : profile.itemGiverAvailability;
        rows.add(new EditorRow.Stepper("Min player level", gate.minLevel(), 0, 1000,
                value -> setItemGiverAvailability(new QuestAvailability(gate.quests(),
                        gate.dialogs(), gate.daytime(), gate.factions(), gate.scores(), value))));
        List<String> times = List.of("Always", "Day", "Night");
        int time = Math.max(0, times.indexOf(gate.daytime().equals("night") ? "Night"
                : gate.daytime().equals("day") ? "Day" : "Always"));
        rows.add(new EditorRow.Cycle("Time", times, time,
                value -> setItemGiverAvailability(new QuestAvailability(gate.quests(),
                        gate.dialogs(), times.get(value).toLowerCase(Locale.ROOT),
                        gate.factions(), gate.scores(), gate.minLevel()))));

        rows.add(new EditorRow.Heading("Quest conditions"));
        rows.add(new EditorRow.Field("Quest id", newItemGiverQuest, 128,
                value -> newItemGiverQuest = value));
        rows.add(new EditorRow.Action("Add quest condition", "mynpcs_button_row", () -> {
            String id = newItemGiverQuest.trim();
            if (id.isEmpty() || gate.quests().size() >= 4) {
                showNotice("Enter a quest id; maximum is four");
                return;
            }
            List<QuestAvailability.QuestGate> quests = new ArrayList<>(gate.quests());
            quests.add(new QuestAvailability.QuestGate(id, QuestAvailability.QuestState.COMPLETED));
            newItemGiverQuest = "";
            setItemGiverAvailability(new QuestAvailability(quests, gate.dialogs(), gate.daytime(),
                    gate.factions(), gate.scores(), gate.minLevel()));
        }));
        List<QuestAvailability.QuestState> states = List.of(QuestAvailability.QuestState.values());
        for (int i = 0; i < gate.quests().size(); i++) {
            int index = i;
            var quest = gate.quests().get(i);
            rows.add(new EditorRow.Text("", quest.questId(), MUTED));
            rows.add(new EditorRow.Cycle("State " + (i + 1), states.stream()
                    .map(Enum::name).toList(), states.indexOf(quest.state()), value -> {
                List<QuestAvailability.QuestGate> quests = new ArrayList<>(gate.quests());
                quests.set(index, new QuestAvailability.QuestGate(quest.questId(), states.get(value)));
                setItemGiverAvailability(new QuestAvailability(quests, gate.dialogs(), gate.daytime(),
                        gate.factions(), gate.scores(), gate.minLevel()));
            }));
            rows.add(new EditorRow.Action("Remove quest " + (i + 1), "mynpcs_button_row", () -> {
                List<QuestAvailability.QuestGate> quests = new ArrayList<>(gate.quests());
                quests.remove(index);
                setItemGiverAvailability(new QuestAvailability(quests, gate.dialogs(), gate.daytime(),
                        gate.factions(), gate.scores(), gate.minLevel()));
            }));
        }

        rows.add(new EditorRow.Heading("Dialogue conditions"));
        rows.add(new EditorRow.Field("Shared dialogue id", newItemGiverDialog, 128,
                value -> newItemGiverDialog = value));
        rows.add(new EditorRow.Action("Add dialogue condition", "mynpcs_button_row", () -> {
            String id = newItemGiverDialog.trim();
            if (id.isEmpty() || gate.dialogs().size() >= 4) {
                showNotice("Enter a shared dialogue id; maximum is four");
                return;
            }
            List<QuestAvailability.DialogGate> dialogs = new ArrayList<>(gate.dialogs());
            dialogs.add(new QuestAvailability.DialogGate(id, QuestAvailability.DialogState.AFTER));
            newItemGiverDialog = "";
            setItemGiverAvailability(new QuestAvailability(gate.quests(), dialogs, gate.daytime(),
                    gate.factions(), gate.scores(), gate.minLevel()));
        }));
        List<QuestAvailability.DialogState> dialogStates =
                List.of(QuestAvailability.DialogState.values());
        for (int i = 0; i < gate.dialogs().size(); i++) {
            int index = i;
            var dialog = gate.dialogs().get(i);
            rows.add(new EditorRow.Text("", dialog.dialogId(), MUTED));
            rows.add(new EditorRow.Cycle("Viewed " + (i + 1), dialogStates.stream()
                    .map(Enum::name).toList(), dialogStates.indexOf(dialog.state()), value -> {
                List<QuestAvailability.DialogGate> dialogs = new ArrayList<>(gate.dialogs());
                dialogs.set(index, new QuestAvailability.DialogGate(dialog.dialogId(),
                        dialogStates.get(value)));
                setItemGiverAvailability(new QuestAvailability(gate.quests(), dialogs, gate.daytime(),
                        gate.factions(), gate.scores(), gate.minLevel()));
            }));
            rows.add(new EditorRow.Action("Remove dialogue " + (i + 1), "mynpcs_button_row", () -> {
                List<QuestAvailability.DialogGate> dialogs = new ArrayList<>(gate.dialogs());
                dialogs.remove(index);
                setItemGiverAvailability(new QuestAvailability(gate.quests(), dialogs, gate.daytime(),
                        gate.factions(), gate.scores(), gate.minLevel()));
            }));
        }

        rows.add(new EditorRow.Heading("Faction conditions"));
        rows.add(new EditorRow.Field("Faction id", newItemGiverFaction, 128,
                value -> newItemGiverFaction = value));
        rows.add(new EditorRow.Action("Add faction condition", "mynpcs_button_row", () -> {
            String id = newItemGiverFaction.trim();
            if (id.isEmpty() || gate.factions().size() >= 2) {
                showNotice("Enter a faction id; maximum is two");
                return;
            }
            List<QuestAvailability.FactionGate> factions = new ArrayList<>(gate.factions());
            factions.add(new QuestAvailability.FactionGate(id,
                    QuestAvailability.Stance.FRIENDLY, true));
            newItemGiverFaction = "";
            setItemGiverAvailability(new QuestAvailability(gate.quests(), gate.dialogs(),
                    gate.daytime(), factions, gate.scores(), gate.minLevel()));
        }));
        List<QuestAvailability.Stance> stances = List.of(QuestAvailability.Stance.values());
        for (int i = 0; i < gate.factions().size(); i++) {
            int index = i;
            var faction = gate.factions().get(i);
            rows.add(new EditorRow.Text("", faction.factionId(), MUTED));
            rows.add(new EditorRow.Cycle("Stance " + (i + 1), stances.stream()
                    .map(Enum::name).toList(), stances.indexOf(faction.stance()), value -> {
                List<QuestAvailability.FactionGate> factions = new ArrayList<>(gate.factions());
                factions.set(index, new QuestAvailability.FactionGate(faction.factionId(),
                        stances.get(value), faction.matches()));
                setItemGiverAvailability(new QuestAvailability(gate.quests(), gate.dialogs(),
                        gate.daytime(), factions, gate.scores(), gate.minLevel()));
            }));
            rows.add(new EditorRow.Toggle("Must match " + (i + 1), faction.matches(), value -> {
                List<QuestAvailability.FactionGate> factions = new ArrayList<>(gate.factions());
                factions.set(index, new QuestAvailability.FactionGate(faction.factionId(),
                        faction.stance(), value));
                setItemGiverAvailability(new QuestAvailability(gate.quests(), gate.dialogs(),
                        gate.daytime(), factions, gate.scores(), gate.minLevel()));
            }));
            rows.add(new EditorRow.Action("Remove faction " + (i + 1), "mynpcs_button_row", () -> {
                List<QuestAvailability.FactionGate> factions = new ArrayList<>(gate.factions());
                factions.remove(index);
                setItemGiverAvailability(new QuestAvailability(gate.quests(), gate.dialogs(),
                        gate.daytime(), factions, gate.scores(), gate.minLevel()));
            }));
        }

        rows.add(new EditorRow.Heading("Scoreboard conditions"));
        rows.add(new EditorRow.Field("Objective", newItemGiverScore, 64,
                value -> newItemGiverScore = value));
        rows.add(new EditorRow.Action("Add score condition", "mynpcs_button_row", () -> {
            String id = newItemGiverScore.trim();
            if (id.isEmpty() || gate.scores().size() >= 2) {
                showNotice("Enter an objective; maximum is two");
                return;
            }
            List<QuestAvailability.ScoreGate> scores = new ArrayList<>(gate.scores());
            scores.add(new QuestAvailability.ScoreGate(id, QuestAvailability.Compare.EQUAL, 0));
            newItemGiverScore = "";
            setItemGiverAvailability(new QuestAvailability(gate.quests(), gate.dialogs(),
                    gate.daytime(), gate.factions(), scores, gate.minLevel()));
        }));
        List<QuestAvailability.Compare> compares = List.of(QuestAvailability.Compare.values());
        for (int i = 0; i < gate.scores().size(); i++) {
            int index = i;
            var score = gate.scores().get(i);
            rows.add(new EditorRow.Text("", score.objective(), MUTED));
            rows.add(new EditorRow.Cycle("Compare " + (i + 1), compares.stream()
                    .map(Enum::name).toList(), compares.indexOf(score.compare()), value -> {
                List<QuestAvailability.ScoreGate> scores = new ArrayList<>(gate.scores());
                scores.set(index, new QuestAvailability.ScoreGate(score.objective(),
                        compares.get(value), score.value()));
                setItemGiverAvailability(new QuestAvailability(gate.quests(), gate.dialogs(),
                        gate.daytime(), gate.factions(), scores, gate.minLevel()));
            }));
            rows.add(new EditorRow.Stepper("Score " + (i + 1), score.value(),
                    -1_000_000, 1_000_000, value -> {
                List<QuestAvailability.ScoreGate> scores = new ArrayList<>(gate.scores());
                scores.set(index, new QuestAvailability.ScoreGate(score.objective(),
                        score.compare(), value));
                setItemGiverAvailability(new QuestAvailability(gate.quests(), gate.dialogs(),
                        gate.daytime(), gate.factions(), scores, gate.minLevel()));
            }));
            rows.add(new EditorRow.Action("Remove score " + (i + 1), "mynpcs_button_row", () -> {
                List<QuestAvailability.ScoreGate> scores = new ArrayList<>(gate.scores());
                scores.remove(index);
                setItemGiverAvailability(new QuestAvailability(gate.quests(), gate.dialogs(),
                        gate.daytime(), gate.factions(), scores, gate.minLevel()));
            }));
        }
        rows.add(new EditorRow.Text("", "Viewed state tracks shared Xeno dialogues opened", MUTED));
        rows.add(new EditorRow.Text("", "since this update; NPC-owned trees have no shared id.", MUTED));
        return rows;
    }

    /** The bank id being typed into Global &gt; Banks. Kept across a page rebuild. */
    private String newBankId = "";

    /** Which bank the page below is showing. */
    private String selectedBank = "";

    /**
     * The Global Banks manager.
     *
     * <p>Their page is a fixed "Tab 1..6 Cost / Can Upgrade" grid, and this keeps that shape - but
     * over real data, so a cost typed here is a cost a player pays. Unlike the faction editor next
     * door, these rows are editable: a bank has no datapack source to be the authority, so the
     * world store is the only copy and editing it here is editing the thing itself.
     *
     * <p>Every edit writes the whole bank back through {@code XenoNpcStoreWritePacket}. A bank is a
     * handful of numbers, so a read-modify-write of the lot is simpler than a per-field packet and
     * cannot leave two fields disagreeing.
     */
    /**
     * The patrol route: the reference's Moving Path, point by point.
     *
     * <p>Points are placed with the Moving Path tool rather than typed here - a route means
     * walking to each spot, and a screen that demanded coordinates would make that worse. What
     * this page is for is the part the tool cannot do: reordering, deleting, and the mode.
     */
    private List<EditorRow> pathRows() {
        List<EditorRow> rows = new ArrayList<>();
        var path = profile.path;

        rows.add(new EditorRow.Heading("Movement"));
        rows.add(new EditorRow.Cycle("Mode",
                java.util.Arrays.stream(
                        net.bullettrain.xenopixelsmod.npc.path.NpcPath.Mode.values())
                        .map(m -> m.id()).toList(),
                path.mode().ordinal(),
                index -> {
                    var modes = net.bullettrain.xenopixelsmod.npc.path.NpcPath.Mode.values();
                    path.setMode(modes[Math.max(0, Math.min(modes.length - 1, index))]);
                    markDirty("Path");
                    rebuild();
                }));
        // A stepper in tenths, because the navigation speed is a multiplier and 0.1 steps are the
        // difference a player can actually feel.
        rows.add(new EditorRow.Stepper("Speed x10", (int) Math.round(path.speed() * 10), 1, 20,
                value -> {
                    path.setSpeed(value / 10.0);
                    markDirty("Path");
                }));

        rows.add(new EditorRow.Heading("Points (" + path.size() + "/"
                + net.bullettrain.xenopixelsmod.npc.path.NpcPath.MAX_POINTS + ")"));
        if (path.isEmpty()) {
            rows.add(new EditorRow.Text("", "No points yet. Bind the Moving Path Tool to this", MUTED));
            rows.add(new EditorRow.Text("", "NPC, then right-click blocks to lay the route.", MUTED));
        }
        for (int i = 0; i < path.size(); i++) {
            int index = i;
            var point = path.get(i);
            rows.add(new EditorRow.Slot(i + 1, point == null ? "?" : point.toString(),
                    // Pick moves the point up, which is the reference's Up control; the slot row
                    // gives us a number, a wide button and a clear, which is its row exactly.
                    () -> {
                        path.swap(index, index - 1);
                        markDirty("Path");
                        rebuild();
                    },
                    () -> {
                        path.remove(index);
                        markDirty("Path");
                        rebuild();
                    }));
        }
        if (!path.isEmpty()) {
            rows.add(new EditorRow.Text("", "The numbered button moves a point up; X deletes it.",
                    MUTED));
        }
        if (!path.walkable() && !path.isEmpty()) {
            rows.add(new EditorRow.Text("", "One point is a place to stand, not a route.", WARN));
        }
        rows.add(new EditorRow.Spacer());
        rows.add(new EditorRow.Text("", "While patrolling, the leash stands down so the", MUTED));
        rows.add(new EditorRow.Text("", "route may leave the home radius.", MUTED));
        return rows;
    }

    /**
     * The Guard job.
     *
     * <p>Their three toggles. The Available / Current Targets move-list is a per-entity-type
     * allow-list and is not built; that is said in plain text rather than shown as a disabled
     * control nobody can use.
     */
    private List<EditorRow> guardRows() {
        List<EditorRow> rows = new ArrayList<>();
        rows.add(new EditorRow.Toggle("Attack Animals", profile.guardAnimals, v -> {
            profile.guardAnimals = v;
            markDirty("GuardAnimals");
        }));
        rows.add(new EditorRow.Toggle("Attack Monsters", profile.guardMonsters, v -> {
            profile.guardMonsters = v;
            markDirty("GuardMonsters");
        }));
        rows.add(new EditorRow.Toggle("Attack Creepers", profile.guardCreepers, v -> {
            profile.guardCreepers = v;
            markDirty("GuardCreepers");
        }));
        rows.add(new EditorRow.Spacer());
        rows.add(new EditorRow.Action("Movement", "mynpcs_button_row",
                () -> openPage(ScreenId.ADVANCED_PATH)));
        rows.add(new EditorRow.Text("", "A guard with a route patrols it. Players and other", MUTED));
        rows.add(new EditorRow.Text("", "Xeno NPCs are never targeted here - that is the", MUTED));
        rows.add(new EditorRow.Text("", "faction system's decision, not this one's.", MUTED));
        rows.add(new EditorRow.Text("", "Per-type target lists are not built.", MUTED));
        if (net.bullettrain.xenopixelsmod.npc.XenoNpcJob.byId(profile.job)
                != net.bullettrain.xenopixelsmod.npc.XenoNpcJob.GUARD) {
            rows.add(new EditorRow.Spacer());
            rows.add(new EditorRow.Text("", "This NPC's job is not Guard, so none of this", MUTED));
            rows.add(new EditorRow.Text("", "runs. Set it on the Advanced page.", MUTED));
        }
        return rows;
    }

    private List<EditorRow> bankRows() {
        List<EditorRow> rows = new ArrayList<>();
        var all = net.bullettrain.xenopixelsmod.client.npc.bank.ClientBanks.all();

        if (all.isEmpty()) {
            rows.add(new EditorRow.Text("", "No banks yet. Add one below, then point a", MUTED));
            rows.add(new EditorRow.Text("", "Bank NPC at it from Advanced > Edit Role.", MUTED));
        }
        for (var entry : all) {
            boolean showing = entry.id().equals(selectedBank);
            rows.add(new EditorRow.Slot(all.indexOf(entry),
                    (showing ? "> " : "  ") + entry.name(),
                    () -> {
                        selectedBank = entry.id();
                        rebuild();
                    },
                    showing ? () -> {
                        selectedBank = "";
                        rebuild();
                    } : null));
        }

        rows.add(new EditorRow.Heading("New bank"));
        rows.add(new EditorRow.Field("ID", newBankId, XenoNpcStorePaths.MAX_ID,
                v -> {
                    newBankId = v;
                    scheduleRefresh();
                }));
        String refusal = XenoNpcStorePaths.reject(newBankId);
        if (refusal != null && !newBankId.isEmpty()) {
            rows.add(new EditorRow.Text("", capitalise(refusal), WARN));
        }
        rows.add(refusal == null && !newBankId.isEmpty()
                ? new EditorRow.Action("Add", "mynpcs_button_row", this::addBank)
                : disabledAction("Add"));

        var selected = net.bullettrain.xenopixelsmod.client.npc.bank.ClientBanks.get(selectedBank);
        if (selected == null) {
            rows.add(new EditorRow.Text("", "Open a bank above to edit its tabs.", MUTED));
            return rows;
        }

        rows.add(new EditorRow.Heading(selected.name()));
        rows.add(new EditorRow.Stepper("Withdraw Fee %", selected.withdrawFeePercent(), 0,
                net.bullettrain.xenopixelsmod.npc.bank.BankDefinition.MAX_WITHDRAW_FEE_PERCENT,
                value -> writeBank(selected, tag -> tag.putInt("WithdrawFee", value))));

        rows.add(new EditorRow.Heading("Bank Slots"));
        for (int index = 0; index < selected.tabs().size(); index++) {
            var tab = selected.tabs().get(index);
            int slot = index;
            rows.add(new EditorRow.Field("Tab " + (slot + 1) + " Cost", tab.costItem(), 96,
                    v -> writeBank(selected,
                            tag -> tabTag(tag, slot).putString("Cost", v.trim()))));
            rows.add(new EditorRow.Stepper("Tab " + (slot + 1) + " Count", tab.costCount(), 1,
                    net.bullettrain.xenopixelsmod.npc.bank.BankTab.MAX_COST_COUNT,
                    value -> writeBank(selected,
                            tag -> tabTag(tag, slot).putInt("CostCount", value))));
            rows.add(new EditorRow.Toggle("Can Upgrade " + (slot + 1), tab.upgradable(),
                    value -> writeBank(selected,
                            tag -> tabTag(tag, slot).putBoolean("Upgradable", value))));
            rows.add(new EditorRow.Stepper("Tab " + (slot + 1) + " Start", tab.startSlots(),
                    net.bullettrain.xenopixelsmod.npc.bank.BankTab.MIN_SLOTS,
                    net.bullettrain.xenopixelsmod.npc.bank.BankTab.MAX_SLOTS,
                    value -> writeBank(selected, tag -> tabTag(tag, slot).putInt("Start", value))));
            rows.add(new EditorRow.Spacer());
        }

        rows.add(selected.tabs().size()
                < net.bullettrain.xenopixelsmod.npc.bank.BankDefinition.MAX_TABS
                ? new EditorRow.Action("Add Tab", "mynpcs_button_row", () -> addBankTab(selected))
                : disabledAction("Add Tab"));
        rows.add(selected.tabs().size() > 1
                ? new EditorRow.Action("Remove Tab", "mynpcs_button_row",
                        () -> removeBankTab(selected))
                : disabledAction("Remove Tab"));
        rows.add(new EditorRow.Action("Remove " + selected.id(), "mynpcs_button_row",
                () -> removeBank(selected.id())));
        rows.add(new EditorRow.Text("", "Saved under " + XenoNpcWorldStore.ROOT_FOLDER
                + "/banks/ in the world.", MUTED));
        rows.add(new EditorRow.Text("", "Banks are shared. Editing one changes it for", MUTED));
        rows.add(new EditorRow.Text("", "every NPC telling for it.", MUTED));
        return rows;
    }

    /** One tab's tag, growing the list to reach it. */
    private static CompoundTag tabTag(CompoundTag bank, int index) {
        net.minecraft.nbt.ListTag tabs = bank.getList("Tabs", net.minecraft.nbt.Tag.TAG_COMPOUND);
        while (tabs.size() <= index) {
            tabs.add(new CompoundTag());
        }
        bank.put("Tabs", tabs);
        return tabs.getCompound(index);
    }

    /** The client's view of a bank, back as the tag the store holds. */
    private static CompoundTag bankTag(
            net.bullettrain.xenopixelsmod.client.npc.bank.ClientBanks.Entry entry) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", entry.name());
        tag.putInt("WithdrawFee", entry.withdrawFeePercent());
        net.minecraft.nbt.ListTag tabs = new net.minecraft.nbt.ListTag();
        for (var tab : entry.tabs()) {
            CompoundTag one = new CompoundTag();
            one.putString("Name", tab.name());
            if (!tab.costItem().isEmpty()) {
                one.putString("Cost", tab.costItem());
                one.putInt("CostCount", tab.costCount());
            }
            one.putInt("Start", tab.startSlots());
            one.putBoolean("Upgradable", tab.upgradable());
            tabs.add(one);
        }
        tag.put("Tabs", tabs);
        return tag;
    }

    /**
     * Applies one change and writes the whole bank back.
     *
     * <p>The screen does not update itself afterwards: the server answers with a
     * {@code SyncBanksPacket}, and rebuilding from that is what keeps the page showing what was
     * actually stored rather than what was typed.
     */
    private void writeBank(net.bullettrain.xenopixelsmod.client.npc.bank.ClientBanks.Entry entry,
                           java.util.function.Consumer<CompoundTag> change) {
        CompoundTag tag = bankTag(entry);
        change.accept(tag);
        ModNetwork.sendToServer(new XenoNpcStoreWritePacket(
                XenoNpcStoreCategory.BANKS.ordinal(), "", entry.id(), false,
                indexedRevision(XenoNpcStoreCategory.BANKS, entry.id()), tag));
    }

    private void addBank() {
        if (XenoNpcStorePaths.reject(newBankId) != null) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", newBankId);
        tag.putInt("WithdrawFee", 0);
        // One free row to start with, so a bank works the moment it is made. An Add that produced
        // something no player could open would be a second step nobody was told about.
        net.minecraft.nbt.ListTag tabs = new net.minecraft.nbt.ListTag();
        CompoundTag first = new CompoundTag();
        first.putString("Name", "Vault");
        first.putInt("Start", net.bullettrain.xenopixelsmod.npc.bank.BankTab.SLOTS_PER_ROW);
        first.putBoolean("Upgradable", true);
        tabs.add(first);
        tag.put("Tabs", tabs);

        AtlasSound.confirm();
        ModNetwork.sendToServer(new XenoNpcStoreWritePacket(
                XenoNpcStoreCategory.BANKS.ordinal(), "", newBankId, false, 0, tag));
        selectedBank = newBankId;
        newBankId = "";
        rebuild();
    }

    private void addBankTab(net.bullettrain.xenopixelsmod.client.npc.bank.ClientBanks.Entry entry) {
        AtlasSound.confirm();
        writeBank(entry, tag -> {
            CompoundTag added = tabTag(tag, entry.tabs().size());
            added.putString("Name", "Vault " + (entry.tabs().size() + 1));
            added.putInt("Start", net.bullettrain.xenopixelsmod.npc.bank.BankTab.SLOTS_PER_ROW);
            added.putBoolean("Upgradable", true);
        });
    }

    /**
     * Drops the last tab.
     *
     * <p>The last one rather than a chosen one, and never the only one: removing a tab from the
     * middle would renumber every tab after it, and players' accounts are keyed by tab index -
     * their items would appear to move.
     */
    private void removeBankTab(
            net.bullettrain.xenopixelsmod.client.npc.bank.ClientBanks.Entry entry) {
        if (entry.tabs().size() <= 1) {
            return;
        }
        AtlasSound.confirm();
        writeBank(entry, tag -> {
            net.minecraft.nbt.ListTag tabs =
                    tag.getList("Tabs", net.minecraft.nbt.Tag.TAG_COMPOUND);
            if (!tabs.isEmpty()) {
                tabs.remove(tabs.size() - 1);
            }
            tag.put("Tabs", tabs);
        });
    }

    private void removeBank(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        AtlasSound.confirm();
        ModNetwork.sendToServer(new XenoNpcStoreWritePacket(
                XenoNpcStoreCategory.BANKS.ordinal(), "", id, true, 0, null));
        selectedBank = "";
        rebuild();
    }

    /**
     * Which bank this NPC tells for.
     *
     * <p>The same picker shape the transporter uses, for the same reason: the NPC holds a
     * reference and the thing it points at is shared.
     */
    private List<EditorRow> bankSetupRows() {
        List<EditorRow> rows = new ArrayList<>();
        rows.add(new EditorRow.Heading("Bank"));

        var all = net.bullettrain.xenopixelsmod.client.npc.bank.ClientBanks.all();
        String current = profile.bankId;

        if (all.isEmpty()) {
            rows.add(new EditorRow.Text("", "No banks yet. Make one in Global > Banks,", MUTED));
            rows.add(new EditorRow.Text("", "then come back and pick it here.", MUTED));
        }
        for (var entry : all) {
            boolean telling = entry.id().equals(current);
            rows.add(new EditorRow.Slot(all.indexOf(entry),
                    (telling ? "> " : "  ") + entry.name(),
                    () -> {
                        profile.bankId = entry.id();
                        markDirty("BankId");
                        rebuild();
                    },
                    telling ? () -> {
                        profile.bankId = "";
                        markDirty("BankId");
                        rebuild();
                    } : null));
        }
        if (!current.isEmpty() && all.stream().noneMatch(e -> e.id().equals(current))) {
            rows.add(new EditorRow.Text("", "Telling for '" + current + "', which no longer",
                    MUTED));
            rows.add(new EditorRow.Text("", "exists. This NPC will talk instead.", MUTED));
        }
        rows.add(new EditorRow.Spacer());
        rows.add(new EditorRow.Text("", "A player's vault follows the player, not the", MUTED));
        rows.add(new EditorRow.Text("", "NPC. Two tellers on one bank share it.", MUTED));
        return rows;
    }

    /**
     * This NPC's own faction, on the Advanced hub.
     *
     * <p>The reference splits the two: Advanced &gt; Factions assigns the NPC to one, Global &gt;
     * Factions browses the registry. Both pointed at the registry browser here, so there was no way
     * to set an NPC's faction at all - the field was read on save and written back unchanged.
     *
     * <p>A cycler over the loaded factions rather than free text, now that there is a list to
     * cycle. A typo used to leave an NPC in a faction that would never match another.
     */
    private List<EditorRow> npcFactionRows() {
        List<EditorRow> rows = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        ids.add("");
        ids.addAll(ClientFactions.ids());

        String current = currentFaction();
        int index = Math.max(0, ids.indexOf(current == null ? "" : current.trim().toLowerCase(Locale.ROOT)));
        rows.add(cycle("Faction",
                ids.stream().map(id -> id.isEmpty() ? "(none)" : id).toList(),
                index,
                picked -> editedFaction = ids.get(Math.floorMod(picked, ids.size())),
                null));

        ClientFactions.Entry entry = ClientFactions.get(current);
        if (entry != null) {
            rows.add(new EditorRow.Text("Colour",
                    String.format(Locale.ROOT, "#%06X", entry.color()),
                    entry.color() | 0xFF000000));
            rows.add(new EditorRow.Text("Hostile to", entry.hostileTo().isEmpty()
                    ? "nobody" : String.join(", ", entry.hostileTo()), MUTED));
        } else if (current != null && !current.isBlank()) {
            // A label left over from a faction a pack later removed. Says so rather than looking
            // set: an NPC naming an unknown faction is hostile to nobody.
            rows.add(new EditorRow.Text("", "\"" + current + "\" is not a loaded faction.", WARN));
        }

        rows.add(new EditorRow.Text("", "Decides who this NPC attacks on sight, and who", MUTED));
        rows.add(new EditorRow.Text("", "attacks it. Players earn hostility through standing.",
                MUTED));
        rows.add(new EditorRow.Action("Browse factions", "mynpcs_button_row",
                () -> openPage(ScreenId.GLOBAL_FACTIONS)));
        return rows;
    }

    /** Which faction the editor screen is showing. */
    private String selectedFaction = "";

    /** Global faction manager, including the dry-run-first MyNPCs migration entry point. */
    private List<EditorRow> factionListRows() {
        List<EditorRow> rows = new ArrayList<>();
        List<ClientFactions.Entry> all = ClientFactions.all();

        rows.add(new EditorRow.Action("Preview MyNPC factions", "mynpcs_button_row",
                () -> runFactionImport(false)));
        rows.add(new EditorRow.Action("Confirm faction import", "mynpcs_button_row",
                () -> runFactionImport(true)));
        rows.add(new EditorRow.Text("", "Preview is read-only; confirm adds factions that",
                MUTED));
        rows.add(new EditorRow.Text("", "do not already exist in this world's Xeno store.", MUTED));

        if (all.isEmpty()) {
            rows.add(new EditorRow.Text("", "No factions yet.", MUTED));
            rows.add(new EditorRow.Text("", "Add one below, or ship JSON under", MUTED));
            rows.add(new EditorRow.Text("", "data/<pack>/npcs/factions/ and run /reload.", MUTED));
        } else {
            for (ClientFactions.Entry entry : all) {
                rows.add(new EditorRow.Action(entry.name(), "mynpcs_button_row", () -> {
                    selectedFaction = entry.id();
                    loadFactionDraft(entry);
                    openPage(ScreenId.GLOBAL_FACTION_EDITOR);
                }));
            }
        }

        rows.add(new EditorRow.Heading("New faction"));
        rows.add(draftField("ID", newFactionId, XenoNpcStorePaths.MAX_ID,
                v -> newFactionId = v));
        String refusal = XenoNpcStorePaths.reject(newFactionId);
        if (refusal != null && !newFactionId.isEmpty()) {
            // Says what is wrong rather than quietly rewriting the id. An id that silently became
            // something else is an entry the operator then cannot find.
            rows.add(new EditorRow.Text("", capitalise(refusal), WARN));
        }
        if (refusal == null) {
            rows.add(new EditorRow.Action("Add", "mynpcs_button_row", this::addFaction));
        } else {
            rows.add(disabledAction("Add"));
        }
        if (ClientFactions.get(selectedFaction) != null) {
            rows.add(new EditorRow.Action("Remove " + selectedFaction, "mynpcs_button_row",
                    () -> removeFaction(selectedFaction)));
        } else {
            rows.add(disabledAction("Remove"));
            rows.add(new EditorRow.Text("", "Open a faction first to remove it.", MUTED));
        }
        rows.add(new EditorRow.Text("", "Saved under " + XenoNpcWorldStore.ROOT_FOLDER
                + "/factions/ in the world.", MUTED));
        rows.add(new EditorRow.Text("", "A datapack faction of the same id stays as the",
                MUTED));
        rows.add(new EditorRow.Text("", "fallback; removing yours brings it back.", MUTED));
        return rows;
    }

    /** Runs the verified MyNPC factions converter through its server-side command gate. */
    private void runFactionImport(boolean confirm) {
        if (minecraft == null || minecraft.player == null
                || minecraft.player.connection == null) {
            return;
        }
        AtlasSound.confirm();
        minecraft.player.connection.sendCommand("xenonpcimport factions"
                + (confirm ? " confirm" : ""));
    }

    /** The id being typed into Global > Factions. Kept across a page rebuild. */
    private String newFactionId = "";
    private String factionNameDraft = "";
    private String factionColorDraft = "#FFFFFF";
    private String factionStandingDraft = "0";
    private String factionHostileDraft = "";
    private boolean factionAttackedDraft;
    private boolean factionAggressiveDraft;
    private String factionMobDraft = "";

    private void loadFactionDraft(ClientFactions.Entry entry) {
        factionNameDraft = entry.name();
        factionColorDraft = String.format(Locale.ROOT, "#%06X", entry.color());
        factionStandingDraft = Integer.toString(entry.defaultStanding());
        factionHostileDraft = String.join(", ", entry.hostileTo());
        factionAttackedDraft = entry.attackedByMobs();
        factionAggressiveDraft = entry.aggressiveToMobs();
        factionMobDraft = String.join(", ", entry.attackableMobs());
    }

    private static List<String> commaIds(String text) {
        if (text == null || text.isBlank()) return List.of();
        return java.util.Arrays.stream(text.split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank()).distinct().limit(64).toList();
    }

    private void saveFactionDraft() {
        if (selectedFaction.isBlank()) return;
        int standing;
        try {
            standing = Integer.parseInt(factionStandingDraft.trim());
        } catch (NumberFormatException invalid) {
            showNotice("Standing must be a number");
            return;
        }
        List<String> mobs = commaIds(factionMobDraft);
        for (String mob : mobs) {
            if (net.minecraft.resources.ResourceLocation.tryParse(mob) == null) {
                showNotice("Invalid mob id: " + mob);
                return;
            }
        }
        int color = parseHex(factionColorDraft, -1);
        if (color < 0) {
            showNotice("Color must be #RRGGBB");
            return;
        }
        var faction = new net.bullettrain.xenopixelsmod.npc.faction.XenoFaction(
                selectedFaction, factionNameDraft, color, commaIds(factionHostileDraft),
                standing, factionAttackedDraft, factionAggressiveDraft, mobs);
        var indexed = ClientNpcStoreIndex.find(XenoNpcStoreCategory.FACTIONS, "", selectedFaction);
        int revision = indexed == null ? 0 : indexed.revision();
        ModNetwork.sendToServer(new XenoNpcStoreWritePacket(
                XenoNpcStoreCategory.FACTIONS.ordinal(), "", selectedFaction, false, revision,
                net.bullettrain.xenopixelsmod.npc.store.XenoFactionNbt.write(faction)));
        showNotice("Faction save requested");
    }

    /**
     * Writes a new faction into the world store.
     *
     * <p>Only the id and a name to start with. Colour, hostility and starting standing are edited
     * afterwards on the faction's own screen - an Add button that demanded every field before it
     * would do anything is a worse first step than one that makes something to edit.
     */
    private void addFaction() {
        if (XenoNpcStorePaths.reject(newFactionId) != null) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", newFactionId);
        tag.putInt("Color", 0xFFFFFF);
        tag.put("HostileTo", new net.minecraft.nbt.ListTag());
        tag.putInt("DefaultStanding", 0);
        tag.putBoolean("AttackedByMobs", false);

        AtlasSound.confirm();
        // 0: this is meant to be new. If somebody else created that id first the server refuses
        // rather than silently replacing their faction with an empty one.
        ModNetwork.sendToServer(new XenoNpcStoreWritePacket(
                XenoNpcStoreCategory.FACTIONS.ordinal(), "", newFactionId, false, 0, tag));
        selectedFaction = newFactionId;
        newFactionId = "";
        rebuild();
    }

    private void removeFaction(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        AtlasSound.confirm();
        ModNetwork.sendToServer(new XenoNpcStoreWritePacket(
                XenoNpcStoreCategory.FACTIONS.ordinal(), "", id, true, 0, null));
        selectedFaction = "";
        rebuild();
    }

    private static String capitalise(String text) {
        return text == null || text.isEmpty()
                ? "" : Character.toUpperCase(text.charAt(0)) + text.substring(1) + ".";
    }

    /**
     * One faction, as loaded.
     *
     * <p>Every row is informational rather than a disabled control: a disabled field suggests it
     * could become editable here, and it cannot - the JSON is the source.
     */
    private List<EditorRow> factionEditorRows() {
        List<EditorRow> rows = new ArrayList<>();
        ClientFactions.Entry entry = ClientFactions.get(selectedFaction);
        if (entry == null) {
            rows.add(new EditorRow.Text("", "No faction selected.", MUTED));
            return rows;
        }

        rows.add(draftField("Name", factionNameDraft, 128, value -> factionNameDraft = value));
        rows.add(new EditorRow.Text("ID", entry.id(), LIGHT));
        rows.add(colorField("Color", factionColorDraft, value -> {
            factionColorDraft = value;
            scheduleRefresh();
        }));
        rows.add(draftField("Start standing", factionStandingDraft, 6,
                value -> factionStandingDraft = value));

        rows.add(new EditorRow.Heading("Hostile Factions"));
        rows.add(draftField("Hostile IDs", factionHostileDraft, 512,
                value -> factionHostileDraft = value));
        rows.add(new EditorRow.Text("", "Comma-separated faction IDs.", MUTED));
        rows.add(new EditorRow.Heading("Mob targeting"));
        rows.add(new EditorRow.Toggle("Aggressive to mobs", factionAggressiveDraft,
                value -> { factionAggressiveDraft = value; scheduleRefresh(); }));
        rows.add(draftField("Attackable mob IDs", factionMobDraft, 1024,
                value -> factionMobDraft = value));
        rows.add(new EditorRow.Text("", "Exact entity IDs, comma-separated. DMZ masters excluded.", MUTED));
        rows.add(new EditorRow.Action("Save faction", "mynpcs_button_row", this::saveFactionDraft));

        rows.add(new EditorRow.Heading("Standing"));
        rows.add(new EditorRow.Text("", "Hostile at or below "
                + net.bullettrain.xenopixelsmod.npc.faction.XenoFaction.HOSTILE_BELOW
                + ", friendly at "
                + net.bullettrain.xenopixelsmod.npc.faction.XenoFaction.FRIENDLY_AT + ".", MUTED));
        rows.add(new EditorRow.Text("", "Quests move it; /reload does not reset it.", MUTED));
        return rows;
    }

    /**
     * Global &gt; Dialogs: conversations kept in the world, shared by every NPC that names one.
     *
     * <p>Live because something reads them: {@code XenoNpcDataSource.dialogue} resolves a role's
     * dialogue through the world store before falling back to the datapack. That is the whole rule
     * for enabling a control here - it goes live once its value is consumed, and not before.
     *
     * <p>Distinct from Advanced &gt; Dialogs, which edits the conversation belonging to <em>this</em>
     * NPC alone. One is the shared library; the other is this NPC's own script. An NPC's own still
     * wins over both.
     */
    private List<EditorRow> globalDialogRows() {
        List<EditorRow> rows = new ArrayList<>();
        rows.add(new EditorRow.Heading("Dialogs"));
        if (returnToAdvancedDialogs) {
            rows.add(new EditorRow.Text("", "Opened from Advanced > Dialogs.", MUTED));
            rows.add(new EditorRow.Action("Return to Advanced dialogs", "mynpcs_button_row",
                    () -> openPage(ScreenId.ADVANCED_DIALOGS), false));
        }

        List<ClientNpcStoreIndex.Entry> stored = new ArrayList<>(
                ClientNpcStoreIndex.of(XenoNpcStoreCategory.DIALOGS));
        stored.sort(java.util.Comparator.comparing(ClientNpcStoreIndex.Entry::group)
                .thenComparing(ClientNpcStoreIndex.Entry::id));
        if (stored.isEmpty()) {
            rows.add(new EditorRow.Text("", "None stored in this world.", MUTED));
            rows.add(new EditorRow.Text("", "Add one below, or ship JSON under", MUTED));
            rows.add(new EditorRow.Text("", "data/<pack>/npcs/dialogue/ and run /reload.", MUTED));
        }
        String lastGroup = "";
        for (ClientNpcStoreIndex.Entry entry : stored) {
            if (!entry.group().equals(lastGroup)) {
                lastGroup = entry.group();
                rows.add(new EditorRow.Heading("Category: " + lastGroup));
            }
            rows.add(new EditorRow.Action(entry.label(), "mynpcs_button_row", () -> {
                selectedStoreDialog = entry.id();
                selectedStoreDialogGroup = entry.group();
                openPage(ScreenId.GLOBAL_DIALOG_NAME);
            }, true));
        }

        rows.add(new EditorRow.Heading("New dialog"));
        rows.add(draftField("Category", newDialogGroup, XenoNpcStorePaths.MAX_GROUP,
                v -> newDialogGroup = v));
        rows.add(draftField("ID", newDialogId, XenoNpcStorePaths.MAX_ID,
                v -> newDialogId = v));
        String refusal = XenoNpcStorePaths.reject(newDialogId);
        String groupRefusal = XenoNpcStorePaths.rejectGroup(newDialogGroup);
        if (groupRefusal != null) {
            rows.add(new EditorRow.Text("", capitalise(groupRefusal), WARN));
        }
        if (refusal != null && !newDialogId.isEmpty()) {
            rows.add(new EditorRow.Text("", capitalise(refusal), WARN));
        }
        if (refusal == null && groupRefusal == null) {
            rows.add(new EditorRow.Action("Add", "mynpcs_button_row", this::addStoreDialog));
        } else {
            rows.add(disabledAction("Add"));
        }
        rows.add(new EditorRow.Text("", "Creates an empty dialog; open it to edit its lines.", MUTED));
        return rows;
    }

    /** Which stored dialog the detail screen is showing, and the id being typed for a new one. */
    private String selectedStoreDialog = "";
    private String newDialogId = "";
    private String newDialogGroup = XenoNpcDataSource.DEFAULT_GROUP;

    /** Accept only the entry this screen requested; a late response must not replace another draft. */
    public void receiveStoreDialogue(String group, String id, int revision, CompoundTag tag) {
        if (!selectedStoreDialogGroup.equals(group) || !selectedStoreDialog.equals(id)) {
            return;
        }
        if (awaitingStoreDialogueSave && editingStoreDialogue) {
            awaitingStoreDialogueSave = false;
            CompoundTag submitted = submittedStoreDialogue;
            submittedStoreDialogue = null;
            if (tag != null && submitted != null
                    && tag.getString("Start").equals(submitted.getString("Start"))
                    && tag.getList("Nodes", net.minecraft.nbt.Tag.TAG_COMPOUND).equals(
                    submitted.getList("Nodes", net.minecraft.nbt.Tag.TAG_COMPOUND))) {
                storeDialogueRevision = revision;
                storeDialogueDirty = !dialogueDraft.toTag().equals(submitted);
                showNotice("Global dialog saved");
            } else {
                showNotice("Save rejected or dialog changed; your draft is still here");
            }
            rebuild();
            return;
        }
        if (subPage != ScreenId.GLOBAL_DIALOG_NAME) {
            return;
        }
        if (tag == null) {
            showNotice("That dialog no longer exists");
            return;
        }
        dialogueDraft = DialogueDraft.of(tag);
        dialogueNode = -1;
        dialogueOption = -1;
        storeDialogueRevision = revision;
        storeDialogueDirty = false;
        editingStoreDialogue = true;
        openPage(ScreenId.GLOBAL_DIALOG_LINES);
    }

    /** Refresh lists after a server write or another operator changes the world store. */
    public void refreshStoreIndex() {
        rebuild();
    }

    /** Loads one server-stored quest into the existing editor fields, retaining all other JSON. */
    public void receiveStoreQuest(String group, String id, int revision, String definitionJson) {
        if (!questLoadAwaiting || !java.util.Objects.equals(questSelectGroup, group)
                || !java.util.Objects.equals(questSelectId, id)) return;
        questLoadAwaiting = false;
        if (definitionJson == null || definitionJson.isBlank() || revision <= 0) {
            showNotice("Stored quest not found; refresh the quest list");
            return;
        }
        try {
            JsonObject root = com.google.gson.JsonParser.parseString(definitionJson).getAsJsonObject();
            loadedQuestDefinition = root.deepCopy();
            questEditingId = id;
            questId = id;
            questGroup = group;
            storeQuestRevision = revision;
            questTitle = string(root, "title", id);
            questDescription = string(root, "description", "");
            questLogText = string(root, "log_text", "");
            questCategory = string(root, "category", group);

            JsonObject firstObjective = root;
            if (root.has("objectives") && root.get("objectives").isJsonArray()
                    && !root.getAsJsonArray("objectives").isEmpty()
                    && root.getAsJsonArray("objectives").get(0).isJsonObject()) {
                firstObjective = root.getAsJsonArray("objectives").get(0).getAsJsonObject();
            }
            var parsedObjective = net.bullettrain.xenopixelsmod.features.progression.QuestObjective
                    .parse(string(firstObjective, "objective", "kill_mobs"));
            questObjective = parsedObjective == null ? 0 : parsedObjective.ordinal();
            questParameter = string(firstObjective, "parameter", "");
            questTarget = Math.max(1, integer(firstObjective, "target",
                    integer(root, "target", 1)));
            questComplete = string(root, "complete_text", "");
            questRepeat = enumOrdinal(net.bullettrain.xenopixelsmod.features.progression.QuestRepeat
                    .values(), string(root, "repeat", "repeatable"));
            questCompletionPalette = List.of("blue", "gold", "green", "red")
                    .indexOf(string(root, "completion_palette", "blue").toLowerCase(Locale.ROOT));
            if (questCompletionPalette < 0) questCompletionPalette = 0;
            questCompletionFrame = "banner".equalsIgnoreCase(
                    string(root, "completion_frame", "rounded")) ? 1 : 0;

            JsonObject reward = root.has("reward") && root.get("reward").isJsonObject()
                    ? root.getAsJsonObject("reward") : new JsonObject();
            int declaredPoints = integer(reward, "skill_points", 2);
            questSkillPointsEnabled = declaredPoints > 0 && xenoSkillPointsOffered();
            questSkillPoints = declaredPoints > 0 ? declaredPoints : 2;
            List<String> commands = new ArrayList<>();
            if (reward.has("commands") && reward.get("commands").isJsonArray()) {
                for (var command : reward.getAsJsonArray("commands")) {
                    if (command.isJsonPrimitive()) commands.add(command.getAsString());
                }
            }
            questRewardActionsEnabled = !commands.isEmpty();
            questCommand = commands.isEmpty() ? "" : commands.get(0);
            questRandomReward = root.has("random_reward") && root.get("random_reward").getAsBoolean();
            questTargetHunts = root.has("target_hunts") && root.get("target_hunts").getAsBoolean();
            openPage(ScreenId.GLOBAL_QUESTS);
            showNotice("Quest loaded; edit its target quota and save");
        } catch (RuntimeException invalid) {
            loadedQuestDefinition = null;
            questEditingId = "";
            showNotice("Quest could not be loaded: " + invalid.getMessage());
        }
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive()
                ? object.get(key).getAsString() : fallback;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) ? object.get(key).getAsInt() : fallback;
        } catch (RuntimeException invalid) {
            return fallback;
        }
    }

    private static <E extends Enum<E>> int enumOrdinal(E[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].name().equalsIgnoreCase(value)) return i;
        }
        return 0;
    }

    /** Acknowledge a global quest write without rebuilding away the current authoring draft. */
    public boolean receiveStoreWriteResult(boolean saved, String reason) {
        if (!questSaveAwaiting) return false;
        questSaveAwaiting = false;
        showNotice(saved ? "Quest saved" : (reason == null || reason.isBlank()
                ? "Quest save rejected; your draft is still here" : reason));
        return true;
    }

    private void saveStoreDialogue() {
        if (!editingStoreDialogue || !storeDialogueDirty) {
            showNotice("No global dialog changes to save");
            return;
        }
        if (awaitingStoreDialogueSave) {
            showNotice("Waiting for the server's reply");
            return;
        }
        if (!dialogueDraft.problems().isEmpty()) {
            showNotice(dialogueDraft.problems().get(0));
            return;
        }
        CompoundTag tag = dialogueDraft.toTag();
        submittedStoreDialogue = tag.copy();
        tag.putString("Name", selectedStoreDialog);
        ModNetwork.sendToServer(new XenoNpcStoreWritePacket(
                XenoNpcStoreCategory.DIALOGS.ordinal(), selectedStoreDialogGroup,
                selectedStoreDialog, false, storeDialogueRevision, tag));
        awaitingStoreDialogueSave = true;
        ModNetwork.sendToServer(new net.bullettrain.xenopixelsmod.network.packet
                .RequestNpcStoreDialoguePacket(selectedStoreDialogGroup, selectedStoreDialog));
        // Wait for an updated index or a new fetch before claiming a successful save. The server
        // can reject a stale revision, and keeping this draft preserves the operator's work.
        showNotice("Saving global dialog; check server reply");
    }

    /**
     * Writes an empty dialogue into the world store.
     *
     * <p>Empty on purpose. {@code XenoDialogueNbt.read} answers null for a dialogue with no usable
     * start node, and every caller treats null as "nothing here" and falls through - so a freshly
     * added one shadows nothing until it has something to say. An Add that immediately silenced
     * every NPC of a role would be a worse first step than one that makes an empty shell to fill.
     */
    private void addStoreDialog() {
        if (XenoNpcStorePaths.reject(newDialogId) != null
                || XenoNpcStorePaths.rejectGroup(newDialogGroup) != null) {
            return;
        }
        CompoundTag tag = XenoDialogueNbt.empty();
        tag.putString("Name", newDialogId);

        AtlasSound.confirm();
        ModNetwork.sendToServer(new XenoNpcStoreWritePacket(
                XenoNpcStoreCategory.DIALOGS.ordinal(), newDialogGroup,
                newDialogId, false, 0, tag));
        selectedStoreDialog = newDialogId;
        selectedStoreDialogGroup = newDialogGroup;
        newDialogId = "";
        rebuild();
    }

    private void removeStoreDialog(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        AtlasSound.confirm();
        ModNetwork.sendToServer(new XenoNpcStoreWritePacket(
                XenoNpcStoreCategory.DIALOGS.ordinal(), selectedStoreDialogGroup,
                id, true, 0, null));
        selectedStoreDialog = "";
        openPage(ScreenId.GLOBAL_DIALOGS);
    }

    /**
     * Global &gt; Dialogs &gt; one stored dialog.
     *
     * <p>The index names the entry; Edit requests its complete tree from the server. The shared
     * draft saves to the world store so every NPC assigned to it sees the same changes.
     */
    private List<EditorRow> dialogNameRows() {
        ClientNpcStoreIndex.Entry entry = ClientNpcStoreIndex.find(
                XenoNpcStoreCategory.DIALOGS, selectedStoreDialogGroup, selectedStoreDialog);
        if (entry == null) {
            return List.of(new EditorRow.Heading("Dialog"),
                    new EditorRow.Text("", "That dialog has been removed.", MUTED),
                    new EditorRow.Action("Back", "mynpcs_button_row",
                            () -> openPage(ScreenId.GLOBAL_DIALOGS)));
        }

        List<EditorRow> rows = new ArrayList<>();
        rows.add(new EditorRow.Heading("Dialog"));
        rows.add(new EditorRow.Text("Name", entry.label(), LIGHT));
        rows.add(new EditorRow.Text("ID", entry.id(), LIGHT));
        rows.add(new EditorRow.Text("", "Stored in this world, not in a datapack.", MUTED));
        rows.add(new EditorRow.Text("", "An NPC with a dialog of its own still uses that one.",
                MUTED));

        rows.add(new EditorRow.Heading("Lines"));
        rows.add(new EditorRow.Action("Edit lines and answers", "mynpcs_button_row", () -> {
            ModNetwork.sendToServer(new net.bullettrain.xenopixelsmod.network.packet
                    .RequestNpcStoreDialoguePacket(selectedStoreDialogGroup, selectedStoreDialog));
            showNotice("Loading dialog from server");
        }));
        rows.add(new EditorRow.Text("", "Assign this dialog under Advanced > Dialogs,", MUTED));
        rows.add(new EditorRow.Text("", "then right-click the NPC to try it.", MUTED));

        rows.add(new EditorRow.Heading("Remove"));
        rows.add(new EditorRow.Action("Remove " + entry.id(), "mynpcs_button_row",
                () -> removeStoreDialog(entry.id())));
        rows.add(new EditorRow.Text("", "A datapack dialog of the same id stays as the", MUTED));
        rows.add(new EditorRow.Text("", "fallback; removing this brings it back.", MUTED));
        return rows;
    }


    private List<EditorRow> playerDataRows() {
        List<EditorRow> rows = new ArrayList<>();
        rows.add(new EditorRow.Heading("Player data"));
        rows.add(new EditorRow.Text("Owner", "XenoPlayerData", LIGHT));
        rows.add(new EditorRow.Text("Quests", "Progress and completion are managed by /xenoquest and dialogue.", MUTED));
        rows.add(new EditorRow.Text("Factions", "Standing is managed by the server-side progression owner.", MUTED));
        rows.add(new EditorRow.Text("Transport", "Unlocked destinations are managed by TransportNetworks.", MUTED));
        rows.add(new EditorRow.Text("Bank", "Vault contents are per-player and not world-store entries.", MUTED));
        rows.add(new EditorRow.Text("Admin changes", "Use the authoritative commands; this NPC editor does not impersonate a player.", WARN));
        return rows;
    }

    private List<EditorRow> globalQuestRows() {
        List<EditorRow> rows = new ArrayList<>(questCatalogRows(
                net.bullettrain.xenopixelsmod.features.progression.XenoQuests.all()));
        var stored = ClientNpcStoreIndex.of(XenoNpcStoreCategory.QUESTS);
        List<ClientNpcStoreIndex.Entry> editable = stored.stream()
                .filter(entry -> entry.revision() > 0).toList();
        if (!editable.isEmpty()) {
            rows.add(new EditorRow.Heading("Edit stored quest"));
            List<String> choices = new ArrayList<>();
            choices.add("Choose a stored quest...");
            for (ClientNpcStoreIndex.Entry entry : editable) {
                choices.add(entry.label() + " [" + entry.group() + "/" + entry.id() + "]");
            }
            int selected = 0;
            for (int i = 0; i < editable.size(); i++) {
                ClientNpcStoreIndex.Entry entry = editable.get(i);
                if (entry.group().equals(questSelectGroup) && entry.id().equals(questSelectId)) {
                    selected = i + 1;
                    break;
                }
            }
            rows.add(new EditorRow.Cycle("Existing quest", choices, selected, index -> {
                int choice = Math.floorMod(index, choices.size());
                if (choice == 0) {
                    questSelectGroup = "";
                    questSelectId = "";
                } else {
                    ClientNpcStoreIndex.Entry entry = editable.get(choice - 1);
                    questSelectGroup = entry.group();
                    questSelectId = entry.id();
                }
                rebuild();
            }));
            rows.add(new EditorRow.Action("Load selected quest", "mynpcs_button_row",
                    this::loadSelectedStoreQuest));
        }
        if (!stored.isEmpty()) {
            rows.add(new EditorRow.Heading("Stored quests"));
        }
        for (var entry : stored) {
            rows.add(new EditorRow.Action("Remove " + entry.id(), "mynpcs_button_row",
                    () -> ModNetwork.sendToServer(new XenoNpcStoreWritePacket(
                            XenoNpcStoreCategory.QUESTS.ordinal(), entry.group(),
                            entry.id(), true, 0, null))));
        }
        rows.add(new EditorRow.Action("New quest", "mynpcs_button_row", this::newStoreQuest));
        rows.add(new EditorRow.Heading(questEditingId.isBlank() ? "Create" : "Edit quest"));
        if (questEditingId.isBlank()) {
            rows.add(new EditorRow.Field("Group", questGroup, 64, value -> questGroup = value));
            rows.add(new EditorRow.Field("Id", questId, 64, value -> questId = value));
        } else {
            rows.add(new EditorRow.Text("Group", questGroup, LIGHT));
            rows.add(new EditorRow.Text("Id", questEditingId, LIGHT));
        }
        rows.add(new EditorRow.Field("Title", questTitle, 128, value -> questTitle = value));
        rows.add(new EditorRow.Field("Description", questDescription, 256,
                value -> questDescription = value));
        rows.add(new EditorRow.Field("Journal text", questLogText, 256,
                value -> questLogText = value));
        rows.add(new EditorRow.Field("Category", questCategory, 64,
                value -> questCategory = value));
        List<String> objectives = new ArrayList<>();
        for (var objective : net.bullettrain.xenopixelsmod.features.progression.QuestObjective.values()) {
            objectives.add(objective.name().toLowerCase(Locale.ROOT));
        }
        int objectiveIndex = Math.floorMod(questObjective, objectives.size());
        rows.add(new EditorRow.Cycle("Objective", objectives, objectiveIndex, index -> {
            questObjective = Math.floorMod(index, objectives.size());
            rebuild();
        }));
        var objective = net.bullettrain.xenopixelsmod.features.progression.QuestObjective
                .values()[objectiveIndex];
        if (objective == net.bullettrain.xenopixelsmod.features.progression.QuestObjective.KILL_TYPE) {
            List<String> entityTypes = new ArrayList<>(net.minecraft.core.registries.BuiltInRegistries
                    .ENTITY_TYPE.keySet().stream().map(Object::toString).sorted().toList());
            if (!questParameter.isBlank() && !entityTypes.contains(questParameter)) {
                entityTypes.add(0, questParameter);
            }
            if (entityTypes.isEmpty()) {
                rows.add(new EditorRow.Text("Target type", "No entity types registered.", MUTED));
            } else {
                List<String> choices = new ArrayList<>();
                choices.add("Choose target type...");
                choices.addAll(entityTypes);
                int selectedType = questParameter.isBlank() ? 0 : entityTypes.indexOf(questParameter) + 1;
                rows.add(new EditorRow.Cycle("Target type", choices, selectedType, index -> {
                    int selected = Math.floorMod(index, choices.size());
                    questParameter = selected == 0 ? "" : entityTypes.get(selected - 1);
                }));
                rows.add(new EditorRow.Text("", "Choose the registered entity type to count.", MUTED));
            }
        } else if (objective == net.bullettrain.xenopixelsmod.features.progression.QuestObjective.KILL_NPC) {
            List<String> npcNames = new ArrayList<>();
            if (Minecraft.getInstance().level != null) {
                for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
                    String typeId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                            .getKey(entity.getType()).toString();
                    if (net.bullettrain.xenopixelsmod.features.progression.QuestNpcTarget.family(
                            entity.getClass().getName(), typeId) == null) continue;
                    String name = entity instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity npc
                            ? npc.npcData().displayName() : entity.getName().getString();
                    if (!name.isBlank() && !npcNames.contains(name)) npcNames.add(name);
                }
            }
            npcNames.sort(String.CASE_INSENSITIVE_ORDER);
            if (!questParameter.isBlank() && !npcNames.contains(questParameter)) {
                npcNames.add(0, questParameter);
            }
            if (!npcNames.isEmpty() || !questParameter.isBlank()) {
                List<String> choices = new ArrayList<>();
                choices.add("Choose NPC...");
                choices.addAll(npcNames);
                int selectedNpc = questParameter.isBlank() ? 0 : npcNames.indexOf(questParameter) + 1;
                rows.add(new EditorRow.Cycle("NPC target", choices,
                        selectedNpc, index -> {
                    int selected = Math.floorMod(index, choices.size());
                    questParameter = selected == 0 ? "" : npcNames.get(selected - 1);
                    rebuild();
                }));
            } else {
                rows.add(new EditorRow.Text("NPC target", "No supported NPCs loaded; enter a name below.", MUTED));
            }
            rows.add(new EditorRow.Field("Exact visible name", questParameter, 256,
                    value -> questParameter = value));
            rows.add(new EditorRow.Text("", "Matches the full visible name: Xeno, MyNPCs, or CustomNPCs.", MUTED));
        } else {
            rows.add(new EditorRow.Field("Parameter", questParameter, 128,
                    value -> questParameter = value));
        }
        rows.add(new EditorRow.Field("Target quota", String.valueOf(questTarget), 8, value -> {
            try {
                questTarget = Math.max(1, Integer.parseInt(value.trim()));
            } catch (NumberFormatException ignored) {
                questTarget = 1;
            }
        }));
        rows.add(new EditorRow.Field("Complete text", questComplete, 256, value -> questComplete = value));
        var repeatRules = java.util.Arrays.stream(net.bullettrain.xenopixelsmod.features.progression.QuestRepeat.values())
                .map(rule -> switch (rule) {
                    case NONE -> "No";
                    case REPEATABLE -> "Yes";
                    case MCDAILY -> "MC daily";
                    case MCWEEKLY -> "MC weekly";
                    case RLDAILY -> "RL daily";
                    case RLWEEKLY -> "RL weekly";
                }).toList();
        rows.add(new EditorRow.Cycle("Repeat", repeatRules,
                Math.floorMod(questRepeat, repeatRules.size()), index -> {
            questRepeat = Math.floorMod(index, repeatRules.size());
            rebuild();
        }));
        if (xenoSkillPointsOffered()) {
            rows.add(new EditorRow.Toggle("Give XenoSkill points (" + questSkillPoints + ")",
                    questSkillPointsEnabled,
                    value -> questSkillPointsEnabled = value));
        }
        rows.add(new EditorRow.Toggle("Enable reward actions", questRewardActionsEnabled,
                value -> questRewardActionsEnabled = value));
        rows.add(new EditorRow.Toggle("Random reward (pay one item)", questRandomReward,
                value -> questRandomReward = value));
        rows.add(new EditorRow.Toggle("Kill target hunts player", questTargetHunts,
                value -> questTargetHunts = value));
        rows.add(new EditorRow.Text("", "Kill NPC only: that NPC attacks the player from quest"
                + " start until the quest ends.", MUTED));
        rows.add(new EditorRow.Field("Reward command", questCommand, 256, value -> questCommand = value));
        rows.add(new EditorRow.Text("", "Commands run only when reward actions are enabled.", MUTED));
        List<String> completionPalettes = List.of("Blue", "Gold", "Green", "Red");
        rows.add(new EditorRow.Cycle("Completion colour", completionPalettes,
                Math.floorMod(questCompletionPalette, completionPalettes.size()), index -> {
            questCompletionPalette = Math.floorMod(index, completionPalettes.size());
            rebuild();
        }));
        List<String> completionFrames = List.of("Rounded", "Banner");
        rows.add(new EditorRow.Cycle("Completion frame", completionFrames,
                Math.floorMod(questCompletionFrame, completionFrames.size()), index -> {
            questCompletionFrame = Math.floorMod(index, completionFrames.size());
            rebuild();
        }));
        rows.add(new EditorRow.Action("Save quest", "mynpcs_button_row", this::saveStoreQuest));
        return rows;
    }

    private void loadSelectedStoreQuest() {
        if (questSelectGroup.isBlank() || questSelectId.isBlank()) {
            showNotice("Choose a stored quest first");
            return;
        }
        if (questLoadAwaiting) {
            showNotice("Waiting for the quest definition");
            return;
        }
        questLoadAwaiting = true;
        ModNetwork.sendToServer(new net.bullettrain.xenopixelsmod.network.packet
                .RequestNpcStoreQuestPacket(questSelectGroup, questSelectId));
        showNotice("Loading quest from server");
    }

    private void newStoreQuest() {
        if (questSaveAwaiting || questLoadAwaiting) {
            showNotice("Wait for the current quest request to finish");
            return;
        }
        loadedQuestDefinition = null;
        questEditingId = "";
        storeQuestRevision = 0;
        questId = "";
        questTitle = "";
        questDescription = "";
        questLogText = "";
        questCategory = questGroup;
        questParameter = "";
        questCommand = "";
        questComplete = "";
        questObjective = 0;
        questRepeat = 0;
        questSkillPointsEnabled = xenoSkillPointsOffered();
        questSkillPoints = 2;
        questRewardActionsEnabled = false;
        questRandomReward = false;
        questTargetHunts = false;
        questCompletionPalette = 0;
        questCompletionFrame = 0;
        questTarget = 1;
        rebuild();
        showNotice("New quest draft");
    }

    private void saveStoreQuest() {
        if (questSaveAwaiting) {
            showNotice("Waiting for the server's reply");
            return;
        }
        if (XenoNpcStorePaths.reject(questId) != null || XenoNpcStorePaths.rejectGroup(questGroup) != null) {
            showNotice("Quest id or group is not valid");
            return;
        }
        if (!questEditingId.isBlank() && !questEditingId.equals(questId)) {
            showNotice("An edited quest keeps its existing id");
            return;
        }
        var objective = net.bullettrain.xenopixelsmod.features.progression.QuestObjective.values()
                [Math.floorMod(questObjective,
                        net.bullettrain.xenopixelsmod.features.progression.QuestObjective.values().length)];
        com.google.gson.JsonObject root = loadedQuestDefinition == null
                ? new com.google.gson.JsonObject() : loadedQuestDefinition.deepCopy();
        root.addProperty("title", questTitle.isBlank() ? questId : questTitle);
        root.addProperty("description", questDescription);
        root.addProperty("log_text", questLogText);
        root.addProperty("target", questTarget);
        root.addProperty("objective", objective.name().toLowerCase(Locale.ROOT));
        root.addProperty("parameter", questParameter);
        root.addProperty("category", questCategory.isBlank() ? questGroup : questCategory);
        if (root.has("objectives") && root.get("objectives").isJsonArray()
                && !root.getAsJsonArray("objectives").isEmpty()
                && root.getAsJsonArray("objectives").get(0).isJsonObject()) {
            JsonObject first = root.getAsJsonArray("objectives").get(0).getAsJsonObject();
            first.addProperty("objective", objective.name().toLowerCase(Locale.ROOT));
            first.addProperty("parameter", questParameter);
            first.addProperty("target", questTarget);
        }
        root.addProperty("complete_text", questComplete);
        root.addProperty("repeat", net.bullettrain.xenopixelsmod.features.progression.QuestRepeat.values()
                [Math.floorMod(questRepeat, net.bullettrain.xenopixelsmod.features.progression.QuestRepeat.values().length)].name());
        root.addProperty("completion_palette", List.of("blue", "gold", "green", "red")
                .get(Math.floorMod(questCompletionPalette, 4)));
        root.addProperty("completion_frame", List.of("rounded", "banner")
                .get(Math.floorMod(questCompletionFrame, 2)));
        root.addProperty("random_reward", questRandomReward);
        root.addProperty("target_hunts", questTargetHunts);
        com.google.gson.JsonObject reward = root.has("reward") && root.get("reward").isJsonObject()
                ? root.getAsJsonObject("reward").deepCopy() : new com.google.gson.JsonObject();
        com.google.gson.JsonArray commands = new com.google.gson.JsonArray();
        if (questRewardActionsEnabled && !questCommand.isBlank()) commands.add(questCommand);
        // An edited quest keeps the count it declared; only the toggle decides zero.
        reward.addProperty("skill_points",
                questSkillPointsEnabled && xenoSkillPointsOffered() ? questSkillPoints : 0);
        reward.add("commands", commands);
        root.add("reward", reward);
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", questTitle.isBlank() ? questId : questTitle);
        tag.putString("DefinitionJson", root.toString());
        ClientNpcStoreIndex.Entry existing = ClientNpcStoreIndex.find(
                XenoNpcStoreCategory.QUESTS, questGroup, questId);
        int expectedRevision = existing != null && existing.revision() > 0
                ? existing.revision() : storeQuestRevision;
        ModNetwork.sendToServer(new XenoNpcStoreWritePacket(
                XenoNpcStoreCategory.QUESTS.ordinal(), questGroup, questId, false,
                expectedRevision, tag));
        questSaveAwaiting = true;
        showNotice("Saving quest; waiting for the server");
    }

    /**
     * Global quest catalog.
     *
     * <p>Imported ids such as {@code mynpcs_the_saiyan_defeat_vegeta} are wider than one editor
     * column. A labelled row draws that id in the label gutter and its title beside it, so the two
     * strings, plus the Goal row paired into the other column, paint on top of each other. Blank
     * labels are full-width lines, which is the only placement that keeps one quest on its own line.
     */
    static List<EditorRow> questCatalogRows(
            java.util.Map<String, net.bullettrain.xenopixelsmod.features.progression.ParallelQuests.QuestDef> quests) {
        List<EditorRow> rows = new ArrayList<>();
        rows.add(new EditorRow.Heading("Quests"));
        if (quests == null || quests.isEmpty()) {
            rows.add(new EditorRow.Text("", "No pack or world-store quests are loaded.", MUTED));
        } else {
            for (var quest : quests.values()) {
                if (quest == null) {
                    continue;
                }
                String title = quest.title() == null || quest.title().isBlank()
                        ? quest.id() : quest.title();
                rows.add(new EditorRow.Text("", title, LIGHT));
                rows.add(new EditorRow.Text("", quest.id() + "   "
                        + quest.goal().type().name().toLowerCase(Locale.ROOT)
                        + " / " + quest.target() + "   "
                        + quest.completionMode().name().toLowerCase(Locale.ROOT), MUTED));
            }
        }
        rows.add(new EditorRow.Text("", "Definitions come from datapacks or the server store.", MUTED));
        rows.add(new EditorRow.Text("", "Offer one with a dialogue option of type QUEST.", MUTED));
        return rows;
    }

    /**
     * The Global &gt; Linked page.
     *
     * <p>Deliberately a pointer rather than a second editor. Links are a fact about one NPC today:
     * {@code LinkedNpcs} lives on the profile and {@code XenoNpcLinkPropagation} reads it when that
     * profile is saved. The {@code linked/} store category is still reserved with no reader, so a
     * "shared template" list authored here would be written to disk and read by nothing — which is
     * the exact shape of a fake feature. What this page can honestly offer is the live per-NPC
     * editor, so that is what its button opens.
     */
    private List<EditorRow> globalLinkedRows() {
        List<EditorRow> rows = new ArrayList<>();
        rows.add(new EditorRow.Heading("Linked NPCs"));
        rows.add(new EditorRow.Action("Edit This NPC's Links", "mynpcs_button_row",
                () -> openPage(ScreenId.ADVANCED_LINKED)));
        rows.add(new EditorRow.Text("", "Per-NPC links are live: a save copies the", LIGHT));
        rows.add(new EditorRow.Text("", "accepted edits to every NPC listed there.", LIGHT));
        rows.add(new EditorRow.Text("", "Shared linked templates have no server reader,", MUTED));
        rows.add(new EditorRow.Text("", "so the store keeps that category reserved and", MUTED));
        rows.add(new EditorRow.Text("", "this page offers no authoring for it.", MUTED));
        return rows;
    }

    /** The spawn rule id being typed into Global &gt; Natural Spawns. Kept across a rebuild. */
    private String newSpawnId = "";

    /** Which stored rule the editor below is showing. */
    private String selectedSpawn = "";

    /** Index into the clone list for the rule being drafted. */
    private int spawnCloneIndex;

    /** Biome id being typed into the selected rule. */
    private String spawnBiomeDraft = "";

    /**
     * The natural spawn rules, and the only place they can be authored.
     *
     * <p>Shaped like the Banks page next door because it is the same kind of thing: shared
     * world-store content that the server owns, that arrives as a mirror, and that is edited by
     * writing the whole entry back. Nothing here is local state - after every write the page shows
     * what the server stored, because the server answers with a {@code SyncNaturalSpawnsPacket}.
     *
     * <p>A rule points at a saved clone rather than carrying an NPC, so the clone library is the
     * picker and a rule cannot be added at all until one clone exists. That is honest: a rule with
     * nothing to place would spawn nothing forever.
     */
    private List<EditorRow> naturalSpawnRows() {
        List<EditorRow> rows = new ArrayList<>();
        var rules = net.bullettrain.xenopixelsmod.client.npc.spawn.ClientNaturalSpawns.all();
        if (rules.isEmpty()) {
            rows.add(new EditorRow.Text("", "No spawn rules yet. Add one below.", MUTED));
        }
        for (int index = 0; index < rules.size(); index++) {
            var rule = rules.get(index);
            boolean showing = rule.id().equals(selectedSpawn);
            rows.add(new EditorRow.Slot(index, (showing ? "> " : "  ") + rule.title(),
                    () -> {
                        selectedSpawn = rule.id();
                        rebuild();
                    },
                    showing ? () -> {
                        selectedSpawn = "";
                        rebuild();
                    } : null));
        }

        var clones = ClientNpcStoreIndex.of(XenoNpcStoreCategory.CLONES);
        rows.add(spacer());
        rows.add(new EditorRow.Heading("New rule"));
        rows.add(new EditorRow.Field("ID", newSpawnId, XenoNpcStorePaths.MAX_ID, v -> {
            newSpawnId = v;
            scheduleRefresh();
        }));
        String refusal = XenoNpcStorePaths.reject(newSpawnId);
        if (refusal != null && !newSpawnId.isEmpty()) {
            rows.add(new EditorRow.Text("", capitalise(refusal), WARN));
        }
        rows.add(new EditorRow.Cycle("Select NPC", spawnCloneLabels(clones),
                Math.min(spawnCloneIndex, Math.max(0, clones.size() - 1)), v -> {
                    spawnCloneIndex = v;
                    scheduleRefresh();
                }));
        boolean canAdd = refusal == null && !newSpawnId.isEmpty() && !clones.isEmpty();
        rows.add(canAdd
                ? new EditorRow.Action("Add", "mynpcs_button_row", this::addNaturalSpawn)
                : disabledAction("Add"));
        if (clones.isEmpty()) {
            rows.add(new EditorRow.Text("", "Save an NPC as a clone first - a rule", MUTED));
            rows.add(new EditorRow.Text("", "places a saved clone, never a blank NPC.", MUTED));
        }

        var selected = net.bullettrain.xenopixelsmod.client.npc.spawn.ClientNaturalSpawns
                .get(selectedSpawn);
        if (selected == null) {
            rows.add(new EditorRow.Text("", "Open a rule above to edit where it spawns.", MUTED));
            return rows;
        }

        rows.add(spacer());
        rows.add(new EditorRow.Heading(selected.title()));
        rows.add(new EditorRow.Field("Title", selected.title(), 64,
                v -> writeNaturalSpawn(selected, tag -> tag.putString("Name", v.trim()))));
        rows.add(new EditorRow.Stepper("Weighted Chance", selected.weight(),
                net.bullettrain.xenopixelsmod.npc.spawn.NpcNaturalSpawn.MIN_WEIGHT,
                net.bullettrain.xenopixelsmod.npc.spawn.NpcNaturalSpawn.MAX_WEIGHT,
                v -> writeNaturalSpawn(selected, tag -> tag.putInt("Weight", v))));
        rows.add(new EditorRow.Cycle("Type",
                net.bullettrain.xenopixelsmod.npc.spawn.NpcNaturalSpawn.TIMES,
                timeIndex(selected.time()),
                v -> writeNaturalSpawn(selected, tag -> tag.putString("Time",
                        net.bullettrain.xenopixelsmod.npc.spawn.NpcNaturalSpawn.TIMES.get(v)))));
        if (!clones.isEmpty()) {
            rows.add(new EditorRow.Cycle("Clone", spawnCloneLabels(clones),
                    spawnCloneIndexOf(clones, selected.cloneTab(), selected.cloneId()), v -> {
                        var pick = clones.get(v);
                        writeNaturalSpawn(selected, tag -> {
                            tag.putInt("CloneTab", spawnCloneTab(pick));
                            tag.putString("CloneId", pick.id());
                        });
                    }));
        }

        rows.add(new EditorRow.Heading("Biomes"));
        if (selected.biomes().isEmpty()) {
            rows.add(new EditorRow.Text("", "None listed: this rule spawns in any", MUTED));
            rows.add(new EditorRow.Text("", "biome the player walks into.", MUTED));
        }
        for (String biome : selected.biomes()) {
            List<String> without = new ArrayList<>(selected.biomes());
            without.remove(biome);
            rows.add(new EditorRow.Action("Remove " + biome, "mynpcs_button_row",
                    () -> writeNaturalSpawn(selected, tag -> putSpawnBiomes(tag, without))));
        }
        rows.add(new EditorRow.Field("Biome", spawnBiomeDraft, 128, v -> {
            spawnBiomeDraft = v;
            scheduleRefresh();
        }));
        boolean biomeDraftOk = spawnBiomeDraft != null && spawnBiomeDraft.contains(":");
        rows.add(biomeDraftOk
                ? new EditorRow.Action("Add Biome", "mynpcs_button_row", () -> {
                    List<String> added = new ArrayList<>(selected.biomes());
                    if (!added.contains(spawnBiomeDraft.trim().toLowerCase(Locale.ROOT))) {
                        added.add(spawnBiomeDraft.trim().toLowerCase(Locale.ROOT));
                    }
                    spawnBiomeDraft = "";
                    writeNaturalSpawn(selected, tag -> putSpawnBiomes(tag, added));
                })
                : disabledAction("Add Biome"));
        rows.add(new EditorRow.Action("Biome +Here", "mynpcs_button_row", () -> {
            String here = spawnBiomeHere();
            if (here.isEmpty()) {
                showNotice("No biome here yet");
                return;
            }
            List<String> added = new ArrayList<>(selected.biomes());
            if (!added.contains(here)) {
                added.add(here);
            }
            writeNaturalSpawn(selected, tag -> putSpawnBiomes(tag, added));
        }));
        if (!selected.biomes().isEmpty()) {
            rows.add(new EditorRow.Action("Clear Biomes", "mynpcs_button_row",
                    () -> writeNaturalSpawn(selected,
                            tag -> putSpawnBiomes(tag, List.of()))));
        }

        rows.add(new EditorRow.Action("Remove " + selected.id(), "mynpcs_button_row",
                () -> removeNaturalSpawn(selected.id())));
        rows.add(new EditorRow.Text("", "Saved under " + XenoNpcWorldStore.ROOT_FOLDER
                + "/spawns/ in the world.", MUTED));
        rows.add(new EditorRow.Text("", "Rules are shared, and place the saved clone -", MUTED));
        rows.add(new EditorRow.Text("", "edit that clone and every rule follows.", MUTED));
        return rows;
    }

    /** The clone list as picker labels, never empty so the cycle always has something to show. */
    private static List<String> spawnCloneLabels(List<ClientNpcStoreIndex.Entry> clones) {
        List<String> out = new ArrayList<>();
        for (var entry : clones) {
            out.add(entry.group() + "/" + entry.id());
        }
        if (out.isEmpty()) {
            out.add("(no clones saved)");
        }
        return out;
    }

    /** Which picker row a stored clone reference sits on. */
    private static int spawnCloneIndexOf(List<ClientNpcStoreIndex.Entry> clones, int tab,
                                         String id) {
        String group = String.valueOf(tab);
        for (int i = 0; i < clones.size(); i++) {
            if (clones.get(i).group().equals(group) && clones.get(i).id().equals(id)) {
                return i;
            }
        }
        return 0;
    }

    /** A clone's tab is its group folder name; anything unparseable is the first tab. */
    private static int spawnCloneTab(ClientNpcStoreIndex.Entry entry) {
        try {
            return net.bullettrain.xenopixelsmod.npc.store.XenoNpcClones
                    .clampTab(Integer.parseInt(entry.group().trim()));
        } catch (NumberFormatException notATab) {
            return net.bullettrain.xenopixelsmod.npc.store.XenoNpcClones.MIN_TAB;
        }
    }

    private static int timeIndex(String time) {
        int at = net.bullettrain.xenopixelsmod.npc.spawn.NpcNaturalSpawn.TIMES.indexOf(time);
        return Math.max(0, at);
    }

    /** Writes the biome list of a rule, which is the whole list each time. */
    private static void putSpawnBiomes(CompoundTag tag, List<String> biomes) {
        ListTag list = new ListTag();
        for (String biome : biomes) {
            list.add(net.minecraft.nbt.StringTag.valueOf(biome));
        }
        tag.put("Biomes", list);
    }

    /** The biome the player holding this screen is standing in, or empty when there is none. */
    private String spawnBiomeHere() {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return "";
        }
        return minecraft.level.getBiome(minecraft.player.blockPosition()).getRegisteredName()
                .toLowerCase(Locale.ROOT);
    }

    /**
     * Applies one change and writes the whole rule back.
     *
     * <p>The revision comes from the store index the server broadcasts, not from the mirror's own
     * copy of the rule, because the store refuses a write whose expected revision is stale: sending
     * 0 for a rule that already exists would mean the first edit of a rule worked and every edit
     * after it silently did not.
     *
     * <p>The page does not update itself from the change: the server answers with a
     * {@code SyncNaturalSpawnsPacket}, so what is listed afterwards is what was actually stored -
     * including the reason a rejected payload left the old rule in place.
     */
    private void writeNaturalSpawn(
            net.bullettrain.xenopixelsmod.npc.spawn.NpcNaturalSpawn rule,
            java.util.function.Consumer<CompoundTag> change) {
        CompoundTag tag = rule.save();
        change.accept(tag);
        ModNetwork.sendToServer(new XenoNpcStoreWritePacket(
                XenoNpcStoreCategory.SPAWNS.ordinal(), "", rule.id(), false,
                spawnRevision(rule.id()), tag));
    }

    /** What the server says this rule is at; 0 when it does not exist yet. */
    private static int spawnRevision(String id) {
        var indexed = ClientNpcStoreIndex.find(XenoNpcStoreCategory.SPAWNS, "", id);
        return indexed == null ? 0 : indexed.revision();
    }

    /**
     * What the server says this stored entry is at; 0 when it does not exist yet. Claiming a
     * literal 0 for an existing entry makes the store refuse every edit after the first.
     */
    private static int indexedRevision(XenoNpcStoreCategory category, String id) {
        var indexed = ClientNpcStoreIndex.find(category, "", id);
        return indexed == null ? 0 : indexed.revision();
    }

    /**
     * Creates a rule for the drafted id, pointing at the picked clone.
     *
     * <p>It starts with no biomes and the default weight, because "spawns anywhere, rarely" is the
     * reading of a rule nobody has configured yet; a rule that spawned nothing until every field
     * was filled would look broken rather than unfinished.
     */
    private void addNaturalSpawn() {
        var clones = ClientNpcStoreIndex.of(XenoNpcStoreCategory.CLONES);
        if (clones.isEmpty() || XenoNpcStorePaths.reject(newSpawnId) != null) {
            return;
        }
        var pick = clones.get(Math.min(Math.max(0, spawnCloneIndex), clones.size() - 1));
        var rule = new net.bullettrain.xenopixelsmod.npc.spawn.NpcNaturalSpawn(newSpawnId,
                newSpawnId, List.of(),
                net.bullettrain.xenopixelsmod.npc.spawn.NpcNaturalSpawn.DEFAULT_WEIGHT,
                spawnCloneTab(pick), pick.id(),
                net.bullettrain.xenopixelsmod.npc.spawn.NpcNaturalSpawn.TIME_ANY);
        AtlasSound.confirm();
        // 0: this one is meant to be new. If somebody else created the id first, the store refuses
        // rather than quietly replacing their rule with this one.
        ModNetwork.sendToServer(new XenoNpcStoreWritePacket(
                XenoNpcStoreCategory.SPAWNS.ordinal(), "", rule.id(), false, 0, rule.save()));
        selectedSpawn = rule.id();
        newSpawnId = "";
        rebuild();
    }

    private void removeNaturalSpawn(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        AtlasSound.confirm();
        ModNetwork.sendToServer(new XenoNpcStoreWritePacket(
                XenoNpcStoreCategory.SPAWNS.ordinal(), "", id, true, 0, null));
        selectedSpawn = "";
        rebuild();
    }

    private List<EditorRow> formRows() {
        List<EditorRow> rows = new ArrayList<>();
        List<String> groups = safeList(NpcFormLookup.groups(profile.raceId));
        rows.add(cycle("Group", groups, indexOf(groups, profile.selectedFormGroup),
                v -> {
                    profile.selectedFormGroup = groups.get(v);
                    rebuild();
                }, "SelectedFormGroup"));
        List<String> forms = safeList(NpcFormLookup.forms(profile.raceId, profile.selectedFormGroup));
        rows.add(cycle("Form", forms, indexOf(forms, profile.selectedFormId),
                v -> {
                    profile.selectedFormId = forms.get(v);
                    rebuild();
                }, "SelectedForm"));
        List<String> stackGroups = safeList(NpcFormLookup.stackGroups());
        rows.add(cycle("Stack Group", stackGroups, indexOf(stackGroups, profile.selectedStackGroup),
                v -> {
                    profile.selectedStackGroup = stackGroups.get(v);
                    rebuild();
                }, "SelectedStackFormGroup"));
        List<String> stackForms = safeList(NpcFormLookup.stackForms(profile.selectedStackGroup));
        rows.add(cycle("Stack Form", stackForms, indexOf(stackForms, profile.selectedStackId),
                v -> {
                    profile.selectedStackId = stackForms.get(v);
                    rebuild();
                }, "SelectedStackForm"));
        rows.add(masteryStepper("Mastery %", profile.selectedFormGroup,
                profile.selectedFormId, false));
        rows.add(masteryStepper("Stack mastery %", profile.selectedStackGroup,
                profile.selectedStackId, true));
        rows.add(new EditorRow.Action("Edit Selected", "mynpcs_button_row",
                () -> openFormMaker(DmzFormKind.NORMAL, profile.selectedFormGroup,
                        profile.selectedFormId)));
        rows.add(new EditorRow.Action("Create New", "mynpcs_button_row",
                () -> openFormMaker(DmzFormKind.NORMAL, "", "")));
        rows.add(new EditorRow.Action("Edit Stack", "mynpcs_button_row",
                () -> openFormMaker(DmzFormKind.STACK, profile.selectedStackGroup,
                        profile.selectedStackId)));
        rows.add(new EditorRow.Action("New Stack", "mynpcs_button_row",
                () -> openFormMaker(DmzFormKind.STACK, "", "")));
        rows.add(new EditorRow.Text("", "Opens the DMZ Form Maker; edits save back to the server.", MUTED));
        return rows;
    }

    /**
     * Opens the native form maker for the selected (or blank = new) definition. Mirrors the compat
     * {@code GuiNpcDmzForms} EDIT/CREATE flow: an existing file is loaded through
     * {@link DmzFormDocument#load}, a new one through {@link DmzFormDocument#create}, and a blank
     * selection on an edit is simply ignored.
     */
    private void openFormMaker(DmzFormKind kind, String group, String form) {
        String race = profile.raceId == null || profile.raceId.isBlank() ? "human" : profile.raceId;
        DmzFormDocument document = group == null || group.isBlank() || form == null || form.isBlank()
                ? DmzFormDocument.create(kind, race)
                : DmzFormDocument.load(kind, race, group, form);
        minecraft.setScreen(new DmzFormMakerScreen(document, this));
    }

    private List<EditorRow> dmzRows() {
        List<EditorRow> r = new ArrayList<>();
        List<String> races = NpcFormLookup.races();
        if (races.isEmpty()) {
            r.add(field("Race", profile.raceId, 64, v -> profile.raceId = v, "Race"));
        } else {
            r.add(cycle("Race", races, Math.max(0, races.indexOf(profile.raceId)),
                    v -> {
                        profile.raceId = races.get(v);
                        rebuild();
                    }, "Race"));
        }

        List<String> groups = safeList(NpcFormLookup.groups(profile.raceId));
        r.add(cycle("Group", groups, indexOf(groups, profile.selectedFormGroup),
                v -> {
                    profile.selectedFormGroup = groups.get(v);
                    rebuild();
                }, "SelectedFormGroup"));
        r.add(intField("STR", profile.strength, v -> profile.strength = v, "Strength"));
        List<String> forms = safeList(NpcFormLookup.forms(profile.raceId, profile.selectedFormGroup));
        r.add(cycle("Form", forms, indexOf(forms, profile.selectedFormId),
                v -> profile.selectedFormId = forms.get(v), "SelectedForm"));
        r.add(intField("SKP", profile.strikePower, v -> profile.strikePower = v, "StrikePower"));
        List<String> stackGroups = safeList(NpcFormLookup.stackGroups());
        r.add(cycle("Stack Grp", stackGroups, indexOf(stackGroups, profile.selectedStackGroup),
                v -> {
                    profile.selectedStackGroup = stackGroups.get(v);
                    rebuild();
                }, "SelectedStackFormGroup"));
        r.add(intField("DEF", profile.resistance, v -> profile.resistance = v, "Resistance"));
        List<String> stackForms = safeList(NpcFormLookup.stackForms(profile.selectedStackGroup));
        r.add(cycle("Stack", stackForms, indexOf(stackForms, profile.selectedStackId),
                v -> profile.selectedStackId = stackForms.get(v), "SelectedStackForm"));
        r.add(intField("VIT", profile.vitality, v -> profile.vitality = v, "Vitality"));
        r.add(colorField("Aura color", NpcCombatProfile.formatHex(profile.auraColor),
                v -> {
                    profile.auraColorHex = v;
                    markDirty("AuraColorHex");
                }));
        r.add(intField("PWR", profile.kiPower, v -> profile.kiPower = v, "KiPower"));
        r.add(floatField("Aura scale", profile.auraScale, v -> profile.auraScale = v, "AuraScale"));
        r.add(intField("ENE", profile.energy, v -> profile.energy = v, "Energy"));
        r.add(intField("Charge %", profile.kiChargePercent,
                v -> profile.kiChargePercent = v, "KiChargePercent"));
        r.add(toggle("Knockable", profile.knockable, v -> profile.knockable = v, "Knockable"));
        r.add(toggle("Damageable", profile.punchable, v -> profile.punchable = v, "Punchable"));

        r.add(new EditorRow.Action("Transform", "mynpcs_button_row",
                () -> sendAction(XenoNpcActionPacket.Action.TRANSFORM,
                        profile.selectedFormGroup, profile.selectedFormId)));
        r.add(new EditorRow.Action("Descend", "mynpcs_button_row",
                () -> sendAction(XenoNpcActionPacket.Action.DESCEND, "", "")));
        r.add(new EditorRow.Action("Stack", "mynpcs_button_row",
                () -> sendAction(XenoNpcActionPacket.Action.STACK,
                        profile.selectedStackGroup, profile.selectedStackId)));
        r.add(new EditorRow.Action("Unstack", "mynpcs_button_row",
                () -> sendAction(XenoNpcActionPacket.Action.UNSTACK, "", "")));
        r.add(new EditorRow.Action("Appearance", "mynpcs_button_row", this::openAppearance));
        r.add(new EditorRow.Action("Skills", "mynpcs_button_row",
                () -> openPage(ScreenId.DMZ_SKILLS)));
        r.add(new EditorRow.Action("Techniques", "mynpcs_button_row",
                () -> openPage(ScreenId.DMZ_TECHNIQUES)));
        r.add(new EditorRow.Action("Forms", "mynpcs_button_row",
                () -> openPage(ScreenId.DMZ_FORMS)));
        r.add(new EditorRow.Action("Attacks", "mynpcs_button_row",
                () -> openPage(ScreenId.DMZ_ATTACKS)));
        r.add(toggle("Fly", profile.brainFly, enabled -> {
            profile.brainFly = enabled;
            markDirty("BrainFly");
        }, "BrainFly"));

        r.add(toggle("Aura on", profile.auraOn, v -> profile.auraOn = v, "AuraOn"));
        r.add(toggle("Rocks", profile.auraRocks, v -> profile.auraRocks = v, "AuraRocks"));
        r.add(toggle("Sparking", profile.auraSparking,
                v -> profile.auraSparking = v, "AuraSparking"));
        r.add(toggle("Lightning", profile.auraLightning,
                v -> profile.auraLightning = v, "AuraLightning"));
        r.add(toggle("Ground ring", profile.auraGroundRing,
                v -> profile.auraGroundRing = v, "AuraGroundRing"));
        r.add(spacer());
        return r;
    }

    private List<EditorRow> dmzSkillRows() {
        List<EditorRow> rows = new ArrayList<>();
        rows.add(new EditorRow.Text("", "DragonMineZ skill levels and enabled states for this NPC.", MUTED));
        Set<String> ids = new java.util.TreeSet<>(NpcSkillSet.knownIds());
        ids.addAll(profile.skills.entries().keySet());
        if (ids.isEmpty()) {
            rows.add(new EditorRow.Text("", "No DMZ skill definitions are currently available.", WARN));
            return rows;
        }
        for (String rawId : ids) {
            String id = NpcSkillSet.canonical(rawId);
            if (id.isBlank()) continue;
            rows.add(new EditorRow.Heading(id));
            int currentLevel = Math.max(1, profile.skills.level(id));
            int configuredMax = NpcSkillSet.configuredMaxLevel(id);
            int max = configuredMax > 0 ? configuredMax : Math.max(1000, currentLevel);
            rows.add(new EditorRow.Toggle("Enabled", profile.skills.isActive(id), enabled -> {
                if (id.equals(NpcSkillSet.FLY)) {
                    profile.setDmzFlyEnabled(enabled);
                    markDirty("FlySkillOn");
                    markDirty("FlySkillLevel");
                } else {
                    profile.skills.setActive(id, enabled);
                }
                markDirty("DmzSkills");
            }));
            rows.add(new EditorRow.Stepper("Level", currentLevel, 1, max, level -> {
                profile.skills.setLevel(id, level);
                if (id.equals(NpcSkillSet.FLY)) {
                    profile.flySkillLevel = profile.skills.level(id);
                    markDirty("FlySkillLevel");
                }
                markDirty("DmzSkills");
            }));
            if (id.equals(NpcSkillSet.KI_SENSE)) {
                rows.add(new EditorRow.Toggle("Lock retaliator",
                        profile.kiSenseLockOnRetaliator, enabled -> {
                    profile.kiSenseLockOnRetaliator = enabled;
                    markDirty("KiSenseLockOn");
                }));
            }
        }
        return rows;
    }

    private List<EditorRow> dmzTechniqueRows() {
        List<EditorRow> rows = new ArrayList<>();
        rows.add(new EditorRow.Text("", "Select attacks and set each DMZ damage/cooldown upgrade level.", MUTED));
        List<String> ids = PredefinedTechniqueLookup.npcTechniqueIds();
        if (ids.isEmpty()) {
            rows.add(new EditorRow.Text("", "No executable DMZ techniques are currently available.", WARN));
            return rows;
        }
        for (String rawId : ids) {
            String id = NpcTechniqueLevels.canonical(rawId);
            rows.add(new EditorRow.Heading(id));
            rows.add(new EditorRow.Toggle("Selected", profile.techniques.contains(id), selected -> {
                if (selected) profile.addTechnique(id);
                else profile.removeTechnique(id);
                markDirty("Techniques");
            }));
            rows.add(new EditorRow.Stepper("Damage level",
                    profile.techniqueLevels.damageLevel(id), 0,
                    NpcTechniqueLevels.MAX_EFFECTIVE_LEVEL, level -> {
                profile.techniqueLevels.setDamage(id, level);
                markDirty("TechniqueLevels");
            }));
            rows.add(new EditorRow.Stepper("Cooldown level",
                    profile.techniqueLevels.cooldownLevel(id), 0,
                    NpcTechniqueLevels.MAX_EFFECTIVE_LEVEL, level -> {
                profile.techniqueLevels.setCooldown(id, level);
                markDirty("TechniqueLevels");
            }));
            rows.add(spacer());
        }
        return rows;
    }

    private EditorRow masteryStepper(String label, String group, String form, boolean stack) {
        com.dragonminez.common.config.FormConfig.FormData data = stack
                ? NpcFormLookup.stackForm(group, form)
                : NpcFormLookup.form(profile.raceId, group, form);
        if (data == null) {
            return new EditorRow.Stepper(label, 0, 0, 100, null);
        }
        double max = NpcFormLookup.maxMastery(data);
        var values = stack ? profile.stackMasteries : profile.masteries;
        int percent = max <= 0.0 ? 0 : (int) Math.round(100.0
                * Math.max(0.0, Math.min(max, values.getMastery(group, form))) / max);
        return new EditorRow.Stepper(label, percent, 0, 100, value -> {
            values.setMastery(group, form, max * value / 100.0, max);
            markDirty(stack ? "StackMasteries" : "Masteries");
        });
    }

    /**
     * Opens the per-part appearance editor.
     *
     * <p>It edits {@code profile.appearance} in place, so the whole {@code DmzAppearance} compound
     * is marked dirty here and travels with this editor's next Save. Nothing is sent from there.
     */
    private void openAppearance() {
        if (minecraft == null) {
            return;
        }
        minecraft.setScreen(new XenoNpcAppearanceScreen(this, entityId, profile, () -> {
            // Hair lives on the profile and the parts live in the appearance block, so an edit on
            // that screen can touch either; mark both so neither is dropped from the save.
            markDirty("DmzAppearance");
            markDirty("HairEnabled");
            markDirty("HairStyleId");
            markDirty("HairColor");
            markDirty("HairCode");
            markDirty("HairCodeChunks");
        }));
    }

    /** A cycle needs at least one entry, and an empty DMZ list is normal for some races. */
    private static List<String> safeList(List<String> values) {
        return values == null || values.isEmpty() ? List.of("(none)") : values;
    }

    private static int indexOf(List<String> values, String current) {
        int i = values.indexOf(current == null ? "" : current);
        return i < 0 ? 0 : i;
    }

    /**
     * Asks the server to run a transform action.
     *
     * <p>These change live entity state rather than a profile field, so they cannot wait for Save;
     * the server answers with the shared save-result packet and the rejection surfaces in chat.
     */
    private void sendAction(XenoNpcActionPacket.Action action, String group, String form) {
        String g = "(none)".equals(group) ? "" : group;
        String f = "(none)".equals(form) ? "" : form;
        ModNetwork.sendToServer(new XenoNpcActionPacket(entityId, revision, action, g, f));
        showNotice(action.name().charAt(0)
                + action.name().substring(1).toLowerCase(Locale.ROOT) + " sent");
    }

    private void toggleEditorLock() {
        ModNetwork.sendToServer(new XenoNpcEditorLockPacket(
                entityId, revision, !profile.editingLocked));
        onClose();
    }

    private List<EditorRow> brainRows() {
        List<EditorRow> r = new ArrayList<>();
        r.add(toggle("Combat brain", profile.combatBrain,
                v -> profile.combatBrain = v, "CombatBrain"));

        List<NpcCombatBrainVersion> versions = NpcBrainPolicy.choices();
        // One brain on offer (the XenoNPCs release): no picker to show.
        if (versions.size() > 1) r.add(cycle("Version",
                versions.stream().map(NpcCombatBrainVersion::label).toList(),
                Math.max(0, versions.indexOf(profile.brainVersion == null
                        ? NpcCombatBrainVersion.V1 : profile.brainVersion)),
                index -> {
                    profile.setBrainVersion(versions.get(Math.floorMod(index, versions.size())));
                    // The rows below depend on which version is selected, so the tab has to be
                    // rebuilt rather than just repainted.
                    rebuild();
                },
                // "Brain" is the on/off boolean; the version is its own key, and sending the wrong
                // one would have saved nothing.
                "BrainVersion"));
        r.add(new EditorRow.Text("", brainVersionHint(), MUTED));

        // The reference Brain tab's header row: Aim, Aggro, Special CD.
        r.add(floatField("Aim", profile.aimAccuracy, v -> profile.aimAccuracy = v, "AimAccuracy"));
        r.add(floatField("Aggro", profile.aggroMultiplier,
                v -> profile.aggroMultiplier = v, "AggroMultiplier"));
        r.add(intField("Special CD", profile.brainSpecialCooldown,
                v -> profile.brainSpecialCooldown = v, "BrainSpecialCooldown"));
        // Honest label: the reference brain's Aim is stored and synced, but the ki-blast lead
        // blends by npcRangedAccuracy (NpcKiAttackDispatcher.leadPoint), not by aimAccuracy.
        // docs/xeno-npc-schema.md says the same ("AimAccuracy still has no consumer").
        r.add(new EditorRow.Text("", "Aim is stored but nothing reads it yet; the ki", MUTED));
        r.add(new EditorRow.Text("", "blast lead uses Ranged Props > Accuracy.", MUTED));

        // The Xeno layer switch, shown only where it does something. v7 is the pure DragonMineZ
        // tree and never uses those moves; every other version reads this field. A toggle on v7
        // would be the dead-control bug, and one hidden on v6 would be a setting nobody could find.
        if (profile.brainVersion == null || profile.brainVersion.usesXenoSpecials()) {
            r.add(toggle("Xeno moves", profile.xenoSpecials,
                    v -> profile.xenoSpecials = v, "BrainXenoSpecials"));
            r.add(new EditorRow.Text("", "The BT3 combo poses and the teleport moves - chase,",
                    MUTED));
            r.add(new EditorRow.Text("", "vanish, backstep. Off leaves the DragonMineZ actions",
                    MUTED));
            r.add(new EditorRow.Text("", "alone; it does not make this NPC weaker.", MUTED));
        }

        if (profile.brainVersion != null && !profile.brainVersion.honoursToggles()) {
            // Everything below is decided by the ported DragonMineZ tree in v7 and v8, so the rows
            // are not shown at all. Leaving them on screen greyed out or live-but-ignored is the
            // dead-control bug: a switch that looks like it means something and does not.
            String version = profile.brainVersion.label();
            r.add(new EditorRow.Text("", "DragonMineZ decides every action in " + version
                    + ", so the", MUTED));
            r.add(new EditorRow.Text("", "per-action switches are hidden. Pick v6 to set them.",
                    MUTED));
            return r;
        }

        r.add(new EditorRow.Heading("Melee"));
        brainAction(r, "Strike", "strike", profile.brainStrike, v -> profile.brainStrike = v,
                "BrainStrike");
        brainAction(r, "Charge", "charge", profile.brainCharge, v -> profile.brainCharge = v,
                "BrainCharge");
        brainAction(r, "Flying fist", "flyingFist", profile.brainFlyingFist,
                v -> profile.brainFlyingFist = v, "BrainFlyingFist");
        brainAction(r, "Heavy hit", "heavyHit", profile.brainHeavyHit,
                v -> profile.brainHeavyHit = v, "BrainHeavyHit");
        brainAction(r, "Bone crush", "boneCrusher", profile.brainBoneCrusher,
                v -> profile.brainBoneCrusher = v, "BrainBoneCrusher");

        r.add(new EditorRow.Heading("Ki"));
        brainAction(r, "Ki blast", "kiBlast", profile.brainKiBlast,
                v -> profile.brainKiBlast = v, "BrainKiBlast");
        brainAction(r, "Ki wave", "kiWave", profile.brainKiWave,
                v -> profile.brainKiWave = v, "BrainKiWave");
        brainAction(r, "Ki disk", "kiDisk", profile.brainKiDisk,
                v -> profile.brainKiDisk = v, "BrainKiDisk");
        brainAction(r, "Named", "kiNamed", profile.brainKiNamed,
                v -> profile.brainKiNamed = v, "BrainKiNamed");

        r.add(new EditorRow.Heading("Evasion"));
        brainAction(r, "Vanish", "vanish", profile.brainVanish,
                v -> profile.brainVanish = v, "BrainVanish");
        brainAction(r, "Zanzoken", "zanzoken", profile.brainZanzoken,
                v -> profile.brainZanzoken = v, "BrainZanzoken");
        brainAction(r, "Defl blast", "deflectBlast", profile.brainDeflectBlast,
                v -> profile.brainDeflectBlast = v, "BrainDeflectBlast");
        brainAction(r, "Defl wave", "deflectWave", profile.brainDeflectWave,
                v -> profile.brainDeflectWave = v, "BrainDeflectWave");

        r.add(new EditorRow.Heading("Movement"));
        brainAction(r, "Chase", "chase", profile.brainChase, v -> profile.brainChase = v,
                "BrainChase");
        brainAction(r, "Fly search", "fly", profile.brainFly,
                v -> profile.brainFly = v, "BrainFly");
        r.add(new EditorRow.Toggle("Can Use Flight", profile.canUseFlight, v -> {
            profile.canUseFlight = v;
            markDirty("CanUseFlight");
        }));
        brainAction(r, "Disengage", "disengage", profile.brainDisengage,
                v -> profile.brainDisengage = v, "BrainDisengage");
        brainAction(r, "Ascend", "ascend", profile.brainAscend, v -> profile.brainAscend = v,
                "BrainAscend");
        return r;
    }

    /**
     * One brain action as the reference lays it out: <em>toggle, chance, modifier</em>.
     *
     * <p>The chance and the modifier are not new settings. {@code brainChance(name)} and
     * {@code brainModifier(name)} have always been on the profile and have always been read by both
     * brains - {@code allowBrainAction} rolls against the chance, and the modifier scales the hit.
     * The editor simply never showed them, so the only way to reach either was a command. Three
     * columns per action is also what takes this tab past one page, the way the reference is paged.
     */
    private void brainAction(List<EditorRow> rows, String label, String action, boolean on,
                             Consumer<Boolean> sink, String key) {
        rows.add(new EditorRow.BrainAction(label, on, value -> {
            sink.accept(value);
            markDirty(key);
        }, profile.brainChance(action), value -> {
            profile.setBrainChance(action, value);
            markDirty("BrainChances");
        }, profile.brainModifier(action), value -> {
            profile.setBrainModifier(action, value);
            markDirty("BrainModifiers");
        }));
    }

    private List<EditorRow> deleteRows() {
        List<EditorRow> r = new ArrayList<>();
        r.add(new EditorRow.Text("", "Deleting is permanent. The NPC is removed and its", MUTED));
        r.add(new EditorRow.Text("", "respawn is cancelled, so it does not come back.", MUTED));
        r.add(new EditorRow.Text("", "/kill does not work on Xeno NPCs, by design.", MUTED));
        r.add(new EditorRow.Heading(deleteArmed ? "Press again to confirm" : "Delete"));
        r.add(new EditorRow.Action(deleteArmed ? "CONFIRM" : "Delete NPC",
                "mynpcs_button_row", this::pressDelete));
        if (deleteArmed) {
            r.add(new EditorRow.Text("", "Disarms in a few seconds, or on leaving this tab.", WARN));
        }
        r.add(new EditorRow.Heading("Editor Security"));
        r.add(new EditorRow.Action(profile.editingLocked ? "Unlock Editor" : "Lock Editor",
                "mynpcs_button_row", this::toggleEditorLock));
        return r;
    }

    /**
     * Two presses to delete.
     *
     * <p>Every other action in this editor can be undone by editing again; this one cannot, so it
     * arms first and performs second. The arming lapses on its own and on leaving the tab, so a
     * half-pressed delete cannot sit waiting to catch a later click.
     */
    private void pressDelete() {
        long now = System.currentTimeMillis();
        if (!deleteArmed || now - deleteArmedAt > DELETE_ARM_MS) {
            deleteArmed = true;
            deleteArmedAt = now;
            showNotice("Press Delete again to confirm");
            rebuild();
            return;
        }
        deleteArmed = false;
        AtlasSound.confirm();
        ModNetwork.sendToServer(new XenoNpcDeletePacket(entityId, revision));
        onClose();
    }

    // ---------------------------------------------------------------- row helpers

    /** Marks {@code tagKey} dirty so the save sends it. A null key means "not a profile field". */
    private void markDirty(String tagKey) {
        if (tagKey != null) {
            dirtyKeys.add(tagKey);
        }
        autoSave.markChanged(editorTicks);
    }

    /** Marks a key on the NPC's data tag, which rides the save's second payload. */
    private void markDataDirty(String tagKey) {
        if (tagKey != null) {
            dataDirtyKeys.add(tagKey);
        }
        autoSave.markChanged(editorTicks);
    }

    private static EditorRow disabledField(String label, String value) {
        return new EditorRow.Field(label, value == null ? "" : value, 256, null);
    }

    private static EditorRow disabledToggle(String label, boolean value) {
        return new EditorRow.Toggle(label, value, null);
    }

    private static EditorRow disabledCycle(String label, String value) {
        return new EditorRow.Cycle(label, List.of(value == null ? "" : value), 0, null);
    }

    private static EditorRow disabledAction(String label) {
        return new EditorRow.Action(label, "mynpcs_button_row", null);
    }

    private static EditorRow disabledAction(String label, String sprite) {
        return new EditorRow.Action(label, sprite, null);
    }

    private static EditorRow wideAction(String label, Runnable action) {
        return new EditorRow.Action(label, "mynpcs_button_row", action, true);
    }

    private static EditorRow spacer() {
        return new EditorRow.Spacer();
    }

    private EditorRow soundPicker(String current, Consumer<String> sink, String key) {
        return new EditorRow.Action("Select Sound", "mynpcs_button_row",
                () -> openSoundPicker(current, sink, key));
    }

    private void openSoundPicker(String current, Consumer<String> sink, String key) {
        if (minecraft == null) {
            return;
        }
        minecraft.setScreen(new XenoNpcSoundPickerScreen(this, current, selected -> {
            sink.accept(selected);
            markDirty(key);
            rebuild();
        }));
    }

    private void openTexturePicker(String current, Consumer<String> sink, String key) {
        if (minecraft == null) return;
        minecraft.setScreen(new XenoNpcTexturePickerScreen(this, current, selected -> {
            sink.accept(selected);
            markDirty(key);
            rebuild();
        }));
    }

    private void openGeckoModelPicker(String current, Consumer<String> sink, String key) {
        if (minecraft == null) return;
        minecraft.setScreen(new XenoNpcTexturePickerScreen(this, current, selected -> {
            sink.accept(selected);
            markDirty(key);
            rebuild();
        }, true));
    }

    private EditorRow field(String label, String value, int max, Consumer<String> sink, String key) {
        return new EditorRow.Field(label, value == null ? "" : value, max, v -> {
            sink.accept(v);
            markDirty(key);
        });
    }

    /** A hex field plus a swatch that opens the shared picker; the sink still owns dirty keys. */
    private EditorRow colorField(String label, String value, Consumer<String> sink) {
        return new EditorRow.Color(label, value == null ? "" : value, sink);
    }

    /** Opens the picker seeded from the box's current text; OK writes through the box responder. */
    private void openColorPicker(EditBox box) {
        int rgb = NpcCombatProfile.parseHexColor(box.getValue()).orElse(0xFFFFFF);
        colorPicker.open(box, box.getX() + box.getWidth() + net.bullettrain.xenopixelsmod.client.ui.atlas.ColorSwatch.W + 6, box.getY(),
                getUiWidth(), getUiHeight(), box.getValue(), box::setValue);
    }

    /** Draft identifiers alter Add availability; ordinary text fields must keep partial input. */
    private EditorRow draftField(String label, String value, int max, Consumer<String> sink) {
        return new EditorRow.Field(label, value == null ? "" : value, max, v -> {
            sink.accept(v);
            scheduleRefresh();
        });
    }

    private EditorRow fullField(String label, String value, int max, Consumer<String> sink,
                                String key) {
        return new EditorRow.Field(label, value == null ? "" : value, max, v -> {
            sink.accept(v);
            markDirty(key);
        }, true);
    }

    private EditorRow intField(String label, int value, IntConsumer sink, String key) {
        return new EditorRow.Field(label, String.valueOf(value), 11, text -> {
            try {
                sink.accept(Integer.parseInt(text.trim()));
                markDirty(key);
            } catch (NumberFormatException ignored) {
                // Half-typed numbers are normal while editing; keep the last parseable value.
            }
        });
    }

    private EditorRow floatField(String label, float value, Consumer<Float> sink, String key) {
        return new EditorRow.Field(label, fmt(value), 12, text -> {
            try {
                sink.accept(Float.parseFloat(text.trim()));
                markDirty(key);
            } catch (NumberFormatException ignored) {
                // As above.
            }
        });
    }

    private EditorRow doubleField(String label, double value,
                                  java.util.function.DoubleConsumer sink, String key) {
        return new EditorRow.Field(label, fmt((float) value), 12, text -> {
            try {
                sink.accept(Double.parseDouble(text.trim()));
                markDirty(key);
            } catch (NumberFormatException ignored) {
                // As above.
            }
        });
    }

    private EditorRow stepper(String label, int value, int min, int max, IntConsumer sink,
                              String key) {
        return new EditorRow.Stepper(label, value, min, max, v -> {
            sink.accept(v);
            markDirty(key);
        });
    }

    /**
     * Whether the six mark icons exist in the atlas yet.
     *
     * <p>They do not, at the time of writing: the generator makes {@code rounded}, {@code pill},
     * {@code hex}, {@code banner}, {@code tab} and the speech bubbles, and no {@code mark} shape.
     * Rather than let the row look functional and render nothing, it says so. No invented art.
     */
    private String markArtHint() {
        boolean haveArt = XenoAtlasSprites.shapes().stream().anyMatch(s -> s.startsWith("mark_"));
        return haveArt
                ? "Shown above the NPC's head."
                : "Stored, but the mark icons are not in the atlas yet.";
    }

    /** One line saying what the selected brain version actually does. */
    private String brainVersionHint() {
        NpcCombatBrainVersion version = profile.brainVersion == null
                ? NpcCombatBrainVersion.V1 : profile.brainVersion;
        return switch (version) {
            case V1 -> "Flag-driven loop. The default.";
            case V2 -> "Saga decision tree.";
            case V3 -> "Saga tree with DragonMineZ flight.";
            case V4 -> "Legacy MyNPCs CombatContext engine.";
            case V5 -> "Native Xeno pathfinding.";
            case V6 -> "DragonMineZ tree, switches below still apply.";
            case V7 -> "Pure DragonMineZ. No switches, no Xeno moves.";
            case V8 -> "DragonMineZ tree plus Xeno moves you can switch.";
            case V9 -> "DragonMineZ saga with action switches; deflections start off.";
        };
    }

    private EditorRow toggle(String label, boolean value, Consumer<Boolean> sink, String key) {
        return new EditorRow.Toggle(label, value, v -> {
            sink.accept(v);
            markDirty(key);
            scheduleRefresh();
        });
    }

    private EditorRow cycle(String label, List<String> values, int selected, IntConsumer sink,
                            String key) {
        return new EditorRow.Cycle(label, values, selected, v -> {
            sink.accept(v);
            markDirty(key);
            scheduleRefresh();
        });
    }

    /** Says what "Model id" means for the selected kind, and where tint applies. */
    private String modelHint() {
        return switch (NpcCombatProfile.normalizeModelKind(profile.modelKind)) {
            case NpcCombatProfile.MODEL_GECKOLIB ->
                    "GeckoLib: animation asset may override the derived animations/ path.";
            case NpcCombatProfile.MODEL_ENTITY ->
                    "Entity: id is a registered entity type, e.g. minecraft:blaze.";
            default -> "Vanilla humanoid. Tint applies to GeckoLib models only.";
        };
    }

    /** Parses "#RRGGBB" or "RRGGBB", keeping the old value when the text is not a colour yet. */
    private static int parseHex(String text, int fallback) {
        if (text == null) {
            return fallback;
        }
        String clean = text.trim();
        if (clean.startsWith("#")) {
            clean = clean.substring(1);
        }
        if (clean.length() != 6) {
            return fallback;
        }
        try {
            return Integer.parseInt(clean, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String fmt(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA0000000);

        beginUiScale(graphics);
        AtlasPanel.fittedInto(frameSprite, frameX, frameY, getUiWidth() - 8, getUiHeight() - 8)
                .render(graphics);

        String title = "Xeno NPC Editor - " + currentPage().title();
        graphics.drawString(font, title, frameX + 18, frameY + 8, GOLD, false);
        String metadata = "Role: " + data.getString("Role") + "  -  Rev " + revision;
        int metadataX = contentRight() - font.width(metadata);
        if (metadataX > frameX + 26 + font.width(title)) {
            graphics.drawString(font, metadata, metadataX, frameY + 8, CYAN, false);
        }

        drawRowText(graphics);
        // Unconditional: the visualizer is on every page, as My NPCs has it. It draws outside the
        // frame now, so it costs the body nothing.
        renderPreviewColumn(graphics, partialTick);
        drawFooterText(graphics);

        super.render(graphics, (int) toUiX(mouseX), (int) toUiY(mouseY), partialTick);
        // Drawn last, above every widget, inside the same UI scale.
        colorPicker.render(graphics, (int) toUiX(mouseX), (int) toUiY(mouseY), partialTick);
        endUiScale(graphics);
    }

    /** Keeps a row's text inside the rectangle {@link EditorLayout} assigned it. */
    private String fitColumn(String text, int width) {
        return AtlasTextFit.fit(text, Math.max(0, width), 1.0f, font::width);
    }

    private void drawRowText(GuiGraphics graphics) {
        for (EditorLayout.Placed p : placed) {
            int textY = p.y() + 6;
            float pageScale = dmzListTextScale();
            { // was a pattern switch; if/else keeps it Java 17 source
                Object __switched3 = p.row();
                if (__switched3 instanceof EditorRow.Text t) {
                    if (t.fullWidth()) {
                        drawScaledText(graphics, t.value(), p.x(), textY,
                                p.columnWidth(), pageScale, t.color());
                    } else {
                        drawScaledText(graphics, t.label(), p.x(), textY,
                                CONTROL_DX - 4, pageScale, MUTED);
                        drawScaledText(graphics, t.value(), p.x() + CONTROL_DX, textY,
                                p.columnWidth() - CONTROL_DX, pageScale, t.color());
                    }
                }
                else if (__switched3 instanceof EditorRow.Heading h) {
                    drawScaledText(graphics, h.text(), p.x(), p.y() + 3,
                            p.columnWidth(), pageScale, CYAN);
                    graphics.fill(p.x(), p.y() + 13, p.x() + p.columnWidth(), p.y() + 14,
                            0x30FFFFFF);
                }
                else if (__switched3 instanceof EditorRow.Field f) { drawScaledText(graphics, f.label(), p.x(), textY,
                        CONTROL_DX - 4, lineTextScales.getOrDefault(p.y(), 1.0f), MUTED); }
                else if (__switched3 instanceof EditorRow.Color c) { drawScaledText(graphics, c.label(), p.x(), textY,
                        CONTROL_DX - 4, lineTextScales.getOrDefault(p.y(), 1.0f), MUTED); }
                else if (__switched3 instanceof EditorRow.Toggle t) { drawScaledText(graphics, t.label(), p.x(), textY,
                        CONTROL_DX - 4, lineTextScales.getOrDefault(p.y(), 1.0f), MUTED); }
                else if (__switched3 instanceof EditorRow.Cycle c) { drawScaledText(graphics, c.label(), p.x(), textY,
                        CONTROL_DX - 4, lineTextScales.getOrDefault(p.y(), 1.0f), MUTED); }
                else if (__switched3 instanceof EditorRow.Stepper st) { drawScaledText(graphics, st.label(), p.x(), textY,
                        CONTROL_DX - 4, lineTextScales.getOrDefault(p.y(), 1.0f), MUTED); }
                else if (__switched3 instanceof EditorRow.BrainAction action) {
                    float scale = lineTextScales.getOrDefault(p.y(), 1.0f);
                    drawScaledText(graphics, action.label(), p.x(), textY,
                            CONTROL_DX - 4, scale, MUTED);
                    drawScaledText(graphics, "%", p.x() + 136, textY, 12, scale, MUTED);
                    drawScaledText(graphics, "x", p.x() + 244, textY, 12, scale, MUTED);
                }
                else if (__switched3 instanceof EditorRow.Spacer ignored) { }
                else if (__switched3 instanceof EditorRow.Slot slot) {
                    // The slot number, drawn to the left of the row's two buttons. It is what an
                    // author arranges by, so it is part of the row rather than part of the label.
                    drawScaledText(graphics, String.valueOf(slot.number()),
                            p.x() - 14, textY, 12, pageScale, MUTED);
                }
                else if (__switched3 instanceof EditorRow.Action ignored) { }
            }
        }
    }

    /** Draws a row label at its group's shared scale, trimming only below the readable floor. */
    private void drawScaledText(GuiGraphics graphics, String text, int x, int y,
                                int availableWidth, float scale, int color) {
        float boundedScale = Math.max(0.1f, Math.min(1.0f, scale));
        String fitted = AtlasTextFit.fit(text, availableWidth, boundedScale, font::width);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0);
        graphics.pose().scale(boundedScale, boundedScale, 1.0f);
        graphics.drawString(font, fitted, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void drawFooterText(GuiGraphics graphics) {
        // The notice goes down FIRST so the pager is drawn over it and never the other way round.
        // It used to be painted last, which is how "Transform sent" ended up sitting across the
        // pager and leaving "Page 1 /" with its total covered.
        drawNotice(graphics);

        if (pageCount > 1) {
            EditorFooter pager = EditorFooter.of(contentRight(),
                    XenoAtlasSprites.get(ARROW).width());
            String label = "Page " + (tabPage[selectedTab] + 1) + " / " + pageCount;
            graphics.drawCenteredString(font, label, pager.labelCentreX(),
                    frameY + frameH - 26, LIGHT);
        }
    }

    /**
     * The transient "saved" / "transform sent" line.
     *
     * <p>Sits on its own row clear above the footer strip. The previous placement added six pixels
     * back on, which pushed it down into the footer band and straight across the pager - both were
     * drawn in the same space and the notice won, because it was drawn second.
     *
     * <p>{@link EditorFooter#leftEdge()} exists so footer content can stay clear of the pager. It is
     * used here as a second guard: if the notice really would collide, it is skipped rather than
     * drawn over the page numbers, because losing a message that reappears is better than losing the
     * control that tells you where you are.
     */
    private void drawNotice(GuiGraphics graphics) {
        if (notice.isEmpty()) {
            return;
        }
        if (System.currentTimeMillis() - noticeSetAt > NOTICE_MS) {
            notice = "";
            return;
        }
        // Compact strip, so the notice sits in the footer gap instead of covering the last row.
        int y = frameY + frameH - FOOTER_H - AtlasNotice.COMPACT_H;
        if (pageCount > 1 && y + AtlasNotice.COMPACT_H > frameY + frameH - FOOTER_H) {
            int pagerLeft = EditorFooter.of(previewX - 12,
                    XenoAtlasSprites.get(ARROW).width()).leftEdge();
            if (bodyX + AtlasNotice.COMPACT_W > pagerLeft) {
                return;
            }
        }
        new AtlasNotice(notice, bodyX, y).renderCompact(graphics, font);
    }

    private void showNotice(String text) {
        notice = text == null ? "" : text;
        noticeSetAt = System.currentTimeMillis();
    }

    /**
     * The live NPC preview.
     *
     * <p>When the entity cannot be resolved the panel says which step failed rather than a bare
     * "not loaded", because the two causes need different fixes: a missing client entity is an id
     * problem, whereas {@code renderPreview} returning false only means DMZ has no appearance state
     * yet, and the plain viewport render stands in for that.
     */
    private void renderPreviewColumn(GuiGraphics graphics, float partialTick) {
        preview.render(graphics, font, entityId, profile.auraOn, partialTick);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pickerClicked(mouseX, mouseY, button)) return true;
        if (npcSavePending) return true;
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
        if (npcSavePending) return true;
        double uiX = toUiX(mouseX);
        double uiY = toUiY(mouseY);
        if (preview.contains(uiX, uiY)) {
            preview.scroll(scrollY);
            return true;
        }
        // The wheel pages the body, which is the other half of "pages in every section tab".
        if (pageCount > 1 && uiX >= bodyX && uiX < bodyX + bodyW
                && uiY >= bodyY && uiY < bodyY + bodyH) {
            turnPage(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (colorPicker.keyPressed(keyCode, scanCode, modifiers)) return true;
        return npcSavePending || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (colorPicker.charTyped(codePoint, modifiers)) return true;
        return npcSavePending || super.charTyped(codePoint, modifiers);
    }

    // ---------------------------------------------------------------- save

    /**
     * Sends only the keys this screen changed.
     *
     * <p>{@code XenoNpcSavePacket} merges the incoming keys over the NPC's current profile, so a
     * subset updates exactly what was edited and leaves everything else - appearance, transform
     * state, mastery - as the server currently has it.
     */
    /** The Save button: save now if anything is unsaved, then close. */
    private void save() {
        if (editingStoreDialogue && (subPage == ScreenId.GLOBAL_DIALOG_LINES
                || subPage == ScreenId.ADVANCED_DIALOG_NODE
                || subPage == ScreenId.ADVANCED_DIALOG_OPTION)) {
            saveStoreDialogue();
            return;
        }
        // A heavier sound than the ordinary click, because this one commits.
        AtlasSound.confirm();
        closeAfterSave = true;
        if (npcSavePending) {
            return;
        }
        if (!autoSave.pending() || autoSaveStopped) {
            super.onClose();
            return;
        }
        sendNpcSave(true);
    }

    /** Sends the unsaved keys; {@code closing} keeps the old "Saving NPC" notice for the close path. */
    private void sendNpcSave(boolean closing) {
        if (npcSavePending) {
            return;
        }
        String name = clampStr(currentName(), 64);
        String title = clampStr(currentTitle(), 64);
        String faction = clampStr(currentFaction(), 64);

        CompoundTag full = profile.toTag();
        CompoundTag subset = new CompoundTag();
        for (String key : dirtyKeys) {
            if (full.contains(key)) {
                subset.put(key, full.get(key).copy());
            } else if (key.equals("TransportNetwork") || key.equals("BankId")
                    || key.equals("SceneId") || key.equals("ScriptId")
                    || key.equals("FollowerName")) {
                // These optional references disappear from toTag() when cleared. Send an explicit
                // empty value so the server merge replaces the old assignment.
                subset.putString(key, "");
            } else if (key.equals("JobEnabled")) {
                // A no-job NPC omits the default true value from its stored tag. An explicit
                // toggle back on must still override a previously saved false value.
                subset.putBoolean(key, profile.jobEnabled);
            } else if (key.equals("ItemGiverAvailability")) {
                subset.putString(key, QuestAvailability.NONE.toJson().toString());
            } else if (key.equals("DialogSlots") || key.equals("Trades")
                    || key.equals("ItemGiverItems") || key.equals("ItemGiverLines")
                    || key.equals("ScriptTabs")) {
                // An empty list replaces the final cleared reference or trade in the server merge.
                subset.put(key, new net.minecraft.nbt.ListTag());
            }
        }

        // The second payload. Built from the screen's data tag, not the profile's, because these
        // keys land on XenoNpcData - a key sent to the wrong destination is discarded in silence.
        CompoundTag dataSubset = new CompoundTag();
        for (String key : dataDirtyKeys) {
            if (data.contains(key)) {
                dataSubset.put(key, data.get(key).copy());
            }
        }

        ModNetwork.sendToServer(new XenoNpcSavePacket(entityId, revision, name, title, faction,
                subset, dataSubset));
        npcSavePending = true;
        // Keys changed while this save travels are the next save's; these are this one's.
        sentKeys = new LinkedHashSet<>(dirtyKeys);
        sentDataKeys = new LinkedHashSet<>(dataDirtyKeys);
        dirtyKeys.clear();
        dataDirtyKeys.clear();
        autoSave.sent();
        if (closing) showNotice("Saving NPC; waiting for the server");
        scheduleRefresh();
    }

    /** A late reply for another NPC or editor revision must not affect this draft. */
    public boolean receiveNpcSaveResult(int responseEntityId, int expectedRevision, int newRevision,
                                        boolean saved, String reason) {
        if (!npcSavePending || responseEntityId != entityId || expectedRevision != revision) {
            return false;
        }
        npcSavePending = false;
        if (saved) {
            if (newRevision >= 0) revision = newRevision;
            sentKeys = Set.of();
            sentDataKeys = Set.of();
            autoSave.acknowledged();
            if (closeAfterSave && !autoSave.pending()) {
                super.onClose();
                return true;
            }
            // No toast for an autosave: the Save button already reads "Saved".
            scheduleRefresh();
        } else {
            // Nothing was applied: the sent keys are unsaved again.
            dirtyKeys.addAll(sentKeys);
            dataDirtyKeys.addAll(sentDataKeys);
            sentKeys = Set.of();
            sentDataKeys = Set.of();
            autoSave.failed(editorTicks);
            closeAfterSave = false;
            if (net.bullettrain.xenopixelsmod.network.packet.NpcProfileSaveResultPacket.STALE.equals(reason)) {
                autoSaveStopped = true;
                showNotice("This NPC was changed elsewhere; close and reopen the editor to keep editing");
            } else {
                showNotice(reason == null || reason.isBlank()
                        ? "NPC save rejected; your changes are still here" : reason);
            }
            rebuild();
        }
        return true;
    }

    private String currentName() {
        return editedName == null ? data.getString("Name") : editedName;
    }

    static int revisionFromPayload(CompoundTag payload) {
        // Revision zero is the valid first revision of a freshly created NPC. Bumping it to one
        // here made every first editor save stale, so names and other edits appeared to do nothing.
        return Math.max(0, payload == null ? 0 : payload.getInt("Revision"));
    }

    private String currentTitle() {
        return editedTitle == null ? data.getString("Title") : editedTitle;
    }

    private String currentFaction() {
        return editedFaction == null ? data.getString("Faction") : editedFaction;
    }

    /** The NPC's recorded spawn point, or a note when it predates the anchor. */
    private String homeText() {
        if (!data.contains("HomeX")) {
            return "(not recorded)";
        }
        return String.format(Locale.ROOT, "%.0f, %.0f, %.0f",
                data.getDouble("HomeX"), data.getDouble("HomeY"), data.getDouble("HomeZ"));
    }

    private static String shortUuid(java.util.UUID id) {
        return id.toString().substring(0, 8) + "...";
    }

    private static String clampStr(String value, int max) {
        String next = value == null ? "" : value.trim();
        return next.substring(0, Math.min(max, next.length()));
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
}

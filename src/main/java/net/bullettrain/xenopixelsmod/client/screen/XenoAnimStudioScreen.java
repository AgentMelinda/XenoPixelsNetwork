package net.bullettrain.xenopixelsmod.client.screen;

import net.bullettrain.xenopixelsmod.client.anim.StudioClipBindings;
import net.bullettrain.xenopixelsmod.client.anim.XenoAnimClip;
import net.bullettrain.xenopixelsmod.client.anim.XenoTechniqueAnimBindingsClient;
import net.bullettrain.xenopixelsmod.combat.anim.TechniqueAnimSlot;
import net.bullettrain.xenopixelsmod.network.AnimClipsNetwork;
import net.bullettrain.xenopixelsmod.client.anim.XenoAnimRecorder;
import net.bullettrain.xenopixelsmod.client.anim.XenoClipSourceExport;
import net.bullettrain.xenopixelsmod.client.anim.XenoClipSources;
import net.bullettrain.xenopixelsmod.client.anim.XenoStudioClipCache;
import net.bullettrain.xenopixelsmod.client.anim.studio.AnimBonePose;
import net.bullettrain.xenopixelsmod.client.anim.studio.AnimBoneTrack;
import net.bullettrain.xenopixelsmod.client.anim.studio.AnimChannel;
import net.bullettrain.xenopixelsmod.client.anim.studio.AnimKey;
import net.bullettrain.xenopixelsmod.client.anim.studio.AnimMotionOps;
import net.bullettrain.xenopixelsmod.client.anim.studio.AnimOscillator;
import net.bullettrain.xenopixelsmod.client.anim.studio.AnimEasing;
import net.bullettrain.xenopixelsmod.client.anim.studio.AnimPoseClipboard;
import net.bullettrain.xenopixelsmod.client.anim.studio.AnimScene;
import net.bullettrain.xenopixelsmod.client.anim.studio.AnimTimeline;
import net.bullettrain.xenopixelsmod.client.anim.studio.AnimUndoStack;
import net.bullettrain.xenopixelsmod.client.anim.studio.StudioPoseBuffer;
import net.bullettrain.xenopixelsmod.client.anim.studio.XenoRig;
import net.bullettrain.xenopixelsmod.client.combat.anim.Bt3AnimationBinding;
import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * In-game GeckoLib scene studio: viewport, transport, bone inspector, timeline.
 *
 * <p>The editing model is one key per tick holding a pose per bone. The playhead moves over a scene
 * length that belongs to {@link AnimTimeline} rather than to the key list, which is what lets a key
 * be placed anywhere instead of only at tick 0.
 */
public final class XenoAnimStudioScreen extends UnblurredScreen {
    private static final int COL_BG = 0xF2080C14;
    private static final int COL_DOCK = 0xE8101624;
    private static final int COL_PANEL = 0xFF141C2C;
    private static final int COL_LINE = 0xFF2A3A55;
    private static final int COL_ACCENT = 0xFF3DDCFF;
    private static final int COL_REC = 0xFFFF3B5C;
    private static final int COL_PLAY = 0xFF5CFF9A;
    private static final int COL_KEY = 0xFFFFC14A;
    private static final int COL_MUTED = 0xFF7A8BA3;
    private static final int COL_TEXT = 0xFFE8F4FF;
    /** Viewport background at two-thirds opacity; what fades the onion-skin ghosts. */
    private static final int COL_GHOST_SCRIM = 0xAA070A12;
    private static final int TOP = 84;
    private static final int BOTTOM = 74;
    private static final int LEFT = 118;
    private static final int RIGHT = 176;

    private static final int CHANNEL_ROT = 0;
    private static final int CHANNEL_POS = 1;
    private static final int CHANNEL_SCALE = 2;
    private static final String[] CHANNEL_NAMES = {"ROT", "POS", "SCALE"};

    /** Slider range per channel: degrees, model units, multiplier. */
    private static final float[] CHANNEL_MIN = {-180f, -16f, 0f};
    private static final float[] CHANNEL_MAX = {180f, 16f, 4f};

    private static final int CHANNEL_ROW_Y = TOP + 16;
    private static final int AXIS_ROW_Y = TOP + 40;
    private static final int AXIS_ROW_STEP = 20;
    private static final int VALUE_ROW_Y = TOP + 104;
    private static final int RESET_ROW_Y = TOP + 116;
    private static final int ONION_ROW_Y = TOP + 134;
    private static final int VIEW_ROW_Y = TOP + 152;

    /** How long a SETTLE takes, and how finely BRIDGE and BAKE MOTION sample. */
    private static final double SETTLE_SECONDS = 0.5;
    private static final int BRIDGE_STEPS = 3;
    private static final double BAKE_STEP_SECONDS = 0.05;

    /** Scratch clip name the baked preview writes to; never bound, always overwritten. */
    private static final String BAKED_PREVIEW_CLIP = "xeno_studio_preview";

    private enum Overlay { NONE, HELP, LOAD, BIND, UNBIND, MOTION }

    /**
     * What PLAY does.
     *
     * <p>{@code POSE} scrubs the studio's own pose buffer: instant, seekable, and what editing
     * wants. {@code BAKED} saves a scratch clip, bakes it, and hands it to DragonMineZ's own
     * animation controller - the same path a bound combat move takes - so what you see is what the
     * move will actually render. The two can differ, and the only way to know is to look at both.
     */
    private enum Preview { POSE, BAKED }

    /** One source of truth for the shortcut table the HELP panel and the docs both show. */
    public static final String[] HELP_LINES = {
        "TRANSPORT",
        "  REC / STOP      record the live player into the clip",
        "  PLAY  (Space)   play or pause the scene",
        "  PV POSE/BAKED   scrub the studio pose, or play the real baked clip as a move does",
        "  LOOP            wrap at the end, and export loop: true",
        "  SAVE            write config/xenopixelsmod-anims/<name>.animation.json",
        "  LOAD            reopen a saved clip for editing",
        "",
        "KEYING",
        "  KEY   (K)       key every posed bone at the playhead; UPD when one is already there",
        "  MODE            KEY ALL, or KEY BONE to key only the selected bone",
        "  DEL   (Delete)  remove the key under the playhead",
        "  |< and >|       jump to the previous / next key  (Shift + Left / Right)",
        "  EASE            cycle how the segment arriving at this key interpolates",
        "",
        "POSING",
        "  bone list       click a bone to edit it",
        "  ROT/POS/SCALE   choose which channel the three sliders drive",
        "  sliders         drag, or use the - / + button at each end of the row",
        "  RESET BONE      back to rest for every channel",
        "  CPY / PST       copy and paste the whole pose;  MIR pastes it mirrored",
        "  UNDO / REDO     Ctrl+Z / Ctrl+Y, 32 steps",
        "  ONION           ghost the previous and next key behind the live pose",
        "",
        "VIEW",
        "  drag            orbit;  shift-drag or middle-drag pans;  wheel zooms",
        "  F               re-frame;  R resets the view entirely",
        "  RESET VIEW      the same, as a button",
        "",
        "TIMELINE",
        "  click or drag the rail to scrub;  Left / Right step one tick",
        "  -20 / +20       change the scene length",
        "  SNAP            off lets a key land between ticks, so two bones can differ",
        "",
        "SHAPING",
        "  SETTLE          ease this bone back to rest from where it is now",
        "  SMOOTH          average out a jittery channel, ends pinned",
        "  BRIDGE          bake the curve between two keys into real keys",
        "  THIN            drop keys the neighbours already describe",
        "  MOTION          continuous waves on a bone; bake them to make them portable",
        "",
        "SOURCES",
        "  SRC             where LOAD reads from: saved, shipped BT3, or DragonMineZ",
        "  EXPORT          dev runs only: merge this clip into the shipped animation file",
        "",
        "COMBAT",
        "  BIND            publish this clip to the server and attach a live slot",
        "  UNBIND          restore a slot to the shipped clip (right-click a BIND row)",
        "  /xenoanim bind <clip> <SLOT>  (Hakai or any BT3 intent; joiners sync)",
        "",
        "The rig is root, waist, head, right_arm, left_arm, right_leg, left_leg.",
        "Full guide: docs/xeno-anim-studio.md",
    };

    private final Screen parent;
    private final AnimScene scene = new AnimScene("scene");
    private final AnimTimeline timeline = new AnimTimeline();
    private final AnimUndoStack history = new AnimUndoStack();
    private final StudioViewport viewport = new StudioViewport();
    private EditBox nameBox;
    private Button playBtn;
    private Button recBtn;
    private Button keyBtn;
    private Button modeBtn;
    private Button easeBtn;
    private Button onionBtn;
    private Button loopBtn;
    private String selected = XenoRig.COMBAT.get(0);
    private int channel = CHANNEL_ROT;
    private boolean playing;
    private boolean loop = true;
    private boolean keySelectedBoneOnly;
    private boolean onion;
    private XenoClipSources.Source loadSource = XenoClipSources.Source.CONFIG;
    private Button snapBtn;
    private Button srcBtn;
    private Button previewBtn;
    private Preview preview = Preview.POSE;
    private Overlay overlay = Overlay.NONE;
    private int overlayScroll;
    private List<String> overlayRows = List.of();
    private String status = "Pose a bone, press KEY, move the playhead, KEY again";
    private int dragAxis = -1;
    private int viewX1, viewY1, viewX2, viewY2, railX1, railX2, railY, boneRailY;

    public XenoAnimStudioScreen(Screen parent) {
        super(Component.literal("Xeno Animator"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (minecraft != null && minecraft.player != null) {
            StudioPoseBuffer.setSubject(minecraft.player.getUUID());
        }
        StudioPoseBuffer.setActive(true);
        StudioPoseBuffer.setWeight(1f);
        layout();

        nameBox = new EditBox(font, 8, 6, 80, 18, Component.literal("clip"));
        nameBox.setValue(timeline.clip() == null ? scene.name : timeline.clip().name);
        nameBox.setMaxLength(32);
        nameBox.setBordered(true);
        addRenderableWidget(nameBox);

        int x = 92;
        recBtn = addRenderableWidget(button("REC", 32, x, 6, b -> toggleRecord()));
        x += 34;
        addRenderableWidget(button("STOP", 34, x, 6, b -> stopAll()));
        x += 36;
        playBtn = addRenderableWidget(button("PLAY", 38, x, 6, b -> togglePlay()));
        x += 40;
        loopBtn = addRenderableWidget(button(loopLabel(), 52, x, 6, b -> {
            loop = !loop;
            loopBtn.setMessage(Component.literal(loopLabel()));
            status = loop ? "Loop on" : "Loop off";
        }));
        x += 54;
        addRenderableWidget(button("SAVE", 36, x, 6, b -> saveClip()));
        x += 38;
        addRenderableWidget(button("LOAD", 36, x, 6, b -> openOverlay(Overlay.LOAD)));
        addRenderableWidget(button("HELP", 36, width - 78, 6, b -> openOverlay(Overlay.HELP)));
        addRenderableWidget(button("X", 18, width - 24, 6, b -> onClose()));

        int y = 28;
        x = 8;
        keyBtn = addRenderableWidget(button(keyLabel(), 34, x, y, b -> setKey()));
        x += 36;
        modeBtn = addRenderableWidget(button(keySelectedBoneOnly ? "BONE" : "ALL", 34, x, y, b -> {
            keySelectedBoneOnly = !keySelectedBoneOnly;
            modeBtn.setMessage(Component.literal(keySelectedBoneOnly ? "BONE" : "ALL"));
            status = keySelectedBoneOnly
                    ? "KEY writes " + selected + " only"
                    : "KEY writes every posed bone";
        }));
        x += 36;
        addRenderableWidget(button("DEL", 28, x, y, b -> deleteKey()));
        x += 30;
        addRenderableWidget(button("|<", 20, x, y, b -> jumpKey(-1)));
        x += 22;
        addRenderableWidget(button(">|", 20, x, y, b -> jumpKey(1)));
        x += 22;
        easeBtn = addRenderableWidget(button(easeLabel(), 48, x, y, b -> cycleEasing()));
        x += 50;
        addRenderableWidget(button("CPY", 30, x, y, b -> {
            AnimPoseClipboard.copy(StudioPoseBuffer.snapshot());
            status = "Pose copied";
        }));
        x += 32;
        addRenderableWidget(button("PST", 30, x, y, b -> pastePose(false)));
        x += 32;
        addRenderableWidget(button("MIR", 30, x, y, b -> pastePose(true)));

        int y3 = 50;
        x = 8;
        snapBtn = addRenderableWidget(button(snapLabel(), 52, x, y3, b -> {
            timeline.snap(!timeline.snap());
            snapBtn.setMessage(Component.literal(snapLabel()));
            status = timeline.snap()
                    ? "Playhead snaps to whole ticks"
                    : "Free scrubbing - keys can land between ticks";
        }));
        x += 54;
        addRenderableWidget(button("SETTLE", 46, x, y3, b -> settleSelected()));
        x += 48;
        addRenderableWidget(button("SMOOTH", 48, x, y3, b -> smoothSelected()));
        x += 50;
        addRenderableWidget(button("BRIDGE", 48, x, y3, b -> bridgeSelected()));
        x += 50;
        addRenderableWidget(button("THIN", 36, x, y3, b -> simplifySelected()));
        x += 38;
        addRenderableWidget(button("MOTION", 48, x, y3, b -> openOverlay(Overlay.MOTION)));
        x += 50;
        previewBtn = addRenderableWidget(button(previewLabel(), 66, x, y3, b -> togglePreview()));
        srcBtn = addRenderableWidget(button(sourceLabel(), 76, width - 80, y3, b -> {
            XenoClipSources.Source[] all = XenoClipSources.Source.values();
            loadSource = all[(loadSource.ordinal() + 1) % all.length];
            srcBtn.setMessage(Component.literal(sourceLabel()));
            openOverlayIfLoad();
            status = "LOAD reads from " + loadSource.label;
        }));
        if (XenoClipSourceExport.available()) {
            // Only a development run has the repository beside the game directory.
            addRenderableWidget(button("EXPORT", 48, width - 132, y3, b -> exportToSource()));
        }

        addRenderableWidget(button("UNBIND", 44, width - 166, y, b -> openOverlay(Overlay.UNBIND)));
        addRenderableWidget(button("BIND", 38, width - 120, y, b -> openOverlay(Overlay.BIND)));
        addRenderableWidget(button("UNDO", 38, width - 80, y, b -> undo()));
        addRenderableWidget(button("REDO", 38, width - 40, y, b -> redo()));

        int dockX = inspectorX();
        int dockW = RIGHT - 20;
        for (int i = 0; i < CHANNEL_NAMES.length; i++) {
            int index = i;
            addRenderableWidget(smallButton(CHANNEL_NAMES[i], dockW / 3 - 2,
                    dockX + i * (dockW / 3), CHANNEL_ROW_Y, 16, b -> {
                        channel = index;
                        status = "Editing " + CHANNEL_NAMES[index] + " of " + selected;
                    }));
        }
        for (int axis = 0; axis < 3; axis++) {
            int index = axis;
            int rowY = axisRowY(axis);
            addRenderableWidget(smallButton("-", 14, dockX, rowY, 14, b -> nudge(index, -1)));
            addRenderableWidget(smallButton("+", 14, width - 24, rowY, 14, b -> nudge(index, 1)));
        }
        addRenderableWidget(smallButton("RESET BONE", dockW, dockX, RESET_ROW_Y, 16, b -> {
            pushHistory();
            StudioPoseBuffer.bone(selected).reset();
            status = selected + " reset";
        }));
        onionBtn = addRenderableWidget(smallButton(onionLabel(), dockW, dockX, ONION_ROW_Y, 16, b -> {
            onion = !onion;
            onionBtn.setMessage(Component.literal(onionLabel()));
        }));
        addRenderableWidget(smallButton("RESET VIEW", dockW, dockX, VIEW_ROW_Y, 16, b -> {
            viewport.reset();
            status = "View reset";
        }));

        addRenderableWidget(button("<", 20, LEFT + 8, height - 50, b -> {
            timeline.step(-1);
            applyPlayhead();
        }));
        addRenderableWidget(button(">", 20, LEFT + 30, height - 50, b -> {
            timeline.step(1);
            applyPlayhead();
        }));
        addRenderableWidget(button("-20", 30, LEFT + 54, height - 50, b -> changeDuration(-20)));
        addRenderableWidget(button("+20", 30, LEFT + 86, height - 50, b -> changeDuration(20)));

        refreshTransport();
    }

    private Button button(String label, int w, int x, int y, Button.OnPress action) {
        return Button.builder(Component.literal(label), action).bounds(x, y, w, 18).build();
    }

    private Button smallButton(String label, int w, int x, int y, int h, Button.OnPress action) {
        return Button.builder(Component.literal(label), action).bounds(x, y, w, h).build();
    }

    private int inspectorX() {
        return width - RIGHT + 10;
    }

    private int sliderX1() {
        return inspectorX() + 16;
    }

    private int sliderX2() {
        return width - 26;
    }

    /**
     * The dock is only {@code height - TOP - BOTTOM} tall, so each axis row keeps its minus and plus
     * buttons inline with the slider instead of on rows of their own. Stacked rows needed more
     * vertical space than the dock has at common window sizes, and the value labels ended up drawn
     * over the buttons beneath them.
     */
    private static int axisRowY(int axis) {
        return AXIS_ROW_Y + axis * AXIS_ROW_STEP;
    }

    private String keyLabel() {
        return timeline.hasKeyAtPlayhead() ? "UPD" : "KEY";
    }

    private String loopLabel() {
        return loop ? "LOOP ON" : "LOOP OFF";
    }

    private String previewLabel() {
        return preview == Preview.BAKED ? "PV BAKED" : "PV POSE";
    }

    /**
     * Switches between scrubbing our pose buffer and playing the real baked animation.
     *
     * <p>Baked preview turns the pose buffer off, because the two would fight over the same bones,
     * and stops the screen pausing the game - a paused world does not run DragonMineZ's controller,
     * so there would be nothing to watch.
     */
    private void togglePreview() {
        preview = preview == Preview.POSE ? Preview.BAKED : Preview.POSE;
        previewBtn.setMessage(Component.literal(previewLabel()));
        playing = false;
        if (preview == Preview.BAKED) {
            StudioPoseBuffer.setActive(false);
            status = "Baked preview - PLAY hands the clip to DragonMineZ, as a bound move would";
        } else {
            if (minecraft != null && minecraft.player != null) {
                StudioPoseBuffer.setSubject(minecraft.player.getUUID());
            }
            StudioPoseBuffer.setActive(true);
            StudioPoseBuffer.setWeight(1f);
            applyPlayhead();
            status = "Pose preview - PLAY scrubs the studio timeline";
        }
        refreshTransport();
    }

    /** Saves a scratch copy, bakes it, and plays it the way a bound combat move is played. */
    private void playBakedPreview() {
        XenoAnimClip clip = ensureClip();
        if (clip.isEmpty()) {
            status = "Nothing to play - press KEY first";
            return;
        }
        try {
            XenoAnimClip scratch = clip.renamed(BAKED_PREVIEW_CLIP);
            scratch.save(loop, timeline.duration());
            XenoStudioClipCache.refresh(scratch.name);
            if (!XenoStudioClipCache.has(scratch.animationName())) {
                status = "Baked preview failed - /xenoanim reload says why";
                return;
            }
            Bt3AnimationBinding.registerAnimationName(scratch.animationName());
            if (minecraft != null
                    && minecraft.player instanceof com.dragonminez.client.animation.IPlayerAnimatable anim) {
                anim.dragonminez$playMeleeAnimation(scratch.animationName(), false, 1.0f);
                status = "Playing baked " + scratch.animationName();
            } else {
                status = "No DragonMineZ model on this player to play it through";
            }
        } catch (Exception e) {
            status = "Baked preview failed: " + e.getMessage();
        }
    }

    private String snapLabel() {
        return timeline.snap() ? "SNAP ON" : "SNAP OFF";
    }

    private String sourceLabel() {
        return "SRC " + loadSource.label;
    }

    private void openOverlayIfLoad() {
        if (overlay == Overlay.LOAD) {
            overlayScroll = 0;
            overlayRows = XenoClipSources.list(loadSource);
        }
    }

    private String onionLabel() {
        return onion ? "ONION ON" : "ONION OFF";
    }

    private String easeLabel() {
        return "E " + AnimEasing.label(easingUnderPlayhead());
    }

    private String easingUnderPlayhead() {
        XenoAnimClip clip = timeline.clip();
        return clip == null ? AnimEasing.LINEAR : clip.easingAt(timeline.playhead());
    }

    private void layout() {
        viewX1 = LEFT + 8;
        viewY1 = TOP + 8;
        viewX2 = width - RIGHT - 8;
        viewY2 = height - BOTTOM - 8;
        railX1 = LEFT + 8;
        railX2 = width - RIGHT - 8;
        railY = height - 30;
        boneRailY = railY - 18;
    }

    // ------------------------------------------------------------------ history

    private AnimUndoStack.Snapshot snapshot() {
        return AnimUndoStack.Snapshot.of(timeline.clip(), StudioPoseBuffer.snapshot(),
                timeline.playhead(), timeline.duration(), loop);
    }

    private void pushHistory() {
        ensureClip();
        history.push(snapshot());
    }

    private void restore(AnimUndoStack.Snapshot state) {
        if (state == null) return;
        XenoAnimClip clip = ensureClip();
        state.restore(clip);
        loop = state.loop();
        timeline.setDuration(state.duration());
        timeline.rebind(clip);
        timeline.seek(state.playhead());
        StudioPoseBuffer.putAll(state.pose());
        refreshTransport();
    }

    private void undo() {
        AnimUndoStack.Snapshot state = history.undo(snapshot());
        if (state == null) {
            status = "Nothing to undo";
            return;
        }
        restore(state);
        status = "Undo";
    }

    private void redo() {
        AnimUndoStack.Snapshot state = history.redo(snapshot());
        if (state == null) {
            status = "Nothing to redo";
            return;
        }
        restore(state);
        status = "Redo";
    }

    // ------------------------------------------------------------------ transport

    private void toggleRecord() {
        if (XenoAnimRecorder.recording()) {
            XenoAnimClip clip = XenoAnimRecorder.stop();
            timeline.bind(clip);
            playing = false;
            status = "Record stopped - " + (clip == null ? 0 : clip.keyCount()) + " keys";
        } else {
            playing = false;
            scene.name = nameBox.getValue();
            XenoAnimRecorder.start(nameBox.getValue());
            status = "Recording scene - act in the viewport";
        }
        refreshTransport();
    }

    private void stopAll() {
        playing = false;
        if (XenoAnimRecorder.recording()) {
            XenoAnimClip clip = XenoAnimRecorder.stop();
            timeline.bind(clip);
        }
        timeline.seek(0.0);
        // Only overwrite the live pose when there is authored content to overwrite it with;
        // otherwise STOP would silently throw away a pose that has not been keyed yet.
        XenoAnimClip clip = timeline.clip();
        if (clip != null && !clip.isEmpty()) applyPlayhead();
        status = "Stopped";
        refreshTransport();
    }

    private void togglePlay() {
        if (XenoAnimRecorder.recording()) XenoAnimRecorder.stop();
        if (preview == Preview.BAKED) {
            playBakedPreview();
            return;
        }
        XenoAnimClip clip = ensureClip();
        timeline.rebind(clip);
        if (playing) {
            playing = false;
            status = "Paused";
        } else if (clip.isEmpty()) {
            status = "Nothing to play - press KEY first";
        } else {
            if (timeline.playhead() >= timeline.durationSeconds()) timeline.seek(0.0);
            playing = true;
            status = "Playing scene";
        }
        refreshTransport();
    }

    private void refreshTransport() {
        if (playBtn != null) playBtn.setMessage(Component.literal(playing ? "PAUSE" : "PLAY"));
        if (recBtn != null) recBtn.setMessage(Component.literal(XenoAnimRecorder.recording() ? "REC*" : "REC"));
        if (keyBtn != null) keyBtn.setMessage(Component.literal(keyLabel()));
        if (easeBtn != null) easeBtn.setMessage(Component.literal(easeLabel()));
    }

    // ------------------------------------------------------------------ keying

    private void setKey() {
        pushHistory();
        XenoAnimClip clip = ensureClip();
        double time = timeline.playhead();
        if (keySelectedBoneOnly) {
            clip.keyBone(time, selected, StudioPoseBuffer.bone(selected).copy());
        } else {
            clip.keyPose(time, StudioPoseBuffer.snapshot());
        }
        // rebind, not bind: bind rewinds to 0, which used to undo the very move that put the
        // playhead where the key was wanted.
        timeline.rebind(clip);
        if (!scene.clips.contains(clip.name)) scene.clips.add(clip.name);
        status = (keySelectedBoneOnly ? "Keyed " + selected + " @ " : "Keyed @ ") + timecode(time);
        refreshTransport();
    }

    private void deleteKey() {
        XenoAnimClip clip = timeline.clip();
        if (clip == null || !clip.hasKeyAt(timeline.playhead())) {
            status = "No key here";
            return;
        }
        pushHistory();
        clip.removeKeysAt(timeline.playhead());
        timeline.rebind(clip);
        status = "Key removed @ " + timecode(timeline.playhead());
        refreshTransport();
    }

    private void jumpKey(int direction) {
        boolean moved = direction < 0 ? timeline.toPrevKey() : timeline.toNextKey();
        if (!moved) {
            status = direction < 0 ? "No earlier key" : "No later key";
            return;
        }
        applyPlayhead();
        status = "Key @ " + timecode(timeline.playhead());
        refreshTransport();
    }

    private void cycleEasing() {
        XenoAnimClip clip = timeline.clip();
        if (clip == null || !clip.hasKeyAt(timeline.playhead())) {
            status = "Easing belongs to a key - press KEY first";
            return;
        }
        pushHistory();
        String next = AnimEasing.next(clip.easingAt(timeline.playhead()));
        clip.setEasingAt(timeline.playhead(), next);
        status = "Easing " + AnimEasing.label(next) + " @ " + timecode(timeline.playhead());
        refreshTransport();
    }

    /** The channel the inspector is currently pointed at, created on demand. */
    private AnimChannel selectedChannel() {
        AnimChannel.Kind kind = switch (channel) {
            case CHANNEL_POS -> AnimChannel.Kind.POSITION;
            case CHANNEL_SCALE -> AnimChannel.Kind.SCALE;
            default -> AnimChannel.Kind.ROTATION;
        };
        return ensureClip().track(selected).channel(kind);
    }

    private void settleSelected() {
        pushHistory();
        AnimChannel target = selectedChannel();
        // Start from whatever is posed right now, not from whatever the keys happen to say.
        AnimBonePose live = StudioPoseBuffer.bone(selected);
        target.put(timeline.playhead(), liveValue(live, target.kind()));
        double landed = AnimMotionOps.settle(target, timeline.playhead(), SETTLE_SECONDS,
                "easeinoutsine");
        timeline.rebind(timeline.clip());
        status = selected + " settles to rest by " + timecode(landed);
        refreshTransport();
    }

    private AnimKey liveValue(AnimBonePose pose, AnimChannel.Kind kind) {
        return switch (kind) {
            case POSITION -> new AnimKey(pose.posX, pose.posY, pose.posZ);
            case SCALE -> new AnimKey(pose.scaleX, pose.scaleY, pose.scaleZ);
            default -> new AnimKey(pose.rotX, pose.rotY, pose.rotZ);
        };
    }

    private void smoothSelected() {
        AnimChannel target = selectedChannel();
        if (target.size() < 3) {
            status = "Smoothing needs at least three keys on this channel";
            return;
        }
        pushHistory();
        int moved = AnimMotionOps.smooth(target, 0.0, timeline.durationSeconds(), 0.5f);
        status = "Smoothed " + moved + " key(s) on " + selected;
    }

    private void bridgeSelected() {
        AnimChannel target = selectedChannel();
        Double prev = target.floorTime(timeline.playhead());
        Double next = target.ceilingTime(timeline.playhead());
        if (prev == null || next == null || prev.equals(next)) {
            status = "Park the playhead between two keys of this channel first";
            return;
        }
        pushHistory();
        int written = AnimMotionOps.bridge(target, prev, next, BRIDGE_STEPS);
        status = "Baked the curve into " + written + " key(s)";
        refreshTransport();
    }

    private void simplifySelected() {
        AnimChannel target = selectedChannel();
        if (target.size() < 3) {
            status = "Nothing to thin on this channel";
            return;
        }
        pushHistory();
        int before = target.size();
        int removed = AnimMotionOps.simplify(target, simplifyTolerance());
        status = "Thinned " + selected + ": " + before + " keys to " + (before - removed);
        timeline.rebind(timeline.clip());
        refreshTransport();
    }

    /**
     * Rotation is in degrees and translation in model units (16 to a block, as GeckoLib reads the
     * file), so one tolerance cannot serve both.
     */
    private double simplifyTolerance() {
        return switch (channel) {
            case CHANNEL_POS -> 0.05;
            case CHANNEL_SCALE -> 0.02;
            default -> 1.0;
        };
    }

    private void addMotion() {
        pushHistory();
        AnimChannel.Kind kind = selectedChannel().kind();
        float amplitude = kind == AnimChannel.Kind.ROTATION ? 10f : 0.5f;
        ensureClip().motions.add(new AnimOscillator(selected, kind, 0, amplitude, 1.0, 0.0,
                AnimOscillator.Wave.SINE));
        overlayRows = motionRows();
        status = "Added a sine on " + selected;
    }

    private void removeMotion(int index) {
        XenoAnimClip clip = timeline.clip();
        if (clip == null || index < 0 || index >= clip.motions.size()) return;
        pushHistory();
        AnimOscillator removed = clip.motions.remove(index);
        overlayRows = motionRows();
        status = "Removed motion on " + removed.bone();
    }

    private void bakeMotions() {
        XenoAnimClip clip = timeline.clip();
        if (clip == null || clip.motions.isEmpty()) {
            status = "No motion to bake";
            return;
        }
        pushHistory();
        int written = 0;
        double end = timeline.durationSeconds();
        for (AnimOscillator motion : List.copyOf(clip.motions)) {
            written += motion.bake(clip.track(motion.bone()).channel(motion.channel()),
                    0.0, end, BAKE_STEP_SECONDS);
        }
        // Once baked they are keys; leaving the generators on would apply the wave twice.
        clip.motions.clear();
        overlayRows = motionRows();
        timeline.rebind(clip);
        status = "Baked motion into " + written + " key(s)";
        refreshTransport();
    }

    private List<String> motionRows() {
        List<String> rows = new ArrayList<>();
        XenoAnimClip clip = timeline.clip();
        rows.add("+ add a sine on " + selected + " (" + CHANNEL_NAMES[channel] + ")");
        rows.add("* bake every motion into keys");
        rows.add("");
        if (clip == null || clip.motions.isEmpty()) {
            rows.add("  nothing running");
        } else {
            for (AnimOscillator motion : clip.motions) {
                rows.add("  " + motion.describe() + "   (click to remove)");
            }
        }
        return rows;
    }

    private void exportToSource() {
        XenoAnimClip clip = timeline.clip();
        if (clip == null || clip.isEmpty()) {
            status = "Nothing to export";
            return;
        }
        try {
            java.nio.file.Path backup = XenoClipSourceExport.export(clip, clip.animationName());
            status = "Exported into the shipped file - backup at " + backup.getFileName();
        } catch (Exception e) {
            status = "Export failed: " + e.getMessage();
        }
    }

    private void changeDuration(int delta) {
        pushHistory();
        timeline.setDuration(timeline.duration() + delta);
        XenoAnimClip clip = timeline.clip();
        if (clip != null) clip.durationTicks = timeline.duration();
        status = "Scene length " + timecode(timeline.duration());
    }

    private void pastePose(boolean mirrored) {
        if (AnimPoseClipboard.isEmpty()) {
            status = "Clipboard empty - press COPY first";
            return;
        }
        pushHistory();
        StudioPoseBuffer.putAll(mirrored ? AnimPoseClipboard.pasteMirrored() : AnimPoseClipboard.paste());
        status = mirrored ? "Pose pasted mirrored" : "Pose pasted";
    }

    private void saveClip() {
        XenoAnimClip clip = ensureClip();
        String wanted = XenoAnimClip.sanitize(nameBox.getValue());
        if (!wanted.equals(clip.name)) {
            clip = clip.renamed(wanted);
            timeline.rebind(clip);
        }
        try {
            clip.save(loop, timeline.duration());
            XenoStudioClipCache.refresh(clip.name);
            Bt3AnimationBinding.registerStudioNames();
            status = "Exported " + clip.name + ".animation.json";
        } catch (Exception e) {
            status = "Save failed: " + e.getMessage();
        }
    }

    private void loadClip(String name) {
        try {
            XenoAnimClip clip = XenoClipSources.load(loadSource, name);
            history.clear();
            timeline.bind(clip);
            loop = clip.loop;
            if (loopBtn != null) loopBtn.setMessage(Component.literal(loopLabel()));
            nameBox.setValue(clip.name);
            applyPlayhead();
            status = "Loaded " + clip.name + " from " + loadSource.label
                    + " - " + clip.keyCount() + " keys";
        } catch (Exception e) {
            status = "Load failed: " + e.getMessage();
        }
        refreshTransport();
    }

    private void bindServerSlot(String slotName) {
        XenoAnimClip clip = timeline.clip();
        String name = clip == null ? XenoAnimClip.sanitize(nameBox.getValue()) : clip.name;
        if (!XenoAnimClip.listSavedNames().contains(name)) {
            status = "Save the clip before binding it";
            return;
        }
        Path file = XenoAnimClip.dir().resolve(name + ".animation.json");
        if (Files.isRegularFile(file)) {
            try {
                AnimClipsNetwork.pushFromClient(name, Files.readString(file));
            } catch (IOException e) {
                status = "Could not publish " + name + ": " + e.getMessage();
                return;
            }
        }
        AnimClipsNetwork.bindFromClient(slotName, name);
        Bt3AnimationIntent intent = StudioClipBindings.intentOf(slotName);
        if (intent != null) {
            StudioClipBindings.bind(intent, name);
        }
        XenoStudioClipCache.refresh(name);
        Bt3AnimationBinding.registerStudioNames();
        status = slotName + " asked the server to play " + name + " for every joiner";
    }

    private void unbindServerSlot(String slotName) {
        AnimClipsNetwork.bindFromClient(slotName, "");
        Bt3AnimationIntent intent = StudioClipBindings.intentOf(slotName);
        if (intent != null) {
            StudioClipBindings.unbind(intent);
        }
        status = slotName + " asked the server to restore the shipped clip";
    }

    private void nudge(int axis, int sign) {
        pushHistory();
        AnimBonePose pose = StudioPoseBuffer.bone(selected);
        float step = channel == CHANNEL_SCALE ? 0.1f : channel == CHANNEL_POS ? 0.5f : 5f;
        setAxis(pose, axis, getAxis(pose, axis) + step * sign);
        status = selected + "  " + fmt(pose);
    }

    private XenoAnimClip ensureClip() {
        XenoAnimClip clip = timeline.clip();
        if (clip == null) {
            clip = XenoAnimRecorder.current();
            if (clip == null) clip = new XenoAnimClip(nameBox == null ? scene.name : nameBox.getValue());
            timeline.rebind(clip);
        }
        return clip;
    }

    private void applyPlayhead() {
        Map<String, AnimBonePose> pose = timeline.poseAtPlayhead();
        if (!pose.isEmpty()) StudioPoseBuffer.putAll(pose);
        refreshTransport();
    }

    // ------------------------------------------------------------------ channel access

    private float getAxis(AnimBonePose pose, int axis) {
        return switch (channel) {
            case CHANNEL_POS -> axis == 0 ? pose.posX : axis == 1 ? pose.posY : pose.posZ;
            case CHANNEL_SCALE -> axis == 0 ? pose.scaleX : axis == 1 ? pose.scaleY : pose.scaleZ;
            default -> axis == 0 ? pose.rotX : axis == 1 ? pose.rotY : pose.rotZ;
        };
    }

    private void setAxis(AnimBonePose pose, int axis, float value) {
        float clamped = Mth.clamp(value, CHANNEL_MIN[channel], CHANNEL_MAX[channel]);
        switch (channel) {
            case CHANNEL_POS -> {
                if (axis == 0) pose.posX = clamped;
                else if (axis == 1) pose.posY = clamped;
                else pose.posZ = clamped;
                pose.usePosition = true;
            }
            case CHANNEL_SCALE -> {
                if (axis == 0) pose.scaleX = clamped;
                else if (axis == 1) pose.scaleY = clamped;
                else pose.scaleZ = clamped;
                pose.useScale = true;
            }
            default -> {
                if (axis == 0) pose.rotX = clamped;
                else if (axis == 1) pose.rotY = clamped;
                else pose.rotZ = clamped;
            }
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public void tick() {
        super.tick();
        layout();
        if (playing) {
            double next = timeline.playhead() + 1.0 / XenoAnimClip.TICKS_PER_SECOND;
            if (next > timeline.durationSeconds() + 1.0e-6) {
                if (loop && timeline.lengthTicks() > 0) next = 0.0;
                else {
                    playing = false;
                    next = timeline.durationSeconds();
                    status = "Scene ended";
                }
            }
            timeline.seek(next);
            applyPlayhead();
        }
        refreshTransport();
    }

    @Override
    public boolean isPauseScreen() {
        // A paused world runs no animation controller, so a baked preview would be a still frame.
        return !XenoAnimRecorder.recording() && preview != Preview.BAKED;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameBox != null && nameBox.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (overlay != Overlay.NONE && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            overlay = Overlay.NONE;
            return true;
        }
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        switch (keyCode) {
            case GLFW.GLFW_KEY_K -> {
                setKey();
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                deleteKey();
                return true;
            }
            case GLFW.GLFW_KEY_SPACE -> {
                togglePlay();
                return true;
            }
            case GLFW.GLFW_KEY_F -> {
                viewport.frame();
                status = "View re-framed";
                return true;
            }
            case GLFW.GLFW_KEY_R -> {
                if (!ctrl) {
                    viewport.reset();
                    status = "View reset";
                    return true;
                }
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (shift) jumpKey(-1);
                else {
                    timeline.step(-1);
                    applyPlayhead();
                }
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (shift) jumpKey(1);
                else {
                    timeline.step(1);
                    applyPlayhead();
                }
                return true;
            }
            case GLFW.GLFW_KEY_Z -> {
                if (ctrl) {
                    undo();
                    return true;
                }
            }
            case GLFW.GLFW_KEY_Y -> {
                if (ctrl) {
                    redo();
                    return true;
                }
            }
            default -> { }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void openOverlay(Overlay which) {
        overlay = overlay == which ? Overlay.NONE : which;
        overlayScroll = 0;
        overlayRows = switch (overlay) {
            case HELP -> List.of(HELP_LINES);
            case LOAD -> XenoClipSources.list(loadSource);
            case BIND, UNBIND -> bindRows();
            case MOTION -> motionRows();
            case NONE -> List.of();
        };
    }

    private List<String> bindRows() {
        List<String> rows = new ArrayList<>();
        Map<Bt3AnimationIntent, String> local = StudioClipBindings.all();
        for (TechniqueAnimSlot slot : TechniqueAnimSlot.values()) {
            String clip = XenoTechniqueAnimBindingsClient.clipFor(slot);
            rows.add(slot.name() + (clip == null ? "   (server)" : "   -> " + clip));
        }
        for (Bt3AnimationIntent intent : Bt3AnimationIntent.values()) {
            String clip = XenoTechniqueAnimBindingsClient.clipFor(intent);
            if (clip == null) clip = local.get(intent);
            rows.add(intent.name() + (clip == null ? "" : "   -> " + clip));
        }
        return rows;
    }

    private int overlayRowAt(double mouseX, double mouseY) {
        if (overlay == Overlay.NONE || overlayRows.isEmpty()) return -1;
        if (mouseX < viewX1 + 8 || mouseX > viewX2 - 8) return -1;
        int first = viewY1 + 24;
        if (mouseY < first) return -1;
        int index = (int) ((mouseY - first) / 11) + overlayScroll;
        return index >= 0 && index < overlayRows.size() ? index : -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (overlay == Overlay.NONE && in(mouseX, mouseY, viewX1, viewY1, viewX2, viewY2)) {
            viewport.scroll(dy);
            return true;
        }
        if (overlay != Overlay.NONE) {
            overlayScroll = Mth.clamp(overlayScroll - (int) Math.signum(dy) * 3, 0,
                    Math.max(0, overlayRows.size() - visibleOverlayRows()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    private int visibleOverlayRows() {
        return Math.max(1, (viewY2 - viewY1 - 32) / 11);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (overlay != Overlay.NONE) {
            int row = overlayRowAt(mouseX, mouseY);
            if (row >= 0) {
                if (overlay == Overlay.LOAD) {
                    loadClip(overlayRows.get(row));
                    overlay = Overlay.NONE;
                    return true;
                }
                if (overlay == Overlay.MOTION) {
                    if (row == 0) addMotion();
                    else if (row == 1) bakeMotions();
                    else if (row >= 3) removeMotion(row - 3);
                    return true;
                }
                if (overlay == Overlay.BIND || overlay == Overlay.UNBIND) {
                    String slot = overlayRows.get(row).split("\\s+")[0];
                    if (overlay == Overlay.UNBIND || button == 1) {
                        unbindServerSlot(slot);
                    } else {
                        bindServerSlot(slot);
                    }
                    overlayRows = bindRows();
                    return true;
                }
            }
            if (in(mouseX, mouseY, viewX1, viewY1, viewX2, viewY2)) return true;
        }

        int dockTop = TOP + 8;
        for (int i = 0; i < XenoRig.COMBAT.size(); i++) {
            int y = dockTop + 16 + i * 18;
            if (mouseX >= 10 && mouseX <= LEFT - 10 && mouseY >= y && mouseY <= y + 16) {
                selected = XenoRig.COMBAT.get(i);
                status = "Editing " + selected;
                return true;
            }
        }
        if (in(mouseX, mouseY, railX1, railY - 4, railX2, railY + 18)) {
            seekFromMouse(mouseX);
            playing = false;
            refreshTransport();
            return true;
        }
        dragAxis = sliderAxis(mouseX, mouseY);
        if (dragAxis >= 0) {
            pushHistory();
            slide(mouseX);
            return true;
        }
        if (in(mouseX, mouseY, viewX1, viewY1, viewX2, viewY2)) {
            viewport.beginOrbit(button == 2 || hasShiftDown());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragAxis >= 0) {
            slide(mouseX);
            return true;
        }
        if (viewport.interacting() && viewport.drag(dx, dy)) {
            return true;
        }
        if (in(mouseX, mouseY, railX1, railY - 4, railX2, railY + 18)) {
            seekFromMouse(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragAxis = -1;
        viewport.endDrag();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void seekFromMouse(double mouseX) {
        double t = (mouseX - railX1) / Math.max(1, railX2 - railX1);
        timeline.seek(Mth.clamp(t, 0.0, 1.0) * timeline.durationSeconds());
        applyPlayhead();
    }

    private int sliderAxis(double mx, double my) {
        if (mx < sliderX1() || mx > sliderX2()) return -1;
        for (int axis = 0; axis < 3; axis++) {
            int y = axisRowY(axis);
            if (my >= y && my <= y + 14) return axis;
        }
        return -1;
    }

    private void slide(double mouseX) {
        int x1 = sliderX1();
        int x2 = sliderX2();
        float t = Mth.clamp((float) ((mouseX - x1) / (double) Math.max(1, x2 - x1)), 0f, 1f);
        float value = CHANNEL_MIN[channel] + t * (CHANNEL_MAX[channel] - CHANNEL_MIN[channel]);
        setAxis(StudioPoseBuffer.bone(selected), dragAxis, value);
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        layout();
        graphics.fill(0, 0, width, height, COL_BG);
        graphics.fill(0, 0, width, TOP, 0xF0121A28);
        graphics.fill(0, TOP - 1, width, TOP, COL_ACCENT);
        graphics.fill(0, TOP, LEFT, height - BOTTOM, COL_DOCK);
        graphics.fill(width - RIGHT, TOP, width, height - BOTTOM, COL_DOCK);
        graphics.fill(0, height - BOTTOM, width, height, 0xF0121A28);
        graphics.fill(0, height - BOTTOM, width, height - BOTTOM + 1, COL_LINE);

        drawViewport(graphics, mouseX, mouseY);
        drawBones(graphics);
        drawInspector(graphics);
        drawTimeline(graphics);

        boolean rec = XenoAnimRecorder.recording();
        graphics.drawString(font, rec ? "LIVE REC" : (playing ? "PLAYING" : "STOPPED"),
                width - RIGHT + 10, height - BOTTOM - 14,
                rec ? COL_REC : (playing ? COL_PLAY : COL_MUTED), false);
        graphics.drawString(font, status, 8, height - 12, COL_MUTED, false);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (overlay != Overlay.NONE) drawOverlay(graphics);
    }

    private void drawViewport(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(viewX1, viewY1, viewX2, viewY2, 0xFF070A12);
        graphics.fill(viewX1, viewY1, viewX2, viewY1 + 1, COL_ACCENT);
        graphics.fill(viewX1, viewY2 - 1, viewX2, viewY2, COL_LINE);
        graphics.fill(viewX1, viewY1, viewX1 + 1, viewY2, COL_LINE);
        graphics.fill(viewX2 - 1, viewY1, viewX2, viewY2, COL_LINE);
        graphics.drawString(font, "SCENE", viewX1 + 8, viewY1 + 6, COL_ACCENT, false);
        graphics.drawString(font, timecode(timeline.playhead()) + "  /  "
                + timecode(timeline.durationSeconds()), viewX1 + 52, viewY1 + 6, COL_TEXT, false);

        viewport.drawGround(graphics, viewX1 + 2, viewY1 + 16, viewX2 - 2, viewY2 - 2,
                0x3DDCFF, 0x553DDCFF);

        if (minecraft != null && minecraft.player != null && viewX2 - viewX1 > 40 && viewY2 - viewY1 > 40) {
            int scale = modelScale();
            if (onion) drawOnionSkin(graphics, scale);
            viewport.render(graphics, viewX1 + 2, viewY1 + 16, viewX2 - 2, viewY2 - 2,
                    minecraft.player, scale);
            graphics.drawString(font, viewport.readout(), viewX1 + 8, viewY2 - 14, COL_MUTED, false);
        }

        if (XenoAnimRecorder.recording()) {
            graphics.fill(viewX2 - 52, viewY1 + 4, viewX2 - 8, viewY1 + 16, COL_REC);
            graphics.drawCenteredString(font, "REC", viewX2 - 30, viewY1 + 6, 0xFFFFFFFF);
        } else if (playing) {
            graphics.fill(viewX2 - 58, viewY1 + 4, viewX2 - 8, viewY1 + 16, 0xFF146B3A);
            graphics.drawCenteredString(font, "PLAY", viewX2 - 33, viewY1 + 6, COL_PLAY);
        }
    }

    /**
     * Ghosts the neighbouring keys behind the live pose.
     *
     * <p>Drawn by swapping the pose buffer, re-rendering the same entity offset sideways through
     * the same camera, then restoring. The vanilla inventory-entity helper takes no alpha and
     * {@code RenderSystem.setShaderColor} does not reliably reach GeckoLib's render types, so the
     * ghosts are faded by compositing instead: both are drawn first, a scrim of the viewport's own
     * background is laid over them, and the live pose goes on top at full strength. The result on
     * screen is translucent ghosts, using nothing but a filled rectangle.
     */
    private void drawOnionSkin(GuiGraphics graphics, int scale) {
        XenoAnimClip clip = timeline.clip();
        if (clip == null || clip.isEmpty() || minecraft == null || minecraft.player == null) return;
        Map<String, AnimBonePose> live = StudioPoseBuffer.snapshot();
        Double prev = clip.prevKeyTime(timeline.playhead());
        Double next = clip.nextKeyTime(timeline.playhead());
        if (prev == null && next == null) return;
        try {
            if (prev != null) drawGhost(graphics, prev, scale, -16);
            if (next != null) drawGhost(graphics, next, scale, 16);
        } finally {
            StudioPoseBuffer.putAll(live);
        }
        // The scrim: everything drawn so far fades towards the background, the live pose does not.
        graphics.fill(viewX1 + 2, viewY1 + 16, viewX2 - 2, viewY2 - 2, COL_GHOST_SCRIM);
    }

    private void drawGhost(GuiGraphics graphics, double time, int scale, int offset) {
        Map<String, AnimBonePose> pose = timeline.poseAt(time);
        if (pose.isEmpty()) return;
        StudioPoseBuffer.putAll(pose);
        viewport.render(graphics, viewX1 + 2, viewY1 + 16, viewX2 - 2, viewY2 - 2,
                minecraft.player, scale, offset, 0f);
    }

    /** One block is this many pixels tall before zoom; sized to the viewport, not the window. */
    private int modelScale() {
        return Mth.clamp((viewY2 - viewY1) / 3, 24, 110);
    }

    private void drawBones(GuiGraphics graphics) {
        graphics.drawString(font, "BONES", 10, TOP + 6, COL_MUTED, false);
        XenoAnimClip clip = timeline.clip();
        int y = TOP + 24;
        for (String bone : XenoRig.COMBAT) {
            boolean on = bone.equals(selected);
            graphics.fill(10, y, LEFT - 10, y + 16, on ? 0xFF1A3A4E : COL_PANEL);
            if (on) graphics.fill(10, y, 13, y + 16, COL_ACCENT);
            graphics.drawString(font, bone.replace('_', ' '), 18, y + 4, on ? COL_ACCENT : COL_TEXT, false);
            if (clip != null && clip.hasKeyAt(bone, timeline.playhead())) {
                graphics.fill(LEFT - 16, y + 6, LEFT - 12, y + 10, COL_KEY);
            }
            y += 18;
        }
        graphics.drawString(font, "key here", 10, y + 4, COL_MUTED, false);
        graphics.fill(LEFT - 16, y + 6, LEFT - 12, y + 10, COL_KEY);
    }

    private void drawInspector(GuiGraphics graphics) {
        int x = inspectorX();
        graphics.drawString(font, selected.replace('_', ' ').toUpperCase(java.util.Locale.ROOT),
                x, TOP + 4, COL_TEXT, false);
        AnimBonePose pose = StudioPoseBuffer.bone(selected);
        drawSlider(graphics, axisRowY(0), getAxis(pose, 0), "X");
        drawSlider(graphics, axisRowY(1), getAxis(pose, 1), "Y");
        drawSlider(graphics, axisRowY(2), getAxis(pose, 2), "Z");
        graphics.drawString(font, fmt(pose), x, VALUE_ROW_Y, COL_MUTED, false);
    }

    private void drawSlider(GuiGraphics graphics, int y, float value, String axis) {
        int x1 = sliderX1();
        int x2 = sliderX2();
        graphics.fill(x1, y, x2, y + 14, 0xFF0C121C);
        float span = CHANNEL_MAX[channel] - CHANNEL_MIN[channel];
        float t = Mth.clamp((value - CHANNEL_MIN[channel]) / span, 0f, 1f);
        int kx = x1 + Math.round(t * (x2 - x1 - 4));
        graphics.fill(x1, y + 6, x2, y + 8, COL_LINE);
        graphics.fill(kx, y, kx + 4, y + 14, COL_ACCENT);
        String text = channel == CHANNEL_ROT
                ? String.format(java.util.Locale.ROOT, "%s %+.0f", axis, value)
                : String.format(java.util.Locale.ROOT, "%s %+.2f", axis, value);
        graphics.drawString(font, text, x1 + 3, y + 3, COL_MUTED, false);
    }

    private void drawTimeline(GuiGraphics graphics) {
        graphics.drawString(font, "TIMELINE", 8, height - 50, COL_MUTED, false);
        double span = Math.max(1.0 / XenoAnimClip.TICKS_PER_SECOND, timeline.durationSeconds());
        XenoAnimClip clip = timeline.clip();

        // One row per channel of the selected bone, so it is visible at a glance that rotation
        // and position are keyed at times of their own rather than sharing one.
        AnimBoneTrack track = clip == null ? null : clip.trackIfPresent(selected);
        AnimChannel.Kind[] kinds = {AnimChannel.Kind.ROTATION, AnimChannel.Kind.POSITION,
                AnimChannel.Kind.SCALE, AnimChannel.Kind.VISIBILITY};
        String[] rowLabels = {"R", "P", "S", "V"};
        for (int row = 0; row < kinds.length; row++) {
            int ry = boneRailY + row * 4;
            graphics.fill(railX1, ry, railX2, ry + 3, 0xFF0A0F18);
            graphics.drawString(font, rowLabels[row], railX1 - 8, ry - 2,
                    row == channelRow() ? COL_ACCENT : COL_MUTED, false);
            if (track == null) continue;
            int color = row == channelRow() ? COL_ACCENT : 0xFF43617F;
            for (double time : track.channel(kinds[row]).times()) {
                int kx = railX(time, span);
                graphics.fill(kx - 1, ry, kx + 2, ry + 3, color);
            }
        }

        graphics.fill(railX1, railY, railX2, railY + 14, 0xFF0C121C);
        for (int second = 0; second <= span; second++) {
            int tx = railX(second, span);
            graphics.fill(tx, railY + 11, tx + 1, railY + 14, COL_LINE);
        }
        if (clip != null) {
            for (double time : clip.keyTimes()) {
                int kx = railX(time, span);
                boolean atPlayhead = Math.abs(time - timeline.playhead()) < 0.001;
                graphics.fill(kx - 1, railY + 2, kx + 2, railY + 12, atPlayhead ? COL_TEXT : COL_KEY);
            }
        }
        int px = railX(timeline.playhead(), span);
        graphics.fill(px, railY - 2, px + 2, railY + 16, COL_ACCENT);

        String counts = (clip == null ? 0 : clip.keyCount()) + " keys  "
                + timecode(timeline.playhead()) + " / " + timecode(span)
                + (timeline.snap() ? "" : "  free");
        graphics.drawString(font, counts, railX2 - font.width(counts), height - 50, COL_TEXT, false);
    }

    /** Which of the four timeline rows the inspector is pointed at. */
    private int channelRow() {
        return switch (channel) {
            case CHANNEL_POS -> 1;
            case CHANNEL_SCALE -> 2;
            default -> 0;
        };
    }

    private int railX(double seconds, double span) {
        return railX1 + (int) ((railX2 - railX1) * (seconds / span));
    }

    private void drawOverlay(GuiGraphics graphics) {
        graphics.fill(viewX1, viewY1, viewX2, viewY2, 0xF2060A12);
        graphics.fill(viewX1, viewY1, viewX2, viewY1 + 1, COL_ACCENT);
        String title = switch (overlay) {
            case HELP -> "HELP  -  click HELP again to close";
            case LOAD -> "LOAD A CLIP  -  click a name";
            case BIND -> "BIND THIS CLIP TO A LIVE SLOT  -  click to bind, right-click to unbind";
            case UNBIND -> "UNBIND A LIVE SLOT  -  restore the shipped clip for joiners";
            case MOTION -> "CONTINUOUS MOTION  -  click a row";
            case NONE -> "";
        };
        graphics.drawString(font, title, viewX1 + 8, viewY1 + 8, COL_ACCENT, false);
        if (overlayRows.isEmpty()) {
            graphics.drawString(font, overlay == Overlay.LOAD
                            ? "Nothing in " + loadSource.label + " - press SAVE, or switch SRC"
                            : "Nothing to show",
                    viewX1 + 8, viewY1 + 26, COL_MUTED, false);
            return;
        }
        int y = viewY1 + 24;
        int visible = visibleOverlayRows();
        for (int i = overlayScroll; i < Math.min(overlayRows.size(), overlayScroll + visible); i++) {
            String row = overlayRows.get(i);
            int color = row.startsWith("  ") || row.isBlank() ? COL_MUTED : COL_TEXT;
            if ((overlay == Overlay.BIND || overlay == Overlay.UNBIND) && row.contains("->")) {
                color = COL_KEY;
            }
            graphics.drawString(font, row, viewX1 + 8, y, color, false);
            y += 11;
        }
        if (overlayRows.size() > visible) {
            graphics.drawString(font, "scroll for more", viewX1 + 8, viewY2 - 12, COL_MUTED, false);
        }
    }

    private static boolean in(double mx, double my, int x1, int y1, int x2, int y2) {
        return mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }

    private static String timecode(double seconds) {
        return timecode((int) Math.round(Math.max(0.0, seconds) * XenoAnimClip.TICKS_PER_SECOND));
    }

    private static String timecode(int ticks) {
        int sec = Math.max(0, ticks) / XenoAnimClip.TICKS_PER_SECOND;
        int fr = Math.max(0, ticks) % XenoAnimClip.TICKS_PER_SECOND;
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", sec / 60, sec % 60, fr);
    }

    private String fmt(AnimBonePose pose) {
        return switch (channel) {
            case CHANNEL_POS -> String.format(java.util.Locale.ROOT, "pos %+.2f %+.2f %+.2f",
                    pose.posX, pose.posY, pose.posZ);
            case CHANNEL_SCALE -> String.format(java.util.Locale.ROOT, "scale %.2f %.2f %.2f",
                    pose.scaleX, pose.scaleY, pose.scaleZ);
            default -> String.format(java.util.Locale.ROOT, "rot %+4.0f %+4.0f %+4.0f",
                    pose.rotX, pose.rotY, pose.rotZ);
        };
    }

    @Override
    public void onClose() {
        playing = false;
        if (!net.bullettrain.xenopixelsmod.client.anim.XenoAnimPlayer.playing()) {
            StudioPoseBuffer.setActive(false);
        }
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        playing = false;
        if (!net.bullettrain.xenopixelsmod.client.anim.XenoAnimPlayer.playing()) {
            StudioPoseBuffer.setActive(false);
        }
        super.removed();
    }
}

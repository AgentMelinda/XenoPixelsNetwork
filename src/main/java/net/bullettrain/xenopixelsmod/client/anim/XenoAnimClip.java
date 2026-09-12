package net.bullettrain.xenopixelsmod.client.anim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.bullettrain.xenopixelsmod.client.anim.studio.AnimBonePose;
import net.bullettrain.xenopixelsmod.client.anim.studio.AnimBoneTrack;
import net.bullettrain.xenopixelsmod.client.anim.studio.AnimChannel;
import net.bullettrain.xenopixelsmod.client.anim.studio.AnimEasing;
import net.bullettrain.xenopixelsmod.client.anim.studio.AnimKey;
import net.bullettrain.xenopixelsmod.client.anim.studio.AnimOscillator;
import net.bullettrain.xenopixelsmod.client.anim.studio.XenoRig;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * An in-game authored clip, read from and written as GeckoLib 1.8 animation JSON.
 *
 * <p>The clip is a map of bone to {@link AnimBoneTrack}, each track holding independent rotation,
 * position, scale and visibility channels with their own key times in seconds. That is the shape
 * the file format already has. An earlier model collapsed everything into one list of whole-tick
 * keys shared by every bone, which is why two bones could not be keyed at different times and why
 * the exporter had to invent channel values it had never been given.
 */
public final class XenoAnimClip {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** The GeckoLib animation name prefix this mod owns. */
    public static final String ANIMATION_PREFIX = "combat.xeno_";

    /** Default scene length when a clip has no keys yet: 3 seconds. */
    public static final int DEFAULT_DURATION_TICKS = 60;

    /** Sidecar objects this mod adds beside {@code bones}; GeckoLib ignores unknown keys. */
    public static final String VISIBILITY_KEY = "xeno:visibility";
    public static final String BLEND_KEY = "xeno:blend";
    public static final String MOTION_KEY = "xeno:motion";

    public static final int TICKS_PER_SECOND = 20;

    public record Frame(int tick, float bodyYaw, float headPitch, float attackAnim, boolean shift) {}

    /** A sound fired at a point in the clip. */
    public record SoundEvent(double time, String sound) {}

    /** A particle effect fired at a point in the clip, optionally anchored to a locator bone. */
    public record ParticleEvent(double time, String effect, String locator, String script) {}

    /** A free-text instruction fired at a point in the clip, for scripts to react to. */
    public record InstructionEvent(double time, String instruction) {}

    public final String name;

    /** Raw live-recording capture; see {@link XenoAnimRecorder}. */
    public final List<Frame> frames = new ArrayList<>();

    /** Bone name to its animated channels. Insertion-ordered so exports stay stable. */
    public final Map<String, AnimBoneTrack> bones = new LinkedHashMap<>();

    public final List<SoundEvent> sounds = new ArrayList<>();
    public final List<ParticleEvent> particles = new ArrayList<>();
    public final List<InstructionEvent> instructions = new ArrayList<>();

    /**
     * Continuous motion evaluated on top of the keys, rather than keyed by hand.
     *
     * <p>Live only on the playback paths this mod owns. {@code BAKE MOTION} turns one into real
     * keys so it also survives into a plain GeckoLib file and into a bound combat move.
     */
    public final List<AnimOscillator> motions = new ArrayList<>();

    /** Authored scene length in ticks, 0 when the clip has never declared one. */
    public int durationTicks;

    /** Authored loop flag, round-tripped through the file. */
    public boolean loop;

    /** Ticks spent easing the pose in at the start and out at the end, so nothing snaps. */
    public int blendInTicks = 3;
    public int blendOutTicks = 3;

    public XenoAnimClip(String name) {
        this.name = sanitize(name);
    }

    // ---------------------------------------------------------------- tracks

    /** The track for {@code bone}, created on first use. */
    public AnimBoneTrack track(String bone) {
        return bones.computeIfAbsent(bone, b -> new AnimBoneTrack());
    }

    /** The track for {@code bone}, or null when the bone has never been keyed. */
    public AnimBoneTrack trackIfPresent(String bone) {
        return bones.get(bone);
    }

    public boolean isEmpty() {
        if (!motions.isEmpty()) return false;
        for (AnimBoneTrack track : bones.values()) {
            if (!track.isEmpty()) return false;
        }
        return true;
    }

    /** Every time any channel of any bone is keyed at, in order. */
    public NavigableSet<Double> keyTimes() {
        TreeSet<Double> all = new TreeSet<>();
        for (AnimBoneTrack track : bones.values()) all.addAll(track.times());
        return all;
    }

    /** How many distinct times carry a key; what the timeline counts. */
    public int keyCount() {
        return keyTimes().size();
    }

    public boolean hasKeyAt(double seconds) {
        for (AnimBoneTrack track : bones.values()) {
            if (track.hasKeyAt(seconds)) return true;
        }
        return false;
    }

    public boolean hasKeyAt(String bone, double seconds) {
        AnimBoneTrack track = bones.get(bone);
        return track != null && track.hasKeyAt(seconds);
    }

    /** Removes every key at {@code seconds} across every bone and channel. */
    public boolean removeKeysAt(double seconds) {
        boolean removed = false;
        for (AnimBoneTrack track : bones.values()) removed |= track.removeKeysAt(seconds);
        return removed;
    }

    public boolean removeKeysAt(String bone, double seconds) {
        AnimBoneTrack track = bones.get(bone);
        return track != null && track.removeKeysAt(seconds);
    }

    public Double nextKeyTime(double seconds) {
        return keyTimes().higher(AnimChannel.quantise(seconds) + AnimChannel.TIME_EPSILON);
    }

    public Double prevKeyTime(double seconds) {
        return keyTimes().lower(AnimChannel.quantise(seconds) - AnimChannel.TIME_EPSILON);
    }

    public double lastTime() {
        double last = 0.0;
        for (AnimBoneTrack track : bones.values()) {
            Double time = track.lastTime();
            if (time != null && time > last) last = time;
        }
        if (!frames.isEmpty()) {
            last = Math.max(last, frames.get(frames.size() - 1).tick() / (double) TICKS_PER_SECOND);
        }
        for (SoundEvent event : sounds) last = Math.max(last, event.time());
        for (ParticleEvent event : particles) last = Math.max(last, event.time());
        for (InstructionEvent event : instructions) last = Math.max(last, event.time());
        return last;
    }

    public int lastKeyTick() {
        return (int) Math.round(lastTime() * TICKS_PER_SECOND);
    }

    public float lengthSeconds() {
        return (float) lastTime();
    }

    /**
     * The scene length to play and export at: the authored duration when there is one, otherwise
     * the last key, and {@link #DEFAULT_DURATION_TICKS} while the clip is still empty so there is
     * somewhere to put a first key.
     */
    public int sceneTicks() {
        int last = lastKeyTick();
        if (durationTicks > 0) return Math.max(durationTicks, last);
        return last > 0 ? last : DEFAULT_DURATION_TICKS;
    }

    // ---------------------------------------------------------------- keying

    /** Writes every channel each supplied pose owns, at {@code seconds}. */
    public void keyPose(double seconds, Map<String, AnimBonePose> poses, String easing) {
        if (poses == null) return;
        poses.forEach((bone, pose) -> {
            if (pose != null) track(bone).key(seconds, pose, easing);
        });
    }

    public void keyPose(double seconds, Map<String, AnimBonePose> poses) {
        keyPose(seconds, poses, easingAt(seconds));
    }

    /** Writes one bone at {@code seconds}. */
    public void keyBone(double seconds, String bone, AnimBonePose pose) {
        if (pose == null) return;
        track(bone).key(seconds, pose, easingAt(seconds));
    }

    /**
     * Whole-tick convenience kept for {@link XenoAnimRecorder} and callers that think in ticks.
     * Per-bone merge semantics: bones absent from {@code poses} keep whatever they had there.
     */
    public void addKey(int tick, Map<String, AnimBonePose> poses) {
        keyPose(tick / (double) TICKS_PER_SECOND, poses);
    }

    public boolean hasKeyAtTick(int tick) {
        return hasKeyAt(tick / (double) TICKS_PER_SECOND);
    }

    public boolean removeKey(int tick) {
        return removeKeysAt(tick / (double) TICKS_PER_SECOND);
    }

    /** The pose {@code bone} holds at {@code seconds}, or null when it is never keyed. */
    public AnimBonePose poseAt(String bone, double seconds) {
        AnimBoneTrack track = bones.get(bone);
        boolean driven = hasMotionFor(bone);
        if ((track == null || track.isEmpty()) && !driven) return null;
        AnimBonePose pose = track == null ? new AnimBonePose() : track.poseAt(seconds);
        applyMotions(bone, pose, seconds);
        return pose;
    }

    private boolean hasMotionFor(String bone) {
        for (AnimOscillator motion : motions) {
            if (motion.bone().equals(bone)) return true;
        }
        return false;
    }

    private void applyMotions(String bone, AnimBonePose pose, double seconds) {
        for (AnimOscillator motion : motions) {
            if (motion.bone().equals(bone)) motion.applyTo(pose, seconds);
        }
    }

    /** Every keyed bone's pose at {@code seconds}. Bones with no track are left out. */
    public Map<String, AnimBonePose> poseAt(double seconds) {
        Map<String, AnimBonePose> out = new LinkedHashMap<>();
        bones.forEach((bone, track) -> {
            if (!track.isEmpty()) out.put(bone, track.poseAt(seconds));
        });
        for (AnimOscillator motion : motions) {
            // A bone an oscillator drives is animated even with no keys of its own.
            AnimBonePose pose = out.computeIfAbsent(motion.bone(), b -> new AnimBonePose());
            motion.applyTo(pose, seconds);
        }
        return out;
    }

    /** The easing shared by the keys at {@code seconds}, or linear when nothing is keyed there. */
    public String easingAt(double seconds) {
        for (AnimBoneTrack track : bones.values()) {
            for (AnimChannel channel : channels(track)) {
                AnimKey key = channel.at(seconds);
                if (key != null) return key.easing;
            }
        }
        return AnimEasing.LINEAR;
    }

    /** Sets the easing of every key at {@code seconds}. */
    public boolean setEasingAt(double seconds, String easing) {
        String id = AnimEasing.sanitize(easing);
        boolean any = false;
        for (AnimBoneTrack track : bones.values()) {
            for (AnimChannel channel : channels(track)) {
                AnimKey key = channel.at(seconds);
                if (key != null) {
                    key.easing = id;
                    any = true;
                }
            }
        }
        return any;
    }

    private static AnimChannel[] channels(AnimBoneTrack track) {
        return new AnimChannel[] {track.rotation, track.position, track.scale, track.visibility};
    }

    /** Recorder capture; also lays down the three bones the frame capture can observe. */
    public void add(Frame frame) {
        frames.add(frame);
        double time = frame.tick() / (double) TICKS_PER_SECOND;
        track("root").rotation.put(time, 0, frame.bodyYaw(), 0);
        track("head").rotation.put(time, frame.headPitch(), 0, 0);
        track("right_arm").rotation.put(time, -90f * frame.attackAnim(), 0, 0);
    }

    /** A copy of this clip under a different name; {@link #name} itself is immutable. */
    public XenoAnimClip renamed(String newName) {
        XenoAnimClip out = new XenoAnimClip(newName);
        out.durationTicks = durationTicks;
        out.loop = loop;
        out.blendInTicks = blendInTicks;
        out.blendOutTicks = blendOutTicks;
        out.frames.addAll(frames);
        bones.forEach((bone, track) -> out.bones.put(bone, track.copy()));
        out.sounds.addAll(sounds);
        out.particles.addAll(particles);
        out.instructions.addAll(instructions);
        out.motions.addAll(motions);
        return out;
    }

    /** The GeckoLib animation name this clip bakes under. */
    public String animationName() {
        return ANIMATION_PREFIX + name;
    }

    // ---------------------------------------------------------------- export

    public String toGeckoJson() {
        return toGeckoJson(loop, durationTicks);
    }

    /**
     * @param loop          written to the clip {@code loop} flag
     * @param durationTicks scene length; the export is never shorter than the last key, so a clip
     *                      that ends on a held pose keeps its tail
     */
    public String toGeckoJson(boolean loop, int durationTicks) {
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.8.0");
        JsonObject clip = new JsonObject();
        clip.addProperty("loop", loop);
        clip.addProperty("animation_length",
                Math.max(lastKeyTick(), Math.max(0, durationTicks)) / (float) TICKS_PER_SECOND);

        JsonObject boneObjects = new JsonObject();
        JsonObject visibility = new JsonObject();
        for (String bone : orderedBones()) {
            AnimBoneTrack track = bones.get(bone);
            if (track == null || track.isEmpty()) continue;
            JsonObject boneObj = new JsonObject();
            addChannel(boneObj, "rotation", track.rotation);
            addChannel(boneObj, "position", track.position);
            addChannel(boneObj, "scale", track.scale);
            if (boneObj.size() > 0) boneObjects.add(bone, boneObj);
            if (!track.visibility.isEmpty()) {
                JsonObject times = new JsonObject();
                for (var entry : track.visibility.entries()) {
                    times.addProperty(time(entry.getKey()), entry.getValue().x >= 0.5f);
                }
                visibility.add(bone, times);
            }
        }
        clip.add("bones", boneObjects);

        if (!sounds.isEmpty()) {
            JsonObject effects = new JsonObject();
            for (SoundEvent event : sounds) {
                JsonObject entry = new JsonObject();
                entry.addProperty("effect", event.sound());
                effects.add(time(event.time()), entry);
            }
            clip.add("sound_effects", effects);
        }
        if (!particles.isEmpty()) {
            JsonObject effects = new JsonObject();
            for (ParticleEvent event : particles) {
                JsonObject entry = new JsonObject();
                entry.addProperty("effect", event.effect());
                if (event.locator() != null && !event.locator().isBlank()) {
                    entry.addProperty("locator", event.locator());
                }
                if (event.script() != null && !event.script().isBlank()) {
                    entry.addProperty("pre_effect_script", event.script());
                }
                effects.add(time(event.time()), entry);
            }
            clip.add("particle_effects", effects);
        }
        if (!instructions.isEmpty()) {
            JsonObject timeline = new JsonObject();
            for (InstructionEvent event : instructions) {
                timeline.addProperty(time(event.time()), event.instruction());
            }
            clip.add("timeline", timeline);
        }
        if (visibility.size() > 0) clip.add(VISIBILITY_KEY, visibility);
        if (!motions.isEmpty()) {
            JsonArray array = new JsonArray();
            for (AnimOscillator motion : motions) {
                JsonObject entry = new JsonObject();
                entry.addProperty("bone", motion.bone());
                entry.addProperty("channel", motion.channel().name());
                entry.addProperty("axis", motion.axis());
                entry.addProperty("amplitude", motion.amplitude());
                entry.addProperty("period", motion.period());
                entry.addProperty("phase", motion.phase());
                entry.addProperty("wave", motion.wave().name());
                array.add(entry);
            }
            clip.add(MOTION_KEY, array);
        }
        if (blendInTicks != 3 || blendOutTicks != 3) {
            JsonObject blend = new JsonObject();
            blend.addProperty("in_ticks", blendInTicks);
            blend.addProperty("out_ticks", blendOutTicks);
            clip.add(BLEND_KEY, blend);
        }

        JsonObject animations = new JsonObject();
        animations.add(animationName(), clip);
        root.add("animations", animations);
        return GSON.toJson(root);
    }

    /** Rig bones first so exports read in skeleton order, then anything else that was keyed. */
    private List<String> orderedBones() {
        List<String> ordered = new ArrayList<>();
        for (String bone : XenoRig.COMBAT) {
            if (bones.containsKey(bone)) ordered.add(bone);
        }
        for (String bone : bones.keySet()) {
            if (!ordered.contains(bone)) ordered.add(bone);
        }
        return ordered;
    }

    private static void addChannel(JsonObject boneObj, String key, AnimChannel channel) {
        if (channel.isEmpty()) return;
        JsonObject times = new JsonObject();
        for (var entry : channel.entries()) {
            times.add(time(entry.getKey()), keyframe(entry.getValue()));
        }
        boneObj.add(key, times);
    }

    private static JsonObject keyframe(AnimKey key) {
        JsonObject entry = new JsonObject();
        JsonArray vector = new JsonArray();
        vector.add(key.x);
        vector.add(key.y);
        vector.add(key.z);
        entry.add("vector", vector);
        String id = AnimEasing.sanitize(key.easing);
        if (!AnimEasing.LINEAR.equals(id)) entry.addProperty("easing", id);
        return entry;
    }

    private static String time(double seconds) {
        return String.format(Locale.ROOT, "%.3f", seconds);
    }

    // ---------------------------------------------------------------- files

    public Path save() throws IOException {
        return save(loop, durationTicks);
    }

    public Path save(boolean loop, int durationTicks) throws IOException {
        Path dir = dir();
        Files.createDirectories(dir);
        Path file = dir.resolve(name + ".animation.json");
        this.loop = loop;
        this.durationTicks = Math.max(0, durationTicks);
        Files.writeString(file, toGeckoJson(loop, durationTicks));
        return file;
    }

    public static List<String> listSaved() {
        Path dir = dir();
        if (!Files.isDirectory(dir)) return List.of();
        try (var stream = Files.list(dir)) {
            return stream.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".animation.json"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Saved clip names with the file suffix stripped. */
    public static List<String> listSavedNames() {
        return listSaved().stream().map(n -> n.replace(".animation.json", "")).toList();
    }

    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) return "clip";
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    public static Path dir() {
        return FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-anims");
    }

    public static XenoAnimClip load(String rawName) throws IOException {
        String base = sanitize(rawName.replace(".animation.json", ""));
        Path file = dir().resolve(base + ".animation.json");
        if (!Files.isRegularFile(file)) {
            throw new IOException("No clip: " + base);
        }
        return fromGeckoJson(base, Files.readString(file));
    }

    // ---------------------------------------------------------------- import

    /** {@code animation_length} in ticks as stored in the file, or 0 when absent. */
    public static int durationTicksOf(String json) {
        JsonObject anim = firstAnimation(json);
        if (anim == null || !anim.has("animation_length")) return 0;
        try {
            return Math.round(anim.get("animation_length").getAsFloat() * TICKS_PER_SECOND);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Whether the single animation in the file is flagged looping. */
    public static boolean loopOf(String json) {
        JsonObject anim = firstAnimation(json);
        return anim != null && readLoop(anim);
    }

    private static boolean readLoop(JsonObject anim) {
        JsonElement loop = anim.get("loop");
        if (loop == null || !loop.isJsonPrimitive()) return false;
        return loop.getAsJsonPrimitive().isBoolean()
                ? loop.getAsBoolean()
                : "loop".equalsIgnoreCase(loop.getAsString());
    }

    private static JsonObject firstAnimation(String json) {
        JsonObject root;
        try {
            root = GSON.fromJson(json, JsonObject.class);
        } catch (RuntimeException e) {
            return null;
        }
        JsonObject animations = root == null ? null : root.getAsJsonObject("animations");
        if (animations == null || animations.entrySet().isEmpty()) return null;
        JsonElement first = animations.entrySet().iterator().next().getValue();
        return first != null && first.isJsonObject() ? first.getAsJsonObject() : null;
    }

    public static XenoAnimClip fromGeckoJson(String fallbackName, String json) {
        JsonObject root;
        try {
            root = GSON.fromJson(json, JsonObject.class);
        } catch (RuntimeException e) {
            return new XenoAnimClip(fallbackName);
        }
        JsonObject animations = root == null ? null : root.getAsJsonObject("animations");
        if (animations == null || animations.entrySet().isEmpty()) {
            return new XenoAnimClip(fallbackName);
        }
        var first = animations.entrySet().iterator().next();
        return fromAnimationObject(fallbackName, first.getKey(),
                first.getValue().isJsonObject() ? first.getValue().getAsJsonObject() : null);
    }

    /** Reads one named animation object, for files holding many - the shipped BT3 file does. */
    public static XenoAnimClip fromAnimationObject(String fallbackName, String animName,
                                                   JsonObject anim) {
        String clipName = animName != null && animName.startsWith(ANIMATION_PREFIX)
                ? animName.substring(ANIMATION_PREFIX.length())
                : sanitize(animName == null || animName.isBlank() ? fallbackName : animName);
        XenoAnimClip clip = new XenoAnimClip(clipName);
        if (anim == null) return clip;

        JsonObject boneObjects = anim.getAsJsonObject("bones");
        if (boneObjects != null) {
            for (var boneEntry : boneObjects.entrySet()) {
                if (!boneEntry.getValue().isJsonObject()) continue;
                JsonObject boneObj = boneEntry.getValue().getAsJsonObject();
                AnimBoneTrack track = clip.track(normaliseBone(boneEntry.getKey()));
                readChannel(track.rotation, boneObj.get("rotation"));
                readChannel(track.position, boneObj.get("position"));
                readChannel(track.scale, boneObj.get("scale"));
            }
        }

        JsonObject visibility = anim.getAsJsonObject(VISIBILITY_KEY);
        if (visibility != null) {
            for (var boneEntry : visibility.entrySet()) {
                if (!boneEntry.getValue().isJsonObject()) continue;
                AnimChannel channel = clip.track(normaliseBone(boneEntry.getKey())).visibility;
                for (var timeEntry : boneEntry.getValue().getAsJsonObject().entrySet()) {
                    Double time = parseTime(timeEntry.getKey());
                    if (time == null) continue;
                    boolean visible = true;
                    try {
                        visible = timeEntry.getValue().getAsBoolean();
                    } catch (RuntimeException ignored) {
                        // A non-boolean here means the sidecar was hand-edited; assume visible.
                    }
                    channel.put(time, visible ? 1f : 0f, 0f, 0f);
                }
            }
        }

        readSounds(clip, anim.getAsJsonObject("sound_effects"));
        readParticles(clip, anim.getAsJsonObject("particle_effects"));
        readTimeline(clip, anim.getAsJsonObject("timeline"));

        JsonElement motionElement = anim.get(MOTION_KEY);
        if (motionElement != null && motionElement.isJsonArray()) {
            for (JsonElement element : motionElement.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject entry = element.getAsJsonObject();
                try {
                    clip.motions.add(new AnimOscillator(
                            stringOr(entry, "bone", null),
                            kindOf(stringOr(entry, "channel", "ROTATION")),
                            optionalInt(entry, "axis", 0),
                            entry.has("amplitude") ? entry.get("amplitude").getAsFloat() : 0f,
                            entry.has("period") ? entry.get("period").getAsDouble() : 1.0,
                            entry.has("phase") ? entry.get("phase").getAsDouble() : 0.0,
                            waveOf(stringOr(entry, "wave", "SINE"))));
                } catch (RuntimeException ignored) {
                    // A hand-edited motion entry that will not parse is dropped, not fatal.
                }
            }
        }

        JsonObject blend = anim.getAsJsonObject(BLEND_KEY);
        if (blend != null) {
            clip.blendInTicks = optionalInt(blend, "in_ticks", clip.blendInTicks);
            clip.blendOutTicks = optionalInt(blend, "out_ticks", clip.blendOutTicks);
        }

        if (anim.has("animation_length")) {
            try {
                clip.durationTicks =
                        Math.round(anim.get("animation_length").getAsFloat() * TICKS_PER_SECOND);
            } catch (RuntimeException ignored) {
                // Leave it at 0 and let sceneTicks() fall back to the last key.
            }
        }
        clip.loop = readLoop(anim);
        return clip;
    }

    private static String normaliseBone(String bone) {
        if ("rightArm".equals(bone)) return "right_arm";
        if ("leftArm".equals(bone)) return "left_arm";
        if ("rightLeg".equals(bone)) return "right_leg";
        if ("leftLeg".equals(bone)) return "left_leg";
        return bone;
    }

    private static void readChannel(AnimChannel channel, JsonElement track) {
        if (track == null || !track.isJsonObject()) return;
        for (var timeEntry : track.getAsJsonObject().entrySet()) {
            Double time = parseTime(timeEntry.getKey());
            if (time == null) continue;
            float[] xyz = readVector(timeEntry.getValue());
            AnimKey key = new AnimKey(xyz[0], xyz[1], xyz[2]);
            String easing = readEasing(timeEntry.getValue());
            if (easing != null) key.easing = easing;
            channel.put(time, key);
        }
    }

    private static void readSounds(XenoAnimClip clip, JsonObject effects) {
        if (effects == null) return;
        for (var entry : effects.entrySet()) {
            Double time = parseTime(entry.getKey());
            String effect = stringField(entry.getValue(), "effect");
            if (time != null && effect != null) clip.sounds.add(new SoundEvent(time, effect));
        }
    }

    private static void readParticles(XenoAnimClip clip, JsonObject effects) {
        if (effects == null) return;
        for (var entry : effects.entrySet()) {
            Double time = parseTime(entry.getKey());
            String effect = stringField(entry.getValue(), "effect");
            if (time == null || effect == null) continue;
            clip.particles.add(new ParticleEvent(time, effect,
                    stringField(entry.getValue(), "locator"),
                    stringField(entry.getValue(), "pre_effect_script")));
        }
    }

    private static void readTimeline(XenoAnimClip clip, JsonObject timeline) {
        if (timeline == null) return;
        for (var entry : timeline.entrySet()) {
            Double time = parseTime(entry.getKey());
            if (time == null) continue;
            JsonElement value = entry.getValue();
            if (value.isJsonArray()) {
                for (JsonElement line : value.getAsJsonArray()) {
                    clip.instructions.add(new InstructionEvent(time, line.getAsString()));
                }
            } else if (value.isJsonPrimitive()) {
                clip.instructions.add(new InstructionEvent(time, value.getAsString()));
            }
        }
    }

    private static String stringField(JsonElement element, String field) {
        if (element == null) return null;
        if (element.isJsonPrimitive()) return "effect".equals(field) ? element.getAsString() : null;
        if (!element.isJsonObject()) return null;
        JsonElement value = element.getAsJsonObject().get(field);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static String stringOr(JsonObject object, String field, String fallback) {
        JsonElement value = object.get(field);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static AnimChannel.Kind kindOf(String name) {
        for (AnimChannel.Kind kind : AnimChannel.Kind.values()) {
            if (kind.name().equalsIgnoreCase(name)) return kind;
        }
        return AnimChannel.Kind.ROTATION;
    }

    private static AnimOscillator.Wave waveOf(String name) {
        for (AnimOscillator.Wave wave : AnimOscillator.Wave.values()) {
            if (wave.name().equalsIgnoreCase(name)) return wave;
        }
        return AnimOscillator.Wave.SINE;
    }

    private static int optionalInt(JsonObject object, String field, int fallback) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive()) return fallback;
        try {
            return value.getAsInt();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static Double parseTime(String raw) {
        try {
            return AnimChannel.quantise(Double.parseDouble(raw));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String readEasing(JsonElement el) {
        if (el == null || !el.isJsonObject()) return null;
        JsonElement easing = el.getAsJsonObject().get("easing");
        if (easing == null || !easing.isJsonPrimitive()) return null;
        return AnimEasing.sanitize(easing.getAsString());
    }

    private static float[] readVector(JsonElement el) {
        float[] xyz = new float[3];
        JsonArray arr = null;
        if (el != null && el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            // Bedrock also allows a split keyframe. The value leaving the frame drives the next
            // segment, so "post" is preferred over "pre".
            for (String key : new String[] {"vector", "post", "pre"}) {
                JsonElement candidate = obj.get(key);
                if (candidate == null) continue;
                if (candidate.isJsonArray()) {
                    arr = candidate.getAsJsonArray();
                    break;
                }
                if (candidate.isJsonObject() && candidate.getAsJsonObject().has("vector")) {
                    arr = candidate.getAsJsonObject().getAsJsonArray("vector");
                    break;
                }
            }
        } else if (el != null && el.isJsonArray()) {
            arr = el.getAsJsonArray();
        }
        if (arr == null) return xyz;
        for (int i = 0; i < Math.min(3, arr.size()); i++) {
            try {
                xyz[i] = arr.get(i).getAsFloat();
            } catch (RuntimeException ignored) {
                // A math-expression keyframe such as "q.anim_time * 2" is not an editable constant.
            }
        }
        return xyz;
    }
}

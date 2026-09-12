package net.bullettrain.xenopixelsmod.client.anim.studio;

import net.bullettrain.xenopixelsmod.client.anim.XenoAnimClip;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded undo/redo over whole studio states.
 *
 * <p>Snapshots rather than commands: a studio state is a few dozen small objects, so copying all of
 * it is cheaper than writing an inverse for every edit and cannot drift out of sync with one. Pure
 * data with no Minecraft types, so it unit-tests directly.
 */
public final class AnimUndoStack {
    /** How many steps back the studio can go. */
    public static final int CAPACITY = 32;

    /** An immutable studio state. */
    public record Snapshot(Map<String, AnimBoneTrack> tracks,
                           List<XenoAnimClip.SoundEvent> sounds,
                           List<XenoAnimClip.ParticleEvent> particles,
                           List<XenoAnimClip.InstructionEvent> instructions,
                           Map<String, AnimBonePose> pose,
                           double playhead, int duration, boolean loop) {

        public static Snapshot of(XenoAnimClip clip, Map<String, AnimBonePose> pose,
                                  double playhead, int duration, boolean loop) {
            Map<String, AnimBoneTrack> tracks = new LinkedHashMap<>();
            List<XenoAnimClip.SoundEvent> sounds = new ArrayList<>();
            List<XenoAnimClip.ParticleEvent> particles = new ArrayList<>();
            List<XenoAnimClip.InstructionEvent> instructions = new ArrayList<>();
            if (clip != null) {
                clip.bones.forEach((bone, track) -> tracks.put(bone, track.copy()));
                sounds.addAll(clip.sounds);
                particles.addAll(clip.particles);
                instructions.addAll(clip.instructions);
            }
            Map<String, AnimBonePose> poseCopy = new LinkedHashMap<>();
            if (pose != null) {
                pose.forEach((name, value) ->
                        poseCopy.put(name, value == null ? new AnimBonePose() : value.copy()));
            }
            return new Snapshot(tracks, List.copyOf(sounds), List.copyOf(particles),
                    List.copyOf(instructions), Map.copyOf(poseCopy), playhead, duration, loop);
        }

        /** How many distinct key times this snapshot holds; used by tests and the status line. */
        public int keyCount() {
            java.util.TreeSet<Double> all = new java.util.TreeSet<>();
            for (AnimBoneTrack track : tracks.values()) all.addAll(track.times());
            return all.size();
        }

        /** Writes this snapshot back over a clip. */
        public void restore(XenoAnimClip clip) {
            if (clip == null) return;
            clip.bones.clear();
            tracks.forEach((bone, track) -> clip.bones.put(bone, track.copy()));
            clip.sounds.clear();
            clip.sounds.addAll(sounds);
            clip.particles.clear();
            clip.particles.addAll(particles);
            clip.instructions.clear();
            clip.instructions.addAll(instructions);
        }
    }

    private final Deque<Snapshot> undo = new ArrayDeque<>();
    private final Deque<Snapshot> redo = new ArrayDeque<>();

    /** Records the state as it was before an edit. Clears the redo branch, as editors do. */
    public void push(Snapshot before) {
        if (before == null) return;
        undo.push(before);
        while (undo.size() > CAPACITY) undo.removeLast();
        redo.clear();
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    /** @return the state to restore, or null when there is nothing to undo */
    public Snapshot undo(Snapshot current) {
        if (undo.isEmpty()) return null;
        if (current != null) redo.push(current);
        return undo.pop();
    }

    /** @return the state to restore, or null when there is nothing to redo */
    public Snapshot redo(Snapshot current) {
        if (redo.isEmpty()) return null;
        if (current != null) undo.push(current);
        return redo.pop();
    }

    public void clear() {
        undo.clear();
        redo.clear();
    }

    public int depth() {
        return undo.size();
    }
}

package net.bullettrain.xenopixelsmod.client.anim.studio;

import net.bullettrain.xenopixelsmod.client.anim.XenoAnimClip;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimUndoStackTest {

    private static AnimUndoStack.Snapshot snapshot(XenoAnimClip clip, double playhead) {
        return AnimUndoStack.Snapshot.of(clip,
                Map.of("head", AnimBonePose.rot((float) playhead, 0, 0)), playhead, 60, false);
    }

    @Test
    void undoAndRedoWalkTheHistory() {
        XenoAnimClip clip = new XenoAnimClip("history");
        AnimUndoStack history = new AnimUndoStack();

        history.push(snapshot(clip, 0.0));
        clip.keyPose(0.5, Map.of("head", AnimBonePose.rot(20, 0, 0)));
        AnimUndoStack.Snapshot second = snapshot(clip, 0.5);

        assertTrue(history.canUndo());
        AnimUndoStack.Snapshot restored = history.undo(second);
        assertEquals(0.0, restored.playhead(), 0.0001);
        assertEquals(0, restored.keyCount());

        assertTrue(history.canRedo());
        AnimUndoStack.Snapshot redone = history.redo(restored);
        assertEquals(0.5, redone.playhead(), 0.0001);
        assertEquals(1, redone.keyCount());
    }

    @Test
    void nothingToUndoReturnsNull() {
        AnimUndoStack history = new AnimUndoStack();
        assertFalse(history.canUndo());
        assertNull(history.undo(snapshot(new XenoAnimClip("x"), 0.0)));
        assertNull(history.redo(snapshot(new XenoAnimClip("x"), 0.0)));
    }

    @Test
    void aNewEditDropsTheRedoBranch() {
        XenoAnimClip clip = new XenoAnimClip("branch");
        AnimUndoStack history = new AnimUndoStack();
        history.push(snapshot(clip, 0.0));
        history.undo(snapshot(clip, 0.25));
        assertTrue(history.canRedo());
        history.push(snapshot(clip, 0.35));
        assertFalse(history.canRedo());
    }

    @Test
    void historyIsBounded() {
        XenoAnimClip clip = new XenoAnimClip("deep");
        AnimUndoStack history = new AnimUndoStack();
        for (int i = 0; i < AnimUndoStack.CAPACITY + 20; i++) {
            history.push(snapshot(clip, (i % 60) / 20.0));
        }
        assertEquals(AnimUndoStack.CAPACITY, history.depth());
    }

    @Test
    void snapshotsAreDetachedFromTheClipTheyCameFrom() {
        XenoAnimClip clip = new XenoAnimClip("detached");
        clip.keyPose(0.25, Map.of("head", AnimBonePose.rot(10, 0, 0)));
        AnimUndoStack.Snapshot taken = snapshot(clip, 0.25);

        clip.trackIfPresent("head").rotation.at(0.25).x = 90f;
        clip.keyPose(1.25, Map.of("head", AnimBonePose.rot(45, 0, 0)));

        taken.restore(clip);
        assertEquals(1, clip.keyCount());
        assertEquals(10f, clip.trackIfPresent("head").rotation.at(0.25).x, 0.001f);
    }

    @Test
    void eventsAreCapturedAndRestored() {
        XenoAnimClip clip = new XenoAnimClip("events");
        clip.sounds.add(new XenoAnimClip.SoundEvent(0.5, "minecraft:ui.button.click"));
        AnimUndoStack.Snapshot taken = snapshot(clip, 0.0);

        clip.sounds.clear();
        clip.particles.add(new XenoAnimClip.ParticleEvent(0.1, "minecraft:crit", null, null));

        taken.restore(clip);
        assertEquals(1, clip.sounds.size());
        assertTrue(clip.particles.isEmpty());
    }
}

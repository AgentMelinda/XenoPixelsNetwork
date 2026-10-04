package net.bullettrain.xenopixelsmod.npc.scene;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XenoNpcSceneEditTest {

    @Test
    void aLegalSayAndClipAreAccepted() {
        CompoundTag tag = scene();
        add(tag, 0, "SAY", "Hello");
        add(tag, 20, "CLIP", "wave");
        assertNull(XenoNpcScene.rejectPayload(tag));
    }

    @Test
    void aTickAboveTheLimitIsRejected() {
        CompoundTag tag = scene();
        add(tag, 1201, "SAY", "Late");
        assertTrue(XenoNpcScene.rejectPayload(tag).contains("invalid scene step"));
    }

    @Test
    void aBlankStepIsRejected() {
        CompoundTag tag = scene();
        add(tag, 0, "SAY", " ");
        assertTrue(XenoNpcScene.rejectPayload(tag).contains("invalid scene step"));
    }

    private static CompoundTag scene() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", "Intro");
        tag.put("Steps", new ListTag());
        return tag;
    }

    private static void add(CompoundTag tag, int time, String kind, String value) {
        CompoundTag step = new CompoundTag();
        step.putInt("Time", time);
        step.putString("Kind", kind);
        step.putString("Value", value);
        tag.getList("Steps", net.minecraft.nbt.Tag.TAG_COMPOUND).add(step);
    }
}

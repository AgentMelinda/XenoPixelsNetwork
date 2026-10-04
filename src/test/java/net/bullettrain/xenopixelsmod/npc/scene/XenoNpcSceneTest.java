package net.bullettrain.xenopixelsmod.npc.scene;

import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class XenoNpcSceneTest {
    @TempDir Path directory;

    @Test
    void timelinePersistsInOrderAndKeepsItsFileId() {
        XenoNpcScene scene = new XenoNpcScene("arrival", "Arrival");
        scene.add(new XenoNpcScene.Step(40, XenoNpcScene.Kind.CLIP, "nod"));
        scene.add(new XenoNpcScene.Step(0, XenoNpcScene.Kind.SAY, "Welcome"));
        XenoNpcWorldStore store = new XenoNpcWorldStore(directory);
        assertNull(store.put(XenoNpcStoreCategory.SCENES, "", scene.id(), scene.save()));
        XenoNpcWorldStore reloaded = new XenoNpcWorldStore(directory);
        reloaded.loadAll();
        XenoNpcScene read = XenoNpcScene.load("renamed", reloaded.get(
                XenoNpcStoreCategory.SCENES, "", "arrival"));
        assertEquals("renamed", read.id());
        assertEquals("Arrival", read.name());
        assertEquals(List.of(0, 40), read.steps().stream().map(XenoNpcScene.Step::time).toList());
        assertTrue(read.remove(0));
        assertEquals(List.of(40), read.steps().stream().map(XenoNpcScene.Step::time).toList());
    }

    @Test
    void invalidPayloadAndOversizedTimelineAreBounded() {
        XenoNpcScene scene = new XenoNpcScene("bounded", "Bounded");
        for (int i = 0; i < XenoNpcScene.MAX_STEPS; i++) {
            assertTrue(scene.add(new XenoNpcScene.Step(i, XenoNpcScene.Kind.SAY, "line")));
        }
        assertFalse(scene.add(new XenoNpcScene.Step(100, XenoNpcScene.Kind.SAY, "extra")));
        assertNull(XenoNpcScene.rejectPayload(scene.save()));
        assertThrows(IllegalArgumentException.class, () -> new XenoNpcScene.Step(
                XenoNpcScene.MAX_TIME + 1, XenoNpcScene.Kind.SAY, "late"));
        CompoundTag malformed = scene.save();
        ListTag list = malformed.getList("Steps", net.minecraft.nbt.Tag.TAG_COMPOUND);
        CompoundTag invalid = new CompoundTag();
        invalid.putInt("Time", -1);
        invalid.putString("Kind", "SAY");
        invalid.putString("Value", "bad");
        list.set(0, invalid);
        assertNotNull(XenoNpcScene.rejectPayload(malformed));
        assertEquals(XenoNpcScene.MAX_STEPS - 1, XenoNpcScene.load("bounded", malformed).steps().size());
        CompoundTag wrongType = new CompoundTag();
        wrongType.putString("Name", "Wrong");
        ListTag strings = new ListTag();
        strings.add(StringTag.valueOf("untyped"));
        wrongType.put("Steps", strings);
        assertNotNull(XenoNpcScene.rejectPayload(wrongType));
    }
}

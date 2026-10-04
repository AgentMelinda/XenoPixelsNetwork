package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip coverage for the model block, and for the merge shape the editor's save depends on.
 */
class NpcCombatProfileModelTest {

    @Test
    void everyModelFieldSurvivesARoundTrip() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.modelKind = NpcCombatProfile.MODEL_GECKOLIB;
        profile.modelId = "mypack:ogre";
        profile.modelTexture = "mypack:textures/entity/ogre_blue.png";
        profile.modelTint = 0x3366CC;
        profile.modelGlowing = true;

        NpcCombatProfile back = NpcCombatProfile.fromTag(profile.toTag());

        assertEquals(NpcCombatProfile.MODEL_GECKOLIB, back.modelKind);
        assertEquals("mypack:ogre", back.modelId);
        assertEquals("mypack:textures/entity/ogre_blue.png", back.modelTexture);
        assertEquals(0x3366CC, back.modelTint);
        assertTrue(back.modelGlowing);
    }

    @Test
    void anUntintedProfileStaysWhiteRatherThanGoingBlack() {
        // A missing tint key must not read as 0x000000, which would silently black out every NPC
        // saved before the model block existed.
        NpcCombatProfile fresh = NpcCombatProfile.fromTag(new CompoundTag());
        assertEquals(0xFFFFFF, fresh.modelTint);
        assertEquals(NpcCombatProfile.MODEL_VANILLA, fresh.modelKind);
    }

    @Test
    void anUnknownModelKindDegradesToVanilla() {
        assertEquals(NpcCombatProfile.MODEL_VANILLA, NpcCombatProfile.normalizeModelKind("nonsense"));
        assertEquals(NpcCombatProfile.MODEL_VANILLA, NpcCombatProfile.normalizeModelKind(null));
        assertEquals(NpcCombatProfile.MODEL_VANILLA, NpcCombatProfile.normalizeModelKind(""));
        // Case and padding are operator input, so they must not decide the outcome.
        assertEquals(NpcCombatProfile.MODEL_ENTITY, NpcCombatProfile.normalizeModelKind(" entity "));
    }

    @Test
    void liveVisualPacketCarriesModelTextureSizeAndNightSkin() {
        NpcCombatProfile source = new NpcCombatProfile();
        source.modelKind = NpcCombatProfile.MODEL_GECKOLIB;
        source.modelId = "dragonminez:textures/entity/master/master_gohan.png";
        source.modelTexture = source.modelId;
        source.nightTexture = "mypack:textures/entity/gohan_night.png";
        source.baseSize = 12;
        source.modelTint = 0x11CCEE;

        NpcCombatProfile client = new NpcCombatProfile();
        client.applyVisualOptions(source.visualOptionsTag());

        assertEquals(source.modelKind, client.modelKind);
        assertEquals(source.modelId, client.modelId);
        assertEquals(source.modelTexture, client.modelTexture);
        assertEquals(source.nightTexture, client.nightTexture);
        assertEquals(source.baseSize, client.baseSize);
        assertEquals(source.modelTint, client.modelTint);
    }

    /**
     * The invariant behind the editor sending a subset: merging a few keys over a full profile must
     * change exactly those keys. If this stops holding, an editor save would clobber fields owned by
     * the other DMZ screens, which is what {@code withKiWeapon} warns about.
     */
    @Test
    void mergingASubsetLeavesUntouchedFieldsAlone() {
        NpcCombatProfile current = new NpcCombatProfile();
        current.strength = 4242;
        current.formGroup = "supersaiyan";
        current.formId = "supersaiyan3";
        current.modelId = "mypack:ogre";

        CompoundTag merged = current.toTag();
        CompoundTag subset = new CompoundTag();
        subset.putInt("Strength", 99);
        for (String key : subset.getAllKeys()) {
            merged.put(key, subset.get(key).copy());
        }

        NpcCombatProfile after = NpcCombatProfile.fromTag(merged);
        assertEquals(99, after.strength, "the edited field changes");
        assertEquals("supersaiyan", after.formGroup, "transform state is preserved");
        assertEquals("supersaiyan3", after.formId, "transform state is preserved");
        assertEquals("mypack:ogre", after.modelId, "unrelated fields are preserved");
    }
}

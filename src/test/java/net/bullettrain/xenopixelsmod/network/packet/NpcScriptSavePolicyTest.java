package net.bullettrain.xenopixelsmod.network.packet;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-NPC script binding: one id, pointing into the script store.
 *
 * <p>The rule this suite protects is the repo's own: a profile key may only become writable once
 * something actually reads it. {@code ScriptId} is written by the editor, stored on the NPC, and
 * resolved by {@code XenoScriptRunner.runForProfile(...)} - so it earns its place in the whitelist.
 * A key with no consumer would be a place for a client to write data the server then ignores.
 */
class NpcScriptSavePolicyTest {

    private static CompoundTag payload(String value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("ScriptId", value);
        return tag;
    }

    @Test
    void aBoundScriptSurvivesTheServerMergeIntoTheProfile() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.scriptId = "greet";
        assertTrue(XenoNpcSavePolicy.validate(payload("greet")).accepted());

        CompoundTag merged = XenoNpcSavePolicy.merge(profile.toTag(), payload("greet"));
        assertEquals("greet", NpcCombatProfile.fromTag(merged).scriptId);
        assertEquals("greet", merged.getString("ScriptId"));
    }

    @Test
    void clearingTheBindingIsAcceptedRatherThanTreatedAsAnEmptyId() {
        // An operator un-selecting a script sends "". If "" were validated as an id it would be
        // refused, and the only way to unbind a script would be to delete the NPC.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.scriptId = "greet";
        assertTrue(XenoNpcSavePolicy.validate(payload("")).accepted());
        NpcCombatProfile cleared = NpcCombatProfile.fromTag(
                XenoNpcSavePolicy.merge(profile.toTag(), payload("")));
        assertEquals("", cleared.scriptId);
        // And a cleared binding is not written back out at all, so old worlds keep their shape.
        assertFalse(cleared.toTag().contains("ScriptId"));
    }

    @Test
    void anIdThatCouldNeverResolveToAStoredScriptIsRefused() {
        // Same boundary as the store's filenames: an NPC pointing at "../evil" points at nothing,
        // and it is better to say so at save time than to look broken in game.
        for (String bad : List.of("../evil", "a/b", "a\\b", "Guards", "con", ".hidden",
                "trailing.",
                "a".repeat(net.bullettrain.xenopixelsmod.npc.store.XenoNpcStorePaths.MAX_ID + 1))) {
            XenoNpcSavePolicy.Validation result = XenoNpcSavePolicy.validate(payload(bad));
            assertFalse(result.accepted(), "should have been refused: " + bad);
            assertNotNull(result.reason());
            assertTrue(result.reason().contains("script"), result.reason());
        }
        assertTrue(XenoNpcSavePolicy.validate(payload("greet")).accepted());
        assertTrue(XenoNpcSavePolicy.validate(payload("village.guards-2")).accepted());
    }

    @Test
    void aScriptIdMustBeTextAndNotSomethingElseWearingTheName() {
        CompoundTag tag = new CompoundTag();
        tag.put("ScriptId", IntTag.valueOf(7));
        assertFalse(XenoNpcSavePolicy.validate(tag).accepted());
    }

    @Test
    void theKeyIsWhitelistedAndAdvertisedInTheShape() {
        // editableKeys() is what a client may send; profileShape() is what it is told to expect. A
        // key in one but not the other is a screen that renders a field it cannot save.
        assertTrue(XenoNpcSavePolicy.editableKeys().contains("ScriptId"));
        assertEquals(net.minecraft.nbt.Tag.TAG_STRING,
                XenoNpcSavePolicy.profileShape().getTagType("ScriptId"));
    }

    @Test
    void anUnknownKeyIsStillRefusedSoTheWhitelistMeansSomething() {
        CompoundTag tag = new CompoundTag();
        tag.putString("ScriptIdx", "greet");
        assertFalse(XenoNpcSavePolicy.validate(tag).accepted());
        assertNull(XenoNpcSavePolicy.profileShape().get("ScriptIdx"));
    }

    @Test
    void anOldProfileWithoutTheKeyReadsAsNoScript() {
        // Upgrading a world must not invent a binding for every NPC.
        CompoundTag legacy = new CompoundTag();
        legacy.putString("Name", "Blacksmith");
        assertEquals("", NpcCombatProfile.fromTag(legacy).scriptId);
    }
}

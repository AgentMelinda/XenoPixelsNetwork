package net.bullettrain.xenopixelsmod.npc;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the bug that made the editor look like it was not saving.
 *
 * <p>{@code XenoNpcData.toTag()} does not carry the combat profile - that lives in the entity's
 * persistent data - but the editor asked its payload for {@code "Profile"} anyway. A missing key
 * answers as an empty tag, so the editor rebuilt a default profile on every open and every field
 * read back as its default, which is indistinguishable from the save having failed.
 *
 * <p>These tests pin the shape the editor depends on. They cannot construct an {@code Entity}
 * without a running client, so they assert on the tag contract either side of it rather than on
 * {@code editorPayload} itself.
 */
class XenoNpcEditorPayloadTest {

    @Test
    void npcDataAloneCarriesNoProfile() {
        // The fact being guarded: this is exactly why the payload has to be assembled, and why
        // reading "Profile" straight off npcData().toTag() was always going to yield a default.
        XenoNpcData data = new XenoNpcData(XenoNpcRole.HUMANOID);
        CompoundTag tag = data.toTag();
        assertFalse(tag.contains("Profile"),
                "XenoNpcData is the NPC's own save data; the profile is stored separately");
    }

    @Test
    void anAbsentProfileKeyReadsAsEmptyRatherThanFailing() {
        // The silent half of the bug: nothing throws, so it was invisible until a value "vanished".
        CompoundTag tag = new XenoNpcData(XenoNpcRole.HUMANOID).toTag();
        assertTrue(tag.getCompound("Profile").isEmpty());
    }

    @Test
    void anAssembledPayloadRoundTripsTheProfile() {
        // The shape editorPayload produces, asserted without needing an Entity.
        XenoNpcData data = new XenoNpcData(XenoNpcRole.GUARD);
        data.setDisplayName("Nappa");

        var profile = new net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile();
        profile.strength = 4242;
        profile.raceId = "saiyan";

        CompoundTag payload = data.toTag();
        payload.put("Profile", profile.toTag());

        var back = net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile
                .fromTag(payload.getCompound("Profile"));
        assertEquals(4242, back.strength, "the editor must see the NPC's real stats");
        assertEquals("saiyan", back.raceId);
        assertEquals("Nappa", payload.getString("Name"));
    }

    @Test
    void titleIsItsOwnFieldAndNotTheFaction() {
        // Both rows used to write the faction, because there was no title field at all.
        XenoNpcData data = new XenoNpcData(XenoNpcRole.HUMANOID);
        data.setTitle("Captain");
        data.setFaction("red_ribbon");

        assertEquals("Captain", data.title());
        assertEquals("red_ribbon", data.faction());

        XenoNpcData back = XenoNpcData.fromTag(data.toTag(), XenoNpcRole.HUMANOID);
        assertEquals("Captain", back.title());
        assertEquals("red_ribbon", back.faction());
    }

    @Test
    void titleAndNameAreClampedAndNeverNull() {
        XenoNpcData data = new XenoNpcData(XenoNpcRole.HUMANOID);
        data.setTitle(null);
        assertEquals("", data.title());

        data.setTitle("x".repeat(200));
        assertEquals(64, data.title().length(), "title is clamped like name and faction");
    }

    @Test
    void roleCanBeChangedAndSurvivesNpcPersistence() {
        XenoNpcData data = new XenoNpcData(XenoNpcRole.HUMANOID);

        data.setRole(XenoNpcRole.TRADER);

        assertEquals(XenoNpcRole.TRADER, data.role());
        assertEquals(XenoNpcRole.TRADER,
                XenoNpcData.fromTag(data.toTag(), XenoNpcRole.HUMANOID).role());
    }
}

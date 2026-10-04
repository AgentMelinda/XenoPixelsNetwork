package net.bullettrain.xenopixelsmod.network;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.network.packet.XenoNpcDataSavePolicy;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whitelist for the editor's second save payload.
 *
 * <p>{@code XenoNpcSavePacket} carried one tag, merged into {@code NpcCombatProfile}. Home, leash
 * and respawn live on {@code XenoNpcData} instead, so adding them to the profile whitelist would
 * have been inert - the keys would pass validation and then be dropped, because
 * {@code NpcCombatProfile.fromTag} has never heard of them. Hence a second payload with its own
 * whitelist rather than a wider one.
 */
class XenoNpcDataSavePolicyTest {

    @Test
    void allSupportedDataKeysAreAllowed() {
        for (String key : new String[]{"HomeX", "HomeY", "HomeZ", "LeashRadius",
                "RespawnEnabled", "RespawnDelayTicks", "Role"}) {
            assertTrue(XenoNpcDataSavePolicy.editableKeys().contains(key), key);
        }
    }

    @Test
    void revisionIsRefusedButAValidRoleCanBeChanged() {
        // Revision is the optimistic-lock counter. Role has a native setter, persistence field,
        // and runtime behavior, so the editor may request one of the bounded role ids.
        CompoundTag tag = new CompoundTag();
        tag.putInt("Revision", 999);
        assertFalse(XenoNpcDataSavePolicy.validate(tag).accepted());

        CompoundTag role = new CompoundTag();
        role.putString("Role", "trader");
        assertTrue(XenoNpcDataSavePolicy.validate(role).accepted());
    }

    @Test
    void invalidRoleAndWrongRoleTypeAreRefused() {
        CompoundTag unknown = new CompoundTag();
        unknown.putString("Role", "admin");
        assertFalse(XenoNpcDataSavePolicy.validate(unknown).accepted());

        CompoundTag wrongType = new CompoundTag();
        wrongType.putInt("Role", 2);
        assertFalse(XenoNpcDataSavePolicy.validate(wrongType).accepted());
    }

    @Test
    void anUnknownKeyIsRefusedRatherThanIgnored() {
        // Silently dropping it would make a crafted save look like it worked.
        CompoundTag tag = new CompoundTag();
        tag.putString("Owner", "somebody-else");
        assertFalse(XenoNpcDataSavePolicy.validate(tag).accepted());
    }

    @Test
    void anEmptyTagIsAccepted() {
        // The editor sends nothing when nothing on this page changed.
        assertTrue(XenoNpcDataSavePolicy.validate(new CompoundTag()).accepted());
    }

    @Test
    void aTagWithTooManyKeysIsRefused() {
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < XenoNpcDataSavePolicy.MAX_KEYS + 1; i++) {
            tag.putInt("k" + i, i);
        }
        assertFalse(XenoNpcDataSavePolicy.validate(tag).accepted());
    }

    @Test
    void everyWhitelistedKeyIsOneXenoNpcDataActuallyWrites() throws IOException {
        // The failure this guards is a whitelisted key that no loader reads - exactly the
        // stored-but-unread trap the profile whitelist's comments describe. If XenoNpcData.toTag
        // does not write the key, nothing will ever read it back.
        String source = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/npc", "XenoNpcData.java"),
                StandardCharsets.UTF_8);
        for (String key : XenoNpcDataSavePolicy.editableKeys()) {
            assertTrue(source.contains('"' + key + '"'),
                    "XenoNpcData does not mention " + key);
        }
    }

    @Test
    void theSavePacketCarriesTheSecondPayload() throws IOException {
        // Appended after profileTag, so an older decoder reading in order is not shifted.
        String packet = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                        "XenoNpcSavePacket.java"), StandardCharsets.UTF_8);
        int profile = packet.indexOf("profileTag = buf.readBoolean()");
        int data = packet.indexOf("dataTag = buf.readBoolean()");
        assertTrue(profile >= 0 && data >= 0, "both payloads should decode");
        assertTrue(data > profile, "the new payload is appended, never inserted");
    }

    @Test
    void theProtocolWasBumped() throws IOException {
        // The wire shape changed; a client that still sends the old five fields would leave the
        // decoder reading a boolean off the end of the buffer.
        assertTrue(ProtocolVersion.current() >= 85,
                "the second save payload needed at least protocol 85");
    }

    @Test
    void mergeOnlyTouchesTheKeysSent() {
        // A save is a subset - only what the editor changed. Merging must not blank the rest.
        CompoundTag before = new CompoundTag();
        before.putDouble("HomeX", 10.0);
        before.putDouble("LeashRadius", 64.0);
        before.putString("Role", "humanoid");

        CompoundTag change = new CompoundTag();
        change.putDouble("HomeX", 20.0);
        change.putString("Role", "trader");

        CompoundTag merged = XenoNpcDataSavePolicy.merge(before, change);
        assertEquals(20.0, merged.getDouble("HomeX"));
        assertEquals(64.0, merged.getDouble("LeashRadius"), "an untouched key survives");
        assertEquals("trader", merged.getString("Role"));
    }
}

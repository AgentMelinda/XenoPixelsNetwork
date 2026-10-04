package net.bullettrain.xenopixelsmod.network.packet;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whitelist's cap is derived from the whitelist.
 *
 * <p>It was a literal 96 with a margin over the list. The list grew to 98 and the number did not,
 * so the cap stopped bounding the thing it exists to bound and a save carrying every editable key
 * would have been refused. The margin <em>was</em> the bug: a second number to remember to change.
 */
class SavePolicyCapTest {

    @Test
    void aSaveMayCarryEveryEditableKey() {
        // The case that was broken. A profile with every conditional field populated writes more
        // keys than the old literal allowed.
        NpcCombatProfile sample = new NpcCombatProfile();
        for (var category
                : net.bullettrain.xenopixelsmod.npc.lines.XenoNpcLines.Category.values()) {
            sample.setLines(category, java.util.List.of("sample"));
        }
        sample.dialogSlots.set(0, "sample", "sample");
        sample.transportNetwork = "sample";
        sample.trades.add(new net.bullettrain.xenopixelsmod.npc.trade.NpcTrade(
                "minecraft:emerald", 1, "", 1, "minecraft:bread", 1, 16));

        CompoundTag full = sample.toTag();
        CompoundTag editableOnly = new CompoundTag();
        for (String key : XenoNpcSavePolicy.editableKeys()) {
            if (full.contains(key)) {
                editableOnly.put(key, full.get(key).copy());
            }
        }
        assertTrue(XenoNpcSavePolicy.validate(editableOnly).accepted(),
                "every editable key at once must fit under the cap");
    }

    @Test
    void oneKeyBeyondTheWhitelistIsStillRefused() {
        // The cap being derived must not mean the cap being absent.
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < XenoNpcSavePolicy.editableKeys().size() + 1; i++) {
            tag.putInt("k" + i, i);
        }
        assertFalse(XenoNpcSavePolicy.validate(tag).accepted());
    }

    @Test
    void theCapIsNotAHandWrittenNumberAnyMore() throws Exception {
        String source = java.nio.file.Files.readString(
                net.bullettrain.xenopixelsmod.RepoRoot.of(
                        "src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                        "XenoNpcSavePolicy.java"), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(source.contains("MAX_KEYS = EDITABLE_KEYS.size()"),
                "derived, so the two cannot diverge again");
        assertFalse(source.contains("MAX_KEYS = 96"), "the literal is what drifted");
    }
}

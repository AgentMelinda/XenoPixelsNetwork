package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the 2026-09-26 fix: a CNPC/Mynpc quest completion flushes the third-party save but must not
 * mint XenoSkill points, which {@link
 * net.bullettrain.xenopixelsmod.features.progression.QuestRewardOriginPolicy} reserves for native
 * Xeno NPC givers. Reading the source is the only headless way to check the grant is gone, since
 * {@code flush} runs through reflection against an optional third-party class.
 */
class NpcQuestCompletionSyncTest {
    @Test
    void theCnpcFlushNoLongerGrantsSkillPoints() throws IOException {
        String source = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/compat/npc",
                        "NpcQuestCompletionSync.java"),
                StandardCharsets.UTF_8);
        assertFalse(source.contains("addSkillPoints"),
                "third-party quest completion must not mint XenoSkill points");
        assertFalse(source.contains("+2 skill points"),
                "the completion message must not claim a reward the policy forbids");
        assertTrue(source.contains("methods[1].invoke(data, true)"),
                "the immediate third-party persistence must stay");
    }
}

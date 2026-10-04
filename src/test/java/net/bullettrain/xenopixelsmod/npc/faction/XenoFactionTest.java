package net.bullettrain.xenopixelsmod.npc.faction;

import com.google.gson.JsonParser;
import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.features.progression.QuestReward;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Factions: the meaning behind a field NPCs have carried since the editor gained it.
 *
 * <p>{@code XenoNpcData.faction} has been editable and saved for a long time and nothing ever read
 * it - a label with nothing behind it, the same shape as the stored-but-unread profile fields that
 * had to earn their way back onto the save whitelist.
 *
 * <p>The registry itself needs a datapack reload, so what is pinned here is the model: parsing,
 * the standing thresholds, and the rules that stop a half-configured pack turning NPCs hostile to
 * everything.
 */
class XenoFactionTest {

    private static XenoFaction parse(String json) {
        return XenoFaction.fromJson("test", JsonParser.parseString(json));
    }

    @Test
    void aMinimalFactionParses() {
        XenoFaction faction = parse("{}");
        assertEquals("test", faction.id());
        assertEquals("test", faction.name(), "the name falls back to the id");
        assertEquals(0xFFFFFF, faction.color());
        assertEquals(0, faction.defaultStanding());
        assertTrue(faction.hostileTo().isEmpty());
    }

    @Test
    void colourAcceptsBothSpellingsAndSurvivesNonsense() {
        assertEquals(0xDD0000, parse("{\"color\":\"#DD0000\"}").color());
        assertEquals(0xDD0000, parse("{\"color\":\"dd0000\"}").color());
        // A typo in a pack should draw white, not stop the faction loading.
        assertEquals(0xFFFFFF, parse("{\"color\":\"not-a-colour\"}").color());
    }

    @Test
    void aFactionIsNeverHostileToItself() {
        // Whatever a pack writes. Otherwise its own guards would fight each other the moment two
        // stood near one another.
        XenoFaction faction = parse("{\"hostile_to\":[\"test\",\"bandits\"]}");
        assertFalse(faction.isHostileTo("test"));
        assertTrue(faction.isHostileTo("bandits"));
    }

    @Test
    void hostilityIgnoresCaseAndPadding() {
        XenoFaction faction = parse("{\"hostile_to\":[\"Bandits\"]}");
        assertTrue(faction.isHostileTo("bandits"));
        assertTrue(faction.isHostileTo("  BANDITS  "));
        assertFalse(faction.isHostileTo(null));
        assertFalse(faction.isHostileTo(""));
    }

    @Test
    void attitudeTurnsOnTheThresholds() {
        assertEquals(XenoFaction.Attitude.HOSTILE,
                XenoFaction.attitudeAt(XenoFaction.HOSTILE_BELOW));
        assertEquals(XenoFaction.Attitude.NEUTRAL,
                XenoFaction.attitudeAt(XenoFaction.HOSTILE_BELOW + 1));
        assertEquals(XenoFaction.Attitude.NEUTRAL, XenoFaction.attitudeAt(0));
        assertEquals(XenoFaction.Attitude.FRIENDLY,
                XenoFaction.attitudeAt(XenoFaction.FRIENDLY_AT));
    }

    @Test
    void aPlayerStartsNeutralSoHostilityHasToBeEarned() {
        // The default default. An NPC should not attack someone who has never met its faction.
        assertEquals(XenoFaction.Attitude.NEUTRAL,
                XenoFaction.attitudeAt(parse("{}").defaultStanding()));
    }

    @Test
    void standingIsClampedBothWays() {
        assertEquals(XenoFaction.MAX_STANDING, XenoFaction.clampStanding(999_999));
        assertEquals(XenoFaction.MIN_STANDING, XenoFaction.clampStanding(-999_999));
        assertEquals(0, XenoFaction.clampStanding(0));
        // A pack cannot put a faction permanently out of reach either.
        assertEquals(XenoFaction.MAX_STANDING,
                parse("{\"default_standing\":50000}").defaultStanding());
    }

    @Test
    void anUnknownFactionIsHostileToNobody() {
        // Both sides have to exist. An NPC labelled with a faction a pack later deleted should
        // stand there, not turn on everything in sight.
        assertFalse(XenoFactions.hostile("nope", "also-nope"));
        assertFalse(XenoFactions.hostile(null, "bandits"));
        assertTrue(XenoFactions.isEmpty(), "no pack is loaded in a unit test");
    }

    @Test
    void factionPointsAreARewardNowThatThereIsSomewhereForThemToGo() {
        // They were deliberately left out while nothing could receive them - a number written into
        // a void. The registry and the per-player standing both exist now.
        QuestReward reward = new QuestReward(0, 0, List.of(), List.of(),
                List.of(new QuestReward.FactionGrant("bandits", -50)));
        assertFalse(reward.isEmpty());
        assertTrue(reward.describe().contains("-50 bandits standing"), reward.describe());
    }

    @Test
    void aRewardCanLowerStandingAsWellAsRaiseIt() {
        // Helping one side should be able to cost you with another.
        QuestReward reward = new QuestReward(0, 0, List.of(), List.of(),
                List.of(new QuestReward.FactionGrant("guards", 25),
                        new QuestReward.FactionGrant("bandits", -25)));
        String text = reward.describe();
        assertTrue(text.contains("+25 guards standing"), text);
        assertTrue(text.contains("-25 bandits standing"), text);
    }

    @Test
    void theOldRewardShapeStillCompilesAndPaysTheSame() {
        // Every existing quest was built with the four-argument form. Adding faction points must
        // not change what they pay.
        QuestReward reward = new QuestReward(2, 30, List.of(), List.of());
        assertTrue(reward.factionPoints().isEmpty());
        assertEquals(2, reward.skillPoints());
        assertEquals(2, QuestReward.DEFAULT.skillPoints());
    }

    @Test
    void theNpcTargetingConsultsTheRegistryRatherThanTheLabel() throws IOException {
        // The point of the whole exercise: the faction string now decides something.
        Path entity = RepoRoot.of(
                "src/main/java/net/bullettrain/xenopixelsmod/npc/XenoNpcEntity.java");
        String source = Files.readString(entity, StandardCharsets.UTF_8);
        assertTrue(source.contains("isFactionEnemy"), "hostility should be a real predicate");
        assertTrue(source.contains("XenoFactions.get("),
                "and should go through the registry, not string comparison");
        assertTrue(source.contains("getFactionStanding("),
                "a player's hostility should be earned through standing");
    }
}

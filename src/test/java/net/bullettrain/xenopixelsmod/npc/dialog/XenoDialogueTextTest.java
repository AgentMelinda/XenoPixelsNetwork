package net.bullettrain.xenopixelsmod.npc.dialog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XenoDialogueTextTest {

    @Test
    void substitutesPlayerAndNpcNames() {
        assertEquals("Hello Dev, I am Nappa.",
                XenoDialogueText.resolve("Hello {player}, I am {npc}.", "Dev", "Nappa"));
    }

    @Test
    void missingNamesBecomeEmptyRatherThanTheWordNull() {
        assertEquals("Hello .", XenoDialogueText.resolve("Hello {player}.", null, null));
    }

    @Test
    void translatesColourCodes() {
        assertEquals("§cDanger§r", XenoDialogueText.colorize("&cDanger&r"));
    }

    @Test
    void anAmpersandFollowedByANonCodeSurvives() {
        // The reason colorize checks the following character: a blind replace would turn "& b" into
        // a formatting code and swallow the letter.
        assertEquals("tea & biscuits", XenoDialogueText.colorize("tea & biscuits"));
        assertEquals("100&% sure", XenoDialogueText.colorize("100&% sure"));
        assertEquals("A&Z", XenoDialogueText.colorize("A&Z"));
    }

    @Test
    void anAmpersandFollowedByARealCodeIsAlwaysTakenAsOne() {
        // "Q&A" becomes green text, because 'a' is a colour code. That is how Minecraft formatting
        // works everywhere, so a dialogue wanting a literal ampersand before a code letter has to
        // escape it as "&&" - which is what that escape is for.
        assertEquals("Q&A", XenoDialogueText.colorize("Q&&A"));
        assertEquals("Q§a", XenoDialogueText.colorize("Q&A"));
    }

    @Test
    void doubleAmpersandEscapesToALiteralOne() {
        assertEquals("&c is red", XenoDialogueText.colorize("&&c is red"));
    }

    @Test
    void aTrailingAmpersandIsLeftAlone() {
        assertEquals("what &", XenoDialogueText.colorize("what &"));
    }

    @Test
    void commandPlaceholderResolvesAndLeadingSlashIsDropped() {
        // Commands are dispatched without the slash, and @dp is the reference's spelling for
        // "the player talking".
        assertEquals("give Dev minecraft:apple",
                XenoDialogueText.resolveCommand("/give @dp minecraft:apple", "Dev"));
        assertEquals("say hi Dev", XenoDialogueText.resolveCommand("say hi @dp", "Dev"));
    }

    @Test
    void blankCommandsResolveToNothing() {
        assertEquals("", XenoDialogueText.resolveCommand(null, "Dev"));
        assertEquals("", XenoDialogueText.resolveCommand("   ", "Dev"));
    }
}

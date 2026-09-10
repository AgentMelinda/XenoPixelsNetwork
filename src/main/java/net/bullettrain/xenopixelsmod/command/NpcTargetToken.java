package net.bullettrain.xenopixelsmod.command;

import java.util.Locale;

/**
 * Recognises the player tokens the NPC mods put into commands.
 *
 * <p>CustomNPCs and its My NPCs fork substitute {@code @dp} in a command with the interacting
 * player before running it — but they substitute the player's <em>display name</em>, and they skip
 * the substitution entirely when there is no player in scope
 * ({@code EspiUtilServer.runCommand} guards it with a null check). Either way vanilla's entity
 * selector cannot cope: it never heard of {@code @dp}, and a display name carrying a nickname,
 * spaces or colour codes will not match a username.
 *
 * <p>So {@code /xenopoints} accepts these tokens itself. Kept free of Minecraft types so the
 * matching rules can be unit tested; the command does the looking-up.
 */
public final class NpcTargetToken {

    /** What the NPC mods and our own quest-command bridge use for "the player who triggered this". */
    private static final String[] DIALOG_PLAYER = {"@dp", "{refplayer}", "@p2"};

    private NpcTargetToken() {
    }

    /** Whether this argument means "whoever this command is being run for". */
    public static boolean isDialogPlayer(String raw) {
        if (raw == null) return false;
        String needle = raw.trim().toLowerCase(Locale.ROOT);
        for (String token : DIALOG_PLAYER) {
            if (token.equals(needle)) return true;
        }
        return false;
    }

    /**
     * Whether an argument names this player.
     *
     * <p>Checks the username first and the display name second, so a player whose nickname happens
     * to be somebody else's username still resolves to the right account.
     */
    public static boolean matches(String raw, String username, String displayName) {
        if (raw == null) return false;
        String needle = clean(raw);
        if (needle.isEmpty()) return false;
        return needle.equals(clean(username)) || needle.equals(clean(displayName));
    }

    /** True when the argument names this player by username, ignoring any display name. */
    public static boolean matchesUsername(String raw, String username) {
        if (raw == null) return false;
        String needle = clean(raw);
        return !needle.isEmpty() && needle.equals(clean(username));
    }

    /**
     * Normalises a name for comparison: trimmed, case-folded, and stripped of section-sign
     * formatting, which nickname mods routinely bake into a display name.
     */
    private static String clean(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '§') {
                i++; // drop the colour or style code that follows
                continue;
            }
            out.append(c);
        }
        return out.toString().trim().toLowerCase(Locale.ROOT);
    }
}

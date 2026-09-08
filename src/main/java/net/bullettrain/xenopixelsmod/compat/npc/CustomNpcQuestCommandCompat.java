package net.bullettrain.xenopixelsmod.compat.npc;

import java.util.Locale;

public final class CustomNpcQuestCommandCompat {
    private static final String REF_PLAYER = "{RefPlayer}";
    private static final String DISPLAY_PLAYER = "@dp";
    private static final String COMMAND_ROOT = "xenopoints";

    private CustomNpcQuestCommandCompat() {
    }

    public static String normalize(String command) {
        if (command == null || !command.contains(REF_PLAYER)) {
            return command;
        }

        int rootStart = 0;
        while (rootStart < command.length() && Character.isWhitespace(command.charAt(rootStart))) {
            rootStart++;
        }
        if (rootStart < command.length() && command.charAt(rootStart) == '/') {
            rootStart++;
            while (rootStart < command.length() && Character.isWhitespace(command.charAt(rootStart))) {
                rootStart++;
            }
        }

        int rootEnd = rootStart;
        while (rootEnd < command.length() && !Character.isWhitespace(command.charAt(rootEnd))) {
            rootEnd++;
        }
        String root = command.substring(rootStart, rootEnd).toLowerCase(Locale.ROOT);
        return COMMAND_ROOT.equals(root) ? command.replace(REF_PLAYER, DISPLAY_PLAYER) : command;
    }
}

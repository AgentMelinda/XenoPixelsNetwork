package net.bullettrain.xenopixelsmod.compat.npc;

import java.util.Locale;

public final class CustomNpcQuestCommandCompat {
    private static final String REF_PLAYER = "{RefPlayer}";
    private static final String DISPLAY_PLAYER = "@dp";
    private static final java.util.Set<String> COMMAND_ROOTS = java.util.Set.of("xenopoints", "dmzpoints");

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
        return COMMAND_ROOTS.contains(root) ? command.replace(REF_PLAYER, DISPLAY_PLAYER) : command;
    }

    public static String normalizeEmbedded(String value) {
        if (value == null || !value.contains(REF_PLAYER)) return value;
        String lower = value.toLowerCase(Locale.ROOT);
        for (String root : COMMAND_ROOTS) {
            int index = lower.indexOf(root);
            while (index >= 0) {
                boolean leftBoundary = index == 0 || !Character.isLetterOrDigit(lower.charAt(index - 1));
                int end = index + root.length();
                boolean rightBoundary = end == lower.length() || !Character.isLetterOrDigit(lower.charAt(end));
                if (leftBoundary && rightBoundary) return value.replace(REF_PLAYER, DISPLAY_PLAYER);
                index = lower.indexOf(root, index + 1);
            }
        }
        return value;
    }
}

package net.bullettrain.xenopixelsmod.features.progression;

/** Expands quest-specific target aliases before a trusted server reward command is parsed. */
public final class QuestCommandTargets {
    private QuestCommandTargets() {}

    /** {@code @dp} means the completing player; vanilla {@code @p} remains a position selector. */
    public static String resolve(String command, String completingPlayer) {
        if (command == null) return "";
        String name = completingPlayer == null ? "" : completingPlayer;
        return command.trim().replace("@dp", name);
    }
}

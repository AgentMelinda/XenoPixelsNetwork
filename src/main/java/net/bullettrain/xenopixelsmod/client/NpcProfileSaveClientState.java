package net.bullettrain.xenopixelsmod.client;

/**
 * Client mirror of the last {@code NpcProfileSavePacket} outcome.
 *
 * <p>Not {@code @OnlyIn}: referenced from common network packets, so stripping it would crash a
 * dedicated server. Holds no Minecraft types for the same reason.
 */
public final class NpcProfileSaveClientState {
    private static volatile boolean success = true;
    private static volatile String message = "";
    private static volatile int sequence;
    private static volatile boolean pendingNotice;

    private NpcProfileSaveClientState() {}

    public static void accept(boolean saved, String resultMessage) {
        success = saved;
        message = resultMessage == null ? "" : resultMessage;
        pendingNotice = !saved;
        sequence++;
    }

    public static boolean success() {
        return success;
    }

    public static String message() {
        return message;
    }

    /** Increments once per answer, so a listener can tell "no result yet" from "same again". */
    public static int sequence() {
        return sequence;
    }

    /** True exactly once per rejected save, so the rejection is announced a single time. */
    public static boolean consumeNotice() {
        if (!pendingNotice) {
            return false;
        }
        pendingNotice = false;
        return true;
    }
}
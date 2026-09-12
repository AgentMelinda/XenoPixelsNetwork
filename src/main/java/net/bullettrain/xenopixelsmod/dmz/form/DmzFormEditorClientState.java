package net.bullettrain.xenopixelsmod.dmz.form;

/**
 * Client-side status of the most recent form-editor save.
 *
 * <p>The editor never advances its own revision optimistically: it waits for the server's
 * authoritative answer, which is what makes the stale-edit rejection in
 * {@link DmzFormEditorService} meaningful. {@link #sequence()} increments once per answer so a
 * screen can tell "no result yet" from "the same result again".
 */
public final class DmzFormEditorClientState {
    private static volatile long revision;
    private static volatile boolean success = true;
    private static volatile boolean inFlight;
    private static volatile int sequence;
    private static volatile String message = "";

    private DmzFormEditorClientState() {
    }

    public static void begin() {
        inFlight = true;
        success = true;
        message = "Saving...";
    }

    public static void accept(boolean saved, long newRevision, String resultMessage) {
        inFlight = false;
        success = saved;
        if (saved) revision = newRevision;
        message = resultMessage == null ? "" : resultMessage;
        sequence++;
    }

    /** Clears any carried-over status so a freshly opened editor does not show a stale message. */
    public static void reset() {
        inFlight = false;
        success = true;
        message = "";
    }

    public static long revision() {
        return revision;
    }

    public static boolean success() {
        return success;
    }

    /** True while a save has been sent and no answer has come back yet. */
    public static boolean inFlight() {
        return inFlight;
    }

    public static int sequence() {
        return sequence;
    }

    public static String message() {
        return message;
    }
}

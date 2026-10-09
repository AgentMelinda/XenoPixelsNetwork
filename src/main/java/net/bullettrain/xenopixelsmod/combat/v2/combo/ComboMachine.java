package net.bullettrain.xenopixelsmod.combat.v2.combo;

/**
 * The pure transition rule for a live combo string. Minecraft-free.
 *
 * <p>Counted from the tick a beat's input was accepted, a beat is closed to new beats until its
 * startup and its cancel time have both passed ({@link ComboNode#openTick()}), then open for its
 * window, then dead. An input that arrives while it is closed is not thrown away: it is held and
 * played the moment the beat opens, which is what stops a fast clicker dropping every second hit
 * on a busy server. Because nothing can start before a beat opens, the authored timings are the
 * pace of the string however fast the client clicks.
 */
public final class ComboMachine {

    public enum Outcome {
        /** Play {@code node} now. */
        PLAY,
        /** The current beat is still closed: hold the input and ask again when it opens. */
        BUFFER,
        /** Nothing can come of this input. */
        REJECT
    }

    public record Decision(Outcome outcome, ComboNode node) {
        static final Decision BUFFERED = new Decision(Outcome.BUFFER, null);
        static final Decision REJECTED = new Decision(Outcome.REJECT, null);
    }

    private ComboMachine() {}

    /**
     * @param graph        the routes
     * @param current      the beat in progress, or null when the fighter is in neutral
     * @param ticksInNode  ticks since {@code current} was accepted
     * @param hitLanded    whether {@code current}'s hit connected; a whiff cannot be continued
     * @param input        what was pressed
     */
    public static Decision decide(ComboGraph graph, ComboNode current, int ticksInNode,
                                  boolean hitLanded, ComboInput input) {
        if (graph == null || input == null) return Decision.REJECTED;
        if (current == null) return fresh(graph, input);
        if (ticksInNode < current.openTick()) {
            // Still closed. Worth holding if it can either continue this string or open a new one.
            return graph.next(current, input) != null || graph.start(input) != null
                    ? Decision.BUFFERED : Decision.REJECTED;
        }
        // Open. A landed beat inside its window continues along its branch; anything else - a
        // whiff, a closed window, an input this beat has no branch for - starts a new string, so
        // a press always does something once the fighter has recovered.
        ComboNode next = hitLanded && !expired(current, ticksInNode) ? graph.next(current, input) : null;
        return next != null ? new Decision(Outcome.PLAY, next) : fresh(graph, input);
    }

    private static Decision fresh(ComboGraph graph, ComboInput input) {
        ComboNode start = graph.start(input);
        return start == null ? Decision.REJECTED : new Decision(Outcome.PLAY, start);
    }

    /** True once the follow-up window of {@code node} has closed. */
    public static boolean expired(ComboNode node, int ticksInNode) {
        return node == null || ticksInNode > node.closeTick();
    }

    /**
     * Ticks left to choose a follow-up, counted from the hit so a prompt can show the choice
     * while the fighter is still recovering. Zero before the hit and after the window.
     */
    public static int choiceTicksLeft(ComboNode node, int ticksInNode) {
        if (node == null || ticksInNode < node.startupTicks()) return 0;
        return Math.max(0, node.closeTick() - ticksInNode);
    }

    /** The full length of that choice: cancel time plus window. */
    public static int choiceTicksTotal(ComboNode node) {
        return node == null ? 0 : node.cancelTicks() + node.windowTicks();
    }

    /** Whether a buffered input is still young enough to be played. */
    public static boolean bufferLive(int bufferedAtTick, int nowTick, int bufferTicks) {
        return bufferedAtTick >= 0 && nowTick - bufferedAtTick <= Math.max(0, bufferTicks);
    }
}

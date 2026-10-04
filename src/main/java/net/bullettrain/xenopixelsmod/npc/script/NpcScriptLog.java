package net.bullettrain.xenopixelsmod.npc.script;

/**
 * The one object a script is allowed to talk back through.
 *
 * <p>Bound as {@code log}, so a script writes {@code log.line("hi")} and the line lands in the
 * editor's output row (or the log, for a script run by a hook). A method call on a Java interface
 * is the minimum interop any JSR-223 engine provides, which keeps this working on Nashorn and
 * GraalJS without either engine needing a host-object wrapper.
 */
public interface NpcScriptLog {

    /** Receives one printed line; implementations must not throw. */
    void line(String text);

    /** Throws nothing and remembers what it was given; used by the tests and the editor. */
    final class Collector implements NpcScriptLog {
        private final StringBuilder text = new StringBuilder();

        @Override
        public void line(String value) {
            if (text.length() > 4096) {
                return;
            }
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(value == null ? "null" : String.valueOf(value));
        }

        public String joined() {
            return text.toString();
        }

        public int lines() {
            return text.length() == 0 ? 0 : text.toString().split("\n").length;
        }
    }
}

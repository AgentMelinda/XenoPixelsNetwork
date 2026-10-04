package net.bullettrain.xenopixelsmod.npc.script;

/**
 * What one script operation produced.
 *
 * <p>Nothing here throws. A script that fails to parse, fails to run, or runs on a server with no
 * engine at all all come back as a result with {@code ok == false} and a one-line reason the editor
 * can show verbatim. That is deliberate: the callers are a network handler and an editor screen,
 * and neither has a useful response to an exception escaping a user's script.
 *
 * @param engine which engine answered, so a report says who is being blamed
 * @param ok     true only when the script ran (or parsed) without error
 * @param value  string form of the return value, empty when there was none
 * @param error  single-line failure reason, empty when {@code ok}
 * @param line   1-based line the engine blamed, or -1 when it did not say
 */
public record NpcScriptResult(String engine, boolean ok, String value, String error, int line) {

    public static NpcScriptResult success(String engine, Object value) {
        return new NpcScriptResult(engine, true, render(value), "", -1);
    }

    public static NpcScriptResult failure(String engine, String error, int line) {
        return new NpcScriptResult(engine, false, "", oneLine(error), line);
    }

    /** True when the failure is "there is no engine here", which is not the script's fault. */
    public boolean engineMissing() {
        return !ok && NpcScriptEngine.NO_ENGINE.equals(error);
    }

    /** Text for the editor's output row: a value, or where and why it failed. */
    public String describe() {
        if (ok) {
            return value.isEmpty() ? "ok" : value;
        }
        if (line > 0) {
            return "line " + line + ": " + error;
        }
        return error;
    }

    private static String render(Object value) {
        if (value == null || "undefined".equals(String.valueOf(value))) {
            return "";
        }
        return oneLine(String.valueOf(value));
    }

    /**
     * Engines report multi-line stack traces. The editor row is one line and the reason travels in
     * a length-capped network field, so the first line plus the total line count is all we keep.
     */
    private static String oneLine(String text) {
        if (text == null || text.isBlank()) {
            return "script failed without a message";
        }
        String trimmed = text.strip();
        int newline = trimmed.indexOf('\n');
        if (newline < 0) {
            return capped(trimmed);
        }
        int lines = 1;
        for (int i = newline; i >= 0 && i < trimmed.length(); i = trimmed.indexOf('\n', i + 1)) {
            lines++;
        }
        return capped(trimmed.substring(0, newline).strip() + " (+" + (lines - 1) + " more lines)");
    }

    private static String capped(String text) {
        return text.length() <= 200 ? text : text.substring(0, 197) + "...";
    }
}

package net.bullettrain.xenopixelsmod.compat.npc;

/**
 * Nashorn does not wrap a Java {@code String[]} as a JS array, so {@code .join}
 * is undefined. Scripts should call {@code XenoPixels.joinNames(...)} or copy
 * into a JS array first.
 */
public final class NpcScriptLists {
    private NpcScriptLists() {}

    public static String joinNames(Object names) {
        return joinNames(names, ", ");
    }

    public static String joinNames(Object names, String separator) {
        String sep = separator == null ? ", " : separator;
        if (names == null) {
            return "";
        }
        if (names instanceof CharSequence text) {
            return text.toString();
        }
        if (names instanceof Object[] array) {
            return joinArray(array, sep);
        }
        if (names instanceof Iterable<?> iterable) {
            return joinIterable(iterable, sep);
        }
        return String.valueOf(names);
    }

    private static String joinArray(Object[] array, String sep) {
        StringBuilder out = new StringBuilder();
        for (Object item : array) {
            if (item == null) {
                continue;
            }
            if (out.length() > 0) {
                out.append(sep);
            }
            out.append(item);
        }
        return out.toString();
    }

    private static String joinIterable(Iterable<?> iterable, String sep) {
        StringBuilder out = new StringBuilder();
        for (Object item : iterable) {
            if (item == null) {
                continue;
            }
            if (out.length() > 0) {
                out.append(sep);
            }
            out.append(item);
        }
        return out.toString();
    }
}

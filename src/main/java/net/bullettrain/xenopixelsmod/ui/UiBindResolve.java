package net.bullettrain.xenopixelsmod.ui;

/** Presentation-only resolve. Missing keys become an error string, never a silent 0. */
public final class UiBindResolve {
    private UiBindResolve() {
    }

    public static String label(UiNode node, UiBindingSource source) {
        String prefix = node == null || node.text == null ? "" : node.text;
        String key = node == null ? "" : node.bind;
        if (key == null || key.isBlank()) {
            return prefix;
        }
        if (source == null) {
            return prefix + "[unbound: " + key + "]";
        }
        String text = source.text(key);
        if (text != null) {
            return prefix + text;
        }
        Double number = source.number(key);
        if (number != null) {
            if (key.endsWith("Percent")) {
                return prefix + Math.round(number * 100.0) + "%";
            }
            if (number == Math.rint(number)) {
                return prefix + String.valueOf(number.longValue());
            }
            return prefix + number;
        }
        return prefix + "[unbound: " + key + "]";
    }

    public static double bar(UiNode node, UiBindingSource source) {
        if (node == null || node.bind == null || node.bind.isBlank() || source == null) {
            return Double.NaN;
        }
        Double number = source.number(node.bind);
        return number == null ? Double.NaN : number;
    }
}

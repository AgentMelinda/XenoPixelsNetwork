package net.bullettrain.xenopixelsmod.client.npc.editor;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * One line of editor content, described as data rather than as a live widget.
 *
 * <p>Rows are declarations: they say what a control is and where its value goes, not where it sits.
 * {@link EditorLayout} decides the column and page, and only then does the screen build the widget
 * at its final rectangle.
 *
 * <p>That ordering is the point. The previous editor constructed every {@code EditBox} at width 1
 * and resized it afterwards, but {@code EditBox.setValue} runs {@code scrollTo} immediately, and
 * with a width of 1 the inner width is negative, so {@code displayPos} was pushed to the end of the
 * string. Resizing later never recomputes it, so a populated field rendered completely empty. A row
 * carries its initial value and is materialised once, at the right size, so that cannot recur.
 */
public sealed interface EditorRow {

    /** A section heading with a rule under it. Always spans the full body width. */
    record Heading(String text) implements EditorRow {
        @Override
        public boolean fullWidth() {
            return true;
        }
    }

    /**
     * Read-only text. A blank {@code label} marks an informational line, which spans the full
     * width — that is how a capability the native NPC does not have is stated plainly instead of
     * being shown as a control that would silently discard.
     */
    record Text(String label, String value, int color) implements EditorRow {
        @Override
        public boolean fullWidth() {
            return label == null || label.isBlank();
        }
    }

    /** An editable text field. {@code initial} is applied at construction, at the final width. */
    record Field(String label, String initial, int maxLength, Consumer<String> sink,
                 boolean fullWidth) implements EditorRow {
        public Field(String label, String initial, int maxLength, Consumer<String> sink) {
            this(label, initial, maxLength, sink, false);
        }
    }

    /**
     * A numbered assignment slot: its number, a clear button, and a wide pick button.
     *
     * <p>My NPCs' dialogue grid, row for row — {@code 0  [X]  [ Select Option ]}. Their screen
     * lays twelve of these out in two columns of six; this frame is narrower, so ours run in one
     * column. The anatomy of each row is what carries the recognition, and it is identical.
     *
     * @param number what the row is labelled with, which is the slot an author arranges by
     * @param label  what is assigned, or the placeholder when nothing is
     * @param onPick opens the picker
     * @param onClear empties the slot; null when it is already empty, which disables the button
     */
    record Slot(int number, String label, Runnable onPick, Runnable onClear)
            implements EditorRow {
    }

    /** A yes/no control. */
    record Toggle(String label, boolean value, Consumer<Boolean> sink) implements EditorRow {}

    /**
     * A hex text field with a swatch that opens the inline {@code InlineColorPicker}. The sink receives
     * the same "#RRGGBB" text the field itself would, so every existing consumer, dirty key, and
     * save-policy path stays identical - the picker is only a second way to author the value.
     */
    record Color(String label, String initial, Consumer<String> sink) implements EditorRow {}

    /**
     * An arrow control over a bounded number, for an index whose preset list cannot be enumerated.
     *
     * <p>Distinct from {@link Cycle} on purpose: a cycle names the values it offers, a stepper only
     * claims a range.
     */
    record Stepper(String label, int value, int min, int max, IntConsumer sink)
            implements EditorRow {}

    /** A previous/value/next control over a fixed list. */
    record Cycle(String label, List<String> values, int selected, IntConsumer sink)
            implements EditorRow {}

    /** A push button that runs an action rather than editing a field. */
    record Action(String label, String sprite, Runnable onPress, boolean fullWidth)
            implements EditorRow {
        public Action(String label, String sprite, Runnable onPress) {
            this(label, sprite, onPress, false);
        }
    }

    /** One Brain action line: toggle, chance, and modifier share one reference row. */
    record BrainAction(String label, boolean enabled, Consumer<Boolean> enabledSink,
                       int chance, IntConsumer chanceSink, float modifier,
                       Consumer<Float> modifierSink) implements EditorRow {
        @Override
        public boolean fullWidth() {
            return true;
        }
    }

    /** Empty half-column used when the reference intentionally leaves one side of a row blank. */
    record Spacer() implements EditorRow {}

    /**
     * Whether this row occupies the whole body width instead of one column.
     *
     * <p>Headings and informational text span; labelled controls pair up two per line, which is how
     * the reference menu fits nine or more rows into the same panel height.
     */
    default boolean fullWidth() {
        return false;
    }
}

package net.bullettrain.xenopixelsmod.client.combat.v2;

/**
 * The five combat-prompt plates and where text sits on each. Minecraft-free.
 *
 * <p>The outline says what kind of thing a prompt is before the words are read: a pill for a
 * punch that keeps the string going, a hex for a hit that ends it (a kick, a launcher, a smash),
 * a tray for the grab and the throw, an arrow for anything that travels, and a burst for the two
 * prompts that are about getting out of trouble.
 *
 * <p>The numbers are measured from the generated art ({@code tools/atlas-panels}): how far the
 * dark fill starts in from each side on the rows a line of text occupies, and the row the text
 * goes on. {@code CombatPromptPlatesTest} re-measures the shipped PNGs against them, so art
 * regenerated at a different border width cannot leave the labels sitting on the frame.
 */
public enum PromptPlate {
    PUNCH("light", 22, 8, 8, 7),
    FINISH("heavy", 22, 16, 16, 7),
    GRAB("grab", 22, 12, 12, 7),
    TRAVEL("rush", 22, 13, 16, 7),
    ALERT("alert", 32, 11, 11, 12);

    /** Clear pixels kept between the text and the edge of the fill, each side. */
    public static final int TEXT_PAD = 2;

    private final String kind;
    private final int height;
    private final int insetLeft;
    private final int insetRight;
    private final int textY;

    PromptPlate(String kind, int height, int insetLeft, int insetRight, int textY) {
        this.kind = kind;
        this.height = height;
        this.insetLeft = insetLeft;
        this.insetRight = insetRight;
        this.textY = textY;
    }

    /** The atlas name fragment: {@code combat_prompt_<kind>_w<width>}. */
    public String kind() {
        return kind;
    }

    public int height() {
        return height;
    }

    /** Columns from the plate's left edge to the first column of fill on the text rows. */
    public int insetLeft() {
        return insetLeft;
    }

    public int insetRight() {
        return insetRight;
    }

    /** Row, from the plate's top, that a line of text is drawn on. */
    public int textY() {
        return textY;
    }

    /** The plate width needed to hold text {@code textWidth} pixels wide. */
    public int widthFor(int textWidth) {
        return textWidth + insetLeft + insetRight + 2 * TEXT_PAD;
    }

    /** The widest text a plate {@code plateWidth} pixels wide holds. */
    public int textRoom(int plateWidth) {
        return plateWidth - insetLeft - insetRight - 2 * TEXT_PAD;
    }

    /** X, from the plate's left edge, at which text {@code textWidth} wide is centred in the fill. */
    public int textX(int plateWidth, int textWidth) {
        int room = plateWidth - insetLeft - insetRight;
        return insetLeft + Math.max(0, (room - textWidth) / 2);
    }
}

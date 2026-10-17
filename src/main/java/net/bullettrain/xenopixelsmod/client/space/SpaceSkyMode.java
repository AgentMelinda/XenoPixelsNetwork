package net.bullettrain.xenopixelsmod.client.space;

/**
 * Which space sky this client draws, per {@code /xenoset sky} and {@code spaceSkyMode} in
 * {@code config/xenopixelsmod-client.json}.
 *
 * <p>The three are ordered by cost, which is the point of the switch: {@link #VOID} draws one
 * gradient cube and allocates nothing else, {@link #STARS} adds the cached 1000-star buffer on top
 * of the cube, and {@link #OFF} draws nothing here at all so the dimension's own {@code SkyType.END}
 * rendering handles the frame. {@link #VOID} is the default because the star field is a nicety and a
 * space dimension is where a player is most likely to be watching a frame counter.
 *
 * <p>No Minecraft types: this is the stored setting's vocabulary, so {@link #byName} is testable
 * without a client, exactly like {@code MouseResponseCurve.byName}.
 */
public enum SpaceSkyMode {
    /** Black fading to a little purple at the bottom, one cube, no star buffer. */
    VOID("void"),
    /** The cube plus the cached star field — the full look, and the most work. */
    STARS("stars"),
    /** Nothing from this mod: vanilla's end-style void sky draws the frame. */
    OFF("off");

    public final String id;

    SpaceSkyMode(String id) {
        this.id = id;
    }

    /** Resolves a stored or typed value, defaulting rather than failing: a typo must not blank the sky. */
    public static SpaceSkyMode byName(String name) {
        if (name != null) {
            for (SpaceSkyMode mode : values()) {
                if (mode.id.equalsIgnoreCase(name.trim())) return mode;
            }
        }
        return VOID;
    }
}

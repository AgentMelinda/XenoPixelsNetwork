package net.bullettrain.xenopixelsmod.plot;

/**
 * Permission bits packed into {@link PlotArea#flags()}.
 *
 * <p>A small integer keeps the flag set inside the plot record rather than a parallel map, so a
 * plot is always one value and cannot exist with its flags missing. New flags take the next free
 * bit; existing bit values must never be renumbered because they are persisted.</p>
 */
public final class PlotFlags {

    /** Others may build inside the plot. Off means owner-only. */
    public static final int ALLOW_BUILD = 1;
    /** Others may open containers inside the plot. */
    public static final int ALLOW_CONTAINERS = 1 << 1;
    /** Others may interact with redstone and doors. */
    public static final int ALLOW_INTERACT = 1 << 2;
    /** Player-versus-player combat is permitted inside the plot. */
    public static final int ALLOW_PVP = 1 << 3;
    /** Others may enter the plot at all. Off means the plot is private space. */
    public static final int ALLOW_ENTRY = 1 << 4;

    /** What a freshly claimed plot starts with: owner-only, no public access. */
    public static final int DEFAULT = 0;

    private PlotFlags() {
    }

    public static boolean has(int flags, int flag) {
        return (flags & flag) != 0;
    }

    public static int set(int flags, int flag, boolean enabled) {
        return enabled ? (flags | flag) : (flags & ~flag);
    }
}
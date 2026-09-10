package net.bullettrain.xenopixelsmod.client.combat;

import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.minecraft.client.KeyMapping;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The moves whose direct input — a key of their own, or a gamepad chord — is being replaced by a
 * route through Controlify's radial menu or a DragonMineZ technique slot.
 *
 * <p>Each one keeps its old input; this is the switch that decides whether that input still acts.
 * The point is that moving a move to a better home never has to mean deleting the way people
 * already reach it: when the radial or slot route lands, the direct route is switched off rather
 * than removed, and anyone who preferred it — or who hits a problem with the new route — turns it
 * back on with {@code /xenobind} and gets the old behaviour back immediately, no restart and no
 * config file surgery.
 *
 * <p>Every route ships enabled, so nothing changes until a migration deliberately flips one.
 *
 * <p>Adding a route is one constant here plus one flag on {@link XenoClientConfig}; the command
 * and both input paths read this list rather than their own copies.
 */
public enum Bt3DirectBind {

    HAKAI("hakai", "Hakai",
            () -> XenoClientConfig.bt3DirectHakai, v -> XenoClientConfig.bt3DirectHakai = v),
    ZANZOKEN("zanzoken", "Zanzoken",
            () -> XenoClientConfig.bt3DirectZanzoken, v -> XenoClientConfig.bt3DirectZanzoken = v),
    MULTIFORM("multiform", "Shi Shin No Ken / Multi-Form",
            () -> XenoClientConfig.bt3DirectMultiform, v -> XenoClientConfig.bt3DirectMultiform = v),
    ULTIMATE("ultimate", "Ultimate",
            () -> XenoClientConfig.bt3DirectUltimate, v -> XenoClientConfig.bt3DirectUltimate = v),
    Z_BURST("zburst", "Z-Burst Dash",
            () -> XenoClientConfig.bt3DirectZBurst, v -> XenoClientConfig.bt3DirectZBurst = v),
    SONIC_SWAY_LEFT("sonic_left", "Sonic Sway Left",
            () -> XenoClientConfig.bt3DirectSonicSwayLeft,
            v -> XenoClientConfig.bt3DirectSonicSwayLeft = v),
    SONIC_SWAY_RIGHT("sonic_right", "Sonic Sway Right",
            () -> XenoClientConfig.bt3DirectSonicSwayRight,
            v -> XenoClientConfig.bt3DirectSonicSwayRight = v),
    SPARKING("sparking", "Sparking",
            () -> XenoClientConfig.bt3DirectSparking, v -> XenoClientConfig.bt3DirectSparking = v);

    private final String id;
    private final String label;
    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;

    Bt3DirectBind(String id, String label, BooleanSupplier getter, Consumer<Boolean> setter) {
        this.id = id;
        this.label = label;
        this.getter = getter;
        this.setter = setter;
    }

    /** Stable name used by {@code /xenobind} and in the config file. */
    public String id() {
        return id;
    }

    /** Human name for command feedback. */
    public String label() {
        return label;
    }

    /** Whether this move's own key or chord still fires it. */
    public boolean enabled() {
        return getter.getAsBoolean();
    }

    /** Switches the direct route on or off and persists the choice. */
    public void set(boolean on) {
        if (enabled() == on) return;
        setter.accept(on);
        XenoClientConfig.save();
    }

    /**
     * {@link KeyMapping#consumeClick()} that always drains the queue, but only reports the press
     * while this route is switched on.
     *
     * <p>Draining either way matters: a press held in the queue while the route is off would fire
     * the move the moment it was switched back on, which reads as the game acting on its own.
     */
    public boolean consume(KeyMapping mapping) {
        boolean clicked = mapping != null && mapping.consumeClick();
        return clicked && enabled();
    }

    /** Lookup by {@link #id()}, case-insensitive; {@code null} when nothing matches. */
    public static Bt3DirectBind byId(String id) {
        if (id == null) return null;
        String needle = id.toLowerCase(Locale.ROOT);
        for (Bt3DirectBind route : values()) {
            if (route.id.equals(needle)) return route;
        }
        return null;
    }
}

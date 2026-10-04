package net.bullettrain.xenopixelsmod.combat.combo;

/**
 * Horizontal-away plus up/down send distance for a combo finisher.
 *
 * <p>Minecraft-free. Axes are clamped to {@code >= 0}; {@code 0} on every axis means no knock.
 */
public record ComboKnockTravel(double distance, double up, double down) {

    public ComboKnockTravel {
        distance = Math.max(0.0, distance);
        up = Math.max(0.0, up);
        down = Math.max(0.0, down);
    }

    public boolean isZero() {
        return distance == 0.0 && up == 0.0 && down == 0.0;
    }
}

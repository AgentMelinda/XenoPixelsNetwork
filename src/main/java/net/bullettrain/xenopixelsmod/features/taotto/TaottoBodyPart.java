package net.bullettrain.xenopixelsmod.features.taotto;

import java.util.Locale;

/**
 * Minecraft 64×64 skin front islands used to place a Taotto canvas on a body part.
 */
public enum TaottoBodyPart {
    HEAD(8, 8, 8, 8),
    TORSO(20, 20, 8, 12),
    RIGHT_ARM(44, 20, 4, 12),
    LEFT_ARM(36, 52, 4, 12),
    RIGHT_LEG(4, 20, 4, 12),
    LEFT_LEG(20, 52, 4, 12);

    private final UvIsland front;

    TaottoBodyPart(int u, int v, int w, int h) {
        this.front = new UvIsland(u, v, w, h);
    }

    public UvIsland front() {
        return front;
    }

    public String label() {
        return switch (this) {
            case HEAD -> "Head";
            case TORSO -> "Torso";
            case RIGHT_ARM -> "Right arm";
            case LEFT_ARM -> "Left arm";
            case RIGHT_LEG -> "Right leg";
            case LEFT_LEG -> "Left leg";
        };
    }

    public static TaottoBodyPart fromName(String name) {
        if (name == null || name.isBlank()) {
            return TORSO;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return TORSO;
        }
    }

    public record UvIsland(int u, int v, int w, int h) {
    }
}

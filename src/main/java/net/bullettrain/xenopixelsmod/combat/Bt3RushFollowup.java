package net.bullettrain.xenopixelsmod.combat;

import java.util.UUID;

/** Pure X-X-X follow-up gate shared by server rules and unit tests. */
public final class Bt3RushFollowup {

    public static final int REQUIRED_COMBO_STEP = 3;
    public static final int WINDOW_TICKS = 12;

    private Bt3RushFollowup() {
    }

    public record Gate(UUID targetId, int expiresAtTick) {
    }

    public static Gate arm(int comboStep, UUID targetId, int nowTick) {
        if (comboStep != REQUIRED_COMBO_STEP || targetId == null) return null;
        return new Gate(targetId, nowTick + WINDOW_TICKS);
    }

    public static boolean accepts(Gate gate, UUID targetId, int nowTick) {
        return gate != null && targetId != null && gate.targetId().equals(targetId)
                && nowTick <= gate.expiresAtTick();
    }

    public static boolean expired(Gate gate, int nowTick) {
        return gate == null || nowTick > gate.expiresAtTick();
    }
}

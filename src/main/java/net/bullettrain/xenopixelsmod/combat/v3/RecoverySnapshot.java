package net.bullettrain.xenopixelsmod.combat.v3;

import java.util.UUID;

/** Recipient-only recovery state; clients never submit progress or a victim identity. */
public record RecoverySnapshot(UUID token, int completed, int total, Kind kind) {
    public enum Kind { RUSH, FINISHER, DASH }
    public RecoverySnapshot {
        if (token == null || kind == null || total < 1 || total > 1200 || completed < 0 || completed > total)
            throw new IllegalArgumentException("Invalid recovery snapshot");
    }
    public float progress() { return completed/(float) total; }
}

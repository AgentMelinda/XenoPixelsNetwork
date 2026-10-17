package net.bullettrain.xenopixelsmod.space.dragonball;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Suppresses repeated diagnostics until their signature changes or clears. */
final class DragonBallDiagnosticLatch {
    private final Map<String, String> active = new HashMap<>();

    boolean shouldReport(String key, String signature) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(signature, "signature");
        return !signature.equals(active.put(key, signature));
    }

    boolean clear(String key) {
        return active.remove(Objects.requireNonNull(key, "key")) != null;
    }

    void clearAll() {
        active.clear();
    }
}

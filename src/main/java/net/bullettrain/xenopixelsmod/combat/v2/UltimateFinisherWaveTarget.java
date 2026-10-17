package net.bullettrain.xenopixelsmod.combat.v2;

/** Internal mixin bridge for the finisher target ID synchronized with the native wave. */
public interface UltimateFinisherWaveTarget {
    int xenopixels$finisherTarget();
    void xenopixels$finisherTarget(int targetId);
}

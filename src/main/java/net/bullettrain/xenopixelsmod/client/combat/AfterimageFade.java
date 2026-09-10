package net.bullettrain.xenopixelsmod.client.combat;

public final class AfterimageFade {
    private AfterimageFade() {}

    public static float alpha(int mode, float configuredAlpha, float age, int lifetime) {
        float base = Math.max(0.05f, Math.min(1.0f, configuredAlpha));
        float progress = Math.max(0.0f, Math.min(1.0f, age / Math.max(1.0f, lifetime)));
        return switch (Math.max(1, Math.min(3, mode))) {
            case 1 -> base * (1.0f - progress);
            case 3 -> base;
            default -> 1.0f - progress;
        };
    }
}

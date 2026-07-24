package net.bullettrain.xenopixelsmod.client;

/**
 * Immutable per-frame snapshot of all data the Xeno HUD needs to render.
 * Produced by {@link XenoHudSnapshotFactory#capture(net.minecraft.client.Minecraft)}
 * so that DMZ-read / fallback / ratio-clamping logic lives in one place,
 * separate from the drawing code in {@link XenoHudOverlay}.
 *
 * Phase 3 of the LDLib HUD migration plan: this is a pure data extraction,
 * no rendering behavior changes.
 */
public final class XenoHudSnapshot {
    public final String name;
    public final boolean dmzPresent;
    /** Formatted "NN%" release text, or {@code null} when DMZ data isn't present. */
    public final String releaseText;

    public final float hpPercent;
    public final float kiPercent;
    public final float stmPercent;

    public final float curHp;
    public final float maxHp;
    public final float curKi;
    public final float maxKi;
    public final float curStm;
    public final float maxStm;

    public final boolean transforming;
    public final float transformChargePercent;

    public XenoHudSnapshot(String name, boolean dmzPresent, String releaseText,
                            float hpPercent, float kiPercent, float stmPercent,
                            float curHp, float maxHp, float curKi, float maxKi,
                            float curStm, float maxStm,
                            boolean transforming, float transformChargePercent) {
        this.name = name;
        this.dmzPresent = dmzPresent;
        this.releaseText = releaseText;
        this.hpPercent = hpPercent;
        this.kiPercent = kiPercent;
        this.stmPercent = stmPercent;
        this.curHp = curHp;
        this.maxHp = maxHp;
        this.curKi = curKi;
        this.maxKi = maxKi;
        this.curStm = curStm;
        this.maxStm = maxStm;
        this.transforming = transforming;
        this.transformChargePercent = transformChargePercent;
    }
}

package net.bullettrain.xenopixelsmod.compat.npc;

/** Client-safe aura animation math. No Minecraft types so unit tests can call it. */
public final class NpcAuraAnim {
    private NpcAuraAnim() {}

    /** Ring phase 0..1 from tick time so a missed frame cannot reset it. */
    public static float pulsePhase(int tickCount, float partial) {
        float cycle = (tickCount + partial) * 0.01f;
        return cycle - (float) Math.floor(cycle);
    }
}

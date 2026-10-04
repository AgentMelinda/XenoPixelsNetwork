package net.bullettrain.xenopixelsmod.compat.npc.brain.v4;

public final class NpcCombatContextV4 {
    public static final double MELEE_RANGE = 4.5;
    public static final double MID_RANGE = 12.0;
    public static final double OUT_RANGE = 28.0;

    public double distance;
    public double healthFraction = 1.0;
    public double energyFraction = 1.0;
    public boolean targetApproaching;
    public boolean targetBlocking;
    public boolean targetHelpless;
    public boolean targetCasting;
    public boolean targetFiring;
    public boolean canRecover;
    public boolean canCombo;
    public boolean canGuardBreak;
    public boolean canVanish;
    public boolean canDash;
    public boolean canHitscan;
    public boolean canTravel;
    public boolean canZone;
    public boolean canMelee = true;
}

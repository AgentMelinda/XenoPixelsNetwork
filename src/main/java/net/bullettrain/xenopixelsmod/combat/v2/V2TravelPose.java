package net.bullettrain.xenopixelsmod.combat.v2;

import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.skills.Skill;
import net.bullettrain.xenopixelsmod.command.XenoAuraCommands;
import net.bullettrain.xenopixelsmod.network.ChaseFlightOwnership;
import net.minecraft.server.level.ServerPlayer;

/**
 * What a travelling fighter looks like: aura lit, and in DragonMineZ's flight pose if they have
 * learned to fly.
 *
 * <p>This is the look v1's chase has, with the same two rules. A chase never hands out a skill:
 * a fighter who has not learned Fly crosses the gap without the pose. And a chase only takes back
 * what it gave: an aura the fighter already had on stays on, and a flight the fighter turned off
 * or switched themselves mid-travel is theirs and is left alone
 * (only a still-active Search Fly mode can be restored).
 */
public final class V2TravelPose {

    /** DMZ flight mode 0 is Search Fly, 1 is Combat Fly. */
    private static final int SEARCH_FLIGHT_MODE = 0;

    /**
     * What the travel changed, and so what it must change back.
     *
     * @param auraTurnedOn    the aura was off and the travel lit it
     * @param flightBorrowed  the travel changed the fighter's flight at all
     * @param flyActiveBefore DragonMineZ fly was already active before the travel
     */
    public record Snapshot(boolean auraTurnedOn, boolean flightBorrowed, boolean flyActiveBefore,
                    int flightModeBefore, boolean flyingBefore, boolean mayflyBefore) {
        static final Snapshot NONE = new Snapshot(false, false, false, 0, false, false);
    }

    private V2TravelPose() {}

    /**
     * @param aura   light the aura if it is off
     * @param flight take the flight pose if the fighter has learned Fly
     */
    public static Snapshot begin(ServerPlayer player, boolean aura, boolean flight) {
        boolean auraTurnedOn = false;
        if (aura && !XenoAuraCommands.isOn(player)) {
            auraTurnedOn = XenoAuraCommands.apply(player, true);
        }
        if (!flight) return auraTurnedOn ? new Snapshot(true, false, false, 0, false, false) : Snapshot.NONE;

        StatsData data = V2Support.stats(player);
        Skill fly = data == null || data.getSkills() == null ? null : data.getSkills().getSkill("fly");
        if (fly == null || fly.getLevel() <= 0 || data.getStatus() == null) {
            return auraTurnedOn ? new Snapshot(true, false, false, 0, false, false) : Snapshot.NONE;
        }
        boolean wasActive = fly.isActive();
        int modeBefore = Math.max(0, data.getStatus().getFlightMode());
        boolean flyingBefore = player.getAbilities().flying;
        boolean mayflyBefore = player.getAbilities().mayfly;
        if (!wasActive || modeBefore != SEARCH_FLIGHT_MODE) {
            fly.setActive(true);
            data.getStatus().setFlightMode(SEARCH_FLIGHT_MODE);
            sync(player);
        }
        if (!wasActive) {
            if (!player.getAbilities().mayfly || !player.getAbilities().flying) {
                player.getAbilities().mayfly = true;
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
            }
        }
        return new Snapshot(auraTurnedOn, true, wasActive, modeBefore, flyingBefore, mayflyBefore);
    }

    /**
     * True when the travel borrowed flight and the fighter has since lost it: they toggled Fly
     * off, or DragonMineZ ended it. The travel must stop rather than carry them on without it.
     */
    public static boolean flightLost(ServerPlayer player, Snapshot snapshot) {
        if (snapshot == null || !snapshot.flightBorrowed()) return false;
        StatsData data = V2Support.stats(player);
        Skill fly = data == null || data.getSkills() == null ? null : data.getSkills().getSkill("fly");
        return fly == null || !fly.isActive();
    }

    public static void end(ServerPlayer player, Snapshot snapshot) {
        if (snapshot == null) return;
        if (snapshot.auraTurnedOn()) XenoAuraCommands.apply(player, false);
        if (!snapshot.flightBorrowed()) return;
        StatsData data = V2Support.stats(player);
        Skill fly = data == null || data.getSkills() == null ? null : data.getSkills().getSkill("fly");
        if (fly == null || data.getStatus() == null) return;
        if (!ChaseFlightOwnership.mayRestoreSearchMode(fly.isActive(), data.getStatus().getFlightMode())) {
            return;
        }
        data.getStatus().setFlightMode(snapshot.flightModeBefore());
        fly.setActive(snapshot.flyActiveBefore());
        player.resetFallDistance();
        if (!snapshot.flyActiveBefore() && !player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = snapshot.mayflyBefore();
            player.getAbilities().flying = snapshot.flyingBefore();
            player.onUpdateAbilities();
        }
        sync(player);
    }

    private static void sync(ServerPlayer player) {
        try {
            NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
        } catch (Throwable ignored) {
            // A missed sync corrects itself on DragonMineZ's next one.
        }
    }
}

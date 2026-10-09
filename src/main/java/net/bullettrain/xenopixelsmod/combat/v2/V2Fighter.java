package net.bullettrain.xenopixelsmod.combat.v2;

import net.bullettrain.xenopixelsmod.api.registry.Bt3RushDefinition;
import net.bullettrain.xenopixelsmod.combat.v2.combo.ComboInput;
import net.bullettrain.xenopixelsmod.combat.v2.combo.ComboNode;

import java.util.UUID;

/**
 * Everything the server knows about one player's v2 fight, in one object.
 *
 * <p>v1 kept this across a dozen static maps in five classes, each with its own logout handler
 * and its own idea of which clock it was on. Here a fighter is one value in one map
 * ({@link V2FighterStore}), every tick field is on the server tick, and clearing a fighter is
 * dropping the object. Only the server thread touches it.
 */
public final class V2Fighter {

    /** What a travel is for; decides what happens on arrival. */
    public enum TravelKind {
        CHASE,
        Z_BURST,
        DRAGON_DASH,
        ULTIMATE_FINISHER
    }

    /** Where a travel is on its way round an obstacle. */
    enum TravelRoute {
        DIRECT,
        /** Climbing to a waypoint above whatever is in the way. */
        ASCEND,
        /** Over the obstacle and coming down on the target. */
        DIVE
    }

    final UUID playerId;

    V2State state = V2State.NEUTRAL;
    /** Tick the last attack input was accepted on, for the anti-spam floor. */
    int lastInputTick = Integer.MIN_VALUE / 2;

    // ---- combo ----
    ComboNode node;
    int nodeStartTick;
    /** The node's hit has not been resolved yet. */
    boolean hitPending;
    boolean hitLanded;
    int targetId = -1;
    V2Direction nodeDirection = V2Direction.NONE;
    /** 0..1 charge carried by a held attack into the node it starts; 0 for a tap. */
    float nodeCharge;
    boolean chargedStrike;
    boolean chargedTeleport;
    int chargeStartTick = -1;
    boolean chargeKick;
    int chargeTargetId = -1;

    ComboInput buffered;
    int bufferedTick = -1;
    int bufferedTargetId = -1;
    V2Direction bufferedDirection = V2Direction.NONE;
    float bufferedCharge;

    // ---- being hit ----
    /** Attacks pass through this fighter until this tick after a vanish. */
    int iframesUntilTick;
    int stunUntilTick;
    int counterOpenUntilTick;
    int counterLockedUntilTick;
    int counterAttackerId = -1;

    // ---- vanish ----
    int vanishReadyTick;

    // ---- chase / homing ----
    int homingVictimId = -1;
    int homingUntilTick;

    TravelKind travelKind;
    int travelTargetId = -1;
    int travelStartTick;
    int travelTimeoutTicks;
    double travelSpeed;
    double travelArrive;
    boolean travelNoGravityBefore;
    float travelCharge;
    /** Aura and flight borrowed for the travel, to be handed back when it ends. */
    V2TravelPose.Snapshot travelPose;
    TravelRoute travelRoute = TravelRoute.DIRECT;
    boolean travelHasWaypoint;
    double travelWaypointX;
    double travelWaypointY;
    double travelWaypointZ;
    /** Consecutive ticks the travel could not move at all. */
    int travelBlockedTicks;

    // ---- dash ----
    int dashReadyTick;
    int dashFollowTargetId = -1;
    int dashFollowUntilTick;

    // ---- rush ----
    Bt3RushDefinition rush;
    int rushTargetId = -1;
    int rushStartTick;
    int rushNextImpact;
    int rushSequenceId;

    // ---- DragonMineZ strike technique in progress ----
    int strikeTargetId = -1;
    int strikeStartTick;
    /** What the strike does to its target once DragonMineZ lets both fighters go. */
    HitReaction strikeReaction;
    boolean strikeChase;
    /** The target's health and absorption when the strike began, to tell afterwards whether it did anything. */
    float strikeTargetHealth;

    // ---- grab ----
    int grabVictimId = -1;
    int grabStartTick;
    int grabConnectTick;
    V2Direction grabDirection = V2Direction.NONE;
    int grabReadyTick;
    /** Set on the victim: entity id of whoever is holding them, or -1. */
    int grabbedById = -1;
    int grabbedSinceTick;

    /** Last state sent to the client, so an unchanged fighter costs no packets. */
    long lastSyncKey = Long.MIN_VALUE;

    V2Fighter(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID playerId() {
        return playerId;
    }

    public V2State state() {
        return state;
    }

    void clearCombo() {
        node = null;
        hitPending = false;
        hitLanded = false;
        nodeCharge = 0f;
        chargedStrike = false;
        chargedTeleport = false;
        nodeDirection = V2Direction.NONE;
        clearBuffer();
    }

    void clearBuffer() {
        buffered = null;
        bufferedTick = -1;
        bufferedTargetId = -1;
        bufferedDirection = V2Direction.NONE;
        bufferedCharge = 0f;
    }

    void clearStrike() {
        strikeTargetId = -1;
        strikeReaction = null;
        strikeChase = false;
    }

    void clearDashFollow() {
        dashFollowTargetId = -1;
        dashFollowUntilTick = 0;
    }

    int dashWindowEnd(int now) {
        if (travelKind == TravelKind.DRAGON_DASH) return travelStartTick + travelTimeoutTicks;
        return dashFollowTargetId >= 0 && now < dashFollowUntilTick ? dashFollowUntilTick : 0;
    }

    int dashWindowTarget(int now) {
        return dashWindowEnd(now) > now
                ? (travelKind == TravelKind.DRAGON_DASH ? travelTargetId : dashFollowTargetId) : -1;
    }

    boolean traveling() {
        return travelKind != null;
    }

    boolean invulnerable(int now) {
        return now < iframesUntilTick;
    }

    /**
     * True when nothing is in progress and no window is open, so the ticker can skip this fighter
     * entirely.
     */
    boolean idle(int now) {
        return state == V2State.NEUTRAL && node == null && buffered == null && !traveling()
                && now > counterOpenUntilTick && now > homingUntilTick && now >= stunUntilTick
                && now >= grabReadyTick && grabbedById < 0 && now >= dashFollowUntilTick;
    }
}

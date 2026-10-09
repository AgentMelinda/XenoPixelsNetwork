package net.bullettrain.xenopixelsmod.combat.v3;

import java.util.UUID;

/** One server-thread-owned fighter. A new session invalidates every queued previous intent. */
public final class V3Fighter {
    final UUID playerId;
    private UUID session = UUID.randomUUID();
    private int acknowledgedSequence = -1;
    V3State state = V3State.IDLE;
    V3TargetSnapshot target;
    UUID approvedTarget;
    long targetRevision;
    long targetSentTick;
    long acquireReadyTick;
    String targetRefusal;
    int chargeTicks;
    int windowTicksLeft;
    int windowTicksTotal;
    long dashReadyTick;
    long heavyReadyTick;
    int meleeCombo;
    long meleeComboLastTick = -1;
    long chargeStartTick = -1;
    int chargeStartSequence = -1;
    final V3Motion.Lease motion = new V3Motion.Lease();
    UUID travelTarget;
    net.minecraft.world.phys.Vec3 travelOffset = net.minecraft.world.phys.Vec3.ZERO;
    double travelSpeed;
    double travelArrive = 2.4;
    long travelStartTick;
    int travelTimeout;
    int travelWait;
    int travelBlocked;
    net.bullettrain.xenopixelsmod.combat.v2.V2TravelPose.Snapshot travelPose;
    UUID dashTarget;
    boolean dashCrossed;
    long dashFollowReadyTick;
    V3Direction dashDirection = V3Direction.NONE;
    V3Window window = V3Window.NONE;
    UUID windowTarget;
    long vanishReadyTick;
    UUID grabVictim;
    UUID grabbedBy;
    long grabStartTick;
    long grabReadyTick;
    V3Direction grabDirection = V3Direction.NONE;
    boolean chargeKick;
    final V3Heavy.Transaction heavy = new V3Heavy.Transaction();
    V3Fighter(UUID playerId) { this.playerId = playerId; }
    public UUID session() { return session; }
    public int acknowledgedSequence() { return acknowledgedSequence; }
    public boolean admit(UUID suppliedSession, int sequence) {
        if (!session.equals(suppliedSession) || sequence < 0 || sequence <= acknowledgedSequence) return false;
        acknowledgedSequence = sequence;
        return true;
    }
    void rotateSession() {
        session = UUID.randomUUID();
        acknowledgedSequence = -1;
        cancelLiveState();
        clearApprovedLock();
    }
    /**
     * Cancels gestures/windows/travel ownership while keeping the approved lock.
     * Owner rule 2026-10-08: a failed or refused action must never cost the lock.
     */
    void cancelLiveState() {
        state = V3State.IDLE;
        chargeTicks = windowTicksLeft = windowTicksTotal = 0;
        window = V3Window.NONE;
        windowTarget = null;
        chargeStartTick = -1;
        chargeStartSequence = -1;
        chargeKick = false;
        travelTarget = null;
        travelWait = travelBlocked = 0;
        dashTarget = null;
        dashCrossed = false;
        dashDirection = V3Direction.NONE;
        grabVictim = null;
        grabbedBy = null;
        // Cooldowns and approvedTarget deliberately survive cancellation.
    }
    /** Explicit unlock / gone-target path. Does not cancel an unrelated gesture by itself. */
    void clearApprovedLock() {
        approvedTarget = null;
        target = null;
    }

    boolean hasLiveOwnership() {
        return state != V3State.IDLE || approvedTarget != null || chargeStartTick >= 0 || travelTarget != null
                || grabVictim != null || grabbedBy != null || motion.owner() != null || travelPose != null;
    }
}

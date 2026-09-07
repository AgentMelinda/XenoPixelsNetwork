package net.bullettrain.xenopixelsmod.combat.clone;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Server-owned membership: recalling bodies remain owned until arrival or loss. */
final class CloneSplitState<T> {
    private final Set<T> members;
    private final int bodies;
    private boolean recalling;

    CloneSplitState(Collection<T> copies) {
        members = new LinkedHashSet<>(copies);
        bodies = members.size() + 1;
    }

    int bodies() {
        return bodies;
    }

    List<T> members() {
        return List.copyOf(members);
    }

    boolean contains(T member) {
        return members.contains(member);
    }

    boolean isEmpty() {
        return members.isEmpty();
    }

    boolean isRecalling() {
        return recalling;
    }

    boolean beginRecall() {
        if (recalling || members.isEmpty()) return false;
        recalling = true;
        return true;
    }

    boolean remove(T member) {
        return members.remove(member);
    }

    /** Consumes membership before the caller restores health, preventing duplicate refunds. */
    float arrive(T member, float survivingHealth) {
        return recalling && members.remove(member) ? Math.max(0f, survivingHealth) : 0f;
    }

    static float healthShare(float currentHealth, int bodies) {
        return Math.max(0f, currentHealth) / Math.max(1, bodies);
    }

    static float reunitedHealth(float ownerHealth, float maxHealth, float returnedHealth) {
        // A dead owner cannot be resurrected by a late arrival.
        return ownerHealth <= 0f ? 0f
                : Math.min(maxHealth, ownerHealth + Math.max(0f, returnedHealth));
    }
}

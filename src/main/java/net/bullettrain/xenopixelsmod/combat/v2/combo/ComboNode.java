package net.bullettrain.xenopixelsmod.combat.v2.combo;

import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;
import net.bullettrain.xenopixelsmod.combat.v2.HitReaction;
import net.bullettrain.xenopixelsmod.combat.v2.V2Direction;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * One authored beat of a combo.
 *
 * <p>Minecraft-free. A node says what the beat looks like, what it costs, what it does to the
 * victim and which nodes may follow it; nothing here moves anyone or spends anything.
 *
 * <p>A beat has three stretches, counted from the tick its input was accepted: {@code startup}
 * until the hit, {@code cancel} while the fighter recovers and nothing new may start, then the
 * {@code window} in which a follow-up is accepted. Startup plus cancel is therefore the fastest a
 * string can be thrown, whatever the click rate.
 *
 * @param id              unique within its graph
 * @param intent          the pose with no direction held
 * @param variants        what a held direction changes; a missing direction changes nothing
 * @param startupTicks    ticks between the input and the hit
 * @param cancelTicks     ticks after the hit before a follow-up beat may start
 * @param windowTicks     ticks after that in which a follow-up input is accepted
 * @param damageScale     multiplier on the fighter's DragonMineZ melee damage
 * @param kiCost          ki spent when the beat starts
 * @param staminaCost     stamina spent when the beat starts
 * @param reaction        what a landed hit does
 * @param chargedReaction what it does instead when the attack was held to charge, or null
 * @param action          what the beat is: a strike resolved here, or a hand-off to grab or rush
 * @param next            follow-up node id per input; an absent input ends the string
 */
public record ComboNode(
        String id,
        Bt3AnimationIntent intent,
        Map<V2Direction, Variant> variants,
        int startupTicks,
        int cancelTicks,
        int windowTicks,
        float damageScale,
        float kiCost,
        float staminaCost,
        HitReaction reaction,
        HitReaction chargedReaction,
        Action action,
        Map<ComboInput, String> next) {

    /** What kind of beat a node is. */
    public enum Action {
        STRIKE,
        GRAB,
        RUSH
    }

    /**
     * What holding a direction turns a beat into.
     *
     * @param intent   the pose
     * @param reaction what the hit does, or null to keep the node's own
     */
    public record Variant(Bt3AnimationIntent intent, HitReaction reaction) {
        public Variant {
            if (intent == null) throw new IllegalArgumentException("variant intent");
        }
    }

    public ComboNode {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("node id");
        if (intent == null) throw new IllegalArgumentException(id + ": intent");
        if (reaction == null) throw new IllegalArgumentException(id + ": reaction");
        if (action == null) action = Action.STRIKE;
        startupTicks = Math.max(0, startupTicks);
        cancelTicks = Math.max(0, cancelTicks);
        windowTicks = Math.max(0, windowTicks);
        damageScale = Math.max(0f, damageScale);
        kiCost = Math.max(0f, kiCost);
        staminaCost = Math.max(0f, staminaCost);
        variants = variants == null || variants.isEmpty()
                ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(variants));
        next = next == null || next.isEmpty()
                ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(next));
    }

    /** The pose for this beat with {@code direction} held. */
    public Bt3AnimationIntent intentFor(V2Direction direction) {
        Variant variant = direction == null ? null : variants.get(direction);
        return variant == null ? intent : variant.intent();
    }

    /**
     * What a landed hit does.
     *
     * <p>A held direction that authors its own reaction wins: W and a kick is the launcher however
     * long the kick was held. Otherwise a charged attack uses the node's charged reaction, and an
     * uncharged one the node's own.
     *
     * @param charged whether the attack was held to charge at all
     */
    public HitReaction reactionFor(V2Direction direction, boolean charged) {
        Variant variant = direction == null ? null : variants.get(direction);
        if (variant != null && variant.reaction() != null) return variant.reaction();
        if (charged && chargedReaction != null) return chargedReaction;
        return reaction;
    }

    /** First tick, counted from the input, on which a follow-up beat may start. */
    public int openTick() {
        return startupTicks + cancelTicks;
    }

    /** Last tick, counted from the input, on which a follow-up is still accepted. */
    public int closeTick() {
        return startupTicks + cancelTicks + windowTicks;
    }

    /** Bitmask of {@link ComboInput}s that lead somewhere from here. */
    public int branchMask() {
        int mask = 0;
        for (ComboInput input : next.keySet()) mask |= input.bit();
        return mask;
    }

    /** True when nothing follows this beat. */
    public boolean terminal() {
        return next.isEmpty();
    }
}

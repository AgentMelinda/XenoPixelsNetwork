package net.bullettrain.xenopixelsmod.combat.v3.technique;

import java.util.List;
import net.bullettrain.xenopixelsmod.combat.v3.anim.V3AnimationCatalog;

/**
 * One BT3 attack as V3 runs it.
 *
 * @param name            the English name players see
 * @param sourceLabel     the label exactly as the reference video shows it (Italian)
 * @param kiTechnique     the native DragonMineZ ki technique fired by this attack's energy beats, or null
 * @param animationStatus {@code archetype_placeholder} until this attack has its own compared clip
 * @param sourceStartsMs  where the attack appears in the reference video
 */
public record V3TechniqueDefinition(String id, String name, String sourceLabel, String type, int durationTicks,
                                    double kiCost,
                                    int cooldownTicks, String kiTechnique, String animationStatus,
                                    List<Long> sourceStartsMs, List<V3Beat> beats, List<V3CameraBeat> camera,
                                    Double range) {
    public V3TechniqueDefinition(String id, String name, String sourceLabel, String type, int durationTicks,
                                 double kiCost, int cooldownTicks, String kiTechnique, String animationStatus,
                                 List<Long> sourceStartsMs, List<V3Beat> beats) {
        this(id, name, sourceLabel, type, durationTicks, kiCost, cooldownTicks, kiTechnique,
                animationStatus, sourceStartsMs, beats, List.of(), null);
    }
    public V3TechniqueDefinition(String id, String name, String sourceLabel, String type, int durationTicks,
                                 double kiCost, int cooldownTicks, String kiTechnique, String animationStatus,
                                 List<Long> sourceStartsMs, List<V3Beat> beats, List<V3CameraBeat> camera) {
        this(id, name, sourceLabel, type, durationTicks, kiCost, cooldownTicks, kiTechnique,
                animationStatus, sourceStartsMs, beats, camera, null);
    }
    public static final String OWNED_PREFIX = "xenopixelsmod:bt3_";
    public static final String COMPLETE = "reference_compared";

    public V3TechniqueDefinition {
        if (id == null || !id.startsWith(OWNED_PREFIX) || id.length() > 96 || !id.equals(id.toLowerCase(java.util.Locale.ROOT))
                || name == null || name.isBlank() || name.length() > 64
                || sourceLabel == null || sourceLabel.isBlank() || sourceLabel.length() > 64
                || type == null || type.isBlank()
                || !Double.isFinite(kiCost) || kiCost < 0 || kiCost > 100000
                || cooldownTicks < 0 || cooldownTicks > 72000 || animationStatus == null
                || beats == null || beats.isEmpty() || beats.size() > 128 || sourceStartsMs == null
                || (range != null && (!Double.isFinite(range) || range < 1 || range > 128))) {
            throw new IllegalArgumentException("Invalid V3 technique " + id);
        }
        beats = List.copyOf(beats);
        sourceStartsMs = List.copyOf(sourceStartsMs);
        camera = V3CameraBeat.validate(camera, durationTicks);
        int last = -1;
        boolean charged = false;
        boolean holdSeen = false;
        boolean holdAwaitingRelease = false;
        boolean releaseSeen = false;
        for (V3Beat beat : beats) {
            if (beat.tick() < last) throw new IllegalArgumentException("Beats out of order in " + id);
            last = beat.tick();
            if (beat.kind() == V3Beat.Kind.POSE && beat.payload().startsWith(V3AnimationCatalog.PREFIX)) {
                int clipTicks = V3AnimationCatalog.durationTicks(beat.payload());
                if (!V3AnimationCatalog.isPlayable(beat.payload()) || clipTicks < 1) {
                    throw new IllegalArgumentException("Missing occurrence animation " + beat.payload() + " in " + id);
                }
                if (durationTicks < beat.tick() + clipTicks) {
                    throw new IllegalArgumentException("Duration does not cover occurrence animation "
                            + beat.payload() + " in " + id);
                }
            }
            if (beat.kind() == V3Beat.Kind.KI_CHARGE) {
                if (kiTechnique == null || kiTechnique.isBlank()) throw new IllegalArgumentException("No projectile for " + id);
                charged = true;
            } else if (beat.kind() == V3Beat.Kind.KI_HOLD) {
                if (!charged || holdSeen || releaseSeen) throw new IllegalArgumentException("Invalid ki hold gate in " + id);
                holdSeen = true;
                holdAwaitingRelease = true;
            } else if (beat.kind() == V3Beat.Kind.KI_RELEASE) {
                if (!charged) throw new IllegalArgumentException("Release without a charge in " + id);
                charged = false;
                holdAwaitingRelease = false;
                releaseSeen = true;
            }
        }
        if (holdAwaitingRelease) throw new IllegalArgumentException("Ki hold without release in " + id);
        if (beats.getLast().kind() != V3Beat.Kind.END) throw new IllegalArgumentException("No END beat in " + id);
        if (durationTicks < last || durationTicks > V3Beat.MAX_TICK) {
            throw new IllegalArgumentException("Duration does not cover the beats of " + id);
        }
    }

    /** Only a clip compared against its reference interval counts as finished. */
    public boolean choreographyComplete() {
        return COMPLETE.equals(animationStatus);
    }
}

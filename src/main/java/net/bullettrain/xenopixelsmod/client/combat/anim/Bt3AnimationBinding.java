package net.bullettrain.xenopixelsmod.client.combat.anim;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationCatalog;
import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Set;

/**
 * What each {@link Bt3AnimationIntent} plays, on either animation backend.
 *
 * <h2>Why there are two backends</h2>
 * PlayerAnimationLibrary cannot draw in this modpack. DragonMineZ's {@code PlayerRendererMixin}
 * injects at the head of {@code PlayerRenderer.render} and calls {@code ci.cancel()} for anybody
 * with a DMZ character, substituting its own GeckoLib model; every PAL mixin targets
 * {@code PlayerRenderer}/{@code PlayerModel}/{@code HumanoidModel}, so PAL poses a model that is
 * never drawn. Verified in game - {@code /xenopal play} reports a successful trigger and nothing
 * moves.
 *
 * <p>So the DMZ backend is the one that renders, and it is the default. The PAL side is kept rather
 * than deleted so the two can still be compared if DMZ's renderer ever changes, or on a profile
 * with no DMZ character where the vanilla player renderer does run.
 *
 * <h2>Where the names live</h2>
 * The intent-to-animation-name table moved to {@link Bt3AnimationCatalog}, which is common code, so
 * the server can name a clip for an NPC to play. This class keeps the two things that are genuinely
 * client-side: the PAL clip id, and how fast a clip has to run to fit one mash beat.
 *
 * <p>Our animations ship in {@code assets/xenopixelsmod/animations/entity/bt3_combat.animation.json}
 * and reach DragonMineZ through the two mixins in
 * {@code net.bullettrain.xenopixelsmod.mixin.compat.dmz} - one appends that file to the player
 * model's GeckoLib fallback list, the other registers the names with DMZ's
 * {@code CombatAnimationResolver}, which otherwise rejects any name it did not load itself.
 */
public final class Bt3AnimationBinding {

    /**
     * Play through PAL instead of DragonMineZ. Off because PAL renders nothing while DMZ owns the
     * player renderer; kept as an A/B switch rather than deleting the PAL path.
     */
    public static final boolean USE_PAL = false;

    /** The animation file this mod adds to DragonMineZ's GeckoLib lookup. */
    public static final ResourceLocation DMZ_ANIMATION_FILE =
            ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "animations/entity/bt3_combat.animation.json");

    /** Ticks per second, for turning a clip's authored length into beats. */
    private static final float TICKS_PER_SECOND = 20.0f;

    /**
     * @param palClip     clip id for the PAL backend
     * @param dmzAnim     DragonMineZ animation name for the requested generation - either one of
     *                    ours ({@code combat.xeno_*}) or a stock DMZ animation where theirs is
     *                    already the right move
     * @param speed       authored playback multiplier
     * @param clipSeconds the clip's {@code animation_length}, so a mash beat can work out how fast
     *                    it has to run to finish inside the beat
     */
    public record Binding(ResourceLocation palClip, String dmzAnim, float speed, float clipSeconds) {
    }

    private Bt3AnimationBinding() {
    }

    /** Never null for any declared intent. Resolved against the shipped animation generation. */
    public static Binding of(Bt3AnimationIntent intent) {
        return of(intent, Bt3AnimationCatalog.GEN_DEFAULT);
    }

    /** As {@link #of(Bt3AnimationIntent)}, for a specific generation. */
    public static Binding of(Bt3AnimationIntent intent, int generation) {
        Bt3AnimationCatalog.Clip clip = Bt3AnimationCatalog.clipFor(intent, generation);
        if (clip == null) {
            return null;
        }
        return new Binding(palClip(intent), clip.name(), Bt3AnimationCatalog.speedOf(intent),
                clip.seconds());
    }

    /**
     * How fast to run {@code intent}'s clip so one beat of a held mash plays it through.
     *
     * <p>The mash fires a beat on a fixed cadence while the clips it names are longer than that
     * cadence, so at the authored speed each swing was cut off partway and restarted from its
     * wind-up - the 360s got barely a quarter turn before snapping back. Rather than re-authoring
     * for every possible beat length, the clips are sped up to fit. Generation 3 exists so that
     * scaling stays small: its clips are authored at about one beat, so they run near 1.0x where an
     * older 360 needed 2.4x.
     *
     * <p>Only ever speeds a clip up. A binding whose authored speed is already higher keeps it, and
     * a clip shorter than the beat plays at its authored speed and holds its last pose.
     */
    public static float mashSpeed(Bt3AnimationIntent intent, int beatTicks) {
        return mashSpeed(intent, beatTicks, Bt3AnimationCatalog.GEN_DEFAULT);
    }

    public static float mashSpeed(Bt3AnimationIntent intent, int beatTicks, int generation) {
        Binding binding = of(intent, generation);
        if (binding == null) {
            return 1.0f;
        }
        if (beatTicks <= 0 || binding.clipSeconds() <= 0.0f) {
            return binding.speed();
        }
        float clipTicks = binding.clipSeconds() * TICKS_PER_SECOND;
        return Math.max(binding.speed(), clipTicks / beatTicks);
    }

    /**
     * Copies {@code existing} and appends {@link #DMZ_ANIMATION_FILE} if it is not already there.
     * Used by the DragonMineZ player-model mixin so GeckoLib searches our file after DMZ's own.
     */
    public static ResourceLocation[] withAnimationFile(ResourceLocation[] existing) {
        ResourceLocation ours = DMZ_ANIMATION_FILE;
        if (existing == null) {
            return new ResourceLocation[] {ours};
        }
        for (ResourceLocation entry : existing) {
            if (ours.equals(entry)) {
                return existing;
            }
        }
        ResourceLocation[] extended = new ResourceLocation[existing.length + 1];
        System.arraycopy(existing, 0, extended, 0, existing.length);
        extended[existing.length] = ours;
        return extended;
    }

    /**
     * The {@code combat.xeno_*} names this mod ships, which DragonMineZ's
     * {@code CombatAnimationResolver} must be told about or it resolves them to nothing.
     */
    public static Set<String> customAnimationNames() {
        return Bt3AnimationCatalog.customAnimationNames();
    }

    /**
     * PAL clip ids are derived from the intent rather than tabled - every one of them was already
     * the lower-cased intent name under a {@code bt3_} prefix, and the PAL path is dormant anyway.
     */
    private static ResourceLocation palClip(Bt3AnimationIntent intent) {
        return ResourceLocation.fromNamespaceAndPath(
                XenoPixelsMod.MOD_ID, "bt3_" + intent.name().toLowerCase(Locale.ROOT));
    }
}

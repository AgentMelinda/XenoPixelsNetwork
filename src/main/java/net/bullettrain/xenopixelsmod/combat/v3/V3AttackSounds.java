package net.bullettrain.xenopixelsmod.combat.v3;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-side sounds for the V3 heavy and charged attacks.
 *
 * <p>Why these exist: DragonMineZ plays its punch sounds from
 * {@code ForgeCommonEvents.onPlayerAttack(AttackEntityEvent)} (decompiled 2.1.3, lines 324-374),
 * which only fires through {@code Player.attack}. V3's heavy and charged strikes land through
 * {@code LivingEntity.hurt} directly ({@link V3Heavy#strike}) so that event never fires and the
 * right-click heavy and both charged attacks were silent. The same DragonMineZ sound events are
 * replayed here at the equivalent moments; nothing is played twice because the native path is not
 * taken for these actions.
 *
 * <p>All calls are no-ops when {@code /xenoset v3.attackSounds false}. Every DragonMineZ lookup is
 * guarded so a missing registry entry degrades to silence, never to a crash.
 */
public final class V3AttackSounds {
    private V3AttackSounds() {}

    private static boolean enabled() {
        return V3Config.get().attackSounds() && V3Config.get().attackSoundVolume() > 0;
    }

    static float volume(float base) {
        return base * (float) V3Config.get().attackSoundVolume();
    }

    /** A committed heavy has audible movement even when its target dodges out of reach. */
    public static void heavySwing(ServerPlayer attacker) {
        if (!enabled()) return;
        SoundEvent sound;
        try {
            sound = com.dragonminez.common.init.MainSounds.FIST_PUNCH.get();
        } catch (RuntimeException | LinkageError unavailable) {
            return;
        }
        if (sound != null) attacker.level().playSound(null, attacker.getX(),
                attacker.getY() + attacker.getBbHeight() * 0.6, attacker.getZ(), sound,
                SoundSource.PLAYERS, volume(0.65f), 0.9f);
    }

    /** One spark when the charge crosses its full threshold; holding longer never repeats it. */
    public static void chargeReady(ServerPlayer attacker) {
        if (!enabled()) return;
        SoundEvent sound;
        try {
            sound = com.dragonminez.common.init.MainSounds.KI_SPARKS.get();
        } catch (RuntimeException | LinkageError unavailable) {
            return;
        }
        if (sound != null) attacker.level().playSound(null, attacker.getX(), attacker.getY() + 1.0,
                attacker.getZ(), sound, SoundSource.PLAYERS, volume(0.65f), 1.4f);
    }

    /** A heavy or charged strike that connected for damage: DragonMineZ's critical hit sounds. */
    public static void heavyHit(ServerPlayer attacker, LivingEntity victim) {
        if (!enabled()) return;
        SoundEvent sound;
        try {
            sound = (attacker.tickCount & 1) == 0
                    ? com.dragonminez.common.init.MainSounds.CRITICO1.get()
                    : com.dragonminez.common.init.MainSounds.CRITICO2.get();
        } catch (RuntimeException | LinkageError unavailable) {
            return;
        }
        at(attacker, victim, sound, 1.15f, 0.9f);
    }

    /** A strike that reached the target but was guarded or refused: DragonMineZ's ordinary punch. */
    public static void contact(ServerPlayer attacker, LivingEntity victim) {
        if (!enabled()) return;
        SoundEvent sound;
        try {
            sound = switch (Math.floorMod(attacker.tickCount, 3)) {
                case 1 -> com.dragonminez.common.init.MainSounds.GOLPE2.get();
                case 2 -> com.dragonminez.common.init.MainSounds.GOLPE3.get();
                default -> com.dragonminez.common.init.MainSounds.GOLPE1.get();
            };
        } catch (RuntimeException | LinkageError unavailable) {
            return;
        }
        at(attacker, victim, sound, 0.95f, 1.05f);
    }

    /** A heavy or charged swing that found nothing in reach. Vanilla's strong swing, quietly. */
    public static void whiff(ServerPlayer attacker) {
        if (!enabled()) return;
        attacker.level().playSound(null, attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.6,
                attacker.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, volume(0.6f), 0.85f);
    }

    /**
     * A charged punch or kick began gathering. DragonMineZ's own saga combos use the ki charge loop
     * as a one-shot charge-up ({@code ComboManager}, pitch 1.5); the same cue is reused here.
     */
    public static void chargeStart(ServerPlayer attacker, boolean kick) {
        if (!enabled()) return;
        SoundEvent sound;
        try {
            sound = com.dragonminez.common.init.MainSounds.KI_CHARGE_LOOP.get();
        } catch (RuntimeException | LinkageError unavailable) {
            return;
        }
        attacker.level().playSound(null, attacker.getX(), attacker.getY() + 1.0, attacker.getZ(), sound,
                SoundSource.PLAYERS, volume(0.7f), kick ? 1.35f : 1.5f);
    }

    /** The charged strike was let go: a spark crack on release, before any contact sound. */
    public static void chargeRelease(ServerPlayer attacker, float charge) {
        if (!enabled()) return;
        SoundEvent sound;
        try {
            sound = com.dragonminez.common.init.MainSounds.KI_SPARKS.get();
        } catch (RuntimeException | LinkageError unavailable) {
            return;
        }
        attacker.level().playSound(null, attacker.getX(), attacker.getY() + 1.0, attacker.getZ(), sound,
                SoundSource.PLAYERS, volume(0.5f + 0.5f * Math.clamp(charge, 0f, 1f)), 1.1f);
    }

    /** The fully charged punch's explosive landing, matching the ULTIMATE impact effect it draws. */
    public static void chargedExplosion(ServerPlayer attacker, LivingEntity victim) {
        if (!enabled()) return;
        at(attacker, victim, SoundEvents.GENERIC_EXPLODE.value(), 0.9f, 1.1f);
    }

    private static void at(ServerPlayer attacker, LivingEntity victim, SoundEvent sound, float volume, float pitch) {
        if (sound == null) return;
        attacker.level().playSound(null, victim.getX(), victim.getY() + victim.getBbHeight() * 0.4, victim.getZ(),
                sound, SoundSource.PLAYERS, volume(volume), pitch);
    }
}

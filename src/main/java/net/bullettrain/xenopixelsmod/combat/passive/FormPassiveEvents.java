package net.bullettrain.xenopixelsmod.combat.passive;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.AfterimageGhost;
import net.bullettrain.xenopixelsmod.combat.fx.HakaiFx;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.features.progression.CombatSkills;
import net.bullettrain.xenopixelsmod.features.transformation.passive.FormPassive;
import net.bullettrain.xenopixelsmod.features.transformation.passive.FormPassiveRules;
import net.bullettrain.xenopixelsmod.features.transformation.passive.FormPassives;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The god-form passives in play (2026-09-29). Which form has which comes from
 * {@link FormPassives}; each one has its own {@code /xenoset} switch ({@link FormPassiveRules}).
 *
 * <ul>
 *   <li><b>Hakaishin mantle</b>: every incoming attack (anything with an attacker or projectile
 *       behind it) is erased, however strong. Falling, fire and the like still hurt, and /kill and
 *       the void still end a life.</li>
 *   <li><b>Ultra Instinct dodge</b>: a chance by form mastery to slip a hit entirely, leaving an
 *       afterimage - none against an attacker who is also in Ultra Instinct.</li>
 *   <li><b>Ultra Ego</b>: an attacker weaker by the battle-power ratio does nothing to you.</li>
 *   <li><b>Aura</b>: weaker projectiles and ki reaching an Ultra Ego user are deleted, and every
 *       one reaching a Hakaishin is erased. Players only (a per-tick scan).</li>
 * </ul>
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class FormPassiveEvents {
    private static final double AURA_REACH = 2.5;

    private FormPassiveEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || !XenoServerConfig.formPassives) return;
        DamageSource source = event.getSource();
        Entity attackerRaw = source.getEntity();
        if (attackerRaw == victim) return;
        boolean isAttack = attackerRaw != null || source.getDirectEntity() != null;
        if (!isAttack) return;
        FormPassive passive = FormPassives.of(victim);
        if (!passive.any()) return;

        if (passive.hakaiMantle() && FormPassiveRules.enabled(FormPassiveRules.Kind.MANTLE)
                && FormPassiveRules.mantleErases(
                source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD),
                source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))) {
            event.setCanceled(true);
            puff(victim, source);
            return;
        }
        if (!(attackerRaw instanceof LivingEntity attacker)) return;

        if (passive.hasDodge() && FormPassiveRules.enabled(FormPassiveRules.Kind.DODGE)
                && FormPassives.mayDodge(passive, FormPassives.of(attacker))) {
            float chance = FormPassives.dodgeChanceOf(victim, passive) * XenoServerConfig.uiDodgeScale;
            if (victim.getRandom().nextFloat() < chance) {
                event.setCanceled(true);
                dodge(victim, attacker);
                return;
            }
        }
        if (passive.weakerImmunity() && FormPassiveRules.enabled(FormPassiveRules.Kind.IMMUNITY)
                && FormPassives.attackerWeaker(attacker, victim, passive.weakerRatio())) {
            event.setCanceled(true);
            if (attacker instanceof ServerPlayer p && victim.tickCount % 10 == 0) {
                p.displayClientMessage(Component.literal("§5Your attack has no effect"), true);
            }
        }
    }

    /**
     * A Hakaishin in the mantle is not pushed by punches or kicks (2026-10-02 owner: "hakaishin
     * form cant be knocked back from both punches and kicks"). {@code /xenoset
     * hakaiMantleNoKnockback false} lets the push through again while the hit stays erased.
     */
    public static boolean mantleHoldsGround(Entity victim) {
        if (victim == null || victim.level().isClientSide() || !XenoServerConfig.formPassives
                || !XenoServerConfig.hakaiMantleNoKnockback
                || !FormPassiveRules.enabled(FormPassiveRules.Kind.MANTLE)) {
            return false;
        }
        return FormPassives.of(victim).hakaiMantle();
    }

    /** Vanilla knockback too, for anything that pushes the wearer without a cancelled hit. */
    @SubscribeEvent
    public static void onKnockBack(net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent event) {
        if (mantleHoldsGround(event.getEntity())) event.setCanceled(true);
    }

    private static void puff(LivingEntity victim, DamageSource source) {
        if (!(victim.level() instanceof ServerLevel level)) return;
        Vec3 at = source.getSourcePosition() != null
                ? source.getSourcePosition() : victim.position().add(0.0, victim.getBbHeight() * 0.6, 0.0);
        HakaiFx.blockPuff(level, at, false);
    }

    /** A sidestep away from the attacker's line, and an afterimage where the hit would have landed. */
    private static void dodge(LivingEntity victim, LivingEntity attacker) {
        Vec3 origin = victim.position();
        Vec3 away = origin.subtract(attacker.position()).multiply(1.0, 0.0, 1.0);
        if (away.lengthSqr() < 1.0e-6) away = new Vec3(1.0, 0.0, 0.0);
        Vec3 side = new Vec3(-away.z, 0.0, away.x).normalize()
                .scale(victim.getRandom().nextBoolean() ? 0.6 : -0.6);
        victim.setDeltaMovement(victim.getDeltaMovement().add(side.x, 0.05, side.z));
        victim.hurtMarked = true;
        if (victim instanceof ServerPlayer player) {
            AfterimageGhost.leave(player, origin);
        }
    }

    /** The aura: projectiles heading at the wearer are deleted (Ultra Ego: weaker ones; Hakaishin: all). */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || (player.tickCount & 1) != 0) return;
        if (!XenoServerConfig.formPassives) return;
        FormPassive passive = FormPassives.of(player);
        gateHakaishin(player, passive);
        boolean mantle = passive.hakaiMantle() && FormPassiveRules.enabled(FormPassiveRules.Kind.MANTLE);
        boolean aura = passive.deleteWeakProjectiles()
                && FormPassiveRules.enabled(FormPassiveRules.Kind.PROJECTILE_AURA);
        if (!mantle && !aura) return;
        ServerLevel level = player.serverLevel();
        Vec3 centre = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
        for (Projectile shot : level.getEntitiesOfClass(Projectile.class,
                player.getBoundingBox().inflate(AURA_REACH), p -> p.isAlive() && !p.isRemoved())) {
            Entity owner = shot.getOwner();
            if (owner == player) continue;
            Vec3 motion = shot.getDeltaMovement();
            if (motion.lengthSqr() > 1.0e-6 && motion.dot(centre.subtract(shot.position())) <= 0.0) continue;
            boolean erase = mantle || (owner instanceof LivingEntity shooter
                    && FormPassives.attackerWeaker(shooter, player, passive.weakerRatio()));
            if (!erase) continue;
            HakaiFx.blockPuff(level, shot.position(), false);
            shot.discard();
        }
    }

    /**
     * Hakaishin is for those who know Hakai (2026-09-29 owner: "every race, after Hakai"). DMZ has no
     * cancellable transform hook, so the form is left again on the next check with DMZ's own
     * {@code clearActiveForm}.
     */
    private static void gateHakaishin(ServerPlayer player, FormPassive passive) {
        if (!XenoServerConfig.hakaishinNeedsHakai || !passive.hakaiMantle() || (player.tickCount % 20) != 0) {
            return;
        }
        if (CombatSkills.hakaiUnlocked(player)) return;
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        if (data == null || data.getCharacter() == null) return;
        data.getCharacter().clearActiveForm(player);
        net.bullettrain.xenopixelsmod.api.dmz.DmzSync.syncStats(player);
        player.displayClientMessage(Component.literal(
                "§5Hakaishin needs Hakai - unlock it in the skill tree first (/xenoskills)"), false);
    }
}

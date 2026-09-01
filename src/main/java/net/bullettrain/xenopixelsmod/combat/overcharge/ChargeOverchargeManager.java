package net.bullettrain.xenopixelsmod.combat.overcharge;

import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.init.MainGameRules;
import com.dragonminez.common.init.block.custom.DragonBallBlock;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiDiskEntity;
import com.dragonminez.common.init.entities.ki.KiExplosionEntity;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.character.Resources;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.TechniqueChargeSyncS2C;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.TechniqueData;
import com.dragonminez.common.stats.techniques.Techniques;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFx;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFxKind;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Continues a held ki charge past DragonMineZ's 175% cap and scales the live
 * projectile through the same public setters beam surge already uses.
 *
 * <p>DMZ still owns 0–175 and the fire itself. This only runs while the player is
 * holding a non-instant {@code KiAttackData} and is eligible.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class ChargeOverchargeManager {

    private static final float INDESTRUCTIBLE = 1000.0f;
    private static final double SEARCH_RADIUS = 30.0;
    private static final int MAX_LIFE_CAP = 400;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private ChargeOverchargeManager() {
    }

    private static final class Session {
        private UUID entityId;
        private String techniqueId = "";
        private float costAccum;
        private int lastCameraTier;
        private int craterAge;
        private int liveRocks;
        private int lastRockTick;
        private float lastPercent = -1.0f;
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        OverchargeVoices.reload();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;
        if (!XenoServerConfig.chargeOverchargeEnabled) {
            if (!SESSIONS.isEmpty()) forget(player.getUUID());
            return;
        }

        StatsData data = stats(player);
        if (data == null) {
            forget(player.getUUID());
            return;
        }
        Techniques techniques = data.getTechniques();
        if (techniques == null || !techniques.isTechniqueCharging() || !techniques.isChargeHolding()) {
            forget(player.getUUID());
            return;
        }

        String techId = techniques.getChargingTechniqueId();
        if (techId == null || techId.isEmpty()) {
            forget(player.getUUID());
            return;
        }
        TechniqueData techniqueData = techniques.getUnlockedTechniques().get(techId);
        if (!(techniqueData instanceof KiAttackData kiAttack) || kiAttack.isInstantCast()) {
            forget(player.getUUID());
            return;
        }
        if (data.getLevel() < XenoServerConfig.chargeOverchargeMinLevel) return;

        Session session = SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session());
        if (!techId.equals(session.techniqueId)) {
            session.techniqueId = techId;
            session.entityId = null;
            session.costAccum = 0.0f;
            session.lastCameraTier = 0;
            session.craterAge = 0;
            session.liveRocks = 0;
            session.lastPercent = -1.0f;
        }

        float percent = techniques.getTechniqueChargePercent();
        float cap = XenoServerConfig.chargeOverchargeMaxPercent;

        // TickHandler may have already stepped past 175 this tick. If it did not
        // (runClient often only replaced one of three 175 literals, then stuck
        // at 176), walk the charge here and sync so the HUD is not slammed back.
        if (percent >= ChargeOverchargeMath.DMZ_CAP - 0.01f && percent < cap - 0.01f) {
            boolean handlerMoved = session.lastPercent >= 0.0f
                    && percent > session.lastPercent + 0.05f;
            if (!handlerMoved) {
                float next = continueCharge(player, data, techniques, kiAttack, session, percent, cap);
                if (next > percent) {
                    percent = next;
                    NetworkHandler.sendToTrackingEntityAndSelf(
                            new TechniqueChargeSyncS2C(player.getId(), percent, true), player);
                }
            }
        }
        session.lastPercent = percent;

        AbstractKiProjectile charging = findCharging(player, session);
        if (charging != null) {
            applyChargeVisual(player, data, kiAttack, charging, percent);
        }

        if (XenoServerConfig.chargeOverchargeCameraEnabled) {
            int tier = ChargeOverchargeMath.cameraTier(percent);
            if (tier > session.lastCameraTier && player.level() instanceof ServerLevel level) {
                CombatFx.cue(level, player.position(), CombatFxKind.CHARGE_PEAK,
                        0.35f + 0.15f * tier);
            }
            session.lastCameraTier = tier;
        }

        if (XenoServerConfig.chargeOverchargeGriefEnabled
                && percent >= XenoServerConfig.chargeOverchargeCraterMinPercent
                && player.level() instanceof ServerLevel level) {
            tickCrater(level, player, session, percent);
        }
    }

    @SubscribeEvent
    public static void onKiFire(DMZEvent.KiAttackFireEvent event) {
        if (!XenoServerConfig.chargeOverchargeEnabled) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        StatsData data = event.getStatsData();
        KiAttackData kiAttack = event.getKiAttack();
        if (data == null || kiAttack == null) return;

        float percent = data.getTechniques().getTechniqueChargePercent();
        AbstractKiProjectile projectile = findFired(player, kiAttack.getId());
        if (projectile != null) {
            applyOnFire(player, data, kiAttack, projectile, percent);
            if (projectile instanceof KiDiskEntity disk
                    && percent > ChargeOverchargeMath.DMZ_CAP
                    && player.level() instanceof ServerLevel level) {
                DiskSliceTracker.track(level, disk);
            }
        }

        if (XenoServerConfig.chargeOverchargeVoicesEnabled
                && percent > ChargeOverchargeMath.DMZ_CAP
                && player.level() instanceof ServerLevel level) {
            OverchargeVoices.play(level, player, kiAttack.getId());
        }

        if (XenoServerConfig.chargeOverchargeCameraEnabled
                && percent >= ChargeOverchargeMath.DMZ_CAP
                && player.level() instanceof ServerLevel level) {
            CombatFx.cue(level, player.position(), CombatFxKind.CHARGE_PEAK,
                    0.4f + 0.12f * ChargeOverchargeMath.cameraTier(percent));
        }

        forget(player.getUUID());
    }

    private static float continueCharge(ServerPlayer player, StatsData data, Techniques techniques,
                                        KiAttackData kiAttack, Session session,
                                        float percent, float cap) {
        float rate = ChargeOverchargeMath.incrementRate(kiAttack.getBaseChargeTicks());
        float newP = Math.min(cap, percent + rate);
        if (newP <= percent) return percent;

        if (player.isCreative()) {
            techniques.setTechniqueChargePercent(newP);
            return newP;
        }

        Resources res = data.getResources();
        if (res == null) return percent;

        float delta = ChargeOverchargeMath.chargeCostDelta(percent, newP,
                (float) kiAttack.getCalculatedCost(data));
        session.costAccum += Math.max(0.0f, delta);
        int whole = (int) session.costAccum;
        int effective = (int) Math.round(whole * data.getKiAttackCostModifier());
        if (effective > 0 && res.getCurrentEnergy() < effective) return percent;
        if (effective > 0) res.removeEnergy(effective);
        session.costAccum -= whole;
        techniques.setTechniqueChargePercent(newP);
        return newP;
    }

    private static void applyChargeVisual(ServerPlayer player, StatsData data, KiAttackData kiAttack,
                                          AbstractKiProjectile charging, float percent) {
        if (kiAttack.getKiType() == null || !ChargeOverchargeMath.scalesSize(kiAttack.getKiType().name())) return;
        float size = kiAttack.getSize()
                * ChargeOverchargeMath.chargeVisualScale(percent,
                XenoServerConfig.chargeOverchargeSizePerPercent)
                * formSize(data)
                * XenoServerConfig.kiOverchargeSizeScale(readPowerRelease(data));
        KiProjectileApply.size(charging, size);
    }

    private static void applyOnFire(ServerPlayer player, StatsData data, KiAttackData kiAttack,
                                    AbstractKiProjectile projectile, float percent) {
        if (kiAttack.getKiType() == null) return;
        String type = kiAttack.getKiType().name();
        float form = formSize(data);
        float power = XenoServerConfig.kiOverchargeSizeScale(readPowerRelease(data));

        if (ChargeOverchargeMath.scalesSize(type)) {
            float size = kiAttack.getSize()
                    * ChargeOverchargeMath.chargeVisualScale(percent,
                    XenoServerConfig.chargeOverchargeSizePerPercent)
                    * form
                    * power;
            KiProjectileApply.size(projectile, size);

            float speedScale = ChargeOverchargeMath.excessScale(percent,
                    XenoServerConfig.chargeOverchargeSpeedPerPercent);
            KiProjectileApply.speed(projectile, projectile.getKiSpeed() * speedScale);
        }

        float dmg = ChargeOverchargeMath.damageFactor(percent,
                XenoServerConfig.chargeOverchargeMaxDamageScale,
                XenoServerConfig.kiFullGameplayScaling);
        if (dmg > 1.001f) {
            KiProjectileApply.damage(projectile, projectile.getKiDamage() * dmg);
        }

        if (ChargeOverchargeMath.scalesLife(type)) {
            float lifeScale = ChargeOverchargeMath.excessScale(percent,
                    XenoServerConfig.chargeOverchargeSizePerPercent);
            int life = Math.min(MAX_LIFE_CAP,
                    Math.max(projectile.getMaxLife(), (int) (projectile.getMaxLife() * lifeScale)));
            if (life != projectile.getMaxLife()) projectile.setMaxLife(life);
        }

        if (projectile instanceof KiExplosionEntity explosion) {
            float boom = ChargeOverchargeMath.excessScale(percent,
                    XenoServerConfig.chargeOverchargeSizePerPercent);
            float radius = Math.min(XenoServerConfig.kiDestructionRadiusLimit(),
                    explosion.getMaxRadius() * boom);
            if (Math.abs(radius - explosion.getMaxRadius()) > KiProjectileApply.SIZE_EPSILON) {
                explosion.setMaxRadius(radius);
            }
        }
    }

    private static float formSize(StatsData data) {
        double formMult = 1.0;
        try {
            formMult = data.getFormMultiplier("STR");
        } catch (Throwable ignored) {
            formMult = 1.0;
        }
        float scale = ChargeOverchargeMath.formSizeScale(formMult,
                XenoServerConfig.chargeFormSizeFactor,
                XenoServerConfig.chargeFormSizeLogCap);
        String key = formKey(data);
        if (key != null) {
            Float extra = XenoServerConfig.chargeFormSizeByForm.get(key);
            if (extra == null) {
                int dot = key.lastIndexOf('.');
                if (dot >= 0 && dot + 1 < key.length()) {
                    extra = XenoServerConfig.chargeFormSizeByForm.get(key.substring(dot + 1));
                }
            }
            if (extra != null && extra > 0.0f && Float.isFinite(extra)) {
                scale *= extra;
            }
        }
        return scale;
    }

    private static String formKey(StatsData data) {
        try {
            Character ch = data.getCharacter();
            if (ch == null) return null;
            String form = ch.getActiveForm();
            if (form == null || form.isEmpty() || "base".equalsIgnoreCase(form)) return null;
            String group = ch.getActiveFormGroup();
            if (group != null && !group.isEmpty()) return group + "." + form;
            return form;
        } catch (Throwable t) {
            return null;
        }
    }

    private static int readPowerRelease(StatsData data) {
        try {
            Resources res = data.getResources();
            return res != null ? res.getPowerRelease() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void tickCrater(ServerLevel level, ServerPlayer player, Session session,
                                   float percent) {
        session.craterAge++;
        int interval = Math.max(2, XenoServerConfig.chargeOverchargeCraterIntervalTicks);
        if (session.craterAge % interval != 0) return;

        float t = (percent - XenoServerConfig.chargeOverchargeCraterMinPercent)
                / Math.max(1.0f, XenoServerConfig.chargeOverchargeMaxPercent
                - XenoServerConfig.chargeOverchargeCraterMinPercent);
        int maxR = Math.max(1, XenoServerConfig.chargeOverchargeCraterMaxRadius);
        int radius = Math.max(1, Math.min(maxR, 1 + (int) (t * maxR)));
        radius = Math.min(radius, (int) XenoServerConfig.kiDestructionRadiusLimit());

        BlockPos origin = player.blockPosition().below();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int inner = Math.max(0, radius - 1);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > radius * radius) continue;
                // First ring fill (r==1) is a disk so a hole appears; later ticks are a ring.
                if (radius > 1 && d2 < inner * inner) continue;
                cursor.set(origin.getX() + dx, origin.getY(), origin.getZ() + dz);
                breakOrLaunch(level, cursor, player, session);
                cursor.setY(origin.getY() - 1);
                breakOrLaunch(level, cursor, player, session);
            }
        }
        if (session.craterAge - session.lastRockTick > 40) {
            session.liveRocks = Math.max(0, session.liveRocks - 1);
            session.lastRockTick = session.craterAge;
        }
    }

    private static void breakOrLaunch(ServerLevel level, BlockPos pos, ServerPlayer player,
                                      Session session) {
        if (!level.isLoaded(pos)) return;
        if (!MainGameRules.canKiGrief(level, pos, player)) return;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;
        if (state.getDestroySpeed(level, pos) < 0.0f) return;
        if (state.getBlock() instanceof DragonBallBlock) return;
        if (state.getExplosionResistance(level, pos, null) >= INDESTRUCTIBLE) return;
        if (!KiDestructionBudget.tryConsume(level, 1)) return;

        boolean launch = session.liveRocks < XenoServerConfig.chargeOverchargeMaxRocks
                && state.getFluidState().isEmpty();
        if (launch && state.getDestroySpeed(level, pos) >= 0.0f) {
            FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, state);
            if (falling != null) {
                falling.setDeltaMovement(
                        (level.random.nextDouble() - 0.5) * 0.35,
                        0.35 + level.random.nextDouble() * 0.25,
                        (level.random.nextDouble() - 0.5) * 0.35);
                session.liveRocks++;
                session.lastRockTick = session.craterAge;
                return;
            }
        }
        level.destroyBlock(pos, false);
    }

    private static AbstractKiProjectile findCharging(ServerPlayer player, Session session) {
        if (!(player.level() instanceof ServerLevel level)) return null;
        if (session.entityId != null) {
            Entity cached = level.getEntity(session.entityId);
            if (cached instanceof AbstractKiProjectile ki
                    && ki.isAlive()
                    && !ki.isFiring()
                    && ki.isOwner(player)) {
                return ki;
            }
            session.entityId = null;
        }
        AABB box = player.getBoundingBox().inflate(SEARCH_RADIUS);
        for (AbstractKiProjectile ki : level.getEntitiesOfClass(AbstractKiProjectile.class, box,
                candidate -> candidate.isAlive() && !candidate.isFiring() && candidate.isOwner(player))) {
            session.entityId = ki.getUUID();
            return ki;
        }
        return null;
    }

    private static AbstractKiProjectile findFired(ServerPlayer player, String techniqueId) {
        if (!(player.level() instanceof ServerLevel level)) return null;
        AABB box = player.getBoundingBox().inflate(SEARCH_RADIUS);
        AbstractKiProjectile fallback = null;
        for (AbstractKiProjectile ki : level.getEntitiesOfClass(AbstractKiProjectile.class, box,
                candidate -> candidate.isAlive() && candidate.isOwner(player))) {
            if (techniqueId != null && techniqueId.equals(ki.getTechniqueId())) return ki;
            if (fallback == null) fallback = ki;
        }
        return fallback;
    }

    private static StatsData stats(ServerPlayer player) {
        try {
            return StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void forget(UUID playerId) {
        if (playerId != null) SESSIONS.remove(playerId);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            forget(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SESSIONS.clear();
        KiDestructionBudget.clear();
    }
}

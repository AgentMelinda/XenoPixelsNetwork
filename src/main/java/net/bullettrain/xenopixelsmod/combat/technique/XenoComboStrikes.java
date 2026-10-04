package net.bullettrain.xenopixelsmod.combat.technique;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.techniques.PredefinedTechniques;
import com.dragonminez.common.stats.techniques.StrikeAttackData;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.command.XenoPermissions;
import net.bullettrain.xenopixelsmod.combat.combo.ComboRoute;
import net.bullettrain.xenopixelsmod.combat.combo.ComboRouteCatalog;
import net.bullettrain.xenopixelsmod.combat.combo.ComboRouteMachine;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.features.progression.CombatSkills;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;

import java.util.Locale;

/**
 * Combo-route strikes listed in DragonMineZ's strike registry.
 *
 * <p>Catalog registration is global. A player only receives {@code unlockTechnique} after
 * {@code /xenoskill grant} has set the matching exclusive skill. Login re-applies already
 * granted skills; it never grants new ones.
 */
public final class XenoComboStrikes {

    public static final String RUSH_COMBO = ComboRouteCatalog.RUSH_COMBO_STRIKE;
    public static final String LIFT_COMBO = ComboRouteCatalog.LIFT_COMBO_STRIKE;

    private XenoComboStrikes() {
    }

    public static void register() {
        register(RUSH_COMBO, "Xeno Rush Combo", "combat.xeno_dmz_punch_left_v4", 1.15f, 16);
        register(LIFT_COMBO, "Xeno Lift Combo", "combat.xeno_heavy_finish_v4", 1.25f, 16);
        try {
            var skills = com.dragonminez.common.config.ConfigManager.getSkillsConfig();
            if (skills != null) {
                XenoStrikeSkills.installInto(skills.getStrikeSkills());
            }
        } catch (Throwable ignored) {
        }
        XenoPixelsMod.LOGGER.info("Registered DMZ strike attacks: {} {}", RUSH_COMBO, LIFT_COMBO);
    }

    public static boolean isComboId(String id) {
        return ComboRouteCatalog.byStrikeId(id) != null;
    }

    /**
     * Pure pre-flight for {@link #cast}. Minecraft-free so the grant/permission/busy gates
     * can be unit-tested without a server.
     *
     * @return a stable reason token, or {@code null} if the cast may proceed
     */
    public static String refuseReason(boolean routesEnabled, boolean routeEnabled, boolean granted,
                                      boolean usePerm, boolean busy, boolean hasTarget, boolean inRange,
                                      boolean pvpPveOk) {
        if (!routesEnabled) return "disabled";
        if (!routeEnabled) return "route_off";
        if (!granted) return "ungranted";
        if (!usePerm) return "permission";
        if (busy) return "busy";
        if (!hasTarget) return "no_target";
        if (!inRange) return "too_far";
        if (!pvpPveOk) return "pvp_pve";
        return null;
    }

    /** Whether a skill level is enough to put the matching strike on the player. */
    public static boolean shouldUnlock(int skillLevel) {
        return skillLevel >= 1;
    }

    public static boolean unlocked(ServerPlayer player, String strikeId) {
        ComboRoute route = ComboRouteCatalog.byStrikeId(strikeId);
        if (player == null || route == null) return false;
        return shouldUnlock(CombatSkills.level(player, route.skillId()));
    }

    /**
     * Re-applies granted combo strikes onto DMZ's technique list. Does not grant unearned ones.
     */
    public static void unlock(ServerPlayer player) {
        if (player == null) return;
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        if (data == null || data.getTechniques() == null) return;
        var techniques = data.getTechniques();
        boolean grantedAny = false;
        for (ComboRoute route : ComboRouteCatalog.all()) {
            if (!shouldUnlock(CombatSkills.level(player, route.skillId()))) continue;
            StrikeAttackData template = PredefinedTechniques.STRIKE_REGISTRY.get(route.strikeId());
            if (template == null) continue;
            StrikeAttackData clone = new StrikeAttackData();
            clone.load(template.save());
            techniques.unlockTechnique(clone);
            grantedAny = true;
        }
        if (!grantedAny) return;
        try {
            com.dragonminez.common.network.NetworkHandler.sendToTrackingEntityAndSelf(
                    new com.dragonminez.common.network.S2C.ProgressionSyncS2C(player), player);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Intercepted slot/selected-technique cast. Returns true when this id is ours so DMZ must
     * not also fire, including refused casts.
     */
    public static boolean cast(ServerPlayer player, String id) {
        return cast(player, id, null);
    }

    public static boolean cast(ServerPlayer player, String id, LivingEntity preferred) {
        ComboRoute route = ComboRouteCatalog.byStrikeId(id);
        if (route == null) return false;
        if (player == null) return true;

        boolean routeEnabled = CombatSkills.RUSHCOMBO.equals(route.skillId())
                ? XenoServerConfig.rushcomboEnabled : XenoServerConfig.liftcomboEnabled;
        LivingEntity target = preferred != null && preferred.isAlive() ? preferred : findLookTarget(player);
        boolean inRange = target != null
                && ComboRouteMachine.withinRange(player.distanceTo(target), XenoServerConfig.comboRouteRange);
        boolean pvpPve = target == null || pvpPveAllowed(target);
        String reason = refuseReason(
                XenoServerConfig.comboRoutesEnabled,
                routeEnabled,
                shouldUnlock(CombatSkills.level(player, route.skillId())),
                XenoPermissions.hasPermission(player, useNode(route.skillId())),
                ComboRouteMachine.isBusy(player.getUUID()) || ComboRouteMachine.onCooldown(player),
                target != null,
                inRange,
                pvpPve);
        if (reason != null) {
            refuseMessage(player, reason);
            return true;
        }
        if (target != null && net.bullettrain.xenopixelsmod.event.DmzMasterProtection.isDmzMaster(target)) {
            refuseMessage(player, "master");
            return true;
        }
        if (!spendKi(player, XenoServerConfig.comboRouteKiCost)) {
            refuseMessage(player, "no_ki");
            return true;
        }
        ComboRouteMachine.start(player, target, route);
        return true;
    }

    public static PermissionNode<Boolean> useNode(String skillId) {
        if (CombatSkills.LIFTCOMBO.equals(skillId)) return XenoPermissions.SKILL_LIFTCOMBO_USE;
        return XenoPermissions.SKILL_RUSHCOMBO_USE;
    }

    private static boolean pvpPveAllowed(LivingEntity target) {
        if (target instanceof ServerPlayer) return XenoServerConfig.comboRoutePvpEnabled;
        return XenoServerConfig.comboRoutePveEnabled;
    }

    private static LivingEntity findLookTarget(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double range = XenoServerConfig.comboRouteRange;
        double search = range > 0.0 ? range : 512.0;
        AABB box = player.getBoundingBox().expandTowards(look.scale(search)).inflate(1.5);
        LivingEntity best = null;
        double bestDot = 0.55;
        for (LivingEntity candidate : player.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive()
                        && !net.bullettrain.xenopixelsmod.event.DmzMasterProtection.isDmzMaster(e))) {
            Vec3 to = candidate.getEyePosition().subtract(eye);
            double len = to.length();
            if (len < 0.2) continue;
            if (!ComboRouteMachine.withinRange(len, range)) continue;
            double dot = look.dot(to.normalize());
            if (dot > bestDot) {
                bestDot = dot;
                best = candidate;
            }
        }
        return best;
    }

    public static LivingEntity resolvePreferred(ServerPlayer player, int preferredTargetId) {
        if (player == null || preferredTargetId <= 0) return null;
        Entity raw = player.level().getEntity(preferredTargetId);
        return raw instanceof LivingEntity living && living.isAlive() && living != player ? living : null;
    }

    private static boolean spendKi(ServerPlayer player, double cost) {
        if (cost <= 0) return true;
        var stats = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        if (stats == null || stats.getResources() == null) return false;
        if (stats.getResources().getCurrentEnergy() < cost) return false;
        stats.getResources().removeEnergy((float) Math.ceil(cost));
        return true;
    }

    private static void refuseMessage(ServerPlayer player, String reason) {
        String text = switch (reason) {
            case "disabled" -> "§7Combo routes are disabled here";
            case "route_off" -> "§7That combo route is disabled here";
            case "ungranted" -> "§7That combo is exclusive — an operator must /xenoskill grant it";
            case "permission" -> "§7You do not have permission to use that combo";
            case "busy" -> "§7Already in a combo route";
            case "no_target" -> "§7No target";
            case "too_far" -> "§7Too far for that combo";
            case "pvp_pve" -> "§7That combo is not allowed against this target";
            case "master" -> "§7Cannot use that combo on a master";
            case "no_ki" -> "§7Not enough KI";
            default -> "§7Cannot use that combo";
        };
        player.displayClientMessage(Component.literal(text), true);
    }

    public static void revoke(ServerPlayer player, String skillId) {
        if (player == null || skillId == null) return;
        ComboRoute route = ComboRouteCatalog.bySkillId(skillId);
        if (route == null) return;
        StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
            if (data.getTechniques() != null) {
                data.getTechniques().removeTechnique(route.strikeId());
            }
        });
        try {
            com.dragonminez.common.network.NetworkHandler.sendToTrackingEntityAndSelf(
                    new com.dragonminez.common.network.S2C.ProgressionSyncS2C(player), player);
        } catch (Throwable ignored) {
        }
    }

    private static void register(String id, String name, String animation, float damage, int duration) {
        String key = id.toLowerCase(Locale.ROOT);
        if (PredefinedTechniques.STRIKE_REGISTRY.containsKey(key)) {
            return;
        }
        StrikeAttackData data = new StrikeAttackData();
        data.setId(key);
        data.setName(name);
        data.setAuthor("XenoPixels");
        data.setDamageMultiplier(damage);
        data.setAnimationId(animation);
        data.setDurationTicks(duration);
        data.setBaseCost(10.0);
        data.applyConfigDefaults();
        data.setCooldown(Math.max(4, duration));
        PredefinedTechniques.STRIKE_REGISTRY.put(key, data);
    }
}

package net.bullettrain.xenopixelsmod.combat.technique;

import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.stats.techniques.KiAttackData;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.features.progression.CombatSkills;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Configured fire window for every DMZ ki type. 0 means stock
 * {@code TechniqueDispatcher.resolvePlayerMaxLifeTicks}.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class KiDuration {

    public static final String[] TYPE_KEYS = {
            "small_ball", "medium_ball", "giant_ball", "wave", "laser", "beam",
            "disk", "explosion", "shield", "barrage", "area"
    };

    private KiDuration() {
    }

    public static boolean isKnownType(String type) {
        if (type == null || type.isBlank()) return false;
        String key = type.trim().toLowerCase();
        for (String known : TYPE_KEYS) {
            if (known.equals(key)) return true;
        }
        return false;
    }

    public static String typeKey(KiAttackData.KiType type) {
        return type == null ? "" : type.name().toLowerCase();
    }

    public static int configuredWindow(KiAttackData.KiType type) {
        if (type == null) return 0;
        Integer per = XenoServerConfig.kiDurationByType.get(typeKey(type));
        if (per != null && per > 0) return per;
        if (type == KiAttackData.KiType.BARRAGE && XenoServerConfig.barrageDurationTicks > 0) {
            return XenoServerConfig.barrageDurationTicks;
        }
        return Math.max(0, XenoServerConfig.kiDurationTicks);
    }

    public static int windowOrStock(KiAttackData.KiType type, int stock) {
        int configured = configuredWindow(type);
        if (configured <= 0) return Math.max(1, stock);
        if (type == KiAttackData.KiType.BARRAGE) {
            return BarrageMastery.extendLife(configured, 0);
        }
        return configured;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onKiFire(DMZEvent.KiAttackFireEvent event) {
        if (event.getKiAttack() == null) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        KiAttackData.KiType type = event.getKiAttack().getKiType();
        int configured = configuredWindow(type);
        if (configured <= 0) return;
        if (!(player.level() instanceof ServerLevel world)) return;
        int extra = type == KiAttackData.KiType.BARRAGE
                ? BarrageMastery.extraTicks(CombatSkills.level(player, CombatSkills.BARRAGE))
                : 0;
        int window = Math.max(1, configured + extra);
        String name = type.name();
        double reach = Math.max(16.0, KiGuidanceMath.controlRange(0));
        AABB box = player.getBoundingBox().inflate(reach);
        for (AbstractKiProjectile ki : world.getEntitiesOfClass(AbstractKiProjectile.class, box,
                candidate -> candidate.isAlive() && candidate.isOwner(player) && candidate.isFiring())) {
            if (!name.equals(ki.getKiType().name())) continue;
            int fireTick = ki.getFireTick();
            if (fireTick < 0) fireTick = ki.tickCount;
            ki.setMaxLife(fireTick + window);
        }
    }
}

package net.bullettrain.xenopixelsmod.combat.technique;

import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import com.dragonminez.common.stats.techniques.KiAttackData;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.features.progression.CombatSkills;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Volley Mastery: configured barrage duration and cooldown, scaled by skill level.
 *
 * <p>Stock and custom {@code BARRAGE} share DMZ's {@code 50 × charge} window unless
 * {@link XenoServerConfig#barrageDurationTicks} is set. Cooldown is the technique's
 * own value unless {@link XenoServerConfig#barrageCooldownTicks} is set. Each mastery
 * level then adds extra fire ticks and subtracts cooldown ticks.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class BarrageMastery {

    private BarrageMastery() {
    }

    public static int extraTicks(int level) {
        int lv = Math.max(0, Math.min(3, level));
        return switch (lv) {
            case 1 -> Math.max(0, XenoServerConfig.barrageExtraTicks1);
            case 2 -> Math.max(0, XenoServerConfig.barrageExtraTicks2);
            case 3 -> Math.max(0, XenoServerConfig.barrageExtraTicks3);
            default -> 0;
        };
    }

    public static int cooldownReduce(int level) {
        int lv = Math.max(0, Math.min(3, level));
        return switch (lv) {
            case 1 -> Math.max(0, XenoServerConfig.barrageCooldownReduce1);
            case 2 -> Math.max(0, XenoServerConfig.barrageCooldownReduce2);
            case 3 -> Math.max(0, XenoServerConfig.barrageCooldownReduce3);
            default -> 0;
        };
    }

    public static int extendLife(int dmzLife, int level) {
        int base = XenoServerConfig.barrageDurationTicks > 0
                ? XenoServerConfig.barrageDurationTicks
                : dmzLife;
        return Math.max(1, base + extraTicks(level));
    }

    public static int cooldownTicks(int dmzCooldown, int level) {
        int base = XenoServerConfig.barrageCooldownTicks > 0
                ? XenoServerConfig.barrageCooldownTicks
                : Math.max(1, dmzCooldown);
        return Math.max(1, base - cooldownReduce(level));
    }

    @SubscribeEvent
    public static void onKiFire(DMZEvent.KiAttackFireEvent event) {
        if (event.getKiAttack() == null || event.getKiAttack().getKiType() != KiAttackData.KiType.BARRAGE) {
            return;
        }
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        int level = CombatSkills.level(player, CombatSkills.BARRAGE);
        event.setCooldownTicks(cooldownTicks(event.getCooldownTicks(), level));
        if (XenoServerConfig.barrageDurationTicks <= 0) return;
        if (!(player.level() instanceof ServerLevel world)) return;
        int window = extendLife(XenoServerConfig.barrageDurationTicks, level);
        AABB box = player.getBoundingBox().inflate(8.0);
        for (KiBlastEntity blast : world.getEntitiesOfClass(KiBlastEntity.class, box,
                ki -> ki.isAlive() && ki.isOwner(player) && ki.getKiRenderType() == 9)) {
            int fireTick = blast.getFireTick();
            if (fireTick < 0) fireTick = blast.tickCount;
            blast.setMaxLife(fireTick + window);
        }
    }
}

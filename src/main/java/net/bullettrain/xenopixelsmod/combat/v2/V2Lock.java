package net.bullettrain.xenopixelsmod.combat.v2;

import com.dragonminez.common.stats.StatsData;
import net.bullettrain.xenopixelsmod.compat.npc.NpcKiAim;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The server's side of "v2 only works on a locked target". See {@link LockRules}.
 */
final class V2Lock {

    private V2Lock() {}

    /**
     * Why {@code target} cannot be {@code player}'s lock-on, or null when it can.
     *
     * @param target the entity the input named as the fighter's lock; null when it named none
     */
    static String refusal(ServerPlayer player, LivingEntity target) {
        if (target == null) return "Lock on to a target first";
        StatsData data = V2Support.stats(player);
        if (data == null || data.getSkills() == null || data.getStatus() == null) return "Lock on to a target first";
        int level = data.getSkills().getSkillLevel("kisense");
        if (!LockRules.canLock(level)) return "Lock-on needs the Ki Sense skill";
        // NpcKiAim.LOCK_RANGE is the base this mod gives DragonMineZ's lock (DmzLockOnNpcMixin).
        if (!LockRules.inRange(player.distanceTo(target), range(data))) return "Target is out of lock-on range";
        return null;
    }

    static double range(StatsData data) {
        if (data == null || data.getSkills() == null || data.getStatus() == null) return 0;
        return LockRules.range(NpcKiAim.LOCK_RANGE, data.getSkills().getSkillLevel("kisense"),
                data.getStatus().isAndroidUpgraded());
    }
}

package net.bullettrain.xenopixelsmod.combat.v3;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.util.TransformationsHelper;
import com.dragonminez.server.world.data.PartySavedData;
import net.bullettrain.xenopixelsmod.event.DmzMasterProtection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Common-side DMZ authority. Missing relevant authority refuses acquisition and retention. */
final class V3Protection {
    private V3Protection() {}
    static StatsData sensingStats(ServerPlayer player) {
        try {
            StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
            return stats != null && stats.getSkills() != null && stats.getSkills().hasSkill("kisense")
                    && stats.getSkills().getSkillLevel("kisense") > 0 ? stats : null;
        } catch (RuntimeException | LinkageError unavailable) { return null; }
    }
    static boolean eligible(ServerPlayer player, LivingEntity target, StatsData sensing) {
        if (sensing == null || target == null || target == player || !target.isAlive() || target.isRemoved()
                || target.level() != player.level()) return false;
        try {
            if (DmzMasterProtection.isDmzMaster(target)) return false;
            boolean npc = npc(target);
            if ((!target.isPickable() && !npc) || (!npc && target.isInvisible()) || target.isInvisibleTo(player)) return false;
            if (target instanceof Player other) {
                if (!(other instanceof ServerPlayer serverTarget) || other.isCreative() || other.isSpectator()
                        || other.isInvulnerable() || player.getServer() == null || !player.getServer().isPvpAllowed()
                        || !player.canHarmPlayer(other)) return false;
                StatsData targetStats = StatsProvider.get(StatsCapability.INSTANCE, other).orElse(null);
                if (targetStats == null || TransformationsHelper.hasAntiKiCloak(other)
                        || (TransformationsHelper.hasGodFormActive(targetStats)
                        && sensing.getSkills().getSkillLevel("godforms") <= 0)) return false;
                PartySavedData parties = PartySavedData.get(player.getServer());
                if (parties == null) return false;
                var ours = parties.getPartyOf(player.getUUID());
                var theirs = parties.getPartyOf(serverTarget.getUUID());
                if (ours != null && theirs != null && ours.getPartyId().equals(theirs.getPartyId()) && !ours.isPvpEnabled()) return false;
            }
            return true;
        } catch (RuntimeException | LinkageError unavailable) { return false; }
    }
    static boolean npc(LivingEntity entity) {
        String name = entity.getClass().getName();
        return name.startsWith("noppes.npcs.entity.") || name.startsWith("espi.mynpcs.entity.");
    }
}

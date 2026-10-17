package net.bullettrain.xenopixelsmod.client.combat;

import com.dragonminez.client.events.LockOnEvent;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.bullettrain.xenopixelsmod.client.XenoServerClientState;
import net.bullettrain.xenopixelsmod.client.compat.npc.DmzLockOnClient;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.combat.StickyLockRules;
import net.bullettrain.xenopixelsmod.combat.v2.LockRules;
import net.bullettrain.xenopixelsmod.compat.npc.NpcKiAim;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/**
 * Keeps the DragonMineZ lock-on through transient drops.
 *
 * <p>Owner request 2026-10-09: when an attack fails to execute the fighter kept losing the DMZ
 * lock. The lock lives client-side in {@code LockOnEvent}, whose tick drops it the moment its
 * own rules fail — including while a dash or launch is still resolving and the target is briefly
 * out of range or out of sight. This keeper remembers the target while it was locked and, if the
 * lock disappears inside a short grace window while the target still passes those same rules,
 * re-pins it through {@link DmzLockOnClient}.
 *
 * <p>An explicit unlock (the lock key) is never overridden: the press opens a short suppression
 * window. A target that is dead, genuinely out of range, or out of sight is left unlocked. The
 * grace length is {@code stickyLockSeconds} in the client config; 0 turns the keeper off.
 */
public final class StickyLock {

    private static final StickyLock INSTANCE = new StickyLock();

    private int rememberedId = -1;
    private long lastSeenMs;
    private long suppressUntilMs;
    private boolean lockKeyWas;

    private StickyLock() {}

    /** Runs once per client tick, from each controller's input layer. */
    public static void tick(Minecraft mc) {
        INSTANCE.tickInner(mc);
    }

    private void tickInner(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            rememberedId = -1;
            lockKeyWas = false;
            return;
        }
        long now = System.currentTimeMillis();
        boolean lockKey = net.bullettrain.xenopixelsmod.client.combat.v2.V2Keys.down(
                net.bullettrain.xenopixelsmod.client.combat.v2.V2Keys.LOCK);
        if (lockKey && !lockKeyWas) suppressUntilMs = now + StickyLockRules.SUPPRESS_MS;
        lockKeyWas = lockKey;

        LivingEntity locked = LockOnEvent.getLockedTarget();
        if (locked != null && locked.isAlive()) {
            rememberedId = locked.getId();
            lastSeenMs = now;
            return;
        }
        LivingEntity remembered = rememberedId < 0 ? null : mc.level.getEntity(rememberedId) instanceof LivingEntity living
                ? living : null;
        long graceMs = (long) (XenoClientConfig.stickyLockSeconds * 1000.0);
        boolean valid = remembered != null && remembered.isAlive() && remembered != mc.player
                && passesLockRules(mc.player, remembered);
        boolean suppress = now < suppressUntilMs;
        if (StickyLockRules.restore(locked != null, valid, now - lastSeenMs, graceMs, suppress)) {
            DmzLockOnClient.apply(rememberedId);
        }
    }

    /** DragonMineZ's own lock rules, evaluated with this client's stats. */
    private static boolean passesLockRules(net.minecraft.client.player.LocalPlayer player,
                                           LivingEntity target) {
        StatsData data;
        try {
            data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        } catch (Throwable t) {
            return false;
        }
        if (data == null || data.getSkills() == null || data.getStatus() == null) return false;
        int level = data.getSkills().getSkillLevel("kisense");
        if (!LockRules.canLock(level)) return false;
        double range = LockRules.range(NpcKiAim.LOCK_RANGE, level, data.getStatus().isAndroidUpgraded());
        if (!LockRules.inRange(player.distanceTo(target), range)) return false;
        return XenoServerClientState.lockOnThroughBlocks() || player.hasLineOfSight(target);
    }
}

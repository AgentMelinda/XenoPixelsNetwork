package net.bullettrain.xenopixelsmod.combat.v3;

import java.util.UUID;
import java.util.function.IntFunction;
import net.bullettrain.xenopixelsmod.combat.controller.CombatControllerMode;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * V3 normal melee uses DragonMineZ's verified attack primitives against the approved lock.
 *
 * <p>The client sends only a V3 input packet. The server resolves the current target identity and
 * then executes one native-style DMZ hit. Raw DMZ attack packets are refused while V3 owns combat.
 */
public final class V3Melee {
    private static final int[] NOBODY = new int[0];
    private V3Melee() {}

    /** Whether V3 narrows this player's native melee right now. */
    public static boolean owns(ServerPlayer player) {
        if (player == null || XenoServerConfig.controllerMode() != CombatControllerMode.V3
                || !XenoServerConfig.bt3CombatEnabled) return false;
        V3Fighter fighter = V3FighterStore.peek(player);
        return fighter != null && fighter.approvedTarget != null;
    }

    /** Raw DMZ requests are refused while V3 owns a locked melee interaction. */
    public static boolean intercepts(ServerPlayer player) {
        return owns(player);
    }

    /** Executes one admitted light tap using server-owned DragonMineZ combo state. */
    public static boolean tap(ServerPlayer player, LivingEntity target, int now) {
        return V3NativeMelee.tap(player, target, now);
    }

    /** The ids DMZ may attack for an owned fighter: the currently valid approved target or nobody. */
    public static int[] narrow(ServerPlayer player, int[] requested) {
        LivingEntity target = V3Targeting.resolve(player);
        if (target == null) return NOBODY;
        UUID approved = target.getUUID();
        return approvedOnly(requested, id -> {
            Entity entity = player.level().getEntity(id);
            return entity == null ? null : entity.getUUID();
        }, approved);
    }

    /** Pure rule: keep the first id whose current entity carries the approved UUID. */
    static int[] approvedOnly(int[] requested, IntFunction<UUID> uuidOf, UUID approved) {
        if (requested == null || approved == null) return NOBODY;
        for (int id : requested) {
            if (approved.equals(uuidOf.apply(id))) return new int[] {id};
        }
        return NOBODY;
    }
}

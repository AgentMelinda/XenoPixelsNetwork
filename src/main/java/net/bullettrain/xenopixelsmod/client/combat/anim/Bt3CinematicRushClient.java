package net.bullettrain.xenopixelsmod.client.combat.anim;

import net.bullettrain.xenopixelsmod.client.combat.DmzAnimHelperClient;
import net.bullettrain.xenopixelsmod.combat.Bt3RushDefinition;
import net.bullettrain.xenopixelsmod.combat.Bt3RushResolver;
import net.bullettrain.xenopixelsmod.network.packet.Bt3RushStatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/** Local prediction and remote playback for one continuous cinematic-rush animation. */
public final class Bt3CinematicRushClient {

    private static final Map<Integer, Active> ACTIVE = new HashMap<>();

    private Bt3CinematicRushClient() {
    }

    public static void apply(Bt3RushStatePacket.Phase phase, int attackerId, int targetId,
                             int sequenceId, String profileId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        if (phase == Bt3RushStatePacket.Phase.CANCEL) {
            ACTIVE.remove(attackerId);
            return;
        }
        Entity raw = minecraft.level.getEntity(attackerId);
        if (!(raw instanceof Player player)) return;
        Bt3RushDefinition definition = Bt3RushResolver.byId(profileId);
        ACTIVE.put(attackerId, new Active(sequenceId, player.tickCount + definition.durationTicks()));
        DmzAnimHelperClient.playLocalMelee(player, definition.animation(), false, 1.0f);
    }

    public static boolean isActive(Player player) {
        if (player == null) return false;
        Active active = ACTIVE.get(player.getId());
        if (active == null) return false;
        if (player.tickCount > active.endsAtTick()) {
            ACTIVE.remove(player.getId());
            return false;
        }
        return true;
    }

    public static void clear() {
        ACTIVE.clear();
    }

    private record Active(int sequenceId, int endsAtTick) {
    }
}

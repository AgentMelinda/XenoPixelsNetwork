package net.bullettrain.xenopixelsmod.combat.v3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.bullettrain.xenopixelsmod.combat.v3.technique.V3CameraBeat;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.CombatV3CameraPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The optional close follow camera during a Dragon Dash flight ({@code /xenoset v3.dashCamera},
 * default off). It belongs to the same approved session and target as the dash and stops on arrival.
 */
public final class V3DashCamera {
    private record View(UUID session, UUID id, UUID target, int targetId) {}
    private static final Map<UUID, View> VIEWS = new HashMap<>();
    private V3DashCamera() {}

    /** Call after the authoritative dash state snapshot. */
    public static void start(ServerPlayer player, LivingEntity target) {
        stop(player);
        UUID session = V3CombatServer.session(player);
        if (session == null || target == null) return;
        View view = new View(session, UUID.randomUUID(), target.getUUID(), target.getId());
        VIEWS.put(player.getUUID(), view);
        send(player, view, 600, new Vec3(4, 2, -7), 0);
    }

    // The former wide "distance" shot after a dash hit was removed on 2026-10-08 (owner): the wide
    // cinematic camera belongs to the Strike attacks, never to the dash chain.

    private static void send(ServerPlayer player, View view, int ticks, Vec3 offset, float focus) {
        ModNetwork.sendToPlayer(player, new CombatV3CameraPacket(view.session, view.id, view.target, view.targetId,
                ticks, 0, List.of(new V3CameraBeat(0, ticks, offset, Vec3.ZERO,
                        focus, V3CameraBeat.Easing.CUT))));
    }

    public static void stop(ServerPlayer player) {
        View view = VIEWS.remove(player.getUUID());
        if (view != null) ModNetwork.sendToPlayer(player, CombatV3CameraPacket.stop(view.session, view.id, view.target, view.targetId));
    }
}

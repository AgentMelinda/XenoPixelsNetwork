package net.bullettrain.xenopixelsmod.combat.v3.ki;

import java.util.UUID;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.CombatV3KiVisualPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Native geometry transport only; the existing shot simulation remains the combat authority. */
final class V3KiVisuals {
    private V3KiVisuals() {}
    static void send(ServerLevel level, UUID id, UUID owner, CombatV3KiVisualPacket.Phase phase,
                     V3KiStyle style, Vec3 pos, Vec3 forward, double length) {
        var packet = new CombatV3KiVisualPacket(level.dimension().location(), id, owner, phase,
                style.kind(), style.nativeRenderType(), style.core(), style.edge(), style.size(), pos,
                forward == null ? Vec3.ZERO : forward, (float)length);
        for (var viewer : level.players()) {
            if (phase == CombatV3KiVisualPacket.Phase.REMOVE || phase == CombatV3KiVisualPacket.Phase.CLEAR_OWNER
                    || viewer.position().distanceToSqr(pos) <= 160 * 160
                    || forward != null && viewer.position().distanceToSqr(pos.add(forward.scale(length))) <= 160 * 160)
                ModNetwork.sendToPlayer(viewer, packet);
        }
    }
}

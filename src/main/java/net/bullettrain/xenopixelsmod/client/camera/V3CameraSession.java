package net.bullettrain.xenopixelsmod.client.camera;

import java.util.LinkedHashSet;
import java.util.UUID;
import net.bullettrain.xenopixelsmod.network.packet.CombatV3CameraPacket;

/** Pure duplicate/stop guard; camera packets cannot revive a retired cast. */
public final class V3CameraSession {
    private final LinkedHashSet<UUID> retired = new LinkedHashSet<>();
    private CombatV3CameraPacket active;

    public boolean apply(CombatV3CameraPacket packet, UUID session, UUID target, boolean cinematic) {
        if (packet == null) return false;
        if (packet.stopping()) {
            retire(packet.cast());
            if (active != null && active.cast().equals(packet.cast()) && active.session().equals(packet.session())) {
                active = null;
                return true;
            }
            return false;
        }
        if (!cinematic || !packet.session().equals(session) || !packet.target().equals(target)
                || retired.contains(packet.cast()) || packet.beats().isEmpty()) return false;
        if (active != null && !active.cast().equals(packet.cast())) return false;
        active = packet;
        return true;
    }
    private void retire(UUID cast) {
        retired.add(cast);
        if (retired.size() > 16) retired.remove(retired.iterator().next());
    }
    public CombatV3CameraPacket active() { return active; }
    public void clear() {
        if (active != null) retire(active.cast());
        active = null;
    }
    public void reset() { active = null; retired.clear(); }
}

package net.bullettrain.xenopixelsmod.client.combat.v3;

import java.util.*;
import net.bullettrain.xenopixelsmod.combat.v3.ki.CastPresentation;
import net.bullettrain.xenopixelsmod.combat.v3.anim.V3AnimationCatalog;
import net.minecraft.client.Minecraft;
import software.bernie.geckolib.animation.RawAnimation;

/** Observer mirror keyed by caster and generation; an old stop cannot end a new cast. */
public final class ClientCastPresentation {
    private record Active(CastPresentation packet, long releasedAt, long finishedAt) {}
    private static final Map<UUID, Active> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> GENERATIONS = new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel lastLevel;
    private ClientCastPresentation() {}
    public static void accept(CastPresentation packet) {
        Minecraft mc = Minecraft.getInstance();
        checkWorld();
        if (mc.level == null) return;
        if (packet.generation() < GENERATIONS.getOrDefault(packet.caster(), -1L)) return;
        Active old = ACTIVE.get(packet.caster());
        // A completion is terminal for its generation, including reordered fire packets.
        if (old != null && old.packet.generation() == packet.generation() && old.finishedAt >= 0) return;
        GENERATIONS.put(packet.caster(), packet.generation());
        long now = mc.level.getGameTime();
        long released = packet.prefix().isEmpty() ? -1 : old != null && old.packet.cast().equals(packet.cast())
                && !old.packet.prefix().isEmpty() ? old.releasedAt : now - packet.elapsedTicks();
        ACTIVE.put(packet.caster(), new Active(packet, released, packet.finished() ? now : -1));
    }
    public static String phase(UUID caster) {
        checkWorld();
        Active active = ACTIVE.get(caster);
        Minecraft mc = Minecraft.getInstance();
        if (active == null || active.packet.prefix().isEmpty() || mc.level == null) return null;
        String prefix = active.packet.prefix();
        if (active.finishedAt >= 0) {
            String recover = prefix + "_recover";
            if (mc.level.getGameTime() - active.finishedAt >= Math.max(1, V3AnimationCatalog.durationTicks(recover))) return null;
            return V3AnimationCatalog.isPlayable(recover) ? recover : null;
        }
        String fire = V3AnimationCatalog.isPlayable(prefix + "_intro") ? prefix + "_intro"
                : V3AnimationCatalog.isPlayable(prefix + "_release") ? prefix + "_release" : prefix + "_fire";
        String sustain = prefix + "_sustain";
        if (mc.level.getGameTime() - active.releasedAt >= V3AnimationCatalog.durationTicks(fire)
                && V3AnimationCatalog.isPlayable(sustain)) return sustain;
        return V3AnimationCatalog.isPlayable(fire) ? fire : null;
    }

    /** Match the actual cast's fire packet, not a pose-name or camera interpolation guess. */
    public static boolean firing(UUID caster, UUID cast) {
        checkWorld(); Active active = ACTIVE.get(caster);
        return active != null && active.packet.cast().equals(cast)
                && active.releasedAt >= 0 && active.finishedAt < 0;
    }
    public static RawAnimation animation(String phase) {
        return phase.endsWith("_sustain") ? RawAnimation.begin().thenLoop(phase)
                : RawAnimation.begin().thenPlayAndHold(phase);
    }
    public static CastPresentation musicCast() {
        checkWorld();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        return ACTIVE.values().stream().filter(a -> a.finishedAt < 0
                && (a.packet.caster().equals(mc.player.getUUID()) || a.packet.target().equals(mc.player.getUUID())))
                .max(Comparator.<Active>comparingInt(a -> a.packet.music().endsWith("001") ? 1 : 0)
                        .thenComparingLong(a -> a.packet.generation())).map(a -> a.packet).orElse(null);
    }
    private static void checkWorld() {
        var level = Minecraft.getInstance().level;
        if (level != lastLevel) { clear(); lastLevel = level; }
    }
    public static void clear() { ACTIVE.clear(); GENERATIONS.clear(); }
}

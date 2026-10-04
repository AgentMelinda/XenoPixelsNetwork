package net.bullettrain.xenopixelsmod.npc.scene;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.lines.XenoNpcSpeech;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Transient server clock for scenes; saved definitions stay in the world store. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoNpcScenePlayback {
    /** pausedElapsed is -1 while running; otherwise it is the frozen timeline position. */
    private record Active(List<XenoNpcScene.Step> steps, long startedAt, int next,
                          long pausedElapsed) {}
    private static final Map<UUID, Active> ACTIVE = new HashMap<>();

    private XenoNpcScenePlayback() {}

    public static boolean start(XenoNpcEntity npc, String sceneId) {
        if (npc == null || npc.level().isClientSide()) {
            return false;
        }
        XenoNpcScene scene = XenoNpcScenes.get(sceneId);
        if (scene == null || scene.steps().isEmpty()) {
            return false;
        }
        ACTIVE.put(npc.getUUID(), new Active(scene.steps(), npc.level().getGameTime(), 0, -1));
        return true;
    }

    public static boolean stop(XenoNpcEntity npc) {
        return npc != null && ACTIVE.remove(npc.getUUID()) != null;
    }

    public static boolean playing(XenoNpcEntity npc) {
        return npc != null && ACTIVE.containsKey(npc.getUUID());
    }

    public static long time(XenoNpcEntity npc) {
        Active active = npc == null ? null : ACTIVE.get(npc.getUUID());
        if (active == null) {
            return -1;
        }
        return active.pausedElapsed() >= 0 ? active.pausedElapsed()
                : Math.max(0, npc.level().getGameTime() - active.startedAt());
    }

    public static boolean pause(XenoNpcEntity npc) {
        Active active = npc == null ? null : ACTIVE.get(npc.getUUID());
        if (active == null || active.pausedElapsed() >= 0) {
            return false;
        }
        ACTIVE.put(npc.getUUID(), new Active(active.steps(), active.startedAt(), active.next(),
                time(npc)));
        return true;
    }

    public static boolean resume(XenoNpcEntity npc) {
        Active active = npc == null ? null : ACTIVE.get(npc.getUUID());
        if (active == null || active.pausedElapsed() < 0) {
            return false;
        }
        ACTIVE.put(npc.getUUID(), new Active(active.steps(),
                npc.level().getGameTime() - active.pausedElapsed(), active.next(), -1));
        return true;
    }

    public static boolean seek(XenoNpcEntity npc, int tick) {
        Active active = npc == null ? null : ACTIVE.get(npc.getUUID());
        if (active == null || tick < 0 || tick > XenoNpcScene.MAX_TIME) {
            return false;
        }
        int next = 0;
        while (next < active.steps().size() && active.steps().get(next).time() < tick) {
            next++;
        }
        ACTIVE.put(npc.getUUID(), new Active(active.steps(), npc.level().getGameTime() - tick,
                next, active.pausedElapsed() >= 0 ? tick : -1));
        return true;
    }

    public static boolean reset(XenoNpcEntity npc) {
        return seek(npc, 0);
    }

    public static void tick(XenoNpcEntity npc) {
        Active active = ACTIVE.get(npc.getUUID());
        if (active == null) {
            return;
        }
        if (active.pausedElapsed() >= 0) {
            return;
        }
        long elapsed = npc.level().getGameTime() - active.startedAt();
        if (elapsed < 0) {
            ACTIVE.remove(npc.getUUID());
            return;
        }
        int next = active.next();
        while (next < active.steps().size() && active.steps().get(next).time() <= elapsed) {
            XenoNpcScene.Step step = active.steps().get(next++);
            if (step.kind() == XenoNpcScene.Kind.SAY) {
                XenoNpcSpeech.say(npc, step.value(), null);
            } else {
                XenoAnimApi.playClip(npc, step.value());
            }
        }
        if (next == active.steps().size()) {
            ACTIVE.remove(npc.getUUID());
        } else {
            ACTIVE.put(npc.getUUID(), new Active(active.steps(), active.startedAt(), next, -1));
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof XenoNpcEntity npc) {
            ACTIVE.remove(npc.getUUID());
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }
}

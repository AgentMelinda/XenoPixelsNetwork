package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.script.ForgeScriptHost;
import net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost;
import net.bullettrain.xenopixelsmod.npc.script.PlayerScriptHost;
import net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import xenoapi.npcs.api.event.WorldEvent;

/**
 * XenoAPI {@code trigger(id, args)}: one {@code ScriptTriggerEvent} delivered to the forge tabs,
 * to the entity's own tabs (an NPC's or a player's), then to Java listeners, as CustomNPCs does.
 * A trigger fired from inside a trigger is allowed a few levels deep and then refused, so a script
 * that triggers itself cannot recurse without end.
 */
public final class XenoScriptTriggers {
    static final int MAX_DEPTH = 4;
    static final int MAX_ARGUMENTS = 32;
    private static int depth;

    private XenoScriptTriggers() {}

    public static void fire(ServerLevel level, BlockPos pos, Entity entity, int id, Object[] arguments) {
        XenoApiAdapters.requireServerThread(level);
        Object[] args = arguments == null ? new Object[0] : arguments.clone();
        if (args.length > MAX_ARGUMENTS) {
            throw new IllegalArgumentException("trigger: at most " + MAX_ARGUMENTS + " arguments");
        }
        if (depth >= MAX_DEPTH) {
            throw new IllegalStateException("trigger: more than " + MAX_DEPTH + " nested triggers (a trigger hook is re-triggering itself)");
        }
        depth++;
        try {
            WorldEvent.ScriptTriggerEvent event = new WorldEvent.ScriptTriggerEvent(id, XenoApiAdapters.wrap(level),
                    pos == null ? null : new XenoPosAdapter(pos), XenoApiAdapters.wrap(entity), args);
            ForgeScriptHost.fireTrigger(level.getServer(), id, args, entity);
            if (entity instanceof XenoNpcEntity npc) NpcScriptHost.fireTrigger(npc, id, args, event);
            if (entity instanceof ServerPlayer player) PlayerScriptHost.fireTyped(player, "trigger", event);
            XenoEventDispatch.post(event);
        } finally {
            depth--;
        }
    }
}

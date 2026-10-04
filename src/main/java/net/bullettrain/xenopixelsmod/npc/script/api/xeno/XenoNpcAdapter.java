package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.compat.npc.NpcScriptSay;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptEntity;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.pathfinder.Path;
import xenoapi.npcs.api.IPos;
import xenoapi.npcs.api.ITimers;
import xenoapi.npcs.api.entity.ICustomNpc;
import xenoapi.npcs.api.entity.IEntityLiving;
import xenoapi.npcs.api.entity.IPlayer;
import xenoapi.npcs.api.entity.IProjectile;
import xenoapi.npcs.api.entity.data.IData;
import xenoapi.npcs.api.entity.data.INPCAdvanced;
import xenoapi.npcs.api.entity.data.INPCAi;
import xenoapi.npcs.api.entity.data.INPCDisplay;
import xenoapi.npcs.api.entity.data.INPCInventory;
import xenoapi.npcs.api.entity.data.INPCJob;
import xenoapi.npcs.api.entity.data.INPCRole;
import xenoapi.npcs.api.entity.data.INPCStats;
import xenoapi.npcs.api.handler.data.IDialog;
import xenoapi.npcs.api.handler.data.IFaction;
import xenoapi.npcs.api.item.IItemStack;

/**
 * A native Xeno NPC as XenoAPI's {@link ICustomNpc}. Speech, targeting, data and timers go
 * through the same native owners as the script binding {@code npc}: data written here is what
 * {@code npc.getTempdata()} reads, and a timer started here fires the script's {@code timer} hook.
 */
public final class XenoNpcAdapter extends XenoLivingAdapter<XenoNpcEntity> implements ICustomNpc<XenoNpcEntity> {
    static final double MAX_NAVIGATION_SPEED = 3.0;
    static final double MAX_NAVIGATION_DISTANCE = 256.0;

    public XenoNpcAdapter(XenoNpcEntity entity) {
        super(entity);
    }

    /** The native wrapper over the host's current shared state; built per call, never cached. */
    ScriptNpc scriptNpc() {
        NpcScriptHost.SharedState state = NpcScriptHost.sharedState(entity);
        return new ScriptNpc(entity, state.temp(), state.timers());
    }

    // ------------------------------------------------------------------ identity

    @Override public String getName() { return entity.npcData().displayName(); }

    /** Renames the NPC through its own data, as the editor and the import wand do. */
    @Override
    public void setName(String name) {
        String next = XenoApiAdapters.boundedText("ICustomNpc.setName", name, 64);
        serverThread();
        entity.npcData().setDisplayName(next);
        entity.refreshNameplate();
    }

    // ------------------------------------------------------------------ speech

    @Override
    public void say(String message) {
        requireLine("ICustomNpc.say", message);
        serverThread();
        scriptNpc().say(message);
    }

    @Override
    public void sayTo(IPlayer player, String message) {
        requireLine("ICustomNpc.sayTo", message);
        if (!(XenoApiAdapters.unwrap(player) instanceof ServerPlayer target)) {
            throw new IllegalArgumentException("ICustomNpc.sayTo: player cannot be null");
        }
        serverThread();
        scriptNpc().sayTo(ScriptEntity.of(target), message);
    }

    private static void requireLine(String method, String message) {
        if (NpcScriptSay.sanitize(message) == null) {
            throw new IllegalArgumentException(method + ": message must be non-empty and within the chat limit");
        }
    }

    // ------------------------------------------------------------------ shared script state

    @Override
    public IData getTempdata() {
        return XenoDataAdapter.of(() -> scriptNpc().getTempdata());
    }

    @Override
    public IData getStoreddata() {
        return XenoDataAdapter.of(() -> scriptNpc().getStoreddata());
    }

    @Override
    public ITimers getTimers() {
        return new XenoTimersAdapter(() -> scriptNpc().getTimers());
    }

    // ------------------------------------------------------------------ navigation

    @Override public boolean isNavigating() { return entity.getNavigation().isInProgress(); }

    @Override
    public void clearNavigation() {
        serverThread();
        entity.getNavigation().stop();
    }

    @Override
    public void navigateTo(double x, double y, double z, double speed) {
        XenoApiAdapters.requireFinite("ICustomNpc.navigateTo", x, y, z, speed);
        if (speed <= 0 || speed > MAX_NAVIGATION_SPEED) {
            throw new IllegalArgumentException("ICustomNpc.navigateTo: speed must be in (0, " + MAX_NAVIGATION_SPEED + "]");
        }
        if (entity.distanceToSqr(x, y, z) > MAX_NAVIGATION_DISTANCE * MAX_NAVIGATION_DISTANCE) {
            throw new IllegalArgumentException("ICustomNpc.navigateTo: target is over "
                    + (int) MAX_NAVIGATION_DISTANCE + " blocks away");
        }
        serverThread();
        entity.getNavigation().moveTo(x, y, z, speed);
    }

    /** The final node of the current path, or null when not navigating. */
    @Override
    public IPos getNavigationPath() {
        Path path = entity.getNavigation().getPath();
        if (path == null || path.isDone()) return null;
        BlockPos end = path.getTarget();
        return end == null ? null : new XenoPosAdapter(end);
    }

    @Override
    public void jump() {
        serverThread();
        entity.getJumpControl().jump();
    }

    // ------------------------------------------------------------------ home / owner

    private BlockPos home() {
        var data = entity.npcData();
        return data.hasHome()
                ? BlockPos.containing(data.homeX(), data.homeY(), data.homeZ())
                : entity.blockPosition();
    }

    /** The NPC's recorded home; its current block when no home is recorded. */
    @Override public int getHomeX() { return home().getX(); }
    @Override public int getHomeY() { return home().getY(); }
    @Override public int getHomeZ() { return home().getZ(); }

    @Override
    public void setHome(int x, int y, int z) {
        serverThread();
        entity.npcData().setHome(x + 0.5, y, z + 0.5);
    }

    /**
     * Who this NPC follows (reference: "In case the npc is a Follower or Companion it will return
     * the one who it's following"). An active follower job returns the followed NPC. The
     * COMPANION role returns its owner when online. Otherwise null.
     */
    @Override
    public IEntityLiving getOwner() {
        var profile = net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.readCached(entity);
        var followed = net.bullettrain.xenopixelsmod.npc.job.NpcFollowerJob.followed(entity, profile);
        if (followed != null) return (IEntityLiving) XenoApiAdapters.wrap(followed);
        if (!net.bullettrain.xenopixelsmod.npc.XenoNpcRoleBehaviour.followsOwner(entity.role())) return null;
        var owner = entity.npcData().owner();
        var server = entity.getServer();
        if (owner == null || server == null) return null;
        return (IEntityLiving) XenoApiAdapters.wrap(server.getPlayerList().getPlayer(owner));
    }

    // ------------------------------------------------------------------ items / commands

    @Override
    public void giveItem(IPlayer player, IItemStack item) {
        if (!(XenoApiAdapters.unwrap(player) instanceof ServerPlayer target)) {
            throw new IllegalArgumentException("ICustomNpc.giveItem: player cannot be null");
        }
        var stack = XenoApiAdapters.unwrap(item).copy();
        if (stack.isEmpty()) return;
        serverThread();
        if (!target.getInventory().add(stack) && !target.getAbilities().instabuild) target.drop(stack, false);
    }

    /**
     * Runs a command as this NPC at permission level 2, as the native {@code npc.executeCommand},
     * and returns what the command printed.
     */
    @Override
    public String executeCommand(String command) {
        var server = entity.getServer();
        if (server == null) throw new IllegalStateException("ICustomNpc.executeCommand needs a running server");
        serverThread();
        if (!net.bullettrain.xenopixelsmod.npc.script.NpcScriptCommandBudget.tryConsume(
                entity.getUUID(), entity.level().getGameTime())) {
            net.bullettrain.xenopixelsmod.npc.script.NpcScriptCommandBudget.refused(entity.getUUID(), getName(), command);
            throw new IllegalStateException("ICustomNpc.executeCommand: more than "
                    + net.bullettrain.xenopixelsmod.npc.script.NpcScriptCommandBudget.PER_TICK
                    + " commands this tick (a hook is re-triggering itself)");
        }
        return XenoApiAdapters.runCommand(server, entity.createCommandSourceStack().withPermission(2), command);
    }

    // ------------------------------------------------------------------ sub-objects

    @Override public INPCDisplay getDisplay() { return new XenoNpcViews.Display(entity); }
    @Override public INPCInventory getInventory() { return new XenoNpcViews.Inventory(entity); }
    @Override public INPCStats getStats() { return new XenoNpcViews.Stats(entity); }
    @Override public INPCAi getAi() { return new XenoNpcViews.Ai(entity); }
    @Override public INPCAdvanced getAdvanced() { return new XenoNpcViews.Advanced(entity); }
    @Override public INPCRole getRole() { return XenoNpcViews.role(entity); }
    @Override public INPCJob getJob() { return XenoNpcViews.job(entity); }

    // ------------------------------------------------------------------ faction / dialogs

    /** The NPC's faction, or null when it has none or names one that no longer exists. */
    @Override
    public IFaction getFaction() {
        String id = entity.npcData().faction();
        return id.isBlank() || net.bullettrain.xenopixelsmod.npc.store.XenoNpcDataSource.faction(id) == null
                ? null : new XenoFactionAdapter(id);
    }

    /** The faction with that CustomNPCs number; -1 clears it. */
    @Override
    public void setFaction(int id) {
        String faction = id < 0 ? "" : XenoScriptIds.factionId(id);
        if (faction == null) throw new xenoapi.npcs.api.CustomNPCsException("ICustomNpc.setFaction: no faction has number %s", id);
        serverThread();
        entity.npcData().setFaction(faction);
        entity.refreshNameplate();
    }

    private static int dialogSlot(String method, int slot) {
        if (slot < 0 || slot >= net.bullettrain.xenopixelsmod.npc.dialog.NpcDialogSlots.MAX_SLOTS) {
            throw new IllegalArgumentException(method + ": slot must be 0-" + (net.bullettrain.xenopixelsmod.npc.dialog.NpcDialogSlots.MAX_SLOTS - 1));
        }
        return slot;
    }

    /** Links a stored conversation into one of the NPC's dialog slots; null empties the slot. */
    @Override
    public void setDialog(int slot, IDialog dialog) {
        int index = dialogSlot("ICustomNpc.setDialog", slot);
        XenoDialogAdapter target = dialog == null ? null : XenoDialogAdapter.nativeDialog(dialog);
        XenoNpcViews.edit(entity, p -> {
            if (target == null) p.dialogSlots.clear(index);
            else p.dialogSlots.set(index, target.group, target.id);
        });
    }

    @Override
    public IDialog getDialog(int slot) {
        var assigned = net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.readCached(entity)
                .dialogSlots.get(dialogSlot("ICustomNpc.getDialog", slot));
        return assigned.assigned() ? new XenoDialogAdapter(assigned.group(), assigned.id()) : null;
    }

    /**
     * Back to its starting state: home, full health, no target, as after a respawn. Stored
     * settings are untouched.
     */
    @Override
    public void reset() {
        serverThread();
        entity.setTarget(null);
        entity.getNavigation().stop();
        entity.clearFire();
        entity.removeAllEffects();
        entity.setHealth(entity.getMaxHealth());
        var data = entity.npcData();
        if (data.hasHome()) entity.teleportTo(data.homeX(), data.homeY(), data.homeZ());
    }

    /** Native NPC data syncs itself on every change; kept so CustomNPCs scripts run unchanged. */
    @Override public void updateClient() { }

    @Override
    public void trigger(int id, Object... arguments) {
        if (!(entity.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            throw new IllegalStateException("ICustomNpc.trigger needs a server level");
        }
        XenoScriptTriggers.fire(level, entity.blockPosition(), entity, id, arguments);
    }

    // ------------------------------------------------------------------ unsupported

    @Override
    public IProjectile shootItem(IEntityLiving target, IItemStack item, int accuracy) {
        throw XenoApiAdapters.unsupported("ICustomNpc.shootItem (native NPCs fire ki attacks, not item projectiles)");
    }

    @Override
    public IProjectile shootItem(double x, double y, double z, IItemStack item, int accuracy) {
        throw XenoApiAdapters.unsupported("ICustomNpc.shootItem (native NPCs fire ki attacks, not item projectiles)");
    }

    @Override
    public XenoNpcEntity getMCEntity() {
        throw XenoApiAdapters.unsupported("ICustomNpc.getMCEntity (raw handles are not exposed)");
    }
}

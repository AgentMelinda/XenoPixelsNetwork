package net.bullettrain.xenopixelsmod.npc.script.api;

/**
 * The argument every hook receives, as CustomNPCs passes {@code event}. Fields a hook does not
 * use are null. {@link #setCanceled} is honoured by {@code interact}, {@code damaged},
 * {@code target}, and {@code meleeAttack}.
 */
public final class ScriptEvent {
    public final ScriptNpc npc;
    public final ScriptEntity player;
    public final ScriptEntity source;
    public final ScriptEntity entity;
    /** MyNPCs meleeAttack counterpart; the same entity is also available as {@code entity}. */
    public final ScriptEntity target;
    public float damage;
    public final String hook;
    /** MyNPCs timer id; zero on other hooks. */
    public int id;
    /** For {@code trigger}: the arguments the caller passed; null for every other hook. */
    public Object[] arguments;
    /** Native quest lookup counterpart. */
    public final ScriptApi API = new ScriptApi();
    /** Typed root API; the legacy public API field keeps its existing quest-helper contract. */
    public xenoapi.npcs.api.NpcAPI getAPI() {
        var api = xenoapi.npcs.api.NpcAPI.Instance();
        if (api == null) throw new IllegalStateException("The native NPC API is not registered");
        return api;
    }
    /** Mutable chat text on player chat hooks. */
    public String message;
    private boolean canceled;

    /** The typed XenoAPI event for this occurrence, or null where none applies (native dialog hooks). */
    public final xenoapi.npcs.api.event.CustomNPCsEvent xeno;

    public ScriptEvent(String hook, ScriptNpc npc, ScriptEntity player, ScriptEntity source,
                       ScriptEntity entity, float damage) {
        this(hook, npc, player, source, entity, damage, null);
    }

    public ScriptEvent(String hook, ScriptNpc npc, ScriptEntity player, ScriptEntity source,
                       ScriptEntity entity, float damage, xenoapi.npcs.api.event.CustomNPCsEvent xeno) {
        this.xeno = xeno;
        this.hook = hook;
        this.npc = npc;
        this.player = player;
        this.source = source;
        this.entity = entity;
        this.target = entity;
        this.damage = damage;
    }

    public Object[] getArguments() {
        return arguments;
    }

    public ScriptNpc getNpc() {
        return npc;
    }

    public ScriptEntity getPlayer() {
        return player;
    }

    public ScriptEntity getSource() {
        return source;
    }

    public ScriptEntity getEntity() {
        return entity;
    }

    public ScriptEntity getTarget() {
        return target;
    }

    public float getDamage() {
        return damage;
    }

    public void setDamage(float damage) {
        if (Float.isFinite(damage)) {
            this.damage = Math.max(0.0f, damage);
            if (xeno != null) net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.setDamage(xeno, this.damage);
        }
    }

    /** For {@code dialogOption}: the index of the chosen answer (carried in the damage slot). */
    public int getOption() {
        return (int) damage;
    }

    public String getHook() {
        return hook;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
        if (xeno != null) net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.setCanceled(xeno, canceled);
    }

    /**
     * Before Java listeners run: an old script may assign {@code event.damage} directly (bypassing
     * setDamage), so the current field value is copied into the typed event first.
     */
    public void pushToXeno() {
        if (xeno != null && net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.damage(xeno) != null) {
            net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.setDamage(xeno, damage);
        }
    }

    /** After Java listeners ran: the typed event's cancel and damage are final where it has them. */
    public void syncFromXeno() {
        if (xeno == null) return;
        if (xeno instanceof net.neoforged.bus.api.ICancellableEvent cancellable) canceled = cancellable.isCanceled();
        Float typed = net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.damage(xeno);
        if (typed != null) damage = typed;
    }

    public boolean isCanceled() {
        return canceled;
    }
}

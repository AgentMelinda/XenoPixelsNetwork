package net.bullettrain.xenopixelsmod.npc.script.api;

/**
 * What a Forge script hook receives. Only wrappers, strings and numbers are exposed; the NeoForge
 * event itself never reaches the script, so a script cannot walk from it to the server.
 *
 * <p>Fields that do not apply to a hook stay null / 0 (for example {@code block} on
 * {@code livingDeath}). {@link #cancel()} works only where the underlying event is cancellable;
 * {@link #isCancelable()} says which.
 */
public final class ForgeScriptEvent {
    public final String hook;
    /** The player involved, when there is one. */
    public ScriptEntity player;
    /** The entity the event is about (the one that died, joined, was hurt or clicked). */
    public ScriptEntity entity;
    /** The attacker / source entity, when there is one. */
    public ScriptEntity source;
    public ScriptWorld world;
    public double x, y, z;
    /** Block registry id for block hooks, e.g. {@code minecraft:stone}. */
    public String block;
    /** Item registry id for item hooks. */
    public String item;
    /** Chat text, damage type id, or dimension id, depending on the hook. */
    public String message;
    /** Damage amount for {@code livingHurt}; writable. */
    public float damage;
    /** For {@code trigger}: the id and arguments the script passed. */
    public int id;
    public Object[] arguments;
    private final boolean cancelable;
    private boolean canceled;

    public ForgeScriptEvent(String hook, boolean cancelable) {
        this.hook = hook;
        this.cancelable = cancelable;
    }

    public String getHook() { return hook; }
    public int getId() { return id; }
    public Object[] getArguments() { return arguments; }
    public ScriptEntity getPlayer() { return player; }
    public ScriptEntity getEntity() { return entity; }
    public ScriptEntity getSource() { return source; }
    public ScriptWorld getWorld() { return world; }
    public String getBlock() { return block; }
    public String getItem() { return item; }
    public String getMessage() { return message; }
    public float getDamage() { return damage; }
    public void setDamage(float value) { damage = Float.isFinite(value) ? Math.max(0f, value) : damage; }
    public boolean isCancelable() { return cancelable; }
    public void cancel() { setCanceled(true); }
    public void setCanceled(boolean value) { if (cancelable) canceled = value; }
    public boolean isCanceled() { return canceled; }
}

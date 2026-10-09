package net.bullettrain.xenopixelsmod.npc.script.api;

import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.lines.BubbleShape;
import net.bullettrain.xenopixelsmod.npc.lines.XenoNpcSpeech;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * The scripted NPC itself: {@code npc} in every hook. Mirrors the CustomNPCs {@code ICustomNpc}
 * calls scripts use most - say, target, stored/temp data, position, health, command - over a
 * native Xeno NPC. Speech goes through the Xeno speech bubble, with an optional palette
 * ({@code blue/gold/green/red}) and outline ({@code rounded/thought/shout/banner}) per line.
 */
public final class ScriptNpc extends ScriptEntity {
    private static final String STORED_DATA = "XenoScriptData";
    /** Stored data is persisted on the entity, so it is bounded. */
    private static final int MAX_STORED_KEYS = 64;
    private static final int MAX_STORED_VALUE = 1024;

    private final XenoNpcEntity npc;
    private final CompoundTag testPersistentData;
    private final Map<String, Object> tempData;
    private final ScriptTimers timers;
    private final Data storedDataView = new Data(false);
    private final Data tempDataView = new Data(true);

    public ScriptNpc(XenoNpcEntity npc, Map<String, Object> tempData) {
        this(npc, tempData, new ScriptTimers());
    }

    public ScriptNpc(XenoNpcEntity npc, Map<String, Object> tempData, ScriptTimers timers) {
        super(npc);
        this.npc = npc;
        this.testPersistentData = null;
        this.tempData = tempData == null ? new HashMap<>() : tempData;
        this.timers = timers;
    }

    /** Unit-test seam for the data API; production always uses the entity constructor. */
    ScriptNpc(CompoundTag persistentData, Map<String, Object> tempData) {
        this(persistentData, tempData, new ScriptTimers());
    }

    /** Test seam sharing one timer owner between wrappers; test timers read game time 0. */
    ScriptNpc(CompoundTag persistentData, Map<String, Object> tempData, ScriptTimers timers) {
        super(null);
        this.npc = null;
        this.testPersistentData = persistentData;
        this.tempData = tempData == null ? new HashMap<>() : tempData;
        this.timers = timers;
    }

    private CompoundTag persistentData() {
        return npc == null ? testPersistentData : npc.getPersistentData();
    }

    // ---------------------------------------------------------------- speech

    /** A speech bubble in this NPC's own palette and outline. */
    public boolean say(String text) {
        return say(text, "", "");
    }

    /** A speech bubble in {@code palette} (blue, gold, green, red). */
    public boolean say(String text, String palette) {
        return say(text, palette, "");
    }

    /** A speech bubble in {@code palette} and {@code shape} (rounded, thought, shout, banner). */
    public boolean say(String text, String palette, String shape) {
        String line = net.bullettrain.xenopixelsmod.compat.npc.NpcScriptSay.sanitize(text);
        if (line == null) {
            return false;
        }
        return XenoNpcSpeech.say(npc, line, null, palette == null ? "" : palette,
                BubbleShape.byName(shape, BubbleShape.INHERIT));
    }

    /** A chat line to one player, prefixed with this NPC's name, as CustomNPCs' {@code sayTo}. */
    public void sayTo(ScriptEntity player, String text) {
        String line = net.bullettrain.xenopixelsmod.compat.npc.NpcScriptSay.sanitize(text);
        if (line != null && player != null && player.unwrap() instanceof Player target) {
            target.sendSystemMessage(Component.literal("<" + getName() + "> " + line));
        }
    }

    // ---------------------------------------------------------------- identity / body

    /** Native shortcuts for the appearance and effect controls shown in the Functions panel. */
    public boolean setAuraScale(float scale) { return NativeXenoScriptApi.INSTANCE.setAuraScale(this, scale); }
    public float getAuraScale() {
        return net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.readCached(npc).auraScale;
    }
    public boolean setAura(boolean on) { return NativeXenoScriptApi.INSTANCE.setAura(this, on); }
    private net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoNpcAdapter adapter() {
        if (npc == null) throw new IllegalStateException("NPC appearance functions require a live NPC");
        return new net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoNpcAdapter(npc);
    }
    /** Typed native API, preserving this wrapper's legacy return contracts. */
    public xenoapi.npcs.api.entity.ICustomNpc<?> getAPI() { return adapter(); }
    public xenoapi.npcs.api.entity.data.INPCDisplay getDisplay() { return adapter().getDisplay(); }
    public xenoapi.npcs.api.entity.data.INPCStats getStats() { return adapter().getStats(); }
    public xenoapi.npcs.api.entity.data.INPCAi getAi() { return adapter().getAi(); }
    public xenoapi.npcs.api.entity.data.INPCAdvanced getAdvanced() { return adapter().getAdvanced(); }
    public xenoapi.npcs.api.entity.data.INPCInventory getInventory() { return adapter().getInventory(); }
    public xenoapi.npcs.api.entity.data.INPCRole getRole() { return adapter().getRole(); }
    public xenoapi.npcs.api.entity.data.INPCJob getJob() { return adapter().getJob(); }
    public int getSize() { return getDisplay().getSize(); }
    public void setSize(int size) { getDisplay().setSize(size); }
    public boolean playSound(String sound, float volume, float pitch) {
        return NativeXenoScriptApi.INSTANCE.playSound(this, sound, volume, pitch);
    }
    public boolean playAnimation(String animation) { return NativeXenoScriptApi.INSTANCE.playAnimation(this, animation); }
    public boolean playAnimation(String animation, float speed) { return NativeXenoScriptApi.INSTANCE.playAnimation(this, animation, speed); }


    @Override
    public String getName() {
        return npc.npcData().displayName();
    }

    public void setPosition(double x, double y, double z) {
        npc.teleportTo(x, y, z);
    }

    public void setHealth(float health) {
        new net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoLivingAdapter<>(npc).setHealth(health);
    }

    /** Changes the live maximum health attribute with the native API's validation. */
    public void setMaxHealth(float health) {
        new net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoLivingAdapter<>(npc).setMaxHealth(health);
    }

    public boolean isKilled() { return !npc.isAlive() || npc.isRemoved(); }

    /** Removes this instance; configured death/respawn scheduling remains with the NPC entity. */
    public void despawn() {
        new net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoNpcAdapter(npc).despawn();
    }

    // ---------------------------------------------------------------- combat

    public ScriptEntity getTarget() {
        return ScriptEntity.of(npc.getTarget());
    }

    public void setTarget(ScriptEntity target) {
        npc.setTarget(target == null ? null : target.unwrap());
    }

    public void clearTarget() {
        npc.setTarget(null);
    }

    public ScriptEntity getAttackTarget() { return getTarget(); }
    public void setAttackTarget(ScriptEntity target) { setTarget(target); }

    public TimerView getTimers() { return new TimerView(); }

    public final class TimerView {
        private long now() { return npc == null ? 0L : npc.level().getGameTime(); }
        public boolean start(int id, int ticks, boolean repeating) {
            return timers.start(id, ticks, repeating, now());
        }
        public boolean forceStart(int id, int ticks, boolean repeating) {
            return timers.forceStart(id, ticks, repeating, now());
        }
        public boolean has(int id) { return timers.has(id); }
        public boolean stop(int id) { return timers.stop(id); }
        public boolean reset(int id) { return timers.reset(id, now()); }
        public void clear() { timers.clear(); }
    }

    // ---------------------------------------------------------------- data

    /** MyNPCs-compatible data object: {@code event.npc.getStoreddata().put("key", value)}. */
    public Data getStoreddata() {
        return storedDataView;
    }

    /** MyNPCs-compatible temporary data object, shared across this NPC's script tabs. */
    public Data getTempdata() {
        return tempDataView;
    }

    /** The six methods exposed by MyNPCs' pinned {@code IData} interface. */
    public final class Data {
        private final boolean temporary;

        private Data(boolean temporary) {
            this.temporary = temporary;
        }

        public void put(String key, Object value) {
            if (temporary) setTempData(key, value);
            else putStoredCompat(key, value);
        }

        public Object get(String key) {
            if (temporary) return getTempData(key);
            CompoundTag data = persistentData().getCompound(STORED_DATA);
            if (key == null || !data.contains(key)) return null;
            return data.get(key) instanceof NumericTag number
                    ? number.getAsDouble() : data.getString(key);
        }

        public void remove(String key) {
            if (temporary) removeTempData(key);
            else removeStoredData(key);
        }

        public boolean has(String key) {
            return temporary ? hasTempData(key) : hasStoredData(key);
        }

        public String[] getKeys() {
            return temporary
                    ? tempData.keySet().toArray(String[]::new)
                    : persistentData().getCompound(STORED_DATA).getAllKeys()
                            .toArray(String[]::new);
        }

        public void clear() {
            if (temporary) tempData.clear();
            else persistentData().remove(STORED_DATA);
        }
    }

    private void putStoredCompat(String key, Object value) {
        if (key == null || key.isBlank() || key.length() > 64) return;
        CompoundTag data = persistentData().getCompound(STORED_DATA);
        if (!data.contains(key) && data.size() >= MAX_STORED_KEYS) return;
        if (value instanceof Number number) {
            double numeric = number.doubleValue();
            if (!Double.isFinite(numeric)) return;
            data.putDouble(key, numeric);
        } else if (value instanceof String string) {
            if (string.length() > MAX_STORED_VALUE) return;
            data.putString(key, string);
        } else {
            return;
        }
        persistentData().put(STORED_DATA, data);
    }

    /** Temporary data: kept while the NPC is loaded, gone after a restart. */
    public Object getTempData(String key) {
        return key == null ? null : tempData.get(key);
    }

    public void setTempData(String key, Object value) {
        if (key == null) return;
        if (value == null) {
            tempData.remove(key);
        } else if (tempData.size() < MAX_STORED_KEYS || tempData.containsKey(key)) {
            tempData.put(key, value);
        }
    }

    public boolean hasTempData(String key) {
        return key != null && tempData.containsKey(key);
    }

    public void removeTempData(String key) {
        if (key != null) tempData.remove(key);
    }

    /** Stored data: saved with the NPC. Values are kept as text, as CustomNPCs stores them. */
    public String getStoredData(String key) {
        CompoundTag data = persistentData().getCompound(STORED_DATA);
        return key == null || !data.contains(key) ? null : data.getString(key);
    }

    public boolean setStoredData(String key, Object value) {
        if (key == null || key.isBlank() || key.length() > 64) {
            return false;
        }
        CompoundTag data = persistentData().getCompound(STORED_DATA);
        if (value == null) {
            data.remove(key);
        } else {
            String text = String.valueOf(value);
            if (text.length() > MAX_STORED_VALUE
                    || (!data.contains(key) && data.size() >= MAX_STORED_KEYS)) {
                return false;
            }
            data.putString(key, text);
        }
        persistentData().put(STORED_DATA, data);
        return true;
    }

    public boolean hasStoredData(String key) {
        return key != null && persistentData().getCompound(STORED_DATA).contains(key);
    }

    public void removeStoredData(String key) {
        setStoredData(key, null);
    }

    // ---------------------------------------------------------------- world

    public ScriptWorld getWorld() {
        return npc.level() instanceof net.minecraft.server.level.ServerLevel level
                ? new ScriptWorld(level) : null;
    }

    /**
     * Runs a command as this NPC, at permission level 2, output suppressed - CustomNPCs'
     * {@code executeCommand}. Writing a script already needs level 4, so this grants nothing the
     * author did not have.
     */
    public void executeCommand(String command) {
        if (command == null || command.isBlank() || command.length() > 256) {
            return;
        }
        var server = npc.getServer();
        if (server == null) {
            return;
        }
        if (!net.bullettrain.xenopixelsmod.npc.script.NpcScriptCommandBudget.tryConsume(
                npc.getUUID(), npc.level().getGameTime())) {
            net.bullettrain.xenopixelsmod.npc.script.NpcScriptCommandBudget.refused(npc.getUUID(), getName(), command);
            return;
        }
        var source = npc.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        String line = command.startsWith("/") ? command.substring(1) : command;
        server.getCommands().performPrefixedCommand(source, line);
    }
}

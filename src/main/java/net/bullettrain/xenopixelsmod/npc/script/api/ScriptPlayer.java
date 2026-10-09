package net.bullettrain.xenopixelsmod.npc.script.api;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** A player as a script sees it: the entity view plus messaging and XenoPixels progression. */
public final class ScriptPlayer extends ScriptEntity {
    private final ServerPlayer player;
    /** Typed native player API, alongside this wrapper's legacy helpers. */
    public xenoapi.npcs.api.entity.IPlayer<?> getAPI() {
        if (player == null) throw new IllegalStateException("Player API functions require a live server player");
        return (xenoapi.npcs.api.entity.IPlayer<?>)
                net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoApiAdapters.wrap(player);
    }
    private static final Map<java.util.UUID, Map<String, Object>> TEMP = new ConcurrentHashMap<>();

    ScriptPlayer(ServerPlayer player) {
        super(player);
        this.player = player;
    }

    /** A chat line to this player only. Capped like every other scripted line. */
    public void message(String text) {
        String line = net.bullettrain.xenopixelsmod.compat.npc.NpcScriptSay.sanitize(text);
        if (line != null) {
            player.sendSystemMessage(Component.literal(line));
        }
    }

    public String getDisplayName() {
        return player.getDisplayName().getString();
    }

    public static void forget(java.util.UUID player) { TEMP.remove(player); }
    public Data getStoreddata() { return new Data(false); }
    public Data getTempdata() { return new Data(true); }
    public boolean hasActiveQuest(int slot) {
        String id = ScriptApi.questId(slot);
        return id != null && XenoCapabilities.get(player).map(d -> d.quests().isActive(id)).orElse(false);
    }
    public boolean hasFinishedQuest(int slot) {
        String id = ScriptApi.questId(slot);
        return id != null && XenoCapabilities.get(player).map(d -> d.quests().hasCompleted(id)).orElse(false);
    }
    public ScriptWorld getWorld() { return new ScriptWorld(player.serverLevel()); }
    public void startQuest(int slot) {
        String id = ScriptApi.questId(slot);
        if (id != null) net.bullettrain.xenopixelsmod.features.progression.ParallelQuests.start(player, id);
    }
    public void finishQuest(int slot) {
        String id = ScriptApi.questId(slot);
        if (id == null) return;
        XenoCapabilities.get(player).ifPresent(data -> {
            if (data.quests().isActive(id))
                net.bullettrain.xenopixelsmod.features.progression.ProgressionEvents.completeQuest(player, data, id);
        });
    }
    public void stopQuest(int slot) {
        String id = ScriptApi.questId(slot);
        if (id == null) return;
        XenoCapabilities.get(player).ifPresent(data -> {
            if (data.quests().isActive(id)) {
                data.quests().abandon(id);
                net.bullettrain.xenopixelsmod.features.progression.QuestSync.push(player);
            }
        });
    }

    public final class Data {
        private final boolean temporary;
        private Data(boolean temporary) { this.temporary = temporary; }
        private Map<String, Object> temp() { return TEMP.computeIfAbsent(player.getUUID(), ignored -> new ConcurrentHashMap<>()); }
        private CompoundTag stored() { return XenoCapabilities.get(player).orElseThrow().scriptData(); }
        public void put(String key, Object value) {
            if (key == null || key.isBlank() || key.length() > 64 || value == null) return;
            if (temporary) {
                if (temp().size() < 64 || temp().containsKey(key)) temp().put(key, value);
                return;
            }
            CompoundTag tag = stored();
            if (tag.size() >= 64 && !tag.contains(key)) return;
            if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
                tag.putDouble(key, number.doubleValue());
            } else if (value instanceof String string && string.length() <= 1024) {
                tag.putString(key, string);
            }
        }
        public Object get(String key) {
            if (key == null) return null;
            if (temporary) return temp().get(key);
            var value = stored().get(key);
            return value instanceof NumericTag number ? number.getAsDouble()
                    : stored().contains(key) ? stored().getString(key) : null;
        }
        public boolean has(String key) { return key != null && (temporary ? temp().containsKey(key) : stored().contains(key)); }
        public void remove(String key) { if (key != null) { if (temporary) temp().remove(key); else stored().remove(key); } }
        public String[] getKeys() { return temporary ? temp().keySet().toArray(String[]::new) : stored().getAllKeys().toArray(String[]::new); }
        public void clear() {
            if (temporary) temp().clear();
            else for (String key : java.util.List.copyOf(stored().getAllKeys())) stored().remove(key);
        }
    }

    /** Adds XenoPixels progression points, as {@code /xenopoints add}. */
    public boolean addXenoPoints(int amount) {
        return net.bullettrain.xenopixelsmod.command.XenoPointsCommands.addPoints(player, amount);
    }

    /**
     * Starts a quest by id through the same rules as {@code /xenoquest start}.
     *
     * @return null when it started, otherwise the refusal the command would print
     */
    public String startQuest(String questId) {
        if (questId == null || questId.isBlank()) {
            return "no quest id";
        }
        return net.bullettrain.xenopixelsmod.features.progression.ParallelQuests.start(player,
                questId.trim());
    }
}

package net.bullettrain.xenopixelsmod.space.dragonball;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The wishes the dragon can grant (design-doc 16, "assignment lives in the datapack"), loaded from
 * {@code data/<namespace>/xenospace/wish/<path>.json} on every resource reload. Each wish has a display name, a description
 * and rewards of four kinds: {@code item} (id, count), {@code effect} (id, seconds, amplifier), {@code xp} (levels) and
 * {@code command} (run as the server with permission level 2, for pack authors). A malformed file is skipped and logged.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class WishCatalog extends SimplePreparableReloadListener<List<WishCatalog.Wish>> {
    public static final int MAX_WISHES = 32, MAX_REWARDS = 16;

    public record Reward(String type, String id, int amount, int amplifier, String command) {}

    public record Wish(String id, String name, String description, List<Reward> rewards) {}

    private static volatile List<Wish> wishes = List.of();

    public static List<Wish> wishes() { return wishes; }

    public static Wish byId(String id) {
        for (Wish wish : wishes) if (wish.id().equals(id)) return wish;
        return null;
    }

    @SubscribeEvent public static void register(AddReloadListenerEvent event) { event.addListener(new WishCatalog()); }

    @Override protected List<Wish> prepare(ResourceManager manager, ProfilerFiller profiler) {
        var found = new TreeMap<ResourceLocation, net.minecraft.server.packs.resources.Resource>(
                manager.listResources("xenospace/wish", id -> id.getPath().endsWith(".json")));
        var out = new ArrayList<Wish>();
        for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> entry : found.entrySet()) {
            if (out.size() >= MAX_WISHES) { XenoPixelsMod.LOGGER.warn("Wish catalog: more than {} wishes, the rest are ignored", MAX_WISHES); break; }
            var location = entry.getKey();
            String path = location.getPath();
            String id = location.getNamespace() + ":" + path.substring("xenospace/wish/".length(), path.length() - 5);
            try (var stream = entry.getValue().open()) {
                byte[] bytes = stream.readNBytes(16385);
                if (bytes.length > 16384) throw new IllegalArgumentException("wish file exceeds 16 KiB");
                out.add(parse(id, JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject()));
            } catch (Exception invalid) {
                XenoPixelsMod.LOGGER.warn("Wish catalog: {} skipped: {}", location, invalid.getMessage());
            }
        }
        return List.copyOf(out);
    }

    @Override protected void apply(List<Wish> prepared, ResourceManager manager, ProfilerFiller profiler) {
        wishes = prepared;
        XenoPixelsMod.LOGGER.info("Wish catalog loaded: {} wishes", prepared.size());
    }

    static Wish parse(String id, JsonObject json) {
        String name = text(json, "name"), description = json.has("description") ? json.get("description").getAsString() : "";
        if (name.length() > 64 || description.length() > 256) throw new IllegalArgumentException("name/description too long");
        JsonArray array = json.getAsJsonArray("rewards");
        if (array == null || array.isEmpty() || array.size() > MAX_REWARDS) throw new IllegalArgumentException("1.." + MAX_REWARDS + " rewards required");
        var rewards = new ArrayList<Reward>();
        for (JsonElement element : array) {
            JsonObject r = element.getAsJsonObject();
            String type = text(r, "type");
            switch (type) {
                case "item" -> rewards.add(new Reward(type, checkedId(text(r, "id")), clamp(r, "count", 1, 1, 4096), 0, ""));
                case "effect" -> rewards.add(new Reward(type, checkedId(text(r, "id")), clamp(r, "seconds", 60, 1, 86_400), clamp(r, "amplifier", 0, 0, 9), ""));
                case "xp" -> rewards.add(new Reward(type, "", clamp(r, "levels", 10, 1, 1000), 0, ""));
                case "command" -> {
                    String command = text(r, "command");
                    if (command.length() > 512) throw new IllegalArgumentException("command too long");
                    rewards.add(new Reward(type, "", 0, 0, command));
                }
                default -> throw new IllegalArgumentException("unknown reward type " + type);
            }
        }
        return new Wish(id, name, description, List.copyOf(rewards));
    }

    private static String text(JsonObject json, String key) {
        if (!json.has(key)) throw new IllegalArgumentException("missing " + key);
        String value = json.get(key).getAsString();
        if (value.isBlank()) throw new IllegalArgumentException("blank " + key);
        return value;
    }

    private static String checkedId(String id) {
        if (ResourceLocation.tryParse(id) == null) throw new IllegalArgumentException("invalid id " + id);
        return id;
    }

    private static int clamp(JsonObject json, String key, int fallback, int min, int max) {
        int value = json.has(key) ? json.get(key).getAsInt() : fallback;
        return Math.max(min, Math.min(max, value));
    }
}

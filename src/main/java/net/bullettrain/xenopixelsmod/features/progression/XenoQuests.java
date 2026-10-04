package net.bullettrain.xenopixelsmod.features.progression;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStores;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads quests from {@code data/<namespace>/npcs/quests/*.json}.
 *
 * <p>Quests were three entries in a static map, which meant a dialogue could offer exactly those
 * three - and the dialogue editor will happily let an author type any id. A quest that cannot be
 * defined without recompiling is not much of a quest system.
 *
 * <p>Follows {@code XenoDialogues} and {@code XenoFactions}, which is the established loader shape
 * here. A pack quest with the same id as a built-in replaces it, so the three built-ins are
 * defaults rather than reserved names.
 *
 * <pre>
 * {
 *   "title": "Wolf Trouble",
 *   "description": "Drive off 5 wolves",
 *   "target": 5,
 *   "objective": "kill_type",
 *   "parameter": "minecraft:wolf",
 *   "reward": { "skill_points": 1, "experience": 40 },
 *   "category": "Main",
 *   "log_text": "The journal entry, which the quest log pages through.",
 *   "complete_text": "What the NPC says when it is handed in.",
 *   "completion": "npc",
 *   "completer_npc": "Stephanie"
 * }
 * </pre>
 *
 * <p>Failures are loud and per-quest: a malformed file is reported with its id and reason, and the
 * rest of the pack still loads. A typo should cost one quest, not all of them.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class XenoQuests extends SimpleJsonResourceReloadListener {

    private static final String DIRECTORY = "npcs/quests";
    private static final Gson GSON = new GsonBuilder().create();

    public static final XenoQuests INSTANCE = new XenoQuests();

    private static volatile Map<String, ParallelQuests.QuestDef> loaded = Map.of();
    private static volatile List<String> loadErrors = List.of();

    private XenoQuests() {
        super(GSON, DIRECTORY);
    }

    /** A pack-defined quest by id, or null when no pack defines one. */
    public static ParallelQuests.QuestDef get(String id) {
        if (id == null) return null;
        String key = id.trim().toLowerCase(Locale.ROOT);
        ParallelQuests.QuestDef world = fromWorldStore(key);
        return world == null ? loaded.get(key) : world;
    }

    /** Every pack-defined quest, in pack order. */
    public static Map<String, ParallelQuests.QuestDef> all() {
        Map<String, ParallelQuests.QuestDef> combined = new LinkedHashMap<>(loaded);
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store != null) {
            for (XenoNpcWorldStore.Entry entry : store.list(XenoNpcStoreCategory.QUESTS)) {
                ParallelQuests.QuestDef quest = decodeStored(entry);
                if (quest != null) combined.put(quest.id(), quest);
            }
        }
        return java.util.Collections.unmodifiableMap(combined);
    }

    /** What failed at the last reload, so commands can surface it. */
    public static List<String> loadErrors() {
        return loadErrors;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources,
                         ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<String, ParallelQuests.QuestDef> quests = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            String id = entry.getKey().getPath().toLowerCase(Locale.ROOT);
            try {
                quests.put(id, parse(id, entry.getValue()));
            } catch (RuntimeException e) {
                String message = entry.getKey() + ": " + e.getMessage();
                errors.add(message);
                XenoPixelsMod.LOGGER.error("Failed to load quest {}", message);
            }
        }

        loaded = java.util.Collections.unmodifiableMap(quests);
        loadErrors = List.copyOf(errors);

        if (errors.isEmpty()) {
            XenoPixelsMod.LOGGER.info("Loaded {} quest(s)", quests.size());
        } else {
            XenoPixelsMod.LOGGER.warn("Loaded {} quest(s), {} failed", quests.size(),
                    errors.size());
        }
    }

    public static ParallelQuests.QuestDef parse(String id, JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("quest must be a JSON object");
        }
        JsonObject root = element.getAsJsonObject();

        String title = root.has("title") ? root.get("title").getAsString() : id;
        String description = root.has("description") ? root.get("description").getAsString() : "";
        int target = root.has("target") ? root.get("target").getAsInt() : 1;
        if (target < 1) {
            throw new IllegalArgumentException("target must be at least 1");
        }

        String rawObjective = root.has("objective") ? root.get("objective").getAsString() : null;
        QuestObjective objective = QuestObjective.parse(rawObjective);
        if (rawObjective != null && objective == null) {
            // Named but unrecognised. Defaulting it would produce a quest that counts the wrong
            // thing and looks like it works, which is the failure this whole type exists to end.
            throw new IllegalArgumentException("unknown objective '" + rawObjective + "'");
        }
        String parameter = root.has("parameter") ? root.get("parameter").getAsString() : "";
        QuestObjective.Goal goal = new QuestObjective.Goal(
                objective == null ? QuestObjective.KILL_MOBS : objective, parameter);

        if (needsParameter(goal) && goal.parameter().isBlank()) {
            throw new IllegalArgumentException("" + goal.type().name().toLowerCase(Locale.ROOT)
                    + " needs a target name");
        }
        List<QuestStep> steps = stepsFrom(root, goal, target);

        String category = root.has("category") ? root.get("category").getAsString() : "";
        String logText = root.has("log_text") ? root.get("log_text").getAsString() : "";
        String completeText = root.has("complete_text")
                ? root.get("complete_text").getAsString() : "";
        String completerNpc = root.has("completer_npc")
                ? root.get("completer_npc").getAsString() : "";
        QuestCompletionMode completion = QuestCompletionMode.parse(
                root.has("completion") ? root.get("completion").getAsString() : null);
        QuestRepeat repeat = QuestRepeat.parse(root.has("repeat")
                ? root.get("repeat").getAsString() : null);

        String next = root.has("next_quest") ? root.get("next_quest").getAsString() : "";
        boolean random = root.has("random_reward") && root.get("random_reward").getAsBoolean();
        QuestMail mail = mailFrom(root.get("mail"));
        QuestAvailability availability = QuestAvailability.fromJson(root.get("availability"));
        String completionPalette = root.has("completion_palette")
                ? root.get("completion_palette").getAsString() : "blue";
        String completionFrame = root.has("completion_frame")
                ? root.get("completion_frame").getAsString() : "rounded";

        // The NPC a KILL_NPC step names starts hunting the claimant when the quest starts.
        boolean targetHunts = root.has("target_hunts") && root.get("target_hunts").getAsBoolean();

        return new ParallelQuests.QuestDef(id, title, description, target, goal,
                QuestReward.fromJson(root.get("reward")),
                category, logText, completeText, completion, completerNpc, repeat,
                steps, next, random, mail, availability, completionPalette, completionFrame,
                targetHunts);
    }

    private static boolean needsParameter(QuestObjective.Goal goal) {
        return goal.type() == QuestObjective.KILL_TYPE || goal.type() == QuestObjective.KILL_NPC
                || goal.type() == QuestObjective.ITEM || goal.type() == QuestObjective.DIALOG
                || goal.type() == QuestObjective.LOCATION;
    }

    private static List<QuestStep> stepsFrom(JsonObject root, QuestObjective.Goal goal, int target) {
        if (!root.has("objectives") || !root.get("objectives").isJsonArray()) {
            return List.of(new QuestStep(goal, target));
        }
        List<QuestStep> steps = new ArrayList<>();
        for (JsonElement raw : root.getAsJsonArray("objectives")) {
            if (!raw.isJsonObject()) continue;
            JsonObject obj = raw.getAsJsonObject();
            String rawObjective = obj.has("objective") ? obj.get("objective").getAsString() : null;
            QuestObjective objective = QuestObjective.parse(rawObjective);
            if (rawObjective != null && objective == null) {
                throw new IllegalArgumentException("unknown objective '" + rawObjective + "'");
            }
            QuestObjective.Goal stepGoal = new QuestObjective.Goal(
                    objective == null ? QuestObjective.KILL_MOBS : objective,
                    obj.has("parameter") ? obj.get("parameter").getAsString() : "");
            int stepTarget = obj.has("target") ? obj.get("target").getAsInt() : 1;
            if (stepTarget < 1) {
                throw new IllegalArgumentException("target must be at least 1");
            }
            if (needsParameter(stepGoal) && stepGoal.parameter().isBlank()) {
                throw new IllegalArgumentException(stepGoal.type().name().toLowerCase(Locale.ROOT)
                        + " needs a target name");
            }
            steps.add(new QuestStep(stepGoal, stepTarget,
                    obj.has("take_items") && obj.get("take_items").getAsBoolean(),
                    obj.has("ignore_damage") && obj.get("ignore_damage").getAsBoolean(),
                    obj.has("ignore_nbt") && obj.get("ignore_nbt").getAsBoolean(),
                    obj.has("x") ? obj.get("x").getAsInt() : 0,
                    obj.has("y") ? obj.get("y").getAsInt() : 0,
                    obj.has("z") ? obj.get("z").getAsInt() : 0,
                    obj.has("dimension") ? obj.get("dimension").getAsString() : "",
                    obj.has("radius") ? obj.get("radius").getAsInt() : 0));
        }
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("objectives must name at least one step");
        }
        if (steps.size() > ActiveQuest.MAX_STEPS) {
            throw new IllegalArgumentException("objectives may contain at most "
                    + ActiveQuest.MAX_STEPS + " steps");
        }
        return steps;
    }

    private static QuestMail mailFrom(JsonElement element) {
        if (element == null || !element.isJsonObject()) return QuestMail.NONE;
        JsonObject obj = element.getAsJsonObject();
        return new QuestMail(
                obj.has("sender") ? obj.get("sender").getAsString() : "",
                obj.has("subject") ? obj.get("subject").getAsString() : "",
                obj.has("body") ? obj.get("body").getAsString() : "",
                QuestReward.fromJson(obj).items());
    }

    private static ParallelQuests.QuestDef fromWorldStore(String id) {
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null) return null;
        for (XenoNpcWorldStore.Entry entry : store.list(XenoNpcStoreCategory.QUESTS)) {
            if (!entry.id().equals(id)) continue;
            ParallelQuests.QuestDef decoded = decodeStored(entry);
            if (decoded != null) return decoded;
        }
        return null;
    }

    private static ParallelQuests.QuestDef decodeStored(XenoNpcWorldStore.Entry entry) {
        String json = entry.tag().getString("DefinitionJson");
        if (json.isBlank()) return null;
        try {
            return parse(entry.id(), com.google.gson.JsonParser.parseString(json));
        } catch (RuntimeException e) {
            XenoPixelsMod.LOGGER.warn("Could not read stored NPC quest {}: {}", entry.id(), e.toString());
            return null;
        }
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }
}

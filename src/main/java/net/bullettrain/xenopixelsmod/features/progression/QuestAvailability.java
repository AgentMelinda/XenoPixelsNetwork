package net.bullettrain.xenopixelsmod.features.progression;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The checks MyNPCs puts on a quest or on the dialogue option that offers it.
 *
 * <p>A failed check hides the offer. Shared dialogue ids are recorded when a player opens them;
 * an unrecorded or unresolved id fails an {@code after} check.
 */
public record QuestAvailability(List<QuestGate> quests, List<DialogGate> dialogs, String daytime,
                                List<FactionGate> factions, List<ScoreGate> scores, int minLevel) {

    public static final QuestAvailability NONE =
            new QuestAvailability(List.of(), List.of(), "always", List.of(), List.of(), 0);

    public enum QuestState { ALWAYS, AFTER, BEFORE, ACTIVE, NOT_ACTIVE, COMPLETED, CAN_START }

    public enum DialogState { ALWAYS, AFTER, BEFORE }

    public enum Stance { FRIENDLY, NEUTRAL, HOSTILE }

    public enum Compare { SMALLER, EQUAL, BIGGER }

    public record QuestGate(String questId, QuestState state) {
        public QuestGate {
            questId = questId == null ? "" : questId.trim().toLowerCase(Locale.ROOT);
            state = state == null ? QuestState.ALWAYS : state;
        }
    }

    public record DialogGate(String dialogId, DialogState state) {
        public DialogGate {
            dialogId = dialogId == null ? "" : dialogId.trim().toLowerCase(Locale.ROOT);
            state = state == null ? DialogState.ALWAYS : state;
        }
    }

    public record FactionGate(String factionId, Stance stance, boolean matches) {
        public FactionGate {
            factionId = factionId == null ? "" : factionId.trim().toLowerCase(Locale.ROOT);
            stance = stance == null ? Stance.NEUTRAL : stance;
        }
    }

    public record ScoreGate(String objective, Compare compare, int value) {
        public ScoreGate {
            objective = objective == null ? "" : objective.trim();
            compare = compare == null ? Compare.EQUAL : compare;
        }
    }

    public QuestAvailability {
        quests = cap(quests, 4);
        dialogs = cap(dialogs, 4);
        daytime = daytime == null || daytime.isBlank() ? "always" : daytime.trim().toLowerCase(Locale.ROOT);
        factions = cap(factions, 2);
        scores = cap(scores, 2);
        minLevel = Math.max(0, minLevel);
    }

    private static <T> List<T> cap(List<T> values, int max) {
        List<T> copy = values == null ? List.of() : List.copyOf(values);
        return copy.size() <= max ? copy : List.copyOf(copy.subList(0, max));
    }

    public static QuestAvailability fromJson(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return NONE;
        }
        JsonObject root = element.getAsJsonObject();
        List<QuestGate> quests = new ArrayList<>();
        for (JsonElement raw : array(root, "quests")) {
            if (!raw.isJsonObject()) continue;
            JsonObject obj = raw.getAsJsonObject();
            quests.add(new QuestGate(text(obj, "id"), enumOf(QuestState.class, text(obj, "state"),
                    QuestState.ALWAYS)));
        }
        List<DialogGate> dialogs = new ArrayList<>();
        for (JsonElement raw : array(root, "dialogs")) {
            if (!raw.isJsonObject()) continue;
            JsonObject obj = raw.getAsJsonObject();
            dialogs.add(new DialogGate(text(obj, "id"), enumOf(DialogState.class, text(obj, "state"),
                    DialogState.ALWAYS)));
        }
        List<FactionGate> factions = new ArrayList<>();
        for (JsonElement raw : array(root, "factions")) {
            if (!raw.isJsonObject()) continue;
            JsonObject obj = raw.getAsJsonObject();
            factions.add(new FactionGate(text(obj, "id"),
                    enumOf(Stance.class, text(obj, "stance"), Stance.NEUTRAL),
                    !obj.has("matches") || obj.get("matches").getAsBoolean()));
        }
        List<ScoreGate> scores = new ArrayList<>();
        for (JsonElement raw : array(root, "scores")) {
            if (!raw.isJsonObject()) continue;
            JsonObject obj = raw.getAsJsonObject();
            scores.add(new ScoreGate(text(obj, "objective"),
                    enumOf(Compare.class, text(obj, "compare"), Compare.EQUAL),
                    obj.has("value") ? obj.get("value").getAsInt() : 0));
        }
        return new QuestAvailability(quests, dialogs,
                root.has("daytime") ? root.get("daytime").getAsString() : "always",
                factions, scores,
                root.has("min_level") ? root.get("min_level").getAsInt() : 0);
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("daytime", daytime);
        root.addProperty("min_level", minLevel);
        root.add("quests", gates(quests, gate -> {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", gate.questId());
            obj.addProperty("state", gate.state().name().toLowerCase(Locale.ROOT));
            return obj;
        }));
        root.add("dialogs", gates(dialogs, gate -> {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", gate.dialogId());
            obj.addProperty("state", gate.state().name().toLowerCase(Locale.ROOT));
            return obj;
        }));
        root.add("factions", gates(factions, gate -> {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", gate.factionId());
            obj.addProperty("stance", gate.stance().name().toLowerCase(Locale.ROOT));
            obj.addProperty("matches", gate.matches());
            return obj;
        }));
        root.add("scores", gates(scores, gate -> {
            JsonObject obj = new JsonObject();
            obj.addProperty("objective", gate.objective());
            obj.addProperty("compare", gate.compare().name().toLowerCase(Locale.ROOT));
            obj.addProperty("value", gate.value());
            return obj;
        }));
        return root;
    }

    private static JsonArray array(JsonObject root, String key) {
        return root.has(key) && root.get(key).isJsonArray()
                ? root.getAsJsonArray(key) : new JsonArray();
    }

    private static String text(JsonObject obj, String key) {
        return obj.has(key) ? obj.get(key).getAsString() : "";
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String raw, E fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static <T> JsonArray gates(List<T> values, java.util.function.Function<T, JsonObject> map) {
        JsonArray array = new JsonArray();
        for (T value : values) {
            array.add(map.apply(value));
        }
        return array;
    }
}

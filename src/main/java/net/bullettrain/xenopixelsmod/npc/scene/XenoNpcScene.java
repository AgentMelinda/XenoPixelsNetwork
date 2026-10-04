package net.bullettrain.xenopixelsmod.npc.scene;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** A bounded, shared timeline. Times are ticks after starting, with no arbitrary commands. */
public final class XenoNpcScene {
    public static final int MAX_STEPS = 32;
    public static final int MAX_TIME = 1200;
    public static final int MAX_TEXT = 256;
    public static final int MAX_CLIP = 64;
    public static final int MAX_NAME = 128;

    public enum Kind { SAY, CLIP }

    public record Step(int time, Kind kind, String value) {
        public Step {
            if (time < 0 || time > MAX_TIME || kind == null || value == null || value.isBlank()
                    || value.length() > (kind == Kind.SAY ? MAX_TEXT : MAX_CLIP)) {
                throw new IllegalArgumentException("invalid scene step");
            }
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Time", time);
            tag.putString("Kind", kind.name());
            tag.putString("Value", value);
            return tag;
        }
    }

    private final String id;
    private String name;
    private final List<Step> steps = new ArrayList<>();

    public XenoNpcScene(String id, String name) {
        this.id = id == null ? "" : id;
        setName(name);
    }

    public String id() { return id; }
    public String name() { return name; }
    public List<Step> steps() { return List.copyOf(steps); }

    public void setName(String value) {
        name = value == null || value.isBlank() ? id : value.trim();
        if (name.length() > MAX_NAME) {
            name = name.substring(0, MAX_NAME);
        }
    }

    public boolean add(Step step) {
        if (step == null || steps.size() >= MAX_STEPS) {
            return false;
        }
        steps.add(step);
        steps.sort(Comparator.comparingInt(Step::time));
        return true;
    }

    public boolean remove(int index) {
        if (index < 0 || index >= steps.size()) {
            return false;
        }
        steps.remove(index);
        return true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        ListTag list = new ListTag();
        for (Step step : steps) {
            list.add(step.save());
        }
        tag.put("Steps", list);
        return tag;
    }

    /** Refusal for an untrusted editor payload, or null when every field is bounded. */
    public static String rejectPayload(CompoundTag tag) {
        if (tag == null || !tag.contains("Name", Tag.TAG_STRING)
                || !tag.contains("Steps", Tag.TAG_LIST)
                || tag.getString("Name").isBlank()
                || tag.getString("Name").length() > MAX_NAME) {
            return "invalid scene name or steps";
        }
        ListTag rows = tag.getList("Steps", Tag.TAG_COMPOUND);
        if (tag.get("Steps") instanceof ListTag raw && !raw.isEmpty()
                && raw.getElementType() != Tag.TAG_COMPOUND) {
            return "scene steps must be compounds";
        }
        if (rows.size() > MAX_STEPS) {
            return "too many scene steps";
        }
        for (int i = 0; i < rows.size(); i++) {
            CompoundTag row = rows.getCompound(i);
            if (!row.contains("Time", Tag.TAG_INT) || !row.contains("Kind", Tag.TAG_STRING)
                    || !row.contains("Value", Tag.TAG_STRING)) {
                return "invalid scene step " + i;
            }
            try {
                new Step(row.getInt("Time"), Kind.valueOf(row.getString("Kind")),
                        row.getString("Value"));
            } catch (IllegalArgumentException invalid) {
                return "invalid scene step " + i;
            }
        }
        return null;
    }

    /** Invalid hand-edited rows are ignored; valid rows remain playable. */
    public static XenoNpcScene load(String id, CompoundTag tag) {
        XenoNpcScene scene = new XenoNpcScene(id, tag == null ? id : tag.getString("Name"));
        if (tag == null || !tag.contains("Steps", Tag.TAG_LIST)) {
            return scene;
        }
        ListTag list = tag.getList("Steps", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(MAX_STEPS, list.size()); i++) {
            CompoundTag row = list.getCompound(i);
            try {
                scene.add(new Step(row.getInt("Time"), Kind.valueOf(row.getString("Kind")),
                        row.getString("Value")));
            } catch (IllegalArgumentException ignored) {
                // A damaged row does not make the rest of the scene unusable.
            }
        }
        return scene;
    }
}

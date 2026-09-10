package net.bullettrain.xenopixelsmod.client.compat.npc;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side transform hold: target form + game-time window so
 * {@link NpcHairVis} can call {@code HairRenderer} with progress 0→1.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class NpcTransformHairClient {
    public static final class Hold {
        private final String toGroup;
        private final String toForm;
        private final boolean stack;
        private final int duration;
        private final long startGameTime;
        private boolean active = true;
        private float lastProgress;
        private long lastNanos;

        private Hold(boolean stack, String toGroup, String toForm, int duration, long startGameTime) {
            this.stack = stack;
            this.toGroup = toGroup;
            this.toForm = toForm;
            this.duration = duration;
            this.startGameTime = startGameTime;
        }

        public String toGroup() { return toGroup; }
        public String toForm() { return toForm; }
        public boolean stack() { return stack; }
        public boolean active() { return active; }

        public float progress(long gameTime, float partialTick) {
            long now = System.nanoTime();
            if (active) {
                lastProgress = duration <= 0 ? 1.0f
                        : Mth.clamp(((gameTime - startGameTime) + partialTick) / duration, 0.0f, 1.0f);
            } else {
                float dt = lastNanos == 0L ? 1.0f
                        : Mth.clamp((now - lastNanos) / 50_000_000.0f, 0.0f, 2.0f);
                // Same reverse rate used by DMZHairLayer when a form charge is cancelled.
                lastProgress = Math.max(0.0f, lastProgress - 0.05f * dt);
            }
            lastNanos = now;
            return lastProgress;
        }

        private void cancel() { active = false; lastNanos = 0L; }
        public boolean finished() { return !active && lastProgress <= 0.0f; }
    }

    private static final Map<UUID, Hold> HOLDS = new ConcurrentHashMap<>();

    private NpcTransformHairClient() {}

    public static void apply(UUID id, String toGroup, String toForm, int duration, long startGameTime) {
        apply(id, false, toGroup, toForm, duration, startGameTime);
    }

    public static void apply(UUID id, boolean stack, String toGroup, String toForm, int duration, long startGameTime) {
        if (id == null) {
            return;
        }
        HOLDS.put(id, new Hold(stack,
                toGroup == null ? "" : toGroup,
                toForm == null ? "" : toForm,
                duration,
                startGameTime));
    }

    public static void clear(UUID id) {
        Hold hold = id == null ? null : HOLDS.get(id);
        if (hold != null) hold.cancel();
    }

    public static void clearAll() {
        HOLDS.clear();
    }

    public static Hold get(UUID id) {
        if (id == null) return null;
        Hold hold = HOLDS.get(id);
        if (hold != null && hold.finished()) {
            HOLDS.remove(id, hold);
            return null;
        }
        return hold;
    }

    /** Drop the hold once appearance has committed the target form. */
    public static void onAppearance(UUID id, String formGroup, String form) {
        onAppearance(id, formGroup, form, "", "");
    }

    /** Drop normal and stack holds against their respective committed state. */
    public static void onAppearance(UUID id, String formGroup, String form,
                                    String stackGroup, String stackForm) {
        Hold hold = get(id);
        if (hold == null) {
            return;
        }
        String group = hold.stack() ? safe(stackGroup) : safe(formGroup);
        String formId = hold.stack() ? safe(stackForm) : safe(form);
        if (group.equals(hold.toGroup()) && formId.equals(hold.toForm())) {
            HOLDS.remove(id);
        }
    }

    private static String safe(String value) { return value == null ? "" : value; }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clearAll();
    }
}

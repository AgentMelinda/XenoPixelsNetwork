package net.bullettrain.xenopixelsmod.client.npc.speech;

import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Which entities currently have a speech bubble, keyed by entity id.
 *
 * <p>Bookkeeping only - {@link SpeechBubbleRenderer} reads this each frame and draws whatever is
 * still live. Ported from the supplied {@code java.zip} integration bundle, whose own notes trace
 * the pattern to DragonMineZ's {@code com.dragonminez.client.systems.kisense.CombatIndicators}: a
 * map keyed by entity id with a tick that drops expired entries, which is how DMZ tracks its
 * floating damage popups. Adapted here to one message per entity rather than an accumulating
 * counter, and to this mod's atlas rather than the bundle's own texture enum.
 *
 * <p>Client-side. A bubble everyone should see arrives as {@code XenoNpcSpeechPacket} and lands here
 * through the client packet handler; nothing else should call {@code show} directly for an NPC line,
 * or only the local player would see it.
 */
public final class SpeechBubbleQueue {

    /** Three seconds at 20 ticks per second. */
    public static final long DEFAULT_LIFETIME_TICKS = 60L;

    private static final Map<Integer, Bubble> ACTIVE = new HashMap<>();

    private SpeechBubbleQueue() {
    }

    public static void show(Entity entity, String text) {
        if (entity == null) {
            return;
        }
        show(entity.getId(), text, entity.level().getGameTime(), DEFAULT_LIFETIME_TICKS);
    }

    public static void show(Entity entity, String text, long durationTicks) {
        if (entity == null) {
            return;
        }
        show(entity.getId(), text, entity.level().getGameTime(), durationTicks);
    }

    /**
     * The form the packet handler uses, where only an id and a duration are on hand.
     *
     * <p>A blank message clears instead of showing an empty bubble, so a server can silence one
     * without a second packet type.
     */
    public static void show(int entityId, String text, long nowGameTime, long durationTicks) {
        show(entityId, text, nowGameTime, durationTicks, "",
                net.bullettrain.xenopixelsmod.npc.lines.BubbleShape.INHERIT);
    }

    /** As above, with this line's own palette and shape (blank / INHERIT keep the NPC's). */
    public static void show(int entityId, String text, long nowGameTime, long durationTicks,
                            String palette, net.bullettrain.xenopixelsmod.npc.lines.BubbleShape shape) {
        if (text == null || text.isBlank()) {
            clear(entityId);
            return;
        }
        long ticks = Math.max(1L, durationTicks);
        ACTIVE.put(entityId, new Bubble(text, nowGameTime, nowGameTime + ticks,
                palette == null ? "" : palette,
                shape == null ? net.bullettrain.xenopixelsmod.npc.lines.BubbleShape.INHERIT : shape));
    }

    public static void clear(int entityId) {
        ACTIVE.remove(entityId);
    }

    public static void clearAll() {
        ACTIVE.clear();
    }

    /** Drops expired bubbles. Called once per client tick by the renderer. */
    public static void tick(long nowGameTime) {
        ACTIVE.values().removeIf(bubble -> nowGameTime >= bubble.expiresAtGameTime());
    }

    /** A copy, so rendering can iterate while a packet arrives on another thread. */
    public static Map<Integer, Bubble> activeSnapshot() {
        return new HashMap<>(ACTIVE);
    }

    public static boolean isEmpty() {
        return ACTIVE.isEmpty();
    }

    public record Bubble(String text, long shownAtGameTime, long expiresAtGameTime, String palette,
                         net.bullettrain.xenopixelsmod.npc.lines.BubbleShape shape) {

        public Bubble(String text, long shownAtGameTime, long expiresAtGameTime) {
            this(text, shownAtGameTime, expiresAtGameTime, "",
                    net.bullettrain.xenopixelsmod.npc.lines.BubbleShape.INHERIT);
        }

        /** Full opacity until the last ten ticks, then smoothly to zero. */
        public float alpha(long nowGameTime) {
            return SpeechBubbleMotion.alpha(expiresAtGameTime, nowGameTime);
        }

        /** Eased lift and return, in world blocks. */
        public float verticalOffset(double nowGameTime) {
            return SpeechBubbleMotion.verticalOffset(
                    shownAtGameTime, expiresAtGameTime, nowGameTime);
        }
    }
}

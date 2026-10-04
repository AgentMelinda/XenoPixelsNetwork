package net.bullettrain.xenopixelsmod.client.npc.speech;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntToDoubleFunction;

/**
 * The order ambient speech bubbles are drawn in.
 *
 * <p>Separated from the renderer because it is the only part of this that a test can reach —
 * {@code SpeechBubbleRenderer} needs a running client, a camera and a level, and none of those
 * exist in a unit test. The ordering is arithmetic, and it is the half that was silently wrong.
 *
 * <p>The renderer used to walk {@code SpeechBubbleQueue.activeSnapshot().entrySet()} directly.
 * That map is a {@code HashMap}, whose iteration order is unspecified and free to change between
 * frames, and the render pass runs with {@code RenderSystem.disableDepthTest()}. Together those
 * meant that when two nearby NPCs' bubbles overlapped on screen, which one drew on top was
 * arbitrary <em>per frame</em> — so the pair flickered — and a distant NPC's bubble could paint
 * over a near one for no reason a player could see.
 *
 * <p>Sorting far to near fixes both: the nearest bubble is drawn last and therefore on top, and it
 * stays there. The depth test deliberately stays off, because enabling it would let an NPC's own
 * body and the terrain clip its bubble, which is a worse artefact than the overlap.
 */
public final class SpeechBubbleOrder {

    private SpeechBubbleOrder() {
    }

    /**
     * Orders entity ids so the farthest is first and the nearest last.
     *
     * <p>Stable: two bubbles at the same distance keep the order they arrived in, which matters
     * because an unstable tie-break would reintroduce exactly the frame-to-frame swapping this
     * exists to stop.
     *
     * @param entityIds       the bubbles to draw
     * @param squaredDistance camera distance squared for one id; squared because only the ordering
     *                        is wanted and a square root would change nothing about it
     */
    public static List<Integer> farToNear(List<Integer> entityIds,
                                          IntToDoubleFunction squaredDistance) {
        if (entityIds == null || entityIds.isEmpty()) {
            return List.of();
        }
        List<Integer> ordered = new ArrayList<>(entityIds);
        // List.sort is guaranteed stable, which is what keeps equal distances from swapping.
        ordered.sort(Comparator.comparingDouble(id -> -squaredDistance.applyAsDouble(id)));
        return List.copyOf(ordered);
    }
}

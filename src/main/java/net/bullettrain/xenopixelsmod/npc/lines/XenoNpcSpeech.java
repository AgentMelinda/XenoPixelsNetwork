package net.bullettrain.xenopixelsmod.npc.lines;

import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.XenoNpcSpeechPacket;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogueText;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes an NPC say a line, and keeps the per-NPC cycle position.
 *
 * <p>The cycle index lives here rather than on the client so repeated clicks advance the same
 * sequence for everyone: two players taking turns clicking one NPC walk through its lines together
 * rather than each seeing their own private progression. It is deliberately not persisted - an
 * NPC starting again from its first line after a restart is fine, and it keeps this off the save
 * format.
 *
 * <p>Server-side only. The bubble itself is drawn from {@code XenoNpcSpeechPacket}.
 */
public final class XenoNpcSpeech {

    /** Entity UUID to the index of the next line, per category. */
    private static final Map<UUID, Map<XenoNpcLines.Category, Integer>> CURSORS =
            new ConcurrentHashMap<>();

    private XenoNpcSpeech() {
    }

    /**
     * Speaks the next line in {@code category}, if the NPC has any.
     *
     * @param viewer the player who prompted it, for {@code {player}} substitution; may be null for
     *               ambient categories that nobody triggered
     * @return true when something was said
     */
    public static boolean speak(XenoNpcEntity npc, XenoNpcLines.Category category, Player viewer) {
        if (npc == null || npc.level().isClientSide()) {
            return false;
        }
        XenoNpcLines lines = linesFor(npc);
        if (!lines.has(category)) {
            return false;
        }

        int index = nextIndex(npc.getUUID(), category);
        String raw = lines.at(category, index);
        if (raw.isBlank()) {
            return false;
        }

        String text = XenoDialogueText.resolve(raw,
                viewer == null ? "" : viewer.getName().getString(),
                npc.npcData().displayName());

        ModNetwork.sendToTrackingAndSelf(npc,
                new XenoNpcSpeechPacket(npc.getId(), text, durationFor(npc)));
        return true;
    }

    /**
     * Says one specific line, rather than picking one from a category.
     *
     * <p>For a scripted line - a scene step - where the text is authored rather than chosen. Goes
     * through the same substitution, the same packet and the same profile-driven duration as
     * {@link #speak}, so a staged line looks exactly like a spoken one and there is no second way
     * for an NPC to talk.
     *
     * @param viewer whoever the line is addressed to, for {@code {player}}; may be null
     * @return true when something was sent
     */
    public static boolean say(XenoNpcEntity npc, String raw, Player viewer) {
        return say(npc, raw, viewer, "", BubbleShape.INHERIT);
    }

    /**
     * Says one line in a bubble with its own palette and shape - the script path
     * ({@code npc.say(text, palette, shape)}). Works for any living NPC the client draws bubbles
     * over, so a CustomNPCs or MyNPCs NPC scripted through the bridge speaks the same way a native
     * one does. A blank palette or {@link BubbleShape#INHERIT} keeps the NPC's own.
     *
     * @return true when something was sent
     */
    public static boolean say(net.minecraft.world.entity.LivingEntity npc, String raw, Player viewer,
                              String palette, BubbleShape shape) {
        if (npc == null || npc.level().isClientSide() || raw == null || raw.isBlank()) {
            return false;
        }
        String speaker = npc instanceof XenoNpcEntity xeno
                ? xeno.npcData().displayName() : npc.getName().getString();
        String text = XenoDialogueText.resolve(raw,
                viewer == null ? "" : viewer.getName().getString(), speaker);
        String paletteName = palette == null || palette.isBlank() ? ""
                : net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.canonicalPalette(palette);
        ModNetwork.sendToTrackingAndSelf(npc, new XenoNpcSpeechPacket(npc.getId(), text,
                durationFor(npc), paletteName,
                (shape == null ? BubbleShape.INHERIT : shape).ordinal()));
        return true;
    }

    /**
     * How far away a player can be and still be the one an NPC is talking to.
     *
     * <p>Beyond this it is talking to itself, which is what {@code WORLD} is for. The speech bubble
     * is legible well past this, so the split is about who the line is addressed to, not who can
     * read it.
     */
    private static final double VIEWER_RANGE = 16.0;

    /** Minimum ticks between two ambient lines from one NPC. Twenty seconds. */
    private static final int AMBIENT_COOLDOWN = 400;

    /** Entity UUID to the game time it may speak ambiently again. */
    private static final Map<UUID, Long> AMBIENT_READY = new ConcurrentHashMap<>();

    /**
     * Says an idle line, if the NPC has one and has not spoken recently.
     *
     * <p>{@code RANDOM} when there is somebody to say it to, {@code WORLD} when there is not - the
     * split {@link XenoNpcLines.Category} already describes. Both categories existed and were
     * loaded from role definitions from the start; nothing ever triggered them, so an NPC with
     * ambient lines written for it stood in silence.
     *
     * <p>Cheap to call often: the cooldown is checked before anything else, and an NPC whose role
     * has no ambient lines is turned away by {@link #speak} without a lookup of its own.
     *
     * @return true when something was said
     */
    public static boolean ambient(XenoNpcEntity npc) {
        if (npc == null || npc.level().isClientSide() || !npc.isAlive()) {
            return false;
        }
        // Checked before the cooldown so a silenced NPC costs nothing at all, and so turning the
        // toggle back on does not have to wait out a cooldown that was never really running.
        if (!net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.readCached(npc)
                .ambientLinesEnabled) {
            return false;
        }
        long now = npc.level().getGameTime();
        // An answer to another NPC is not ambient chatter: it goes out on time, cooldown or not.
        if (answerPending(npc, now)) {
            AMBIENT_READY.put(npc.getUUID(), now + AMBIENT_COOLDOWN);
            return true;
        }
        Long ready = AMBIENT_READY.get(npc.getUUID());
        if (ready != null && now < ready) {
            return false;
        }
        // Set the cooldown whether or not a line comes out, so an NPC with no ambient lines is not
        // re-checked on every staggered tick.
        AMBIENT_READY.put(npc.getUUID(), now + AMBIENT_COOLDOWN);

        if (talkToNearbyNpc(npc, now)) {
            return true;
        }
        Player nearest = npc.level().getNearestPlayer(npc, VIEWER_RANGE);
        return nearest != null
                ? speak(npc, XenoNpcLines.Category.RANDOM, nearest)
                : speak(npc, XenoNpcLines.Category.WORLD, null);
    }

    /** How close two NPCs must stand to talk, in blocks. */
    private static final double NPC_TALK_RANGE = 6.0;
    /** Ticks before the addressed NPC answers, so the two bubbles read as an exchange. */
    private static final int NPC_REPLY_DELAY = 50;
    /** Addressed NPC -> {game time to answer, who spoke to it}. */
    private static final Map<UUID, Object[]> PENDING_REPLY = new ConcurrentHashMap<>();

    /**
     * NPC-to-NPC lines: an NPC with {@code NPC} lines addresses another NPC nearby, filling
     * {@code {npc}} with that NPC's name. Only lines the author wrote are ever said.
     */
    private static boolean talkToNearbyNpc(XenoNpcEntity npc, long now) {
        XenoNpcLines lines = linesFor(npc);
        if (!lines.has(XenoNpcLines.Category.NPC)) {
            return false;
        }
        net.minecraft.world.entity.LivingEntity other = nearestNpc(npc);
        if (other == null) {
            return false;
        }
        String otherName = other instanceof XenoNpcEntity xeno
                ? xeno.npcData().displayName() : other.getName().getString();
        List<String> options = lines.lines(XenoNpcLines.Category.NPC);
        String raw = options.get(npc.getRandom().nextInt(options.size())).replace("{npc}", otherName);
        if (!say(npc, raw, null)) {
            return false;
        }
        if (other instanceof XenoNpcEntity addressed && linesFor(addressed).has(XenoNpcLines.Category.NPC)) {
            PENDING_REPLY.put(addressed.getUUID(), new Object[] {now + NPC_REPLY_DELAY,
                    npc.npcData().displayName()});
        }
        return true;
    }

    /** Answers an NPC that spoke to this one, once its reply delay is up. */
    private static boolean answerPending(XenoNpcEntity npc, long now) {
        Object[] pending = PENDING_REPLY.get(npc.getUUID());
        if (pending == null || now < (long) pending[0]) {
            return false;
        }
        PENDING_REPLY.remove(npc.getUUID());
        List<String> options = linesFor(npc).lines(XenoNpcLines.Category.NPC);
        if (options.isEmpty()) {
            return false;
        }
        String raw = options.get(npc.getRandom().nextInt(options.size()))
                .replace("{npc}", String.valueOf(pending[1]));
        return say(npc, raw, null);
    }

    private static net.minecraft.world.entity.LivingEntity nearestNpc(XenoNpcEntity npc) {
        net.minecraft.world.entity.LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (net.minecraft.world.entity.LivingEntity candidate : npc.level().getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class,
                npc.getBoundingBox().inflate(NPC_TALK_RANGE),
                e -> e != npc && e.isAlive() && isNpc(e))) {
            double d = candidate.distanceToSqr(npc);
            if (d < bestDistance) {
                bestDistance = d;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean isNpc(net.minecraft.world.entity.LivingEntity entity) {
        String type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getKey(entity.getType()).toString();
        return net.bullettrain.xenopixelsmod.features.progression.QuestNpcTarget.family(
                entity.getClass().getName(), type) != null;
    }

    /** The NPC's line set, resolved from its role definition refs. */
    /**
     * How long this NPC's bubble stays up.
     *
     * <p>Decided here rather than on the client because the client is told a duration by the
     * packet, and the profile that carries it is server state.
     */
    private static int durationFor(net.minecraft.world.entity.LivingEntity npc) {
        var profile = net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.readCached(npc);
        int stored = profile.bubbleDurationTicks;
        return stored > 0 ? stored : XenoNpcSpeechPacket.DEFAULT_TICKS;
    }

    /**
     * The lines this NPC speaks.
     *
     * <p>Its own when it has any, otherwise its role's. <b>Full override</b>, not a per-category
     * merge: an NPC with one custom greeting speaks only its own lines, in every category.
     *
     * <p>Lines used to come only from the role's datapack file, so every NPC of a role said the
     * same things and nothing in game could change it - a datapack is not writable at runtime.
     * That is the same reason dialogue lives on the profile, and this follows it.
     */
    public static XenoNpcLines linesFor(XenoNpcEntity npc) {
        if (npc == null) {
            return XenoNpcLines.EMPTY;
        }
        var profile = net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.readCached(npc);
        if (profile.hasOwnLines()) {
            java.util.Map<XenoNpcLines.Category, java.util.List<String>> own =
                    new java.util.EnumMap<>(XenoNpcLines.Category.class);
            for (XenoNpcLines.Category category : XenoNpcLines.Category.values()) {
                java.util.List<String> stored = profile.linesFor(category);
                if (!stored.isEmpty()) {
                    own.put(category, stored);
                }
            }
            return new XenoNpcLines(own);
        }
        var definition = net.bullettrain.xenopixelsmod.npc.XenoNpcRoleDefinitions.get(npc.role());
        if (definition == null || definition.refs() == null) {
            return XenoNpcLines.EMPTY;
        }
        return XenoNpcLineSets.get(definition.refs().lines());
    }

    private static int nextIndex(UUID npcId, XenoNpcLines.Category category) {
        Map<XenoNpcLines.Category, Integer> perCategory =
                CURSORS.computeIfAbsent(npcId, id -> new ConcurrentHashMap<>());
        // Each category cycles independently, so attack lines do not consume interact lines.
        return perCategory.merge(category, 0, (existing, ignored) -> existing + 1);
    }

    /** Drops a deleted NPC's cursor so the map cannot grow without bound. */
    public static void forget(UUID npcId) {
        if (npcId != null) {
            CURSORS.remove(npcId);
            AMBIENT_READY.remove(npcId);
        }
    }

    /** Clears everything, for a server stop or a test. */
    public static void clearAll() {
        CURSORS.clear();
        AMBIENT_READY.clear();
    }
}

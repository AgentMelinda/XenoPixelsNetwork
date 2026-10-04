package net.bullettrain.xenopixelsmod.features.progression;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;

/**
 * What a quest asks the player to do.
 *
 * <p>Before this, {@code ProgressionEvents} switched on the quest's <em>id</em> to decide whether a
 * kill counted - {@code case "kill_mobs"}, {@code case "kill_players"}, and a {@code default} that
 * quietly counted any non-player death. So the three quests that existed were the only three that
 * could exist: adding a fourth meant editing the event handler, and any quest whose id was not in
 * the switch silently became "kill anything".
 *
 * <p>A quest now declares what it wants and the handlers ask. Every type here has a real hook
 * behind it - deliberately, because an objective that nothing advances is a quest that can never be
 * finished, which is worse than one that does not exist.
 */
public enum QuestObjective {

    /** Any hostile death that is not a player and not a training dummy. Counts one per kill. */
    KILL_MOBS,

    /** Player kills only. */
    KILL_PLAYERS,

    /**
     * Kills of one entity type, named by the goal's parameter (e.g. {@code minecraft:zombie}).
     *
     * <p>A parameter that names no registered type matches nothing, rather than falling through to
     * "anything" the way the old default branch did.
     */
    KILL_TYPE,

    /** Kills of a native Xeno NPC with the named display name. */
    KILL_NPC,

    /** Damage dealt to a training dummy, counted in points of damage. */
    DUMMY_DAMAGE,

    /** Hits landed on a training dummy, counted one per hit regardless of damage. */
    DUMMY_HITS,

    /**
     * Talking to a Xeno NPC, counted once per interaction.
     *
     * <p>A blank parameter counts any NPC; otherwise it is matched against the NPC's name and then
     * its role id, so both {@code Master Roshi} and {@code master} work.
     */
    TALK_TO_NPC,

    /** The player is carrying the named item. Taking it happens when the quest completes. */
    ITEM,

    /** The player selected the dialogue option named by the parameter. */
    DIALOG,

    /** The player reached the position stored on the step. */
    LOCATION,

    /** A kill counts only inside the step's radius of its position. */
    AREA_KILL,

    /** Progress moves only when an operator runs {@code /xenoquest progress}. */
    MANUAL;

    /** What a quest asks for: a type, and the thing it is about when the type needs one. */
    public record Goal(QuestObjective type, String parameter) {
        public Goal {
            type = type == null ? KILL_MOBS : type;
            parameter = parameter == null ? "" : parameter.trim().toLowerCase(Locale.ROOT);
        }

        public Goal(QuestObjective type) {
            this(type, "");
        }

        /** Whether a death advances this goal, and the kill counts as one step. */
        public boolean countsKill(Entity victim, boolean trainingDummy) {
            if (victim == null) {
                return false;
            }
            return switch (type) {
                case KILL_MOBS -> !(victim instanceof Player) && !trainingDummy;
                case KILL_PLAYERS -> victim instanceof Player;
                case KILL_TYPE -> !trainingDummy && matchesType(victim);
                case KILL_NPC -> QuestNpcTarget.matches(victim, parameter);
                default -> false;
            };
        }

        /** Whether the visible name of one native Xeno NPC is this goal's target. */
        public boolean matchesNpcName(String displayName) {
            return type == KILL_NPC && !parameter.isEmpty()
                    && parameter.equals(lower(displayName));
        }

        /**
         * How much one dummy hit advances this goal - damage, one, or nothing.
         *
         * <p>Rounded up to at least one for a damage goal, so a hit that lands always shows
         * progress. A hit worth zero progress reads as the quest being broken.
         */
        public int dummyProgress(float damage) {
            return switch (type) {
                case DUMMY_DAMAGE -> Math.max(1, Math.round(damage));
                case DUMMY_HITS -> 1;
                default -> 0;
            };
        }

        /** Whether talking to this NPC advances the goal. */
        public boolean countsTalk(String npcName, String roleId) {
            if (type != TALK_TO_NPC) {
                return false;
            }
            if (parameter.isEmpty()) {
                return true;
            }
            return parameter.equals(lower(npcName)) || parameter.equals(lower(roleId));
        }

        private boolean matchesType(Entity victim) {
            ResourceLocation wanted = ResourceLocation.tryParse(parameter);
            if (wanted == null) {
                return false;
            }
            return wanted.equals(BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()));
        }

        private static String lower(String value) {
            return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        }
    }

    /** Parses a type name. An unknown one is rejected rather than guessed at. */
    public static QuestObjective parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

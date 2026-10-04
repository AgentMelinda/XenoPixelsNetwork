package net.bullettrain.xenopixelsmod.features.progression;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a quest pays out on completion.
 *
 * <p>Before this, every quest paid the same hard-coded two skill points - {@code completeQuest} had
 * no idea which quest it was finishing. {@code doco.md} §7 lists experience, items, faction points
 * and commands, so this carries the three of those that have somewhere to go.
 *
 * <p>Faction points were deliberately absent while there was no faction system to receive them.
 * There is one now - {@code XenoFactions} loads the registry and {@code XenoPlayerData} stores a
 * standing per faction - so they are here, and a grant naming a faction no pack defines is reported
 * to the player rather than silently dropped.
 *
 * @param skillPoints XenoPixels skill points, the reward every quest has always paid
 * @param experience vanilla experience points, granted through {@code Player.giveExperiencePoints}
 * @param items item ids with counts; an id that does not resolve is skipped, never a crash
 * @param commands server commands run as the server, with {@code {player}} replaced by the name
 */
public record QuestReward(int skillPoints, int experience, List<ItemGrant> items,
                          List<String> commands, List<FactionGrant> factionPoints) {

    /** Standing to move with one faction on completion. Negative is a perfectly good reward. */
    public record FactionGrant(String factionId, int points) {
        public FactionGrant {
            factionId = factionId == null ? "" : factionId.trim().toLowerCase(Locale.ROOT);
        }
    }

    /** One stack to hand over. {@code id} is a registry id such as {@code minecraft:diamond}. */
    public record ItemGrant(String id, int count) {
        public ItemGrant {
            count = Math.max(1, count);
        }
    }

    /**
     * The reward every quest paid before rewards were configurable.
     *
     * <p>Kept as the default so a quest that does not declare one behaves exactly as it always has.
     * Changing what an existing quest pays is a content decision, not a side effect of adding the
     * ability to declare it.
     */
    public static final QuestReward DEFAULT =
            new QuestReward(2, 0, List.of(), List.of(), List.of());

    public QuestReward {
        skillPoints = Math.max(0, skillPoints);
        experience = Math.max(0, experience);
        items = List.copyOf(items == null ? List.of() : items);
        commands = List.copyOf(commands == null ? List.of() : commands);
        factionPoints = List.copyOf(factionPoints == null ? List.of() : factionPoints);
    }

    /** A reward with no faction movement, which is most of them. */
    public QuestReward(int skillPoints, int experience, List<ItemGrant> items,
                       List<String> commands) {
        this(skillPoints, experience, items, commands, List.of());
    }

    /** Skill points only, which is what most of the shipped quests want. */
    public static QuestReward ofSkillPoints(int skillPoints) {
        return new QuestReward(skillPoints, 0, List.of(), List.of(), List.of());
    }

    /**
     * Reads a reward out of a quest's JSON, or {@link #DEFAULT} when it declares none.
     *
     * <p>Lenient about shape and strict about nothing, on purpose: a reward is the last thing a
     * quest does, and refusing to load an otherwise good quest because one item id was mistyped
     * would cost the whole quest. Ids that do not resolve are reported to the player at grant time
     * instead - {@link #grant} already does that.
     *
     * <p>An absent {@code reward} block or an absent {@code skill_points} member means
     * {@link #DEFAULT}, the two points every quest paid before rewards were configurable. A quest
     * whose reward is switched off in the editor says so out loud with {@code "skill_points": 0};
     * the zero is the only way to ask for no points, and {@code QuestRewardOriginPolicy} is what
     * keeps a non-native quest from paying points it did not ask for.
     *
     * <pre>
     * "reward": {
     *   "skill_points": 2,
     *   "experience": 40,
     *   "items": [ { "id": "minecraft:diamond", "count": 2 } ],
     *   "faction_points": [ { "faction": "guards", "points": 25 } ],
     *   "commands": [ "say {player} is done" ]
     * }
     * </pre>
     */
    public static QuestReward fromJson(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return DEFAULT;
        }
        JsonObject root = element.getAsJsonObject();

        int skillPoints = root.has("skill_points") ? root.get("skill_points").getAsInt()
                : DEFAULT.skillPoints();
        int experience = root.has("experience") ? root.get("experience").getAsInt() : 0;

        List<ItemGrant> items = new ArrayList<>();
        for (JsonElement raw : array(root, "items")) {
            if (raw.isJsonObject()) {
                JsonObject obj = raw.getAsJsonObject();
                String id = obj.has("id") ? obj.get("id").getAsString() : "";
                if (!id.isBlank()) {
                    items.add(new ItemGrant(id, obj.has("count") ? obj.get("count").getAsInt() : 1));
                }
            }
        }

        List<FactionGrant> factions = new ArrayList<>();
        for (JsonElement raw : array(root, "faction_points")) {
            if (raw.isJsonObject()) {
                JsonObject obj = raw.getAsJsonObject();
                String id = obj.has("faction") ? obj.get("faction").getAsString() : "";
                if (!id.isBlank()) {
                    factions.add(new FactionGrant(id,
                            obj.has("points") ? obj.get("points").getAsInt() : 0));
                }
            }
        }

        List<String> commands = new ArrayList<>();
        for (JsonElement raw : array(root, "commands")) {
            String command = raw.isJsonPrimitive() ? raw.getAsString() : "";
            if (!command.isBlank()) {
                commands.add(command);
            }
        }

        return new QuestReward(skillPoints, experience, items, commands, factions);
    }

    private static JsonArray array(JsonObject root, String key) {
        return root.has(key) && root.get(key).isJsonArray()
                ? root.getAsJsonArray(key) : new JsonArray();
    }

    /** Whether there is anything at all to hand over. */
    public boolean isEmpty() {
        return skillPoints <= 0 && experience <= 0 && items.isEmpty() && commands.isEmpty()
                && factionPoints.isEmpty();
    }

    /**
     * A one-line summary for the completion message, e.g. {@code "+2 skill points, +50 xp"}.
     *
     * <p>Commands are counted rather than listed: a reward command is usually how a pack grants
     * something it has no other way to give, and pasting {@code /give @s ...} into chat tells the
     * player nothing useful about what they got.
     */
    public String describe() {
        List<String> parts = new ArrayList<>();
        if (skillPoints > 0) {
            parts.add("+" + skillPoints + " skill point" + (skillPoints == 1 ? "" : "s"));
        }
        if (experience > 0) {
            parts.add("+" + experience + " xp");
        }
        for (ItemGrant grant : items) {
            parts.add(grant.count() + "x " + shortName(grant.id()));
        }
        for (FactionGrant grant : factionPoints) {
            parts.add((grant.points() >= 0 ? "+" : "") + grant.points() + " "
                    + grant.factionId() + " standing");
        }
        if (!commands.isEmpty()) {
            parts.add(commands.size() + " reward action" + (commands.size() == 1 ? "" : "s"));
        }
        return parts.isEmpty() ? "nothing" : String.join(", ", parts);
    }

    /** Resolves an item id, or null when it names nothing. Lenient by design; see the record doc. */
    public static Item resolveItem(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        ResourceLocation key = ResourceLocation.tryParse(id.trim().toLowerCase(Locale.ROOT));
        if (key == null) {
            return null;
        }
        // getOptional, not get: the plain getter answers AIR for an unknown id, and silently
        // handing out air is worse than skipping the entry and saying so.
        return BuiltInRegistries.ITEM.getOptional(key).orElse(null);
    }

    /**
     * Pays this reward to {@code player}.
     *
     * <p>Items that do not fit are dropped rather than lost, the same fallback
     * {@code ProgressionCommands} already uses. An unresolvable item id is reported to the player
     * instead of failing the completion - the quest was still finished, and losing the rest of the
     * reward over one typo in a datapack would be worse.
     *
     * @return what was actually handed over, for the completion message
     */
    public String grant(ServerPlayer player) {
        return grant(player, null);
    }

    /** Pays the reward with command selectors anchored at the original quest giver when available. */
    public String grant(ServerPlayer player, ActiveQuest activeQuest) {
        if (player == null) {
            return "nothing";
        }
        if (experience > 0) {
            player.giveExperiencePoints(experience);
        }
        for (ItemGrant entry : items) {
            Item item = resolveItem(entry.id());
            if (item == null) {
                player.displayClientMessage(Component.literal(
                        "§7Quest reward item not found: §f" + entry.id()), false);
                continue;
            }
            ItemStack stack = new ItemStack(item, entry.count());
            if (!player.addItem(stack)) {
                player.drop(stack, false);
            }
        }
        for (FactionGrant grant : factionPoints) {
            // Through the data source: a faction an operator created in game is as real a target
            // for a quest reward as one a pack shipped.
            if (net.bullettrain.xenopixelsmod.npc.store.XenoNpcDataSource
                    .faction(grant.factionId()) == null) {
                player.displayClientMessage(Component.literal(
                        "§7Quest reward faction not found: §f" + grant.factionId()), false);
                continue;
            }
            net.bullettrain.xenopixelsmod.capability.XenoCapabilities.get(player)
                    .ifPresent(data -> data.addFactionStanding(grant.factionId(), grant.points()));
        }
        net.minecraft.commands.CommandSourceStack commandSource = player.createCommandSourceStack();
        if (activeQuest != null && activeQuest.giverPosition() != null) {
            net.minecraft.resources.ResourceLocation dimension = activeQuest.giverDimension();
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key =
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                            dimension);
            net.minecraft.server.level.ServerLevel giverLevel = player.server.getLevel(key);
            if (giverLevel != null) {
                commandSource = commandSource.withLevel(giverLevel)
                        .withPosition(activeQuest.giverPosition());
            }
        }
        for (String command : commands) {
            if (command == null || command.isBlank()) {
                continue;
            }
            // Run as the server, not as the player: a reward should not depend on what the player
            // is allowed to type. {player} is the one substitution, so a pack can target them.
            String resolved = QuestCommandTargets.resolve(command, player.getGameProfile().getName())
                    .replace("{player}", player.getGameProfile().getName());
            player.server.getCommands().performPrefixedCommand(commandSource, resolved);
        }
        return describe();
    }

    private static String shortName(String id) {
        int colon = id == null ? -1 : id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : String.valueOf(id);
    }
}

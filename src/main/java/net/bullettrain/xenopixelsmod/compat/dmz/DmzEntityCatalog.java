package net.bullettrain.xenopixelsmod.compat.dmz;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Every DragonMineZ NPC, grouped so a command can offer them without a hand-written list.
 *
 * <p>Read from {@link BuiltInRegistries#ENTITY_TYPE} rather than from a map of our own, so a
 * DragonMineZ update adds entities here on its own. DMZ registers 205 entity types; the fourteen
 * listed in {@link #NOT_AN_NPC} are projectiles and visual effects rather than NPCs, and spawning
 * one bare either does nothing or misfires, so they are left out.
 *
 * <p>The groups follow DMZ's own id prefixes: {@code master_} for the teachers, {@code saga_} for
 * the story fighters, and everything else as a plain mob. Quest NPCs are the exception and are not
 * a prefix at all - see {@link #QUEST_IDENTITIES}.
 */
public final class DmzEntityCatalog {

    /** DragonMineZ's namespace. Nothing outside it is offered. */
    public static final String NAMESPACE = "dragonminez";

    /** The one entity type that carries a quest-giver identity. */
    public static final ResourceLocation QUEST_NPC =
            ResourceLocation.fromNamespaceAndPath(NAMESPACE, "quest_npc");

    /**
     * Projectiles and effects. Registered entity types, but not NPCs.
     *
     * <p>Listed explicitly rather than filtered by {@code MobCategory.MISC}, because that category
     * also holds things worth summoning - the space pod and the eternal dragons among them.
     */
    private static final Set<String> NOT_AN_NPC = Set.of(
            "ki_area", "ki_barrier", "ki_blast", "ki_disc", "ki_explosion", "ki_explosion_visual",
            "ki_laser", "ki_wave", "majin_skill", "shadow_dummy",
            "sp_blue_hurricane", "sp_dragon_fist", "sp_majin_candy", "sp_ozaru_fist");

    /**
     * The quest-giver identities DragonMineZ ships, read out of its own sidequest JSONs
     * ({@code "quest_giver"} / {@code "turn_in"}).
     *
     * <p>These are not entity types. A quest NPC is one {@code dragonminez:quest_npc} whose
     * {@code QuestNpcId} says which character it is, which is why "summon Bulma" is a quest spawn
     * and not a saga spawn - {@code saga_bulma} exists but is a story fighter with no dialogue.
     */
    public static final List<String> QUEST_IDENTITIES = List.of(
            "bulma", "dende", "gohan", "goku", "guru", "kingkai", "krillin",
            "piccolo", "popo", "roshi", "trunks", "vegeta", "yamcha");

    /** How a summonable entity is grouped for tab completion. */
    public enum Group {
        /** {@code master_*} - the teachers, with DMZ's training menus. */
        MASTER("master_"),
        /** {@code saga_*} - the story fighters. */
        SAGA("saga_"),
        /** Everything else: traders, Red Ribbon, wildlife, the dragons, the space pod. */
        MOB("");

        private final String prefix;

        Group(String prefix) {
            this.prefix = prefix;
        }

        /** The id prefix DMZ uses, or "" for the catch-all. */
        public String prefix() {
            return prefix;
        }
    }

    private DmzEntityCatalog() {
    }

    /**
     * Short ids in {@code group}, ascending - the part after the prefix, so a player types
     * {@code beerus} rather than {@code master_beerus}.
     */
    public static List<String> ids(Group group) {
        List<String> out = new ArrayList<>();
        if (group == null) {
            return out;
        }
        for (ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            if (!NAMESPACE.equals(id.getNamespace())) {
                continue;
            }
            String path = id.getPath();
            if (NOT_AN_NPC.contains(path) || groupOf(path) != group) {
                continue;
            }
            out.add(path.substring(group.prefix().length()));
        }
        out.sort(String::compareTo);
        return out;
    }

    /** Which group a DMZ entity path belongs to. */
    private static Group groupOf(String path) {
        if (path.startsWith(Group.MASTER.prefix())) {
            return Group.MASTER;
        }
        if (path.startsWith(Group.SAGA.prefix())) {
            return Group.SAGA;
        }
        return Group.MOB;
    }

    /**
     * The registered entity type for a short id in {@code group}, or null when there is none.
     *
     * <p>Accepts the long form too ({@code master_beerus} as well as {@code beerus}), because the
     * existing {@code /xenostructure summon <master>} form has always taken the long spelling and
     * scripts may pass either.
     */
    public static EntityType<?> type(Group group, String shortId) {
        if (group == null || shortId == null || shortId.isBlank()) {
            return null;
        }
        String key = shortId.trim().toLowerCase(Locale.ROOT);
        if (key.startsWith(NAMESPACE + ":")) {
            key = key.substring(NAMESPACE.length() + 1);
        }
        String path = key.startsWith(group.prefix()) ? key : group.prefix() + key;
        if (NOT_AN_NPC.contains(path) || groupOf(path) != group) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(NAMESPACE + ":" + path);
        if (id == null) {
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
    }

    /** Whether {@code identity} is one DragonMineZ actually ships quests for. */
    public static boolean isQuestIdentity(String identity) {
        return identity != null
                && QUEST_IDENTITIES.contains(identity.trim().toLowerCase(Locale.ROOT));
    }

    /** The quest NPC entity type, or null when DragonMineZ is not loaded. */
    public static EntityType<?> questNpcType() {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(QUEST_NPC).orElse(null);
    }
}

package net.bullettrain.xenopixelsmod.npc.store;

import java.util.Locale;

/**
 * The kinds of thing the world store holds, and the folder each lives in.
 *
 * <p>The folder names mirror My NPCs' own world layout, so that migrating a server later is a
 * matter of renaming keys inside files rather than working out which file is which. Ours sit under
 * {@code <world>/XenoNpcs/} where theirs sit under {@code <world>/mynpcs/}.
 *
 * <p>One deliberate divergence: My NPCs keeps factions, banks, transport, recipes and spawns as a
 * single gzipped {@code .dat} blob each, and only dialogs, quests, clones, linked NPCs and player
 * data as folders of files. Every category here is a folder of one file per entry. A per-entry file
 * can be diffed, hand-edited, backed up and — the part that matters — <em>fail alone</em>. One
 * corrupt byte in a combined blob takes every faction with it. Importing one of their blobs is
 * therefore a fan-out rather than a copy, which is a cost paid once by the importer instead of
 * every day by the operator.
 *
 * <p>{@code grouped} categories nest one folder deeper — {@code dialogs/<category>/<id>.json} —
 * exactly as theirs do. Ungrouped ones are flat: {@code factions/<id>.json}.
 */
public enum XenoNpcStoreCategory {

    /**
     * Saved NPC templates. Grouped by tab, as My NPCs groups clones by a numbered tab folder.
     *
     * <p><b>Live.</b> Read and written by {@code XenoNpcClones} and spent by {@code /xenoclone
     * place} and the Cloner item - which is exactly what the reservation here asked for: "a path
     * that applies a stored template to a live or newly placed NPC". A stored clone is the same
     * payload the Cloner carries and the editor opens with, so the library and the item cannot
     * disagree about what an NPC is.
     */
    CLONES("clones", true),

    /** Conversations. Grouped by category, as theirs are. */
    DIALOGS("dialogs", true),

    /**
     * Quests. Grouped by category, as theirs are.
     *
     * <p><b>Live.</b> {@code XenoQuests} resolves world-store definitions before datapack
     * definitions, so imported and operator-authored quests use the same objective runtime as pack
     * quests.
     */
    QUESTS("quests", true),

    /** Factions. Theirs is one {@code factions.dat}; ours is a file per faction. */
    FACTIONS("factions", false),

    /**
     * Banks - a named set of vault tabs and their costs, shared by the NPCs that tell for it.
     *
     * <p><b>Live.</b> Read and written by {@code Banks}. This was reserved with "unblocked by the
     * Bank role, which does not exist"; the role landed on 2026-09-23 and this is that reader.
     *
     * <p>Holds only what the bank <em>offers</em>. What a player <em>has</em> in a vault is
     * per-player and lives on {@code XenoPlayerData}, which is why {@link #PLAYERDATA} below stays
     * permanently empty rather than becoming this category's other half.
     */
    BANKS("banks", false),

    /**
     * Transport networks — a named set of destinations that transporter NPCs share.
     *
     * <p><b>Live.</b> Read and written by {@code TransportNetworks}. Destinations were copied into
     * each transporter's own NBT until 2026-09-23; a network is shared by its nature, so renaming
     * a destination meant editing every NPC that listed it. The NPC now holds only the network id.
     */
    TRANSPORT("transport", false),

    /**
     * Linked NPC templates. A folder in theirs too.
     *
     * <p><b>Reserved, no reader.</b> {@code LinkedNpcs} is a per-NPC list today, propagated by
     * {@code XenoNpcLinkPropagation}. Unblocked by wanting a link group to outlive the NPCs in it
     * — which is the moment it stops being a fact about one NPC and becomes shared content.
     */
    LINKED("linked", false),

    /**
     * Natural spawn entries. Theirs is one {@code spawns.dat}.
     *
     * <p><b>Live.</b> Read by {@code NpcNaturalSpawns}, spent by {@code NpcNaturalSpawnService},
     * which places a stored clone into the world by biome, time-of-day and weight. This is the
     * reader the reservation asked for ("a spawner that reads them"); the entry format is ours, not
     * theirs, so importing their {@code spawns.dat} remains open work.
     */
    SPAWNS("spawns", false),

    /**
     * Carpentry-style recipes. Theirs is one {@code recipes.dat}.
     *
     * <p><b>Reserved, no reader.</b> Unblocked by the carpentry bench, which does not exist.
     */
    RECIPES("recipes", false),

    /**
     * Per-player records.
     *
     * <p><b>Reserved permanently empty — unlike every other reservation here, nothing unblocks
     * this one.</b> Per-player state lives on {@code XenoPlayerData}: faction standing, quest
     * progress, skill points and unlocked transport destinations are all there, and it already
     * persists and already syncs. Writing them here as well would give one fact two writers and no
     * rule for which wins, which is worse than one writer.
     *
     * <p>The folder name is claimed anyway, so an importer reading their {@code playerdata/} has
     * somewhere obvious to land it before it is folded into the capability.
     */
    PLAYERDATA("playerdata", false),

    /** Shared, operator-authored speech and animation timelines. Appended to preserve wire ids. */
    SCENES("scenes", false),

    /**
     * Operator-authored NPC scripts. Appended to preserve wire ids.
     *
     * <p><b>Live.</b> Read and written by {@code XenoNpcScripts}; {@code XenoScriptRunner} is the
     * runtime consumer that spends an entry. My NPCs keeps the same text inside
     * {@code world_data.json} and its per-NPC {@code scripts} lists; ours is one file per script
     * because a script is content an operator reviews, diffs and hand-edits, and an NPC holds only
     * the id. The category is ungrouped, so one id names one script everywhere.
     *
     * <p>Stored text is evaluated by bundled Nashorn only while bound to an enabled NPC.
     */
    SCRIPTS("scripts", false),

    /** Global player script tabs. Appended to preserve every existing category wire id. */
    PLAYER_SCRIPTS("player_scripts", false),

    /** Forge (world event) script tabs, run by ForgeScriptHost. Appended for the same reason. */
    FORGE_SCRIPTS("forge_scripts", false);

    private final String folder;
    private final boolean grouped;

    XenoNpcStoreCategory(String folder, boolean grouped) {
        this.folder = folder;
        this.grouped = grouped;
    }

    /** The directory name under {@code <world>/XenoNpcs/}. */
    public String folder() {
        return folder;
    }

    /** Whether entries sit one folder deeper, under a group name. */
    public boolean grouped() {
        return grouped;
    }

    /** Whether anything currently reads this category at runtime. */
    public boolean hasRuntimeConsumer() {
        return this == DIALOGS || this == QUESTS || this == FACTIONS || this == BANKS || this == TRANSPORT
                || this == SCENES || this == SPAWNS || this == SCRIPTS || this == PLAYER_SCRIPTS
                || this == FORGE_SCRIPTS;
    }

    /** Resolves a folder name, or null when it names no category. */
    public static XenoNpcStoreCategory byFolder(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        for (XenoNpcStoreCategory category : values()) {
            if (category.folder.equals(wanted)) {
                return category;
            }
        }
        return null;
    }

    /**
     * Resolves a wire ordinal, or null when out of range.
     *
     * <p>Ordinals travel on the network, so this is a bounds check and not a convenience: a crafted
     * packet must not index past the end of the array.
     */
    public static XenoNpcStoreCategory byOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
    }
}

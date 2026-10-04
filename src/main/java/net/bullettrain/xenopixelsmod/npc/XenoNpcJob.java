package net.bullettrain.xenopixelsmod.npc;

import java.util.List;
import java.util.Locale;

/**
 * What an NPC does when nobody is talking to it — My NPCs' second axis.
 *
 * <p>A job is <b>independent of the role</b>. Their editor lets one NPC be a Trader and a Bard at
 * once, and nothing about the two interferes: the role answers "what happens when I click it", the
 * job answers "what is it doing while I don't".
 *
 * <p>The order below is their Advanced page's exact visible order, so an operator moving between
 * the two editors finds the same list in the same place.
 *
 * <p>Keyed by id string and never by ordinal. Inserting a job into the middle of this list must not
 * silently reclassify every saved NPC after it — the mistake {@code XenoNpcRole} already avoids for
 * the same reason.
 */
public enum XenoNpcJob {

    /**
     * No job.
     *
     * <p>Its id is the empty string deliberately: a profile whose job is NONE writes no job tag at
     * all, so an ordinary NPC's NBT is byte-identical to what it was before jobs existed.
     */
    NONE("", "No Job"),

    /** Plays a sound to players within range. <b>Implemented.</b> */
    BARD("bard", "Bard"),

    /** Applies configured potion effects to nearby living entities. <b>Implemented.</b> */
    HEALER("healer", "Healer"),

    /**
     * Attacks hostiles near its post, and patrols a route when it has one.
     *
     * <p><b>Implemented.</b> Distinct from the GUARD <em>role</em>, which already fights: as a job
     * it sits on any role, so a Trader can mind its stall and still object to zombies. That is the
     * point of the axis.
     */
    GUARD("guard", "Guard"),

    /** Hands authored items to nearby players with per-player cooldown state. <b>Implemented.</b> */
    ITEM_GIVER("item_giver", "Item Giver"),

    /** Follows another nearby NPC by its visible name. <b>Implemented.</b> */
    FOLLOWER("follower", "Follower"),

    /** Spawns mobs on a timer. <b>Reserved</b> — unblocked by a spawn budget nothing has yet. */
    SPAWNER("spawner", "Spawner"),

    /** Two NPCs talk to each other. <b>Reserved</b> — unblocked by NPC-to-NPC line addressing. */
    CONVERSATION("conversation", "Conversation"),

    /** Keeps its chunk loaded. <b>Reserved</b> — unblocked by an owner decision on ticket cost. */
    CHUNK_LOADER("chunk_loader", "Chunk Loader"),

    /** Mirrors another NPC's pose. <b>Reserved</b> — unblocked by a pose channel between NPCs. */
    PUPPET("puppet", "Puppet"),

    /** Builds a stored schematic. <b>Reserved</b> — unblocked by a schematic format. */
    BUILDER("builder", "Builder"),

    /** Tends crops nearby. <b>Reserved</b> — unblocked by a work area, which the Pather defines. */
    FARMER("farmer", "Farmer");

    private final String id;
    private final String label;

    XenoNpcJob(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    /** What the editor shows. Their wording, so the two editors read alike. */
    public String label() {
        return label;
    }

    /** Whether anything actually runs this job today. */
    public boolean implemented() {
        return this == NONE || this == BARD || this == HEALER || this == GUARD
                || this == FOLLOWER || this == ITEM_GIVER;
    }

    /**
     * The job named by an id, or {@link #NONE}.
     *
     * <p>An unknown id folds to NONE rather than throwing: a world saved with a job that a later
     * build removed must still load, as an NPC that simply does nothing.
     */
    public static XenoNpcJob byId(String raw) {
        if (raw != null) {
            String id = raw.trim().toLowerCase(Locale.ROOT);
            for (XenoNpcJob job : values()) {
                if (job.id.equals(id)) {
                    return job;
                }
            }
        }
        return NONE;
    }

    /** Labels in their visible order, for the editor's cycle. */
    public static List<String> labels() {
        return java.util.Arrays.stream(values()).map(XenoNpcJob::label).toList();
    }

    public XenoNpcJob next() {
        XenoNpcJob[] jobs = values();
        return jobs[(ordinal() + 1) % jobs.length];
    }
}

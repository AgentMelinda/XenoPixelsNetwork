package net.bullettrain.xenopixelsmod.npc;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatBrainVersion;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.UUID;

public final class XenoNpcData {
    public static final int SCHEMA_VERSION = 2;

    /**
     * The identity tag's own version, kept apart from the combat profile's.
     *
     * <p>Both used to be spelled {@code Schema}. Nested in separate compounds that was harmless;
     * flat at the entity root they are siblings, and each would have read the other's number - the
     * profile's is currently 17 against this one's 2, so the profile would have re-run its whole
     * migration chain against a tag that had already been migrated.
     */
    public static final String TAG_SCHEMA = "NpcSchema";

    /**
     * Where the identity tag used to live.
     *
     * <p>Kept so a world saved before the tag was flattened still loads. See
     * {@link #unwrapLegacy(CompoundTag)}.
     */
    public static final String LEGACY_ROOT = "XenoNpcData";

    private XenoNpcRole role;
    private NpcCombatBrainVersion brainVersion = NpcCombatBrainVersion.V5;
    private String displayName = "Xeno NPC";
    /** Shown under the name, the way the reference menu has it. Separate from {@link #faction}. */
    private String title = "";
    private String faction = "";
    private UUID owner;
    private String sourceMod = "";
    private UUID sourceUuid;
    private int revision = 1;

    /**
     * Where this NPC was created, and how far it may wander from there.
     *
     * <p>CustomNPCs and MyNPCs both anchor an NPC to its spawn point: it respawns there rather than
     * where it died, and it returns if it strays too far. Without a home an NPC that chased
     * something across the world stayed there, and respawned at the place it was killed.
     *
     * <p>{@code homeSet} distinguishes "no home recorded" from a legitimate home at the origin.
     */
    private boolean homeSet;
    private double homeX;
    private double homeY;
    private double homeZ;

    /** Blocks from home before the NPC is returned. Zero disables the leash. */
    private double leashRadius = DEFAULT_LEASH_RADIUS;

    /**
     * Whether this NPC comes back after dying.
     *
     * <p>Defaults to true because every NPC respawned before this field existed; defaulting to
     * false would silently stop every NPC in every existing world from returning.
     */
    private boolean respawnEnabled = true;

    /** Ticks between death and return. The old hardcoded value in {@code XenoNpcEntity.die}. */
    private int respawnDelayTicks = DEFAULT_RESPAWN_DELAY_TICKS;

    /** Roomy enough to fight in, tight enough that an NPC stays where it was put. */
    public static final double DEFAULT_LEASH_RADIUS = 32.0;

    /**
     * How far from the origin a home may sit.
     *
     * <p>The vanilla world border caps here. A home beyond it names a position the server cannot
     * load, so a respawn scheduled there would never fire and the NPC would be gone for good.
     */
    public static final double MAX_HOME_COORDINATE = 30_000_000.0;

    /** What {@code XenoNpcEntity.die} used before the delay was configurable. */
    public static final int DEFAULT_RESPAWN_DELAY_TICKS = 100;

    /** One in-game hour. Longer is indistinguishable from "never" and should use the toggle. */
    public static final int MAX_RESPAWN_DELAY_TICKS = 72_000;

    public XenoNpcData(XenoNpcRole role) {
        this.role = role == null ? XenoNpcRole.HUMANOID : role;
    }

    public XenoNpcRole role() { return role; }
    public void setRole(XenoNpcRole value) {
        XenoNpcRole next = value == null ? XenoNpcRole.HUMANOID : value;
        if (role != next) {
            role = next;
            touch();
        }
    }
    /**
     * Always {@link NpcCombatBrainVersion#V5}.
     *
     * <p>Native entities run the V5 brain and nothing selects another; {@code docs/xeno-npcs-v5.md}
     * records that. Kept as an accessor rather than inlined so the day a second version exists
     * there is one place to change, but it is no longer persisted - see {@link #toTag()}.
     */
    public NpcCombatBrainVersion brainVersion() { return brainVersion; }
    /** The name as text - colour codes removed - for chat, bubbles and lists. */
    public String displayName() {
        String plain = XenoNpcNameFormat.plain(displayName).trim();
        return plain.isEmpty() ? "Xeno NPC" : plain;
    }
    /** The stored name with its colour codes, for the nameplate and the editor. */
    public String rawDisplayName() { return displayName; }
    /** Applies the entity's vanilla synced custom name to the client-side NPC view without saving. */
    void syncVisibleName(String value) {
        if (value == null || value.isBlank()) return;
        String normalized = value.trim();
        displayName = normalized.substring(0, Math.min(64, normalized.length()));
    }
    /** The title as text, colour codes removed. */
    public String title() { return XenoNpcNameFormat.plain(rawTitle()).trim(); }
    /** The stored title with its colour codes, for the nameplate and the editor. */
    public String rawTitle() { return title == null ? "" : title; }
    public String faction() { return faction; }
    public @Nullable UUID owner() { return owner; }
    public String sourceMod() { return sourceMod; }
    public @Nullable UUID sourceUuid() { return sourceUuid; }
    public int revision() { return revision; }
    public boolean hasHome() { return homeSet; }
    public double homeX() { return homeX; }
    public double homeY() { return homeY; }
    public double homeZ() { return homeZ; }
    public double leashRadius() { return leashRadius; }
    public boolean respawnEnabled() { return respawnEnabled; }
    public int respawnDelayTicks() { return respawnDelayTicks; }

    /**
     * Records where this NPC belongs, and therefore where it respawns.
     *
     * <p>No longer only called at creation: the editor can move it, so the values arrive from a
     * save packet and are validated like any other packet field. A non-finite coordinate clears
     * the home rather than storing a NaN that would make every leash comparison false, and a
     * coordinate past the world border is clamped to it.
     */
    public void setHome(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            clearHome();
            return;
        }
        homeSet = true;
        homeX = clampCoordinate(x);
        homeY = clampCoordinate(y);
        homeZ = clampCoordinate(z);
        touch();
    }

    /** Forgets the home, so the NPC falls back to respawning where it fell. */
    public void clearHome() {
        homeSet = false;
        homeX = 0.0;
        homeY = 0.0;
        homeZ = 0.0;
        touch();
    }

    private static double clampCoordinate(double value) {
        return Math.max(-MAX_HOME_COORDINATE, Math.min(MAX_HOME_COORDINATE, value));
    }

    public void setRespawnEnabled(boolean enabled) {
        respawnEnabled = enabled;
        touch();
    }

    /** Clamped: zero would schedule a return inside the death tick, and huge values never fire. */
    public void setRespawnDelayTicks(int ticks) {
        respawnDelayTicks = Math.max(1, Math.min(MAX_RESPAWN_DELAY_TICKS, ticks));
        touch();
    }

    public void setLeashRadius(double radius) {
        leashRadius = Double.isFinite(radius) ? Math.max(0.0, Math.min(512.0, radius)) : 0.0;
        touch();
    }

    public void setDisplayName(String value) {
        String next = value == null || value.isBlank() ? "Xeno NPC" : value.trim();
        displayName = next.substring(0, Math.min(64, next.length()));
        touch();
    }
    public void setTitle(String value) {
        String next = value == null ? "" : value.trim();
        title = next.substring(0, Math.min(64, next.length()));
        touch();
    }
    public void setFaction(String value) {
        String next = value == null ? "" : value.trim();
        faction = next.substring(0, Math.min(64, next.length()));
        touch();
    }

    public boolean applyEditorIdentity(String name, String nextTitle, String nextFaction) {
        String normalizedName = name == null || name.isBlank() ? "Xeno NPC" : name.trim();
        normalizedName = normalizedName.substring(0, Math.min(64, normalizedName.length()));
        String normalizedTitle = nextTitle == null ? "" : nextTitle.trim();
        normalizedTitle = normalizedTitle.substring(0, Math.min(64, normalizedTitle.length()));
        String normalizedFaction = nextFaction == null ? "" : nextFaction.trim();
        normalizedFaction = normalizedFaction.substring(0, Math.min(64, normalizedFaction.length()));

        boolean changed = !displayName.equals(normalizedName)
                || !rawTitle().equals(normalizedTitle)
                || !faction.equals(normalizedFaction);
        displayName = normalizedName;
        title = normalizedTitle;
        faction = normalizedFaction;
        // Deliberately does not touch(). The editor save applies identity, data and profile
        // together and then calls markEdited() once, so a save is one revision rather than three -
        // XenoNpcDataTest.editorIdentityMutationBumpsTheRevisionExactlyOnce holds that contract.
        return changed;
    }

    public void markEdited() {
        touch();
    }
    public void setOwner(@Nullable UUID value) { owner = value; touch(); }
    public void setImportSource(String modId, @Nullable UUID uuid) {
        String next = modId == null ? "" : modId.trim();
        sourceMod = next.substring(0, Math.min(32, next.length()));
        sourceUuid = uuid;
        touch();
    }

    /**
     * The payload the NPC editor is opened with.
     *
     * <p>{@link #toTag()} is this NPC's own save data and deliberately does not include the combat
     * profile, which lives in the entity's persistent data under its own key. The editor needs
     * both, and for a long time it asked for {@code "Profile"} on a tag that never carried one -
     * {@code getCompound} answers a missing key with an empty tag, so the editor silently rebuilt a
     * default profile on every open and every field read back as its default. That looked exactly
     * like saving being broken.
     *
     * <p>Both places that open the editor build their payload here so they cannot drift apart
     * again.
     */
    public static CompoundTag editorPayload(net.minecraft.world.entity.Entity npc,
                                            XenoNpcData data) {
        CompoundTag payload = data.toTag();
        payload.put("Profile",
                net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.read(npc).toTag());
        // Script errors and prints, so the editor's Scripts screen shows them like the tool does.
        if (npc instanceof XenoNpcEntity xeno) payload.put("ScriptConsole", xeno.scriptConsoleTag());
        return payload;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_SCHEMA, SCHEMA_VERSION);
        tag.putString("Role", role.id());
        // No brain key here. It used to write "Brain", which flattening exposed twice over: the
        // name collides with LivingEntity's brain-memory compound, and the obvious rename to
        // "BrainVersion" collides with NpcCombatProfile's - which is the live one, the one the
        // editor writes and the save whitelist knows. This one was written as a constant and read
        // as a constant: fromTag assigned V5 unconditionally and never looked at the stored value,
        // so it was a stored-but-unread field wearing the same name as a real one. Dropped rather
        // than renamed a second time.
        tag.putString("Name", displayName);
        tag.putString("Title", title == null ? "" : title);
        tag.putString("Faction", faction);
        tag.putInt("Revision", revision);
        if (owner != null) tag.putUUID("Owner", owner);
        if (!sourceMod.isBlank()) tag.putString("SourceMod", sourceMod);
        if (sourceUuid != null) tag.putUUID("SourceUuid", sourceUuid);
        if (homeSet) {
            tag.putDouble("HomeX", homeX);
            tag.putDouble("HomeY", homeY);
            tag.putDouble("HomeZ", homeZ);
        }
        tag.putDouble("LeashRadius", leashRadius);
        tag.putBoolean("RespawnEnabled", respawnEnabled);
        tag.putInt("RespawnDelayTicks", respawnDelayTicks);
        return tag;
    }

    public void restoreFromTag(CompoundTag tag) {
        XenoNpcData restored = fromTag(tag, role);
        role = restored.role;
        brainVersion = restored.brainVersion;
        displayName = restored.displayName;
        faction = restored.faction;
        owner = restored.owner;
        sourceMod = restored.sourceMod;
        sourceUuid = restored.sourceUuid;
        revision = restored.revision;
        homeSet = restored.homeSet;
        homeX = restored.homeX;
        homeY = restored.homeY;
        homeZ = restored.homeZ;
        leashRadius = restored.leashRadius;
        respawnEnabled = restored.respawnEnabled;
        respawnDelayTicks = restored.respawnDelayTicks;
    }

    /**
     * Lifts a pre-flatten tag up to the root, or returns it unchanged.
     *
     * <p>A world saved before the flattening has its identity under {@code XenoNpcData}. Reading it
     * from the root would find nothing and quietly hand back a default NPC - the name, faction,
     * home and leash all reset. So the old shape is recognised and unwrapped; the next save writes
     * it flat and the old key is gone for good.
     *
     * <p>Recognised by the nested compound's presence rather than by a version number, because a
     * version-1 tag kept its number <em>inside</em> that compound where a root read cannot see it.
     */
    public static CompoundTag unwrapLegacy(CompoundTag tag) {
        if (tag == null || !tag.contains(LEGACY_ROOT, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            return tag;
        }
        CompoundTag nested = tag.getCompound(LEGACY_ROOT);
        CompoundTag lifted = new CompoundTag();
        for (String key : nested.getAllKeys()) {
            // "Schema" was the version under the old spelling. "Brain" is dropped: it only ever
            // held the constant V5 and its name is taken twice over at the root.
            if (key.equals("Brain")) {
                continue;
            }
            String moved = key.equals("Schema") ? TAG_SCHEMA : key;
            lifted.put(moved, nested.get(key).copy());
        }
        return lifted;
    }

    public static XenoNpcData fromTag(CompoundTag raw, XenoNpcRole fallback) {
        CompoundTag tag = unwrapLegacy(raw);
        XenoNpcRole storedRole = tag == null || !tag.contains("Role")
                ? fallback
                : XenoNpcRole.byId(tag.getString("Role"));
        XenoNpcData data = new XenoNpcData(storedRole);
        if (tag == null) return data;
        data.brainVersion = NpcCombatBrainVersion.V5;
        if (tag.contains("Name")) {
            String name = tag.getString("Name").trim();
            if (!name.isEmpty()) data.displayName = name.substring(0, Math.min(64, name.length()));
        }
        if (tag.contains("Title")) {
            data.title = tag.getString("Title").trim();
            if (data.title.length() > 64) data.title = data.title.substring(0, 64);
        }
        if (tag.contains("Faction")) {
            data.faction = tag.getString("Faction").trim();
            if (data.faction.length() > 64) data.faction = data.faction.substring(0, 64);
        }
        if (tag.hasUUID("Owner")) data.owner = tag.getUUID("Owner");
        if (tag.contains("SourceMod")) data.sourceMod = tag.getString("SourceMod");
        if (tag.hasUUID("SourceUuid")) data.sourceUuid = tag.getUUID("SourceUuid");
        if (tag.contains("HomeX") && tag.contains("HomeY") && tag.contains("HomeZ")) {
            data.homeSet = true;
            data.homeX = tag.getDouble("HomeX");
            data.homeY = tag.getDouble("HomeY");
            data.homeZ = tag.getDouble("HomeZ");
        }
        // A tag written before the leash existed keeps the default rather than reading as zero,
        // which would silently disable the leash for every NPC already in a world.
        data.leashRadius = tag.contains("LeashRadius")
                ? tag.getDouble("LeashRadius") : DEFAULT_LEASH_RADIUS;
        // Both absent keys keep the pre-field behaviour: every NPC respawned, after 100 ticks.
        data.respawnEnabled = !tag.contains("RespawnEnabled") || tag.getBoolean("RespawnEnabled");
        data.respawnDelayTicks = tag.contains("RespawnDelayTicks")
                ? Math.max(1, Math.min(MAX_RESPAWN_DELAY_TICKS, tag.getInt("RespawnDelayTicks")))
                : DEFAULT_RESPAWN_DELAY_TICKS;
        data.revision = Math.max(1, tag.getInt("Revision"));
        return data;
    }

    private void touch() { revision = revision == Integer.MAX_VALUE ? 1 : revision + 1; }
}

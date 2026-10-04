package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.character.Resources;
import com.dragonminez.common.stats.character.Stats;
import com.dragonminez.compat.util.LazyOptional;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * A real DragonMineZ {@link StatsData} carried by a CustomNPC / MyNPC entity.
 *
 * <p>DMZ stores a player's stats in its own {@code PLAYER_STATS} attachment, whose factory casts
 * the holder to {@link Player}; {@code StatsProvider.get} likewise returns empty for anything that
 * is not a player. So an NPC cannot join that attachment, and this is a parallel one holding a
 * {@code StatsData} of its own. {@code StatsProviderNpcGetMixin} is what makes DMZ's own lookups
 * find it, so code that already calls {@code StatsProvider.get(entity)} starts working on NPCs
 * without being rewritten.
 *
 * <h2>Why there is no player behind it</h2>
 *
 * <p>The blob is built with {@code new StatsData(null)}. That is not a trick: every accessor DMZ
 * needs for stats, resources, forms, skills and techniques already null-checks its player, and
 * {@code save()} and {@code load()} never touch it at all. Reading the decompiled 2.1.3 source,
 * exactly these methods dereference the player without a guard, and they are the ones an NPC must
 * never be asked for:
 *
 * <ul>
 *   <li>{@code getDefense()} / {@code getMaxDefense()} — worn armour value</li>
 *   <li>{@code getHealthRegenPerSecond()} / {@code getEnergyRegenPerSecond()} — armour enchantments</li>
 *   <li>the gravity and Hyperbolic Time Chamber family: {@code getGravity*}, {@code getTp*},
 *       {@code getLoadDrainMultiplier()}</li>
 *   <li>{@code getPlayer()}</li>
 * </ul>
 *
 * <p>{@link #safeForNpc} names that list in code so the boundary is checkable rather than a comment
 * someone has to remember. The alternative the plan sketched — a world {@code FakePlayer}, or
 * mixins widening {@code Stats.setPlayer} to take a {@code LivingEntity} — is both larger and
 * riskier: the field is declared {@code Player} and a {@code FakePlayer} in the world is the very
 * thing that produces the AttributeMap warnings this work is trying to stop chasing.
 *
 * <p>This means NPC stats are real DMZ numbers computed by DMZ's own formulas, but an NPC is not a
 * logged-in player: it has no quests, no gravity training and no armour contribution. That is the
 * first-pass boundary the plan set, made explicit.
 */
public final class NpcDmzStats {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, XenoPixelsMod.MOD_ID);

    /**
     * The attachment itself.
     *
     * <p>Not {@code copyOnDeath}: NPCs are respawned from their profile, and a stale blob surviving
     * a death would outrank the profile the spawner just applied.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<NpcStatsAttachment>> NPC_DMZ_STATS =
            ATTACHMENTS.register("npc_dmz_stats",
                    () -> AttachmentType.serializable(holder -> new NpcStatsAttachment()).build());

    private NpcDmzStats() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    /**
     * Methods of {@link StatsData} that need a real player and must not be called on an NPC blob.
     *
     * <p>Exposed so tests can pin the list against the decompiled source: if a DMZ update adds a
     * guard, or removes one, the list is wrong in a way nothing else would notice until an NPC
     * threw a {@link NullPointerException} in combat.
     */
    public static boolean safeForNpc(String methodName) {
        return switch (methodName) {
            case "getDefense", "getMaxDefense", "getHealthRegenPerSecond", "getEnergyRegenPerSecond",
                    "getGravityEnvironmentalMultiplier", "getGravityPenalizationGravity",
                    "getGravityStatMultiplier", "getGravityTotalWeight", "getLoadDrainMultiplier",
                    "getTpGravityMultiplier", "getTpHTCMultiplier", "getTpIdealWeight",
                    "getTpWeightBellMultiplier", "getPlayer" -> false;
            default -> true;
        };
    }

    /**
     * The NPC's stats blob, or {@code null} when it has none.
     *
     * <p>Never creates one. A read has to be able to say "this entity has no DMZ stats" — that is
     * what lets {@code NpcKiAttackDispatcher} fall back to the profile formula instead of silently
     * attaching an empty blob to every mob it fires at and reading zeroes out of it.
     */
    public static StatsData stats(Entity entity) {
        if (!eligible(entity)) return null;
        if (!entity.hasData(NPC_DMZ_STATS.get())) return null;
        return entity.getData(NPC_DMZ_STATS.get()).data;
    }

    /** Player-parity DMZ melee output, with the profile formula reserved for unbound NPCs. */
    static double meleeDamage(LivingEntity npc, NpcCombatProfile profile) {
        StatsData data = stats(npc);
        return data == null ? profile == null ? 1.0 : profile.meleeDamage()
                : safeDamage(data.getMeleeDamage(), profile == null ? 1.0 : profile.meleeDamage());
    }

    /** Player-parity DMZ strike output, with the profile formula reserved for unbound NPCs. */
    static double strikeDamage(LivingEntity npc, NpcCombatProfile profile) {
        StatsData data = stats(npc);
        return data == null ? profile == null ? 1.0 : profile.strikeDamage()
                : safeDamage(data.getStrikeDamage(), profile == null ? 1.0 : profile.strikeDamage());
    }

    /** The exact bonus DMZ applies above a player's 20-point base-health attribute. */
    static float healthBonus(LivingEntity npc, NpcCombatProfile profile) {
        StatsData data = stats(npc);
        if (data != null) {
            float bonus = data.getHealthBonus();
            if (Float.isFinite(bonus) && bonus > 0.0f) return bonus;
            return 0.0f;
        }
        if (profile == null) return 0.0f;
        return NpcVitalityMath.healthBonus(profile.vitality,
                NpcVitalitySync.vitalityMultiplier(profile),
                NpcVitalitySync.vitalityScaling(profile));
    }

    private static double safeDamage(double calculated, double fallback) {
        return Double.isFinite(calculated) && calculated >= 0.0
                ? Math.min(calculated, Float.MAX_VALUE) : fallback;
    }

    /** {@link #stats} in the shape DMZ's own capability lookups return. */
    public static LazyOptional<StatsData> optional(Entity entity) {
        StatsData data = stats(entity);
        return data == null ? LazyOptional.empty() : LazyOptional.of(() -> data);
    }

    /** True when this NPC already carries a blob. */
    public static boolean has(Entity entity) {
        return stats(entity) != null;
    }

    /**
     * The NPC's stats blob, creating an empty one if this is the first time.
     *
     * <p>Only ever called from the profile apply path. Everything else reads through
     * {@link #stats}, so an NPC that was never given a profile never grows one.
     */
    public static StatsData getOrCreate(LivingEntity npc) {
        if (!eligible(npc)) return null;
        StatsData data = npc.getData(NPC_DMZ_STATS.get()).data;
        bindHost(npc, data);
        return data;
    }

    /**
     * Points a blob's attribute reads and writes at the NPC carrying it.
     *
     * <p>{@code StatsDataNpcHostMixin} and {@code StatsNpcHostMixin} exist to route DMZ's
     * player-shaped attribute access onto a living NPC - but nothing ever called them, so
     * {@code Stats.attributesReady()} stayed false and {@link Stats#applyToAttributes()} returned
     * on its first line. The machinery was there and inert; this is the call that arms it.
     *
     * <p>Also marks the blob loaded. DMZ guards several reads on {@code isDataLoaded}, which is only
     * ever set by the player login path, so without this an NPC blob answers defaults no matter what
     * is in it.
     *
     * <p>Rebinding on every fetch is deliberate: an entity is a different object after a reload, and
     * a blob holding the old one would write attributes onto a corpse.
     */
    private static void bindHost(LivingEntity npc, StatsData data) {
        if (npc == null || data == null) return;
        if (data instanceof NpcStatsDataAccess access) {
            access.xenopixels$setNpcHost(npc);
            access.xenopixels$markLoaded();
        }
        Stats stats = data.getStats();
        if (stats instanceof NpcStatsAttributeHost host) {
            host.xenopixels$setNpcHost(npc);
        }
    }

    /** Drop an NPC's stats, so the next profile apply starts from a clean blob. */
    public static void clear(Entity entity) {
        if (!eligible(entity)) return;
        if (entity.hasData(NPC_DMZ_STATS.get())) {
            entity.removeData(NPC_DMZ_STATS.get());
        }
    }

    /**
     * Copy the editor's numbers into the real stats blob.
     *
     * <p>{@link NpcCombatProfile} stays the schema the GUI, the wand and the scripts write; this is
     * the one place it becomes DMZ data. Deliberately one-way: the profile is authored, the blob is
     * derived, and letting the blob write back would make "what did I set this NPC to" depend on
     * what happened to it in a fight.
     *
     * <p>Only the fields DMZ's own formulas read are copied. Aura colour, brain flags and the rest
     * of the profile describe XenoPixels behaviour and have no DMZ counterpart.
     *
     * @return the blob, or {@code null} when the entity cannot carry one
     */
    public static StatsData syncFromProfile(LivingEntity npc, NpcCombatProfile profile) {
        if (profile == null) return null;
        StatsData data = getOrCreate(npc);
        if (data == null) return null;

        Stats stats = data.getStats();
        stats.setStrength(Math.max(0, profile.strength));
        stats.setStrikePower(Math.max(0, profile.strikePower));
        stats.setResistance(Math.max(0, profile.resistance));
        stats.setVitality(Math.max(0, profile.vitality));
        stats.setKiPower(Math.max(0, profile.kiPower));
        stats.setEnergy(Math.max(0, profile.energy));

        Character character = data.getCharacter();
        if (profile.raceId != null && !profile.raceId.isBlank()) {
            character.setRace(profile.raceId);
        }
        // Race alone is not enough: DMZ's getStatScaling reads race *and* class, so leaving the
        // class at its "warrior" default gave an NPC its configured class's health and warrior's
        // ki damage. Same source as NpcVitalitySync uses, so the two cannot drift apart.
        character.setCharacterClass(profile.characterClass());
        // Active form drives getFormMultiplier("PWR"), which is a large part of ki damage. Blank
        // means base form, which is what DMZ spells as an empty group and form rather than null.
        character.setActiveFormGroup(profile.formGroup == null ? "" : profile.formGroup);
        character.setActiveForm(profile.formId == null ? "" : profile.formId);
        character.setActiveStackFormGroup(profile.stackGroup == null ? "" : profile.stackGroup);
        character.setActiveStackForm(profile.stackId == null ? "" : profile.stackId);

        Resources resources = data.getResources();
        resources.setPowerRelease(clampRelease(profile.powerReleasePercent));

        // Push the six main stats onto the entity's own attributes. DMZ's setters above skip this
        // when there is no Player behind the blob, so without this call the numbers lived only in
        // the blob and nothing that reads attributes - DMZ's formulas included - could see them.
        stats.applyToAttributes();
        return data;
    }

    /**
     * Power release as a percentage DMZ will divide by 100.
     *
     * <p>The profile treats a non-positive value as "unset, so full"; zero here would silently
     * zero every ki formula that multiplies by the release, which reads in game as an NPC whose
     * attacks do nothing at all.
     */
    static int clampRelease(int percent) {
        if (percent <= 0) return 100;
        return Math.min(100, percent);
    }

    /**
     * Who may carry a blob.
     *
     * <p>Players are excluded outright: they have DMZ's own attachment, and a second one would be a
     * second source of truth for the same character.
     */
    private static boolean eligible(Entity entity) {
        if (entity == null || entity instanceof Player) return false;
        // Native Xeno NPCs count too. This used to be CustomNPCs-only, which meant a native NPC
        // never got a DragonMineZ blob at all: syncFromProfile returned null on the first line and
        // every stat the editor set stayed in our own NBT, invisible to DMZ. That is why an NPC's
        // battle power did not move when its stats changed - there were no DMZ stats to read.
        return NpcCounterpartSync.isCustomNpc(entity)
                || entity instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
    }

    /** The serializable wrapper the attachment actually stores. */
    public static final class NpcStatsAttachment implements INBTSerializable<CompoundTag> {

        /**
         * Built with a null player on purpose; see the class javadoc for what that costs and why
         * the alternatives cost more.
         */
        private final StatsData data = new StatsData(null);

        public StatsData data() {
            return data;
        }

        @Override
        public CompoundTag serializeNBT(HolderLookup.Provider provider) {
            return data.save();
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
            try {
                data.load(nbt);
            } catch (ClassNotFoundException e) {
                // DMZ declares this on load because its skill/technique tables resolve classes by
                // name. A blob written by a build that had a technique this one does not is not
                // worth killing the entity over; the NPC keeps whatever loaded before the failure.
                XenoPixelsMod.LOGGER.warn("NPC DragonMineZ stats failed to load, keeping defaults", e);
            }
        }
    }
}

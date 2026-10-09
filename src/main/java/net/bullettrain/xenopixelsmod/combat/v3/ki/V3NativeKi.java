package net.bullettrain.xenopixelsmod.combat.v3.ki;

import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.PredefinedTechniques;
import com.dragonminez.common.stats.techniques.TechniqueDispatcher;
import com.dragonminez.common.stats.techniques.Techniques;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.v3.technique.V3Beat;
import net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueCatalog;
import net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueDefinition;
import net.minecraft.server.level.ServerPlayer;

/**
 * The catalog's ki attacks are real DragonMineZ ki techniques. Nothing about a ki attack is
 * simulated by this mod: DragonMineZ charges it while the slot key is held, spawns its own
 * projectile, moves it, and deals its damage. Only the look is replaced, on the client
 * ({@code HdKiClient}).
 *
 * <p>Each technique is a copy of the DragonMineZ technique it stands for under its own id
 * ({@code bt3_<name>}), name and colours, so it shows in DragonMineZ's ki attacks list and can be
 * given with {@code /dmztech add}. Registration only adds: an id that already exists is left alone.
 */
public final class V3NativeKi {
    private static final String OWNER_PREFIX = "xenopixelsmod:";
    public static final String OWNED_VISUAL = "xenopixelsmod.v3NativeKiVisual";
    private static final List<String> IDS = new ArrayList<>();
    private static boolean registered;

    private V3NativeKi() {}

    /** Only the native ids actually registered by this mod, excluding conflicts. */
    public static synchronized boolean owns(String id) {
        return id != null && IDS.contains(nativeId(id));
    }

    public static void markVisual(String techniqueId, net.minecraft.world.entity.Entity visual) {
        if (visual instanceof com.dragonminez.common.init.entities.ki.KiExplosionVisualEntity && owns(techniqueId))
            visual.getPersistentData().putBoolean(OWNED_VISUAL, true);
    }

    /** {@code xenopixelsmod:bt3_kamehameha_x10} to {@code bt3_kamehameha_x10}: plain, as /dmztech wants. */
    public static String nativeId(String techniqueId) {
        if (techniqueId == null) return null;
        String id = techniqueId.toLowerCase(Locale.ROOT);
        return id.startsWith(OWNER_PREFIX) ? id.substring(OWNER_PREFIX.length()) : id;
    }

    /**
     * A technique that is nothing but ki: no strike, burst, hold or approach of its own. These
     * are DragonMineZ ki attacks outright; V3 runs no timeline for them.
     */
    public static boolean pureKi(V3TechniqueDefinition technique) {
        if (technique == null || technique.kiTechnique() == null || technique.kiTechnique().isBlank()) return false;
        for (V3Beat beat : technique.beats()) {
            switch (beat.kind()) {
                case STRIKE, RADIAL, HOLD_TARGET, APPROACH, SHOVE -> { return false; }
                default -> { }
            }
        }
        return true;
    }

    /** Native id to the catalog technique behind it, for every ki technique in {@code techniques}. */
    public static Map<String, V3TechniqueDefinition> plan(Iterable<V3TechniqueDefinition> techniques) {
        Map<String, V3TechniqueDefinition> out = new LinkedHashMap<>();
        for (V3TechniqueDefinition technique : techniques) {
            if (technique.kiTechnique() == null || technique.kiTechnique().isBlank()) continue;
            out.putIfAbsent(nativeId(technique.id()), technique);
        }
        return out;
    }

    /**
     * Adds the techniques to DragonMineZ's ki registry once DragonMineZ's own are there to copy.
     * Safe to call any number of times, from either side.
     */
    public static synchronized List<String> ensureRegistered() {
        if (registered) return List.copyOf(IDS);
        return registerAll(false);
    }

    /**
     * Reload owned registry copies from current {@link XenoKiProfileCatalog} (after Maker /
     * {@code /xenokiprofile reload}).
     */
    public static synchronized List<String> refreshOwned() {
        for (String id : IDS) {
            PredefinedTechniques.REGISTRY.remove(id);
        }
        IDS.clear();
        registered = false;
        return registerAll(true);
    }

    private static List<String> registerAll(boolean replaceOwned) {
        Map<String, V3TechniqueDefinition> plan = plan(V3TechniqueCatalog.all());
        if (plan.isEmpty() || PredefinedTechniques.REGISTRY.isEmpty()) return List.of();
        int skipped = 0;
        for (Map.Entry<String, V3TechniqueDefinition> entry : plan.entrySet()) {
            String id = entry.getKey();
            V3TechniqueDefinition technique = entry.getValue();
            KiAttackData template = PredefinedTechniques.REGISTRY.get(technique.kiTechnique());
            if (template == null) {
                skipped++;
                continue;
            }
            if (PredefinedTechniques.REGISTRY.containsKey(id) && !replaceOwned) {
                skipped++;
                continue;
            }
            V3KiStyle style = V3KiStyle.of(technique);
            KiAttackData data = copy(template);
            data.setId(id);
            data.setName(technique.name());
            data.setAuthor("XenoPixels");
            data.setColorInterior(style.core());
            data.setColorExterior(style.edge());
            XenoKiProfileCatalog.resolve(technique).applyTo(data);
            PredefinedTechniques.REGISTRY.put(id, data);
            if (!IDS.contains(id)) IDS.add(id);
        }
        registered = true;
        XenoPixelsMod.LOGGER.info("Combat V3 ki attacks added to DragonMineZ's ki list: {} ({} skipped)", IDS.size(), skipped);
        return List.copyOf(IDS);
    }

    /** Mutates DragonMineZ's live ki skill list in place, so its menus and /dmztech accept the ids. */
    public static void installInto(List<String> live) {
        if (live == null) return;
        for (String id : ensureRegistered()) {
            if (!live.contains(id)) live.add(id);
        }
    }

    /**
     * Gives {@code data} every pure ki technique as a DragonMineZ ki attack, and moves any that
     * were unlocked or slotted in their old strike form over to it.
     *
     * @return whether anything changed
     */
    public static boolean grant(StatsData data) {
        if (data == null || data.getTechniques() == null) return false;
        ensureRegistered();
        Techniques techniques = data.getTechniques();
        String[] slots = techniques.getEquippedSlots();
        boolean changed = false;
        for (V3TechniqueDefinition technique : V3TechniqueCatalog.all()) {
            if (!pureKi(technique)) continue;
            String id = nativeId(technique.id());
            KiAttackData template = PredefinedTechniques.REGISTRY.get(id);
            if (template == null) continue;
            if (techniques.getUnlockedTechniques().remove(technique.id()) != null) changed = true;
            if (slots != null) {
                for (int i = 0; i < slots.length; i++) {
                    if (technique.id().equals(slots[i])) {
                        slots[i] = id;
                        changed = true;
                    }
                }
            }
            if (!techniques.getUnlockedTechniques().containsKey(id)) {
                techniques.unlockTechnique(copy(template));
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Fires DragonMineZ's own {@code nativeKiId} ki attack from {@code player}: its dispatcher makes
     * the projectile and fires it. For techniques that end a melee string with a blast.
     */
    public static boolean fire(ServerPlayer player, String nativeKiId) {
        return fire(player, nativeKiId, null);
    }

    /**
     * Prefer the owned {@code bt3_*} registry copy when present, apply {@link XenoKiProfile}, then
     * fire through DragonMineZ's dispatcher (real hitboxes).
     */
    public static boolean fire(ServerPlayer player, String nativeKiId, V3TechniqueDefinition technique) {
        ensureRegistered();
        String owned = technique != null ? nativeId(technique.id()) : null;
        String id = owned != null && PredefinedTechniques.REGISTRY.containsKey(owned) ? owned : nativeKiId;
        KiAttackData template = id == null ? null : PredefinedTechniques.REGISTRY.get(id);
        StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        if (template == null || stats == null) return false;
        KiAttackData ki = copy(template);
        XenoKiProfile profile = technique != null
                ? XenoKiProfileCatalog.resolve(technique)
                : (owned != null
                        ? XenoKiProfileCatalog.resolve(OWNER_PREFIX + owned, null, nativeKiId)
                        : XenoKiProfile.empty("", nativeKiId == null ? "" : nativeKiId));
        profile.applyTo(ki);
        // Below half charge the dispatcher creates the projectile; from half it fires it.
        boolean fired = TechniqueDispatcher.executeKiAttack(player, player.level(), ki, stats, 0.01f)
                && TechniqueDispatcher.executeKiAttack(player, player.level(), ki, stats, 2.0f);
        if (fired) applyMuzzle(player, profile);
        return fired;
    }

    /**
     * Apply profile muzzle offsets onto the player's live {@link KiWaveEntity} after DMZ spawn.
     * {@code centerMuzzle} forces OFFSET_X = 0 so beams do not miss right of the target.
     */
    static void applyMuzzle(ServerPlayer player, XenoKiProfile profile) {
        if (player == null || profile == null || player.level().isClientSide()) return;
        boolean hasOffsets = profile.centerMuzzle()
                || profile.castOffsetX() != null
                || profile.castOffsetY() != null
                || profile.castOffsetZ() != null;
        if (!hasOffsets) return;
        float ox = profile.centerMuzzle() ? 0f
                : (profile.castOffsetX() != null ? profile.castOffsetX() : 0f);
        float oy = profile.castOffsetY() != null ? profile.castOffsetY() : 0.2f;
        float oz = profile.castOffsetZ() != null ? profile.castOffsetZ() : 0.5f;
        for (var entity : player.level().getEntities(player, player.getBoundingBox().inflate(8.0),
                e -> e instanceof KiWaveEntity wave && wave.isOwner(player))) {
            ((KiWaveEntity) entity).setContinuousFollow(false);
            ((KiWaveEntity) entity).setCastOffsets(ox, oy, oz);
        }
    }

    private static KiAttackData copy(KiAttackData template) {
        KiAttackData data = new KiAttackData();
        data.load(template.save());
        return data;
    }
}

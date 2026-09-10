package net.bullettrain.xenopixelsmod.combat.technique;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.techniques.PredefinedTechniques;
import com.dragonminez.common.stats.techniques.StrikeAttackData;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.network.Bt3CombatPacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * Xeno moves that live in DragonMineZ's own technique list, so they can be equipped to a slot and
 * reached from Controlify's radial menu instead of needing a key or gamepad chord of their own.
 *
 * <p>Registration only places the data in DMZ's predefined strike registry; unlocking, equipping
 * and slot selection stay DMZ's. Casting does <em>not</em>: these are not melee strikes, so
 * {@code StrikeAttackHandlerMixin} intercepts DMZ's {@code requestStrike} for these ids, calls
 * {@link #cast} and cancels, and none of DMZ's strike machinery — targeting, dash, damage, cost,
 * cooldown — ever runs. Each move keeps paying its own costs inside its own handler, which is why
 * there is no cost or cooldown override here of the kind the rush strikes need.
 *
 * <p>The registered entries carry no damage and no animation on purpose: DMZ only ever shows them
 * in its technique list, it never executes them.
 */
public final class XenoSlotTechniques {

    public static final String HAKAI = XenoPixelsMod.MOD_ID + ":hakai";
    public static final String ZANZOKEN = XenoPixelsMod.MOD_ID + ":zanzoken";
    public static final String MULTIFORM = XenoPixelsMod.MOD_ID + ":multiform";

    private static final String[] IDS = {HAKAI, ZANZOKEN, MULTIFORM};

    private XenoSlotTechniques() {
    }

    public static void register() {
        if (!XenoServerConfig.xenoSlotTechniquesEnabled) return;
        register(HAKAI, "Hakai");
        register(ZANZOKEN, "Zanzoken");
        register(MULTIFORM, "Shi Shin No Ken");
    }

    /**
     * Grants all three to a player.
     *
     * <p>They are Xeno entries injected into DMZ's registry, so no DMZ progression path can ever
     * unlock them — the same reasoning as {@link XenoRushTechniques#unlockRushTechniques}, which is
     * why this is not gated behind a permission either.
     */
    public static void unlock(ServerPlayer player) {
        if (player == null || !XenoServerConfig.xenoSlotTechniquesEnabled) return;
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        if (data == null || data.getTechniques() == null) return;
        var techniques = data.getTechniques();
        for (String id : IDS) {
            StrikeAttackData strike = PredefinedTechniques.STRIKE_REGISTRY.get(id);
            if (strike != null) {
                techniques.unlockTechnique(strike);
            }
        }
    }

    /** True for the three Xeno-owned slot ids, so nothing else is ever intercepted. */
    public static boolean isSlotTechniqueId(String id) {
        // Also the guard the strike interception leans on: with the feature off nothing is ours,
        // so DMZ's own strike lifecycle is never diverted.
        if (id == null || !XenoServerConfig.xenoSlotTechniquesEnabled) return false;
        for (String own : IDS) {
            if (own.equals(id)) return true;
        }
        return false;
    }

    /**
     * Runs the Xeno move behind {@code id}.
     *
     * <p>Each target reuses the same server entry point the key and chord routes use, so a slot
     * cast cannot drift from them: identical validation, resource cost, cooldown and messaging.
     *
     * @return whether {@code id} was one of ours and was dispatched
     */
    public static boolean cast(ServerPlayer player, String id) {
        if (player == null || id == null) return false;
        if (HAKAI.equals(id)) {
            // Null target: resolve from where the caster is looking, exactly as the held key does.
            Bt3CombatPacket.startHakai(player, null);
            return true;
        }
        if (ZANZOKEN.equals(id)) {
            Bt3CombatPacket.handleZanzoken(player);
            return true;
        }
        if (MULTIFORM.equals(id)) {
            Bt3CombatPacket.handleMultiForm(player);
            return true;
        }
        return false;
    }

    private static void register(String id, String name) {
        String key = id.toLowerCase(Locale.ROOT);
        if (PredefinedTechniques.STRIKE_REGISTRY.containsKey(key)) {
            return;
        }
        StrikeAttackData data = new StrikeAttackData();
        data.setId(key);
        data.setName(name);
        data.setAuthor("XenoPixels");
        data.setDamageMultiplier(0.0f);
        data.setDurationTicks(1);
        data.setBaseCost(0.0);
        // DMZ overwrites castTime and cooldown from its own per-id config, so let it run rather
        // than fighting it. Nothing here reaches the game: the cast is intercepted before DMZ's
        // strike machinery starts, and the move charges its own cost.
        data.applyConfigDefaults();
        PredefinedTechniques.STRIKE_REGISTRY.put(key, data);
    }
}

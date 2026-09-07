package net.bullettrain.xenopixelsmod.combat.technique;

import com.dragonminez.common.stats.techniques.PredefinedTechniques;
import com.dragonminez.common.stats.techniques.StrikeAttackData;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.server.level.ServerPlayer;
import net.bullettrain.xenopixelsmod.command.XenoPermissions;
import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;

import java.util.Locale;

/**
 * DMZ-native strike definitions for Xeno rush attacks.
 *
 * <p>Registration only places the data in DMZ's own predefined strike maps. Unlocking, equipping,
 * slot selection, and casting remain owned by DragonMineZ.
 */
public final class XenoRushTechniques {
    public static final String RUSH_LEFT = XenoPixelsMod.MOD_ID + ":rush_left";
    public static final String RUSH_RIGHT = XenoPixelsMod.MOD_ID + ":rush_right";
    public static final String RUSH_BREAKER = XenoPixelsMod.MOD_ID + ":rush_breaker";
    public static final String RUSH_FINISHER = XenoPixelsMod.MOD_ID + ":rush_finisher";

    private XenoRushTechniques() {
    }

    public static void register() {
        register(RUSH_LEFT, "Xeno Rush Left", "combat.xeno_dmz_punch_left_v4", 1.0f, 8);
        register(RUSH_RIGHT, "Xeno Rush Right", "combat.xeno_dmz_punch_right_v4", 1.0f, 8);
        register(RUSH_BREAKER, "Xeno Rush Breaker", "combat.xeno_cross_left_v2", 1.25f, 12);
        register(RUSH_FINISHER, "Xeno Rush Finisher", "combat.xeno_heavy_finish_v4", 1.6f, 20);
    }

    /**
     * Optional explicit slot helper. It is never called during login or data loading, so rush
     * attacks do not silently register themselves in or occupy the player's DMZ bars.
     */
    public static void syncNativeSlots(ServerPlayer player) {
        if (player == null) {
            return;
        }
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        if (data == null || data.getTechniques() == null) {
            return;
        }
        var techniques = data.getTechniques();
        var unlocked = techniques.getUnlockedTechniques();
        String[] slots = techniques.getEquippedSlots();
        if (slots == null || slots.length < 6) {
            return;
        }
        bindIfUnlocked(unlocked, slots, 0, RUSH_LEFT);
        bindIfUnlocked(unlocked, slots, 1, RUSH_RIGHT);
        bindIfUnlocked(unlocked, slots, 4, RUSH_BREAKER);
        bindIfUnlocked(unlocked, slots, 5, RUSH_FINISHER);
    }

    /**
     * Grants the four registered rush definitions to every player.
     *
     * <p>These strikes are Xeno entries injected into DMZ's predefined strike registry, so no DMZ
     * progression path can ever unlock them. Gating this on an operator permission left them
     * permanently locked for ordinary players — including in single-player without cheats — which
     * made rush look broken rather than unearned. {@link XenoPermissions#BT3_RUSH_UNLOCK_BYPASS}
     * stays declared for compatibility but is no longer consulted here.
     */
    public static void unlockRushTechniques(ServerPlayer player) {
        if (player == null) {
            return;
        }
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        if (data == null || data.getTechniques() == null) {
            return;
        }
        var techniques = data.getTechniques();
        for (String id : RUSH_IDS) {
            StrikeAttackData strike = PredefinedTechniques.STRIKE_REGISTRY.get(id);
            if (strike != null) {
                techniques.unlockTechnique(strike);
            }
        }
        syncNativeSlots(player);
    }

    private static final String[] RUSH_IDS = {
            RUSH_LEFT, RUSH_RIGHT, RUSH_BREAKER, RUSH_FINISHER
    };

    /** True for the four Xeno-owned strike ids, so overrides never touch a DMZ or player technique. */
    public static boolean isRushId(String id) {
        if (id == null) return false;
        for (String rush : RUSH_IDS) {
            if (rush.equals(id)) return true;
        }
        return false;
    }

    /** Deterministic four-beat sequence used by the delayed-W rush path. */
    public static String idForRushStep(int step) {
        return RUSH_IDS[Math.floorMod(Math.max(1, step) - 1, RUSH_IDS.length)];
    }

    public static Bt3AnimationIntent animationForRushStep(int step) {
        return switch (Math.floorMod(Math.max(1, step) - 1, RUSH_IDS.length)) {
            case 0 -> Bt3AnimationIntent.JAB_LEFT;
            case 1 -> Bt3AnimationIntent.JAB_RIGHT;
            case 2 -> Bt3AnimationIntent.CROSS_LEFT;
            default -> Bt3AnimationIntent.HEAVY_FINISH;
        };
    }

    public static int slotForRushId(String id) {
        if (RUSH_LEFT.equals(id)) return 0;
        if (RUSH_RIGHT.equals(id)) return 1;
        if (RUSH_BREAKER.equals(id)) return 4;
        if (RUSH_FINISHER.equals(id)) return 5;
        return -1;
    }

    private static void bindIfUnlocked(java.util.Map<String, ?> unlocked, String[] slots,
                                       int slot, String id) {
        if (unlocked.containsKey(id) && (slots[slot] == null || slots[slot].isEmpty())) {
            TechniqueSlotBind.place(slots, slot, id);
        }
    }

    private static void register(String id, String name, String animation, float damage, int duration) {
        String key = id.toLowerCase(Locale.ROOT);
        if (PredefinedTechniques.STRIKE_REGISTRY.containsKey(key)) {
            return;
        }
        StrikeAttackData data = new StrikeAttackData();
        data.setId(key);
        data.setName(name);
        data.setAuthor("XenoPixels");
        data.setDamageMultiplier(damage);
        data.setAnimationId(animation);
        data.setDurationTicks(duration);
        data.setBaseCost(10.0);
        // DMZ's applyConfigDefaults overwrites castTime and cooldown from its own per-id config,
        // so it has to run before ours rather than after. The cooldown actually used at cast time
        // comes from getActualCooldown, which re-reads that config every call — StrikeAttackCostMixin
        // is what makes the Xeno value stick.
        data.applyConfigDefaults();
        data.setCooldown(Math.max(4, duration));
        PredefinedTechniques.STRIKE_REGISTRY.put(key, data);
    }
}

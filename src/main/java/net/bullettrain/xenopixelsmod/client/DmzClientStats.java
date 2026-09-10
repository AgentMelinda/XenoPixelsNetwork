package net.bullettrain.xenopixelsmod.client;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Resources;
import com.dragonminez.common.stats.character.Status;
import com.dragonminez.common.stats.extras.ActionMode;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.dragonminez.compat.util.LazyOptional;

/**
 * Live read of DragonMineZ client-side stats for the custom HUD.
 *
 * <p>Per-game-tick cache: HUD + hotbar + party + combat may all call {@link #read}
 * in the same tick; capability lookups are not free.
 */
@OnlyIn(Dist.CLIENT)
public final class DmzClientStats {
    /** entityId → snapshot for the current client game time. */
    private static final Int2ObjectOpenHashMap<Snapshot> TICK_CACHE = new Int2ObjectOpenHashMap<>();
    private static long cacheGameTime = Long.MIN_VALUE;
    private static final Snapshot EMPTY = new Snapshot(
            false, 0, 100, 0f, 0f, 0f, 0f, 0f, 0, 0, false, null,
            false, Status.FLIGHT_SEARCH, 0, "");

    private DmzClientStats() {}

    public static void clear() {
        TICK_CACHE.clear();
        cacheGameTime = Long.MIN_VALUE;
    }

    public static final class Snapshot {
        public final boolean present;
        public final int powerRelease;
        public final int releaseLimit;
        public final float energy;
        public final float maxEnergy;
        public final float stamina;
        public final float maxStamina;
        public final float maxHealth;
        /** Raw DMZ action charge (transform hold G). */
        public final int actionCharge;
        /** Same display mapping DMZ HUD uses for the portrait charge fill. */
        public final int displayActionCharge;
        public final boolean actionCharging;
        public final ActionMode selectedAction;
        public final boolean flyActive;
        public final int flightMode;
        public final int level;
        public final String activeForm;

        private Snapshot(boolean present, int powerRelease, int releaseLimit,
                         float energy, float maxEnergy, float stamina, float maxStamina, float maxHealth,
                         int actionCharge, int displayActionCharge, boolean actionCharging, ActionMode selectedAction,
                         boolean flyActive, int flightMode, int level, String activeForm) {
            this.present = present;
            this.powerRelease = powerRelease;
            this.releaseLimit = releaseLimit;
            this.energy = energy;
            this.maxEnergy = maxEnergy;
            this.stamina = stamina;
            this.maxStamina = maxStamina;
            this.maxHealth = maxHealth;
            this.actionCharge = actionCharge;
            this.displayActionCharge = displayActionCharge;
            this.actionCharging = actionCharging;
            this.selectedAction = selectedAction;
            this.flyActive = flyActive;
            this.flightMode = flightMode;
            this.level = level;
            this.activeForm = activeForm == null ? "" : activeForm;
        }

        public static Snapshot empty() {
            return EMPTY;
        }

        public float releasePercent() {
            int cap = releaseLimit > 0 ? releaseLimit : 100;
            return Math.max(0f, Math.min(1f, powerRelease / (float) cap));
        }

        public float energyPercent() {
            if (maxEnergy <= 0f) return 0f;
            return Math.max(0f, Math.min(1f, energy / maxEnergy));
        }

        public float staminaPercent() {
            if (maxStamina <= 0f) return 0f;
            return Math.max(0f, Math.min(1f, stamina / maxStamina));
        }

        /**
         * 0..1 transform / action charge around the portrait.
         * DMZ maps charge to a fill with {@code display = charge < 10 ? charge + 10 : charge}, then / 100.
         */
        public float transformChargePercent() {
            return Math.max(0f, Math.min(1f, displayActionCharge / 100f));
        }

        public boolean isTransforming() {
            return displayActionCharge > 0 || actionCharging;
        }
    }

    /**
     * Widened from the original {@code LocalPlayer}-only signature so party
     * HUD code ({@link XenoPartyOverlay}) can read any nearby tracked
     * {@link Player}'s DMZ resources. DMZ's capability system is registered
     * per-{@code Entity} ({@code StatsProvider.get(Capability, Entity)}) and
     * syncs per-player-id resource packets to observing clients (used for
     * DMZ's own above-head HP display), so this is expected to also resolve
     * for remote players, not just the local one — if a given remote
     * player's data hasn't synced yet, this safely falls back to
     * {@link Snapshot#empty()} like it always has for the local player.
     */
    public static Snapshot read(Player player) {
        if (player == null) return Snapshot.empty();

        long gameTime = player.level().getGameTime();
        if (gameTime != cacheGameTime) {
            TICK_CACHE.clear();
            cacheGameTime = gameTime;
        }

        int id = player.getId();
        Snapshot cached = TICK_CACHE.get(id);
        if (cached != null) {
            return cached;
        }

        Snapshot snap = readUncached(player);
        TICK_CACHE.put(id, snap);
        return snap;
    }

    private static Snapshot readUncached(Player player) {
        try {
            LazyOptional<StatsData> opt = StatsProvider.get(StatsCapability.INSTANCE, player);
            if (!opt.isPresent()) return Snapshot.empty();

            StatsData data = opt.orElse(null);
            if (data == null || !data.isDataLoaded()) return Snapshot.empty();

            Resources res = data.getResources();
            if (res == null) return Snapshot.empty();

            int charge = res.getActionCharge();
            // Match DMZ XenoverseHUD display mapping
            int displayCharge = charge < 10 ? charge + 10 : charge;
            if (charge <= 0) {
                displayCharge = 0;
            }

            boolean charging = false;
            ActionMode mode = null;
            boolean flyActive = false;
            int flightMode = Status.FLIGHT_SEARCH;
            Status status = data.getStatus();
            if (status != null) {
                charging = status.isActionCharging();
                mode = status.getSelectedAction();
                flightMode = status.getFlightMode();
            }
            if (data.getSkills() != null) flyActive = data.getSkills().isSkillActive("fly");

            return new Snapshot(
                    true,
                    res.getPowerRelease(),
                    res.getReleaseLimit(),
                    res.getCurrentEnergy(),
                    data.getMaxEnergy(),
                    res.getCurrentStamina(),
                    data.getMaxStamina(),
                    data.getMaxHealth(),
                    charge,
                    displayCharge,
                    charging,
                    mode,
                    flyActive,
                    flightMode,
                    data.getLevel(),
                    data.getCharacter() == null ? "" : data.getCharacter().getActiveForm()
            );
        } catch (Throwable t) {
            return Snapshot.empty();
        }
    }
}

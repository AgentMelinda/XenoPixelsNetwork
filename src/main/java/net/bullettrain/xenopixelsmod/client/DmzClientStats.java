package net.bullettrain.xenopixelsmod.client;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Resources;
import com.dragonminez.common.stats.character.Status;
import com.dragonminez.common.stats.extras.ActionMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.LazyOptional;

/**
 * Live read of DragonMineZ client-side stats for the custom HUD.
 */
@OnlyIn(Dist.CLIENT)
public final class DmzClientStats {
    private DmzClientStats() {}

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

        private Snapshot(boolean present, int powerRelease, int releaseLimit,
                         float energy, float maxEnergy, float stamina, float maxStamina, float maxHealth,
                         int actionCharge, int displayActionCharge, boolean actionCharging, ActionMode selectedAction) {
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
        }

        public static Snapshot empty() {
            return new Snapshot(false, 0, 100, 0f, 0f, 0f, 0f, 0f, 0, 0, false, null);
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

    public static Snapshot read(LocalPlayer player) {
        if (player == null) return Snapshot.empty();
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
            Status status = data.getStatus();
            if (status != null) {
                charging = status.isActionCharging();
                mode = status.getSelectedAction();
            }

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
                    mode
            );
        } catch (Throwable t) {
            return Snapshot.empty();
        }
    }
}

package net.bullettrain.xenopixelsmod.client.aura;

import com.dragonminez.common.stats.StatsData;
import net.bullettrain.xenopixelsmod.api.dmz.DmzAccess;
import net.minecraft.world.entity.Entity;

/** DMZ's body-following Surge/Sparking border, with no gameplay state changes. */
public final class HakaiEnergyAura {
    private HakaiEnergyAura() {}

    public static boolean active(Entity player) {
        if (player == null || !SecondAuraClientState.on(player.getId())) return false;
        return active(player, DmzAccess.stats(player).orElse(null));
    }

    /**
     * The idle Hakai mode hides the ordinary aura, but charging ki must still show its charge aura.
     * DragonMineZ sets {@code isChargingKi} before it marks the aura active, so check that status
     * at the render boundary instead of suppressing every queued aura for this form.
     */
    public static boolean suppressAura(Entity player) {
        if (player == null || !SecondAuraClientState.on(player.getId())) return false;
        StatsData stats = DmzAccess.stats(player).orElse(null);
        if (!active(player, stats)) return false;
        boolean chargingKi = stats != null && stats.getStatus() != null && stats.getStatus().isChargingKi();
        return HakaiEnergyAuraPolicy.suppressAura(chargingKi);
    }

    static boolean active(Entity player, StatsData stats) {
        var character = stats == null ? null : stats.getCharacter();
        return player != null && character != null && HakaiEnergyAuraPolicy.active(
                SecondAuraClientState.on(player.getId()), character.hasActiveForm(), character.getActiveFormGroup());
    }

    public static float[] colour(Entity player, StatsData stats, float[] original) {
        if (!active(player, stats) || player.isInvisible() || !player.isAlive()) return original;
        if (player instanceof net.minecraft.world.entity.player.Player p && p.isSpectator()) return original;
        var form = stats.getCharacter().getActiveFormData();
        float[] colour = form == null ? null : form.getRgbAuraColor();
        if (colour == null || colour.length < 3) colour = stats.getCharacter().getRgbAuraColor();
        return colour == null || colour.length < 3 ? original : colour.clone();
    }
}

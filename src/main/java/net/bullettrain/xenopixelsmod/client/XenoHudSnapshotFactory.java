package net.bullettrain.xenopixelsmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.bullettrain.xenopixelsmod.client.combat.Bt3CombatClient;
import net.bullettrain.xenopixelsmod.effect.ModEffects;

/**
 * Builds a {@link XenoHudSnapshot} for the current frame. Moved out of
 * {@link XenoHudOverlay} in Phase 3 of the LDLib HUD migration plan: all
 * DMZ-read / fallback / ratio-clamping logic now lives here, unchanged in
 * behavior, so the overlay's draw code only consumes already-resolved values.
 */
public final class XenoHudSnapshotFactory {
    private static long cachedGameTime = Long.MIN_VALUE;
    private static int cachedPlayerId = Integer.MIN_VALUE;
    private static XenoHudSnapshot cachedSnapshot;

    private XenoHudSnapshotFactory() {
    }

    public static XenoHudSnapshot capture(Minecraft mc) {
        long gameTime = mc.level != null ? mc.level.getGameTime() : Long.MIN_VALUE;
        int playerId = mc.player != null ? mc.player.getId() : -1;
        if (cachedSnapshot != null && cachedGameTime == gameTime && cachedPlayerId == playerId) {
            return cachedSnapshot;
        }
        DmzClientStats.Snapshot dmz = DmzClientStats.read(mc.player);

        String name = resolveName(mc);
        String releaseText = dmz.present ? (dmz.powerRelease + "%") : null;

        float hp = resolveHp(mc, dmz);
        float ki = dmz.present ? dmz.energyPercent() : resolveKiFallback();
        float stm = dmz.present ? dmz.staminaPercent() : resolveStmFallback();

        float curHp = resolveCurrentHp(mc);
        float maxHp = resolveMaxHp(mc, dmz);
        float curKi = dmz.present ? dmz.energy : XenoClientData.ki;
        float maxKi = dmz.present ? dmz.maxEnergy : XenoClientData.maxKi;
        float curStm = dmz.present ? dmz.stamina : XenoClientData.stamina;
        float maxStm = dmz.present ? dmz.maxStamina : XenoClientData.maxStamina;

        boolean transforming = dmz.present && dmz.isTransforming();
        float chargePercent = transforming ? dmz.transformChargePercent() : 0f;

        XenoHudSnapshot snapshot = new XenoHudSnapshot(name, dmz.present, releaseText,
                dmz.present ? dmz.releasePercent() : 0f,
                hp, ki, stm, curHp, maxHp, curKi, maxKi, curStm, maxStm,
                transforming, chargePercent, dmz.level, dmz.activeForm,
                Bt3CombatClient.getSparkingMeter(), mc.player != null && mc.player.hasEffect(ModEffects.SPARKING));
        cachedGameTime = gameTime;
        cachedPlayerId = playerId;
        cachedSnapshot = snapshot;
        return snapshot;
    }

    private static float resolveCurrentHp(Minecraft mc) {
        if (mc.player != null) return mc.player.getHealth();
        return XenoClientData.health;
    }

    private static float resolveMaxHp(Minecraft mc, DmzClientStats.Snapshot dmz) {
        float max = mc.player != null ? mc.player.getMaxHealth() : XenoClientData.maxHealth;
        if (dmz.present && dmz.maxHealth > 0f) max = Math.max(max, dmz.maxHealth);
        return max;
    }

    private static float resolveHp(Minecraft mc, DmzClientStats.Snapshot dmz) {
        if (mc.player != null) {
            float max = mc.player.getMaxHealth();
            if (dmz.present && dmz.maxHealth > 0f) max = Math.max(max, dmz.maxHealth);
            return safePercent(mc.player.getHealth(), max);
        }
        if (XenoClientData.maxHealth > 0f) {
            return safePercent(XenoClientData.health, XenoClientData.maxHealth);
        }
        return 1f;
    }

    private static float resolveKiFallback() {
        if (XenoClientData.maxKi > 0f) return safePercent(XenoClientData.ki, XenoClientData.maxKi);
        return 1f;
    }

    private static float resolveStmFallback() {
        if (XenoClientData.maxStamina > 0f) return safePercent(XenoClientData.stamina, XenoClientData.maxStamina);
        return 1f;
    }

    private static String resolveName(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player != null) {
            String n = player.getGameProfile().getName();
            if (n != null && !n.isEmpty()) return n;
            return player.getName().getString();
        }
        return "Player";
    }

    private static float safePercent(float value, float max) {
        if (max <= 0f) return 0f;
        return value / max;
    }
}

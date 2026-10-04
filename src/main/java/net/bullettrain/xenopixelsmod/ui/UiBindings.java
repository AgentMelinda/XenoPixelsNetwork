package net.bullettrain.xenopixelsmod.ui;

import java.util.Set;

/**
 * Inspector allow-list. Snapshot keys match {@code XenoHudSnapshot}. Vanilla keys
 * match NeoForge 21.1.248 {@code Player} / {@code FoodData} / {@code Entity} /
 * {@code MultiPlayerGameMode} accessors verified in mapped sources. Unknown keys
 * fail validation instead of silently drawing zero.
 */
public final class UiBindings {
    public static final Set<String> KEYS = Set.of(
            "player.health",
            "player.maxHealth",
            "player.hpPercent",
            "player.ki",
            "player.maxKi",
            "player.kiPercent",
            "player.stm",
            "player.maxStm",
            "player.stmPercent",
            "player.releasePercent",
            "player.release",
            "player.transforming",
            "player.transformChargePercent",
            "player.sparking",
            "player.sparkingActive",
            "player.dmzPresent",
            "player.level",
            "player.name",
            "player.form",
            "player.food",
            "player.foodPercent",
            "player.saturation",
            "player.air",
            "player.maxAir",
            "player.airPercent",
            "player.armor",
            "player.absorption",
            "player.xpLevel",
            "player.xpPercent",
            "player.pose",
            "player.gamemode",
            "master.name",
            "master.id",
            "master.nearby",
            "master.prerequisite"
    );

    private UiBindings() {
    }

    public static boolean isKnown(String key) {
        return key != null && KEYS.contains(key);
    }
}

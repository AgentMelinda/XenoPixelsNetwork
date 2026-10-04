package net.bullettrain.xenopixelsmod.client.ui.runtime;

import net.bullettrain.xenopixelsmod.client.XenoHudSnapshot;
import net.bullettrain.xenopixelsmod.ui.UiBindingSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Snapshot keys plus vanilla {@code LocalPlayer} fields verified against NeoForge 21.1.248
 * mapped sources. Missing DMZ or a null player returns null so the resolver shows
 * {@code [unbound: …]}, not a silent 0.
 */
@OnlyIn(Dist.CLIENT)
public final class UiSnapshotBindings implements UiBindingSource {
    /** Clamp used by {@code FoodData.add} in NeoForge 21.1.248 sources. */
    private static final double VANILLA_FOOD_MAX = 20.0;

    private final XenoHudSnapshot snapshot;
    private final LocalPlayer player;
    private final GameType gameMode;

    public UiSnapshotBindings(XenoHudSnapshot snapshot) {
        this(Minecraft.getInstance(), snapshot);
    }

    public UiSnapshotBindings(Minecraft minecraft, XenoHudSnapshot snapshot) {
        this.snapshot = snapshot;
        this.player = minecraft == null ? null : minecraft.player;
        this.gameMode = minecraft != null && minecraft.gameMode != null
                ? minecraft.gameMode.getPlayerMode() : null;
    }

    @Override
    public Double number(String key) {
        if (key == null) {
            return null;
        }
        Double fromSnapshot = snapshotNumber(key);
        if (fromSnapshot != null) {
            return fromSnapshot;
        }
        return vanillaNumber(key);
    }

    @Override
    public String text(String key) {
        if (key == null) {
            return null;
        }
        String fromSnapshot = snapshotText(key);
        if (fromSnapshot != null) {
            return fromSnapshot;
        }
        return vanillaText(key);
    }

    private Double snapshotNumber(String key) {
        if (snapshot == null) {
            return null;
        }
        return switch (key) {
            case "player.health" -> (double) snapshot.curHp;
            case "player.maxHealth" -> (double) snapshot.maxHp;
            case "player.hpPercent" -> (double) snapshot.hpPercent;
            case "player.ki" -> (double) snapshot.curKi;
            case "player.maxKi" -> (double) snapshot.maxKi;
            case "player.kiPercent" -> (double) snapshot.kiPercent;
            case "player.stm" -> (double) snapshot.curStm;
            case "player.maxStm" -> (double) snapshot.maxStm;
            case "player.stmPercent" -> (double) snapshot.stmPercent;
            case "player.releasePercent" -> (double) snapshot.releasePercent;
            case "player.transforming" -> snapshot.transforming ? 1.0 : 0.0;
            case "player.transformChargePercent" -> (double) snapshot.transformChargePercent;
            case "player.sparking" -> (double) snapshot.sparking;
            case "player.sparkingActive" -> snapshot.sparkingActive ? 1.0 : 0.0;
            case "player.dmzPresent" -> snapshot.dmzPresent ? 1.0 : 0.0;
            case "player.level" -> (double) snapshot.level;
            default -> null;
        };
    }

    private String snapshotText(String key) {
        if (snapshot == null) {
            return null;
        }
        return switch (key) {
            case "player.name" -> snapshot.name;
            case "player.form" -> snapshot.activeForm;
            case "player.release" -> snapshot.releaseText;
            case "player.transforming" -> snapshot.transforming ? "true" : "false";
            case "player.sparkingActive" -> snapshot.sparkingActive ? "true" : "false";
            case "player.dmzPresent" -> snapshot.dmzPresent ? "true" : "false";
            default -> null;
        };
    }

    private Double vanillaNumber(String key) {
        if (player == null) {
            return null;
        }
        return switch (key) {
            case "player.food" -> (double) player.getFoodData().getFoodLevel();
            case "player.foodPercent" -> player.getFoodData().getFoodLevel() / VANILLA_FOOD_MAX;
            case "player.saturation" -> (double) player.getFoodData().getSaturationLevel();
            case "player.air" -> (double) player.getAirSupply();
            case "player.maxAir" -> (double) player.getMaxAirSupply();
            case "player.airPercent" -> {
                int max = player.getMaxAirSupply();
                yield max <= 0 ? Double.NaN : player.getAirSupply() / (double) max;
            }
            case "player.armor" -> (double) player.getArmorValue();
            case "player.absorption" -> (double) player.getAbsorptionAmount();
            case "player.xpLevel" -> (double) player.experienceLevel;
            case "player.xpPercent" -> (double) player.experienceProgress;
            default -> null;
        };
    }

    private String vanillaText(String key) {
        if (player == null) {
            return null;
        }
        return switch (key) {
            case "player.pose" -> player.getPose().name();
            case "player.gamemode" -> gameMode == null ? null : gameMode.getName();
            default -> null;
        };
    }
}

package net.bullettrain.xenopixelsmod.features.progression;

import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Super Soul–style passives. Equip by holding the soul item and using
 * {@code /xenosoul equip} or right-click equip on the item.
 */
public final class SuperSoulCatalog {
    public static final Map<String, SoulDef> SOULS = new LinkedHashMap<>();

    static {
        SOULS.put("warrior", new SoulDef("warrior", "Warrior's Pride",
                "Outgoing damage +10%", 1.10f, 1.0f, 1.0f, 1.0f));
        SOULS.put("iron", new SoulDef("iron", "Iron Will",
                "Incoming damage -8%", 1.0f, 0.92f, 1.0f, 1.0f));
        SOULS.put("spark", new SoulDef("spark", "Sparking Heart",
                "Sparking build +25%", 1.0f, 1.0f, 1.25f, 1.0f));
        SOULS.put("finisher", new SoulDef("finisher", "Finisher Soul",
                "Ultimate damage +20%", 1.0f, 1.0f, 1.0f, 1.20f));
        SOULS.put("balanced", new SoulDef("balanced", "Balanced Spirit",
                "Damage +5%, taken -4%", 1.05f, 0.96f, 1.0f, 1.0f));
    }

    private SuperSoulCatalog() {}

    public record SoulDef(
            String id,
            String title,
            String desc,
            float outDamageMult,
            float inDamageMult,
            float sparkingBuildMult,
            float ultimateMult) {}

    public static SoulDef get(String id) {
        if (id == null || id.isBlank()) return null;
        return SOULS.get(id.toLowerCase());
    }

    public static SoulDef equipped(ServerPlayer player) {
        if (player == null) return null;
        XenoPlayerData data = XenoCapabilities.get(player).orElse(null);
        if (data == null) return null;
        return get(data.getSuperSoulId());
    }

    public static float outMult(ServerPlayer player) {
        SoulDef s = equipped(player);
        return s != null ? s.outDamageMult : 1f;
    }

    public static float inMult(ServerPlayer player) {
        SoulDef s = equipped(player);
        return s != null ? s.inDamageMult : 1f;
    }

    public static float sparkMult(ServerPlayer player) {
        SoulDef s = equipped(player);
        return s != null ? s.sparkingBuildMult : 1f;
    }

    public static float ultMult(ServerPlayer player) {
        SoulDef s = equipped(player);
        return s != null ? s.ultimateMult : 1f;
    }

    public static String soulIdFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getString("SuperSoulId");
    }
}

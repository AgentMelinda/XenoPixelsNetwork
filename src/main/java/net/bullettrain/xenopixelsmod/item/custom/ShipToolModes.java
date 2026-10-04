package net.bullettrain.xenopixelsmod.item.custom;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/** Persists {@link ShipToolMode} on the held stack. */
public final class ShipToolModes {
    private static final String TAG = "ShipToolMode";

    private ShipToolModes() {}

    public static ShipToolMode get(ItemStack stack, ShipToolMode fallback) {
        if (stack == null || stack.isEmpty()) {
            return fallback;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(TAG)) {
            return fallback;
        }
        return ShipToolMode.byName(tag.getString(TAG));
    }

    public static void set(ItemStack stack, ShipToolMode mode) {
        if (stack == null || stack.isEmpty() || mode == null) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(TAG, mode.name()));
    }

    public static ShipToolMode cycle(ItemStack stack, ShipToolMode fallback) {
        ShipToolMode next = get(stack, fallback).next();
        set(stack, next);
        return next;
    }

    public static void announce(@Nullable Player player, ShipToolMode mode) {
        if (player == null || mode == null) {
            return;
        }
        player.displayClientMessage(Component.literal(
                "§eTool mode: §f" + mode.title() + " §7— " + mode.hint()), true);
    }
}

package net.bullettrain.xenopixelsmod.item.custom;

import net.bullettrain.xenopixelsmod.item.ModItemTags;
import net.bullettrain.xenopixelsmod.missile.MissileSize;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MissileItem extends Item {
    private final MissileSize size;

    public MissileItem(Properties properties, MissileSize size) {
        super(properties.stacksTo(size.stackSize()));
        this.size = size;
    }

    public MissileSize size() {
        return size;
    }

    public static @Nullable MissileSize sizeOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.getItem() instanceof MissileItem missile) return missile.size;
        return null;
    }

    public static boolean isBody(ItemStack stack) {
        return sizeOf(stack) != null || (stack != null && stack.is(ModItemTags.MISSILE_BODIES));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.xenopixelsmod.missile_load"));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}

package net.bullettrain.xenopixelsmod.item.custom;

import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.features.progression.SuperSoulCatalog;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Right-click to equip this Super Soul (passive buff). */
public class SuperSoulItem extends Item {
    private final String soulId;

    public SuperSoulItem(Properties props, String soulId) {
        super(props.stacksTo(1));
        this.soulId = soulId;
    }

    public String getSoulId() {
        return soulId;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            SuperSoulCatalog.SoulDef def = SuperSoulCatalog.get(soulId);
            if (def == null) {
                sp.displayClientMessage(Component.literal("§cUnknown Super Soul"), true);
                return InteractionResultHolder.fail(stack);
            }
            sp.getCapability(XenoCapabilities.XENO_DATA).ifPresent(data -> {
                data.setSuperSoulId(def.id());
                sp.displayClientMessage(Component.literal(
                        "§dEquipped Super Soul: §f" + def.title() + " §7(" + def.desc() + ")"), true);
                net.bullettrain.xenopixelsmod.effect.XenoStatusEffectSync.sync(sp);
            });
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        SuperSoulCatalog.SoulDef def = SuperSoulCatalog.get(soulId);
        if (def != null) {
            tip.add(Component.literal(def.title()).withStyle(ChatFormatting.LIGHT_PURPLE));
            tip.add(Component.literal(def.desc()).withStyle(ChatFormatting.GRAY));
            tip.add(Component.literal("Right-click to equip").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        SuperSoulCatalog.SoulDef def = SuperSoulCatalog.get(soulId);
        if (def != null) {
            return Component.literal("Super Soul: " + def.title());
        }
        return super.getName(stack);
    }
}

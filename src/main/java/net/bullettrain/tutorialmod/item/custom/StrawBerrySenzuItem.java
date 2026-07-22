package net.bullettrain.tutorialmod.item.custom;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class StrawBerrySenzuItem extends Item {
    public StrawBerrySenzuItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity); // מפעיל את ה-food effects
        if (!level.isClientSide && entity instanceof Player player) {
            player.getCooldowns().addCooldown(this, 20 * 30); // 30 שניות (20 טיקים = שנייה)
        }
        return result;
    }

    // אופציונלי: לחסום התחלת אכילה אם עדיין ב-cooldown
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            if (!level.isClientSide) {
                int ticksLeft = player.getCooldowns().getCooldownPercent(this, 1.0f) > 0
                        ? 0 : 0; // ראה הערה למטה
                player.displayClientMessage(
                        Component.translatable("message.tutorialmod.senzu_cooldown"),
                        true // true = action bar, false = chat
                );
            }
            return InteractionResultHolder.fail(stack);
        }
        return super.use(level, player, hand);
    }
}

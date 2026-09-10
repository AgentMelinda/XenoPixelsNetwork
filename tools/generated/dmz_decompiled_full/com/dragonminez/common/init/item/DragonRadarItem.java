package com.dragonminez.common.init.item;

import com.dragonminez.common.dragonball.DragonBallDefinitions;
import com.dragonminez.common.dragonball.DragonRadarDefinition;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.quest.QuestUnlocks;
import java.util.Arrays;
import java.util.List;
import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

@ParametersAreNonnullByDefault
public class DragonRadarItem extends Item {
   public static final String NBT_RANGE = "RadarRange";
   private static final int COOLDOWN_TICKS = 320;
   public static final String QUEST_RADAR_AMPLIFIER = "bulma_radar_amplifier";
   public static final String QUEST_PROXIMITY_HUD = "bulma_proximity_hud";
   private static final int AMPLIFIED_RANGE = 600;
   private final String radarDefinitionId;

   public static boolean hasCompletedQuest(Player player, String questId) {
      return QuestUnlocks.isCompleted(player, questId);
   }

   public DragonRadarItem(String radarDefinitionId) {
      super(new Properties().stacksTo(1));
      this.radarDefinitionId = radarDefinitionId;
   }

   public DragonRadarDefinition getDefinition() {
      return DragonBallDefinitions.getRadar(this.radarDefinitionId);
   }

   public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (hand == InteractionHand.OFF_HAND && !player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
         return InteractionResultHolder.fail(stack);
      } else if (player.getCooldowns().isOnCooldown(this)) {
         return InteractionResultHolder.fail(stack);
      } else {
         player.playSound((SoundEvent)MainSounds.DRAGONRADAR.get());
         if (!world.isClientSide()) {
            DragonRadarDefinition definition = this.getDefinition();
            int currentRange = ((CustomData)stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)).copyTag().getInt("RadarRange");
            int[] ranges = definition == null ? new int[0] : this.effectiveRanges(definition, player);
            int newRange = ranges.length == 0 ? currentRange : this.getNextRange(ranges, currentRange);
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("RadarRange", newRange));
            player.displayClientMessage(Component.translatable("gui.dmzradar.range", new Object[]{newRange}), true);
         }

         player.getCooldowns().addCooldown(this, 320);
         return InteractionResultHolder.sidedSuccess(stack, world.isClientSide());
      }
   }

   private int[] effectiveRanges(DragonRadarDefinition definition, Player player) {
      int[] base = definition.getRanges();
      if (!hasCompletedQuest(player, "bulma_radar_amplifier")) {
         return base;
      } else {
         for (int r : base) {
            if (r == 600) {
               return base;
            }
         }

         int[] extended = Arrays.copyOf(base, base.length + 1);
         extended[base.length] = 600;
         return extended;
      }
   }

   private int getNextRange(int[] ranges, int currentRange) {
      if (ranges.length == 0) {
         return currentRange;
      } else {
         for (int i = 0; i < ranges.length; i++) {
            if (ranges[i] == currentRange) {
               return ranges[(i + 1) % ranges.length];
            }
         }

         return ranges[0];
      }
   }

   public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, List<Component> tooltip, @NotNull TooltipFlag isAdvanced) {
      DragonRadarDefinition definition = this.getDefinition();
      if (definition != null) {
         tooltip.add(Component.translatable(definition.getTooltipKey()).withStyle(ChatFormatting.GRAY));
      }
   }

   public String getRadarDefinitionId() {
      return this.radarDefinitionId;
   }
}

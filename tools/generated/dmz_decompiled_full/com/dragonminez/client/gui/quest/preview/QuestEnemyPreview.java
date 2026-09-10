package com.dragonminez.client.gui.quest.preview;

import com.dragonminez.client.util.TextUtil;
import com.dragonminez.common.init.entities.ITextureVariant;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import com.dragonminez.common.quest.Difficulty;
import com.dragonminez.common.quest.Quest;
import com.dragonminez.common.quest.QuestObjective;
import com.dragonminez.common.quest.QuestUnlocks;
import com.dragonminez.common.quest.objectives.KillObjective;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class QuestEnemyPreview {
   private static final ResourceLocation DMZ_FONT = ResourceLocation.fromNamespaceAndPath("dragonminez", "smooth");
   private static final float YAW_DEG_PER_SEC = 55.0F;
   private static final int CYCLE_TICKS = 110;
   private static final int FADE_TICKS = 8;
   private static final float HOVER_SPEED = 6.0F;
   private final List<QuestEnemyPreview.Target> targets = new ArrayList<>();
   private final Map<String, LivingEntity> entityCache = new HashMap<>();
   private final Set<String> failedIds = new HashSet<>();
   private Quest boundQuest = null;
   private Difficulty boundDifficulty = Difficulty.NORMAL;
   private int boundPartySize = 1;
   private int currentIndex = 0;
   private int cycleTimer = 0;
   private float modelYaw = 0.0F;
   private float hoverProgress = 0.0F;
   private int hitX;
   private int hitY;
   private int hitW;
   private int hitH;

   public boolean isActive() {
      return !this.targets.isEmpty();
   }

   public boolean hasMultipleTargets() {
      return this.targets.size() > 1;
   }

   public boolean advanceTarget() {
      if (this.targets.size() <= 1) {
         return false;
      } else {
         this.currentIndex = (this.currentIndex + 1) % this.targets.size();
         this.cycleTimer = 0;
         this.hoverProgress = 0.0F;
         return true;
      }
   }

   public void setQuest(Quest quest, Difficulty difficulty, int partySize) {
      if (difficulty == null) {
         difficulty = Difficulty.NORMAL;
      }

      int safePartySize = Math.max(1, partySize);
      if (quest != this.boundQuest || difficulty != this.boundDifficulty || safePartySize != this.boundPartySize) {
         this.boundQuest = quest;
         this.boundDifficulty = difficulty;
         this.boundPartySize = safePartySize;
         this.targets.clear();
         this.currentIndex = 0;
         this.cycleTimer = 0;
         this.hoverProgress = 0.0F;
         if (quest != null && quest.isSagaQuest()) {
            for (QuestObjective objective : quest.getObjectives()) {
               if (objective instanceof KillObjective kill) {
                  this.targets.add(new QuestEnemyPreview.Target(quest, kill, difficulty, safePartySize));
               }
            }
         }
      }
   }

   public void clientTick() {
      if (!this.targets.isEmpty()) {
         LivingEntity entity = this.getCurrentEntity();
         if (entity != null) {
            entity.tickCount++;
         }

         if (this.targets.size() > 1) {
            this.cycleTimer++;
            if (this.cycleTimer >= 110) {
               this.cycleTimer = 0;
               this.currentIndex = (this.currentIndex + 1) % this.targets.size();
            }
         }
      }
   }

   public void render(GuiGraphics graphics, Font font, int regionX, int regionY, int regionW, int regionH, int mouseX, int mouseY, float dt, float visibility) {
      if (!this.targets.isEmpty() && !(visibility <= 0.02F)) {
         this.modelYaw += 55.0F * dt;
         if (this.modelYaw >= 360.0F) {
            this.modelYaw -= 360.0F;
         }

         LivingEntity entity = this.getCurrentEntity();
         int centerX = regionX + regionW / 2;
         int feetY = regionY + (int)((float)regionH * 0.46F);
         this.hitW = (int)((float)regionW * 0.5F);
         this.hitH = (int)((float)regionH * 0.48F);
         this.hitX = centerX - this.hitW / 2;
         this.hitY = regionY + (int)((float)regionH * 0.08F);
         boolean hovered = this.isHovering(mouseX, mouseY) && visibility > 0.6F;
         float hoverTarget = hovered ? 1.0F : 0.0F;
         this.hoverProgress = approach(this.hoverProgress, hoverTarget, 6.0F * dt);
         int alpha = (int)(visibility * 255.0F) & 0xFF;
         MutableComponent header = this.tr("gui.dragonminez.quest_tree.preview.target");
         TextUtil.drawCenteredStringWithBorder(graphics, font, header, centerX, regionY + 6, withAlpha(-2513855, alpha));
         if (this.targets.size() > 1) {
            Component counter = this.txt("‹ " + (this.currentIndex + 1) + "/" + this.targets.size() + " ›");
            int counterColor = hovered ? -8054 : -4473925;
            TextUtil.drawStringWithBorder(graphics, font, counter, regionX + regionW - font.width(counter) - 82, regionY + 18, withAlpha(counterColor, alpha));
         }

         if (entity != null && visibility > 0.4F) {
            int scale = this.computeModelScale(entity, regionH);
            this.renderRotatingEntity(graphics, centerX, feetY, scale, entity);
         } else if (entity == null) {
            TextUtil.drawCenteredStringWithBorder(
               graphics, font, this.tr("gui.dragonminez.quest_tree.preview.unknown"), centerX, regionY + regionH / 2, withAlpha(-5614251, alpha)
            );
         }

         Component name = this.getTargetName(entity);
         TextUtil.drawCenteredStringWithBorder(graphics, font, name, centerX, feetY + 6, withAlpha(-1, alpha));
         if (this.hoverProgress > 0.01F) {
            this.renderStatsCard(graphics, font, entity, regionX, regionY, regionW, regionH, alpha);
         }
      }
   }

   private void renderRotatingEntity(GuiGraphics graphics, int x, int y, int scale, LivingEntity entity) {
      Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
      Quaternionf cameraOrientation = new Quaternionf();
      float yaw = this.modelYaw;
      entity.yBodyRot = yaw;
      entity.yBodyRotO = yaw;
      entity.setYRot(yaw);
      entity.yRotO = yaw;
      entity.setXRot(0.0F);
      entity.xRotO = 0.0F;
      entity.yHeadRot = yaw;
      entity.yHeadRotO = yaw;
      graphics.pose().pushPose();
      graphics.pose().translate(0.0, 0.0, 150.0);
      InventoryScreen.renderEntityInInventory(graphics, (float)x, (float)y, (float)scale, new Vector3f(0.0F, 0.0F, 0.0F), pose, cameraOrientation, entity);
      graphics.pose().popPose();
   }

   private void renderStatsCard(GuiGraphics graphics, Font font, LivingEntity entity, int regionX, int regionY, int regionW, int regionH, int baseAlpha) {
      float ease = easeOut(this.hoverProgress);
      int cardAlpha = (int)((float)baseAlpha * ease) & 0xFF;
      if (cardAlpha > 4) {
         QuestEnemyPreview.Target target = this.targets.get(this.currentIndex);
         List<Component> lines = new ArrayList<>();
         if (entity instanceof DBSagasEntity dbz) {
            String bpValue = QuestUnlocks.isCompleted(Minecraft.getInstance().player, "bulma_scouter_calibration")
               ? abbreviate((long)dbz.getBattlePower())
               : "???";
            lines.add(this.stat("gui.dragonminez.quest_tree.preview.battle_power", bpValue, -14249));
         }

         lines.add(this.stat("gui.dragonminez.quest_tree.preview.health", formatNumber(target.health), -38037));
         if (target.meleeDamage > 0.0) {
            lines.add(this.stat("gui.dragonminez.quest_tree.preview.melee", formatNumber(target.meleeDamage), -2056096));
         }

         if (target.kiDamage > 0.0) {
            lines.add(this.stat("gui.dragonminez.quest_tree.preview.ki", formatNumber(target.kiDamage), -9455617));
         }

         List<Component> threats = this.collectThreats(entity);
         if (!threats.isEmpty()) {
            lines.add(this.tr("gui.dragonminez.quest_tree.preview.threats").withStyle(s -> s.withBold(true)));
            if (QuestUnlocks.isCompleted(Minecraft.getInstance().player, "bulma_scouter_threat_db")) {
               lines.addAll(threats);
            } else {
               lines.add(this.tr("gui.dragonminez.quest_tree.preview.scan_required").withStyle(s -> s.withColor(-7829368)));
            }
         }

         int pad = 5;
         int lineH = 9 + 1;
         int contentW = 0;

         for (Component line : lines) {
            contentW = Math.max(contentW, font.width(line));
         }

         int cardW = contentW + pad * 2;
         int cardH = lines.size() * lineH + pad * 2;
         int shift = (int)((1.0F - ease) * 12.0F);
         int cardRight = regionX + regionW - 4 + shift;
         int cardX = Math.max(regionX + 2, cardRight - cardW);
         int cardY = Math.max(regionY + 2, regionY + regionH - cardH - 4);
         int bg = withAlpha(-16119276, (int)((float)cardAlpha * 0.88F));
         int border = withAlpha(-2513855, cardAlpha);
         graphics.fill(cardX, cardY, cardRight, cardY + cardH, bg);
         graphics.fill(cardX, cardY, cardRight, cardY + 1, border);
         graphics.fill(cardX, cardY + cardH - 1, cardRight, cardY + cardH, border);
         int ty = cardY + pad;

         for (Component line : lines) {
            int lineX = cardRight - pad - font.width(line);
            TextUtil.drawStringWithBorder(graphics, font, line, lineX, ty, withAlpha(-1, cardAlpha));
            ty += lineH;
         }
      }
   }

   private List<Component> collectThreats(LivingEntity entity) {
      List<Component> out = new ArrayList<>();
      if (!(entity instanceof DBSagasEntity dbz)) {
         return out;
      } else {
         HashSet seen = new HashSet();

         for (DBSagasEntity.KiSkill skill : dbz.getSkillPool()) {
            DBSagasEntity.KiSkillType type = DBSagasEntity.KiSkillType.fromId(skill.id);
            if (type != null) {
               String key = "gui.dragonminez.quest_tree.preview.skill." + type.name().toLowerCase();
               if (seen.add(key)) {
                  out.add(this.txt("• ").append(this.tr(key)).withStyle(s -> s.withColor(-7686920)));
               }
            }
         }

         int[] combos = dbz.getAllowedCombos();
         if (combos != null) {
            for (int id : combos) {
               DBSagasEntity.ComboType type = DBSagasEntity.ComboType.fromId(id);
               if (type != null) {
                  String key = "gui.dragonminez.quest_tree.preview.combo." + type.name().toLowerCase();
                  if (seen.add(key)) {
                     out.add(this.txt("• ").append(this.tr(key)).withStyle(s -> s.withColor(-1013110)));
                  }
               }
            }
         }

         return out;
      }
   }

   private Component getTargetName(LivingEntity entity) {
      return entity != null
         ? entity.getType().getDescription().copy().withStyle(Style.EMPTY.withFont(DMZ_FONT))
         : this.txt(this.targets.get(this.currentIndex).entityId);
   }

   private LivingEntity getCurrentEntity() {
      if (this.targets.isEmpty()) {
         return null;
      } else {
         QuestEnemyPreview.Target target = this.targets.get(this.currentIndex);
         String id = target.entityId;
         LivingEntity entity;
         if (this.entityCache.containsKey(id)) {
            entity = this.entityCache.get(id);
         } else {
            if (this.failedIds.contains(id)) {
               return null;
            }

            entity = this.createDummy(id);
            if (entity == null) {
               this.failedIds.add(id);
               return null;
            }

            this.entityCache.put(id, entity);
         }

         if (entity instanceof ITextureVariant variantEntity) {
            int desiredVariant = target.textureVariant >= 0 ? target.textureVariant : 0;
            if (variantEntity.getTextureVariant() != desiredVariant) {
               variantEntity.setTextureVariant(desiredVariant);
            }
         }

         return entity;
      }
   }

   private LivingEntity createDummy(String id) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return null;
      } else {
         EntityType<?> type = resolveType(id);
         if (type == null) {
            return null;
         } else {
            try {
               Entity created = type.create(mc.level);
               if (created instanceof LivingEntity) {
                  return (LivingEntity)created;
               }
            } catch (Exception var6) {
            }

            return null;
         }
      }
   }

   private static EntityType<?> resolveType(String id) {
      if (id != null && id.startsWith("#")) {
         ResourceLocation tagId = ResourceLocation.tryParse(id.substring(1));
         if (tagId == null) {
            return null;
         } else {
            TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, tagId);
            Iterator var3 = BuiltInRegistries.ENTITY_TYPE.getTagOrEmpty(tag).iterator();
            if (var3.hasNext()) {
               Holder<EntityType<?>> holder = (Holder<EntityType<?>>)var3.next();
               return (EntityType<?>)holder.value();
            } else {
               return null;
            }
         }
      } else {
         ResourceLocation rl = ResourceLocation.tryParse(id);
         return rl == null ? null : (EntityType)BuiltInRegistries.ENTITY_TYPE.get(rl);
      }
   }

   public void clear() {
      for (LivingEntity entity : this.entityCache.values()) {
         if (entity != null) {
            entity.discard();
         }
      }

      this.entityCache.clear();
      this.failedIds.clear();
      this.targets.clear();
      this.boundQuest = null;
      this.boundDifficulty = Difficulty.NORMAL;
      this.boundPartySize = 1;
   }

   public boolean isHovering(int mouseX, int mouseY) {
      return mouseX >= this.hitX && mouseX <= this.hitX + this.hitW && mouseY >= this.hitY && mouseY <= this.hitY + this.hitH;
   }

   private int computeModelScale(LivingEntity entity, int regionH) {
      float bbHeight = Math.max(0.8F, entity.getBbHeight());
      int scale = (int)((float)regionH * 0.2F / bbHeight);
      return Math.max(5, Math.min(28, scale));
   }

   private MutableComponent stat(String key, String value, int valueColor) {
      return this.tr(key).append(this.txt(": ").withStyle(s -> s.withColor(-5592406))).append(this.txt(value).withStyle(s -> s.withColor(valueColor)));
   }

   private static String formatNumber(double v) {
      return v == Math.floor(v) && !Double.isInfinite(v) ? abbreviate((long)v) : String.format("%.1f", v);
   }

   private static String abbreviate(long v) {
      if (v >= 1000000000L) {
         return trim((double)v / 1.0E9) + "B";
      } else if (v >= 1000000L) {
         return trim((double)v / 1000000.0) + "M";
      } else {
         return v >= 10000L ? trim((double)v / 1000.0) + "K" : String.format("%,d", v);
      }
   }

   private static String trim(double d) {
      String s = String.format("%.1f", d);
      return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
   }

   private static int withAlpha(int argb, int alpha) {
      return Math.min(alpha, 255) << 24 | argb & 16777215;
   }

   private static float approach(float current, float target, float step) {
      if (current < target) {
         return Math.min(target, current + step);
      } else {
         return current > target ? Math.max(target, current - step) : current;
      }
   }

   private static float easeOut(float t) {
      if (t <= 0.0F) {
         return 0.0F;
      } else {
         return t >= 1.0F ? 1.0F : 1.0F - (1.0F - t) * (1.0F - t);
      }
   }

   private MutableComponent tr(String key, Object... args) {
      return Component.translatable(key, args).withStyle(Style.EMPTY.withFont(DMZ_FONT));
   }

   private MutableComponent txt(String text) {
      return Component.literal(text).withStyle(Style.EMPTY.withFont(DMZ_FONT));
   }

   private static final class Target {
      final String entityId;
      final double health;
      final double meleeDamage;
      final double kiDamage;
      final int count;
      final int textureVariant;

      Target(Quest quest, KillObjective obj, Difficulty difficulty, int partySize) {
         this.entityId = obj.getEntityId();
         this.health = quest.getScaledKillHealth(obj, partySize) * difficulty.hpMultiplier();
         this.meleeDamage = quest.getScaledKillMeleeDamage(obj, partySize) * difficulty.damageMultiplier();
         this.kiDamage = quest.getScaledKillKiDamage(obj, partySize) * difficulty.damageMultiplier();
         this.count = obj.getCount();
         this.textureVariant = obj.getTextureVariant();
      }
   }
}

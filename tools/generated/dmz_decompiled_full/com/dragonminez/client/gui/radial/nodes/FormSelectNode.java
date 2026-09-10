package com.dragonminez.client.gui.radial.nodes;

import com.dragonminez.client.gui.radial.AbstractRadialNode;
import com.dragonminez.client.gui.radial.FormPreview;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.ExecuteActionC2S;
import com.dragonminez.common.network.C2S.SelectFormC2S;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.extras.ActionMode;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class FormSelectNode extends AbstractRadialNode {
   private final String race;
   private final String group;
   private final String form;
   private final boolean stack;
   private final ResourceLocation icon;
   private final int tint;

   public FormSelectNode(String race, String group, String form, boolean stack) {
      this.race = race;
      this.group = group;
      this.form = form;
      this.stack = stack;
      FormConfig config = stack ? ConfigManager.getStackFormGroup(group) : ConfigManager.getFormGroup(race, group);
      String formType = config != null ? config.getFormType() : "";
      this.icon = iconForFormType(formType);
      FormConfig.FormData formData = stack ? ConfigManager.getStackForm(group, form) : ConfigManager.getForm(race, group, form);
      this.tint = tintOf(formData);
   }

   @Override
   public String orderKey() {
      return (this.stack ? "stack:" : "form:") + this.group + ":" + this.form;
   }

   @Override
   public Component label(StatsData stats) {
      return this.stack
         ? Component.translatable("race.dragonminez.stack.form." + this.group + "." + this.form)
         : Component.translatable("race.dragonminez." + this.race + ".form." + this.group + "." + this.form);
   }

   @Override
   public ResourceLocation icon(StatsData stats) {
      return this.icon;
   }

   @Override
   public int iconTint(StatsData stats) {
      return this.tint;
   }

   @Override
   public boolean active(StatsData stats) {
      Character character = stats.getCharacter();
      ActionMode mode = stats.getStatus().getSelectedAction();
      return this.stack
         ? mode == ActionMode.STACK
            && this.group.equalsIgnoreCase(character.getSelectedStackFormGroup())
            && this.form.equalsIgnoreCase(character.getSelectedStackForm())
         : mode == ActionMode.FORM && this.group.equalsIgnoreCase(character.getSelectedFormGroup()) && this.form.equalsIgnoreCase(character.getSelectedForm());
   }

   @Override
   public int labelColor(StatsData stats) {
      return this.active(stats) ? 2883328 : 16718592;
   }

   @Override
   public FormPreview preview(StatsData stats) {
      return new FormPreview(this.group, this.form, this.stack);
   }

   @Override
   public void onSelect(StatsData stats) {
      NetworkHandler.sendToServer(new SelectFormC2S(this.group, this.form, this.stack));
      this.playToggle(true);
   }

   @Override
   public void onDoubleSelect(StatsData stats) {
      NetworkHandler.sendToServer(new SelectFormC2S(this.group, this.form, this.stack));
      NetworkHandler.sendToServer(new ExecuteActionC2S(ExecuteActionC2S.ActionType.INSTANT_TRANSFORM));
      this.playToggle(true);
   }
}

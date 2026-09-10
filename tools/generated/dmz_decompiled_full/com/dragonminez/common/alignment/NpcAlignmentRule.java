package com.dragonminez.common.alignment;

import com.dragonminez.common.combat.logic.player.TargetHelper;

public record NpcAlignmentRule(
   TargetHelper.Relation defaultRelation, Integer minInteractionAlignment, Integer maxInteractionAlignment, Integer hostileBelow, Integer hostileAbove
) {
   public NpcAlignmentRule(
      TargetHelper.Relation defaultRelation, Integer minInteractionAlignment, Integer maxInteractionAlignment, Integer hostileBelow, Integer hostileAbove
   ) {
      defaultRelation = defaultRelation != null ? defaultRelation : TargetHelper.Relation.NEUTRAL;
      this.defaultRelation = defaultRelation;
      this.minInteractionAlignment = minInteractionAlignment;
      this.maxInteractionAlignment = maxInteractionAlignment;
      this.hostileBelow = hostileBelow;
      this.hostileAbove = hostileAbove;
   }

   public boolean allowsInteraction(int alignment) {
      return this.minInteractionAlignment != null && alignment < this.minInteractionAlignment
         ? false
         : this.maxInteractionAlignment == null || alignment <= this.maxInteractionAlignment;
   }

   public boolean isHostileFor(int alignment) {
      return this.hostileBelow != null && alignment < this.hostileBelow ? true : this.hostileAbove != null && alignment > this.hostileAbove;
   }
}

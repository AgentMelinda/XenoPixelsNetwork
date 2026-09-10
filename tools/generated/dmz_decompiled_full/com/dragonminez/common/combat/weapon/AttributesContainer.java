package com.dragonminez.common.combat.weapon;

import java.util.Objects;
import org.jetbrains.annotations.Nullable;

public final class AttributesContainer {
   @Nullable
   private final String parent;
   @Nullable
   private final WeaponAttributes attributes;

   public AttributesContainer(@Nullable String parent, @Nullable WeaponAttributes attributes) {
      this.parent = parent;
      this.attributes = attributes;
   }

   public String parent() {
      return this.parent;
   }

   public WeaponAttributes attributes() {
      return this.attributes;
   }

   @Override
   public boolean equals(Object obj) {
      if (obj == this) {
         return true;
      } else if (obj != null && obj.getClass() == this.getClass()) {
         AttributesContainer that = (AttributesContainer)obj;
         return Objects.equals(this.parent, that.parent) && Objects.equals(this.attributes, that.attributes);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.parent, this.attributes);
   }

   @Override
   public String toString() {
      return "AttributesContainer[parent=" + this.parent + ", attributes=" + this.attributes + "]";
   }
}

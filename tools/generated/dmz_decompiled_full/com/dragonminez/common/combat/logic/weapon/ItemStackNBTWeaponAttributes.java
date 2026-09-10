package com.dragonminez.common.combat.logic.weapon;

import com.dragonminez.common.combat.weapon.WeaponAttributes;
import org.jetbrains.annotations.Nullable;

public interface ItemStackNBTWeaponAttributes {
   boolean hasInvalidAttributes();

   void setInvalidAttributes(boolean var1);

   @Nullable
   WeaponAttributes getWeaponAttributes();

   void setWeaponAttributes(@Nullable WeaponAttributes var1);
}

package com.dragonminez.client.animation;

public interface IPlayerAnimatable {
   void dragonminez$setFlying(boolean var1);

   boolean dragonminez$isFlying();

   void dragonminez$triggerDash(int var1);

   void dragonminez$triggerEvasion();

   void dragonminez$setShootingKi(boolean var1);

   boolean dragonminez$isShootingKi();

   void dragonminez$playMeleeAnimation(String var1, boolean var2, float var3);

   boolean dragonminez$isPlayingCombatAnimation();

   boolean dragonminez$isAttackingWithOffhand();

   float dragonminez$getCombatPlacementWeight();

   void dragonminez$playKiAnimation(String var1, boolean var2);

   void dragonminez$stopKiAnimation();

   String dragonminez$getCurrentPlayingAnimation();
}

<div dir="rtl">

# 🔧 שלב 29 — אנימציית תקיפה (Attack Animation)

בשלב הקודם יצרנו את הקרנף שהוא חיה ביישנית. עכשיו אנחנו מוסיפים לו **יכולת תקיפה**: הוא ירדוף אחרי השחקן, יניף את הראש ואז ינשוך. זה דורש שילוב של מטרת AI חדשה (`RhinoAttackGoal`), סנכרון מצב התקיפה בין השרת ללקוח, ואנימציה ייעודית שהגדרנו כבר ב-`ModAnimationDefinitions` (ה-`RHINO_ATTACK`).

## מה השתנה לעומת השלב הקודם

| קובץ | מה נוסף |
|---|---|
| `entity/ai/RhinoAttackGoal.java` | מטרת ה-AI שמנהלת את ההתקפה |
| `entity/custom/RhinoEntity.java` | מצב תקיפה מסונכרן + הפעלת אנימציית תקיפה |
| `entity/client/RhinoModel.java` | שורה אחת שמפעילה את אנימציית התקיפה במודל |
| `entity/animations/ModAnimationDefinitions.java` | נוספה הגדרת `RHINO_ATTACK` (כבר הייתה, מופעלת עכשיו) |

---

## מטרת ההתקפה — `entity/ai/RhinoAttackGoal.java`

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.entity.ai;

import net.bullettrain.tutorialmod.entity.custom.RhinoEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

public class RhinoAttackGoal extends MeleeAttackGoal {
    private final RhinoEntity entity;
    private int attackDelay = 40;
    private int ticksUntilNextAttack = 40;
    private boolean shouldCountTillNextAttack = false;

    public RhinoAttackGoal(PathfinderMob pMob, double pSpeedModifier, boolean pFollowingTargetEvenIfNotSeen) {
        super(pMob, pSpeedModifier, pFollowingTargetEvenIfNotSeen);
        entity = ((RhinoEntity) pMob);
    }

    @Override
    public void start() {
        super.start();
        attackDelay = 40;
        ticksUntilNextAttack = 40;
    }

    @Override
    protected void checkAndPerformAttack(LivingEntity pEnemy, double pDistToEnemySqr) {
        if (isEnemyWithinAttackDistance(pEnemy, pDistToEnemySqr)) {
            shouldCountTillNextAttack = true;

            if(isTimeToStartAttackAnimation()) {
                entity.setAttacking(true);
            }

            if(isTimeToAttack()) {
                this.mob.getLookControl().setLookAt(pEnemy.getX(), pEnemy.getEyeY(), pEnemy.getZ());
                performAttack(pEnemy);
            }
        } else {
            resetAttackCooldown();
            shouldCountTillNextAttack = false;
            entity.setAttacking(false);
            entity.attackAnimationTimeout = 0;
        }
    }

    private boolean isEnemyWithinAttackDistance(LivingEntity pEnemy, double pDistToEnemySqr) {
        return pDistToEnemySqr <= this.getAttackReachSqr(pEnemy);
    }

    protected void resetAttackCooldown() {
        this.ticksUntilNextAttack = this.adjustedTickDelay(attackDelay * 2);
    }

    protected boolean isTimeToAttack() {
        return this.ticksUntilNextAttack <= 0;
    }

    protected boolean isTimeToStartAttackAnimation() {
        return this.ticksUntilNextAttack <= attackDelay;
    }

    protected void performAttack(LivingEntity pEnemy) {
        this.resetAttackCooldown();
        this.mob.swing(InteractionHand.MAIN_HAND);
        this.mob.doHurtTarget(pEnemy);
    }

    @Override
    public void tick() {
        super.tick();
        if(shouldCountTillNextAttack) {
            this.ticksUntilNextAttack = Math.max(this.ticksUntilNextAttack - 1, 0);
        }
    }

    @Override
    public void stop() {
        entity.setAttacking(false);
        super.stop();
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `extends MeleeAttackGoal` | יורש מהתנהגות תקיפה מקרוב של Minecraft ומרחיב אותה |
| `attackDelay` / `ticksUntilNextAttack` | זמני קירור: מתי להתחיל אנימציה ומתי להכות בפועל |
| `checkAndPerformAttack` | נקראת כשיש אויב בטווח — בודקת מרחק וקובעת אם להתחיל אנימציה/להתקיף |
| `entity.setAttacking(true)` | מסמן לישות שהיא בתקיפה (כדי להפעיל אנימציה מסונכרנת) |
| `performAttack` | מאפסת קירור, מניעה את היד וגורמת נזק (`doHurtTarget`) |
| `tick()` | מפחיתה את המונה כל פריים כשצריך |
| `stop()` | כשהמטרה נעצרת — מסיים את מצב התקיפה |

---

## סנכרון מצב התקיפה — `RhinoEntity.java`

<div dir="ltr">

```java
private static final EntityDataAccessor<Boolean> ATTACKING =
        SynchedEntityData.defineId(RhinoEntity.class, EntityDataSerializers.BOOLEAN);

public final AnimationState attackAnimationState = new AnimationState();
public int attackAnimationTimeout = 0;

// בתוך tick():
if(this.isAttacking() && attackAnimationTimeout <= 0) {
    attackAnimationTimeout = 80; // Length in ticks of your animation
    attackAnimationState.start(this.tickCount);
} else {
    --this.attackAnimationTimeout;
}
if(!this.isAttacking()) {
    attackAnimationState.stop();
}

public void setAttacking(boolean attacking) { this.entityData.set(ATTACKING, attacking); }
public boolean isAttacking() { return this.entityData.get(ATTACKING); }

@Override
protected void defineSynchedData() {
    super.defineSynchedData();
    this.entityData.define(ATTACKING, false);
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `EntityDataAccessor<Boolean> ATTACKING` | נתון מסונכרן (synched) — נשלח מהשרת לכל הלקוחות |
| `attackAnimationState` | מצב אנימציה שיופעל בזמן התקיפה |
| `isAttacking() && attackAnimationTimeout <= 0` | אם בתקיפה ועדיין לא התחלנו אנימציה — מפעילים אותה ל-80 טיקים |
| `defineSynchedData()` | רושם את הנתון המסונכרן עם ערך התחלתי `false` |

בנוסף הוספנו את המטרה ב-`registerGoals`:
<div dir="ltr">

```java
this.goalSelector.addGoal(1, new RhinoAttackGoal(this, 1.0D, true));
this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
```

</div>
וכשורה אחת ב-`RhinoModel.setupAnim`:
<div dir="ltr">

```java
this.animate(((RhinoEntity) entity).attackAnimationState, ModAnimationDefinitions.RHINO_ATTACK, ageInTicks, 1f);
```

</div>
## מושגי מפתח

| מושג | הסבר |
|---|---|
| `MeleeAttackGoal` | התנהגות תקיפה מקרוב מובנית של Minecraft |
| `SynchedEntityData` | מערכת לסנכרון נתונים מהשרת ללקוח (למשל "האם התוקף") |
| `EntityDataAccessor` | עמודת נתונים מסונכרנת אחת בתוך ה-SynchedEntityData |
| `AnimationState` | מפעיל אנימציה מסוימת (כאן `RHINO_ATTACK`) לפרק זמן |

<div dir="ltr">

⬅️ [שלב 28](Step-28-Entity) · ➡️ [שלב 30](Step-30-Block-Entity)

</div>

</div>
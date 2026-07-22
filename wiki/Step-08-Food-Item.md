<div dir="rtl">

# 🔧 שלב 08 — פריט מאכל (Food Item)

בשלב הזה אנו מוסיפים את **הפריט הראשון עם מאפייני מזון** — **Strawberry**! 🇬🇧
פריט זה ממלא **רעב (hunger)** ומעניק **האצת תנועה** למשך מספר שניות כאשר נאכל.

זהו הדרך שבה Minecraft מממש את מערכת המזון — כל פריט מוגדר עם מאפייני מזון
(MobEffects) ספציפיים, והמשחק אחראי על חישוב הערכים.

---

## מה השתנה לעומת השלב הקודם

| קובץ | שינוי |
|---|---|
| `src/main/java/.../item/ModCreativeModTabs.java` | הוספת `STRAWBERRY` ל-tab |
| `src/main/java/.../item/ModFoods.java` | **חדש** — הגדרת מאפייני המזון |
| `src/main/java/.../item/ModsItems.java` | רישום `STRAWBERRY` עם `ModFoods.STRAWBERRY` |
| `src/main/resources/.../lang/en_us.json` | הוספת שם תצוגה |
| `src/main/resources/.../models/item/strawberry.json` | **חדש** |
| `src/main/resources/.../textures/item/strawberry.png` | **חדש** |

---

## מחלקת ModFoods.java

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.item;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;

public class ModFoods {
    public static final FoodProperties STRAWBERRY = new FoodProperties.Builder().nutrition(2).fast()
            .saturationMod(0.2f).effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200), 0.1f).build();
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `new FoodProperties.Builder()` | בונה את מאפייני המזון |
| `.nutrition(2)` | כמות הרעב שהפריט ממלא — 2 מתוך 20 |
| `.fast()` | מזון מהיר — ניתן לאכול במהירות |
| `.saturationMod(0.2f)` | אורך Zeitgis של השובע |
| `.effect(() -> new MobEffectInstance(...), 0.1f)` | אפקט הניתן — 10% סיכוי |
| `MobEffects.MOVEMENT_SPEED` | האפקט: האצת תנועה (Speed) |
| `200` | משך האפקט ב-ticks (200 = 10 שניות) |

---

## עדכון ModsItems.java

<div dir="ltr">

```java
import net.bullettrain.tutorialmod.item.ModFoods;
// ...

public static final RegistryObject<Item> STRAWBERRY = ITEMS.register("strawberry",
        () -> new Item(new Item.Properties().food(ModFoods.STRAWBERRY)));
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `new Item.Properties().food(ModFoods.STRAWBERRY)` | מגדיר שהפריט הוא מזון עם מאפייני ה-Strawberry |

---

## עדכון ModCreativeModTabs.java

<div dir="ltr">

```java
pOutput.accept(ModsItems.STRAWBERRY.get());
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `pOutput.accept(ModsItems.STRAWBERRY.get());` | מוסיף את ה-Strawberry ל-Creative Tab |

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| **FoodProperties** | מחלקה שמגדירה את מאפייני המזון (רעב, עמידות, אפקטים) |
| **nutrition** | כמות הרעב שהפריט ממלא |
| **saturationMod** | מעריך את הזמן עד שהשחקן ירגיש רעב שוב |
| **MobEffectInstance** | אפקט סטטוס (Potion Effect) עם משך ועוצמה |
| **MobEffects** | Enum של כל האפקטים הזמינים ב-Minecraft |

---

<div dir="ltr">

⬅️ [שלב 07](Step-07-Advanced-Block) · ➡️ [שלב 09](Step-09-Fuel-Item)

</div>

</div>

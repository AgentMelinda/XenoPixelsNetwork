<div dir="rtl">

# 🔧 שלב 09 — פריט דלק (Fuel Item)

בשלב הזה אנו יוצרים את **פריט הדלק הראשון** במוד — **Pine Cone**! 🇬🇧
פריט זה ניתן להניח בכבשן (furnace) או בכבשן ברזי (blast furnace)
והוא **מספק דלק למשך 400 ticks** (20 שניות).

כאשר Minecraft בודקת אם ניתן לבשל בכבשן עם פריט מסוים, היא שואלת את הפריט:
"מהו זמן הבליעה שלך?" (`getBurnTime`). אנו דורסים (override) מתודה זו כדי להחזיר ערך קבוע.

---

## מה השתנה לעומת השלב הקודם

| קובץ | שינוי |
|---|---|
| `src/main/java/.../item/ModCreativeModTabs.java` | הוספת `PINE_CONE` ל-tab |
| `src/main/java/.../item/ModsItems.java` | רישום `PINE_CONE` עם `FuelItem` ו-burnTime 400 |
| `src/main/java/.../item/custom/FuelItem.java` | **חדש** — מחלקת הפריט עם לוגיקת דלק |
| `src/main/resources/.../lang/en_us.json` | הוספת שם תצוגה |
| `src/main/resources/.../models/item/pine_cone.json` | **חדש** |
| `src/main/resources/.../textures/item/pine_cone.png` | **חדש** |

---

## מחלקת FuelItem.java

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.item.custom;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

public class FuelItem extends Item {
    private int burnTime = 0;

    public FuelItem(Properties pProperties, int burnTime) {
        super(pProperties);
        this.burnTime = burnTime;
    }

    @Override
    public int getBurnTime(ItemStack itemStack, @Nullable RecipeType<?> recipeType) {
        return this.burnTime;
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `public class FuelItem extends Item` | יורש ממחלקת ה-Item הבסיסית |
| `private int burnTime = 0;` | שדה פרטי ששומר את זמן הבליעה |
| `public FuelItem(Properties pProperties, int burnTime)` | בורר שמקבל גם מאפייני פריט וגם זמן דלק |
| `this.burnTime = burnTime;` | שומר את זמן הדלק למשתמש מאוחר יותר |
| `public int getBurnTime(ItemStack itemStack, @Nullable RecipeType<?> recipeType)` | מתודה שקורית Minecraft כאשר בודקת אם פריט הוא דלק |
| `return this.burnTime;` | מחזיר את זמן הדלק ב-ticks |

> 💡 400 ticks = 20 שניות. להמחשה:
> - Coal = 800 ticks (40 שניות)
> - Blaze Rod = 2400 ticks (120 שניות)

---

## עדכון ModsItems.java

<div dir="ltr">

```java
import net.bullettrain.tutorialmod.item.custom.FuelItem;
// ...

public static final RegistryObject<Item> PINE_CONE = ITEMS.register("pine_cone",
        () -> new FuelItem(new Item.Properties(), 400));
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `new FuelItem(new Item.Properties(), 400)` | יוצר פריט דלק עם 400 ticks דלק |

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| **Fuel** | פריט/בלוק שיכול להישרף בכבשן כדי לבשל |
| **getBurnTime** | מתודה שקורית quando Minecraft בודקת אם פריט הוא דלק |
| **RecipeType** | סוג המתכון (smelting, blasting, smoking וכו') |
| **@Nullable** | ה-annotation שמסמלת שהערך יכול להיות null |

---

<div dir="ltr">

⬅️ [שלב 08](Step-08-Food-Item) · ➡️ [שלב 10](Step-10-Tooltips)

</div>

</div>

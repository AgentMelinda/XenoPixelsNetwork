<div dir="rtl">

# 🔧 שלב 11 — Tags (תיוגים)

בשלב הזה אנו לומדים על **Tags** — מערכת התיוג של Minecraft שמאפשרת לקבץ
procedural blocks ו-items לקבוצות עם משמעות משחקית.

עד כה, ה-Metal Detector ידע לחפש **רק** `Blocks.IRON_ORE` ו-`Blocks.DIAMOND_ORE` בקוד.
כעת נהפוך זאת ל-**Tag** שנקרא `metal_detector_valuables`, כך שנוכל להוסיף עופרות
גם בעתיד בלי לשנות קוד Java.

---

## מה השתנה לעומת השלב הקודם

| קובץ | שינוי |
|---|---|
| `src/main/java/.../item/custom/MetalDetectorItem.java` | שינוי `isValuableBlock` להשתמש ב-`ModTags` |
| `src/main/java/.../util/ModTags.java` | **חדש** — הגדרת ה-Tags |
| `src/main/resources/data/tutorialmod/tags/blocks/metal_detector_valuables.json` | **חדש** — קובץ ה-Tag |

---

## מחלקת ModTags.java

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.util;

import net.bullettrain.tutorialmod.TutorialMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class ModTags {
    public static class Blocks {
        public static final TagKey<Block> METAL_DETECTOR_VALUABLES = tag("metal_detector_valuables");

        private static TagKey<Block> tag(String name) {
            return BlockTags.create(new ResourceLocation(TutorialMod.MOD_ID, name));
        }
    }

    public static class Items {
        private static TagKey<Item> tag(String name) {
            return ItemTags.create(new ResourceLocation(TutorialMod.MOD_ID, name));
        }
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `public static class Blocks` | מחלקה פנימית שמאחסנת tags של בלוקים |
| `public static final TagKey<Block> METAL_DETECTOR_VALUABLES` | ה-Tag שנקרא `metal_detector_valuables` |
| `tag("metal_detector_valuables")` | מתודת עזר היוצרת TagKey |
| `BlockTags.create(new ResourceLocation(TutorialMod.MOD_ID, name))` | יוצרת tag עם שם ייחודי |
| `TagKey<Block>` | סוג הגeneric שמסמל tag לבלוקים |
| `ResourceLocation` | מזהו ייחודי בפורמט `modid:name` |

---

## עדכון MetalDetectorItem.java

<div dir="ltr">

```java
import net.bullettrain.tutorialmod.util.ModTags;
// ...

private boolean isValuableBlock(BlockState state) {
    return state.is(ModTags.Blocks.METAL_DETECTOR_VALUABLES);
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `state.is(ModTags.Blocks.METAL_DETECTOR_VALUABLES)` | בודק אם הבלוק שייך ל-tag `metal_detector_valuables` |

> 💚 לפני השינוי היינו צריכים לכתוב `state.is(Blocks.IRON_ORE) || state.is(Blocks.DIAMOND_ORE)`.
> כעת עם Tag, ההוספה של עופרת חדשה היא רק הוספת שורה לקובץ JSON.

---

## קובץ Tag — metal_detector_valuables.json

<div dir="ltr">

```json
{
  "values": [
    "tutorialmod:sapphire_ore",
    "#forge:ores"
  ]
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `"values": [...]` | רשימת הערכים שייכנסו ל-tag |
| `"tutorialmod:sapphire_ore"` | עופרת הספיר של המוד |
| `"#forge:ores"` | הפניה ל-tag אחר — כל עופרת ב-Forge תיכנס |

> 💡 הסימן `#` לפני שם משמעו הפניה ל-tag אחר. זה מאפשר הרחבה דינמית.

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| **Tag** | קבוצת blocks/items שמשתמשים בה לצורך משותף |
| **TagKey\<T\>** | המזהה הייחודי של ה-Tag (Generic type) |
| **ResourceLocation** | מזהו ייחודי בפורמט `namespace:path` |
| **state.is(TagKey)** | בודק אם הבלוק/פריט שייך ל-Tag |
| **#forge:ores** | הפניה ל-tag קיים — כולל את כל העפרות של Minecraft |

---

<div dir="ltr">

⬅️ [שלב 10](Step-10-Tooltips) · ➡️ [שלב 12](Step-12-Data-Generation)

</div>

</div>

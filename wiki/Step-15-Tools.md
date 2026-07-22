<div dir="rtl">

# 🔧 שלב 15 — כלים (Tools)

בשלב הקודם יצרנו פריט תלת-ממדי (מטה Sapphire Staff). כעת אנו נלמד ליצור **סט כלי מותאם**
מחודש — מכוש, גרזן, חרב, pointed pickaxe ו-juda. הכלים משתמשים ברמת חומר (Tier) חדשה
שאנו מגדירים: **Sapphire Tier**.

> 💡 כדי ליצור Tier חדש נדרשת מחלקת `ModToolTiers` שמשתמשת ב-`ForgeTier` ו-`TierSortingRegistry`.

---

## מה השתנה לעומת השלב הקודם

| קבצים חדשים/ששונו | תיאור |
|---|---|
| `ModToolTiers.java` | מחלקת Tier חדשה ל-Sapphire |
| `ModsItems.java` | 5 כלי sapphire חדשים |
| `ModItemModelProvider.java` | `handheldItem(...)` למודלי כלים |
| `ModBlockTagGenerator.java` | תגית `needs_sapphire_tool` |
| `ModTags.java` | `NEEDS_SAPPHIRE_TOOL` |
| `en_us.json` | שמות לכלי Sapphire |
| `textures/item/` | 5 קבצי טקסטורה לכלים |

---

## קוד חדש ב-`ModToolTiers.java`

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.item;

import net.bullettrain.tutorialmod.TutorialMod;
import net.bullettrain.tutorialmod.util.ModTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.ForgeTier;
import net.minecraftforge.common.TierSortingRegistry;

import java.util.List;

public class ModToolTiers {
    public static final Tier SAPPHIRE = TierSortingRegistry.registerTier(
            new ForgeTier(5, 1500, 5f, 4f, 25,
                    ModTags.Blocks.NEEDS_SAPPHIRE_TOOL, () -> Ingredient.of(ModsItems.SAPPHIRE.get())),
            new ResourceLocation(TutorialMod.MOD_ID, "sapphire"), List.of(Tiers.NETHERITE), List.of());
}
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `new ForgeTier(5, 1500, 5f, 4f, 25, ...)` | 5=רמת עריכת 블וקים, 1500=נשאיות, 5f=מהירות כריתת בלוקים, 4f=נזק לכלי, 25=הסת curses |
| `ModTags.Blocks.NEEDS_SAPPHIRE_TOOL` | תגית הבלוקים שדורשים כלי Sapphire |
| `() -> Ingredient.of(ModsItems.SAPPHIRE.get())` | חומרי תיקון — ניתן לתקן כלי Sapphire ב-Sapphire |
| `TierSortingRegistry.registerTier(...)` | רושם את ה-Tier כך שהמשחק יודע לסדר lui |
| `List.of(Tiers.NETHERITE)` | Sapphire טוענת **אחרי** Netherite בסדר ה-tier |
| `List.of()` | אין טיירים שלפני Sapphire |

---

## קוד חדש ב-`ModsItems.java`

<div dir="ltr">

```java
public static final RegistryObject<Item> SAPPHIRE_SWORD = ITEMS.register("sapphire_sword",
        () -> new SwordItem(ModToolTiers.SAPPHIRE, 4, 2, new Item.Properties()));
public static final RegistryObject<Item> SAPPHIRE_PICKAXE = ITEMS.register("sapphire_pickaxe",
        () -> new PickaxeItem(ModToolTiers.SAPPHIRE, 1, 1, new Item.Properties()));
public static final RegistryObject<Item> SAPPHIRE_AXE = ITEMS.register("sapphire_axe",
        () -> new AxeItem(ModToolTiers.SAPPHIRE, 7, 1, new Item.Properties()));
public static final RegistryObject<Item> SAPPHIRE_SHOVEL = ITEMS.register("sapphire_shovel",
        () -> new ShovelItem(ModToolTiers.SAPPHIRE, 0, 0, new Item.Properties()));
public static final RegistryObject<Item> SAPPHIRE_HOE = ITEMS.register("sapphire_hoe",
        () -> new HoeItem(ModToolTiers.SAPPHIRE, 0, 0, new Item.Properties()));
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `SwordItem(ModToolTiers.SAPPHIRE, 4, 2, ...)` | 4=נזק נגד יצורים, 2=מהירות תקיפה |
| `PickaxeItem(ModToolTiers.SAPPHIRE, 1, 1, ...)` | 1=מהירות כרייתブロックים, 1=מהירות תקיפה |
| `AxeItem(ModToolTiers.SAPPHIRE, 7, 1, ...)` | 7=נזק, 1=מהירות תקיפה |
| `ShovelItem(ModToolTiers.SAPPHIRE, 0, 0, ...)` | 0=מהירות כרייה, 0=מהירות תקיפה |
| `HoeItem(ModToolTiers.SAPPHIRE, 0, 0, ...)` | 0=מהירות עבודת קרקע, 0=מהירות תקיפה |

---

## קוד חדש ב-`ModItemModelProvider.java`

<div dir="ltr">

```java
@Override
protected void registerModels() {
    // ... קוד קודם ...

    handheldItem(ModsItems.SAPPHIRE_SWORD);
    handheldItem(ModsItems.SAPPHIRE_PICKAXE);
    handheldItem(ModsItems.SAPPHIRE_AXE);
    handheldItem(ModsItems.SAPPHIRE_SHOVEL);
    handheldItem(ModsItems.SAPPHIRE_HOE);
}

private ItemModelBuilder handheldItem(RegistryObject<Item> item) {
    return withExistingParent(item.getId().getPath(),
            new ResourceLocation("item/handheld")).texture("layer0",
            new ResourceLocation(TutorialMod.MOD_ID,"item/" + item.getId().getPath()));
}
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `new ResourceLocation("item/handheld")` | משתמש בתבנית "שחור" של פריט שנלחץ ביד |
| `"layer0": "tutorialmod:item/sapphire_sword"` | מגדיר את הטקסטורה הספציפית לכל כלי |

---

## קוד חדש ב-`ModBlockTagGenerator.java`

<div dir="ltr">

```java
this.tag(Tags.Blocks.NEEDS_SAPPHIRE_TOOL)
        .add(ModBlocks.SAPPHIRE_BLOCK.get());
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `Tags.Blocks.NEEDS_SAPPHIRE_TOOL` | תגית שמורה שמגדירה אילו בלוקים דורשים כלי Sapphire לכרייה |
| `.add(ModBlocks.SAPPHIRE_BLOCK.get())` | `SAPPHIRE_BLOCK` דורש כלי Sapphire (אל תשבור אותו ביד) |

---

## קוד חדש ב-`ModTags.java`

<div dir="ltr">

```java
public static class Blocks {
    public static final TagKey<Block> METAL_DETECTOR_VALUABLES = tag("metal_detector_valuables");
    public static final TagKey<Block> NEEDS_SAPPHIRE_TOOL = tag("needs_sapphire_tool");
    // ...
}
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `TagKey<Block> NEEDS_SAPPHIRE_TOOL` | מגדיר תגית בלוקים חדשה עם מרחב שמות `tutorialmod` |
| `tag("needs_sapphire_tool")` | יוצר ResourceLocation `tutorialmod:needs_sapphire_tool` |

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| **`Tier`** | רמת כלי — קובע מהירות כרייה, נזק, נשאיות ומעבדות |
| **`ForgeTier`** | מימוש מותאם של `Tier` שנוצר על ידי Forge |
| **`TierSortingRegistry`** | רושם Tier כך שהמשחק יודע לסדר lui |
| **`NEEDS_SAPPHIRE_TOOL`** | תגית בלוקים שדורשים כלי Sapphire או טרighter |
| **`Ingredient.of(...)`** | מחומרי תיקון — מגדיר מה ניתן להשתמש כדי לתקן את הכלי |

---

<div dir="ltr">

⬅️ [שלב 14](Step-14-2D-Textures-3D-Model) · ➡️ [שלב 16](Step-16-Armor)

</div>

</div>

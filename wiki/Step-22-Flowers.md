<div dir="rtl">

# 🔧 שלב 22 — פרחים (Flowers)

בשלב הקודם יצרנו גידול דו-ממדי (Corn). כעת אנו ממשיכים עם **צמחי נוי** —
**Catmint** כפרח רגיל ו-**Potted Catmint** כפרח בכוס.

> 💡 `FlowerBlock` הוא `CropBlock` מותאם שלא ניתן לגדל — הוא רק נותן אפקט (במקרה שלנו,
> `MobEffects.LUCK` למשך 5 שניות). `FlowerPotBlock` מוסיף את הצמח לכדור פרחים.

---

## מה השתנה לעומת השלב הקודם

| קבצים חדשים/ששונו | תיאור |
|---|---|
| `ModBlocks.java` | `CATMINT` + `POTTED_CATMINT` |
| `ModBlockStateProvider.java` | `flowerBlock` + `pottedFlower` |
| `ModItemModelProvider.java` | מודלים לפריטים |
| `ModBlockLootTables.java` | loot tables לפרח ולכדור |
| `TutorialMod.java` | `FlowerPotBlock.addPlant(...)` ב-`commonSetup` |
| `ModCreativeModTabs.java` | הוספת הפרחים לטאב |
| `en_us.json` | שם "Catmint" |
| `textures/block/` | 2 תמונות (פריח כדור) |

---

## קוד חדש ב-`ModBlocks.java`

<div dir="ltr">

```java
public static final RegistryObject<Block> CATMINT = registerBlock("catmint",
        () -> new FlowerBlock(() -> MobEffects.LUCK, 5,
                BlockBehaviour.Properties.copy(Blocks.ALLIUM).noOcclusion().noCollission()));
public static final RegistryObject<Block> POTTED_CATMINT = BLOCKS.register("potted_catmint",
        () -> new FlowerPotBlock(() -> ((FlowerPotBlock) Blocks.FLOWER_POT), ModBlocks.CATMINT,
                BlockBehaviour.Properties.copy(Blocks.POTTED_ALLIUM).noOcclusion()));
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `FlowerBlock(() -> MobEffects.LUCK, 5, ...)` | פרח שנותן Luck level 1 למשך 5 שניות כאשר נגעים בו |
| `BlockBehaviour.Properties.copy(Blocks.ALLIUM)`| לוכד תכונות מפרח Allium (גודל, צבע) |
| `FlowerPotBlock(() -> ((FlowerPotBlock) Blocks.FLOWER_POT), ...)`| יוצר כדור פרחים עם CATMINT בפנים |
| `Blocks.FLOWER_POT` | מניע את כדור הפרחים הרגיל (כדי שנרחיב אותו) |

---

## שינוי ב-`TutorialMod.java`

<div dir="ltr">

```java
private void commonSetup(final FMLCommonSetupEvent event) {
    event.enqueueWork(() -> {
        ((FlowerPotBlock) Blocks.FLOWER_POT).addPlant(ModBlocks.CATMINT.getId(), ModBlocks.POTTED_CATMINT);
    });
}
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `FlowerPotBlock.addPlant(...)` | רושם את הפרח כך שהמשחק יודע להניח אותו בכדור פרחים |

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| **`FlowerBlock`** | גידול קטן שנותן אפקט למגע (כמו Allium, Poppy) |
| **`FlowerPotBlock`** | כדור פרחים — מחזיק פרח/עציצה |
| **`MobEffects.LUCK`** | אפקט Luck — מגדיל ממוצע loot |

---

<div dir="ltr">

⬅️ [שלב 21](Step-21-Two-Block-High-Crops) · ➡️ [שלב 23](Step-23-Villager-Trades)

</div>

</div>

<div dir="rtl">

# 🔧 שלב 20 — בלוק גידול (Crop Block)

בשלבים קודמים יצרנו בלוקים פשוטים ומורכבים. כעת אנו נלמד ליצור **בלוק גידול (Crop) מותאם** —
לא wheat רגיל, אלא **Strawberry Crop** עם 6 שלבי גידול (age 0–5) ועם פריטי זרעים
וסוף יבול מותאמים.

> 💡 `CropBlock` היא מחלקה בסיסית של Minecraft שמנהלת את הגיל, הצמיחה והיבול.
> אנו רק צריכים להגדיר: מה הזרע, כמה שלבים, ואיך נראים בהם המודלים.

---

## מה השתנה לעומת השלב הקודם

| קבצים חדשים/ששונו | תיאור |
|---|---|
| `custom/StrawberryCropBlock.java` | מחלקת Crop מותאמת |
| `ModBlocks.java` | רישום `STRAWBERRY_CROP` |
| `ModsItems.java` | `STRAWBERRY_SEEDS` כ-`ItemNameBlockItem` |
| `item/ModFoods.java` | מאפייני מזון ל-`STRAWBERRY` |
| `ModBlockStateProvider.java` | `makeStrawberryCrop(...)` עם `ConfiguredModel` |
| `ModItemModelProvider.java` | `simpleItem` לזרעים |
| `ModBlockLootTables.java` | loot table עם `createCropDrops` |
| `ModCreativeModTabs.java` | הוספת זרעים ותפוחים לטאב |
| `en_us.json` | שמות חדשים |
| `textures/block/` | 6 תמונות שלביות |
| `textures/item/` | תמונות לזרעים ותפוח |

---

## קוד חדש ב-`custom/StrawberryCropBlock.java`

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.block.custom;

import net.bullettrain.tutorialmod.item.ModsItems;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class StrawberryCropBlock extends CropBlock {
    public static final int MAX_AGE = 5;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_5;

    public StrawberryCropBlock(Properties pProperties) {
        super(pProperties);
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return ModsItems.STRAWBERRY_SEEDS.get();
    }

    @Override
    public IntegerProperty getAgeProperty() {
        return AGE;
    }

    @Override
    public int getMaxAge() {
        return MAX_AGE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(AGE);
    }
}
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `extends CropBlock` | מ-inherits את כל לוגיקת הגידול מהבסיס של Minecraft |
| `BlockStateProperties.AGE_5` | מאפיין גיל מ-0 ל-5 (6 שלבים) |
| `getBaseSeedId()` | מגדיר את הפריט שמופיע ב-inventory כ"זרעים" |
| `getAgeProperty()` | מחזיר את מאפיין הגיל (`AGE`) |
| `getMaxAge()` | מגדיר את הגיל המקסימלי ל-5 |
| `createBlockStateDefinition(...)`| רושם את מאפיין הגיל ל-`BlockState` |

---

## קוד חדש ב-`ModBlocks.java`

<div dir="ltr">

```java
public static final RegistryObject<Block> STRAWBERRY_CROP = BLOCKS.register("strawberry_crop",
        () -> new StrawberryCropBlock(BlockBehaviour.Properties.copy(Blocks.WHEAT).noOcclusion().noCollission()));
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `BlockBehaviour.Properties.copy(Blocks.WHEAT)`| לוכד תכונות מבלוק החיטה (גודל, צבע, תחושת צעדים) |
| `.noOcclusion()` | מונע מהבלוק לכסות Cubes סמוכים (כמו גידול) |
| `.noCollission()` | אין התנגשות — השחקן עובר דרכו |

---

## קוד חדש ב-`ModsItems.java`

<div dir="ltr">

```java
public static final RegistryObject<Item> STRAWBERRY_SEEDS = ITEMS.register("strawberry_seeds",
        () -> new ItemNameBlockItem(ModBlocks.STRAWBERRY_CROP.get(), new Item.Properties()));
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `new ItemNameBlockItem(ModBlocks.STRAWBERRY_CROP.get(), ...)`| פריט שמקם את הבלוק כאשר הוא משמש (כמו seeds רגילים) |

---

## קוד חדש ב-`item/ModFoods.java`

<div dir="ltr">

```java
public static final FoodProperties STRAWBERRY = new FoodProperties.Builder().nutrition(2).fast()
        .saturationMod(0.2f).effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200), 0.1f).build();
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `nutrition(2)` | 2 נקודות רעב (כמו תפוז) |
| `.fast()` | אוכל מהר (כמו תפוז/עוגיה) |
| `saturationMod(0.2f)` | 0.2 שביעות — רעב יורד לאט |
| `effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200), 0.1f)` | 10% סיכוי לקבל Swiftness ל-10 שניות |

---

## קוד חדש ב-`ModBlockStateProvider.java`

<div dir="ltr">

```java
public void makeStrawberryCrop(CropBlock block, String modelName, String textureName) {
    Function<BlockState, ConfiguredModel[]> function = state -> strawberryStates(state, block, modelName, textureName);
    getVariantBuilder(block).forAllStates(function);
}

private ConfiguredModel[] strawberryStates(BlockState state, CropBlock block, String modelName, String textureName) {
    ConfiguredModel[] models = new ConfiguredModel[1];
    models[0] = new ConfiguredModel(models().crop(modelName + state.getValue(((StrawberryCropBlock) block).getAgeProperty()),
            new ResourceLocation(TutorialMod.MOD_ID, "block/" + textureName + state.getValue(((StrawberryCropBlock) block).getAgeProperty()))).renderType("cutout"));
    return models;
}
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `getVariantBuilder(block).forAllStates(function)`| עובר על כל מצב `BlockState` של הגידול |
| `models().crop(...)` | יוצר model פשוט עם טקסטורה `crop` (render type cutout) |
| `renderType("cutout")` | משתמש ב-cutout render type כדי שרקע ה-transparent יראו |

---

## קוד חדש ב-`ModBlockLootTables.java`

<div dir="ltr">

```java
LootItemCondition.Builder lootitemcondition$builder = LootItemBlockStatePropertyCondition
        .hasBlockStateProperties(ModBlocks.STRAWBERRY_CROP.get())
        .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(StrawberryCropBlock.AGE, 5));

this.add(ModBlocks.STRAWBERRY_CROP.get(), createCropDrops(ModBlocks.STRAWBERRY_CROP.get(), ModsItems.STRAWBERRY.get(),
        ModsItems.STRAWBERRY_SEEDS.get(), lootitemcondition$builder));
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `hasProperty(StrawberryCropBlock.AGE, 5)` | תנאי: רק כאשר הגידול בגיל 5 (בשלב סיום) |
| `createCropDrops(...)` | יוצר loot table שמחזיר 1-3 זרעים + 1 תפוח אם בגיל מלא |

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| **`CropBlock`** | מחלקה בסיסית לגידול — מנהלת גיל וצמיחה |
| **`ItemNameBlockItem`** | פריט שמקים בלוק כאשר משתמשים בו (כמו seeds) |
| **`BlockStateProperties.AGE_5`** | מאפיין גיל מ-0 ל-5 (6 שלבים) |
| **`createCropDrops(...)`** | יוצר loot table שמחזיר זרעים + יבול כשהגידול מלא |

---

<div dir="ltr">

⬅️ [שלב 19](Step-19-Suspicious-Sand-Item) · ➡️ [שלב 21](Step-21-Two-Block-High-Crops)

</div>

</div>

<div dir="rtl">

# 🔧 שלב 03 — בלוקים מותאמים (Custom Blocks)

בשלב הזה אנו מוסיפים את **הבלוקים הראשונים** למוד שלנו! 🇬🇧
ניצור שני בלוקים חדשים — **Jackietonite Ore Block** ו-**Raw Jackietonite Ore Block** — ונלמד כיצד
ליצור גם את ה-BlockItem שמייצג אותם ב-hand.

> 💡 **במוד שלנו** שמות הבלוקים הם `jackietonite_ore_block` / `raw_jackietonite_ore_block` (לא sapphire). package: `net.bullettrain.tutorialmod`.

עד כה המוד שלנו כבר מכיל פריטים, אבל בלי בלוקים אין מה לשים בעולם. בלוקים הם
**הבסיס של כל מבנה** — הבית שלכם, הכוורת ומכונות החקלאות — כולם בנויים מבלוקים.

---

## מה השתנה לעומת השלב הקודם

| קובץ | שינוי |
|---|---|
| `src/main/java/.../TutorialMod.java` | רישום `ModBlocks` לאוטובוס |
| `src/main/java/.../block/ModBlocks.java` | **חדש** — רישום הבלוקים JACKIETONITE_ORE_BLOCK ו-RAW_JACKIETONITE_ORE_BLOCK |
| `src/main/java/.../item/ModCreativeModTabs.java` | הוספת הבלוקים ל-Creative Tab |
| `src/main/resources/.../blockstates/jackietonite_ore_block.json` | **חדש** — קובץ המציין באיזה model להשתמש |
| `src/main/resources/.../blockstates/raw_jackietonite_ore_block.json` | **חדש** |
| `src/main/resources/.../models/block/jackietonite_ore_block.json` | **חדש** — model תלת-ממדי של הבלוק |
| `src/main/resources/.../models/block/raw_jackietonite_ore_block.json` | **חדש** |
| `src/main/resources/.../models/item/jackietonite_ore_block.json` | **חדש** — model הפריט שיופיע ב-hand |
| `src/main/resources/.../models/item/raw_jackietonite_ore_block.json` | **חדש** |
| `src/main/resources/.../lang/en_us.json` | הוספת שמות תצוגה לבלוקים |
| `src/main/resources/.../textures/block/*.png` | **חדש** — טקסטורות לבלוקים |

---

## מחלקת ModBlocks.java

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.block;

import net.bullettrain.tutorialmod.TutorialMod;
import net.bullettrain.tutorialmod.item.ModsItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, TutorialMod.MOD_ID);

    public static final RegistryObject<Block> JACKIETONITE_ORE_BLOCK = registerBlock("jackietonite_ore_block",
            () -> new Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(3.0F, 3.0F)
        )
);
    public static final RegistryObject<Block> RAW_JACKIETONITE_ORE_BLOCK = registerBlock("raw_jackietonite_ore_block",
            () -> new Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(3.0F, 3.0F)
        )
 );

    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block) {
        RegistryObject<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> RegistryObject<Item> registerBlockItem(String name, RegistryObject<T> block) {
        return ModsItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID)` | יוצר דף רישום דחוי לבלוקים |
| `JACKIETONITE_ORE_BLOCK = registerBlock("jackietonite_ore_block", ...)` | רושם בלוק חדש עם ה-name `jackietonite_ore_block` |
| `BlockBehaviour.Properties.of()` | יוצר מאפיינים חדשים מאפס |
| `.mapColor(MapColor.STONE)` | צבע על המפה |
| `.instrument(NoteBlockInstrument.BASEDRUM)` | צליל note block |
| `.requiresCorrectToolForDrops()` | דורש כלי מתאים כדי שייפול שלל |
| `.strength(3.0F, 3.0F)` | חוזק (hardness, resistance) |
| `registerBlockItem(name, toReturn)` | יוצר גם את ה-BlockItem המתאים לבלוק |
| `ModsItems.ITEMS.register(name, () -> new BlockItem(...))` | רושם פריט שמייצג את הבלוק ב-hand |
| `new BlockItem(block.get(), new Item.Properties())` | יוצר פריט-בלוק עם מאפייני פריט רגילים |

---

## עדכון TutorialMod.java

<div dir="ltr">

```java
import net.bullettrain.tutorialmod.block.ModBlocks;
// ...
public TutorialMod() {
    // ...
    ModBlocks.register(modEventBus);
    // ...
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `import net.bullettrain.tutorialmod.block.ModBlocks;` | מייבא את מחלקת הבלוקים |
| `ModBlocks.register(modEventBus);` | רושם את כל הבלוקים לאוטובוס |

---

## עדכון ModCreativeModTabs.java

<div dir="ltr">

```java
// בקובץ הקיים, בתוך displayItems:
pOutput.accept(ModBlocks.JACKIETONITE_ORE_BLOCK.get());
pOutput.accept(ModBlocks.RAW_JACKIETONITE_ORE_BLOCK.get());
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `pOutput.accept(ModBlocks.JACKIETONITE_ORE_BLOCK.get());` | מוסיף את הבלוק ל-Creative Tab |
| `.get()` | מפרק את ה-RegistryObject כדי לקבל את הבלוק בפועל |

---

## Blockstate — jackietonite_ore_block.json

<div dir="ltr">

```json
{
  "variants": {
    "": {
      "model": "tutorialmod:block/jackietonite_ore_block"
    }
  }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `"variants"` | מיפוי בין מצבי בלוק ל-models |
| `"": { "model": ... }` | מצב ברירת מחדל (אין משתנים) משתמש ב-model `jackietonite_ore_block` |

---

## Block Model — jackietonite_ore_block.json

<div dir="ltr">

```json
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "tutorialmod:block/jackietonite_ore_block"
  }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `"parent": "minecraft:block/cube_all"` | משתמש במודל הבסיסי של קובייה מלאה |
| `"all": "tutorialmod:block/jackietonite_ore_block"` | קובע שכל 6 הפאות משתמשות בטקסטורה `jackietonite_ore_block.png` |

---

## Item Model — jackietonite_ore_block.json

<div dir="ltr">

```json
{
  "parent": "tutorialmod:block/jackietonite_ore_block"
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `"parent": "tutorialmod:block/jackietonite_ore_block"` | הפריט ישתמש באותו model כמו הבלוק — כך כאשר אתה מחזיק את הבלוק ב-hand הוא נראה כמו הבלוק בעולם |

---

## עדכון Lang en_us.json

<div dir="ltr">

```json
{
  "block.tutorialmod.jackietonite_ore_block": "Block of Sapphire",
  "block.tutorialmod.raw_jackietonite_ore_block": "Block of Raw Sapphire"
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `"block.tutorialmod.jackietonite_ore_block"` | מפתח התרגום לבלוק |
| `"Block of Sapphire"` | שם תצוגה |

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| **Block** | מחלקהbase של Minecraft לבלוק בעולם |
| **BlockBehaviour.Properties** | קבוצת מאפיינים של הבלוק (חוזק, צליל, צורה וכו') |
| **BlockItem** | פריט שמייצג את הבלוק — נחוץ כדי שהשחקן יוכל להניח/להניח את הבלוק |
| **Blockstate** | קובץ JSON שממפה מצבי בלוק ל-models |
| **block/cube_all** | מודל בסיסי של קובייה עם אותה טקסטורה על כל הפאות |

---

<div dir="ltr">

⬅️ [שלב 02](Step-02-Custom-Items) · ➡️ [שלב 04](Step-04-Recipes)

</div>

</div>

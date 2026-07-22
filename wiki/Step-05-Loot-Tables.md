<div dir="rtl">

# 🔧 שלב 05 — טבלאות שלל (Loot Tables)

בשלב הזה אנו מגדירים **מה נופל** כאשר שחקן שובר בלוקים שונים! 🇬🇧
טבלאות שלל (Loot Tables) הן מערכת של Minecraft שקובעת:
- מהי הפריט/כמות הנופלים כשמשברים בלוק
- האם יש טיפול ב-Silk Touch
- האם יש בונוס מ-Fortune
- איך מתפזרת הפליטה במופעי פיצוץ

בשלב הזה ניצור טבלאות שלל עבור:
- **Sapphire Block** ו-**Raw Sapphire Block** — מפילים את עצמם
- **Sapphire Ore** (4 סוגים) — מפילים Raw Sapphire עם טיפול ב-Silk Touch

---

## מה השתנה לעומת השלב הקודם

| קובץ | שינוי |
|---|---|
| `src/main/java/.../block/ModBlocks.java` | הוספת 4 Ore Blocks |
| `src/main/java/.../item/ModCreativeModTabs.java` | הוספת ה-Ores ל-tab |
| `src/main/resources/.../blockstates/*ore.json` | **חדשים** — blockstates ל-4 סוגי עופרת |
| `src/main/resources/.../models/block/*ore.json` | **חדשים** |
| `src/main/resources/.../models/item/*ore.json` | **חדשים** |
| `src/main/resources/.../lang/en_us.json` | הוספת שמות תצוגה |
| `src/main/resources/.../textures/block/*ore.png` | **חדשים** |
| `src/main/resources/data/minecraft/tags/blocks/mineable/pickaxe.json` | הוספת בלוקים ל-tag |
| `src/main/resources/data/minecraft/tags/blocks/needs_*_tool.json` | הוספת דרישות כלי |
| `src/main/resources/data/tutorialmod/loot_tables/blocks/*.json` | **חדשים** — 8 קבצי loot table |

---

## עדכון ModBlocks.java — הוספת Ore Blocks

<div dir="ltr">

```java
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.DropExperienceBlock;
// ...

public static final RegistryObject<Block> SAPPHIRE_ORE = registerBlock("sapphire_ore",
        () -> new DropExperienceBlock(BlockBehaviour.Properties.copy(Blocks.STONE)
                .strength(2f).requiresCorrectToolForDrops(), UniformInt.of(3, 6)));
public static final RegistryObject<Block> DEEPSLATE_SAPPHIRE_ORE = registerBlock("deepslate_sapphire_ore",
        () -> new DropExperienceBlock(BlockBehaviour.Properties.copy(Blocks.DEEPSLATE)
                .strength(3f).requiresCorrectToolForDrops(), UniformInt.of(3, 7)));
public static final RegistryObject<Block> NETHER_SAPPHIRE_ORE = registerBlock("nether_sapphire_ore",
        () -> new DropExperienceBlock(BlockBehaviour.Properties.copy(Blocks.NETHERRACK)
                .strength(1f).requiresCorrectToolForDrops(), UniformInt.of(3, 7)));
public static final RegistryObject<Block> END_STONE_SAPPHIRE_ORE = registerBlock("end_stone_sapphire_ore",
        () -> new DropExperienceBlock(BlockBehaviour.Properties.copy(Blocks.END_STONE)
                .strength(5f).requiresCorrectToolForDrops(), UniformInt.of(3, 7)));
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `DropExperienceBlock` | בלוק שמוריד XP כאשר נשבר |
| `BlockBehaviour.Properties.copy(Blocks.STONE)` | מעתיק מאפיינים מבלוק האבן (צבע, חוזק בסיסי) |
| `.strength(2f)` | קובע חוזק הבלוק — 2f קשה יותר מאבן רגילה |
| `.requiresCorrectToolForDrops()` | דורש כלי מתאים כדי שהשלל ייפול |
| `UniformInt.of(3, 6)` | טווח XP אקראי — בין 3 ל-6 |
| `Blocks.DEEPSLATE` / `Blocks.NETHERRACK` / `Blocks.END_STONE` | משתמש במאפיינים של בלוקי בסיס שונים |

---

## עדכון Tags — mineable/pickaxe.json

<div dir="ltr">

```json
{
  "values": [
    "tutorialmod:sapphire_block",
    "tutorialmod:raw_sapphire_block",
    "tutorialmod:sapphire_ore",
    "tutorialmod:deepslate_sapphire_ore",
    "tutorialmod:end_stone_sapphire_ore",
    "tutorialmod:nether_sapphire_ore"
  ]
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `"values": [...]` | רשימת הבלוקים השייכים ל-tag |
| `minecraft:mineable/pickaxe` |tag שמגדיר שבלוקים אלה נחצים בעזרת pickaxe |

> 💡 **Tags** (תיוגים) הם קובצי JSON שמקבצים items ו-blocks לצרכים שונים —
> למשל, `mineable/pickaxe` קובע איזה כלי שובר איזה בלוק.

---

## עדכון Tags — needs_diamond_tool.json

<div dir="ltr">

```json
{
  "values": [
    "tutorialmod:raw_sapphire_block"
  ]
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `needs_diamond_tool` | בלוקים שדורשים עופרת (diamond tier) כדי לכרות |

---

## Loot Table — sapphire_block.json

<div dir="ltr">

```json
{
  "type": "minecraft:block",
  "pools": [
    {
      "bonus_rolls": 0.0,
      "conditions": [
        {
          "condition": "minecraft:survives_explosion"
        }
      ],
      "entries": [
        {
          "type": "minecraft:item",
          "name": "tutorialmod:sapphire_block"
        }
      ],
      "rolls": 1.0
    }
  ],
  "random_sequence": "tutorialmod:blocks/sapphire_block"
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `"type": "minecraft:block"` | טבלה של שלל עבור בלוק |
| `"pools": [...]` |groups של גרלות — כל pool מייצר פריטים נפרדים |
| `"condition": "minecraft:survives_explosion"` | הפריט נשמר רק אם הבלוק שרד פיצוץ |
| `"type": "minecraft:item"` | כניסה שמחזירה פריט |
| `"name": "tutorialmod:sapphire_block"` | הפריט המוחזר |
| `"rolls": 1.0` | מספר הגרלות בפעם אחת |
| `"random_sequence"` | מזהה ייחודי לטבלה (מונע כפילויות)

---

## Loot Table — sapphire_ore.json

<div dir="ltr">

```json
{
  "type": "minecraft:block",
  "pools": [
    {
      "bonus_rolls": 0.0,
      "entries": [
        {
          "type": "minecraft:alternatives",
          "children": [
            {
              "type": "minecraft:item",
              "conditions": [
                {
                  "condition": "minecraft:match_tool",
                  "predicate": {
                    "enchantments": [
                      {
                        "enchantment": "minecraft:silk_touch",
                        "levels": {
                          "min": 1
                        }
                      }
                    ]
                  }
                }
              ],
              "name": "tutorialmod:sapphire_ore"
            },
            {
              "type": "minecraft:item",
              "functions": [
                {
                  "add": false,
                  "count": {
                    "type": "minecraft:uniform",
                    "max": 5.0,
                    "min": 2.0
                  },
                  "function": "minecraft:set_count"
                },
                {
                  "enchantment": "minecraft:fortune",
                  "formula": "minecraft:ore_drops",
                  "function": "minecraft:apply_bonus"
                },
                {
                  "function": "minecraft:explosion_decay"
                }
              ],
              "name": "tutorialmod:raw_sapphire"
            }
          ]
        }
      ],
      "rolls": 1.0
    }
  ],
  "random_sequence": "tutorialmod:blocks/sapphire_ore"
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `"type": "minecraft:alternatives"` | בוחר **אחד** מהילדים לפי תנאים |
| `"condition": "minecraft:match_tool"` | תנאי: יש להשתמש בכלי עם השם הנכון |
| `"enchantment": "minecraft:silk_touch"` | אם יש Silk Touch — נופל הבלוק עצמו |
| `"type": "minecraft:item"` (הילד השני) | ברירת מחדל — הפליטה הרגילה |
| `"function": "minecraft:set_count"` | קובע כמות אקראית בין 2 ל-5 |
| `"function": "minecraft:apply_bonus"` | מחשב בונוס לפי השליפה (Fortune) |
| `"formula": "minecraft:ore_drops"` | נוסחת הבונוס הספציפית לעפרות |
| `"function": "minecraft:explosion_decay"` | מפחית כמות אם יש פיצוץ |

> 💡 ה loot tables האלה נוצרים אוטומטית על ידי Data Generator בשלב 12,
> אבל כרגע הן קבצים ידניים ב-`src/main/resources/data/`.

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| **Loot Table** | טבלה שמגדירה מה נופל מאובייקט במשחק כאשר נשבר/נפתח |
| **Pool** | קבוצת גרלות בתוך ה-loot table |
| **Silk Touch** | השדה שמאפשר לקבל את הבלוק עצמו במקום להרוס אותו |
| **Fortune** | השדה שמגביה את כמות ההפélées מעפרות |
| **Tag** | קבוצת אובייקטים (blocks/items) עם תכלית משותפת |

---

<div dir="ltr">

⬅️ [שלב 04](Step-04-Recipes) · ➡️ [שלב 06](Step-06-Advanced-Item)

</div>

</div>

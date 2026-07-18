# TutorialMod — Minecraft Forge Mod (1.20.X)

מוד לימודי ל-Minecraft שנכתב ב-Java עם Forge כ-modding framework.

---

## פריטים

| שם | ID | תיאור |
|---|---|---|
| Sapphire | `tutorialmod:sapphire` | ספיר — פריט רגיל |
| Raw Sapphire | `tutorialmod:raw_sapphire` | ספיר גולמי — פריט רגיל |

---

## בלוקים

| שם | ID | תיאור |
|---|---|---|
| Jackietonite Ore Block | `tutorialmod:jackietonite_ore_block` | בלוק עפרה מותאם אישית, חוזק 3.0, דורש כלי נכון לשבירה, מפיל 3–7 XP |
| Raw Jackietonite Ore Block | `tutorialmod:raw_jackietonite_ore_block` | בלוק עפרה גולמית, חוזק 3.0 |
| Jackietonite Ore | `tutorialmod:jackietonite_ore` | עפרה בסלע רגיל, חוזק 2.0, מפיל 3–6 XP |
| Deepslate Jackietonite Ore | `tutorialmod:deepslate_jackietonite_ore` | עפרה בדיפסלייט, חוזק 3.0, מפיל 3–6 XP |
| Nether Jackietonite Ore | `tutorialmod:nether_jackietonite_ore` | עפרה בנת'ר, חוזק 1.0, מפיל 3–6 XP |
| End Stone Jackietonite Ore | `tutorialmod:end_stone_jackietonite_ore` | עפרה באבן האנד, חוזק 5.0, מפיל 3–6 XP |

---

## טאב קריאייטיב

טאב מותאם אישית בשם **Tutorial Tab** המכיל את כל הפריטים והבלוקים של המוד:
- Sapphire
- Raw Sapphire
- Jackietonite Ore Block
- Raw Jackietonite Ore Block
- Jackietonite Ore
- Deepslate Jackietonite Ore
- Nether Jackietonite Ore
- End Stone Jackietonite Ore

הפריטים מופיעים גם בטאב הוניל **Ingredients**.

---

## מבנה הקוד

```
src/main/java/net/bullettrain/tutorialmod/
├── TutorialMod.java              # נקודת הכניסה הראשית של המוד
├── item/
│   ├── ModsItems.java            # רישום פריטים
│   └── ModCreativeModTabs.java   # רישום טאב קריאייטיב
└── block/
    └── ModBlocks.java            # רישום בלוקים
```

---

## הסבר הקוד

### ModsItems.java — פריטים

```java
public class ModsItems {
    // יצירת רשימת רישום לפריטים — DeferredRegister דוחה את הרישום עד שהמשחק מוכן
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, TutorialMod.MOD_ID);

    // רישום ספיר
    public static final RegistryObject<Item> SAPPHIRE =
        ITEMS.register("sapphire", () -> new Item(new Item.Properties()));

    // רישום ספיר גולמי
    public static final RegistryObject<Item> RAW_SAPPHIRE =
        ITEMS.register("raw_sapphire", () -> new Item(new Item.Properties()));

    // חיבור לאוטובוס האירועים של Forge
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
```

### ModBlocks.java — בלוקים

```java
public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(ForgeRegistries.BLOCKS, TutorialMod.MOD_ID);

    // בלוק עפרה מותאם — עם DropExperienceBlock שמפיל 3–7 XP בשבירה
    public static final RegistryObject<Block> JACKIETONITE_ORE_BLOCK =
        registerBlock("jackietonite_ore_block",
            () -> new DropExperienceBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)               // צבע על המפה
                .instrument(NoteBlockInstrument.BASEDRUM) // צליל note block
                .requiresCorrectToolForDrops()          // דורש כלי נכון לשבירה
                .strength(3.0F, 3.0F),                  // חוזק הבלוק
                UniformInt.of(3, 7)                     // כמות XP אקראית: 3 עד 7
            )
        );

    // בלוק עפרה גולמית — בלוק רגיל ללא XP
    public static final RegistryObject<Block> RAW_JACKIETONITE_ORE_BLOCK =
        registerBlock("raw_jackietonite_ore_block",
            () -> new Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(3.0F, 3.0F)
            )
        );

    // עפרות בסביבות שונות — כולן DropExperienceBlock עם 3–6 XP
    // BlockBehaviour.Properties.copy(Blocks.STONE) מעתיק את המאפיינים מבלוק קיים
    public static final RegistryObject<Block> JACKIETONITE_ORE =
        registerBlock("jackietonite_ore",
            () -> new DropExperienceBlock(BlockBehaviour.Properties.copy(Blocks.STONE)
                .strength(2f).requiresCorrectToolForDrops(), UniformInt.of(3, 6)));

    public static final RegistryObject<Block> DEEPSLATE_JACKIETONITE_ORE =
        registerBlock("deepslate_jackietonite_ore",
            () -> new DropExperienceBlock(BlockBehaviour.Properties.copy(Blocks.STONE)
                .strength(3f).requiresCorrectToolForDrops(), UniformInt.of(3, 6)));

    public static final RegistryObject<Block> NETHER_JACKIETONITE_ORE =
        registerBlock("nether_jackietonite_ore",
            () -> new DropExperienceBlock(BlockBehaviour.Properties.copy(Blocks.STONE)
                .strength(1f).requiresCorrectToolForDrops(), UniformInt.of(3, 6)));

    public static final RegistryObject<Block> END_STONE_JACKIETONITE_ORE =
        registerBlock("end_stone_jackietonite_ore",
            () -> new DropExperienceBlock(BlockBehaviour.Properties.copy(Blocks.STONE)
                .strength(5f).requiresCorrectToolForDrops(), UniformInt.of(3, 6)));

    // רושם בלוק + יוצר לו BlockItem אוטומטית (פריט בתיק)
    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block) {
        RegistryObject<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }
}
```

### ModCreativeModTabs.java — טאב קריאייטיב

```java
public static final RegistryObject<CreativeModeTab> TUTORIAL_TAB =
    CREATIVE_MODE_TABS.register("tutorial_tab",
        () -> CreativeModeTab.builder()
            .icon(() -> new ItemStack(ModsItems.SAPPHIRE.get())) // אייקון הטאב
            .title(Component.translatable("creativetab.tutorial_tab"))
            .displayItems((pParameters, pOutput) -> {
                pOutput.accept(ModsItems.SAPPHIRE.get());
                pOutput.accept(ModsItems.RAW_SAPPHIRE.get());
                pOutput.accept(ModBlocks.JACKIETONITE_ORE_BLOCK.get());
                pOutput.accept(ModBlocks.RAW_JACKIETONITE_ORE_BLOCK.get());
                pOutput.accept(ModBlocks.JACKIETONITE_ORE.get());
                pOutput.accept(ModBlocks.DEEPSLATE_JACKIETONITE_ORE.get());
                pOutput.accept(ModBlocks.NETHER_JACKIETONITE_ORE.get());
                pOutput.accept(ModBlocks.END_STONE_JACKIETONITE_ORE.get());
            })
            .build());
```

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| `DeferredRegister` | דוחה רישום עד שהמשחק מוכן — מונע קריסות |
| `RegistryObject` | מחזיק הפניה לפריט/בלוק אחרי הרישום |
| `IEventBus` | אוטובוס שמעביר אירועים בין Forge לבין המוד |
| `BlockItem` | פריט שמייצג בלוק בתיק השחקן |
| `CreativeModeTab` | טאב בתפריט הקריאייטיב |
| `DropExperienceBlock` | בלוק שמפיל XP כשנשבר — מחליף את `Block` הרגיל |
| `UniformInt.of(min, max)` | מגדיר כמות XP אקראית בין min ל-max בכל שבירה |
| `BlockBehaviour.Properties.copy(...)` | מעתיק מאפיינים מבלוק קיים (כמו `Blocks.STONE`) במקום להגדיר הכל מחדש |

---

## Tags — כלי שבירה

Tags הם קבצי JSON שמגדירים **אילו כלים יכולים לשבור כל בלוק** ובאיזה רמה.

### מה זה Tag?
Tag = רשימה של בלוקים שחולקים תכונה משותפת. Minecraft בודק את הרשימות האלה כדי לדעת אם הכלי שבידך מתאים לשבירת הבלוק.

### קבצי Tags שנוצרו

| קובץ | בלוקים שנכללו | משמעות |
|---|---|---|
| `needs_stone_tool.json` | `jackietonite_ore` | ניתן לשבירה עם כלי אבן ומעלה |
| `needs_iron_tool.json` | `jackietonite_ore_block`, `raw_jackietonite_ore_block` | ניתן לשבירה עם כלי ברזל ומעלה |
| `needs_diamond_tool.json` | `deepslate_jackietonite_ore`, `end_stone_jackietonite_ore` | ניתן לשבירה עם כלי יהלום ומעלה |
| `needs_netherite_tool.json` (Forge) | `nether_jackietonite_ore` | ניתן לשבירה עם כלי נת'ריט בלבד |
| `mineable/pickaxe.json` | כל 6 הבלוקים | כולם נשברים עם **מכוש** בלבד |

### דוגמה לקובץ Tag

```json
{
  "values": [
    "tutorialmod:jackietonite_ore_block",
    "tutorialmod:raw_jackietonite_ore_block"
  ]
}
```

**הסבר:** הקובץ `needs_iron_tool.json` אומר ל-Minecraft שהבלוקים האלה דורשים לפחות כלי ברזל כדי להפיל פריטים. שבירה עם כלי חלש יותר לא תפיל כלום.

---

## Loot Tables — מה נופל מהבלוקים

Loot Table = קובץ JSON שמגדיר **מה נופל** כשבלוק נשבר.

### טבלת Loot Tables

| בלוק | מה נופל | הערות |
|---|---|---|
| `jackietonite_ore_block` | את עצמו | רק אם שורד פיצוץ |
| `raw_jackietonite_ore_block` | את עצמו | רק אם שורד פיצוץ |
| `jackietonite_ore` | 2–5 `raw_sapphire` | עם Fortune מוסיף עוד, עם Silk Touch נופל הבלוק עצמו |

### דוגמה — `jackietonite_ore.json`

```json
{
  "type": "minecraft:block",
  "pools": [{
    "entries": [{
      "type": "minecraft:alternatives",
      "children": [
        {
          "type": "minecraft:item",
          "conditions": [{ "condition": "minecraft:match_tool",
            "predicate": { "enchantments": [{ "enchantment": "minecraft:silk_touch", "levels": { "min": 1 } }] }
          }],
          "name": "tutorialmod:jackietonite_ore"
        },
        {
          "type": "minecraft:item",
          "functions": [
            { "function": "minecraft:set_count", "count": { "type": "minecraft:uniform", "min": 2.0, "max": 5.0 } },
            { "function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops" },
            { "function": "minecraft:explosion_decay" }
          ],
          "name": "tutorialmod:raw_sapphire"
        }
      ]
    }]
  }]
}
```

**הסבר שורה אחר שורה:**
- `minecraft:alternatives` — בודק תנאים לפי סדר, לוקח את הראשון שמתאים
- `match_tool` + `silk_touch` — אם יש Silk Touch → נופל הבלוק עצמו
- `set_count` — קובע כמות: 2 עד 5 raw_sapphire
- `apply_bonus` + `fortune` — Fortune מגדיל את הכמות
- `explosion_decay` — פיצוץ עלול להשמיד חלק מהפריטים

---

## Recipes — מתכונים

### מתכון: jackietonite_ore_block → 9 sapphire

```json
{
  "type": "crafting_shapeless",
  "category": "misc",
  "ingredients": [
    { "item": "tutorialmod:jackietonite_ore_block" }
  ],
  "result": {
    "item": "tutorialmod:sapphire",
    "count": 9
  }
}
```

**הסבר:**
- `crafting_shapeless` — מתכון **ללא צורה** (אפשר לשים בכל מקום בשולחן הנגרות)
- **קלט:** בלוק עפרה אחד (`jackietonite_ore_block`)
- **פלט:** 9 ספירים (`sapphire`)

---

## Assets

```
src/main/resources/assets/tutorialmod/
├── textures/
│   ├── item/sapphire.png
│   ├── item/raw_sapphire.png
│   ├── item/jackietonite_ore_block.png
│   └── block/jackietonite_ore_block.png
├── models/
│   ├── item/sapphire.json
│   ├── item/raw_sapphire.json
│   ├── item/jackietonite_ore_block.json
│   └── block/jackietonite_ore_block.json
├── blockstates/
│   └── jackietonite_ore_block.json
└── lang/
    └── en_us.json
```

---

## דרישות

- Minecraft 1.20.X
- Forge MDK לגרסה 1.20.X
- Java 17+

---

## הרצה

```bash
./gradlew runClient
```

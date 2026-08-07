# XenoPixels Network — Minecraft NeoForge Mod (1.21.1)

XenoPixels (`xenopixelsmod`) — custom DragonMineZ server content, Xenoverse-style HUD, combat, and Sable ship support for NeoForge 1.21.1.

---

## פריטים

| שם | ID | תיאור |
|---|---|---|
| Sapphire | `xenopixelsmod:sapphire` | ספיר — פריט רגיל |
| Raw Sapphire | `xenopixelsmod:raw_sapphire` | ספיר גולמי — פריט רגיל |
| Metal Detector | `xenopixelsmod:metal_detector` | גלאי מתכות — כלי עם 100 עמידות, מאתר עפרות מתחת לרגליים |

---

## בלוקים

| שם | ID | תיאור |
|---|---|---|
| Jackietonite Ore Block | `xenopixelsmod:jackietonite_ore_block` | בלוק עפרה מותאם אישית, חוזק 3.0, דורש כלי נכון לשבירה, מפיל 3–7 XP |
| Raw Jackietonite Ore Block | `xenopixelsmod:raw_jackietonite_ore_block` | בלוק עפרה גולמית, חוזק 3.0 |
| Jackietonite Ore | `xenopixelsmod:jackietonite_ore` | עפרה בסלע רגיל, חוזק 2.0, מפיל 3–6 XP |
| Deepslate Jackietonite Ore | `xenopixelsmod:deepslate_jackietonite_ore` | עפרה בדיפסלייט, חוזק 3.0, מפיל 3–6 XP |
| Nether Jackietonite Ore | `xenopixelsmod:nether_jackietonite_ore` | עפרה בנת'ר, חוזק 1.0, מפיל 3–6 XP |
| End Stone Jackietonite Ore | `xenopixelsmod:end_stone_jackietonite_ore` | עפרה באבן האנד, חוזק 5.0, מפיל 3–6 XP |

---

## טאב קריאייטיב

טאב מותאם אישית בשם **XenoPixels** המכיל את כל הפריטים והבלוקים של המוד:
- Sapphire
- Raw Sapphire
- **Metal Detector** ← חדש!
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
src/main/java/net/bullettrain/xenopixelsmod/
├── XenoPixelsMod.java              # נקודת הכניסה הראשית של המוד
├── item/
│   ├── ModsItems.java            # רישום פריטים
│   ├── ModCreativeModTabs.java   # רישום טאב קריאייטיב
│   └── custom/
│       └── MetalDetectorItem.java  # לוגיקת גלאי המתכות
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
        DeferredRegister.create(ForgeRegistries.ITEMS, XenoPixelsMod.MOD_ID);

    // רישום ספיר
    public static final RegistryObject<Item> SAPPHIRE =
        ITEMS.register("sapphire", () -> new Item(new Item.Properties()));

    // רישום ספיר גולמי
    public static final RegistryObject<Item> RAW_SAPPHIRE =
        ITEMS.register("raw_sapphire", () -> new Item(new Item.Properties()));

    // רישום גלאי מתכות — פריט מותאם עם 100 נקודות עמידות
    public static final RegistryObject<Item> METAL_DETECTOR =
        ITEMS.register("metal_detector",
            () -> new MetalDetectorItem(new Item.Properties().durability(100)));

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
        DeferredRegister.create(ForgeRegistries.BLOCKS, XenoPixelsMod.MOD_ID);

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

### MetalDetectorItem.java — גלאי מתכות

קלאס מותאם שיורש מ-`Item` ומממש התנהגות מיוחדת בלחיצה על בלוק.

```java
public class MetalDetectorItem extends Item {
    public MetalDetectorItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public InteractionResult useOn(UseOnContext pContext) {
        if (!pContext.getLevel().isClientSide()) {          // רץ רק בצד השרת
            BlockPos positionClicked = pContext.getClickedPos();
            Player player = pContext.getPlayer();
            boolean foundBlock = false;

            // לולאה שיורדת עד 64 בלוקים מתחת לנקודת הלחיצה
            for (int i = 0; i <= positionClicked.getY() + 64; i++) {
                BlockState state = pContext.getLevel().getBlockState(positionClicked.below(i));

                if (isValueableBlock(state)) {
                    outputValuableCoordinates(positionClicked.below(i), player, state.getBlock());
                    foundBlock = true;
                    break;
                }
            }

            if (!foundBlock) {
                player.sendSystemMessage(Component.literal("No valuables found"));
            }

            // מוריד 1 נקודת עמידות בכל שימוש
            pContext.getItemInHand().hurtAndBreak(1, pContext.getPlayer(),
                Player -> player.broadcastBreakEvent(player.getUsedItemHand()));
        }
        return InteractionResult.SUCCESS;
    }

    // שולח הודעה עם שם הבלוק והקואורדינטות שלו
    private void outputValuableCoordinates(BlockPos blockPos, Player player, Block block) {
        player.sendSystemMessage(Component.literal(
            "Found " + I18n.get(block.getDescriptionId()) +
            "(" + blockPos.getX() + ", " + blockPos.getY() + "," + blockPos.getZ() + ")"));
    }

    // בודק אם הבלוק הוא עפרה בעלת ערך
    private boolean isValueableBlock(BlockState state) {
        return state.is(Blocks.IRON_ORE) || state.is(Blocks.DIAMOND_ORE);
    }
}
```

**הסבר:**
- `useOn()` — נקרא כשמשתמשים בפריט על בלוק (קליק ימני)
- `isClientSide()` — בודק שהקוד רץ בצד השרת בלבד (מניעת כפילות)
- `positionClicked.below(i)` — יורד בלוק אחד בכל איטרציה
- `hurtAndBreak(1, ...)` — מוריד 1 עמידות; כשמגיע ל-0 הפריט נשבר
- `I18n.get(block.getDescriptionId())` — מחזיר את שם הבלוק בשפת המשתמש

---

### ModCreativeModTabs.java — טאב קריאייטיב

```java
public static final RegistryObject<CreativeModeTab> TUTORIAL_TAB =
    CREATIVE_MODE_TABS.register("xenopixels_tab",
        () -> CreativeModeTab.builder()
            .icon(() -> new ItemStack(ModsItems.SAPPHIRE.get())) // אייקון הטאב
            .title(Component.translatable("creativetab.xenopixels_tab"))
            .displayItems((pParameters, pOutput) -> {
                pOutput.accept(ModsItems.SAPPHIRE.get());
                pOutput.accept(ModsItems.RAW_SAPPHIRE.get());
                pOutput.accept(ModsItems.METAL_DETECTOR.get()); // גלאי מתכות
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
| `Item extends` / `useOn()` | יצירת פריט מותאם עם התנהגות מיוחדת בלחיצה על בלוק |
| `.durability(n)` | מגדיר עמידות לפריט — נשבר אחרי n שימושים |
| `hurtAndBreak(1, ...)` | מוריד 1 נקודת עמידות מהפריט בכל שימוש |
| `I18n.get(...)` | מחזיר שם מתורגם של בלוק/פריט לפי שפת המשתמש |
| `isClientSide()` | בודק אם הקוד רץ בצד הלקוח — משמש למניעת כפילות לוגיקה |

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
    "xenopixelsmod:jackietonite_ore_block",
    "xenopixelsmod:raw_jackietonite_ore_block"
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
          "name": "xenopixelsmod:jackietonite_ore"
        },
        {
          "type": "minecraft:item",
          "functions": [
            { "function": "minecraft:set_count", "count": { "type": "minecraft:uniform", "min": 2.0, "max": 5.0 } },
            { "function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops" },
            { "function": "minecraft:explosion_decay" }
          ],
          "name": "xenopixelsmod:raw_sapphire"
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
    { "item": "xenopixelsmod:jackietonite_ore_block" }
  ],
  "result": {
    "item": "xenopixelsmod:sapphire",
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
src/main/resources/assets/xenopixelsmod/
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

---

## פתרון בעיות — קריסת Mixin מ־Valkyrien Skies (MixinTransformerError)

### התופעה

בהרצת `runClient` המשחק קורס עם שגיאה כזו ב־`run/logs/debug.log`:

```
org.spongepowered.asm.mixin.transformer.throwables.MixinTransformerError: An unexpected critical error was encountered
...
Caused by: org.spongepowered.asm.mixin.injection.throwables.InvalidInjectionException:
Critical injection failure: @WrapOperation annotation on useOriginalCrosshairForBlockPlacement
could not find any targets matching 'Lnet/minecraft/client/Minecraft;m_91277_()V'
in net.minecraft.client.Minecraft. Using refmap valkyrienskies-120-common-refmap.json
[... valkyrienskies-common.mixins.json:client.MixinMinecraft ...]
```

### מה באמת קורה כאן (חקירה)

חשוב להבין: **זו לא קריסה שקשורה למוד שלנו (`xenopixelsmod`)**. לפרויקט הזה אין אף Mixin משלנו כלל — לא הוגדר קובץ `*.mixins.json`, ולא נכתבה שום מחלקת Mixin ב־`src`.

הבאג נמצא בתוך ה־Mixin **הפנימי** של מוד **Valkyrien Skies** (VS) עצמו — `valkyrienskies-common.mixins.json:client.MixinMinecraft`. זהו מוד תלות שנוסף ל־`build.gradle` (`org.valkyrienskies:valkyrienskies-120-forge`). ה־Mixin הזה מנסה "לעטוף" (`@WrapOperation`) קריאה למתודה `Minecraft.startUseItem()` (בשם SRG הפנימי — `m_91277_`), אבל בזמן טעינת המשחק Mixin לא מצליח לאתר את המתודה הזו לפי אותו שם SRG בתוך הקובץ המקומפל (למרות שהמתודה `startUseItem()` בהחלט קיימת ב־`Minecraft.class` — זה נבדק ואומת ידנית עם `javap`).

**סדר הבדיקה שבוצע:**

1. אישרנו שאין קבצי לוג ישנים ואין Mixins בפרויקט שלנו — הריצה הראשונה של `runClient` יצרה את `run/logs/debug.log` מחדש.
2. חיפשנו בלוג את שרשרת ה־`Caused by` המלאה ומצאנו ש־FATAL מגיע מ־Mixin של VS, לא מהמוד שלנו.
3. פיענחנו את שם ה־SRG: `m_91277_` = `startUseItem` (לפי `methods.csv` של מיפוי MCP לגרסת 1.20.1).
4. בדקנו עם `javap` שהמתודה `startUseItem()` אכן קיימת ב־`Minecraft.class` בסביבת הפיתוח (`forge-1.20.1-47.4.10_mapped_official_1.20.1.jar`).
5. חילצנו את קובץ ה־refmap של VS (`valkyrienskies-120-common-refmap.json`) ווידאנו שהמיפוי `startUseItem → m_91277_` קיים שם כראוי.
6. בדקנו את תבנית ה־Forge Java הרשמית של VS ואת מדריך ה־Addon של DragonMineZ. התבנית של VS משתמשת ב־ModDev, אך מדריך DMZ מחייב ForgeGradle 6 עם ParchmentMC ו־`fg.deobf(...)`.

**מסקנה:** הכשל לא היה Mixin שצריך לכתוב במוד שלנו ולא פגם במתודה `startUseItem()`. הוא נבע מכך שה־refmap של VS לא קיבל את אותו תהליך מיפוי כמו קוד Minecraft בסביבת הפיתוח. הפתרון הסופי הוא ForgeGradle 6 עם ParchmentMC, שבו גם VS וגם DragonMineZ נטענים דרך `fg.deobf(...)` ונמפים באותה שרשרת.

### הפתרון שיושם ואומת

| רכיב | הגדרה סופית |
|---|---|
| מערכת build | ForgeGradle 6 + ParchmentMC Librarian + Sponge Mixin Gradle |
| Gradle Wrapper | 8.8 |
| Minecraft / Forge | 1.20.1 / 47.4.10 |
| מיפויים | Parchment `2023.09.03-1.20.1` |
| Valkyrien Skies | `2.4.13+c2e82178c0` |
| VS Core | `1.1.0+ea6dc8576e` |
| Mixin של המוד | `xenopixelsmod.mixins.json` עם refmap ו־Java 17 |

נוסף קובץ `xenopixelsmod.mixins.json` ריק ומוכן לשימוש עתידי. הוא אינו משנה שום התנהגות כרגע; כאשר יתווספו Mixins בעתיד, יש להוסיף את שם המחלקה לרשימת `mixins` או `client` וליצור את המחלקה תחת `net.bullettrain.xenopixelsmod.mixin`.

האימות בוצע עם `./gradlew runClient`: גם ה־refmap של VS וגם ה־refmap של DMZ עברו remap, VS Core אותחל, DragonMineZ נטען והמשחק נשאר פעיל ללא `MixinTransformerError` וללא הכשל ב־`startUseItem`.

---

## תמיכת Addon ל־DragonMineZ

התמיכה ממומשת לפי [Creating a DMZ Addon](https://github.com/DragonMineZ/dragonminez/wiki/Creating-a-DMZ-Addon).

### מה נוסף

- `ParchmentMC` מופעל ב־`gradle.properties` וב־`build.gradle` עם מיפוי `2023.09.03-1.20.1`.
- `META-INF/mods.toml` מגדיר את `dragonminez` כתלות **חובה** בגרסה `[2.1.2]`, בסדר טעינה `AFTER` ובשני הצדדים.
- `build.gradle` מוסיף את DMZ ואת תלויות הריצה שלו: GeckoLib, TerraBlender ו־Curios.
- `DmzHooks.java` נרשם לאוטובוס האירועים של Forge ומאזין ל־`DMZEvent.TPGainEvent`. כרגע הוא רק כותב הודעת `debug`; הוא אינו משנה את כמות ה־TP, ולכן מוסיף נקודת הרחבה בטוחה ללא שינוי בהתנהגות המשחק.

### התקנת JAR הפיתוח של DMZ

ל־DragonMineZ אין artifact של API ב־Maven. לכן צריך להוריד את JAR הפיתוח המדויק ולשים אותו מקומית:

1. הורד `dragonminez-2.1.2.jar` מ־[Modrinth](https://modrinth.com/mod/dragonminez/version/t1Qn8aCi).
2. צור תיקייה `libs` בשורש הפרויקט אם אינה קיימת.
3. העתק אליה את הקובץ כך שהנתיב יהיה `libs/dragonminez-2.1.2.jar`.
4. הרץ `./gradlew runClient`.

התיקייה `libs/` נמצאת ב־`.gitignore`; אין להעלות את ה־JAR של DMZ למאגר. הבנייה נכשלת במפורש עם הודעה ברורה אם הקובץ החסר, במקום להפיק JAR שנראה תקין אך אינו תומך ב־DMZ.

### הרחבת ה־Hook

המאזין הקיים נמצא ב־`src/main/java/net/bullettrain/xenopixelsmod/event/DmzHooks.java`. לדוגמה, כדי לשנות TP יש להשתמש ב־`event.setTpGain(...)` בתוך `onTrainingPointGain`. יש לבצע שינוי כזה רק כאשר רוצים שינוי מכניקת משחק מכוון, משום שה־Hook הנוכחי נבחר במכוון להיות תצפיתי בלבד.

---

## הסבר שינויי Build — gradle.properties ו־build.gradle

סעיף זה מסביר **שלב אחר שלב** את כל השינויים שנעשו בקובצי ה-build של הפרויקט, מה שינינו, למה ובאיזה סדר.

---

### שלב 1 — שינוי המיפויים מ-parchment ל-official

**קובץ:** `gradle.properties`

**לפני:**
```properties
mapping_channel=parchment
mapping_version=2023.06.26-1.20.1
```

**אחרי:**
```properties
mapping_channel=official
mapping_version=1.20.1
```

**הסבר:**
- **parchment** = מיפויים לא-רשמיים של ParchmentMC עם שמות פרמטרים נוחים יותר לקריאה
- **official** = המיפויים הרשמיים של Mojang, שמות ה-SRG עם שמות פרמטרים מ-Mojang ישירות
- הסיבה לשינוי: Valkyrien Skies בנה את ה-refmap שלו עם `official` mappings — כאשר הפרויקט שלנו השתמש ב-parchment, שמות המתודות (SRG) לא התאימו ו-Mixin של VS קרס

**בנוסף ב-build.gradle:**
```groovy
// הפלאגין של ParchmentMC הוסר/הושבת כי אינו נדרש יותר
// id 'org.parchmentmc.librarian.forgegradle' version '1.+'
```

---

### שלב 2 — עדכון גרסת Forge

**קובץ:** `gradle.properties`

**לפני:**
```properties
forge_version=47.4.0
```

**אחרי:**
```properties
forge_version=47.4.10
```

**הסבר:**
- 47.4.10 היא גרסה חדשה יותר ויציבה יותר של Forge לגרסת Minecraft 1.20.1
- גרסת Forge חייבת להתאים לגרסת VS2 שנוסיף — יש לבדוק תאימות בין הגרסאות

---

### שלב 3 — הוספת גרסאות Valkyrien Skies ל-gradle.properties

**קובץ:** `gradle.properties`

```properties
vs2_mc_version=120        # גרסת Minecraft ב-format מקוצר (1.20.X → 120)
vs2_version=2.4.10        # גרסת VS2 עבור Minecraft 1.20
vs_core_version=1.1.0+1d4a7373e9  # גרסת VS Core (ספרייה הפנימית של VS)
```

**הסבר:**
- `vs2_mc_version` — VS משתמש ב-artifact שמותאם לגרסת MC ספציפית (`valkyrienskies-120-forge`)
- `vs2_version` — גרסת ה-jar הראשי של VS2
- `vs_core_version` — VS מחולק ל-`vs-core` (לוגיקה) ו-`valkyrienskies-forge` (אינטגרציה עם Forge) — שניהם נדרשים

---

### שלב 4 — הוספת Maven Repository של Valkyrien Skies

**קובץ:** `build.gradle` — בלוק `repositories`

```groovy
maven {
    name = 'Valkyrien Skies Internal'
    url = project.vs_maven_url ?: 'https://maven.valkyrienskies.org'
    // תמיכה אופציונלית ב-authentication למרות שב-build רגיל אינה נדרשת
    if (project.vs_maven_username && project.vs_maven_password) {
        credentials {
            username = project.vs_maven_username
            password = project.vs_maven_password
        }
    }
}
```

**הסבר:**
- Valkyrien Skies **אינו** ב-Maven Central — יש לו maven משלו בכתובת `https://maven.valkyrienskies.org`
- ה-`?: 'https://...'` = אם `vs_maven_url` ריק ב-`gradle.properties`, יש fallback לכתובת הברירת מחדל
- ה-`if (credentials...)` = אפשרות לחבר maven פרטי (לא נדרש בפיתוח רגיל)

---

### שלב 5 — הוספת Dependencies של Valkyrien Skies

**קובץ:** `build.gradle` — בלוק `dependencies`

```groovy
// region Valkyrien Skies

// VS Core — ספרייה פנימית מחולקת ל-4 מודולים
implementation("org.valkyrienskies.core:api:${vs_core_version}") { transitive = false
    exclude group: 'org.joml', module: ''
}
implementation("org.valkyrienskies.core:internal:${vs_core_version}") { transitive = false
    exclude group: 'org.joml', module: ''
}
implementation("org.valkyrienskies.core:util:${vs_core_version}") { transitive = false
    exclude group: 'org.joml', module: ''
}
implementation("org.valkyrienskies.core:impl:${vs_core_version}") { transitive = false
    exclude group: 'org.joml', module: ''
}

// VS2 עצמו — נטען דרך fg.deobf() כדי ש-ForgeGradle ימפה אותו
implementation fg.deobf("org.valkyrienskies:valkyrienskies-120-forge:${vs2_version}") {
    transitive = false
    exclude group: 'org.valkyrienskies.core', module: ''
}
// endregion

// region VS deps — ספריות תלות של VS
implementation "com.fasterxml.jackson.core:jackson-annotations:2.13.3"  // JSON serialization
compileOnly("org.joml:joml:1.10.4")           // מתמטיקה וקטורית/מטריציות
compileOnly("org.joml:joml-primitives:1.10.0") // סוגי נתונים גיאומטריים של joml
// endregion
```

**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `implementation(...)` | מוסיף את ה-jar לקלאספת' של הקומפילציה וריצה |
| `transitive = false` | מונע מ-Gradle להוריד **אוטומטית** את כל התלויות הנסתרות של VS Core — אנחנו מצהירים עליהן ידנית |
| `exclude group: 'org.joml'` | מונע קונפליקט גרסאות — joml מוגדר נפרד בהמשך |
| `fg.deobf(...)` | **ForgeGradle deobfuscate** — מעביר את ה-jar של VS2 דרך אותו תהליך remapping כמו קוד Minecraft, כך ש-Mixin יכול למצוא שמות מתודות נכון |
| `exclude group: 'org.valkyrienskies.core'` | מונע טעינה כפולה — VS Core כבר הוגדר ידנית למעלה |
| `compileOnly(joml)` | joml נדרש בזמן קומפילציה בלבד; בזמן ריצה VS מספק אותו בעצמו |

---

### שלב 6 — הוספת Kotlin for Forge

**קובץ:** `build.gradle`

```groovy
implementation 'thedarkcolour:kotlinforforge:4.11.0'
```

**הסבר:**
- DragonMineZ כתוב חלקית ב-Kotlin — נדרש `kotlinforforge` כדי שקוד Kotlin יעבוד בסביבת Forge
- גרסה 4.x תואמת ל-Minecraft 1.20.x ול-Forge 47.x
- ה-maven repository של KotlinForForge כבר מוגדר בבלוק `repositories`:
  ```groovy
  maven {
      name = 'Kotlin for Forge'
      url = 'https://thedarkcolour.github.io/KotlinForForge/'
      content { includeGroup 'thedarkcolour' }
  }
  ```

---

### סדר הפעולות הנכון — סיכום

| שלב | קובץ | מה עשינו | למה |
|---|---|---|---|
| 1 | `gradle.properties` | `mapping_channel=official` | תאימות VS Mixin refmap |
| 2 | `build.gradle` | הסרת פלאגין parchment | לא נדרש יותר |
| 3 | `gradle.properties` | `forge_version=47.4.10` | גרסה עדכנית ותואמת VS |
| 4 | `gradle.properties` | הוספת `vs2_version`, `vs_core_version` | הגדרת גרסאות VS |
| 5 | `build.gradle` → `repositories` | הוספת maven של VS | VS לא ב-Maven Central |
| 6 | `build.gradle` → `dependencies` | הוספת VS Core (4 מודולים) | ספרייה הפנימית של VS |
| 7 | `build.gradle` → `dependencies` | `fg.deobf(valkyrienskies-120-forge)` | remapping נכון של VS |
| 8 | `build.gradle` → `dependencies` | jackson, joml, joml-primitives | תלויות של VS |
| 9 | `build.gradle` → `dependencies` | `kotlinforforge:4.11.0` | תמיכת Kotlin עבור DMZ |

### מושגי מפתח נוספים

| מושג | הסבר |
|---|---|
| `mapping_channel` | קובע את **מערכת שמות** המתודות והפרמטרים — `official` = מ-Mojang, `parchment` = שמות ידידותיים נוספים |
| `fg.deobf(...)` | **ForgeGradle deobfuscate** — מאפשר ל-Forge לקרוא jar חיצוני ולמפות את השמות שלו לאותה מערכת שמות כמו Minecraft |
| `transitive = false` | מונע מ-Gradle להוריד אוטומטית תלויות נסתרות — מאפשר שליטה ידנית מלאה |
| `exclude group:` | מסיר dependency ספציפי מ-jar שנוסף, מונע קונפליקטי גרסאות |
| `compileOnly` | ה-jar קיים בקומפילציה בלבד — לא נארז ב-output jar הסופי |
| `Mixin refmap` | קובץ JSON שממפה שמות מתודות של Mixin לשמות ה-SRG — חייב להיות באותה שיטת מיפוי כמו הפרויקט |


---

## מדריך בסיס מפורט: Elementa GUI ב־Forge

[Elementa](https://github.com/SparkUniverse/Elementa) היא ספריית GUI דקלרטיבית. במקום לחשב ידנית את מיקום כל רכיב בכל frame, מגדירים לרכיב **מה** צריך להיות גודלו ומיקומו ביחס להורה שלו; Elementa מחשבת את התוצאה ומציירת את עץ הממשק.

המדריך מתאים לפרויקט הזה: **Minecraft 1.20.1, Forge 47.4.10, Java 17, ForgeGradle 6 ו־ParchmentMC**. הוא אינו מוסיף את Elementa לפרויקט כברירת מחדל; השלבים למטה הם מתכון מלא ומכוון, שאפשר ליישם כאשר מתחילים לבנות GUI.

### 1. להבין את מבנה הממשק

Elementa בנויה מעץ רכיבים:

| מושג | משמעות |
|---|---|
| `Window` | שורש עץ הרכיבים; אחראי גם להעברת אירועי עכבר ומקלדת |
| `WindowScreen` | `Screen` מוכן של Elementa; יוצר `Window`, מצייר אותו ומעביר אירועי קלט אוטומטית |
| `UIComponent` | מחלקת הבסיס לכל רכיב UI |
| `UIBlock` | מלבן צבוע; שימושי כרקע, panel או כפתור בסיסי |
| `UIText` | טקסט בשורה אחת; הגודל שלו נגזר מהטקסט אם לא מגדירים אילוצים |
| `UIContainer` | רכיב שקוף שמארגן ילדים, בדומה ל־`div` ב־HTML |
| Constraints | אילוצים ל־x/y/width/height/color; כולם יחסיים להורה הישיר |
| Effects | אפקטים כגון `ScissorEffect`, שחותך ציור של ילדים אל גבולות הרכיב |

לכל רכיב יש הורה יחיד ואפס או יותר ילדים. רכיב שאינו ילד של `Window` לא יוצג. לכן תמיד יוצרים רכיב, מגדירים לו constraints, ואז מחברים אותו לעץ.

### 2. להוסיף repositories וגרסאות

ב־`gradle.properties` הוסף גרסאות מפורשות. בזמן כתיבת המדריך הגרסאות האחרונות במאגר Essential הן `745` עבור Elementa ו־`505` עבור UniversalCraft ל־Forge 1.20.1:

```properties
elementa_version=745
universalcraft_version=505
```

ב־`build.gradle`, בתוך `repositories`, הוסף את מאגר Essential:

```groovy
maven {
    name = 'Essential'
    url = 'https://repo.essential.gg/repository/maven-public'
}
```

שימוש בגרסה מפורשת חשוב: `+` או `latest.release` עלולים לעדכן את Elementa בלי בדיקה ולשבור GUI בגלל שינוי API.

### 3. להוסיף את התלויות

בתוך `dependencies` הוסף:

```groovy
implementation "gg.essential:elementa:${elementa_version}"
implementation "gg.essential:universalcraft-1.20.1-forge:${universalcraft_version}"
```

`Elementa` עצמה אינה קשורה לגרסת Minecraft; `UniversalCraft` הוא שכבת ההתאמה שמחברת אותה ל־Forge ול־Minecraft 1.20.1. יש לשמור את שתיהן באותה אסטרטגיית אריזה ו־relocation בשלב הבא.

> אין להשתמש ב־`jarJar` עבור Elementa בפרויקט Forge. לפי התיעוד הרשמי של Elementa, ב־Forge יש להצליל (shade) ולבצע relocate לשתי הספריות.

### 4. למה Forge דורש Shadow ו־relocation

Forge טוען את כל המודים לאותו classpath. אם שני מודים כוללים גרסאות שונות של `gg.essential.elementa` או `gg.essential.universalcraft`, אחד מהם עלול לטעון מחלקה מהגרסה של השני ולקבל `NoSuchMethodError`, `ClassCastException` או קריסה אקראית ב־GUI.

`relocation` משנה את ה־package שמאוחסן בתוך JAR המוד שלנו, לדוגמה:

```text
gg.essential.elementa       -> net.bullettrain.xenopixelsmod.shadow.elementa
gg.essential.universalcraft -> net.bullettrain.xenopixelsmod.shadow.universalcraft
```

כך המוד שלנו מחזיק עותק פרטי של Elementa ושל UniversalCraft, שאינו מתנגש עם מודים אחרים.

### 5. להגדיר Shadow בצורה נכונה

ב־`plugins` הוסף. לאחר מכן **החלף** את שתי תלויות ה־`implementation` משלב 3 בבלוק `elementaShade` הבא; אין להחזיק את שתי הצורות במקביל:

```groovy
id 'com.github.johnrengelman.shadow' version '8.1.1'
```

לאחר בלוק `dependencies`, הוסף:

```groovy
configurations {
    elementaShade
    implementation.extendsFrom elementaShade
}

dependencies {
    elementaShade "gg.essential:elementa:${elementa_version}"
    elementaShade "gg.essential:universalcraft-1.20.1-forge:${universalcraft_version}"
}

tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
    archiveClassifier.set('')
    configurations = [project.configurations.elementaShade]
    relocate 'gg.essential.elementa', "${mod_group_id}.shadow.elementa"
    relocate 'gg.essential.universalcraft', "${mod_group_id}.shadow.universalcraft"
}

tasks.named('reobfJar') {
    dependsOn tasks.named('shadowJar')
}

tasks.named('jar') {
    archiveClassifier.set('slim')
}
```

**הסבר השורות החשובות:**

1. `elementaShade` מפרידה את הספריות שצריכות להיכנס ל־JAR מהתלויות הרגילות של המשחק.
2. `implementation.extendsFrom elementaShade` משאירה את Elementa זמינה לקומפילציה.
3. `shadowJar` מכניס רק את שתי ספריות Elementa/UniversalCraft, ולא בטעות את Forge, Minecraft או תלות גדולה אחרת.
4. `relocate` חייב לכלול את **שני** ה־packages; הזזה של Elementa בלבד תיצור קריסה משום ש־Elementa מפנה ל־UniversalCraft.
5. `reobfJar` חייב לרוץ אחרי `shadowJar`, אחרת ה־JAR המוצלל לא יעבור reobfuscation המתאים ל־Forge.

לאחר ההגדרה, קובץ ההפצה הוא ה־JAR ללא classifier תחת `build/libs/`; קובץ `-slim.jar` אינו מיועד להפצה כי הוא אינו כולל את Elementa.

### 6. ליצור מסך Java ראשון

צור את הקובץ:

```text
src/main/java/net/bullettrain/xenopixelsmod/client/ElementaExampleScreen.java
```

דוגמת בסיס ב־Java:

```java
package net.bullettrain.xenopixelsmod.client;

import gg.essential.elementa.ElementaVersion;
import gg.essential.elementa.WindowScreen;
import gg.essential.elementa.components.UIBlock;
import gg.essential.elementa.components.UIText;
import gg.essential.elementa.constraints.CenterConstraint;
import gg.essential.elementa.constraints.PixelConstraint;
import net.minecraft.network.chat.Component;

import java.awt.Color;

public final class ElementaExampleScreen extends WindowScreen {
    public ElementaExampleScreen() {
        super(ElementaVersion.V2);

        UIBlock panel = new UIBlock(new Color(20, 28, 48, 220));
        panel.setX(new CenterConstraint());
        panel.setY(new CenterConstraint());
        panel.setWidth(new PixelConstraint(220.0F));
        panel.setHeight(new PixelConstraint(120.0F));
        panel.setChildOf(getWindow());

        UIText title = new UIText("XenoPixelsMod + Elementa");
        title.setX(new CenterConstraint());
        title.setY(new PixelConstraint(18.0F));
        title.setChildOf(panel);
    }

    @Override
    public Component getTitle() {
        return Component.literal("XenoPixelsMod");
    }
}
```

### 7. הסבר שורה אחר שורה לדוגמה

1. `extends WindowScreen` עדיף על יצירה ידנית של `Window`: הוא כבר מחבר את `draw`, הקלקות, גלילה, תנועה, לחיצות מקלדת ושחרור מקשים ל־Elementa.
2. `super(ElementaVersion.V2)` בוחר את API V2 של Elementa. יש לשמור אותו גם כאשר GUI קטן; ערבוב גרסאות API ייצור הבדלי התנהגות.
3. `UIBlock` הוא panel שקוף למחצה. הערך הרביעי של `Color` הוא alpha (`220` מתוך `255`).
4. `CenterConstraint` ממקם את הרכיב במרכז ההורה שלו. במקרה הזה ההורה הוא ה־`Window`, לכן ה־panel במרכז המסך.
5. `PixelConstraint` קובע גודל קבוע בפיקסלים. UI גמיש יותר יכול להשתמש ב־`RelativeConstraint` או ב־`ChildBasedSizeConstraint`.
6. `setChildOf(getWindow())` מוסיף את panel לעץ. בלי השורה הזו לא יצויר כלום.
7. ה־`UIText` מחובר ל־`panel`, ולכן ה־`CenterConstraint` שלו מתייחס למרכז ה־panel ולא למרכז המסך.
8. `getTitle()` הוא הכותרת הסטנדרטית של מסך Minecraft; רכיבי Elementa עצמם מצוירים דרך ה־`Window`.

### 8. לפתוח את המסך בצורה בטוחה בצד הלקוח

אסור לייבא `Minecraft` או `Screen` במחלקת common שניטענת גם בשרת ייעודי. פתח את המסך רק בקוד client:

```java
import net.minecraft.client.Minecraft;

Minecraft.getInstance().setScreen(new ElementaExampleScreen());
```

מקומות תקינים לקריאה הם keybind בצד הלקוח, לחיצה על כפתור client, או packet שהשרת שלח וה־client מטפל בו. אין לקרוא לכך מתוך `commonSetup` או מאזין שרת.

### 9. אירועים, לחצנים ואנימציה

כל אירוע מתחיל ב־`Window`. ב־`WindowScreen` ההעברה נעשית עבורך. לדוגמה, אפשר להצמיד callback לרכיב:

```java
panel.onMouseEnterRunnable(() -> {
    // שינוי צבע, התחלת אנימציה או לוגיקה מקומית
});
```

לאנימציה יוצרים `AnimatingConstraints`, בוחרים easing מתוך `Animations`, ואז מפעילים `animateTo`. זמני Elementa הם בשניות, ולכן `0.25F` הוא רבע שנייה. אין לשנות constraints בכל tick ידנית אם אנימציה של Elementa מתאימה; כך נשמרת לוגיקת UI הצהרתית וברורה.

ל־input טקסט השתמש ב־`UITextInput` או `UIMultilineTextInput`. כאשר משתמשים ב־`WindowScreen`, focus, מקלדת ועכבר כבר מועברים לחלון. אם עובדים ישירות עם `Window`, חובה להעביר ידנית אירועי `draw`, `mouseClick`, `mouseRelease`, `mouseScroll`, `keyType` ו־`keyPressed`.

### 10. Constraints שימושיים

| Constraint | שימוש |
|---|---|
| `PixelConstraint(value)` | גודל/היסט קבוע בפיקסלים |
| `CenterConstraint()` | מרכוז בתוך ההורה הישיר |
| `RelativeConstraint(0.5F)` | 50% מגודל ההורה |
| `SiblingConstraint()` | מיקום אחרי רכיב אח/אחות בעץ |
| `ChildBasedSizeConstraint()` | גודל לפי תוכן הילדים |
| `ChildBasedMaxSizeConstraint()` | גודל לפי הילד הגדול ביותר |
| `ImageAspectConstraint()` | שמירת יחס ממדים של תמונה |

הכל יחסי להורה. אם כותרת לא ממורכזת במקום הצפוי, בדוק קודם מי ההורה שלה בעץ ולא את גודל המסך.

### 11. clipping, תמונות וביצועים

- הוסף `ScissorEffect` ל־container גלילה או panel כשילדים אינם אמורים לצייר מחוץ לגבולותיו.
- `UIImage` טוען תמונות באופן אסינכרוני ויכול להציג placeholder בזמן הטעינה. לתמונה מקומית עדיף resource בתוך JAR; ל־URL חיצוני יש להוסיף timeout, fallback ולא לחסום את render thread.
- אל תיצור מחדש את כל עץ ה־UI בכל frame. צור אותו פעם אחת ב־constructor, ועדכן רק טקסט/constraints/מצב שהשתנה.
- לפני סגירת מסך או מעבר למסך אחר, נקה listeners או animation מותאמים אישית שמחזיקים הפניות למצב משחק גדול.

### 12. רשימת בדיקה לפני הפצה

1. `./gradlew build` יוצר JAR ללא classifier ו־`-slim.jar`.
2. בדוק שה־JAR ללא classifier מכיל packages relocated תחת `net/bullettrain/xenopixelsmod/shadow/`.
3. הפעל את המשחק עם מוד נוסף שמשתמש ב־Elementa, כדי לוודא שאין התנגשות classpath.
4. בדוק client רגיל וגם dedicated server: השרת חייב להתחיל בלי לטעון מחלקת GUI.
5. ודא ש־`ElementaVersion.V2` והגרסאות ב־`gradle.properties` תואמות לגרסה שנבדקה.

### 13. מקורות רשמיים

- [Elementa README](https://github.com/SparkUniverse/Elementa)
- [JavaTestGui](https://github.com/SparkUniverse/Elementa/blob/master/example/src/main/kotlin/gg/essential/elementa/example/JavaTestGui.java)
- [רשימת רכיבים](https://github.com/SparkUniverse/Elementa/blob/master/docs/components.md)
- [מדריך מעבר ל־V2](https://github.com/SparkUniverse/Elementa/blob/master/docs/migration.md)

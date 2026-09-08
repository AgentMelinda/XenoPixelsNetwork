<div dir="rtl">

# 🛠️ מתקדם 05 — מעבר ל-Registrate (פחות JSON ידני)

> מדריך מעשי למוד שלנו: [`AgentMelinda/XenoPixelsNetwork`](https://github.com/AgentMelinda/XenoPixelsNetwork)
> (`net.bullettrain.tutorialmod`, `ModsItems`).
>
> מבוסס על [tterrag1098/Registrate · branch 1.20](https://github.com/tterrag1098/Registrate/tree/1.20).

---

## למה בכלל Registrate?

כרגע, לכל פריט/בלוק אתם כותבים **כמה דברים מפוזרים**:

| מה | איפה (בלי Registrate) |
|---|---|
| רישום ב-Java | `ModsItems` / `ModBlocks` + `DeferredRegister` |
| שם באנגלית | `assets/tutorialmod/lang/en_us.json` |
| model של פריט | `assets/.../models/item/*.json` |
| blockstate | `assets/.../blockstates/*.json` |
| model של בלוק | `assets/.../models/block/*.json` |
| loot table | `data/.../loot_tables/blocks/*.json` (או datagen) |

**Registrate** הוא ספריית Java (לא מוד נפרד) שעוטפת את הרישום ומאפשרת **fluent API**:
בשורה אחת (או כמה) אתם רושמים פריט/בלוק **וגם** מגדירים model / lang / loot / tags —
ואז `./gradlew runData` מייצר את ה-JSON אוטומטית.

> 💡 זה **לא** מחליף טקסטורות (PNG). עדיין שמים `textures/...`.  
> זה מחליף בעיקר את **כתיבת ה-JSON ביד**.

---

## מה נשאר ידני / מה נעלם

| נושא | לפני | אחרי Registrate |
|---|---|---|
| `DeferredRegister` + `RegistryObject` | ידני ב-`ModsItems`/`ModBlocks` | `REGISTRATE.item(...).register()` |
| `models/item/*.json` | ידני | נוצר ב-datagen (ברירת מחדל) |
| `blockstates/*.json` + `models/block/*.json` | ידני | נוצר ב-datagen |
| `lang/en_us.json` (שמות) | ידני | `.lang("שם")` או ברירת מחדל |
| loot tables לבלוקים פשוטים | ידני / datagen | ברירת מחדל: drop self |
| tags | JSON ידני | `.tag(...)` |
| טקסטורות PNG | ידני | **עדיין ידני** |
| לוגיקה מיוחדת (`MetalDetectorItem`) | מחלקה custom | נשארת — רק הרישום משתנה |
| Creative Tab | `ModCreativeModTabs` | אפשר דרך Registrate או להשאיר כמו היום |

---

## גרסה מומלצת (1.20 / 1.20.1)

מ-Maven של tterrag (עודכן):

| | ערך |
|---|---|
| Artifact | `com.tterrag.registrate:Registrate` |
| גרסה לפיתוח | **`MC1.20-1.3.11`** |
| Maven | `https://maven.tterrag.com/` |
| Branch בקוד | [1.20](https://github.com/tterrag1098/Registrate/tree/1.20) |

---

## שלב 1 — הוספת Maven + תלות ב-`build.gradle`

### 1.1 Repository

בתוך בלוק `repositories { ... }`:

<div dir="ltr">

```groovy
maven {
    name = 'tterrag maven (Registrate)'
    url = 'https://maven.tterrag.com/'
}
```

</div>

**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `name = '...'` | שם לתצוגה בלוגים של Gradle |
| `url = 'https://maven.tterrag.com/'` | המאגר הרשמי של Registrate |

### 1.2 Dependency (פשוט — בלי Jar-in-Jar)

לפיתוח מקומי מספיק:

<div dir="ltr">

```groovy
dependencies {
    // ... שאר התלויות שלכם (Forge, VS2, וכו')

    implementation fg.deobf("com.tterrag.registrate:Registrate:MC1.20-1.3.11")
}
```

</div>

**הסבר:**

| שורה | הסבר |
|---|---|
| `implementation` | התלות זמינה בקומפילציה + ריצה |
| `fg.deobf(...)` | ForgeGradle ממפה שמות כמו את Minecraft — **חובה** לספריות Forge |
| `MC1.20-1.3.11` | גרסת Registrate ל-Minecraft 1.20.x |

### 1.3 (אופציונלי) Jar-in-Jar — לארוז את Registrate בתוך ה-JAR שלכם

אם אתם מפיצים את המוד למישהו **בלי** להתקין Registrate בנפרד:

<div dir="ltr">

```groovy
// איפשהו ב-build.gradle (ברמת root של הסקריפט)
jarJar.enable()

reobf {
    jarJar { }
}

tasks.jarJar.finalizedBy('reobfJarJar')

dependencies {
    implementation fg.deobf("com.tterrag.registrate:Registrate:MC1.20-1.3.11")
    // טווח גרסאות לאריזה בתוך ה-jar
    jarJar(group: 'com.tterrag.registrate', name: 'Registrate', version: "[MC1.20,MC1.21)")
}
```

</div>

> ⚠️ אחרי Jar-in-Jar, ה-JAR שתפיצו הוא בדרך כלל זה עם הסיומת `-all` (אלא אם החלפתם classifiers).  
> פרטים: [Forge Jar-in-Jar](https://forge.gemwire.uk/wiki/Jar-in-jar).

### 1.4 (מומלץ) גרסה ב-`gradle.properties`

<div dir="ltr">

```properties
registrate_version=MC1.20-1.3.11
```

</div>

ואז ב-`build.gradle`:

<div dir="ltr">

```groovy
implementation fg.deobf("com.tterrag.registrate:Registrate:${registrate_version}")
```

</div>

לאחר השינוי:

<div dir="ltr">

```bash
./gradlew --refresh-dependencies
```

</div>

(ב-Windows: `gradlew.bat --refresh-dependencies`)

---

## שלב 2 — יצירת אובייקט `Registrate` במוד

**חשוב:** אם השדה `static` נמצא ב-`@Mod` class, צריך ליצור אותו **בעצלנות (lazy)** — אחרת הוא עלול להיווצר מוקדם מדי בטעינה.

### אפשרות A — lazy (מומלץ ב-`TutorialMod`)

<div dir="ltr">

```java
package net.bullettrain.tutorialmod;

import com.tterrag.registrate.Registrate;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
// ...

@Mod(TutorialMod.MOD_ID)
public class TutorialMod {
    public static final String MOD_ID = "tutorialmod";

    /** נקודת הכניסה לכל הרישום דרך Registrate */
    public static final NonNullSupplier<Registrate> REGISTRATE =
            NonNullSupplier.lazy(() -> Registrate.create(MOD_ID));

    public TutorialMod() {
        // גישה ראשונה מפעילה את היצירה בזמן בטוח
        REGISTRATE.get();

        // בהמשך: קריאות שרושמות פריטים/בלוקים
        // ModsItems.register();  // או אתחול סטטי של השדות
        // ModBlocks.register();
    }
}
```

</div>

**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `NonNullSupplier.lazy(() -> ...)` | יוצר את הערך רק בפעם הראשונה שקוראים ל-`.get()` |
| `Registrate.create(MOD_ID)` | יוצר מופע Registrate עם ה-mod id שלנו (`tutorialmod`) |
| `REGISTRATE.get()` | מפעיל יצירה בזמן טעינת המוד (בטוח) |

### אפשרות B — שדה רגיל (רק אם **לא** ב-static initializer מוקדם)

<div dir="ltr">

```java
public static final Registrate REGISTRATE = Registrate.create(TutorialMod.MOD_ID);
```

</div>

בפועל, עם `@Mod`, **אפשרות A בטוחה יותר**.

---

## שלב 3 — המרת פריטים (`ModsItems`) מ-DeferredRegister ל-Registrate

### לפני (היום במוד שלנו)

<div dir="ltr">

```java
public class ModsItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TutorialMod.MOD_ID);

    public static final RegistryObject<Item> SAPPHIRE = ITEMS.register("sapphire",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> RAW_SAPPHIRE = ITEMS.register("raw_sapphire",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> METAL_DETECTOR = ITEMS.register("metal_detector",
            () -> new MetalDetectorItem(new Item.Properties().durability(100)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
```

</div>

### אחרי (Registrate)

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.item;

import com.tterrag.registrate.util.entry.ItemEntry;
import net.bullettrain.tutorialmod.TutorialMod;
import net.bullettrain.tutorialmod.item.custom.MetalDetectorItem;
import net.minecraft.world.item.Item;

public class ModsItems {
    // פריט פשוט — model + lang נוצרים אוטומטית ב-datagen
    public static final ItemEntry<Item> SAPPHIRE = TutorialMod.REGISTRATE.get()
            .item("sapphire", Item::new)
            .lang("Sapphire")
            .register();

    public static final ItemEntry<Item> RAW_SAPPHIRE = TutorialMod.REGISTRATE.get()
            .item("raw_sapphire", Item::new)
            .lang("Raw Sapphire")
            .register();

    // פריט עם מחלקה מותאמת + durability
    public static final ItemEntry<MetalDetectorItem> METAL_DETECTOR = TutorialMod.REGISTRATE.get()
            .item("metal_detector", props -> new MetalDetectorItem(props.durability(100)))
            .lang("Metal Detector")
            .register();

    /** קריאה שמכריחה אתטעינת השדות הסטטיים (רישום בפועל) */
    public static void register() {
        // אין צורך ב-ITEMS.register(eventBus) — Registrate מטפל בזה
    }
}
```

</div>

**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `ItemEntry<Item>` | כמו `RegistryObject` — מחזיק הפניה לפריט שנרשם |
| `.item("sapphire", Item::new)` | רושם פריט עם id `tutorialmod:sapphire` |
| `Item::new` | method reference — יוצר `new Item(properties)` |
| `.lang("Sapphire")` | שם תצוגה באנגלית (ייכנס ל-lang ב-datagen) |
| `.register()` | **סוגר** את השרשרת ורושם באמת |
| `props -> new MetalDetectorItem(props.durability(100))` | מעביר `Item.Properties` למחלקה המותאמת |

### שינוי ב-`TutorialMod`

**לפני:**

<div dir="ltr">

```java
ModsItems.register(modEventBus);
```

</div>

**אחרי:**

<div dir="ltr">

```java
// רק כדי לוודא שה-class נטען והשדות הסטטיים נרשמים
ModsItems.register();
// או: Class.forName / גישה ל-ModsItems.SAPPHIRE
```

</div>

> 💡 גישה לכל `ItemEntry` סטטי (למשל ב-Creative Tab) גם מפעילה רישום.  
> חשוב ש-`ModsItems` ייטען **אחרי** `REGISTRATE.get()`.

### גישה לפריט בקוד

| לפני | אחרי |
|---|---|
| `ModsItems.SAPPHIRE.get()` | `ModsItems.SAPPHIRE.get()` (אותו דבר!) |
| `ModsItems.SAPPHIRE` כ-`RegistryObject` | `ModsItems.SAPPHIRE` כ-`ItemEntry` |

`ItemEntry` תומך ב-`.get()` כמו `RegistryObject`, אז רוב הקוד (Creative Tab וכו') ממשיך לעבוד.

---

## שלב 4 — המרת בלוקים (`ModBlocks`)

### לפני (מקוצר)

`registerBlock` + `registerBlockItem` + JSON ידני לכל בלוק.

### אחרי — בלוק פשוט + BlockItem אוטומטי

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.block;

import com.tterrag.registrate.util.entry.BlockEntry;
import net.bullettrain.tutorialmod.TutorialMod;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Blocks;

public class ModBlocks {

    public static final BlockEntry<Block> JACKIETONITE_ORE_BLOCK = TutorialMod.REGISTRATE.get()
            .block("jackietonite_ore_block", props -> new Block(props
                    .mapColor(MapColor.STONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 3.0F)))
            .lang("Jackietonite Block")
            .simpleItem()          // יוצר BlockItem + model פריט
            .register();

    public static final BlockEntry<Block> RAW_JACKIETONITE_ORE_BLOCK = TutorialMod.REGISTRATE.get()
            .block("raw_jackietonite_ore_block", props -> new Block(props
                    .mapColor(MapColor.STONE)
                    .strength(3.0F, 3.0F)))
            .lang("Raw Jackietonite Block")
            .simpleItem()
            .register();

    public static final BlockEntry<DropExperienceBlock> JACKIETONITE_ORE = TutorialMod.REGISTRATE.get()
            .block("jackietonite_ore", props -> new DropExperienceBlock(
                    BlockBehaviour.Properties.copy(Blocks.STONE)
                            .strength(3f)
                            .requiresCorrectToolForDrops(),
                    UniformInt.of(3, 6)))
            .lang("Jackietonite Ore")
            .simpleItem()
            // loot מותאם (לא drop-self) — ראו למטה
            .register();

    public static void register() { }
}
```

</div>

**הסבר:**

| שורה | הסבר |
|---|---|
| `.block("id", factory)` | רושם בלוק; `props` = `BlockBehaviour.Properties` |
| `.simpleItem()` | יוצר `BlockItem` + model פריט ברירת מחדל |
| `.lang("...")` | שם באנגלית ל-lang datagen |
| ברירת מחדל | blockstate פשוט + cube_all model + loot drop-self |

### מה לגבי `registerBlockItem` הידני?

**נמחק.** `.simpleItem()` / `.item()` מחליפים אותו.

---

## שלב 5 — הפעלת Data Generation (כאן נעלמים ה-JSON)

Registrate מייצר JSON **רק** כשמריצים datagen.

### 5.1 וידוא ש-`runData` קיים (כבר יש אצלכם)

ב-`build.gradle` של המוד כבר מוגדר משהו בסגנון:

<div dir="ltr">

```groovy
data {
    args '--mod', mod_id, '--all',
         '--output', file('src/generated/resources/'),
         '--existing', file('src/main/resources/')
}
```

</div>

וגם:

<div dir="ltr">

```groovy
sourceSets.main.resources {
    srcDir 'src/generated/resources'
}
```

</div>

### 5.2 חיבור Registrate ל-datagen (GatherDataEvent)

צרו מחלקה (או הוסיפו ל-event קיים):

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.datagen;

import net.bullettrain.tutorialmod.TutorialMod;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TutorialMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModDataGenerators {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        // Registrate רושם לבד את כל ה-providers שלו
        TutorialMod.REGISTRATE.get().addDataGenerators(event);
    }
}
```

</div>

> ⚠️ שם המתודה / ה-API המדויק (`addDataGenerators` / `setupDatagen` וכו') עשוי להשתנות מעט בין גרסאות.  
> אם IntelliJ מסמן שגיאה — השתמשו ב-autocomplete על `REGISTRATE.get()` וחפשו `Data` / `GatherData`.  
> בגרסאות רבות מספיק:

<div dir="ltr">

```java
// חלק מהגרסאות:
TutorialMod.REGISTRATE.get().addDataGenerator(/* ... */);
// או רישום אוטומטי כבר ב-create() — בדקו Javadoc של MC1.20-1.3.11
```

</div>

### 5.3 הרצה

<div dir="ltr">

```bash
./gradlew runData
```

</div>

הקבצים יופיעו תחת:

<div dir="ltr">

```
src/generated/resources/assets/tutorialmod/...
src/generated/resources/data/tutorialmod/...
```

</div>

### 5.4 מה עושים עם ה-JSON הישנים?

| מצב | פעולה |
|---|---|
| JSON ידני זהה לברירת המחדל | **למחוק** מ-`src/main/resources` (כדי שלא יתנגש) |
| JSON מותאם (מודל 3D, blockstate מורכב) | להשאיר ב-`src/main/resources` **או** להגדיר ב-Registrate `.model(...)` / `.blockstate(...)` |
| `en_us.json` ידני | אפשר למזג: datagen יוסיף מפתחות; אל תכפילו מפתחות |

> כלל אצבע: **generated גובר / משתלב** דרך `src/generated` + `srcDir`.  
> אל תעתיקו ידנית את אותו קובץ גם ל-`main` וגם ל-`generated`.

---

## שלב 6 — דוגמאות נפוצות (במקום JSON)

### 6.1 שם + model פריט רגיל (generated item/handheld)

<div dir="ltr">

```java
REGISTRATE.get().item("sapphire", Item::new)
    .lang("Sapphire")
    .defaultModel()   // או ברירת מחדל בלי לקרוא בכלל
    .register();
```

</div>

### 6.2 בלוק + tags (במקום `data/minecraft/tags/blocks/mineable/pickaxe.json`)

<div dir="ltr">

```java
import net.minecraft.tags.BlockTags;

REGISTRATE.get().block("jackietonite_ore_block", Block::new)
    .properties(p -> p.strength(3f).requiresCorrectToolForDrops())
    .tag(BlockTags.MINEABLE_WITH_PICKAXE)
    .tag(BlockTags.NEEDS_IRON_TOOL)   // לפי הצורך
    .simpleItem()
    .lang("Jackietonite Block")
    .register();
```

</div>

**לפני:** עריכת JSON תגיות.  
**אחרי:** שורת `.tag(...)` — JSON נוצר ב-`runData`.

### 6.3 Creative Tab דרך Registrate (אופציונלי)

אפשר להמשיך עם `ModCreativeModTabs` הקיים (עובד עם `.get()`),  
או לעבור ל-API של Registrate לטאבים (תלוי גרסה).  
למתחילים: **השאירו את `ModCreativeModTabs`** ורק החליפו `RegistryObject` → `ItemEntry`/`BlockEntry`.

### 6.4 פריט עם מחלקה מותאמת (Metal Detector)

המחלקה `MetalDetectorItem` **לא משתנה**.  
רק הרישום:

<div dir="ltr">

```java
public static final ItemEntry<MetalDetectorItem> METAL_DETECTOR =
    TutorialMod.REGISTRATE.get()
        .item("metal_detector", p -> new MetalDetectorItem(p.durability(100)))
        .lang("Metal Detector")
        .register();
```

</div>

### 6.5 Loot מותאם לעפרה (לא drop-self)

לבלוקים כמו ore, ברירת המחדל (drop self) לא מספיקה.  
אפשרויות:

1. להשאיר loot JSON ב-`data/.../loot_tables` ידני / datagen ישן  
2. להגדיר `.loot(...)` ב-Registrate (API של LootBuilder)

דוגמה כללית (בדקו חתימה ב-Javadoc):

<div dir="ltr">

```java
.loot((tables, block) -> tables.add(block,
    // בניית LootTable.Builder — כמו ב-BlockLootSubProvider
))
```

</div>

---

## שלב 7 — סדר עבודה מומלץ למעבר (במוד הקיים)

1. הוסיפו Maven + dependency + `./gradlew --refresh-dependencies`
2. הוסיפו `REGISTRATE` lazy ב-`TutorialMod`
3. **המירו פריט אחד** (`SAPPHIRE`) ל-Registrate
4. `runData` → בדקו שנוצר `models/item/sapphire.json` + lang
5. מחקו את ה-JSON הידני הכפול של sapphire
6. `runClient` → ודאו שהפריט מופיע עם טקסטורה
7. המשיכו פריט-פריט, אחר כך בלוק-בלוק
8. רק בסוף מחקו את `DeferredRegister` הישן לגמרי

> אל תמירו הכל בבת אחת. מעבר הדרגתי = פחות כאב ראש.

---

## לפני / אחרי — סיכום ויזואלי

### רישום Sapphire

| | קוד |
|---|---|
| **לפני** | `ITEMS.register("sapphire", () -> new Item(...))` + `models/item/sapphire.json` + שורה ב-`en_us.json` |
| **אחרי** | `.item("sapphire", Item::new).lang("Sapphire").register()` + `runData` |

### רישום בלוק

| | קוד |
|---|---|
| **לפני** | `registerBlock` + `registerBlockItem` + blockstate + models/block + models/item + lang |
| **אחרי** | `.block(...).simpleItem().lang(...).register()` + `runData` |

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| **Registrate** | ספרייה (לא מוד) לרישום fluent + datagen אוטומטי |
| **`Registrate.create(modId)`** | יוצר מופע לכל המוד |
| **`ItemEntry` / `BlockEntry`** | כמו `RegistryObject` — `.get()` מחזיר את האובייקט |
| **`.register()`** | חובה בסוף כל שרשרת — בלי זה אין רישום |
| **`.simpleItem()`** | BlockItem אוטומטי לבלוק |
| **`.lang("...")`** | שם תצוגה ל-datagen |
| **`.tag(...)`** | מוסיף ל-tag (JSON נוצר ב-datagen) |
| **`runData`** | מריץ data generators — **בלי זה אין JSON חדשים** |
| **Jar-in-Jar** | אריזת Registrate בתוך ה-JAR שלכם להפצה |
| **`fg.deobf`** | מיפוי שמות לספריית Forge חיצונית |

---

## בעיות נפוצות

| בעיה | פתרון |
|---|---|
| `Could not find com.tterrag.registrate...` | חסר maven `https://maven.tterrag.com/` |
| פריט בלי טקסטורה / סגול-שחור | אין PNG ב-`textures/item/<name>.png` **או** לא הרצתם `runData` |
| כפילות / התנגשות JSON | מחקו JSON ידני שכבר נוצר ב-`generated` |
| `Exception in Registrate` בטעינה | צרו lazy (`NonNullSupplier.lazy`) במקום `static final Registrate = create()` מוקדם |
| Creative tab ריק | ודאו ש-`ModsItems` נטען לפני בניית ה-tab; השתמשו ב-`.get()` |
| Loot של ore לא נכון | ברירת מחדל היא drop-self — הגדירו loot מותאם |
| עובד ב-dev אבל לא ב-JAR | הוסיפו Jar-in-Jar **או** דרשו התקנת Registrate (לא רלוונטי — זה lib בתוך jar) |

---

## קישורים

- קוד: [github.com/tterrag1098/Registrate/tree/1.20](https://github.com/tterrag1098/Registrate/tree/1.20)
- Maven: [maven.tterrag.com — Registrate](https://maven.tterrag.com/com/tterrag/registrate/Registrate/)
- Wiki רשמי (WIP): [Registrate Wiki](https://github.com/tterrag1098/Registrate/wiki)
- המוד שלנו: [AgentMelinda/XenoPixelsNetwork](https://github.com/AgentMelinda/XenoPixelsNetwork)

---

## מה הלאה אחרי המעבר?

- [Data Generation ידני (שלב 12)](Step-12-Data-Generation) — להבין מה Registrate מייצר מאחורי הקלעים  
- [Build + VS2](Advanced-01-Build-and-VS2) — אם משלבים גם Valkyrien Skies  
- חזרו ל-[שלב 02 · פריטים](Step-02-Custom-Items) והשוו: אותו רעיון, פחות קבצים

---

<div dir="ltr">

⬅️ [Advanced 04 — Mixin Fix](Advanced-04-Mixin-Fix) · ➡️ [Home](Home)

</div>

</div>

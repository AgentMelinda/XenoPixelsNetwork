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
| Jackietonite Ore Block | `tutorialmod:jackietonite_ore_block` | בלוק עפרה מותאם אישית, חוזק 3.0, דורש כלי נכון לשבירה |

---

## טאב קריאייטיב

טאב מותאם אישית בשם **Tutorial Tab** המכיל את כל הפריטים והבלוקים של המוד:
- Sapphire
- Raw Sapphire
- Jackietonite Ore Block

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

    // רישום בלוק עפרה עם מאפיינים מותאמים
    public static final RegistryObject<Block> JACKIETONITE_ORE_BLOCK =
        registerBlock("jackietonite_ore_block",
            () -> new Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)               // צבע על המפה
                .instrument(NoteBlockInstrument.BASEDRUM) // צליל note block
                .requiresCorrectToolForDrops()          // דורש כלי נכון לשבירה
                .strength(3.0F, 3.0F)                   // חוזק הבלוק
            )
        );

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

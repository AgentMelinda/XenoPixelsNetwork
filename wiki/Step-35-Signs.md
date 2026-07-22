<div dir="rtl">

# 🔧 שלב 35 — שלטים (Signs)

בהמשך לסט העץ משלב 34, בשלב הזה אנחנו מוסיפים **שלטים (Signs)** מעץ האורן שלנו: שלט עומד, שלט על קיר, שלט תלוי (hanging sign) ושלט תלוי על קיר. זה דורש הגדרת `WoodType` משלנו, בלוקי שלט שיורשים מהמחלקות המובנות של מייןקראפט, ו-Block Entities קטנים ששומרים את הטקסט.

## מה השתנה לעומת השלב הקודם

| קובץ | מה נוסף |
|---|---|
| `util/ModWoodTypes.java` | הגדרת `WoodType.PINE` |
| `block/custom/ModStandingSignBlock.java` ועוד 3 בלוקים | בלוקי שלט שיורשים מ-Standing/Wall/Hanging Sign |
| `block/entity/ModSignBlockEntity.java` + `ModHangingSignBlockEntity.java` | ה-BE ששומר טקסט שלט |
| `block/entity/ModBlockEntities.java` | רישום שני ה-BE |
| `item/ModsItems.java` + `ModCreativeModTabs.java` | פריטי שלט (SignItem / HangingSignItem) |
| `event/ModEventBusClientEvents.java` | רישום רינדוררים לשלטים |
| `TutorialMod.java` | `Sheets.addWoodType(ModWoodTypes.PINE)` |

> מודלי השלטים, ה-blockstates, ה-lang והמרקמים (גם `textures/entity/signs/pine.png`) נוצרים אוטומטית ע"י ה-Data Generator.

---

## סוג העץ — `util/ModWoodTypes.java`

<div dir="ltr">

```java
public class ModWoodTypes {
    public static final WoodType PINE = WoodType.register(new WoodType(TutorialMod.MOD_ID + ":pine", BlockSetType.OAK));
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `WoodType.register(...)` | רושם סוג עץ חדש (נדרש כדי ששלטים/דלתות יעבדו) |
| `BlockSetType.OAK` | משתמש בסאונדים והתנהגות של אלון (פשוט יותר) |

---

## בלוקי השלט — דוגמת `ModStandingSignBlock.java`

<div dir="ltr">

```java
public class ModStandingSignBlock extends StandingSignBlock {
    public ModStandingSignBlock(Properties pProperties, WoodType pType) {
        super(pProperties, pType);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new ModSignBlockEntity(pPos, pState);
    }
}
```

</div>
הבלוקים `ModWallSignBlock`, `ModHangingSignBlock`, `ModWallHangingSignBlock` זהים באותו דפוס — כל אחד יורש מהמחלקה המתאימה ומחזיר את ה-BE המתאים.

**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `extends StandingSignBlock` | משתמש בלוגיקת שלט עומד מובנית |
| `new ModSignBlockEntity(...)` | כל שלט צריך BE ששומר את הטקסט |

---

## ה-Block Entity של השלט — `ModSignBlockEntity.java`

<div dir="ltr">

```java
public class ModSignBlockEntity extends SignBlockEntity {
    public ModSignBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(ModBlockEntities.MOD_SIGN.get(), pPos, pBlockState);
    }

    @Override
    public BlockEntityType<?> getType() {
        return ModBlockEntities.MOD_SIGN.get();
    }
}
```

</div>
`ModHangingSignBlockEntity` זהה רק עם `MOD_HANGING_SIGN`. הרישום ב-`ModBlockEntities`:
<div dir="ltr">

```java
public static final RegistryObject<BlockEntityType<ModSignBlockEntity>> MOD_SIGN =
        BLOCK_ENTITIES.register("mod_sign", () ->
                BlockEntityType.Builder.of(ModSignBlockEntity::new,
                        ModBlocks.PINE_SIGN.get(), ModBlocks.PINE_WALL_SIGN.get()).build(null));
public static final RegistryObject<BlockEntityType<ModHangingSignBlockEntity>> MOD_HANGING_SIGN =
        BLOCK_ENTITIES.register("mod_hanging_sign", () ->
                BlockEntityType.Builder.of(ModHangingSignBlockEntity::new,
                        ModBlocks.PINE_HANGING_SIGN.get(), ModBlocks.PINE_WALL_HANGING_SIGN.get()).build(null));
```

</div>
---

## הפריטים והרינדוררים

ב-`ModsItems` נרשמו פריטי שלט:
<div dir="ltr">

```java
public static final RegistryObject<Item> PINE_SIGN = ITEMS.register("pine_sign",
        () -> new SignItem(new Item.Properties().stacksTo(16), ModBlocks.PINE_SIGN.get(), ModBlocks.PINE_WALL_SIGN.get()));
public static final RegistryObject<Item> PINE_HANGING_SIGN = ITEMS.register("pine_hanging_sign",
        () -> new HangingSignItem(ModBlocks.PINE_HANGING_SIGN.get(), ModBlocks.PINE_WALL_HANGING_SIGN.get(), new Item.Properties().stacksTo(16)));
```

</div>
ב-`ModEventBusClientEvents` נרשמו הרינדוררים (`SignRenderer`, `HangingSignRenderer`), וב-`TutorialMod.onClientSetup` נוספה השורה `Sheets.addWoodType(ModWoodTypes.PINE);` שדואגת שהמראה של שלטי האורן ייטען.

## מושגי מפתח

| מושג | הסבר |
|---|---|
| `WoodType` | סוג עץ רשום — דרוש לשלטים, דלתות וכו' |
| `SignBlockEntity` | BE ששומר את השורות שכתובות על השלט |
| `SignItem` / `HangingSignItem` | פריטי השלט שמציבים אותם בעולם |
| `Sheets.addWoodType` | רושם את המראה (textures) של סוג העץ לציור שלטים |

<div dir="ltr">

⬅️ [שלב 34](Step-34-Wood) · ➡️ [שלב 36](Step-36-Boats)

</div>

</div>
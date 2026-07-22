<div dir="rtl">

# 🔧 שלב 34 — עץ (Wood)

בשלב הזה אנחנו בונים **סט עץ (Wood Set) שלם** מעץ האורן (Pine): גזע, עץ, פלטות, עלים, וגרסאות "קלופות" (stripped). זה לא סתם בלוקים — אנחנו דואגים שהעץ **ידליק** (flammable), שאפשר **לקלף** אותו עם גרזן, שהוא נכנס ל-tags של מייןקראפט (כך שהוא עובד עם נפחים, דלק וכו'), ושהמודלים והלוט-טבלאות נוצרים אוטומטית ע"י ה-Data Generator.

## מה השתנה לעומת השלב הקודם

| קובץ | מה נוסף |
|---|---|
| `block/custom/ModFlammableRotatedPillarBlock.java` | בלוק גזע שנדלק ומגיב לגרזן (קילוף) |
| `block/ModBlocks.java` | רישום כל הבלוקים (log/wood/planks/leaves) |
| `datagen/ModBlockStateProvider.java` | יצירת מודלים לגזעים/פלטות/עלים אוטומטית |
| `datagen/ModBlockTagGenerator.java` + `ModItemTagGenerator.java` | הוספה ל-tags `logs_that_burn` ו-`planks` |
| `datagen/loot/ModBlockLootTables.java` | לוט-טבלאות לכל הבלוקים |
| `ModCreativeModTabs.java` + משאבים (textures/models/lang) | טאב, מרקמים ושמות |

> שים לב: קבצי המודלים (`models/block/*.json`), ה-blockstates, ה-lang (`en_us.json`) והמרקמים (`.png`) **נוצרו אוטומטית על ידי Gradle Data Generation** — לא כתבנו אותם ידנית.

---

## בלוק הגזע הבוער — `ModFlammableRotatedPillarBlock.java`

<div dir="ltr">

```java
public class ModFlammableRotatedPillarBlock extends RotatedPillarBlock {
    public ModFlammableRotatedPillarBlock(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return true;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 5;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 5;
    }

    @Override
    public @Nullable BlockState getToolModifiedState(BlockState state, UseOnContext context, ToolAction toolAction, boolean simulate) {
        if(context.getItemInHand().getItem() instanceof AxeItem) {
            if(state.is(ModBlocks.PINE_LOG.get())) {
                return ModBlocks.STRIPPED_PINE_LOG.get().defaultBlockState().setValue(AXIS, state.getValue(AXIS));
            }
            if(state.is(ModBlocks.PINE_WOOD.get())) {
                return ModBlocks.STRIPPED_PINE_WOOD.get().defaultBlockState().setValue(AXIS, state.getValue(AXIS));
            }
        }
        return super.getToolModifiedState(state, context, toolAction, simulate);
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `extends RotatedPillarBlock` | בלוק גזע שמסתובב לפי הציר (עומד/שכוב) |
| `isFlammable`/`getFlammability` | קובעים שהבלוק נדלק ובאיזו מידה |
| `getToolModifiedState` | מגיב לכלי — אם זה גרזן, מחזיר את הגרסה ה"קלופה" |
| `.setValue(AXIS, ...)` | שומר את כיוון הגזע אחרי הקילוף |

---

## רישום הבלוקים — `ModBlocks.java`

<div dir="ltr">

```java
public static final RegistryObject<Block> PINE_LOG = registerBlock("pine_log",
        () -> new ModFlammableRotatedPillarBlock(BlockBehaviour.Properties.copy(Blocks.OAK_LOG).strength(3f)));
public static final RegistryObject<Block> PINE_WOOD = registerBlock("pine_wood",
        () -> new ModFlammableRotatedPillarBlock(BlockBehaviour.Properties.copy(Blocks.OAK_WOOD).strength(3f)));
public static final RegistryObject<Block> STRIPPED_PINE_LOG = registerBlock("stripped_pine_log",
        () -> new ModFlammableRotatedPillarBlock(BlockBehaviour.Properties.copy(Blocks.STRIPPED_OAK_LOG).strength(3f)));
public static final RegistryObject<Block> STRIPPED_PINE_WOOD = registerBlock("stripped_pine_wood",
        () -> new ModFlammableRotatedPillarBlock(BlockBehaviour.Properties.copy(Blocks.STRIPPED_OAK_WOOD).strength(3f)));

public static final RegistryObject<Block> PINE_PLANKS = registerBlock("pine_planks",
        () -> new Block(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)) {
            @Override public boolean isFlammable(BlockState s, BlockGetter l, BlockPos p, Direction d) { return true; }
            @Override public int getFlammability(BlockState s, BlockGetter l, BlockPos p, Direction d) { return 20; }
            @Override public int getFireSpreadSpeed(BlockState s, BlockGetter l, BlockPos p, Direction d) { return 5; }
        });
public static final RegistryObject<Block> PINE_LEAVES = registerBlock("pine_leaves",
        () -> new LeavesBlock(BlockBehaviour.Properties.copy(Blocks.OAK_LEAVES)) { /* flammable overrides */ });
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `Properties.copy(Blocks.OAK_LOG)` | מעתיק את התכונות מעץ האלון של מייןקראפט |
| `PINE_PLANKS` (בלוק אנונימי) | פלטות — מגדירים בעירה ישירות כי אין מחלקה משלנו |
| `LeavesBlock` | בלוק עלים (נשרים, שקוף חלקית, תומך בעלים של שתיל) |

---

## ה-Data Generator

ב-`ModBlockStateProvider` השתמשנו בעוזרים של Forge כדי ליצור את כל המודלים: `logBlock`/`axisBlock` לגזעים, `blockWithItem` לפלטות, ופונקציית `leavesBlock` לעלים. ב-`ModBlockTagGenerator` ו-`ModItemTagGenerator` הוספנו את הבלוקים ל-tags `logs_that_burn` ו-`planks` (גם ברמת הבלוק וגם ברמת הפריט). הלוט-טבלאות נוצרו דרך `dropSelf` ו-`createLeavesDrops`.

## מושגי מפתח

| מושג | הסבר |
|---|---|
| `RotatedPillarBlock` | בלוק עמוד/גזע שמסתובב לפי ציר |
| `ToolAction` / גרזן | פעולת כלי — כאן משמשת לקילוף (stripping) |
| `BlockTags.LOGS_THAT_BURN` | תג שמאפשר לעץ לעבוד עם תנורים, נפחים וכו' |
| Data Generation | Gradle יוצר עבורנו JSON של מודלים/טאגים/לוט |

<div dir="ltr">

⬅️ [שלב 33](Step-33-Block-Entity-Renderer) · ➡️ [שלב 35](Step-35-Signs)

</div>

</div>
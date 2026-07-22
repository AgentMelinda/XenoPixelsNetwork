<div dir="rtl">

# 🔧 שלב 39 — עץ מותאם (Custom Tree)

בהמשך לסט העץ משלב 34, עכשיו אנחנו יוצרים **עץ אורן (Pine) מותאם**: שתיל (Sapling) שכשהוא גדל — צומח עץ שלם בעזרת הגדרת `TreeConfiguration`. בניגוד לעפרות משלב 38, עץ דורש קישור בין השתיל ל"מגדל עצים" (`TreeGrower`) שבוחר את ה-Configured Feature המתאים.

## מה השתנה לעומת השלב הקודם

| קובץ | מה נוסף |
|---|---|
| `worldgen/tree/PineTreeGrower.java` | המחלקה שמחליטה איזה עץ לגדל |
| `block/ModBlocks.java` | רישום `PINE_SAPLING` (SaplingBlock) |
| `worldgen/ModConfiguredFeatures.java` | נוסף `PINE_KEY` עם `Feature.TREE` |
| `datagen/ModBlockStateProvider.java` + `ModItemModelProvider.java` + `ModBlockLootTables.java` + `ModCreativeModTabs.java` | מודל שתיל, לוט, טאב |

> קובץ `worldgen/configured_feature/pine.json` (וה-placed feature) **נוצר אוטומטית ע"י Gradle Data Generation**.

---

## השתיל — `block/ModBlocks.java`

<div dir="ltr">

```java
public static final RegistryObject<Block> PINE_SAPLING = registerBlock("pine_sapling",
        () -> new SaplingBlock(new PineTreeGrower(), BlockBehaviour.Properties.copy(Blocks.OAK_SAPLING)));
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `SaplingBlock(new PineTreeGrower(), ...)` | בלוק שתיל שמשתמש במגדל העצים שלנו כדי לגדול |

---

## מגדל העצים — `worldgen/tree/PineTreeGrower.java`

<div dir="ltr">

```java
public class PineTreeGrower extends AbstractTreeGrower {
    @Nullable
    @Override
    protected ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(RandomSource pRandom, boolean pHasFlowers) {
        return ModConfiguredFeatures.PINE_KEY;
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `extends AbstractTreeGrower` | מחלקת בסיס לשתילים — גורמת לעץ לצמוח |
| `getConfiguredFeature` | מחזירה איזה Configured Feature (עץ) לגדל — כאן תמיד את ה-Pine |

---

## הגדרת העץ — `ModConfiguredFeatures.java` (PINE_KEY)

<div dir="ltr">

```java
public static final ResourceKey<ConfiguredFeature<?, ?>> PINE_KEY = registerKey("pine");

// בתוך bootstrap:
register(context, PINE_KEY, Feature.TREE, new TreeConfiguration.TreeConfigurationBuilder(
        BlockStateProvider.simple(ModBlocks.PINE_LOG.get()),
        new StraightTrunkPlacer(5, 4, 3),

        BlockStateProvider.simple(ModBlocks.PINE_LEAVES.get()),
        new BlobFoliagePlacer(ConstantInt.of(3), ConstantInt.of(2), 3),

        new TwoLayersFeatureSize(1, 0, 2)).build());
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `Feature.TREE` | סוג התכונה — עץ |
| `BlockStateProvider.simple(PINE_LOG)` | חומר הגזע |
| `StraightTrunkPlacer(5, 4, 3)` | יוצר גזע ישר (גובה בסיס 5, טווח רנדומלי) |
| `BlockStateProvider.simple(PINE_LEAVES)` | חומר העלים |
| `BlobFoliagePlacer(3, 2, 3)` | צורת העלווה (עיגול) |
| `TwoLayersFeatureSize(1, 0, 2)` | גודל שכבות העץ |

## מושגי מפתח

| מושג | הסבר |
|---|---|
| `SaplingBlock` | בלוק שתיל שגדל לעץ |
| `AbstractTreeGrower` | מגדל עצים — מחבר שתיל ל-Configured Feature |
| `TreeConfiguration` | הגדרת עץ: גזע + עלים + placers |
| `TrunkPlacer` / `FoliagePlacer` | איך הגזע והעלווה נבנים |

<div dir="ltr">

⬅️ [שלב 38](Step-38-Ore-Generation) · ➡️ [שלב 40](Step-40-Tree-Gen)

</div>

</div>
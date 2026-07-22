<div dir="rtl">

# 🔧 שלב 38 — יצירת עפרות בעולם (Ore Generation)

עד עכשיו כל הבלוקים שלנו היו נוצרים ביד. בשלב הזה אנחנו גורמים ל**עפרת הספיר (Sapphire Ore)** להיווצר בעולם באופן טבעי — באוברוורלד, בנתר ובאנד. זה נעשה דרך מערכת ה-World Generation: **Configured Feature** (איך העפרה נראית), **Placed Feature** (איפה וכמה), ו-**Biome Modifier** (באילו ביומים להכניס אותה).

## מה השתנה לעומת השלב הקודם

| קובץ | מה נוסף |
|---|---|
| `worldgen/ModConfiguredFeatures.java` | הגדרת ה-Ore Configured Features (Overworld/Nether/End) |
| `worldgen/ModOrePlacement.java` | עזרים למיקום עפרה (כמות/גובה) |
| `worldgen/ModPlacedFeatures.java` | הגדרת ה-Placed Features |
| `worldgen/ModBiomeModifiers.java` | הוספת העפרות לביומים דרך Forge Biome Modifiers |
| `datagen/ModWorldGenProvider.java` | רושם את כל נתוני העולם ל-Data Generator |
| קבצי JSON ב-`data/tutorialmod/worldgen/` | נוצרים אוטומטית ע"י Gradle |

---

## ה-Configured Feature — `worldgen/ModConfiguredFeatures.java`

<div dir="ltr">

```java
public static void bootstrap(BootstapContext<ConfiguredFeature<?, ?>> context) {
    RuleTest stoneReplaceable = new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES);
    RuleTest deepslateReplaceables = new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES);
    RuleTest netherrackReplacables = new BlockMatchTest(Blocks.NETHERRACK);
    RuleTest endReplaceables = new BlockMatchTest(Blocks.END_STONE);

    List<OreConfiguration.TargetBlockState> overworldSapphireOres = List.of(
            OreConfiguration.target(stoneReplaceable, ModBlocks.SAPPHIRE_ORE.get().defaultBlockState()),
            OreConfiguration.target(deepslateReplaceables, ModBlocks.DEEPSLATE_SAPPHIRE_ORE.get().defaultBlockState()));

    register(context, OVERWORLD_SAPPHIRE_ORE_KEY, Feature.ORE, new OreConfiguration(overworldSapphireOres, 9));
    register(context, NETHER_SAPPHIRE_ORE_KEY, Feature.ORE, new OreConfiguration(netherrackReplacables,
            ModBlocks.NETHER_SAPPHIRE_ORE.get().defaultBlockState(), 9));
    register(context, END_SAPPHIRE_ORE_KEY, Feature.ORE, new OreConfiguration(endReplaceables,
            ModBlocks.END_STONE_SAPPHIRE_ORE.get().defaultBlockState(), 9));
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `RuleTest` | מתי מותר להחליף בלוק (אבן/דיפסלייט/נתרק/אנד-סטון) |
| `OreConfiguration.target(...)` | "בבלוק X, שים את עפרת הספיר" |
| `Feature.ORE` | סוג התכונה — פיזור עורות |
| `new OreConfiguration(..., 9)` | גודל הורידה המקסימלי (9 בלוקים) |

---

## עזרי מיקום — `worldgen/ModOrePlacement.java`

<div dir="ltr">

```java
public class ModOrePlacement {
    public static List<PlacementModifier> orePlacement(PlacementModifier p_195347_, PlacementModifier p_195348_) {
        return List.of(p_195347_, InSquarePlacement.spread(), p_195348_, BiomeFilter.biome());
    }
    public static List<PlacementModifier> commonOrePlacement(int pCount, PlacementModifier pHeightRange) {
        return orePlacement(CountPlacement.of(pCount), pHeightRange);
    }
    public static List<PlacementModifier> rareOrePlacement(int pChance, PlacementModifier pHeightRange) {
        return orePlacement(RarityFilter.onAverageOnceEvery(pChance), pHeightRange);
    }
}
```

</div>
---

## ה-Placed Feature — `worldgen/ModPlacedFeatures.java`

<div dir="ltr">

```java
public static void bootstrap(BootstapContext<PlacedFeature> context) {
    HolderGetter<ConfiguredFeature<?, ?>> configuredFeatures = context.lookup(Registries.CONFIGURED_FEATURE);

    register(context, SAPPHIRE_ORE_PLACED_KEY, configuredFeatures.getOrThrow(ModConfiguredFeatures.OVERWORLD_SAPPHIRE_ORE_KEY),
            ModOrePlacement.commonOrePlacement(12,
                    HeightRangePlacement.uniform(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(80))));
    // ... אותו דבר ל-Nether ו-End
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `commonOrePlacement(12, ...)` | 12 ורידים בממוצע לאזור |
| `HeightRangePlacement.uniform(-64, 80)` | טווח הגבהים שבו העפרה מופיעה |

---

## ה-Biome Modifier — `worldgen/ModBiomeModifiers.java`

<div dir="ltr">

```java
public static void bootstrap(BootstapContext<BiomeModifier> context) {
    var placedFeatures = context.lookup(Registries.PLACED_FEATURE);
    var biomes = context.lookup(Registries.BIOME);

    context.register(ADD_SAPPHIRE_ORE, new ForgeBiomeModifiers.AddFeaturesBiomeModifier(
            biomes.getOrThrow(BiomeTags.IS_OVERWORLD),
            HolderSet.direct(placedFeatures.getOrThrow(ModPlacedFeatures.SAPPHIRE_ORE_PLACED_KEY)),
            GenerationStep.Decoration.UNDERGROUND_ORES));
    // ... נתר ואנד באותו דפוס עם IS_NETHER / IS_END
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `AddFeaturesBiomeModifier` | מוסיף תכונה לביומים מסוימים |
| `BiomeTags.IS_OVERWORLD` | מכוון לכל ביומי האוברוורלד |
| `GenerationStep.Decoration.UNDERGROUND_ORES` | בשלב יצירת העורות התת-קרקעיות |

## מושגי מפתח

| מושג | הסבר |
|---|---|
| `ConfiguredFeature` | מה התכונה יוצרת (כאן: ורידי עפרה) |
| `PlacedFeature` | איפה/כמה פעמים התכונה מופיעה |
| `BiomeModifier` | מכניס תכונה לביומים נבחרים |
| `RuleTest` | תנאי החלפת בלוק בזמן יצירת עולם |

<div dir="ltr">

⬅️ [שלב 37](Step-37-Throwable-Projectile) · ➡️ [שלב 39](Step-39-Custom-Tree)

</div>

</div>
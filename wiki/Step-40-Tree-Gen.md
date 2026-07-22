<div dir="rtl">

# 🔧 שלב 40 — יצירת עץ (Tree Gen)

בשלב הקודם הגדרנו את העץ (Configured Feature) אבל הוא עדיין לא מופיע בעולם. בשלב הזה אנחנו מכניסים את עץ האורן ל**ביומי המישורים (Plains)** בעזרת Placed Feature ו-Biome Modifier — כך שכשהשחקן מהלך במישורים, הוא יראה עצי אורן.

## מה השתנה לעומת השלב הקודם

| קובץ | מה נוסף |
|---|---|
| `worldgen/ModPlacedFeatures.java` | נוסף `PINE_PLACED_KEY` (עץ + מיקום) |
| `worldgen/ModBiomeModifiers.java` | נוסף `ADD_TREE_PINE` שמוסיף את העץ לביומי המישורים |

---

## ה-Placed Feature — `ModPlacedFeatures.java`

<div dir="ltr">

```java
public static final ResourceKey<PlacedFeature> PINE_PLACED_KEY = registerKey("pine_placed");

// בתוך bootstrap:
register(context, PINE_PLACED_KEY, configuredFeatures.getOrThrow(ModConfiguredFeatures.PINE_KEY),
        VegetationPlacements.treePlacement(PlacementUtils.countExtra(3, 0.1f, 2),
                ModBlocks.PINE_SAPLING.get()));
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `PINE_PLACED_KEY` | מפתח ה-Placed Feature של העץ |
| `configuredFeatures.getOrThrow(PINE_KEY)` | מקשר ל-Configured Feature מהשלב הקודם |
| `VegetationPlacements.treePlacement(...)` | מציב עץ עם לוגיקת צמחייה תקנית |
| `countExtra(3, 0.1f, 2)` | ~3 עצים באזור, עם סיכוי לעוד |
| `PINE_SAPLING` | השתיל ש"גדל" כדי ליצור את העץ |

---

## ה-Biome Modifier — `ModBiomeModifiers.java`

<div dir="ltr">

```java
public static final ResourceKey<BiomeModifier> ADD_TREE_PINE = registerKey("add_tree_pine");

// בתוך bootstrap (אחרי הורידות הספיר):
context.register(ADD_TREE_PINE, new ForgeBiomeModifiers.AddFeaturesBiomeModifier(
        biomes.getOrThrow(Tags.Biomes.IS_PLAINS),
        HolderSet.direct(placedFeatures.getOrThrow(ModPlacedFeatures.PINE_PLACED_KEY)),
        GenerationStep.Decoration.VEGETAL_DECORATION));
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `Tags.Biomes.IS_PLAINS` | מכוון לכל ביומי המישורים |
| `HolderSet.direct(PINE_PLACED_KEY)` | מוסיף את ה-Placed Feature הזה בלבד |
| `GenerationStep.Decoration.VEGETAL_DECORATION` | שלב יצירת הצמחייה (עצים/שיחים) |

<div dir="ltr">

⬅️ [שלב 39](Step-39-Custom-Tree) · ➡️ [שלב 41](Step-41-Trunk-Placers)

</div>

</div>
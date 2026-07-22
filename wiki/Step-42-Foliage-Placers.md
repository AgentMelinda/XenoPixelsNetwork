<div dir="rtl">

# 🔧 שלב 42 — Foliage Placers

בשלב הקודם יצרנו Trunk Placer מותאם. עכשיו אנחנו יוצרים **Foliage Placer משלנו** (`PineFoliagePlacer`) שקובע איך העלווה של עץ האורן נראית — במקום ה-`BlobFoliagePlacer` המובנה. כמו עם ה-Trunk Placer, אנחנו חייבים **לרשום את הסוג** (`FoliagePlacerType`) כדי שיעבוד עם ה-Data Generator.

## מה השתנה לעומת השלב הקודם

| קובץ | מה נוסף |
|---|---|
| `worldgen/tree/ModFoliagePlacers.java` | רישום סוג ה-Foliage Placer |
| `worldgen/tree/custom/PineFoliagePlacer.java` | ה-Foliage Placer המותאם |
| `worldgen/ModConfiguredFeatures.java` | מחליף את `BlobFoliagePlacer` ב-`PineFoliagePlacer` |
| `TutorialMod.java` | רישום `ModFoliagePlacers` |

---

## רישום הסוג — `worldgen/tree/ModFoliagePlacers.java`

<div dir="ltr">

```java
public class ModFoliagePlacers {
    public static final DeferredRegister<FoliagePlacerType<?>> FOLIAGE_PLACERS =
            DeferredRegister.create(Registries.FOLIAGE_PLACER_TYPE, TutorialMod.MOD_ID);

    public static final RegistryObject<FoliagePlacerType<PineFoliagePlacer>> PINE_FOLIAGE_PLACER =
            FOLIAGE_PLACERS.register("pine_foliage_placer", () -> new FoliagePlacerType<>(PineFoliagePlacer.CODEC));

    public static void register(IEventBus eventBus) {
        FOLIAGE_PLACERS.register(eventBus);
    }
}
```

</div>
---

## ה-Foliage Placer המותאם — `worldgen/tree/custom/PineFoliagePlacer.java`

<div dir="ltr">

```java
public class PineFoliagePlacer extends FoliagePlacer {
    public static final Codec<PineFoliagePlacer> CODEC = RecordCodecBuilder.create(pineFoliagePlacerInstance
            -> foliagePlacerParts(pineFoliagePlacerInstance).and(Codec.intRange(0, 16).fieldOf("height")
            .forGetter(fp -> fp.height)).apply(pineFoliagePlacerInstance, PineFoliagePlacer::new));
    private final int height;

    public PineFoliagePlacer(IntProvider pRadius, IntProvider pOffset, int height) {
        super(pRadius, pOffset);
        this.height = height;
    }

    @Override
    protected FoliagePlacerType<?> type() { return ModFoliagePlacers.PINE_FOLIAGE_PLACER.get(); }

    @Override
    protected void createFoliage(LevelSimulatedReader pLevel, FoliageSetter pBlockSetter, RandomSource pRandom, TreeConfiguration pConfig,
                                 int pMaxFreeTreeHeight, FoliageAttachment pAttachment, int pFoliageHeight, int pFoliageRadius, int pOffset) {

        this.placeLeavesRow(pLevel, pBlockSetter, pRandom, pConfig, pAttachment.pos().above(0), 2, 2, pAttachment.doubleTrunk());
        this.placeLeavesRow(pLevel, pBlockSetter, pRandom, pConfig, pAttachment.pos().above(1), 2, 2, pAttachment.doubleTrunk());
        this.placeLeavesRow(pLevel, pBlockSetter, pRandom, pConfig, pAttachment.pos().above(2), 2, 2, pAttachment.doubleTrunk());
    }

    @Override
    public int foliageHeight(RandomSource pRandom, int pHeight, TreeConfiguration pConfig) {
        return this.height;
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource pRandom, int pLocalX, int pLocalY, int pLocalZ, int pRange, boolean pLarge) {
        return false;
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `extends FoliagePlacer` | מחלקת בסיס לבניית עלווה |
| `Codec.intRange(0,16).fieldOf("height")` | מוסיף למתכון שדה `height` (בין 0 ל-16) לטעינה מ-JSON |
| `createFoliage(...)` | מציב 3 שורות עלים מעל ראש הגזע (בגובה 0/1/2) |
| `placeLeavesRow(..., 2, 2, ...)` | שורת עלים ברדיוס 2 |
| `foliageHeight(...)` | מחזירה את גובה העלווה (כאן קבוע לפי `height`) |
| `shouldSkipLocation` | מחזירה `false` = לא דילוג על אף מיקום (עלווה מלאה) |

---

## חיבור ב-`ModConfiguredFeatures`

<div dir="ltr">

```java
register(context, PINE_KEY, Feature.TREE, new TreeConfiguration.TreeConfigurationBuilder(
        BlockStateProvider.simple(ModBlocks.PINE_LOG.get()),
        new PineTrunkPlacer(5, 4, 3),

        BlockStateProvider.simple(ModBlocks.PINE_LEAVES.get()),
        new PineFoliagePlacer(ConstantInt.of(3), ConstantInt.of(2), 3),

        new TwoLayersFeatureSize(1, 0, 2)).build());
```

</div>
כעת העץ משתמש גם ב-Trunk Placer וגם ב-Foliage Placer המותאמים שלנו.

## מושגי מפתח

| מושג | הסבר |
|---|---|
| `FoliagePlacer` | קובע איך העלווה של העץ נבנית |
| `FoliagePlacerType` | רישום של ה-Placer לטעינה מ-JSON |
| `placeLeavesRow` | מציבה שורת עלים מסביב לנקודה |

<div dir="ltr">

⬅️ [שלב 41](Step-41-Trunk-Placers) · ➡️ [שלב 43](Step-43-Biomes)

</div>

</div>
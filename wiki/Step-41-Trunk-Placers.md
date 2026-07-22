<div dir="rtl">

# 🔧 שלב 41 — Trunk Placers

בשלב 39 השתמשנו ב-`StraightTrunkPlacer` המובנה (גזע ישר). בשלב הזה אנחנו יוצרים **Trunk Placer משלנו** (`PineTrunkPlacer`) שיוצר גזע עם "ענפים" בולטים — כדי לתת לעץ האורן מראה ייחודי. כדי שזה יעבוד עם ה-Data Generator ועם קבצי JSON, אנחנו חייבים **לרשום את סוג ה-Trunk Placer** (`TrunkPlacerType`) ב-Registry.

## מה השתנה לעומת השלב הקודם

| קובץ | מה נוסף |
|---|---|
| `worldgen/tree/ModTrunkPlacerTypes.java` | רישום סוג ה-Trunk Placer |
| `worldgen/tree/custom/PineTrunkPlacer.java` | ה-Trunk Placer המותאם |
| `worldgen/ModConfiguredFeatures.java` | מחליף את `StraightTrunkPlacer` ב-`PineTrunkPlacer` |
| `TutorialMod.java` | רישום `ModTrunkPlacerTypes` |

---

## רישום הסוג — `worldgen/tree/ModTrunkPlacerTypes.java`

<div dir="ltr">

```java
public class ModTrunkPlacerTypes {
    public static final DeferredRegister<TrunkPlacerType<?>> TRUNK_PLACER =
            DeferredRegister.create(Registries.TRUNK_PLACER_TYPE, TutorialMod.MOD_ID);

    public static final RegistryObject<TrunkPlacerType<PineTrunkPlacer>> PINE_TRUNK_PLACER =
            TRUNK_PLACER.register("pine_trunk_placer", () -> new TrunkPlacerType<>(PineTrunkPlacer.CODEC));

    public static void register(IEventBus eventBus) {
        TRUNK_PLACER.register(eventBus);
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `DeferredRegister<TrunkPlacerType<?>>` | רשם לסוגי Trunk Placer |
| `new TrunkPlacerType<>(PineTrunkPlacer.CODEC)` | יוצר את הסוג מ-CODEC (כדי שאפשר לקרוא אותו מ-JSON) |

---

## ה-Trunk Placer המותאם — `worldgen/tree/custom/PineTrunkPlacer.java`

<div dir="ltr">

```java
public class PineTrunkPlacer extends TrunkPlacer {
    public static final Codec<PineTrunkPlacer> CODEC = RecordCodecBuilder.create(pineTrunkPlacerInstance ->
            trunkPlacerParts(pineTrunkPlacerInstance).apply(pineTrunkPlacerInstance, PineTrunkPlacer::new));

    public PineTrunkPlacer(int pBaseHeight, int pHeightRandA, int pHeightRandB) {
        super(pBaseHeight, pHeightRandA, pHeightRandB);
    }

    @Override
    protected TrunkPlacerType<?> type() { return ModTrunkPlacerTypes.PINE_TRUNK_PLACER.get(); }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(LevelSimulatedReader pLevel, BiConsumer<BlockPos, BlockState> pBlockSetter,
                                                            RandomSource pRandom, int pFreeTreeHeight, BlockPos pPos, TreeConfiguration pConfig) {
        setDirtAt(pLevel, pBlockSetter, pRandom, pPos.below(), pConfig);
        int height = pFreeTreeHeight + pRandom.nextInt(heightRandA, heightRandA + 3) + pRandom.nextInt(heightRandB - 1, heightRandB + 1);

        for(int i = 0; i < height; i++) {
            placeLog(pLevel, pBlockSetter, pRandom, pPos.above(i), pConfig);

            if(i % 2 == 0 && pRandom.nextBoolean()) {
                if(pRandom.nextFloat() > 0.25f) {
                    for(int x = 0; x < 4; x++) {
                        pBlockSetter.accept(pPos.above(i).relative(Direction.NORTH, x), ((BlockState)
                                Function.identity().apply(pConfig.trunkProvider.getState(pRandom, pPos).setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z))));
                    }
                }
                // ... אותו דפוס ל-SOUTH / EAST / WEST (ענפים בולטים)
            }
        }
        return ImmutableList.of(new FoliagePlacer.FoliageAttachment(pPos.above(height), 0, false));
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `extends TrunkPlacer` | מחלקת בסיס לבניית גזעים |
| `Codec<PineTrunkPlacer>` | מאפשר לקרוא את ה-placer מקובץ JSON (נדרש ל-Data Gen) |
| `setDirtAt(...)` | שם בלוק אדמה/שורש בתחתית |
| `placeLog(...)` | מציב בלוק גזע בגובה i |
| `relative(Direction.NORTH, x)` | יוצר ענף בולט לכיוון מסוים (לכל אורך 4) |
| `setValue(RotatedPillarBlock.AXIS, ...)` | מסובב את בלוק הענף לפי הציר הנכון |
| `FoliageAttachment(...)` | מחזיר איפה העלים יתחילו (ראש הגזע) |

## מושגי מפתח

| מושג | הסבר |
|---|---|
| `TrunkPlacer` | קובע איך הגזע של העץ נבנה |
| `TrunkPlacerType` | רישום של ה-Placer כדי שניתן לטעון אותו מ-JSON |
| `Codec` | ממיר בין אובייקט ל-JSON (לפענוח/כתיבה) |

<div dir="ltr">

⬅️ [שלב 40](Step-40-Tree-Gen) · ➡️ [שלב 42](Step-42-Foliage-Placers)

</div>

</div>
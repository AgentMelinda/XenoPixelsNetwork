<div dir="rtl">

# 🔧 שלב 44 — ממד (Dimension)

בשלב האחרון אנחנו יוצרים **ממד (Dimension) חדש לגמרי** — "KaupenDim" — עם בלוק פורטל משלנו שמעביר את השחקן בין העולם הרגיל לממד. זה דורש הגדרת `DimensionType` (תכונות הממד), `LevelStem` (איך הממד נבנה), בלוק פורטל, ו-Teleporter שמעביר את השחקן ויוצר פורטל בצד השני.

## מה השתנה לעומת השלב הקודם

| קובץ | מה נוסף |
|---|---|
| `worldgen/dimension/ModDimensions.java` | הגדרת סוג הממד + ה-LevelStem |
| `worldgen/portal/ModTeleporter.java` | מעביר את השחקן בין הממדים |
| `block/custom/ModPortalBlock.java` | בלוק הפורטל |
| `block/ModBlocks.java` + `ModCreativeModTabs.java` | רישום בלוק הפורטל |
| `datagen/ModWorldGenProvider.java` | רישום DIMENSION_TYPE ו-LEVEL_STEM ב-Data Generator |
| קבצי JSON ב-`data/tutorialmod/dimension/` ו-`dimension_type/` | נוצרים אוטומטית ע"י Gradle |

---

## הגדרת הממד — `worldgen/dimension/ModDimensions.java` (תמצית)

<div dir="ltr">

```java
public class ModDimensions {
    public static final ResourceKey<LevelStem> KAUPENDIM_KEY = ResourceKey.create(Registries.LEVEL_STEM,
            new ResourceLocation(TutorialMod.MOD_ID, "kaupendim"));
    public static final ResourceKey<Level> KAUPENDIM_LEVEL_KEY = ResourceKey.create(Registries.DIMENSION,
            new ResourceLocation(TutorialMod.MOD_ID, "kaupendim"));
    public static final ResourceKey<DimensionType> KAUPEN_DIM_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE,
            new ResourceLocation(TutorialMod.MOD_ID, "kaupendim_type"));

    public static void bootstrapType(BootstapContext<DimensionType> context) {
        context.register(KAUPEN_DIM_TYPE, new DimensionType(
                OptionalLong.of(12000), false, false, false, false, 1.0,
                true, false, 0, 256, 256, BlockTags.INFINIBURN_OVERWORLD,
                BuiltinDimensionTypes.OVERWORLD_EFFECTS, 1.0f,
                new DimensionType.MonsterSettings(false, false, ConstantInt.of(0), 0)));
    }

    public static void bootstrapStem(BootstapContext<LevelStem> context) {
        HolderGetter<Biome> biomeRegistry = context.lookup(Registries.BIOME);
        HolderGetter<DimensionType> dimTypes = context.lookup(Registries.DIMENSION_TYPE);
        HolderGetter<NoiseGeneratorSettings> noiseGenSettings = context.lookup(Registries.NOISE_SETTINGS);

        NoiseBasedChunkGenerator noiseBasedChunkGenerator = new NoiseBasedChunkGenerator(
                MultiNoiseBiomeSource.createFromList(new Climate.ParameterList<>(List.of(
                        Pair.of(Climate.parameters(0.0F,0.0F,0.0F,0.0F,0.0F,0.0F,0.0F), biomeRegistry.getOrThrow(ModBiomes.TEST_BIOME)),
                        Pair.of(Climate.parameters(0.1F,0.2F,0.0F,0.2F,0.0F,0.0F,0.0F), biomeRegistry.getOrThrow(Biomes.BIRCH_FOREST)),
                        Pair.of(Climate.parameters(0.3F,0.6F,0.1F,0.1F,0.0F,0.0F,0.0F), biomeRegistry.getOrThrow(Biomes.OCEAN)),
                        Pair.of(Climate.parameters(0.4F,0.3F,0.2F,0.1F,0.0F,0.0F,0.0F), biomeRegistry.getOrThrow(Biomes.DARK_FOREST))
                ))),
                noiseGenSettings.getOrThrow(NoiseGeneratorSettings.AMPLIFIED));

        LevelStem stem = new LevelStem(dimTypes.getOrThrow(ModDimensions.KAUPEN_DIM_TYPE), noiseBasedChunkGenerator);
        context.register(KAUPENDIM_KEY, stem);
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `KAUPENDIM_LEVEL_KEY` | מזהה הממד עצמו (כתובת לטלפורטציה) |
| `KAUPEN_DIM_TYPE` | סוג הממד (זמן קבוע 12000, אור שמש 1.0) |
| `bootstrapType` | מגדיר את תכונות סוג הממד |
| `MultiNoiseBiomeSource` | מקור ביומים לפי פרמטרים אקלימיים (הביום שלנו + כמה מובנים) |
| `NoiseBasedChunkGenerator(..., AMPLIFIED)` | מייצר טרrain שטוח/מוגבר (AMPLIFIED) |
| `LevelStem` | מקשר סוג ממד + יוצר העולם |

---

## הטלפורטר — `worldgen/portal/ModTeleporter.java` (תמצית)

<div dir="ltr">

```java
public class ModTeleporter implements ITeleporter {
    public static BlockPos thisPos = BlockPos.ZERO;
    public static boolean insideDimension = true;

    public ModTeleporter(BlockPos pos, boolean insideDim) {
        thisPos = pos; insideDimension = insideDim;
    }

    @Override
    public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destinationWorld,
                              float yaw, Function<Boolean, Entity> repositionEntity) {
        entity = repositionEntity.apply(false);
        int y = insideDimension ? thisPos.getY() : 61;
        BlockPos destinationPos = new BlockPos(thisPos.getX(), y, thisPos.getZ());

        int tries = 0;
        while (/* התאורה תפוסה */ (tries < 25)) { destinationPos = destinationPos.above(2); tries++; }

        entity.setPos(destinationPos.getX(), destinationPos.getY(), destinationPos.getZ());

        if (insideDimension) {
            // מחפש פורטל קיים בקרבת מקום; אם אין — מציב פורטל חדש
            boolean doSetBlock = true;
            for (BlockPos checkPos : BlockPos.betweenClosed(destinationPos.below(10).west(10), destinationPos.above(10).east(10))) {
                if (destinationWorld.getBlockState(checkPos).getBlock() instanceof ModPortalBlock) { doSetBlock = false; break; }
            }
            if (doSetBlock) { destinationWorld.setBlock(destinationPos, ModBlocks.MOD_PORTAL.get().defaultBlockState(), 3); }
        }
        return entity;
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `implements ITeleporter` | ממשק המעבר בין ממדים של Forge |
| `repositionEntity.apply(false)` | מעביר את הישות לעולם היעד |
| `destinationPos` | מחפש מקום פנוי (מעל ל-61) כדי להניח את השחקן |
| `setBlock(MOD_PORTAL)` | יוצר פורטל חדש בצד השני אם אין קיים |

---

## בלוק הפורטל — `block/custom/ModPortalBlock.java`

<div dir="ltr">

```java
public class ModPortalBlock extends Block {
    public ModPortalBlock(Properties pProperties) { super(pProperties); }

    @Override
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        if (pPlayer.canChangeDimensions()) {
            handleKaupenPortal(pPlayer, pPos);
            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.CONSUME;
        }
    }

    private void handleKaupenPortal(Entity player, BlockPos pPos) {
        if (player.level() instanceof ServerLevel serverlevel) {
            MinecraftServer minecraftserver = serverlevel.getServer();
            ResourceKey<Level> resourcekey = player.level().dimension() == ModDimensions.KAUPENDIM_LEVEL_KEY ?
                    Level.OVERWORLD : ModDimensions.KAUPENDIM_LEVEL_KEY;
            ServerLevel portalDimension = minecraftserver.getLevel(resourcekey);
            if (portalDimension != null && !player.isPassenger()) {
                if(resourcekey == ModDimensions.KAUPENDIM_LEVEL_KEY) {
                    player.changeDimension(portalDimension, new ModTeleporter(pPos, true));
                } else {
                    player.changeDimension(portalDimension, new ModTeleporter(pPos, false));
                }
            }
        }
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `use(...)` | בלחיצה על הפורטל — מנסה לעבור ממד |
| `dimension() == KAUPENDIM_LEVEL_KEY` | בודק באיזה ממד נמצאים כדי לדעת לאן לעבור |
| `changeDimension(portalDimension, new ModTeleporter(...))` | מעביר את השחקן לממד היעד עם הטלפורטר שלנו |

---

## רישום ב-Data Generator

ב-`ModWorldGenProvider` ה-`BUILDER` הורחב:
<div dir="ltr">

```java
.add(Registries.DIMENSION_TYPE, ModDimensions::bootstrapType)
.add(Registries.BIOME, ModBiomes::boostrap)
.add(Registries.LEVEL_STEM, ModDimensions::bootstrapStem);
```

</div>
## מושגי מפתח

| מושג | הסבר |
|---|---|
| `DimensionType` | תכונות הממד (זמן, אור, גובה) |
| `LevelStem` | מקשר סוג ממד + יוצר עולם (Noise/ביומים) |
| `ITeleporter` | ממשק למעבר בין ממדים |
| `changeDimension` | מעביר ישות לממד אחר |

<div dir="ltr">

⬅️ [שלב 43](Step-43-Biomes) · ➡️ [דף הבית](Home)

</div>

</div>
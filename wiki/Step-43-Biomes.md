<div dir="rtl">

# 🔧 שלב 43 — ביומים (Biomes)

עד עכשיו הכנסנו תכונות (עפרות, עצים) לביומים קיימים. בשלב הזה אנחנו יוצרים **ביום משלנו** (`test_biome`) עם צבעים, צליל רקע וספאון יצורים משלנו, ומחליפים בעזרת **TerraBlender** את ביום היער (Forest) בביום שלנו. זה דורש גם הגדרת כללי פני שטח (Surface Rules) מותאמים.

## מה השתנה לעומת השלב הקודם

| קובץ | מה נוסף |
|---|---|
| `worldgen/biome/ModBiomes.java` | הגדרת הביום (צבעים, ספאון, תכונות) |
| `worldgen/biome/ModOverworldRegion.java` | אזור TerraBlender שמחליף את ה-Forest |
| `worldgen/biome/ModTerrablender.java` | רישום האזור |
| `worldgen/biome/surface/ModSurfaceRules.java` | כללי פני שטח מותאמים |
| `build.gradle` + `gradle.properties` + `settings.gradle` + `mods.toml` | תלות TerraBlender |
| `TutorialMod.java` + `ModWorldGenProvider.java` | חיבור TerraBlender ורישום הביום |

---

## הגדרת הביום — `worldgen/biome/ModBiomes.java` (תמצית)

<div dir="ltr">

```java
public class ModBiomes {
    public static final ResourceKey<Biome> TEST_BIOME = ResourceKey.create(Registries.BIOME,
            new ResourceLocation(TutorialMod.MOD_ID, "test_biome"));

    public static void boostrap(BootstapContext<Biome> context) {
        context.register(TEST_BIOME, testBiome(context));
    }

    public static Biome testBiome(BootstapContext<Biome> context) {
        MobSpawnSettings.Builder spawnBuilder = new MobSpawnSettings.Builder();
        spawnBuilder.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(ModEntities.RHINO.get(), 2, 3, 5));
        spawnBuilder.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.WOLF, 5, 4, 4));
        BiomeDefaultFeatures.farmAnimals(spawnBuilder);
        BiomeDefaultFeatures.commonSpawns(spawnBuilder);

        BiomeGenerationSettings.Builder biomeBuilder =
                new BiomeGenerationSettings.Builder(context.lookup(Registries.PLACED_FEATURE), context.lookup(Registries.CONFIGURED_CARVER));
        globalOverworldGeneration(biomeBuilder);
        BiomeDefaultFeatures.addDefaultOres(biomeBuilder);
        biomeBuilder.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.TREES_PLAINS);
        biomeBuilder.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, ModPlacedFeatures.PINE_PLACED_KEY);

        return new Biome.BiomeBuilder()
                .hasPrecipitation(true)
                .downfall(0.8f)
                .temperature(0.7f)
                .generationSettings(biomeBuilder.build())
                .mobSpawnSettings(spawnBuilder.build())
                .specialEffects((new BiomeSpecialEffects.Builder())
                        .waterColor(0xe82e3b).waterFogColor(0xbf1b26).skyColor(0x30c918)
                        .grassColorOverride(0x7f03fc).foliageColorOverride(0xd203fc)
                        .fogColor(0x22a1e6)
                        .ambientMoodSound(AmbientMoodSettings.LEGACY_CAVE_SETTINGS)
                        .backgroundMusic(Musics.createGameMusic(ModSounds.BAR_BRAWL.getHolder().get())).build())
                .build();
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `ResourceKey.create(Registries.BIOME, ...)` | מזהה הביום (`test_biome`) |
| `addSpawn(RHINO, 2, 3, 5)` | קרנפים יופיעו (קבוצות של 3-5, משקל 2) |
| `BiomeDefaultFeatures.*` | מוסיף תכונות סטנדרטיות של עולם (אבנים, עורות, צמחייה) |
| `PINE_PLACED_KEY` | עצי האורן שלנו יופיעו בביום |
| `BiomeSpecialEffects` | צבעי מים/שמיים/ערפל/עשב + מוזיקת רקע (התקליט BAR_BRAWL) |

---

## TerraBlender — החלפת ה-Forest

`ModTerrablender.registerBiomes()` רושם אזור:
<div dir="ltr">

```java
public class ModOverworldRegion extends Region {
    public ModOverworldRegion(ResourceLocation name, int weight) {
        super(name, RegionType.OVERWORLD, weight);
    }
    @Override
    public void addBiomes(Registry<Biome> registry, Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
        this.addModifiedVanillaOverworldBiomes(mapper, modifiedVanillaOverworldBuilder -> {
            modifiedVanillaOverworldBuilder.replaceBiome(Biomes.FOREST, ModBiomes.TEST_BIOME);
        });
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `RegionType.OVERWORLD` | האזור שייך לעולם הרגיל |
| `replaceBiome(FOREST, TEST_BIOME)` | בכל מקום שהיה יער — יהיה הביום שלנו |

---

## כללי פני שטח — `worldgen/biome/surface/ModSurfaceRules.java`

<div dir="ltr">

```java
public static SurfaceRules.RuleSource makeRules() {
    SurfaceRules.ConditionSource isAtOrAboveWaterLevel = SurfaceRules.waterBlockCheck(-1, 0);
    SurfaceRules.RuleSource grassSurface = SurfaceRules.sequence(
            SurfaceRules.ifTrue(isAtOrAboveWaterLevel, GRASS_BLOCK), DIRT);

    return SurfaceRules.sequence(
            SurfaceRules.sequence(SurfaceRules.ifTrue(SurfaceRules.isBiome(ModBiomes.TEST_BIOME),
                            SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, RAW_SAPPHIRE)),
                    SurfaceRules.ifTrue(SurfaceRules.ON_CEILING, SAPPHIRE)),
            SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, grassSurface));
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `makeRules()` | מחזירה את כללי פני השטח לביום |
| `isBiome(TEST_BIOME)` | רק בביום שלנו — רצפת ספיר גולמי, תקרת ספיר |
| `grassSurface` | ברירת מחדל: דשא למעלה, אדמה מתחת |

נרשם ב-`TutorialMod.commonSetup`: `SurfaceRuleManager.addSurfaceRules(SurfaceRuleManager.RuleCategory.OVERWORLD, MOD_ID, ModSurfaceRules.makeRules());`

## תלויות ה-build

`terrablender_version=3.0.0.169` ב-`gradle.properties`, פלאגין SpongePowered Mixin + maven repo ב-`settings.gradle`, תלות `TerraBlender-forge` ב-`build.gradle`, ותלות `terrablender` ב-`mods.toml`.

## מושגי מפתח

| מושג | הסבר |
|---|---|
| `Biome` | ביום — אקלים, צבעים, ספאון ותכונות |
| TerraBlender | מוד שמאפשר להוסיף/לשנות ביומים בעולם |
| `Region` | אזור שמגדיר אילו ביומים להחליף |
| `SurfaceRules` | כללי איזה בלוק בראש/בתקרה של הביום |

<div dir="ltr">

⬅️ [שלב 42](Step-42-Foliage-Placers) · ➡️ [שלב 44](Step-44-Dimension)

</div>

</div>
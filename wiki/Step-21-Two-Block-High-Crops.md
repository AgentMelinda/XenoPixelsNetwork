<div dir="rtl">

# 🔧 שלב 21 — גידולים בגובה 2 בלוקים (Two-Block-High Crops)

בשלב הקודם יצרנו גידול רגיל (Strawberry) בגובה בלוק אחד. כעת אנו בונים **Corn Crop** —
גידול בגובה **2 בלוקים** (כמו Sugar Cane / Melons) עם 9 שלבי גידול.

> 💡 הגידול דו-ממדי מנוהל ע״י `CornCropBlock` שמנהלת שני שלבים:
> - שלבים 0-7: הבלוק התחתון גדל
> - שלב 8: הבלוק העליון (corn_top) ונעלם אם אין מקום

---

## מה השתנה לעומת השלב הקודם

| קבצים חדשים/ששונו | תיאור |
|---|---|
| `custom/CornCropBlock.java` | מחלקת Crop מותאמת בגובה 2 |
| `ModBlocks.java` | רישום `CORN_CROP` |
| `ModsItems.java` | `CORN_SEEDS` + `CORN` |
| `ModBlockStateProvider.java` | `makeCornCrop(...)` עם `ConfiguredModel` |
| `ModItemModelProvider.java` | `simpleItem` לזרעים ותפוח |
| `ModBlockLootTables.java` | loot table מותאמת לגידול דו-ממדי |
| `ModCreativeModTabs.java` | הוספת זרעים ותפוח לטאב |
| `en_us.json` | שמות חדשים |
| `textures/block/` | 9 תמונות שלביות |
| `textures/item/` | תמונות לזרעים ותפוח |

---

## קוד חדש ב-`custom/CornCropBlock.java`

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.block.custom;

import net.bullettrain.tutorialmod.item.ModsItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.IPlantable;

public class CornCropBlock extends CropBlock {
    public static final int FIRST_STAGE_MAX_AGE = 7;
    public static final int SECOND_STAGE_MAX_AGE = 1;

    private static final VoxelShape[] SHAPE_BY_AGE = new VoxelShape[]{
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D),
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D),
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D),
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D),
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 10.0D, 16.0D),
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 12.0D, 16.0D),
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 14.0D, 16.0D),
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D),
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D)};

    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 8);

    public CornCropBlock(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return SHAPE_BY_AGE[this.getAge(pState)];
    }

    public void randomTick(BlockState pState, ServerLevel pLevel, BlockPos pPos, RandomSource pRandom) {
        if (!pLevel.isAreaLoaded(pPos, 1)) return;
        if (pLevel.getRawBrightness(pPos, 0) >= 9) {
            int currentAge = this.getAge(pState);

            if (currentAge < this.getMaxAge()) {
                float growthSpeed = getGrowthSpeed(this, pLevel, pPos);

                if (net.minecraftforge.common.ForgeHooks.onCropsGrowPre(pLevel, pPos, pState, pRandom.nextInt((int)(25.0F / growthSpeed) + 1) == 0)) {
                    if(currentAge == FIRST_STAGE_MAX_AGE) {
                        if(pLevel.getBlockState(pPos.above(1)).is(Blocks.AIR)) {
                            pLevel.setBlock(pPos.above(1), this.getStateForAge(currentAge + 1), 2);
                        }
                    } else {
                        pLevel.setBlock(pPos, this.getStateForAge(currentAge + 1), 2);
                    }

                    net.minecraftforge.common.ForgeHooks.onCropsGrowPost(pLevel, pPos, pState);
                }
            }
        }
    }

    @Override
    public boolean canSustainPlant(BlockState state, BlockGetter world, BlockPos pos, Direction facing, IPlantable plantable) {
        return super.mayPlaceOn(state, world, pos);
    }

    @Override
    public boolean canSurvive(BlockState pState, LevelReader pLevel, BlockPos pPos) {
        return super.canSurvive(pState, pLevel, pPos) || (pLevel.getBlockState(pPos.below(1)).is(this) &&
                pLevel.getBlockState(pPos.below(1)).getValue(AGE) == 7);
    }

    @Override
    public void growCrops(Level pLevel, BlockPos pPos, BlockState pState) {
        int nextAge = this.getAge(pState) + this.getBonemealAgeIncrease(pLevel);
        int maxAge = this.getMaxAge();
        if(nextAge > maxAge) {
            nextAge = maxAge;
        }

        if(this.getAge(pState) == FIRST_STAGE_MAX_AGE && pLevel.getBlockState(pPos.above(1)).is(Blocks.AIR)) {
            pLevel.setBlock(pPos.above(1), this.getStateForAge(nextAge), 2);
        } else {
            pLevel.setBlock(pPos, this.getStateForAge(nextAge - SECOND_STAGE_MAX_AGE), 2);
        }
    }

    @Override
    public int getMaxAge() {
        return FIRST_STAGE_MAX_AGE + SECOND_STAGE_MAX_AGE;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return ModsItems.CORN_SEEDS.get();
    }

    @Override
    public IntegerProperty getAgeProperty() {
        return AGE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(AGE);
    }
}
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `FIRST_STAGE_MAX_AGE = 7` | שלבי הגידול התחתון (0-7) |
| `SECOND_STAGE_MAX_AGE = 1` | שלב הגידול העליון (8) — רק שלב אחד |
| `VoxelShape[] SHAPE_BY_AGE` | צורת ה-collision משתנה עם הגיל — גובה גדל מ-2 ל-16 |
| `IntegerProperty.create("age", 0, 8)` | מאפיין גיל מ-0 ל-8 (9 שלבים) |
| `randomTick(...)` | נקראת רנדומלית כל טיק — מנהלת צמיחה |
| `if(currentAge == FIRST_STAGE_MAX_AGE)` | כשמגיעים לגיל 7, יוצרים בלוק חדש מעל (`pPos.above(1)`) |
| `pLevel.setBlock(pPos.above(1), ...)`| מניח את הבלוק העליון בגיל 8 |
| `canSurvive(...)` | הבלוק העליון יכול לשרוד רק אם התחתון בגיל 7 |
| `growCrops(...)` | מנהלת צמיחה באמצעות bonemeal — מעבירה גיל מתחתון לעליון |

---

## קוד חדש ב-`ModBlockStateProvider.java`

<div dir="ltr">

```java
public void makeCornCrop(CropBlock block, String modelName, String textureName) {
    Function<BlockState, ConfiguredModel[]> function = state -> cornStates(state, block, modelName, textureName);
    getVariantBuilder(block).forAllStates(function);
}

private ConfiguredModel[] cornStates(BlockState state, CropBlock block, String modelName, String textureName) {
    ConfiguredModel[] models = new ConfiguredModel[1];
    models[0] = new ConfiguredModel(models().crop(modelName + "_" + state.getValue(((CornCropBlock) block).getAgeProperty()),
            new ResourceLocation(TutorialMod.MOD_ID, "block/" + textureName + "_" + state.getValue(((CornCropBlock) block).getAgeProperty()))).renderType("cutout"));
    return models;
}
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `forAllStates(function)` | יוצר model לכל גיל אפשרי (0-8) |
| `crop(modelName + "_" + age, ...)` | משתמש במוטיב `minecraft:block/crop` עם שם הקובץ הספציפי |

---

## קוד חדש ב-`ModBlockLootTables.java`

<div dir="ltr">

```java
this.add(ModBlocks.CORN_CROP.get(),
        block -> createCropDrops(ModBlocks.CORN_CROP.get(), ModsItems.CORN.get(),
                ModsItems.CORN_SEEDS.get(), lootitemcondition$builder));
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `createCropDrops(...)` | כשהגידול בגיל מלא (8), נותן CORN + CORN_SEEDS |

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| **`VoxelShape`** | צורת ה-collision של הבלוק — משתנה לפי גיל |
| **`randomTick`** | טיק רנדומלי — מנהל צמיחה אוטומטית |
| **`pPos.above(1)`**| הבלוק befindet sich בדיוק מעל הבלוק הנוכחי |
| **`canSurvive`**| בודק אם הבלוק יכול לשרוד במיקום הנוכחי |

---

<div dir="ltr">

⬅️ [שלב 20](Step-20-Crop-Block) · ➡️ [שלב 22](Step-22-Flowers)

</div>

</div>

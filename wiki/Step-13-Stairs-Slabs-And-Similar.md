<div dir="rtl">

# 🔧 שלב 13 — מדרגות, ספסלים ודומיהם (Stairs, Slabs and similar)

בשלב הקודם בנינו פריטים מותאמים וחמישה בלוקים בסיסיים. כעת אנו מרחיבים את משפחת הבלוקים של **Sapphire** — מוסיפים מדרגות, ספסלים, כפתורים, לוחות לחץ, גדרות, שערים, קירות, דלתות ומשקופים.

> 💡 קבצי ה-`blockstates`, ה-`models` וה-`loot_tables` הרבים שיוצרו כאן מיוצרים אוטומטית על ידי Gradle Data Generation.

---

## מה השתנה לעומת השלב הקודם

| קבצים חדשים/ששונו | תיאור |
|---|---|
| `ModBlocks.java` | 9 בלוקי ספירה חדשים |
| `ModBlockStateProvider.java` | generate למדרגות, ספסלים, כפתורים וכו' |
| `ModItemModelProvider.java` | 6 מתודות עזר חדשות למודלי פריטים |
| `ModBlockTagGenerator.java` | תגיות FENCES, FENCE_GATES, WALLS |
| `ModBlockLootTables.java` | loot tables לכל הבלוקים החדשים |
| `ModCreativeModTabs.java` | הוספת כל הבלוקים לטאב הקריאייטיב |
| `en_us.json` | שמות לכל הפריטים והבלוקים החדשים |
| `assets/.../textures/block/` | 5 קבצי תמונות חדשים |

---

## קוד חדש ב-`ModBlocks.java`

<div dir="ltr">

```java
public static final RegistryObject<Block> SAPPHIRE_STAIRS = registerBlock("sapphire_stairs",
        () -> new StairBlock(() -> ModBlocks.SAPPHIRE_BLOCK.get().defaultBlockState(),
                BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).sound(SoundType.AMETHYST)));
public static final RegistryObject<Block> SAPPHIRE_SLAB = registerBlock("sapphire_slab",
        () -> new SlabBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).sound(SoundType.AMETHYST)));

public static final RegistryObject<Block> SAPPHIRE_BUTTON = registerBlock("sapphire_button",
        () -> new ButtonBlock(BlockBehaviour.Properties.copy(Blocks.STONE_BUTTON).sound(SoundType.AMETHYST),
                BlockSetType.IRON, 10, true));
public static final RegistryObject<Block> SAPPHIRE_PRESSURE_PLATE = registerBlock("sapphire_pressure_plate",
        () -> new PressurePlateBlock(PressurePlateBlock.Sensitivity.EVERYTHING,
                BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).sound(SoundType.AMETHYST),
                BlockSetType.IRON));

public static final RegistryObject<Block> SAPPHIRE_FENCE = registerBlock("sapphire_fence",
        () -> new FenceBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).sound(SoundType.AMETHYST)));
public static final RegistryObject<Block> SAPPHIRE_FENCE_GATE = registerBlock("sapphire_fence_gate",
        () -> new FenceGateBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).sound(SoundType.AMETHYST),
                SoundEvents.CHAIN_PLACE, SoundEvents.ANVIL_BREAK));
public static final RegistryObject<Block> SAPPHIRE_WALL = registerBlock("sapphire_wall",
        () -> new WallBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).sound(SoundType.AMETHYST)));

public static final RegistryObject<Block> SAPPHIRE_DOOR = registerBlock("sapphire_door",
        () -> new DoorBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).sound(SoundType.AMETHYST)
                .noOcclusion(), BlockSetType.IRON));
public static final RegistryObject<Block> SAPPHIRE_TRAPDOOR = registerBlock("sapphire_trapdoor",
        () -> new TrapDoorBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).sound(SoundType.AMETHYST)
                .noOcclusion(), BlockSetType.IRON));
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `StairBlock(() -> ModBlocks.SAPPHIRE_BLOCK.get().defaultBlockState(), ...)` | המדרגות משתמשות במצב ברירת המחדל של `SAPPHIRE_BLOCK` כבסיס |
| `BlockSetType.IRON` | קובע סאונדים ובדיקות תקינות בסגנון ברזל לכפתור/דלת/משקוף |
| `PressurePlateBlock.Sensitivity.EVERYTHING` | לוחץ לחץ שיורע על ידי כל דבר, לא רק שחקנים/יצורים |
| `FenceGateBlock(..., SoundEvents.CHAIN_PLACE, SoundEvents.ANVIL_BREAK)` | סאונדים לפתיחה וסגירה של השער |
| `DoorBlock(... .noOcclusion(), BlockSetType.IRON)` | דלת צריכה `noOcclusion()` כדי שהמשחק לא יכסה את המשקוף |
| `TrapDoorBlock(... .noOcclusion(), BlockSetType.IRON)` | אותו דבר למשקוף נפתח |

---

## קוד חדש ב-`ModBlockStateProvider.java`

<div dir="ltr">

```java
@Override
protected void registerStatesAndModels() {
    blockWithItem(ModBlocks.SOUND_BLOCK);

    stairsBlock(((StairBlock) ModBlocks.SAPPHIRE_STAIRS.get()), blockTexture(ModBlocks.SAPPHIRE_BLOCK.get()));
    slabBlock(((SlabBlock) ModBlocks.SAPPHIRE_SLAB.get()), blockTexture(ModBlocks.SAPPHIRE_BLOCK.get()), blockTexture(ModBlocks.SAPPHIRE_BLOCK.get()));

    buttonBlock(((ButtonBlock) ModBlocks.SAPPHIRE_BUTTON.get()), blockTexture(ModBlocks.SAPPHIRE_BLOCK.get()));
    pressurePlateBlock(((PressurePlateBlock) ModBlocks.SAPPHIRE_PRESSURE_PLATE.get()), blockTexture(ModBlocks.SAPPHIRE_BLOCK.get()));

    fenceBlock(((FenceBlock) ModBlocks.SAPPHIRE_FENCE.get()), blockTexture(ModBlocks.SAPPHIRE_BLOCK.get()));
    fenceGateBlock(((FenceGateBlock) ModBlocks.SAPPHIRE_FENCE_GATE.get()), blockTexture(ModBlocks.SAPPHIRE_BLOCK.get()));
    wallBlock(((WallBlock) ModBlocks.SAPPHIRE_WALL.get()), blockTexture(ModBlocks.SAPPHIRE_BLOCK.get()));

    doorBlockWithRenderType(((DoorBlock) ModBlocks.SAPPHIRE_DOOR.get()), modLoc("block/sapphire_door_bottom"), modLoc("block/sapphire_door_top"), "cutout");
    trapdoorBlockWithRenderType(((TrapDoorBlock) ModBlocks.SAPPHIRE_TRAPDOOR.get()), modLoc("block/sapphire_trapdoor"), true, "cutout");
}
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `stairsBlock(...)` | יוצר blockstate + 3 models (פנימי, חיצוני, פינה) |
| `slabBlock(...)` | יוצר blockstate + 2 models (עליוני, תחתון) |
| `buttonBlock(...)` | יוצר model לכפתור בפרספקטיבה של ה-inventory |
| `pressurePlateBlock(...)` | יוצר model ללוח לחץ |
| `fenceBlock(...)` / `fenceGateBlock(...)` | יוצרים fence + gate עם textures של post ו-side |
| `wallBlock(...)` | יוצר blockstate + models לקיר (post, side, side_tall) |
| `doorBlockWithRenderType(..., "cutout")` | דלת משתמשת ב-`cutout` render type כדי שעבורי הזכוכית יראו |
| `trapdoorBlockWithRenderType(..., true, "cutout")` | משקוף נפתח עם `cutout` ופתיחה נגד כיוון השעון |

---

## קוד חדש ב-`ModItemModelProvider.java`

<div dir="ltr">

```java
@Override
protected void registerModels() {
    // ... קוד קודם ...

    simpleBlockItem(ModBlocks.SAPPHIRE_DOOR);

    fenceItem(ModBlocks.SAPPHIRE_FENCE, ModBlocks.SAPPHIRE_BLOCK);
    buttonItem(ModBlocks.SAPPHIRE_BUTTON, ModBlocks.SAPPHIRE_BLOCK);
    wallItem(ModBlocks.SAPPHIRE_WALL, ModBlocks.SAPPHIRE_BLOCK);

    evenSimplerBlockItem(ModBlocks.SAPPHIRE_STAIRS);
    evenSimplerBlockItem(ModBlocks.SAPPHIRE_SLAB);
    evenSimplerBlockItem(ModBlocks.SAPPHIRE_PRESSURE_PLATE);
    evenSimplerBlockItem(ModBlocks.SAPPHIRE_FENCE_GATE);

    trapdoorItem(ModBlocks.SAPPHIRE_TRAPDOOR);
}

public void evenSimplerBlockItem(RegistryObject<Block> block) {
    this.withExistingParent(TutorialMod.MOD_ID + ":" + ForgeRegistries.BLOCKS.getKey(block.get()).getPath(),
            modLoc("block/" + ForgeRegistries.BLOCKS.getKey(block.get()).getPath()));
}

public void trapdoorItem(RegistryObject<Block> block) {
    this.withExistingParent(ForgeRegistries.BLOCKS.getKey(block.get()).getPath(),
            modLoc("block/" + ForgeRegistries.BLOCKS.getKey(block.get()).getPath() + "_bottom"));
}

public void fenceItem(RegistryObject<Block> block, RegistryObject<Block> baseBlock) {
    this.withExistingParent(ForgeRegistries.BLOCKS.getKey(block.get()).getPath(), mcLoc("block/fence_inventory"))
            .texture("texture",  new ResourceLocation(TutorialMod.MOD_ID, "block/" + ForgeRegistries.BLOCKS.getKey(baseBlock.get()).getPath()));
}

public void buttonItem(RegistryObject<Block> block, RegistryObject<Block> baseBlock) {
    this.withExistingParent(ForgeRegistries.BLOCKS.getKey(block.get()).getPath(), mcLoc("block/button_inventory"))
            .texture("texture",  new ResourceLocation(TutorialMod.MOD_ID, "block/" + ForgeRegistries.BLOCKS.getKey(baseBlock.get()).getPath()));
}

public void wallItem(RegistryObject<Block> block, RegistryObject<Block> baseBlock) {
    this.withExistingParent(ForgeRegistries.BLOCKS.getKey(block.get()).getPath(), mcLoc("block/wall_inventory"))
            .texture("wall",  new ResourceLocation(TutorialMod.MOD_ID, "block/" + ForgeRegistries.BLOCKS.getKey(baseBlock.get()).getPath()));
}

private ItemModelBuilder simpleBlockItem(RegistryObject<Block> item) {
    return withExistingParent(item.getId().getPath(),
            new ResourceLocation("item/generated")).texture("layer0",
            new ResourceLocation(TutorialMod.MOD_ID,"item/" + item.getId().getPath()));
}
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `evenSimplerBlockItem(...)` | משתמש ב-`withExistingParent` שמקשר למודל ה-block הקיים — למדרגות וספסלים |
| `trapdoorItem(...)` | משתמש במודל `_bottom` של המשקוף לפריט ה-inventory |
| `fenceItem(...)` | משתמש במוטיב `block/fence_inventory` של Minecraft עם הטקסטורה של הספירה |
| `buttonItem(...)` | משתמש במוטיב `block/button_inventory` עם הטקסטורה של הספירה |
| `wallItem(...)` | משתמש במוטיב `block/wall_inventory` עם הטקסטורה של הספירה |

---

## קוד חדש ב-`ModBlockTagGenerator.java`

<div dir="ltr">

```java
this.tag(BlockTags.FENCES)
        .add(ModBlocks.SAPPHIRE_FENCE.get());
this.tag(BlockTags.FENCE_GATES)
        .add(ModBlocks.SAPPHIRE_FENCE_GATE.get());
this.tag(BlockTags.WALLS)
        .add(ModBlocks.SAPPHIRE_WALL.get());
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `BlockTags.FENCES` | מאפשר לפריטי נגנית ברזל להצטרף לגדרת הספירה |
| `BlockTags.FENCE_GATES` | מאפשר לשער הספירה להירשם כ-tag תקין |
| `BlockTags.WALLS` | מאפשר לקיר הספירה להירשם כחלק מקירות |

---

## קוד חדש ב-`ModBlockLootTables.java`

<div dir="ltr">

```java
this.dropSelf(ModBlocks.SAPPHIRE_STAIRS.get());
this.dropSelf(ModBlocks.SAPPHIRE_BUTTON.get());
this.dropSelf(ModBlocks.SAPPHIRE_PRESSURE_PLATE.get());
this.dropSelf(ModBlocks.SAPPHIRE_TRAPDOOR.get());
this.dropSelf(ModBlocks.SAPPHIRE_FENCE.get());
this.dropSelf(ModBlocks.SAPPHIRE_FENCE_GATE.get());
this.dropSelf(ModBlocks.SAPPHIRE_WALL.get());

this.add(ModBlocks.SAPPHIRE_SLAB.get(),
        block -> createSlabItemTable(ModBlocks.SAPPHIRE_SLAB.get()));
this.add(ModBlocks.SAPPHIRE_DOOR.get(),
        block -> createDoorTable(ModBlocks.SAPPHIRE_DOOR.get()));
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `dropSelf(...)` | הבלוק נופל כעצמו כאשר נשבר (ללא עיגול נוסף) |
| `createSlabItemTable(...)` | ספסל נותן 1 פריט ספסל כאשר נשבר (במקום 2) |
| `createDoorTable(...)` | דלת מייצרת 2 פריטי דלת כאשר נשברת |

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| **`StairBlock`** | בלוק מדרגות — יוצר אוטומטית 3 צורות (פנימי, חיצוני, פינה) |
| **`SlabBlock`** | ספסל — חצי בלוק, ניתן למקם בחצי גובה |
| **`BlockSetType`** | קובע סאונדים ודפוסי התנהגות לכפתורים/דלתות/משקופים |
| **`noOcclusion()`** | מונע מהבלוק לכסות Cubes סמוכים — נדרש לדלתות ומשקופים |
| **`BlockTags.FENCES`** | תגית שמורה שמאגדת גדרות — נחוצה לתקשורת עם פריטי נגנית |

---

<div dir="ltr">

⬅️ [שלב 12](Step-12-Data-Generation) · ➡️ [שלב 14](Step-14-2D-Textures-3D-Model)

</div>

</div>

<div dir="rtl">

# 🔧 שלב 12 — יצירת נתונים אוטומטית (Data Generation)

בשלב הזה אנו משליכים את **כל קבצי ה-JSON הידניים** שבנינו עד כה
ומחליפים אותם ב-**Java Data Generators**! 🇬🇧

במקום לכתוב `recipes/sapphire_block_from_sapphire.json` ידנית,
אנחנו כותבים קוד Java שיוצר את הקובץ אוטומטית כאשר מריצים
`./gradlew runData`. זהו הסטנדרט המודרני לפיתוח מודי Forge.

---

## מה השתנה לעומת השלב הקודם

**קבצים שהועברו אוטומטית ל-`src/generated/resources/`:**
כל קבצי ה-models, blockstates, loot tables, recipes, ו-tags שהיו ב-`src/main/resources/`
עברו אוטומטית ל-`src/generated/resources/` על ידי ה-data generator.

**קבצי Java חדשים (Data Generators):**

| קובץ | תיאור |
|---|---|
| `src/main/java/.../datagen/DataGenerators.java` | נקודת הכניסה ל-data generation |
| `src/main/java/.../datagen/ModBlockStateProvider.java` | יוצר blockstates ו-models |
| `src/main/java/.../datagen/ModItemModelProvider.java` | יוצר item models |
| `src/main/java/.../datagen/ModBlockTagGenerator.java` | יוצר block tags |
| `src/main/java/.../datagen/ModItemTagGenerator.java` | יוצר item tags |
| `src/main/java/.../datagen/ModLootTableProvider.java` | יוצר loot tables |
| `src/main/java/.../datagen/ModRecipeProvider.java` | יוצר מתכונים |
| `src/main/java/.../datagen/loot/ModBlockLootTables.java` | לוגיקת loot tables לבלוקים |

**קבצים שנמחקו מ-`src/main/resources/`:**
קבצי tags ריקים (`mineable/axe.json`, `mineable/hoe.json`, `mineable/shovel.json`)
וקבצי recipes/loot tables ידניים הוסרו — DataGenerator יוצר אותם עכשיו.

---

## מחלקת DataGenerators.java

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.datagen;

import net.bullettrain.tutorialmod.TutorialMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.concurrent.CompletableFuture;

@Mod.EventBusSubscriber(modid = TutorialMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DataGenerators {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        generator.addProvider(event.includeServer(), new ModRecipeProvider(packOutput));
        generator.addProvider(event.includeServer(), ModLootTableProvider.create(packOutput));

        generator.addProvider(event.includeClient(), new ModBlockStateProvider(packOutput, existingFileHelper));
        generator.addProvider(event.includeClient(), new ModItemModelProvider(packOutput, existingFileHelper));

        ModBlockTagGenerator blockTagGenerator = generator.addProvider(event.includeServer(),
                new ModBlockTagGenerator(packOutput, lookupProvider, existingFileHelper));
        generator.addProvider(event.includeServer(), new ModItemTagGenerator(packOutput, lookupProvider, blockTagGenerator.contentsGetter(), existingFileHelper));
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `@Mod.EventBusSubscriber(modid = ..., bus = Bus.MOD)`| רושם אוטומטית את כל המתודות הסטטיות המודעות לאירועי GatherDataEvent |
| `public static void gatherData(GatherDataEvent event)` | נקודת הכניסה ל-data generation |
| `DataGenerator generator = event.getGenerator()` | האובייקט שיוצר את כל קבצי הנתונים |
| `PackOutput packOutput = generator.getPackOutput()`| פלט הנתונים — היכן הקבצים ייו&#8206;צרו |
| `generator.addProvider(event.includeServer(), ...)` | מוסיף provider לצד השרת (recipes, loot, tags) |
| `generator.addProvider(event.includeClient(), ...)` | מוסיף provider לצד הקליינט (models, blockstates) |
| `new ModRecipeProvider(packOutput)` | provider שיוצר מתכונים |
| `ModLootTableProvider.create(packOutput)` | provider שיוצר loot tables |
| `new ModBlockStateProvider(packOutput, existingFileHelper)` | provider שיוצר blockstates |
| `new ModItemModelProvider(packOutput, existingFileHelper)` | provider שיוצר item models |

---

## מחלקת ModBlockStateProvider.java

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.datagen;

import net.bullettrain.tutorialmod.TutorialMod;
import net.bullettrain.tutorialmod.block.ModBlocks;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockStateProvider extends BlockStateProvider {
    public ModBlockStateProvider(PackOutput output, ExistingFileHelper exFileHelper) {
        super(output, TutorialMod.MOD_ID, exFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        blockWithItem(ModBlocks.SAPPHIRE_BLOCK);
        blockWithItem(ModBlocks.RAW_SAPPHIRE_BLOCK);
        blockWithItem(ModBlocks.SAPPHIRE_ORE);
        blockWithItem(ModBlocks.DEEPSLATE_SAPPHIRE_ORE);
        blockWithItem(ModBlocks.END_STONE_SAPPHIRE_ORE);
        blockWithItem(ModBlocks.NETHER_SAPPHIRE_ORE);
        blockWithItem(ModBlocks.SOUND_BLOCK);
    }

    private void blockWithItem(RegistryObject<Block> blockRegistryObject) {
        simpleBlockWithItem(blockRegistryObject.get(), cubeAll(blockRegistryObject.get()));
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `extends BlockStateProvider` | יורש ממחלקה שיודעת ליצור blockstates ו-models |
| `registerStatesAndModels()` | מתודה שקורית ליצירת כל ה-blockstates |
| `blockWithItem(ModBlocks.SAPPHIRE_BLOCK)` | יוצר גם blockstate וגם item model לבלוק |
| `simpleBlockWithItem(...)` | שיטה מוגדרת מראש ליצירת blockstate פשוט |
| `cubeAll(blockRegistryObject.get())` | יוצר model של קובייה עם אותה טקסטורה על כל הפאות |

---

## מחלקת ModItemModelProvider.java

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.datagen;

import net.bullettrain.tutorialmod.TutorialMod;
import net.bullettrain.tutorialmod.item.ModsItems;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.RegistryObject;

public class ModItemModelProvider extends ItemModelProvider {
    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, TutorialMod.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        simpleItem(ModsItems.SAPPHIRE);
        simpleItem(ModsItems.RAW_SAPPHIRE);
        simpleItem(ModsItems.METAL_DETECTOR);
        simpleItem(ModsItems.PINE_CONE);
        simpleItem(ModsItems.STRAWBERRY);
    }

    private ItemModelBuilder simpleItem(RegistryObject<Item> item) {
        return withExistingParent(item.getId().getPath(),
                new ResourceLocation("item/generated")).texture("layer0",
                new ResourceLocation(TutorialMod.MOD_ID,"item/" + item.getId().getPath()));
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `extends ItemModelProvider` | יורש ממחלקה שיודעת ליצור item models |
| `registerModels()` | מתודה שקורית ליצירת כל ה-item models |
| `simpleItem(ModsItems.SAPPHIRE)` | יוצר model פשוט לפריט |
| `withExistingParent(item.getId().getPath(), new ResourceLocation("item/generated"))` | משתמש במודל `item/generated` כבסיס |
| `.texture("layer0", ...)` | קובע את הטקסטורה ל-layer0 |

---

## מחלקת ModRecipeProvider.java

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.datagen;

import net.bullettrain.tutorialmod.TutorialMod;
import net.bullettrain.tutorialmod.block.ModBlocks;
import net.bullettrain.tutorialmod.item.ModsItems;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.common.crafting.conditions.IConditionBuilder;
import java.util.List;
import java.util.function.Consumer;

public class ModRecipeProvider extends RecipeProvider implements IConditionBuilder {
    private static final List<ItemLike> SAPPHIRE_SMELTABLES = List.of(
            ModsItems.RAW_SAPPHIRE.get(),
            ModBlocks.SAPPHIRE_ORE.get(),
            ModBlocks.DEEPSLATE_SAPPHIRE_ORE.get(),
            ModBlocks.NETHER_SAPPHIRE_ORE.get(),
            ModBlocks.END_STONE_SAPPHIRE_ORE.get());

    public ModRecipeProvider(PackOutput pOutput) {
        super(pOutput);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> pWriter) {
        oreSmelting(pWriter, SAPPHIRE_SMELTABLES, RecipeCategory.MISC, ModsItems.SAPPHIRE.get(), 0.25f, 200, "sapphire");
        oreBlasting(pWriter, SAPPHIRE_SMELTABLES, RecipeCategory.MISC, ModsItems.SAPPHIRE.get(), 0.25f, 100, "sapphire");

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.SAPPHIRE_BLOCK.get())
                .pattern("SSS")
                .pattern("SSS")
                .pattern("SSS")
                .define('S', ModsItems.SAPPHIRE.get())
                .unlockedBy(getHasName(ModsItems.SAPPHIRE.get()), has(ModsItems.SAPPHIRE.get()))
                .save(pWriter);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModsItems.SAPPHIRE.get(), 9)
                .requires(ModBlocks.SAPPHIRE_BLOCK.get())
                .unlockedBy(getHasName(ModBlocks.SAPPHIRE_BLOCK.get()), has(ModBlocks.SAPPHIRE_BLOCK.get()))
                .save(pWriter);
    }

    protected static void oreSmelting(Consumer<FinishedRecipe> pFinishedRecipeConsumer, List<ItemLike> pIngredients,
                                      RecipeCategory pCategory, ItemLike pResult, float pExperience,
                                      int pCookingTIme, String pGroup) {
        oreCooking(pFinishedRecipeConsumer, RecipeSerializer.SMELTING_RECIPE, pIngredients, pCategory, pResult, pExperience, pCookingTIme, pGroup, "_from_smelting");
    }

    protected static void oreBlasting(Consumer<FinishedRecipe> pFinishedRecipeConsumer, List<ItemLike> pIngredients,
                                      RecipeCategory pCategory, ItemLike pResult, float pExperience,
                                      int pCookingTime, String pGroup) {
        oreCooking(pFinishedRecipeConsumer, RecipeSerializer.BLASTING_RECIPE, pIngredients, pCategory, pResult, pExperience, pCookingTime, pGroup, "_from_blasting");
    }

    protected static void oreCooking(Consumer<FinishedRecipe> pFinishedRecipeConsumer, RecipeSerializer<? extends AbstractCookingRecipe> pCookingSerializer,
                                     List<ItemLike> pIngredients, RecipeCategory pCategory, ItemLike pResult, float pExperience,
                                     int pCookingTime, String pGroup, String pRecipeName) {
        for(ItemLike itemlike : pIngredients) {
            SimpleCookingRecipeBuilder.generic(Ingredient.of(itemlike), pCategory, pResult,
                    pExperience, pCookingTime, pCookingSerializer)
                    .group(pGroup).unlockedBy(getHasName(itemlike), has(itemlike))
                    .save(pFinishedRecipeConsumer, TutorialMod.MOD_ID + ":" + getItemName(pResult) + pRecipeName + "_" + getItemName(itemlike));
        }
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `private static final List<ItemLike> SAPPHIRE_SMELTABLES` | רשימת כל הפריטים הניתנים למַסֶּיר |
| `oreSmelting(...)` | יוצר מתכוני smelting עבור כל פריט ברשימה |
| `oreBlasting(...)` | יוצר מתכוני blasting עבור כל פריט ברשימה |
| `ShapedRecipeBuilder.shaped(...)` | בונה מתכן shapedrogrammatically |
| `.pattern("SSS")` | שורות המתכון |
| `.define('S', ModsItems.SAPPHIRE.get())` | מגדיר את הסמל `S` כפריט Sapphire |
| `.unlockedBy(getHasName(...), has(...))` | קובע תנאי לפתיחת המתכון (יש לפריט) |
| `ShapelessRecipeBuilder.shapeless(...)` | בונה מתכן shapelessrogrammatically |
| `oreCooking(...)` | מתודת עזר שיוצרת כל המתכונים |

---

## מחלקת ModLootTableProvider.java

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.datagen;

import net.bullettrain.tutorialmod.datagen.loot.ModBlockLootTables;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import java.util.List;
import java.util.Set;

public class ModLootTableProvider {
    public static LootTableProvider create(PackOutput output) {
        return new LootTableProvider(output, Set.of(), List.of(
                new LootTableProvider.SubProviderEntry(ModBlockLootTables::new, LootContextParamSets.BLOCK)
        ));
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `LootTableProvider` | הספק שיוצר את כל טבלאות השלל |
| `Set.of()` | רשימת חריגים (ריקה כרגע) |
| `List.of(new LootTableProvider.SubProviderEntry(...))` | רשימת providers |
| `ModBlockLootTables::new` | הפניה לבנאי של המחלקה שיוצרת loot tables |
| `LootContextParamSets.BLOCK` | הקונטקסט הוא שבירת בלוקים |

---

## מחלקת ModBlockLootTables.java

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.datagen.loot;

import net.bullettrain.tutorialmod.block.ModBlocks;
import net.bullettrain.tutorialmod.item.ModsItems;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraftforge.registries.RegistryObject;
import java.util.Set;

public class ModBlockLootTables extends BlockLootSubProvider {
    public ModBlockLootTables() {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags());
    }

    @Override
    protected void generate() {
        this.dropSelf(ModBlocks.SAPPHIRE_BLOCK.get());
        this.dropSelf(ModBlocks.RAW_SAPPHIRE_BLOCK.get());
        this.dropSelf(ModBlocks.SOUND_BLOCK.get());

        this.add(ModBlocks.SAPPHIRE_ORE.get(),
                block -> createCopperLikeOreDrops(ModBlocks.SAPPHIRE_ORE.get(), ModsItems.RAW_SAPPHIRE.get()));
        this.add(ModBlocks.DEEPSLATE_SAPPHIRE_ORE.get(),
                block -> createCopperLikeOreDrops(ModBlocks.DEEPSLATE_SAPPHIRE_ORE.get(), ModsItems.RAW_SAPPHIRE.get()));
        this.add(ModBlocks.NETHER_SAPPHIRE_ORE.get(),
                block -> createCopperLikeOreDrops(ModBlocks.NETHER_SAPPHIRE_ORE.get(), ModsItems.RAW_SAPPHIRE.get()));
        this.add(ModBlocks.END_STONE_SAPPHIRE_ORE.get(),
                block -> createCopperLikeOreDrops(ModBlocks.END_STONE_SAPPHIRE_ORE.get(), ModsItems.RAW_SAPPHIRE.get()));
    }

    protected LootTable.Builder createCopperLikeOreDrops(Block pBlock, Item item) {
        return createSilkTouchDispatchTable(pBlock,
                this.applyExplosionDecay(pBlock,
                        LootItem.lootTableItem(item)
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(2.0F, 5.0F)))
                                .apply(ApplyBonusCount.addOreBonusCount(Enchantments.BLOCK_FORTUNE))));
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries().stream().map(RegistryObject::get)::iterator;
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `extends BlockLootSubProvider` | יורש ממחלקה שיודעת ליצור loot tables לבלוקים |
| `this.dropSelf(ModBlocks.SAPPHIRE_BLOCK.get())` | מוסיף loot table שמפיל את הבלוק עצמו |
| `this.add(ModBlocks.SAPPHIRE_ORE.get(), block -> ...)` | מוסיף loot table מותאמת לעופרת |
| `createSilkTouchDispatchTable(pBlock, ...)` | יוצר טבלה שבה Silk Touch מפיל את הבלוק עצמו |
| `applyExplosionDecay(pBlock, ...)`| מחשב decay אם הבלוק נהרס במופעי פיצוץ |
| `LootItem.lootTableItem(item)` | פריט שנופל מהשלל |
| `SetItemCountFunction.setCount(UniformGenerator.between(2.0F, 5.0F))` | כמות אקראית בין 2 ל-5 |
| `ApplyBonusCount.addOreBonusCount(Enchantments.BLOCK_FORTUNE)`| מחשב בונוס לפי עופרת |
| `getKnownBlocks()` | מחזיר את כל הבלוקים של המוד |

---

## מחלקת ModBlockTagGenerator.java

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.datagen;

import net.bullettrain.tutorialmod.TutorialMod;
import net.bullettrain.tutorialmod.block.ModBlocks;
import net.bullettrain.tutorialmod.util.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;
import java.util.concurrent.CompletableFuture;

public class ModBlockTagGenerator extends BlockTagsProvider {
    public ModBlockTagGenerator(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, TutorialMod.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider pProvider) {
        this.tag(ModTags.Blocks.METAL_DETECTOR_VALUABLES)
                .add(ModBlocks.SAPPHIRE_ORE.get()).addTag(Tags.Blocks.ORES);

        this.tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(ModBlocks.SAPPHIRE_BLOCK.get(),
                        ModBlocks.RAW_SAPPHIRE_BLOCK.get(),
                        ModBlocks.SAPPHIRE_ORE.get(),
                        ModBlocks.DEEPSLATE_SAPPHIRE_ORE.get(),
                        ModBlocks.NETHER_SAPPHIRE_ORE.get(),
                        ModBlocks.END_STONE_SAPPHIRE_ORE.get(),
                        ModBlocks.SOUND_BLOCK.get());

        this.tag(BlockTags.NEEDS_IRON_TOOL)
                .add(ModBlocks.SAPPHIRE_BLOCK.get());

        this.tag(BlockTags.NEEDS_DIAMOND_TOOL)
                .add(ModBlocks.RAW_SAPPHIRE_BLOCK.get());

        this.tag(BlockTags.NEEDS_STONE_TOOL)
                .add(ModBlocks.NETHER_SAPPHIRE_ORE.get());

        this.tag(Tags.Blocks.NEEDS_NETHERITE_TOOL)
                .add(ModBlocks.END_STONE_SAPPHIRE_ORE.get());
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `extends BlockTagsProvider` | יורש ממחלקה שיודעת ליצור block tags |
| `this.tag(ModTags.Blocks.METAL_DETECTOR_VALUABLES)` | מתחיל להגדיר tag מותאם |
| `.add(ModBlocks.SAPPHIRE_ORE.get())` | מוסיף בלוק ל-tag |
| `.addTag(Tags.Blocks.ORES)` | מוסיף את כל ה-ores של Minecraft |
| `this.tag(BlockTags.MINEABLE_WITH_PICKAXE)` | הגדרת tag רשמי — ניתן לשבור ב-pickaxe |
| `this.tag(BlockTags.NEEDS_IRON_TOOL)` | דורש כלי ברזל |
| `this.tag(BlockTags.NEEDS_DIAMOND_TOOL)` | דורש כלי יהלום |
| `this.tag(BlockTags.NEEDS_STONE_TOOL)` | דורש כלי אבן |
| `this.tag(Tags.Blocks.NEEDS_NETHERITE_TOOL)` | דורש כלי Netherite |

---

## הפעלת Data Generator

כדי לייצר את כל הקבצים, מריצים:

<div dir="ltr">

```bash
./gradlew runData
```

</div>
פקודה זו מריצה את כל ה-providers שיצרנו, והקבצים נוצרים בתיקייה
`src/generated/resources/`. לאחר מכן ניתן להעתיק אותם ל-`src/main/resources/`
או להשאיר אותם ב-generated (כפי ש-MDK ממליץ).

> 💡 הקבצים שנוצרים אוטומטית כוללים: מתכונים, loot tables,
> blockstates, item models, tags, ו-advancements.

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| **Data Generator** | מערכת שיוצרת קבצי JSON אוטומטית מקוד Java |
| **GatherDataEvent** | אירוע שמפעיל את כל ה-data generators |
| **PackOutput** | ממשק לכתיבה של קבצי משאבים |
| **BlockStateProvider** | provider שיוצר blockstates ו-models |
| **ItemModelProvider** | provider שיוצר item models |
| **BlockTagsProvider** | provider שיוצר block tags |
| **RecipeProvider** | provider שיוצר מתכונים |
| **BlockLootSubProvider** | provider שיוצר loot tables לבלוקים |

---

<div dir="ltr">

⬅️ [שלב 11](Step-11-Tags) · ➡️ [שלב 13](Step-13-Stairs-Slabs-And-Similar)

</div>

</div>

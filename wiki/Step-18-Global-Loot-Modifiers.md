<div dir="rtl">

# 🔧 שלב 18 — שינויי שלל גלובליים (Global Loot Modifiers)

בשלבים קודמים יצרנו loot tables מותאמות לבלוקים שלנו. כעת אנו נלמד לשנות **שלל (loot) של
משחק עצמו** בלי לידול קבצי Minecraft — באמצעות **Global Loot Modifiers**.

אנו מוסיפים:
- **Pine Cone** מ dropping מהדשא (35% סיכוי)
- **Pine Cone** מ-Creeper
- **Metal Detector** מ-Jungle Temple chests

> 💡 Global Loot Modifiers מאפשרים לשנות loot tables של Minecraft ו-loot שלנו ללא ע^kubing ב-`data/` של Minecraft.
> Gradle יוצר את הקבצים ב-`src/generated/resources/` עבורנו.

---

## מה השתנה לעומת השלב הקודם

| קבצים חדשים/ששונו | תיאור |
|---|---|
| `loot/AddItemModifier.java` | מחלקת loot modifier חדשה |
| `loot/ModLootModifiers.java` | רישום ה-modifier |
| `datagen/ModGlobalLootModifiersProvider.java` | provider שמגדיר 3 מקרים |
| `datagen/DataGenerators.java` | רישום ה-provider החדש |
| `TutorialMod.java` | רישום `ModLootModifiers` |
| `gradle.properties` | עדכון Forge ל-47.1.3 |
| `generated/data/.../loot_modifiers/` | 3 קבצי JSON נוצרו אוטומטית |

---

## קוד חדש ב-`loot/AddItemModifier.java`

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.loot;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class AddItemModifier extends LootModifier {
    public static final Supplier<Codec<AddItemModifier>> CODEC = Suppliers.memoize(()
            -> RecordCodecBuilder.create(inst -> codecStart(inst).and(ForgeRegistries.ITEMS.getCodec()
            .fieldOf("item").forGetter(m -> m.item)).apply(inst, AddItemModifier::new)));
    private final Item item;

    public AddItemModifier(LootItemCondition[] conditionsIn, Item item) {
        super(conditionsIn);
        this.item = item;
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        for(LootItemCondition condition : this.conditions) {
            if(!condition.test(context)) {
                return generatedLoot;
            }
        }

        generatedLoot.add(new ItemStack(this.item));

        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `extends LootModifier` | מ-inherits את מחלקת הבסיס של Forge ל-loot modifications |
| `Suppliers.memoize(() -> RecordCodecBuilder.create(...))` | יוצר Codec סגול (lazy) ש-Minecraft יודע איך לקרוא ולכתוב |
| `ForgeRegistries.ITEMS.getCodec().fieldOf("item")` | קורא מהקובץ את שדה ה-`item` כמופע Item |
| `codecStart(inst)` | מכניס את `conditions` מהקובץ (רשימה של תנאים) |
| `doApply(...)` | הפונקציה שמפעילה את השינוי — בודק תנאים ומוסיף פריט |
| `generatedLoot.add(new ItemStack(this.item))` | מוסיף פריט לרשימת השלל |

---

## קוד חדש ב-`loot/ModLootModifiers.java`

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.loot;

import com.mojang.serialization.Codec;
import net.bullettrain.tutorialmod.TutorialMod;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModLootModifiers {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIER_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, TutorialMod.MOD_ID);

    public static final RegistryObject<Codec<? extends IGlobalLootModifier>> ADD_ITEM =
            LOOT_MODIFIER_SERIALIZERS.register("add_item", AddItemModifier.CODEC);

    public static void register(IEventBus eventBus) {
        LOOT_MODIFIER_SERIALIZERS.register(eventBus);
    }
}
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, ...)`| רושם סוג של Codecs שמגדירים איך לקרוא loot modifiers |
| `register("add_item", AddItemModifier.CODEC)` | רושם את ה-CODEC של `AddItemModifier` תחת השם `add_item` |
| `LOOT_MODIFIER_SERIALIZERS.register(eventBus)`| מעביר את הרישום לאירוע bus של Forge |

---

## קוד חדש ב-`datagen/ModGlobalLootModifiersProvider.java`

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.datagen;

import net.bullettrain.tutorialmod.TutorialMod;
import net.bullettrain.tutorialmod.item.ModsItems;
import net.bullettrain.tutorialmod.loot.AddItemModifier;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraftforge.common.data.GlobalLootModifierProvider;
import net.minecraftforge.common.loot.LootTableIdCondition;

public class ModGlobalLootModifiersProvider extends GlobalLootModifierProvider {
    public ModGlobalLootModifiersProvider(PackOutput output) {
        super(output, TutorialMod.MOD_ID);
    }

    @Override
    protected void start() {
        add("pine_cone_from_grass", new AddItemModifier(new LootItemCondition[] {
                LootItemBlockStatePropertyCondition.hasBlockStateProperties(Blocks.GRASS).build(),
                LootItemRandomChanceCondition.randomChance(0.35f).build()}, ModsItems.PINE_CONE.get()));

        add("pine_cone_from_creeper", new AddItemModifier(new LootItemCondition[] {
                new LootTableIdCondition.Builder(new ResourceLocation("entities/creeper")).build() }, ModsItems.PINE_CONE.get()));

        add("metal_detector_from_jungle_temples", new AddItemModifier(new LootItemCondition[] {
                new LootTableIdCondition.Builder(new ResourceLocation("chests/jungle_temple")).build() }, ModsItems.METAL_DETECTOR.get()));
    }
}
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `extends GlobalLootModifierProvider` | מחלקת Data Generator שיוצרת קבצי loot modifiers |
| `add("pine_cone_from_grass", ...)` | מגדיר שם פנימי + מופע של ה-modifier |
| `LootItemBlockStatePropertyCondition.hasBlockStateProperties(Blocks.GRASS).build()`| תנאי: הבלוק fracture הוא דשא |
| `LootItemRandomChanceCondition.randomChance(0.35f).build()` | תנאי: 35% סיכוי |
| `new LootTableIdCondition.Builder(new ResourceLocation("entities/creeper")).build()` | תנאי: ה-loot table שייך ל-creeper |
| `new ResourceLocation("chests/jungle_temple")` | תנאי: ה-loot table של דיילung temple |

---

## שינוי ב-`TutorialMod.java`

<div dir="ltr">

```java
public TutorialMod() {
    IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

    ModCreativeModTabs.register(modEventBus);

    ModsItems.register(modEventBus);
    ModBlocks.register(modEventBus);

    ModLootModifiers.register(modEventBus);

    modEventBus.addListener(this::commonSetup);
    MinecraftForge.EVENT_BUS.register(this);
    modEventBus.addListener(this::addCreative);
}
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `ModLootModifiers.register(modEventBus)` | רושם את ה-loot modifiers לאירוע bus |

---

## שינוי ב-`datagen/DataGenerators.java`

<div dir="ltr">

```java
this.addProvider(DataGeneratorProvider.TARGET_GENERATION, new ModGlobalLootModifiersProvider(output));
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `ModGlobalLootModifiersProvider` | מוסיף provider שיוצר קבצי loot modifiers ב-build |

---

## קבצים שנוצרו אוטומטית (generated)

| קובץ | תוכן |
|---|---|
| `tutorialmod:pine_cone_from_grass` | מוסיף Pine Cone אם fracture דשא + 35% סיכוי |
| `tutorialmod:pine_cone_from_creeper` | מוסיף Pine Cone מ-Creeper |
| `tutorialmod:metal_detector_from_jungle_temples`| מוסיף Metal Detector מ-Jungle Temple chest |
| `forge:global_loot_modifiers` | רשימת כל ה-modifiers (replace=false) |

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| **`IGlobalLootModifier`** | ממשק שמאפשר לשנות loot tables של Minecraft |
| **`LootTableIdCondition`** | תנאי שבודק את שם ה-loot table |
| **`LootItemRandomChanceCondition`** | תנאי של סיכוי רנדומלי |
| **`Codec`** | קודקוד סיריאליזציה של Minecraft/Mojang — קורא וכותב JSON |
| **`RecordCodecBuilder`** | בונה Codec מדר Christmas |

---

<div dir="ltr">

⬅️ [שלב 17](Step-17-Full-Armor-Effect) · ➡️ [שלב 19](Step-19-Suspicious-Sand-Item)

</div>

</div>

<div dir="rtl">

# 🔧 שלב 19 — פריט חול חשוד (Suspicious Sand Item)

בשלב הקודם יצרנו Global Loot Modifiers למוסיף Pine Cone מ-Dropgrass ו-Creeper ו-Metal Detector
מ-Jungle Temple. כעת אנו מוסיפים **פריט חדש** — **Metal Detector** גם מ-Suspicious Sand
(ארכיאולוגיה) במדבר pyramit.

ההבדל העיקרי מהשלב הקודם: ה-modifier החדש לא רק מוסיף פריט — הוא **מנקה את כל השלל הקיים**
ומחליף אותו בפריט שלנו, עם סיכוי של 50%.

> 💡 שים לב: ה-`AddSusSandItemModifier` משתמשת ב-`generatedLoot.clear()` —
> זהו דוגמה ל-loot modifier שמחליף loot table לגמרי, לא רק מוסיף אליה.

---

## מה השתנה לעומת השלב הקודם

| קבצים חדשים/ששונו | תיאור |
|---|---|
| `loot/AddSusSandItemModifier.java` | loot modifier חדש שמחליף שלל |
| `loot/ModLootModifiers.java` | רישום `ADD_SUS_SAND_ITEM` |
| `datagen/ModGlobalLootModifiersProvider.java` | מקרה חדש למדבר pyramit |
| `generated/data/.../loot_modifiers/` | קבצי JSON נוצרו אוטומטית |

---

## קוד חדש ב-`loot/AddSusSandItemModifier.java`

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

public class AddSusSandItemModifier extends LootModifier {
    public static final Supplier<Codec<AddSusSandItemModifier>> CODEC = Suppliers.memoize(()
            -> RecordCodecBuilder.create(inst -> codecStart(inst).and(ForgeRegistries.ITEMS.getCodec()
            .fieldOf("item").forGetter(m -> m.item)).apply(inst, AddSusSandItemModifier::new)));
    private final Item item;

    public AddSusSandItemModifier(LootItemCondition[] conditionsIn, Item item) {
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

        if(context.getRandom().nextFloat() < 0.5f) { // 50% WAY TOO HIGH!
            generatedLoot.clear();
            generatedLoot.add(new ItemStack(this.item));
        }

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
| `generatedLoot.clear()` | מנקה את כל הפריטים ש-naturally נופלים מ-suspicious sand brushing |
| `generatedLoot.add(new ItemStack(this.item))` | מוסיף רק את הפריט שלנו (Metal Detector) |
| `context.getRandom().nextFloat() < 0.5f` | 50% סיכוי — אם לא, משאיר את השלל הריק |
| `// 50% WAY TOO HIGH!` | הערת הקוד: 50% הוא גבוה מדי — במשחק האמיתי נשתמש באחוז נמוך יותר |

---

## שינוי ב-`ModLootModifiers.java`

<div dir="ltr">

```java
public static final RegistryObject<Codec<? extends IGlobalLootModifier>> ADD_SUS_SAND_ITEM =
        LOOT_MODIFIER_SERIALIZERS.register("add_sus_sand_item", AddSusSandItemModifier.CODEC);
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `register("add_sus_sand_item", AddSusSandItemModifier.CODEC)`| רושם את ה-CODEC החדש תחת שם `add_sus_sand_item` |

---

## שינוי ב-`ModGlobalLootModifiersProvider.java`

<div dir="ltr">

```java
add("metal_detector_from_suspicious_sand", new AddSusSandItemModifier(new LootItemCondition[] {
        new LootTableIdCondition.Builder(new ResourceLocation("minecraft:archaeology/desert_pyramid")).build() }, ModsItems.METAL_DETECTOR.get()));
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `"minecraft:archaeology/desert_pyramid"` | loot table של suspicious sand ב-desert pyramid |
| `ModsItems.METAL_DETECTOR.get()` | הפריט שנוסיף |

---

## קבצים שנוצרו אוטומטית (generated)

| קובץ | תוכן |
|---|---|
| `tutorialmod:metal_detector_from_suspicious_sand`| תנאי: loot table = archaeology/desert_pyramid |
| `forge:global_loot_modifiers` | מעדכן את הרשימה עם ה-modifier החדש |

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| **`generatedLoot.clear()`**| מנקה את כל הפריטים מ-loot table — משמש כדי להחליף loot לגמרי |
| **`LootTableIdCondition`**| מזהה loot table לפי ResourceLocation |
| **`context.getRandom()`**| מספר רנדומלי ספציפי ל-context זה (לא גלובלי) |

---

<div dir="ltr">

⬅️ [שלב 18](Step-18-Global-Loot-Modifiers) · ➡️ [שלב 20](Step-20-Crop-Block)

</div>

</div>

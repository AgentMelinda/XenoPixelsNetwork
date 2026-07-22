<div dir="rtl">

# 🔧 שלב 16 — שריון (Armor)

בשלב הקודם יצרנו סט כלי Sapphire מלא. כעת אנו ממשיכים עם **שריון** — קסדה, שריון, מכנסיים
ומגפיים. כמו הכלים, גם השריון דורש חומר שריון (Armor Material) מותאם.

> 💡 כדי ליצור שריון מותאם אנו צריכים:
> 1. מחלקת Armor Material (`ModArmorMaterials`)
> 2. רישום הפריטים ב-`ModsItems`
> 3. יצירת מודלים מותאמת עם trim supports

---

## מה השתנה לעומת השלב הקודם

| קבצים חדשים/ששונו | תיאור |
|---|---|
| `ModArmorMaterials.java` | מחלקת ArmorMaterial חדשה |
| `ModsItems.java` | 4 פריטי שריון חדשים |
| `ModItemModelProvider.java` | `trimmedArmorItem(...)` עם trim supports |
| `en_us.json` | שמות הפריטי שריון |
| `textures/item/` | 4 טקסטורות שריון |
| `textures/models/armor/` | 2 שכבת שריון (layer_1, layer_2) |

---

## קוד חדש ב-`ModArmorMaterials.java`

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.item;

import net.bullettrain.tutorialmod.TutorialMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.function.Supplier;

public enum ModArmorMaterials implements ArmorMaterial {
    SAPPHIRE("sapphire", 26, new int[]{ 5, 7, 5, 4 }, 25,
            SoundEvents.ARMOR_EQUIP_GOLD, 1f, 0f, () -> Ingredient.of(ModsItems.SAPPHIRE.get()));

    private final String name;
    private final int durabilityMultiplier;
    private final int[] protectionAmounts;
    private final int enchantmentValue;
    private final SoundEvent equipSound;
    private final float toughness;
    private final float knockbackResistance;
    private final Supplier<Ingredient> repairIngredient;

    private static final int[] BASE_DURABILITY = { 11, 16, 16, 13 };

    ModArmorMaterials(String name, int durabilityMultiplier, int[] protectionAmounts, int enchantmentValue,
                      SoundEvent equipSound, float toughness, float knockbackResistance,
                      Supplier<Ingredient> repairIngredient) {
        this.name = name;
        this.durabilityMultiplier = durabilityMultiplier;
        this.protectionAmounts = protectionAmounts;
        this.enchantmentValue = enchantmentValue;
        this.equipSound = equipSound;
        this.toughness = toughness;
        this.knockbackResistance = knockbackResistance;
        this.repairIngredient = repairIngredient;
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type pType) {
        return BASE_DURABILITY[pType.ordinal()] * this.durabilityMultiplier;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type pType) {
        return this.protectionAmounts[pType.ordinal()];
    }

    @Override
    public int getEnchantmentValue() {
        return enchantmentValue;
    }

    @Override
    public SoundEvent getEquipSound() {
        return this.equipSound;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return this.repairIngredient.get();
    }

    @Override
    public String getName() {
        return TutorialMod.MOD_ID + ":" + this.name;
    }

    @Override
    public float getToughness() {
        return this.toughness;
    }

    @Override
    public float getKnockbackResistance() {
        return this.knockbackResistance;
    }
}
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `new int[]{ 5, 7, 5, 4 }` | הגנת חלקי השריון: קסדה=5, חזה=7, מכנסיים=5, מגפיים=4 |
| `26` |מכפיל durability — `{11,16,16,13} × 26` |
| `25` | ערך enchantability — כמה קל להשגים enchantments |
| `SoundEvents.ARMOR_EQUIP_GOLD` | סאונד של לבישת שריון |
| `1f` | toughness — מפחית נזק מעבר להגנה הבסיסית |
| `0f` | knockbackResistance — מניע Hetback |
| `BASE_DURABILITY[pType.ordinal()] * this.durabilityMultiplier` | מחשב נשאיות לפי סוג הפריט |

---

## קוד חדש ב-`ModsItems.java`

<div dir="ltr">

```java
public static final RegistryObject<Item> SAPPHIRE_HELMET = ITEMS.register("sapphire_helmet",
        () -> new ArmorItem(ModArmorMaterials.SAPPHIRE, ArmorItem.Type.HELMET, new Item.Properties()));
public static final RegistryObject<Item> SAPPHIRE_CHESTPLATE = ITEMS.register("sapphire_chestplate",
        () -> new ArmorItem(ModArmorMaterials.SAPPHIRE, ArmorItem.Type.CHESTPLATE, new Item.Properties()));
public static final RegistryObject<Item> SAPPHIRE_LEGGINGS = ITEMS.register("sapphire_leggings",
        () -> new ArmorItem(ModArmorMaterials.SAPPHIRE, ArmorItem.Type.LEGGINGS, new Item.Properties()));
public static final RegistryObject<Item> SAPPHIRE_BOOTS = ITEMS.register("sapphire_boots",
        () -> new ArmorItem(ModArmorMaterials.SAPPHIRE, ArmorItem.Type.BOOTS, new Item.Properties()));
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `ArmorItem(ModArmorMaterials.SAPPHIRE, ArmorItem.Type.HELMET, ...)` | קסדה — משמשת ב-HELMET slot |
| `ArmorItem.Type.CHESTPLATE` | שריון חזה — בחזה |
| `ArmorItem.Type.LEGGINGS` | מכנסיים |
| `ArmorItem.Type.BOOTS` | מגפיים |

---

## קוד חדש ב-`ModItemModelProvider.java` — `trimmedArmorItem(...)`

<div dir="ltr">

```java
private static LinkedHashMap<ResourceKey<TrimMaterial>, Float> trimMaterials = new LinkedHashMap<>();
static {
    trimMaterials.put(TrimMaterials.QUARTZ, 0.1F);
    trimMaterials.put(TrimMaterials.IRON, 0.2F);
    trimMaterials.put(TrimMaterials.NETHERITE, 0.3F);
    trimMaterials.put(TrimMaterials.REDSTONE, 0.4F);
    trimMaterials.put(TrimMaterials.COPPER, 0.5F);
    trimMaterials.put(TrimMaterials.GOLD, 0.6F);
    trimMaterials.put(TrimMaterials.EMERALD, 0.7F);
    trimMaterials.put(TrimMaterials.DIAMOND, 0.8F);
    trimMaterials.put(TrimMaterials.LAPIS, 0.9F);
    trimMaterials.put(TrimMaterials.AMETHYST, 1.0F);
}

// ...

trimmedArmorItem(ModsItems.SAPPHIRE_HELMET);
trimmedArmorItem(ModsItems.SAPPHIRE_CHESTPLATE);
trimmedArmorItem(ModsItems.SAPPHIRE_LEGGINGS);
trimmedArmorItem(ModsItems.SAPPHIRE_BOOTS);
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `trimMaterials.put(TrimMaterials.QUARTZ, 0.1F)` | maps trim type למשקל (שימו לב: TrimMaterials זה enum מוגדר) |
| `trimmedArmorItem(...)` | יוצר מודלים לכל trim type אפשרי + override לפריט הבסיס |
| `existingFileHelper.trackGenerated(trimResLoc, ...)`| מונע `IllegalArgumentException` — Minecraft מחפש את הטקסטורה כברירת מחדל |
| `getBuilder(currentTrimName).parent(...).texture("layer0", ...).texture("layer1", ...)` | layer0 = השריון, layer1 = ה-trim |

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| **`ArmorMaterial`** | מחלקה שמגדירה חומר שריון: הגנה, נשאיות, סאונד, תיקון |
| **`ArmorItem`** | פריט שריון —מקושר ל-slot ספציפי (HEAD, CHEST, LEGS, FEET) |
| **`TrimMaterial`** | חומר Trim — ניתן להוסיף לשריון עם Smithing Table |
| **`layer0` / `layer1`**| שכבת טקסטורה: layer0 = השריון עצמו, layer1 = ה-trim |

---

<div dir="ltr">

⬅️ [שלב 15](Step-15-Tools) · ➡️ [שלב 17](Step-17-Full-Armor-Effect)

</div>

</div>

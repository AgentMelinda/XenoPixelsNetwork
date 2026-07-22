<div dir="rtl">

# 🔧 שלב 17 — אפקט סט שריון מלא (Full Armor Effect)

בשלב הקודם יצרנו את סט השריון של Sapphire. כעת אנו נלמד להוסיף **אפקט פעיל** כאשר השחקן
לובש את כל ארבעת חלקי השריון — במקרה שלנו, **Night Vision** (ראיית לילה).

> 💡 רעיון הבסיס: יוצרים מחלקה מותאמת `ModArmorItem` שמרחיבה את `ArmorItem`,
> וודורסים (override) את `onArmorTick(...)` כדי לבדוק אם יש שריון מלא ולהחיל אפקט.

---

## מה השתנה לעומת השלב הקודם

| קבצים חדשים/ששונו | תיאור |
|---|---|
| `custom/ModArmorItem.java` | מחלקה מותאמת עם `onArmorTick` |
| `ModsItems.java` | `SAPPHIRE_HELMET` עכשיו משתמש ב-`ModArmorItem` |

---

## קוד חדש ב-`ModArmorItem.java`

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.item.custom;

import com.google.common.collect.ImmutableMap;
import net.bullettrain.tutorialmod.item.ModArmorMaterials;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Map;

public class ModArmorItem extends ArmorItem {
    private static final Map<ArmorMaterial, MobEffectInstance> MATERIAL_TO_EFFECT_MAP =
            (new ImmutableMap.Builder<ArmorMaterial, MobEffectInstance>())
                    .put(ModArmorMaterials.SAPPHIRE, new MobEffectInstance(MobEffects.NIGHT_VISION, 200, 1,
                            false, false, true)).build();

    public ModArmorItem(ArmorMaterial pMaterial, Type pType, Properties pProperties) {
        super(pMaterial, pType, pProperties);
    }

    @Override
    public void onArmorTick(ItemStack stack, Level world, Player player) {
        if (!world.isClientSide()) {
            if (hasFullSuitOfArmorOn(player)) {
                evaluateArmorEffects(player);
            }
        }
    }

    private void evaluateArmorEffects(Player player) {
        for (Map.Entry<ArmorMaterial, MobEffectInstance> entry : MATERIAL_TO_EFFECT_MAP.entrySet()) {
            ArmorMaterial mapArmorMaterial = entry.getKey();
            MobEffectInstance mapStatusEffect = entry.getValue();

            if (hasCorrectArmorOn(mapArmorMaterial, player)) {
                addStatusEffectForMaterial(player, mapArmorMaterial, mapStatusEffect);
            }
        }
    }

    private void addStatusEffectForMaterial(Player player, ArmorMaterial mapArmorMaterial,
                                            MobEffectInstance mapStatusEffect) {
        boolean hasPlayerEffect = player.hasEffect(mapStatusEffect.getEffect());

        if (hasCorrectArmorOn(mapArmorMaterial, player) && !hasPlayerEffect) {
            player.addEffect(new MobEffectInstance(mapStatusEffect));
        }
    }

    private boolean hasFullSuitOfArmorOn(Player player) {
        ItemStack boots = player.getInventory().getArmor(0);
        ItemStack leggings = player.getInventory().getArmor(1);
        ItemStack breastplate = player.getInventory().getArmor(2);
        ItemStack helmet = player.getInventory().getArmor(3);

        return !helmet.isEmpty() && !breastplate.isEmpty()
                && !leggings.isEmpty() && !boots.isEmpty();
    }

    private boolean hasCorrectArmorOn(ArmorMaterial material, Player player) {
        for (ItemStack armorStack : player.getInventory().armor) {
            if (!(armorStack.getItem() instanceof ArmorItem)) {
                return false;
            }
        }

        ArmorItem boots = ((ArmorItem) player.getInventory().getArmor(0).getItem());
        ArmorItem leggings = ((ArmorItem) player.getInventory().getArmor(1).getItem());
        ArmorItem breastplate = ((ArmorItem) player.getInventory().getArmor(2).getItem());
        ArmorItem helmet = ((ArmorItem) player.getInventory().getArmor(3).getItem());

        return helmet.getMaterial() == material && breastplate.getMaterial() == material &&
                leggings.getMaterial() == material && boots.getMaterial() == material;
    }
}
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `MATERIAL_TO_EFFECT_MAP` | map שמקשר חומר שריון ל-`MobEffectInstance` |
| `MobEffectInstance(MobEffects.NIGHT_VISION, 200, 1, ...)` | אפקט ראיית לילה, 200 טיקים (=10 שניות), עוצמה 1, עם ק(false), showIcon(true) |
| `onArmorTick(...)` | נקראת כל טיק כאשר השחקן לובש שריון |
| `if (!world.isClientSide())` | מריץ רק על השרת — לא צריך לרוץ ללקוח |
| `hasFullSuitOfArmorOn(player)` | בודק אם כל 4 סלויות השריון מלאות |
| `evaluateArmorEffects(player)`| עובר על כל חומר שריון ובודק אם השחקן לובש אותו |
| `hasCorrectArmorOn(...)` | בודק שכל 4 חלקי השריון מאותו חומר |
| `player.addEffect(new MobEffectInstance(...))` | מכניס את האפקט לשחקן |

---

## שינוי ב-`ModsItems.java`

<div dir="ltr">

```java
public static final RegistryObject<Item> SAPPHIRE_HELMET = ITEMS.register("sapphire_helmet",
        () -> new ModArmorItem(ModArmorMaterials.SAPPHIRE, ArmorItem.Type.HELMET, new Item.Properties()));
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `new ModArmorItem(...)` | במקום `ArmorItem` רגיל, משתמשים במחלקה המותאמת שלנו |

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| **`onArmorTick`** | אירוע that fires כל טיק כאשר הפריט נשתמש כשריון |
| **`isClientSide()`** | returns true אם הרץ על הלקוח (גרפיקה) — משתמשים בזה כדי להמנע מריצה כפולה |
| **`MobEffectInstance`** | אפקט סטטוס עם משך, עוצמה והגדרות נוספות |
| **`ImmutableMap`** | map שאינו ניתן לשינוי לאחר יצירה |

---

<div dir="ltr">

⬅️ [שלב 16](Step-16-Armor) · ➡️ [שלב 18](Step-18-Global-Loot-Modifiers)

</div>

</div>

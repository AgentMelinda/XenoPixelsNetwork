<div dir="rtl">

# 🔧 שלב 23 — מסחר עם כפריים (Villager Trades)

בשלבים קודמים בנינו פריטים, בלוקים וגידולים. כעת אנו נלמד להוסיף **עסקאות כפריים**
(masquerading trades) לכפריים קיימים וגם ל-Wandering Trader.

אנו מוסיפים:
- **Farmer** (מגדל): קונה Strawberry, sells Corn, sells Corn Seeds
- **Librarian** (ספרן): מוכר ספר עם Enchantment של Thorns II
- **Wandering Trader** (סוכן נסע): מוכר Sapphire Boots ו-Metal Detector

> 💡 עסקאות מותאמות מוגדרות ב-`@SubscribeEvent` לאירוע `VillagerTradesEvent`
> ו-`WandererTradesEvent`.

---

## מה השתנה לעומת השלב הקודם

| קבצים חדשים/ששונו | תיאור |
|---|---|
| `event/ModEvents.java` | מחלקת events חדשה עם 2 מתודות subscribe |
| `en_us.json` | (אין שינוי משמעותי) |

---

## קוד חדש ב-`event/ModEvents.java`

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.event;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.bullettrain.tutorialmod.TutorialMod;
import net.bullettrain.tutorialmod.item.ModsItems;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = TutorialMod.MOD_ID)
public class ModEvents {

    @SubscribeEvent
    public static void addCustomTrades(VillagerTradesEvent event) {
        if(event.getType() == VillagerProfession.FARMER) {
            Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();

            // Level 1
            trades.get(1).add((pTrader, pRandom) -> new MerchantOffer(
                    new ItemStack(Items.EMERALD, 2),
                    new ItemStack(ModsItems.STRAWBERRY.get(), 12),
                    10, 8, 0.02f));

            // Level 2
            trades.get(2).add((pTrader, pRandom) -> new MerchantOffer(
                    new ItemStack(Items.EMERALD, 5),
                    new ItemStack(ModsItems.CORN.get(), 6),
                    5, 9, 0.035f));

            // Level 3
            trades.get(3).add((pTrader, pRandom) -> new MerchantOffer(
                    new ItemStack(Items.GOLD_INGOT, 8),
                    new ItemStack(ModsItems.CORN_SEEDS.get(), 2),
                    2, 12, 0.075f));
        }

        if(event.getType() == VillagerProfession.LIBRARIAN) {
            Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();
            ItemStack enchantedBook = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(Enchantments.THORNS, 2));

            // Level 1
            trades.get(1).add((pTrader, pRandom) -> new MerchantOffer(
                    new ItemStack(Items.EMERALD, 32),
                    enchantedBook,
                    2, 8, 0.02f));
        }
    }

    @SubscribeEvent
    public static void addCustomWanderingTrades(WandererTradesEvent event) {
        List<VillagerTrades.ItemListing> genericTrades = event.getGenericTrades();
        List<VillagerTrades.ItemListing> rareTrades = event.getRareTrades();

        genericTrades.add((pTrader, pRandom) -> new MerchantOffer(
                new ItemStack(Items.EMERALD, 12),
                new ItemStack(ModsItems.SAPPHIRE_BOOTS.get(), 1),
                3, 2, 0.2f));

        rareTrades.add((pTrader, pRandom) -> new MerchantOffer(
                new ItemStack(Items.EMERALD, 24),
                new ItemStack(ModsItems.METAL_DETECTOR.get(), 1),
                2, 12, 0.15f));
    }
}
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `@Mod.EventBusSubscriber(modid = TutorialMod.MOD_ID)`| מאפשר ל-Forge לגלות אוטומטית את המתודות הסטטיות עם `@SubscribeEvent` |
| `VillagerTradesEvent event` | אירוע שמתרחש לפני שהכפרי מוצר עסקאות — אפשר להוסיף עסקאות מותאמות |
| `event.getType() == VillagerProfession.FARMER` | בודק אם הכפרי הוא מגדל (Farmer) |
| `trades.get(1).add(...)` | מוסיף עסקה ל-Level 1 של הכפרי |
| `new MerchantOffer(ItemStack(EMERALD, 2), ItemStack(STRAWBERRY, 12), 10, 8, 0.02f)` | 2 Emerald → 12 Strawberry, 10 uses, Level 8, 2% price change |
| `EnchantedBookItem.createForEnchantment(...)` | יוצר ספר מוחשן עם Enchantment ספציפי |
| `WandererTradesEvent event` | אירוע ל-Wandering Trader (הסוכן הנסע) |
| `genericTrades.add(...)` | עסקאות כלליות שמופיעות תמיד |
| `rareTrades.add(...)` | עסקאות נדירות שמופיעות לעיתים רחוקות |

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| **`VillagerTradesEvent`** | אירוע שמופעל לפני יצירת עסקאות הכפרי |
| **`MerchantOffer`** | עסקה אחת: מחיר, מצרך, כמות שימושים, רמה, מחיר משתנה |
| **`EnchantmentInstance`** | Enchantment עם רמה — משמש לספרים מוחשנים |
| **`Int2ObjectMap`** | map מ-int ל-List — משמשת לשליפת עסקאות לפי רמה |

---

<div dir="ltr">

⬅️ [שלב 22](Step-22-Flowers) · ➡️ [שלב 24](Step-24-Villagers)

</div>

</div>

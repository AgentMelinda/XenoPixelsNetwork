<div dir="rtl">

# 🔧 שלב 24 — כפריים (Villagers)

בשלב הקודם הוספנו עסקאות לכפריים קיימים. כעת אנו יוצרים **כפרי מותאם חדש** —
**Sound Master** — כפרי שמתעסק במוזיקה וסאונד, עם תחנת עניין (POI) מותאמת.

> 💡 יצירת כפרי מותאם דורשת 3 דברים:
> 1. **POI Type** (תחנת עניין) — מה הבלוק שהכפרי "חושב עליו"
> 2. **Villager Profession** (מקצוע) — קובע אילו עסקאות הכפרי יקח
> 3. **POI Tag** (`acquirable_job_site`) — מאפשר לכפרי לתפוס את ה-POI

---

## מה השתנה לעומת השלב הקודם

| קבצים חדשים/ששונו | תיאור |
|---|---|
| `villager/ModVillagers.java` | `SOUND_POI` + `SOUND_MASTER` |
| `datagen/ModPoiTypeTagsProvider.java` | תגית `acquirable_job_site` |
| `event/ModEvents.java` | עסקאות ל-Sound Master |
| `datagen/DataGenerators.java` | רישום `ModPoiTypeTagsProvider` |
| `TutorialMod.java` | רישום `ModVillagers` |
| `en_us.json` | "Sound Master" |
| `textures/entity/villager/profession/soundmaster.png` | תמונה של הכפרי |

---

## קוד חדש ב-`villager/ModVillagers.java`

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.villager;

import com.google.common.collect.ImmutableSet;
import net.bullettrain.tutorialmod.TutorialMod;
import net.bullettrain.tutorialmod.block.ModBlocks;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModVillagers {
    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(ForgeRegistries.POI_TYPES, TutorialMod.MOD_ID);
    public static final DeferredRegister<VillagerProfession> VILLAGER_PROFESSIONS =
            DeferredRegister.create(ForgeRegistries.VILLAGER_PROFESSIONS, TutorialMod.MOD_ID);

    public static final RegistryObject<PoiType> SOUND_POI = POI_TYPES.register("sound_poi",
            () -> new PoiType(ImmutableSet.copyOf(ModBlocks.SOUND_BLOCK.get().getStateDefinition().getPossibleStates()),
                    1, 1));

    public static final RegistryObject<VillagerProfession> SOUND_MASTER =
            VILLAGER_PROFESSIONS.register("soundmaster", () -> new VillagerProfession("soundmaster",
                    holder -> holder.get() == SOUND_POI.get(), holder -> holder.get() == SOUND_POI.get(),
                    ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_ARMORER));

    public static void register(IEventBus eventBus) {
        POI_TYPES.register(eventBus);
        VILLAGER_PROFESSIONS.register(eventBus);
    }
}
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `DeferredRegister.create(ForgeRegistries.POI_TYPES, ...)` | רושם סוגי POI חדשים |
| `DeferredRegister.create(ForgeRegistries.VILLAGER_PROFESSIONS, ...)` | רושם מקצועות כפריים חדשים |
| `new PoiType(ImmutableSet.copyOf(...), 1, 1)` | יוצר POI עם כל מצבי הבלוק, גודל 1×1, טווח 1 |
| `new VillagerProfession("soundmaster", ...)` | מקצוע חדש: "soundmaster" |
| `holder -> holder.get() == SOUND_POI.get()` | תנאי עבודה: הבלוק חייב להיות SOUND_BLOCK |
| `ImmutableSet.of(), ImmutableSet.of()` | אין תחנות עבודה נוספות, אין טיפולים |
| `SoundEvents.VILLAGER_WORK_ARMORER` | סאונד העבודה (בדומה ל-armorer) |

---

## קוד חדש ב-`datagen/ModPoiTypeTagsProvider.java`

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.datagen;

import net.bullettrain.tutorialmod.TutorialMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.PoiTypeTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.PoiTypeTags;
import net.minecraftforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class ModPoiTypeTagsProvider extends PoiTypeTagsProvider {
    public ModPoiTypeTagsProvider(PackOutput pOutput, CompletableFuture<HolderLookup.Provider> pProvider,
                                  @Nullable ExistingFileHelper existingFileHelper) {
        super(pOutput, pProvider, TutorialMod.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider pProvider) {
        tag(PoiTypeTags.ACQUIRABLE_JOB_SITE)
                .addOptional(new ResourceLocation(TutorialMod.MOD_ID, "sound_poi"));
    }
}
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `extends PoiTypeTagsProvider` | Data Provider ל-tags של POI types |
| `PoiTypeTags.ACQUIRABLE_JOB_SITE` | תגית שמגדירה אילו POI כפרי יכול לתפוס |
| `addOptional(...)` | מוסיף את ה-POI שלנו כתג (אופציונלי — נחוץ ליצירת הכפרי) |

---

## שינוי ב-`event/ModEvents.java`

<div dir="ltr">

```java
if(event.getType() == ModVillagers.SOUND_MASTER.get()) {
    Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();

    trades.get(1).add((pTrader, pRandom) -> new MerchantOffer(
            new ItemStack(Items.EMERALD, 16),
            new ItemStack(ModBlocks.SOUND_BLOCK.get(), 1),
            16, 8, 0.02f));

    trades.get(2).add((pTrader, pRandom) -> new MerchantOffer(
            new ItemStack(Items.EMERALD, 6),
            new ItemStack(ModBlocks.SAPPHIRE_ORE.get(), 2),
            5, 12, 0.02f));
}
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `event.getType() == ModVillagers.SOUND_MASTER.get()`| מפעיל עסקאות רק עבור Sound Master |
| `16 Emerald → 1 Sound Block` | Level 1 — Sound Master מוכר את הבלוק המיוחד שלנו |
| `6 Emerald → 2 Sapphire Ore` | Level 2 — מוכר את הפחםן שלנו |

---

## שינוי ב-`TutorialMod.java`

<div dir="ltr">

```java
public TutorialMod() {
    // ...
    ModLootModifiers.register(modEventBus);
    ModVillagers.register(modEventBus);
    // ...
}
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `ModVillagers.register(modEventBus)` | רושם את POI וה-Profession לאירוע bus |

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| **`PoiType`** | תחנת עניין — מה הבלוק שהכפרי "חושב עליו" במהלך העבודה |
| **`VillagerProfession`** | מקצוע כפרי — קובע אילו עסקאות הכפרי ינסה לפתוח |
| **`ACQUIRABLE_JOB_SITE`** | תגית שמורה שמורה ל-POI כפרי שניתן לתפוס |
| **`ImmutableSet`** | Set שלא ניתן לשנות — משמש לברירת מחדל Froge |

---

<div dir="ltr">

⬅️ [שלב 23](Step-23-Villager-Trades) · ➡️ [שלב 25](Step-25-Sounds)

</div>

</div>

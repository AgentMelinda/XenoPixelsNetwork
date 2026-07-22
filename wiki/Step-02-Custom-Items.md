<div dir="rtl">

# 🔧 שלב 02 — פריטים מותאמים (Custom Items)

בשלב הזה אנו מוסיפים את **הפריטים הראשונים** למוד שלנו! 🇬🇧
ניצור שני פריטים חדשים — **Sapphire** ו-**Raw Sapphire** — ונלמד את מערכת ה-Creative Tabs,
שכן זה המקום שבו משתמשים רואים את הפריטים במשחק.

מה הלאה? נעניק לפריטים מראה (model) וטקסטורות, ונרשום אותם ב-registry של Minecraft כך שהמשחק יכיר אותם.

---

## מה השתנה לעומת השלב הקודם

| קובץ | שינוי |
|---|---|
| `src/main/java/.../TutorialMod.java` | רישום `ModCreativeModTabs` ו-`ModsItems`; הוספת פריטים ל-tab |
| `src/main/java/.../item/ModCreativeModTabs.java` | **חדש** — רישום של Creative Mode Tab |
| `src/main/java/.../item/ModsItems.java` | **חדש** — רישום הפריטים SAPPHIRE ו-RAW_SAPPHIRE |
| `src/main/resources/assets/tutorialmod/lang/en_us.json` | **חדש** — שמות תצוגה לפריטים ול-tab |
| `src/main/resources/.../models/item/sapphire.json` | **חדש** — model לפריט Sapphire |
| `src/main/resources/.../models/item/raw_sapphire.json` | **חדש** — model לפריט Raw Sapphire |
| `src/main/resources/.../textures/item/*.png` | **חדש** — טקסטורות לפריטים |

---

## מחלקת TutorialMod.java

<div dir="ltr">

```java
package net.bullettrain.tutorialmod;

import com.mojang.logging.LogUtils;
import net.bullettrain.tutorialmod.item.ModCreativeModTabs;
import net.bullettrain.tutorialmod.item.ModsItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(TutorialMod.MOD_ID)
public class TutorialMod {
    public static final String MOD_ID = "tutorialmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TutorialMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // סדר הרישום במוד שלנו (bullettrain):
        ModsItems.register(modEventBus);          // -- mods item register
        ModCreativeModTabs.register(modEventBus); // -- creative tab register

        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        modEventBus.addListener(this::addCreative);
    }

    private void commonSetup(final FMLCommonSetupEvent event) { }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if(event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModsItems.SAPPHIRE);
            event.accept(ModsItems.RAW_SAPPHIRE);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) { }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) { }
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `import net.bullettrain.tutorialmod.item.ModCreativeModTabs;` | מייבא את מחלקת ה-Tabs כדי לרשום אותה ב-event bus |
| `import net.bullettrain.tutorialmod.item.ModsItems;` | מייבא את מחלקת הפריטים כדי לרשום אותם |
| `import net.minecraft.world.item.CreativeModeTabs;` | מייבא את ה-enums של ה-Creative Tabs של Minecraft |
| `ModsItems.register(modEventBus);` | רושם את כל הפריטים לאוטובוס (שם המחלקה במוד שלנו: **ModsItems** עם s) |
| `ModCreativeModTabs.register(modEventBus);` | רושם את ה-Tab לאוטובוס האירועים של המוד |
| `if(event.getTabKey() == CreativeModeTabs.INGREDIENTS)` | בודק אם האירוע הוא עבור ה-tab שלIngredients |
| `event.accept(ModsItems.SAPPHIRE);` | מוסיף את פריט ה-Sapphire ל-tab |
| `event.accept(ModsItems.RAW_SAPPHIRE);` | מוסיף את פריט ה-Raw Sapphire ל-tab |

---

## מחלקת ModCreativeModTabs.java

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.item;

import net.bullettrain.tutorialmod.TutorialMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeModTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TutorialMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> TUTORIAL_TAB = CREATIVE_MODE_TABS.register("tutorial_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModsItems.SAPPHIRE.get()))
                    .title(Component.translatable("creativetab.tutorial_tab"))
                    .displayItems((pParameters, pOutput) -> {
                        pOutput.accept(ModsItems.SAPPHIRE.get());
                        pOutput.accept(ModsItems.RAW_SAPPHIRE.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID)` | יוצר דף רישום דחוי (deferred) ל-Creative Tabs |
| `CREATIVE_MODE_TABS.register("tutorial_tab", ...)` | רושם tab חדש עם המזהה `tutorial_tab` |
| `CreativeModeTab.builder()` | בונה (builder) את ה-tab החדש |
| `.icon(() -> new ItemStack(ModsItems.SAPPHIRE.get()))` | קובע את הפריט שיופיע כאייקון ב-tab |
| `.title(Component.translatable("creativetab.tutorial_tab"))` | שם ה-tab — יתורגם מ-`en_us.json` |
| `.displayItems((pParameters, pOutput) -> { ... })` | לוגיקה שמוסיפה פריטים ל-tab כאשר הוא נטען |
| `pOutput.accept(ModsItems.SAPPHIRE.get());` | מוסיף את ה-Sapphire |

| `.build()` | בונה את ה-tab |
| `CREATIVE_MODE_TABS.register(eventBus)` | הרישום בפועל לאוטובוס |

---

## מחלקת ModsItems.java

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.item;

import net.bullettrain.tutorialmod.TutorialMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModsItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TutorialMod.MOD_ID);

    public static final RegistryObject<Item> SAPPHIRE = ITEMS.register("sapphire",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> RAW_SAPPHIRE = ITEMS.register("raw_sapphire",
            () -> new Item(new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID)` | יוצר דף רישום דחוי לפריטים |
| `ITEMS.register("sapphire", () -> new Item(...))` | רושם פריט חדש עם המזהה `sapphire` |
| `new Item(new Item.Properties())` | יוצר פריט בסיסי ללא התנהגות מיוחדת |
| `ITEMS.register(eventBus)` | הרישום בפועל לאוטובוס האירועים |

> 💡 `DeferredRegister` דוחה את הרישום עד שהמשחק מוכן, כך נמנעות קריסות של טעינה מוקדמת מדי.

---

## Model לפריט Sapphire

<div dir="ltr">

```json
{
  "parent": "item/generated",
  "textures": {
    "layer0": "tutorialmod:item/sapphire"
  }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `"parent": "item/generated"` | משתמש במודל הבסיסי של Minecraft לפריטים דו-ממדיים (כמו יהלום) |
| `"layer0": "tutorialmod:item/sapphire"` | מיקום הטקסטורה — `assets/tutorialmod/textures/item/sapphire.png` |

---

## Model לפריט Raw Sapphire

<div dir="ltr">

```json
{
  "parent": "item/generated",
  "textures": {
    "layer0": "tutorialmod:item/raw_sapphire"
  }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `"parent": "item/generated"` | אותו מודל בסיסי כמו sapphire |
| `"layer0": "tutorialmod:item/raw_sapphire"` | טקסטורה נפרדת לפריט הגולמי |

---

## קובץ Lang en_us.json

<div dir="ltr">

```json
{
  "item.tutorialmod.sapphire": "Sapphire",
  "item.tutorialmod.raw_sapphire": "Raw Sapphire",
  "creativetab.tutorial_tab": "Sapphire Tutorial Tab"
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `"item.tutorialmod.sapphire"` | מפתח התרגום לפריט sapphire |
| `"Sapphire"` | שם התצוגה שיופיע במשחק |
| `"creativetab.tutorial_tab"` | שם ה-tab שיופיע ב-creative menu |

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| **RegistryObject\<T\>** | הפניה לפריט/בלוק שנרשם — מאפשרת גישה אליו מאוחר יותר |
| **DeferredRegister** | רישום דחוי — ממתין עד שהמשחק מוכן לפני שמוסיף ל-registry |
| **CreativeModeTab** | טאב ב-creative menu שבו מוצגים הפריטים של המוד |
| **Item.Properties** | קבוצת מאפיינים של הפריט (חוזק, עמידות וכו') |
| **ItemStack** | "ערימה" של פריטים — מייצג פריט יחיד עם כמות |
| **Component.translatable** | מחרוזת שתתורגם אוטומטית לפי קובץ ה-lang |

---

<div dir="ltr">

⬅️ [שלב 01](Step-01-Setup) · ➡️ [שלב 03](Step-03-Custom-Blocks)

</div>

</div>

<div dir="rtl">

# 🎯 מתקדם 06 — מערכת האירועים (Events) של Forge

> מדריך מעשי למוד שלנו: [`AgentMelinda/XenoPixelsNetwork`](https://github.com/AgentMelinda/XenoPixelsNetwork)
> (`net.bullettrain.tutorialmod`).
>
> המטרה: להבין איך "לתפוס" רגע מסוים במשחק (שחקן משתמש בפריט, שרת עולה, וכו')
> ולהריץ קוד משלנו — ואיך ליצור **אירוע משלנו** שמודים אחרים יוכלו להאזין לו.

---

## מה זה בכלל Event Bus?

Forge מריץ קוד "ליבה" (vanilla + Forge) שכל הזמן **קורה** (post) אירועים —
אובייקטים שמתארים "משהו קרה עכשיו" (שחקן פתח דלת, בלוק נשבר, שחקן אכל פריט...).

אתם לא צריכים "לחפור" בקוד של Minecraft כדי להריץ קוד משלכם ברגע מסוים —
מספיק **להירשם** (subscribe) לאירוע המתאים, וה-Bus יקרא לפונקציה שלכם אוטומטית.

יש שני "אוטובוסים" עיקריים:

| Bus | מתי משתמשים | דוגמה |
|---|---|---|
| `MinecraftForge.EVENT_BUS` (`Bus.FORGE`) | אירועי **משחק** בפועל: שחקן, world, entity | `PlayerInteractEvent`, `LivingHurtEvent` |
| `FMLJavaModLoadingContext` mod bus (`Bus.MOD`) | אירועי **מחזור חיים של המוד עצמו** | `FMLCommonSetupEvent`, `RegisterEvent`, `GatherDataEvent` |

בפרויקט שלנו זה כבר בשימוש ב-`TutorialMod.java`:

```java
modEventBus.addListener(this::commonSetup);       // FMLCommonSetupEvent → mod bus
MinecraftForge.EVENT_BUS.register(this);           // ServerStartingEvent → forge bus
```

---

## דרך 1: `@SubscribeEvent` + `@Mod.EventBusSubscriber` (הכי נפוץ)

זו הדרך המומלצת למחלקות סטטיות שמטרתן היחידה היא להאזין לאירועים.
דוגמה אמיתית מהפרויקט — `event/DmzHooks.java`:

```java
package net.bullettrain.tutorialmod.event;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TutorialMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzHooks {
    private DmzHooks() {}

    @SubscribeEvent
    public static void onTrainingPointGain(DMZEvent.TPGainEvent event) {
        // הקוד שלכם רץ אוטומטית כשה-DMZEvent.TPGainEvent נורה
    }
}
```

**חשוב:**
- הפונקציה חייבת להיות `public static` (או `public` רגיל אם המחלקה עצמה נרשמת ידנית — ראו דרך 2).
- הפרמטר היחיד הוא סוג האירוע — Forge קורא **לפי הטיפוס**, לא לפי שם הפונקציה.
- `bus` במאפיין (`Bus.FORGE`/`Bus.MOD`) חייב להתאים לסוג האירוע (בדקו מאיפה האירוע מגיע — בד"כ ה-Javadoc/שם החבילה מרמז).

---

## דרך 2: הרשמה ידנית עם `addListener` / `register`

שימושי כשרוצים להירשם רק בתנאים מסוימים, או בתוך הקונסטרקטור של המוד:

```java
public TutorialMod() {
    IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

    modEventBus.addListener(this::commonSetup);        // מאזין ישיר לפונקציה
    MinecraftForge.EVENT_BUS.register(this);            // רושם את כל המתודות המסומנות ב-@SubscribeEvent במחלקה הזו
    modEventBus.addListener(this::addCreative);
}

private void commonSetup(final FMLCommonSetupEvent event) { }

private void addCreative(BuildCreativeModeTabContentsEvent event) { }
```

---

## דוגמה: "שחקן משתמש בפריט"

יש כמה אירועים ל"שימוש בפריט" ולכל אחד תפקיד שונה — זה המקום הכי מבלבל, אז הנה טבלה:

| אירוע | מתי נורה | שימוש טיפוסי |
|---|---|---|
| `PlayerInteractEvent.RightClickItem` | ברגע שלוחצים קליק ימני עם הפריט ביד (לפני כל דבר אחר) | לבטל שימוש (`event.setCanceled(true)`), לוגיקה מיידית |
| `LivingEntityUseItemEvent.Start` | ברגע שמתחילים "להחזיק" שימוש ממושך (מתח קשת, אכילה) | לחשב cooldown/תנאים לפני שמתחילים |
| `LivingEntityUseItemEvent.Tick` | כל טיק **במהלך** שימוש ממושך | אפקטים מתמשכים (למשל חלקיקים בזמן אכילה) |
| `LivingEntityUseItemEvent.Finish` | ברגע שסיימו להשתמש (סיום אכילה, ירי חץ) | להפעיל קולדאון, לתת אפקט/פרס |
| `LivingEntityUseItemEvent.Stop` | כשמפסיקים **באמצע** (שחררו קליק לפני הסוף) | לנקות מצב, לבטל אפקט זמני |

דוגמה מעשית — הודעת cooldown לשחקן כשמנסה לאכול פריט שעדיין נעול (בהמשך לשיחה שלנו על `STRAWBERRY_SENZU`):

```java
@Mod.EventBusSubscriber(modid = TutorialMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ItemCooldownHooks {

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();

        if (stack.getItem() instanceof SenzuBeanItem && player.getCooldowns().isOnCooldown(stack.getItem())) {
            if (!event.getLevel().isClientSide) {
                player.displayClientMessage(Component.translatable("message.tutorialmod.senzu_cooldown"), true);
            }
            event.setCanceled(true); // עוצר את השימוש כאן, לפני שהאכילה בכלל מתחילה
        }
    }
}
```

> 💡 שימו לב: `event.setCanceled(true)` עובד רק על אירועים שמסומנים `@Cancelable` (אפשר לבדוק
> ב-Javadoc של המחלקה, או לנסות — אם לא תומך, הקומפיילר/ריצה יזרוק אזהרה/שגיאה).

---

## יצירת אירוע משלכם (Custom Event)

לפעמים אתם רוצים ש**מוד אחר** (או חלק אחר בקוד שלכם) יוכל להאזין לרגע מסוים אצלכם —
בדיוק כמו ש-DragonMineZ חושף `DMZEvent.TPGainEvent`.

### שלב 1 — הגדירו מחלקת אירוע

```java
package net.bullettrain.tutorialmod.event;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

// @Cancelable — רק אם אתם רוצים לאפשר event.setCanceled(true) למאזינים
@Cancelable
public class MetalDetectorFoundEvent extends Event {
    private final Player player;
    private final String foundItemId;

    public MetalDetectorFoundEvent(Player player, String foundItemId) {
        this.player = player;
        this.foundItemId = foundItemId;
    }

    public Player getPlayer() {
        return player;
    }

    public String getFoundItemId() {
        return foundItemId;
    }
}
```

### שלב 2 — "יריתם" (post) את האירוע מהקוד שלכם

```java
// בתוך MetalDetectorItem, ברגע שמצאתם משהו:
MetalDetectorFoundEvent event = new MetalDetectorFoundEvent(player, "tutorialmod:sapphire");
boolean canceled = MinecraftForge.EVENT_BUS.post(event);

if (!canceled) {
    // ההתנהגות הרגילה — למשל לתת לשחקן את הפריט
}
```

### שלב 3 — כל אחד (כולל אתם) יכול להאזין אליו

```java
@SubscribeEvent
public static void onMetalDetectorFound(MetalDetectorFoundEvent event) {
    TutorialMod.LOGGER.info("{} found {}", event.getPlayer().getGameProfile().getName(), event.getFoundItemId());
    // event.setCanceled(true); // רק אם המחלקה מסומנת @Cancelable
}
```

זו **בדיוק** השיטה ש-DragonMineZ השתמשו בה כדי לחשוף לכם את `DMZEvent.TPGainEvent` —
ראו [מתקדם 02 — DragonMineZ](Advanced-02-DragonMineZ) להרחבה על שימוש באירועי מוד חיצוני.

---

## טעויות נפוצות

- **בחירת Bus שגוי** — `Bus.MOD` לאירועי מחזור-חיים (setup, register, gather data), `Bus.FORGE` לאירועי משחק.
  אם המאזין שלכם "לא נקרא בכלל", בדקו קודם את זה.
- **לשכוח `static`** ב-`@Mod.EventBusSubscriber` — המתודה חייבת להיות `static` כשמשתמשים בהרשמה אוטומטית דרך המחלקה.
- **`event.setCanceled(true)`** על אירוע שלא `@Cancelable` — לא יעבוד; בדקו את מחלקת האירוע.
- **קוד לקוח בצד שרת (או להפך)** — תמיד תבדקו `level.isClientSide` / `!level.isClientSide` לפני שליחת הודעות
  לשחקן, שינוי נתונים, וכו', כדי לא להריץ לוגיקה כפולה משני הצדדים.

---

<div dir="ltr">

⬅️ [Advanced 05 — Registrate](Advanced-05-Registrate)

</div>

</div>

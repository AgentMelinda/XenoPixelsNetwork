<div dir="rtl">

# 🔧 שלב 06 — פריט מתקדם (Advanced Item)

בשלב הזה אנו יוצרים את **הפריט המתקדם הראשון** במוד — **Metal Detector**! 🇬🇧
פריט זה אינו רק תמונה בתיקייה: הוא כולל **לוגיקה Java** — כאשר שחקן לוחץ עליו על בלוק,
הפריט סורק 64 בלוקים למטה וחותר אחרי עופרת יקרה (Iron Ore ו-Diamond Ore) ומדווח על קואורדינטות.

זהו צעד קריטי: אנו כבר לא רק "מוסיפים פריטים" אלא **יוצרים התנהגות מותאמת**.

---

## מה השתנה לעומת השלב הקודם

| קובץ | שינוי |
|---|---|
| `src/main/java/.../TutorialMod.java` | הוספת ייבוא ורישום `ModsItems` |
| `src/main/java/.../item/ModCreativeModTabs.java` | הוספת `METAL_DETECTOR` ל-tab |
| `src/main/java/.../item/ModsItems.java` | רישום `METAL_DETECTOR` עם durability 100 |
| `src/main/java/.../item/custom/MetalDetectorItem.java` | **חדש** — מחלקת הפריט עם לוגיקה מותאמת |
| `src/main/resources/.../lang/en_us.json` | הוספת שם תצוגה |
| `src/main/resources/.../models/item/metal_detector.json` | **חדש** |
| `src/main/resources/.../textures/item/metal_detector.png` | **חדש** |

---

## מחלקת ModsItems.java — עדכון

<div dir="ltr">

```java
import net.bullettrain.tutorialmod.item.custom.MetalDetectorItem;
// ...

public static final RegistryObject<Item> METAL_DETECTOR = ITEMS.register("metal_detector",
        () -> new MetalDetectorItem(new Item.Properties().durability(100)));
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `import net.bullettrain.tutorialmod.item.custom.MetalDetectorItem;` | מייבא את מחלקת הפריט המותאמת |
| `new Item.Properties().durability(100)` | מגדיר שהפריט יש לו 100 שימושים לפני שהוא נשבר |

---

## מחלקת MetalDetectorItem.java

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.item.custom;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class MetalDetectorItem extends Item {
    public MetalDetectorItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public InteractionResult useOn(UseOnContext pContext) {
        if (!pContext.getLevel().isClientSide()) {
            BlockPos positionClicked = pContext.getClickedPos();
            Player player = pContext.getPlayer();
            boolean foundBlock = false;

            for (int i = 0; i <= positionClicked.getY() + 64; i++) {
                BlockState state = pContext.getLevel().getBlockState(positionClicked.below(i));

                if (isValueableBlock(state)) {
                    outputValuableCoordinates(positionClicked.below(i), player, state.getBlock());
                    foundBlock = true;

                    break;
                }
            }

            if(!foundBlock) {
                player.sendSystemMessage(Component.literal("No valuables found"));
            }

            pContext.getItemInHand().hurtAndBreak(1, pContext.getPlayer(),
                    Player -> player.broadcastBreakEvent(player.getUsedItemHand()));
        }
        return InteractionResult.SUCCESS;
    }

    private void outputValuableCoordinates(BlockPos blockPos, Player player, Block block) {
        player.sendSystemMessage(Component.literal("Found " + I18n.get(block.getDescriptionId()) + " at " +
                "(" + blockPos.getX() + ", " + blockPos.getY() + "," + blockPos.getZ() + ")"));
    }

    private boolean isValueableBlock(BlockState state) {
        return state.is(Blocks.IRON_ORE) || state.is(Blocks.DIAMOND_ORE);
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `public class MetalDetectorItem extends Item` | יורש ממחלקת `Item` של Minecraft |
| `public InteractionResult useOn(UseOnContext pContext)` | נקודת הכניסה כאשר השחקן לוחץ על בלוק |
| `if(!pContext.getLevel().isClientSide())` | מוודא שהקוד רץ רק בצד השרת (לא כפול) |
| `BlockPos positionClicked = pContext.getClickedPos();` | קואורדינטות המקום שבו לחץ השחקן |
| `for(int i = 0; i <= positionClicked.getY() + 64; i++)` | סורק עד 64 בלוקים למטה |
| `positionClicked.below(i)` | קואורדינטת הבלוק `i` בלוקים מתחת |
| `getBlockState(...)` | מקבל את מצב הבלוק במיקום הזה |
| `isValueableBlock(state)` | בודק אם זה עופרת יקרה |
| `outputValuableCoordinates(...)` | שולח הודעה לשחקן עם הקואורדינטות |
| `player.sendSystemMessage(...)` | מציג הודעת chat לשחקן |
| `I18n.get(block.getDescriptionId())` | מתרגם את שם הבלוק לפי קובץ ה-lang |
| `hurtAndBreak(1, ...)` | מחסר 1 points מה-durability של הפריט |
| `broadcastBreakEvent(...)` | שולח הודעה ללקוחות שהפריט ניזוק |
| `return InteractionResult.SUCCESS;` | מסמל שהפעולה הצליחה |

---

## Model — metal_detector.json

<div dir="ltr">

```json
{
  "parent": "item/generated",
  "textures": {
    "layer0": "tutorialmod:item/metal_detector"
  }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `"parent": "item/generated"` | משתמש במודל item רגיל דו-ממדי |
| `"layer0": "tutorialmod:item/metal_detector"` | קובע את הטקסטורה |

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| **extends Item** | יורש ממחלקת ה-Item הבסיסית של Minecraft |
| **useOn(UseOnContext)** | מתודה שנקראת כאשר השחקן לוחץ על בלוק עם הפריט ב-hand |
| **InteractionResult** | קוד חיווי האם הפעולה הצליחה, נדחתה או נשארה לאורָה |
| **BlockPos** | מחלקה שמאחסנת קואורדינטות x, y, z |
| **BlockState** | מצב הבלוק במיקום מסוים (מהו סוג הבלוק) |
| **isClientSide()** | מחזיר `true` אם הקוד רץ בצד הקליינט |
| **durability** | עמידות הפריט — כמה פעמים ניתן להשתמש בו לפני שבירה |
| **hurtAndBreak** | מחסיר points מהעמידות ופועל callbacks |

---

<div dir="ltr">

⬅️ [שלב 05](Step-05-Loot-Tables) · ➡️ [שלב 07](Step-07-Advanced-Block)

</div>

</div>

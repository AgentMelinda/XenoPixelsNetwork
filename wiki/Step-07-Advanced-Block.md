<div dir="rtl">

# 🔧 שלב 07 — בלוק מתקדם (Advanced Block)

בשלב הזה אנו יוצרים את **הבלוק המתקדם הראשון** במוד — **Sound Block**! 🇬🇧
בלוק זה אינו רק בלוק סטטי: הוא כולל **התנהגות מותאמת** — כאשר שחקן לוחץ עליו,
הוא מנגן צליל מהמשחק (`NOTE_BLOCK_DIDGERIDOO`).

זהו הצעד הבא אחרי הפריט המתקדם: עכשיו אנחנו כותבים **Logic לבלוקים** עצמם.

---

## מה השתנה לעומת השלב הקודם

| קובץ | שינוי |
|---|---|
| `src/main/java/.../block/ModBlocks.java` | רישום `SOUND_BLOCK` |
| `src/main/java/.../block/custom/SoundBlock.java` | **חדש** — מחלקת הבלוק עם לוגיקה מותאמת |
| `src/main/java/.../item/ModCreativeModTabs.java` | הוספת `SOUND_BLOCK` ל-tab |
| `src/main/resources/.../blockstates/sound_block.json` | **חדש** |
| `src/main/resources/.../models/block/sound_block.json` | **חדש** |
| `src/main/resources/.../models/item/sound_block.json` | **חדש** |
| `src/main/resources/.../lang/en_us.json` | הוספת שם תצוגה |
| `src/main/resources/.../textures/block/sound_block.png` | **חדש** |

---

## עדכון ModBlocks.java

<div dir="ltr">

```java
import net.bullettrain.tutorialmod.block.custom.SoundBlock;
// ...

public static final RegistryObject<Block> SOUND_BLOCK = registerBlock("sound_block",
        () -> new SoundBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)));
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `import net.bullettrain.tutorialmod.block.custom.SoundBlock;` | מייבא את מחלקת הבלוק המותאמת |
| `new SoundBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK))` | יוצר בלוק חדש המח לקוח ממאפייני הברזל |

---

## מחלקת SoundBlock.java

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class SoundBlock extends Block {
    public SoundBlock(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos,
                                 Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        pLevel.playSound(pPlayer, pPos, SoundEvents.NOTE_BLOCK_DIDGERIDOO.get(), SoundSource.BLOCKS,
                1f, 1f);
        return InteractionResult.SUCCESS;
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `public class SoundBlock extends Block` | יורש ממחלקת ה-Block הבסיסית |
| `public SoundBlock(Properties pProperties)` | בורר שמעביר מאפיינים למחלקת האב |
| `super(pProperties)` | קורא לבורר של מחלקת האב |
| `public InteractionResult use(...)` | נקודת הכניסה כאשר השחקן לוחץ על הבלוק |
| `BlockState pState` | מצב הבלוק הנוכחי |
| `Level pLevel` | העולם שבו נמצא הבלוק |
| `BlockPos pPos` | קואורדינטות הבלוק |
| `Player pPlayer` | השחקן שלחץ |
| `InteractionHand pHand` | איזה יד (main/off) שימשה |
| `BlockHitResult pHit` | תוצאתiserit הקליק (איפה בבלוק הלחצו) |
| `pLevel.playSound(...)` | מנגן צליל בעולם |
| `SoundEvents.NOTE_BLOCK_DIDGERIDOO.get()` | הצליל הספציפי — צליל של note block |
| `SoundSource.BLOCKS` | מקור הצליל — קטגוריית `blocks` |
| `1f, 1f` | עוצמה ופיצוי (pitch) — 1f = ברירת מחדל |
| `return InteractionResult.SUCCESS;` | מסמל שהפעולה הצליחה |

---

## Model — sound_block.json

<div dir="ltr">

```json
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "tutorialmod:block/sound_block"
  }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `"parent": "minecraft:block/cube_all"` | מודל קובייה מלאה |
| `"all": "tutorialmod:block/sound_block"` | כל 6 הפאות משתמשות בטקסטורה `sound_block.png` |

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| **extends Block** | מחלקה שמגדירה התנהגות חדשה לבלוק |
| **BlockState** | מצב הבלוק (האם פתוח, דולק, מכוון וכו') |
| **Level** | העולם (מתפצל ל-ClientLevel ו-ServerLevel) |
| **SoundEvents** | Enum של כל הצלילים הזמינים ב-Minecraft |
| **SoundSource** | קטגוריית הצליל (BLOCKS, PLAYER, AMBIENT וכו') |
| **InteractionResult** | קוד חיווי של פעולת player על בלוק/פריט |

---

<div dir="ltr">

⬅️ [שלב 06](Step-06-Advanced-Item) · ➡️ [שלב 08](Step-08-Food-Item)

</div>

</div>

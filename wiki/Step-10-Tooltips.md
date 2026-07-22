<div dir="rtl">

# 🔧 שלב 10 — Tooltips

בשלב הזה אנו מוסיפים **טקסט מרחף** (tooltip) לפריטים ולבלוקים! 🇬🇧
כאשר השחקן מעביר את העכבר מעל לפריט או בלוק, מופיע טקסט נוסף מתחת לשם.

בשלב הזה ניצור שני tooltips:
1. **Metal Detector** — "Finds valuables underground!"
2. **Sound Block** — "Makes sweet sounds when right-clicked!"

---

## מה השתנה לעומת השלב הקודם

| קובץ | שינוי |
|---|---|
| `src/main/java/.../block/custom/SoundBlock.java` | הוספת `appendHoverText` |
| `src/main/java/.../item/custom/MetalDetectorItem.java` | הוספת `appendHoverText` |
| `src/main/resources/.../lang/en_us.json` | הוספת `tooltip.tutorialmod.metal_detector.tooltip` |

---

## עדכון SoundBlock.java

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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

    @Override
    public void appendHoverText(ItemStack pStack, @Nullable BlockGetter pLevel, List<Component> pTooltip, TooltipFlag pFlag) {
        pTooltip.add(Component.literal("Makes sweet sounds when right-clicked!"));
        super.appendHoverText(pStack, pLevel, pTooltip, pFlag);
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `import net.minecraft.world.item.ItemStack;` | מייבא את מחלקת ערימת הפריטים |
| `import net.minecraft.world.item.TooltipFlag;` | מייבא את דגל ה-tooltip (advanced/normal) |
| `import net.minecraft.world.level.BlockGetter;` | ממשק לקבלת גישה לבלוקים בעולם |
| `import org.jetbrains.annotations.Nullable;` | ה-annotation שמסמלת שהערך יכול להיות null |
| `import java.util.List;` | מייבא את ה-List של רכיבי ה-tooltip |
| `appendHoverText(ItemStack pStack, ...)` | מתודה שקורית כאשר רואים tooltip של הפריט/בלוק |
| `pTooltip.add(Component.literal("..."));` | מוסיף שורה טקסט ל-tooltip |
| `super.appendHoverText(...)` | קורא לביצוע המחלקה האב (שמור על התנהגות ברירת מחדל) |

---

## עדכון MetalDetectorItem.java

<div dir="ltr">

```java
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.List;
// ...

@Override
public void appendHoverText(ItemStack pStack, @Nullable Level pLevel, List<Component> pTooltipComponents, TooltipFlag pIsAdvanced) {
    pTooltipComponents.add(Component.translatable("tooltip.tutorialmod.metal_detector.tooltip"));
    super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced);
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `Component.translatable("tooltip.tutorialmod.metal_detector.tooltip")` | String שתתורגם אוטומטית מ-`en_us.json` |
| `pTooltipComponents.add(...)` | מוסיף את המשפט המותאם ל-tooltip |

---

## עדכון Lang en_us.json

<div dir="ltr">

```json
{
  "tooltip.tutorialmod.metal_detector.tooltip": "Finds valuables underground!"
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `tooltip.tutorialmod.metal_detector.tooltip` | מפתח התרגום ל-tooltip של Metal Detector |
| `"Finds valuables underground!"` | הטקסט שיופיע במשחק |

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| **appendHoverText** | מתודה שקורית כאשר רואים tooltip של פריט/בלוק |
| **ItemStack** | ערימת פריטים — נמסר ל-tooltip כדי לדעת על אילו פריטים מדובר |
| **TooltipFlag** | דגל שמצייד האם המשתמש לוחץ על Shift (advanced tooltip) |
| **Component.literal** | יצירת רכיב טקסט ללא תרגום |
| **Component.translatable** | יצירת רכיב טקסט שיתורגם לפי קובץ ה-lang |

---

<div dir="ltr">

⬅️ [שלב 09](Step-09-Fuel-Item) · ➡️ [שלב 11](Step-11-Tags)

</div>

</div>

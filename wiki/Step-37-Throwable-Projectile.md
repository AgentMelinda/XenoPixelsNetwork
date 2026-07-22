<div dir="rtl">

# 🔧 שלב 37 — קליע נזרק (Throwable Projectile)

בשלב הזה אנחנו מוסיפים **פריט שאפשר לזרוק** — קוביית קלפים (Dice). כשזורקים אותה, נוצרת ישות "קליע נזרק" שעפה וכשהיא פוגעת בקרקע — היא מציבה במקום **בלוק קובייה** עם מספר רנדומלי (1-6). זה שלב מצוין להבין איך יוצרים ישות נזרקת, פריט שיורה אותה, ובלוק עם מצב (state) שמשתנה.

## מה השתנה לעומת השלב הקודם

| קובץ | מה נוסף |
|---|---|
| `item/custom/DiceItem.java` | הפריט שזורק את הקליע |
| `entity/custom/DiceProjectileEntity.java` | הישות הנזרקת |
| `block/custom/DiceBlock.java` | בלוק בעל מצב רנדומלי (מספר 1-6) |
| `entity/ModEntities.java` + `block/ModBlocks.java` + `item/ModsItems.java` + `ModCreativeModTabs.java` | רישום הישות, הבלוק והפריט |
| `TutorialMod.java` + `ModEventBusClientEvents` | רישום רינדורר (`ThrownItemRenderer`) |

> המודלים (`dice_1`..`dice_6`), ה-blockstates, ה-lang והמרקמים נוצרים אוטומטית ע"י ה-Data Generator.

---

## הפריט הזורק — `item/custom/DiceItem.java`

<div dir="ltr">

```java
public class DiceItem extends Item {
    public DiceItem(Properties pProperties) { super(pProperties); }

    public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pHand) {
        ItemStack itemstack = pPlayer.getItemInHand(pHand);
        pLevel.playSound((Player)null, pPlayer.getX(), pPlayer.getY(), pPlayer.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (pLevel.getRandom().nextFloat() * 0.4F + 0.8F));

        if (!pLevel.isClientSide) {
            DiceProjectileEntity dice = new DiceProjectileEntity(pLevel, pPlayer);
            dice.setItem(itemstack);
            dice.shootFromRotation(pPlayer, pPlayer.getXRot(), pPlayer.getYRot(), 0.0F, 1.5F, 1.0F);
            pLevel.addFreshEntity(dice);
        }

        pPlayer.awardStat(Stats.ITEM_USED.get(this));
        if (!pPlayer.getAbilities().instabuild) { itemstack.shrink(1); }
        return InteractionResultHolder.sidedSuccess(itemstack, pLevel.isClientSide());
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `extends Item` | פריט רגיל שמרחיבים את ה-`use` שלו |
| `playSound(SNOWBALL_THROW)` | צליל זריקה כמו כדור-שלג |
| `new DiceProjectileEntity(pLevel, pPlayer)` | יוצר את הישות הנזרקת מהשחקן |
| `shootFromRotation(...)` | מכוון ומעיף את הקליע לפי זווית המבט |
| `itemstack.shrink(1)` | מפחית פריט אחד מהיד (אלא אם creative) |

---

## הישות הנזרקת — `entity/custom/DiceProjectileEntity.java`

<div dir="ltr">

```java
public class DiceProjectileEntity extends ThrowableItemProjectile {
    public DiceProjectileEntity(EntityType<? extends ThrowableItemProjectile> pEntityType, Level pLevel) { super(pEntityType, pLevel); }
    public DiceProjectileEntity(Level pLevel) { super(ModEntities.DICE_PROJECTILE.get(), pLevel); }
    public DiceProjectileEntity(Level pLevel, LivingEntity livingEntity) { super(ModEntities.DICE_PROJECTILE.get(), livingEntity, pLevel); }

    @Override
    protected Item getDefaultItem() { return ModsItems.DICE.get(); }

    @Override
    protected void onHitBlock(BlockHitResult pResult) {
        if(!this.level().isClientSide()) {
            this.level().broadcastEntityEvent(this, ((byte) 3));
            this.level().setBlock(blockPosition(), ((DiceBlock) ModBlocks.DICE_BLOCK.get()).getRandomBlockState(), 3);
        }
        this.discard();
        super.onHitBlock(pResult);
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `extends ThrowableItemProjectile` | ישות נזרקת שמבוססת על פריט (כמו ביצה/כדור שלג) |
| `getDefaultItem()` | איזה פריט הישות "נושאת" |
| `onHitBlock(...)` | נקראת בפגיעה בבלוק — מציבה בלוק קובייה רנדומלי |
| `broadcastEntityEvent((byte)3)` | אפקט התנפצות סטנדרטי |
| `discard()` | מסירה את הישות אחרי הפגיעה |

---

## בלוק הקובייה — `block/custom/DiceBlock.java`

<div dir="ltr">

```java
public class DiceBlock extends Block {
    public static DirectionProperty FACING = DirectionProperty.create("number",
            Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.DOWN);

    public DiceBlock(Properties properties) { super(properties); }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext placeContext) {
        return this.defaultBlockState().setValue(FACING, getRandomDirection());
    }

    public BlockState getRandomBlockState() {
        return this.defaultBlockState().setValue(FACING, getRandomDirection());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    private Direction getRandomDirection() {
        Direction[] dirs = { Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.DOWN };
        return dirs[RandomSource.create().nextIntBetweenInclusive(0, dirs.length-1)];
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `DirectionProperty FACING = ... "number"` | מצב הבלוק = כיוון אחד מ-6, שמייצג את המספר שעלה בקובייה |
| `getStateForPlacement` | כשמציבים ידנית — בוחר כיוון רנדומלי |
| `getRandomBlockState()` | משמש את הישות הנזרקת להציב תוצאה רנדומלית |
| `createBlockStateDefinition` | מגדירה אילו מצבים (states) לבלוק יש |

## מושגי מפתח

| מושג | הסבר |
|---|---|
| `ThrowableItemProjectile` | ישות נזרקת מבוססת-פריט |
| `shootFromRotation` | מעיפה את הקליע לפי זווית השחקן |
| `DirectionProperty` (מצב/state) | מאפשר לבלוק "להראות" ערך שונה (כאן: המספר שעלה) |
| `onHitBlock` | נקודת ההתיחסות לפגיעה בקרקע/בלוק |

<div dir="ltr">

⬅️ [שלב 36](Step-36-Boats) · ➡️ [שלב 38](Step-38-Ore-Generation)

</div>

</div>
<div dir="rtl">

# 🔧 שלב 30 — Block Entity

בשלב הזה אנחנו בונים את **תחנת ליטוש האבנים (Gem Polishing Station)** — בלוק שאינו סתם בלוק אלא מכיל **מצב פנימי**: מלאי של שני סlotים (קלט ופלט) ותהליך ייצור שמתקדם עם הזמן. בשביל זה אנחנו משתמשים ב-**Block Entity** — אובייקט שצמוד לבלוק ושומר נתונים. בנוסף אנחנו מוסיפים ממשק GUI (תפריט) שנפתח בלחיצה, עם מסך מותאם.

## מה השתנה לעומת השלב הקודם

| קובץ | מה נוסף |
|---|---|
| `block/custom/GemPolishingStationBlock.java` | הבלוק עצמו (פותח GUI, שומר מלאי) |
| `block/entity/GemPolishingStationBlockEntity.java` | ה-Block Entity עם המלאי והלוגיקה |
| `block/entity/ModBlockEntities.java` | רישום ה-Block Entity |
| `screen/GemPolishingStationMenu.java` + `GemPolishingStationScreen.java` + `ModMenuTypes.java` | תפריט + מסך + רישום סוג התפריט |
| `TutorialMod.java` | חיבור BlockEntities, Menus ורישום המסך |
| `block/ModBlocks.java` + `ModCreativeModTabs.java` | רישום הבלוק ביצירה ובטאב |

---

## הבלוק — `GemPolishingStationBlock.java`

<div dir="ltr">

```java
public class GemPolishingStationBlock extends BaseEntityBlock {
    public static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 12, 16);

    public GemPolishingStationBlock(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        if (pState.getBlock() != pNewState.getBlock()) {
            BlockEntity blockEntity = pLevel.getBlockEntity(pPos);
            if (blockEntity instanceof GemPolishingStationBlockEntity) {
                ((GemPolishingStationBlockEntity) blockEntity).drops();
            }
        }
        super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);
    }

    @Override
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        if (!pLevel.isClientSide()) {
            BlockEntity entity = pLevel.getBlockEntity(pPos);
            if(entity instanceof GemPolishingStationBlockEntity) {
                NetworkHooks.openScreen(((ServerPlayer)pPlayer), (GemPolishingStationBlockEntity)entity, pPos);
            } else {
                throw new IllegalStateException("Our Container provider is missing!");
            }
        }
        return InteractionResult.sidedSuccess(pLevel.isClientSide());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new GemPolishingStationBlockEntity(pPos, pState);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level pLevel, BlockState pState, BlockEntityType<T> pBlockEntityType) {
        if(pLevel.isClientSide()) { return null; }
        return createTickerHelper(pBlockEntityType, ModBlockEntities.GEM_POLISHING_BE.get(),
                (pLevel1, pPos, pState1, pBlockEntity) -> pBlockEntity.tick(pLevel1, pPos, pState1));
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `extends BaseEntityBlock` | בלוק שמחזיק Block Entity (במקום `Block` רגיל) |
| `VoxelShape SHAPE` | צורת ההתנגשות — גובה 12 במקום 16 (הבלוק נמוך יותר) |
| `onRemove` | כשהבלוק נהרס — מפיל את תוכן המלאי (`drops()`) |
| `use(...)` | בלחיצה (בצד השרת) פותח את המסך דרך `NetworkHooks.openScreen` |
| `newBlockEntity(...)` | יוצר את ה-Block Entity הצמוד לבלוק |
| `getTicker(...)` | מחזיר פונקציה שנקראת כל טיק כדי להריץ `tick()` (רק בשרת) |

---

## ה-Block Entity — `GemPolishingStationBlockEntity.java` (תמצית)

<div dir="ltr">

```java
public class GemPolishingStationBlockEntity extends BlockEntity implements MenuProvider {
    private final ItemStackHandler itemHandler = new ItemStackHandler(2);
    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT = 1;
    private final ContainerData data;
    private int progress = 0;
    private int maxProgress = 78;

    public GemPolishingStationBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(ModBlockEntities.GEM_POLISHING_BE.get(), pPos, pBlockState);
        this.data = new ContainerData() {
            public int get(int pIndex) {
                return switch (pIndex) {
                    case 0 -> GemPolishingStationBlockEntity.this.progress;
                    case 1 -> GemPolishingStationBlockEntity.this.maxProgress;
                    default -> 0;
                };
            }
            public void set(int pIndex, int pValue) { /* progress / maxProgress */ }
            public int getCount() { return 2; }
        };
    }

    public void tick(Level pLevel, BlockPos pPos, BlockState pState) {
        if(hasRecipe()) {
            increaseCraftingProgress();
            setChanged(pLevel, pPos, pState);
            if(hasProgressFinished()) { craftItem(); resetProgress(); }
        } else {
            resetProgress();
        }
    }

    private void craftItem() {
        ItemStack result = new ItemStack(ModsItems.SAPPHIRE.get(), 1);
        this.itemHandler.extractItem(INPUT_SLOT, 1, false);
        this.itemHandler.setStackInSlot(OUTPUT_SLOT, new ItemStack(result.getItem(),
                this.itemHandler.getStackInSlot(OUTPUT_SLOT).getCount() + result.getCount()));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.tutorialmod.gem_polishing_station");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int pContainerId, Inventory pPlayerInventory, Player pPlayer) {
        return new GemPolishingStationMenu(pContainerId, pPlayerInventory, this, this.data);
    }

    @Override
    protected void saveAdditional(CompoundTag pTag) {
        pTag.put("inventory", itemHandler.serializeNBT());
        pTag.putInt("gem_polishing_station.progress", progress);
        super.saveAdditional(pTag);
    }

    @Override
    public void load(CompoundTag pTag) {
        super.load(pTag);
        itemHandler.deserializeNBT(pTag.getCompound("inventory"));
        progress = pTag.getInt("gem_polishing_station.progress");
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `ItemStackHandler itemHandler(2)` | מלאי של 2 סlotים (קלט/פלט) מטופל ע"י Forge capabilities |
| `ContainerData data` | נתונים שמסונכרנים למסך (התקדמות) |
| `tick(...)` | כל טיק בודק מתכון, מקדם התקדמות, וכשמסתיים — מייצר פריט |
| `craftItem()` | פחות 1 קלט, מוסיף תוצרת לסlot הפלט |
| `MenuProvider` / `createMenu` | מאפשר לפתוח תפריט GUI צמוד ל-Block Entity |
| `saveAdditional` / `load` | שומר וטוען את המלאי וההתקדמות ל-NBT (כשהעולם נשמר) |

---

## התפריט והמסך

`GemPolishingStationMenu` יורש מ-`AbstractContainerMenu`, מגדיר 2 סlotים (במיקום 80,11 ו-80,59) ומספק `getScaledProgress()` למסך. `GemPolishingStationScreen` יורש מ-`AbstractContainerScreen` ומצייר את הטקסטורה `textures/gui/gem_polishing_station_gui.png` ואת חץ ההתקדמות. `ModMenuTypes` רושם את סוג התפריט דרך `IForgeMenuType.create`.

ב-`TutorialMod` נרשם הכל: `ModBlockEntities.register`, `ModMenuTypes.register`, וב-`onClientSetup` — `MenuScreens.register(ModMenuTypes.GEM_POLISHING_MENU.get(), GemPolishingStationScreen::new)`.

## מושגי מפתח

| מושג | הסבר |
|---|---|
| `BlockEntity` | אובייקט צמוד לבלוק ששומר מצב/מלאי (לא רק מראה) |
| `MenuProvider` | ממשק שמאפשר לבלוק "לפתוח" תפריט GUI |
| `ItemStackHandler` | מלאי מטופל-Forge (2 סlotים כאן) |
| `ContainerData` | מערך נתונים מסונכרן למסך (התקדמות) |
| `NetworkHooks.openScreen` | פותח מסך תפריט בצד הלקוח מתוך לחיצה בשרת |

<div dir="ltr">

⬅️ [שלב 29](Step-29-Attack-Animation) · ➡️ [שלב 31](Step-31-Recipe-Types)

</div>

</div>
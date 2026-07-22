<div dir="rtl">

# 🔧 שלב 33 — Block Entity Renderer

בשלב הקודם תחנת ליטוש האבנים עבדה לוגית אבל נראתה כמו בלוק רגיל. בשלב הזה אנחנו מוסיפים **Block Entity Renderer (BER)** — רכיב שמצייר משהו דינמי מעל הבלוק, במקרה שלנו את הפריט שנמצא כרגע בתחנה (קלט או פלט) כך שהשחקן רואה אותו מונח על המכונה. בנוסף אנחנו דואגים שהשינויים במלאי **יסונכרנו ללקוח** (כדי שהרינדורר יראה את העדכון).

## מה השתנה לעומת השלב הקודם

| קובץ | מה נוסף |
|---|---|
| `block/entity/renderer/GemPolishingBlockEntityRenderer.java` | הרינדורר שמצייר את הפריט מעל התחנה |
| `block/entity/GemPolishingStationBlockEntity.java` | סנכרון שינויים ללקוח + `getRenderStack()` |
| `event/ModEventBusClientEvents.java` | רישום ה-BER דרך `RegisterRenderers` |

---

## הרינדורר — `GemPolishingBlockEntityRenderer.java`

<div dir="ltr">

```java
public class GemPolishingBlockEntityRenderer implements BlockEntityRenderer<GemPolishingStationBlockEntity> {
    public GemPolishingBlockEntityRenderer(BlockEntityRendererProvider.Context context) { }

    @Override
    public void render(GemPolishingStationBlockEntity pBlockEntity, float pPartialTick, PoseStack pPoseStack,
                       MultiBufferSource pBuffer, int pPackedLight, int pPackedOverlay) {
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        ItemStack itemStack = pBlockEntity.getRenderStack();

        pPoseStack.pushPose();
        pPoseStack.translate(0.5f, 0.75f, 0.5f);
        pPoseStack.scale(0.35f, 0.35f, 0.35f);
        pPoseStack.mulPose(Axis.XP.rotationDegrees(270));

        itemRenderer.renderStatic(itemStack, ItemDisplayContext.FIXED, getLightLevel(pBlockEntity.getLevel(), pBlockEntity.getBlockPos()),
                OverlayTexture.NO_OVERLAY, pPoseStack, pBuffer, pBlockEntity.getLevel(), 1);
        pPoseStack.popPose();
    }

    private int getLightLevel(Level level, BlockPos pos) {
        int bLight = level.getBrightness(LightLayer.BLOCK, pos);
        int sLight = level.getBrightness(LightLayer.SKY, pos);
        return LightTexture.pack(bLight, sLight);
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `implements BlockEntityRenderer<...>` | רינדורר ל-Block Entity ספציפי |
| `getRenderStack()` | הפריט שנצייר (פלט אם יש, אחרת קלט) |
| `pushPose` / `translate(0.5,0.75,0.5)` | מרכז את הפריט באמצע הבלוק ומעט למעלה |
| `scale(0.35)` | מקטין את הפריט כך שיראה "מונח" על המכונה |
| `Axis.XP.rotationDegrees(270)` | מסובב את הפריט שינוח נכון |
| `renderStatic(...)` | מצייר את המודל של הפריט סטטית |
| `getLightLevel` | חישוב תאורה (בלוק + שמיים) כדי שהפריט יואר נכון |

---

## סנכרון ללקוח — ב-`GemPolishingStationBlockEntity`

כדי שהרינדורר יראה עדכונים, הוספנו `onContentsChanged` ששולח עדכון ללקוח כשהמלאי משתנה:
<div dir="ltr">

```java
private final ItemStackHandler itemHandler = new ItemStackHandler(2) {
    @Override
    protected void onContentsChanged(int slot) {
        setChanged();
        if(!level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }
};

public ItemStack getRenderStack() {
    if(itemHandler.getStackInSlot(OUTPUT_SLOT).isEmpty()) {
        return itemHandler.getStackInSlot(INPUT_SLOT);
    } else {
        return itemHandler.getStackInSlot(OUTPUT_SLOT);
    }
}

@Nullable
@Override
public Packet<ClientGamePacketListener> getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
}

@Override
public CompoundTag getUpdateTag() {
    return saveWithoutMetadata();
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `onContentsChanged` | נקראת בכל שינוי במלאי — שולחת עדכון ללקוח |
| `getRenderStack()` | בוחרת מה להציג: פלט אם יש, אחרת קלט |
| `getUpdatePacket()` | חבילת רשת ששולחת את נתוני ה-BE ללקוח |
| `getUpdateTag()` | הנתונים שנשלחים ללקוח בעדכון |

---

## רישום ה-BER

ב-`ModEventBusClientEvents` נוספה פעולה שרשמה את הרינדורר:
<div dir="ltr">

```java
@SubscribeEvent
public static void registerBER(EntityRenderersEvent.RegisterRenderers event) {
    event.registerBlockEntityRenderer(ModBlockEntities.GEM_POLISHING_BE.get(), GemPolishingBlockEntityRenderer::new);
}
```

</div>
## מושגי מפתח

| מושג | הסבר |
|---|---|
| `BlockEntityRenderer` | מחלקה שמצייר תוכן דינמי עבור Block Entity |
| `ItemRenderer.renderStatic` | מציירת פריט (מודל) בתוך קוד רינדור מותאם |
| `ClientboundBlockEntityDataPacket` | חבילת רשת שמסנכרנת נתוני BE ללקוח |

<div dir="ltr">

⬅️ [שלב 32](Step-32-JEI-Compat) · ➡️ [שלב 34](Step-34-Wood)

</div>

</div>
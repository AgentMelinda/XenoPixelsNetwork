<div dir="rtl">

# 🔧 שלב 36 — סירות (Boats)

בהמשך לסט העץ, בשלב הזה אנחנו מוסיפים **סירה וסירת-מזוודה (Chest Boat)** מעץ האורן שלנו. זה דורש יצור (Entity) לסירה, פריט ש"משליך" אותה למים, רינדורר מותאם שבוחר את הטקסטורה הנכונה לפי סוג העץ, ורישום שכבות מודל (Model Layers) לסירה ולסירת-המזוודה.

## מה השתנה לעומת השלב הקודם

| קובץ | מה נוסף |
|---|---|
| `entity/ModEntities.java` | רישום `MOD_BOAT` ו-`MOD_CHEST_BOAT` |
| `entity/custom/ModBoatEntity.java` + `ModChestBoatEntity.java` | הישויות של הסירה |
| `entity/client/ModBoatRenderer.java` + `ModModelLayers.java` | רינדורר ושכבות מודל |
| `item/custom/ModBoatItem.java` | הפריט שיוצר את הסירה בלחיצה |
| `item/ModsItems.java` + `ModCreativeModTabs.java` | רישום פריטי הסירה |
| `TutorialMod.java` + `ModEventBusClientEvents.java` | רישום רינדוררים ושכבות |

> המודלים, ה-lang והמרקמים (`textures/entity/boat/pine.png`, `chest_boat/pine.png`) נוצרים אוטומטית ע"י ה-Data Generator.

---

## ישות הסירה — `ModBoatEntity.java` (תמצית)

<div dir="ltr">

```java
public class ModBoatEntity extends Boat {
    private static final EntityDataAccessor<Integer> DATA_ID_TYPE = SynchedEntityData.defineId(Boat.class, EntityDataSerializers.INT);

    public ModBoatEntity(EntityType<? extends Boat> pEntityType, Level pLevel) { super(pEntityType, pLevel); }

    public ModBoatEntity(Level level, double pX, double pY, double pZ) {
        this(ModEntities.MOD_BOAT.get(), level);
        this.setPos(pX, pY, pZ);
    }

    @Override
    public Item getDropItem() {
        return switch (getModVariant()) { case PINE -> ModsItems.PINE_BOAT.get(); };
    }

    public void setVariant(Type pVariant) { this.entityData.set(DATA_ID_TYPE, pVariant.ordinal()); }
    public Type getModVariant() { return Type.byId(this.entityData.get(DATA_ID_TYPE)); }

    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ID_TYPE, Type.PINE.ordinal());
    }

    public static enum Type implements StringRepresentable {
        PINE(ModBlocks.PINE_PLANKS.get(), "pine");
        private final String name;
        private final Block planks;
        public static final StringRepresentable.EnumCodec<ModBoatEntity.Type> CODEC =
                StringRepresentable.fromEnum(ModBoatEntity.Type::values);
        private static final IntFunction<ModBoatEntity.Type> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        // getSerializedName / getName / getPlanks / byId / byName ...
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `extends Boat` | הסירה שלנו היא סירה רגילה עם סוג עץ משלנו |
| `DATA_ID_TYPE` (Int) | מסנכרן איזה סוג עץ (PINE) לכל הלקוחות |
| `getDropItem()` | כשהסירה נהרסת — נופלת הסירה מעץ האורן |
| `enum Type` | רשימת סוגי הסירות (כרגע רק PINE) |

`ModChestBoatEntity` זהה רק שיורש מ-`ChestBoat` ומשתמש באותו `enum Type`.

---

## פריט הסירה — `ModBoatItem.java` (תמצית)

<div dir="ltr">

```java
public class ModBoatItem extends Item {
    private final ModBoatEntity.Type type;
    private final boolean hasChest;

    public ModBoatItem(boolean pHasChest, ModBoatEntity.Type pType, Item.Properties pProperties) {
        super(pProperties);
        this.hasChest = pHasChest;
        this.type = pType;
    }

    public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pHand) {
        ItemStack itemstack = pPlayer.getItemInHand(pHand);
        HitResult hitresult = getPlayerPOVHitResult(pLevel, pPlayer, ClipContext.Fluid.ANY);
        if (hitresult.getType() == HitResult.Type.BLOCK) {
            Boat boat = this.getBoat(pLevel, hitresult);
            if(boat instanceof ModChestBoatEntity chestBoat) { chestBoat.setVariant(this.type); }
            else if(boat instanceof ModBoatEntity) { ((ModBoatEntity)boat).setVariant(this.type); }
            boat.setYRot(pPlayer.getYRot());
            if (!pLevel.noCollision(boat, boat.getBoundingBox())) { return InteractionResultHolder.fail(itemstack); }
            if (!pLevel.isClientSide) {
                pLevel.addFreshEntity(boat);
                if (!pPlayer.getAbilities().instabuild) { itemstack.shrink(1); }
            }
            return InteractionResultHolder.sidedSuccess(itemstack, pLevel.isClientSide());
        }
        return InteractionResultHolder.pass(itemstack);
    }

    private Boat getBoat(Level pLevel, HitResult pResult) {
        return (Boat)(this.hasChest ? new ModChestBoatEntity(pLevel, pResult.getLocation().x, pResult.getLocation().y, pResult.getLocation().z)
                : new ModBoatEntity(pLevel, pResult.getLocation().x, pResult.getLocation().y, pResult.getLocation().z));
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `use(...)` | נקראת בלחיצה — יורה את הסירה למטרה שנבחרה |
| `getPlayerPOVHitResult` | חישוב נקודת הפגיעה מול מבט השחקן |
| `setVariant(type)` | קובע שהסירה משתמשת בסוג העץ הנכון |
| `addFreshEntity(boat)` | מציב את הסירה בעולם (בצד השרת) |

---

## הרינדורר ושכבות המודל

`ModBoatRenderer` יורש מ-`BoatRenderer` ובונה מפה מ-Type ל-(טקסטורה, מודל). ב-`ModModelLayers` נוספו `PINE_BOAT_LAYER` ו-`PINE_CHEST_BOAT_LAYER`, וב-`ModEventBusClientEvents` נרשמו שכבות אלו דרך `BoatModel::createBodyModel` / `ChestBoatModel::createBodyModel`. ב-`TutorialMod` נרשמו הרינדוררים:
<div dir="ltr">

```java
EntityRenderers.register(ModEntities.MOD_BOAT.get(), pContext -> new ModBoatRenderer(pContext, false));
EntityRenderers.register(ModEntities.MOD_CHEST_BOAT.get(), pContext -> new ModBoatRenderer(pContext, true));
```

</div>
## מושגי מפתח

| מושג | הסבר |
|---|---|
| `Boat` / `ChestBoat` | ישויות סירה מובניות של מייןקראפט |
| `ModBoatItem` | פריט ש"משליך" סירה למים בלחיצה |
| `ModelLayerLocation` | שכבת מודל שנדרשת לצייר סירה נכון |

<div dir="ltr">

⬅️ [שלב 35](Step-35-Signs) · ➡️ [שלב 37](Step-37-Throwable-Projectile)

</div>

</div>
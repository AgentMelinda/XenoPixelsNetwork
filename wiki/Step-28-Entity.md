<div dir="rtl">

# 🔧 שלב 28 — יצור (Entity)

בשלב הזה אנחנו מוסיפים למוד שלנו את ה**יצור הראשון** — קרנף (Rhino) שנוכל לראות בעולם, להתרבות איתו, שהוא תוקף ואפשר לייצר לו ביצת spawn. זהו השלב הראשון מתוך סדרת השלבים שעוסקת ב-Entities (יצורים) וב-Block Entities. עד עכשיו עסקנו בבלוקים ופריטים "דוממים"; עכשיו אנחנו נכנסים לעולם של אובייקטים שחיים, זזים ומגיבים לשחקן.

חשוב לזכור: יצור ב-Minecraft מורכב משלוש רמות עיקריות — הלוגיקה (המחלקה שמגדירה התנהגות), המודל (איך הוא נראה בתלת-ממד), והרינדור (המחלקה שמחברת ביניהם ומצייר אותו על המסך).

## מה השתנה לעומת השלב הקודם

| קובץ | מה נוסף |
|---|---|
| `entity/ModEntities.java` | רישום ה-Entity החדש (RHINO) |
| `entity/custom/RhinoEntity.java` | הלוגיקה של הקרנף — תכונות, מטרות AI, צלילים |
| `entity/client/RhinoModel.java` | מודל התלת-ממד של הקרנף |
| `entity/client/RhinoRenderer.java` | הרינדורר שמצייר את הקרנף |
| `entity/client/ModModelLayers.java` | הגדרת "שכבת המודל" (Model Layer) |
| `entity/animations/ModAnimationDefinitions.java` | הגדרות אנימציה (עמידה, הליכה) |
| `event/ModEventBusEvents.java` | רישום תכונות (Attributes) היצור |
| `event/ModEventBusClientEvents.java` | רישום שכבת המודל בצד הלקוח |
| `item/ModsItems.java` + `ModCreativeModTabs.java` | ביצת ה-Spawn של הקרנף |
| `TutorialMod.java` | חיבור ה-Entities ורישום הרינדורר |

---

## רישום היצור — `entity/ModEntities.java`

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.entity;

import net.bullettrain.tutorialmod.TutorialMod;
import net.bullettrain.tutorialmod.entity.custom.RhinoEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TutorialMod.MOD_ID);

    public static final RegistryObject<EntityType<RhinoEntity>> RHINO =
            ENTITY_TYPES.register("rhino", () -> EntityType.Builder.of(RhinoEntity::new, MobCategory.CREATURE)
                    .sized(2.5f, 2.5f).build("rhino"));


    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `DeferredRegister<EntityType<?>> ENTITY_TYPES` | רשם דחוי לסוג הרישום `ENTITY_TYPES` — כל היצורים של המוד שלנו יירשמו כאן |
| `ENTITY_TYPES.register("rhino", ...)` | רושם יצור חדש עם המזהה `rhino` |
| `EntityType.Builder.of(RhinoEntity::new, MobCategory.CREATURE)` | בונה את ה-Entity: הפעולה שיוצרת אותו + הקטגוריה (CREATURE = חיה שלא תוקפת כברירת מחדל) |
| `.sized(2.5f, 2.5f)` | גודל תיבות ההתנגשות (רוחב × גובה) בשורשים |
| `.build("rhino")` | סוגר את הבנייה ויוצר את `EntityType` |
| `register(IEventBus)` | מקפיץ את הרשם לאוטובוס האירועים של Forge |

---

## הלוגיקה — `entity/custom/RhinoEntity.java` (תמצית)

<div dir="ltr">

```java
public class RhinoEntity extends Animal {
    public RhinoEntity(EntityType<? extends Animal> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    public final AnimationState idleAnimationState = new AnimationState();
    private int idleAnimationTimeout = 0;

    @Override
    public void tick() {
        super.tick();
        if(this.level().isClientSide()) {
            setupAnimationStates();
        }
    }

    private void setupAnimationStates() {
        if(this.idleAnimationTimeout <= 0) {
            this.idleAnimationTimeout = this.random.nextInt(40) + 80;
            this.idleAnimationState.start(this.tickCount);
        } else {
            --this.idleAnimationTimeout;
        }
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new BreedGoal(this, 1.15D));
        this.goalSelector.addGoal(2, new TemptGoal(this, 1.2D, Ingredient.of(Items.COOKED_BEEF), false));
        this.goalSelector.addGoal(3, new FollowParentGoal(this, 1.1D));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 1.1D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 3f));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20D)
                .add(Attributes.FOLLOW_RANGE, 24D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ARMOR_TOUGHNESS, 0.1f)
                .add(Attributes.ATTACK_KNOCKBACK, 0.5f)
                .add(Attributes.ATTACK_DAMAGE, 2f);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel pLevel, AgeableMob pOtherParent) {
        return ModEntities.RHINO.get().create(pLevel);
    }

    @Override
    public boolean isFood(ItemStack pStack) {
        return pStack.is(Items.COOKED_BEEF);
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `extends Animal` | הקרנף הוא "חיה" — יורש מ-`Animal` ומקבל התנהגויות כמו רבייה והאכלה |
| `AnimationState idleAnimationState` | מצב אנימציה שמשמש את המודל להצגת אנימציית עמידה מחזורית |
| `tick()` | נקרא כל פריים; בצד הלקוח אנחנו מעדכנים את מצבי האנימציה |
| `registerGoals()` | מגדיר סדרי עדיפויות ל-AI (0 = הכי דחוף, 6 = הכי נמוך) |
| `FloatGoal` / `BreedGoal` / `TemptGoal` | מטרות: שחייה, רבייה, הליכה אחרי אוכל (בשר מבושל) |
| `createAttributes()` | קובע תכונות: חיים, מהירות, נזק התקפה וכו' |
| `getBreedOffspring` | כששני קרנפים מתרבים — נוצר קרנף חדש |
| `isFood(Items.COOKED_BEEF)` | הבשר המבושל הוא האוכל שלו |

---

## המודל והרינדורר

`RhinoModel` בונה את צורת הקרנף מתיבות (`CubeListBuilder`) בצורה היררכית (rhino → body → torso → head → ...). ה-`RhinoRenderer` יורש מ-`MobRenderer` ומחבר בין המודל לטקסטורה `textures/entity/rhino.png`, ומקטין יצורים תינוקיים (`isBaby()` → scale 0.5).

`ModModelLayers` מגדיר `ModelLayerLocation` אחד (`rhino_layer`), ו-`ModEventBusClientEvents` רושם אותו דרך `RegisterLayerDefinitions`. ב-`TutorialMod` אנחנו רושמים את הרינדורר: `EntityRenderers.register(ModEntities.RHINO.get(), RhinoRenderer::new)`.

## מושגי מפתח

| מושג | הסבר |
|---|---|
| `EntityType` | "תבנית" של יצור — מגדירה איך יוצרים אותו ובאיזו קטגוריה |
| `MobCategory` | קטגוריית יצור (CREATURE, MONSTER, MISC...) — משפיעה על ספאון |
| `AnimationState` | מצב אנימציה שמתנפנף בין המודל לישות |
| `Goal` / `goalSelector` | מערכת ה-AI: כל Goal הוא התנהגות, וה-selector מחליט מה לעשות |
| `ModelLayerLocation` | מזהה שכבת מודל שמאפשר לרשום/לאפות מודל לפי דרישה |

<div dir="ltr">

⬅️ [שלב 27](Step-27-No-Update) · ➡️ [שלב 29](Step-29-Attack-Animation)

</div>

</div>
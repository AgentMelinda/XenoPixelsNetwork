<div dir="rtl">

# 🔧 שלב 31 — סוגי מתכונים (Recipe Types)

בשלב הקודם תחנת ליטוש האבנים ייצרה ספציפית ספיר מהחומר הגולמי. עכשיו אנחנו הופכים את זה ל**מערכת מתכונים אמיתית**: במקום קוד קשיח, אנחנו מגדירים **Recipe Type** ו-**Recipe Serializer** משלנו, כך שהמתכונים יוגדרו בקבצי JSON (כמו מתכוני יצירה רגילים). זה מאפשר להוסיף מתכונים חדשים בלי לשנות קוד בכלל.

## מה השתנה לעומת השלב הקודם

| קובץ | מה נוסף |
|---|---|
| `recipe/GemPolishingRecipe.java` | מחלקת המתכון + ה-Type + ה-Serializer |
| `recipe/ModRecipes.java` | רישום ה-Serializer |
| `block/entity/GemPolishingStationBlockEntity.java` | ה-Block Entity משתמש עכשיו במתכון במקום בקוד קשיח |
| `data/tutorialmod/recipes/*.json` | שני מתכונים: ספיר ויהלום |
| `TutorialMod.java` | רישום `ModRecipes` |

---

## הגדרת המתכון — `recipe/GemPolishingRecipe.java`

<div dir="ltr">

```java
public class GemPolishingRecipe implements Recipe<SimpleContainer> {
    private final NonNullList<Ingredient> inputItems;
    private final ItemStack output;
    private final ResourceLocation id;

    public GemPolishingRecipe(NonNullList<Ingredient> inputItems, ItemStack output, ResourceLocation id) {
        this.inputItems = inputItems;
        this.output = output;
        this.id = id;
    }

    @Override
    public boolean matches(SimpleContainer pContainer, Level pLevel) {
        if(pLevel.isClientSide()) { return false; }
        return inputItems.get(0).test(pContainer.getItem(0));
    }

    @Override
    public ItemStack getResultItem(RegistryAccess pRegistryAccess) {
        return output.copy();
    }

    @Override
    public ResourceLocation getId() { return id; }

    @Override
    public RecipeSerializer<?> getSerializer() { return Serializer.INSTANCE; }

    @Override
    public RecipeType<?> getType() { return Type.INSTANCE; }

    public static class Type implements RecipeType<GemPolishingRecipe> {
        public static final Type INSTANCE = new Type();
        public static final String ID = "gem_polishing";
    }

    public static class Serializer implements RecipeSerializer<GemPolishingRecipe> {
        public static final Serializer INSTANCE = new Serializer();
        public static final ResourceLocation ID = new ResourceLocation(TutorialMod.MOD_ID, "gem_polishing");

        @Override
        public GemPolishingRecipe fromJson(ResourceLocation pRecipeId, JsonObject pSerializedRecipe) {
            ItemStack output = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(pSerializedRecipe, "output"));
            JsonArray ingredients = GsonHelper.getAsJsonArray(pSerializedRecipe, "ingredients");
            NonNullList<Ingredient> inputs = NonNullList.withSize(1, Ingredient.EMPTY);
            for(int i = 0; i < inputs.size(); i++) {
                inputs.set(i, Ingredient.fromJson(ingredients.get(i)));
            }
            return new GemPolishingRecipe(inputs, output, pRecipeId);
        }

        @Override
        public @Nullable GemPolishingRecipe fromNetwork(ResourceLocation pRecipeId, FriendlyByteBuf pBuffer) {
            NonNullList<Ingredient> inputs = NonNullList.withSize(pBuffer.readInt(), Ingredient.EMPTY);
            for(int i = 0; i < inputs.size(); i++) { inputs.set(i, Ingredient.fromNetwork(pBuffer)); }
            ItemStack output = pBuffer.readItem();
            return new GemPolishingRecipe(inputs, output, pRecipeId);
        }

        @Override
        public void toNetwork(FriendlyByteBuf pBuffer, GemPolishingRecipe pRecipe) {
            pBuffer.writeInt(pRecipe.inputItems.size());
            for (Ingredient ingredient : pRecipe.getIngredients()) { ingredient.toNetwork(pBuffer); }
            pBuffer.writeItemStack(pRecipe.getResultItem(null), false);
        }
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `implements Recipe<SimpleContainer>` | המתכון עובד על מיכל פשוט (המלאי של התחנה) |
| `matches(...)` | בודק אם הפריט בסlot 0 תואם למרכיב המתכון |
| `Type.INSTANCE` | המזהה הלוגי של סוג המתכון (`gem_polishing`) |
| `Serializer.fromJson` | קורא מתכון מקובץ JSON (שדות `ingredients` + `output`) |
| `fromNetwork` / `toNetwork` | סנכרון המתכון ברשת (שרת↔לקוח) דרך `FriendlyByteBuf` |

---

## רישום ה-Serializer — `ModRecipes.java`

<div dir="ltr">

```java
public class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, TutorialMod.MOD_ID);

    public static final RegistryObject<RecipeSerializer<GemPolishingRecipe>> GEM_POLISHING_SERIALIZER =
            SERIALIZERS.register("gem_polishing", () -> GemPolishingRecipe.Serializer.INSTANCE);

    public static void register(IEventBus eventBus) {
        SERIALIZERS.register(eventBus);
    }
}
```

</div>
---

## קבצי מתכון — `data/tutorialmod/recipes/`

`diamond_from_gem_polishing.json` (פחם ← 7 יהלומים) ו-`sapphire_from_gem_polishing.json` (חומר גולמי ← 3 ספירים). שניהם משתמשים ב-`"type": "tutorialmod:gem_polishing"`:

<div dir="ltr">

```json
{
  "type": "tutorialmod:gem_polishing",
  "ingredients": [
    { "item": "tutorialmod:raw_sapphire" }
  ],
  "output": { "count": 3, "item": "tutorialmod:sapphire" }
}
```

</div>
## שימוש ב-Block Entity

ב-`GemPolishingStationBlockEntity` החלפנו את הקוד הקשיח בשאילתת מתכון אמיתית:
<div dir="ltr">

```java
Optional<GemPolishingRecipe> recipe = getCurrentRecipe();
ItemStack result = recipe.get().getResultItem(getLevel().registryAccess());
```

</div>
ופונקציית `getCurrentRecipe()` משתמשת ב-`level.getRecipeManager().getRecipeFor(GemPolishingRecipe.Type.INSTANCE, inventory, level)`.

## מושגי מפתח

| מושג | הסבר |
|---|---|
| `Recipe<T>` | ממשק מתכון — `matches`, `getResultItem`, `getType` |
| `RecipeType` | "סוג מתכון" לוגי (למשל `gem_polishing`) |
| `RecipeSerializer` | איך קוראים את המתכון מ-JSON ומרשת |
| `RecipeManager` | מנהל המתכונים של השרת — שואלים אותו לפי Type |

<div dir="ltr">

⬅️ [שלב 30](Step-30-Block-Entity) · ➡️ [שלב 32](Step-32-JEI-Compat)

</div>

</div>
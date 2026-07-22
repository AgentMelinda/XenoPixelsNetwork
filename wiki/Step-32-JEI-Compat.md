<div dir="rtl">

# 🔧 שלב 32 — תאימות JEI

בשלב הקודם הגדרנו מערכת מתכונים משלנו. הבעיה: השחקן לא יכול לראות את המתכונים האלה בתוך **JEI** (Just Enough Items — מוד שמציג מתכונים). בשלב הזה אנחנו מוסיפים **תאימות ל-JEI**: מגדירים קטגוריית מתכון מותאמת שמציגה את הקלט והפלט של תחנת ליטוש האבנים, ומרשמים את המתכונים לתוך JEI.

## מה השתנה לעומת השלב הקודם

| קובץ | מה נוסף |
|---|---|
| `build.gradle` + `gradle.properties` | הוספת תלות JEI (maven + גרסה) |
| `compat/GemPolishingCategory.java` | קטגוריית מתכון מותאמת ל-JEI |
| `compat/JEITutorialModPlugin.java` | ה-plugin שמרשם קטגוריות ומתכונים ל-JEI |
| `recipe/GemPolishingRecipe.java` | נוספה `getIngredients()` הנדרשת ל-JEI |

---

## תלויות ה-build

ב-`gradle.properties` נוספה גרסת JEI:
<div dir="ltr">

```properties
jei_version=15.2.0.27
```

</div>
וב-`build.gradle` — ה-maven של JEI והתלויות עצמן (compileOnly מול ה-API, הרצה מלאה מול ה-jar):
<div dir="ltr">

```groovy
maven {
    name = "Jared's maven"
    url = "https://maven.blamejared.com/"
}
compileOnly(fg.deobf("mezz.jei:jei-${minecraft_version}-common-api:${jei_version}"))
compileOnly(fg.deobf("mezz.jei:jei-${minecraft_version}-forge-api:${jei_version}"))
runtimeOnly(fg.deobf("mezz.jei:jei-${minecraft_version}-forge:${jei_version}"))
```

</div>
---

## קטגוריית המתכון — `compat/GemPolishingCategory.java`

<div dir="ltr">

```java
@SuppressWarnings("removal")
public class GemPolishingCategory implements IRecipeCategory<GemPolishingRecipe> {
    public static final ResourceLocation UID = new ResourceLocation(TutorialMod.MOD_ID, "gem_polishing");
    public static final ResourceLocation TEXTURE = new ResourceLocation(TutorialMod.MOD_ID,
            "textures/gui/gem_polishing_station_gui.png");
    public static final RecipeType<GemPolishingRecipe> GEM_POLISHING_TYPE =
            new RecipeType<>(UID, GemPolishingRecipe.class);

    private final IDrawable background;
    private final IDrawable icon;

    public GemPolishingCategory(IGuiHelper helper) {
        this.background = helper.createDrawable(TEXTURE, 0, 0, 176, 85);
        this.icon = helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ModBlocks.GEM_POLISHING_STATION.get()));
    }

    @Override
    public RecipeType<GemPolishingRecipe> getRecipeType() { return GEM_POLISHING_TYPE; }

    @Override
    public Component getTitle() {
        return Component.translatable("block.tutorialmod.gem_polishing_station");
    }

    @Override
    public IDrawable getBackground() { return this.background; }
    @Override
    public IDrawable getIcon() { return this.icon; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, GemPolishingRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 80, 11).addIngredients(recipe.getIngredients().get(0));
        builder.addSlot(RecipeIngredientRole.OUTPUT, 80, 59).addItemStack(recipe.getResultItem(null));
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `implements IRecipeCategory<GemPolishingRecipe>` | מגדיר איך JEI מציג את המתכון הזה |
| `RecipeType<>(UID, ...)` | מקשר בין הקטגוריה לסוג המתכון שלנו |
| `createDrawable(TEXTURE, ...)` | רקע הקטגוריה = טקסטורת ה-GUI של התחנה |
| `getTitle()` | הכותרת שמופיעה למעלה (מהקובץ lang) |
| `setRecipe(...)` | מציב את סlot הקלט (80,11) והפלט (80,59) — אותם מיקומים כמו במסך |

---

## ה-plugin — `compat/JEITutorialModPlugin.java`

<div dir="ltr">

```java
@JeiPlugin
public class JEITutorialModPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(TutorialMod.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new GemPolishingCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        RecipeManager recipeManager = Minecraft.getInstance().level.getRecipeManager();
        List<GemPolishingRecipe> polishingRecipes = recipeManager.getAllRecipesFor(GemPolishingRecipe.Type.INSTANCE);
        registration.addRecipes(GemPolishingCategory.GEM_POLISHING_TYPE, polishingRecipes);
    }

    @Override
    public void registerGuiHandlers(IRecipeHandlerRegistration registration) {
        registration.addRecipeClickArea(GemPolishingStationScreen.class, 60, 30, 20, 30,
                GemPolishingCategory.GEM_POLISHING_TYPE);
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `@JeiPlugin` | מעיד על JEI שזה ה-plugin של המוד |
| `registerCategories` | מוסיף את קטגוריית "תחנת ליטוש האבנים" |
| `registerRecipes` | שולף את כל המתכונים מסוגנו ומרשם אותם ל-JEI |
| `addRecipeClickArea` | לחיצה על אזור במסך התחנה פותחת את המתכון ב-JEI |

## מושגי מפתח

| מושג | הסבר |
|---|---|
| JEI | Just Enough Items — מוד שמציג מתכונים ופריטים |
| `IRecipeCategory` | איך קטגוריית מתכון אחת מוצגת ב-JEI |
| `@JeiPlugin` | אנוטציה שמרשמת את המוד ל-JEI |
| `compileOnly` vs `runtimeOnly` | נגד ה-API בקומפילציה, ה-jar המלא בריצה |

<div dir="ltr">

⬅️ [שלב 31](Step-31-Recipe-Types) · ➡️ [שלב 33](Step-33-Block-Entity-Renderer)

</div>

</div>
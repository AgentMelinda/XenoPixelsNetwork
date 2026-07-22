<div dir="rtl">

# 🛠️ מתקדם 01 — שינויי Build + Valkyrien Skies

> עמוד זה חורג מהמדריך המקורי ומתעד את שילוב **Valkyrien Skies (VS2)** ב-fork האישי.
> הוא מסביר **שלב אחר שלב** את כל השינויים בקובצי ה-build: מה שינינו, למה ובאיזה סדר.

---

## שלב 1 — שינוי המיפויים מ-parchment ל-official

**קובץ:** `gradle.properties`

**לפני:**
```properties
mapping_channel=parchment
mapping_version=2023.09.03-1.20.1
```

**אחרי:**
```properties
mapping_channel=official
mapping_version=1.20.1
```

**הסבר:**
- **parchment** = מיפויים לא-רשמיים של ParchmentMC עם שמות פרמטרים נוחים יותר לקריאה
- **official** = המיפויים הרשמיים של Mojang, שמות ה-SRG עם שמות פרמטרים מ-Mojang ישירות
- הסיבה לשינוי: Valkyrien Skies בנה את ה-refmap שלו עם `official` mappings — כאשר הפרויקט שלנו השתמש ב-parchment, שמות המתודות (SRG) לא התאימו ו-Mixin של VS קרס

**בנוסף ב-build.gradle:**
```groovy
// הפלאגין של ParchmentMC הוסר/הושבת כי אינו נדרש יותר
// id 'org.parchmentmc.librarian.forgegradle' version '1.+'
```

---

## שלב 2 — עדכון גרסת Forge

**קובץ:** `gradle.properties`

**לפני:** `forge_version=47.4.0` → **אחרי:** `forge_version=47.4.10`

**הסבר:**
- 47.4.10 היא גרסה חדשה ויציבה יותר של Forge לגרסת Minecraft 1.20.1
- גרסת Forge חייבת להתאים לגרסת VS2 שנוסיף — יש לבדוק תאימות בין הגרסאות

---

## שלב 3 — הוספת גרסאות Valkyrien Skies ל-gradle.properties

```properties
vs2_mc_version=120        # גרסת Minecraft ב-format מקוצר (1.20.X → 120)
vs2_version=2.4.10        # גרסת VS2 עבור Minecraft 1.20
vs_core_version=1.1.0+1d4a7373e9  # גרסת VS Core (ספרייה הפנימית של VS)
```

**הסבר:**
- `vs2_mc_version` — VS משתמש ב-artifact שמותאם לגרסת MC ספציפית (`valkyrienskies-120-forge`)
- `vs2_version` — גרסת ה-jar הראשי של VS2
- `vs_core_version` — VS מחולק ל-`vs-core` (לוגיקה) ו-`valkyrienskies-forge` (אינטגרציה עם Forge) — שניהם נדרשים

---

## שלב 4 — הוספת Maven Repository של Valkyrien Skies

**קובץ:** `build.gradle` — בלוק `repositories`

```groovy
maven {
    name = 'Valkyrien Skies Internal'
    url = project.vs_maven_url ?: 'https://maven.valkyrienskies.org'
    if (project.vs_maven_username && project.vs_maven_password) {
        credentials {
            username = project.vs_maven_username
            password = project.vs_maven_password
        }
    }
}
```

**הסבר:**
- Valkyrien Skies **אינו** ב-Maven Central — יש לו maven משלו בכתובת `https://maven.valkyrienskies.org`
- ה-`?: 'https://...'` = אם `vs_maven_url` ריק, יש fallback לכתובת ברירת המחדל
- ה-`if (credentials...)` = אפשרות לחבר maven פרטי (לא נדרש בפיתוח רגיל)

---

## שלב 5 — הוספת Dependencies של Valkyrien Skies

**קובץ:** `build.gradle` — בלוק `dependencies`

```groovy
// region Valkyrien Skies
implementation("org.valkyrienskies.core:api:${vs_core_version}") { transitive = false
    exclude group: 'org.joml', module: '' }
implementation("org.valkyrienskies.core:internal:${vs_core_version}") { transitive = false
    exclude group: 'org.joml', module: '' }
implementation("org.valkyrienskies.core:util:${vs_core_version}") { transitive = false
    exclude group: 'org.joml', module: '' }
implementation("org.valkyrienskies.core:impl:${vs_core_version}") { transitive = false
    exclude group: 'org.joml', module: '' }

// VS2 עצמו — נטען דרך fg.deobf() כדי ש-ForgeGradle ימפה אותו
implementation fg.deobf("org.valkyrienskies:valkyrienskies-120-forge:${vs2_version}") {
    transitive = false
    exclude group: 'org.valkyrienskies.core', module: '' }
// endregion

// region VS deps
implementation "com.fasterxml.jackson.core:jackson-annotations:2.13.3"
compileOnly("org.joml:joml:1.10.4")
compileOnly("org.joml:joml-primitives:1.10.0")
// endregion
```

**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `implementation(...)` | מוסיף את ה-jar לקלאספת' של הקומפילציה וריצה |
| `transitive = false` | מונע מ-Gradle להוריד **אוטומטית** את תלויות ה-VS Core — מצהירים עליהן ידנית |
| `exclude group: 'org.joml'` | מונע קונפליקט גרסאות — joml מוגדר נפרד בהמשך |
| `fg.deobf(...)` | **ForgeGradle deobfuscate** — מעביר את jar של VS2 דרך אותו remapping כמו Minecraft |
| `exclude group: 'org.valkyrienskies.core'` | מונע טעינה כפולה — VS Core כבר הוגדר ידנית |
| `compileOnly(joml)` | joml נדרש בקומפילציה בלבד; בריצה VS מספק אותו בעצמו |

---

## שלב 6 — הוספת Kotlin for Forge

```groovy
implementation 'thedarkcolour:kotlinforforge:4.11.0'
```

**הסבר:**
- DragonMineZ כתוב חלקית ב-Kotlin — נדרש `kotlinforforge` כדי שקוד Kotlin יעבוד בסביבת Forge
- גרסה 4.x תואמת ל-Minecraft 1.20.x ול-Forge 47.x
- ה-maven repository של KotlinForForge כבר מוגדר בבלוק `repositories`:
  ```groovy
  maven {
      name = 'Kotlin for Forge'
      url = 'https://thedarkcolour.github.io/KotlinForForge/'
      content { includeGroup 'thedarkcolour' }
  }
  ```

---

## סדר הפעולות הנכון — סיכום

| שלב | קובץ | מה עשינו | למה |
|---|---|---|---|
| 1 | `gradle.properties` | `mapping_channel=official` | תאימות VS Mixin refmap |
| 2 | `build.gradle` | הסרת פלאגין parchment | לא נדרש יותר |
| 3 | `gradle.properties` | `forge_version=47.4.10` | גרסה עדכנית ותואמת VS |
| 4 | `gradle.properties` | הוספת `vs2_version`, `vs_core_version` | הגדרת גרסאות VS |
| 5 | `build.gradle` → `repositories` | הוספת maven של VS | VS לא ב-Maven Central |
| 6 | `build.gradle` → `dependencies` | הוספת VS Core (4 מודולים) | ספרייה הפנימית של VS |
| 7 | `build.gradle` → `dependencies` | `fg.deobf(valkyrienskies-120-forge)` | remapping נכון של VS |
| 8 | `build.gradle` → `dependencies` | jackson, joml, joml-primitives | תלויות של VS |
| 9 | `build.gradle` → `dependencies` | `kotlinforforge:4.11.0` | תמיכת Kotlin עבור DMZ |

## מושגי מפתח

| מושג | הסבר |
|---|---|
| `mapping_channel` | קובע את **מערכת שמות** המתודות — `official` = מ-Mojang, `parchment` = שמות ידידותיים |
| `fg.deobf(...)` | מאפשר ל-Forge לקרוא jar חיצוני ולמפות שמות לאותה מערכת כמו Minecraft |
| `transitive = false` | מונע הורדה אוטומטית של תלויות נסתרות — שליטה ידנית מלאה |
| `exclude group:` | מסיר dependency ספציפי מ-jar שנוסף, מונע קונפליקטי גרסאות |
| `compileOnly` | ה-jar קיים בקומפילציה בלבד — לא נארז ב-output jar הסופי |
| `Mixin refmap` | קובץ JSON שממפה שמות מתודות של Mixin לשמות SRG — חייב באותה שיטת מיפוי |

---

<div dir="ltr">

⬅️ [Home](Home) · ➡️ [Advanced 02 — DragonMineZ](Advanced-02-DragonMineZ)

</div>

</div>

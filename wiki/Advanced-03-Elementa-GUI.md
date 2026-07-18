<div dir="rtl">

# 🖼️ מתקדם 03 — Elementa GUI ב-Forge

> עמוד זה חורג מהמדריך המקורי.
> <a href="https://github.com/SparkUniverse/Elementa" dir="ltr">Elementa</a> היא ספריית GUI דקלרטיבית.
> במקום לחשב ידנית את מיקום כל רכיב בכל frame, מגדירים לרכיב **מה** צריך להיות גודלו
> ומיקומו ביחס להורה שלו; Elementa מחשבת את התוצאה ומציירת את עץ הממשק.

המדריך מתאים לפרויקט הזה: **Minecraft 1.20.1, Forge 47.4.10, Java 17, ForgeGradle 6 ו-ParchmentMC**.
הוא אינו מוסיף את Elementa לפרויקט כברירת מחדל; השלבים למטה הם מתכון מלא ומכוון.

---

## 1. להבין את מבנה הממשק

Elementa בנויה מעץ רכיבים:

| מושג | משמעות |
|---|---|
| `Window` | שורש עץ הרכיבים; אחראי גם להעברת אירועי עכבר ומקלדת |
| `WindowScreen` | `Screen` מוכן של Elementa; יוצר `Window`, מצייר אותו ומעביר אירועי קלט אוטומטית |
| `UIComponent` | מחלקת הבסיס לכל רכיב UI |
| `UIBlock` | מלבן צבוע; שימושי כרקע, panel או כפתור בסיסי |
| `UIText` | טקסט בשורה אחת; הגודל שלו נגזר מהטקסט אם לא מגדירים אילוצים |
| `UIContainer` | רכיב שקוף שמארגן ילדים, בדומה ל-`div` ב-HTML |
| Constraints | אילוצים ל-x/y/width/height/color; כולם יחסיים להורה הישיר |
| Effects | אפקטים כגון `ScissorEffect`, שחותך ציור של ילדים אל גבולות הרכיב |

לכל רכיב יש הורה יחיד ואפס או יותר ילדים. רכיב שאינו ילד של `Window` לא יוצג.
לכן תמיד יוצרים רכיב, מגדירים לו constraints, ואז מחברים אותו לעץ.

---

## 2. להוסיף repositories וגרסאות

ב-`gradle.properties`:
```properties
elementa_version=745
universalcraft_version=505
```

ב-`build.gradle`, בתוך `repositories`:
```groovy
maven {
    name = 'Essential'
    url = 'https://repo.essential.gg/repository/maven-public'
}
```

> שימוש בגרסה מפורשת חשוב: `+` או `latest.release` עלולים לעדכן את Elementa בלי בדיקה ולשבור GUI בגלל שינוי API.

---

## 3. להוסיף את התלויות

```groovy
implementation "gg.essential:elementa:${elementa_version}"
implementation "gg.essential:universalcraft-1.20.1-forge:${universalcraft_version}"
```

`Elementa` עצמה אינה קשורה לגרסת Minecraft; `UniversalCraft` הוא שכבת ההתאמה שמחברת
אותה ל-Forge ול-Minecraft 1.20.1.

> אין להשתמש ב-`jarJar` עבור Elementa בפרויקט Forge. לפי התיעוד הרשמי, ב-Forge יש
> להצליל (shade) ולבצע relocate לשתי הספריות.

---

## 4. למה Forge דורש Shadow ו-relocation

Forge טוען את כל המודים לאותו classpath. אם שני מודים כוללים גרסאות שונות של
`gg.essential.elementa`, אחד מהם עלול לטעון מחלקה מהגרסה של השני ולקבל `NoSuchMethodError`,
`ClassCastException` או קריסה אקראית ב-GUI.

`relocation` משנה את ה-package שמאוחסן בתוך JAR המוד שלנו:
```text
gg.essential.elementa       -> net.bullettrain.tutorialmod.shadow.elementa
gg.essential.universalcraft -> net.bullettrain.tutorialmod.shadow.universalcraft
```

---

## 5. להגדיר Shadow בצורה נכונה

ב-`plugins`:
```groovy
id 'com.github.johnrengelman.shadow' version '8.1.1'
```

לאחר בלוק `dependencies`:
```groovy
configurations {
    elementaShade
    implementation.extendsFrom elementaShade
}

dependencies {
    elementaShade "gg.essential:elementa:${elementa_version}"
    elementaShade "gg.essential:universalcraft-1.20.1-forge:${universalcraft_version}"
}

tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
    archiveClassifier.set('')
    configurations = [project.configurations.elementaShade]
    relocate 'gg.essential.elementa', "${mod_group_id}.shadow.elementa"
    relocate 'gg.essential.universalcraft', "${mod_group_id}.shadow.universalcraft"
}

tasks.named('reobfJar') { dependsOn tasks.named('shadowJar') }
tasks.named('jar') { archiveClassifier.set('slim') }
```

**הסבר השורות החשובות:**
1. `elementaShade` מפרידה את הספריות שצריכות להיכנס ל-JAR מהתלויות הרגילות.
2. `implementation.extendsFrom elementaShade` משאירה את Elementa זמינה לקומפילציה.
3. `shadowJar` מכניס רק את שתי ספריות Elementa/UniversalCraft, לא את Forge/Minecraft.
4. `relocate` חייב לכלול את **שני** ה-packages; הזזה של Elementa בלבד תקרוס כי Elementa מפנה ל-UniversalCraft.
5. `reobfJar` חייב לרוץ אחרי `shadowJar`, אחרת ה-JAR המוצלל לא יעבור reobfuscation.

---

## 6. ליצור מסך Java ראשון

`src/main/java/net/bullettrain/tutorialmod/client/ElementaExampleScreen.java`:

```java
package net.bullettrain.tutorialmod.client;

import gg.essential.elementa.ElementaVersion;
import gg.essential.elementa.WindowScreen;
import gg.essential.elementa.components.UIBlock;
import gg.essential.elementa.components.UIText;
import gg.essential.elementa.constraints.CenterConstraint;
import gg.essential.elementa.constraints.PixelConstraint;
import net.minecraft.network.chat.Component;

import java.awt.Color;

public final class ElementaExampleScreen extends WindowScreen {
    public ElementaExampleScreen() {
        super(ElementaVersion.V2);

        UIBlock panel = new UIBlock(new Color(20, 28, 48, 220));
        panel.setX(new CenterConstraint());
        panel.setY(new CenterConstraint());
        panel.setWidth(new PixelConstraint(220.0F));
        panel.setHeight(new PixelConstraint(120.0F));
        panel.setChildOf(getWindow());

        UIText title = new UIText("TutorialMod + Elementa");
        title.setX(new CenterConstraint());
        title.setY(new PixelConstraint(18.0F));
        title.setChildOf(panel);
    }

    @Override
    public Component getTitle() {
        return Component.literal("TutorialMod");
    }
}
```

---

## 7. הסבר שורה אחר שורה

1. `extends WindowScreen` — כבר מחבר `draw`, הקלקות, גלילה, מקלדת ושחרור מקשים ל-Elementa.
2. `super(ElementaVersion.V2)` — בוחר API V2. שמרו עליו תמיד; ערבוב גרסאות ייצור הבדלי התנהגות.
3. `UIBlock` — panel שקוף למחצה. הערך הרביעי של `Color` הוא alpha (`220` מתוך `255`).
4. `CenterConstraint` — ממקם במרכז ההורה. כאן ההורה הוא ה-`Window`, לכן במרכז המסך.
5. `PixelConstraint` — גודל קבוע בפיקסלים. גמיש יותר: `RelativeConstraint` או `ChildBasedSizeConstraint`.
6. `setChildOf(getWindow())` — מוסיף לעץ. בלי זה לא יצויר כלום.
7. ה-`UIText` מחובר ל-`panel`, ולכן ה-`CenterConstraint` שלו מתייחס למרכז ה-panel.
8. `getTitle()` — הכותרת הסטנדרטית של מסך Minecraft.

---

## 8. לפתוח את המסך בצורה בטוחה בצד הלקוח

אסור לייבא `Minecraft` או `Screen` במחלקת common שניטענת גם בשרת ייעודי. פתח רק בקוד client:
```java
import net.minecraft.client.Minecraft;
Minecraft.getInstance().setScreen(new ElementaExampleScreen());
```
מקומות תקינים: keybind בצד הלקוח, לחיצה על כפתור client, או packet שהשרת שלח וה-client מטפל בו.
אין לקרוא לכך מתוך `commonSetup` או מאזין שרת.

---

## 9. אירועים, לחצנים ואנימציה

כל אירוע מתחיל ב-`Window`. ב-`WindowScreen` ההעברה נעשית עבורך:
```java
panel.onMouseEnterRunnable(() -> {
    // שינוי צבע, התחלת אנימציה או לוגיקה מקומית
});
```
לאנימציה יוצרים `AnimatingConstraints`, בוחרים easing מתוך `Animations`, ומפעילים `animateTo`.
זמני Elementa בשניות, לכן `0.25F` = רבע שנייה. ל-input טקסט השתמש ב-`UITextInput` או `UIMultilineTextInput`.

---

## 10. Constraints שימושיים

| Constraint | שימוש |
|---|---|
| `PixelConstraint(value)` | גודל/היסט קבוע בפיקסלים |
| `CenterConstraint()` | מרכוז בתוך ההורה הישיר |
| `RelativeConstraint(0.5F)` | 50% מגודל ההורה |
| `SiblingConstraint()` | מיקום אחרי רכיב אח/אחות בעץ |
| `ChildBasedSizeConstraint()` | גודל לפי תוכן הילדים |
| `ChildBasedMaxSizeConstraint()` | גודל לפי הילד הגדול ביותר |
| `ImageAspectConstraint()` | שמירת יחס ממדים של תמונה |

הכל יחסי להורה. אם כותרת לא ממורכזת במקום הצפוי, בדוק קודם מי ההורה שלה בעץ.

---

## 11. clipping, תמונות וביצועים

- הוסף `ScissorEffect` ל-container גלילה כשילדים אינם אמורים לצייר מחוץ לגבולות.
- `UIImage` טוען תמונות אסינכרונית. לתמונה מקומית עדיף resource בתוך JAR; ל-URL חיצוני הוסף timeout ו-fallback.
- אל תיצור מחדש את כל עץ ה-UI בכל frame. צור פעם אחת ב-constructor, ועדכן רק מה שהשתנה.
- לפני סגירת מסך, נקה listeners/animation שמחזיקים הפניות למצב משחק גדול.

---

## 12. רשימת בדיקה לפני הפצה

1. `./gradlew build` יוצר JAR ללא classifier ו-`-slim.jar`.
2. בדוק שה-JAR ללא classifier מכיל packages relocated תחת `net/bullettrain/tutorialmod/shadow/`.
3. הפעל את המשחק עם מוד נוסף שמשתמש ב-Elementa, לוודא שאין התנגשות classpath.
4. בדוק client וגם dedicated server: השרת חייב להתחיל בלי לטעון מחלקת GUI.
5. ודא ש-`ElementaVersion.V2` והגרסאות ב-`gradle.properties` תואמות לגרסה שנבדקה.

---

## 13. מקורות רשמיים

<div dir="ltr">

- [Elementa README](https://github.com/SparkUniverse/Elementa)
- [JavaTestGui](https://github.com/SparkUniverse/Elementa/blob/master/example/src/main/kotlin/gg/essential/elementa/example/JavaTestGui.java)
- [רשימת רכיבים](https://github.com/SparkUniverse/Elementa/blob/master/docs/components.md)
- [מדריך מעבר ל-V2](https://github.com/SparkUniverse/Elementa/blob/master/docs/migration.md)

⬅️ [Advanced 02 — DragonMineZ](Advanced-02-DragonMineZ) · ➡️ [Advanced 04 — Mixin Fix](Advanced-04-Mixin-Fix)

</div>

</div>

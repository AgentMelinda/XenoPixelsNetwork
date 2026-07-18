<div dir="rtl">

# 🐉 מתקדם 02 — תמיכת Addon ל-DragonMineZ

> עמוד זה חורג מהמדריך המקורי. התמיכה ממומשת לפי
> <a href="https://github.com/DragonMineZ/dragonminez/wiki/Creating-a-DMZ-Addon" dir="ltr">Creating a DMZ Addon</a>.

---

## מה נוסף

- **`ParchmentMC`** מופעל ב-`gradle.properties` וב-`build.gradle` עם מיפוי `2023.09.03-1.20.1`.
- **`META-INF/mods.toml`** מגדיר את `dragonminez` כתלות **חובה** בגרסה `[2.1.2]`, בסדר טעינה `AFTER` ובשני הצדדים.
- **`build.gradle`** מוסיף את DMZ ואת תלויות הריצה שלו: GeckoLib, TerraBlender ו-Curios.
- **`DmzHooks.java`** נרשם לאוטובוס האירועים של Forge ומאזין ל-`DMZEvent.TPGainEvent`. כרגע הוא רק כותב הודעת `debug`; הוא אינו משנה את כמות ה-TP, ולכן מוסיף נקודת הרחבה בטוחה ללא שינוי בהתנהגות המשחק.

---

## התקנת JAR הפיתוח של DMZ

ל-DragonMineZ אין artifact של API ב-Maven. לכן צריך להוריד את JAR הפיתוח המדויק ולשים אותו מקומית:

1. הורד `dragonminez-2.1.2.jar` מ-<a href="https://modrinth.com/mod/dragonminez/version/t1Qn8aCi" dir="ltr">Modrinth</a>.
2. צור תיקייה `libs` בשורש הפרויקט אם אינה קיימת.
3. העתק אליה את הקובץ כך שהנתיב יהיה `libs/dragonminez-2.1.2.jar`.
4. הרץ `./gradlew runClient`.

> ⚠️ התיקייה `libs/` נמצאת ב-`.gitignore`; אין להעלות את ה-JAR של DMZ למאגר.
> הבנייה נכשלת במפורש עם הודעה ברורה אם הקובץ חסר, במקום להפיק JAR שנראה תקין אך אינו תומך ב-DMZ.

---

## הרחבת ה-Hook

המאזין הקיים נמצא ב-`src/main/java/net/bullettrain/tutorialmod/event/DmzHooks.java`.
לדוגמה, כדי לשנות TP יש להשתמש ב-`event.setTpGain(...)` בתוך `onTrainingPointGain`.
יש לבצע שינוי כזה רק כאשר רוצים שינוי מכניקת משחק מכוון, משום שה-Hook הנוכחי נבחר
במכוון להיות **תצפיתי בלבד**.

---

<div dir="ltr">

⬅️ [Advanced 01 — Build + VS2](Advanced-01-Build-and-VS2) · ➡️ [Advanced 03 — Elementa GUI](Advanced-03-Elementa-GUI)

</div>

</div>

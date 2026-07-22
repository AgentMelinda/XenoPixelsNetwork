<div dir="rtl">

# 🩹 מתקדם 04 — פתרון קריסת Mixin (MixinTransformerError)

> עמוד זה חורג מהמדריך המקורי ומתעד פתרון בעיה אמיתית שהתרחשה ב-fork בעת שילוב Valkyrien Skies.

---

## התופעה

בהרצת `runClient` המשחק קורס עם שגיאה כזו ב-`run/logs/debug.log`:

```
org.spongepowered.asm.mixin.transformer.throwables.MixinTransformerError: An unexpected critical error was encountered
...
Caused by: org.spongepowered.asm.mixin.injection.throwables.InvalidInjectionException:
Critical injection failure: @WrapOperation annotation on useOriginalCrosshairForBlockPlacement
could not find any targets matching 'Lnet/minecraft/client/Minecraft;m_91277_()V'
in net.minecraft.client.Minecraft. Using refmap valkyrienskies-120-common-refmap.json
[... valkyrienskies-common.mixins.json:client.MixinMinecraft ...]
```

---

## מה באמת קורה כאן (חקירה)

חשוב להבין: **זו לא קריסה שקשורה למוד שלנו (`tutorialmod`)**. לפרויקט הזה אין אף Mixin
משלנו כלל — לא הוגדר קובץ `*.mixins.json`, ולא נכתבה שום מחלקת Mixin ב-`src`.

הבאג נמצא בתוך ה-Mixin **הפנימי** של מוד **Valkyrien Skies** (VS) עצמו —
`valkyrienskies-common.mixins.json:client.MixinMinecraft`. ה-Mixin הזה מנסה "לעטוף"
(`@WrapOperation`) קריאה למתודה `Minecraft.startUseItem()` (בשם SRG הפנימי — `m_91277_`),
אבל בזמן הטעינה Mixin לא מצליח לאתר את המתודה לפי אותו שם SRG בקובץ המקומפל (למרות
שהמתודה `startUseItem()` בהחלט קיימת — זה נבדק ואומת עם `javap`).

**סדר הבדיקה שבוצע:**
1. אישרנו שאין קבצי לוג ישנים ואין Mixins בפרויקט שלנו.
2. חיפשנו בלוג את שרשרת ה-`Caused by` ומצאנו ש-FATAL מגיע מ-Mixin של VS.
3. פיענחנו את שם ה-SRG: `m_91277_` = `startUseItem` (לפי `methods.csv` של MCP ל-1.20.1).
4. בדקנו עם `javap` שהמתודה קיימת ב-`Minecraft.class` בסביבת הפיתוח.
5. חילצנו את ה-refmap של VS ווידאנו שהמיפוי `startUseItem → m_91277_` קיים שם.
6. בדקנו את התבנית הרשמית של VS ואת מדריך ה-Addon של DragonMineZ.

**מסקנה:** הכשל נבע מכך שה-refmap של VS לא קיבל את אותו תהליך מיפוי כמו קוד Minecraft
בסביבת הפיתוח. הפתרון הסופי הוא **ForgeGradle 6 עם ParchmentMC**, שבו גם VS וגם DMZ
נטענים דרך `fg.deobf(...)` ונמפים באותה שרשרת.

---

## הפתרון שיושם ואומת

| רכיב | הגדרה סופית |
|---|---|
| מערכת build | ForgeGradle 6 + ParchmentMC Librarian + Sponge Mixin Gradle |
| Gradle Wrapper | 8.8 |
| Minecraft / Forge | 1.20.1 / 47.4.10 |
| מיפויים | Parchment `2023.09.03-1.20.1` |
| Valkyrien Skies | `2.4.13+c2e82178c0` |
| VS Core | `1.1.0+ea6dc8576e` |
| Mixin של המוד | `tutorialmod.mixins.json` עם refmap ו-Java 17 |

נוסף קובץ `tutorialmod.mixins.json` ריק ומוכן לשימוש עתידי. הוא אינו משנה התנהגות כרגע;
כאשר יתווספו Mixins בעתיד, יש להוסיף את שם המחלקה לרשימת `mixins` או `client` וליצור
את המחלקה תחת `net.bullettrain.tutorialmod.mixin`.

האימות בוצע עם `./gradlew runClient`: גם ה-refmap של VS וגם של DMZ עברו remap,
VS Core אותחל, DragonMineZ נטען והמשחק נשאר פעיל **ללא** `MixinTransformerError`.

> 🔗 ראו גם: [Advanced 01 — Build + VS2](Advanced-01-Build-and-VS2) להסבר המלא על שינויי ה-build.

---

<div dir="ltr">

⬅️ [Advanced 03 — Elementa GUI](Advanced-03-Elementa-GUI) · 🏠 [Home](Home)

</div>

</div>

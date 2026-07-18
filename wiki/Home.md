<div dir="rtl">

# 🧱 מדריך Minecraft Forge Modding — 1.20.X (בעברית)

ברוכים הבאים ל-Wiki הלימודי של הפרויקט **TutorialMod**! 🎉

זהו מדריך **צעד-אחר-צעד** לכתיבת מוד ל-Minecraft בעזרת **Forge**, בנוי במיוחד למי
שחדש ל-**Java** ול-**Minecraft modding**. כל שלב מוסבר **שורה אחר שורה**, בעברית,
עם הקוד המלא, טבלאות מושגים והסברים על *למה* עושים כל דבר — ולא רק *איך*.

> 💡 המדריך מבוסס על [Forge Tutorial 1.20.X של Kaupenjoe](https://github.com/Tutorials-By-Kaupenjoe/Forge-Tutorial-1.20.X),
> שבו **כל branch הוא שלב** נפרד. כל עמוד כאן מתאר בדיוק **מה נוסף בשלב הזה** לעומת הקודם.

---

## 🚀 מאיפה מתחילים?

1. אם אתם ממש בהתחלה — קראו קודם את [מבוא: Java ו-Forge](00-Intro-Java-and-Forge).
2. עברו על השלבים **לפי הסדר** — כל שלב מסתמך על הקודם.
3. אחרי כל שלב, נסו להריץ את המשחק עם `./gradlew runClient` ולראות את התוצאה.

---

## 📚 כל השלבים

### 🟢 בסיס — פריטים, בלוקים ומתכונים
| # | שלב | מה בונים |
|---|---|---|
| 01 | [התקנה והכנת הסביבה](Step-01-Setup) | הורדת ה-MDK, הרצה ראשונה |
| 02 | [פריטים מותאמים](Step-02-Custom-Items) | Sapphire, Raw Sapphire + טאב קריאייטיב |
| 03 | [בלוקים מותאמים](Step-03-Custom-Blocks) | Sapphire Block + עפרות |
| 04 | [מתכונים (Recipes)](Step-04-Recipes) | crafting, smelting, blasting |
| 05 | [טבלאות שלל (Loot Tables)](Step-05-Loot-Tables) | מה נופל מבלוק כשהוא נשבר |

### 🔵 פריטים ובלוקים מתקדמים
| # | שלב | מה בונים |
|---|---|---|
| 06 | [פריט מתקדם](Step-06-Advanced-Item) | Metal Detector — פריט עם לוגיקה |
| 07 | [בלוק מתקדם](Step-07-Advanced-Block) | בלוק עם התנהגות מיוחדת |
| 08 | [פריט מאכל](Step-08-Food-Item) | הוספת רעב ואפקטים |
| 09 | [פריט דלק](Step-09-Fuel-Item) | דלק לכבשן |
| 10 | [Tooltips](Step-10-Tooltips) | טקסט מרחף על פריט |
| 11 | [Tags](Step-11-Tags) | תיוג בלוקים/פריטים |
| 12 | [Data Generation](Step-12-Data-Generation) | יצירת JSON אוטומטית |

### 🟣 מבנים ובלוקים מיוחדים
| # | שלב | מה בונים |
|---|---|---|
| 13 | [מדרגות, ספסלים ודומיהם](Step-13-Stairs-Slabs-And-Similar) | Stairs, Slabs, Walls... |
| 14 | [מרקם 2D על מודל 3D](Step-14-2D-Textures-3D-Model) | פריט תלת-ממדי |
| 15 | [כלים (Tools)](Step-15-Tools) | מכוש, גרזן, חרב מותאמים |
| 16 | [שריון (Armor)](Step-16-Armor) | סט שריון מלא |
| 17 | [אפקט סט שריון מלא](Step-17-Full-Armor-Effect) | בונוס ללובש סט מלא |
| 18 | [Global Loot Modifiers](Step-18-Global-Loot-Modifiers) | שינוי שלל גלובלי |
| 19 | [פריט Suspicious Sand](Step-19-Suspicious-Sand-Item) | ארכיאולוגיה |

### 🌱 צמחייה, כפריים וסאונד
| # | שלב | מה בונים |
|---|---|---|
| 20 | [בלוק גידול (Crop)](Step-20-Crop-Block) | גידול חקלאי |
| 21 | [גידולים בגובה 2 בלוקים](Step-21-Two-Block-High-Crops) | גידול גבוה |
| 22 | [פרחים](Step-22-Flowers) | פרח רגיל + פרח סיר |
| 23 | [מסחר עם כפריים](Step-23-Villager-Trades) | הוספת עסקאות |
| 24 | [כפריים (Villagers)](Step-24-Villagers) | מקצוע + POI מותאם |
| 25 | [סאונדים](Step-25-Sounds) | הוספת צלילים |
| 26 | [תקליט מוזיקה](Step-26-Music-Disc) | Music Disc מותאם |
| 27 | [No Update](Step-27-No-Update) | פרטי גרסה / ניקוי |

### 🐉 יצורים (Entities) ו-Block Entities
| # | שלב | מה בונים |
|---|---|---|
| 28 | [יצור (Entity)](Step-28-Entity) | יצור מותאם + מודל |
| 29 | [אנימציית תקיפה](Step-29-Attack-Animation) | אנימציה ליצור |
| 30 | [Block Entity](Step-30-Block-Entity) | בלוק עם מצב/מלאי |
| 31 | [סוגי מתכונים (Recipe Types)](Step-31-Recipe-Types) | מתכון מותאם |
| 32 | [תאימות JEI](Step-32-JEI-Compat) | הצגת מתכונים ב-JEI |
| 33 | [Block Entity Renderer](Step-33-Block-Entity-Renderer) | ציור מותאם לבלוק |

### 🌳 עץ, שילוט וסירות
| # | שלב | מה בונים |
|---|---|---|
| 34 | [עץ (Wood)](Step-34-Wood) | סט עץ מלא |
| 35 | [שלטים (Signs)](Step-35-Signs) | שלט + שלט תלוי |
| 36 | [סירות (Boats)](Step-36-Boats) | סירה מהעץ המותאם |
| 37 | [קליע נזרק](Step-37-Throwable-Projectile) | פריט שנזרק |

### ⛰️ עולם, יצירת שטח וממדים
| # | שלב | מה בונים |
|---|---|---|
| 38 | [יצירת עפרות בעולם](Step-38-Ore-Generation) | Ore Generation |
| 39 | [עץ מותאם](Step-39-Custom-Tree) | Sapling + עץ |
| 40 | [יצירת עץ (Tree Gen)](Step-40-Tree-Gen) | Configured/Placed Features |
| 41 | [Trunk Placers](Step-41-Trunk-Placers) | צורת הגזע |
| 42 | [Foliage Placers](Step-42-Foliage-Placers) | צורת העלווה |
| 43 | [ביומים (Biomes)](Step-43-Biomes) | ביום מותאם |
| 44 | [ממד (Dimension)](Step-44-Dimension) | עולם/ממד חדש |

---

## 🛠️ תוכן מתקדם (מ-fork האישי)

הסעיפים האלה חורגים מהמדריך המקורי ומתעדים שילוב מודים חיצוניים ופתרון בעיות:

| עמוד | נושא |
|---|---|
| [שינויי Build + Valkyrien Skies](Advanced-01-Build-and-VS2) | `gradle.properties`, `build.gradle`, VS2 |
| [תמיכת Addon ל-DragonMineZ](Advanced-02-DragonMineZ) | חיבור למוד DMZ |
| [Elementa GUI](Advanced-03-Elementa-GUI) | ספריית ממשק דקלרטיבית |
| [פתרון קריסת Mixin](Advanced-04-Mixin-Fix) | `MixinTransformerError` של VS |

---

## ✅ דרישות

- **Minecraft 1.20.X**
- **Forge MDK** לגרסה 1.20.X
- **Java 17+**
- **IntelliJ IDEA** (מומלץ) או עורך אחר

---

<div dir="ltr">

Based on [Forge-Tutorial-1.20.X by Kaupenjoe](https://github.com/Tutorials-By-Kaupenjoe/Forge-Tutorial-1.20.X)

</div>

</div>

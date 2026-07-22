<div dir="rtl">

# 🔧 שלב 04 — מתכונים (Recipes)

בשלב הזה אנו מוסיפים **מתכוני יצירה** (crafting recipes) למוד שלנו! 🇬🇧
ניצור ארבעה מתכונים:
1. **Sapphire Block** ← 9 Sapphire (crafting shaped)
2. **Sapphire** ← 1 Sapphire Block (crafting shapeless)
3. **Sapphire** ← Raw Sapphire (smelting)
4. **Sapphire** ← Raw Sapphire (blasting)

מתכונים הם **לוגיקת ה"ק rafting"** של Minecraft — הם קובעים איך שחקנים יוצרים פריטים
בין ערימות ( crafting table ) ובכבשן ( furnace / blast furnace ).

---

## מה השתנה לעומת השלב הקודם

| קובץ | שינוי |
|---|---|
| `src/main/resources/data/tutorialmod/recipes/sapphire_block_from_sapphire.json` | **חדש** — 9 SAPPHIRE → SAPPHIRE_BLOCK |
| `src/main/resources/data/tutorialmod/recipes/sapphire_from_sapphire_block.json` | **חדש** — 1 SAPPHIRE_BLOCK → 9 SAPPHIRE |
| `src/main/resources/data/tutorialmod/recipes/sapphire_from_smelting_raw_sapphire.json` | **חדש** — smelting של RAW_SAPPHIRE |
| `src/main/resources/data/tutorialmod/recipes/sapphire_from_blasting_raw_sapphire.json` | **חדש** — blasting של RAW_SAPPHIRE |

> 💡 ב-Gradle Data Generator (שלבים הבאים) רבים מהקבצים האלה ייווצרו אוטומטית,
> אבל בשלב הזה נכתוב אותם בעצמנו כדי להבין את המבנה.

---

## מתכון: Sapphire Block

<div dir="ltr">

```json
{
  "type": "minecraft:crafting_shaped",
  "category": "misc",
  "pattern": [
    "###",
    "###",
    "###"
  ],
  "key": {
    "#": {
      "item": "tutorialmod:sapphire"
    }
  },
  "result": {
    "item": "tutorialmod:sapphire_block"
  }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `"type": "minecraft:crafting_shaped"` | מתכון sculpted — יש צורה קבועה (pattern) |
| `"category": "misc"` | קטגוריה — משפיעה על סידור ב-creative menu |
| `"pattern": ["###", "###", "###"]` | התבנית: 9 פריטי sapphire בצורת ריבוע 3x3 |
| `"key": { "#": { "item": "tutorialmod:sapphire" } }` | הסמל `#` מייצג את פריט ה-sapphire |
| `"result": { "item": "tutorialmod:sapphire_block" }` | התוצאה היא sapphire_block |

---

## מתכון: Sapphire from Block

<div dir="ltr">

```json
{
  "type": "minecraft:crafting_shapeless",
  "category": "misc",
  "ingredients": [
    {
      "item": "tutorialmod:sapphire_block"
    }
  ],
  "result": {
    "item": "tutorialmod:sapphire",
    "count": 9
  }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `"type": "minecraft:crafting_shapeless"` | מתכון ללא צורה קבועה — הסדר אינו חשוב |
| `"ingredients": [ { "item": "tutorialmod:sapphire_block" } ]` | מרכיב אחד: sapphire_block |
| `"count": 9` | כמות התוצאה — 9 פריטי sapphire |

---

## מתכון: Smelting

<div dir="ltr">

```json
{
  "type": "minecraft:smelting",
  "category": "misc",
  "cookingtime": 200,
  "experience": 0.7,
  "group": "sapphire",
  "ingredient": {
    "item": "tutorialmod:raw_sapphire"
  },
  "result": "tutorialmod:sapphire"
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `"type": "minecraft:smelting"` | מתכון smelting רגיל (כבשן) |
| `"cookingtime": 200` | זמן בישול ב-ticks (200 ticks = 10 שניות) |
| `"experience": 0.7` | כמות ה-XP שנרשמת לכבשן |
| `"group": "sapphire"` | קטגוריה — מאפשרת לשמור מתכונות דומות יחד |
| `"ingredient": { "item": "tutorialmod:raw_sapphire" }` | המרכיב: raw_sapphire |
| `"result": "tutorialmod:sapphire"` | התוצאה: sapphire |

---

## מתכון: Blasting

<div dir="ltr">

```json
{
  "type": "minecraft:blasting",
  "category": "misc",
  "cookingtime": 100,
  "experience": 0.7,
  "group": "sapphire",
  "ingredient": {
    "item": "tutorialmod:raw_sapphire"
  },
  "result": "tutorialmod:sapphire"
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `"type": "minecraft:blasting"` | מתכון blasting (blast furnace) — מהיר פי 2 מ-smelting |
| `"cookingtime": 100` | 100 ticks = 5 שניות (חצי מזמן smelting) |
| `"experience": 0.7` | אותו XP כמו ב-smelting |

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| **crafting_shaped** | מתכון עם צורה קבועה — שורה ועמודה |
| **crafting_shapeless** | מתכון ללא צורה קבועה |
| **smelting** | בישול בכבשן רגיל |
| **blasting** | בישול בכבשן ברזי (blast furnace) — מהיר יותר |
| **cookingtime** | זמן התהליך ב-ticks (20 ticks = 1 שנייה) |
| **experience** | כמות ה-XP שהשחקן מקבל |

---

<div dir="ltr">

⬅️ [שלב 03](Step-03-Custom-Blocks) · ➡️ [שלב 05](Step-05-Loot-Tables)

</div>

</div>

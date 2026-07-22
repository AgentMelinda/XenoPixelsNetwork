<div dir="rtl">

# 🔧 שלב 14 — מרקם 2D על מודל 3D (2D Textures on a 3D Model)

בשלבים קודמים ראינו כיצד לפריטים פשוטים משתמשים במוטיב `item/generated` — תמונה שטוחה בתיבת לוגו.
כעת נלמד ליצור **פריט תלת-ממדי** (3D) עם מודל Blockbench מותאם, תוך שמירה על תצוגה דו-ממדית בטופס
ה-GUI ו-terrain/fixed. לכן נ uses את ה-`forge:separate_transforms` loader.

> 💡 שים לב: המודל התלת-ממדי `sapphire_staff_3d.json` נוצר באמצעות **Blockbench**
> וייצוא ל-Forge. אנו רק מגדירים את ה-loader ופר-spectives בקובץ הפריט.

---

## מה השתנה לעומת השלב הקודם

| קבצים חדשים/ששונו | תיאור |
|---|---|
| `ModsItems.java` | הוספת `SAPPHIRE_STAFF` |
| `sapphire_staff.json` | מודל פריט עם `separate_transforms` |
| `sapphire_staff_2d.json` | מודל GUI פשוט (2D) |
| `sapphire_staff_3d.json` | מודל Blockbench תלת-ממדי |
| `en_us.json` | שם הפריט "Sapphire Staff" |
| `textures/item/` | 2 קבצי טקסטורה (2D + 3D) |

---

## קוד חדש ב-`ModsItems.java`

<div dir="ltr">

```java
public static final RegistryObject<Item> SAPPHIRE_STAFF = ITEMS.register("sapphire_staff",
        () -> new Item(new Item.Properties().stacksTo(1)));
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `ITEMS.register("sapphire_staff", ...)` | רושם פריט חדש עם מזהה `sapphire_staff` |
| `new Item(new Item.Properties().stacksTo(1))` | הפריט אינו ניתן להערמה (stack) (כמו כל כלי נשק/מטה) |

---

## קוד חדש ב-`sapphire_staff.json` (מודל הפריט)

<div dir="ltr">

```json
{
  "parent": "minecraft:item/handheld",
  "loader": "forge:separate_transforms",
  "base": {
    "parent": "tutorialmod:item/sapphire_staff_3d"
  },
  "perspectives": {
    "gui": {
      "parent": "tutorialmod:item/sapphire_staff_2d"
    },
    "ground": {
      "parent": "tutorialmod:item/sapphire_staff_2d"
    },
    "fixed": {
      "parent": "tutorialmod:item/sapphire_staff_2d"
    }
  }
}
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `"parent": "minecraft:item/handheld"` | מוריש תבנית של פריט שנלחץ ביד (מחזיק את החץ) |
| `"loader": "forge:separate_transforms"` | ה loader של Forge שמאפשר perspectives נפרדות |
| `"base"` | הפר-spective ברירת המחדל (יד, עולם) — משתמשת במודל ה-3D |
| `"perspectives"` | מגדירה תצוגה שונה לכל מצלמה |
| `"gui"` | בטופס ה-creative/inventory — מציגה מודל 2D פשוט |
| `"ground"` | כאשר הפריט נופל על הקרקע — מציגה מודל 2D |
| `"fixed"` | מצלמה קבועה — מציגה מודל 2D |

---

## קוד חדש ב-`sapphire_staff_2d.json` (מודל GUI)

<div dir="ltr">

```json
{
  "parent": "minecraft:item/handheld",
  "textures": {
    "layer0": "tutorialmod:item/sapphire_staff"
  }
}
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `"parent": "minecraft:item/handheld"` | משתמש בתבנית הפשוטה של פריט שנלחץ |
| `"layer0": "tutorialmod:item/sapphire_staff"` | מגדיר את הטקסטורה השטוחה ל-GUI |

---

## קוד חדש ב-`sapphire_staff_3d.json` (מודל Blockbench)

<div dir="ltr">

```json
{
  "credit": "Made with Blockbench",
  "texture_size": [32, 32],
  "textures": {
    "0": "tutorialmod:item/sapphire_staff_3d",
    "particle": "tutorialmod:item/sapphire_staff_3d"
  },
  "elements": [
    {
      "name": "staff_grip",
      "from": [7.25, 0, 7.25],
      "to": [8.75, 11, 8.75],
      "rotation": {"angle": 0, "axis": "y", "origin": [8, 6.5, 8]},
      "faces": {
        "north": {"uv": [0, 0, 1, 5.5], "texture": "#0"},
        "east": {"uv": [1.5, 0, 2.5, 5.5], "texture": "#0"},
        "south": {"uv": [3, 0, 4, 5.5], "texture": "#0"},
        "west": {"uv": [4.5, 0, 5.5, 5.5], "texture": "#0"},
        "up": {"uv": [7, 8, 6, 7], "texture": "#0"},
        "down": {"uv": [8.5, 0, 7.5, 1], "texture": "#0"}
      }
    },
    {
      "name": "staff_pommel_ring_bottom",
      "from": [7, 1, 7],
      "to": [9, 2, 9],
      "faces": {
        "north": {"uv": [9, 4, 10, 4.5], "texture": "#0"},
        "east": {"uv": [4.5, 9, 5.5, 9.5], "texture": "#0"},
        "south": {"uv": [9, 5, 10, 5.5], "texture": "#0"},
        "west": {"uv": [9, 6, 10, 6.5], "texture": "#0"},
        "up": {"uv": [5.5, 7, 4.5, 6], "texture": "#0"},
        "down": {"uv": [7, 5.5, 6, 6.5], "texture": "#0"}
      }
    },
    {
      "name": "staff_pommel_ring_top",
      "from": [7, 9, 7],
      "to": [9, 10, 9],
      "faces": {
        "north": {"uv": [10.5, 1, 11.5, 1.5], "texture": "#0"},
        "east": {"uv": [10.5, 2, 11.5, 2.5], "texture": "#0"},
        "south": {"uv": [10.5, 3, 11.5, 3.5], "texture": "#0"},
        "west": {"uv": [10.5, 4, 11.5, 4.5], "texture": "#0"},
        "up": {"uv": [8.5, 5.5, 7.5, 4.5], "texture": "#0"},
        "down": {"uv": [8.5, 6, 7.5, 7], "texture": "#0"}
      }
    },
    {
      "name": "chappe_base",
      "from": [7, 11, 7],
      "to": [9, 12, 9],
      "faces": {
        "north": {"uv": [7.5, 7.5, 8.5, 8], "texture": "#0"},
        "east": {"uv": [0, 8, 1, 8.5], "texture": "#0"},
        "south": {"uv": [1.5, 8, 2.5, 8.5], "texture": "#0"},
        "west": {"uv": [6, 8.5, 7, 9], "texture": "#0"},
        "up": {"uv": [4, 7, 3, 6], "texture": "#0"},
        "down": {"uv": [7, 4, 6, 5], "texture": "#0"}
      }
    },
    {
      "from": [6.5, 12, 6.5],
      "to": [9.5, 14, 9.5],
      "faces": {
        "north": {"uv": [12, 1.5, 13.5, 2.5], "texture": "#0"},
        "east": {"uv": [12, 1.5, 13.5, 2.5], "texture": "#0"},
        "south": {"uv": [12, 1, 13.5, 2], "texture": "#0"},
        "west": {"uv": [12, 1, 13.5, 2], "texture": "#0"},
        "up": {"uv": [12, 1, 13.5, 2.5], "texture": "#0"},
        "down": {"uv": [12, 1, 13.5, 2.5], "texture": "#0"}
      }
    }
  ],
  "display": {
    "ground": {"rotation": [67.28, -28.64, -41.92], "translation": [0, -1.25, 0]},
    "gui": {"rotation": [0, 0, -46.5], "translation": [-0.75, -0.5, 0]},
    "fixed": {"translation": [0, -1.25, 0]}
  },
  "groups": [
    {"name": "hilt", "origin": [0, 0, 0], "color": 0, "nbt": "{}", "children": [0, 1, 2, {"name": "chappe", "origin": [0, 0, 0], "color": 0, "nbt": "{}", "children": [3]}]},
    4
  ]
}
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `"credit": "Made with Blockbench"` | פלט מיוצא מ-Blockbench — כלי עריכת מודלים |
| `"textures": {"0": ..., "particle": ...}` | `#0` מתייחס לטקסטורה הזו; `particle` טקסטורה לאפקטי חלקיקים |
| `"elements": [...]` | רשימת Volumes (קוביות/תיבה) שקונים את המודל |
| `"from"` / `"to"` | נקודת התחלה וסיום של התיבה בפר-צימנטים |
| `"rotation": {"angle": ..., "axis": "y", "origin": [...]}`| סיבוב התיבה סביב ציר Y בנקודת המוצא |
| `"faces": {"north": {...}, ...}`| 6 הפנים של התיבה — כל אחת עם UV וטקסטורה |
| `"display": {"ground": {...}, "gui": {...}, ...}`| זווית ומיקום הפריט בכל מצלמה |
| `"groups": [...]`| היררכיה של חלקי המודל (כדי ש-Blockbench יוכל לערוך אותם) |

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| **`forge:separate_transforms`** | loader שמאפשר perspectives שונות לפריט |
| **`parent: item/handheld`** | תבנית בנויה של Minecraft לפריטים שנלחצים ביד |
| **Blockbench** | כלי ויזואלי ליצירת מודלים 3D ל-Minecraft |
| **`elements` / `faces`** | JSON שמגדיר Volumes וטקסטורות — מה שהמנוע מצייר |
| **`perspectives`** | מצלמות שונות: gui, ground, fixed, firstperson, thirdperson |

---

<div dir="ltr">

⬅️ [שלב 13](Step-13-Stairs-Slabs-And-Similar) · ➡️ [שלב 15](Step-15-Tools)

</div>

</div>

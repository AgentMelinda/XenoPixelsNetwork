<div dir="rtl">

# 🔧 שלב 26 — תקליט מוזיקה (Music Disc)

בשלב הקודם הוספנו צלילים מותאמים למוד. כעת אנו ממשיכים עם **תקליט מוזיקה מותאם** —
**Bar Brawl Music Disc** — תקליט שניתן למצוא ב-game (דרך loot) ולהפעיל ב-Jukebox.

> 💡 `RecordItem` הוא פריט שמנגן music disc ב-Jukebox.
> הוא דורש `SoundEvent` שמנגן את הקובץ, ומשתמש ב-`stream: true` ב-`sounds.json`
> כדי שהקובץ הארוך יזרום (stream) במקום להיטען כולו לזיכרון.

---

## מה השתנה לעומת השלב הקודם

| קבצים חדשים/ששונו | תיאור |
|---|---|
| `ModsItems.java` | `BAR_BRAWL_MUSIC_DISC` |
| `sound/ModSounds.java` | `BAR_BRAWL` SoundEvent |
| `ModItemModelProvider.java` | `simpleItem` לתקליט |
| `ModItemTagGenerator.java` | תגיות `creeper_drop_music_discs` + `music_discs` |
| `sounds.json` | `bar_brawl` עם `stream: true` |
| `en_us.json` | שם + תיאור התקליט |
| `textures/item/` | תמונת התקליט |
| `sounds/` | `bar_brawl.ogg` (קובץ המוזיקה) |

---

## קוד חדש ב-`ModsItems.java`

<div dir="ltr">

```java
public static final RegistryObject<Item> BAR_BRAWL_MUSIC_DISC = ITEMS.register("bar_brawl_music_disc",
        () -> new RecordItem(6, ModSounds.BAR_BRAWL, new Item.Properties().stacksTo(1), 2440));
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `new RecordItem(6, ModSounds.BAR_BRAWL, ...)` | 6 = קוד מוזיקה (משמש ל-Jukebox), `BAR_BRAWL` = הסאונד |
| `new Item.Properties().stacksTo(1)` | ניתן לשחק רק 1 תקליט ב-stack |
| `2440` | משך המוזיקה בטיקים (2440 = כ-2 דקות) |

---

## קוד חדש ב-`sound/ModSounds.java`

<div dir="ltr">

```java
public static final RegistryObject<SoundEvent> BAR_BRAWL = registerSoundEvents("bar_brawl");
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `registerSoundEvents("bar_brawl")` | יוצר URI `tutorialmod:bar_brawl` |

---

## קוד חדש ב-`sounds.json`

<div dir="ltr">

```json
"bar_brawl": {
  "sounds": [
    {
      "name": "tutorialmod:bar_brawl",
      "stream": true
    }
  ]
}
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `"stream": true` | מפעיל streaming — קובץ המוזיקה ארוך ונשמע בזמן אמת |
| (ללא `"subtitle"`) | תקליטי מוזיקה אין להם כתובית תנייה |

---

## שינוי ב-`ModItemTagGenerator.java`

<div dir="ltr">

```java
this.tag(ItemTags.CREEPER_DROP_MUSIC_DISCS)
        .add(ModsItems.BAR_BRAWL_MUSIC_DISC.get());
this.tag(ItemTags.MUSIC_DISCS)
        .add(ModsItems.BAR_BRAWL_MUSIC_DISC.get());
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `ItemTags.CREEPER_DROP_MUSIC_DISCS` | תגית שמגדירה שהתקליט יכול ליפול מ-Creeper |
| `ItemTags.MUSIC_DISCS` | תגית כללית לכל תקליטי המוזיקה |

---

## שינוי ב-`en_us.json`

<div dir="ltr">

```json
"item.tutorialmod.bar_brawl_music_disc": "Bar Brawl Music Disc",
"item.tutorialmod.bar_brawl_music_disc.desc": "Bryan Tech - Bar Brawl (CC0)",
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `"bar_brawl_music_disc"` | שם הפריט |
| `"bar_brawl_music_disc.desc"` | תיאור/קרדיט שמתחת לשם |

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| **`RecordItem`** | פריט תקליט מוזיקה — מנוגן ב-Jukebox |
| **`stream: true`**| מפעיל streaming לקבצים ארוכים (מוזיקה) |
| **`MUSIC_DISCS`** | תגית שמורה לכל תקליטי המוזיקה |
| **`CREEPER_DROP_MUSIC_DISCS`** | תגית שמגדירה אילו תקליטים נופלים מ-Creeper |

---

<div dir="ltr">

⬅️ [שלב 25](Step-25-Sounds) · ➡️ [שלב 27](Step-27-No-Update)

</div>

</div>

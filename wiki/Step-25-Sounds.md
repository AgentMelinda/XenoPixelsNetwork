<div dir="rtl">

# 🔧 שלב 25 — סאונדים (Sounds)

בשלבים קודמים יצרנו פריטים, בלוקים, כפריים וכו'. כעת אנו נלמד להוסיף **צלילים מותאמים**
למוד שלנו. אנו מוסיפים:
- סאונד למתג Metal Detector
- סאונדים לבלוק Sound Block (break, step, place, hit, fall)

> 💡 הצלילים נשמרים כ-`ogg` ב-`assets/tutorialmod/sounds/`.
> `ModSounds.java` רושם את `SoundEvent` URI, ו-`sounds.json` ממפה את השם לקובץ.

---

## מה השתנה לעומת השלב הקודם

| קבצים חדשים/ששונו | תיאור |
|---|---|
| `sound/ModSounds.java` | רישום SoundEvents + ForgeSoundType |
| `sounds.json` | מיפוי שמות סאונדים לקבצי ogg |
| `sounds/` | 6 קבצי ogg |
| `MetalDetectorItem.java` | השמעת סאונד כאשר נמצאORES |
| `SoundBlock.java` | שימוש ב-`SOUND_BLOCK_SOUNDS` |
| `en_us.json` | תיאור סאונד |

---

## קוד חדש ב-`sound/ModSounds.java`

<div dir="ltr">

```java
package net.bullettrain.tutorialmod.sound;

import net.bullettrain.tutorialmod.TutorialMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.common.util.ForgeSoundType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, TutorialMod.MOD_ID);

    public static final RegistryObject<SoundEvent> METAL_DETECTOR_FOUND_ORE = registerSoundEvents("metal_detector_found_ore");

    public static final RegistryObject<SoundEvent> SOUND_BLOCK_BREAK = registerSoundEvents("sound_block_break");
    public static final RegistryObject<SoundEvent> SOUND_BLOCK_STEP = registerSoundEvents("sound_block_step");
    public static final RegistryObject<SoundEvent> SOUND_BLOCK_FALL = registerSoundEvents("sound_block_fall");
    public static final RegistryObject<SoundEvent> SOUND_BLOCK_PLACE = registerSoundEvents("sound_block_place");
    public static final RegistryObject<SoundEvent> SOUND_BLOCK_HIT = registerSoundEvents("sound_block_hit");

    public static final ForgeSoundType SOUND_BLOCK_SOUNDS = new ForgeSoundType(1f, 1f,
            ModSounds.SOUND_BLOCK_BREAK, ModSounds.SOUND_BLOCK_STEP, ModSounds.SOUND_BLOCK_PLACE,
            ModSounds.SOUND_BLOCK_HIT, ModSounds.SOUND_BLOCK_FALL);

    private static RegistryObject<SoundEvent> registerSoundEvents(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(TutorialMod.MOD_ID, name)));
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ...)` | רושם SoundEvents למוד שלנו |
| `registerSoundEvents("metal_detector_found_ore")` | יוצר URI `tutorialmod:metal_detector_found_ore` |
| `SoundEvent.createVariableRangeEvent(...)` | סאונד טווח משתנה — נשמע differently לפי מרחק |
| `ForgeSoundType(1f, 1f, ...)` | SoundType מותאם: עוצמה, pitch, break/step/place/hit/fall |
| `SOUND_EVENTS.register(eventBus)` | מעביר את הרישום לאירוע bus |

---

## קוד חדש ב-`sounds.json`

<div dir="ltr">

```json
{
  "metal_detector_found_ore": {
    "subtitles": "sounds.tutorialmod.metal_detector_found_ore",
    "sounds": [
      "tutorialmod:metal_detector_found_ore"
    ]
  },
  "sound_block_break": {
    "subtitle": "sounds.tutorialmod.sound_block_break",
    "sounds": [
      "tutorialmod:sound_block_break"
    ]
  },
  "sound_block_step": {
    "subtitle": "sounds.tutorialmod.sound_block_step",
    "sounds": [
      "tutorialmod:sound_block_step"
    ]
  },
  "sound_block_place": {
    "subtitle": "sounds.tutorialmod.sound_block_place",
    "sounds": [
      "tutorialmod:sound_block_place"
    ]
  },
  "sound_block_hit": {
    "subtitle": "sounds.tutorialmod.sound_block_hit",
    "sounds": [
      "tutorialmod:sound_block_hit"
    ]
  },
  "sound_block_fall": {
    "subtitle": "sounds.tutorialmod.sound_block_fall",
    "sounds": [
      "tutorialmod:sound_block_fall"
    ]
  }
}
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `"subtitles": "sounds.tutorialmod.metal_detector_found_ore"` | מגדיר כתובית תרגום שמוצגת כאשר הסאונד מתנגש |
| `"sounds": ["tutorialmod:metal_detector_found_ore"]` | מפנה לקובץ ה-ogg |

---

## שינוי ב-`MetalDetectorItem.java`

<div dir="ltr">

```java
pContext.getLevel().playSeededSound(null, positionClicked.getX(), positionClicked.getY(), positionClicked.getZ(),
        ModSounds.METAL_DETECTOR_FOUND_ORE.get(), SoundSource.BLOCKS, 1f, 1f, 0);
```

</div>
### הסבר שורה אחר שורה

| שורה | הסבר |
|---|---|
| `playSeededSound(null, x, y, z, ...)` | `null` = השמעה לכל שחקנים קרובים; x,y,z = מקור הסאונד |
| `SoundSource.BLOCKS` | קטגוריית הסאונד — נשמע כצלצל בלוקים |
| `1f, 1f, 0` | עוצמה, pitch, seed רנדומלי (0=אקראי) |

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| **`SoundEvent`** | אירוע סאונד — מזהה ייחודי שמוקשר לקובץ ogg |
| **`ForgeSoundType`** | SoundType מותאם לבלוק — קובע סאונדים ל-break/step/place |
| **`sounds.json`** | מיפוי JSON בין שם הסאונד לקובץ ומתי להפעיל כתובית |
| **`playSeededSound`** | מנגן סאונד בנקודת עולם ספציפית |

---

<div dir="ltr">

⬅️ [שלב 24](Step-24-Villagers) · ➡️ [שלב 26](Step-26-Music-Disc)

</div>

</div>

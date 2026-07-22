<div dir="rtl">

# 🔧 שלב 01 — התקנה והכנת הסביבה (Setup)

בשלב הראשון אנו מגדירים את **בסיס המוד** מרוצה — מורידים את ה-MDK של Forge, פותחים
בפרויקט, ומבטיחים ש-`./gradlew runClient` מריץ את Minecraft ללא תקלות.
למרות שאין עדיין פריטים או בלוקים מותאמים, הקבצים שאנו יוצרים כאן הם
**השלד שעליו נבנה כל שאר המוד**.

---

## מה השתנה לעומת השלב הקודם

זהו השלב הראשון — אנו יוצרים את כל הקבצים הבסיסיים של הפרויקט מתוך תבנית ה-MDK:

| קובץ | תיאור |
|---|---|
| `build.gradle` | קובץ הבנייה הראשי של Gradle — מגדיר תלויות, גרסאות והרצות |
| `gradle.properties` | מאפיינים (properties) של הפרויקט — גרסאות Minecraft/Forge, מזהה המוד |
| `settings.gradle` | הגדרת שם הפרויקט ו-repositories של Gradle |
| `gradlew` / `gradlew.bat` | סקריפטים להרצת Gradle ללא התקנה ידנית |
| `gradle/wrapper/gradle-wrapper.jar` | קבצי wrapper שיהליכו את גרסת Gradle הנכונה |
| `src/main/java/.../TutorialMod.java` | מחלקת הכניסה הראשית של המוד (`@Mod`) |
| `src/main/resources/META-INF/mods.toml` | קובץ תיאור המוד — שם, גרסה, תלויות, מאפיינים |
| `src/main/resources/pack.mcmeta` | מטא-דאטה של חבילת המשאבים (resource pack) |

---

## קובץ Build.gradle

<div dir="ltr">

```groovy
plugins {
    id 'eclipse'
    id 'idea'
    id 'maven-publish'
    id 'net.minecraftforge.gradle' version '[6.0,6.2)'
    id 'org.parchmentmc.librarian.forgegradle' version '1.+'
}

version = mod_version
group = mod_group_id

base {
    archivesName = mod_id
}

java.toolchain.languageVersion = JavaLanguageVersion.of(17)
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `id 'net.minecraftforge.gradle'` | פלאגין הרשמי של Forge — מאפשר לקמפל ולרוץ את המשחק עם המוד |
| `id 'org.parchmentmc.librarian.forgegradle'` | מיפויים (mappings) של ParchmentMC — שמות פרמטרים קריאים יותר בקוד |
| `version = mod_version` | גרסת המוד — נקראת מ-`gradle.properties` |
| `group = mod_group_id` | חבילה (group) של Java — בדרך כלל שם הדומיין ההפוך |
| `base { archivesName = mod_id }` | שם קובץ ה-JAR שיווצר |
| `java.toolchain.languageVersion = JavaLanguageVersion.of(17)` | מכוון את Java ל-gradle ל-Java 17 (נדרש ל-Minecraft 1.20.1) |

<div dir="ltr">

```groovy
minecraft {
    mappings channel: mapping_channel, version: mapping_version
    copyIdeResources = true

    runs {
        client {
            workingDirectory project.file('run')
            property 'forge.logging.markers', 'REGISTRIES'
            property 'forge.logging.console.level', 'debug'
            property 'forge.enabledGameTestNamespaces', mod_id
            mods { "${mod_id}" { source sourceSets.main } }
        }
        server { /* דומה ל-client אבל מריץ שרת */ }
        data { /* הגדרות ל-data generation — נראה בשלב 12 */ }
    }
}

sourceSets.main.resources { srcDir 'src/generated/resources' }
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `mappings channel: ...` | קובע את מערכת שמות השדות/מתודות — `official` או `parchment` |
| `copyIdeResources = true` | מעתיק משאבים לתיקיית ה-output של IntelliJ לפני הריצה |
| `runs { client { ... } }` | הגדרות הרצה לפיתוח — `runClient` מריץ כאן |
| `workingDirectory project.file('run')` | תיקיית העבודה של המשחק המרוץ |
| `property 'forge.logging.markers', 'REGISTRIES'` | מציג לוגים של רישום Registry שימושיים לניפוי באגים |
| `sourceSets.main.resources { srcDir 'src/generated/resources' }` | כולל משאבים שמייצרים אוטומטית (datagen) בתיקייה זו |

<div dir="ltr">

```groovy
dependencies {
    minecraft "net.minecraftforge:forge:${minecraft_version}-${forge_version}"
}

jar {
    manifest {
        attributes([
            "Specification-Title"     : mod_id,
            "Implementation-Title"    : project.name,
            "Implementation-Vendor"   : mod_authors
        ])
    }
}
jar.finalizedBy('reobfJar')
publishing { /* הגדרות פרסום — לא בשימוש בפיתוח יומיומי */ }
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `minecraft "net.minecraftforge:forge:..."` | התלות המרכזית — MC + Forge |
| `jar { manifest { ... } }` | מגדיר תעודת זהות בקובץ JAR (נחוץ ל-Forge loader) |
| `jar.finalizedBy('reobfJar')` | אחרי בנייה, מעביר שמות למצב-obfuscated להפצה |

---

## קובץ Gradle.properties

<div dir="ltr">

```properties
org.gradle.jvmargs=-Xmx3G
org.gradle.daemon=false

minecraft_version=1.20.1
minecraft_version_range=[1.20.1,1.21)
forge_version=47.4.10
forge_version_range=[47,)
loader_version_range=[47,)
mapping_channel=parchment
mapping_version=2023.09.03-1.20.1

mod_id=tutorialmod
mod_name=Tutorial Mod
mod_license=MIT
mod_version=0.1-1.20.1
mod_group_id=net.bullettrain.tutorialmod
mod_authors=bullettrain
mod_description=This is a Tutorialmod made by bullettrain :)
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `org.gradle.jvmargs=-Xmx3G` | מקצה 3GB זיכרון ל-Gradle (נדרש לניתוח Minecraft) |
| `minecraft_version=1.20.1` | גרסת Minecraft שאליה פונים |
| `forge_version=47.4.10` | גרסת Forge המתאימה ל-MC 1.20.1 |
| `mapping_channel=parchment` | משתמש במיפויים ידידותיים של ParchmentMC |
| `mod_id=tutorialmod` | המזהה הייחודי של המוד (lowercase, בלי רווחים) |
| `mod_group_id=net.bullettrain.tutorialmod` | החבילה הבסיסית של קוד ה-Java |
| `mod_version=0.1-1.20.1` | גרסת המוד — SemVer |
| `mod_license=MIT` | רישיון הקוד |

---

## קובץ Settings.gradle

<div dir="ltr">

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { name = 'MinecraftForge'; url = 'https://maven.minecraftforge.net/' }
        maven { url = 'https://maven.parchmentmc.org' }
    }
}

plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.5.0'
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `pluginManagement { repositories { ... } }` | מאחסן של פלאגינים — Gradle יוריד מכאן את פלאגין Forge ו-Parchment |
| `maven.minecraftforge.net` | המאגר הרשמי של Forge |
| `org.gradle.toolchains.foojay-resolver-convention` | פלאגין שמסייע ב-Java toolchain — מאפשר ל-Gradle להוריד Java 17 אוטומטית |

---

## מחלקת TutorialMod.java

<div dir="ltr">

```java
package net.bullettrain.tutorialmod;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(TutorialMod.MOD_ID)
public class TutorialMod {
    public static final String MOD_ID = "tutorialmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TutorialMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        modEventBus.addListener(this::addCreative);
    }

    private void commonSetup(final FMLCommonSetupEvent event) { }

    private void addCreative(BuildCreativeModeTabContentsEvent event) { }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) { }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) { }
    }
}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `package net.bullettrain.tutorialmod;` | חבילת ה-Java של המוד — כל הקבצים נמצאים תחתיה |
| `@Mod(TutorialMod.MOD_ID)` | ה-annotation שמסמלת למשחק שזוהי נקודת הכניסה של המוד |
| `public static final String MOD_ID = "tutorialmod";` | המזהה הייחודי של המוד — חייב להתאים ל-`modId` ב-mods.toml |
| `public static final Logger LOGGER = LogUtils.getLogger();` | לוגר לשימוש בדיבאג (debug) — מומלץ להשתמש בו במקום `System.out.println` |
| `IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();` | אוטובוס האירועים של המוד — אירועי lifecycle של Forge עוברים דרכו |
| `modEventBus.addListener(this::commonSetup);` | מאזין לאירוע `FMLCommonSetupEvent` — נקודת כניסה לקמפוננטים משותפים |
| `MinecraftForge.EVENT_BUS.register(this);` | רושם את המחלקה לאירועים כלליים של Minecraft/Forge |
| `modEventBus.addListener(this::addCreative);` | מאזין לאירוע הוספת פריטים ל-Creative Tab |
| `@SubscribeEvent public void onServerStarting(...)` | פועל כשהשרת מתחיל — מקוםgood למידע או פקודות ראשוניות |
| `@Mod.EventBusSubscriber(...)` | מחלקה סטטית שמאזינה לאירועים בצד הקליינט (CLIENT) בלבד |

---

## קובץ Mods.toml

<div dir="ltr">

```toml
modLoader="javafml"
loaderVersion="${loader_version_range}"

[[mods]]
modId="${mod_id}"
version="${mod_version}"
displayName="${mod_name}"
authors="${mod_authors}"
description='''${mod_description}'''

[[dependencies.${mod_id}]]
    modId="forge"
    mandatory=true
    versionRange="${forge_version_range}"
    ordering="NONE"
    side="BOTH"

[[dependencies.${mod_id}]]
    modId="minecraft"
    mandatory=true
    versionRange="${minecraft_version_range}"
    ordering="NONE"
    side="BOTH"
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `modLoader="javafml"` | מציין שזה מוד שטעון via JavaFML (סטנדרט למודי Forge) |
| `loaderVersion="${loader_version_range}"` | טווח גרסאות של ה-loader שנדרש |
| `[[mods]]` | בלוק המתאר את המוד עצמו |
| `modId="${mod_id}"` | מזהה המוד — חייבת להיות זהה ל-`MOD_ID` ב-Java |
| `version="${mod_version}"` | גרסת המוד — מוצגת ב-launcher |
| `displayName="${mod_name}"` | שם תצוגה של המוד |
| `[[dependencies.${mod_id}]]` | התלות של המוד — Forge ו-Minecraft |
| `mandatory=true` | האם ההתקנה נכשלת אם התלות חסרה |
| `side="BOTH"` | המוד נטען גם בצד קליינט וגם בצד שרת |

---

## קובץ Pack.mcmeta

<div dir="ltr">

```json
{"pack":{"description":{"text":"${mod_id} resources"},"pack_format":15}}
```

</div>
**הסבר שורה אחר שורה:**

| שורה | הסבר |
|---|---|
| `pack` | מחלקת השורשת של חבילת המשאבים |
| `description.text` | תיאור החבילה — מופיע ב-`mod_id` |
| `pack_format: 15` | פורמט המשאבים — 15 תואם ל-Minecraft 1.20.1 |

---

## מושגי מפתח

| מושג | הסבר |
|---|---|
| **Gradle** | כלי בנייה שמקמפל את המוד, מוריד תלויות ומריץ את המשחק |
| **MDK (Mod Development Kit)** | ערכת הפיתוח של Forge שמכילה את כל הקבצים הנדרשים להתחלה |
| **@Mod** | ה-annotation שמגדירה את מחלקת הכניסה של המוד |
| **MOD_ID** | המזהה הייחודי של המוד — משמש ברשומות (registry) ובמזהה משאבים |
| **IEventBus** | "אוטובוס אירועים" — מעביר אירועים בין Forge למוד |
| **mapping_channel** | קובע אילו שמות שדות/מתודות להשתמש בהם (`official` או `parchment`) |

---

<div dir="ltr">

⬅️ [מבוא](00-Intro-Java-and-Forge) · ➡️ [שלב 02](Step-02-Custom-Items)

</div>

</div>

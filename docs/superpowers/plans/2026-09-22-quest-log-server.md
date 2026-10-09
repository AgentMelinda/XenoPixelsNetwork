# Quest Log (Server Half) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a player hold many quests at once, remember the ones they finished, and hand in quests that require an NPC — which also repairs `MasterPrerequisites` quest gates, currently impossible to satisfy.

**Architecture:** `XenoPlayerData` moves from three scalar quest fields to a bounded map of actives plus a completed set. Every consumer that reads "the" active quest reads the map instead. `QuestDef` gains the fields a journal needs (`category`, `logText`, `completeText`, `completionMode`, `completerNpc`), defaulting so no existing quest changes meaning. NPC-mode quests reach target and wait for hand-in rather than auto-completing.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.248, JUnit 5, Gradle (ModDevGradle).

**Spec:** `docs/superpowers/specs/2026-09-22-quest-log-design.md`

## Global Constraints

- Java 21 · Minecraft 1.21.1 · NeoForge 21.1.248 · mod id `xenopixelsmod`. Verified from `build.gradle` (`JavaLanguageVersion.of(21)`, `options.release = 21`) and `gradle.properties`.
- DragonMineZ jar `libs/dragonminez-2.1.3.jar`, SHA-256 `5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581`. Do not change.
- **No invented APIs.** Every method, field or class must be verified in repository source, tracked decompiled source, or `javap` against the exact configured jar before use.
- **Server owns quest authority.** A client may request; the server validates identity, range, state, ownership and bounds before applying.
- **This plan adds no packets.** The sync packet belongs to the client-half plan, so `ModNetwork` and its protocol version are untouched here.
- **Bounds:** 32 active quests, 512 completed (oldest evicted), `MAX_QUEST_VISITED = 256` per active quest.
- Validation ladder: `gradlew.bat test -PofflineMcMeta` → `gradlew.bat build jarJar serverJar -PofflineMcMeta` → `gradlew.bat buildApiExampleAddon -PofflineMcMeta`. Baseline to beat: **1702 tests, 0 failures**.
- Never `git add -A`. Stage only the paths each task names; the tree carries 378 unrelated untracked paths.

## Review Focus

Five things the spec implies that no task's own happy-path tests would otherwise exercise. Each has its test added to the task that owns the code.

1. **A save whose quest id no longer exists in any pack** — a quest removed from a datapack between sessions must load and be abortable, not throw or wedge the player. (Task 2 — `aQuestWhoseDefinitionVanishedStillLoadsAndCanBeAbandoned`)
2. **Two quests sharing one objective type** — two `KILL_MOBS` quests active together must both advance on one kill, and one completing must not disturb the other's progress. (Task 5 — `twoQuestsWithTheSameGoalBothAdvance`, `completingOneLeavesTheOthersAlone`)
3. **A hand-in quest whose completer NPC no longer exists** — the NPC is deleted while the quest is ready; the quest must stay abortable rather than becoming permanently un-completable with no recourse. (Task 6 — `aQuestWhoseCompleterNoLongerExistsStaysAbandonable`)
4. **`/xenoquest abort` with no id, or an id the player is not on** — the pre-change command took no argument; it must not abort an arbitrary quest or fail silently. (Task 7 — Step 5 replies "You are not on that quest." and the id is required)
5. **A completed quest started again** — completion history must not block a repeat start, because repeat rules are explicitly out of scope and silently refusing would look like a bug. (Task 2 — `aCompletedQuestCanBeStartedAgain`)


> **Execution note (2026-09-22):** every task is implemented and verified. Steps marked `- [-]`
> are the plan's `git commit` steps, deliberately **not** run: AGENTS.md says "Do not commit, tag,
> push, or rewrite history unless the user explicitly requests it." The work sits in the working
> tree instead.

---

## File Structure

| File | Responsibility |
|---|---|
| `features/progression/QuestCompletionMode.java` | **Create.** Two-value enum, `INSTANT` / `NPC`, with lenient parsing. |
| `features/progression/ActiveQuest.java` | **Create.** One in-progress quest: progress, target, ready flag, visited set. Pure; no Minecraft types beyond NBT. |
| `features/progression/QuestBook.java` | **Create.** The player's actives + completed, with all bounds. Pure, fully unit-testable, owns its own NBT. |
| `capability/XenoPlayerData.java` | **Modify.** Delegates quest state to `QuestBook`; keeps the old single-quest accessors as deprecated shims during migration, then drops them. |
| `features/progression/ParallelQuests.java` | **Modify.** `QuestDef` gains five fields; `start`/`status` read the book. |
| `features/progression/XenoQuests.java` | **Modify.** Parse the new JSON fields. |
| `features/progression/ProgressionEvents.java` | **Modify.** Advance every matching active; `completeQuest` takes an id; NPC mode marks ready. |
| `features/progression/MasterPrerequisites.java` | **Modify.** Gate reads `completed`. |
| `compat/npc/NpcPartyQuestObjectives.java` | **Modify.** Share every matching active. |
| `command/ProgressionCommands.java` | **Modify.** `abort <id>`, `status` lists, `log` lists completed. |
| `npc/XenoNpcEntity.java` | **Modify.** Talking to a completer NPC with a ready quest hands it in. |

Tests mirror each under `src/test/java/...`.

---

### Task 1: Quest completion mode

**Files:**
- Create: `src/main/java/net/bullettrain/xenopixelsmod/features/progression/QuestCompletionMode.java`
- Test: `src/test/java/net/bullettrain/xenopixelsmod/features/progression/QuestCompletionModeTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `enum QuestCompletionMode { INSTANT, NPC }`, `static QuestCompletionMode parse(String)` returning `INSTANT` for null/blank/unrecognised.

- [x] **Step 1: Write the failing test**

```java
package net.bullettrain.xenopixelsmod.features.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How a quest finishes.
 *
 * <p>INSTANT is the default on purpose: it is exactly what every quest did before this field
 * existed, so adding the field changes no existing quest's meaning.
 */
class QuestCompletionModeTest {

    @Test
    void anUnknownOrAbsentModeIsInstant() {
        assertEquals(QuestCompletionMode.INSTANT, QuestCompletionMode.parse(null));
        assertEquals(QuestCompletionMode.INSTANT, QuestCompletionMode.parse(""));
        assertEquals(QuestCompletionMode.INSTANT, QuestCompletionMode.parse("  "));
        assertEquals(QuestCompletionMode.INSTANT, QuestCompletionMode.parse("handshake"));
    }

    @Test
    void bothModesParseCaseAndPaddingInsensitively() {
        assertEquals(QuestCompletionMode.NPC, QuestCompletionMode.parse("npc"));
        assertEquals(QuestCompletionMode.NPC, QuestCompletionMode.parse("  NPC "));
        assertEquals(QuestCompletionMode.INSTANT, QuestCompletionMode.parse("Instant"));
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `gradlew.bat test -PofflineMcMeta --tests '*QuestCompletionModeTest*'`
Expected: FAIL — `QuestCompletionMode` does not exist (compile error).

- [x] **Step 3: Write minimal implementation**

```java
package net.bullettrain.xenopixelsmod.features.progression;

import java.util.Locale;

/**
 * How a quest finishes.
 *
 * <p>Mirrors My NPCs' {@code EnumQuestCompletion}, whose quest editor offers exactly two choices -
 * {@code quest.npc} "Complete by npc" and {@code quest.instant} "Instant Complete".
 *
 * <p>{@link #INSTANT} is the default everywhere. It is precisely what every quest in this repo did
 * before this type existed, so a quest that says nothing about completion keeps behaving as it
 * always has.
 */
public enum QuestCompletionMode {

    /** Pays out the moment progress reaches target. */
    INSTANT,

    /**
     * Reaches target and waits to be handed in to a named NPC.
     *
     * <p>A quest in this mode stays active and is marked ready; it is not complete until the player
     * talks to its completer.
     */
    NPC;

    /** Parses a mode. Anything unrecognised is {@link #INSTANT} rather than a failure. */
    public static QuestCompletionMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return INSTANT;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            // A pack that misspells the mode gets the old behaviour, not a broken quest.
            return INSTANT;
        }
    }
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `gradlew.bat test -PofflineMcMeta --tests '*QuestCompletionModeTest*'`
Expected: PASS, 2 tests.

- [-] **Step 5: Commit**

```bash
git add src/main/java/net/bullettrain/xenopixelsmod/features/progression/QuestCompletionMode.java src/test/java/net/bullettrain/xenopixelsmod/features/progression/QuestCompletionModeTest.java
git commit -m "Add quest completion mode, defaulting to today's instant behaviour"
```

---

### Task 2: `ActiveQuest` and `QuestBook`

**Files:**
- Create: `src/main/java/net/bullettrain/xenopixelsmod/features/progression/ActiveQuest.java`
- Create: `src/main/java/net/bullettrain/xenopixelsmod/features/progression/QuestBook.java`
- Test: `src/test/java/net/bullettrain/xenopixelsmod/features/progression/QuestBookTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `ActiveQuest(String id, int target)`; `int progress()`, `int target()`, `boolean ready()`, `String id()`, `boolean addProgress(int)` (true when it reaches target), `void markReady()`, `boolean markVisited(String)`, `boolean hasVisited(String)`, `CompoundTag save()`, `static ActiveQuest load(CompoundTag)`.
  - `QuestBook()`; `boolean isActive(String)`, `ActiveQuest active(String)`, `List<ActiveQuest> actives()`, `String start(String id, int target)` (null on success, else a refusal), `void abandon(String)`, `void complete(String id, long gameTime)`, `boolean hasCompleted(String)`, `long completedAt(String)`, `List<String> completed()`, `void saveTo(CompoundTag)`, `void loadFrom(CompoundTag)`, constants `MAX_ACTIVE = 32`, `MAX_COMPLETED = 512`, `MAX_VISITED = 256`.

- [x] **Step 1: Write the failing test**

```java
package net.bullettrain.xenopixelsmod.features.progression;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The player's quests — many at once, and the ones they have finished.
 *
 * <p>Before this, a player held exactly one quest and finishing it erased every trace. That second
 * part is why `MasterPrerequisites` gates could never pass: the gate wants a quest id that is both
 * matched and complete, and completion blanked the id.
 */
class QuestBookTest {

    @Test
    void manyQuestsRunAtOnce() {
        QuestBook book = new QuestBook();
        assertNull(book.start("kill_mobs", 10));
        assertNull(book.start("wolf_trouble", 5));
        assertEquals(2, book.actives().size());
        assertTrue(book.isActive("kill_mobs"));
        assertTrue(book.isActive("wolf_trouble"));
    }

    @Test
    void startingOneAlreadyRunningIsRefused() {
        QuestBook book = new QuestBook();
        book.start("kill_mobs", 10);
        assertNotNull(book.start("kill_mobs", 10), "a duplicate should say why, not silently reset");
    }

    @Test
    void theActiveCapHolds() {
        QuestBook book = new QuestBook();
        for (int i = 0; i < QuestBook.MAX_ACTIVE; i++) {
            assertNull(book.start("q" + i, 1));
        }
        assertNotNull(book.start("one_too_many", 1));
        assertEquals(QuestBook.MAX_ACTIVE, book.actives().size());
    }

    @Test
    void progressIsPerQuest() {
        QuestBook book = new QuestBook();
        book.start("a", 3);
        book.start("b", 3);
        book.active("a").addProgress(2);
        assertEquals(2, book.active("a").progress());
        assertEquals(0, book.active("b").progress(), "quests must not share a counter");
    }

    @Test
    void visitedSetsAreNotShared() {
        // The whole reason questVisited moved inside each quest. A TALK_TO_NPC quest and a kill
        // quest running together must not consume each other's visited entries.
        QuestBook book = new QuestBook();
        book.start("talk", 3);
        book.start("kill", 3);
        assertTrue(book.active("talk").markVisited("npc-1"));
        assertFalse(book.active("talk").markVisited("npc-1"), "the same NPC counts once");
        assertTrue(book.active("kill").markVisited("npc-1"), "but only for that quest");
    }

    @Test
    void completingMovesItAndRemembersWhen() {
        QuestBook book = new QuestBook();
        book.start("hunt", 1);
        book.complete("hunt", 1234L);
        assertFalse(book.isActive("hunt"));
        assertTrue(book.hasCompleted("hunt"));
        assertEquals(1234L, book.completedAt("hunt"));
    }

    @Test
    void aCompletedQuestCanBeStartedAgain() {
        // Repeat rules are out of scope, so nothing should block a second run. Silently refusing
        // would read as a bug rather than as a rule.
        QuestBook book = new QuestBook();
        book.start("hunt", 1);
        book.complete("hunt", 1L);
        assertNull(book.start("hunt", 1), "history must not block a restart");
        assertTrue(book.isActive("hunt"));
        assertTrue(book.hasCompleted("hunt"), "and the history survives the restart");
    }

    @Test
    void theCompletedCapEvictsOldest() {
        QuestBook book = new QuestBook();
        for (int i = 0; i < QuestBook.MAX_COMPLETED + 10; i++) {
            book.start("q" + i, 1);
            book.complete("q" + i, i);
        }
        assertEquals(QuestBook.MAX_COMPLETED, book.completed().size());
        assertFalse(book.hasCompleted("q0"), "the oldest goes first");
        assertTrue(book.hasCompleted("q" + (QuestBook.MAX_COMPLETED + 9)));
    }

    @Test
    void everythingSurvivesASaveAndLoad() {
        QuestBook book = new QuestBook();
        book.start("a", 5);
        book.active("a").addProgress(2);
        book.active("a").markVisited("npc-1");
        book.start("b", 2);
        book.active("b").markReady();
        book.start("c", 1);
        book.complete("c", 99L);

        CompoundTag tag = new CompoundTag();
        book.saveTo(tag);
        QuestBook reloaded = new QuestBook();
        reloaded.loadFrom(tag);

        assertEquals(2, reloaded.actives().size());
        assertEquals(2, reloaded.active("a").progress());
        assertTrue(reloaded.active("a").hasVisited("npc-1"));
        assertTrue(reloaded.active("b").ready());
        assertTrue(reloaded.hasCompleted("c"));
        assertEquals(99L, reloaded.completedAt("c"));
    }

    @Test
    void aQuestWhoseDefinitionVanishedStillLoadsAndCanBeAbandoned() {
        // A pack can remove a quest between sessions. The book knows nothing about definitions, so
        // the entry must survive as data and stay abortable rather than wedging the player.
        QuestBook book = new QuestBook();
        book.start("removed_by_a_pack", 4);
        CompoundTag tag = new CompoundTag();
        book.saveTo(tag);

        QuestBook reloaded = new QuestBook();
        reloaded.loadFrom(tag);
        assertTrue(reloaded.isActive("removed_by_a_pack"));
        reloaded.abandon("removed_by_a_pack");
        assertFalse(reloaded.isActive("removed_by_a_pack"));
    }

    @Test
    void loadReplacesRatherThanMerges() {
        QuestBook book = new QuestBook();
        book.start("stale", 1);
        book.loadFrom(new CompoundTag());
        assertFalse(book.isActive("stale"), "loading an empty tag must clear, not keep");
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `gradlew.bat test -PofflineMcMeta --tests '*QuestBookTest*'`
Expected: FAIL — `ActiveQuest` / `QuestBook` do not exist.

- [x] **Step 3: Write `ActiveQuest`**

```java
package net.bullettrain.xenopixelsmod.features.progression;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One quest a player is part-way through.
 *
 * <p>Its {@code visited} set belongs to <em>this</em> quest. It used to be one set on the player,
 * which is correct for one quest and wrong for several: a "speak to three masters" quest and a kill
 * quest running together would have consumed each other's entries.
 */
public final class ActiveQuest {

    private final String id;
    private final int target;
    private int progress;
    private boolean ready;
    private final Set<String> visited = new LinkedHashSet<>();

    public ActiveQuest(String id, int target) {
        this.id = id == null ? "" : id;
        this.target = Math.max(1, target);
    }

    public String id() {
        return id;
    }

    public int progress() {
        return progress;
    }

    public int target() {
        return target;
    }

    /** Whether this quest has reached its target and is waiting to be handed in. */
    public boolean ready() {
        return ready;
    }

    public void markReady() {
        ready = true;
    }

    /**
     * Advances this quest.
     *
     * @return true when this call reached the target
     */
    public boolean addProgress(int amount) {
        if (progress >= target) {
            return false;
        }
        progress = Math.min(target, progress + Math.max(0, amount));
        return progress >= target;
    }

    /**
     * Records that this quest has counted {@code key}.
     *
     * @return true the first time only, so a caller counts it once
     */
    public boolean markVisited(String key) {
        if (key == null || key.isEmpty() || visited.size() >= QuestBook.MAX_VISITED) {
            return false;
        }
        return visited.add(key);
    }

    public boolean hasVisited(String key) {
        return key != null && visited.contains(key);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putInt("Target", target);
        tag.putInt("Progress", progress);
        tag.putBoolean("Ready", ready);
        ListTag seen = new ListTag();
        for (String key : visited) {
            seen.add(StringTag.valueOf(key));
        }
        tag.put("Visited", seen);
        return tag;
    }

    /** Reads one quest, or null when the tag names none. */
    public static ActiveQuest load(CompoundTag tag) {
        if (tag == null || tag.getString("Id").isEmpty()) {
            return null;
        }
        ActiveQuest quest = new ActiveQuest(tag.getString("Id"), tag.getInt("Target"));
        quest.progress = Math.min(quest.target, Math.max(0, tag.getInt("Progress")));
        quest.ready = tag.getBoolean("Ready");
        ListTag seen = tag.getList("Visited", Tag.TAG_STRING);
        for (int i = 0; i < Math.min(QuestBook.MAX_VISITED, seen.size()); i++) {
            quest.visited.add(seen.getString(i));
        }
        return quest;
    }
}
```

- [x] **Step 4: Write `QuestBook`**

```java
package net.bullettrain.xenopixelsmod.features.progression;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every quest a player is on, and every one they have finished.
 *
 * <p>Deliberately pure - no player, no server, no Minecraft beyond NBT - so all of its rules can be
 * tested without a running game. {@code XenoPlayerData} owns one of these and does nothing with
 * quests itself.
 *
 * <p>Completed ids are kept because {@code MasterPrerequisites} needs them. Its gate requires a
 * quest to be both named and finished, and before this the id was erased at the moment of
 * completion, so the gate could never pass at any point in time.
 */
public final class QuestBook {

    /** More than this and the log stops being a log. Also bounds the client sync payload. */
    public static final int MAX_ACTIVE = 32;

    /** Completed ids live on disk forever; without a ceiling a long world grows without limit. */
    public static final int MAX_COMPLETED = 512;

    /** Per active quest. Was one player-wide set before quests could run together. */
    public static final int MAX_VISITED = 256;

    private final Map<String, ActiveQuest> active = new LinkedHashMap<>();
    private final Map<String, Long> completed = new LinkedHashMap<>();

    private static String key(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isActive(String id) {
        return active.containsKey(key(id));
    }

    /** One active quest, or null. */
    public ActiveQuest active(String id) {
        return active.get(key(id));
    }

    /** Every active quest, in the order they were started. */
    public List<ActiveQuest> actives() {
        return List.copyOf(active.values());
    }

    /**
     * Starts a quest.
     *
     * @return null on success, or why it was refused
     */
    public String start(String id, int target) {
        String wanted = key(id);
        if (wanted.isEmpty()) {
            return "that is not a quest id";
        }
        if (active.containsKey(wanted)) {
            return "you are already on " + wanted;
        }
        if (active.size() >= MAX_ACTIVE) {
            return "you are already on " + MAX_ACTIVE + " quests; finish or abandon one first";
        }
        active.put(wanted, new ActiveQuest(wanted, target));
        return null;
    }

    /** Drops a quest without completing it. Its history, if any, is untouched. */
    public void abandon(String id) {
        active.remove(key(id));
    }

    /** Moves a quest from active to completed, remembering when. */
    public void complete(String id, long gameTime) {
        String wanted = key(id);
        active.remove(wanted);
        if (wanted.isEmpty()) {
            return;
        }
        // Re-inserted so a repeat completion moves to the back of the eviction order.
        completed.remove(wanted);
        completed.put(wanted, gameTime);
        Iterator<String> oldest = completed.keySet().iterator();
        while (completed.size() > MAX_COMPLETED && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }

    public boolean hasCompleted(String id) {
        return completed.containsKey(key(id));
    }

    /** When a quest was completed, or 0 when it never was. */
    public long completedAt(String id) {
        return completed.getOrDefault(key(id), 0L);
    }

    /** Completed ids, oldest first. */
    public List<String> completed() {
        return List.copyOf(completed.keySet());
    }

    public void saveTo(CompoundTag tag) {
        ListTag list = new ListTag();
        for (ActiveQuest quest : active.values()) {
            list.add(quest.save());
        }
        tag.put("ActiveQuests", list);

        CompoundTag done = new CompoundTag();
        completed.forEach(done::putLong);
        tag.put("CompletedQuests", done);
    }

    /** Replaces everything. An empty tag empties the book rather than leaving it stale. */
    public void loadFrom(CompoundTag tag) {
        active.clear();
        completed.clear();
        if (tag == null) {
            return;
        }
        ListTag list = tag.getList("ActiveQuests", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(MAX_ACTIVE, list.size()); i++) {
            ActiveQuest quest = ActiveQuest.load(list.getCompound(i));
            if (quest != null) {
                active.put(quest.id(), quest);
            }
        }
        CompoundTag done = tag.getCompound("CompletedQuests");
        List<String> keys = new ArrayList<>(done.getAllKeys());
        for (int i = Math.max(0, keys.size() - MAX_COMPLETED); i < keys.size(); i++) {
            completed.put(keys.get(i), done.getLong(keys.get(i)));
        }
    }
}
```

- [x] **Step 5: Run test to verify it passes**

Run: `gradlew.bat test -PofflineMcMeta --tests '*QuestBookTest*'`
Expected: PASS, 11 tests.

- [-] **Step 6: Commit**

```bash
git add src/main/java/net/bullettrain/xenopixelsmod/features/progression/ActiveQuest.java src/main/java/net/bullettrain/xenopixelsmod/features/progression/QuestBook.java src/test/java/net/bullettrain/xenopixelsmod/features/progression/QuestBookTest.java
git commit -m "Add QuestBook: many active quests, completed history, per-quest visited sets"
```

---

### Task 3: Player data adopts the book, and old saves migrate

**Files:**
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/capability/XenoPlayerData.java`
- Test: `src/test/java/net/bullettrain/xenopixelsmod/capability/XenoPlayerQuestMigrationTest.java`

**Interfaces:**
- Consumes: `QuestBook`, `ActiveQuest` (Task 2).
- Produces: `XenoPlayerData.quests()` returning the `QuestBook`. The old `getQuestId()`, `getQuestProgress()`, `getQuestTarget()`, `hasActiveQuest()`, `startQuest`, `clearQuest`, `addQuestProgress`, `markQuestVisited`, `hasQuestVisited` are **removed** — Tasks 4–7 update every caller.

> The full list of callers, from `grep -rn "hasActiveQuest()\|getQuestId()\|getQuestProgress()\|getQuestTarget()\|startQuest(\|clearQuest()\|addQuestProgress(" src/main/java`: `ProgressionCommands:487`, `NpcPartyQuestObjectives:34,35,36,37,50`, `MasterPrerequisites:89,90`, `ParallelQuests:109-113,122,123`, `ProgressionEvents:115,116,119,123,124,149,153,158,162,184,187,197,201,217,219`. `DojoHubManager`'s `getQuestId()` is a **different class's own method** and must not be touched.

- [x] **Step 1: Write the failing test**

```java
package net.bullettrain.xenopixelsmod.capability;

import net.bullettrain.xenopixelsmod.features.progression.QuestBook;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A save written before quests could run together still loads.
 *
 * <p>The old shape was three scalars plus one player-wide visited set. Losing a player's
 * in-progress quest on upgrade would be a silent data loss, so the old keys are read once and
 * folded into the book.
 */
class XenoPlayerQuestMigrationTest {

    /** A save in the pre-book shape. */
    private static CompoundTag legacySave() {
        CompoundTag tag = new CompoundTag();
        tag.putString("QuestId", "kill_mobs");
        tag.putInt("QuestProgress", 4);
        tag.putInt("QuestTarget", 10);
        ListTag visited = new ListTag();
        visited.add(StringTag.valueOf("npc-1"));
        tag.put("QuestVisited", visited);
        return tag;
    }

    @Test
    void anOldSingleQuestBecomesOneActiveQuest() {
        XenoPlayerData data = new XenoPlayerData();
        data.loadNBT(legacySave());

        QuestBook book = data.quests();
        assertEquals(1, book.actives().size());
        assertTrue(book.isActive("kill_mobs"));
        assertEquals(4, book.active("kill_mobs").progress());
        assertEquals(10, book.active("kill_mobs").target());
        assertTrue(book.active("kill_mobs").hasVisited("npc-1"),
                "the old player-wide visited set belongs to the quest that was running");
    }

    @Test
    void anOldSaveWithNoQuestLoadsEmpty() {
        XenoPlayerData data = new XenoPlayerData();
        data.loadNBT(new CompoundTag());
        assertTrue(data.quests().actives().isEmpty());
        assertTrue(data.quests().completed().isEmpty());
    }

    @Test
    void theNextSaveWritesTheNewShapeAndDropsTheOldKeys() {
        XenoPlayerData data = new XenoPlayerData();
        data.loadNBT(legacySave());

        CompoundTag written = new CompoundTag();
        data.saveNBT(written);
        assertTrue(written.contains("ActiveQuests"));
        assertFalse(written.contains("QuestId"), "the old shape is not written back");
        assertFalse(written.contains("QuestProgress"));
        assertFalse(written.contains("QuestVisited"));
    }

    @Test
    void theBookRoundTripsThroughPlayerData() {
        XenoPlayerData data = new XenoPlayerData();
        data.quests().start("a", 3);
        data.quests().active("a").addProgress(1);
        data.quests().start("b", 1);
        data.quests().complete("b", 77L);

        CompoundTag tag = new CompoundTag();
        data.saveNBT(tag);
        XenoPlayerData reloaded = new XenoPlayerData();
        reloaded.loadNBT(tag);

        assertEquals(1, reloaded.quests().active("a").progress());
        assertTrue(reloaded.quests().hasCompleted("b"));
    }

    @Test
    void questsSurviveTheDeathCopy() {
        // copyFrom runs on respawn and on the end-return clone. A player losing every quest on
        // death would be worse than losing the inventory they keep.
        XenoPlayerData before = new XenoPlayerData();
        before.quests().start("a", 3);
        before.quests().active("a").addProgress(2);
        before.quests().start("done", 1);
        before.quests().complete("done", 5L);

        XenoPlayerData after = new XenoPlayerData();
        after.copyFrom(before);

        assertEquals(2, after.quests().active("a").progress());
        assertTrue(after.quests().hasCompleted("done"));
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `gradlew.bat test -PofflineMcMeta --tests '*XenoPlayerQuestMigrationTest*'`
Expected: FAIL — `quests()` does not exist.

- [x] **Step 3: Replace the quest fields with the book**

In `XenoPlayerData.java`, delete the fields `questId`, `questProgress`, `questTarget`, `questVisited`, the constant `MAX_QUEST_VISITED`, and the methods `getQuestId`, `getQuestProgress`, `getQuestTarget`, `hasActiveQuest`, `startQuest`, `clearQuest`, `addQuestProgress`, `markQuestVisited`, `hasQuestVisited`. Add:

```java
    /**
     * Every quest this player is on, and every one they have finished.
     *
     * <p>Replaces the single {@code questId}/{@code questProgress}/{@code questTarget} trio. The
     * player could hold exactly one quest, which stopped making sense once a datapack or the world
     * store could define any number of them.
     */
    private final QuestBook quests = new QuestBook();

    public QuestBook quests() {
        return quests;
    }
```

Add the import `net.bullettrain.xenopixelsmod.features.progression.QuestBook;`.

- [x] **Step 4: Save, load with migration, and copy**

In `saveNBT`, delete the three `tag.put*("Quest*", …)` lines and the `QuestVisited` list block, and add:

```java
        quests.saveTo(tag);
```

In `loadNBT`, delete the `questId` / `questProgress` / `questTarget` reads and the `QuestVisited` block, and add:

```java
        quests.loadFrom(tag);
        migrateLegacyQuest(tag);
```

Add the method:

```java
    /**
     * Folds a pre-book save into the book.
     *
     * <p>The old shape held one quest as three scalars plus a player-wide visited set. Dropping it
     * would silently lose whatever the player was part-way through, so it is read once here and the
     * old keys are never written again.
     */
    private void migrateLegacyQuest(CompoundTag tag) {
        if (!tag.contains("QuestId") || tag.getString("QuestId").isEmpty()) {
            return;
        }
        String id = tag.getString("QuestId");
        int target = tag.getInt("QuestTarget");
        if (target <= 0 || quests.isActive(id)) {
            return;
        }
        quests.start(id, target);
        ActiveQuest quest = quests.active(id);
        quest.addProgress(tag.getInt("QuestProgress"));
        ListTag visited = tag.getList("QuestVisited", Tag.TAG_STRING);
        for (int i = 0; i < visited.size(); i++) {
            // The old set belonged to whichever quest was running, which is this one.
            quest.markVisited(visited.getString(i));
        }
    }
```

Add the import `net.bullettrain.xenopixelsmod.features.progression.ActiveQuest;`.

In `copyFrom`, delete the three `this.quest* = other.quest*` lines and the `questVisited` copy, and add:

```java
        CompoundTag carried = new CompoundTag();
        other.quests.saveTo(carried);
        this.quests.loadFrom(carried);
```

- [x] **Step 5: Run test to verify it passes**

Run: `gradlew.bat test -PofflineMcMeta --tests '*XenoPlayerQuestMigrationTest*'`
Expected: PASS, 5 tests. Other modules still fail to compile — Tasks 4–7 fix them.

- [-] **Step 6: Commit**

```bash
git add src/main/java/net/bullettrain/xenopixelsmod/capability/XenoPlayerData.java src/test/java/net/bullettrain/xenopixelsmod/capability/XenoPlayerQuestMigrationTest.java
git commit -m "Move player quest state into QuestBook, migrating old single-quest saves"
```

---

### Task 4: Quest definitions carry the journal fields

**Files:**
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/features/progression/ParallelQuests.java`
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/features/progression/XenoQuests.java`
- Modify: `src/test/java/net/bullettrain/xenopixelsmod/features/progression/QuestRewardTest.java`
- Test: `src/test/java/net/bullettrain/xenopixelsmod/features/progression/QuestDefinitionFieldsTest.java`

**Interfaces:**
- Consumes: `QuestCompletionMode` (Task 1), `QuestBook` (Task 2).
- Produces: `QuestDef(String id, String title, String desc, int target, QuestObjective.Goal goal, QuestReward reward, String category, String logText, String completeText, QuestCompletionMode completionMode, String completerNpc)`, plus the existing short constructors. `ParallelQuests.start(ServerPlayer, String)` now starts into the book.

- [x] **Step 1: Write the failing test**

```java
package net.bullettrain.xenopixelsmod.features.progression;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a quest definition carries for the journal.
 *
 * <p>Every field defaults so that a quest written before they existed behaves exactly as it did:
 * blank category, no log text, and INSTANT completion, which is the old auto-complete path.
 */
class QuestDefinitionFieldsTest {

    @Test
    void aQuestThatDeclaresNoneOfTheNewFieldsIsUnchanged() {
        ParallelQuests.QuestDef def = XenoQuests.parse("plain",
                JsonParser.parseString("{ \"target\": 3 }"));
        assertEquals("", def.category());
        assertEquals("", def.logText());
        assertEquals("", def.completeText());
        assertEquals(QuestCompletionMode.INSTANT, def.completionMode());
        assertEquals("", def.completerNpc());
    }

    @Test
    void allNewFieldsParse() {
        ParallelQuests.QuestDef def = XenoQuests.parse("gold", JsonParser.parseString("""
                {
                  "title": "Gold",
                  "description": "Bring Stephanie something shiny",
                  "target": 10,
                  "category": "Main",
                  "log_text": "Stephanie has been asking around for something shiny.",
                  "complete_text": "I could make some earrings with this.",
                  "completion": "npc",
                  "completer_npc": "Stephanie"
                }
                """));
        assertEquals("Main", def.category());
        assertTrue(def.logText().startsWith("Stephanie has been"));
        assertEquals("I could make some earrings with this.", def.completeText());
        assertEquals(QuestCompletionMode.NPC, def.completionMode());
        assertEquals("Stephanie", def.completerNpc());
    }

    @Test
    void anNpcModeQuestWithNoCompleterFallsBackToInstant() {
        // Otherwise it would reach target and wait forever for an NPC that was never named.
        ParallelQuests.QuestDef def = XenoQuests.parse("bad",
                JsonParser.parseString("{ \"target\": 1, \"completion\": \"npc\" }"));
        assertEquals(QuestCompletionMode.INSTANT, def.completionMode());
    }

    @Test
    void theShippedPackQuestsStillParse() {
        // wolf_trouble and pay_respects predate these fields.
        assertEquals(QuestCompletionMode.INSTANT,
                ParallelQuests.definition("wolf_trouble").completionMode());
        assertEquals(QuestCompletionMode.INSTANT,
                ParallelQuests.definition("pay_respects").completionMode());
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `gradlew.bat test -PofflineMcMeta --tests '*QuestDefinitionFieldsTest*'`
Expected: FAIL — `category()` does not exist on `QuestDef`.

- [x] **Step 3: Widen `QuestDef`**

Replace the record header and compact constructor in `ParallelQuests.java`:

```java
    public record QuestDef(String id, String title, String desc, int target,
                           QuestObjective.Goal goal, QuestReward reward,
                           String category, String logText, String completeText,
                           QuestCompletionMode completionMode, String completerNpc) {
        public QuestDef {
            reward = reward == null ? QuestReward.DEFAULT : reward;
            goal = goal == null ? new QuestObjective.Goal(QuestObjective.KILL_MOBS) : goal;
            category = category == null ? "" : category.trim();
            logText = logText == null ? "" : logText;
            completeText = completeText == null ? "" : completeText;
            completerNpc = completerNpc == null ? "" : completerNpc.trim();
            // A quest that asks to be handed in but names nobody would sit at full progress
            // forever, so it keeps the behaviour it would have had without the field.
            completionMode = completionMode == QuestCompletionMode.NPC && !completerNpc.isEmpty()
                    ? QuestCompletionMode.NPC : QuestCompletionMode.INSTANT;
        }

        /** A quest that pays the long-standing default and counts any hostile kill. */
        public QuestDef(String id, String title, String desc, int target) {
            this(id, title, desc, target, null, QuestReward.DEFAULT);
        }

        /** A quest with a goal and reward but none of the journal fields. */
        public QuestDef(String id, String title, String desc, int target,
                        QuestObjective.Goal goal, QuestReward reward) {
            this(id, title, desc, target, goal, reward, "", "", "",
                    QuestCompletionMode.INSTANT, "");
        }
    }
```

- [x] **Step 4: Parse the fields and start into the book**

In `XenoQuests.parse`, before the `return`:

```java
        String category = root.has("category") ? root.get("category").getAsString() : "";
        String logText = root.has("log_text") ? root.get("log_text").getAsString() : "";
        String completeText = root.has("complete_text")
                ? root.get("complete_text").getAsString() : "";
        String completerNpc = root.has("completer_npc")
                ? root.get("completer_npc").getAsString() : "";
        QuestCompletionMode completion = QuestCompletionMode.parse(
                root.has("completion") ? root.get("completion").getAsString() : null);
```

and change the return to:

```java
        return new ParallelQuests.QuestDef(id, title, description, target, goal,
                QuestReward.fromJson(root.get("reward")),
                category, logText, completeText, completion, completerNpc);
```

In `ParallelQuests.start`, replace the `hasActiveQuest` block and the `startQuest` call:

```java
        String refusal = data.quests().start(def.id(), def.target());
        if (refusal != null) {
            return refusal;
        }
```

Replace `status`:

```java
    public static String status(ServerPlayer player) {
        XenoPlayerData data = XenoCapabilities.get(player).orElse(null);
        if (data == null) return "No player data";
        List<ActiveQuest> actives = data.quests().actives();
        if (actives.isEmpty()) return "No active quest. /xenoquest start <id>";
        StringBuilder out = new StringBuilder();
        for (ActiveQuest quest : actives) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(quest.id()).append(": ")
                    .append(quest.progress()).append('/').append(quest.target());
            if (quest.ready()) {
                QuestDef def = definition(quest.id());
                out.append(" — ready, complete with ")
                        .append(def == null ? "an NPC" : def.completerNpc());
            }
        }
        return out.toString();
    }
```

Add imports `java.util.List` and `net.minecraft.server.level.ServerPlayer` if absent.

- [x] **Step 5: Update the existing arity test**

In `QuestRewardTest.aQuestThatDeclaresNoRewardGetsTheDefault`, the six-argument call still compiles unchanged. Verify by running it.

- [x] **Step 6: Run tests to verify they pass**

Run: `gradlew.bat test -PofflineMcMeta --tests '*QuestDefinitionFieldsTest*' --tests '*QuestRewardTest*' --tests '*QuestObjectiveTest*'`
Expected: PASS.

- [-] **Step 7: Commit**

```bash
git add src/main/java/net/bullettrain/xenopixelsmod/features/progression/ParallelQuests.java src/main/java/net/bullettrain/xenopixelsmod/features/progression/XenoQuests.java src/test/java/net/bullettrain/xenopixelsmod/features/progression/QuestDefinitionFieldsTest.java
git commit -m "Give quests a category, log text, and completion mode"
```

---

### Task 5: Every matching quest advances, and NPC quests wait

**Files:**
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/features/progression/ProgressionEvents.java`
- Test: `src/test/java/net/bullettrain/xenopixelsmod/features/progression/QuestAdvanceTest.java`

**Interfaces:**
- Consumes: `QuestBook`, `ActiveQuest`, `QuestCompletionMode`, the widened `QuestDef`.
- Produces: `ProgressionEvents.completeQuest(ServerPlayer, XenoPlayerData, String questId)` — now takes the id explicitly, since "the" active quest no longer exists. `ProgressionEvents.advanceAll(XenoPlayerData, java.util.function.ToIntFunction<ActiveQuest>)` is **not** introduced; each handler loops directly, because their match rules differ.

- [x] **Step 1: Write the failing test**

```java
package net.bullettrain.xenopixelsmod.features.progression;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One event advances every quest that asked for it.
 *
 * <p>The handlers took "the" active quest and advanced it. With several running, advancing only the
 * first would leave the rest permanently stuck with no visible reason.
 */
class QuestAdvanceTest {

    private static String events() throws IOException {
        return Files.readString(RepoRoot.of(
                "src/main/java/net/bullettrain/xenopixelsmod/features/progression",
                "ProgressionEvents.java"), StandardCharsets.UTF_8);
    }

    @Test
    void twoQuestsWithTheSameGoalBothAdvance() {
        QuestBook book = new QuestBook();
        book.start("hunt_a", 3);
        book.start("hunt_b", 3);

        // What the handler does per kill: every active quest whose goal matches gets one step.
        for (ActiveQuest quest : book.actives()) {
            quest.addProgress(1);
        }
        assertEquals(1, book.active("hunt_a").progress());
        assertEquals(1, book.active("hunt_b").progress());
    }

    @Test
    void completingOneLeavesTheOthersAlone() {
        QuestBook book = new QuestBook();
        book.start("a", 1);
        book.start("b", 5);
        book.active("b").addProgress(2);
        book.complete("a", 1L);

        assertFalse(book.isActive("a"));
        assertEquals(2, book.active("b").progress(), "the survivor keeps its progress");
    }

    @Test
    void theHandlersLoopOverActivesRatherThanReadingOne() throws IOException {
        String source = events();
        assertFalse(source.contains("data.hasActiveQuest()"),
                "the single-quest read is what this replaced");
        assertFalse(source.contains("data.getQuestId()"));
        assertTrue(source.contains("for (ActiveQuest quest : data.quests().actives())"),
                "each handler should walk every active quest");
    }

    @Test
    void completeQuestTakesAnExplicitId() throws IOException {
        // It used to read the id off the player, which only worked while there was exactly one.
        assertTrue(events().contains(
                "public static void completeQuest(ServerPlayer player, XenoPlayerData data, String id)"));
    }

    @Test
    void anNpcModeQuestIsMarkedReadyRatherThanCompleted() throws IOException {
        String source = events();
        assertTrue(source.contains("QuestCompletionMode.NPC"),
                "the handler must branch on completion mode");
        assertTrue(source.contains("quest.markReady()"),
                "and hold the quest open for hand-in");
    }

    @Test
    void aReadyQuestStopsAccumulatingProgress() {
        // Otherwise a quest waiting for hand-in would keep counting kills it will never use.
        ActiveQuest quest = new ActiveQuest("a", 2);
        assertTrue(quest.addProgress(2));
        quest.markReady();
        assertFalse(quest.addProgress(1), "at target, further progress is not a completion");
        assertEquals(2, quest.progress());
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `gradlew.bat test -PofflineMcMeta --tests '*QuestAdvanceTest*'`
Expected: FAIL — the source assertions fail; `ProgressionEvents` still reads one quest.

- [x] **Step 3: Rewrite the three handlers**

In `handleDummyHit`, replace the `if (XenoServerConfig.parallelQuestEnabled && data.hasActiveQuest())` block with:

```java
            if (XenoServerConfig.parallelQuestEnabled) {
                for (ActiveQuest quest : data.quests().actives()) {
                    if (quest.ready()) {
                        continue;
                    }
                    QuestObjective.Goal goal = ParallelQuests.goalFor(quest.id());
                    int step = goal == null ? 0 : goal.dummyProgress(amount);
                    if (step <= 0) {
                        continue;
                    }
                    if (quest.addProgress(step)) {
                        finish(atk, data, quest);
                    } else if (hits % 5 == 0) {
                        atk.displayClientMessage(Component.literal(
                                "§bQuest §7" + quest.progress() + "/" + quest.target()), true);
                    }
                }
            }
```

In `onDeath`, replace the body of the `ifPresent` lambda with:

```java
            for (ActiveQuest quest : data.quests().actives()) {
                if (quest.ready()) {
                    continue;
                }
                QuestObjective.Goal goal = ParallelQuests.goalFor(quest.id());
                if (goal == null
                        || !goal.countsKill(event.getEntity(), isTrainingDummy(event.getEntity()))) {
                    continue;
                }
                if (quest.addProgress(1)) {
                    finish(killer, data, quest);
                } else {
                    killer.displayClientMessage(Component.literal(
                            "§bQuest §7" + quest.progress() + "/" + quest.target()), true);
                }
            }
```

In `onTalkedToNpc`, replace the body of the `ifPresent` lambda with:

```java
            for (ActiveQuest quest : data.quests().actives()) {
                if (quest.ready()) {
                    continue;
                }
                QuestObjective.Goal goal = ParallelQuests.goalFor(quest.id());
                if (goal == null || !goal.countsTalk(npcName, roleId)) {
                    continue;
                }
                // Counted once per NPC, per quest. "Speak to three masters" has to mean three
                // masters, and two such quests must not consume each other's entries.
                if (npcId == null || !quest.markVisited(npcId.toString())) {
                    continue;
                }
                if (quest.addProgress(1)) {
                    finish(player, data, quest);
                } else {
                    player.displayClientMessage(Component.literal(
                            "§bQuest §7" + quest.progress() + "/" + quest.target()), true);
                }
            }
```

- [x] **Step 4: Add the finish/complete split**

```java
    /**
     * A quest has reached its target.
     *
     * <p>An INSTANT quest pays out here, exactly as every quest used to. An NPC-mode quest is held
     * open and marked ready instead: it is handed in by talking to its completer, and only then
     * pays. That is the one behavioural change in this work.
     */
    private static void finish(ServerPlayer player, XenoPlayerData data, ActiveQuest quest) {
        ParallelQuests.QuestDef def = ParallelQuests.definition(quest.id());
        if (def != null && def.completionMode() == QuestCompletionMode.NPC) {
            quest.markReady();
            player.displayClientMessage(Component.literal(
                    "§bQuest ready: §f" + def.title() + " §7— complete with "
                            + def.completerNpc()), false);
            return;
        }
        completeQuest(player, data, quest.id());
    }

    /**
     * Ends one quest and pays what it is worth.
     *
     * <p>Takes the id explicitly. It used to read "the" active quest off the player, which only
     * worked while a player could hold exactly one.
     */
    public static void completeQuest(ServerPlayer player, XenoPlayerData data, String id) {
        QuestReward reward = ParallelQuests.rewardFor(id);
        data.quests().complete(id, player.level().getGameTime());

        if (reward.skillPoints() > 0) {
            data.addSkillPoints(reward.skillPoints());
        }
        String paid = reward.grant(player);

        ParallelQuests.QuestDef def = ParallelQuests.definition(id);
        if (def != null && !def.completeText().isEmpty()) {
            player.displayClientMessage(Component.literal("§f" + def.completeText()), false);
        }
        player.displayClientMessage(Component.literal(
                "§a§lQuest complete: §f" + id + " §7(" + paid + ")"), false);
        player.displayClientMessage(Component.literal(
                "§7Skill points: §f" + data.getSkillPoints()
                        + " §7— /xenoskill unlock <power|guard|sparking|ultimate>"), false);
    }
```

Add imports for `ActiveQuest` and `QuestCompletionMode`.

- [x] **Step 5: Run test to verify it passes**

Run: `gradlew.bat test -PofflineMcMeta --tests '*QuestAdvanceTest*'`
Expected: PASS, 6 tests.

- [-] **Step 6: Commit**

```bash
git add src/main/java/net/bullettrain/xenopixelsmod/features/progression/ProgressionEvents.java src/test/java/net/bullettrain/xenopixelsmod/features/progression/QuestAdvanceTest.java
git commit -m "Advance every matching quest; hold NPC-mode quests for hand-in"
```

---

### Task 6: Hand-in at the NPC

**Files:**
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/npc/XenoNpcEntity.java`
- Test: `src/test/java/net/bullettrain/xenopixelsmod/npc/QuestHandInTest.java`

**Interfaces:**
- Consumes: `ProgressionEvents.completeQuest(ServerPlayer, XenoPlayerData, String)`, `QuestBook`, `QuestCompletionMode`.
- Produces: `ProgressionEvents.tryHandIn(ServerPlayer, String npcName, String roleId)` returning `true` when a quest was handed in, so the NPC does not also speak a line.

- [x] **Step 1: Write the failing test**

```java
package net.bullettrain.xenopixelsmod.npc;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.features.progression.ActiveQuest;
import net.bullettrain.xenopixelsmod.features.progression.QuestBook;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Handing a finished quest to the NPC that wanted it.
 *
 * <p>The server decides. A client cannot say "I completed this" - talking to an NPC is the request,
 * and the server checks the quest is active, ready, in NPC mode, and that this NPC is its completer.
 */
class QuestHandInTest {

    private static String source(String dir, String file) throws IOException {
        return Files.readString(RepoRoot.of(dir, file), StandardCharsets.UTF_8);
    }

    @Test
    void onlyAReadyQuestCanBeHandedIn() {
        QuestBook book = new QuestBook();
        book.start("gold", 10);
        assertFalse(book.active("gold").ready(), "a quest short of target is not ready");
        book.active("gold").addProgress(10);
        book.active("gold").markReady();
        assertTrue(book.active("gold").ready());
    }

    @Test
    void theServerChecksTheNpcMatchesTheCompleter() throws IOException {
        String events = source("src/main/java/net/bullettrain/xenopixelsmod/features/progression",
                "ProgressionEvents.java");
        assertTrue(events.contains("public static boolean tryHandIn("),
                "hand-in should be a server-side method");
        assertTrue(events.contains("completerNpc()"),
                "and must compare against the quest's named completer");
        assertTrue(events.contains("quest.ready()"), "and require the quest to be ready");
    }

    @Test
    void handingInIsTriedBeforeTheNpcSpeaks() throws IOException {
        // Otherwise the NPC would deliver an ambient line instead of taking the quest.
        String entity = source("src/main/java/net/bullettrain/xenopixelsmod/npc",
                "XenoNpcEntity.java");
        int handIn = entity.indexOf("tryHandIn(");
        int speak = entity.indexOf("XenoNpcSpeech.speak(");
        assertTrue(handIn >= 0 && speak >= 0);
        assertTrue(handIn < speak, "hand-in comes first");
    }

    @Test
    void aQuestWhoseCompleterNoLongerExistsStaysAbandonable() {
        // The NPC can be deleted while the quest is ready. The player must not be stuck holding a
        // quest that can never be completed and never be dropped.
        QuestBook book = new QuestBook();
        book.start("gold", 1);
        book.active("gold").addProgress(1);
        book.active("gold").markReady();
        book.abandon("gold");
        assertFalse(book.isActive("gold"));
    }

    @Test
    void aReadyQuestPaysOnlyOnce() {
        QuestBook book = new QuestBook();
        book.start("gold", 1);
        book.active("gold").markReady();
        book.complete("gold", 5L);
        assertFalse(book.isActive("gold"), "it leaves the active list, so a second hand-in finds nothing");
        assertTrue(book.hasCompleted("gold"));
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `gradlew.bat test -PofflineMcMeta --tests '*QuestHandInTest*'`
Expected: FAIL — `tryHandIn` does not exist.

- [x] **Step 3: Add `tryHandIn` to `ProgressionEvents`**

```java
    /**
     * Hands in any ready quest this NPC is the completer for.
     *
     * <p>Called from the NPC's own interaction, before it says anything: an NPC that answered a
     * finished quest with an ambient line would look like it had not noticed.
     *
     * <p>Every check is server-side. The player does not say which quest - the server looks at what
     * they are actually holding, whether it is actually ready, and whether this NPC is actually its
     * completer.
     *
     * @return true when something was handed in, so the caller does not also speak
     */
    public static boolean tryHandIn(ServerPlayer player, String npcName, String roleId) {
        if (player == null || !XenoServerConfig.parallelQuestEnabled) {
            return false;
        }
        XenoPlayerData data = XenoCapabilities.get(player).orElse(null);
        if (data == null) {
            return false;
        }
        for (ActiveQuest quest : data.quests().actives()) {
            if (!quest.ready()) {
                continue;
            }
            ParallelQuests.QuestDef def = ParallelQuests.definition(quest.id());
            if (def == null || def.completionMode() != QuestCompletionMode.NPC) {
                continue;
            }
            String completer = def.completerNpc();
            if (completer.isEmpty()
                    || !(completer.equalsIgnoreCase(npcName) || completer.equalsIgnoreCase(roleId))) {
                continue;
            }
            completeQuest(player, data, quest.id());
            return true;
        }
        return false;
    }
```

- [x] **Step 4: Call it from the NPC**

In `XenoNpcEntity.mobInteract`, inside the existing `if (!level().isClientSide())` block, immediately after the `onTalkedToNpc` call and before the dialogue/line/readout chain:

```java
            // Before anything else the NPC might say: a finished quest is handed in here, and an
            // NPC that answered one with an ambient line would look like it had not noticed.
            if (player instanceof net.minecraft.server.level.ServerPlayer handing
                    && !player.isShiftKeyDown()
                    && net.bullettrain.xenopixelsmod.features.progression.ProgressionEvents
                            .tryHandIn(handing, getName().getString(), role().id())) {
                return InteractionResult.sidedSuccess(level().isClientSide());
            }
```

- [x] **Step 5: Run test to verify it passes**

Run: `gradlew.bat test -PofflineMcMeta --tests '*QuestHandInTest*'`
Expected: PASS, 5 tests.

- [-] **Step 6: Commit**

```bash
git add src/main/java/net/bullettrain/xenopixelsmod/features/progression/ProgressionEvents.java src/main/java/net/bullettrain/xenopixelsmod/npc/XenoNpcEntity.java src/test/java/net/bullettrain/xenopixelsmod/npc/QuestHandInTest.java
git commit -m "Hand in a ready quest by talking to its completer NPC"
```

---

### Task 7: The dead gate, the party bridge, and the commands

**Files:**
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/features/progression/MasterPrerequisites.java`
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/compat/npc/NpcPartyQuestObjectives.java`
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/command/ProgressionCommands.java`
- Test: `src/test/java/net/bullettrain/xenopixelsmod/features/progression/MasterGateTest.java`

**Interfaces:**
- Consumes: everything from Tasks 1–6.
- Produces: no new public API. `/xenoquest abort <id>` and `/xenoquest log` are new command shapes.

- [x] **Step 1: Write the failing test**

```java
package net.bullettrain.xenopixelsmod.features.progression;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The master gate that could never open.
 *
 * <p>`check()` required the requirement's quest id to equal the player's current quest id AND
 * progress to have reached target. But reaching target ran `completeQuest`, which called
 * `clearQuest()` and blanked the id. So the gate failed while the quest was unfinished and failed
 * again the moment it finished. A master gated on a quest was locked forever, silently.
 */
class MasterGateTest {

    private static String source(String file) throws IOException {
        return Files.readString(RepoRoot.of(
                "src/main/java/net/bullettrain/xenopixelsmod/features/progression", file),
                StandardCharsets.UTF_8);
    }

    @Test
    void aFinishedQuestSatisfiesTheGate() {
        QuestBook book = new QuestBook();
        book.start("trial", 1);
        book.active("trial").addProgress(1);
        book.complete("trial", 10L);
        assertTrue(book.hasCompleted("trial"), "this is what the gate now reads");
    }

    @Test
    void anUnfinishedQuestDoesNotSatisfyIt() {
        QuestBook book = new QuestBook();
        book.start("trial", 5);
        book.active("trial").addProgress(3);
        assertFalse(book.hasCompleted("trial"));
    }

    @Test
    void theGateReadsCompletedRatherThanTheCurrentQuest() throws IOException {
        String gate = source("MasterPrerequisites.java");
        assertTrue(gate.contains("hasCompleted("), "the gate should ask whether it was finished");
        assertFalse(gate.contains("data.getQuestId()"),
                "comparing against the current quest is the bug this fixes");
        assertFalse(gate.contains("data.getQuestProgress()"));
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `gradlew.bat test -PofflineMcMeta --tests '*MasterGateTest*'`
Expected: FAIL — the source assertions fail.

- [x] **Step 3: Fix the gate**

In `MasterPrerequisites.check`, replace the quest block:

```java
        if (!requirement.questId().isEmpty()) {
            var data = XenoCapabilities.get(player).orElse(null);
            // Reads the completed set, not the current quest. The old check wanted the id to match
            // AND progress to be at target, but completion erased the id in the same instant - so
            // there was no moment at which it could pass.
            if (data == null || !data.quests().hasCompleted(requirement.questId())) {
                return new Result(false, "Complete quest " + requirement.questId() + " first");
            }
        }
```

- [x] **Step 4: Fix the party bridge**

In `NpcPartyQuestObjectives`, replace the single-quest read with a loop over `data.quests().actives()`, emitting one entry per active quest using `quest.id()`, `quest.progress()` and `quest.target()` where the old code used the player-level getters. Replace the `hasActiveQuest()` guard at line 50 with `!data.quests().actives().isEmpty()`.

- [x] **Step 5: Update the commands**

In `ProgressionCommands`:

- `abort` takes a `StringArgumentType.word()` id, suggests from `ParallelQuests.ids()`, and calls `data.quests().abandon(id)` — replacing the bare `data.clearQuest()` at line 487. When the player is not on that quest, reply `"You are not on that quest."` rather than doing nothing.
- Add `log`, printing `data.quests().completed()` or `"No quests completed yet."`.

- [x] **Step 6: Run the focused tests**

Run: `gradlew.bat test -PofflineMcMeta --tests '*MasterGateTest*' --tests '*QuestBookTest*' --tests '*QuestAdvanceTest*'`
Expected: PASS.

- [x] **Step 7: Run the full ladder**

```bash
gradlew.bat test -PofflineMcMeta
gradlew.bat build jarJar serverJar -PofflineMcMeta
gradlew.bat buildApiExampleAddon -PofflineMcMeta
```

Expected: all green, test count above the 1702 baseline, 0 failures. Confirm the server jar holds 0 entries under `META-INF/jarjar/`.

- [-] **Step 8: Commit**

```bash
git add src/main/java/net/bullettrain/xenopixelsmod/features/progression/MasterPrerequisites.java src/main/java/net/bullettrain/xenopixelsmod/compat/npc/NpcPartyQuestObjectives.java src/main/java/net/bullettrain/xenopixelsmod/command/ProgressionCommands.java src/test/java/net/bullettrain/xenopixelsmod/features/progression/MasterGateTest.java
git commit -m "Repair the master quest gate; share all party quests; abort and log by id"
```

---

## In-game verification

None of the above proves a path ran. After the ladder passes, in a fresh process:

1. `/xenoquest start kill_mobs`, then `/xenoquest start wolf_trouble` — both are accepted, where the second used to be refused.
2. Kill one hostile mob — `kill_mobs` advances and `wolf_trouble` does not.
3. `/xenoquest status` lists both with their progress.
4. Complete a quest, then approach a master gated on it — the gate opens. **It never has before.**
5. `/xenoquest log` lists the completed quest.
6. Author a quest with `"completion": "npc"` and `"completer_npc"` naming a Xeno NPC. Reach its target — it says *ready*, and does not pay. Talk to that NPC — it pays.
7. Load a world saved before this change — the single active quest is still there with its progress.
8. Die and respawn while holding two quests — both survive.

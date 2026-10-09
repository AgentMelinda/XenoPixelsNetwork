# Quest Log — Client Half Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Put the player's active quests on their screen — a sync packet that carries them, a Quests tab and a Factions tab beside the vanilla inventory, and a "New Quest" toast.

**Architecture:** The server half (plan `2026-09-22-quest-log-server.md`) built `QuestBook`/`ActiveQuest`, and nothing on the client can see any of it. A new S2C packet mirrors the player's **active** quests into a client-side `ClientQuests` holder, modelled key-for-key on the proven `SyncFactionsPacket` → `ClientFactions` pair. Two tabs hang off the vanilla `InventoryScreen` through `ScreenEvent`, anchored on `getGuiLeft()`/`getGuiTop()` the way `XenoInventoryEffects` already anchors its effect rail. The toast is one more `RegisterGuiLayersEvent` layer alongside the twelve already registered.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.248, JUnit 5. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-09-22-quest-log-design.md` — §4 Screens, §4 Sync. Read it alongside this plan.

## Global Constraints

- **No invented APIs.** Every symbol named in this plan was read out of repo source or `neoforge-21.1.248-sources.jar` while it was being written. If you need one this plan does not name, verify it the same way before use — repo source, then tracked decompiled source, then `javap` against the exact jar.
- **The NPC system must not depend on any other mod.** Nothing in these files may import `espi.mynpcs`, `noppes`, or DragonMineZ.
- **Packet order is append-only.** `ModNetwork` ids are positional; a new packet goes at the **end** of `register()`. Bump `PROTOCOL` from `"82"` to `"83"` (`ModNetwork.java:155`).
- **Client classes must never load on a dedicated server.** Everything under `client/` is `@EventBusSubscriber(..., value = Dist.CLIENT)` and is reached only from packet `handle` bodies that already run client-side.
- **Bounds are checked on read as well as on write.** A hostile server is as real as a hostile client.
- **Active quests only.** Completed quests are not synced to the client at all (spec §4).
- **No dead controls.** A row with no backing consumer is informational text, not a widget.
- **Do not commit, tag, push, or rewrite history unless the user explicitly requests it** (AGENTS.md). The `git commit` steps below are written for completeness; **skip them** and leave the work in the tree unless the user says otherwise.
- Baseline to beat: **1739 tests, 0 failures** (`gradlew.bat test -PofflineMcMeta`).

## Review Focus

Six things the spec implies that no obvious task test would catch, ordered by how likely they are to bite a real player. Each has a test assigned to the task that owns the code.

1. **The Factions tab showing the faction's default standing instead of the player's own.** `ClientFactions.Entry.defaultStanding()` is the *faction's* default; the player's standing lives in `XenoPlayerData.getFactionStanding(String)` and reaches no client today. Reading the former would show every player on a server identical numbers and would look entirely plausible. → Task 7 test `anUnknownFactionFallsBackToItsDefault`, Task 8 test `thePanelReadsThePlayersOwnStanding`.
2. **A quest whose definition the client cannot resolve.** The packet carries quest *ids*; titles and log text come from `ParallelQuests.definition(id)`, which is server-side datapack state. A client that never received that definition must show the raw id, not a blank row or an NPE. → Task 4 test `aQuestWithNoKnownDefinitionStillRenders`.
3. **Category sort must be natural, not lexicographic.** `Side 10` sorting before `Side 2` is the single most visible wrongness in the finished screen, and `String.compareTo` gets it wrong by default. → Task 3 test `sideTenSortsAfterSideTwo`.
4. **Log text that is one enormous paragraph.** `logText` is author-supplied with no length rule; paging must not divide by zero on an empty string or drop the tail of a long one. → Task 4 tests `emptyLogTextIsOnePageNotZero`, `theLastPageKeepsTheTail`, `aNonsensePageSizeDoesNotDivideByZero`.
5. **The sync arriving before the player's screen exists, and never arriving at all.** "No quests" and "not yet synced" are different states and must read differently, exactly as `ClientFactions.isEmpty()` already distinguishes them. → Task 1 test `neverSyncedIsDistinctFromNoQuests`, Task 6 test `thePanelDistinguishesThoseTwoStates`.
6. **A disconnect leaving the previous world's quests on screen.** `ClientQuests` is static and outlives a world; without an explicit clear, logging into world B shows world A's quest log. → Task 1 test `disconnectClearsTheLog`.


> **Execution note (2026-09-22):** implemented and verified — 1806 tests, 0 failures (up from the
> 1739 baseline; 67 new). Steps marked `- [-]` are the plan's `git commit` steps, deliberately
> **not** run: AGENTS.md says "Do not commit, tag, push, or rewrite history unless the user
> explicitly requests it." Two deviations from the written order, both noted in the report: the
> toast overlay (Task 9) was written alongside Task 1 rather than stubbed and restored, and the
> per-file test classes were merged into `QuestPanelsTest` and the two packet tests. Nothing has
> been run in a game; the In-game verification list below is all outstanding.

---

## File Structure

| File | Responsibility |
|---|---|
| `client/npc/quest/ClientQuests.java` (new) | The client's mirror of the player's active quests. Thin record list plus a sync-state flag. Mirrors `ClientFactions`. |
| `network/packet/SyncQuestsPacket.java` (new) | S2C wire format for that mirror. Mirrors `SyncFactionsPacket`. |
| `features/progression/QuestSync.java` (new) | Server-side: decides *when* to send. `OnDatapackSyncEvent` plus an explicit push on every state change. |
| `client/npc/quest/QuestLogLayout.java` (new) | Pure grouping, sorting and paging maths. No Minecraft types — so it is unit-testable without a client. |
| `client/npc/quest/XenoInventoryTabs.java` (new) | The tab strip: hit-testing, which tab is open, dispatching render and click. |
| `client/npc/quest/QuestLogPanel.java` (new) | Draws the Quests tab body. |
| `client/npc/quest/FactionPanel.java` (new) | Draws the Factions tab body. |
| `client/npc/faction/ClientStandings.java` (new) | The client's mirror of **this player's** faction standings. |
| `network/packet/SyncStandingsPacket.java` (new) | S2C wire format for that mirror. |
| `client/npc/quest/QuestToastOverlay.java` (new) | The "New Quest" toast layer. |
| `client/npc/quest/ClientQuestReset.java` (new) | Clears the mirror on disconnect. |
| `network/ModNetwork.java` (modify) | Register `SyncQuestsPacket`; bump `PROTOCOL` to `"83"`. |
| `client/XenoHudRegistration.java` (modify) | Register the toast layer. |
| `features/progression/ProgressionEvents.java` (modify) | Push after every quest state change. |
| `features/progression/ParallelQuests.java` (modify) | Same, in `start()`. |
| `command/ProgressionCommands.java` (modify) | Same, in `questAbort()`. |

Layout maths lives in `QuestLogLayout` rather than inside the panels because **that is the part a test can reach**. A JUnit test cannot instantiate `InventoryScreen`, but it can assert that `Side 2` sorts before `Side 10` and that a long `logText` pages without losing its tail. Everything the Review Focus list names is therefore reachable by a test.

---

### Task 1: The wire format

**Files:**
- Create: `src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/ClientQuests.java`
- Create: `src/main/java/net/bullettrain/xenopixelsmod/network/packet/SyncQuestsPacket.java`
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/network/ModNetwork.java:155` and the end of `register()`
- Test: `src/test/java/net/bullettrain/xenopixelsmod/network/SyncQuestsPacketTest.java`

**Interfaces:**
- Consumes: `QuestBook.actives()` → `List<ActiveQuest>`; `ActiveQuest.id()/progress()/target()/ready()` (verified, `ActiveQuest.java:31-48`); `ParallelQuests.definition(String)` → `QuestDef` with `title()/category()/logText()/completerNpc()`.
- Produces:
  - `ClientQuests.Entry(String id, String title, String category, String logText, int progress, int target, boolean ready, String completerNpc)`
  - `ClientQuests.accept(List<Entry>)`, `ClientQuests.all() -> List<Entry>`, `ClientQuests.isSynced() -> boolean`, `ClientQuests.clear()`, `ClientQuests.MAX_QUESTS`
  - `SyncQuestsPacket.forPlayer(ServerPlayer) -> SyncQuestsPacket`

- [x] **Step 1: Write the failing test**

Create `src/test/java/net/bullettrain/xenopixelsmod/network/SyncQuestsPacketTest.java`:

```java
package net.bullettrain.xenopixelsmod.network;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.client.npc.quest.ClientQuests;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client's quest mirror.
 *
 * <p>Wire encode/decode needs a {@code FriendlyByteBuf}, which needs Netty buffers a plain unit
 * test has no server to supply; the caps and the mirror's own rules are what these pin. The round
 * trip itself is an in-game check, listed in the plan's verification section.
 */
class SyncQuestsPacketTest {

    private static ClientQuests.Entry entry(String id) {
        return new ClientQuests.Entry(id, id, "", "", 0, 1, false, "");
    }

    @Test
    void neverSyncedIsDistinctFromNoQuests() {
        // Two states that must not read the same on screen: "the server said you have none" and
        // "the server has not said anything yet". ClientFactions draws the same distinction for
        // the same reason.
        ClientQuests.clear();
        assertFalse(ClientQuests.isSynced(), "nothing has arrived yet");

        ClientQuests.accept(List.of());
        assertTrue(ClientQuests.isSynced(), "an empty sync is still a sync");
        assertTrue(ClientQuests.all().isEmpty());
    }

    @Test
    void disconnectClearsTheLog() {
        // Otherwise logging into world B shows world A's quests, because this holder is static.
        ClientQuests.accept(List.of(entry("wolf_trouble")));
        assertFalse(ClientQuests.all().isEmpty());

        ClientQuests.clear();
        assertTrue(ClientQuests.all().isEmpty());
        assertFalse(ClientQuests.isSynced(), "a cleared log is back to not-yet-synced");
    }

    @Test
    void theOrderTheServerSentIsTheOrderKept() {
        // Map.copyOf is unordered and has silently scrambled documented-ordered data three times
        // in this subsystem already. The server sends quests in start order and the log walks
        // them in that order.
        ClientQuests.clear();
        ClientQuests.accept(List.of(entry("c"), entry("a"), entry("b")));
        assertEquals(List.of("c", "a", "b"),
                ClientQuests.all().stream().map(ClientQuests.Entry::id).toList());
    }

    @Test
    void aBlankIdIsDropped() {
        // It would render as an empty row that cannot be selected or completed.
        ClientQuests.clear();
        ClientQuests.accept(List.of(entry("real"), entry("  ")));
        assertEquals(1, ClientQuests.all().size());
    }

    @Test
    void moreQuestsThanTheCapAreTruncatedNotThrown() {
        // A hostile or buggy server must not be able to make the client allocate without bound.
        ClientQuests.clear();
        List<ClientQuests.Entry> many = new ArrayList<>();
        for (int i = 0; i < ClientQuests.MAX_QUESTS + 10; i++) {
            many.add(entry("q" + i));
        }
        ClientQuests.accept(many);
        assertEquals(ClientQuests.MAX_QUESTS, ClientQuests.all().size());
    }

    @Test
    void anEntryWithNoTitleFallsBackToItsId() {
        // Quest definitions are datapack state. A client on a quest whose definition it was never
        // sent must get a row it can read and select, not a blank line.
        ClientQuests.Entry unknown =
                new ClientQuests.Entry("wolf_trouble", "", "", "", 2, 5, false, "");
        assertEquals("wolf_trouble", unknown.title());
    }

    @Test
    void progressIsClampedIntoItsTarget() {
        // A server that says 9/5 would draw a bar past its own end.
        ClientQuests.Entry silly = new ClientQuests.Entry("q", "q", "", "", 9, 5, false, "");
        assertEquals(5, silly.progress());
        ClientQuests.Entry negative = new ClientQuests.Entry("q", "q", "", "", -3, 5, false, "");
        assertEquals(0, negative.progress());
    }

    @Test
    void theProtocolWasBumpedAndThePacketAppended() throws IOException {
        // A wire change without a protocol bump lets a stale client connect and misread every
        // packet after the new one, because ids are positional.
        String network = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/network", "ModNetwork.java"),
                StandardCharsets.UTF_8);
        assertTrue(network.contains("PROTOCOL = \"83\""), "protocol should be 83");

        int factions = network.indexOf("SyncFactionsPacket.class, id++");
        int quests = network.indexOf("SyncQuestsPacket.class,");
        assertTrue(factions >= 0 && quests >= 0);
        assertTrue(quests > factions, "the new packet is appended, never inserted");
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `gradlew.bat test --tests "*SyncQuestsPacketTest*" -PofflineMcMeta`
Expected: FAIL — compilation error, `package net.bullettrain.xenopixelsmod.client.npc.quest does not exist`.

- [x] **Step 3: Write `ClientQuests`**

Create `src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/ClientQuests.java`:

```java
package net.bullettrain.xenopixelsmod.client.npc.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The player's active quests as the client knows them.
 *
 * <p>Quest state lives in {@code QuestBook} on the server and reaches no client today. The log
 * screen runs on the client, so without this it would be permanently empty - which reads as a
 * broken screen rather than as data that has not arrived. The same trap the faction list, the mark
 * icons, the bubble palettes and the dialogue trees each hit in turn.
 *
 * <p>Carries each quest's <em>definition</em> text as well as its progress. Definitions are
 * datapack state, which is also server-side, so a client sent only ids could render nothing but
 * ids.
 *
 * <p>Active quests only. Completed quests stay on the server for {@code MasterPrerequisites} and
 * the availability checks a later spec will add; nothing on the client displays them, and syncing
 * them would send a list that grows without bound over a world's lifetime.
 *
 * @see net.bullettrain.xenopixelsmod.features.progression.QuestBook
 */
public final class ClientQuests {

    /** Matches {@code QuestBook.MAX_ACTIVE}; a server claiming more is not to be believed. */
    public static final int MAX_QUESTS = 32;

    /** One active quest, as much of it as the log screen needs. */
    public record Entry(String id, String title, String category, String logText,
                        int progress, int target, boolean ready, String completerNpc) {
        public Entry {
            id = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
            // Falls back to the id so a quest whose definition the client never received renders
            // as something readable and selectable rather than as a blank row.
            title = title == null || title.isBlank() ? id : title;
            category = category == null ? "" : category.trim();
            logText = logText == null ? "" : logText;
            target = Math.max(1, target);
            progress = Math.max(0, Math.min(target, progress));
            completerNpc = completerNpc == null ? "" : completerNpc.trim();
        }
    }

    private static volatile List<Entry> active = List.of();
    private static volatile boolean synced;

    private ClientQuests() {
    }

    /** Replaces the client's view. Called when the server syncs. */
    public static void accept(List<Entry> entries) {
        List<Entry> next = new ArrayList<>();
        for (Entry entry : entries == null ? List.<Entry>of() : entries) {
            if (!entry.id().isBlank() && next.size() < MAX_QUESTS) {
                next.add(entry);
            }
        }
        // The toast fires on an id that was not here before - not on every sync, because progress
        // pushes one per kill and a toast that re-fired on each would never leave the screen. The
        // first sync after login only establishes the baseline: announcing it would greet a
        // returning player with a toast for every quest they took hours ago.
        if (synced) {
            for (Entry entry : next) {
                if (!containsId(active, entry.id())) {
                    QuestToastOverlay.show(entry.title());
                }
            }
        }
        // Deliberately a list, in the order the server sent: that is quest start order, and it is
        // the order the log walks. A map would have unordered it.
        active = Collections.unmodifiableList(next);
        synced = true;
    }

    private static boolean containsId(List<Entry> entries, String id) {
        for (Entry entry : entries) {
            if (entry.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** Every active quest, in the order the server sent them. */
    public static List<Entry> all() {
        return active;
    }

    /**
     * Whether the server has sent anything yet.
     *
     * <p>Distinct from "you have no quests": one means the log is genuinely empty, the other means
     * the sync has not arrived. A screen should say which, because they are not the same news.
     */
    public static boolean isSynced() {
        return synced;
    }

    /**
     * Forgets everything, including that a sync ever happened.
     *
     * <p>Called on disconnect. This holder is static and outlives a world, so without it a player
     * leaving world A and joining world B would see world A's quests until the new sync landed -
     * and if world B has none, would see them indefinitely.
     */
    public static void clear() {
        active = List.of();
        synced = false;
        QuestToastOverlay.reset();
    }
}
```

**Note:** `QuestToastOverlay` arrives in Task 9. Until then, comment out the two calls to it and the `containsId` block; Task 9 Step 4 restores them. Leave a sentence in their place rather than a marker:

```java
        // The toast that reads this baseline lands in the last task of this plan.
```

- [x] **Step 4: Run the test to verify it passes**

Run: `gradlew.bat test --tests "*SyncQuestsPacketTest*" -PofflineMcMeta`
Expected: FAIL on `theProtocolWasBumpedAndThePacketAppended` only — the other 7 pass. That one is fixed in Step 6.

- [x] **Step 5: Write `SyncQuestsPacket`**

Create `src/main/java/net/bullettrain/xenopixelsmod/network/packet/SyncQuestsPacket.java`:

```java
package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.bullettrain.xenopixelsmod.client.npc.quest.ClientQuests;
import net.bullettrain.xenopixelsmod.features.progression.ActiveQuest;
import net.bullettrain.xenopixelsmod.features.progression.ParallelQuests;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S2C: one player's active quests, so their quest log can show them.
 *
 * <p>Quest state is a server capability and quest definitions are a server-side reload listener.
 * Neither reaches a client on its own, so the log screen would be permanently empty - including in
 * single-player, which still talks to its own integrated server over the network.
 *
 * <p>Per-player, unlike {@code SyncFactionsPacket}: a quest log is the player's own, and
 * broadcasting everybody's would leak progress across a server.
 *
 * <p>Carries the definition text alongside the progress, because the definition is datapack state
 * the client does not have.
 */
public record SyncQuestsPacket(List<ClientQuests.Entry> entries) {

    /** Matches {@code QuestBook.MAX_ACTIVE}. */
    private static final int MAX_QUESTS = 32;

    private static final int MAX_ID = 64;
    private static final int MAX_TITLE = 128;
    private static final int MAX_CATEGORY = 64;

    /** Generous, because this is prose an author writes - but still bounded. */
    private static final int MAX_LOG_TEXT = 4096;

    /** What this player is currently on. */
    public static SyncQuestsPacket forPlayer(ServerPlayer player) {
        XenoPlayerData data = player == null ? null : XenoCapabilities.get(player).orElse(null);
        if (data == null) {
            return new SyncQuestsPacket(List.of());
        }
        List<ClientQuests.Entry> entries = new ArrayList<>();
        for (ActiveQuest quest : data.quests().actives()) {
            if (entries.size() >= MAX_QUESTS) {
                break;
            }
            ParallelQuests.QuestDef def = ParallelQuests.definition(quest.id());
            entries.add(new ClientQuests.Entry(
                    quest.id(),
                    def == null ? quest.id() : def.title(),
                    def == null ? "" : def.category(),
                    def == null ? "" : def.logText(),
                    quest.progress(),
                    quest.target(),
                    quest.ready(),
                    def == null ? "" : def.completerNpc()));
        }
        return new SyncQuestsPacket(entries);
    }

    public SyncQuestsPacket(FriendlyByteBuf buf) {
        this(read(buf));
    }

    private static List<ClientQuests.Entry> read(FriendlyByteBuf buf) {
        int count = Math.min(MAX_QUESTS, buf.readVarInt());
        List<ClientQuests.Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf(MAX_ID);
            String title = buf.readUtf(MAX_TITLE);
            String category = buf.readUtf(MAX_CATEGORY);
            String logText = buf.readUtf(MAX_LOG_TEXT);
            int progress = buf.readVarInt();
            int target = buf.readVarInt();
            boolean ready = buf.readBoolean();
            String completer = buf.readUtf(MAX_TITLE);
            entries.add(new ClientQuests.Entry(id, title, category, logText,
                    progress, target, ready, completer));
        }
        return entries;
    }

    public void encode(FriendlyByteBuf buf) {
        List<ClientQuests.Entry> list = entries == null ? List.of() : entries;
        int count = Math.min(MAX_QUESTS, list.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            ClientQuests.Entry entry = list.get(i);
            buf.writeUtf(entry.id(), MAX_ID);
            buf.writeUtf(entry.title(), MAX_TITLE);
            buf.writeUtf(entry.category(), MAX_CATEGORY);
            // Truncated rather than refused: an over-long journal entry should cost its tail, not
            // the whole quest log.
            buf.writeUtf(trim(entry.logText(), MAX_LOG_TEXT), MAX_LOG_TEXT);
            buf.writeVarInt(entry.progress());
            buf.writeVarInt(entry.target());
            buf.writeBoolean(entry.ready());
            buf.writeUtf(entry.completerNpc(), MAX_TITLE);
        }
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> ClientQuests.accept(entries));
        ctx.setPacketHandled(true);
    }
}
```

- [x] **Step 6: Register the packet and bump the protocol**

In `src/main/java/net/bullettrain/xenopixelsmod/network/ModNetwork.java`, change line 155:

```java
    private static final String PROTOCOL = "83";
```

Then append at the **end** of `register()`, after the `SyncNpcStoreIndexPacket` block and before the closing `XenoPixelsMod.LOGGER.info(...)` call:

```java
        // Appended, as this list always is - ids are positional and reordering would make an old
        // client read the wrong packet.
        CHANNEL.messageBuilder(
                        net.bullettrain.xenopixelsmod.network.packet.SyncQuestsPacket.class,
                        id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.SyncQuestsPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.SyncQuestsPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.SyncQuestsPacket::handle)
                .add();
```

- [x] **Step 7: Run the tests**

Run: `gradlew.bat test --tests "*SyncQuestsPacketTest*" -PofflineMcMeta`
Expected: PASS, 8 tests.

- [x] **Step 8: Compile**

Run: `gradlew.bat compileJava -PofflineMcMeta`
Expected: BUILD SUCCESSFUL.

- [-] **Step 9: Commit** — *skip unless the user asked; see Global Constraints*

```bash
git add src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/ClientQuests.java src/main/java/net/bullettrain/xenopixelsmod/network/packet/SyncQuestsPacket.java src/main/java/net/bullettrain/xenopixelsmod/network/ModNetwork.java src/test/java/net/bullettrain/xenopixelsmod/network/SyncQuestsPacketTest.java
git commit -m "feat(npc): carry active quests to the client"
```

---

### Task 2: Sending it at the right moments

**Files:**
- Create: `src/main/java/net/bullettrain/xenopixelsmod/features/progression/QuestSync.java`
- Create: `src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/ClientQuestReset.java`
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/features/progression/ProgressionEvents.java`
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/features/progression/ParallelQuests.java`
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/command/ProgressionCommands.java`
- Test: `src/test/java/net/bullettrain/xenopixelsmod/features/progression/QuestSyncTest.java`

**Interfaces:**
- Consumes: `SyncQuestsPacket.forPlayer(ServerPlayer)` (Task 1); `ModNetwork.sendToPlayer(ServerPlayer, Object)` (`ModNetwork.java:548`); `OnDatapackSyncEvent.getPlayer()` / `getPlayerList()`.
- Produces: `QuestSync.push(ServerPlayer)` — sends one player their current log. Call it after **every** quest state change.

- [x] **Step 1: Verify the two events before writing against them**

Run:

```bash
JAR=build/moddev/artifacts/neoforge-21.1.248-sources.jar
D=$(mktemp -d) && cd "$D" && unzip -o -q "$OLDPWD/$JAR" \
  "net/neoforged/neoforge/event/OnDatapackSyncEvent.java" \
  "net/neoforged/neoforge/client/event/ClientPlayerNetworkEvent.java" \
  && grep -n "public " net/neoforged/neoforge/event/OnDatapackSyncEvent.java \
  && grep -n "class LoggingOut" net/neoforged/neoforge/client/event/ClientPlayerNetworkEvent.java
```

Expected: `OnDatapackSyncEvent` exposes `getPlayer()` returning `ServerPlayer` and `getPlayerList()` returning `PlayerList`; `ClientPlayerNetworkEvent.LoggingOut` exists. `PlayerList.getPlayers()` returns `List<ServerPlayer>`. **If any accessor is named differently, use the real name** — do not guess, and correct the code in Steps 3 and 6 to match.

- [x] **Step 2: Write the failing test**

Create `src/test/java/net/bullettrain/xenopixelsmod/features/progression/QuestSyncTest.java`:

```java
package net.bullettrain.xenopixelsmod.features.progression;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every place quest state changes must tell the client, or the log silently goes stale.
 *
 * <p>Source-level assertions: sending a packet needs a server and a connection, and a unit test
 * has neither. What is checkable here is that no mutation site was left without a push - which is
 * the actual failure mode, and one that produces a screen that looks correct and is wrong.
 */
class QuestSyncTest {

    private static String code(String dir, String file) throws IOException {
        String raw = Files.readString(RepoRoot.of(dir, file), StandardCharsets.UTF_8);
        // Comments are stripped before searching: a test asserting "this call exists" must not be
        // satisfied by a comment that merely mentions it.
        return raw.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    private static String progression(String file) throws IOException {
        return code("src/main/java/net/bullettrain/xenopixelsmod/features/progression", file);
    }

    @Test
    void theSyncFiresOnJoinAndAfterReload() throws IOException {
        // OnDatapackSyncEvent is the right hook rather than a login listener: it fires on join AND
        // after every /reload, so an operator who edits a quest file sees it without rejoining.
        String sync = progression("QuestSync.java");
        assertTrue(sync.contains("OnDatapackSyncEvent"), "join and /reload come through one event");
        assertTrue(sync.contains("public static void push(ServerPlayer player)"),
                "and a direct push for state changes between those moments");
    }

    @Test
    void aReloadWithNoJoiningPlayerReachesEverybody() throws IOException {
        // getPlayer() is null for a /reload. Returning early on null would mean a reload updated
        // nobody - which is exactly the case an operator is exercising when they run it.
        assertTrue(progression("QuestSync.java").contains("getPlayerList()"),
                "a null joining player must fan out to the online players");
    }

    @Test
    void completingAQuestPushesTheNewLog() throws IOException {
        // Without this the finished quest stays on the player's screen until they relog.
        String events = progression("ProgressionEvents.java");
        int complete = events.indexOf("public static void completeQuest(");
        assertTrue(complete >= 0);
        assertTrue(events.indexOf("QuestSync.push(", complete) >= 0,
                "completeQuest should tell the client");
    }

    @Test
    void startingAQuestPushesTheNewLog() throws IOException {
        // Otherwise the New Quest toast has nothing to fire on and the log stays empty.
        assertTrue(progression("ParallelQuests.java").contains("QuestSync.push("),
                "start should tell the client");
    }

    @Test
    void markingReadyPushesTheNewLog() throws IOException {
        // "Ready - complete with Stephanie" is the whole point of NPC mode; if the client is not
        // told, the log shows a full bar that has not paid out, which reads as a bug.
        String events = progression("ProgressionEvents.java");
        int finish = events.indexOf("private static void finish(");
        assertTrue(finish >= 0);
        int nextMethod = events.indexOf("public static boolean tryHandIn(", finish);
        assertTrue(nextMethod > finish);
        int push = events.indexOf("QuestSync.push(", finish);
        assertTrue(push >= 0 && push < nextMethod,
                "finish() should push before the next method begins");
    }

    @Test
    void abandoningAQuestPushesTheNewLog() throws IOException {
        // Otherwise an aborted quest stays visible and looks abortable again.
        String commands = code("src/main/java/net/bullettrain/xenopixelsmod/command",
                "ProgressionCommands.java");
        int abort = commands.indexOf("questAbort(CommandSourceStack src, String id)");
        assertTrue(abort >= 0);
        assertTrue(commands.indexOf("QuestSync.push(", abort) >= 0,
                "abort should tell the client");
    }

    @Test
    void theClientForgetsOnDisconnect() throws IOException {
        // ClientQuests is static and outlives a world.
        String reset = code("src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest",
                "ClientQuestReset.java");
        assertTrue(reset.contains("ClientPlayerNetworkEvent.LoggingOut"));
        assertTrue(reset.contains("ClientQuests.clear()"));
        assertTrue(reset.contains("Dist.CLIENT"),
                "a client-only listener must never load on a dedicated server");
    }
}
```

- [x] **Step 3: Run the test to verify it fails**

Run: `gradlew.bat test --tests "*QuestSyncTest*" -PofflineMcMeta`
Expected: FAIL — `NoSuchFileException` for `QuestSync.java`.

- [x] **Step 4: Write `QuestSync`**

Create `src/main/java/net/bullettrain/xenopixelsmod/features/progression/QuestSync.java`:

```java
package net.bullettrain.xenopixelsmod.features.progression;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.SyncQuestsPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/**
 * Sends a player their own quest log.
 *
 * <p>Quest state is a server capability and quest definitions are a server-side reload listener.
 * The log screen runs on the client, so without this it shows nothing forever - which reads as a
 * broken screen rather than as data that has not arrived.
 *
 * <p>Per-player rather than broadcast: a quest log is the player's own business, and sending
 * everybody's to everybody would leak progress across a server.
 *
 * <p>{@link OnDatapackSyncEvent} covers the two moments a whole log needs resending - on join, and
 * after a {@code /reload} that may have redefined every quest's title and journal text. Changes
 * between those moments go through {@link #push(ServerPlayer)} at each mutation site.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class QuestSync {

    private QuestSync() {
    }

    /**
     * Sends this player their current active quests.
     *
     * <p>Call after every quest state change. A log that is right only until the next kill is
     * worse than no log, because it looks authoritative.
     */
    public static void push(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ModNetwork.sendToPlayer(player, SyncQuestsPacket.forPlayer(player));
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        ServerPlayer joining = event.getPlayer();
        if (joining != null) {
            push(joining);
            return;
        }
        // Null means a /reload rather than a join. Quest definitions may all have changed, so
        // every online player's log needs rebuilding - and this is precisely the case an operator
        // is exercising when they run the command.
        for (ServerPlayer player : event.getPlayerList().getPlayers()) {
            push(player);
        }
    }
}
```

- [x] **Step 5: Add the push at each mutation site**

In `ProgressionEvents.java`, at the end of `completeQuest(ServerPlayer player, XenoPlayerData data, String id)`, after the final `displayClientMessage` call:

```java
        // The log is now wrong on the client until it is told. A finished quest that stays on
        // screen reads as a quest that did not register.
        QuestSync.push(player);
```

In `ProgressionEvents.java`, inside `finish(...)`, in the `QuestCompletionMode.NPC` branch, immediately before its `return;`:

```java
            QuestSync.push(player);
```

In `ParallelQuests.java`, inside `start(...)`, after the `refusal` check has passed and the quest is actually started, immediately before the method returns its success string:

```java
        // Pushed here rather than left to the next tick: the New Quest toast fires off this
        // packet, and a toast that arrives seconds after the dialogue closed has lost its moment.
        QuestSync.push(player);
```

In `ProgressionCommands.java`, inside `questAbort(CommandSourceStack src, String id)`, after `data.quests().abandon(id);`:

```java
            QuestSync.push(p);
```

- [x] **Step 6: Write the disconnect listener**

Create `src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/ClientQuestReset.java`:

```java
package net.bullettrain.xenopixelsmod.client.npc.quest;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * Forgets the quest log on disconnect.
 *
 * <p>{@link ClientQuests} is static and outlives a world. Without this, leaving world A and joining
 * world B shows world A's quests until the new sync lands - and if the new world has none, shows
 * them indefinitely.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientQuestReset {

    private ClientQuestReset() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientQuests.clear();
    }
}
```

- [x] **Step 7: Run the tests**

Run: `gradlew.bat test --tests "*QuestSyncTest*" -PofflineMcMeta`
Expected: PASS, 7 tests.

- [x] **Step 8: Compile**

Run: `gradlew.bat compileJava -PofflineMcMeta`
Expected: BUILD SUCCESSFUL.

- [-] **Step 9: Commit** — *skip unless the user asked*

```bash
git add src/main/java/net/bullettrain/xenopixelsmod/features/progression/QuestSync.java src/main/java/net/bullettrain/xenopixelsmod/features/progression/ProgressionEvents.java src/main/java/net/bullettrain/xenopixelsmod/features/progression/ParallelQuests.java src/main/java/net/bullettrain/xenopixelsmod/command/ProgressionCommands.java src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/ClientQuestReset.java src/test/java/net/bullettrain/xenopixelsmod/features/progression/QuestSyncTest.java
git commit -m "feat(npc): push the quest log on join, reload and every state change"
```

---

### Task 3: Grouping and sorting — the pure maths

**Files:**
- Create: `src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/QuestLogLayout.java`
- Test: `src/test/java/net/bullettrain/xenopixelsmod/client/npc/quest/QuestLogLayoutTest.java`

**Interfaces:**
- Consumes: `ClientQuests.Entry` (Task 1).
- Produces:
  - `QuestLogLayout.GENERAL` — the `String` a quest with no declared category is filed under.
  - `QuestLogLayout.categories(List<ClientQuests.Entry>) -> List<String>`
  - `QuestLogLayout.inCategory(List<ClientQuests.Entry>, String) -> List<ClientQuests.Entry>`
  - `QuestLogLayout.naturalCompare(String, String) -> int`

- [x] **Step 1: Write the failing test**

Create `src/test/java/net/bullettrain/xenopixelsmod/client/npc/quest/QuestLogLayoutTest.java`:

```java
package net.bullettrain.xenopixelsmod.client.npc.quest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Grouping active quests into category buttons, and ordering those buttons the way a person reads. */
class QuestLogLayoutTest {

    private static ClientQuests.Entry quest(String id, String category) {
        return new ClientQuests.Entry(id, id, category, "", 0, 1, false, "");
    }

    @Test
    void sideTenSortsAfterSideTwo() {
        // Lexicographic order puts "Side 10" before "Side 2", which is the single most visible
        // wrongness a finished quest log can have. The spec calls for natural sort by name.
        List<String> categories = QuestLogLayout.categories(List.of(
                quest("a", "Side 10"), quest("b", "Side 2"), quest("c", "Side 1")));
        assertEquals(List.of("Side 1", "Side 2", "Side 10"), categories);
    }

    @Test
    void naturalSortHandlesNumbersThatAreNotAtTheEnd() {
        assertTrue(QuestLogLayout.naturalCompare("Chapter 2 - Dawn", "Chapter 10 - Dusk") < 0);
    }

    @Test
    void naturalSortIsCaseInsensitive() {
        // Two categories differing only in case are one category to a reader; a case-sensitive
        // tie-break would flip their order between openings of the screen.
        assertEquals(0, QuestLogLayout.naturalCompare("main", "Main"));
    }

    @Test
    void leadingZerosDoNotChangeTheOrder() {
        // "Side 02" and "Side 2" are the same chapter to a reader.
        assertEquals(0, QuestLogLayout.naturalCompare("Side 02", "Side 2"));
    }

    @Test
    void aVeryLongNumberDoesNotOverflow() {
        // Compared by digit-run length rather than parsed, so a category named with something
        // longer than an int survives instead of throwing.
        assertTrue(QuestLogLayout.naturalCompare("q 99999999999999999999", "q 3") > 0);
    }

    @Test
    void aQuestWithNoCategoryGetsTheGeneralOneAndItComesFirst() {
        // "category" is optional in the quest JSON, and every quest predating the field has none.
        // They must still be reachable, and should not be buried under named chapters.
        List<String> categories = QuestLogLayout.categories(List.of(
                quest("a", "Side"), quest("b", "")));
        assertEquals(List.of(QuestLogLayout.GENERAL, "Side"), categories);
    }

    @Test
    void eachCategoryAppearsOnce() {
        assertEquals(List.of("Main"), QuestLogLayout.categories(List.of(
                quest("a", "Main"), quest("b", "Main"), quest("c", "Main"))));
    }

    @Test
    void inCategoryKeepsServerOrderWithin() {
        // Quests inside a category stay in the order the server sent, which is start order. A
        // second sort here would reorder a list the player has already learned.
        List<ClientQuests.Entry> all = List.of(
                quest("c", "Main"), quest("a", "Main"), quest("b", "Side"));
        assertEquals(List.of("c", "a"),
                QuestLogLayout.inCategory(all, "Main").stream()
                        .map(ClientQuests.Entry::id).toList());
    }

    @Test
    void inCategoryFindsTheUncategorised() {
        List<ClientQuests.Entry> all = List.of(quest("a", ""), quest("b", "Main"));
        assertEquals(List.of("a"),
                QuestLogLayout.inCategory(all, QuestLogLayout.GENERAL).stream()
                        .map(ClientQuests.Entry::id).toList());
    }

    @Test
    void noQuestsMeansNoCategories() {
        assertTrue(QuestLogLayout.categories(List.of()).isEmpty());
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `gradlew.bat test --tests "*QuestLogLayoutTest*" -PofflineMcMeta`
Expected: FAIL — `cannot find symbol: class QuestLogLayout`.

- [x] **Step 3: Write `QuestLogLayout`**

Create `src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/QuestLogLayout.java`:

```java
package net.bullettrain.xenopixelsmod.client.npc.quest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Grouping, ordering and paging for the quest log.
 *
 * <p>Deliberately free of any Minecraft type. The panel that draws the log cannot be instantiated
 * in a unit test - {@code InventoryScreen} needs a running client - so every rule worth pinning
 * lives here instead, where a test can reach it.
 */
public final class QuestLogLayout {

    /** What a quest with no declared category is filed under. */
    public static final String GENERAL = "General";

    private QuestLogLayout() {
    }

    /**
     * The category buttons, in the order they are drawn.
     *
     * <p>Natural order, so {@code Side 2} precedes {@code Side 10}. Lexicographic order does the
     * opposite, and it is the most visible thing a log can get wrong.
     *
     * <p>{@link #GENERAL} sorts first when present: it holds every quest written before the
     * category field existed, and burying those under named chapters would hide them.
     */
    public static List<String> categories(List<ClientQuests.Entry> quests) {
        Set<String> seen = new LinkedHashSet<>();
        for (ClientQuests.Entry quest : quests == null ? List.<ClientQuests.Entry>of() : quests) {
            seen.add(quest.category().isBlank() ? GENERAL : quest.category());
        }
        List<String> out = new ArrayList<>(seen);
        out.sort((a, b) -> {
            if (a.equals(GENERAL) || b.equals(GENERAL)) {
                return a.equals(b) ? 0 : a.equals(GENERAL) ? -1 : 1;
            }
            return naturalCompare(a, b);
        });
        return List.copyOf(out);
    }

    /** The quests filed under one category, in the order the server sent them. */
    public static List<ClientQuests.Entry> inCategory(List<ClientQuests.Entry> quests,
                                                      String category) {
        List<ClientQuests.Entry> out = new ArrayList<>();
        String wanted = category == null || category.isBlank() ? GENERAL : category;
        for (ClientQuests.Entry quest : quests == null ? List.<ClientQuests.Entry>of() : quests) {
            String own = quest.category().isBlank() ? GENERAL : quest.category();
            if (own.equalsIgnoreCase(wanted)) {
                out.add(quest);
            }
        }
        return List.copyOf(out);
    }

    /**
     * Compares two names the way a reader orders them: digit runs compare as numbers.
     *
     * <p>Case-insensitive, because two categories differing only in case are one category to a
     * person, and a case-sensitive tie-break would flip their order between openings.
     */
    public static int naturalCompare(String left, String right) {
        String a = left == null ? "" : left;
        String b = right == null ? "" : right;
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int startA = i;
                int startB = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) {
                    i++;
                }
                while (j < b.length() && Character.isDigit(b.charAt(j))) {
                    j++;
                }
                // Compared by digit-run length first, so "10" beats "2" without parsing a number
                // that may be longer than any integer type can hold.
                String numA = stripLeadingZeros(a.substring(startA, i));
                String numB = stripLeadingZeros(b.substring(startB, j));
                if (numA.length() != numB.length()) {
                    return numA.length() - numB.length();
                }
                int cmp = numA.compareTo(numB);
                if (cmp != 0) {
                    return cmp;
                }
                continue;
            }
            int cmp = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
            if (cmp != 0) {
                return cmp;
            }
            i++;
            j++;
        }
        return (a.length() - i) - (b.length() - j);
    }

    private static String stripLeadingZeros(String digits) {
        int k = 0;
        while (k < digits.length() - 1 && digits.charAt(k) == '0') {
            k++;
        }
        return digits.substring(k);
    }
}
```

- [x] **Step 4: Run the tests**

Run: `gradlew.bat test --tests "*QuestLogLayoutTest*" -PofflineMcMeta`
Expected: PASS, 10 tests.

- [-] **Step 5: Commit** — *skip unless the user asked*

```bash
git add src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/QuestLogLayout.java src/test/java/net/bullettrain/xenopixelsmod/client/npc/quest/QuestLogLayoutTest.java
git commit -m "feat(npc): group and naturally sort quest log categories"
```

---

### Task 4: Paging the journal text

**Files:**
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/QuestLogLayout.java`
- Test: `src/test/java/net/bullettrain/xenopixelsmod/client/npc/quest/QuestLogLayoutTest.java`

**Interfaces:**
- Produces: `QuestLogLayout.paginate(List<String> wrappedLines, int linesPerPage) -> List<List<String>>` — always at least one page.

Word-wrapping needs the client's `Font` to measure pixel widths and so stays in the panel. **Splitting already-wrapped lines into pages does not**, and that is where the off-by-one lives, so it comes here.

- [x] **Step 1: Write the failing test**

Append these methods to `QuestLogLayoutTest.java`, inside the class:

```java
    @Test
    void emptyLogTextIsOnePageNotZero() {
        // A quest with no journal entry must still show its objectives. Zero pages would mean the
        // page indicator read "1 / 0" and the body drew nothing at all.
        assertEquals(1, QuestLogLayout.paginate(List.of(), 5).size());
        assertTrue(QuestLogLayout.paginate(List.of(), 5).get(0).isEmpty());
    }

    @Test
    void theLastPageKeepsTheTail() {
        // Seven lines at three per page is three pages, the last holding one line - not two pages
        // with the seventh silently dropped.
        List<List<String>> pages =
                QuestLogLayout.paginate(List.of("1", "2", "3", "4", "5", "6", "7"), 3);
        assertEquals(3, pages.size());
        assertEquals(List.of("7"), pages.get(2));
    }

    @Test
    void anExactMultipleDoesNotProduceATrailingEmptyPage() {
        // Six lines at three per page is two pages. A third, blank one would look like the author
        // left the entry unfinished.
        assertEquals(2, QuestLogLayout.paginate(List.of("1", "2", "3", "4", "5", "6"), 3).size());
    }

    @Test
    void aNonsensePageSizeDoesNotDivideByZero() {
        // linesPerPage is derived from the panel's pixel height divided by the font's line height.
        // At a small GUI scale that arithmetic can reach zero, and an unguarded division there
        // would crash the whole inventory screen rather than just the quest tab.
        assertEquals(1, QuestLogLayout.paginate(List.of("a", "b"), 0).size());
        assertEquals(List.of("a", "b"), QuestLogLayout.paginate(List.of("a", "b"), 0).get(0));
        assertEquals(1, QuestLogLayout.paginate(List.of("a"), -4).size());
    }

    @Test
    void aQuestWithNoKnownDefinitionStillRenders() {
        // Quest definitions are datapack state. If a client is on a quest whose definition it was
        // never sent - a pack removed mid-session, or a sync that lost a race - the row must show
        // the id rather than a blank line that cannot be selected.
        ClientQuests.Entry unknown =
                new ClientQuests.Entry("wolf_trouble", "", "", "", 2, 5, false, "");
        assertEquals("wolf_trouble", unknown.title(), "the id is the fallback title");
        assertEquals(QuestLogLayout.GENERAL,
                QuestLogLayout.categories(List.of(unknown)).get(0),
                "and it is filed somewhere reachable");
    }
```

- [x] **Step 2: Run the test to verify it fails**

Run: `gradlew.bat test --tests "*QuestLogLayoutTest*" -PofflineMcMeta`
Expected: FAIL — `cannot find symbol: method paginate`.

- [x] **Step 3: Add `paginate`**

Add to `QuestLogLayout.java`, before the closing brace:

```java
    /**
     * Splits already-wrapped lines into pages.
     *
     * <p>Wrapping needs the client's font to measure pixel widths and so stays in the panel; this
     * is the part with the off-by-one in it, so it lives where a test can reach it.
     *
     * <p>Always returns at least one page. Zero pages would mean a quest with no journal entry
     * showed a "1 / 0" indicator over an empty body, and would hide its objectives with it.
     */
    public static List<List<String>> paginate(List<String> lines, int linesPerPage) {
        List<String> all = lines == null ? List.of() : lines;
        // A page size of zero or less is not a caller error worth crashing the inventory screen
        // over: it comes from a panel height divided by a font height, which can reach zero at a
        // small GUI scale. One page holding everything is the graceful reading.
        if (linesPerPage < 1) {
            return List.of(List.copyOf(all));
        }
        if (all.isEmpty()) {
            return List.of(List.of());
        }
        List<List<String>> pages = new ArrayList<>();
        for (int start = 0; start < all.size(); start += linesPerPage) {
            pages.add(List.copyOf(all.subList(start, Math.min(all.size(), start + linesPerPage))));
        }
        return List.copyOf(pages);
    }
```

- [x] **Step 4: Run the tests**

Run: `gradlew.bat test --tests "*QuestLogLayoutTest*" -PofflineMcMeta`
Expected: PASS, 15 tests.

- [-] **Step 5: Commit** — *skip unless the user asked*

```bash
git add src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/QuestLogLayout.java src/test/java/net/bullettrain/xenopixelsmod/client/npc/quest/QuestLogLayoutTest.java
git commit -m "feat(npc): page quest journal text"
```

---

### Task 5: The tab strip beside the inventory

**Files:**
- Create: `src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/XenoInventoryTabs.java`
- Test: `src/test/java/net/bullettrain/xenopixelsmod/client/npc/quest/XenoInventoryTabsTest.java`

**Interfaces:**
- Consumes: `ScreenEvent.Render.Post` — `getGuiGraphics()`, `getMouseX()`, `getMouseY()`; `ScreenEvent.MouseButtonPressed.Pre` — `getMouseX()`, `getMouseY()`, `getButton()`, `setCanceled(boolean)`. All verified present in `neoforge-21.1.248-sources.jar` (`ScreenEvent.java:367-408`, `MouseInput.getMouseX/getMouseY`). `InventoryScreen.getGuiLeft()`, `getGuiTop()`, `getXSize()` — the anchors `XenoInventoryEffects` already uses.
- Produces:
  - `XenoInventoryTabs.Tab` enum: `INVENTORY`, `FACTIONS`, `QUESTS` — declared in that order, which is the drawn order.
  - `XenoInventoryTabs.tabRect(int guiLeft, int guiTop, int index) -> int[]{x, y, w, h}`
  - `XenoInventoryTabs.hit(int guiLeft, int guiTop, double mouseX, double mouseY) -> Tab` (null when nothing was hit)
  - `XenoInventoryTabs.open() -> Tab`

- [x] **Step 1: Write the failing test**

Create `src/test/java/net/bullettrain/xenopixelsmod/client/npc/quest/XenoInventoryTabsTest.java`:

```java
package net.bullettrain.xenopixelsmod.client.npc.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hit-testing the tab strip.
 *
 * <p>Pure geometry, deliberately: the render half needs a running client, but "did the click land
 * on the tab the player aimed at" is arithmetic, and it is the half that silently misbehaves.
 */
class XenoInventoryTabsTest {

    private static final int LEFT = 100;
    private static final int TOP = 50;

    @Test
    void theTabOrderIsInventoryThenFactionsThenQuests() {
        // The reference groups them in this order, and the drawn order comes from the enum. A
        // reordering here silently moves every player's tabs.
        assertArrayEquals(
                new XenoInventoryTabs.Tab[]{
                        XenoInventoryTabs.Tab.INVENTORY,
                        XenoInventoryTabs.Tab.FACTIONS,
                        XenoInventoryTabs.Tab.QUESTS},
                XenoInventoryTabs.Tab.values());
    }

    @Test
    void tabsDoNotOverlap() {
        // Overlapping rectangles mean the top edge of one tab activates its neighbour, which reads
        // as a misclick the player cannot explain.
        for (int i = 1; i < XenoInventoryTabs.Tab.values().length; i++) {
            int[] above = XenoInventoryTabs.tabRect(LEFT, TOP, i - 1);
            int[] below = XenoInventoryTabs.tabRect(LEFT, TOP, i);
            assertTrue(above[1] + above[3] <= below[1],
                    "tab " + i + " must start below the previous one's bottom edge");
        }
    }

    @Test
    void aClickInTheMiddleOfEachTabFindsThatTab() {
        for (int index = 0; index < XenoInventoryTabs.Tab.values().length; index++) {
            int[] rect = XenoInventoryTabs.tabRect(LEFT, TOP, index);
            XenoInventoryTabs.Tab hit = XenoInventoryTabs.hit(LEFT, TOP,
                    rect[0] + rect[2] / 2.0, rect[1] + rect[3] / 2.0);
            assertEquals(XenoInventoryTabs.Tab.values()[index], hit, "tab index " + index);
        }
    }

    @Test
    void aClickInsideTheInventoryItselfHitsNoTab() {
        // The tabs sit to the left of the inventory window. A click on a slot must not be stolen,
        // or the player cannot pick up their own items.
        assertNull(XenoInventoryTabs.hit(LEFT, TOP, LEFT + 40, TOP + 40));
    }

    @Test
    void aClickJustPastTheBottomTabHitsNothing() {
        // Off-by-one at the far edge: the first pixel below the strip must not belong to the last
        // tab, or clicks in empty space open the quest log.
        int last = XenoInventoryTabs.Tab.values().length - 1;
        int[] rect = XenoInventoryTabs.tabRect(LEFT, TOP, last);
        assertNull(XenoInventoryTabs.hit(LEFT, TOP, rect[0] + 2.0, rect[1] + rect[3] + 1.0));
    }

    @Test
    void aClickOnTheTopEdgeCountsAndOnTheBottomEdgeDoesNot() {
        // Half-open intervals, so two stacked tabs can never both claim one pixel row.
        int[] rect = XenoInventoryTabs.tabRect(LEFT, TOP, 0);
        assertEquals(XenoInventoryTabs.Tab.INVENTORY,
                XenoInventoryTabs.hit(LEFT, TOP, rect[0], rect[1]));
        assertNull(XenoInventoryTabs.hit(LEFT, TOP, rect[0] + rect[2], rect[1]));
    }

    @Test
    void theStripSitsLeftOfTheInventoryWindow() {
        // Anchored off getGuiLeft() the way XenoInventoryEffects anchors its rail off
        // getGuiLeft() + getXSize(). Drawing over the window would cover the armour slots.
        for (int index = 0; index < XenoInventoryTabs.Tab.values().length; index++) {
            int[] rect = XenoInventoryTabs.tabRect(LEFT, TOP, index);
            assertTrue(rect[0] + rect[2] <= LEFT,
                    "tab " + index + " must not overlap the window");
        }
    }

    @Test
    void theInventoryTabIsOpenBeforeAnybodyClicksAnything() {
        // Otherwise opening the inventory would show a quest log over the player's items.
        assertEquals(XenoInventoryTabs.Tab.INVENTORY, XenoInventoryTabs.open());
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `gradlew.bat test --tests "*XenoInventoryTabsTest*" -PofflineMcMeta`
Expected: FAIL — `cannot find symbol: class XenoInventoryTabs`.

- [x] **Step 3: Write the geometry and the event hooks**

Create `src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/XenoInventoryTabs.java`:

```java
package net.bullettrain.xenopixelsmod.client.npc.quest;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Factions and Quests, as tabs beside the vanilla inventory.
 *
 * <p>Attached through {@code ScreenEvent} and anchored on {@code getGuiLeft()} /
 * {@code getGuiTop()} exactly as {@code XenoInventoryEffects} anchors its effect rail - a hook this
 * repo has already proven, rather than a mixin into the inventory screen.
 *
 * <p>The strip is drawn to the <em>left</em> of the window because the right is where the effect
 * rail already lives.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class XenoInventoryTabs {

    /** Drawn top to bottom in declaration order; the reference groups them this way. */
    public enum Tab {
        INVENTORY,
        FACTIONS,
        QUESTS
    }

    private static final int TAB_WIDTH = 28;
    private static final int TAB_HEIGHT = 24;

    /** A gap, so two tabs never share an edge and a click can never be ambiguous. */
    private static final int TAB_GAP = 2;

    /** Where the strip starts, relative to the window top. */
    private static final int TOP_INSET = 4;

    private static Tab open = Tab.INVENTORY;

    private XenoInventoryTabs() {
    }

    /** Which tab is showing. Starts on the inventory, so opening it shows the player's items. */
    public static Tab open() {
        return open;
    }

    /** {@code {x, y, width, height}} of one tab. */
    public static int[] tabRect(int guiLeft, int guiTop, int index) {
        int x = guiLeft - TAB_WIDTH;
        int y = guiTop + TOP_INSET + index * (TAB_HEIGHT + TAB_GAP);
        return new int[]{x, y, TAB_WIDTH, TAB_HEIGHT};
    }

    /**
     * The tab under this point, or null.
     *
     * <p>Half-open on both axes, so two stacked tabs can never both claim one pixel row.
     */
    public static Tab hit(int guiLeft, int guiTop, double mouseX, double mouseY) {
        for (int index = 0; index < Tab.values().length; index++) {
            int[] rect = tabRect(guiLeft, guiTop, index);
            if (mouseX >= rect[0] && mouseX < rect[0] + rect[2]
                    && mouseY >= rect[1] && mouseY < rect[1] + rect[3]) {
                return Tab.values()[index];
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen inventory) || event.getButton() != 0) {
            return;
        }
        Tab clicked = hit(inventory.getGuiLeft(), inventory.getGuiTop(),
                event.getMouseX(), event.getMouseY());
        if (clicked != null) {
            open = clicked;
            // Cancelled only when a tab was actually hit, so every other click still reaches the
            // inventory's own slot handling.
            event.setCanceled(true);
            return;
        }
        // Panels land in the next two tasks; the call is restored in Task 7.
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen inventory)) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int left = inventory.getGuiLeft();
        int top = inventory.getGuiTop();

        for (int index = 0; index < Tab.values().length; index++) {
            int[] rect = tabRect(left, top, index);
            boolean active = Tab.values()[index] == open;
            graphics.fill(rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3],
                    active ? 0xF0121C26 : 0xC00A1018);
            graphics.fill(rect[0], rect[1], rect[0] + 2, rect[1] + rect[3],
                    active ? 0xFF35D7FF : 0xFF33475A);
            graphics.drawString(Minecraft.getInstance().font,
                    Component.literal(label(Tab.values()[index])), rect[0] + 10, rect[1] + 8,
                    active ? 0xFFFFFFFF : 0xFF8FA3B4, false);
        }

        // Panels land in the next two tasks; until then the strip draws and switches, which is
        // what this task's tests cover.
    }

    private static String label(Tab tab) {
        return switch (tab) {
            case INVENTORY -> "I";
            case FACTIONS -> "F";
            case QUESTS -> "Q";
        };
    }
}
```

- [x] **Step 4: Run the tests**

Run: `gradlew.bat test --tests "*XenoInventoryTabsTest*" -PofflineMcMeta`
Expected: PASS, 8 tests.

- [x] **Step 5: Compile**

Run: `gradlew.bat compileJava -PofflineMcMeta`
Expected: BUILD SUCCESSFUL.

- [-] **Step 6: Commit** — *skip unless the user asked*

```bash
git add src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/XenoInventoryTabs.java src/test/java/net/bullettrain/xenopixelsmod/client/npc/quest/XenoInventoryTabsTest.java
git commit -m "feat(npc): add the inventory tab strip"
```

---

### Task 6: The Quests panel

**Files:**
- Create: `src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/QuestLogPanel.java`
- Test: `src/test/java/net/bullettrain/xenopixelsmod/client/npc/quest/QuestLogPanelTest.java`

**Interfaces:**
- Consumes: `ClientQuests.all()`, `ClientQuests.isSynced()` (Task 1); `QuestLogLayout.categories/inCategory/paginate` (Tasks 3-4); `InventoryScreen.getGuiLeft()/getGuiTop()/getXSize()`; `Font.split(FormattedText, int)` returning `List<FormattedCharSequence>`.
- Produces:
  - `QuestLogPanel.render(GuiGraphics, InventoryScreen, int mouseX, int mouseY)`
  - `QuestLogPanel.click(InventoryScreen, double mouseX, double mouseY) -> boolean`

Three columns, per spec §4: category buttons left, that category's quests in the middle, the selected quest right — paged `logText` with forward/back, an **Objectives** line with progress, and *Complete with …* **only** when the quest is ready and names a completer.

- [x] **Step 1: Write the failing test**

Create `src/test/java/net/bullettrain/xenopixelsmod/client/npc/quest/QuestLogPanelTest.java`:

```java
package net.bullettrain.xenopixelsmod.client.npc.quest;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the quest panel shows, and what it must not.
 *
 * <p>Rendering needs a running client, so these are source-level. Each names a rule the spec states
 * explicitly and that a plausible implementation would break silently.
 */
class QuestLogPanelTest {

    private static String code(String file) throws IOException {
        String raw = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest", file),
                StandardCharsets.UTF_8);
        // Comments stripped: a test asserting "this call exists" must not be satisfied by a
        // comment that merely mentions it.
        return raw.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    @Test
    void thePanelReadsActiveQuestsOnly() throws IOException {
        // Spec section 3.1: the log groups ACTIVE quests. Completed ones are not synced at all,
        // so a panel reaching for them would be reaching for something that does not exist.
        String panel = code("QuestLogPanel.java");
        assertTrue(panel.contains("ClientQuests.all()"));
        assertFalse(panel.contains("completed"), "there is no completed list on the client");
    }

    @Test
    void notYetSyncedReadsDifferentlyFromNoQuests() {
        // Two states; one is "you have none", the other is "the server has not told us yet", and
        // showing the first for the second is a lie the player cannot detect.
        ClientQuests.clear();
        assertFalse(ClientQuests.isSynced());
        ClientQuests.accept(List.of());
        assertTrue(ClientQuests.isSynced());
    }

    @Test
    void thePanelDistinguishesThoseTwoStates() throws IOException {
        String panel = code("QuestLogPanel.java");
        assertTrue(panel.contains("ClientQuests.isSynced()"),
                "the panel must branch on whether the sync arrived");
        assertTrue(panel.contains("no active quests"),
                "and say the empty case out loud rather than drawing an empty box");
    }

    @Test
    void completeWithIsShownOnlyForAReadyQuestThatNamesSomebody() throws IOException {
        // An INSTANT quest has no completer. Printing "Complete with" and a blank name would send
        // the player looking for an NPC that does not exist.
        String panel = code("QuestLogPanel.java");
        int marker = panel.indexOf("Complete with");
        assertTrue(marker >= 0, "the NPC-mode hint should be present");
        String before = panel.substring(Math.max(0, marker - 400), marker);
        assertTrue(before.contains("completerNpc().isEmpty()"),
                "guarded on the quest actually naming a completer");
        assertTrue(before.contains("ready()"), "and on it actually being ready");
    }

    @Test
    void theObjectivesLineShowsProgressAgainstTarget() throws IOException {
        String panel = code("QuestLogPanel.java");
        assertTrue(panel.contains("progress()") && panel.contains("target()"),
                "a quest log without a progress count is not a quest log");
        assertTrue(panel.contains("Objectives"), "and the spec names the section");
    }

    @Test
    void pagingGoesThroughTheTestedHelper() throws IOException {
        // Rather than an inline subList, which is where the dropped-tail bug lives.
        assertTrue(code("QuestLogPanel.java").contains("QuestLogLayout.paginate("));
    }

    @Test
    void categoriesGoThroughTheNaturalSort() throws IOException {
        assertTrue(code("QuestLogPanel.java").contains("QuestLogLayout.categories("));
    }

    @Test
    void aSelectionThatNoLongerExistsFallsBackRatherThanBlanking() throws IOException {
        // A quest completed while its own page was open must not leave the panel showing nothing
        // with no way back.
        String panel = code("QuestLogPanel.java");
        assertTrue(panel.contains("categories.contains(selectedCategory)"),
                "a vanished category falls back to the first");
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `gradlew.bat test --tests "*QuestLogPanelTest*" -PofflineMcMeta`
Expected: FAIL — `NoSuchFileException: QuestLogPanel.java`.

- [x] **Step 3: Write `QuestLogPanel`**

Create `src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/QuestLogPanel.java`:

```java
package net.bullettrain.xenopixelsmod.client.npc.quest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * The Quests tab body: categories, the quests in one, and the selected quest's journal entry.
 *
 * <p>Active quests only. The client is never sent completed ones - they stay on the server for the
 * master gate and for the availability checks a later spec will add.
 */
public final class QuestLogPanel {

    private static final int PANEL_WIDTH = 248;
    private static final int PANEL_HEIGHT = 166;
    private static final int COLUMN_CATEGORIES = 66;
    private static final int COLUMN_QUESTS = 78;
    private static final int PADDING = 6;
    private static final int LINE_HEIGHT = 10;

    private static String selectedCategory = "";
    private static String selectedQuest = "";
    private static int page;

    private QuestLogPanel() {
    }

    public static void render(GuiGraphics graphics, InventoryScreen inventory,
                              int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        int left = inventory.getGuiLeft() + inventory.getXSize() + PADDING;
        int top = inventory.getGuiTop();

        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xF00A1018);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 2, 0xFF35D7FF);

        List<ClientQuests.Entry> quests = ClientQuests.all();

        if (!ClientQuests.isSynced()) {
            // Not the same as having no quests. Saying "no quests" here would be a lie the player
            // cannot distinguish from the truth.
            graphics.drawString(minecraft.font, Component.literal("Loading quests..."),
                    left + PADDING, top + PADDING + 4, 0xFF8FA3B4, false);
            return;
        }
        if (quests.isEmpty()) {
            graphics.drawString(minecraft.font,
                    Component.literal("You have no active quests."),
                    left + PADDING, top + PADDING + 4, 0xFF8FA3B4, false);
            return;
        }

        List<String> categories = QuestLogLayout.categories(quests);
        if (!categories.contains(selectedCategory)) {
            // The selected category can vanish under the player - the last quest in it finishes
            // while its page is open. Falling back beats showing an empty panel with no way out.
            selectedCategory = categories.get(0);
            selectedQuest = "";
            page = 0;
        }

        int y = top + PADDING;
        for (String category : categories) {
            boolean active = category.equals(selectedCategory);
            graphics.drawString(minecraft.font, Component.literal(category),
                    left + PADDING, y, active ? 0xFFFFC928 : 0xFF8FA3B4, false);
            y += LINE_HEIGHT;
        }

        List<ClientQuests.Entry> inCategory = QuestLogLayout.inCategory(quests, selectedCategory);
        ClientQuests.Entry selected = null;
        y = top + PADDING;
        for (ClientQuests.Entry quest : inCategory) {
            boolean active = quest.id().equals(selectedQuest);
            if (active) {
                selected = quest;
            }
            graphics.drawString(minecraft.font, Component.literal(quest.title()),
                    left + COLUMN_CATEGORIES, y, active ? 0xFFFFFFFF : 0xFF8FA3B4, false);
            y += LINE_HEIGHT;
        }
        if (selected == null && !inCategory.isEmpty()) {
            selected = inCategory.get(0);
            selectedQuest = selected.id();
        }
        if (selected == null) {
            return;
        }

        int detailX = left + COLUMN_CATEGORIES + COLUMN_QUESTS;
        int detailWidth = PANEL_WIDTH - COLUMN_CATEGORIES - COLUMN_QUESTS - PADDING * 2;
        int detailY = top + PADDING;

        graphics.drawString(minecraft.font, Component.literal(selected.title()),
                detailX, detailY, 0xFFFFC928, false);
        detailY += LINE_HEIGHT + 2;

        // Wrapping needs the font to measure pixels, so it happens here; the paging that follows
        // is the tested helper, because that is where the dropped-tail bug lives.
        List<String> wrapped = new ArrayList<>();
        for (FormattedCharSequence line
                : minecraft.font.split(Component.literal(selected.logText()), detailWidth)) {
            StringBuilder sb = new StringBuilder();
            line.accept((index, style, codePoint) -> {
                sb.appendCodePoint(codePoint);
                return true;
            });
            wrapped.add(sb.toString());
        }

        int footer = LINE_HEIGHT * 4;
        int bodyLines = Math.max(1, (PANEL_HEIGHT - (detailY - top) - footer) / LINE_HEIGHT);
        List<List<String>> pages = QuestLogLayout.paginate(wrapped, bodyLines);
        page = Math.max(0, Math.min(pages.size() - 1, page));
        for (String line : pages.get(page)) {
            graphics.drawString(minecraft.font, Component.literal(line),
                    detailX, detailY, 0xFFD8E4EE, false);
            detailY += LINE_HEIGHT;
        }
        if (pages.size() > 1) {
            graphics.drawString(minecraft.font,
                    Component.literal("Page " + (page + 1) + " / " + pages.size()),
                    detailX, top + PANEL_HEIGHT - LINE_HEIGHT * 4, 0xFF688195, false);
        }

        // Only a ready quest that names somebody. Printing this for an instant quest would send
        // the player looking for an NPC that does not exist.
        if (selected.ready() && !selected.completerNpc().isEmpty()) {
            graphics.drawString(minecraft.font,
                    Component.literal("Complete with " + selected.completerNpc()),
                    detailX, top + PANEL_HEIGHT - LINE_HEIGHT * 3, 0xFF7CE08A, false);
        }

        graphics.drawString(minecraft.font, Component.literal("Objectives"),
                detailX, top + PANEL_HEIGHT - LINE_HEIGHT * 2, 0xFF35D7FF, false);
        graphics.drawString(minecraft.font,
                Component.literal(selected.progress() + " / " + selected.target()),
                detailX, top + PANEL_HEIGHT - LINE_HEIGHT,
                selected.ready() ? 0xFF7CE08A : 0xFFD8E4EE, false);
    }

    /**
     * Handles a click inside the panel.
     *
     * @return true when the click was consumed, so the caller cancels it
     */
    public static boolean click(InventoryScreen inventory, double mouseX, double mouseY) {
        int left = inventory.getGuiLeft() + inventory.getXSize() + PADDING;
        int top = inventory.getGuiTop();
        List<ClientQuests.Entry> quests = ClientQuests.all();
        if (quests.isEmpty()) {
            return false;
        }

        int row = (int) ((mouseY - (top + PADDING)) / LINE_HEIGHT);
        if (row < 0) {
            return false;
        }

        List<String> categories = QuestLogLayout.categories(quests);
        if (mouseX >= left + PADDING && mouseX < left + COLUMN_CATEGORIES
                && row < categories.size()) {
            selectedCategory = categories.get(row);
            selectedQuest = "";
            page = 0;
            return true;
        }

        List<ClientQuests.Entry> inCategory = QuestLogLayout.inCategory(quests, selectedCategory);
        if (mouseX >= left + COLUMN_CATEGORIES && mouseX < left + COLUMN_CATEGORIES + COLUMN_QUESTS
                && row < inCategory.size()) {
            selectedQuest = inCategory.get(row).id();
            page = 0;
            return true;
        }

        // A click anywhere in the detail column turns the page forward; render clamps it back to
        // the last page, so the end of a short entry is not a dead click that eats the input.
        if (mouseX >= left + COLUMN_CATEGORIES + COLUMN_QUESTS && mouseX < left + PANEL_WIDTH
                && mouseY >= top && mouseY < top + PANEL_HEIGHT) {
            page++;
            return true;
        }
        return false;
    }
}
```

- [x] **Step 4: Run the tests**

Run: `gradlew.bat test --tests "*QuestLogPanelTest*" -PofflineMcMeta`
Expected: PASS, 8 tests.

- [x] **Step 5: Compile**

Run: `gradlew.bat compileJava -PofflineMcMeta`
Expected: BUILD SUCCESSFUL.

- [-] **Step 6: Commit** — *skip unless the user asked*

```bash
git add src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/QuestLogPanel.java src/test/java/net/bullettrain/xenopixelsmod/client/npc/quest/QuestLogPanelTest.java
git commit -m "feat(npc): draw the quest log panel"
```

---

### Task 7: The player's own faction standings

**Files:**
- Create: `src/main/java/net/bullettrain/xenopixelsmod/client/npc/faction/ClientStandings.java`
- Create: `src/main/java/net/bullettrain/xenopixelsmod/network/packet/SyncStandingsPacket.java`
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/network/ModNetwork.java` (append; `PROTOCOL` is already `"83"` from Task 1, and one bump covers every wire change in a release)
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/features/progression/QuestSync.java`
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/ClientQuestReset.java`
- Test: `src/test/java/net/bullettrain/xenopixelsmod/network/SyncStandingsPacketTest.java`

**Why this task exists.** The Factions panel in the next task needs the player's own standing, and nothing carries it today. Spec §4 says the Factions tab shows "the player's standing" and asserts every input already exists — but `XenoPlayerData.factionStanding` is a server capability and reaches no client. That assumption is the gap; this closes it.

**Interfaces:**
- Consumes: `XenoPlayerData.factionStandings()` → `Map<String, Integer>` (`XenoPlayerData.java:176`); `XenoCapabilities.get(ServerPlayer)`; `ModNetwork.sendToPlayer`; `XenoFaction.MAX_STANDING` (`XenoFaction.java:39`).
- Produces:
  - `ClientStandings.accept(Map<String, Integer>)`, `ClientStandings.of(String factionId, int fallback) -> int`, `ClientStandings.size() -> int`, `ClientStandings.clear()`, `ClientStandings.MAX_STANDINGS`
  - `SyncStandingsPacket.forPlayer(ServerPlayer) -> SyncStandingsPacket`

- [x] **Step 1: Write the failing test**

Create `src/test/java/net/bullettrain/xenopixelsmod/network/SyncStandingsPacketTest.java`:

```java
package net.bullettrain.xenopixelsmod.network;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.client.npc.faction.ClientStandings;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This player's faction standings, as the client knows them.
 *
 * <p>Looked up by id rather than iterated, so the server-side {@code Map.copyOf} behind
 * {@code factionStandings()} - which is unordered, and has silently scrambled documented-ordered
 * data three times in this subsystem - cannot affect the order rows are drawn in. The panel walks
 * {@code ClientFactions.all()}, which is ordered, and asks this holder one faction at a time.
 */
class SyncStandingsPacketTest {

    @Test
    void aStoredStandingIsReturned() {
        ClientStandings.accept(Map.of("saiyan", 250));
        assertEquals(250, ClientStandings.of("saiyan", 0));
    }

    @Test
    void anUnknownFactionFallsBackToItsDefault() {
        // The same rule XenoPlayerData.getFactionStanding applies server-side: a faction the
        // player has never interacted with reads as that faction's own default, not as zero -
        // defaulting to zero would read as "neutral" for a faction whose default is hostile.
        ClientStandings.accept(Map.of("saiyan", 250));
        assertEquals(-100, ClientStandings.of("frieza_force", -100));
    }

    @Test
    void lookupIsCaseInsensitive() {
        // Ids are lower-cased everywhere else in this subsystem; a screen that missed on case
        // would silently show the fallback for a standing the player actually has.
        ClientStandings.accept(Map.of("saiyan", 250));
        assertEquals(250, ClientStandings.of("Saiyan", 0));
    }

    @Test
    void aBlankOrNullIdIsTheFallback() {
        ClientStandings.accept(Map.of("saiyan", 250));
        assertEquals(7, ClientStandings.of("", 7));
        assertEquals(7, ClientStandings.of(null, 7));
    }

    @Test
    void disconnectForgetsThem() {
        // Otherwise world A's standings are shown against world B's factions.
        ClientStandings.accept(Map.of("saiyan", 250));
        ClientStandings.clear();
        assertEquals(0, ClientStandings.of("saiyan", 0));
    }

    @Test
    void moreStandingsThanTheCapAreTruncatedNotThrown() {
        Map<String, Integer> many = new HashMap<>();
        for (int i = 0; i < ClientStandings.MAX_STANDINGS + 10; i++) {
            many.put("f" + i, i);
        }
        ClientStandings.accept(many);
        // Truncation is by count, so which entries survive is unspecified. What matters is that a
        // hostile server cannot make the client allocate without bound.
        assertEquals(ClientStandings.MAX_STANDINGS, ClientStandings.size());
    }

    @Test
    void standingsRideTheSamePushAsTheQuestLog() throws IOException {
        // A separate trigger would fire at the same moments and open a window where one had
        // arrived and the other had not - the panel would then draw new factions against old
        // standings.
        String sync = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/features/progression",
                        "QuestSync.java"), StandardCharsets.UTF_8);
        assertTrue(sync.contains("SyncStandingsPacket.forPlayer(player)"));
    }

    @Test
    void disconnectClearsThemToo() throws IOException {
        String reset = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest",
                        "ClientQuestReset.java"), StandardCharsets.UTF_8);
        assertTrue(reset.contains("ClientStandings.clear()"));
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `gradlew.bat test --tests "*SyncStandingsPacketTest*" -PofflineMcMeta`
Expected: FAIL — `cannot find symbol: class ClientStandings`.

- [x] **Step 3: Write `ClientStandings`**

Create `src/main/java/net/bullettrain/xenopixelsmod/client/npc/faction/ClientStandings.java`:

```java
package net.bullettrain.xenopixelsmod.client.npc.faction;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * This player's faction standings, as the client knows them.
 *
 * <p>{@link ClientFactions} carries what the factions <em>are</em>, including each one's default
 * standing. That default is the faction's, not the player's - reading it as the player's would show
 * everyone on a server identical numbers, and would look entirely right while doing so.
 *
 * <p>Standings live in {@code XenoPlayerData} on the server and reach no client on their own, which
 * is the same trap the faction list itself hit.
 *
 * <p>A map looked up by id, never iterated for display: the server-side source is a
 * {@code Map.copyOf}, which is unordered. The Factions tab walks {@link ClientFactions#all()} -
 * which is ordered - and asks this holder one faction at a time.
 */
public final class ClientStandings {

    /** Matches {@code SyncFactionsPacket.MAX_FACTIONS}: a player cannot stand with more. */
    public static final int MAX_STANDINGS = 256;

    private static volatile Map<String, Integer> standings = Map.of();

    private ClientStandings() {
    }

    /** Replaces the client's view. Called when the server syncs. */
    public static void accept(Map<String, Integer> next) {
        Map<String, Integer> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry
                : (next == null ? Map.<String, Integer>of() : next).entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()
                    || copy.size() >= MAX_STANDINGS) {
                continue;
            }
            copy.put(entry.getKey().trim().toLowerCase(Locale.ROOT),
                    entry.getValue() == null ? 0 : entry.getValue());
        }
        standings = Collections.unmodifiableMap(copy);
    }

    /**
     * This player's standing with {@code factionId}, or {@code fallback} when they have none.
     *
     * <p>The fallback is the faction's own default, which is what
     * {@code XenoPlayerData.getFactionStanding} returns server-side for a faction the player has
     * never interacted with. Defaulting to zero instead would read as "neutral" for a faction whose
     * default is hostile.
     */
    public static int of(String factionId, int fallback) {
        if (factionId == null || factionId.isBlank()) {
            return fallback;
        }
        Integer stored = standings.get(factionId.trim().toLowerCase(Locale.ROOT));
        return stored == null ? fallback : stored;
    }

    /** How many standings are held. For tests, and for a bounds check. */
    public static int size() {
        return standings.size();
    }

    /** Forgets them. Called on disconnect, so world A's standings do not outlive world A. */
    public static void clear() {
        standings = Map.of();
    }
}
```

- [x] **Step 4: Run the test to verify the pure ones pass**

Run: `gradlew.bat test --tests "*SyncStandingsPacketTest*" -PofflineMcMeta`
Expected: the 6 holder tests PASS; `standingsRideTheSamePushAsTheQuestLog` and `disconnectClearsThemToo` still FAIL. Steps 7 and 8 fix those.

- [x] **Step 5: Write `SyncStandingsPacket`**

First confirm the clamp helper is callable:

```bash
grep -n "clampStanding\|MIN_STANDING\|MAX_STANDING" src/main/java/net/bullettrain/xenopixelsmod/npc/faction/XenoFaction.java
```

Expected: `public static final int MAX_STANDING = 1000;` and a `clampStanding(int)` near line 51. If `clampStanding` is not public, clamp inline with `Math.max(XenoFaction.MIN_STANDING, Math.min(XenoFaction.MAX_STANDING, value))` rather than widening its visibility for one caller.

Create `src/main/java/net/bullettrain/xenopixelsmod/network/packet/SyncStandingsPacket.java`:

```java
package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.bullettrain.xenopixelsmod.client.npc.faction.ClientStandings;
import net.bullettrain.xenopixelsmod.npc.faction.XenoFaction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * S2C: this player's faction standings, so the Factions tab can show their own.
 *
 * <p>{@code SyncFactionsPacket} carries what the factions are, including each one's default
 * standing - the same for everybody. This carries what one player has actually earned, which is
 * per-player and must not be broadcast.
 */
public record SyncStandingsPacket(Map<String, Integer> standings) {

    /** Matches {@code SyncFactionsPacket.MAX_FACTIONS}. */
    private static final int MAX_STANDINGS = 256;

    private static final int MAX_ID = 64;

    /** What this player currently stands at. */
    public static SyncStandingsPacket forPlayer(ServerPlayer player) {
        XenoPlayerData data = player == null ? null : XenoCapabilities.get(player).orElse(null);
        return new SyncStandingsPacket(data == null ? Map.of() : data.factionStandings());
    }

    public SyncStandingsPacket(FriendlyByteBuf buf) {
        this(read(buf));
    }

    private static Map<String, Integer> read(FriendlyByteBuf buf) {
        int count = Math.min(MAX_STANDINGS, buf.readVarInt());
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf(MAX_ID);
            // Shifted so a negative standing survives a VarInt, which is unsigned-friendly only -
            // the same trick SyncFactionsPacket uses, for the same reason.
            int standing = buf.readVarInt() - XenoFaction.MAX_STANDING;
            out.put(id, standing);
        }
        return out;
    }

    public void encode(FriendlyByteBuf buf) {
        Map<String, Integer> map = standings == null ? Map.of() : standings;
        int count = Math.min(MAX_STANDINGS, map.size());
        buf.writeVarInt(count);
        int written = 0;
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (written >= count) {
                break;
            }
            buf.writeUtf(entry.getKey(), MAX_ID);
            buf.writeVarInt(XenoFaction.clampStanding(
                    entry.getValue() == null ? 0 : entry.getValue()) + XenoFaction.MAX_STANDING);
            written++;
        }
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> ClientStandings.accept(standings));
        ctx.setPacketHandled(true);
    }
}
```

- [x] **Step 6: Register the packet**

Append at the **end** of `ModNetwork.register()`, after the `SyncQuestsPacket` block added in Task 1:

```java
        // Appended, as this list always is - ids are positional.
        CHANNEL.messageBuilder(
                        net.bullettrain.xenopixelsmod.network.packet.SyncStandingsPacket.class,
                        id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.SyncStandingsPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.SyncStandingsPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.SyncStandingsPacket::handle)
                .add();
```

`PROTOCOL` stays at `"83"` — it was bumped in Task 1, and one bump covers every wire change in a single release.

- [x] **Step 7: Send it alongside the quest log**

In `QuestSync.java`, add `import net.bullettrain.xenopixelsmod.network.packet.SyncStandingsPacket;` and extend `push`:

```java
    public static void push(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ModNetwork.sendToPlayer(player, SyncQuestsPacket.forPlayer(player));
        // Standings ride the same push. The only thing that moves them today is a quest reward
        // paying faction points inside completeQuest - which is one of this method's callers - so
        // a separate trigger would fire at exactly the same moments and open a window where one
        // had arrived and the other had not.
        ModNetwork.sendToPlayer(player, SyncStandingsPacket.forPlayer(player));
    }
```

- [x] **Step 8: Clear them on disconnect**

In `ClientQuestReset.java`, add to `onLoggingOut`, after the existing `ClientQuests.clear()`:

```java
        net.bullettrain.xenopixelsmod.client.npc.faction.ClientStandings.clear();
```

- [x] **Step 9: Run the tests**

Run: `gradlew.bat test --tests "*SyncStandingsPacketTest*" -PofflineMcMeta`
Expected: PASS, 8 tests.

- [x] **Step 10: Compile**

Run: `gradlew.bat compileJava -PofflineMcMeta`
Expected: BUILD SUCCESSFUL.

- [-] **Step 11: Commit** — *skip unless the user asked*

```bash
git add src/main/java/net/bullettrain/xenopixelsmod/client/npc/faction/ClientStandings.java src/main/java/net/bullettrain/xenopixelsmod/network/packet/SyncStandingsPacket.java src/main/java/net/bullettrain/xenopixelsmod/network/ModNetwork.java src/main/java/net/bullettrain/xenopixelsmod/features/progression/QuestSync.java src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/ClientQuestReset.java src/test/java/net/bullettrain/xenopixelsmod/network/SyncStandingsPacketTest.java
git commit -m "feat(npc): show the player's own faction standings"
```

---

### Task 8: The Factions panel, and wiring both panels in

**Files:**
- Create: `src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/FactionPanel.java`
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/XenoInventoryTabs.java`
- Test: `src/test/java/net/bullettrain/xenopixelsmod/client/npc/quest/FactionPanelTest.java`

**Interfaces:**
- Consumes: `ClientFactions.all()` → `List<ClientFactions.Entry>`, `ClientFactions.isEmpty()` (`ClientFactions.java:56-78`); `ClientFactions.Entry.id()/name()/color()/defaultStanding()`; `XenoFaction.attitudeAt(int)` → `XenoFaction.Attitude` (`XenoFaction.java:55`); `ClientStandings.of(String factionId, int fallback)` (Task 7).

**Read this before writing the panel.** `ClientFactions.Entry.defaultStanding()` is the *faction's* default, not the player's own. Reading it here would show every player on a server identical numbers, and would look entirely right. The player's own standing arrived in Task 7; this task writes the panel against `ClientStandings.of(...)` from the start, with the faction default as its fallback — which is exactly what `XenoPlayerData.getFactionStanding` does server-side for a faction the player has no stored standing with.
- Produces: `FactionPanel.render(GuiGraphics, InventoryScreen, int mouseX, int mouseY)`.

Read-only, per spec §4. Built now so the tab group is right the first time rather than Quests occupying slot two and moving later. **No controls**: nothing on the client can change a standing, so a button here would be a dead control.

- [x] **Step 1: Verify the Attitude constants before writing a switch over them**

Run:

```bash
sed -n '55,80p' src/main/java/net/bullettrain/xenopixelsmod/npc/faction/XenoFaction.java
```

Note every constant of `XenoFaction.Attitude`. The two `switch` expressions in Step 3 must cover all of them — an unhandled constant will not compile, which is the outcome you want. If the set is not exactly `FRIENDLY`, `NEUTRAL`, `HOSTILE`, extend both switches and the test's vocabulary assertion to match.

- [x] **Step 2: Write the failing test**

Create `src/test/java/net/bullettrain/xenopixelsmod/client/npc/quest/FactionPanelTest.java`:

```java
package net.bullettrain.xenopixelsmod.client.npc.quest;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Factions tab is a read-out, not a control panel. */
class FactionPanelTest {

    private static String code(String file) throws IOException {
        String raw = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest", file),
                StandardCharsets.UTF_8);
        return raw.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    @Test
    void itUsesOurOwnAttitudeVocabulary() throws IOException {
        // Theirs is Friendly / Neutral / Unfriendly; ours is FRIENDLY / NEUTRAL / HOSTILE, which
        // is already in XenoFaction.Attitude and already on the wire. A second vocabulary on the
        // screen only would mean two names for one state.
        String panel = code("FactionPanel.java");
        assertTrue(panel.contains("attitudeAt("),
                "standing should be classified by the shared rule, not a local threshold");
        assertFalse(panel.contains("Unfriendly"), "that is their word, not ours");
    }

    @Test
    void thePanelReadsThePlayersOwnStanding() throws IOException {
        // ClientFactions.Entry.defaultStanding() is the FACTION's default. Reading it as the
        // player's would show everyone on a server identical numbers and would look correct.
        String panel = code("FactionPanel.java");
        assertTrue(panel.contains("ClientStandings.of("),
                "the player's own standing, not the faction's default");
        assertFalse(panel.contains("attitudeAt(faction.defaultStanding())"),
                "classifying the default is the bug this guards");
    }

    @Test
    void itHasNoControls() throws IOException {
        // Nothing on the client can change a standing, so a button here would be a dead control -
        // the standing rule is that a widget goes in only once something consumes its value.
        String panel = code("FactionPanel.java");
        assertFalse(panel.contains("Button.builder"), "read-only means no buttons");
        assertFalse(panel.contains("sendToServer"), "and nothing to send");
    }

    @Test
    void theEmptyCaseSaysSomething() throws IOException {
        // Their copy is "You have no standings with any faction". An empty panel reads as broken.
        String panel = code("FactionPanel.java");
        assertTrue(panel.contains("ClientFactions.isEmpty()"));
        assertTrue(panel.contains("no standings"));
    }

    @Test
    void theListIsBoundedByThePanelHeight() throws IOException {
        // A server with 200 factions must not draw 200 rows down over the hotbar and the chat.
        assertTrue(code("FactionPanel.java").contains("PANEL_HEIGHT"),
                "the row loop should stop at the panel's own bottom edge");
    }

    @Test
    void bothPanelsAreWiredIntoTheTabStrip() throws IOException {
        // An unreferenced panel compiles perfectly and draws nothing, which is the failure this
        // catches.
        String tabs = code("XenoInventoryTabs.java");
        assertTrue(tabs.contains("QuestLogPanel.render("), "the quests tab must draw its body");
        assertTrue(tabs.contains("FactionPanel.render("), "and so must the factions tab");
        assertTrue(tabs.contains("QuestLogPanel.click("), "and the quest panel must take clicks");
    }
}
```

- [x] **Step 3: Run the test to verify it fails**

Run: `gradlew.bat test --tests "*FactionPanelTest*" -PofflineMcMeta`
Expected: FAIL — `NoSuchFileException: FactionPanel.java`.

- [x] **Step 4: Write `FactionPanel`**

Create `src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/FactionPanel.java`:

```java
package net.bullettrain.xenopixelsmod.client.npc.quest;

import net.bullettrain.xenopixelsmod.client.npc.faction.ClientFactions;
import net.bullettrain.xenopixelsmod.client.npc.faction.ClientStandings;
import net.bullettrain.xenopixelsmod.npc.faction.XenoFaction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/**
 * The Factions tab body: a read-out of where the player stands.
 *
 * <p>Read-only on purpose. Standings move on NPC death, in combat, and through quest rewards - all
 * server-side - so a control here would be a widget with nothing behind it.
 *
 * <p>Built alongside the quest log rather than later so the tab group is right the first time,
 * instead of Quests sitting in slot two and moving once Factions arrives.
 */
public final class FactionPanel {

    private static final int PANEL_WIDTH = 248;
    private static final int PANEL_HEIGHT = 166;
    private static final int PADDING = 6;
    private static final int LINE_HEIGHT = 10;

    private FactionPanel() {
    }

    public static void render(GuiGraphics graphics, InventoryScreen inventory,
                              int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        int left = inventory.getGuiLeft() + inventory.getXSize() + PADDING;
        int top = inventory.getGuiTop();

        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xF00A1018);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 2, 0xFF35D7FF);

        if (ClientFactions.isEmpty()) {
            // Covers both "the pack defines none" and "the sync has not arrived". Either way there
            // is nothing to show, and saying so beats an empty rectangle that reads as broken.
            graphics.drawString(minecraft.font,
                    Component.literal("You have no standings with any faction."),
                    left + PADDING, top + PADDING + 4, 0xFF8FA3B4, false);
            return;
        }

        int y = top + PADDING;
        for (ClientFactions.Entry faction : ClientFactions.all()) {
            // Bounded by the panel, not by the list: a pack with two hundred factions must not
            // draw rows down over the hotbar and the chat.
            if (y > top + PANEL_HEIGHT - LINE_HEIGHT) {
                break;
            }
            // The player's own standing, falling back to the faction's default for one they have
            // no stored standing with - the same rule XenoPlayerData.getFactionStanding applies on
            // the server. Reading defaultStanding() directly would show every player the same
            // numbers, which is wrong in a way nobody would notice.
            int standing = ClientStandings.of(faction.id(), faction.defaultStanding());
            XenoFaction.Attitude attitude = XenoFaction.attitudeAt(standing);
            graphics.drawString(minecraft.font, Component.literal(faction.name()),
                    left + PADDING, y, 0xFF000000 | faction.color(), false);
            graphics.drawString(minecraft.font, Component.literal(Integer.toString(standing)),
                    left + PANEL_WIDTH - 110, y, 0xFFD8E4EE, false);
            graphics.drawString(minecraft.font, Component.literal(name(attitude)),
                    left + PANEL_WIDTH - 70, y, colour(attitude), false);
            y += LINE_HEIGHT;
        }
    }

    private static String name(XenoFaction.Attitude attitude) {
        return switch (attitude) {
            case FRIENDLY -> "Friendly";
            case NEUTRAL -> "Neutral";
            case HOSTILE -> "Hostile";
        };
    }

    private static int colour(XenoFaction.Attitude attitude) {
        return switch (attitude) {
            case FRIENDLY -> 0xFF7CE08A;
            case NEUTRAL -> 0xFF8FA3B4;
            case HOSTILE -> 0xFFE06C6C;
        };
    }
}
```

- [x] **Step 5: Wire both panels into the tab strip**

In `XenoInventoryTabs.java`, replace the placeholder comment at the end of `onRender` with:

```java
        if (open == Tab.QUESTS) {
            QuestLogPanel.render(graphics, inventory, event.getMouseX(), event.getMouseY());
        } else if (open == Tab.FACTIONS) {
            FactionPanel.render(graphics, inventory, event.getMouseX(), event.getMouseY());
        }
```

And in `onClick`, replace the placeholder comment after the tab-hit block with:

```java
        // A click inside the open quest panel selects a category, a quest, or turns the page.
        // Cancelled only when the panel actually took it, so clicks elsewhere still reach the
        // inventory's slots.
        if (open == Tab.QUESTS
                && QuestLogPanel.click(inventory, event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
        }
```

- [x] **Step 6: Run the tests**

Run: `gradlew.bat test --tests "*FactionPanelTest*" --tests "*SyncStandingsPacketTest*" -PofflineMcMeta`
Expected: PASS — 6 faction-panel tests, 8 standings tests still green.

- [x] **Step 7: Compile**

Run: `gradlew.bat compileJava -PofflineMcMeta`
Expected: BUILD SUCCESSFUL.

- [-] **Step 8: Commit** — *skip unless the user asked*

```bash
git add src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/FactionPanel.java src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/XenoInventoryTabs.java src/test/java/net/bullettrain/xenopixelsmod/client/npc/quest/FactionPanelTest.java
git commit -m "feat(npc): add the faction standings tab"
```

---

### Task 9: The New Quest toast

**Files:**
- Create: `src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/QuestToastOverlay.java`
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/ClientQuests.java`
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/client/XenoHudRegistration.java`
- Test: `src/test/java/net/bullettrain/xenopixelsmod/client/npc/quest/QuestToastOverlayTest.java`

**Interfaces:**
- Consumes: `LayeredDraw.Layer` — `void render(GuiGraphics, DeltaTracker)`, verified at `net/minecraft/client/gui/LayeredDraw.java:42-44`. `RegisterGuiLayersEvent.registerAbove(ResourceLocation, ResourceLocation, LayeredDraw.Layer)` — the form all twelve existing layers use. `GuiGraphics.guiWidth()`.
- Produces: `QuestToastOverlay.show(String questTitle)`, `QuestToastOverlay.lastShown() -> String`, `QuestToastOverlay.reset()`, `QuestToastOverlay.render(GuiGraphics, DeltaTracker)`.

Gold `New Quest` over the quest name in white, cyan border on dark navy, top-right, fading after **4 seconds** — the same `4000L` as `XenoNpcEditorScreen.NOTICE_MS` (`XenoNpcEditorScreen.java:166`), whose comment already gives the reasoning: long enough to read, short enough not to become furniture.

- [x] **Step 1: Write the failing test**

Create `src/test/java/net/bullettrain/xenopixelsmod/client/npc/quest/QuestToastOverlayTest.java`:

```java
package net.bullettrain.xenopixelsmod.client.npc.quest;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When the toast fires, and when it must not. */
class QuestToastOverlayTest {

    private static ClientQuests.Entry quest(String id) {
        return new ClientQuests.Entry(id, id, "", "", 0, 1, false, "");
    }

    @Test
    void aNewQuestFiresTheToast() {
        ClientQuests.clear();
        ClientQuests.accept(List.of());
        ClientQuests.accept(List.of(quest("wolf_trouble")));
        assertEquals("wolf_trouble", QuestToastOverlay.lastShown());
    }

    @Test
    void aProgressUpdateDoesNotFireItAgain() {
        // Every kill pushes a fresh packet. If the toast fired on each one it would sit on screen
        // permanently, which is exactly what its four-second life is meant to prevent.
        ClientQuests.clear();
        ClientQuests.accept(List.of());
        ClientQuests.accept(List.of(quest("wolf_trouble")));
        QuestToastOverlay.reset();

        ClientQuests.accept(List.of(
                new ClientQuests.Entry("wolf_trouble", "wolf_trouble", "", "", 3, 5, false, "")));
        assertEquals("", QuestToastOverlay.lastShown(), "same quest, only more progress");
    }

    @Test
    void theFirstSyncAfterLoginDoesNotToastEveryExistingQuest() {
        // Otherwise a player relogging mid-playthrough is hit with one toast per active quest,
        // for quests they took hours ago.
        ClientQuests.clear();
        ClientQuests.accept(List.of(quest("a"), quest("b"), quest("c")));
        assertEquals("", QuestToastOverlay.lastShown(),
                "the first sync establishes a baseline, it does not announce it");
    }

    @Test
    void aSecondNewQuestAfterThatDoesToast() {
        ClientQuests.clear();
        ClientQuests.accept(List.of(quest("a")));
        ClientQuests.accept(List.of(quest("a"), quest("b")));
        assertEquals("b", QuestToastOverlay.lastShown());
    }

    @Test
    void aDisconnectSilencesIt() {
        // Otherwise a stale toast from the last world greets the player in the next one.
        ClientQuests.clear();
        ClientQuests.accept(List.of());
        ClientQuests.accept(List.of(quest("a")));
        assertEquals("a", QuestToastOverlay.lastShown());

        ClientQuests.clear();
        assertEquals("", QuestToastOverlay.lastShown());
    }

    @Test
    void itLastsFourSeconds() throws IOException {
        // Reusing the editor notice's reasoning: long enough to read, short enough not to become
        // furniture.
        String source = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest",
                        "QuestToastOverlay.java"), StandardCharsets.UTF_8);
        assertTrue(source.contains("4000L"), "four seconds, matching the editor notice");
    }

    @Test
    void itIsRegisteredAsAHudLayer() throws IOException {
        // An overlay nobody registered draws nothing, and compiles perfectly while doing so.
        String registration = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/client",
                        "XenoHudRegistration.java"), StandardCharsets.UTF_8);
        assertTrue(registration.contains("QuestToastOverlay::render"),
                "matching the static ::render form the neighbouring layers use");
        assertTrue(registration.contains("xeno_quest_toast"), "with its own layer id");
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `gradlew.bat test --tests "*QuestToastOverlayTest*" -PofflineMcMeta`
Expected: FAIL — `cannot find symbol: class QuestToastOverlay`.

- [x] **Step 3: Write `QuestToastOverlay`**

Create `src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/QuestToastOverlay.java`:

```java
package net.bullettrain.xenopixelsmod.client.npc.quest;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * "New Quest", top-right, for four seconds.
 *
 * <p>Raised by {@link ClientQuests} when a quest id appears that was not in the previous sync - not
 * on every sync, because progress pushes one per kill and a toast that re-fired on each would never
 * leave the screen.
 */
public final class QuestToastOverlay {

    /** Long enough to read, short enough not to become furniture - the editor notice's reasoning. */
    private static final long LIFETIME_MS = 4000L;

    private static final int WIDTH = 160;
    private static final int HEIGHT = 32;
    private static final int MARGIN = 8;

    private static volatile String title = "";
    private static volatile long shownAt;

    private QuestToastOverlay() {
    }

    /** Raises the toast. */
    public static void show(String questTitle) {
        title = questTitle == null ? "" : questTitle;
        shownAt = System.currentTimeMillis();
    }

    /** What the toast last announced. Empty once it has been reset. */
    public static String lastShown() {
        return title;
    }

    /**
     * Takes the toast down.
     *
     * <p>Called on disconnect, so a stale announcement from the last world does not greet the
     * player in the next one.
     */
    public static void reset() {
        title = "";
        shownAt = 0L;
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        String shown = title;
        if (shown.isEmpty() || System.currentTimeMillis() - shownAt > LIFETIME_MS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int x = graphics.guiWidth() - WIDTH - MARGIN;
        int y = MARGIN;

        graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xF00A1830);
        graphics.fill(x, y, x + WIDTH, y + 1, 0xFF35D7FF);
        graphics.fill(x, y + HEIGHT - 1, x + WIDTH, y + HEIGHT, 0xFF35D7FF);
        graphics.fill(x, y, x + 1, y + HEIGHT, 0xFF35D7FF);
        graphics.fill(x + WIDTH - 1, y, x + WIDTH, y + HEIGHT, 0xFF35D7FF);

        graphics.drawString(minecraft.font, Component.literal("New Quest"),
                x + 8, y + 7, 0xFFFFC928, false);
        graphics.drawString(minecraft.font, Component.literal(shown),
                x + 8, y + 19, 0xFFFFFFFF, false);
    }
}
```

- [x] **Step 4: Restore the toast calls in `ClientQuests`**

In `ClientQuests.java`, replace the placeholder comment inside `accept` with the real baseline check, and restore the reset in `clear`. Both bodies are given in full in Task 1 Step 3 — the `if (synced) { ... containsId ... }` block in `accept`, and `QuestToastOverlay.reset();` as the last line of `clear()`.

- [x] **Step 5: Register the layer**

In `XenoHudRegistration.java`, add inside `onRegisterOverlays`, after the `xeno_ui_pack_hud` line:

```java
        event.registerAbove(DMZ_TOP, ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "xeno_quest_toast"),
                net.bullettrain.xenopixelsmod.client.npc.quest.QuestToastOverlay::render);
```

- [x] **Step 6: Run the tests**

Run: `gradlew.bat test --tests "*QuestToastOverlayTest*" -PofflineMcMeta`
Expected: PASS, 7 tests.

- [x] **Step 7: Run the whole suite**

Run: `gradlew.bat test -PofflineMcMeta`
Expected: all green, test count above the **1739** baseline, 0 failures.

- [x] **Step 8: Build**

Run: `gradlew.bat build jarJar serverJar -PofflineMcMeta`, then `gradlew.bat buildApiExampleAddon -PofflineMcMeta`
Expected: BUILD SUCCESSFUL for both. Confirm the server jar holds **0** entries under `META-INF/jarjar/`:

```bash
unzip -l build/libs/xenopixelsmod-Server-*.jar | grep -c "META-INF/jarjar/.*\.jar"
```

- [-] **Step 9: Commit** — *skip unless the user asked*

```bash
git add src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/QuestToastOverlay.java src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest/ClientQuests.java src/main/java/net/bullettrain/xenopixelsmod/client/XenoHudRegistration.java src/test/java/net/bullettrain/xenopixelsmod/client/npc/quest/QuestToastOverlayTest.java
git commit -m "feat(npc): announce a new quest with a toast"
```

---

## In-game verification

None of this is proven by a green build. A dedicated server is the important case — every bug this subsystem has had was a client unable to see server-only state.

1. `/xenoquest start wolf_trouble` → the **New Quest** toast appears top-right and fades after about four seconds.
2. Open the inventory → three tabs down the left. Click **Q** → the quest is listed under **General** at `0 / 5`.
3. Kill a wolf → the count rises and the toast does **not** re-fire.
4. Define quests with `"category": "Side 10"` and `"Side 2"` → `Side 2` is listed **above** `Side 10`.
5. Define a quest with a long `log_text` → the journal pages on click, and the last page keeps its tail.
6. Define a quest with `"completion": "npc"` and `"completer_npc": "Stephanie"` and finish it → the log shows *Complete with Stephanie*; an instant quest shows no such line.
7. `/reload` after editing a quest's title → the open log shows the new title without rejoining.
8. Click **F** → faction standings render, with nothing clickable.
9. Give one player faction points via a quest reward, then compare the **F** tab between two players on a dedicated server → the numbers differ. Identical numbers mean the panel is reading the faction default rather than the player’s own standing.
10. Disconnect and join a different world → the log is empty, not the previous world's.
11. **Dedicated server**, two players on different quests → neither sees the other's.
12. A client built before this change → refused on protocol mismatch rather than misreading packets.
13. Click a slot while the Quests tab is open → the item is picked up; the tab strip does not swallow inventory clicks.

## Unverified

- `OnDatapackSyncEvent.getPlayerList()` and `ClientPlayerNetworkEvent.LoggingOut` — Task 2 Step 1 checks both against the sources jar before anything is written against them. If an accessor is named differently, use the real name.
- `XenoFaction.Attitude`'s constants — Task 7 Step 1 reads them before the `switch` is written.
- Every pixel constant here (`PANEL_WIDTH`, `COLUMN_CATEGORIES`, the tab sizes, the toast box) is a first guess. They are named constants so the in-game pass can correct them in one place. **None is derived from a MyNPCs measurement**, and none should be described as matching the reference until someone has looked at both.
- The panel is drawn to the right of the inventory window and the tab strip to its left. On a narrow window or a large GUI scale the panel may run off-screen; the in-game pass should check 1280×720 at GUI scale 3.

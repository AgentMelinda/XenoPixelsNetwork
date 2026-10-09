# Native Xeno NPC scenes

**Source state:** 2026-09-23. Build and unit checks are recorded in the current handoff. Gameplay
playback in a fresh process is not verified.

Scenes are shared definitions under `<world>/XenoNpcs/scenes/<id>.json`. An NPC stores only its
`SceneId` reference. The definition can hold up to 32 ordered speech or animation steps, each at
0–1200 ticks after starting. Speech is capped at 256 characters, clip IDs at 64. The scene clock is
transient; stopping or unloading an NPC drops its current playback. There is no arbitrary command
step.

Operator level 2 commands:

```text
/xenoscene create arrival Arrival at the station
/xenoscene say arrival 0 Welcome aboard!
/xenoscene clip arrival 20 hi_wave
/xenoscene show arrival
/xenoscene list
/xenoscene start <npc> arrival
/xenoscene pause <npc>
/xenoscene resume <npc>
/xenoscene time <npc> 10
/xenoscene reset <npc>
/xenoscene stop <npc>
/xenoscene remove arrival 0
/xenoscene delete arrival
```

`<npc>` is one Brigadier entity selector resolving to a native Xeno NPC. The editor's Advanced >
Scenes page assigns an existing scene and saves the reference on the NPC; after that,
`/xenoscene start <npc>` uses the assignment. `show` displays zero-based step indexes for `remove`.
`pause` freezes the timeline clock, but does not freeze speech bubbles or clips already started.
While a scene is running, ambient chatter and social gestures are suppressed for that NPC.

Command writes update the client store index. A scene whose file is deleted remains playable on
NPCs already running a snapshot of it; a later start fails until the definition exists again.
This command-backed editor path does not yet reproduce MyNPCs' full scene timeline UI or its
enabled/disabled entry controls.

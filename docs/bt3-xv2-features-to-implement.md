# BT3 / Xenoverse 2 — Features We Can Implement

Roadmap for **XenoPixels** as a public DMZ companion mod.  
Focus: real combat feel, server-safe balance, dedicated keybinds, DMZ techniques/forms where possible.

Related notes: `docs/xv2-bt3-feature-ideas.md`, `src/.../features/FeatureStatus.java`.

---

## Already live (do not re-invent)

| Area | What we have |
|------|----------------|
| **BT3 movement** | Vanish (A/D double-tap), chase (W), backstep (S), dragon dash, charge fist/kick |
| **Combo** | Mash combo + finisher, cooldown HUD chips |
| **Forms** | Custom saiyan/god/saga forms, Dark Frieza (frostdemon), form power scales (`/xenoform`) |
| **HUD** | XV2-style HP/KI/STM, tech hotbar, combat CD strip, party strip |
| **KI** | Overcharge scaling on high power-release; DMZ techniques still own cast |
| **Balance** | Server config + per-form / per-stat multipliers, permissions |

**Stubs only (code exists, not real gameplay):** skill tree UI, cosmetics registry, dojo hub, teaching NPCs, boss dungeon, twin-art equip, time freeze. Prefer *rebuilding properly* over “enabling” stubs.

---

## Design rules for new combat

1. **Dedicated keybinds** for big tools (guard, ultimate, Z-burst, ki-blast cancel) — avoid more WASD dual-maps.  
2. **Lock-on aware** where it matters; freelook OK for combos/charges where already supported.  
3. **Server authority** for damage, KI/STM costs, CDs; client predicts movement/FX only.  
4. **Toggleable** via `xenopixelsmod-server.json` + `/xenoserver` / own commands.  
5. **Play well with DMZ** techniques, forms, beam clash, scouter — don’t cancel their HUDs unless we replace them.

---

# A. Combat (BT3 / Sparking Zero feel)

### A1. Guard / block + stamina break
- Hold guard key: reduce melee/ki damage, drain STM.
- Empty STM → **guard crush** stun (short lockout).
- Optional: perfect guard window (tiny frame, no STM drain, counter prompt).
- **Source:** XV2 stamina block / BT3 guard.  
- **Depends on:** STM from DMZ, new packet for block state.

### A2. Super counter / vanish counter
- After taking a hit, short window to press vanish/counter key.
- Success: i-frames + teleport behind attacker + free combo starter.
- Fail / spam: longer CD or STM tax.
- **Source:** BT3 vanish counter / XV2 counter.  
- **Depends on:** hit event, vanish packet, CD HUD chip.

### A3. Rush chain / air chase
- After knockup or heavy hit, press chase / rush key to **follow into the air** and continue the string.
- Chain limit (e.g. 1–2 mid-air chases) so it isn’t infinite.
- **Source:** BT3 dragon rush / air follow-up.  
- **Depends on:** target velocity/position, chase action.

### A4. Sonic sway / afterimage step
- Side-step with brief i-frames + afterimage trail (we already have afterimage FX hooks).
- Costs STM; short CD.
- **Source:** BT3 sonic sway / XV2 afterimage.  
- **Depends on:** input key, server i-frame flag.

### A5. Z-Burst dash (mid-combo only)
- Mid-combo dedicated key (e.g. **V**): short burst toward target, extends string, not freelook spam.
- Separate from dragon dash (standalone hold key).
- **Source:** BT3 Z-Burst / XV2 step-in.  
- **Depends on:** combo step state on client+server.

### A6. Ki blast cancel
- From combo, fire a light ki blast and **end or cancel** the string (BT3-style cancel).
- Small KI cost, short CD; does not replace equipped techniques.
- **Source:** BT3 ki blast cancel.  
- **Depends on:** DMZ ki projectile spawn or lightweight custom projectile.

### A7. Heavy smash / step-in smash
- Fully charged fist/kick already exist — add **step-in** option (hold W while releasing) or grounded smash that knocks up for rush chain.
- **Source:** BT3 smash / Sparking heavy.  
- **Depends on:** existing charge release path.

### A8. Throw / grab
- Close-range throw when locked: fixed anim, damage, reposition (behind / slam).
- Tech vs throw optional later.
- **Source:** BT3 / XV2 grab.  
- **Depends on:** range check, immobilize ticks.

### A9. Sparking / limit-style mode (meter)
- Build meter from damage dealt/taken; activate for temporary buff (dmg, speed, reduced form drain).
- Timed duration, big CD after.
- **Source:** BT3 Sparking / FighterZ sparking-adjacent feel / XV2 limit break vibes.  
- **Depends on:** new meter in capability + HUD pip.

### A10. Lock-on improvements
- Cycle targets (next/prev), soft-lock for freelook combos, lock-on range config.
- Optional: lose lock if target too far / behind walls (ray).
- **Source:** BT3 / XV2 camera lock.  
- **Depends on:** DMZ `LockOnEvent` (already used).

---

# B. Techniques & forms (XV2 + BT3)

### B1. Ultimate skill slot
- One “ultimate” bind: highest-cost DMZ technique or form-linked finisher.
- Long CD, power-release gate, optional transformation required.
- **Source:** XV2 ultimate skills.  
- **Depends on:** technique equip data or fixed skill id per race/form.

### B2. Evasive skill (dedicated)
- Short-range escape (vanish-like) with longer CD than basic vanish; cancels some knockback.
- **Source:** XV2 evasive skills.  
- **Depends on:** KI cost, i-frames.

### B3. Super Soul–style passives
- Equipable items or GUI slots: small passives (kick dmg+, vanish CD−, KI regen, form drain−).
- Server-defined catalog JSON; no pay-to-win defaults.
- **Source:** XV2 Super Souls.  
- **Depends on:** inventory/curios or custom equipment screen.

### B4. QQ Bang–style stats (optional)
- Craftable or earned items that bump base stats with tradeoffs (e.g. +STR −DEF).
- Cap total so form mults stay the main lever.
- **Source:** XV2 QQ Bang.  
- **Depends on:** items + DMZ stats write path (careful).

### B5. Transformation impact
- On form enter: short pose, ring VFX, light local knockback/push.
- Configurable per form group.
- **Source:** BT3 / games transform impact.  
- **Depends on:** DMZ transform event if available, or form change poll.

### B6. Form-linked combat modifiers
- While in form X: tweak vanish range, charge speed, guard strength (data-driven).
- Complements `/xenoform` numeric scales with **feel** knobs.
- **Source:** XV2 transformation skill kits.  
- **Depends on:** active form id (already readable).

### B7. Beam struggle polish
- DMZ already has beam clash; improve feedback (our CD HUD, mash prompts, winner FX).
- Optional: third-party assist from party member.
- **Source:** BT3 / XV2 beam struggle.  
- **Depends on:** existing DMZ beam clash HUD (keep unblocked).

---

# C. Training & progression (XV2 hub fantasy)

### C1. Training dummy + damage meter — **LIVE (Phase 3)**
- `/xenotrain dummy|reset|stats` — armor stand dummy, session/hit/total damage on action bar.
- Skill-point milestones at 25 / 100 / 250 hits. Server flag: `trainingDummyEnabled`.
- **Source:** XV2 training / BT3 practice.

### C2. Combo training challenges
- Hit counters / timing windows with star ranks (C–Z).
- Rewards: TP, cosmetic, temporary Super Soul.
- **Source:** XV2 training missions. *(still planned)*

### C3. Parallel quest lite — **LIVE (Phase 3)**
- `/xenoquest list|start|status|abort` — `kill_mobs`, `kill_players`, `dummy_session`.
- Completing grants skill points. Flag: `parallelQuestEnabled`.
- **Source:** XV2 Parallel Quests.

### C4. Mentor / teacher (real) — **LIVE lite (Phase 3)**
- `/xenomentor set|clear|status` — nearby mentor gains sparking meter from student damage.
- Flag: `mentorEnabled`. Full shared-TP / duo finisher later.
- **Source:** XV2 mentors / dual play.

### C5. Skill tree (real, slim) — **LIVE (Phase 3)**
- `/xenoskill list|unlock|points` — power / guard / sparking / ultimate, max Lv.3 each.
- Super Souls: `/xenosoul` + equip items (warrior, iron, spark, finisher, balanced).
- **Source:** XV2 skill trees / Super Souls.

### C6. Expert missions / boss challenge (real)
- Weekly boss with phases, weak-point markers, no paywall.
- Scanner item reveals weak point; rewards form TP + Super Soul.
- **Source:** XV2 expert missions / Conton City.  
- **Depends on:** boss entity AI or DMZ boss hooks.

---

# D. Movement & flight (BT3 / XV2)

### D1. High-speed movement modes
- Toggle sprint-flight boost (KI drain) vs normal fly.
- Trail FX intensity scales with speed.
- **Source:** XV2 high-speed movement.

### D2. Dragon dash polish
- Mid-dash cancel into attack; fail-safe if no lock.
- Optional “dragon smash” on full charge release near target.
- **Source:** BT3 dragon dash variants.

### D3. Wall / ground bounce combos
- Wall splat then continue combo (server-safe knockback vectors).
- **Source:** BT3 stage interactions (simplified for Minecraft terrain).

---

# E. Social / party / PvP (public server)

### E1. Party shared combat HUD
- Show teammate form, HP%, sparking meter; ping target.
- Extend current party strip.
- **Source:** XV2 multiplayer UI.

### E2. Ranked duel arena
- 1v1 / 2v2 region with auto-reset, no item use, form mult ruleset preset.
- Optional Elo-lite stored in capability.
- **Source:** XV2 PvP / BT3 tournament mode vibe.

### E3. Emotes & victory poses
- Trigger DMZ anim keys on keybind; victory after KO.
- **Source:** BT3 / XV2 presentation.

### E4. Spectator freecam for fights
- Ops/stream mode: follow lock-on pair.
- **Source:** tournament / content creator needs.

---

# F. Presentation & juice

### F1. Hit spark tiers
- Light / medium / heavy / smash sparks (particle + sound pitch).
- **Source:** BT3 hitstop-lite (tiny client pause optional).

### F2. Announcer / banner on KO
- “K.O.” / “Ring Out” style banner (or fall into void).
- **Source:** BT3.

### F3. Aura while charging / flying
- Stronger aura LOD when charging KI technique or form flight.
- **Source:** XV2 / BT3 aura.

### F4. Cooldown HUD presets
- XV2 / BT3 / minimal skins for combat chip strip.
- **Source:** QoL for public server branding.

---

# G. Explicitly de-prioritize or avoid

| Idea | Why |
|------|-----|
| Full XV2 open-world Conton City | Huge content; not a combat mod’s job |
| Gacha / pay cosmetics | Bad for public trust |
| Replacing DMZ core stats UI | Fragile; patch around DMZ |
| Auto-combo bots | Ruins PvP |
| Dual-mapping more movement keys | Breaks flight/launch (we already scrub WASD duals) |
| “Enable stub managers” as features | Stubs do nothing in-world — rebuild or drop |

---

# Suggested implementation order (public server)

### Phase 1 — Combat core (highest impact)
1. Guard + stamina break — **LIVE** (hold **B**, STM drain, guard break stun)  
2. Vanish / super counter — **LIVE** (after hit, vanish = super counter)  
3. Ki blast cancel — **LIVE** (mid-combo **C**)  
4. Z-Burst mid-combo — **LIVE** (**V** while combo active + lock-on)  
5. Lock-on cycle — **LIVE** (**[** / **]** prev/next)  

### Phase 2 — Depth
6. Rush chain / air chase — **LIVE** (double-tap **W** while target airborne mid-combo)  
7. Sonic sway / afterimage step — **LIVE** (**`,`** / **`.`** left/right, STM + i-frames)  
8. Ultimate skill slot — **LIVE** (**U**, big KI smash, CD)  
9. Transformation impact — **LIVE** (ring + knock when form changes)  
10. Sparking meter — **LIVE** (build on hits, activate **Y** at 100%)  

Also: **DMZ masters protected** from player punch/damage/knockback (`protectDmzMasters`).

### Phase 3 — Progression / retention
11. Training dummy + damage meter  
12. Super Soul passives  
13. Slim skill tree (combat passives)  
14. Parallel quest lite  
15. Mentor pairing  

### Phase 4 — Polish / social
16. Party combat strip upgrades  
17. Duel arena ruleset  
18. Hit sparks / KO banner  
19. Emotes  
20. Beam struggle feedback pass  

---

# Per-feature checklist (use when implementing)

For each feature:

- [ ] Server config flag + default ON/OFF for public balance  
- [ ] Permission node if command-driven  
- [ ] Client keybind (dedicated, not WASD dual)  
- [ ] KI / STM / CD costs  
- [ ] Packet + anti-spam validation  
- [ ] Cooldown HUD chip (optional)  
- [ ] Lang en_us  
- [ ] Content catalog entry  
- [ ] Does not break DMZ techniques / forms / beam clash  

---

# Quick BT3 vs XV2 mapping

| Feel | Lean BT3 | Lean XV2 |
|------|----------|----------|
| Neutral / pressure | Guard, smash, rush chain | Super Soul, ultimate, high-speed move |
| Defense | Vanish counter, sonic sway | Evasive skill, stamina break |
| Team | Dual finish (later) | Mentors, parallel quests, party HUD |
| Power fantasy | Sparking mode, dragon dash polish | Form kits, skill tree passives |

---

*Last updated for XenoPixels public-server planning. Prefer fewer, polished LIVE systems over large stub packs.*

# Combat V3 integration evidence

**Date:** 2026-10-07  
**Scope:** Task 1A, exact integration evidence; no implementation or gameplay proof.  
**Baseline:** branch 1.21.1, HEAD e011e7bec8061d8c2228cfbdc63ee57468749d0d. Existing owner changes are preserved.

## Evidence identity

Repository source was read first, tracked tools/generated/dmz_decompiled_full next, and javap signatures/selected bytecode against the exact jar after that. Reference material was not regenerated. The following are inspected inputs, not authored source:

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| libs/dragonminez-2.1.3.jar | 61672772 | 5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581 |
| build/moddev/artifacts/neoforge-21.1.248.jar | 23343775 | 1fb8153a2643c8834006bf12ad69e55bcf7cb2c84268e9e783aa5a4d031990a7 |
| build/moddev/artifacts/neoforge-21.1.248-sources.jar | 9401741 | d56098671d180fce7bc2812c0f16af904c77d37f66305dfa63da160fe9479e05 |
| NeoForge 21.1.248 universal jar | 3551268 | 90a56f70425711b4e1a4b94ff0c2904ae9f6d74ca6478b3b2152ac794a07b8e5 |
| GeckoLib NeoForge 1.21.1-4.9.2 jar | 630584 | 5e548466af9ab6aca7a91a7c7d4dc0dc8bc385e22958aed5da0e7bebd0fa3fba |

DMZ matches dragonminez_sha256 in current gradle.properties and the approved constraints. Minecraft/NeoForge source and bytecode came from the local pinned ModDev artifacts above, read-only; cached coordinates and exact reproduction commands are in task-1a-report.md. Java tool: C:/Program Files/java/jdk-21.0.11/bin/javap.exe.

No build, tests, client/server startup, or gameplay were run. Jar signatures/control flow are verified statically; installed mixin application, rendering and actual gameplay remain **not verified at runtime**. The complete Part 1 visual inventory belongs to Task 1B and is not completed or counted here.

## Native melee and queued execution

Exact class com.dragonminez.common.network.C2S.CombatAttackRequestC2S:

    public CombatAttackRequestC2S(int, boolean, int, int[])
      (IZI[I)V
    public static processAttackRequest(ServerPlayer, CombatAttackRequestC2S): void
      (Lnet/minecraft/server/level/ServerPlayer;Lcom/dragonminez/common/network/C2S/CombatAttackRequestC2S;)V
    private static lambda$processAttackRequest$2(CombatAttackRequestC2S, ServerPlayer): void
      (Lcom/dragonminez/common/network/C2S/CombatAttackRequestC2S;Lnet/minecraft/server/level/ServerPlayer;)V

Tracked reference common/network/C2S/CombatAttackRequestC2S.java: handle enqueues server work, resolves sender/stun, then calls processAttackRequest. Its worker validates inventory slot and native rate, resolves native AttackHand, emits native sound/MeleeAnimationS2C, installs temporary attributes, validates targets/range, and calls player.attack. Decoder caps entity IDs at 64. Bytecode confirms ServerPlayer.attack(Entity) at offset **665** in lambda$processAttackRequest$2.

**Recommended seam, inferred from verified flow:** accepted left taps use this real request route with server-validated state/targets. Direct target.hurt or V2Damage is not that route. A V3-only late/duplicate/session gate belongs at worker lambda$processAttackRequest$2 HEAD, before animation, sound or mutation, because the public call can defer. This is a proposed injector, not an applied/runtime-proven injector.

Pinned Minecraft Player.attack calls CommonHooks.onPlayerAttackTarget before its attack body. Exact NeoForge AttackEntityEvent and LivingIncomingDamageEvent implement ICancellableEvent. AttackEntityEvent can refuse damage but runs after DMZ has already emitted animation and installed temporary attributes. LivingDamageEvent.Pre and Post do not implement cancellation; Pre can modify damage and Post observes the result. Keep server damage/input handlers common and client gesture ownership isolated.

### Server-thread calls are not always synchronous

Pinned source entries net/minecraft/util/thread/BlockableEventLoop.java, ReentrantBlockableEventLoop.java and net/minecraft/server/MinecraftServer.java, cross-checked with javap -c:

    BlockableEventLoop.execute(Runnable):
      scheduleExecutables() ? tell(wrapRunnable(task)) : task.run()
    BlockableEventLoop.scheduleExecutables(): !isSameThread()
    ReentrantBlockableEventLoop.scheduleExecutables(): runningTask() || super.scheduleExecutables()
    ReentrantBlockableEventLoop.runningTask(): reentrantCount != 0
    MinecraftServer.scheduleExecutables(): super.scheduleExecutables() && !isStopped()

MinecraftServer inherits ReentrantBlockableEventLoop. doRunTask increments reentrantCount and restores it in finally. Calling processAttackRequest from a running queued task therefore defers its inner worker even on the server thread. A try/finally resource scope around the public call is **not generally valid**. Persist bounded session UUID/sequence/request identity across queued execution, recheck the session in the worker, and install/restore synchronous context around the actual attack/hurt. Outside a running queued task, same-thread execute can run inline; support both cases. A fresh scheduling trace remains not verified.

## Resources and duplicate stamina work

Exact com.dragonminez.common.stats.character.Resources methods:

    getCurrentStamina()F; getCurrentEnergy()F
    setCurrentStamina(F)V; setCurrentEnergy(F)V
    removeStamina(F)V; removeEnergy(F)V
    StatsProvider.get(Capability, Entity): LazyOptional
      (Lcom/dragonminez/compat/capabilities/Capability;Lnet/minecraft/world/entity/Entity;)Lcom/dragonminez/compat/util/LazyOptional;

Tracked common/stats/character/Resources.java clamps to attached StatsData maximum, rounds small pools to quarter units, and removal awards actual spent units via DynamicGrowthService for ServerPlayer owners. Measure before/after for actual drain. Resolve through existing StatsProvider/StatsCapability; refuse missing stats instead of manufacturing a pool.

Repository compat/npc/NpcResources.java has get(LivingEntity,NpcCombatProfile): Snapshot, spendStamina(...,double): boolean, spendStaminaPartial(...,double): double, setStamina(...,double): void and combined spend(...,double,double): boolean. Partial spend exhausts an insufficient pool and returns the affordable fraction. Clones delegate spending to CloneCombatBridge and the owner's pool; setStamina intentionally returns without changing a clone. Generic unprofiled mobs have no verified stamina owner. Published semantics must be preserved.

Exact DMZ server.events.players.combat.CombatEvent:

    onLivingHurt(LivingDamageEvent$Pre)V
    overrideVanillaArmorReduction(LivingDamageEvent$Pre)V
      (Lnet/neoforged/neoforge/event/entity/living/LivingDamageEvent$Pre;)V
    private static lambda$onLivingHurt$3(boolean[], Pre, boolean[], Player, boolean,
      double[], LivingEntity, double[], StatsData): void
      ([ZLnet/neoforged/neoforge/event/entity/living/LivingDamageEvent$Pre;[ZLnet/minecraft/world/entity/player/Player;Z[DLnet/minecraft/world/entity/LivingEntity;[DLcom/dragonminez/common/stats/StatsData;)V

Tracked CombatEvent.java HIGH onLivingHurt posts DMZEvent.DamageModifyEvent in its attacker melee branch. **Cancellation sets dmzDamage to zero and continues**, including stamina computation. In attacker lambda$onLivingHurt$3, javap confirms cost getter at offset **512**, removeStamina at **685**, and insufficient-pool setCurrentStamina(0) at **721**. Canceling DamageModifyEvent is not debit cancellation.

Native attacker debit uses dmz_first_hit: absent/true pays or empties an insufficient pool and scales damage; false uses dmz_swing_stamina_ratio (default 1) without paying. Victim defense is separate and can spend block/parry stamina. The handler records dmz_raw_damage; LOWEST overrideVanillaArmorReduction reconstructs player damage from that value and defense. Canceling the whole handler removes victim handling/bookkeeping; changing damage only after HIGH may be overwritten by LOWEST.

**Existing conditional seam:** repository combat/v2/V2Damage snapshots dmz_first_hit, dmz_swing_stamina_ratio and dmz_server_speed. It sets first=false and ratio=1 around synchronous target.hurt only when the attacker is a DMZ character and V2Config.get().strikesDrainStamina is disabled; the matching restoration in finally uses that same condition. With stamina draining enabled, V2Damage retains native attacker debit. Its speed=0 override and restoration are separately conditional on being a DMZ character. DMZ source/bytecode verifies how the applied overrides suppress native debit and momentum while retaining victim rules.

**Proposed V3 owned suppression seam:** an explicitly prepaid custom V3 heavy can independently use first=false and ratio=1 in an exact owned-hit scope around the actual synchronous hit, with original state restored in finally; momentum suppression is a separate movement-owner choice. This proposal is distinct from V2Damage's configuration-dependent behavior and does not reuse the V2 controller. Native left taps keep native costs. Do not scope the potentially deferred public processAttackRequest call this way.

If a new injector is necessary, target attacker-only cost computation in lambda$onLivingHurt$3 under an exact owned-hit context. Suppressing only removeStamina still leaves the insufficient-pool zeroing/affordability branch. Do not globally suppress Resources.removeStamina or the full damage event.

Repository NpcMeleeDamage.onNpcMelee (HIGHEST) independently spends NpcResources.spendStaminaPartial for direct, non-strike profiled NPC attackers. A prepaid NPC heavy must bypass only that owned attacker's cost/scaling branch; the same handler also mitigates defender damage and must not be wholly canceled. Player-heavy hits against NPCs are not NPC-attacker debits.

**Accepted victim-drain seam:** positive LivingDamageEvent.Post.getNewDamage with exact session/sequence, owned direct source and attacker/victim UUID; record once per admitted hit, independently of attacker-start spend. A true hurt return alone is weaker than positive Post evidence. Keep normal block/parry/protection authoritative. Actual player/NPC/clone rounding, refused/guarded/zero-damage values and transaction counts are not verified at runtime.

## Strike bar interception: preferred target identity

    com.dragonminez.server.events.players.combat.StrikeAttackHandler
    public static requestStrike(ServerPlayer,int): void
      (Lnet/minecraft/server/level/ServerPlayer;I)V

Tracked StrikeAttackHandler.java names the int **preferredTargetId**. It reads already selected StrikeAttackData and passes the int to findConeTarget(player,range,preferredTargetId). Bytecode lambda$requestStrike$0 offset **217** invokes findConeTarget(ServerPlayer,double,int). HEAD cancellation precedes energy debit, target/strike state, animation, damage and cooldown and is the earliest verified bar diversion seam.

Existing repository StrikeAttackHandlerMixin calls this int slotIndex and reads equippedSlots[slotIndex] for some old routes. That does not alter DMZ semantics. V3 must resolve the selected technique and treat the int as preferred target identity. Preserve published api/event/StrikeInterceptEvent signatures and unrelated routes; this task does not repair them.

Jar-verified DMZ StrikeAttackCastEvent, StrikeAttackFireEvent, KiAttackCastEvent and KiAttackFireEvent extend plain Event without ICancellableEvent. They cannot cancel native actions. The Xeno intercept event and cancellation of a mixin callback are separate ownership handoffs. Existing require=0 injection can miss without startup failure; fresh application proof is still required.

## Flight ownership

    FlySkillEvent.handleFlightMovement(LocalPlayer,int,boolean) [private static]
      (Lnet/minecraft/client/player/LocalPlayer;IZ)V
    CombatFlightHandler.handle(LocalPlayer,StatsData,boolean) [public static]
      (Lnet/minecraft/client/player/LocalPlayer;Lcom/dragonminez/common/stats/StatsData;Z)V
    Status.getFlightMode()I; Status.setFlightMode(I)V; Skill.setActive(Z)V

Tracked client/events/FlySkillEvent.java selects Combat Fly for mode 1 and Search Fly otherwise when flying. The final boolean is **movementRestricted**, despite existing injector parameter names canSprint. Existing DmzFlyMovementDuringChaseMixin and DmzCombatFlyMovementDuringChaseMixin cancel only those motion methods at HEAD for chase/UltimateFinisher ownership, and are in the client mixin list. Both branches need V3 motion gating; keep client classes isolated from dedicated-server loading.

Current ChaseFlightSystem.beginSearchFly snapshots learned fly/mode/abilities, enables inactive learned flight in mode 0 once, then syncs. Restore only its unchanged active mode-0 grant; ChaseFlightOwnership prevents resurrecting native disables or overwriting another mode. The legacy path leaves already active modes alone; V2 has a separate borrowed-mode policy. Native FlightModeC2S toggles 0/1 and respects CombatFlyLock; FlyToggleC2S enables/disables. CombatEvent.maybeForceCombatFly can change active mode 0 to 1 on damage and applies the lock.

No standalone SearchToFly symbol was found: the verified adapter is learned skill/status/abilities plus both client motion branches. Recommended: one server velocity owner and entry snapshot; respect native depletion, toggles, auto-switch and all stop conditions instead of blind restoration. Preserve UltimateFinisher jitter gates. Fresh flight transition/cleanup/dedicated-client/no-oscillation proof is not verified.

## Real Ki charge, release and final hit

    TechniqueDispatcher.executeKiAttack(LivingEntity,Level,KiAttackData,StatsData,float): boolean
      (Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lcom/dragonminez/common/stats/techniques/KiAttackData;Lcom/dragonminez/common/stats/StatsData;F)Z
    private static TechniqueDispatcher.getChargingKiEntities(LivingEntity,Level): List
      (Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;)Ljava/util/List;
    private static TickHandler.handleTechniqueCharge(ServerPlayer,StatsData): void
      (Lnet/minecraft/server/level/ServerPlayer;Lcom/dragonminez/common/stats/StatsData;)V
    private static TickHandler.resolveKiAttackOnRelease(ServerPlayer,StatsData,Techniques): void
      (Lnet/minecraft/server/level/ServerPlayer;Lcom/dragonminez/common/stats/StatsData;Lcom/dragonminez/common/stats/techniques/Techniques;)V
    KiWaveEntity.fireHability(I)V
    AbstractKiProjectile.isFiring()Z; setFiring(Z)V; getTechniqueId()Ljava/lang/String;
    AbstractKiProjectile.applyDamageOrHeal(Entity,float): boolean
      (Lnet/minecraft/world/entity/Entity;F)Z

Tracked TechniqueDispatcher.java returns false client-side. Multiplier <0.5 starts a native projectile; release clamps 0.5–2 and releases **all unfired owned AbstractKiProjectile instances within an inflated-30 owner box**, without technique-ID filtering. It sets real native type/rendering/colors/heal/homing/damage and calls native fire. UltimateFinisherKamehameha starts at 0.01, marks the created wave and releases at 2.0 only if its charge list contains exactly that wave. Preserve that ownership precheck.

Dispatcher alone does not implement admission, progress, charge/release costs, cooldown or events. TechniqueChargeC2S sets validated charge state. TickHandler.handleTechniqueCharge advances progress, charges energy and dispatches initial spawn; resolveKiAttackOnRelease refuses below 50%, fires, posts noncancellable fire event, assigns cooldown and charges release energy. It also discards leftovers and can interfere with a separately owned charge. V3 must coordinate/exclude concurrent native charging, use exact owned type/entity/technique identity, and discard only owned projectiles. Per-entry real native lifecycle/rendering remains not verified at runtime.

Tracked AbstractKiProjectile.applyDamageOrHeal resolves parts, distinguishes healing, applies Ki modifiers and calls LivingEntity.hurt; javap offsets **180–183** are hurt and its boolean return. It lacks a universal top-level server guard: the owned adapter must assert server-side. True return may be healing or a nonpositive damaging result.

Existing UltimateFinisher.onDamage records a positive Post witness for a firing owned wave; afterWaveDamage consumes the matched victim UUID. FinisherWaveKnockbackMixin injects RETURN into applyDamageOrHeal, after vanilla damage and knockback. This is the narrow final impulse seam: owned nonheal projectile, positive Post witness, matching resolved victim UUID and true return. Track nested/multiple hits explicitly. KiImpactEvents cancels a KiExplosionVisualEntity join only for replacement FX; that is visual ownership, not damage proof. Accepted/canceled/heal/part hits and final velocity remain not verified at runtime.

## Animation resolver and bones

    CombatAnimationResolver.reload(ResourceManager): void
      (Lnet/minecraft/server/packs/resources/ResourceManager;)V
    resolveAttack(String,boolean): String
      (Ljava/lang/String;Z)Ljava/lang/String;
    resolvePose(String): String; private static toPlayableKey(String): String
      (Ljava/lang/String;)Ljava/lang/String;
    private static final AVAILABLE_RAW: Set
      Ljava/util/Set;
    DMZPlayerModel.getAnimationResourceFallbacks(AbstractClientPlayer): ResourceLocation[]
      (Lnet/minecraft/client/player/AbstractClientPlayer;)[Lnet/minecraft/resources/ResourceLocation;
    DMZPlayerModel.getAnimationResourceFallbacks(GeoAnimatable): ResourceLocation[] [bridge]
      (Lsoftware/bernie/geckolib/animatable/GeoAnimatable;)[Lnet/minecraft/resources/ResourceLocation;
    GeoModel.getAnimation(GeoAnimatable,String): Animation
      (Lsoftware/bernie/geckolib/animatable/GeoAnimatable;Ljava/lang/String;)Lsoftware/bernie/geckolib/animation/Animation;
    MeleeAnimationS2C(int,String,boolean,float)
      (ILjava/lang/String;ZF)V

Tracked CombatAnimationResolver reloads only dragonminez:animations/entity/races/combat.animation.json and resolves keys present directly or prefixed with combat. Existing DmzCombatAnimationRegistryMixin adds custom names after every reload RETURN. DmzGeoModelBt3AnimationMixin owns nonnull combat.xeno_* studio/shipped lookup at GeoModel HEAD for DMZPlayerModel; RETURN is another fallback. DmzPlayerModelAnimationFilesMixin appends the authored file only when baked and hooks both jar-verified overloads. These client-only, namespace-gated seams are additive; do not replace native files or cancel global native resolution.

Exact jar JSON inspection covered 32 assets under assets/dragonminez/geo/entity/races/: all **27 body models** contain the seven authored combat bones; five accessory/Ki-effect assets are not complete body skeletons. Current authored bt3_combat.animation.json uses exactly those seven names. Human and human_slim parents are:

| Channel | Bone | Parent |
|---|---|---|
| Whole-body visual orientation/offset | root | none |
| Torso/pelvis lean | waist | root |
| Head | head | waist |
| Right arm | right_arm | waist |
| Left arm | left_arm | waist |
| Right leg | right_leg | root |
| Left leg | left_leg | root |

DMZPlayerModel.setCustomAnimations writes head look and shooting-arm aim and adds walking arm motion. Existing DmzMeleeHeadLookMixin/DmzMeleeHeadMolangMixin address overlays within their own gate. Valid names do not prove an animation wins those overlays. Root bone motion is render displacement, not server entity movement; avoid double movement with server root authority. Skeleton names are verified; proportions/pivots/custom models/hair, bake/reload, visible choreography and reference parity are not verified at runtime.

## Existing-chunk ticket boundary

Pinned local ServerChunkCache/DistanceManager/ChunkLevel source and javap verify:

    ServerLevel.getChunkSource(): ServerChunkCache
      ()Lnet/minecraft/server/level/ServerChunkCache;
    ServerChunkCache.getChunkNow(int,int): LevelChunk
      (II)Lnet/minecraft/world/level/chunk/LevelChunk;
    ServerChunkCache.addRegionTicket(TicketType,ChunkPos,int,Object): void
    ServerChunkCache.removeRegionTicket(TicketType,ChunkPos,int,Object): void
      (Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V
    addRegionTicket(TicketType,ChunkPos,int,Object,boolean): void
    removeRegionTicket(TicketType,ChunkPos,int,Object,boolean): void
      (Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;Z)V
    TicketType.create(String,Comparator): TicketType
      (Ljava/lang/String;Ljava/util/Comparator;)Lnet/minecraft/server/level/TicketType;
    TicketType.create(String,Comparator,int): TicketType
      (Ljava/lang/String;Ljava/util/Comparator;I)Lnet/minecraft/server/level/TicketType;
    ServerLevel.setChunkForced(int,int,boolean): boolean
      (IIZ)Z

getChunkNow is non-generating: null off main thread or absent current FULL LevelChunk, querying visible holders/cache. NeoForge can expose currentlyLoading to avoid deadlock; nonnull does not alone prove entity ticking. Region tickets require matching type/position/distance/value/forceTicks for removal. DistanceManager computes FULL level minus distance; ChunkLevel defines FULL=33, BLOCK_TICKING=32, ENTITY_TICKING=31. Distance 0 requests FULL and distance 2 requests entity ticking. Optional boolean is **forceTicks**, not no-generation. TicketType.create integer is timeout, not radius.

**Available minimal seam:** server-thread loaded eligibility via getChunkNow, private V3 ticket type and fighter/session value, bounded acquired-position set, exact matched removal on every stop and timeout fallback. Respect maximum nine held tickets and current/next look-ahead; do not call a generating getChunk for admission. Existing MissileChunkLoadManager uses global setChunkForced and can load/generate requested coordinates, so it is not an existing-only combat adapter.

**Unavailable guarantee:** no inspected ticket API offers intrinsic existing-only/no-generation semantics. Tickets propagate level requirements and generation dependencies to neighboring chunks (ChunkLevel uses GENERATION_PYRAMID). Checking only directly ticketed coordinates cannot prove no neighboring terrain generation; nine held tickets is not nine affected chunks. Entity-ticking distance 2 strengthens that concern. Task 7 needs a separately reviewed policy covering required neighborhoods, or conservative loaded-only cancellation, plus fresh generation observation. A safe complete no-generation traversal policy, 40-tick missing-chunk handling and 999-block entity/client retention are **not verified**. These signatures alone do not meet Task 7 acceptance.

## Required next evidence

Fresh runtime fixtures must prove native tap scheduling and animation, old-session queued rejection, heavy costs/positive accepted-hit drains, native projectile lifecycle, flight cleanup/no jitter, no terrain generation and bounded ticket cleanup/999 retention. Per-attack full visual-reference and in-game animation comparison remain mandatory. All are not verified by this static task.



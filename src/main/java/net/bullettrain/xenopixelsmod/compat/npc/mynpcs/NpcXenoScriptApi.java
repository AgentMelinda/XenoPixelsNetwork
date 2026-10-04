package net.bullettrain.xenopixelsmod.compat.npc.mynpcs;


// The shared, NPC-mod-agnostic half of the integration, which stayed in compat.npc.
import net.bullettrain.xenopixelsmod.compat.npc.NpcAggroBridge;
import net.bullettrain.xenopixelsmod.compat.npc.NpcAppearanceFx;
import net.bullettrain.xenopixelsmod.compat.npc.NpcAuraFx;
import net.bullettrain.xenopixelsmod.compat.npc.NpcAuraResolver;
import net.bullettrain.xenopixelsmod.compat.npc.NpcAuraStyle;
import net.bullettrain.xenopixelsmod.compat.npc.NpcChargeMoves;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatBrainVersion;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatMoves;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCounterpartSync;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDisplayApply;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDmzAppearance;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDmzAnim;
import net.bullettrain.xenopixelsmod.compat.npc.NpcEntityLookup;
import net.bullettrain.xenopixelsmod.compat.npc.NpcFlightBridge;
import net.bullettrain.xenopixelsmod.compat.npc.NpcFormLookup;
import net.bullettrain.xenopixelsmod.compat.npc.NpcHakai;
import net.bullettrain.xenopixelsmod.compat.npc.NpcHairBridge;
import net.bullettrain.xenopixelsmod.compat.npc.NpcKiAim;
import net.bullettrain.xenopixelsmod.compat.npc.NpcKiAttackDispatcher;
import net.bullettrain.xenopixelsmod.compat.npc.NpcKiCooldowns;
import net.bullettrain.xenopixelsmod.compat.npc.NpcMeleeDamage;
import net.bullettrain.xenopixelsmod.compat.npc.NpcResources;
import net.bullettrain.xenopixelsmod.compat.npc.NpcScriptLists;
import net.bullettrain.xenopixelsmod.compat.npc.NpcScriptSay;
import net.bullettrain.xenopixelsmod.compat.npc.NpcScriptSound;
import net.bullettrain.xenopixelsmod.compat.npc.NpcSkillSet;
import net.bullettrain.xenopixelsmod.compat.npc.NpcTransformSystem;
import espi.mynpcs.api.entity.ICustomNpc;
import espi.mynpcs.api.entity.IEntity;
import espi.mynpcs.api.entity.IPlayer;
import espi.mynpcs.api.event.PlayerEvent;
import net.bullettrain.xenopixelsmod.command.XenoPointsCommands;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.DmzLockOnPacket;
import espi.mynpcs.controllers.ScriptContainer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit, server-side XenoPixels bridge exposed to CustomNPCs scripts as {@code XenoPixels}. */
public final class NpcXenoScriptApi {
    public static final NpcXenoScriptApi INSTANCE = new NpcXenoScriptApi();
    private static final String VERSION = "28";
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    private NpcXenoScriptApi() {}

    public static void install() {
        ScriptContainer.Data.put("XenoPixels", INSTANCE);
    }

    public String getVersion() { return VERSION; }

    /** Returns the current mutable message while a My NPCs {@code chat(event)} script is running. */
    public String getChatMessage(PlayerEvent.ChatEvent event) {
        return NpcScriptSay.resolveChatMessage(event == null ? null : event.message);
    }

    /**
     * Replaces {@code ChatEvent.message} so {@code chat(event)} write-back can see the
     * new line. The signed {@code ServerChatEvent} is cancelled by
     * {@code PlayerChatEventMixin} or the LOWEST empty-key listener, which then
     * broadcasts {@code chat.type.text}. This method only assigns the field.
     */
    public boolean setChatMessage(PlayerEvent.ChatEvent event, String message) {
        if (event == null || message == null || message.length() > ChatEventPolicy.MAX_MESSAGE_LENGTH) {
            return false;
        }
        event.message = message;
        return true;
    }

    /** Cancels the cancellable My NPCs chat event before it is posted to its API event bus. */
    public boolean cancelChat(PlayerEvent.ChatEvent event) {
        if (event == null) return false;
        event.setCanceled(true);
        NpcScriptSay.markChatCancelled();
        return true;
    }

    public boolean isChatCancelled(PlayerEvent.ChatEvent event) {
        return event != null && event.isCanceled();
    }

    /**
     * Private literal system line to this player. Not public chat and not {@code IPlayer.message}
     * (that path treats the text as a translation key and shows nothing).
     *
     * @return false when the player is missing or the text is empty / longer than 256
     */
    public boolean say(IPlayer<?> player, String message) {
        if (player == null) return false;
        return NpcScriptSay.tell(player.getMCEntity(), message);
    }

    /**
     * A XenoPixels speech bubble over any entity - a player too - that everyone nearby sees.
     * {@code say(player, text)} stays the private chat line. Palette: blue, gold, green, red;
     * shape: rounded, thought, shout, banner. Honors {@code /xenopixels npcsay}.
     */
    public boolean bubble(IEntity<?> entity, String message) {
        return bubble(entity, message, "", "");
    }

    public boolean bubble(IEntity<?> entity, String message, String palette) {
        return bubble(entity, message, palette, "");
    }

    public boolean bubble(IEntity<?> entity, String message, String palette, String shape) {
        String text = NpcScriptSay.sanitize(message);
        if (text == null || entity == null) return false;
        if (!net.bullettrain.xenopixelsmod.config.XenoServerConfig.npcSayEnabled) return false;
        if (!(entity.getMCEntity() instanceof net.minecraft.world.entity.LivingEntity living)) return false;
        return net.bullettrain.xenopixelsmod.npc.lines.XenoNpcSpeech.say(living, text, null,
                palette == null ? "" : palette,
                net.bullettrain.xenopixelsmod.npc.lines.BubbleShape.byName(shape,
                        net.bullettrain.xenopixelsmod.npc.lines.BubbleShape.INHERIT));
    }

    /**
     * Public {@code <name> text} line to every player. Use this instead of
     * {@code IPlayer.message} or a native broadcast — those hide on 1.21.1 signed chat.
     */
    public boolean broadcast(IPlayer<?> player, String message) {
        if (player == null) return false;
        return NpcScriptSay.broadcastLater(player.getMCEntity(), message);
    }

    /**
     * NPC chat bubble via {@code ICustomNpc.say}. Honors {@code /xenopixels npcsay}.
     *
     * @return false when muted, the NPC is missing, or the text is empty / longer than 256
     */
    public boolean say(ICustomNpc npc, String message) {
        String text = NpcScriptSay.sanitize(message);
        if (text == null || npc == null) return false;
        if (!net.bullettrain.xenopixelsmod.config.XenoServerConfig.npcSayEnabled) return false;
        npc.say(text);
        return true;
    }

    /**
     * A XenoPixels speech bubble over this NPC in {@code palette} (blue, gold, green, red) - the
     * same bubble native Xeno NPCs use, rather than the NPC mod's own chat bubble.
     */
    public boolean say(ICustomNpc npc, String message, String palette) {
        return say(npc, message, palette, "");
    }

    /**
     * As above in {@code shape}: rounded, thought, shout or banner. Blank palette or shape keeps
     * the NPC's default. Honors {@code /xenopixels npcsay}.
     */
    public boolean say(ICustomNpc npc, String message, String palette, String shape) {
        String text = NpcScriptSay.sanitize(message);
        if (text == null || npc == null) return false;
        if (!net.bullettrain.xenopixelsmod.config.XenoServerConfig.npcSayEnabled) return false;
        if (!(npc.getMCEntity() instanceof net.minecraft.world.entity.LivingEntity living)) return false;
        return net.bullettrain.xenopixelsmod.npc.lines.XenoNpcSpeech.say(living, text, null,
                palette == null ? "" : palette,
                net.bullettrain.xenopixelsmod.npc.lines.BubbleShape.byName(shape,
                        net.bullettrain.xenopixelsmod.npc.lines.BubbleShape.INHERIT));
    }

    /** Adds real DragonMineZ training points and immediately synchronizes the player's HUD. */
    public boolean addDmzPoints(IPlayer<?> player, int amount) {
        if (player == null || amount <= 0) return false;
        ServerPlayer serverPlayer = player.getMCEntity();
        return XenoPointsCommands.addPoints(serverPlayer, amount);
    }

    public boolean hasProfile(ICustomNpc npc) { return entity(npc) != null && NpcCombatProfile.hasProfile(entity(npc)); }

    public boolean isAuthoritative(ICustomNpc npc) {
        LivingEntity entity = require(npc);
        return NpcCombatProfile.read(entity).authoritative;
    }

    public boolean setAuthoritative(ICustomNpc npc, boolean authoritative) {
        return mutate(npc, p -> p.authoritative = authoritative);
    }

    public boolean isKnockable(ICustomNpc npc) { return NpcCombatProfile.read(require(npc)).knockable; }
    public boolean setKnockable(ICustomNpc npc, boolean value) { return mutate(npc, p -> p.knockable = value); }
    public boolean isPunchable(ICustomNpc npc) { return NpcCombatProfile.read(require(npc)).punchable; }
    public boolean setPunchable(ICustomNpc npc, boolean value) { return mutate(npc, p -> p.punchable = value); }

    /** Returns a defensive DMZ-shaped NBT snapshot, not a native DragonMineZ StatsData object. */
    public net.minecraft.nbt.CompoundTag getDmzStatSnapshot(ICustomNpc npc) {
        return NpcCombatProfile.read(require(npc)).dmzStatSnapshot();
    }

    /** Reapplies the explicit profile to verified native NPC counterpart fields immediately. */
    public boolean reapplyStats(ICustomNpc npc) {
        LivingEntity entity = require(npc);
        NpcCounterpartSync.force(entity, NpcCombatProfile.read(entity));
        return true;
    }

    public boolean setStat(ICustomNpc npc, String stat, int value) {
        String key = safe(stat).toLowerCase(java.util.Locale.ROOT);
        return mutate(npc, p -> {
            int v = Math.max(0, value);
            switch (key) {
                case "strength" -> p.strength = v;
                case "strikepower", "strike_power" -> p.strikePower = v;
                case "resistance" -> p.resistance = v;
                case "vitality" -> p.vitality = v;
                case "kipower", "ki_power" -> p.kiPower = v;
                case "energy" -> p.energy = v;
                default -> throw new IllegalArgumentException("Unknown XenoPixels stat: " + stat);
            }
        });
    }

    public Map<String, Object> getProfile(ICustomNpc npc) {
        LivingEntity entity = require(npc);
        NpcCombatProfile p = NpcCombatProfile.read(entity);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("race", p.raceId); out.put("strength", p.strength); out.put("strikePower", p.strikePower);
        out.put("resistance", p.resistance); out.put("vitality", p.vitality); out.put("kiPower", p.kiPower);
        out.put("energy", p.energy); out.put("kiChargePercent", p.kiChargePercent);
        out.put("formGroup", p.formGroup); out.put("form", p.formId);
        out.put("selectedFormGroup", p.selectedFormGroup); out.put("selectedForm", p.selectedFormId);
        out.put("stackGroup", p.stackGroup); out.put("stack", p.stackId);
        out.put("selectedStackGroup", p.selectedStackGroup); out.put("selectedStack", p.selectedStackId);
        out.put("auraOn", p.auraOn); out.put("auraColor", p.auraColorHex);
        out.put("auraScale", p.auraScale); out.put("halo", p.haloOn);
        out.put("auraRocks", p.auraRocks); out.put("auraSparking", p.auraSparking);
        out.put("auraLightning", p.auraLightning); out.put("auraGroundRing", p.auraGroundRing);
        out.put("flySkill", p.flySkillOn); out.put("flySkillLevel", p.flySkillLevel);
        out.put("aggroMultiplier", p.aggroMultiplier); out.put("aimAccuracy", p.aimAccuracy);
        out.put("combatBrain", p.combatBrain);
        out.put("brainVersion", p.brainVersion.label());
        out.put("brain", p.brainFlagMap());
        Map<String, Object> skillMap = new LinkedHashMap<>();
        for (Map.Entry<String, NpcSkillSet.Entry> entry : p.skills.entries().entrySet()) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("on", entry.getValue().active());
            value.put("level", entry.getValue().level());
            skillMap.put(entry.getKey(), value);
        }
        out.put("skills", skillMap);
        out.put("kiWeaponOn", p.kiWeaponOn); out.put("kiWeaponType", p.kiWeaponType);
        out.put("saiyanTail", p.appearance.saiyanTail);
        out.put("tailColor", p.appearance.tailColor);
        out.put("tailUseRaceColor", p.appearance.tailUseRaceColor);
        NpcResources.Snapshot resources = NpcResources.get(entity, p);
        out.put("currentEnergy", resources.energy()); out.put("maxEnergy", resources.maxEnergy());
        out.put("currentStamina", resources.stamina()); out.put("maxStamina", resources.maxStamina());
        out.put("techniques", List.copyOf(p.techniques));
        out.put("hair", getHair(npc));
        return out;
    }

    public boolean setProfile(ICustomNpc npc, String race, int strength, int strikePower, int resistance,
                              int vitality, int kiPower, int energy) {
        LivingEntity entity = require(npc); NpcCombatProfile p = NpcCombatProfile.read(entity);
        p.raceId = race == null || race.isBlank() ? "human" : race;
        p.strength = Math.max(0, strength); p.strikePower = Math.max(0, strikePower);
        p.resistance = Math.max(0, resistance); p.vitality = Math.max(0, vitality);
        p.kiPower = Math.max(0, kiPower); p.energy = Math.max(0, energy); p.write(entity); return true;
    }

    public boolean setKiCharge(ICustomNpc npc, int percent) { return mutate(npc, p -> p.kiChargePercent = Math.max(1, Math.min(1000, percent))); }
    /**
     * Stance multiplier. 100 = a player at full release. Caps at the server
     * {@code sparkingReleaseLimit} (default 225), not a live Sparking meter.
     */
    public boolean setPowerRelease(ICustomNpc npc, int percent) {
        int cap = Math.max(100, net.bullettrain.xenopixelsmod.config.XenoServerConfig.sparkingReleaseLimit);
        return mutate(npc, p -> p.powerReleasePercent = Math.max(1, Math.min(cap, percent)));
    }
    public boolean setAura(ICustomNpc npc, boolean on) { return mutateAura(npc, p -> p.auraOn = on); }
    public boolean setAuraColor(ICustomNpc npc, String hex) { return color(npc, hex, true); }
    /**
     * Clearer-named alias for {@link #setAuraColor} -- this only ever affects the base/
     * untransformed aura layer; a transformed form's own DMZ aura color (or its per-form style
     * override) always wins while transformed. Same effect, kept as a separate method rather
     * than renaming {@code setAuraColor} so existing scripts keep working unchanged.
     */
    public boolean setBaseAuraColor(ICustomNpc npc, String hex) { return color(npc, hex, true); }
    public boolean setKiColor(ICustomNpc npc, String hex) { return color(npc, hex, false); }
    public boolean setAuraScale(ICustomNpc npc, float scale) { return mutateAura(npc, p -> p.auraScale = NpcCombatProfile.clampAuraScale(scale)); }
    public boolean setHalo(ICustomNpc npc, boolean on) { return mutate(npc, p -> p.haloOn = on); }
    public boolean setAuraRocks(ICustomNpc npc, boolean on) { return mutateAura(npc, p -> p.auraRocks = on); }
    public boolean setAuraSparking(ICustomNpc npc, boolean on) { return mutateAura(npc, p -> p.auraSparking = on); }
    public boolean setAuraLightning(ICustomNpc npc, boolean on) { return mutateAura(npc, p -> p.auraLightning = on); }
    /** The expanding floor rings under the aura, independent of the aura column itself. */
    public boolean setAuraGroundRing(ICustomNpc npc, boolean on) { return mutateAura(npc, p -> p.auraGroundRing = on); }

    /**
     * DragonMineZ's {@code fly} skill. Turning it on both gives a FULL-appearance NPC DMZ's
     * flight pose and switches CustomNPCs' navigator to the flying one, so the NPC actually
     * pathfinds through the air.
     */
    public boolean setFlySkill(ICustomNpc npc, boolean on) { return mutateFlight(npc, p -> p.flySkillOn = on); }
    /** Clamped to DMZ's own usable 1..10 range. */
    public boolean setFlySkillLevel(ICustomNpc npc, int level) {
        return mutateFlight(npc, p -> p.flySkillLevel = NpcCombatProfile.clampFlySkillLevel(level));
    }
    public boolean isFlySkillOn(ICustomNpc npc) { return NpcCombatProfile.read(require(npc)).flySkillOn; }
    public int getFlySkillLevel(ICustomNpc npc) { return NpcCombatProfile.read(require(npc)).flySkillLevel; }

    // ---- DragonMineZ skills -------------------------------------------------------------
    // Ids come from DMZ's own skills config, not a list baked in here; listSkills() reports
    // exactly what this DMZ install recognises.

    /** Every DMZ skill id this install knows, e.g. "kamehameha", "kaioken", "kisense". */
    public List<String> listSkills() { return NpcSkillSet.knownIds(); }
    /** DMZ's own maximum level for a skill, derived from the length of its TP cost table. */
    public int getSkillMaxLevel(String id) { return NpcSkillSet.maxLevelOf(id); }

    public boolean setSkill(ICustomNpc npc, String id, boolean on) {
        if (!NpcSkillSet.isKnown(id)) return false;
        return mutateSkills(npc, id, p -> p.skills.setActive(id, on));
    }

    public boolean setSkillLevel(ICustomNpc npc, String id, int level) {
        if (!NpcSkillSet.isKnown(id)) return false;
        return mutateSkills(npc, id, p -> p.skills.setLevel(id, level));
    }

    /** Sets both at once. */
    public boolean setSkill(ICustomNpc npc, String id, boolean on, int level) {
        if (!NpcSkillSet.isKnown(id)) return false;
        return mutateSkills(npc, id, p -> p.skills.set(id, on, level));
    }

    public boolean isSkillOn(ICustomNpc npc, String id) {
        return NpcCombatProfile.read(require(npc)).skills.isActive(id);
    }

    public int getSkillLevel(ICustomNpc npc, String id) {
        return NpcCombatProfile.read(require(npc)).skills.level(id);
    }

    public boolean removeSkill(ICustomNpc npc, String id) {
        return mutateSkills(npc, id, p -> p.skills.remove(id));
    }

    public boolean clearSkills(ICustomNpc npc) {
        return mutateSkills(npc, NpcSkillSet.FLY, p -> {
            p.skills.clear();
            p.flySkillOn = false;
            p.flySkillLevel = 1;
        });
    }

    // ---- Targeting and lock-on ----------------------------------------------------------

    /** Pins this NPC to a player by UUID until {@link #clearLock} is called. */
    public boolean lockOnPlayer(ICustomNpc npc, String playerUuid) {
        LivingEntity e = require(npc);
        java.util.UUID id;
        try {
            id = java.util.UUID.fromString(safe(playerUuid));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        if (!(e.level() instanceof ServerLevel level)) return false;
        LivingEntity target = NpcEntityLookup.findAlive(level.getServer(), id);
        if (target != null) {
            NpcKiAim.hardLock(e, target);
        } else {
            // Not loaded right now -- remember the id so the lock takes effect when it is.
            NpcKiAim.hardLock(e, id);
        }
        return true;
    }

    /** Pins this NPC to any entity until cleared. */
    public boolean lockOn(ICustomNpc npc, IEntity target) {
        LivingEntity e = require(npc);
        LivingEntity living = living(target);
        if (living == null) return false;
        NpcKiAim.hardLock(e, living);
        return true;
    }

    public boolean clearLock(ICustomNpc npc) {
        NpcKiAim.clearHardLock(require(npc));
        return true;
    }

    public boolean isLocked(ICustomNpc npc) { return NpcKiAim.isHardLocked(require(npc)); }

    /**
     * Forces a player's own DragonMineZ lock-on reticle onto an entity. Purely client-visual --
     * DMZ's lock-on drives its camera and HUD, not damage.
     */
    public boolean forcePlayerLock(IEntity player, IEntity target) {
        if (player == null || !(player.getMCEntity() instanceof ServerPlayer viewer)) return false;
        int targetId = target != null && target.getMCEntity() != null
                ? target.getMCEntity().getId() : -1;
        ModNetwork.sendToPlayer(viewer, new DmzLockOnPacket(targetId));
        return true;
    }

    public boolean clearPlayerLock(IEntity player) {
        if (player == null || !(player.getMCEntity() instanceof ServerPlayer viewer)) return false;
        ModNetwork.sendToPlayer(viewer, DmzLockOnPacket.clear());
        return true;
    }

    // ---- Combat moves and autonomous behaviour ------------------------------------------

    public boolean setAggroMultiplier(ICustomNpc npc, float multiplier) {
        LivingEntity e = require(npc); NpcCombatProfile p = NpcCombatProfile.read(e);
        p.aggroMultiplier = NpcCombatProfile.clampAggroMultiplier(multiplier);
        p.write(e);
        NpcAggroBridge.forget(e.getUUID());
        NpcAggroBridge.apply(e, p);
        return true;
    }

    /**
     * Stores AimAccuracy. Nothing reads it yet: ki-blast lead blends by
     * {@code npcRangedAccuracy}. Kept so existing scripts do not break.
     */
    public boolean setAimAccuracy(ICustomNpc npc, float accuracy) {
        return mutate(npc, p -> p.aimAccuracy = NpcCombatProfile.clampAimAccuracy(accuracy));
    }

    /** Autonomous combat. Off by default so a scripted NPC keeps doing exactly what its script says. */
    public boolean setCombatBrain(ICustomNpc npc, boolean on) {
        return mutate(npc, p -> p.combatBrain = on);
    }

    public boolean setBrainVersion(ICustomNpc npc, String version) {
        return mutate(npc, p -> p.setBrainVersion(NpcCombatBrainVersion.byLegacyName(version)));
    }

    public String getBrainVersion(ICustomNpc npc) {
        return NpcCombatProfile.read(require(npc)).brainVersion.label();
    }

    /**
     * Brain flag names: {@code strike}, {@code charge}, {@code flyingFist}, {@code heavyHit},
     * {@code boneCrusher}, {@code kiai}, {@code randomKi}, {@code kiBlast}, {@code kiWave},
     * {@code kiDisk}, {@code kiNamed}, {@code fly}, {@code land}, {@code ascend},
     * {@code disengage}, {@code vanish}, {@code zanzoken}, {@code chase}, {@code deflectBlast},
     * {@code deflectWave}.
     */
    public boolean setBrainFlag(ICustomNpc npc, String flag, boolean on) {
        return mutate(npc, p -> p.setBrainFlag(flag, on));
    }

    public boolean getBrainFlag(ICustomNpc npc, String flag) {
        return NpcCombatProfile.read(require(npc)).brainFlag(flag);
    }

    public boolean setBrainChance(ICustomNpc npc, String flag, int chance) {
        return mutate(npc, p -> p.setBrainChance(flag, chance));
    }

    public int getBrainChance(ICustomNpc npc, String flag) {
        return NpcCombatProfile.read(require(npc)).brainChance(flag);
    }

    public boolean setBrainModifier(ICustomNpc npc, String flag, double modifier) {
        return mutate(npc, p -> p.setBrainModifier(flag, (float) modifier));
    }

    public double getBrainModifier(ICustomNpc npc, String flag) {
        return NpcCombatProfile.read(require(npc)).brainModifier(flag);
    }

    public boolean setBrainSpecialCooldown(ICustomNpc npc, int ticks) {
        return mutate(npc, p -> p.brainSpecialCooldown = NpcCombatProfile.clampSpecialCooldown(ticks));
    }

    /**
     * Teleports the NPC to the target's back, the way a player's vanish does.
     *
     * @param side negative = left of behind, 0 = directly behind, positive = right of behind —
     *             the same convention {@code Bt3CombatPacket.vanishBehind} uses for the player's
     *             double-tap A/D, and it tests the sign only, not the magnitude.
     * @return false when the move is refused: its 40-tick cooldown is still running, the NPC
     *         cannot pay the energy cost, or the target is beyond {@code vanishMaxRange}.
     */
    public boolean vanish(ICustomNpc npc, IEntity target, int side) {
        return NpcCombatMoves.vanish(require(npc), living(target), side);
    }
    /** Vanish directly behind the target. */
    public boolean vanish(ICustomNpc npc, IEntity target) {
        return vanish(npc, target, 0);
    }
    /** Vanish directly behind the target. */
    public boolean vanishBehind(ICustomNpc npc, IEntity target) {
        return vanish(npc, target, 0);
    }
    /** Vanish to the target's back-left — the player's double-tap-A direction. */
    public boolean vanishLeft(ICustomNpc npc, IEntity target) {
        return vanish(npc, target, -1);
    }
    /** Vanish to the target's back-right — the player's double-tap-D direction. */
    public boolean vanishRight(ICustomNpc npc, IEntity target) {
        return vanish(npc, target, 1);
    }
    public boolean chase(ICustomNpc npc, IEntity target) {
        return NpcCombatMoves.chase(require(npc), living(target));
    }
    public boolean backstep(ICustomNpc npc, IEntity target) {
        return NpcCombatMoves.backstep(require(npc), living(target));
    }
    public boolean zBurst(ICustomNpc npc, IEntity target) {
        return NpcCombatMoves.zBurst(require(npc), living(target));
    }
    public boolean setGuard(ICustomNpc npc, boolean on) {
        return NpcCombatMoves.guard(require(npc), on);
    }
    public boolean isGuarding(ICustomNpc npc) { return NpcCombatMoves.isGuarding(require(npc)); }

    /**
     * Plays one of this mod's DragonMineZ combat clips on the NPC.
     *
     * <p>Only NPCs set to the Full DragonMineZ appearance can show these - that mode draws the NPC
     * through a synthetic player, which is the only thing DragonMineZ's animation system will pose.
     * Anything else returns false rather than quietly doing nothing.
     *
     * @param animation a name from {@link #listAnimations()}, ours ({@code combat.xeno_*}) or one
     *                  of the stock DragonMineZ clips this mod binds
     * @return false for an unknown name or an NPC that cannot show these clips
     */
    public boolean playAnimation(ICustomNpc npc, String animation) {
        return NpcDmzAnim.play(require(npc), animation);
    }

    /** @param speed playback multiplier, clamped to 0.15-4.0 */
    public boolean playAnimation(ICustomNpc npc, String animation, float speed) {
        return NpcDmzAnim.play(require(npc), animation, speed);
    }

    /**
     * Plays one beat of the same BT3 rush string the player's held mash walks, so an NPC can throw
     * the authored choreography rather than a list of individually named clips.
     *
     * @param step 1-based beat; wraps past the end of the route
     */
    public boolean playComboBeat(ICustomNpc npc, int step) {
        return NpcDmzAnim.play(require(npc),
                net.bullettrain.xenopixelsmod.combat.Bt3ComboChoreography.resolve(0, step, false, false));
    }

    /** Every animation name {@link #playAnimation} accepts. */
    public String[] listAnimations() {
        var names = net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationCatalog
                .playableAnimationNames();
        return names.toArray(new String[0]);
    }

    // ---------------------------------------------------------------- studio clips

    /**
     * Plays a Xeno Anim Studio clip on this NPC.
     *
     * <p>Takes the bare clip name ({@code "my_jab"}) or the full animation name
     * ({@code "combat.xeno_my_jab"}). The clip must be one this mod ships or one published to the
     * server with {@code /xenoanim global push} - a clip that only exists in one player's config
     * folder is not something the server can ask everyone to draw.
     *
     * @return false when the NPC is not drawn in Full DragonMineZ appearance, or the clip is unknown
     */
    public boolean playClip(ICustomNpc npc, String clip) {
        return net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.playClip(require(npc), clip);
    }

    /** As {@link #playClip(ICustomNpc, String)}, at a playback multiplier. */
    public boolean playClip(ICustomNpc npc, String clip, float speed) {
        return net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.playClip(require(npc), clip, speed);
    }

    /** Plays {@code clip} and cuts it after {@code durationTicks}. */
    public boolean playClip(ICustomNpc npc, String clip, float speed, int durationTicks) {
        return net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.playClip(
                require(npc), clip, speed, durationTicks);
    }

    /**
     * Plays {@code clip} at {@code speed}. After {@code durationTicks} the pose returns to idle
     * unless {@code hold} is true, in which case the last authored frame stays until
     * {@link #stopClip(ICustomNpc)} or another play.
     */
    public boolean playClip(ICustomNpc npc, String clip, float speed, int durationTicks, boolean hold) {
        return net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.playClip(
                require(npc), clip, speed, durationTicks, hold);
    }

    /**
     * Nashorn-safe one-shot. JS {@code Number} is {@code Double}, so
     * {@code playClip(npc, name, 1.0, ticks, false)} often fails to bind and the
     * interact/timer script dies before a clip starts.
     */
    public boolean playClipOnce(ICustomNpc npc, String clip) {
        return net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.playClip(
                require(npc), clip, 1.0f, 0, false);
    }

    /**
     * Nashorn-safe hold. Plays {@code clip} and freezes the last authored frame
     * until {@link #stopClip} or another play. Do not use the 5-arg
     * {@code playClip(..., true)} from Nashorn — JS {@code Boolean} often fails to bind.
     */
    public boolean playClipHold(ICustomNpc npc, String clip) {
        return net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.playClip(
                require(npc), clip, 1.0f, 0, true);
    }

    public boolean playClip(IPlayer<?> player, String clip) {
        return playClipOn(living(player), clip, 1.0f, 0, false);
    }

    public boolean playClip(IPlayer<?> player, String clip, float speed) {
        return playClipOn(living(player), clip, speed, 0, false);
    }

    public boolean playClip(IPlayer<?> player, String clip, float speed, int durationTicks) {
        return playClipOn(living(player), clip, speed, durationTicks, false);
    }

    public boolean playClip(IPlayer<?> player, String clip, float speed, int durationTicks, boolean hold) {
        return playClipOn(living(player), clip, speed, durationTicks, hold);
    }

    /**
     * Stops a scripted clip, including one already on DragonMineZ's KI controller.
     */
    public boolean stopClip(ICustomNpc npc) {
        return net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.stopClip(require(npc));
    }

    public boolean stopClip(IPlayer<?> player) {
        LivingEntity living = living(player);
        return living != null && net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.stopClip(living);
    }

    public boolean canPlayClip(ICustomNpc npc) {
        return net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.canPlay(require(npc));
    }

    public boolean canPlayClip(IPlayer<?> player) {
        LivingEntity living = living(player);
        return living != null && net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.canPlay(living);
    }

    /** Authored length in ticks, or {@code -1} when the server does not know it. */
    public int clipDuration(String clip) {
        return net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.clipDuration(clip);
    }

    private static boolean playClipOn(LivingEntity target, String clip, float speed,
                                      int durationTicks, boolean hold) {
        return target != null && net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.playClip(
                target, clip, speed, durationTicks, hold);
    }

    /** Every clip name {@link #playClip} accepts, including the server's published library. */
    public String[] listClips() {
        return net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.listClips().toArray(new String[0]);
    }

    /** Just the clips published to this server, as bare names. */
    public String[] listLibraryClips() {
        return net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.listLibraryClips()
                .toArray(new String[0]);
    }

    public boolean isClipAvailable(String clip) {
        return net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.isClipAvailable(clip);
    }

    /** Melee swing, using a flight clip when the NPC is on the flying navigator. */
    public boolean playMelee(ICustomNpc npc) {
        NpcKiAim.playMelee(require(npc));
        return true;
    }

    /**
     * One profile-scaled melee hit. {@code scale} 1 is a normal punch; 1.6 is a heavy finish.
     * This is not a charged punch or kick.
     */
    public boolean meleeHit(ICustomNpc npc, IEntity target) {
        return meleeHit(npc, target, 1.0);
    }

    public boolean meleeHit(ICustomNpc npc, IEntity target, double scale) {
        LivingEntity victim = living(target);
        if (victim == null) {
            return false;
        }
        float s = (float) scale;
        if (!(s > 0.0f) || Float.isNaN(s) || Float.isInfinite(s)) {
            s = 1.0f;
        }
        return NpcMeleeDamage.hit(require(npc), victim, s);
    }

    /**
     * Selects the animation that will play when CustomNPCs' next real melee attack lands.
     *
     * <p>Full DragonMineZ NPCs require a name from {@link #listAnimations()}. Gecko custom-model
     * NPCs accept an animation name authored in that model. The selection is stored on the NPC's
     * combat profile (the wand Atk field) until a script replaces or clears it; merely selecting
     * it never causes damage.
     */
    public boolean setMeleeAnimation(ICustomNpc npc, String animation) {
        return NpcMeleeDamage.persist(require(npc), animation);
    }

    public boolean clearMeleeAnimation(ICustomNpc npc) {
        return NpcMeleeDamage.persist(require(npc), "");
    }

    /**
     * Binds a published studio clip to a combat/transform state on this NPC.
     *
     * <p>Slots: {@code TRANSFORM}, {@code PUNCH}, {@code CHARGE_PUNCH},
     * {@code CHARGE_PUNCH_FIRE}, {@code CHARGE_KICK}, {@code CHARGE_KICK_FIRE},
     * {@code CHARGE_KI}, {@code HAKAI_HOLD}, {@code HAKAI_FIRE}. Punch is the same
     * store as {@link #setMeleeAnimation}. Empty clip clears the override.
     */
    public boolean setStateClip(ICustomNpc npc, String slot, String clip) {
        return net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.setStateClip(require(npc), slot, clip);
    }

    public boolean setStateClip(IPlayer<?> player, String slot, String clip) {
        return net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.setStateClip(living(player), slot, clip);
    }

    public String getStateClip(ICustomNpc npc, String slot) {
        return net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.getStateClip(require(npc), slot);
    }

    public String getStateClip(IPlayer<?> player, String slot) {
        LivingEntity target = living(player);
        return target == null ? ""
                : net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.getStateClip(target, slot);
    }

    public boolean clearStateClip(ICustomNpc npc, String slot) {
        return net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.clearStateClip(require(npc), slot);
    }

    public boolean clearStateClip(IPlayer<?> player, String slot) {
        return net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.clearStateClip(living(player), slot);
    }

    /** Plays the clip currently bound to {@code slot}. */
    public boolean playState(ICustomNpc npc, String slot) {
        return net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.playState(require(npc), slot);
    }

    public boolean playState(IPlayer<?> player, String slot) {
        return net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.playState(living(player), slot);
    }

    public String[] listStateSlots() {
        return net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.listStateSlots();
    }

    /**
     * Nashorn does not give Java arrays a JS {@code join}. Use this for
     * {@link #listStateSlots()}, {@link #listLibraryClips()}, and other lists.
     */
    public String joinNames(Object names) {
        return NpcScriptLists.joinNames(names);
    }

    public String joinNames(Object names, String separator) {
        return NpcScriptLists.joinNames(names, separator);
    }

    /**
     * Server-wide bind for every player and NPC without a per-entity override.
     * Same map as {@code /xenoanim bind}. Empty clip restores the shipped default.
     */
    public boolean bindStateSlot(String slot, String clip) {
        return net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.bindStateSlot(slot, clip);
    }

    /**
     * Starts a charged punch. {@code durationTicks <= 0} holds until
     * {@link #releaseCharge} or the configured max. Auto-releases at duration.
     */
    public boolean startChargePunch(ICustomNpc npc, int durationTicks) {
        return NpcChargeMoves.startPunch(require(npc), durationTicks);
    }

    /**
     * Starts a charged kick. {@code verticalBias} is -1 down, 0 neutral, +1 up.
     */
    public boolean startChargeKick(ICustomNpc npc, int durationTicks, int verticalBias) {
        return NpcChargeMoves.startKick(require(npc), durationTicks, verticalBias);
    }

    public boolean startChargeKick(ICustomNpc npc, int durationTicks) {
        return startChargeKick(npc, durationTicks, 0);
    }

    public boolean releaseCharge(ICustomNpc npc) {
        return NpcChargeMoves.release(require(npc));
    }

    public boolean cancelCharge(ICustomNpc npc) {
        return NpcChargeMoves.cancel(require(npc));
    }

    public boolean isCharging(ICustomNpc npc) {
        return NpcChargeMoves.isCharging(require(npc));
    }

    public int getChargePercent(ICustomNpc npc) {
        return NpcChargeMoves.getPercent(require(npc));
    }

    public String getChargeStyle(ICustomNpc npc) {
        return NpcChargeMoves.getStyle(require(npc));
    }

    /**
     * Plays a sound at this NPC that is actually audible at range.
     *
     * <p>CustomNPCs' own {@code world.playSoundAt} sends to a fixed 16-block radius regardless of
     * volume, so a loud NPC sound is simply never delivered further out -- see
     * {@link NpcScriptSound}.
     */
    public boolean playSound(ICustomNpc npc, String sound, float volume, float pitch) {
        LivingEntity e = require(npc);
        if (!NpcScriptSound.canPlayFrom(e) || !(e.level() instanceof ServerLevel level)) return false;
        var id = net.minecraft.resources.ResourceLocation.tryParse(safe(sound));
        if (id == null) return false;
        var event = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(id);
        if (event == null) return false;
        level.playSound(null, e.getX(), e.getY(), e.getZ(), event,
                net.minecraft.sounds.SoundSource.HOSTILE, Math.max(0.0f, volume), pitch);
        return true;
    }
    public boolean setKiWeaponOn(ICustomNpc npc, boolean on) { return mutate(npc, p -> p.kiWeaponOn = on); }
    public boolean setKiWeaponType(ICustomNpc npc, String type) {
        if (!NpcCombatProfile.isKiWeaponType(type)) return false;
        return mutate(npc, p -> p.kiWeaponType = NpcCombatProfile.canonicalKiWeaponType(type));
    }
    public boolean setSaiyanTail(ICustomNpc npc, boolean on) { return mutate(npc, p -> p.appearance.saiyanTail = on); }
    /** Empty color restores DMZ's race/body/form tail-color inheritance. */
    public boolean setTailColor(ICustomNpc npc, String hex) {
        if (hex == null || hex.isBlank()) return clearTailColor(npc);
        var parsed = NpcCombatProfile.parseHexColor(hex);
        if (parsed.isEmpty()) return false;
        return mutate(npc, p -> {
            p.appearance.tailColor = NpcCombatProfile.formatHex(parsed.getAsInt());
            // Setting a colour is a request to use it, matching the editor's picker.
            p.appearance.tailUseRaceColor = false;
        });
    }
    /**
     * Goes back to the race's own tail colour.
     *
     * <p>Keeps the stored colour rather than blanking it, so a later {@code setTailColor} with no
     * argument -- or the editor's toggle -- can restore the same one.
     */
    public boolean clearTailColor(ICustomNpc npc) {
        return mutate(npc, p -> p.appearance.tailUseRaceColor = true);
    }

    /** Moves the NPC to absolute world coordinates without vanish's energy or cooldown cost. */
    public boolean teleport(ICustomNpc npc, double x, double y, double z) {
        LivingEntity e = require(npc);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
        e.teleportTo(x, y, z);
        NpcAppearanceFx.sync(e);
        return true;
    }

    /** Moves the NPC onto an entity's position, keeping the NPC's current facing. */
    public boolean teleportToEntity(ICustomNpc npc, IEntity target) {
        LivingEntity t = living(target);
        return t != null && teleport(npc, t.getX(), t.getY(), t.getZ());
    }

    /** Moves the NPC onto a player's position, keeping the NPC's current facing. */
    public boolean teleportToPlayer(ICustomNpc npc, IPlayer<?> player) {
        if (player == null) return false;
        ServerPlayer target = player.getMCEntity();
        return target != null && teleport(npc, target.getX(), target.getY(), target.getZ());
    }

    /**
     * Plays a sound at an explicit world position with vanilla's volume-scaled audible radius.
     *
     * <p>My NPCs has no {@code world.playSoundAt} at all, and CustomNPCs' version sends to a fixed
     * 16-block radius regardless of volume. Both are covered here.
     */
    public boolean playSoundAt(ICustomNpc npc, double x, double y, double z,
                               String sound, float volume, float pitch) {
        LivingEntity e = require(npc);
        if (!(e.level() instanceof ServerLevel level)) return false;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
        var id = net.minecraft.resources.ResourceLocation.tryParse(safe(sound));
        if (id == null) return false;
        var event = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(id);
        if (event == null) return false;
        level.playSound(null, x, y, z, event, net.minecraft.sounds.SoundSource.HOSTILE,
                Math.max(0.0f, volume), pitch);
        return true;
    }

    /** Plays a sound only for one player, regardless of distance. */
    public boolean playSoundFor(IPlayer<?> player, String sound, float volume, float pitch) {
        if (player == null) return false;
        ServerPlayer target = player.getMCEntity();
        if (target == null) return false;
        var id = net.minecraft.resources.ResourceLocation.tryParse(safe(sound));
        if (id == null) return false;
        var event = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(id);
        if (event == null) return false;
        target.playNotifySound(event, net.minecraft.sounds.SoundSource.HOSTILE,
                Math.max(0.0f, volume), pitch);
        return true;
    }

    /** Current appearance mode: {@code OFF}, {@code OVERLAY} or {@code FULL}. */
    public String getAppearanceMode(ICustomNpc npc) {
        return NpcCombatProfile.read(require(npc)).appearance.mode.name();
    }

    /** Sets the appearance mode; an unrecognized name falls back to {@code OFF}. */
    public boolean setAppearanceMode(ICustomNpc npc, String mode) {
        NpcDmzAppearance.Mode parsed = NpcDmzAppearance.Mode.parse(safe(mode));
        return mutateAppearance(npc, p -> p.appearance.mode = parsed);
    }

    /** Every editable appearance color, as hex strings. */
    public Map<String, Object> getAppearanceColors(ICustomNpc npc) {
        NpcDmzAppearance a = NpcCombatProfile.read(require(npc)).appearance;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("bodyColor", a.bodyColor);
        out.put("bodyColor2", a.bodyColor2);
        out.put("bodyColor3", a.bodyColor3);
        out.put("eyeColor1", a.eye1Color);
        out.put("eyeColor2", a.eye2Color);
        return out;
    }

    public boolean setBodyColor(ICustomNpc npc, String hex) { return setAppearanceColor(npc, hex, ColorField.BODY_1); }
    public boolean setBodyColor2(ICustomNpc npc, String hex) { return setAppearanceColor(npc, hex, ColorField.BODY_2); }
    public boolean setBodyColor3(ICustomNpc npc, String hex) { return setAppearanceColor(npc, hex, ColorField.BODY_3); }
    public boolean setEyeColor1(ICustomNpc npc, String hex) { return setAppearanceColor(npc, hex, ColorField.EYE_1); }
    public boolean setEyeColor2(ICustomNpc npc, String hex) { return setAppearanceColor(npc, hex, ColorField.EYE_2); }

    private enum ColorField { BODY_1, BODY_2, BODY_3, EYE_1, EYE_2 }

    private boolean setAppearanceColor(ICustomNpc npc, String hex, ColorField field) {
        var parsed = NpcCombatProfile.parseHexColor(hex);
        if (parsed.isEmpty()) return false;
        String canonical = NpcCombatProfile.formatHex(parsed.getAsInt());
        return mutateAppearance(npc, p -> {
            switch (field) {
                case BODY_1 -> p.appearance.bodyColor = canonical;
                case BODY_2 -> p.appearance.bodyColor2 = canonical;
                case BODY_3 -> p.appearance.bodyColor3 = canonical;
                case EYE_1 -> p.appearance.eye1Color = canonical;
                case EYE_2 -> p.appearance.eye2Color = canonical;
            }
        });
    }

    /** Like {@link #mutate}, plus the appearance re-sync so clients pick the change up. */
    private boolean mutateAppearance(ICustomNpc npc, ProfileEdit edit) {
        LivingEntity e = require(npc); NpcCombatProfile p = NpcCombatProfile.read(e);
        edit.apply(p); p.write(e);
        NpcAppearanceFx.sync(e);
        return true;
    }

    public List<String> listFormGroups(String race) { return NpcFormLookup.groups(race); }
    public List<String> listForms(String race, String group) { return NpcFormLookup.forms(race, group); }
    public List<String> listStackGroups() { return NpcFormLookup.stackGroups(); }
    public List<String> listStackForms(String group) { return NpcFormLookup.stackForms(group); }

    public boolean selectForm(ICustomNpc npc, String group, String form) {
        LivingEntity entity = require(npc); NpcCombatProfile p = NpcCombatProfile.read(entity);
        if (NpcFormLookup.form(p.raceId, group, form) == null) return false;
        p.selectedFormGroup = safe(group); p.selectedFormId = safe(form);
        NpcFormLookup.grantMastery(p, group, form); p.write(entity); return true;
    }

    public boolean selectStack(ICustomNpc npc, String group, String form) {
        LivingEntity entity = require(npc); NpcCombatProfile p = NpcCombatProfile.read(entity);
        var data = NpcFormLookup.stackForm(group, form); if (data == null) return false;
        p.selectedStackGroup = safe(group); p.selectedStackId = safe(form);
        double max = NpcFormLookup.maxMastery(data);
        if (p.stackMasteries.getMastery(group, form) <= 0.0) p.stackMasteries.setMastery(group, form, max, max);
        p.write(entity); return true;
    }

    public boolean setMastery(ICustomNpc npc, String group, String form, double percent) {
        LivingEntity entity = require(npc); NpcCombatProfile p = NpcCombatProfile.read(entity);
        var data = NpcFormLookup.form(p.raceId, group, form); double max = NpcFormLookup.maxMastery(data);
        p.masteries.setMastery(group, form, max * Math.max(0, Math.min(100, percent)) / 100.0, max); p.write(entity); return true;
    }

    public boolean setStackMastery(ICustomNpc npc, String group, String form, double percent) {
        LivingEntity entity = require(npc); NpcCombatProfile p = NpcCombatProfile.read(entity);
        var data = NpcFormLookup.stackForm(group, form); if (data == null) return false;
        double max = NpcFormLookup.maxMastery(data);
        p.stackMasteries.setMastery(group, form, max * Math.max(0, Math.min(100, percent)) / 100.0, max);
        p.write(entity); return true;
    }

    public boolean addTechnique(ICustomNpc npc, String id) { return mutate(npc, p -> p.addTechnique(id)); }
    public boolean removeTechnique(ICustomNpc npc, String id) { return mutate(npc, p -> p.removeTechnique(id)); }
    public List<String> listTechniques(ICustomNpc npc) { return List.copyOf(NpcCombatProfile.read(require(npc)).techniques); }

    public boolean ascend(ICustomNpc npc, String group, String form, int ticks) { return NpcTransformSystem.start(require(npc), group, form, ticks); }
    public boolean stack(ICustomNpc npc, String group, String form, int ticks) { return NpcTransformSystem.startStack(require(npc), group, form, ticks); }
    public boolean unstack(ICustomNpc npc) { return NpcTransformSystem.unstack(require(npc)); }
    public boolean descend(ICustomNpc npc, int ticks) { return NpcTransformSystem.descend(require(npc)); }
    public boolean descendOne(ICustomNpc npc, int ticks) { return NpcTransformSystem.descendOne(require(npc), ticks); }
    public boolean isTransforming(ICustomNpc npc) { return NpcTransformSystem.isHolding(require(npc).getUUID()); }

    public Map<String, Object> getAuraStyle(ICustomNpc npc, String scope, String group, String form) {
        NpcCombatProfile p = NpcCombatProfile.read(require(npc));
        NpcAuraStyle style = auraStyle(p, scope, group, form, false);
        return styleMap(style == null ? new NpcAuraStyle() : style);
    }

    public boolean setAuraStyle(ICustomNpc npc, String scope, String group, String form,
                                boolean enabled, String primaryColor, String primaryType, int primaryLayer,
                                boolean extraConfigured, boolean extraEnabled, String extraColor,
                                String extraType, int extraLayer, boolean lightningConfigured,
                                boolean lightningEnabled, String lightningColor) {
        LivingEntity entity = require(npc); NpcCombatProfile p = NpcCombatProfile.read(entity);
        NpcAuraStyle style = auraStyle(p, scope, group, form, true); if (style == null) return false;
        style.enabled = enabled;
        style.primaryColor = optionalColor(primaryColor); style.primaryType = safe(primaryType);
        style.primaryLayer = NpcAuraStyle.clampLayer(primaryLayer);
        style.extraConfigured = extraConfigured; style.extraEnabled = extraEnabled;
        style.extraColor = optionalColor(extraColor); style.extraType = safe(extraType);
        style.extraLayer = NpcAuraStyle.clampLayer(extraLayer);
        style.lightningConfigured = lightningConfigured; style.lightningEnabled = lightningEnabled;
        style.lightningColor = optionalColor(lightningColor);
        p.write(entity);
        NpcAuraFx.sync(entity);
        NpcAppearanceFx.sync(entity);
        return true;
    }

    public boolean clearAuraStyle(ICustomNpc npc, String scope, String group, String form) {
        LivingEntity entity = require(npc); NpcCombatProfile p = NpcCombatProfile.read(entity);
        String normalized = safe(scope).toLowerCase(java.util.Locale.ROOT);
        if ("base".equals(normalized)) p.baseAuraStyle = new NpcAuraStyle();
        else {
            String key = NpcCombatProfile.auraKey(group, form); if (key.isBlank()) return false;
            ("stack".equals(normalized) ? p.stackAuraStyles : p.formAuraStyles).remove(key);
        }
        p.write(entity);
        NpcAuraFx.sync(entity);
        NpcAppearanceFx.sync(entity);
        return true;
    }

    public Map<String, Object> getResolvedAura(ICustomNpc npc) {
        NpcAuraResolver.Resolved resolved = NpcAuraResolver.resolve(NpcCombatProfile.read(require(npc)));
        Map<String, Object> out = new LinkedHashMap<>();
        java.util.List<Map<String, Object>> layers = new java.util.ArrayList<>();
        for (NpcAuraResolver.Layer layer : resolved.layers()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", layer.type()); entry.put("layer", layer.index());
            entry.put("color", NpcCombatProfile.formatHex(layer.rgb())); layers.add(entry);
        }
        out.put("layers", layers); out.put("lightning", resolved.lightning());
        out.put("lightningColor", NpcCombatProfile.formatHex(resolved.lightningRgb()));
        out.put("rocks", resolved.rocks()); out.put("sparking", resolved.sparking());
        return out;
    }

    /**
     * Starts the NPC Hakai channel on {@code target}. Last-frame hold until erase,
     * cancel, or the target leaves range. Not a ki blast — {@code fireTechnique}
     * has no Hakai id.
     */
    public boolean startHakai(ICustomNpc npc, IEntity target) {
        return NpcHakai.start(require(npc), living(target));
    }

    public boolean cancelHakai(ICustomNpc npc) {
        return NpcHakai.cancel(require(npc));
    }

    public boolean isHakai(ICustomNpc npc) {
        return NpcHakai.isChanneling(require(npc));
    }

    public boolean fireTechnique(ICustomNpc npc, String id, IEntity target, int durationTicks) {
        LivingEntity caster = require(npc);
        LivingEntity aim = target != null && target.getMCEntity() instanceof LivingEntity living ? living : null;
        return NpcKiAttackDispatcher.fire(id, caster, NpcCombatProfile.read(caster), durationTicks, aim);
    }
    /**
     * Same as the 4-arg {@code fireTechnique}, plus a one-shot colour override for just this
     * attack -- unlike {@link #setKiColor}, this never touches the profile's persistent
     * {@code kiColor}, so it does not recolour the NPC's later attacks.
     */
    public boolean fireTechnique(ICustomNpc npc, String id, IEntity target, int durationTicks, String hex) {
        LivingEntity caster = require(npc);
        LivingEntity aim = target != null && target.getMCEntity() instanceof LivingEntity living ? living : null;
        int colorOverride = NpcCombatProfile.parseHexColor(hex).orElse(0);
        return NpcKiAttackDispatcher.fire(id, caster, NpcCombatProfile.read(caster), durationTicks, aim, colorOverride);
    }
    public int getTechniqueCooldown(ICustomNpc npc, String id) { return NpcKiCooldowns.remaining(require(npc), id); }
    public boolean isTechniqueReady(ICustomNpc npc, String id) { return NpcKiCooldowns.ready(require(npc), id); }
    public void clearTechniqueCooldown(ICustomNpc npc, String id) { NpcKiCooldowns.clear(require(npc), id); }

    public double getCurrentEnergy(ICustomNpc npc) { LivingEntity e = require(npc); return NpcResources.get(e, NpcCombatProfile.read(e)).energy(); }
    public double getMaxEnergy(ICustomNpc npc) { LivingEntity e = require(npc); return NpcResources.get(e, NpcCombatProfile.read(e)).maxEnergy(); }
    public double getCurrentStamina(ICustomNpc npc) { LivingEntity e = require(npc); return NpcResources.get(e, NpcCombatProfile.read(e)).stamina(); }
    public double getMaxStamina(ICustomNpc npc) { LivingEntity e = require(npc); return NpcResources.get(e, NpcCombatProfile.read(e)).maxStamina(); }
    public boolean setCurrentEnergy(ICustomNpc npc, double value) { LivingEntity e = require(npc); NpcResources.setEnergy(e, NpcCombatProfile.read(e), value); return true; }
    public boolean setCurrentStamina(ICustomNpc npc, double value) { LivingEntity e = require(npc); NpcResources.setStamina(e, NpcCombatProfile.read(e), value); return true; }

    public Map<String, Object> getHair(ICustomNpc npc) {
        NpcCombatProfile profile = NpcCombatProfile.read(require(npc));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", profile.hairEnabled);
        out.put("code", profile.hairCode == null ? "" : profile.hairCode);
        out.put("color", profile.hairColor == null ? "" : profile.hairColor);
        out.put("style", profile.hairStyleId);
        out.put("styleCount", NpcHairBridge.presetCount());
        return out;
    }
    public boolean setHairEnabled(ICustomNpc npc, boolean enabled) { return NpcHairBridge.setEnabled(require(npc), enabled) == NpcHairBridge.Result.OK; }
    public boolean setHairCode(ICustomNpc npc, String code) { return NpcHairBridge.setCode(require(npc), code) == NpcHairBridge.Result.OK; }

    /**
     * Picks a built-in DragonMineZ hair style, or {@code 0} to fall back to the NPC's hair code.
     *
     * <p>Rejects an out-of-range id rather than silently storing it, because a bad style would
     * otherwise just render as the code with no indication why.
     */
    public boolean setHairStyle(ICustomNpc npc, int styleId) {
        return NpcHairBridge.setStyle(require(npc), styleId) == NpcHairBridge.Result.OK;
    }

    /** How many built-in hair styles are available, for a script that wants to cycle them. */
    public int getHairStyleCount() { return NpcHairBridge.presetCount(); }
    public boolean setHairColor(ICustomNpc npc, String color) { return NpcHairBridge.setColor(require(npc), color) == NpcHairBridge.Result.OK; }

    /** Sets the skin by player name (empty name clears it) and re-syncs clients. */
    public boolean setSkinPlayer(ICustomNpc npc, String name) {
        LivingEntity entity = require(npc); NpcCombatProfile p = NpcCombatProfile.read(entity);
        p.skinPlayer = safe(name); p.write(entity);
        NpcDisplayApply.applySkin(entity, p.skinPlayer, p.skinUrl);
        NpcAppearanceFx.sync(entity);
        return true;
    }

    /** Sets the skin by URL (empty URL clears it) and re-syncs clients. */
    public boolean setSkinUrl(ICustomNpc npc, String url) {
        LivingEntity entity = require(npc); NpcCombatProfile p = NpcCombatProfile.read(entity);
        p.skinUrl = safe(url); p.write(entity);
        NpcDisplayApply.applySkin(entity, p.skinPlayer, p.skinUrl);
        NpcAppearanceFx.sync(entity);
        return true;
    }

    /** Switches the CNPC display model to/from the player model; false when CNPC has no such toggle. */
    public boolean setPlayerModel(ICustomNpc npc, boolean playerModel) {
        return NpcDisplayApply.setPlayerModel(require(npc), playerModel);
    }

    /** Copies an online player's armor onto this NPC; false when that player is not online. */
    public boolean copyPlayerClothing(ICustomNpc npc, String playerName) {
        LivingEntity entity = require(npc);
        if (playerName == null || playerName.isBlank()
                || !(entity.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        ServerPlayer source = serverLevel.getServer().getPlayerList().getPlayerByName(playerName);
        if (source == null) {
            return false;
        }
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack item = source.getItemBySlot(slot);
            entity.setItemSlot(slot, item.isEmpty() ? ItemStack.EMPTY : item.copy());
        }
        return true;
    }

    private interface ProfileEdit { void apply(NpcCombatProfile profile); }
    private boolean mutate(ICustomNpc npc, ProfileEdit edit) { LivingEntity e = require(npc); NpcCombatProfile p = NpcCombatProfile.read(e); edit.apply(p); p.write(e); return true; }
    /** Like {@link #mutate}, plus the aura/appearance re-sync plain NBT writes never trigger. */
    private boolean mutateAura(ICustomNpc npc, ProfileEdit edit) { LivingEntity e = require(npc); NpcCombatProfile p = NpcCombatProfile.read(e); edit.apply(p); p.write(e); NpcAuraFx.sync(e); NpcAppearanceFx.sync(e); return true; }
    /**
     * Like {@link #mutate}, plus the native-navigator push and the appearance re-sync: the
     * fly flag drives both CustomNPCs' movement and the client-side DMZ flight pose, and
     * neither reacts to a bare NBT write.
     */
    private boolean mutateFlight(ICustomNpc npc, ProfileEdit edit) {
        LivingEntity e = require(npc); NpcCombatProfile p = NpcCombatProfile.read(e);
        edit.apply(p); p.write(e);
        NpcFlightBridge.force(e, p);
        NpcAppearanceFx.sync(e);
        return true;
    }
    private boolean color(ICustomNpc npc, String value, boolean aura) {
        var parsed = NpcCombatProfile.parseHexColor(value);
        if (parsed.isEmpty()) return false;
        ProfileEdit edit = p -> { if (aura) p.setAuraColor(NpcCombatProfile.formatHex(parsed.getAsInt())); else p.kiColor = parsed.getAsInt(); };
        return aura ? mutateAura(npc, edit) : mutate(npc, edit);
    }
    private static NpcAuraStyle auraStyle(NpcCombatProfile p, String scope, String group, String form, boolean create) {
        String normalized = safe(scope).toLowerCase(java.util.Locale.ROOT);
        if ("base".equals(normalized)) return p.baseAuraStyle;
        if (!"form".equals(normalized) && !"stack".equals(normalized)) return null;
        return p.auraStyle("stack".equals(normalized), group, form, create);
    }
    private static Map<String, Object> styleMap(NpcAuraStyle style) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", style.enabled); out.put("primaryColor", style.primaryColor);
        out.put("primaryType", style.primaryType); out.put("primaryLayer", style.primaryLayer);
        out.put("extraConfigured", style.extraConfigured); out.put("extraEnabled", style.extraEnabled);
        out.put("extraColor", style.extraColor); out.put("extraType", style.extraType);
        out.put("extraLayer", style.extraLayer); out.put("lightningConfigured", style.lightningConfigured);
        out.put("lightningEnabled", style.lightningEnabled); out.put("lightningColor", style.lightningColor);
        return out;
    }
    private static String optionalColor(String value) {
        if (value == null || value.isBlank()) return "";
        return NpcCombatProfile.parseHexColor(value).isPresent()
                ? NpcCombatProfile.formatHex(NpcCombatProfile.parseHexColor(value).getAsInt()) : "";
    }
    /** Like {@link #mutate}, plus the appearance re-sync so the client's skill view follows. */
    private boolean mutateSkills(ICustomNpc npc, String id, ProfileEdit edit) {
        LivingEntity e = require(npc); NpcCombatProfile p = NpcCombatProfile.read(e);
        edit.apply(p);
        // Fly also drives CustomNPCs' navigator, so keep the dedicated fields in step with the
        // map entry -- NpcFlightBridge and NpcFullDmzRenderer both read those.
        if (NpcSkillSet.FLY.equals(NpcSkillSet.canonical(id))) {
            p.flySkillOn = p.skills.isActive(NpcSkillSet.FLY);
            p.flySkillLevel = NpcCombatProfile.clampFlySkillLevel(
                    Math.max(1, p.skills.level(NpcSkillSet.FLY)));
        }
        p.write(e);
        NpcFlightBridge.force(e, p);
        NpcAppearanceFx.sync(e);
        return true;
    }

    private static LivingEntity living(IEntity entity) {
        return entity != null && entity.getMCEntity() instanceof LivingEntity l ? l : null;
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static LivingEntity require(ICustomNpc npc) { LivingEntity e = entity(npc); if (e == null) throw new IllegalArgumentException("XenoPixels requires a live CustomNPC entity"); return e; }
    private static LivingEntity entity(ICustomNpc npc) { return npc == null ? null : npc.getMCEntity(); }
}

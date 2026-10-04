package net.bullettrain.xenopixelsmod.npc.script.api;

import net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi;
import net.bullettrain.xenopixelsmod.compat.npc.*;
import net.bullettrain.xenopixelsmod.command.XenoPointsCommands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Script-call counterpart to the MyNPCs XenoPixels global, backed by the same Xeno services. */
public final class NativeXenoScriptApi {
    public static final NativeXenoScriptApi INSTANCE = new NativeXenoScriptApi();
    private NativeXenoScriptApi() {}

    private static LivingEntity entity(ScriptEntity e) {
        if (e == null || e.unwrap() == null) throw new IllegalArgumentException("XenoPixels requires a live entity");
        return e.unwrap();
    }
    private static LivingEntity maybe(ScriptEntity e) { return e == null ? null : e.unwrap(); }
    private static boolean edit(ScriptNpc n, Consumer<NpcCombatProfile> action) {
        LivingEntity e = entity(n);
        NpcCombatProfile p = NpcCombatProfile.read(e);
        action.accept(p);
        p.write(e);
        return true;
    }
    private static boolean appearance(ScriptNpc n, Consumer<NpcCombatProfile> action) {
        boolean result = edit(n, action);
        NpcAuraFx.sync(entity(n));
        NpcAppearanceFx.sync(entity(n));
        return result;
    }
    private static boolean color(ScriptNpc n, String hex, boolean aura) {
        var parsed = NpcCombatProfile.parseHexColor(hex);
        if (parsed.isEmpty()) return false;
        return aura ? appearance(n, p -> p.setAuraColor(NpcCombatProfile.formatHex(parsed.getAsInt())))
                : edit(n, p -> p.kiColor = parsed.getAsInt());
    }

    public String getVersion() { return "28"; }
    public boolean say(ScriptNpc n, String message) { return n != null && n.say(message); }
    public boolean say(ScriptNpc n, String message, String palette) { return n != null && n.say(message, palette); }
    public boolean say(ScriptNpc n, String message, String palette, String shape) { return n != null && n.say(message, palette, shape); }
    /**
     * A speech bubble over any entity, players included; everyone nearby sees it. say(player, ...)
     * stays the private chat line. Takes a wrapper or a XenoAPI entity, so event.player works in
     * typed hooks (died, levelUp...) too. False for anything that is not a living entity.
     */
    public boolean bubble(Object e, String message) { return bubble(e, message, "", ""); }
    public boolean bubble(Object e, String message, String palette) { return bubble(e, message, palette, ""); }
    public boolean bubble(Object e, String message, String palette, String shape) {
        ScriptEntity target = e instanceof ScriptEntity se ? se
                : e instanceof xenoapi.npcs.api.entity.IEntity<?> api
                        && net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoApiAdapters.unwrap(api) instanceof LivingEntity living
                        ? ScriptEntity.of(living) : null;
        return target != null && target.say(message, palette, shape);
    }
    public boolean say(ScriptPlayer p, String message) { return p != null && NpcScriptSay.tell((ServerPlayer) entity(p), message); }
    public boolean broadcast(ScriptPlayer p, String message) { return p != null && NpcScriptSay.broadcastLater((ServerPlayer) entity(p), message); }
    public boolean addDmzPoints(ScriptPlayer p, int amount) { return p != null && amount > 0 && XenoPointsCommands.addPoints((ServerPlayer) entity(p), amount); }
    public String getChatMessage(ScriptEvent event) { return event == null ? "" : event.message; }
    public boolean setChatMessage(ScriptEvent event, String message) {
        if (event == null || message == null || message.length() > 256) return false;
        event.message = message;
        return true;
    }
    public boolean cancelChat(ScriptEvent event) { if (event == null) return false; event.setCanceled(true); return true; }
    public boolean isChatCancelled(ScriptEvent event) { return event != null && event.isCanceled(); }

    public boolean hasProfile(ScriptNpc n) { return NpcCombatProfile.hasProfile(entity(n)); }
    public boolean isAuthoritative(ScriptNpc n) { return NpcCombatProfile.read(entity(n)).authoritative; }
    public boolean isKnockable(ScriptNpc n) { return NpcCombatProfile.read(entity(n)).knockable; }
    public boolean setKnockable(ScriptNpc n, boolean on) { return edit(n, p -> p.knockable = on); }
    public boolean isPunchable(ScriptNpc n) { return NpcCombatProfile.read(entity(n)).punchable; }
    public boolean setPunchable(ScriptNpc n, boolean on) { return edit(n, p -> p.punchable = on); }
    public boolean reapplyStats(ScriptNpc n) {
        LivingEntity e = entity(n);
        NpcCounterpartSync.force(e, NpcCombatProfile.read(e));
        return true;
    }
    public net.minecraft.nbt.CompoundTag getDmzStatSnapshot(ScriptNpc n) {
        return NpcCombatProfile.read(entity(n)).dmzStatSnapshot();
    }
    public boolean setStat(ScriptNpc n, String stat, int value) {
        String key = stat == null ? "" : stat.trim().toLowerCase(java.util.Locale.ROOT);
        int v = Math.max(0, value);
        if (!java.util.Set.of("strength", "strikepower", "strike_power", "resistance", "vitality",
                "kipower", "ki_power", "energy").contains(key)) return false;
        return edit(n, p -> {
            switch (key) {
                case "strength" -> p.strength = v;
                case "strikepower", "strike_power" -> p.strikePower = v;
                case "resistance" -> p.resistance = v;
                case "vitality" -> p.vitality = v;
                case "kipower", "ki_power" -> p.kiPower = v;
                case "energy" -> p.energy = v;
            }
        });
    }
    public Map<String, Object> getProfile(ScriptNpc n) {
        NpcCombatProfile p = NpcCombatProfile.read(entity(n));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("race", p.raceId); out.put("strength", p.strength);
        out.put("strikePower", p.strikePower); out.put("resistance", p.resistance);
        out.put("vitality", p.vitality); out.put("kiPower", p.kiPower); out.put("energy", p.energy);
        out.put("formGroup", p.formGroup); out.put("form", p.formId);
        out.put("selectedFormGroup", p.selectedFormGroup); out.put("selectedForm", p.selectedFormId);
        out.put("stackGroup", p.stackGroup); out.put("stack", p.stackId);
        out.put("selectedStackGroup", p.selectedStackGroup); out.put("selectedStack", p.selectedStackId);
        out.put("auraOn", p.auraOn); out.put("auraColor", p.auraColorHex);
        out.put("auraScale", p.auraScale); out.put("kiChargePercent", p.kiChargePercent);
        out.put("halo", p.haloOn); out.put("auraRocks", p.auraRocks);
        out.put("auraSparking", p.auraSparking); out.put("auraLightning", p.auraLightning);
        out.put("auraGroundRing", p.auraGroundRing);
        out.put("aggroMultiplier", p.aggroMultiplier); out.put("aimAccuracy", p.aimAccuracy);
        out.put("combatBrain", p.combatBrain); out.put("brainVersion", p.brainVersion.label());
        out.put("brain", p.brainFlagMap());
        Map<String, Object> skillMap = new LinkedHashMap<>();
        for (var entry : p.skills.entries().entrySet()) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("on", entry.getValue().active()); one.put("level", entry.getValue().level());
            skillMap.put(entry.getKey(), one);
        }
        out.put("skills", skillMap);
        out.put("flySkill", p.flySkillOn); out.put("flySkillLevel", p.flySkillLevel);
        out.put("kiWeaponOn", p.kiWeaponOn); out.put("kiWeaponType", p.kiWeaponType);
        out.put("saiyanTail", p.appearance.saiyanTail);
        out.put("tailColor", p.appearance.tailColor);
        out.put("tailUseRaceColor", p.appearance.tailUseRaceColor);
        out.put("currentEnergy", NpcResources.get(entity(n), p).energy());
        out.put("maxEnergy", NpcResources.get(entity(n), p).maxEnergy());
        out.put("currentStamina", NpcResources.get(entity(n), p).stamina());
        out.put("maxStamina", NpcResources.get(entity(n), p).maxStamina());
        out.put("techniques", java.util.List.copyOf(p.techniques));
        out.put("hair", getHair(n));
        return out;
    }
    public boolean setProfile(ScriptNpc n, String race, int strength, int strikePower, int resistance,
                              int vitality, int kiPower, int energy) {
        return edit(n, p -> {
            p.raceId = race == null || race.isBlank() ? "human" : race;
            p.strength = Math.max(0, strength); p.strikePower = Math.max(0, strikePower);
            p.resistance = Math.max(0, resistance); p.vitality = Math.max(0, vitality);
            p.kiPower = Math.max(0, kiPower); p.energy = Math.max(0, energy);
        });
    }
    public boolean setPowerRelease(ScriptNpc n, int percent) {
        int cap = Math.max(100, net.bullettrain.xenopixelsmod.config.XenoServerConfig.sparkingReleaseLimit);
        return edit(n, p -> p.powerReleasePercent = Math.max(1, Math.min(cap, percent)));
    }
    public boolean setAuthoritative(ScriptNpc n, boolean on) { return edit(n, p -> p.authoritative = on); }
    public boolean setCombatBrain(ScriptNpc n, boolean on) { return edit(n, p -> p.combatBrain = on); }
    public boolean setBrainVersion(ScriptNpc n, String version) {
        return edit(n, p -> p.setBrainVersion(NpcCombatBrainVersion.byLegacyName(version)));
    }
    public String getBrainVersion(ScriptNpc n) { return NpcCombatProfile.read(entity(n)).brainVersion.label(); }
    public boolean setBrainFlag(ScriptNpc n, String flag, boolean on) { return edit(n, p -> p.setBrainFlag(flag, on)); }
    public boolean getBrainFlag(ScriptNpc n, String flag) { return NpcCombatProfile.read(entity(n)).brainFlag(flag); }
    public boolean setBrainChance(ScriptNpc n, String flag, int chance) { return edit(n, p -> p.setBrainChance(flag, chance)); }
    public int getBrainChance(ScriptNpc n, String flag) { return NpcCombatProfile.read(entity(n)).brainChance(flag); }
    public boolean setBrainModifier(ScriptNpc n, String flag, double value) { return edit(n, p -> p.setBrainModifier(flag, (float) value)); }
    public double getBrainModifier(ScriptNpc n, String flag) { return NpcCombatProfile.read(entity(n)).brainModifier(flag); }
    public boolean setBrainSpecialCooldown(ScriptNpc n, int ticks) {
        return edit(n, p -> p.brainSpecialCooldown = NpcCombatProfile.clampSpecialCooldown(ticks));
    }
    public boolean setAggroMultiplier(ScriptNpc n, float multiplier) {
        boolean result = edit(n, p -> p.aggroMultiplier = NpcCombatProfile.clampAggroMultiplier(multiplier));
        LivingEntity e = entity(n); NpcAggroBridge.forget(e.getUUID());
        NpcAggroBridge.apply(e, NpcCombatProfile.read(e)); return result;
    }
    public boolean setKiCharge(ScriptNpc n, int percent) { return edit(n, p -> p.kiChargePercent = Math.max(1, Math.min(1000, percent))); }
    public boolean setAura(ScriptNpc n, boolean on) { return appearance(n, p -> p.auraOn = on); }
    public boolean setAuraColor(ScriptNpc n, String hex) { return color(n, hex, true); }
    public boolean setBaseAuraColor(ScriptNpc n, String hex) { return color(n, hex, true); }
    public boolean setKiColor(ScriptNpc n, String hex) { return color(n, hex, false); }
    public boolean setAuraScale(ScriptNpc n, float scale) { return appearance(n, p -> p.auraScale = NpcCombatProfile.clampAuraScale(scale)); }
    public boolean setHalo(ScriptNpc n, boolean on) { return edit(n, p -> p.haloOn = on); }
    public boolean setAuraRocks(ScriptNpc n, boolean on) { return appearance(n, p -> p.auraRocks = on); }
    public boolean setAuraSparking(ScriptNpc n, boolean on) { return appearance(n, p -> p.auraSparking = on); }
    public boolean setAuraLightning(ScriptNpc n, boolean on) { return appearance(n, p -> p.auraLightning = on); }
    public boolean setAuraGroundRing(ScriptNpc n, boolean on) { return appearance(n, p -> p.auraGroundRing = on); }
    public boolean setFlySkill(ScriptNpc n, boolean on) {
        boolean result = edit(n, p -> p.flySkillOn = on);
        LivingEntity e = entity(n); NpcFlightBridge.force(e, NpcCombatProfile.read(e));
        NpcAppearanceFx.sync(e); return result;
    }
    public boolean setFlySkillLevel(ScriptNpc n, int level) {
        boolean result = edit(n, p -> p.flySkillLevel = NpcCombatProfile.clampFlySkillLevel(level));
        LivingEntity e = entity(n); NpcFlightBridge.force(e, NpcCombatProfile.read(e));
        NpcAppearanceFx.sync(e); return result;
    }
    public boolean isFlySkillOn(ScriptNpc n) { return NpcCombatProfile.read(entity(n)).flySkillOn; }
    public int getFlySkillLevel(ScriptNpc n) { return NpcCombatProfile.read(entity(n)).flySkillLevel; }
    public boolean setAppearanceMode(ScriptNpc n, String mode) {
        return appearance(n, p -> p.appearance.mode = NpcDmzAppearance.Mode.parse(mode));
    }
    public String getAppearanceMode(ScriptNpc n) { return NpcCombatProfile.read(entity(n)).appearance.mode.name(); }
    public Map<String, Object> getAppearanceColors(ScriptNpc n) {
        var a = NpcCombatProfile.read(entity(n)).appearance;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("bodyColor", a.bodyColor); out.put("bodyColor2", a.bodyColor2);
        out.put("bodyColor3", a.bodyColor3); out.put("eyeColor1", a.eye1Color);
        out.put("eyeColor2", a.eye2Color); return out;
    }
    private static boolean appearanceColor(ScriptNpc n, String hex, int field) {
        var parsed = NpcCombatProfile.parseHexColor(hex);
        if (parsed.isEmpty()) return false;
        String color = NpcCombatProfile.formatHex(parsed.getAsInt());
        return appearance(n, p -> {
            switch (field) {
                case 0 -> p.appearance.bodyColor = color;
                case 1 -> p.appearance.bodyColor2 = color;
                case 2 -> p.appearance.bodyColor3 = color;
                case 3 -> p.appearance.eye1Color = color;
                case 4 -> p.appearance.eye2Color = color;
            }
        });
    }
    public boolean setBodyColor(ScriptNpc n, String hex) { return appearanceColor(n, hex, 0); }
    public boolean setBodyColor2(ScriptNpc n, String hex) { return appearanceColor(n, hex, 1); }
    public boolean setBodyColor3(ScriptNpc n, String hex) { return appearanceColor(n, hex, 2); }
    public boolean setEyeColor1(ScriptNpc n, String hex) { return appearanceColor(n, hex, 3); }
    public boolean setEyeColor2(ScriptNpc n, String hex) { return appearanceColor(n, hex, 4); }
    /** On a native Xeno NPC, this selects its full DragonMineZ player-style renderer. */
    public boolean setPlayerModel(ScriptNpc n, boolean on) {
        return appearance(n, p -> p.appearance.mode = on
                ? NpcDmzAppearance.Mode.FULL : NpcDmzAppearance.Mode.OFF);
    }
    public java.util.List<String> listSkills() { return NpcSkillSet.knownIds(); }
    public int getSkillMaxLevel(String id) { return NpcSkillSet.maxLevelOf(id); }
    private static boolean skill(ScriptNpc n, String id, Consumer<NpcCombatProfile> action) {
        if (!NpcSkillSet.isKnown(id)) return false;
        boolean result = edit(n, p -> {
            action.accept(p);
            if (NpcSkillSet.FLY.equals(NpcSkillSet.canonical(id))) {
                p.flySkillOn = p.skills.isActive(NpcSkillSet.FLY);
                p.flySkillLevel = NpcCombatProfile.clampFlySkillLevel(
                        Math.max(1, p.skills.level(NpcSkillSet.FLY)));
            }
        });
        LivingEntity e = entity(n);
        NpcFlightBridge.force(e, NpcCombatProfile.read(e));
        NpcAppearanceFx.sync(e);
        return result;
    }
    public boolean setSkill(ScriptNpc n, String id, boolean on) { return skill(n, id, p -> p.skills.setActive(id, on)); }
    public boolean setSkillLevel(ScriptNpc n, String id, int level) { return skill(n, id, p -> p.skills.setLevel(id, level)); }
    public boolean setSkill(ScriptNpc n, String id, boolean on, int level) { return skill(n, id, p -> p.skills.set(id, on, level)); }
    public boolean isSkillOn(ScriptNpc n, String id) { return NpcCombatProfile.read(entity(n)).skills.isActive(id); }
    public int getSkillLevel(ScriptNpc n, String id) { return NpcCombatProfile.read(entity(n)).skills.level(id); }
    public boolean removeSkill(ScriptNpc n, String id) { return skill(n, id, p -> p.skills.remove(id)); }
    public boolean clearSkills(ScriptNpc n) { return skill(n, NpcSkillSet.FLY, p -> p.skills.clear()); }
    // Ki Sense: the skill itself, the editor's "lock on to whoever hit me" flag, and the live lock.
    public boolean setKiSense(ScriptNpc n, boolean on) { return setSkill(n, NpcSkillSet.KI_SENSE, on); }
    public boolean isKiSenseOn(ScriptNpc n) { return isSkillOn(n, NpcSkillSet.KI_SENSE); }
    public boolean setKiSenseLockOn(ScriptNpc n, boolean on) { return edit(n, p -> p.kiSenseLockOnRetaliator = on); }
    public boolean isKiSenseLockOn(ScriptNpc n) { return NpcCombatProfile.read(entity(n)).kiSenseLockOnRetaliator; }
    public boolean isKiSenseLockedOn(ScriptNpc n, ScriptEntity target) {
        return NpcTargetKeeper.isKiSenseLockedOn(entity(n), maybe(target));
    }

    // Generic access to every saved setting - each DMZ tab and every other editor tab - by its tag
    // key, e.g. setDmz(npc, "AuraLightning", true) or setDmz(npc, "DmzAppearance.SaiyanTail", true).
    // listDmz(npc) prints the keys. Values keep their stored type; the profile's clamps still apply.
    public java.util.List<String> listDmz(ScriptNpc n) {
        return NpcProfileKeys.keys(NpcCombatProfile.read(entity(n)).toTag());
    }
    public Object getDmz(ScriptNpc n, String key) {
        return NpcProfileKeys.get(NpcCombatProfile.read(entity(n)).toTag(), key);
    }
    public boolean setDmz(ScriptNpc n, String key, Object value) {
        return editKey(n, tag -> NpcProfileKeys.set(tag, key, value));
    }
    public boolean toggleDmz(ScriptNpc n, String key) {
        return editKey(n, tag -> NpcProfileKeys.toggle(tag, key));
    }
    // The same calls under the NPC-wide names: the profile tag holds every editor tab.
    public java.util.List<String> listNpcSettings(ScriptNpc n) { return listDmz(n); }
    public Object getNpcSetting(ScriptNpc n, String key) { return getDmz(n, key); }
    public boolean setNpcSetting(ScriptNpc n, String key, Object value) { return setDmz(n, key, value); }
    public boolean toggleNpcSetting(ScriptNpc n, String key) { return toggleDmz(n, key); }
    private static boolean editKey(ScriptNpc n, java.util.function.Predicate<net.minecraft.nbt.CompoundTag> change) {
        LivingEntity e = entity(n);
        net.minecraft.nbt.CompoundTag tag = NpcCombatProfile.read(e).toTag();
        if (!change.test(tag)) return false;
        NpcCombatProfile updated = NpcCombatProfile.fromTag(tag);
        updated.write(e);
        NpcAuraFx.setActive(e, updated.auraOn);
        if (e instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity npc) {
            net.bullettrain.xenopixelsmod.npc.XenoNpcBehaviour.apply(npc, updated);
            npc.npcData().markEdited();
        }
        return true;
    }

    // Native NPC identity and world settings that live outside the profile (Xeno NPCs only).
    private static net.bullettrain.xenopixelsmod.npc.XenoNpcData data(ScriptNpc n) {
        return entity(n) instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity npc ? npc.npcData() : null;
    }
    private static boolean dataEdit(ScriptNpc n, Consumer<net.bullettrain.xenopixelsmod.npc.XenoNpcData> action) {
        var d = data(n);
        if (d == null) return false;
        action.accept(d);
        d.markEdited();
        if (entity(n) instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity npc) {
            npc.refreshNameplate();
        }
        return true;
    }
    public boolean setRole(ScriptNpc n, String role) {
        return dataEdit(n, d -> d.setRole(net.bullettrain.xenopixelsmod.npc.XenoNpcRole.byId(role)));
    }
    public String getRole(ScriptNpc n) { var d = data(n); return d == null ? "" : d.role().name().toLowerCase(java.util.Locale.ROOT); }
    public boolean setDisplayName(ScriptNpc n, String name) { return dataEdit(n, d -> d.setDisplayName(name)); }
    public boolean setTitle(ScriptNpc n, String title) { return dataEdit(n, d -> d.setTitle(title)); }
    public boolean setFaction(ScriptNpc n, String faction) { return dataEdit(n, d -> d.setFaction(faction)); }
    public boolean setHome(ScriptNpc n, double x, double y, double z) { return dataEdit(n, d -> d.setHome(x, y, z)); }
    public boolean setLeashRadius(ScriptNpc n, double radius) { return dataEdit(n, d -> d.setLeashRadius(radius)); }
    public boolean setRespawn(ScriptNpc n, boolean on) { return dataEdit(n, d -> d.setRespawnEnabled(on)); }
    public boolean setRespawnDelay(ScriptNpc n, int ticks) { return dataEdit(n, d -> d.setRespawnDelayTicks(ticks)); }
    public boolean setHairEnabled(ScriptNpc n, boolean on) { return NpcHairBridge.setEnabled(entity(n), on) == NpcHairBridge.Result.OK; }
    public boolean setHairCode(ScriptNpc n, String code) { return NpcHairBridge.setCode(entity(n), code) == NpcHairBridge.Result.OK; }
    public boolean setHairColor(ScriptNpc n, String hex) { return NpcHairBridge.setColor(entity(n), hex) == NpcHairBridge.Result.OK; }
    public boolean setHairStyle(ScriptNpc n, int style) { return NpcHairBridge.setStyle(entity(n), style) == NpcHairBridge.Result.OK; }
    public int getHairStyleCount() { return NpcHairBridge.presetCount(); }
    public boolean setKiWeaponOn(ScriptNpc n, boolean on) { return edit(n, p -> p.kiWeaponOn = on); }
    public boolean setKiWeaponType(ScriptNpc n, String type) {
        if (!NpcCombatProfile.isKiWeaponType(type)) return false;
        return edit(n, p -> p.kiWeaponType = NpcCombatProfile.canonicalKiWeaponType(type));
    }
    public boolean setSaiyanTail(ScriptNpc n, boolean on) { return appearance(n, p -> p.appearance.saiyanTail = on); }
    public boolean setTailColor(ScriptNpc n, String hex) {
        if (hex == null || hex.isBlank()) return clearTailColor(n);
        var parsed = NpcCombatProfile.parseHexColor(hex);
        if (parsed.isEmpty()) return false;
        return appearance(n, p -> { p.appearance.tailColor = NpcCombatProfile.formatHex(parsed.getAsInt());
            p.appearance.tailUseRaceColor = false; });
    }
    public boolean clearTailColor(ScriptNpc n) { return appearance(n, p -> p.appearance.tailUseRaceColor = true); }
    public boolean setSkinPlayer(ScriptNpc n, String name) {
        return appearance(n, p -> p.skinPlayer = name == null ? "" : name.trim());
    }
    public boolean copyPlayerClothing(ScriptNpc n, String name) {
        LivingEntity npc = entity(n);
        if (name == null || name.isBlank()
                || !(npc.level() instanceof net.minecraft.server.level.ServerLevel level)) return false;
        ServerPlayer source = level.getServer().getPlayerList().getPlayerByName(name);
        if (source == null) return false;
        for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[] {
                net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.FEET }) {
            var item = source.getItemBySlot(slot);
            npc.setItemSlot(slot, item.isEmpty() ? net.minecraft.world.item.ItemStack.EMPTY : item.copy());
        }
        return true;
    }
    public boolean teleport(ScriptNpc n, double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
        entity(n).teleportTo(x, y, z);
        NpcAppearanceFx.sync(entity(n));
        return true;
    }
    public boolean teleportToEntity(ScriptNpc n, ScriptEntity target) {
        LivingEntity e = maybe(target);
        return e != null && teleport(n, e.getX(), e.getY(), e.getZ());
    }
    public boolean teleportToPlayer(ScriptNpc n, ScriptPlayer target) { return teleportToEntity(n, target); }
    private static net.minecraft.sounds.SoundEvent sound(String id) {
        var key = net.minecraft.resources.ResourceLocation.tryParse(id == null ? "" : id.trim());
        return key == null ? null : net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(key);
    }
    public boolean playSound(ScriptNpc n, String id, float volume, float pitch) {
        LivingEntity e = entity(n);
        return playSoundAt(n, e.getX(), e.getY(), e.getZ(), id, volume, pitch);
    }
    public boolean playSoundAt(ScriptNpc n, double x, double y, double z,
                               String id, float volume, float pitch) {
        if (!(entity(n).level() instanceof net.minecraft.server.level.ServerLevel level)
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
        var sound = sound(id);
        if (sound == null) return false;
        level.playSound(null, x, y, z, sound, net.minecraft.sounds.SoundSource.HOSTILE,
                Math.max(0, volume), pitch);
        return true;
    }
    public boolean playSoundFor(ScriptPlayer player, String id, float volume, float pitch) {
        if (player == null) return false;
        var sound = sound(id);
        if (sound == null) return false;
        ((ServerPlayer) entity(player)).playNotifySound(sound,
                net.minecraft.sounds.SoundSource.HOSTILE, Math.max(0, volume), pitch);
        return true;
    }
    public Map<String, Object> getHair(ScriptNpc n) {
        NpcCombatProfile p = NpcCombatProfile.read(entity(n));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", p.hairEnabled);
        out.put("code", p.hairCode);
        out.put("color", p.hairColor);
        out.put("style", p.hairStyleId);
        out.put("styleCount", NpcHairBridge.presetCount());
        return out;
    }
    public boolean addTechnique(ScriptNpc n, String id) { return edit(n, p -> p.addTechnique(id)); }
    public boolean removeTechnique(ScriptNpc n, String id) { return edit(n, p -> p.removeTechnique(id)); }
    public java.util.List<String> listTechniques(ScriptNpc n) { return java.util.List.copyOf(NpcCombatProfile.read(entity(n)).techniques); }
    public boolean setMastery(ScriptNpc n, String group, String form, double percent) {
        LivingEntity e = entity(n); NpcCombatProfile p = NpcCombatProfile.read(e);
        double max = NpcFormLookup.maxMastery(NpcFormLookup.form(p.raceId, group, form));
        p.masteries.setMastery(group, form, max * Math.max(0, Math.min(100, percent)) / 100.0, max);
        p.write(e); return true;
    }
    public java.util.List<String> listFormGroups(String race) { return NpcFormLookup.groups(race); }
    public java.util.List<String> listForms(String race, String group) { return NpcFormLookup.forms(race, group); }
    public java.util.List<String> listStackGroups() { return NpcFormLookup.stackGroups(); }
    public java.util.List<String> listStackForms(String group) { return NpcFormLookup.stackForms(group); }
    public boolean selectForm(ScriptNpc n, String group, String form) {
        LivingEntity e = entity(n); NpcCombatProfile p = NpcCombatProfile.read(e);
        if (NpcFormLookup.form(p.raceId, group, form) == null) return false;
        p.selectedFormGroup = group; p.selectedFormId = form;
        NpcFormLookup.grantMastery(p, group, form); p.write(e); return true;
    }
    public boolean selectStack(ScriptNpc n, String group, String form) {
        LivingEntity e = entity(n); NpcCombatProfile p = NpcCombatProfile.read(e);
        var data = NpcFormLookup.stackForm(group, form);
        if (data == null) return false;
        p.selectedStackGroup = group; p.selectedStackId = form;
        double max = NpcFormLookup.maxMastery(data);
        if (p.stackMasteries.getMastery(group, form) <= 0)
            p.stackMasteries.setMastery(group, form, max, max);
        p.write(e); return true;
    }
    public boolean setStackMastery(ScriptNpc n, String group, String form, double percent) {
        LivingEntity e = entity(n); NpcCombatProfile p = NpcCombatProfile.read(e);
        var data = NpcFormLookup.stackForm(group, form);
        if (data == null) return false;
        double max = NpcFormLookup.maxMastery(data);
        p.stackMasteries.setMastery(group, form, max * Math.max(0, Math.min(100, percent)) / 100.0, max);
        p.write(e); return true;
    }
    public boolean ascend(ScriptNpc n, String group, String form, int ticks) { return NpcTransformSystem.start(entity(n), group, form, ticks); }
    public boolean stack(ScriptNpc n, String group, String form, int ticks) { return NpcTransformSystem.startStack(entity(n), group, form, ticks); }
    public boolean unstack(ScriptNpc n) { return NpcTransformSystem.unstack(entity(n)); }
    public boolean descend(ScriptNpc n, int ticks) { return NpcTransformSystem.descend(entity(n)); }
    public boolean descendOne(ScriptNpc n, int ticks) { return NpcTransformSystem.descendOne(entity(n), ticks); }
    public boolean isTransforming(ScriptNpc n) { return NpcTransformSystem.isHolding(entity(n).getUUID()); }
    private static NpcAuraStyle auraStyle(NpcCombatProfile p, String scope, String group,
                                          String form, boolean create) {
        String key = scope == null ? "" : scope.trim().toLowerCase(java.util.Locale.ROOT);
        if ("base".equals(key)) return p.baseAuraStyle;
        if (!"form".equals(key) && !"stack".equals(key)) return null;
        return p.auraStyle("stack".equals(key), group, form, create);
    }
    public Map<String, Object> getAuraStyle(ScriptNpc n, String scope, String group, String form) {
        NpcAuraStyle style = auraStyle(NpcCombatProfile.read(entity(n)), scope, group, form, false);
        if (style == null) style = new NpcAuraStyle();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", style.enabled); out.put("primaryColor", style.primaryColor);
        out.put("primaryType", style.primaryType); out.put("primaryLayer", style.primaryLayer);
        out.put("extraConfigured", style.extraConfigured); out.put("extraEnabled", style.extraEnabled);
        out.put("extraColor", style.extraColor); out.put("extraType", style.extraType);
        out.put("extraLayer", style.extraLayer); out.put("lightningConfigured", style.lightningConfigured);
        out.put("lightningEnabled", style.lightningEnabled); out.put("lightningColor", style.lightningColor);
        return out;
    }
    private static String optionalColor(String hex) {
        if (hex == null || hex.isBlank()) return "";
        var parsed = NpcCombatProfile.parseHexColor(hex);
        return parsed.isPresent() ? NpcCombatProfile.formatHex(parsed.getAsInt()) : "";
    }
    public boolean setAuraStyle(ScriptNpc n, String scope, String group, String form,
                                boolean enabled, String primaryColor, String primaryType, int primaryLayer,
                                boolean extraConfigured, boolean extraEnabled, String extraColor,
                                String extraType, int extraLayer, boolean lightningConfigured,
                                boolean lightningEnabled, String lightningColor) {
        LivingEntity e = entity(n); NpcCombatProfile p = NpcCombatProfile.read(e);
        NpcAuraStyle style = auraStyle(p, scope, group, form, true);
        if (style == null) return false;
        style.enabled = enabled; style.primaryColor = optionalColor(primaryColor);
        style.primaryType = primaryType == null ? "" : primaryType.trim();
        style.primaryLayer = NpcAuraStyle.clampLayer(primaryLayer);
        style.extraConfigured = extraConfigured; style.extraEnabled = extraEnabled;
        style.extraColor = optionalColor(extraColor);
        style.extraType = extraType == null ? "" : extraType.trim();
        style.extraLayer = NpcAuraStyle.clampLayer(extraLayer);
        style.lightningConfigured = lightningConfigured; style.lightningEnabled = lightningEnabled;
        style.lightningColor = optionalColor(lightningColor);
        p.write(e); NpcAuraFx.sync(e); NpcAppearanceFx.sync(e); return true;
    }
    public boolean clearAuraStyle(ScriptNpc n, String scope, String group, String form) {
        LivingEntity e = entity(n); NpcCombatProfile p = NpcCombatProfile.read(e);
        String key = scope == null ? "" : scope.trim().toLowerCase(java.util.Locale.ROOT);
        if ("base".equals(key)) p.baseAuraStyle = new NpcAuraStyle();
        else if ("stack".equals(key) || "form".equals(key)) {
            String formKey = NpcCombatProfile.auraKey(group, form);
            if (formKey.isBlank()) return false;
            ("stack".equals(key) ? p.stackAuraStyles : p.formAuraStyles).remove(formKey);
        } else return false;
        p.write(e); NpcAuraFx.sync(e); NpcAppearanceFx.sync(e); return true;
    }
    public Map<String, Object> getResolvedAura(ScriptNpc n) {
        var resolved = NpcAuraResolver.resolve(NpcCombatProfile.read(entity(n)));
        Map<String, Object> out = new LinkedHashMap<>();
        java.util.List<Map<String, Object>> layers = new java.util.ArrayList<>();
        for (var layer : resolved.layers()) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("type", layer.type()); one.put("layer", layer.index());
            one.put("color", NpcCombatProfile.formatHex(layer.rgb())); layers.add(one);
        }
        out.put("layers", layers); out.put("lightning", resolved.lightning());
        out.put("lightningColor", NpcCombatProfile.formatHex(resolved.lightningRgb()));
        out.put("rocks", resolved.rocks()); out.put("sparking", resolved.sparking());
        return out;
    }

    public boolean fireTechnique(ScriptNpc n, String id, ScriptEntity target, int ticks) {
        LivingEntity e = entity(n); return NpcKiAttackDispatcher.fire(id, e, NpcCombatProfile.read(e), ticks, maybe(target));
    }
    public boolean fireTechnique(ScriptNpc n, String id, ScriptEntity target, int ticks, String hex) {
        LivingEntity e = entity(n); int color = NpcCombatProfile.parseHexColor(hex).orElse(0);
        return NpcKiAttackDispatcher.fire(id, e, NpcCombatProfile.read(e), ticks, maybe(target), color);
    }
    public boolean isTechniqueReady(ScriptNpc n, String id) { return NpcKiCooldowns.ready(entity(n), id); }
    public int getTechniqueCooldown(ScriptNpc n, String id) { return NpcKiCooldowns.remaining(entity(n), id); }
    public void clearTechniqueCooldown(ScriptNpc n, String id) { NpcKiCooldowns.clear(entity(n), id); }
    public boolean startHakai(ScriptNpc n, ScriptEntity target) { return NpcHakai.start(entity(n), maybe(target)); }
    public boolean cancelHakai(ScriptNpc n) { return NpcHakai.cancel(entity(n)); }
    public boolean isHakai(ScriptNpc n) { return NpcHakai.isChanneling(entity(n)); }
    public boolean lockOn(ScriptNpc n, ScriptEntity target) { if (target == null) return false; NpcKiAim.hardLock(entity(n), entity(target)); return true; }
    public boolean lockOnPlayer(ScriptNpc n, String uuid) {
        java.util.UUID id;
        try { id = java.util.UUID.fromString(uuid); }
        catch (IllegalArgumentException invalid) { return false; }
        LivingEntity e = entity(n);
        if (!(e.level() instanceof net.minecraft.server.level.ServerLevel level)) return false;
        LivingEntity target = NpcEntityLookup.findAlive(level.getServer(), id);
        if (target == null) NpcKiAim.hardLock(e, id);
        else NpcKiAim.hardLock(e, target);
        return true;
    }
    public boolean forcePlayerLock(ScriptPlayer player, ScriptEntity target) {
        if (player == null) return false;
        net.bullettrain.xenopixelsmod.network.ModNetwork.sendToPlayer((ServerPlayer) entity(player),
                new net.bullettrain.xenopixelsmod.network.packet.DmzLockOnPacket(
                        target == null ? -1 : entity(target).getId()));
        return true;
    }
    public boolean clearPlayerLock(ScriptPlayer player) {
        if (player == null) return false;
        net.bullettrain.xenopixelsmod.network.ModNetwork.sendToPlayer((ServerPlayer) entity(player),
                net.bullettrain.xenopixelsmod.network.packet.DmzLockOnPacket.clear());
        return true;
    }
    public boolean isLocked(ScriptNpc n) { return NpcKiAim.isHardLocked(entity(n)); }
    public boolean clearLock(ScriptNpc n) { NpcKiAim.clearHardLock(entity(n)); return true; }
    public boolean chase(ScriptNpc n, ScriptEntity target) { return NpcCombatMoves.chase(entity(n), maybe(target)); }
    public boolean backstep(ScriptNpc n, ScriptEntity target) { return NpcCombatMoves.backstep(entity(n), maybe(target)); }
    public boolean vanish(ScriptNpc n, ScriptEntity target, int side) { return NpcCombatMoves.vanish(entity(n), maybe(target), side); }
    public boolean vanish(ScriptNpc n, ScriptEntity target) { return vanish(n, target, 0); }
    public boolean vanishLeft(ScriptNpc n, ScriptEntity target) { return NpcCombatMoves.vanish(entity(n), maybe(target), -1); }
    public boolean vanishRight(ScriptNpc n, ScriptEntity target) { return NpcCombatMoves.vanish(entity(n), maybe(target), 1); }
    public boolean vanishBehind(ScriptNpc n, ScriptEntity target) { return NpcCombatMoves.vanish(entity(n), maybe(target), 0); }
    public boolean zBurst(ScriptNpc n, ScriptEntity target) { return NpcCombatMoves.zBurst(entity(n), maybe(target)); }
    public boolean setGuard(ScriptNpc n, boolean on) { return NpcCombatMoves.guard(entity(n), on); }
    public boolean isGuarding(ScriptNpc n) { return NpcCombatMoves.isGuarding(entity(n)); }
    public boolean startChargePunch(ScriptNpc n, int ticks) { return NpcChargeMoves.startPunch(entity(n), ticks); }
    public boolean startChargeKick(ScriptNpc n, int ticks) { return NpcChargeMoves.startKick(entity(n), ticks, 0); }
    public boolean startChargeKick(ScriptNpc n, int ticks, int bias) { return NpcChargeMoves.startKick(entity(n), ticks, bias); }
    public boolean releaseCharge(ScriptNpc n) { return NpcChargeMoves.release(entity(n)); }
    public boolean cancelCharge(ScriptNpc n) { return NpcChargeMoves.cancel(entity(n)); }
    public boolean isCharging(ScriptNpc n) { return NpcChargeMoves.isCharging(entity(n)); }
    public int getChargePercent(ScriptNpc n) { return NpcChargeMoves.getPercent(entity(n)); }
    public String getChargeStyle(ScriptNpc n) { return NpcChargeMoves.getStyle(entity(n)); }
    public boolean meleeHit(ScriptNpc n, ScriptEntity target) { return target != null && NpcMeleeDamage.hit(entity(n), entity(target), 1); }
    public boolean meleeHit(ScriptNpc n, ScriptEntity target, double scale) { return target != null && NpcMeleeDamage.hit(entity(n), entity(target), (float) scale); }
    public boolean playMelee(ScriptNpc n) { NpcKiAim.playMelee(entity(n)); return true; }
    public boolean setMeleeAnimation(ScriptNpc n, String animation) { return NpcMeleeDamage.persist(entity(n), animation); }
    public boolean clearMeleeAnimation(ScriptNpc n) { return NpcMeleeDamage.persist(entity(n), ""); }
    public boolean playAnimation(ScriptNpc n, String animation) { return NpcDmzAnim.play(entity(n), animation); }
    public boolean playAnimation(ScriptNpc n, String animation, float speed) { return NpcDmzAnim.play(entity(n), animation, speed); }
    public boolean playComboBeat(ScriptNpc n, int step) {
        return NpcDmzAnim.play(entity(n),
                net.bullettrain.xenopixelsmod.combat.Bt3ComboChoreography.resolve(0, step, false, false));
    }
    public String[] listAnimations() { return net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationCatalog.playableAnimationNames().toArray(String[]::new); }
    public boolean playClip(ScriptEntity e, String clip) { return XenoAnimApi.playClip(entity(e), clip); }
    public boolean playClip(ScriptEntity e, String clip, float speed) { return XenoAnimApi.playClip(entity(e), clip, speed); }
    public boolean playClip(ScriptEntity e, String clip, float speed, int ticks) { return XenoAnimApi.playClip(entity(e), clip, speed, ticks); }
    public boolean playClip(ScriptEntity e, String clip, float speed, int ticks, boolean hold) { return XenoAnimApi.playClip(entity(e), clip, speed, ticks, hold); }
    public boolean playClipOnce(ScriptEntity e, String clip) { return XenoAnimApi.playClip(entity(e), clip, 1, 0, false); }
    public boolean playClipHold(ScriptEntity e, String clip) { return XenoAnimApi.playClip(entity(e), clip, 1, 0, true); }
    public boolean stopClip(ScriptEntity e) { return XenoAnimApi.stopClip(entity(e)); }
    public boolean canPlayClip(ScriptEntity e) { return XenoAnimApi.canPlay(entity(e)); }
    public int clipDuration(String clip) { return XenoAnimApi.clipDuration(clip); }
    public String[] listClips() { return XenoAnimApi.listClips().toArray(String[]::new); }
    public String[] listLibraryClips() { return XenoAnimApi.listLibraryClips().toArray(String[]::new); }
    public boolean isClipAvailable(String clip) { return XenoAnimApi.isClipAvailable(clip); }
    public boolean setStateClip(ScriptEntity e, String slot, String clip) { return XenoAnimApi.setStateClip(entity(e), slot, clip); }
    public String getStateClip(ScriptEntity e, String slot) { return XenoAnimApi.getStateClip(entity(e), slot); }
    public boolean clearStateClip(ScriptEntity e, String slot) { return XenoAnimApi.clearStateClip(entity(e), slot); }
    public boolean playState(ScriptEntity e, String slot) { return XenoAnimApi.playState(entity(e), slot); }
    public String[] listStateSlots() { return XenoAnimApi.listStateSlots(); }
    public String joinNames(Object names) { return NpcScriptLists.joinNames(names); }
    public String joinNames(Object names, String separator) { return NpcScriptLists.joinNames(names, separator); }
    public boolean bindStateSlot(String slot, String clip) { return XenoAnimApi.bindStateSlot(slot, clip); }
    /**
     * The XenoAPI adapter for a native wrapper: {@code npc} becomes an {@code ICustomNpc} over the
     * same data and timers, a player an {@code IPlayer}. Null stays null.
     */
    public xenoapi.npcs.api.entity.IEntity<?> toXeno(ScriptEntity e) {
        return e == null ? null : net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoApiAdapters.wrap(e.unwrap());
    }

    /**
     * The native wrapper for a XenoAPI adapter, so XenoAPI entities can use every other
     * XenoPixels call: {@code XenoPixels.setAura(XenoPixels.fromXeno(apiNpc), true)}. Null stays
     * null; non-living entities and foreign adapters are refused.
     */
    public ScriptEntity fromXeno(xenoapi.npcs.api.entity.IEntity<?> e) {
        net.minecraft.world.entity.Entity raw = net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoApiAdapters.unwrap(e);
        if (raw == null) return null;
        if (raw instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity npc) {
            var state = net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.sharedState(npc);
            return new ScriptNpc(npc, state.temp(), state.timers());
        }
        if (raw instanceof LivingEntity living) return ScriptEntity.of(living);
        throw new IllegalArgumentException("XenoPixels.fromXeno needs a living entity");
    }

    public double getCurrentEnergy(ScriptNpc n) { LivingEntity e = entity(n); return NpcResources.get(e, NpcCombatProfile.read(e)).energy(); }
    public double getCurrentStamina(ScriptNpc n) { LivingEntity e = entity(n); return NpcResources.get(e, NpcCombatProfile.read(e)).stamina(); }
    public double getMaxEnergy(ScriptNpc n) { LivingEntity e = entity(n); return NpcResources.get(e, NpcCombatProfile.read(e)).maxEnergy(); }
    public double getMaxStamina(ScriptNpc n) { LivingEntity e = entity(n); return NpcResources.get(e, NpcCombatProfile.read(e)).maxStamina(); }
    public boolean setCurrentEnergy(ScriptNpc n, double value) { LivingEntity e = entity(n); NpcResources.setEnergy(e, NpcCombatProfile.read(e), value); return true; }
    public boolean setCurrentStamina(ScriptNpc n, double value) { LivingEntity e = entity(n); NpcResources.setStamina(e, NpcCombatProfile.read(e), value); return true; }
}

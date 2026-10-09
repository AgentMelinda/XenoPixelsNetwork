package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.NpcDoorInteract;
import net.bullettrain.xenopixelsmod.npc.NpcOnFoundEnemy;
import net.bullettrain.xenopixelsmod.npc.NpcShelterFrom;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.XenoNpcJob;
import net.bullettrain.xenopixelsmod.npc.XenoNpcRole;
import net.bullettrain.xenopixelsmod.npc.inventory.NpcDrop;
import net.bullettrain.xenopixelsmod.npc.inventory.NpcSlotStack;
import net.bullettrain.xenopixelsmod.npc.lines.XenoNpcLines;
import net.bullettrain.xenopixelsmod.npc.path.NpcPath;
import net.bullettrain.xenopixelsmod.npc.trade.NpcTrade;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import xenoapi.npcs.api.constants.JobType;
import xenoapi.npcs.api.constants.PotionEffectType;
import xenoapi.npcs.api.constants.RoleType;
import xenoapi.npcs.api.entity.IPlayer;
import xenoapi.npcs.api.entity.data.INPCAdvanced;
import xenoapi.npcs.api.entity.data.INPCAi;
import xenoapi.npcs.api.entity.data.INPCDisplay;
import xenoapi.npcs.api.entity.data.INPCInventory;
import xenoapi.npcs.api.entity.data.INPCJob;
import xenoapi.npcs.api.entity.data.INPCMelee;
import xenoapi.npcs.api.entity.data.INPCRanged;
import xenoapi.npcs.api.entity.data.INPCRole;
import xenoapi.npcs.api.entity.data.INPCStats;
import xenoapi.npcs.api.entity.data.role.IRoleTrader;
import xenoapi.npcs.api.item.IItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The {@code ICustomNpc} sub-objects over a native NPC. Every getter reads the NPC's live profile
 * and data; every setter writes the profile through {@link NpcCombatProfile#write}, the same path
 * the editor's save and the {@code /npcprofile} commands use, so the change applies and syncs at
 * once. A CustomNPCs setting with no native counterpart throws the named unsupported error.
 *
 * <p>Unit conversions, where the two mods measure differently: melee delay is ticks per attack
 * here and attacks per second natively ({@code 20 / ticks}); resistances are CustomNPCs' 0-2
 * multipliers against the native 0-100 percent bonus ({@code (v - 1) * 100}, as the importer
 * converts them); accuracy is a percent against a 0-1 blend; effect times are seconds against ticks;
 * projectile speed and size are CustomNPCs' 10 and 5 per native unit.
 */
final class XenoNpcViews {
    private XenoNpcViews() {}

    static <T> T read(XenoNpcEntity npc, Function<NpcCombatProfile, T> getter) {
        return getter.apply(NpcCombatProfile.readCached(npc));
    }

    static void edit(XenoNpcEntity npc, Consumer<NpcCombatProfile> change) {
        XenoApiAdapters.requireServerThread(npc.level());
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        change.accept(profile);
        profile.write(npc);
    }

    static UnsupportedOperationException none(String method) {
        return XenoApiAdapters.unsupported(method + " (native NPCs have no such setting)");
    }

    static String text(String method, String value, int max) {
        return XenoApiAdapters.boundedText(method, value, max);
    }

    // ------------------------------------------------------------------ effects

    static String effectId(int type) {
        if (type == PotionEffectType.NONE) return "";
        Holder<MobEffect> holder = PotionEffectType.getMCType(type);
        if (holder == null) throw new IllegalArgumentException("Unknown potion effect type: " + type);
        return holder.unwrapKey().map(key -> key.location().toString()).orElse("");
    }

    static int effectType(String id) {
        if (id == null || id.isBlank()) return PotionEffectType.NONE;
        List<Holder<MobEffect>> all = PotionEffectType.getMCAllTypes();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).unwrapKey().map(key -> key.location().toString().equals(id)).orElse(false)) return i + 1;
        }
        return PotionEffectType.NONE;
    }

    static ItemStack stackOf(String id, int count) {
        ResourceLocation key = id == null || id.isBlank() ? null : ResourceLocation.tryParse(id);
        Item item = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, count));
    }

    static String idOf(ItemStack stack) {
        return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    // ================================================================== display

    static final class Display implements INPCDisplay {
        private final XenoNpcEntity npc;

        Display(XenoNpcEntity npc) { this.npc = npc; }

        @Override public String getName() { return npc.npcData().displayName(); }

        @Override
        public void setName(String name) {
            String value = text("INPCDisplay.setName", name, 64);
            XenoApiAdapters.requireServerThread(npc.level());
            npc.npcData().setDisplayName(value);
            npc.refreshNameplate();
        }

        @Override public String getTitle() { return npc.npcData().title(); }

        @Override
        public void setTitle(String title) {
            String value = text("INPCDisplay.setTitle", title, 64);
            XenoApiAdapters.requireServerThread(npc.level());
            npc.npcData().setTitle(value);
            npc.refreshNameplate();
        }

        @Override public String getSkinUrl() { return read(npc, p -> p.skinUrl); }
        @Override public void setSkinUrl(String url) { String v = text("INPCDisplay.setSkinUrl", url, 512); edit(npc, p -> p.skinUrl = v); }
        @Override public String getSkinPlayer() { return read(npc, p -> p.skinPlayer); }
        @Override public void setSkinPlayer(String name) { String v = text("INPCDisplay.setSkinPlayer", name, 64); edit(npc, p -> p.skinPlayer = v); }
        @Override public String getSkinTexture() { return read(npc, p -> p.modelTexture); }
        @Override public void setSkinTexture(String texture) { String v = text("INPCDisplay.setSkinTexture", texture, 256); edit(npc, p -> p.modelTexture = v); }
        @Override public boolean getHasLivingAnimation() { throw none("INPCDisplay.getHasLivingAnimation"); }
        @Override public void setHasLivingAnimation(boolean enabled) { throw none("INPCDisplay.setHasLivingAnimation"); }

        /** 0 visible, 1 invisible; 2 (semi-invisible) reads and writes as invisible. */
        @Override public int getVisible() { return read(npc, p -> p.visible) ? 0 : 1; }

        @Override
        public void setVisible(int type) {
            if (type < 0 || type > 2) throw new IllegalArgumentException("INPCDisplay.setVisible: type must be 0-2");
            edit(npc, p -> p.visible = type == 0);
        }

        @Override public boolean isVisibleTo(IPlayer player) { return read(npc, p -> p.visible); }

        @Override public int getBossbar() { return read(npc, p -> p.bossBar ? p.bossBarMode : 0); }

        @Override
        public void setBossbar(int type) {
            if (type < 0 || type > 2) throw new IllegalArgumentException("INPCDisplay.setBossbar: type must be 0-2");
            edit(npc, p -> { p.bossBar = type != 0; p.bossBarMode = type == 2 ? 2 : 1; });
        }

        @Override public int getSize() { return read(npc, p -> p.baseSize <= 0 ? 5 : p.baseSize); }

        @Override
        public void setSize(int size) {
            if (size < 1 || size > 30) throw new IllegalArgumentException("INPCDisplay.setSize: size must be 1-30");
            edit(npc, p -> p.baseSize = size);
        }

        @Override public int getTint() { return read(npc, p -> p.modelTint); }
        @Override public void setTint(int color) { edit(npc, p -> p.modelTint = color & 0xFFFFFF); }
        @Override public int getShowName() { return read(npc, p -> p.displayShowName); }
        @Override public void setShowName(int type) {
            if (type < 0 || type > 2) throw new IllegalArgumentException("INPCDisplay.setShowName: type must be 0-2");
            edit(npc, p -> p.displayShowName = type);
        }
        @Override public void setCapeTexture(String texture) { String v = text("INPCDisplay.setCapeTexture", texture, 256); edit(npc, p -> p.displayCape = v); }
        @Override public String getCapeTexture() { return read(npc, p -> p.displayCape); }
        @Override public void setOverlayTexture(String texture) { String v = text("INPCDisplay.setOverlayTexture", texture, 256); edit(npc, p -> p.displayOverlay = v); }
        @Override public String getOverlayTexture() { return read(npc, p -> p.displayOverlay); }
        @Override public void setModelScale(int part, float x, float y, float z) { throw none("INPCDisplay.setModelScale (use setSize)"); }
        @Override public float[] getModelScale(int part) { throw none("INPCDisplay.getModelScale (use getSize)"); }

        /** CustomNPCs' order is vanilla's BossBarColor order: pink, blue, red, green, yellow, purple, white. */
        @Override
        public int getBossColor() {
            String name = read(npc, p -> p.bossBarColor);
            for (var color : net.minecraft.world.BossEvent.BossBarColor.values()) {
                if (color.name().equalsIgnoreCase(name)) return color.ordinal();
            }
            return net.minecraft.world.BossEvent.BossBarColor.PURPLE.ordinal();
        }

        @Override
        public void setBossColor(int color) {
            var values = net.minecraft.world.BossEvent.BossBarColor.values();
            if (color < 0 || color >= values.length) throw new IllegalArgumentException("INPCDisplay.setBossColor: color must be 0-" + (values.length - 1));
            edit(npc, p -> p.bossBarColor = values[color].name());
        }

        /** An entity id makes the NPC wear that entity's model; null or blank returns it to its own. */
        @Override
        public void setModel(String model) {
            String value = model == null ? "" : text("INPCDisplay.setModel", model, 128);
            if (!value.isEmpty()) {
                ResourceLocation id = ResourceLocation.tryParse(value);
                if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                    throw new xenoapi.npcs.api.CustomNPCsException("Unknown entity id: %s", model);
                }
            }
            edit(npc, p -> {
                p.modelKind = value.isEmpty() ? NpcCombatProfile.MODEL_VANILLA : NpcCombatProfile.MODEL_ENTITY;
                p.modelId = value;
            });
        }

        @Override
        public String getModel() {
            return read(npc, p -> NpcCombatProfile.MODEL_ENTITY.equals(p.modelKind) && !p.modelId.isBlank() ? p.modelId : null);
        }

        @Override public void setHitboxState(byte state) { throw none("INPCDisplay.setHitboxState"); }
        @Override public byte getHitboxState() { throw none("INPCDisplay.getHitboxState"); }
    }

    // ================================================================== stats

    static final class Stats implements INPCStats {
        private final XenoNpcEntity npc;

        Stats(XenoNpcEntity npc) { this.npc = npc; }

        @Override public int getMaxHealth() { return Math.round(npc.getMaxHealth()); }

        /** The editor's max-health override; the DragonMineZ stats decide it when this is 0. */
        @Override
        public void setMaxHealth(int maxHealth) {
            if (maxHealth < 0 || maxHealth > 1_000_000) throw new IllegalArgumentException("INPCStats.setMaxHealth: 0-1000000");
            edit(npc, p -> p.maxHealthOverride = maxHealth);
            if (npc.getHealth() > npc.getMaxHealth()) npc.setHealth(npc.getMaxHealth());
        }

        private static float toMultiplier(int percent) { return 1.0f + percent / 100.0f; }

        private static int toPercent(float multiplier) {
            if (!Float.isFinite(multiplier)) throw new IllegalArgumentException("INPCStats.setResistance: value must be finite");
            return NpcCombatProfile.clampNpcResistance(Math.round((multiplier - 1.0f) * 100.0f));
        }

        /** 0 melee, 1 arrow, 2 explosion, 3 knockback; 1 is normal, 2 the native maximum. */
        @Override
        public float getResistance(int type) {
            return read(npc, p -> switch (type) {
                case 0 -> toMultiplier(p.npcMeleeResistance);
                case 1 -> toMultiplier(p.npcArrowResistance);
                case 2 -> toMultiplier(p.npcExplosionResistance);
                case 3 -> toMultiplier(p.npcKnockbackResistance);
                default -> throw new IllegalArgumentException("INPCStats.getResistance: type must be 0-3");
            });
        }

        @Override
        public void setResistance(int type, float value) {
            if (type < 0 || type > 3) throw new IllegalArgumentException("INPCStats.setResistance: type must be 0-3");
            int percent = toPercent(value);
            edit(npc, p -> {
                switch (type) {
                    case 0 -> p.npcMeleeResistance = percent;
                    case 1 -> p.npcArrowResistance = percent;
                    case 2 -> p.npcExplosionResistance = percent;
                    default -> p.npcKnockbackResistance = percent;
                }
            });
        }

        @Override public int getCombatRegen() { return read(npc, p -> Math.round(p.combatRegen)); }
        @Override public void setCombatRegen(int regen) { edit(npc, p -> p.combatRegen = NpcCombatProfile.clampRegen(regen)); }
        @Override public int getHealthRegen() { return read(npc, p -> Math.round(p.healthRegen)); }
        @Override public void setHealthRegen(int regen) { edit(npc, p -> p.healthRegen = NpcCombatProfile.clampRegen(regen)); }
        @Override public INPCMelee getMelee() { return new Melee(npc); }
        @Override public INPCRanged getRanged() { return new Ranged(npc); }

        /** 0 potion, 1 fall damage, 2 sunburning, 3 fire, 4 drowning, 5 cobweb. */
        @Override
        public boolean getImmune(int type) {
            return read(npc, p -> switch (type) {
                case 0 -> p.potionImmune;
                case 1 -> p.noFallDamage;
                case 2 -> !p.burnsInSun;
                case 3 -> p.fireImmune;
                case 4 -> !p.canDrown;
                case 5 -> !p.cobwebAffected;
                default -> throw new IllegalArgumentException("INPCStats.getImmune: type must be 0-5");
            });
        }

        @Override
        public void setImmune(int type, boolean bo) {
            if (type < 0 || type > 5) throw new IllegalArgumentException("INPCStats.setImmune: type must be 0-5");
            edit(npc, p -> {
                switch (type) {
                    case 0 -> p.potionImmune = bo;
                    case 1 -> p.noFallDamage = bo;
                    case 2 -> p.burnsInSun = !bo;
                    case 3 -> p.fireImmune = bo;
                    case 4 -> p.canDrown = !bo;
                    default -> p.cobwebAffected = !bo;
                }
            });
        }

        private static final List<String> CREATURES = List.of("normal", "undead", "arthropod");

        /** 0 normal, 1 undead, 2 arthropod; the native illager and aquatic families read as normal. */
        @Override
        public void setCreatureType(int type) {
            if (type < 0 || type >= CREATURES.size()) throw new IllegalArgumentException("INPCStats.setCreatureType: type must be 0-2");
            edit(npc, p -> p.creatureType = CREATURES.get(type));
        }

        @Override
        public int getCreatureType() {
            int index = CREATURES.indexOf(read(npc, p -> p.creatureType == null ? "normal" : p.creatureType.toLowerCase(Locale.ROOT)));
            return Math.max(0, index);
        }

        /** 0 always respawns, 3 never; day-only, night-only and natural despawn have no native rule. */
        @Override public int getRespawnType() { return npc.npcData().respawnEnabled() ? 0 : 3; }

        @Override
        public void setRespawnType(int type) {
            if (type != 0 && type != 3) throw none("INPCStats.setRespawnType " + type + " (only 0 always and 3 never)");
            XenoApiAdapters.requireServerThread(npc.level());
            npc.npcData().setRespawnEnabled(type == 0);
        }

        @Override public int getRespawnTime() { return npc.npcData().respawnDelayTicks() / 20; }

        @Override
        public void setRespawnTime(int seconds) {
            if (seconds < 1 || seconds > 3600) throw new IllegalArgumentException("INPCStats.setRespawnTime: 1-3600 seconds");
            XenoApiAdapters.requireServerThread(npc.level());
            npc.npcData().setRespawnDelayTicks(seconds * 20);
        }

        @Override public boolean getHideDeadBody() { return read(npc, p -> p.hideDeadBody); }
        @Override public void setHideDeadBody(boolean hide) { edit(npc, p -> p.hideDeadBody = hide); }

        /** How far the NPC notices targets: its follow-range attribute. */
        @Override
        public int getAggroRange() {
            var attribute = npc.getAttribute(Attributes.FOLLOW_RANGE);
            return attribute == null ? 0 : (int) Math.round(attribute.getBaseValue());
        }

        @Override
        public void setAggroRange(int range) {
            if (range < 1 || range > 128) throw new IllegalArgumentException("INPCStats.setAggroRange: 1-128");
            var attribute = npc.getAttribute(Attributes.FOLLOW_RANGE);
            if (attribute == null) throw none("INPCStats.setAggroRange");
            XenoApiAdapters.requireServerThread(npc.level());
            attribute.setBaseValue(range);
        }
    }

    // ================================================================== melee

    static final class Melee implements INPCMelee {
        private final XenoNpcEntity npc;

        Melee(XenoNpcEntity npc) { this.npc = npc; }

        @Override public int getStrength() { return read(npc, p -> Math.round(p.npcMeleeDamage)); }
        @Override public void setStrength(int strength) { edit(npc, p -> p.npcMeleeDamage = NpcCombatProfile.clampNpcMeleeDamage(strength)); }
        /** Ticks between attacks. */
        @Override public int getDelay() { return read(npc, p -> Math.round(20.0f / NpcCombatProfile.clampNpcMeleeSpeed(p.npcMeleeSpeed))); }

        @Override
        public void setDelay(int speed) {
            if (speed < 1 || speed > 200) throw new IllegalArgumentException("INPCMelee.setDelay: 1-200 ticks");
            edit(npc, p -> p.npcMeleeSpeed = NpcCombatProfile.clampNpcMeleeSpeed(20.0f / speed));
        }

        @Override public int getRange() { return read(npc, p -> Math.round(p.npcMeleeRange)); }
        @Override public void setRange(int range) { edit(npc, p -> p.npcMeleeRange = NpcCombatProfile.clampNpcMeleeRange(range)); }
        @Override public int getKnockback() { return read(npc, p -> Math.round(p.npcMeleeKnockback)); }
        @Override public void setKnockback(int knockback) { edit(npc, p -> p.npcMeleeKnockback = NpcCombatProfile.clampNpcProjectileKnockback(knockback)); }
        @Override public int getEffectType() { return read(npc, p -> effectType(p.npcMeleeEffect)); }
        /** Seconds. */
        @Override public int getEffectTime() { return read(npc, p -> p.npcMeleeEffectDuration / 20); }
        @Override public int getEffectStrength() { return read(npc, p -> p.npcMeleeEffectAmplifier); }

        @Override
        public void setEffect(int type, int strength, int time) {
            String id = effectId(type);
            edit(npc, p -> {
                p.npcMeleeEffect = id;
                p.npcMeleeEffectAmplifier = NpcCombatProfile.clampNpcEffectAmplifier(strength);
                p.npcMeleeEffectDuration = NpcCombatProfile.clampNpcEffectDuration(time * 20);
            });
        }
    }

    // ================================================================== ranged

    static final class Ranged implements INPCRanged {
        private final XenoNpcEntity npc;

        Ranged(XenoNpcEntity npc) { this.npc = npc; }

        @Override public int getStrength() { return read(npc, p -> Math.round(p.npcProjectileStrength)); }
        @Override public void setStrength(int strength) { edit(npc, p -> p.npcProjectileStrength = NpcCombatProfile.clampNpcProjectileStrength(strength)); }
        @Override public int getSpeed() { return read(npc, p -> Math.round(p.npcProjectileSpeed * 10.0f)); }
        @Override public void setSpeed(int speed) { edit(npc, p -> p.npcProjectileSpeed = NpcCombatProfile.clampNpcProjectileSpeed(speed / 10.0f)); }
        @Override public int getBurst() { return read(npc, p -> p.npcRangedBurstCount); }
        @Override public void setBurst(int count) { edit(npc, p -> p.npcRangedBurstCount = NpcCombatProfile.clampNpcBurstCount(count)); }
        @Override public int getBurstDelay() { return read(npc, p -> p.npcRangedBurstRate); }
        @Override public void setBurstDelay(int delay) { edit(npc, p -> p.npcRangedBurstRate = NpcCombatProfile.clampNpcRangedDelay(delay)); }
        @Override public int getKnockback() { return read(npc, p -> Math.round(p.npcProjectileKnockback)); }
        @Override public void setKnockback(int punch) { edit(npc, p -> p.npcProjectileKnockback = NpcCombatProfile.clampNpcProjectileKnockback(punch)); }
        @Override public int getSize() { return read(npc, p -> Math.round(p.npcProjectileSize * 5.0f)); }
        @Override public void setSize(int size) { edit(npc, p -> p.npcProjectileSize = NpcCombatProfile.clampNpcProjectileSize(size / 5.0f)); }
        @Override public boolean getRender3D() { throw none("INPCRanged.getRender3D"); }
        @Override public void setRender3D(boolean render3d) { throw none("INPCRanged.setRender3D"); }
        @Override public boolean getSpins() { return read(npc, p -> p.npcProjectileSpins); }
        @Override public void setSpins(boolean spins) { edit(npc, p -> p.npcProjectileSpins = spins); }
        @Override public boolean getSticks() { return read(npc, p -> p.npcProjectileSticks); }
        @Override public void setSticks(boolean sticks) { edit(npc, p -> p.npcProjectileSticks = sticks); }
        @Override public boolean getHasGravity() { return read(npc, p -> !"none".equals(NpcCombatProfile.canonicalProjectileGravity(p.npcProjectileGravity))); }

        @Override
        public void setHasGravity(boolean hasGravity) {
            edit(npc, p -> p.npcProjectileGravity = hasGravity ? "normal" : "none");
        }

        @Override public boolean getAccelerate() { return read(npc, p -> "accelerate".equals(NpcCombatProfile.canonicalProjectileGravity(p.npcProjectileGravity))); }

        @Override
        public void setAccelerate(boolean accelerate) {
            edit(npc, p -> p.npcProjectileGravity = accelerate ? "accelerate" : "normal");
        }

        @Override public int getExplodeSize() { return read(npc, p -> Math.round(p.npcProjectileExplosion)); }
        @Override public void setExplodeSize(int size) { edit(npc, p -> p.npcProjectileExplosion = NpcCombatProfile.clampNpcProjectileExplosion(size)); }
        @Override public int getEffectType() { return read(npc, p -> effectType(p.npcProjectileEffect)); }
        @Override public int getEffectTime() { return read(npc, p -> p.npcProjectileEffectDuration / 20); }
        @Override public int getEffectStrength() { return read(npc, p -> p.npcProjectileEffectAmplifier); }

        @Override
        public void setEffect(int type, int strength, int time) {
            String id = effectId(type);
            edit(npc, p -> {
                p.npcProjectileEffect = id;
                p.npcProjectileEffectAmplifier = NpcCombatProfile.clampNpcEffectAmplifier(strength);
                p.npcProjectileEffectDuration = NpcCombatProfile.clampNpcEffectDuration(time * 20);
            });
        }

        @Override public boolean getGlows() { return read(npc, p -> p.npcProjectileGlows); }
        @Override public void setGlows(boolean glows) { edit(npc, p -> p.npcProjectileGlows = glows); }
        /** The trail particle. */
        @Override public String getParticle() { return read(npc, p -> p.npcProjectileTrail); }
        @Override public void setParticle(String type) { String v = NpcCombatProfile.canonicalProjectileTrail(type); edit(npc, p -> p.npcProjectileTrail = v); }

        /** 0 firing, 1 hit, 2 ground. */
        @Override
        public String getSound(int type) {
            return read(npc, p -> switch (type) {
                case 0 -> p.npcRangedFireSound;
                case 1 -> p.npcRangedHitSound;
                case 2 -> p.npcRangedGroundSound;
                default -> throw new IllegalArgumentException("INPCRanged.getSound: type must be 0-2");
            });
        }

        @Override
        public void setSound(int type, String sound) {
            if (type < 0 || type > 2) throw new IllegalArgumentException("INPCRanged.setSound: type must be 0-2");
            String value = sound == null ? "" : text("INPCRanged.setSound", sound, 128);
            if (!value.isEmpty()) XenoApiAdapters.sound(value);
            edit(npc, p -> {
                switch (type) {
                    case 0 -> p.npcRangedFireSound = value;
                    case 1 -> p.npcRangedHitSound = value;
                    default -> p.npcRangedGroundSound = value;
                }
            });
        }

        @Override public int getShotCount() { return read(npc, p -> p.npcRangedShotCount); }
        @Override public void setShotCount(int count) { edit(npc, p -> p.npcRangedShotCount = NpcCombatProfile.clampNpcShotCount(count)); }
        @Override public boolean getHasAimAnimation() { return read(npc, p -> !"no".equals(p.npcRangedAimMode)); }

        @Override
        public void setHasAimAnimation(boolean aim) {
            edit(npc, p -> p.npcRangedAimMode = aim ? "distant" : "no");
        }

        /** Percent. */
        @Override public int getAccuracy() { return read(npc, p -> Math.round(p.npcRangedAccuracy * 100.0f)); }
        @Override public void setAccuracy(int accuracy) { edit(npc, p -> p.npcRangedAccuracy = NpcCombatProfile.clampNpcRangedAccuracy(accuracy / 100.0f)); }
        @Override public int getRange() { return read(npc, p -> Math.round(p.npcRangedRange)); }
        @Override public void setRange(int range) { edit(npc, p -> p.npcRangedRange = NpcCombatProfile.clampNpcRangedRange(range)); }
        @Override public int getDelayMin() { return read(npc, p -> p.npcRangedMinDelay); }
        @Override public int getDelayMax() { return read(npc, p -> p.npcRangedMaxDelay); }

        @Override
        public int getDelayRNG() {
            int min = getDelayMin();
            int max = Math.max(min, getDelayMax());
            return min + npc.getRandom().nextInt(max - min + 1);
        }

        @Override
        public void setDelay(int min, int max) {
            if (min < 1 || max < min) throw new IllegalArgumentException("INPCRanged.setDelay: need 1 <= min <= max");
            edit(npc, p -> {
                p.npcRangedMinDelay = NpcCombatProfile.clampNpcRangedDelay(min);
                p.npcRangedMaxDelay = NpcCombatProfile.clampNpcRangedDelay(max);
            });
        }

        /** 0 fires at any distance, 1 is the native indirect (arcing) fire. */
        @Override public int getFireType() { return read(npc, p -> p.npcRangedIndirect ? 1 : 0); }

        @Override
        public void setFireType(int type) {
            if (type < 0 || type > 2) throw new IllegalArgumentException("INPCRanged.setFireType: type must be 0-2");
            edit(npc, p -> p.npcRangedIndirect = type != 0);
        }
    }

    // ================================================================== AI

    static final class Ai implements INPCAi {
        private final XenoNpcEntity npc;

        Ai(XenoNpcEntity npc) { this.npc = npc; }

        @Override public int getAnimation() { throw none("INPCAi.getAnimation (native NPCs play studio clips)"); }
        @Override public void setAnimation(int type) { throw none("INPCAi.setAnimation (native NPCs play studio clips)"); }
        @Override public int getCurrentAnimation() { throw none("INPCAi.getCurrentAnimation (native NPCs play studio clips)"); }
        @Override public void setReturnsHome(boolean bo) { edit(npc, p -> p.aiReturnToStart = bo); }
        @Override public boolean getReturnsHome() { return read(npc, p -> p.aiReturnToStart); }
        /** 0 retaliate, 1 panic, 2 retreat, 3 nothing. */
        @Override public int getRetaliateType() { return read(npc, p -> p.aiOnFoundEnemy == null ? 0 : p.aiOnFoundEnemy.ordinal()); }

        @Override
        public void setRetaliateType(int type) {
            if (type < 0 || type >= NpcOnFoundEnemy.values().length) throw new IllegalArgumentException("INPCAi.setRetaliateType: type must be 0-3");
            edit(npc, p -> p.aiOnFoundEnemy = NpcOnFoundEnemy.byIndex(type));
        }

        /** 0 standing (stays home), 1 wandering, 2 walking its path. */
        @Override
        public int getMovingType() {
            return read(npc, p -> p.path != null && p.path.walkable() ? 2 : p.stayHome ? 0 : 1);
        }

        @Override
        public void setMovingType(int type) {
            if (type < 0 || type > 2) throw new IllegalArgumentException("INPCAi.setMovingType: type must be 0-2");
            if (type == 2 && !read(npc, p -> p.path != null && p.path.walkable())) {
                throw new IllegalStateException("INPCAi.setMovingType: the NPC has no path; set one with the Path Tool");
            }
            if (type != 2 && read(npc, p -> p.path != null && p.path.walkable())) {
                throw new IllegalStateException("INPCAi.setMovingType: the NPC walks a path; clear it with the Path Tool first");
            }
            edit(npc, p -> p.stayHome = type == 0);
        }

        @Override public int getNavigationType() {
            var navigation = npc.getNavigation();
            if (navigation instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation) return 0;
            if (navigation instanceof net.minecraft.world.entity.ai.navigation.FlyingPathNavigation) return 1;
            if (navigation instanceof net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation) return 2;
            throw none("INPCAi.getNavigationType (unmapped native navigator)");
        }
        @Override public void setNavigationType(int type) {
            if (type < 0 || type > 2) throw new IllegalArgumentException("INPCAi.setNavigationType: type must be 0-2");
            XenoApiAdapters.requireServerThread(npc.level());
            if (type != getNavigationType()) throw none("INPCAi.setNavigationType (native navigator replacement)");
        }
        @Override public int getStandingType() { throw none("INPCAi.getStandingType"); }
        @Override public void setStandingType(int type) { throw none("INPCAi.setStandingType"); }
        @Override public boolean getAttackInvisible() { return read(npc, p -> p.aiAttackInvisible); }
        @Override public void setAttackInvisible(boolean attack) { edit(npc, p -> p.aiAttackInvisible = attack); }
        /** How far from home before the NPC is brought back (the leash). */
        @Override public int getWanderingRange() { return (int) Math.round(npc.npcData().leashRadius()); }

        @Override
        public void setWanderingRange(int range) {
            if (range < 0 || range > 512) throw new IllegalArgumentException("INPCAi.setWanderingRange: 0-512");
            XenoApiAdapters.requireServerThread(npc.level());
            npc.npcData().setLeashRadius(range);
        }

        @Override public boolean getInteractWithNPCs() { return read(npc, p -> p.socialGestures); }
        @Override public void setInteractWithNPCs(boolean interact) { edit(npc, p -> p.socialGestures = interact); }
        @Override public boolean getStopOnInteract() { throw none("INPCAi.getStopOnInteract"); }
        @Override public void setStopOnInteract(boolean stopOnInteract) { throw none("INPCAi.setStopOnInteract"); }
        /** The path walking speed, CustomNPCs' 1-10 against the native 0.1-2 multiplier (5 = 1.0). */
        @Override public int getWalkingSpeed() { return read(npc, p -> (int) Math.round((p.path == null ? NpcPath.DEFAULT_SPEED : p.path.speed()) * 5.0)); }

        @Override
        public void setWalkingSpeed(int speed) {
            if (speed < 0 || speed > 10) throw new IllegalArgumentException("INPCAi.setWalkingSpeed: 0-10");
            edit(npc, p -> { if (p.path != null) p.path.setSpeed(speed / 5.0); });
        }

        /** 0 loop, 1 back and forth, 2 once. */
        @Override public int getMovingPathType() { return read(npc, p -> p.path == null ? 0 : p.path.mode().ordinal()); }
        @Override public boolean getMovingPathPauses() { return read(npc, p -> p.path != null && p.path.pauses()); }

        @Override
        public void setMovingPathType(int type, boolean pauses) {
            if (type < 0 || type >= NpcPath.Mode.values().length) throw new IllegalArgumentException("INPCAi.setMovingPathType: type must be 0-2");
            edit(npc, p -> {
                if (p.path == null) p.path = new NpcPath();
                p.path.setMode(NpcPath.Mode.values()[type]);
                p.path.setPauses(pauses);
            });
        }

        /** CustomNPCs' 0 break, 1 open, 2 disabled. */
        @Override
        public int getDoorInteract() {
            NpcDoorInteract mode = read(npc, p -> p.aiDoorInteract);
            if (mode == null) return 2;
            return switch (mode) {
                case BREAK -> 0;
                case OPEN -> 1;
                case DISABLED -> 2;
            };
        }

        @Override
        public void setDoorInteract(int type) {
            NpcDoorInteract mode = switch (type) {
                case 0 -> NpcDoorInteract.BREAK;
                case 1 -> NpcDoorInteract.OPEN;
                case 2 -> NpcDoorInteract.DISABLED;
                default -> throw new IllegalArgumentException("INPCAi.setDoorInteract: type must be 0-2");
            };
            edit(npc, p -> p.aiDoorInteract = mode);
        }

        @Override public boolean getCanSwim() { return read(npc, p -> p.aiCanSwim); }
        @Override public void setCanSwim(boolean canSwim) { edit(npc, p -> p.aiCanSwim = canSwim); }
        /** 0 darkness, 1 sunlight, 2 disabled. */
        @Override public int getSheltersFrom() { return read(npc, p -> p.aiShelterFrom == null ? 2 : p.aiShelterFrom.ordinal()); }

        @Override
        public void setSheltersFrom(int type) {
            if (type < 0 || type >= NpcShelterFrom.values().length) throw new IllegalArgumentException("INPCAi.setSheltersFrom: type must be 0-2");
            edit(npc, p -> p.aiShelterFrom = NpcShelterFrom.byIndex(type));
        }

        @Override public boolean getAttackLOS() { return read(npc, p -> p.aiMustSeeTarget); }
        @Override public void setAttackLOS(boolean enabled) { edit(npc, p -> p.aiMustSeeTarget = enabled); }
        @Override public boolean getAvoidsWater() { return read(npc, p -> p.aiAvoidsWater); }
        @Override public void setAvoidsWater(boolean enabled) { edit(npc, p -> p.aiAvoidsWater = enabled); }
        @Override public boolean getLeapAtTarget() { return read(npc, p -> p.aiLeapAtTarget); }
        @Override public void setLeapAtTarget(boolean leap) { edit(npc, p -> p.aiLeapAtTarget = leap); }
    }

    // ================================================================== inventory

    static final class Inventory implements INPCInventory {
        /** NpcGear order: head, chest, legs, feet, main hand, off hand. */
        static final int MAIN_HAND = 4;
        static final int OFF_HAND = 5;
        private final XenoNpcEntity npc;

        Inventory(XenoNpcEntity npc) { this.npc = npc; }

        private IItemStack gear(int index) {
            return XenoApiAdapters.wrap(XenoNpcViews.<ItemStack>read(npc, p -> p.gear.stack(index, npc.registryAccess())));
        }

        private void setGear(int index, IItemStack item) {
            ItemStack stack = XenoApiAdapters.unwrap(item).copy();
            edit(npc, p -> p.gear.setStack(index, stack, npc.registryAccess()));
        }

        @Override public IItemStack getRightHand() { return gear(MAIN_HAND); }
        @Override public void setRightHand(IItemStack item) { setGear(MAIN_HAND, item); }
        @Override public IItemStack getLeftHand() { return gear(OFF_HAND); }
        @Override public void setLeftHand(IItemStack item) { setGear(OFF_HAND, item); }
        @Override public IItemStack getProjectile() { throw none("INPCInventory.getProjectile (native NPCs fire ki)"); }
        @Override public void setProjectile(IItemStack item) { throw none("INPCInventory.setProjectile (native NPCs fire ki)"); }

        /** 0 head, 1 chest, 2 legs, 3 feet. */
        @Override
        public IItemStack getArmor(int slot) {
            if (slot < 0 || slot > 3) throw new IllegalArgumentException("INPCInventory.getArmor: slot must be 0-3");
            return gear(slot);
        }

        @Override
        public void setArmor(int slot, IItemStack item) {
            if (slot < 0 || slot > 3) throw new IllegalArgumentException("INPCInventory.setArmor: slot must be 0-3");
            setGear(slot, item);
        }

        /** Nine drop rows; chance is a percent. An empty item clears the row. */
        @Override
        public void setDropItem(int slot, IItemStack item, float chance) {
            if (slot < 0 || slot >= net.bullettrain.xenopixelsmod.npc.inventory.NpcDropList.MAX_DROPS) {
                throw new IllegalArgumentException("INPCInventory.setDropItem: slot must be 0-8");
            }
            XenoApiAdapters.requireFinite("INPCInventory.setDropItem", chance);
            ItemStack stack = XenoApiAdapters.unwrap(item).copy();
            edit(npc, p -> {
                while (p.drops.size() <= slot) p.drops.add(NpcDrop.empty());
                p.drops.set(slot, stack.isEmpty() ? NpcDrop.empty()
                        : new NpcDrop(NpcSlotStack.of(stack, npc.registryAccess()), chance));
            });
        }

        @Override
        public IItemStack getDropItem(int slot) {
            return XenoApiAdapters.wrap(XenoNpcViews.<ItemStack>read(npc, p -> slot < 0 || slot >= p.drops.size() ? ItemStack.EMPTY
                    : p.drops.get(slot).stack(npc.registryAccess())));
        }

        @Override public int getExpMin() { return read(npc, p -> p.drops.minExp()); }
        @Override public int getExpMax() { return read(npc, p -> p.drops.maxExp()); }
        @Override public int getExpRNG() { return read(npc, p -> p.drops.rollExperience(npc.getRandom())); }

        @Override
        public void setExp(int min, int max) {
            if (min < 0 || max < min) throw new IllegalArgumentException("INPCInventory.setExp: need 0 <= min <= max");
            edit(npc, p -> {
                p.drops.setMinExp(min);
                p.drops.setMaxExp(max);
            });
        }

        /** One roll of the drop table, as a death would drop it. */
        @Override
        public IItemStack[] getItemsRNG() {
            List<ItemStack> rolled = read(npc, p -> p.drops.roll(npc.getRandom(), npc.registryAccess()));
            return rolled.stream().map(XenoApiAdapters::wrap).toArray(IItemStack[]::new);
        }
    }

    // ================================================================== advanced (lines / sounds)

    static final class Advanced implements INPCAdvanced {
        /** CustomNPCs' line types in order: interact, attack, world, killed, kill, NPC interact. */
        static final XenoNpcLines.Category[] LINES = {
                XenoNpcLines.Category.INTERACT, XenoNpcLines.Category.ATTACK, XenoNpcLines.Category.WORLD,
                XenoNpcLines.Category.KILLED, XenoNpcLines.Category.KILL, XenoNpcLines.Category.NPC};
        static final int MAX_LINES = 16;
        private final XenoNpcEntity npc;

        Advanced(XenoNpcEntity npc) { this.npc = npc; }

        private static XenoNpcLines.Category category(String method, int type) {
            if (type < 0 || type >= LINES.length) throw new IllegalArgumentException(method + ": type must be 0-5");
            return LINES[type];
        }

        /** Native lines carry no sound of their own, so {@code sound} is not kept. */
        @Override
        public void setLine(int type, int slot, String text, String sound) {
            XenoNpcLines.Category category = category("INPCAdvanced.setLine", type);
            if (slot < 0 || slot >= MAX_LINES) throw new IllegalArgumentException("INPCAdvanced.setLine: slot must be 0-" + (MAX_LINES - 1));
            String line = text("INPCAdvanced.setLine", text, 256);
            edit(npc, p -> {
                List<String> lines = new ArrayList<>(p.linesFor(category));
                if (line.isEmpty()) {
                    if (slot < lines.size()) lines.remove(slot);
                } else {
                    while (lines.size() <= slot) lines.add("");
                    lines.set(slot, line);
                    lines.removeIf(String::isEmpty);
                }
                p.setLines(category, lines);
            });
        }

        @Override
        public String getLine(int type, int slot) {
            List<String> lines = read(npc, p -> p.linesFor(category("INPCAdvanced.getLine", type)));
            return slot >= 0 && slot < lines.size() ? lines.get(slot) : null;
        }

        @Override public int getLineCount(int type) { return read(npc, p -> p.linesFor(category("INPCAdvanced.getLineCount", type)).size()); }

        /** 0 idle, 1 angry, 2 hurt, 3 death, 4 step. */
        @Override
        public String getSound(int type) {
            return read(npc, p -> switch (type) {
                case 0 -> p.soundLiving;
                case 1 -> p.soundAngry;
                case 2 -> p.soundHurt;
                case 3 -> p.soundDeath;
                case 4 -> p.soundStep;
                default -> throw new IllegalArgumentException("INPCAdvanced.getSound: type must be 0-4");
            });
        }

        @Override
        public void setSound(int type, String sound) {
            if (type < 0 || type > 4) throw new IllegalArgumentException("INPCAdvanced.setSound: type must be 0-4");
            String value = sound == null ? "" : text("INPCAdvanced.setSound", sound, 128);
            if (!value.isEmpty()) XenoApiAdapters.sound(value);
            edit(npc, p -> {
                switch (type) {
                    case 0 -> p.soundLiving = value;
                    case 1 -> p.soundAngry = value;
                    case 2 -> p.soundHurt = value;
                    case 3 -> p.soundDeath = value;
                    default -> p.soundStep = value;
                }
            });
        }
    }

    // ================================================================== role / job

    static int roleType(XenoNpcRole role) {
        return switch (role) {
            case TRADER -> RoleType.TRADER;
            case BANK -> RoleType.BANK;
            case TRANSPORTER -> RoleType.TRANSPORTER;
            case COMPANION -> RoleType.COMPANION;
            default -> RoleType.NONE;
        };
    }

    static INPCRole role(XenoNpcEntity npc) {
        return switch (npc.role()) {
            case TRADER -> new Trader(npc);
            case TRANSPORTER -> new XenoTransporterAdapter(npc);
            default -> () -> roleType(npc.role());
        };
    }

    /** A trader's shop. Trades are item ids and counts natively, so components on a stack are not kept. */
    static final class Trader implements IRoleTrader {
        private final XenoNpcEntity npc;

        Trader(XenoNpcEntity npc) { this.npc = npc; }

        @Override public int getType() { return RoleType.TRADER; }

        private NpcTrade trade(int slot) {
            if (slot < 0 || slot >= net.bullettrain.xenopixelsmod.npc.trade.NpcTradeList.MAX_TRADES) {
                throw new IllegalArgumentException("IRoleTrader: slot must be 0-" + (net.bullettrain.xenopixelsmod.npc.trade.NpcTradeList.MAX_TRADES - 1));
            }
            return read(npc, p -> p.trades.get(slot));
        }

        @Override public IItemStack getSold(int slot) { NpcTrade t = trade(slot); return XenoApiAdapters.wrap(stackOf(t.result(), t.resultCount())); }
        @Override public IItemStack getCurrency1(int slot) { NpcTrade t = trade(slot); return XenoApiAdapters.wrap(stackOf(t.costA(), t.countA())); }
        @Override public IItemStack getCurrency2(int slot) { NpcTrade t = trade(slot); return XenoApiAdapters.wrap(stackOf(t.costB(), t.countB())); }

        @Override
        public void set(int slot, IItemStack currency, IItemStack currency2, IItemStack sold) {
            trade(slot);
            ItemStack a = XenoApiAdapters.unwrap(currency);
            ItemStack b = XenoApiAdapters.unwrap(currency2);
            ItemStack r = XenoApiAdapters.unwrap(sold);
            if (a.isEmpty() || r.isEmpty()) throw new IllegalArgumentException("IRoleTrader.set: a trade needs a currency and an item sold");
            NpcTrade next = new NpcTrade(idOf(a), a.getCount(), idOf(b), b.isEmpty() ? 0 : b.getCount(),
                    idOf(r), r.getCount(), NpcTrade.DEFAULT_MAX_USES);
            edit(npc, p -> p.trades.set(slot, next));
        }

        @Override
        public void remove(int slot) {
            trade(slot);
            edit(npc, p -> p.trades.remove(slot));
        }

        @Override public void setMarket(String name) { throw none("IRoleTrader.setMarket (native traders keep their own stock)"); }
        @Override public String getMarket() { throw none("IRoleTrader.getMarket (native traders keep their own stock)"); }
    }

    /** The job, in CustomNPCs' {@link JobType} numbering, which the native job list follows. */
    static INPCJob job(XenoNpcEntity npc) {
        NpcCombatProfile currentProfile = NpcCombatProfile.readCached(npc);
        if (currentProfile.jobEnabled) {
            if (XenoNpcJob.byId(currentProfile.job) == XenoNpcJob.BARD) return new Bard(npc);
            if (XenoNpcJob.byId(currentProfile.job) == XenoNpcJob.FOLLOWER) return new Follower(npc);
        }
        return () -> {
            NpcCombatProfile profile = NpcCombatProfile.readCached(npc);
            if (!profile.jobEnabled) return JobType.NONE;
            return XenoNpcJob.byId(profile.job).ordinal();
        };
    }

    public static final class Bard implements INPCJob, xenoapi.npcs.api.entity.data.role.IJobBard {
        private final XenoNpcEntity npc;
        Bard(XenoNpcEntity npc) { this.npc = npc; }
        @Override public int getType() { return currentJobType(npc); }
        @Override public String getSong() { return read(npc, p -> p.bardSound); }
        @Override public void setSong(String song) {
            String value = text("IJobBard.setSong", song, 128);
            if (!value.isEmpty() && net.bullettrain.xenopixelsmod.compat.npc.NpcCustomSounds.resolve(value) == null) {
                throw new IllegalArgumentException("IJobBard.setSong: unknown sound id " + value);
            }
            edit(npc, p -> p.bardSound = value);
        }
        @Override public boolean getLooping() { return read(npc, p -> p.bardLoops); }
        @Override public void setLooping(boolean enabled) { edit(npc, p -> p.bardLoops = enabled); }
        @Override public boolean getIsBackground() { return read(npc, p -> p.bardJukebox); }
        @Override public void setIsBackground(boolean enabled) { edit(npc, p -> p.bardJukebox = enabled); }
        @Override public int getMinRange() { return read(npc, p -> p.bardOnDistance); }
        @Override public void setMinRange(int range) {
            bardRange(range);
            edit(npc, p -> p.bardOnDistance = range);
        }
        @Override public int getMaxRange() { return read(npc, p -> p.bardOffDistance); }
        @Override public void setMaxRange(int range) {
            bardRange(range);
            edit(npc, p -> p.bardOffDistance = range);
        }
        @Override public boolean getHasMaxRange() { return read(npc, p -> p.bardHasOffDistance); }
        @Override public void setHasMaxRange(boolean enabled) { edit(npc, p -> p.bardHasOffDistance = enabled); }
    }

    static void bardRange(int range) {
        if (range < 0 || range > NpcCombatProfile.MAX_BARD_DISTANCE) {
            throw new IllegalArgumentException("IJobBard: range must be 0-128");
        }
    }

    private static int currentJobType(XenoNpcEntity npc) {
        NpcCombatProfile profile = NpcCombatProfile.readCached(npc);
        return profile.jobEnabled ? XenoNpcJob.byId(profile.job).ordinal() : JobType.NONE;
    }

    public static final class Follower implements xenoapi.npcs.api.entity.data.role.IJobFollower {
        private final XenoNpcEntity npc;
        Follower(XenoNpcEntity npc) { this.npc = npc; }
        @Override public int getType() { return currentJobType(npc); }
        @Override public String getFollowing() { return read(npc, p -> p.followerName); }
        @Override public void setFollowing(String name) {
            String value = text("IJobFollower.setFollowing", name, 64);
            edit(npc, p -> p.followerName = value);
        }
        @Override public boolean isFollowing() {
            return net.bullettrain.xenopixelsmod.npc.job.NpcFollowerJob.followed(npc,
                    NpcCombatProfile.readCached(npc)) != null;
        }
        @Override public xenoapi.npcs.api.entity.ICustomNpc<?> getFollowingNpc() {
            var target = net.bullettrain.xenopixelsmod.npc.job.NpcFollowerJob.followed(npc,
                    NpcCombatProfile.readCached(npc));
            return target instanceof XenoNpcEntity nativeNpc ? new XenoNpcAdapter(nativeNpc) : null;
        }
    }
}

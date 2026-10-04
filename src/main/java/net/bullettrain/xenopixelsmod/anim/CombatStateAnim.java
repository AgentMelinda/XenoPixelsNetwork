package net.bullettrain.xenopixelsmod.anim;

import net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi;
import net.bullettrain.xenopixelsmod.combat.anim.TechniqueAnimSlot;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDmzAnim;
import net.bullettrain.xenopixelsmod.compat.npc.NpcMeleeDamage;
import net.bullettrain.xenopixelsmod.network.AnimClipsNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves and plays the clip bound to a combat / transform state.
 *
 * <p>Order: the entity's own override, then the server-wide technique bind, then the slot's
 * shipped default. Empty defaults ({@code TRANSFORM}, {@code PUNCH}) mean "keep the built-in
 * behaviour" rather than playing a blank name.
 */
public final class CombatStateAnim {
    private static final Map<UUID, Map<String, String>> PLAYERS = new ConcurrentHashMap<>();

    private CombatStateAnim() {}

    public static String[] slotNames() {
        TechniqueAnimSlot[] slots = TechniqueAnimSlot.values();
        String[] names = new String[slots.length];
        for (int i = 0; i < slots.length; i++) {
            names[i] = slots[i].name();
        }
        return names;
    }

    public static TechniqueAnimSlot slotOf(String raw) {
        return TechniqueAnimSlot.of(raw);
    }

    public static boolean set(LivingEntity entity, String slotName, String clip) {
        TechniqueAnimSlot slot = TechniqueAnimSlot.of(slotName);
        if (entity == null || slot == null) {
            return false;
        }
        String value = clip == null ? "" : clip.trim();
        if (entity instanceof ServerPlayer player) {
            if (value.isBlank()) {
                Map<String, String> map = PLAYERS.get(player.getUUID());
                if (map != null) {
                    map.remove(slot.name());
                }
            } else {
                PLAYERS.computeIfAbsent(player.getUUID(), ignored -> new ConcurrentHashMap<>())
                        .put(slot.name(), value);
            }
            return true;
        }
        if (!NpcCombatProfile.hasProfile(entity)) {
            return false;
        }
        if (slot == TechniqueAnimSlot.PUNCH) {
            return NpcMeleeDamage.persist(entity, value);
        }
        NpcCombatProfile profile = NpcCombatProfile.read(entity);
        profile.setStateClip(slot.name(), value);
        profile.write(entity);
        return true;
    }

    public static String get(LivingEntity entity, String slotName) {
        TechniqueAnimSlot slot = TechniqueAnimSlot.of(slotName);
        if (entity == null || slot == null) {
            return "";
        }
        String override = entityClip(entity, slot);
        return override == null ? "" : override;
    }

    public static boolean clear(LivingEntity entity, String slotName) {
        return set(entity, slotName, "");
    }

    public static void forget(UUID id) {
        if (id != null) {
            PLAYERS.remove(id);
        }
    }

    public static boolean hasCustom(LivingEntity entity, TechniqueAnimSlot slot) {
        if (slot == null) {
            return false;
        }
        String override = entityClip(entity, slot);
        if (override != null && !override.isBlank()) {
            return true;
        }
        String global = XenoTechniqueAnimBindings.clipFor(slot);
        return global != null && !global.isBlank();
    }

    /**
     * Animation name to play for {@code slot}, or the empty string when the slot has no default
     * and nothing is bound.
     */
    public static String resolve(LivingEntity entity, TechniqueAnimSlot slot) {
        if (slot == null) {
            return "";
        }
        String override = entityClip(entity, slot);
        if (override != null && !override.isBlank()) {
            return named(override);
        }
        return XenoTechniqueAnimBindings.resolve(slot);
    }

    public static boolean play(LivingEntity entity, TechniqueAnimSlot slot) {
        if (entity == null || slot == null) {
            return false;
        }
        String anim = resolve(entity, slot);
        if (anim == null || anim.isBlank()) {
            return false;
        }
        if (slot.hold()) {
            if (XenoAnimApi.resolve(anim) != null) {
                return XenoAnimApi.playClip(entity, anim, 1.0f, 0, true);
            }
            return NpcDmzAnim.broadcast(entity, anim, 1.0f, NpcDmzAnim.FLAG_HOLD);
        }
        // Fire slots must be one-shots. playClip(..., hold=true) is a KI hold;
        // DMZ charge/kick names have no authored length, so they would never release.
        return NpcDmzAnim.broadcast(entity, anim, 1.0f, 0);
    }

    public static boolean play(LivingEntity entity, String slotName) {
        return play(entity, TechniqueAnimSlot.of(slotName));
    }

    /** Server-wide bind. Empty {@code clip} restores the shipped default. */
    public static boolean bindGlobal(String slotName, String clip) {
        TechniqueAnimSlot slot = TechniqueAnimSlot.of(slotName);
        if (slot == null) {
            return false;
        }
        String value = clip == null ? "" : clip.trim();
        if (value.isBlank()) {
            XenoTechniqueAnimBindings.unbind(slot.name());
        } else {
            String bare = value.toLowerCase(Locale.ROOT).startsWith(XenoAnimApi.PREFIX)
                    ? XenoClipLibrary.sanitize(value.substring(XenoAnimApi.PREFIX.length()))
                    : XenoClipLibrary.sanitize(value);
            if (bare.isBlank()) {
                return false;
            }
            XenoTechniqueAnimBindings.bind(slot.name(), bare);
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            AnimClipsNetwork.broadcastBindings(server.getPlayerList().getPlayers());
        }
        return true;
    }

    private static String entityClip(LivingEntity entity, TechniqueAnimSlot slot) {
        if (entity instanceof ServerPlayer player) {
            Map<String, String> map = PLAYERS.get(player.getUUID());
            return map == null ? null : map.get(slot.name());
        }
        if (!NpcCombatProfile.hasProfile(entity)) {
            return null;
        }
        return NpcCombatProfile.read(entity).getStateClip(slot.name());
    }

    private static String named(String raw) {
        String resolved = XenoAnimApi.resolve(raw);
        return resolved != null ? resolved : raw.trim();
    }
}

package net.bullettrain.xenopixelsmod.client.compat.npc;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDmzAppearance;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class NpcAppearanceClient {
    public record State(String race, String formGroup, String form,
                        boolean hairEnabled, String hairCode, String hairColor,
                        int strength, int strikePower, int resistance,
                        int vitality, int kiPower, int energy, boolean authoritative,
                        int auraColor, float auraScale, NpcDmzAppearance appearance,
                        CompoundTag visualOptions,
                        String skinPlayer, String skinUrl, String skinUuid) {

        /**
         * Which DragonMineZ hair preset this NPC wears, or {@code 0} for its custom hair code.
         *
         * <p>Read out of {@link #visualOptions} rather than being a field of its own, because that
         * tag is already carried verbatim by the appearance packet — so the style reaches every
         * nearby client without widening the wire format.
         */
        public int hairStyleId() {
            return visualOptions == null ? 0
                    : Math.max(0, visualOptions.getInt("HairStyleId"));
        }
    }
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private NpcAppearanceClient() {}

    public static void apply(UUID id, String race, String formGroup, String form) {
        apply(id, race, formGroup, form, false, "", "", 0, 0, 0, 0, 0, 0, true,
                0, 1.0f, new CompoundTag(), new CompoundTag());
    }

    public static void apply(UUID id, String race, String formGroup, String form,
                             boolean hairEnabled, String hairCode, String hairColor,
                             int strength, int strikePower, int resistance,
                             int vitality, int kiPower, int energy, boolean authoritative,
                             int auraColor, float auraScale, CompoundTag dmzAppearance,
                             CompoundTag visualOptions) {
        apply(id, race, formGroup, form, hairEnabled, hairCode, hairColor,
                strength, strikePower, resistance, vitality, kiPower, energy, authoritative,
                auraColor, auraScale, dmzAppearance, visualOptions, "", "", "");
    }

    public static void apply(UUID id, String race, String formGroup, String form,
                             boolean hairEnabled, String hairCode, String hairColor,
                             int strength, int strikePower, int resistance,
                             int vitality, int kiPower, int energy, boolean authoritative,
                             int auraColor, float auraScale, CompoundTag dmzAppearance,
                             CompoundTag visualOptions,
                             String skinPlayer, String skinUrl, String skinUuid) {
        if (id != null) {
            STATES.put(id, new State(safe(race), safe(formGroup), safe(form),
                    hairEnabled, safe(hairCode), safe(hairColor),
                    strength, strikePower, resistance, vitality, kiPower, energy, authoritative,
                    auraColor & 0xFFFFFF, NpcCombatProfile.clampAuraScale(auraScale),
                    NpcDmzAppearance.fromTag(dmzAppearance),
                    visualOptions == null ? new CompoundTag() : visualOptions.copy(),
                    safe(skinPlayer), safe(skinUrl), safe(skinUuid)));
            NpcCombatProfile visual = new NpcCombatProfile();
            visual.applyVisualOptions(visualOptions);
            NpcTransformHairClient.onAppearance(id, formGroup, form,
                    visual.stackGroup, visual.stackId);
        }
    }

    public static State get(UUID id) {
        return id == null ? null : STATES.get(id);
    }

    public static void applyProfile(UUID id, NpcCombatProfile profile) {
        if (id == null || profile == null) return;
        apply(id, profile.raceId, profile.formGroup, profile.formId,
                profile.hairEnabled, profile.hairCode, profile.hairColor,
                profile.strength, profile.strikePower, profile.resistance,
                profile.vitality, profile.kiPower, profile.energy, profile.authoritative,
                profile.auraColor, profile.auraScale,
                (profile.appearance == null ? new NpcDmzAppearance() : profile.appearance).toTag(),
                profile.visualOptionsTag(),
                profile.skinPlayer, profile.skinUrl, "");
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public static void clear() {
        STATES.clear();
        NpcFullDmzRenderer.clearCache();
    }

    private static String safe(String value) { return value == null ? "" : value; }
}

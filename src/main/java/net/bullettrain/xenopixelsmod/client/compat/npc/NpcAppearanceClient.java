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
                        int vitality, int kiPower, int energy,
                        int auraColor, float auraScale, NpcDmzAppearance appearance,
                        CompoundTag visualOptions) {}
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private NpcAppearanceClient() {}

    public static void apply(UUID id, String race, String formGroup, String form) {
        apply(id, race, formGroup, form, false, "", "", 0, 0, 0, 0, 0, 0,
                0, 1.0f, new CompoundTag(), new CompoundTag());
    }

    public static void apply(UUID id, String race, String formGroup, String form,
                             boolean hairEnabled, String hairCode, String hairColor,
                             int strength, int strikePower, int resistance,
                             int vitality, int kiPower, int energy,
                             int auraColor, float auraScale, CompoundTag dmzAppearance,
                             CompoundTag visualOptions) {
        if (id != null) {
            STATES.put(id, new State(safe(race), safe(formGroup), safe(form),
                    hairEnabled, safe(hairCode), safe(hairColor),
                    strength, strikePower, resistance, vitality, kiPower, energy,
                    auraColor & 0xFFFFFF, NpcCombatProfile.clampAuraScale(auraScale),
                    NpcDmzAppearance.fromTag(dmzAppearance),
                    visualOptions == null ? new CompoundTag() : visualOptions.copy()));
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
                profile.vitality, profile.kiPower, profile.energy,
                profile.auraColor, profile.auraScale,
                (profile.appearance == null ? new NpcDmzAppearance() : profile.appearance).toTag(),
                profile.visualOptionsTag());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        STATES.clear();
        NpcFullDmzRenderer.clearCache();
    }

    private static String safe(String value) { return value == null ? "" : value; }
}

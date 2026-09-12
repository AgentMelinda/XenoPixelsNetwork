package net.bullettrain.xenopixelsmod.client.anim;

import net.bullettrain.xenopixelsmod.anim.XenoTechniqueAnimBindings;
import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;
import net.bullettrain.xenopixelsmod.combat.anim.TechniqueAnimSlot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Client copy of the server's slot map, applied on join and after a bind. */
public final class XenoTechniqueAnimBindingsClient {
    private static final Map<String, String> BINDINGS = new LinkedHashMap<>();
    private static volatile boolean applied;

    private XenoTechniqueAnimBindingsClient() {}

    public static void apply(String json) {
        applied = true;
        BINDINGS.clear();
        BINDINGS.putAll(XenoTechniqueAnimBindings.parse(json));
    }

    public static void clear() {
        applied = false;
        BINDINGS.clear();
    }

    public static boolean applied() {
        return applied;
    }

    public static String clipFor(String slotName) {
        String slot = XenoTechniqueAnimBindings.normalizeSlot(slotName);
        return slot == null ? null : BINDINGS.get(slot);
    }

    public static String clipFor(TechniqueAnimSlot slot) {
        return slot == null ? null : BINDINGS.get(slot.name());
    }

    public static String clipFor(Bt3AnimationIntent intent) {
        return intent == null ? null : BINDINGS.get(intent.name());
    }

    public static String resolve(TechniqueAnimSlot slot) {
        if (slot == null) return "";
        String clip = clipFor(slot);
        if (clip == null || clip.isBlank()) return slot.defaultAnim();
        return XenoAnimClip.ANIMATION_PREFIX + clip;
    }

    public static Map<String, String> all() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(BINDINGS));
    }
}

package net.bullettrain.xenopixelsmod.client.combat.v2;

import com.dragonminez.client.util.KeyBinds;
import com.mojang.blaze3d.platform.InputConstants;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/**
 * The XenoCombat v2 key mappings.
 *
 * <p>The layout deliberately shares keys with Minecraft (Q, right and middle mouse): outside
 * a fight those keys keep their ordinary meaning, and {@link CombatStance} decides when the
 * combat meaning takes over. So these mappings live in a conflict context of their own that only
 * conflicts with itself, or the Controls screen would paint half the layout red against keys it
 * is designed to share.
 *
 * <p>They are polled physically by {@link V2InputLayer} rather than through
 * {@code KeyMapping.isDown()}, the same way the rest of this mod reads keys that share a button,
 * so which meaning fires never depends on how the loader resolves two mappings on one key.
 *
 * <p>The dedicated vanish key sits on B because V is DragonMineZ's stats menu. Double-tapping
 * either side movement key also activates vanish, matching the other controllers.
 */
public final class V2Keys {

    /** A context that is in game, and collides only with other v2 mappings. */
    public static final IKeyConflictContext CONTEXT = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return Minecraft.getInstance().screen == null;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this;
        }
    };

    private static final String CATEGORY = "key.categories.xenopixelsmod.v2";
    /** Bumped when a default moves, so profiles saved with the old default are moved once. */
    private static final int LAYOUT = 3;

    public static final KeyMapping LIGHT = mouse("v2_light", GLFW.GLFW_MOUSE_BUTTON_LEFT);
    public static final KeyMapping HEAVY = mouse("v2_heavy", GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    public static final KeyMapping GUARD = key("v2_guard", GLFW.GLFW_KEY_R);
    public static final KeyMapping VANISH = key("v2_vanish", GLFW.GLFW_KEY_B);
    public static final KeyMapping KI_BLAST = key("v2_ki_blast", GLFW.GLFW_KEY_Q);
    public static final KeyMapping LOCK = mouse("v2_lock", GLFW.GLFW_MOUSE_BUTTON_MIDDLE);

    private V2Keys() {}

    private static KeyMapping key(String name, int glfw) {
        return new KeyMapping("key.xenopixelsmod." + name, CONTEXT,
                InputConstants.Type.KEYSYM, glfw, CATEGORY);
    }

    private static KeyMapping mouse(String name, int glfw) {
        return new KeyMapping("key.xenopixelsmod." + name, CONTEXT,
                InputConstants.Type.MOUSE, glfw, CATEGORY);
    }

    /** The physical state of a mapping's current key, whatever else is bound to that key. */
    public static boolean down(KeyMapping mapping) {
        if (mapping == null || mapping.isUnbound()) return false;
        try {
            return KeyBinds.isPhysicallyDown(mapping);
        } catch (Throwable t) {
            return mapping.isDown();
        }
    }

    /** Whether two mappings currently sit on the same physical key. */
    public static boolean sameKey(KeyMapping a, KeyMapping b) {
        return a != null && b != null && !a.isUnbound() && !b.isUnbound()
                && a.getKey().equals(b.getKey());
    }

    private static boolean on(KeyMapping mapping, int glfwKey) {
        return !mapping.isUnbound() && mapping.getKey().getType() == InputConstants.Type.KEYSYM
                && mapping.getKey().getValue() == glfwKey;
    }

    /**
     * Moves a profile off superseded v2 defaults, once per layout version.
     *
     * <p>Minecraft saves every binding, so changing a default in code changes nothing for anyone
     * who already has an options file: their vanish would stay on V, opening DragonMineZ's stats
     * menu on every vanish and their guard would stay on the inventory key E. Only a binding
     * still sitting on its old default is touched; one the player moved themselves is theirs.
     */
    public static void migrateLayout(Minecraft mc) {
        if (XenoClientConfig.v2KeyLayout >= LAYOUT) return;
        boolean changed = false;
        if (XenoClientConfig.v2KeyLayout < 2 && on(VANISH, GLFW.GLFW_KEY_V)) {
            VANISH.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_B));
            changed = true;
        }
        if (on(GUARD, GLFW.GLFW_KEY_E) && GUARD.getKeyModifier() == KeyModifier.NONE) {
            GUARD.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_R));
            changed = true;
        }
        if (changed) {
            KeyMapping.resetMapping();
            mc.options.save();
        }
        XenoClientConfig.v2KeyLayout = LAYOUT;
        XenoClientConfig.save();
    }

    @EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(LIGHT);
            event.register(HEAVY);
            event.register(GUARD);
            event.register(VANISH);
            event.register(KI_BLAST);
            event.register(LOCK);
        }
    }
}

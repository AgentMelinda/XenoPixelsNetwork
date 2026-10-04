package net.bullettrain.xenopixelsmod.client.ui.atlas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * The click every atlas control makes.
 *
 * <p>DragonMineZ's own menus click, and these screens are meant to sit alongside them, so they use
 * DMZ's sound rather than vanilla's. {@code ui_menu_switch} is a real entry in DragonMineZ's
 * {@code sounds.json} (151 entries; this one is listed with subtitle
 * {@code sounds.dragonminez.ui_menu_switch}).
 *
 * <p>Looked up through the registry rather than held as a constant, because DMZ is an optional
 * companion at runtime: an absent sound falls back to the vanilla UI click instead of throwing.
 * That is the same approach {@code Bt3CombatClient.playLocalIt} already takes for DMZ's evasion
 * sounds.
 */
public final class AtlasSound {

    private static final ResourceLocation DMZ_CLICK =
            ResourceLocation.fromNamespaceAndPath("dragonminez", "ui_menu_switch");

    /** DMZ's heavier confirm sound, for actions that commit something. */
    private static final ResourceLocation DMZ_CONFIRM =
            ResourceLocation.fromNamespaceAndPath("dragonminez", "confirm_menu");

    private AtlasSound() {
    }

    /** The ordinary click, for a button, tab, toggle or cycle. */
    public static void click() {
        play(DMZ_CLICK, SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
    }

    /** A weightier click for save, delete confirm and similar committing actions. */
    public static void confirm() {
        play(DMZ_CONFIRM, SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
    }

    private static void play(ResourceLocation dmzId, SoundEvent fallback, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) {
            return;
        }
        SoundEvent dmz = BuiltInRegistries.SOUND_EVENT.get(dmzId);
        SoundEvent sound = dmz != null ? dmz : fallback;
        if (sound == null) {
            return;
        }
        mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
    }
}

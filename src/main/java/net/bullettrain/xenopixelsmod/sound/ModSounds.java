package net.bullettrain.xenopixelsmod.sound;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Mod sound events.
 *
 * <p>Registered here, but <b>played through {@code XenoServerConfig.vanishSoundOut/In}</b> rather
 * than referenced directly from the combat code. That indirection is what lets a server owner
 * point vanish at a different sound — or back at the DragonMineZ default — without a rebuild.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, XenoPixelsMod.MOD_ID);

    /**
     * The vanish crack, used for both departure and arrival.
     *
     * <p>The event id is {@code vanish} while the file behind it is {@code sounds/vanish_out.ogg} —
     * {@code sounds.json} maps the two, and the same clip serves both ends of the teleport, so
     * naming the event after only one of them would be misleading.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> VANISH = register("vanish");

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}

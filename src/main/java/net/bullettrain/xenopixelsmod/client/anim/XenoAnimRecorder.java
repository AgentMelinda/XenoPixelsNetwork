package net.bullettrain.xenopixelsmod.client.anim;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(value = Dist.CLIENT, modid = net.bullettrain.xenopixelsmod.XenoPixelsMod.MOD_ID)
public final class XenoAnimRecorder {
    private static XenoAnimClip clip;
    private static boolean recording;
    private static int tick;

    private XenoAnimRecorder() {}

    public static boolean recording() {
        return recording;
    }

    public static XenoAnimClip current() {
        return clip;
    }

    public static void start(String name) {
        clip = new XenoAnimClip(name);
        tick = 0;
        recording = true;
        // Ask the render mixin to start copying the finished pose out of the model.
        net.bullettrain.xenopixelsmod.client.anim.studio.StudioBoneSampler.setWanted(true);
    }

    public static XenoAnimClip stop() {
        recording = false;
        net.bullettrain.xenopixelsmod.client.anim.studio.StudioBoneSampler.setWanted(false);
        return clip;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!recording || clip == null) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        XenoAnimClip.Frame frame = new XenoAnimClip.Frame(
                tick,
                player.yBodyRot,
                player.getXRot(),
                player.attackAnim,
                player.isShiftKeyDown());
        if (net.bullettrain.xenopixelsmod.client.anim.studio.StudioBoneSampler.hasSample()) {
            // A real body pose, sampled off the model the renderer just finished with.
            clip.frames.add(frame);
            clip.keyPose(tick / (double) XenoAnimClip.TICKS_PER_SECOND,
                    net.bullettrain.xenopixelsmod.client.anim.studio.StudioBoneSampler.take());
        } else {
            // No model to sample - a player with no DragonMineZ character, say. Fall back to the
            // three values that can be read straight off the entity.
            clip.add(frame);
        }
        tick++;
    }
}

package net.bullettrain.xenopixelsmod.client.space;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.space.SpaceTransitionDimensions;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.DimensionTransitionScreenManager.ReceivingLevelScreenFactory;
import net.neoforged.neoforge.client.event.RegisterDimensionTransitionScreenEvent;

import java.util.function.BooleanSupplier;

/**
 * Makes the altitude handoff read as continuous flight rather than a teleport, by replacing the vanilla
 * "Downloading terrain" screen with an invisible one for every crossing that <b>enters or leaves a Voidsea
 * host</b>.
 *
 * <p><b>What this does and does not claim.</b> It stops a loading screen from ever being visible. It does
 * not stop the {@code ClientLevel} rebuild or the chunk wait underneath — vanilla still tears the old
 * client level down and the game still ticks on until the destination chunks arrive. The difference the
 * player sees is only that the last rendered frame, rather than a portal/water/black panel with text, is
 * what fills those frames. The honest wording is "no visible screen", never "no reload".
 *
 * <p><b>Why it works.</b> {@code GameRenderer.render} draws the level whenever {@code minecraft.level !=
 * null} and only <em>then</em> paints the active screen on top of it; a screen whose {@code render} draws
 * nothing leaves that already-drawn level on screen. {@link ReceivingLevelScreen}'s whole visible product
 * is its {@code render} (the centered text) and its {@code renderBackground} (portal/panorama/blur), so a
 * subclass that overrides both to no-ops is invisible. Its inherited {@code tick()} is deliberately kept:
 * that is what closes the screen the instant the destination level is received, so the exemption is self-
 * clearing and bounded by vanilla's own 30 s ceiling rather than anything we time.
 *
 * <p><b>Why only the host dimensions are registered.</b> A handoff always crosses a host boundary — you
 * leave a body for the Voidsea, or leave the Voidsea for a body — so attaching the invisible screen as
 * both the incoming <em>and</em> outgoing effect of each host catches every crossing, including planet to
 * planet routed through the Voidsea, without touching a normal overworld↔nether↔end trip. Registering it
 * against the planets themselves would suppress the screen for arbitrary third-party teleports into those
 * dimensions, which is a broader change to vanilla behaviour than the feature warrants.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SpaceTransitionScreens {
    /** The shipped Voidsea host, and the visual spike host the handoff gate is exercised in. */
    private static final ResourceKey<Level> VOIDSEA = key("xenopixelsmod", "voidsea");
    private static final ResourceKey<Level> SPIKE_ORBIT = key("xenospace_spike", "orbit");
    /** Dimensions the shipped planet entries and the optional spike pack name as planet surfaces. */
    private static final java.util.List<ResourceKey<Level>> PLANET_SURFACES = SpaceTransitionDimensions.PLANET_SURFACES
            .stream().map(id -> ResourceKey.create(Registries.DIMENSION, id)).toList();

    private SpaceTransitionScreens() {}

    private static ResourceKey<Level> key(String namespace, String path) {
        return ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    /**
     * Registers the invisible screen as the incoming and outgoing effect of both hosts. The registration
     * is unconditional and happens once at mod setup: the screen can only be reached by an actual crossing
     * into or out of a host, which in turn only happens when the handoff is running (or someone is
     * teleported there), so there is no config to consult here and nothing drawn when no crossing occurs.
     */
    @SubscribeEvent public static void register(RegisterDimensionTransitionScreenEvent event) {
        ReceivingLevelScreenFactory clear = ClearScreen::new;
        for (ResourceKey<Level> host : java.util.List.of(VOIDSEA, SPIKE_ORBIT)) {
            event.registerIncomingEffect(host, clear);
            event.registerOutgoingEffect(host, clear);
        }
        // Planet dimensions, behind spaceHidePlanetLoadScreen. Registration happens once at mod setup,
        // before any chart data is loaded, so the shipped and spike planet surfaces are named here; the
        // flag is read when the screen opens, so switching it off restores the vanilla screen at once.
        // This deliberately widens the scope described above: any arrival in or departure from these
        // dimensions, including ordinary portals, shows the last frame instead of the loading panel.
        ReceivingLevelScreenFactory planet = (levelReceived, reason) ->
                XenoClientConfig.spaceHidePlanetLoadScreen
                        ? new ClearScreen(levelReceived, reason)
                        : new ReceivingLevelScreen(levelReceived, reason);
        for (ResourceKey<Level> surface : PLANET_SURFACES) {
            event.registerIncomingEffect(surface, planet);
            event.registerOutgoingEffect(surface, planet);
            // NeoForge resolves pair > incoming > outgoing, so the planet's incoming factory would
            // otherwise win over the host's outgoing one and bring the vanilla screen back to a
            // host-to-planet descent whenever the flag is off.
            for (ResourceKey<Level> host : java.util.List.of(VOIDSEA, SPIKE_ORBIT))
                event.registerConditionalEffect(surface, host, clear);
        }
    }

    /**
     * A {@link ReceivingLevelScreen} that draws nothing. It exists purely so the vanilla screen slot is
     * occupied by a transparent frame instead of the opaque loading panel, keeping the world visible across
     * the crossing while the level swap completes underneath.
     */
    private static final class ClearScreen extends ReceivingLevelScreen {
        ClearScreen(BooleanSupplier levelReceived, Reason reason) {
            super(levelReceived, reason);
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            // Intentionally empty: neither the "Downloading terrain" text nor super.render's background.
        }

        @Override
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            // Intentionally empty: no portal, panorama, or blur over the still-rendered level.
        }
    }
}

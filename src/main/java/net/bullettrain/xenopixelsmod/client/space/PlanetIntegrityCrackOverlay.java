package net.bullettrain.xenopixelsmod.client.space;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;

/** Adds one shared cached crack-map pass over already textured scarred/critical planet spheres. */
final class PlanetIntegrityCrackOverlay {
    private static final Set<net.minecraft.resources.ResourceLocation> REGISTERED = new HashSet<>();
    private static final Matrix4f MODEL = new Matrix4f();

    private PlanetIntegrityCrackOverlay() {}

    static void draw(RenderLevelStageEvent event, String bodyId, PlanetIntegrityRules.Stage stage,
                     Matrix4f model, VertexBuffer sphere) {
        if (!PlanetIntegritySurfaceTint.hasCrackOverlay(stage)) return;
        boolean critical = stage == PlanetIntegrityRules.Stage.CRITICAL;
        var location = critical ? PlanetIntegrityCrackTexture.CRITICAL : PlanetIntegrityCrackTexture.SCARRED;
        var manager = Minecraft.getInstance().getTextureManager();
        if (!REGISTERED.contains(location)) {
            manager.register(location, new PlanetIntegrityCrackTexture(critical));
            REGISTERED.add(location);
        }

        float red = 1.0f, green, blue, alpha;
        if (critical) {
            green = 0.12f;
            blue = 0.025f;
            alpha = 0.92f;
        } else {
            green = 0.34f;
            blue = 0.06f;
            alpha = 0.76f;
        }
        RenderSystem.setShaderTexture(0, location);
        RenderSystem.setShaderColor(red, green, blue, alpha);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        MODEL.set(model).rotateY((float) PlanetIntegrityCrackPattern.rotationRadians(bodyId));
        sphere.bind();
        sphere.drawWithShader(MODEL, event.getProjectionMatrix(), GameRenderer.getPositionTexColorShader());
    }
}

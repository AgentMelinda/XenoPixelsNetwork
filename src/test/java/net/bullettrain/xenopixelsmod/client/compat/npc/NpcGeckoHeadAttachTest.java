package net.bullettrain.xenopixelsmod.client.compat.npc;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcGeckoHeadAttachTest {
    @Test
    void resolvesSurrogateThenAppearanceThenHead() {
        assertEquals("neck", NpcGeckoHeadAttach.resolveBone("neck", "Head"));
        assertEquals("Head", NpcGeckoHeadAttach.resolveBone("", "Head"));
        assertEquals("head", NpcGeckoHeadAttach.resolveBone("  ", " "));
        assertEquals("head", NpcGeckoHeadAttach.resolveBone(null, null));
    }

    @Test
    void matchesHeadBoneIgnoringCase() {
        assertTrue(NpcGeckoHeadAttach.matches("head", "Head"));
        assertTrue(NpcGeckoHeadAttach.matches("bipedHead", "bipedHead"));
    }

    @Test
    void geckoLibHasVerifiedBoneMatrixMethods() throws Exception {
        Class<?> renderUtil = Class.forName("software.bernie.geckolib.util.RenderUtil");
        Class<?> pose = Class.forName("com.mojang.blaze3d.vertex.PoseStack");
        Class<?> bone = Class.forName("software.bernie.geckolib.cache.object.GeoBone");
        Method prep = renderUtil.getMethod(NpcGeckoHeadAttach.RENDERER_BONE_MATRIX, pose, bone);
        Method pivot = renderUtil.getMethod(NpcGeckoHeadAttach.LAYER_PIVOT, pose, bone);
        assertNotNull(prep);
        assertNotNull(pivot);
    }
}

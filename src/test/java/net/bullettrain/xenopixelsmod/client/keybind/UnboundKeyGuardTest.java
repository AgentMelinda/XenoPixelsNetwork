package net.bullettrain.xenopixelsmod.client.keybind;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guard that makes an unbound key safe to ask about ({@code InputConstantsUnboundKeyMixin}):
 * that the method it hooks is still the one the crash went through, and that it is registered.
 *
 * <p>The crash it answers, from {@code crash-2026-10-06_13.52.33-client.txt}:
 * <pre>
 * IllegalStateException: Encountered GL error off-thread @ Render: 65539: Invalid key -1
 *   at GLFW.glfwGetKey
 *   at InputConstants.isKeyDown
 *   at com.simibubi.create.AllKeys.isKeyDown
 *   at com.simibubi.create.AllKeys.shiftDown
 *   at com.simibubi.create.content.logistics.filter.FilterItem.appendHoverText
 *   ... SessionSearchTrees.updateCreativeTooltips (worker thread)
 * </pre>
 */
class UnboundKeyGuardTest {

    private static MethodNode isKeyDown() throws Exception {
        try (var stream = UnboundKeyGuardTest.class.getClassLoader()
                .getResourceAsStream("com/mojang/blaze3d/platform/InputConstants.class")) {
            assertNotNull(stream, "InputConstants is not on the test classpath");
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node.methods.stream()
                    .filter(m -> m.name.equals("isKeyDown") && m.desc.equals("(JI)Z"))
                    .findFirst().orElse(null);
        }
    }

    @Test
    void theHookedMethodExistsAndIsStatic() throws Exception {
        MethodNode method = isKeyDown();
        assertNotNull(method, "InputConstants.isKeyDown(long, int) is gone");
        assertTrue((method.access & Opcodes.ACC_STATIC) != 0);
    }

    /** It hands the key code to GLFW unchecked, which is what makes -1 an error. */
    @Test
    void itStillPassesTheKeyStraightToGlfw() throws Exception {
        boolean callsGlfw = false;
        for (AbstractInsnNode insn : isKeyDown().instructions) {
            if (insn instanceof MethodInsnNode call && call.name.equals("glfwGetKey")) callsGlfw = true;
        }
        assertTrue(callsGlfw, "isKeyDown no longer asks GLFW directly; the guard may not be needed");
    }

    @Test
    void theGuardIsAClientMixinAndTurnsNegativeKeysAway() throws Exception {
        String config = Files.readString(RepoRoot.of("src/main/resources", "xenopixelsmod.mixins.json"),
                StandardCharsets.UTF_8);
        int client = config.indexOf("\"client\"");
        int guard = config.indexOf("\"client.InputConstantsUnboundKeyMixin\"");
        assertTrue(client > 0 && guard > client, "the guard must be in the client list: the class is client-only");

        String mixin = Files.readString(RepoRoot.of(
                "src/main/java/net/bullettrain/xenopixelsmod/mixin/client", "InputConstantsUnboundKeyMixin.java"),
                StandardCharsets.UTF_8);
        assertTrue(mixin.contains("method = \"isKeyDown\""));
        assertTrue(mixin.contains("if (key < 0) cir.setReturnValue(false);"));
    }
}

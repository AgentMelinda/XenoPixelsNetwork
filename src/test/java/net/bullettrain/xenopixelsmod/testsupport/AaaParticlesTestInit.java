package net.bullettrain.xenopixelsmod.testsupport;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Test-only: starts AAA Particles' platform helper the way the game does.
 *
 * <p>AAA Particles mixes into {@code ResourceLocation.validPathChar} and, on the first malformed id,
 * looks up {@code PlatformMethods} with {@code ServiceLoader.load}, i.e. the thread context class
 * loader. In a game that is the mod layer; in the unit-test worker it is the JVM app loader, the
 * lookup fails, and the class is left unusable for the rest of the run (every test that validates a
 * bad id then threw NoClassDefFoundError). Initialising it once with the mod layer as the context
 * loader, before the first test class, makes tests match the game.
 */
public final class AaaParticlesTestInit implements BeforeAllCallback {
    private static volatile boolean done;

    @Override
    public void beforeAll(ExtensionContext context) {
        if (done) return;
        synchronized (AaaParticlesTestInit.class) {
            if (done) return;
            ClassLoader modLayer = context.getRequiredTestClass().getClassLoader();
            Thread thread = Thread.currentThread();
            ClassLoader previous = thread.getContextClassLoader();
            try {
                thread.setContextClassLoader(modLayer);
                Class.forName("mod.chloeprime.aaaparticles.PlatformMethods", true, modLayer)
                        .getMethod("get").invoke(null);
            } catch (ReflectiveOperationException | LinkageError e) {
                // Absent or changed library: leave it; the affected tests will say so.
            } finally {
                thread.setContextClassLoader(previous);
                done = true;
            }
        }
    }
}

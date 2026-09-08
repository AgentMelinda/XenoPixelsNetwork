package net.bullettrain.xenopixelsmod.client.pad;

import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.api.entrypoint.ControlifyEntrypoint;
import dev.isxander.controlify.api.entrypoint.InitContext;
import dev.isxander.controlify.api.entrypoint.PreInitContext;

/**
 * How Controlify finds this mod's gamepad bindings.
 *
 * <p>Discovery on NeoForge is a plain {@link java.util.ServiceLoader} lookup — Controlify's
 * {@code NeoforgePlatformMainImpl.applyToControlifyEntrypoint} is literally
 * {@code ServiceLoader.load(ControlifyEntrypoint.class).forEach(...)} — so this class is named in
 * {@code META-INF/services/dev.isxander.controlify.api.entrypoint.ControlifyEntrypoint}. There is
 * no annotation or IMC route, and nothing in this mod calls it.
 *
 * <p>That is also what keeps Controlify optional: with Controlify absent nothing performs the
 * service lookup, so this class and everything it touches is never loaded, and the mod behaves
 * exactly as it did before gamepad support existed.
 */
public final class XenoControlifyEntrypoint implements ControlifyEntrypoint {

    @Override
    public void onControlifyPreInit(PreInitContext context) {
        // Bindings must be declared in pre-init: Controlify reads its saved bind configuration
        // straight after this, and a binding registered later would have nothing to restore from.
        XenoPadBinds.register(context.bindings());
    }

    @Override
    public void onControlifyInit(InitContext context) {
        // Nothing to do. Bindings are declarative and the state reads are pulled, not pushed.
    }

    @Override
    public void onControllersDiscovered(ControlifyApi api) {
        // Nothing to do. XenoPadBinds asks for the current controller when it needs one, so a
        // controller connecting or disconnecting mid-session needs no bookkeeping here.
    }
}

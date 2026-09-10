/**
 * The public API of XenoPixelsNetwork.
 *
 * <p>Everything in this package and its subpackages is intended for addon authors and is kept
 * stable. Everything <em>outside</em> it is internal: it is public only because the mod is built
 * from a single source set, and it may be renamed, moved or deleted without notice.
 *
 * <h2>Depending on this API</h2>
 *
 * <p>Gate on the presence of {@link net.bullettrain.xenopixelsmod.api.XenoPixelsApi} rather than on
 * the {@code xenopixelsmod} mod id, and check {@link
 * net.bullettrain.xenopixelsmod.api.XenoPixelsApi#API_VERSION}. This mod already uses that rule for
 * its own optional dependencies - {@code ConditionalMixinPlugin} requires an actual API class to be
 * loadable before applying the YAWP compatibility mixins, because a mod id on its own says nothing
 * about which version is installed.
 *
 * <h2>What is deliberately not here</h2>
 *
 * <p>Some parts of the mod cannot be opened up without changing their shape, and this API does not
 * pretend otherwise:
 *
 * <ul>
 *   <li>New BT3 animation <em>intents</em>. {@code Bt3AnimationCatalog} is keyed by an enum, so a
 *       new intent needs a recompile. Registering clips and playable names for existing intents is
 *       supported.
 *   <li>Server config keys. {@code XenoServerConfigKeys} registers into a private table whose
 *       values must be fields on {@code XenoServerConfig}.
 *   <li>Permission nodes. {@code XenoPermissions} is a flat list of constants.
 * </ul>
 *
 * <h2>DragonMineZ</h2>
 *
 * <p>DragonMineZ is a required dependency of this mod, and it has its own event bus:
 * {@code com.dragonminez.common.events.DMZEvent}. That is the correct place to hook DragonMineZ
 * itself - this API does not re-fire those events. The {@link net.bullettrain.xenopixelsmod.api.dmz}
 * package only adds the small amount that is genuinely missing.
 */
package net.bullettrain.xenopixelsmod.api;

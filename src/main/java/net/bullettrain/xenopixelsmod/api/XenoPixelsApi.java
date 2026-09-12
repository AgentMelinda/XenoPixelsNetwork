package net.bullettrain.xenopixelsmod.api;

/**
 * Entry point and version gate for the XenoPixelsNetwork API.
 *
 * <p>Addons should test for this class rather than for the {@code xenopixelsmod} mod id, and should
 * check {@link #API_VERSION} before using anything below. A mod id says only that some version is
 * installed; the presence of a class and a version number says what it can actually do. XenoPixels
 * applies the same rule to its own optional dependencies.
 *
 * <pre>{@code
 * private static boolean xenoPixelsAvailable() {
 *     try {
 *         return XenoPixelsApi.isAtLeast(1);
 *     } catch (Throwable notInstalled) {
 *         return false;
 *     }
 * }
 * }</pre>
 *
 * <h2>What the API covers</h2>
 *
 * <p>Events, in {@code net.bullettrain.xenopixelsmod.api.event}, all posted on
 * {@code NeoForge.EVENT_BUS} on the server:
 *
 * <ul>
 *   <li>{@code SparkingEvent} - the BT3 Sparking meter and buff. Activation is cancellable and its
 *       duration can be changed.
 *   <li>{@code RushEvent} - cinematic rushes. Start is cancellable; each landed blow reports its
 *       damage.
 *   <li>{@code CloneEvent} - multiform splitting and reuniting. Splitting is cancellable.
 *   <li>{@code ZanzokenEvent} - a dodge about to succeed. Cancellable, and a cancelled dodge still
 *       falls through to guarding.
 *   <li>{@code StrikeInterceptEvent} - a DragonMineZ technique slot about to be diverted into a
 *       XenoPixels move. Cancelling hands the slot back to DragonMineZ.
 * </ul>
 *
 * <h2>Adding your own content</h2>
 *
 * <p>{@code net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi} plays animation clips on an entity -
 * both the ones this mod ships and the ones authored in the Xeno Anim Studio and published to the
 * server. Overloads take a speed, an optional duration in ticks, and a hold-last-frame flag;
 * players as well as Full DragonMineZ NPCs can be targeted. {@code
 * net.bullettrain.xenopixelsmod.api.event.AnimInstructionEvent} fires when a clip reaches an
 * instruction keyframe. Both are additive, so {@link #API_VERSION} did not move; test for the
 * {@code XenoAnimApi} class itself the way this class asks you to test for this one.
 *
 * <p>{@code net.bullettrain.xenopixelsmod.api.registry.RushRegistry} takes cinematic rushes for a
 * form or a race. Read its notes on substring matching before choosing your aliases.
 * Addons that need packets use {@code net.bullettrain.xenopixelsmod.api.network.AddonNetwork}; it
 * owns a separate protocol and collision-safe namespaced registrations.
 *
 * <h2>Working with DragonMineZ</h2>
 *
 * <p>{@code net.bullettrain.xenopixelsmod.api.dmz} carries the parts that are easy to get wrong:
 *
 * <ul>
 *   <li>{@code DmzAccess} - reads a player's race, form, battle power and skills without the
 *       four-step capability dance, and reports absence instead of returning a misleading zero.
 *   <li>{@code DmzForms} - form definitions without needing a player, because forms are
 *       server-editable configuration rather than code.
 *   <li>{@code DmzSync} - pushes a server-side change to clients. DragonMineZ state does not
 *       replicate on its own, and forgetting this is the most common addon bug.
 * </ul>
 *
 * <p>To <em>react</em> to DragonMineZ, subscribe to
 * {@code com.dragonminez.common.events.DMZEvent} directly. It already carries stat, ki, damage,
 * form, quest and player-data events, and XenoPixels does not re-fire them.
 *
 * <p>One thing worth knowing about {@code DMZEvent.FormChangeEvent}: it is posted from two
 * different places. Transforming posts it with the real new group and form; returning to base
 * posts it with both of those <em>empty</em>. So an empty new form means untransformed, not
 * missing data.
 *
 * <p>See the package documentation for what is deliberately <em>not</em> exposed.
 */
public final class XenoPixelsApi {

	/**
	 * The API generation. Incremented only when something already published here changes in a way
	 * that could break a compiled addon; purely additive releases leave it alone.
	 */
	public static final int API_VERSION = 1;

	private XenoPixelsApi() {}

	/** True when the installed API is at least {@code version}. */
	public static boolean isAtLeast(int version) {
		return API_VERSION >= version;
	}
}

package xenoapi.npcs.api.event;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import xenoapi.npcs.api.IWorld;
import xenoapi.npcs.api.entity.IEntity;

/**
 * Called for most NeoForge events. For the events I use the NeoForge name and make the first letter lowercase. <br>
 * Eg: <br>
 * - EntityEvent.EntityJoinLevelEvent becomes entityEventEntityJoinLevelEvent <br>
 * - PlayerEvent.StartTracking becomes playerEventStartTracking <br>
 * - etc <br>
 *
 * Note that these events can change anytime and that I have no control over these. Use at own risk
 *
 */
public class ForgeEvent extends CustomNPCsEvent implements ICancellableEvent {
	public final Event event;
	public ForgeEvent(Event event) {
		this.event = event;
	}

	/**
	 * @return true when the wrapped NeoForge event can be canceled
	 */
	public boolean isCancelable() {
		return event instanceof ICancellableEvent;
	}
	@Override
	public boolean isCanceled() {
		return event instanceof ICancellableEvent cancellable && cancellable.isCanceled();
	}
	@Override
	public void setCanceled(boolean cancel) {
		if(event instanceof ICancellableEvent cancellable) {
			cancellable.setCanceled(cancel);
		}
		else if(cancel) {
			throw new UnsupportedOperationException("Attempted to cancel an uncancelable event: " + event.getClass().getName());
		}
	}

	/**
	 * init <br>
	 * The init event has no NeoForge event
	 */
	public static class InitEvent extends ForgeEvent {
		public InitEvent() {
			super(new NoForgeEvent());
		}
	}

	/**
	 * Placeholder for events which have no NeoForge counterpart, NeoForge's Event is abstract
	 */
	private static final class NoForgeEvent extends Event {
	}

	/**
	 * This event is used for every NeoForge event which extends net.neoforged.neoforge.event.entity.EntityEvent <br>
	 * (this includes LivingEvent and PlayerEvent)
	 */
	public static class EntityEvent extends ForgeEvent implements ICancellableEvent {
		public final IEntity entity;

		public EntityEvent(net.neoforged.neoforge.event.entity.EntityEvent event, IEntity entity) {
			super(event);
			this.entity = entity;
		}

	}

	/**
	 * This event is used for every NeoForge event which extends net.neoforged.neoforge.event.level.LevelEvent
	 */
	public static class LevelEvent extends ForgeEvent implements ICancellableEvent {
		public final IWorld world;

		public LevelEvent(net.neoforged.neoforge.event.level.LevelEvent event, IWorld world) {
			super(event);
			this.world = world;
		}

	}
}

package xenoapi.npcs.api.event;

import xenoapi.npcs.api.handler.IFactionHandler;
import xenoapi.npcs.api.handler.IRecipeHandler;

public class HandlerEvent {

	public static class RecipesLoadedEvent extends CustomNPCsEvent{
		public final IRecipeHandler handler;
		
		public RecipesLoadedEvent(IRecipeHandler handler) {
			this.handler = handler;
		}
	}

	public static class FactionsLoadedEvent extends CustomNPCsEvent{
		public final IFactionHandler handler;
		
		public FactionsLoadedEvent(IFactionHandler handler) {
			this.handler = handler;
		}
	}
}

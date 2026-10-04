package xenoapi.npcs.api.event;

import net.neoforged.bus.api.Event;
import xenoapi.npcs.api.NpcAPI;

public class CustomNPCsEvent extends Event {
	public final NpcAPI API = NpcAPI.Instance();
}

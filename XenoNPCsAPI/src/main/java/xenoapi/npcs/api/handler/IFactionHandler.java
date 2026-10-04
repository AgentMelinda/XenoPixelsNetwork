package xenoapi.npcs.api.handler;

import java.util.List;

import xenoapi.npcs.api.handler.data.IFaction;

public interface IFactionHandler {
	
	public List<IFaction> list();
	
	public IFaction delete(int id);
	
	/**
	 * Example: create("Bandits", 0xFF0000)
	 */
	public IFaction create(String name, int color);
	
	public IFaction get(int id);
}

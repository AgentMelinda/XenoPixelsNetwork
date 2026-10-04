package xenoapi.npcs.api.handler;

import xenoapi.npcs.api.IWorld;
import xenoapi.npcs.api.entity.IEntity;

public interface ICloneHandler {

	public IEntity spawn(double x, double y, double z, int tab, String name, IWorld world);

	public IEntity get(int tab, String name, IWorld world);
	
	public void set(int tab, String name, IEntity entity);
	
	public void remove(int tab, String name);
	
}

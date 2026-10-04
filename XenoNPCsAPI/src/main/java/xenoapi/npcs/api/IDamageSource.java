package xenoapi.npcs.api;

import net.minecraft.world.damagesource.DamageSource;
import xenoapi.npcs.api.entity.IEntity;

public interface IDamageSource {

	public String getType();
	
	public boolean isUnblockable();
	
	public boolean isProjectile();
	
	public IEntity getTrueSource();
	
	public IEntity getImmediateSource();
	
	public DamageSource getMCDamageSource();
}

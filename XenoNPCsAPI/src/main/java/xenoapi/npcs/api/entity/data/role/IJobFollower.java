package xenoapi.npcs.api.entity.data.role;

import xenoapi.npcs.api.entity.ICustomNpc;
import xenoapi.npcs.api.entity.data.INPCJob;

public interface IJobFollower extends INPCJob{

	public String getFollowing();

	public void setFollowing(String name);
	
	public boolean isFollowing();

	public ICustomNpc getFollowingNpc();
}

package xenoapi.npcs.api;

import xenoapi.npcs.api.block.IBlock;

public interface IRayTrace {
	public IPos getPos();
	
	public IBlock getBlock();
	
	public int getSideHit();
}

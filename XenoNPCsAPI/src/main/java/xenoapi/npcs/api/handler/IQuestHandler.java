package xenoapi.npcs.api.handler;

import java.util.List;

import xenoapi.npcs.api.handler.data.IQuest;
import xenoapi.npcs.api.handler.data.IQuestCategory;

public interface IQuestHandler {
	
	public List<IQuestCategory> categories();
	
	public IQuest get(int id);
}

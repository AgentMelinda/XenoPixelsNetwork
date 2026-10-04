package xenoapi.npcs.api.event;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.ICancellableEvent;
import xenoapi.npcs.api.NpcAPI;
import xenoapi.npcs.api.entity.IPlayer;
import xenoapi.npcs.api.handler.data.IQuest;
import xenoapi.npcs.api.item.IItemStack;

public class QuestEvent extends CustomNPCsEvent {
	public final IQuest quest;
	public final IPlayer player;
	public QuestEvent(IPlayer player, IQuest quest) {
		this.quest = quest;
		this.player = player;
	}

	public static class QuestStartEvent extends QuestEvent implements ICancellableEvent {

		public QuestStartEvent(IPlayer player, IQuest quest) {
			super(player, quest);
		}
		
	}

	public static class QuestCompletedEvent extends QuestEvent{
		
		public QuestCompletedEvent(IPlayer player, IQuest quest) {
			super(player, quest);
		}
		
	}

	public static class QuestTurnedInEvent extends QuestEvent{
		public int expReward;		
		public IItemStack[] itemRewards = new IItemStack[0];

		public QuestTurnedInEvent(IPlayer player, IQuest quest) {
			super(player, quest);
		}
		
	}
}

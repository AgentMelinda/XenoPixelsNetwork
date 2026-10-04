package xenoapi.npcs.api.event;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.ICancellableEvent;
import xenoapi.npcs.api.NpcAPI;
import xenoapi.npcs.api.entity.ICustomNpc;
import xenoapi.npcs.api.entity.IPlayer;
import xenoapi.npcs.api.entity.data.IPlayerMail;
import xenoapi.npcs.api.entity.data.role.IRoleTransporter.ITransportLocation;
import xenoapi.npcs.api.item.IItemStack;

public class RoleEvent extends CustomNPCsEvent {
	public final ICustomNpc npc;
	public final IPlayer player;
	
	public RoleEvent(Player player, ICustomNpc npc){
		this.npc = npc;
		this.player = (IPlayer) NpcAPI.Instance().getIEntity(player);
	}

	public static class TransporterUseEvent extends RoleEvent implements ICancellableEvent {
		public final ITransportLocation location;
		public TransporterUseEvent(Player player, ICustomNpc npc, ITransportLocation location) {
			super(player, npc);
			this.location = location;
		}
	}
	
	public static class TransporterUnlockedEvent extends RoleEvent implements ICancellableEvent {
		
		public TransporterUnlockedEvent(Player player, ICustomNpc npc) {
			super(player, npc);
		}
	}

	public static class MailmanEvent extends RoleEvent implements ICancellableEvent {
		public final IPlayerMail mail;
		
		public MailmanEvent(Player player, ICustomNpc npc, IPlayerMail mail) {
			super(player, npc);
			this.mail = mail;
		}
	}

	public static class FollowerHireEvent extends RoleEvent implements ICancellableEvent {
		public int days;
		
		public FollowerHireEvent(Player player, ICustomNpc npc, int days) {
			super(player, npc);
			this.days = days;
		}
	}

	public static class FollowerFinishedEvent extends RoleEvent{
		
		public FollowerFinishedEvent(Player player, ICustomNpc npc) {
			super(player, npc);
		}		
	}
	
	public static class TraderEvent extends RoleEvent implements ICancellableEvent {
		public IItemStack sold;
		public IItemStack currency1;
		public IItemStack currency2;
		
		public TraderEvent(Player player, ICustomNpc npc, IItemStack sold, IItemStack currency1, IItemStack currency2) {
			super(player, npc);
			this.currency1 = currency1.copy();
			this.currency2 = currency2.copy();
			this.sold = sold.copy();
		}
	}
	
	public static class TradeFailedEvent extends RoleEvent{
		public final IItemStack sold;
		public final IItemStack currency1;
		public final IItemStack currency2;
		public IItemStack receiving;
		
		public TradeFailedEvent(Player player, ICustomNpc npc, IItemStack sold, IItemStack currency1, IItemStack currency2) {
			super(player, npc);
			this.currency1 = currency1.copy();
			this.currency2 = currency2.copy();
			this.sold = sold.copy();
		}
	}

	public static class BankUnlockedEvent extends RoleEvent{
		public final int slot;
		
		public BankUnlockedEvent(Player player, ICustomNpc npc, int slot) {
			super(player, npc);
			this.slot = slot;
		}		
	}

	public static class BankUpgradedEvent extends RoleEvent{
		public final int slot;
		
		public BankUpgradedEvent(Player player, ICustomNpc npc, int slot) {
			super(player, npc);
			this.slot = slot;
		}		
	}
}

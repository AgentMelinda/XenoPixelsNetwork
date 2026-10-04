package xenoapi.npcs.api.event;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.ICancellableEvent;
import xenoapi.npcs.api.IDamageSource;
import xenoapi.npcs.api.NpcAPI;
import xenoapi.npcs.api.entity.ICustomNpc;
import xenoapi.npcs.api.entity.IEntity;
import xenoapi.npcs.api.entity.IEntityLiving;
import xenoapi.npcs.api.entity.IProjectile;
import xenoapi.npcs.api.entity.data.ILine;
import xenoapi.npcs.api.item.IItemStack;
import xenoapi.npcs.api.entity.IPlayer;

public class NpcEvent extends CustomNPCsEvent{
	public final ICustomNpc npc;
	public NpcEvent(ICustomNpc npc) {
		this.npc = npc;
	}

	/**
	 * init
	 */
	public static class InitEvent extends NpcEvent{
		public InitEvent(ICustomNpc npc) {
			super(npc);
		}
	}

	/**
	 * tick
	 */
	public static class UpdateEvent extends NpcEvent{
		public UpdateEvent(ICustomNpc npc) {
			super(npc);
		}
	}

	/**
	 * target
	 */
	public static class TargetEvent extends NpcEvent implements ICancellableEvent {
		public IEntityLiving entity;
		public TargetEvent(ICustomNpc npc, LivingEntity entity) {
			super(npc);
			this.entity = (IEntityLiving) NpcAPI.Instance().getIEntity(entity);
		}
	}

	/**
	 * targetLost
	 */
	public static class TargetLostEvent extends NpcEvent implements ICancellableEvent {
		/**
		 * The previous target
		 */
		public final IEntityLiving entity;
		public TargetLostEvent(ICustomNpc npc, LivingEntity entity) {
			super(npc);
			this.entity = (IEntityLiving) NpcAPI.Instance().getIEntity(entity);
		}
	}

	/**
	 * interact
	 */
	public static class InteractEvent extends NpcEvent implements ICancellableEvent {
		public final IPlayer player;
		public InteractEvent(ICustomNpc npc, Player player) {
			super(npc);
			this.player = (IPlayer) NpcAPI.Instance().getIEntity(player);
		}
	}

	/**
	 * died
	 */
	public static class DiedEvent extends NpcEvent{
		public final IDamageSource damageSource;
		
		public final String type;
		public final IEntity source;
		
		public IItemStack[] droppedItems;
		public int expDropped;
		public ILine line;
		public DiedEvent(ICustomNpc npc, DamageSource damagesource, Entity entity) {
			super(npc);
			this.damageSource = NpcAPI.Instance().getIDamageSource(damagesource);
			type = damagesource.getMsgId();
			this.source = NpcAPI.Instance().getIEntity(entity);
		}
	}

	/**
	 * kill
	 */
	public static class KilledEntityEvent extends NpcEvent{
		public final IEntityLiving entity;
		public KilledEntityEvent(ICustomNpc npc, LivingEntity entity) {
			super(npc);
			this.entity = (IEntityLiving) NpcAPI.Instance().getIEntity(entity);
		}
	}

	/**
	 * meleeAttack
	 */
	public static class MeleeAttackEvent extends NpcEvent implements ICancellableEvent {
		public final IEntityLiving target;
		public float damage;

		public MeleeAttackEvent(ICustomNpc npc, LivingEntity target, float damage) {
			super(npc);
			this.target = (IEntityLiving) NpcAPI.Instance().getIEntity(target);
			this.damage = damage;
		}
	}

	/**
	 * rangedAttack
	 */
	public static class RangedLaunchedEvent extends NpcEvent{
		public final IEntityLiving target;
		public float damage;
		public List<IProjectile> projectiles = new ArrayList<IProjectile>();

		public RangedLaunchedEvent(ICustomNpc npc, LivingEntity target, float damage) {
			super(npc);
			this.target = (IEntityLiving) NpcAPI.Instance().getIEntity(target);
			this.damage = damage;
		}
	}

	/**
	 * damaged
	 */
	public static class DamagedEvent extends NpcEvent implements ICancellableEvent {
		public final IDamageSource damageSource;
		public final IEntity source;
		public float damage;
		public boolean clearTarget = false;

		public DamagedEvent(ICustomNpc npc, Entity source, float damage, DamageSource damagesource) {
			super(npc);
			this.source = (IEntity) NpcAPI.Instance().getIEntity(source);
			this.damage = damage;
			this.damageSource = NpcAPI.Instance().getIDamageSource(damagesource);
		}
	}

	/**
	 * collide
	 */
	public static class CollideEvent extends NpcEvent{
		public final IEntity entity;

		public CollideEvent(ICustomNpc npc, Entity entity) {
			super(npc);
			this.entity = (IEntity) NpcAPI.Instance().getIEntity(entity);
		}
	}

	/**
	 * timer
	 */
	public static class TimerEvent extends NpcEvent{
		public final int id;
		
		public TimerEvent(ICustomNpc npc, int id) {
			super(npc);
			this.id = id;
		}
	}
}

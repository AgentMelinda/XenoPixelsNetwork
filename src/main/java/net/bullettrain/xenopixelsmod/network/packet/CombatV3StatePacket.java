package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.combat.v3.V3State;
import net.bullettrain.xenopixelsmod.combat.v3.V3TargetSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import java.util.UUID;
import java.util.function.Supplier;

/** S2C authoritative fighter state. All durations are bounded remaining server ticks. */
public record CombatV3StatePacket(UUID session, V3State state, V3TargetSnapshot target,
                                  int acknowledgedSequence, int chargeTicks,
                                  int windowTicksLeft, int windowTicksTotal,
                                  net.bullettrain.xenopixelsmod.combat.v3.V3Window window,
                                  net.bullettrain.xenopixelsmod.combat.v3.RecoverySnapshot recovery,
                                  int comboHits, int comboTicksLeft, int rushPromptTicks, boolean rushUltimate, UUID rushToken, int rushProgress) {
    public CombatV3StatePacket(UUID session,V3State state,V3TargetSnapshot target,int acknowledgedSequence,
        int chargeTicks,int windowTicksLeft,int windowTicksTotal,net.bullettrain.xenopixelsmod.combat.v3.V3Window window,
        net.bullettrain.xenopixelsmod.combat.v3.RecoverySnapshot recovery,int comboHits,int comboTicksLeft,
        int rushPromptTicks,boolean rushUltimate){
        this(session,state,target,acknowledgedSequence,chargeTicks,windowTicksLeft,windowTicksTotal,window,
             recovery,comboHits,comboTicksLeft,rushPromptTicks,rushUltimate,null,0);
    }
    public CombatV3StatePacket(UUID session, V3State state, V3TargetSnapshot target, int acknowledgedSequence,
                               int chargeTicks, int windowTicksLeft, int windowTicksTotal,
                               net.bullettrain.xenopixelsmod.combat.v3.V3Window window) {
        this(session,state,target,acknowledgedSequence,chargeTicks,windowTicksLeft,windowTicksTotal,window,null,0,0,0,false);
    }
    public CombatV3StatePacket(UUID session, V3State state, V3TargetSnapshot target, int acknowledgedSequence,
                               int chargeTicks, int windowTicksLeft, int windowTicksTotal,
                               net.bullettrain.xenopixelsmod.combat.v3.V3Window window,
                               net.bullettrain.xenopixelsmod.combat.v3.RecoverySnapshot recovery,
                               int comboHits, int comboTicksLeft) {
        this(session,state,target,acknowledgedSequence,chargeTicks,windowTicksLeft,windowTicksTotal,
                window,recovery,comboHits,comboTicksLeft,0,false);
    }
    public CombatV3StatePacket(UUID session, V3State state, V3TargetSnapshot target, int acknowledgedSequence,
                               int chargeTicks, int windowTicksLeft, int windowTicksTotal) {
        this(session, state, target, acknowledgedSequence, chargeTicks, windowTicksLeft, windowTicksTotal,
                net.bullettrain.xenopixelsmod.combat.v3.V3Window.NONE);
    }
    public CombatV3StatePacket {
        if (session == null || state == null || window == null || acknowledgedSequence < -1
                || chargeTicks < 0 || chargeTicks > 200 || windowTicksLeft < 0
                || windowTicksTotal < 0 || windowTicksTotal > 1200 || windowTicksLeft > windowTicksTotal
                || comboHits < 0 || comboHits > 10000 || comboTicksLeft < 0 || comboTicksLeft > 80
                || rushPromptTicks < 0 || rushPromptTicks > 1200 || rushProgress < 0 || rushProgress > 1000) {
            throw new IllegalArgumentException("Invalid V3 server state");
        }
    }
    public CombatV3StatePacket(FriendlyByteBuf buf) {
        this(buf.readUUID(), V3State.decode(buf.readVarInt()), readTarget(buf), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                net.bullettrain.xenopixelsmod.combat.v3.V3Window.decode(buf.readVarInt()),
                readRecovery(buf),buf.isReadable()?buf.readVarInt():0,buf.isReadable()?buf.readVarInt():0,
                buf.isReadable()?buf.readVarInt():0,buf.isReadable() && buf.readBoolean(),
                buf.isReadable() && buf.readBoolean()?buf.readUUID():null,buf.isReadable()?buf.readVarInt():0);
    }
    private static net.bullettrain.xenopixelsmod.combat.v3.RecoverySnapshot readRecovery(FriendlyByteBuf buf) {
        if(!buf.isReadable() || !buf.readBoolean()) return null;
        UUID token=buf.readUUID();int completed=buf.readVarInt(),total=buf.readVarInt(),kind=buf.readVarInt();
        var kinds=net.bullettrain.xenopixelsmod.combat.v3.RecoverySnapshot.Kind.values();
        if(kind<0 || kind>=kinds.length)throw new IllegalArgumentException("Invalid recovery kind");
        return new net.bullettrain.xenopixelsmod.combat.v3.RecoverySnapshot(token,completed,total,kinds[kind]);
    }
    private static V3TargetSnapshot readTarget(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        return new V3TargetSnapshot(buf.readUUID(), buf.readVarInt(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readVarLong());
    }
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(session);
        buf.writeVarInt(state.ordinal());
        buf.writeBoolean(target != null);
        if (target != null) {
            buf.writeUUID(target.target()); buf.writeVarInt(target.entityId());
            buf.writeDouble(target.position().x); buf.writeDouble(target.position().y); buf.writeDouble(target.position().z);
            buf.writeDouble(target.velocity().x); buf.writeDouble(target.velocity().y); buf.writeDouble(target.velocity().z);
            buf.writeVarLong(target.revision());
        }
        buf.writeVarInt(acknowledgedSequence); buf.writeVarInt(chargeTicks);
        buf.writeVarInt(windowTicksLeft); buf.writeVarInt(windowTicksTotal);
        buf.writeVarInt(window.ordinal());
        buf.writeBoolean(recovery!=null);
        if(recovery!=null){buf.writeUUID(recovery.token());buf.writeVarInt(recovery.completed());
            buf.writeVarInt(recovery.total());buf.writeVarInt(recovery.kind().ordinal());}
        buf.writeVarInt(comboHits);buf.writeVarInt(comboTicksLeft);
        buf.writeVarInt(rushPromptTicks);buf.writeBoolean(rushUltimate);
        buf.writeBoolean(rushToken!=null);if(rushToken!=null)buf.writeUUID(rushToken);buf.writeVarInt(rushProgress);
    }
    public void handle(Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                net.bullettrain.xenopixelsmod.client.ClientPacketHandlers.handleCombatV3State(this);
            }
        });
        ctx.setPacketHandled(true);
    }
}

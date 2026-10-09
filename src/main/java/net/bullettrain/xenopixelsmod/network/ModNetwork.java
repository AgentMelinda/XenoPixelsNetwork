package net.bullettrain.xenopixelsmod.network;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.network.packet.GuidanceControlPacket;
import net.bullettrain.xenopixelsmod.network.packet.SyncFactionsPacket;
import net.bullettrain.xenopixelsmod.network.packet.TeleportShipPacket;
import net.bullettrain.xenopixelsmod.network.packet.SetTargetToolPacket;
import net.bullettrain.xenopixelsmod.network.packet.UnbindTechniqueSlotPacket;
import net.bullettrain.xenopixelsmod.network.packet.AfterimageGhostPacket;
import net.bullettrain.xenopixelsmod.network.packet.FlightPlanRequestPacket;
import net.bullettrain.xenopixelsmod.network.packet.FlightPlanResultPacket;
import net.bullettrain.xenopixelsmod.network.packet.BodyCalibrationPacket;
import net.bullettrain.xenopixelsmod.network.packet.OpenGuidancePacket;
import net.bullettrain.xenopixelsmod.network.packet.AeroControlPacket;
import net.bullettrain.xenopixelsmod.network.packet.AeroStatePacket;
import net.bullettrain.xenopixelsmod.network.packet.BeamSurgePacket;
import net.bullettrain.xenopixelsmod.network.packet.GuidanceHoldPacket;
import net.bullettrain.xenopixelsmod.network.packet.SeatFlightInputPacket;
import net.bullettrain.xenopixelsmod.network.packet.SeatToggleBindPacket;
import net.bullettrain.xenopixelsmod.network.packet.TargetLockPacket;
import net.bullettrain.xenopixelsmod.network.packet.TargetLockStatePacket;
import net.bullettrain.xenopixelsmod.network.packet.TargetLockErrorPacket;
import net.bullettrain.xenopixelsmod.network.packet.CombatFxPacket;
import net.bullettrain.xenopixelsmod.network.packet.ChaseFlightStatePacket;
import net.bullettrain.xenopixelsmod.network.packet.SparkingChargePacket;
import net.bullettrain.xenopixelsmod.network.packet.SparkingStatePacket;
import net.bullettrain.xenopixelsmod.network.packet.PartySyncPacket;
import net.bullettrain.xenopixelsmod.network.packet.PartyActionPacket;
import net.bullettrain.xenopixelsmod.network.packet.PartyPingPacket;
import net.bullettrain.xenopixelsmod.network.packet.OpenPartyScreenPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.dragonminez.compat.network.NetworkDirection;
import com.dragonminez.compat.network.NetworkRegistry;
import com.dragonminez.compat.network.simple.SimpleChannel;

/**
 * Central SimpleChannel registration. Every packet class must be registered here
 * before {@link SimpleChannel#send} / {@link #sendToServer} or login will fail with
 * {@code Invalid message &lt;packet class&gt;}.
 */
public class ModNetwork {
    /**
     * Bump when packet set or wire format changes.
     * <p>108: appended caster-only V3 camera timelines with session/cast/target identity.
     *
     * <p>29: appended {@code SeatToggleBindPacket}, the seated pilot's chair-bind toggle key.
     * <p>28: {@code AeroStatePacket} carries the air-brake flag, so the flight HUD can show a
     * control the pilot is actually holding.
     * <p>27: appended {@code TargetLockPacket} (C2S), {@code TargetLockStatePacket} (S2C) and
     * {@code TargetLockErrorPacket} (S2C) for seat-mounted combat lock-on.
     * <p>26: appended {@code SeatFlightInputPacket}, the seated pilot's control stream.
     * <p>25: AeroStatePacket carries flap extension / target / auto-flap; AeroControlPacket gains
     * SET_FLAP and TOGGLE_AUTO_FLAP.
     * <p>24: dropped lock-through-blocks (feature removed).
     * <p>23: SyncServerConfigPacket carries guidance knobs, barrage extras, and beam-surge fields.
     * <p>22: SyncServerConfigPacket carries per-type ki duration.
     * <p>21: SyncServerConfigPacket carries barrage duration/cooldown.
     * <p>20: GuidanceHoldPacket also carries camera aim (Sokidan-style).
     * <p>19: GuidanceHoldPacket now carries lock-on entity id.
     * <p>18: appended {@code GuidanceHoldPacket}, Ki Guidance key hold.
     * <p>16: expanded party state and appended action, ping, and screen-open packets.
     * <p>15: appended {@code PartySyncPacket}, the party roster.
     * <p>14: appended {@code BeamSurgePacket}, the sustained-beam feed report.
     * <p>13: appended {@code CombatFxPacket}, the combat impact cue.
     * <p>12: extends Aero control/state with absolute-attitude target/route autopilot.
     * <p>11: appended {@code AeroControlPacket} and {@code AeroStatePacket} for the Aero
     * flight controller. Clients and servers must both run this build — the channel refuses
     * a mismatched protocol, so differently versioned builds cannot join each other.
     * <p>63: appended {@code Bt3RushStatePacket}; {@code Bt3CombatPacket.Action} appended
     * {@code CINEMATIC_RUSH}; server config sync appended the cinematic-rush toggle.
     *
     * <p>30: {@code SeatFlightInputPacket} grew two stick bytes and a mouse-aim flag bit for
     * direct keyboard-mode flap control.
     *
     * <p>31: {@code SeatFlightInputPacket} grew a third stick byte (yaw), for the W/S axis remap.
     *
     * <p>32: appended {@code NpcAuraPacket}, CustomNPC DragonMineZ aura mesh sync.
     *
     * <p>33: {@code NpcAuraPacket} is keyed by entity UUID (mesh was dropped when the
     * client received the id before the NPC existed).
     *
     * <p>34: {@code SyncServerConfigPacket} appended {@code lockOnThroughBlocks}.
     *
     * <p>35: {@code NpcAuraPacket} appended aura scale.
     * <p>36: appended {@code NpcAppearancePacket} for CustomNPC Gecko/DMZ form appearance.
     * <p>37: appended {@code NpcProfileSavePacket} (C2S DMZ editor tab).
     * <p>38: {@code NpcAppearancePacket} carries DMZ hair enabled/code/color.
     * <p>39: appended {@code NpcTransformHoldPacket} so NPC hair morphs during transform hold.
     * <p>40: {@code NpcAppearancePacket} sends hair code in chunks (full-set codes exceed 32767).
     * <p>42: {@code NpcAuraPacket} carries form-driven lightning state and color.
     * <p>43: {@code NpcAppearancePacket} carries aura color and the versioned DMZ appearance profile.
     * <p>44: {@code NpcAppearancePacket} also carries the NPC's native DMZ aura-scale multiplier.
     * <p>45: NPC appearance/transform/aura packets carry stack-form state, layered aura styles,
     * secondary colors, and independent visual-effect toggles.
     * <p>46: NPC appearance visual options carry the Ki Weapon enabled state and model type.
     * <p>47: {@code NpcAuraPacket} carries the ground-ring toggle; NPC visual options carry the
     * DMZ skill map (fly included); appended {@code DmzLockOnPacket} for scripted player lock-on.
     * <p>48: {@code Bt3AnimIntentPacket} carries the BT3 combo beat the server accepted, so every
     * client renders the same choreography step instead of deriving one from the combo counter.
     * <p>49: appended spin-then-strike intents; SyncServerConfigPacket carries chaseStopGap,
     * vanishGap, vanishSide.
     * <p>50: {@code Bt3CombatPacket} appended the mash style byte, so a held strafe key can pin
     * the combo to a punch or uppercut cycle server-side instead of the authored route.
     * <p>51: {@code SyncServerConfigPacket} carries comboMashIntervalTicks (the held-mash beat, and
     * with it every mash clip's playback speed) and vanishNearField.
     * <p>52: {@code SyncServerConfigPacket} carries vanishOpenSpotSearch. Both it and
     * vanishNearField now default off, so vanish ships with its original landing geometry and the
     * two reworks stay available to switch on.
     * <p>53: {@code SyncServerConfigPacket} carries comboAnimGeneration, which picks between the
     * three authored generations of the combat clips; appended {@code NpcAnimationPacket} so an NPC
     * can be told to play one of them.
     * <p>54: comboAnimGeneration accepts generation 4, whose names and semantics are unknown to
     * older clients even though the serialized field remains an integer.
     * <p>55: generation 4's ordinary jab pair now resolves to Xeno-owned copies of DMZ's exact
     * left/right one-handed punches.
     * <p>56: appended {@code ChaseFlightStatePacket} to arbitrate DMZ and Xeno flight movement.
     * <p>61: appended {@code SparkingStatePacket}. Mob effects are not synced to the clients
     * tracking a player, so the Sparking aura needs its own signal to be visible on anyone but the
     * local player.
     * <p>62: appended {@code SparkingChargePacket} for the owner HUD's staged Max Power charge.
     * <p>64: appended {@code NpcProfileSaveResultPacket}, the server's answer to a wand
     * {@code NpcProfileSavePacket}. Every guard in that packet returned silently, so a save
     * rejected for permission, range, or a stale entity id looked exactly like one that saved.
     * <p>65: appended {@code HakaiFadePacket} (entity dissolve amplifier). Mob effects are not
     * a reliable tracker-client signal — same lesson as protocol 61 for Sparking.
     * {@code SyncServerConfigPacket} appended the four {@code hakaiFade*} knobs.
     * <p>66: {@code SyncServerConfigPacket} appended {@code hakaiFadeSpeed}, {@code hakaiFadeBand},
     * {@code hakaiFxColor}, and {@code hakaiFxRimColor}. Restore-tick ceiling is still a VarInt;
     * only the allowed range moved.
     * <p>67: {@code SyncServerConfigPacket} appended {@code hakaiFxEnabled}, {@code hakaiDustEnabled},
     * {@code hakaiSilhouetteEnabled}, {@code hakaiSilhouetteColor}, and {@code hakaiGlowColor}.
     * <p>68: {@code NpcAnimationPacket} appended a flags byte (KI play-and-hold and a real
     * controller stop) so a scripted studio clip can run for a duration and then idle.
     * <p>77: appended {@code XenoNpcEditorLockPacket}. Lock changes have their own operation so an
     * unlock cannot carry unrelated profile or identity edits through the locked save guard.
     * <p>76: appended {@code OpenXenoNpcDialoguePacket} (S2C) and {@code XenoNpcDialoguePacket}
     * (C2S). The open packet carries the server-resolved dialogue tree because datapacks load on
     * the server; the choice packet carries an option index, and the server re-reads its dialogue
     * rather than trusting what the option claims to do.
     * <p>75: appended {@code XenoNpcSpeechPacket} (S2C NPC speech bubble). The bubble renderer is
     * client-side, so without a packet only the player who clicked would see an NPC's line; the
     * server picks the line and broadcasts to the entity's trackers.
     * <p>74: appended {@code XenoNpcDeletePacket}. {@code /kill} is refused on Xeno NPCs, and was
     * never a real delete anyway because {@code die()} schedules a respawn, so removing one is now
     * an explicit action that cancels the respawn before discarding the entity.
     * <p>73: appended {@code XenoNpcActionPacket} (C2S NPC editor DMZ tab). Transform, descend and
     * stack are server-side entity state, so the editor cannot apply them the way it edits a
     * profile field. It reuses {@code NpcProfileSaveResultPacket} for its answer rather than adding
     * a second result type.
     * <p>70: {@code SyncServerConfigPacket} appended {@code combatControllerMode} (16-char UTF,
     * {@code legacy} or {@code bt3_manual}) so the client input layer can follow the server's
     * controller choice instead of guessing from the legacy feature flags.
     * <p>71: {@code Bt3CombatPacket.Action} appended {@code RUSH_COMBO} and {@code LIFT_COMBO}.
     */
    /**
     * 100 (2026-09-29): SyncServerConfigPacket appends effekseerEnabled and effekseerShipThrusters,
     * so the client knows whether the ship thruster plume is the server's Effekseer effect or its
     * own vanilla flames.
     * 99 (2026-09-28): appended NpcProfileRequestPacket (C2S) and NpcProfileRefreshPacket (S2C) -
     * DMZ screens now receive the NPC's full server profile - and NpcProfileSavePacket appends an
     * optional baseline so only changed keys are applied (stale client copies no longer revert
     * script edits). The Nearby NPCs wand screen packets follow in this same protocol.
     * 103 (2026-10-06): no packet added. The two v2 packets changed since 102:
     * {@code CombatV2StatePacket} appends the id of whoever opened the counter window and its
     * branch-mask byte now carries branch labels; {@code V2Input.VANISH} and
     * {@code V2State.STRIKE} were appended; and the target id in {@code CombatV2InputPacket} now
     * means the sender's DragonMineZ lock-on target, without which the input is refused.
     * 102 (2026-10-05): appended {@code CombatV2InputPacket} (C2S) and {@code CombatV2StatePacket}
     * (S2C) for the {@code v2} combat controller. No existing packet changed.
     * 101: appended {@code SecondAuraStatePacket}, a player's own second-aura switch as seen by
     * everyone tracking them.
     * 97: XenoNpcSpeechPacket appends a per-line palette and bubble shape; Open/BindXenoNpcScript
     * carry the NPC's script container (tabs, loaded scripts, language, enabled, console) instead
     * of one script id. No packet was added or reordered.
     * 96: scripting tool open/bind packets. 95: NPC script editor packets. 94: natural spawn sync
     * appended. 93: Xeno NPC bank packet supports physical Zeni cash actions. 92: editor save result.
     */
    // 104: appended server-owned UltimateFinisher cinematic state.
    // 105 appends server-timed v2 charge inputs and the CHARGE state; packet ids stay stable.
    // 106 appends the Dragon Dash continuation window and target to the existing state packet.
    // 110 appends the 2026-10-08 V3 tuning tail to CombatV3ConfigPacket; packet ids are unchanged.
    private static final String PROTOCOL = "111";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private static int id = 0;
    private static boolean registered;

    public static void register() {
        if (registered) return;
        registered = true;

        // --- Server → Client ---
        CHANNEL.messageBuilder(SyncServerConfigPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncServerConfigPacket::decode)
                .encoder(SyncServerConfigPacket::encode)
                .consumerMainThread(SyncServerConfigPacket::handle)
                .add();

        CHANNEL.messageBuilder(SyncDmzHudStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncDmzHudStatePacket::decode)
                .encoder(SyncDmzHudStatePacket::encode)
                .consumerMainThread(SyncDmzHudStatePacket::handle)
                .add();

        CHANNEL.messageBuilder(SyncXenoStatsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncXenoStatsPacket::decode)
                .encoder(SyncXenoStatsPacket::encode)
                .consumerMainThread(SyncXenoStatsPacket::handle)
                .add();

        CHANNEL.messageBuilder(FlightPlanResultPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(FlightPlanResultPacket::new)
                .encoder(FlightPlanResultPacket::encode)
                .consumerMainThread(FlightPlanResultPacket::handle)
                .add();

        CHANNEL.messageBuilder(OpenGuidancePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(OpenGuidancePacket::new)
                .encoder(OpenGuidancePacket::encode)
                .consumerMainThread(OpenGuidancePacket::handle)
                .add();

        // --- Client → Server ---
        CHANNEL.messageBuilder(TeleportShipPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(TeleportShipPacket::new)
                .encoder(TeleportShipPacket::encode)
                .consumerMainThread(TeleportShipPacket::handle)
                .add();

        CHANNEL.messageBuilder(GuidanceControlPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(GuidanceControlPacket::new)
                .encoder(GuidanceControlPacket::encode)
                .consumerMainThread(GuidanceControlPacket::handle)
                .add();

        CHANNEL.messageBuilder(AfterimageGhostPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(AfterimageGhostPacket::new)
                .encoder(AfterimageGhostPacket::encode)
                .consumerMainThread(AfterimageGhostPacket::handle)
                .add();

        CHANNEL.messageBuilder(UnbindTechniqueSlotPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(UnbindTechniqueSlotPacket::new)
                .encoder(UnbindTechniqueSlotPacket::encode)
                .consumerMainThread(UnbindTechniqueSlotPacket::handle)
                .add();

        CHANNEL.messageBuilder(SetTargetToolPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(SetTargetToolPacket::new)
                .encoder(SetTargetToolPacket::encode)
                .consumerMainThread(SetTargetToolPacket::handle)
                .add();

        CHANNEL.messageBuilder(FlightPlanRequestPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(FlightPlanRequestPacket::new)
                .encoder(FlightPlanRequestPacket::encode)
                .consumerMainThread(FlightPlanRequestPacket::handle)
                .add();

        CHANNEL.messageBuilder(BodyCalibrationPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(BodyCalibrationPacket::new)
                .encoder(BodyCalibrationPacket::encode)
                .consumerMainThread(BodyCalibrationPacket::handle)
                .add();

        CHANNEL.messageBuilder(Bt3CombatPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(Bt3CombatPacket::decode)
                .encoder(Bt3CombatPacket::encode)
                .consumerMainThread(Bt3CombatPacket::handle)
                .add();

        CHANNEL.messageBuilder(ChargeAnimPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(ChargeAnimPacket::decode)
                .encoder(ChargeAnimPacket::encode)
                .consumerMainThread(ChargeAnimPacket::handle)
                .add();

        // --- Aero flight controller (appended; ids are assigned by registration order) ---
        CHANNEL.messageBuilder(AeroControlPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(AeroControlPacket::new)
                .encoder(AeroControlPacket::encode)
                .consumerMainThread(AeroControlPacket::handle)
                .add();

        CHANNEL.messageBuilder(AeroStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(AeroStatePacket::new)
                .encoder(AeroStatePacket::encode)
                .consumerMainThread(AeroStatePacket::handle)
                .add();

        // --- Combat impact FX (appended) ---
        // Server → client, but registered here at the end rather than up in the S2C block:
        // ids come from registration order, and inserting into that block would renumber every
        // client → server packet below it.
        CHANNEL.messageBuilder(CombatFxPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(CombatFxPacket::new)
                .encoder(CombatFxPacket::encode)
                .consumerMainThread(CombatFxPacket::handle)
                .add();

        // --- sustained ki wave (appended) ---
        CHANNEL.messageBuilder(BeamSurgePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(BeamSurgePacket::new)
                .encoder(BeamSurgePacket::encode)
                .consumerMainThread(BeamSurgePacket::handle)
                .add();

        // --- party roster (appended) ---
        CHANNEL.messageBuilder(PartySyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(PartySyncPacket::new)
                .encoder(PartySyncPacket::encode)
                .consumerMainThread(PartySyncPacket::handle)
                .add();

        CHANNEL.messageBuilder(PartyActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(PartyActionPacket::new)
                .encoder(PartyActionPacket::encode)
                .consumerMainThread(PartyActionPacket::handle)
                .add();

        CHANNEL.messageBuilder(PartyPingPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(PartyPingPacket::new)
                .encoder(PartyPingPacket::encode)
                .consumerMainThread(PartyPingPacket::handle)
                .add();

        CHANNEL.messageBuilder(OpenPartyScreenPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(OpenPartyScreenPacket::new)
                .encoder(OpenPartyScreenPacket::encode)
                .consumerMainThread(OpenPartyScreenPacket::handle)
                .add();

        CHANNEL.messageBuilder(GuidanceHoldPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(GuidanceHoldPacket::new)
                .encoder(GuidanceHoldPacket::encode)
                .consumerMainThread(GuidanceHoldPacket::handle)
                .add();

        // --- Clone lock-on target sync (client → server) ---
        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.CloneTargetPacket.class, id++,
                        NetworkDirection.PLAY_TO_SERVER)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.CloneTargetPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.CloneTargetPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.CloneTargetPacket::handle)
                .add();

        CHANNEL.messageBuilder(SeatFlightInputPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(SeatFlightInputPacket::new)
                .encoder(SeatFlightInputPacket::encode)
                .consumerMainThread(SeatFlightInputPacket::handle)
                .add();

        // --- Seat combat lock-on (appended) ---
        CHANNEL.messageBuilder(TargetLockPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(TargetLockPacket::new)
                .encoder(TargetLockPacket::encode)
                .consumerMainThread(TargetLockPacket::handle)
                .add();

        CHANNEL.messageBuilder(TargetLockStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(TargetLockStatePacket::new)
                .encoder(TargetLockStatePacket::encode)
                .consumerMainThread(TargetLockStatePacket::handle)
                .add();

        CHANNEL.messageBuilder(TargetLockErrorPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(TargetLockErrorPacket::new)
                .encoder(TargetLockErrorPacket::encode)
                .consumerMainThread(TargetLockErrorPacket::handle)
                .add();

        // --- Seat chair-bind toggle (appended) ---
        CHANNEL.messageBuilder(SeatToggleBindPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(SeatToggleBindPacket::new)
                .encoder(SeatToggleBindPacket::encode)
                .consumerMainThread(SeatToggleBindPacket::handle)
                .add();

        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.NpcAuraPacket.class, id++,
                        NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.NpcAuraPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.NpcAuraPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.NpcAuraPacket::handle)
                .add();

        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.NpcAppearancePacket.class, id++,
                        NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.NpcAppearancePacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.NpcAppearancePacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.NpcAppearancePacket::handle)
                .add();

        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.NpcProfileSavePacket.class, id++,
                        NetworkDirection.PLAY_TO_SERVER)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.NpcProfileSavePacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.NpcProfileSavePacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.NpcProfileSavePacket::handle)
                .add();

        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.NpcTransformHoldPacket.class, id++,
                        NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.NpcTransformHoldPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.NpcTransformHoldPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.NpcTransformHoldPacket::handle)
                .add();

        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.NpcAnimationPacket.class, id++,
                        NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.NpcAnimationPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.NpcAnimationPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.NpcAnimationPacket::handle)
                .add();

        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.DmzLockOnPacket.class, id++,
                        NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.DmzLockOnPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.DmzLockOnPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.DmzLockOnPacket::handle)
                .add();

        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.Bt3AnimIntentPacket.class, id++,
                        NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.Bt3AnimIntentPacket::decode)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.Bt3AnimIntentPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.Bt3AnimIntentPacket::handle)
                .add();

        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.Bt3RushStatePacket.class, id++,
                        NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.Bt3RushStatePacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.Bt3RushStatePacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.Bt3RushStatePacket::handle)
                .add();

        CHANNEL.messageBuilder(ChaseFlightStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(ChaseFlightStatePacket::new)
                .encoder(ChaseFlightStatePacket::encode)
                .consumerMainThread(ChaseFlightStatePacket::handle)
                .add();

        CHANNEL.messageBuilder(SparkingStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SparkingStatePacket::new)
                .encoder(SparkingStatePacket::encode)
                .consumerMainThread(SparkingStatePacket::handle)
                .add();

        CHANNEL.messageBuilder(SparkingChargePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SparkingChargePacket::new)
                .encoder(SparkingChargePacket::encode)
                .consumerMainThread(SparkingChargePacket::handle)
                .add();

        CHANNEL.messageBuilder(
                        net.bullettrain.xenopixelsmod.network.packet.NpcProfileSaveResultPacket.class, id++,
                        NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.NpcProfileSaveResultPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.NpcProfileSaveResultPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.NpcProfileSaveResultPacket::handle)
                .add();

        CHANNEL.messageBuilder(
                        net.bullettrain.xenopixelsmod.network.packet.HakaiFadePacket.class, id++,
                        NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.HakaiFadePacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.HakaiFadePacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.HakaiFadePacket::handle)
                .add();

        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.OpenXenoNpcEditorPacket.class, id++,
                        NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.OpenXenoNpcEditorPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.OpenXenoNpcEditorPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.OpenXenoNpcEditorPacket::handle)
                .add();

        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcSavePacket.class, id++,
                        NetworkDirection.PLAY_TO_SERVER)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcSavePacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcSavePacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.XenoNpcSavePacket::handle)
                .add();

        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.RequestXenoNpcEditorPacket.class, id++,
                        NetworkDirection.PLAY_TO_SERVER)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.RequestXenoNpcEditorPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.RequestXenoNpcEditorPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.RequestXenoNpcEditorPacket::handle)
                .add();

        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcActionPacket.class, id++,
                        NetworkDirection.PLAY_TO_SERVER)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcActionPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcActionPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.XenoNpcActionPacket::handle)
                .add();

        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcDeletePacket.class, id++,
                        NetworkDirection.PLAY_TO_SERVER)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcDeletePacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcDeletePacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.XenoNpcDeletePacket::handle)
                .add();

        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcSpeechPacket.class, id++,
                        NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcSpeechPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcSpeechPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.XenoNpcSpeechPacket::handle)
                .add();

        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.OpenXenoNpcDialoguePacket.class, id++,
                        NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.OpenXenoNpcDialoguePacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.OpenXenoNpcDialoguePacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.OpenXenoNpcDialoguePacket::handle)
                .add();

        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcDialoguePacket.class, id++,
                        NetworkDirection.PLAY_TO_SERVER)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcDialoguePacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcDialoguePacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.XenoNpcDialoguePacket::handle)
                .add();

        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcEditorLockPacket.class, id++,
                        NetworkDirection.PLAY_TO_SERVER)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcEditorLockPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcEditorLockPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.XenoNpcEditorLockPacket::handle)
                .add();

        // Appended, as this list always is - ids are positional and reordering would make an old
        // client read the wrong packet.
        CHANNEL.messageBuilder(SyncFactionsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncFactionsPacket::new)
                .encoder(SyncFactionsPacket::encode)
                .consumerMainThread(SyncFactionsPacket::handle)
                .add();

        // Appended, as this list always is - ids are positional.
        CHANNEL.messageBuilder(
                        net.bullettrain.xenopixelsmod.network.packet.XenoNpcStoreWritePacket.class,
                        id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcStoreWritePacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcStoreWritePacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.XenoNpcStoreWritePacket::handle)
                .add();

        CHANNEL.messageBuilder(
                        net.bullettrain.xenopixelsmod.network.packet.SyncNpcStoreIndexPacket.class,
                        id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.SyncNpcStoreIndexPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.SyncNpcStoreIndexPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.SyncNpcStoreIndexPacket::handle)
                .add();

        // Appended, as this list always is - ids are positional and reordering would make an old
        // client read the wrong packet.
        CHANNEL.messageBuilder(
                        net.bullettrain.xenopixelsmod.network.packet.SyncQuestsPacket.class,
                        id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.SyncQuestsPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.SyncQuestsPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.SyncQuestsPacket::handle)
                .add();

        // Appended, as this list always is - ids are positional.
        CHANNEL.messageBuilder(
                        net.bullettrain.xenopixelsmod.network.packet.SyncStandingsPacket.class,
                        id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.SyncStandingsPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.SyncStandingsPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.SyncStandingsPacket::handle)
                .add();

        // Appended, as this list always is - ids are positional.
        CHANNEL.messageBuilder(
                        net.bullettrain.xenopixelsmod.network.packet.XenoNpcTravelPacket.class,
                        id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcTravelPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcTravelPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.XenoNpcTravelPacket::handle)
                .add();

        CHANNEL.messageBuilder(
                        net.bullettrain.xenopixelsmod.network.packet.XenoNpcBankPacket.class,
                        id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcBankPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcBankPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.XenoNpcBankPacket::handle)
                .add();

        CHANNEL.messageBuilder(
                        net.bullettrain.xenopixelsmod.network.packet.SyncBanksPacket.class,
                        id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.SyncBanksPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.SyncBanksPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.SyncBanksPacket::handle)
                .add();

        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.RequestNpcStoreDialoguePacket.class,
                        id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.RequestNpcStoreDialoguePacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.RequestNpcStoreDialoguePacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.RequestNpcStoreDialoguePacket::handle)
                .add();
        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.NpcStoreDialoguePacket.class,
                        id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.NpcStoreDialoguePacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.NpcStoreDialoguePacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.NpcStoreDialoguePacket::handle)
                .add();

        // Appended at the end, never inserted: ids are handed out sequentially by id++, so slotting
        // one into the middle silently repoints every packet after it.
        CHANNEL.messageBuilder(
                        net.bullettrain.xenopixelsmod.network.packet
                                .XenoNpcInventoryOpenPacket.class,
                        id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(net.bullettrain.xenopixelsmod.network.packet
                        .XenoNpcInventoryOpenPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet
                        .XenoNpcInventoryOpenPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet
                        .XenoNpcInventoryOpenPacket::handle)
                .add();

        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.QuestCompletionPopupPacket.class,
                        id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.QuestCompletionPopupPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.QuestCompletionPopupPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.QuestCompletionPopupPacket::handle)
                .add();

        CHANNEL.messageBuilder(
                        net.bullettrain.xenopixelsmod.network.packet.RequestNpcStoreQuestPacket.class,
                        id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.RequestNpcStoreQuestPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.RequestNpcStoreQuestPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.RequestNpcStoreQuestPacket::handle)
                .add();
        CHANNEL.messageBuilder(
                        net.bullettrain.xenopixelsmod.network.packet.NpcStoreQuestPacket.class,
                        id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.NpcStoreQuestPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.NpcStoreQuestPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.NpcStoreQuestPacket::handle)
                .add();

        CHANNEL.messageBuilder(
                        net.bullettrain.xenopixelsmod.network.packet.XenoNpcEditorSaveResultPacket.class,
                        id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcEditorSaveResultPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcEditorSaveResultPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.XenoNpcEditorSaveResultPacket::handle)
                .add();

        // Appended, as this list always is - ids are positional and reordering would make an old
        // client read the wrong packet.
        CHANNEL.messageBuilder(
                        net.bullettrain.xenopixelsmod.network.packet.SyncNaturalSpawnsPacket.class,
                        id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.SyncNaturalSpawnsPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.SyncNaturalSpawnsPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.SyncNaturalSpawnsPacket::handle)
                .add();

        // Appended, as this list always is - ids are positional and reordering would make an old
        // client read the wrong packet.
        CHANNEL.messageBuilder(
                        net.bullettrain.xenopixelsmod.network.packet.NpcScriptPacket.class,
                        id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.NpcScriptPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.NpcScriptPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.NpcScriptPacket::handle)
                .add();
        CHANNEL.messageBuilder(
                        net.bullettrain.xenopixelsmod.network.packet.NpcScriptResultPacket.class,
                        id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.NpcScriptResultPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.NpcScriptResultPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.NpcScriptResultPacket::handle)
                .add();
        CHANNEL.messageBuilder(
                        net.bullettrain.xenopixelsmod.network.packet.OpenXenoNpcScriptPacket.class,
                        id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.OpenXenoNpcScriptPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.OpenXenoNpcScriptPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.OpenXenoNpcScriptPacket::handle)
                .add();
        CHANNEL.messageBuilder(
                        net.bullettrain.xenopixelsmod.network.packet.BindXenoNpcScriptPacket.class,
                        id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.BindXenoNpcScriptPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.BindXenoNpcScriptPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.BindXenoNpcScriptPacket::handle)
                .add();

        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.NpcProfileRequestPacket.class,
                        id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.NpcProfileRequestPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.NpcProfileRequestPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.NpcProfileRequestPacket::handle)
                .add();
        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.NpcProfileRefreshPacket.class,
                        id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.NpcProfileRefreshPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.NpcProfileRefreshPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.NpcProfileRefreshPacket::handle)
                .add();
        // Nearby NPCs wand screen (same protocol 99).
        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcNearbyPackets.Request.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcNearbyPackets.Request::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcNearbyPackets.Request::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.XenoNpcNearbyPackets.Request::handle)
                .add();
        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcNearbyPackets.List_.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcNearbyPackets.List_::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcNearbyPackets.List_::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.XenoNpcNearbyPackets.List_::handle)
                .add();
        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcNearbyPackets.Action.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcNearbyPackets.Action::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.XenoNpcNearbyPackets.Action::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.XenoNpcNearbyPackets.Action::handle)
                .add();
        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.SecondAuraStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.SecondAuraStatePacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.SecondAuraStatePacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.SecondAuraStatePacket::handle)
                .add();

        // XenoCombat v2 (protocol 102). Appended, never inserted, like everything above.
        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.CombatV2InputPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.CombatV2InputPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.CombatV2InputPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.CombatV2InputPacket::handle)
                .add();
        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.CombatV2StatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.CombatV2StatePacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.CombatV2StatePacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.CombatV2StatePacket::handle)
                .add();

        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.UltimateFinisherCameraPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.UltimateFinisherCameraPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.UltimateFinisherCameraPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.UltimateFinisherCameraPacket::handle)
                .add();

        // Protocol 107 (2026-10-07): native V3 intents and server-owned state/config, appended.
        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.CombatV3InputPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.CombatV3InputPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.CombatV3InputPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.CombatV3InputPacket::handle)
                .add();
        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.CombatV3StatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.CombatV3StatePacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.CombatV3StatePacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.CombatV3StatePacket::handle)
                .add();
        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.CombatV3ConfigPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.CombatV3ConfigPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.CombatV3ConfigPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.CombatV3ConfigPacket::handle)
                .add();

        // Protocol 108: authored V3 camera timelines; preserve every existing sequential id.
        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.CombatV3CameraPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.CombatV3CameraPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.CombatV3CameraPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.CombatV3CameraPacket::handle)
                .add();

        // Protocol 109: native DMZ render proxies, appended after the camera packet.
        CHANNEL.messageBuilder(net.bullettrain.xenopixelsmod.network.packet.CombatV3KiVisualPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(net.bullettrain.xenopixelsmod.network.packet.CombatV3KiVisualPacket::new)
                .encoder(net.bullettrain.xenopixelsmod.network.packet.CombatV3KiVisualPacket::encode)
                .consumerMainThread(net.bullettrain.xenopixelsmod.network.packet.CombatV3KiVisualPacket::handle)
                .add();

        XenoPixelsMod.LOGGER.info("ModNetwork: registered {} packet types (protocol {})", id, PROTOCOL);
    }

    /** Everyone who can see the entity, including the entity itself when it is a player. */
    public static void sendToTrackingAndSelf(net.minecraft.world.entity.Entity entity, Object msg) {
        CHANNEL.sendToTrackingEntityAndSelf(msg, entity);
    }

    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }

    public static void sendToPlayer(ServerPlayer player, Object msg) {
        CHANNEL.sendToPlayer(msg, player);
    }

    public static void sendToAll(Object msg) {
        CHANNEL.sendToAllPlayers(msg);
    }
}

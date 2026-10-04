package net.bullettrain.xenopixelsmod.features.progression;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcTargetKeeper;
import net.bullettrain.xenopixelsmod.missile.ModEntities;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The training partners that really fight (2026-10-02 owner: "dummy clone shadow not figthing me
 * let it have combat brain v9 just with ki blasts and chase ... and the dummytrain also ... and
 * dmz lockon ofcourse"): a native Xeno NPC wearing the trainer's skin and running the V9 combat
 * brain, instead of the standing copy that swung on a timer. Being an ordinary NPC it chases,
 * flies, uses the brain's moves, and can be locked on to like any other.
 *
 * <ul>
 *   <li>{@link Kind#KI} - {@code /xenotrain shadow}: melee, ki blasts and the chase.</li>
 *   <li>{@link Kind#MELEE} - {@code /xenotrain dummytrain}: melee and the chase, no ki at all.</li>
 * </ul>
 * The big ki techniques (waves, disks, named attacks) are off for both.
 *
 * <p>It is kept on its trainer and removed when the trainer leaves, goes far away or dismisses
 * it; one left in a saved world is removed when it loads. A trainer in creative or spectator is
 * not a target for any NPC, so it only fights in survival or adventure.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class TrainingNpc {
    public static final String TAG = "xenopixelsmod_training_npc";
    static final double LEASH = 96.0;

    public enum Kind { KI, MELEE }

    /** Brain actions that fire ki. */
    static final List<String> KI_ACTIONS = List.of("kiblast", "kiwave", "kidisk", "kinamed", "randomki");

    /** Training NPC id to its trainer's id; only what this server run spawned. */
    private static final Map<UUID, UUID> ACTIVE = new ConcurrentHashMap<>();

    private TrainingNpc() {
    }

    /** Which ki actions this kind keeps: ki blasts for the shadow, none for the melee partner. */
    static boolean kiActionOn(Kind kind, String action) {
        return kind == Kind.KI && "kiblast".equals(action);
    }

    /** @return null on success, otherwise why it could not be spawned */
    public static String spawn(ServerPlayer player, int powerPercent, Kind kind) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return "Players only";
        int pct = Math.max(25, Math.min(100, powerPercent));
        TrainingDummySpawner.dismissOwn(player, 48.0);

        XenoNpcEntity npc = ModEntities.XENO_NPC_HUMANOID.get().create(level);
        if (npc == null) return "Failed to create the training NPC";
        npc.moveTo(player.getX() + player.getLookAngle().x * 3.0, player.getY(),
                player.getZ() + player.getLookAngle().z * 3.0, player.getYRot() + 180f, 0);
        npc.npcData().setHome(npc.getX(), npc.getY(), npc.getZ());
        CompoundTag data = npc.getPersistentData();
        data.putBoolean(TAG, true);
        data.putBoolean(ProgressionEvents.DUMMY_TAG, true);
        data.putUUID(ShadowDummyTraining.TAG_OWNER, player.getUUID());
        String name = (kind == Kind.MELEE ? "Training Fighter (" : "Shadow Dummy (") + pct + "%)";
        npc.npcData().setDisplayName(name);
        npc.setCustomName(Component.literal(name));
        npc.setCustomNameVisible(true);
        // Registered before it joins: onJoin removes any training NPC this run did not spawn.
        ACTIVE.put(npc.getUUID(), player.getUUID());
        if (!level.addFreshEntity(npc)) {
            ACTIVE.remove(npc.getUUID());
            return "Could not spawn the training NPC (area blocked?)";
        }

        // An unconfigured NPC reads the fresh defaults: V9 brain on, every DMZ skill granted.
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        profile.combatBrain = true;
        profile.skinPlayer = player.getGameProfile().getName();
        copyLook(profile, player);
        profile.maxHealthOverride = Math.max(20, Math.round(player.getMaxHealth() * pct / 100f));
        profile.kiPower = Math.max(10, (int) (player.getMaxHealth() * pct / 20f));
        profile.setBrainFlag("chase", true);
        for (String action : KI_ACTIONS) {
            profile.setBrainFlag(action, kiActionOn(kind, action));
        }
        profile.write(npc);
        npc.setHealth(npc.getMaxHealth());
        npc.setTarget(player);

        XenoCapabilities.get(player).ifPresent(d -> d.resetDummySession());
        boolean untargetable = !NpcTargetKeeper.isCombatTarget(player);
        player.displayClientMessage(Component.literal("§d" + name + " §7spawned — "
                + (kind == Kind.MELEE ? "melee only, no ki" : "melee and ki blasts")
                + ". /xenotrain dismiss"
                + (untargetable ? " §c(it will not attack you in creative or spectator)" : "")), false);
        return null;
    }

    /**
     * The trainer's DragonMineZ character on the NPC (2026-10-02 owner: "dummytrain dosnt get the
     * plater skin and modellib geko with dmz hair and stuff"): Full appearance, so it is drawn
     * through DragonMineZ's own player model, with the trainer's race, body, face, colours, tail,
     * hair and the form they are in when it is spawned. Without a DMZ character it keeps the
     * plain skin.
     */
    static void copyLook(NpcCombatProfile profile, ServerPlayer player) {
        var stats = net.bullettrain.xenopixelsmod.api.dmz.DmzAccess.stats(player).orElse(null);
        var character = stats == null ? null : stats.getCharacter();
        if (character == null) return;
        var look = profile.appearance;
        look.mode = net.bullettrain.xenopixelsmod.compat.npc.NpcDmzAppearance.Mode.FULL;
        look.gender = orElse(character.getGender(), look.gender);
        look.characterClass = orElse(character.getCharacterClass(), look.characterClass);
        look.bodyType = character.getBodyType();
        look.eyesType = character.getEyesType();
        look.noseType = character.getNoseType();
        look.mouthType = character.getMouthType();
        look.tattooType = character.getTattooType();
        look.boobScale = character.getBoobScale();
        look.bodyColor = orElse(character.getBodyColor(), look.bodyColor);
        look.bodyColor2 = orElse(character.getBodyColor2(), look.bodyColor2);
        look.bodyColor3 = orElse(character.getBodyColor3(), look.bodyColor3);
        look.eye1Color = orElse(character.getEye1Color(), look.eye1Color);
        look.eye2Color = orElse(character.getEye2Color(), look.eye2Color);
        look.activeHeadBone = orElse(character.getActiveHeadBone(), "");
        look.saiyanTail = character.isHasSaiyanTail();
        look.renderHairBase = character.isRenderHairBase();
        profile.raceId = orElse(character.getRaceName(), profile.raceId);
        profile.hairEnabled = true;
        profile.hairColor = orElse(character.getHairColor(), profile.hairColor);
        profile.auraColorHex = orElse(character.getAuraColor(), profile.auraColorHex);
        // A custom hair set is read only when the hair id is 0; otherwise the id names a preset.
        String code = hairCode(character);
        profile.hairCode = code;
        profile.hairStyleId = code.isEmpty() ? Math.max(0, character.getHairId()) : 0;
        if (character.hasActiveForm()) {
            profile.formGroup = orElse(character.getActiveFormGroup(), "");
            profile.formId = orElse(character.getActiveForm(), "");
        }
    }

    private static String hairCode(com.dragonminez.common.stats.character.Character character) {
        var base = character.getHairBase();
        var ssj = character.getHairSSJ();
        var ssj2 = character.getHairSSJ2();
        var ssj3 = character.getHairSSJ3();
        boolean any = false;
        for (var hair : new com.dragonminez.common.hair.CustomHair[] {base, ssj, ssj2, ssj3}) {
            if (hair != null && !hair.isEmpty()) any = true;
        }
        if (!any) return "";
        String code = com.dragonminez.common.hair.HairManager.toFullSetCode(
                nz(base), nz(ssj), nz(ssj2), nz(ssj3));
        return code == null ? "" : code.trim();
    }

    private static com.dragonminez.common.hair.CustomHair nz(com.dragonminez.common.hair.CustomHair hair) {
        return hair == null ? new com.dragonminez.common.hair.CustomHair() : hair;
    }

    private static String orElse(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Removes this player's training NPCs in range; {@code everyone} takes other players' too. */
    public static int dismissNearby(ServerPlayer player, double range, boolean everyone) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return 0;
        int n = 0;
        AABB box = player.getBoundingBox().inflate(range);
        for (XenoNpcEntity npc : level.getEntitiesOfClass(XenoNpcEntity.class, box)) {
            CompoundTag data = npc.getPersistentData();
            if (!data.getBoolean(TAG)) continue;
            if (data.hasUUID(ShadowDummyTraining.TAG_OWNER)
                    && !data.getUUID(ShadowDummyTraining.TAG_OWNER).equals(player.getUUID())
                    && !everyone) {
                continue;
            }
            ACTIVE.remove(npc.getUUID());
            npc.discard();
            n++;
        }
        return n;
    }

    /** Once a second: keep each training NPC on its trainer, and remove the ones left alone. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (ACTIVE.isEmpty() || server.getTickCount() % 20 != 0) return;
        for (var it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            XenoNpcEntity npc = find(server, entry.getKey());
            if (npc == null || !npc.isAlive()) {
                it.remove();
                continue;
            }
            ServerPlayer trainer = server.getPlayerList().getPlayer(entry.getValue());
            if (trainer == null || trainer.level() != npc.level()
                    || trainer.distanceToSqr(npc) > LEASH * LEASH) {
                it.remove();
                npc.discard();
                continue;
            }
            if (trainer.isAlive() && npc.getTarget() != trainer && NpcTargetKeeper.isCombatTarget(trainer)) {
                npc.setTarget(trainer);
            }
        }
    }

    private static XenoNpcEntity find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof XenoNpcEntity npc) return npc;
        }
        return null;
    }

    /** A training NPC saved with the world has no trainer any more: it does not come back. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof XenoNpcEntity npc)) return;
        if (npc.getPersistentData().getBoolean(TAG) && !ACTIVE.containsKey(npc.getUUID())) {
            event.setCanceled(true);
        }
    }
}

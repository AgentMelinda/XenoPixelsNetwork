package net.bullettrain.xenopixelsmod.command;

import com.dragonminez.common.init.entities.questnpc.QuestNPCEntity;
import com.dragonminez.server.world.structure.helper.DMZStructures;
import com.dragonminez.server.world.structure.helper.StructureLocator;
import com.dragonminez.server.world.structure.placement.StructureRepairManager;
import com.dragonminez.server.world.structure.placement.StructureSpawnPlanner;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.compat.dmz.DmzEntityCatalog;
import net.bullettrain.xenopixelsmod.event.DmzMasterProtection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ops tools for DMZ master sites: list, locate, place at a chosen Y, repair, relocate.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoStructureCommands {

    private XenoStructureCommands() {
    }

    private static final Map<String, ResourceKey<Structure>> BY_ID = new LinkedHashMap<>();
    private static final Map<String, String> MASTER_ENTITY = new LinkedHashMap<>();

    static {
        collectStructures();
        MASTER_ENTITY.put("goku_house", "dragonminez:master_goku");
        MASTER_ENTITY.put("roshi_house", "dragonminez:master_roshi");
        MASTER_ENTITY.put("kami_lookout", "dragonminez:master_popo");
        MASTER_ENTITY.put("kamilookout", "dragonminez:master_popo");
        MASTER_ENTITY.put("elder_guru", "dragonminez:master_guru");
        MASTER_ENTITY.put("gero_lab", "dragonminez:master_gero");
        MASTER_ENTITY.put("babidi", "dragonminez:master_babidi");
        MASTER_ENTITY.put("cell_arena", "dragonminez:master_cell");
        MASTER_ENTITY.put("frieza_ship", "dragonminez:master_frieza");
        MASTER_ENTITY.put("piccolo_house", "dragonminez:master_piccolo");
        MASTER_ENTITY.put("oldkai_pillar", "dragonminez:master_oldkai");
        MASTER_ENTITY.put("yamcha_house", "dragonminez:master_yamcha");
        MASTER_ENTITY.put("trunks_ship", "dragonminez:master_trunks");
        MASTER_ENTITY.put("vegeta_pod", "dragonminez:master_vegeta");
        MASTER_ENTITY.put("timechamber", "dragonminez:master_whis");
    }

    private static void collectStructures() {
        try {
            for (Field field : DMZStructures.class.getFields()) {
                if (!ResourceKey.class.isAssignableFrom(field.getType())) continue;
                @SuppressWarnings("unchecked")
                ResourceKey<Structure> key = (ResourceKey<Structure>) field.get(null);
                if (key == null || key.location() == null) continue;
                BY_ID.put(key.location().getPath().toLowerCase(Locale.ROOT), key);
            }
        } catch (Throwable t) {
            XenoPixelsMod.LOGGER.warn("Could not list DMZ structures: {}", t.toString());
        }
    }

    private static final SuggestionProvider<CommandSourceStack> IDS =
            (ctx, b) -> SharedSuggestionProvider.suggest(BY_ID.keySet(), b);

    /** Tab completion for one DragonMineZ group, read from the registry at completion time. */
    private static SuggestionProvider<CommandSourceStack> dmzIds(DmzEntityCatalog.Group group) {
        return (ctx, b) -> SharedSuggestionProvider.suggest(DmzEntityCatalog.ids(group), b);
    }

    /** The thirteen quest-giver identities DragonMineZ ships sidequests for. */
    private static final SuggestionProvider<CommandSourceStack> QUEST_IDS =
            (ctx, b) -> SharedSuggestionProvider.suggest(DmzEntityCatalog.QUEST_IDENTITIES, b);

    private static final SuggestionProvider<CommandSourceStack> MASTER_IDS =
            (ctx, b) -> {
                List<String> ids = new ArrayList<>();
                ids.add("all");
                for (String type : MASTER_ENTITY.values()) {
                    String shortName = shortMaster(type);
                    if (!ids.contains(shortName)) ids.add(shortName);
                }
                return SharedSuggestionProvider.suggest(ids, b);
            };

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("xenostructure")
                .requires(XenoPermissions.require(XenoPermissions.XENOSERVER_SET))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("locate")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(IDS)
                                .executes(ctx -> locate(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("place")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(IDS)
                                .executes(ctx -> place(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"), null, null, null))
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .executes(ctx -> place(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"),
                                                null, IntegerArgumentType.getInteger(ctx, "x"), null))
                                        .then(Commands.argument("y", IntegerArgumentType.integer(-64, 320))
                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                        .executes(ctx -> place(ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "id"),
                                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                                IntegerArgumentType.getInteger(ctx, "z"))))))))
                .then(Commands.literal("repair")
                        .executes(ctx -> repair(ctx.getSource())))
                .then(Commands.literal("relocate")
                        .executes(ctx -> relocate(ctx.getSource())))
                .then(Commands.literal("summon")
                        // Grouped forms first. Brigadier matches literals before arguments, so
                        // "summon master goku" takes this branch while "summon goku_house" still
                        // falls through to the structure-master form below, unchanged.
                        .then(Commands.literal("master")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(dmzIds(DmzEntityCatalog.Group.MASTER))
                                        .executes(ctx -> summonDmz(ctx.getSource(),
                                                DmzEntityCatalog.Group.MASTER,
                                                StringArgumentType.getString(ctx, "id")))))
                        .then(Commands.literal("saga")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(dmzIds(DmzEntityCatalog.Group.SAGA))
                                        .executes(ctx -> summonDmz(ctx.getSource(),
                                                DmzEntityCatalog.Group.SAGA,
                                                StringArgumentType.getString(ctx, "id")))))
                        .then(Commands.literal("mob")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(dmzIds(DmzEntityCatalog.Group.MOB))
                                        .executes(ctx -> summonDmz(ctx.getSource(),
                                                DmzEntityCatalog.Group.MOB,
                                                StringArgumentType.getString(ctx, "id")))))
                        .then(Commands.literal("quest")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(QUEST_IDS)
                                        .executes(ctx -> summonQuestNpc(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id")))))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(MASTER_IDS)
                                .executes(ctx -> summonMaster(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("talk")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(MASTER_IDS)
                                .executes(ctx -> talkToMaster(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("prerequisites")
                        .then(Commands.literal("list")
                                .executes(ctx -> listPrerequisites(ctx.getSource())))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(MASTER_IDS)
                                        .executes(ctx -> removePrerequisite(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id")))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(MASTER_IDS)
                                        .then(Commands.argument("quest", StringArgumentType.word())
                                                .then(Commands.argument("externalQuest", StringArgumentType.word())
                                                        .then(Commands.argument("item", StringArgumentType.word())
                                                                .then(Commands.argument("itemCount", IntegerArgumentType.integer(0, 999))
                                                                        .then(Commands.argument("skill", StringArgumentType.word())
                                                                                .then(Commands.argument("skillLevel", IntegerArgumentType.integer(0, 3))
                                                                                        .executes(ctx -> setPrerequisite(ctx.getSource(),
                                                                                                StringArgumentType.getString(ctx, "id"),
                                                                                                StringArgumentType.getString(ctx, "quest"),
                                                                                                StringArgumentType.getString(ctx, "externalQuest"),
                                                                                                StringArgumentType.getString(ctx, "item"),
                                                                                                IntegerArgumentType.getInteger(ctx, "itemCount"),
                                                                                                StringArgumentType.getString(ctx, "skill"),
                                                                                                IntegerArgumentType.getInteger(ctx, "skillLevel"))))))))))))
                .then(Commands.literal("masters")
                        .executes(ctx -> listMasters(ctx.getSource(), false))
                        .then(Commands.literal("all")
                                .executes(ctx -> listMasters(ctx.getSource(), true))))
                .then(Commands.literal("killdupes")
                        .executes(ctx -> killDupes(ctx.getSource(), false))
                        .then(Commands.literal("all")
                                .executes(ctx -> killDupes(ctx.getSource(), true))))
                .then(Commands.literal("killmasters")
                        .executes(ctx -> {
                            ctx.getSource().sendFailure(Component.literal(
                                    "Usage: /xenostructure killmasters <all|goku|roshi|…>"));
                            return 0;
                        })
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(MASTER_IDS)
                                .executes(ctx -> killMasters(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"), false))
                                .then(Commands.literal("all")
                                        .executes(ctx -> killMasters(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"), true)))))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenostructure list|locate <id>|place <id> [x y z]"
                                    + "|summon <master>|summon master|saga|mob|quest <id>"
                                    + "|talk <master>|prerequisites list|set <master> <quest|-> <external|-> <item|-> <count> <skill|-> <level>|repair|relocate|masters [all]|killdupes [all]|killmasters <all|id> [all]"),
                            false);
                    return 1;
                }));
    }

    private static int list(CommandSourceStack source) {
        if (BY_ID.isEmpty()) {
            source.sendFailure(Component.literal("No DMZ structures found"));
            return 0;
        }
        List<String> names = new ArrayList<>(BY_ID.keySet());
        source.sendSuccess(() -> Component.literal("DMZ structures: " + String.join(", ", names)
                + "\nY=" + XenoServerConfig.dmzStructureY
                + " offset=" + XenoServerConfig.dmzStructureYOffset
                + " master=" + XenoServerConfig.dmzStructureMaster), false);
        return names.size();
    }

    private static int locate(CommandSourceStack source, String id) {
        ResourceKey<Structure> key = resolve(id);
        if (key == null) {
            source.sendFailure(Component.literal("Unknown structure '" + id + "'. /xenostructure list"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition());
        BlockPos found = StructureLocator.locateStructure(level, key, origin);
        if (found == null) {
            source.sendFailure(Component.literal("Not found: " + key.location()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Found " + key.location()
                + " at " + found.getX() + " " + found.getY() + " " + found.getZ()), false);
        return 1;
    }

    private static int place(CommandSourceStack source, String id, Integer xArg, Integer yArg, Integer zArg) {
        ResourceKey<Structure> key = resolve(id);
        if (key == null) {
            source.sendFailure(Component.literal("Unknown structure '" + id + "'"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos here = BlockPos.containing(source.getPosition());
        int x = xArg != null ? xArg : here.getX();
        int z = zArg != null ? zArg : here.getZ();
        int y;
        if (yArg != null && xArg != null && zArg != null) {
            y = yArg;
        } else if (yArg != null && zArg == null) {
            // place <id> <y>  — xArg was bound to that single integer
            y = xArg;
            x = here.getX();
            z = here.getZ();
        } else if (XenoServerConfig.dmzStructureY != 0) {
            y = XenoServerConfig.dmzStructureY + XenoServerConfig.dmzStructureYOffset;
        } else {
            y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z)
                    + XenoServerConfig.dmzStructureYOffset;
        }
        String masterId = MASTER_ENTITY.get(key.location().getPath().toLowerCase(Locale.ROOT));
        clearOld(level, key, here, masterId);
        String cmd = "place structure " + key.location() + " " + x + " " + y + " " + z;
        source.getServer().getCommands().performPrefixedCommand(source, cmd);
        if (XenoServerConfig.dmzStructureMaster && masterId != null) {
            source.getServer().getCommands().performPrefixedCommand(source,
                    "summon " + masterId + " " + x + " " + (y + 1) + " " + z);
        }
        int fx = x, fy = y, fz = z;
        source.sendSuccess(() -> Component.literal(
                "Placed " + key.location() + " at " + fx + " " + fy + " " + fz), true);
        return 1;
    }

    private static void clearOld(ServerLevel level, ResourceKey<Structure> key, BlockPos origin, String masterId) {
        BlockPos found = StructureLocator.locateStructure(level, key, origin);
        BlockPos centre = found != null ? found : origin;
        int r = 28;
        BlockPos.betweenClosedStream(centre.offset(-r, -12, -r), centre.offset(r, 28, r)).forEach(p -> {
            if (!level.getBlockState(p).isAir()) {
                level.removeBlock(p, false);
            }
        });
        for (Entity e : findMasters(level)) {
            String name = masterType(e);
            if (masterId != null && masterId.equals(name)) {
                e.discard();
            } else if (name.startsWith("dragonminez:master_")) {
                e.discard();
            }
        }
    }

    /**
     * Spawns any DragonMineZ NPC in {@code group} at the caller.
     *
     * <p>Deliberately not routed through the vanilla {@code /summon} the master path uses. That
     * works, but it cannot touch the entity afterwards, which the quest-NPC path needs - and doing
     * both the same way means one spawn idiom to reason about. {@code create} then
     * {@code addFreshEntity} is the same pair {@code XenoNpcWandItem} already uses.
     */
    private static int summonDmz(CommandSourceStack source, DmzEntityCatalog.Group group,
                                 String rawId) {
        EntityType<?> type = DmzEntityCatalog.type(group, rawId);
        if (type == null) {
            source.sendFailure(Component.literal(
                    "Unknown DragonMineZ " + group.name().toLowerCase(Locale.ROOT)
                            + " '" + rawId + "'"));
            return 0;
        }
        Entity spawned = placeAtCaller(source, type);
        if (spawned == null) {
            source.sendFailure(Component.literal("Could not create " + type.getDescriptionId()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Summoned ")
                .append(spawned.getDisplayName()), true);
        return 1;
    }

    /**
     * Spawns a DragonMineZ quest NPC carrying {@code identity}.
     *
     * <p>Bulma and the other quest givers are not entity types of their own - there is one
     * {@code dragonminez:quest_npc} and its {@code QuestNpcId} says which character it is. Setting
     * that is the whole difference between a generic NPC and one whose right-click opens
     * DragonMineZ's quest dialogue: the screen is opened by DMZ's own {@code mobInteract}, not by
     * us, so all this has to get right is the identity.
     *
     * <p>{@code saga_bulma} exists as well, but it is a story fighter with no dialogue - summoning
     * that is {@code summon saga bulma}, and it is not the same thing.
     */
    private static int summonQuestNpc(CommandSourceStack source, String rawId) {
        String identity = rawId == null ? "" : rawId.trim().toLowerCase(Locale.ROOT);
        if (!DmzEntityCatalog.isQuestIdentity(identity)) {
            source.sendFailure(Component.literal("Unknown quest giver '" + rawId
                    + "'. DragonMineZ ships: "
                    + String.join(", ", DmzEntityCatalog.QUEST_IDENTITIES)));
            return 0;
        }
        EntityType<?> type = DmzEntityCatalog.questNpcType();
        if (type == null) {
            source.sendFailure(Component.literal("DragonMineZ has no quest_npc entity registered"));
            return 0;
        }
        Entity spawned = placeAtCaller(source, type);
        if (spawned == null) {
            source.sendFailure(Component.literal("Could not create quest_npc"));
            return 0;
        }
        if (!(spawned instanceof QuestNPCEntity npc)) {
            // Registered under that id but not the class we expect: a DMZ change we should not
            // paper over. The entity is left where it is rather than silently removed.
            source.sendFailure(Component.literal(
                    "dragonminez:quest_npc is not a QuestNPCEntity in this DragonMineZ build"));
            return 0;
        }
        npc.setNpcId(identity);
        npc.setHomePosition(npc.getX(), npc.getZ());
        source.sendSuccess(() -> Component.literal("Summoned quest giver " + identity
                + " - right-click to open their dialogue"), true);
        return 1;
    }

    /** Creates {@code type} two blocks in front of the caller and adds it to the world. */
    private static Entity placeAtCaller(CommandSourceStack source, EntityType<?> type) {
        ServerLevel level = source.getLevel();
        Entity entity = type.create(level);
        if (entity == null) {
            return null;
        }
        Vec3 look = Vec3.directionFromRotation(0.0f, source.getRotation().y);
        Vec3 at = source.getPosition().add(look.scale(2.0));
        entity.moveTo(at.x, source.getPosition().y, at.z, source.getRotation().y, 0.0f);
        return level.addFreshEntity(entity) ? entity : null;
    }

    private static int summonMaster(CommandSourceStack source, String rawId) {
        String type = resolveMasterType(rawId);
        if (type == null || !type.startsWith("dragonminez:master_")) {
            source.sendFailure(Component.literal("Unknown master '" + rawId + "'"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        Entity existing = findMasters(level).stream()
                .filter(e -> type.equals(masterType(e)))
                .min((a, b) -> Double.compare(a.distanceToSqr(source.getPosition()),
                        b.distanceToSqr(source.getPosition())))
                .orElse(null);
        if (existing != null && existing.distanceToSqr(source.getPosition()) <= 32 * 32) {
            source.sendFailure(Component.literal("A " + shortMaster(type) + " master is already nearby"));
            return 0;
        }
        source.getServer().getCommands().performPrefixedCommand(source,
                "summon " + type + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        Entity summoned = findMasters(level).stream()
                .filter(e -> type.equals(masterType(e)))
                .min((a, b) -> Double.compare(a.distanceToSqr(source.getPosition()),
                        b.distanceToSqr(source.getPosition())))
                .orElse(null);
        if (summoned == null || summoned.distanceToSqr(source.getPosition()) > 8 * 8) {
            source.sendFailure(Component.literal("DragonMineZ did not create " + type));
            return 0;
        }
        source.sendSuccess(() -> summonMenuMessage(shortMaster(type)), true);
        return 1;
    }

    private static MutableComponent summonMenuMessage(String master) {
        String normalized = master.toLowerCase(Locale.ROOT);
        MutableComponent message = Component.literal("Summoned " + master + " — ");
        message.append(menuAction("OPEN MENU", "talk " + normalized, 0x55FFFF));
        if ("bulma".equals(normalized) || "saga_bulma".equals(normalized)) {
            message.append(Component.literal("  "));
            message.append(menuAction("BULMA QUESTS", "talk " + normalized, 0xFFAA55));
            message.append(Component.literal("  "));
            message.append(Component.literal("Saga/quest dialogue is provided by DragonMineZ when available.")
                    .withStyle(Style.EMPTY.withColor(0xAAAAAA)));
        }
        return message;
    }

    private static MutableComponent menuAction(String label, String command, int color) {
        return Component.literal("[" + label + "]").withStyle(style -> style
                .withColor(color)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/xenostructure " + command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Open " + label.toLowerCase(Locale.ROOT)))));
    }

    private static int talkToMaster(CommandSourceStack source, String rawId) {
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            source.sendFailure(Component.literal("This command requires a player"));
            return 0;
        }
        String type = resolveMasterType(rawId);
        if (type == null) {
            source.sendFailure(Component.literal("Unknown master '" + rawId + "'"));
            return 0;
        }
        var gate = net.bullettrain.xenopixelsmod.features.progression.MasterPrerequisites.check(player, shortMaster(type));
        if (!gate.allowed()) {
            source.sendFailure(Component.literal(gate.message()));
            return 0;
        }
        Entity master = findMasters(source.getLevel()).stream()
                .filter(e -> type.equals(masterType(e)))
                .min((a, b) -> Double.compare(a.distanceToSqr(player.getPosition(1.0f)),
                        b.distanceToSqr(player.getPosition(1.0f))))
                .orElse(null);
        if (master == null || master.distanceToSqr(player.getPosition(1.0f)) > 8 * 8) {
            source.sendFailure(Component.literal("No nearby " + rawId + " master to talk to"));
            return 0;
        }
        InteractionResult result = master.interact(player, InteractionHand.MAIN_HAND);
        if (result.consumesAction()) {
            source.sendSuccess(() -> Component.literal("Opened " + shortMaster(type) + " master dialogue"), false);
            return 1;
        }
        source.sendFailure(Component.literal("The master did not accept interaction"));
        return 0;
    }

    private static int listPrerequisites(CommandSourceStack source) {
        var entries = net.bullettrain.xenopixelsmod.features.progression.MasterPrerequisites.snapshot();
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No master prerequisites configured"), false);
            return 1;
        }
        entries.forEach((id, req) -> source.sendSuccess(() -> Component.literal(id + " -> quest="
                + req.questId() + ", external=" + req.externalQuestTitle() + ", item=" + req.itemId()
                + " x" + req.itemCount() + ", skill=" + req.skillId() + " Lv." + req.skillLevel()), false));
        return entries.size();
    }

    private static int setPrerequisite(CommandSourceStack source, String id, String quest,
                                       String external, String item, int itemCount, String skill, int level) {
        net.bullettrain.xenopixelsmod.features.progression.MasterPrerequisites.put(id,
                new net.bullettrain.xenopixelsmod.features.progression.MasterPrerequisites.Requirement(
                        emptyArg(quest), emptyArg(external), emptyArg(item), itemCount, emptyArg(skill), level));
        source.sendSuccess(() -> Component.literal("Saved prerequisites for " + id), true);
        return 1;
    }

    private static int removePrerequisite(CommandSourceStack source, String id) {
        net.bullettrain.xenopixelsmod.features.progression.MasterPrerequisites.remove(id);
        source.sendSuccess(() -> Component.literal("Removed prerequisites for " + id), true);
        return 1;
    }

    private static String emptyArg(String value) {
        return "-".equals(value) ? "" : value;
    }

    private static int listMasters(CommandSourceStack source, boolean allDims) {
        StringBuilder out = new StringBuilder();
        int total = 0;
        int dupeTypes = 0;
        for (ServerLevel level : levels(source, allDims)) {
            Map<String, List<Entity>> grouped = groupMasters(level);
            if (grouped.isEmpty()) continue;
            out.append("§7").append(level.dimension().location()).append("§r\n");
            for (Map.Entry<String, List<Entity>> e : grouped.entrySet()) {
                List<Entity> list = e.getValue();
                total += list.size();
                if (list.size() > 1) dupeTypes++;
                out.append("  ").append(list.size() > 1 ? "§c" : "§a")
                        .append(e.getKey()).append(" x").append(list.size()).append("§r");
                for (Entity m : list) {
                    out.append(" @ ").append(m.blockPosition().getX()).append(" ")
                            .append(m.blockPosition().getY()).append(" ")
                            .append(m.blockPosition().getZ());
                }
                out.append("\n");
            }
        }
        if (total == 0) {
            source.sendFailure(Component.literal("No loaded DMZ masters"
                    + (allDims ? " in any dimension" : " in this dimension")));
            return 0;
        }
        int extra = dupeTypes;
        int count = total;
        String report = out.toString();
        source.sendSuccess(() -> Component.literal(report + "Total " + count
                + " loaded master(s), " + extra + " type(s) with duplicates."
                + " /xenostructure killdupes to keep one of each."), false);
        return count;
    }

    private static int killDupes(CommandSourceStack source, boolean allDims) {
        Vec3 here = source.getPosition();
        int removed = 0;
        int kept = 0;
        for (ServerLevel level : levels(source, allDims)) {
            Map<String, List<Entity>> grouped = groupMasters(level);
            for (Map.Entry<String, List<Entity>> e : grouped.entrySet()) {
                List<Entity> list = e.getValue();
                if (list.isEmpty()) continue;
                Entity keep = pickKeep(level, e.getKey(), list, here);
                for (Entity m : list) {
                    if (m == keep) {
                        kept++;
                        continue;
                    }
                    m.discard();
                    removed++;
                }
            }
        }
        if (kept == 0 && removed == 0) {
            source.sendFailure(Component.literal("No loaded DMZ masters to clean"));
            return 0;
        }
        int k = kept;
        int r = removed;
        source.sendSuccess(() -> Component.literal(
                "Kept " + k + " master(s), discarded " + r + " duplicate(s)"), true);
        return removed;
    }

    private static int killMasters(CommandSourceStack source, String id, boolean allDims) {
        String want = "all".equalsIgnoreCase(id) ? "all" : resolveMasterType(id);
        if (want == null) {
            source.sendFailure(Component.literal("Unknown master '" + id
                    + "'. Try goku, roshi, popo, or all"));
            return 0;
        }
        int removed = 0;
        for (ServerLevel level : levels(source, allDims)) {
            for (Entity e : findMasters(level)) {
                if ("all".equals(want) || want.equals(masterType(e))) {
                    e.discard();
                    removed++;
                }
            }
        }
        if (removed == 0) {
            source.sendFailure(Component.literal("No matching loaded masters"));
            return 0;
        }
        int n = removed;
        source.sendSuccess(() -> Component.literal("Discarded " + n + " master(s)"), true);
        return removed;
    }

    private static List<ServerLevel> levels(CommandSourceStack source, boolean allDims) {
        List<ServerLevel> out = new ArrayList<>();
        if (allDims) {
            for (ServerLevel level : source.getServer().getAllLevels()) {
                out.add(level);
            }
        } else {
            out.add(source.getLevel());
        }
        return out;
    }

    private static Map<String, List<Entity>> groupMasters(ServerLevel level) {
        Map<String, List<Entity>> grouped = new LinkedHashMap<>();
        for (Entity e : findMasters(level)) {
            grouped.computeIfAbsent(masterType(e), k -> new ArrayList<>()).add(e);
        }
        return grouped;
    }

    private static List<Entity> findMasters(ServerLevel level) {
        List<Entity> out = new ArrayList<>();
        try {
            for (Entity e : level.getEntities().getAll()) {
                if (isMaster(e)) out.add(e);
            }
            if (!out.isEmpty()) return out;
        } catch (Throwable ignored) {
            // EntityLookup API differs; fall through to player-radius scan.
        }
        for (var player : level.players()) {
            AABB box = player.getBoundingBox().inflate(384);
            for (Entity e : level.getEntities(player, box)) {
                if (isMaster(e) && !out.contains(e)) out.add(e);
            }
        }
        BlockPos spawn = level.getSharedSpawnPos();
        AABB spawnBox = new AABB(spawn).inflate(384);
        for (Entity e : level.getEntities(null, spawnBox)) {
            if (isMaster(e) && !out.contains(e)) out.add(e);
        }
        return out;
    }

    private static boolean isMaster(Entity e) {
        if (e == null || !e.isAlive()) return false;
        if (DmzMasterProtection.isDmzMaster(e)) return true;
        return masterType(e).startsWith("dragonminez:master_");
    }

    private static String masterType(Entity e) {
        ResourceLocation loc = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        return loc != null ? loc.toString() : "";
    }

    private static String shortMaster(String typeId) {
        int i = typeId.lastIndexOf("master_");
        return i >= 0 ? typeId.substring(i + "master_".length()) : typeId;
    }

    private static String resolveMasterType(String raw) {
        if (raw == null) return null;
        String n = raw.toLowerCase(Locale.ROOT);
        if (n.startsWith("dragonminez:")) {
            return MASTER_ENTITY.containsValue(n) ? n : null;
        }
        if (n.startsWith("master_")) {
            return MASTER_ENTITY.containsValue("dragonminez:" + n) ? "dragonminez:" + n : null;
        }
        String guess = "dragonminez:master_" + n;
        for (String type : MASTER_ENTITY.values()) {
            if (type.equals(guess) || shortMaster(type).equals(n)) return type;
        }
        return null;
    }

    private static Entity pickKeep(ServerLevel level, String typeId, List<Entity> list, Vec3 here) {
        BlockPos anchor = BlockPos.containing(here);
        for (Map.Entry<String, String> e : MASTER_ENTITY.entrySet()) {
            if (!e.getValue().equals(typeId)) continue;
            ResourceKey<Structure> key = BY_ID.get(e.getKey());
            if (key == null) continue;
            BlockPos found = StructureLocator.locateStructure(level, key, anchor);
            if (found != null) {
                anchor = found;
                break;
            }
        }
        Entity best = list.get(0);
        double bestD = best.distanceToSqr(Vec3.atCenterOf(anchor));
        for (int i = 1; i < list.size(); i++) {
            Entity m = list.get(i);
            double d = m.distanceToSqr(Vec3.atCenterOf(anchor));
            if (d < bestD) {
                best = m;
                bestD = d;
            }
        }
        return best;
    }

    private static int repair(CommandSourceStack source) {
        StructureRepairManager.reset();
        source.sendSuccess(() -> Component.literal("DMZ structure repair reset"), true);
        return 1;
    }

    private static int relocate(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        StructureSpawnPlanner.reset();
        StructureSpawnPlanner.onLevelLoad(level);
        source.sendSuccess(() -> Component.literal("DMZ structure planner reset for this dimension"), true);
        return 1;
    }

    private static ResourceKey<Structure> resolve(String raw) {
        if (raw == null) return null;
        String n = raw.toLowerCase(Locale.ROOT);
        ResourceKey<Structure> direct = BY_ID.get(n);
        if (direct != null) return direct;
        ResourceLocation loc = ResourceLocation.tryParse(raw.contains(":") ? raw : "dragonminez:" + n);
        if (loc == null) return null;
        return BY_ID.get(loc.getPath().toLowerCase(Locale.ROOT));
    }
}

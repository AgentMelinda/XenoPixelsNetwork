package net.bullettrain.xenopixelsmod.command;

import com.dragonminez.common.hair.CustomHair;
import com.dragonminez.common.hair.HairManager;
import com.dragonminez.common.hair.HairCodec;
import com.dragonminez.common.hair.HairStyleSlot;
import java.util.EnumMap;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.compat.util.LazyOptional;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalInt;

/**
 * {@code /xenopixels genhaircode <color> [preset]} — real {@link HairManager} full-set
 * (or single) code plus a tint for the NPC DMZ tab. Color is the tab Color field;
 * the code is the mesh.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class HairCodeCommands {
    private static final int DEFAULT_PRESET = 1;

    private HairCodeCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("xenopixels")
                .then(Commands.literal("genhaircode")
                        .requires(XenoPermissions.require(XenoPermissions.GENHAIRCODE))
                        .executes(HairCodeCommands::usage)
                        .then(Commands.argument("color", StringArgumentType.word())
                                .executes(ctx -> generate(ctx, -1, false))
                                .then(Commands.literal("apply")
                                        .executes(ctx -> generate(ctx, -1, true)))
                                .then(Commands.argument("preset", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> generate(ctx,
                                                IntegerArgumentType.getInteger(ctx, "preset"), false))
                                        .then(Commands.literal("apply")
                                                .executes(ctx -> generate(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "preset"), true)))))));
    }

    private static int usage(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Usage: /xenopixels genhaircode <color> [preset] [apply]  (look at an NPC + apply)"),
                false);
        return 1;
    }

    private static int generate(CommandContext<CommandSourceStack> ctx, int presetArg, boolean apply) {
        CommandSourceStack source = ctx.getSource();
        String rawColor = StringArgumentType.getString(ctx, "color");
        OptionalInt rgb = NpcCombatProfile.parseHexColor(rawColor);
        if (rgb.isEmpty()) {
            source.sendFailure(Component.literal(
                    "Invalid color '" + rawColor + "'. Use FFFFFF, #FF00AA, 0xFF00AA, or a name (white, gold, ssj)."));
            return 0;
        }
        String hex = NpcCombatProfile.formatHex(rgb.getAsInt());

        Encoded encoded;
        try {
            encoded = encode(source.getEntity() instanceof ServerPlayer player ? player : null, presetArg);
        } catch (RuntimeException e) {
            source.sendFailure(Component.literal("Hair encode failed: " + e.getMessage()));
            return 0;
        }
        if (encoded == null || encoded.code == null || encoded.code.isBlank()) {
            source.sendFailure(Component.literal("Could not build a DMZ hair code (empty mesh)."));
            return 0;
        }

        Path file = writeFile(source, hex, encoded.code);
        String origin = encoded.origin;
        source.sendSuccess(() -> Component.literal("DMZ hair " + origin
                + "  Color " + hex + "  Code " + encoded.code.length() + " chars")
                .withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("[Click to copy hair code]")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, encoded.code))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Copy " + encoded.code.length()
                                        + " chars — paste into NPC DMZ tab Code")))), false);
        source.sendSuccess(() -> Component.literal("[Click to copy color " + hex + "]")
                .withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, hex))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Paste into NPC DMZ tab Color")))), false);
        if (file != null) {
            source.sendSuccess(() -> Component.literal("Also wrote " + file.toAbsolutePath())
                    .withStyle(ChatFormatting.GRAY), false);
        }
        if (apply) {
            if (!(source.getEntity() instanceof ServerPlayer player)) {
                source.sendFailure(Component.literal("apply must be run as a player looking at an NPC"));
                return 0;
            }
            LivingEntity npc = lookedAt(player);
            if (npc == null) {
                source.sendFailure(Component.literal("Look at an NPC, then run /xenopixels genhaircode "
                        + rawColor + " apply"));
                return 0;
            }
            NpcCombatProfile profile = NpcCombatProfile.read(npc);
            profile.hairEnabled = true;
            profile.hairCode = encoded.code;
            profile.hairColor = hex;
            profile.write(npc);
            source.sendSuccess(() -> Component.literal("Applied hair to "
                    + npc.getName().getString() + "  Color " + hex + "  Code " + encoded.code.length() + " chars")
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        source.sendSuccess(() -> Component.literal(
                "Look at the NPC and run /xenopixels genhaircode " + rawColor + " apply  (GUI paste truncates)")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static LivingEntity lookedAt(ServerPlayer player) {
        double reach = 20.0;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(reach));
        AABB box = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                entity -> entity instanceof LivingEntity && entity.isAlive() && entity != player,
                reach * reach);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    private static Encoded encode(ServerPlayer player, int presetArg) {
        if (presetArg > 0) {
            return fromPreset(presetArg);
        }
        Encoded fromPlayer = fromPlayer(player);
        if (fromPlayer != null) {
            return fromPlayer;
        }
        return fromPreset(DEFAULT_PRESET);
    }

    private static Encoded fromPlayer(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        LazyOptional<StatsData> opt = StatsProvider.get(StatsCapability.INSTANCE, player);
        StatsData data = opt.orElse(null);
        if (data == null) {
            return null;
        }
        Character character = data.getCharacter();
        if (character == null) {
            return null;
        }
        EnumMap<HairStyleSlot, CustomHair> styles = new EnumMap<>(HairStyleSlot.class);
        for (HairStyleSlot slot : HairStyleSlot.values()) styles.put(slot, nz(character.getHairStyle(slot)));
        if (styles.values().stream().anyMatch(hair -> !hair.isEmpty())) {
            String code = HairCodec.toFullSetCode(styles);
            if (code != null && !code.isBlank()) return new Encoded(code, "from your DMZ hair");
        }
        int hairId = character.getHairId();
        if (hairId > 0) {
            Encoded preset = fromPreset(hairId);
            if (preset != null) {
                return new Encoded(preset.code, "from your DMZ preset #" + hairId);
            }
        }
        return null;
    }

    private static Encoded fromPreset(int id) {
        CustomHair base = HairManager.getPresetStyle(id, HairStyleSlot.BASE);
        if (base == null || base.isEmpty()) return null;
        EnumMap<HairStyleSlot, CustomHair> styles = new EnumMap<>(HairStyleSlot.class);
        for (HairStyleSlot slot : HairStyleSlot.values()) styles.put(slot, nz(HairManager.getPresetStyle(id, slot)));
        String code = HairCodec.toFullSetCode(styles);
        return code == null || code.isBlank() ? null : new Encoded(code, "preset #" + id + " full set");
    }

    private static CustomHair nz(CustomHair hair) {
        return hair == null ? new CustomHair() : hair;
    }

    private static Path writeFile(CommandSourceStack source, String hex, String code) {
        try {
            Path file = source.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("xenopixels_hair_code.txt");
            Files.writeString(file, "COLOR=" + hex + System.lineSeparator()
                    + "CODE=" + code + System.lineSeparator(), StandardCharsets.UTF_8);
            return file;
        } catch (Exception e) {
            XenoPixelsMod.LOGGER.warn("Could not write hair code file: {}", e.toString());
            return null;
        }
    }

    private record Encoded(String code, String origin) {}
}

package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.fx.effek.EffectSlot;
import net.bullettrain.xenopixelsmod.fx.effek.XenoEffects;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * {@code /xenofx play <slot> [scale]}: plays an Effekseer effect at the block you look at (or four
 * blocks ahead), facing your look direction - for checking plume direction and effect sizes.
 * {@code /xenofx list} names the slots. Op level 2.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class EffectCommands {
    private EffectCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(command());
    }

    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("xenofx")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("Effects: " + names()), false);
                    return EffectSlot.values().length;
                }))
                .then(Commands.literal("play")
                        .then(Commands.argument("slot", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(EffectSlot.values()).map(EffectCommands::name), builder))
                                .executes(ctx -> play(ctx.getSource(), StringArgumentType.getString(ctx, "slot"), 1.0f))
                                .then(Commands.argument("scale", FloatArgumentType.floatArg(0.05f, 20.0f))
                                        .executes(ctx -> play(ctx.getSource(), StringArgumentType.getString(ctx, "slot"),
                                                FloatArgumentType.getFloat(ctx, "scale"))))));
    }

    static EffectSlot slot(String name) {
        for (EffectSlot slot : EffectSlot.values()) {
            if (name(slot).equalsIgnoreCase(name)) return slot;
        }
        return null;
    }

    private static String name(EffectSlot slot) {
        return slot.name().toLowerCase(Locale.ROOT);
    }

    private static String names() {
        return Arrays.stream(EffectSlot.values()).map(EffectCommands::name).collect(Collectors.joining(", "));
    }

    private static int play(CommandSourceStack source, String name, float scale) {
        EffectSlot slot = slot(name);
        if (slot == null) {
            source.sendFailure(Component.literal("Unknown effect '" + name + "'. Effects: " + names()));
            return 0;
        }
        if (!(source.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            source.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        HitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(32)), ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        Vec3 at = hit.getType() == HitResult.Type.BLOCK
                ? Vec3.atCenterOf(((BlockHitResult) hit).getBlockPos()).add(Vec3.atLowerCornerOf(
                        ((BlockHitResult) hit).getDirection().getNormal()).scale(0.6))
                : eye.add(look.scale(4));
        // Effects the game rides on an entity are previewed riding on you, the way they play.
        switch (slot) {
            case MISSILE_THRUSTER, SPARKING_FLIGHT -> {
                boolean ok = XenoEffects.playBoundLook(level, slot, player.position(), player.getId(), scale);
                source.sendSuccess(() -> Component.literal(name(slot) + ": "
                        + (ok ? "riding on you, along your look" : "not played - check the effekseer switches")), false);
                return ok ? 1 : 0;
            }
            case SPARKING_AURA, SPARKING_BURST -> {
                boolean ok = XenoEffects.playBound(level, slot, player.position(), player.getId(), scale);
                source.sendSuccess(() -> Component.literal(name(slot) + ": "
                        + (ok ? "following you" : "not played - check the effekseer switches")), false);
                return ok ? 1 : 0;
            }
            default -> { }
        }
        XenoEffects.Outcome outcome = XenoEffects.attempt(level, slot, at, look, scale, -1);
        source.sendSuccess(() -> Component.literal(name(slot) + ": " + switch (outcome) {
            case PLAYED -> "played" + (slot.upright() ? " (upright)" : " facing your look");
            case DUPLICATE -> "skipped (already played on that target this tick)";
            case UNAVAILABLE -> "not played - check /xenoget effekseerEnabled and its category switch";
        }), false);
        return outcome == XenoEffects.Outcome.PLAYED ? 1 : 0;
    }
}

package net.bullettrain.xenopixelsmod.space.dragonball;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.block.ModBlocks;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * Super Dragon Balls v1 (design-doc 16; owner chose items + radar first). Switch
 * {@link XenoSpaceConfig#cosmicDragonBallsEnabled()}, default off. Seven balls are scattered deterministically (world seed +
 * scatter cycle) around the spawn of the configured host dimensions; a ball becomes a block only when a player comes near
 * its spot. The Cosmic Ball Scanner reports bearing and signal strength (exact within 64 blocks) and also tracks a ball
 * another player is carrying. With all seven in the inventory, sneak-using a ball summons the dragon and offers the wish
 * catalog; granting a wish consumes the balls, re-scatters them and makes them dormant for a number of days.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class CosmicDragonBalls {
    public static final int COUNT = 7;
    private static final int EXACT_RANGE = 64, PLACE_RANGE = 48;
    private static final long OFFER_TICKS = 20L * 120;
    private static final Map<UUID, Long> OFFERS = new HashMap<>();

    private CosmicDragonBalls() {}

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!XenoSpaceConfig.cosmicDragonBallsEnabled() || server.getTickCount() % 40 != 0) return;
        var data = DragonBallSavedData.get(server);
        if (data.cycle < 0) scatter(server, data, 0);
        for (int number = 1; number <= COUNT; number++) {
            var ball = data.ball(number);
            if (ball.status != DragonBallSavedData.Status.SCATTERED) continue;
            ServerLevel level = level(server, ball.dimension);
            if (level == null) continue;
            for (ServerPlayer player : level.players()) {
                double dx = player.getX() - ball.x, dz = player.getZ() - ball.z;
                if (dx * dx + dz * dz > PLACE_RANGE * PLACE_RANGE || !level.isLoaded(new BlockPos(ball.x, 0, ball.z))) continue;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ball.x, ball.z);
                if (!level.setBlock(new BlockPos(ball.x, y, ball.z), ModBlocks.COSMIC_DRAGON_BALLS.get(number - 1).get().defaultBlockState(), 3)) {
                    XenoPixelsMod.LOGGER.warn("Super Dragon Ball {} could not be placed at {} {} {} {}",
                            number, ball.dimension, ball.x, y, ball.z);
                    continue;
                }
                ball.status = DragonBallSavedData.Status.PLACED;
                ball.y = y;
                data.setDirty();
                XenoPixelsMod.LOGGER.info("Super Dragon Ball {} appeared at {} {} {} {}", number, ball.dimension, ball.x, y, ball.z);
                break;
            }
        }
    }

    /** Deterministic scatter for a cycle; balls already placed in loaded chunks are removed first. */
    static void scatter(MinecraftServer server, DragonBallSavedData data, long cycle) {
        var hosts = new ArrayList<String>();
        for (String host : XenoSpaceConfig.cosmicDragonBallHosts()) if (level(server, host) != null) hosts.add(host);
        if (hosts.isEmpty()) hosts.add("minecraft:overworld");
        int radius = XenoSpaceConfig.cosmicDragonBallRadius();
        for (int number = 1; number <= COUNT; number++) {
            var ball = data.ball(number);
            if (ball.status == DragonBallSavedData.Status.HELD) continue;
            if (ball.status == DragonBallSavedData.Status.PLACED) {
                ServerLevel old = level(server, ball.dimension);
                BlockPos pos = new BlockPos(ball.x, ball.y, ball.z);
                if (old != null && old.isLoaded(pos) && old.getBlockState(pos).is(ModBlocks.COSMIC_DRAGON_BALLS.get(number - 1).get()))
                    old.removeBlock(pos, false);
            }
            assignScatterTarget(server, ball, number, cycle, hosts, radius);
            // The successful wish path explicitly resets the seven consumed balls before calling us.
            ball.status = statusAfterScatter(ball.status);
        }
        data.cycle = cycle;
        data.setDirty();
        XenoPixelsMod.LOGGER.info("Super Dragon Balls scattered (cycle {}) across {}", cycle, hosts);
    }

    /** True when this block is the ball the saved data points at; false for a stale copy left in a chunk that was unloaded during a re-scatter. */
    public static boolean isCurrent(ServerLevel level, int number, BlockPos pos) {
        var ball = DragonBallSavedData.get(level.getServer()).ball(number);
        return ball.status == DragonBallSavedData.Status.PLACED && ball.dimension.equals(level.dimension().location().toString())
                && ball.x == pos.getX() && ball.y == pos.getY() && ball.z == pos.getZ();
    }

    /** Called by the ball block when a player picks a ball up. */
    public static void onCollected(MinecraftServer server, int number) {
        var data = DragonBallSavedData.get(server);
        data.ball(number).status = DragonBallSavedData.Status.HELD;
        data.setDirty();
    }

    /** Called by the ball block when a player places a ball back in the world, so the radar keeps tracking it. */
    public static void onPlaced(ServerLevel level, int number, BlockPos pos) {
        var data = DragonBallSavedData.get(level.getServer());
        var ball = data.ball(number);
        ball.status = DragonBallSavedData.Status.PLACED;
        ball.dimension = level.dimension().location().toString();
        ball.x = pos.getX(); ball.y = pos.getY(); ball.z = pos.getZ();
        data.setDirty();
    }

    /** Cosmic Ball Scanner: bearing and signal to the nearest ball not in this player's inventory. */
    public static Component radar(ServerPlayer player) {
        if (!XenoSpaceConfig.cosmicDragonBallsEnabled()) return Component.translatable("message.xenopixelsmod.radar_off");
        var server = player.getServer();
        var data = DragonBallSavedData.get(server);
        String here = player.level().dimension().location().toString();
        int best = -1;
        double bestDistance = Double.POSITIVE_INFINITY, bx = 0, bz = 0;
        List<String> elsewhere = new ArrayList<>();
        for (int number = 1; number <= COUNT; number++) {
            if (holds(player, number)) continue;
            var ball = data.ball(number);
            String dimension = ball.dimension;
            double x = ball.x, z = ball.z;
            if (ball.status == DragonBallSavedData.Status.HELD) {
                ServerPlayer holder = holder(server, number);
                if (holder == null) continue;
                dimension = holder.level().dimension().location().toString();
                x = holder.getX(); z = holder.getZ();
            }
            if (!dimension.equals(here)) { if (!elsewhere.contains(dimension)) elsewhere.add(dimension); continue; }
            double d = Math.hypot(x - player.getX(), z - player.getZ());
            if (d < bestDistance) { bestDistance = d; best = number; bx = x; bz = z; }
        }
        if (best < 0)
            return elsewhere.isEmpty() ? Component.translatable("message.xenopixelsmod.radar_none")
                    : Component.translatable("message.xenopixelsmod.radar_elsewhere", String.join(", ", elsewhere));
        if (bestDistance <= EXACT_RANGE)
            return Component.translatable("message.xenopixelsmod.radar_exact", best, (int) bx, (int) bz, (int) bestDistance);
        String signal = bestDistance < 500 ? "strong" : bestDistance < 2000 ? "medium" : "weak";
        return Component.translatable("message.xenopixelsmod.radar_bearing", best, bearing(bx - player.getX(), bz - player.getZ()),
                Component.translatable("message.xenopixelsmod.radar_signal_" + signal));
    }

    /** Sneak-use of a ball with all seven held: summon and offer the wish catalog. */
    public static void trySummon(ServerPlayer player) {
        if (!XenoSpaceConfig.cosmicDragonBallsEnabled()) return;
        var server = player.getServer();
        var data = DragonBallSavedData.get(server);
        long day = server.overworld().getDayTime() / 24_000L;
        if (day < data.dormantUntilDay) {
            player.displayClientMessage(Component.translatable("message.xenopixelsmod.dragon_dormant", data.dormantUntilDay - day), true);
            return;
        }
        for (int number = 1; number <= COUNT; number++)
            if (!holds(player, number)) { player.displayClientMessage(Component.translatable("message.xenopixelsmod.dragon_missing", number), true); return; }
        var wishes = WishCatalog.wishes();
        if (wishes.isEmpty()) { player.displayClientMessage(Component.translatable("message.xenopixelsmod.dragon_no_wishes"), true); return; }
        ServerLevel level = player.serverLevel();
        for (int i = 0; i < 3; i++) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt == null) break;
            bolt.moveTo(player.getX() + (i - 1) * 6, player.getY(), player.getZ() + (i % 2) * 6);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 2f, 0.6f);
        server.getPlayerList().broadcastSystemMessage(Component.translatable("message.xenopixelsmod.dragon_summoned", player.getDisplayName()), false);
        OFFERS.put(player.getUUID(), server.overworld().getGameTime() + OFFER_TICKS);
        var entries = new ArrayList<WishOffer.Entry>();
        for (var wish : wishes) entries.add(new WishOffer.Entry(wish.id(), wish.name(), wish.description()));
        AddonNetwork.sendToPlayer(player, new WishOffer(entries));
    }

    /** The player's wish choice (serverbound). Everything is re-checked: offer still open, all seven still held. */
    public static void grant(ServerPlayer player, String wishId) {
        var server = player.getServer();
        Long expiry = OFFERS.remove(player.getUUID());
        if (expiry == null || server.overworld().getGameTime() > expiry) return;
        for (int number = 1; number <= COUNT; number++) if (!holds(player, number)) return;
        var wish = WishCatalog.byId(wishId);
        if (wish == null) return;
        for (int number = 1; number <= COUNT; number++) consumeOne(player, number);
        for (var reward : wish.rewards()) applyReward(player, reward);
        var data = DragonBallSavedData.get(server);
        for (int number = 1; number <= COUNT; number++) data.ball(number).status = DragonBallSavedData.Status.SCATTERED;
        scatter(server, data, data.cycle + 1);
        long day = server.overworld().getDayTime() / 24_000L;
        data.dormantUntilDay = day + XenoSpaceConfig.cosmicDragonBallDormantDays();
        data.setDirty();
        server.getPlayerList().broadcastSystemMessage(Component.translatable("message.xenopixelsmod.dragon_granted",
                player.getDisplayName(), wish.name()), false);
        XenoPixelsMod.LOGGER.info("Super Dragon Balls: {} wished for {}", player.getScoreboardName(), wish.id());
    }

    /** Admin re-scatter. HELD items remain HELD unless an operator explicitly recovers one slot. */
    public static void adminScatter(MinecraftServer server) {
        var data = DragonBallSavedData.get(server);
        scatter(server, data, data.cycle + 1);
        data.dormantUntilDay = 0;
        data.setDirty();
    }

    /** Explicit operator recovery. A held item may still exist in offline or unloaded storage. */
    public static String recoverHeld(MinecraftServer server, int number) {
        if (number < 1 || number > COUNT) return "Super Dragon Ball slot must be in [1, " + COUNT + "].";
        var data = DragonBallSavedData.get(server);
        var ball = data.ball(number);
        if (ball.status != DragonBallSavedData.Status.HELD)
            return "Super Dragon Ball " + number + " is " + ball.status.name().toLowerCase(Locale.ROOT) + "; only HELD balls can be recovered.";
        var hosts = configuredHosts(server);
        assignScatterTarget(server, ball, number, data.cycle, hosts, XenoSpaceConfig.cosmicDragonBallRadius());
        ball.status = statusAfterAdminRecovery(ball.status);
        data.setDirty();
        return "Super Dragon Ball " + number + " marked SCATTERED. If its item remains in offline inventory, a container, or an unloaded item entity, this may create a duplicate.";
    }

    static DragonBallSavedData.Status statusAfterScatter(DragonBallSavedData.Status current) {
        return current == DragonBallSavedData.Status.HELD
                ? DragonBallSavedData.Status.HELD : DragonBallSavedData.Status.SCATTERED;
    }

    static DragonBallSavedData.Status statusAfterAdminRecovery(DragonBallSavedData.Status current) {
        return current == DragonBallSavedData.Status.HELD ? DragonBallSavedData.Status.SCATTERED : current;
    }

    private static List<String> configuredHosts(MinecraftServer server) {
        var hosts = new ArrayList<String>();
        for (String host : XenoSpaceConfig.cosmicDragonBallHosts()) if (level(server, host) != null) hosts.add(host);
        if (hosts.isEmpty()) hosts.add("minecraft:overworld");
        return hosts;
    }

    private static void assignScatterTarget(MinecraftServer server, DragonBallSavedData.Ball ball, int number,
                                            long cycle, List<String> hosts, int radius) {
        var random = new SplittableRandom(mix(server.overworld().getSeed() ^ mix(cycle * 31 + number) ^ 0x5D_B0_11L));
        String host = hosts.get(random.nextInt(hosts.size()));
        ServerLevel level = level(server, host);
        BlockPos spawn = level == null ? BlockPos.ZERO : level.getSharedSpawnPos();
        double angle = random.nextDouble(Math.PI * 2), distance = random.nextDouble(radius * 0.2, radius);
        ball.dimension = host;
        ball.x = spawn.getX() + (int) Math.round(Math.cos(angle) * distance);
        ball.z = spawn.getZ() + (int) Math.round(Math.sin(angle) * distance);
        ball.y = 0;
    }

    public static String status(MinecraftServer server) {
        var data = DragonBallSavedData.get(server);
        var out = new StringBuilder("super dragon balls: enabled=" + XenoSpaceConfig.cosmicDragonBallsEnabled() + " cycle=" + data.cycle
                + " dormantUntilDay=" + data.dormantUntilDay + " wishes=" + WishCatalog.wishes().size());
        for (int n = 1; n <= COUNT; n++) {
            var b = data.ball(n);
            out.append("\n ").append(n).append(": ").append(b.status.name().toLowerCase(Locale.ROOT)).append(' ').append(b.dimension)
                    .append(' ').append(b.x).append(' ').append(b.y).append(' ').append(b.z);
        }
        return out.toString();
    }

    private static void applyReward(ServerPlayer player, WishCatalog.Reward reward) {
        switch (reward.type()) {
            case "item" -> {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(reward.id()));
                ItemStack stack = new ItemStack(item, reward.amount());
                if (!player.getInventory().add(stack)) player.drop(stack, false);
            }
            case "effect" -> BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(reward.id())).ifPresent(effect ->
                    player.addEffect(new MobEffectInstance(effect, reward.amount() * 20, reward.amplifier())));
            case "xp" -> player.giveExperienceLevels(reward.amount());
            case "command" -> {
                var source = player.getServer().createCommandSourceStack().withPermission(2).withSuppressedOutput()
                        .withEntity(player).withPosition(player.position()).withLevel(player.serverLevel());
                player.getServer().getCommands().performPrefixedCommand(source, reward.command().replace("@wisher", player.getScoreboardName()));
            }
            default -> { }
        }
    }

    static boolean holds(ServerPlayer player, int number) {
        Item item = ModBlocks.COSMIC_DRAGON_BALL_ITEMS.get(number - 1).get();
        return player.getInventory().contains(new ItemStack(item));
    }

    private static void consumeOne(ServerPlayer player, int number) {
        Item item = ModBlocks.COSMIC_DRAGON_BALL_ITEMS.get(number - 1).get();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) { stack.shrink(1); return; }
        }
    }

    private static ServerPlayer holder(MinecraftServer server, int number) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (holds(p, number)) return p;
        return null;
    }

    private static ServerLevel level(MinecraftServer server, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    private static String bearing(double dx, double dz) {
        String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        double degrees = Math.toDegrees(Math.atan2(-dx, dz));
        int index = (int) Math.floorMod(Math.round(degrees / 45.0), 8);
        return names[index];
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { OFFERS.clear(); }
}

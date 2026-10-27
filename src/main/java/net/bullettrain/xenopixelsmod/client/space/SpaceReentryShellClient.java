package net.bullettrain.xenopixelsmod.client.space;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

/** Short client-only plasma shell around the arriving player, started at fade or destination readiness. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class SpaceReentryShellClient {
    private static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(0.92f, 0.96f, 1.0f), 1.15f);
    private static final DustParticleOptions BLUE = new DustParticleOptions(new Vector3f(0.2f, 0.55f, 1.0f), 1.0f);
    private static final int DURATION_TICKS = 18;
    private static final int PARTICLES_PER_TICK = 10;
    private static int ticksRemaining;
    private static int age;
    private static ClientLevel sourceLevel;
    private static java.util.UUID sourcePlayer;

    private SpaceReentryShellClient() {}

    public static void start() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            stop();
            return;
        }
        age = 0;
        ticksRemaining = DURATION_TICKS;
        sourceLevel = minecraft.level;
        sourcePlayer = minecraft.player.getUUID();
    }

    public static void stop() {
        ticksRemaining = 0;
        age = 0;
        sourceLevel = null;
        sourcePlayer = null;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (ticksRemaining <= 0) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null
                || minecraft.level != sourceLevel || !minecraft.player.getUUID().equals(sourcePlayer)) {
            stop();
            return;
        }

        var player = minecraft.player;
        Vec3 center = player.position().add(0.0, player.getBbHeight() * 0.52, 0.0);
        Vec3 motion = player.getDeltaMovement();
        for (int i = 0; i < PARTICLES_PER_TICK; i++) {
            int index = age * PARTICLES_PER_TICK + i;
            double u = (index + 0.5) / (DURATION_TICKS * (double) PARTICLES_PER_TICK);
            double y = 1.0 - 2.0 * u;
            double ring = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double angle = index * 2.399963229728653;
            double nx = Math.cos(angle) * ring;
            double nz = Math.sin(angle) * ring;
            double ny = y;
            double radius = 0.85 + 0.12 * Math.sin((age + i) * 0.7);
            ParticleOptions option = (index & 3) == 0 ? ParticleTypes.ELECTRIC_SPARK : ((index & 1) == 0 ? WHITE : BLUE);
            minecraft.level.addParticle(option,
                    center.x + nx * radius, center.y + ny * radius, center.z + nz * radius,
                    motion.x * 0.35 + nx * 0.035, motion.y * 0.35 + ny * 0.035,
                    motion.z * 0.35 + nz * 0.035);
        }
        age++;
        ticksRemaining--;
    }
}

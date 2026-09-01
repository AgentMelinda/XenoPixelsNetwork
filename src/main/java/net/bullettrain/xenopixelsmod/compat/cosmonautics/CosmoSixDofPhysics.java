package net.bullettrain.xenopixelsmod.compat.cosmonautics;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Dedicated-server copy of Cosmonautics {@code FreeMotionHandler.apply6DOFPhysics}.
 *
 * <p>Cosmo's common {@code LivingEntityMixin#rocketnautics$6DOFMovement} calls that
 * client class from {@code travel}. Loading it on a dedicated server DistCleans
 * {@code LocalPlayer} and kicks the player. Cosmo types are reflected so this
 * compiles without a rocketnautics compile dependency.
 */
public final class CosmoSixDofPhysics {

    private static final Class<?> FREE_MOTION;
    private static final Method IS_6DOF;
    private static final Method IS_AMBULANT;
    private static final Method SET_AMBULANT;
    private static final Method GET_DAMPENER;
    private static final Method GET_ACCEL;
    private static final Method GET_ORIENTATION;
    private static final Method IS_DEEP_SPACE;
    private static final Method GET_DIM_DATA;
    private static final Method ENTITY_DRAG;
    private static final Method EVAL_BEZIER;
    private static final Object EMPTY_BEZIER;

    static {
        Class<?> free = null;
        Method is6 = null, isAmb = null, setAmb = null, damp = null, accel = null, orient = null;
        Method deep = null, dimData = null, drag = null, eval = null;
        Object empty = null;
        try {
            free = Class.forName("dev.devce.rocketnautics.api.FreeMotionEntity");
            is6 = free.getMethod("is6DOFEnabled");
            isAmb = free.getMethod("isAmbulant");
            setAmb = free.getMethod("setAmbulant", boolean.class);
            damp = free.getMethod("getDampenerForce");
            accel = free.getMethod("getMovementAcceleration");
            orient = free.getMethod("getOrientation");
            Class<?> helper = Class.forName("dev.devce.rocketnautics.api.orbit.DeepSpaceHelper");
            deep = helper.getMethod("isDeepSpace", Level.class);
            dimData = helper.getMethod("getDataForDimension", Level.class);
            Class<?> pdata = Class.forName("dev.devce.rocketnautics.content.orbit.universe.PlanetDimensionData");
            drag = pdata.getMethod("entityDragMultiplier");
            Field emptyField = pdata.getField("EMPTY_BEZIER");
            empty = emptyField.get(null);
            if (empty != null) {
                eval = empty.getClass().getMethod("evaluateFunction", double.class);
            }
        } catch (Throwable ignored) {
        }
        FREE_MOTION = free;
        IS_6DOF = is6;
        IS_AMBULANT = isAmb;
        SET_AMBULANT = setAmb;
        GET_DAMPENER = damp;
        GET_ACCEL = accel;
        GET_ORIENTATION = orient;
        IS_DEEP_SPACE = deep;
        GET_DIM_DATA = dimData;
        ENTITY_DRAG = drag;
        EVAL_BEZIER = eval;
        EMPTY_BEZIER = empty;
    }

    private CosmoSixDofPhysics() {
    }

    public static boolean apply(Vector3f motion, LivingEntity entity) {
        if (FREE_MOTION == null || IS_6DOF == null) return false;
        if (!(entity instanceof Player player)) return false;
        if (player.onClimbable()) return false;
        if (!FREE_MOTION.isInstance(entity)) return false;
        try {
            if (!((Boolean) IS_6DOF.invoke(entity))) return false;

            Level level = player.level();
            if (!level.getFluidState(player.blockPosition()).isEmpty()) {
                SET_AMBULANT.invoke(entity, false);
                return false;
            }

            boolean ambulant = (Boolean) IS_AMBULANT.invoke(entity);
            float movementAccel = (Float) GET_ACCEL.invoke(entity);
            float maxDampener = ambulant
                    ? (Float) GET_DAMPENER.invoke(entity) * movementAccel
                    : 0.0f;
            Vector3f velocity = entity.getDeltaMovement().toVector3f();

            float pressure = pressureAt(level, entity.getY());
            float dragXZ = 1 - (1 - 0.91f) * pressure;
            float dragY = 1 - 0.02f * pressure;
            Vector3f env = new Vector3f(dragXZ, dragY, dragXZ);

            if (!player.shouldDiscardFriction() && entity.onGround()) {
                float friction = level.getBlockState(entity.getBlockPosBelowThatAffectsMyMovement())
                        .getFriction(level, entity.getBlockPosBelowThatAffectsMyMovement(), entity);
                env.sub(new Vector3f(friction));
            }

            Vector3f gravity = new Vector3f(0, -(float) player.getGravity(), 0);
            velocity.mul(env);
            if (!player.getAbilities().flying) {
                velocity.add(gravity);
                velocity.y += Math.clamp(maxDampener, 0, (float) player.getGravity());
            }

            Quaternionf orientation = new Quaternionf((Quaternionf) GET_ORIENTATION.invoke(entity));
            Vector3f acceleration = orientation.transform(motion.mul(-1));
            acceleration.mul(ambulant ? movementAccel : 0.0f);

            Vector3f dampener = new Vector3f(
                    -Math.clamp(Math.abs(velocity.x), 0, maxDampener) * Math.signum(velocity.x),
                    -Math.clamp(Math.abs(velocity.y), 0, maxDampener) * Math.signum(velocity.y),
                    -Math.clamp(Math.abs(velocity.z), 0, maxDampener) * Math.signum(velocity.z)
            ).mul(0.05f);
            velocity.add(dampener);
            velocity.add(acceleration);

            entity.setDeltaMovement(new Vec3(velocity));
            entity.move(MoverType.SELF, new Vec3(velocity));
            entity.fallDistance = 0;

            if (!level.isClientSide && (entity.horizontalCollision || entity.verticalCollision)) {
                float speed = velocity.length();
                if (speed > 0.3f) {
                    entity.playSound((speed * 10) > 4
                            ? entity.getFallSounds().big()
                            : entity.getFallSounds().small(), 1.0f, 1.0f);
                    entity.hurt(entity.damageSources().flyIntoWall(), speed * 10.0f);
                }
            }

            entity.setSwimming(true);
            entity.calculateEntityAnimation(false);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static float pressureAt(Level level, double y) throws ReflectiveOperationException {
        if (IS_DEEP_SPACE != null && Boolean.TRUE.equals(IS_DEEP_SPACE.invoke(null, level))) {
            return 0;
        }
        if (GET_DIM_DATA == null || ENTITY_DRAG == null || EVAL_BEZIER == null) {
            return 1;
        }
        Object opt = GET_DIM_DATA.invoke(null, level);
        Object bezier = EMPTY_BEZIER;
        if (opt instanceof Optional<?> optional && optional.isPresent()) {
            Object data = optional.get();
            Object mapped = ENTITY_DRAG.invoke(data);
            if (mapped != null) bezier = mapped;
        }
        if (bezier == null) return 1;
        Object value = EVAL_BEZIER.invoke(bezier, y);
        return value instanceof Number n ? n.floatValue() : 1;
    }
}


package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.item.LaserSaberItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class SaberPose {
    public record Local(Vec3d hilt, Quaternionf rotation) {
        public Vec3d axis() {
            Vector3f direction = rotation.transform(new Vector3f(0, 1, 0));
            return new Vec3d(direction.x, direction.y, direction.z);
        }
    }

    public record Frame(Vec3d hilt, Vec3d axis, Vec3d eye, Vec3d right, Vec3d up, Vec3d forward) {
        public LaserBeamTrace blade(PlayerEntity player, LaserSaberItem item, int end, double extension) {
            Vec3d direction = axis.multiply(end);
            Vec3d start = hilt.add(direction.multiply(item.hiltEnd()));
            var obstruction = LaserBeamTrace.traceFrom(player.getWorld(), eye, start.subtract(eye).normalize(), eye.distanceTo(start));
            // A hand penetrating a wall still burns its near face, never the space behind it.
            if (obstruction.hasBlockHit()) return obstruction;
            return LaserBeamTrace.traceFrom(player.getWorld(), start, direction, item.bladeLength() * extension);
        }

        public Quaternionf rotation(Local local) {
            return new Quaternionf().setFromNormalized(new Matrix3f(
                    (float) right.x, (float) right.y, (float) right.z,
                    (float) up.x, (float) up.y, (float) up.z,
                    (float) -forward.x, (float) -forward.y, (float) -forward.z)).mul(local.rotation());
        }
    }

    public static Vec3d right(double yaw) {
        double angle = Math.toRadians(yaw);
        // Yaw defines roll even at +/-90 degrees pitch, where cross(view, worldUp) degenerates.
        return new Vec3d(-Math.cos(angle), 0, -Math.sin(angle));
    }

    public static Local combat(SaberState state, int hand, double time, boolean ignited) {
        Local rest = local(state.staff() ? new double[]{.29, -.48, -.48, -22, 0, -40}
                : new double[]{.32, -.46, -.47, -24, -8, 10}, hand);
        if (!ignited) return local(new double[]{.32, -.65, -.21, 166, 0, -8}, hand);
        Local guard = local(new double[]{.1, -.40, -.51, -55, 10, state.staff() ? 74 : 65}, hand);
        double elapsed = state.elapsed(time);
        if (state.guarding()) {
            Local base = blend(rest, guard, smooth(elapsed / 2));
            if (state.action() == SaberState.Action.PARRY)
                return blend(guard, local(new double[]{.03, -.33, -.47, -38, -10, 78}, hand), 1 - smooth(elapsed / 4));
            return base;
        }
        if (state.action() == SaberState.Action.RECOIL || state.action() == SaberState.Action.BROKEN) {
            Local deflected = local(new double[]{.40, -.40, -.29, 20, 20, -35}, hand);
            return blend(deflected, rest, smooth(elapsed / Math.max(1, state.recovery())));
        }
        if (state.action() != SaberState.Action.ATTACK || elapsed >= state.duration()) return rest;
        double[][] angles = switch (state.cut()) {
            case OVERHEAD -> new double[][]{{-8, -12, 8}, {-162, 10, -16}};
            case RISING -> new double[][]{{-162, 12, -16}, {-12, -14, 14}};
            case LEFT -> new double[][]{{-85, 75, 0}, {-95, -75, 0}};
            case RIGHT -> new double[][]{{-85, -75, 0}, {-95, 75, 0}};
            case DIAGONAL_LEFT -> new double[][]{{-32, 68, 8}, {-145, -55, -20}};
            case DIAGONAL_RIGHT -> new double[][]{{-32, -68, -8}, {-145, 55, 20}};
            case RETURNING -> new double[][]{{85, 80, 0}, {100, -80, 0}};
        };
        Local start = local(new double[]{.28, -.36, -.41, angles[0][0], angles[0][1], angles[0][2]}, hand);
        Local finish = local(new double[]{-.10, -.50, -.51, angles[1][0], angles[1][1], angles[1][2]}, hand);
        if (elapsed < state.windup()) return blend(rest, start, smooth(elapsed / state.windup()));
        if (elapsed < state.windup() + state.active())
            return blend(start, finish, smooth((elapsed - state.windup()) / state.active()));
        return blend(finish, rest, smooth((elapsed - state.windup() - state.active()) / state.recovery()));
    }

    private static double smooth(double t) { t = MathHelper.clamp(t, 0, 1); return t * t * (3 - 2 * t); }

    private static Local blend(Local from, Local to, double progress) {
        return new Local(from.hilt().lerp(to.hilt(), progress),
                new Quaternionf(from.rotation()).slerp(to.rotation(), (float) progress));
    }

    private static Local local(double[] pose, int hand) {
        Quaternionf rotation = new Quaternionf().rotationYXZ((float) Math.toRadians(pose[4] * hand),
                (float) Math.toRadians(pose[3]), (float) Math.toRadians(pose[5] * hand));
        return new Local(new Vec3d(pose[0] * hand, pose[1], pose[2]), rotation);
    }

    public static Frame frame(PlayerEntity player, float delta, boolean firstPerson, Local local) {
        return frame(player, Hand.MAIN_HAND, delta, firstPerson, local);
    }

    public static Frame frame(PlayerEntity player, Hand hand, float delta, boolean firstPerson, Local local) {
        Vec3d forward = player.getRotationVec(delta).normalize();
        Vec3d right = right(MathHelper.lerp(delta, player.prevYaw, player.getYaw()));
        Vec3d up = right.crossProduct(forward).normalize();
        right = forward.crossProduct(up).normalize();
        Vec3d eye = player.getLerpedPos(delta).add(0, player.getEyeY() - player.getY(), 0);
        Vec3d p = local.hilt(), a = local.axis();
        Vec3d hilt = eye.add(right.multiply(p.x)).add(up.multiply(p.y)).add(forward.multiply(-p.z));
        // Keep the grip within the same physical arm length used by the third-person model.
        Arm arm = WeaponHands.arm(player, hand);
        Vec3d shoulder = shoulder(player, delta, arm);
        Vec3d reach = hilt.subtract(shoulder);
        hilt = shoulder.add(reach.normalize().multiply(.69));
        Vec3d axis = right.multiply(a.x).add(up.multiply(a.y)).add(forward.multiply(-a.z));
        if (player.getStackInHand(hand).getItem() instanceof LaserSaberItem saber && saber.isStaff()
                && player.getStackInHand(hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND).isEmpty())
            hilt = twoHandedGrip(hilt, shoulder, shoulder(player, delta, arm.getOpposite()), axis.normalize());
        return new Frame(hilt, axis.normalize(), eye, right, up, forward);
    }

    static Vec3d twoHandedGrip(Vec3d desired, Vec3d mainShoulder, Vec3d supportShoulder, Vec3d axis) {
        // The two grips lie on equal-radius arm spheres, with the support grip 0.24 blocks down the hilt.
        Vec3d otherCenter = supportShoulder.add(axis.multiply(.24));
        Vec3d centers = otherCenter.subtract(mainShoulder);
        Vec3d normal = centers.normalize(), middle = mainShoulder.add(otherCenter).multiply(.5);
        Vec3d offset = desired.subtract(middle);
        Vec3d radial = offset.subtract(normal.multiply(offset.dotProduct(normal)));
        if (radial.lengthSquared() < .000001) radial = normal.crossProduct(new Vec3d(0, 1, 0));
        double radius = Math.sqrt(Math.max(0, .69 * .69 - centers.lengthSquared() * .25));
        return middle.add(radial.normalize().multiply(radius));
    }

    public static Vec3d shoulder(PlayerEntity player, float delta, Arm arm) {
        return player.getLerpedPos(delta).add(0, player.isSneaking() ? 1.15 : 1.40, 0)
                .add(right(MathHelper.lerpAngleDegrees(delta, player.prevBodyYaw, player.bodyYaw)).multiply(arm == Arm.RIGHT ? .3125 : -.3125));
    }

    private SaberPose() { }
}

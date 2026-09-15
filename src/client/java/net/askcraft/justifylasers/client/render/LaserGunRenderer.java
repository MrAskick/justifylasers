package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.item.LaserGunItem;
import net.askcraft.justifylasers.laser.LaserBeamTrace;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.laser.LaserWeapon;
import net.askcraft.justifylasers.platform.GameVersion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class LaserGunRenderer {
    private static AbstractClientPlayerEntity animatedPlayer;
    private static float swayYaw, swayPitch, previousSwayYaw, previousSwayPitch;
    private static float lastYaw, lastPitch, sprint, previousSprint;
    private static final float[] fire = new float[2], previousFire = new float[2];
    private static float yawVelocity, pitchVelocity, aim, previousAim, strafe, previousStrafe, lift, previousLift, landing;
    private static double lastVerticalSpeed;

    public static void tick(MinecraftClient client) {
        if (client.player != animatedPlayer) {
            animatedPlayer = client.player;
            swayYaw = swayPitch = previousSwayYaw = previousSwayPitch = sprint = previousSprint = 0;
            java.util.Arrays.fill(fire, 0);
            java.util.Arrays.fill(previousFire, 0);
            yawVelocity = pitchVelocity = aim = previousAim = strafe = previousStrafe = lift = previousLift = landing = 0;
            lastVerticalSpeed = 0;
            if (animatedPlayer != null) { lastYaw = animatedPlayer.getYaw(); lastPitch = animatedPlayer.getPitch(); }
        }
        if (animatedPlayer == null || client.isPaused()) return;
        previousSwayYaw = swayYaw; previousSwayPitch = swayPitch; previousSprint = sprint;
        previousAim = aim; previousStrafe = strafe; previousLift = lift;
        boolean firing = LaserGunItem.isFiring(animatedPlayer, Hand.MAIN_HAND) || LaserGunItem.isFiring(animatedPlayer, Hand.OFF_HAND);
        yawVelocity += (MathHelper.clamp(MathHelper.wrapDegrees(animatedPlayer.getYaw() - lastYaw), -18, 18) - swayYaw) * 0.24F - yawVelocity * 0.52F;
        pitchVelocity += (MathHelper.clamp(animatedPlayer.getPitch() - lastPitch, -14, 14) - swayPitch) * 0.24F - pitchVelocity * 0.52F;
        swayYaw += yawVelocity; swayPitch += pitchVelocity;
        aim = MathHelper.lerp(0.32F, aim, net.askcraft.justifylasers.client.LaserGunControls.aiming(client) ? 1 : 0);
        Vec3d right = animatedPlayer.getRotationVec(1).crossProduct(new Vec3d(0, 1, 0)).normalize();
        strafe = MathHelper.lerp(0.25F, strafe, (float) animatedPlayer.getVelocity().dotProduct(right));
        if (animatedPlayer.isOnGround() && lastVerticalSpeed < -0.14) landing = (float) Math.min(0.085, -lastVerticalSpeed * 0.11);
        landing *= 0.64F;
        lift = MathHelper.lerp(0.28F, lift, MathHelper.clamp((float) animatedPlayer.getVelocity().y * -0.07F - landing, -0.10F, 0.055F));
        lastVerticalSpeed = animatedPlayer.getVelocity().y;
        sprint = MathHelper.lerp(0.25F, sprint, animatedPlayer.isSprinting() && !firing ? 1 : 0);
        for (Hand hand : Hand.values()) {
            int index = hand.ordinal();
            previousFire[index] = fire[index];
            boolean held = LaserGunItem.isFiring(animatedPlayer, hand);
            fire[index] = MathHelper.lerp(held ? 0.55F : 0.30F, fire[index], held ? 1 : 0);
        }
        lastYaw = animatedPlayer.getYaw(); lastPitch = animatedPlayer.getPitch();
    }

    private static void firstPersonPose(AbstractClientPlayerEntity player, Hand hand, float delta, float equip,
                                        float swing, MatrixStack matrices) {
        int sign = (hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite()) == Arm.RIGHT ? 1 : -1;
        float time = (player.age + delta) / 20F;
        float recoil = MathHelper.lerp(delta, previousFire[hand.ordinal()], fire[hand.ordinal()]) * (0.5F + 0.5F * (float) Math.sin(time * 42));
        float lowered = MathHelper.lerp(delta, previousSprint, sprint) * (1 - aim(delta));
        double walking = Math.min(1, player.getVelocity().horizontalLength() * 6);
        float ads = aim(delta), motion = 1 - ads;
        if (!net.askcraft.justifylasers.client.ClientSettings.get().weaponSway) motion = 0;
        float sideways = MathHelper.lerp(delta, previousStrafe, strafe);
        matrices.translate(sign * 0.38 * (1 - ads) - sideways * 0.065 * motion + Math.sin(time * 1.3) * 0.0015 * motion
                        + Math.sin(time * 7) * walking * 0.010 * motion,
                MathHelper.lerp(ads, -0.34, -15.7 * 0.025) - equip * 0.65 - lowered * 0.14
                        + (Math.cos(time * 14) * walking * 0.007 + MathHelper.lerp(delta, previousLift, lift)) * motion,
                MathHelper.lerp(ads, -0.84, -0.52) + recoil * 0.014);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-MathHelper.lerp(delta, previousSwayYaw, swayYaw) * 0.65F * motion));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-MathHelper.lerp(delta, previousSwayPitch, swayPitch) * 0.56F * motion
                - lowered * 14 - recoil * 0.45F - MathHelper.sin(swing * (float) Math.PI) * 5));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sign * (lowered * 18 + MathHelper.lerp(delta, previousSwayYaw, swayYaw) * 0.16F * motion)
                + sideways * -8 * motion));
    }

    public static float aim(float delta) {
        return net.askcraft.justifylasers.laser.WeaponHands.dual(MinecraftClient.getInstance().player)
                ? 0 : MathHelper.lerp(delta, previousAim, aim);
    }

    static void saberMotion(AbstractClientPlayerEntity player, Hand hand, float delta, float equip, MatrixStack matrices) {
        matrices.translate(0, -equip * .65, 0);
        if (!net.askcraft.justifylasers.client.ClientSettings.get().weaponSway) return;
        int sign = net.askcraft.justifylasers.laser.WeaponHands.arm(player, hand) == Arm.RIGHT ? 1 : -1;
        float time = (player.age + delta) / 20F;
        double walk = Math.min(1, player.getVelocity().horizontalLength() * 6);
        float lowered = MathHelper.lerp(delta, previousSprint, sprint);
        float sideways = MathHelper.lerp(delta, previousStrafe, strafe);
        float yaw = MathHelper.lerp(delta, previousSwayYaw, swayYaw);
        matrices.translate(Math.sin(time * 7) * walk * .010 - sideways * .065,
                Math.cos(time * 14) * walk * .007 + MathHelper.lerp(delta, previousLift, lift) - lowered * .10, 0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw * .65F));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-MathHelper.lerp(delta, previousSwayPitch, swayPitch) * .56F - lowered * 10));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sign * (lowered * 12 + yaw * .16F) - sideways * 8));
    }

    public static void renderFirstPerson(AbstractClientPlayerEntity player, ItemStack stack, Hand hand, float delta,
                                         float equip, float swing, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        matrices.push();
        try {
            firstPersonPose(player, hand, delta, equip, swing, matrices);
            Arm trigger = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
            int sign = trigger == Arm.RIGHT ? 1 : -1;
            if (!player.isInvisible()) {
                drawArm(player, trigger, new Vec3d(sign * 0.06, -0.36, 0.74), new Vec3d(0, -0.08, 0.21), matrices, consumers, light);
                ItemStack other = player.getStackInHand(hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND);
                if (other.isEmpty()) drawArm(player, trigger.getOpposite(), new Vec3d(sign * -0.52, -0.31, 0.31),
                        new Vec3d(sign * -0.04, -0.02, -0.28), matrices, consumers, light);
            }
            matrices.scale(0.025F, 0.025F, 0.025F);
            LaserGunModel.renderFirstPerson(matrices, consumers, light, LaserColor.byIndex(GameVersion.cubeColor(stack)).rgb());
            LaserScopeRenderer.queue(matrices);
        } finally { matrices.pop(); }
    }

    static void drawArm(AbstractClientPlayerEntity player, Arm arm, Vec3d shoulder, Vec3d grip,
                                 MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        if (!(MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(player) instanceof PlayerEntityRenderer renderer)) return;
        Vec3d delta = grip.subtract(shoulder);
        matrices.push();
        try {
            matrices.translate(shoulder.x, shoulder.y, shoulder.z);
            Vec3d direction = delta.normalize();
            matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f((float) direction.x, (float) direction.y, (float) direction.z)));
            matrices.scale(0.8F, (float) delta.length() / 0.75F, 0.8F);
            matrices.translate(arm == Arm.RIGHT ? 0.375 : -0.375, -0.125, 0);
            if (arm == Arm.RIGHT) renderer.renderRightArm(matrices, consumers, light, player);
            else renderer.renderLeftArm(matrices, consumers, light, player);
        } finally { matrices.pop(); }
    }

    public static void renderItem(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        matrices.push();
        try {
            if (mode == ModelTransformationMode.GUI) LaserGunModel.fitGui(matrices);
            else {
                matrices.translate(0.5, 0.45, 0.5);
                matrices.scale(1 / 32F, 1 / 32F, 1 / 32F);
            }
            LaserGunModel.render(matrices, consumers, light, LaserColor.byIndex(GameVersion.cubeColor(stack)).rgb());
        } finally { matrices.pop(); }
    }

    public static void renderBeams(LaserRenderFrame frame) {
        for (var player : frame.world().getPlayers()) for (Hand weaponHand : Hand.values()) {
            if (!LaserGunItem.isFiring(player, weaponHand)) continue;
            ItemStack gun = player.getStackInHand(weaponHand);
            net.askcraft.justifylasers.client.LaserSoundController.weapon(player.getUuid(), player.getEyePos());
            Vec3d eye = player.getLerpedPos(frame.tickDelta()).add(player.getEyePos().subtract(player.getPos()));
            Vec3d axis = player.getRotationVec(frame.tickDelta());
            var hit = LaserWeapon.trace(frame.world(), eye, axis, LaserGunItem.range(gun), player);
            Vec3d right = axis.crossProduct(new Vec3d(0, 1, 0)).normalize();
            int hand = (weaponHand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite()) == Arm.RIGHT ? 1 : -1;
            Vec3d muzzle = eye.add(axis.multiply(0.8)).add(right.multiply(hand * 0.24)).add(0, -0.22, 0);
            var client = MinecraftClient.getInstance();
            if (player == client.player && client.options.getPerspective().isFirstPerson()) {
                MatrixStack pose = new MatrixStack();
                firstPersonPose(client.player, weaponHand, frame.tickDelta(), 0, 0, pose);
                Vector3f offset = pose.peek().getPositionMatrix().transformPosition(new Vector3f(0, 4.5F * 0.025F, -26.1F * 0.025F));
                Vec3d up = right.crossProduct(axis).normalize();
                muzzle = eye.add(right.multiply(offset.x)).add(up.multiply(offset.y)).add(axis.multiply(-offset.z));
            }
            // The server ray starts at the eyes: the hand offset must never extend a shot through a nearby wall.
            if (hit.beam().length() < 1.6) muzzle = eye.add(axis.multiply(Math.min(0.15, hit.beam().length() * 0.5)));
            LaserBeamTrace beam = new LaserBeamTrace(muzzle, hit.beam().end(), hit.beam().direction(), hit.beam().hitBlock(), hit.beam().hitSide());
            MatrixStack matrices = frame.matrixStack();
            matrices.push();
            try {
                Vec3d offset = muzzle.subtract(frame.camera().getPos());
                matrices.translate(offset.x, offset.y, offset.z);
                LaserBeamRenderer.render(beam, muzzle, player.getUuid(), weaponHand.ordinal(), player.age + frame.tickDelta(),
                        LaserColor.byIndex(GameVersion.cubeColor(gun)).rgb(), 0.8, true, matrices, frame.consumers());
            } finally { matrices.pop(); }
        }
    }

    private LaserGunRenderer() { }
}

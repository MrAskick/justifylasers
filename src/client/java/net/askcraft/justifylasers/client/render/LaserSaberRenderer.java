package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.client.ClientSettings;
import net.askcraft.justifylasers.client.LaserSoundController;
import net.askcraft.justifylasers.item.LaserSaberItem;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.laser.LaserScorchMarks;
import net.askcraft.justifylasers.laser.SaberPose;
import net.askcraft.justifylasers.laser.SaberCombat;
import net.askcraft.justifylasers.laser.SaberState;
import net.askcraft.justifylasers.laser.SaberCut;
import net.askcraft.justifylasers.laser.LaserBeamTrace;
import net.askcraft.justifylasers.platform.GameVersion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.askcraft.justifylasers.laser.WeaponHands;
import java.util.EnumMap;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class LaserSaberRenderer {
    private static final Map<PlayerEntity, EnumMap<Hand, Animation>> ANIMATIONS = new WeakHashMap<>();
    private static Object world;

    private static final class Animation {
        float previous, extension;
        long lastAttack = Long.MIN_VALUE;
    }

    private static Animation animation(PlayerEntity player, Hand hand) {
        return ANIMATIONS.computeIfAbsent(player, ignored -> new EnumMap<>(Hand.class)).computeIfAbsent(hand, ignored -> new Animation());
    }

    public static void tick(MinecraftClient client) {
        if (client.world != world) { ANIMATIONS.clear(); SaberCombat.clearClientState(); world = client.world; }
        if (client.world == null || client.isPaused()) return;
        ANIMATIONS.keySet().removeIf(player -> player.isRemoved() || !client.world.getPlayers().contains(player));
        for (var player : client.world.getPlayers()) for (Hand hand : Hand.values()) {
            ItemStack stack = player.getStackInHand(hand);
            if (!(stack.getItem() instanceof LaserSaberItem)) {
                var hands = ANIMATIONS.get(player);
                if (hands != null) hands.remove(hand);
                continue;
            }
            Animation animation = animation(player, hand);
            animation.previous = animation.extension;
            animation.extension = MathHelper.clamp(animation.extension + (LaserSaberItem.active(stack) ? 0.34F : -0.34F), 0, 1);
        }
    }

    public static List<LaserScorchMarks.WeaponContact> contacts(MinecraftClient client) {
        if (client.world == null) return List.of();
        List<LaserScorchMarks.WeaponContact> contacts = new ArrayList<>();
        for (var player : client.world.getPlayers()) for (Hand hand : Hand.values()) {
            if (!player.isAlive() || !LaserSaberItem.active(player.getStackInHand(hand))) continue;
            LaserSaberItem saber = (LaserSaberItem) player.getStackInHand(hand).getItem();
            SaberPose.Frame pose = pose(player, hand, client, 1);
            for (int end : saber.isStaff() ? new int[]{1, -1} : new int[]{1}) {
                var ray = pose.blade(player, saber, end, extension(player, hand, 1));
                contacts.add(new LaserScorchMarks.WeaponContact(player.getUuid(), end + 4 + hand.ordinal() * 10, ray, 0.026));
            }
        }
        return contacts;
    }

    private static SaberPose.Frame pose(PlayerEntity player, Hand hand, MinecraftClient client, float delta) {
        return SaberPose.frame(player, hand, delta, player == client.player && client.options.getPerspective().isFirstPerson(), localPose(player, hand, delta));
    }

    private static float extension(PlayerEntity player, Hand hand, float delta) {
        var hands = ANIMATIONS.get(player);
        Animation animation = hands == null ? null : hands.get(hand);
        return animation == null ? (LaserSaberItem.active(player.getStackInHand(hand)) ? 1 : 0)
                : MathHelper.lerp(delta, animation.previous, animation.extension);
    }

    public static void onSwing(PlayerEntity player) { onSwing(player, Hand.MAIN_HAND); }

    public static void onSwing(PlayerEntity player, Hand hand) {
        if (!(player.getStackInHand(hand).getItem() instanceof LaserSaberItem saber)) return;
        var s = SaberCombat.state(player, hand);
        long now = player.getWorld().getTime();
        if (s.action() == SaberState.Action.ATTACK && s.elapsed(now) < s.duration()) return;
        var timings = SaberState.idle(saber.isStaff(), now);
        var animation = animation(player, hand);
        int stage = animation.lastAttack != Long.MIN_VALUE && s.staff() == saber.isStaff() && now - animation.lastAttack <= s.duration() + 8
                ? (s.stage() + 1) % (saber.isStaff() ? 3 : 2) : 0;
        animation.lastAttack = now;
        var velocity = player.getVelocity();
        var cut = SaberCut.select(velocity.dotProduct(Vec3d.fromPolar(0, player.getYaw())),
                velocity.dotProduct(SaberPose.right(player.getYaw())), stage, saber.isStaff());
        SaberCombat.acceptState(player, hand, new SaberState(SaberState.Action.ATTACK, cut, stage, now,
                timings.windup(), timings.active(), timings.recovery(), s.stamina(), s.capacity(), s.sequence() + 1, saber.isStaff()));
        LaserSoundController.saberSwing(player.getEyePos(), saber.isStaff());
    }

    public static void synchronizeAttack(PlayerEntity player, long started) { synchronizeAttack(player, Hand.MAIN_HAND, started); }

    public static void synchronizeAttack(PlayerEntity player, Hand hand, long started) {
        animation(player, hand).lastAttack = started;
    }

    public static SaberPose.Local localPose(PlayerEntity player, float delta) {
        return localPose(player, Hand.MAIN_HAND, delta);
    }

    public static SaberPose.Local localPose(PlayerEntity player, Hand hand, float delta) {
        return localPose(player, hand, player.getStackInHand(hand), delta);
    }

    public static SaberPose.Local localPose(PlayerEntity player, ItemStack renderedStack, float delta) {
        return localPose(player, renderedStack == player.getOffHandStack() ? Hand.OFF_HAND : Hand.MAIN_HAND, renderedStack, delta);
    }

    private static SaberPose.Local localPose(PlayerEntity player, Hand hand, ItemStack renderedStack, float delta) {
        boolean staff = renderedStack.getItem() instanceof LaserSaberItem saber && saber.isStaff();
        var state = renderedStack == player.getStackInHand(hand) ? SaberCombat.state(player, hand) : SaberState.idle(staff, player.getWorld().getTime());
        if (state.staff() != staff) state = SaberState.idle(staff, player.getWorld().getTime());
        var local = SaberPose.combat(state, WeaponHands.arm(player, hand) == Arm.RIGHT ? 1 : -1,
                player.getWorld().getTime() + delta, LaserSaberItem.active(renderedStack));
        var frame = SaberPose.frame(player, hand, delta, false, local);
        Vec3d offset = frame.hilt().subtract(frame.eye());
        return new SaberPose.Local(new Vec3d(offset.dotProduct(frame.right()), offset.dotProduct(frame.up()), -offset.dotProduct(frame.forward())), local.rotation());
    }

    public static void renderFirstPerson(AbstractClientPlayerEntity player, ItemStack stack, float delta, float equip,
                                         MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        renderFirstPerson(player, stack, Hand.MAIN_HAND, delta, equip, matrices, consumers, light);
    }

    public static void renderFirstPerson(AbstractClientPlayerEntity player, ItemStack stack, Hand hand, float delta, float equip,
                                         MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        var client = MinecraftClient.getInstance();
        if (client.options.hudHidden || net.askcraft.justifylasers.client.compat.IrisCompatibility.isRenderingShadowPass()) return;
        if (!(stack.getItem() instanceof LaserSaberItem saber)) return;
        // HeldItemRenderer keeps the previous stack during the equip transition.
        SaberPose.Local local = localPose(player, hand, stack, delta);
        int sign = WeaponHands.arm(player, hand) == Arm.RIGHT ? 1 : -1;
        int rgb = LaserColor.byIndex(GameVersion.cubeColor(stack)).rgb();
        float extension = extension(player, hand, delta);
        matrices.push();
        try {
            LaserGunRenderer.saberMotion(player, hand, delta, equip, matrices);
            if (!player.isInvisible()) {
                LaserGunRenderer.drawArm(player, WeaponHands.arm(player, hand), new Vec3d(sign * .42, -.58, .13),
                        local.hilt().add(local.axis().multiply(-.09)), matrices, consumers, light);
                if (saber.isStaff() && player.getStackInHand(hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND).isEmpty())
                    LaserGunRenderer.drawArm(player, WeaponHands.arm(player, hand).getOpposite(), new Vec3d(-sign * .37, -.55, .02),
                            local.hilt().add(local.axis().multiply(-.27)), matrices, consumers, light);
            }
            matrices.translate(local.hilt().x, local.hilt().y, local.hilt().z);
            matrices.multiply(local.rotation());
            LaserSaberModel.renderHilt(saber.isStaff(), matrices, consumers, light, rgb, extension > .01F);
            if (extension <= .001F) return;
            for (int end : saber.isStaff() ? new int[]{1, -1} : new int[]{1}) {
                var blade = new LaserBeamTrace(new Vec3d(0, end * saber.hiltEnd(), 0),
                        new Vec3d(0, end * (saber.hiltEnd() + saber.bladeLength() * extension), 0),
                        net.minecraft.util.math.Direction.UP, null);
                SaberBladeRenderer.render(blade, Vec3d.ZERO, null, end, rgb, matrices, consumers);
                SaberHandRenderer.queue(blade, rgb, matrices);
            }
        } finally { matrices.pop(); }
    }

    public static void renderWorld(LaserRenderFrame frame) {
        var client = MinecraftClient.getInstance();
        for (var player : frame.world().getPlayers()) for (Hand hand : Hand.values()) {
            if (!player.isAlive() || player.isSpectator() || !(player.getStackInHand(hand).getItem() instanceof LaserSaberItem saber)
                    || player.squaredDistanceTo(frame.camera().getPos()) > 96 * 96
                    || player == client.player && client.options.getPerspective().isFirstPerson()) continue;
            SaberPose.Local local = localPose(player, hand, frame.tickDelta());
            SaberPose.Frame pose = SaberPose.frame(player, hand, frame.tickDelta(), false, local);
            Vec3d hilt = pose.hilt(), camera = frame.camera().getPos();
            int light = WorldRenderer.getLightmapCoordinates(frame.world(), player.getBlockPos());
            int rgb = LaserColor.byIndex(GameVersion.cubeColor(player.getStackInHand(hand))).rgb();
            float extension = extension(player, hand, frame.tickDelta());
            MatrixStack matrices = frame.matrixStack();
            matrices.push();
            try {
                Vec3d offset = hilt.subtract(camera);
                matrices.translate(offset.x, offset.y, offset.z);
                matrices.push();
                matrices.multiply(pose.rotation(local));
                LaserSaberModel.renderHilt(saber.isStaff(), matrices, frame.consumers(), light, rgb, extension > .01F);
                matrices.pop();
                if (extension <= .001F) continue;
                for (int end : saber.isStaff() ? new int[]{1, -1} : new int[]{1}) {
                    var blade = pose.blade(player, saber, end, extension);
                    SaberBladeRenderer.render(blade, hilt, player.getUuid(), 40 + hand.ordinal() * 10 + end, rgb, matrices, frame.consumers());
                }
            } finally { matrices.pop(); }
        }
    }

    public static void renderItem(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        var saber = (LaserSaberItem) stack.getItem();
        matrices.push();
        matrices.translate(0.5, 0.5, 0.5);
        if (mode == ModelTransformationMode.GUI) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-30));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-20));
            float size = saber.isStaff() ? 1.02F : 1.7F;
            matrices.scale(size, size, size);
        }
        LaserSaberModel.renderHilt(saber.isStaff(), matrices, consumers, light,
                LaserColor.byIndex(GameVersion.cubeColor(stack)).rgb(), LaserSaberItem.active(stack));
        matrices.pop();
    }

    private LaserSaberRenderer() { }
}

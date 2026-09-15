package net.askcraft.justifylasers.mixin.client;

import net.askcraft.justifylasers.item.LaserGunItem;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BipedEntityModel.class)
public abstract class LaserGunPoseMixin {
    @Shadow public ModelPart head;
    @Shadow public ModelPart rightArm;
    @Shadow public ModelPart leftArm;

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void justifylasers$twoHandedAim(LivingEntity entity, float limbAngle, float limbDistance, float animation,
                                          float headYaw, float headPitch, CallbackInfo ci) {
        if (entity.isSwimming() || entity.isFallFlying()) return;
        for (var hand : net.minecraft.util.Hand.values()) {
            var stack = entity.getStackInHand(hand);
            var other = entity.getStackInHand(hand == net.minecraft.util.Hand.MAIN_HAND ? net.minecraft.util.Hand.OFF_HAND : net.minecraft.util.Hand.MAIN_HAND);
            Arm side = hand == net.minecraft.util.Hand.MAIN_HAND ? entity.getMainArm() : entity.getMainArm().getOpposite();
            boolean right = side == Arm.RIGHT;
            ModelPart trigger = right ? rightArm : leftArm, support = right ? leftArm : rightArm;
            if (entity instanceof net.minecraft.entity.player.PlayerEntity player && stack.getItem() instanceof net.askcraft.justifylasers.item.LaserSaberItem saber) {
                float delta = net.minecraft.util.math.MathHelper.clamp(animation - entity.age, 0, 1);
                var local = net.askcraft.justifylasers.client.render.LaserSaberRenderer.localPose(player, hand, delta);
                var pose = net.askcraft.justifylasers.laser.SaberPose.frame(player, hand, delta, false, local);
                justifylasers$aimSaberArm(player, trigger, side, pose.hilt(), delta);
                if (saber.isStaff() && other.isEmpty())
                    justifylasers$aimSaberArm(player, support, side.getOpposite(), pose.hilt().add(pose.axis().multiply(-.24)), delta);
            } else if (stack.getItem() instanceof LaserGunItem) {
                boolean firing = entity instanceof net.minecraft.entity.player.PlayerEntity player && LaserGunItem.isFiring(player, hand);
                float recoil = firing ? (float) Math.sin(animation * 2.1) * .018F : 0;
                trigger.pitch = head.pitch - 1.43F + recoil;
                trigger.yaw = head.yaw + (right ? -.12F : .12F);
                trigger.roll = right ? .025F : -.025F;
                if (!other.isEmpty()) continue;
                support.pitch = head.pitch - 1.52F + recoil;
                support.yaw = head.yaw + (right ? .46F : -.46F);
                support.roll = right ? -.10F : .10F;
            }
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private static void justifylasers$aimSaberArm(net.minecraft.entity.player.PlayerEntity player, ModelPart arm, Arm side,
                                                 net.minecraft.util.math.Vec3d grip, float delta) {
        var direction = grip.subtract(net.askcraft.justifylasers.laser.SaberPose.shoulder(player, delta, side)).normalize();
        float yaw = net.minecraft.util.math.MathHelper.lerpAngleDegrees(delta, player.prevBodyYaw, player.bodyYaw);
        double right = direction.dotProduct(net.askcraft.justifylasers.laser.SaberPose.right(yaw));
        double forward = direction.dotProduct(net.minecraft.util.math.Vec3d.fromPolar(0, yaw));
        arm.pitch = -(float) Math.acos(net.minecraft.util.math.MathHelper.clamp(-direction.y, -1, 1));
        arm.yaw = (float) Math.atan2(right, forward);
        arm.roll = 0;
    }
}

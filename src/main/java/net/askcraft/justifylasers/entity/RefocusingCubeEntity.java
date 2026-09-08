package net.askcraft.justifylasers.entity;

import net.askcraft.justifylasers.laser.CubeOptics;
import net.askcraft.justifylasers.laser.LaserBeamNetwork;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.registry.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class RefocusingCubeEntity extends Entity {
    private static final TrackedData<Integer> COLOR = DataTracker.registerData(RefocusingCubeEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> LIT = DataTracker.registerData(RefocusingCubeEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> EMISSION = DataTracker.registerData(RefocusingCubeEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> HOLDER = DataTracker.registerData(RefocusingCubeEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private int interpolationSteps;
    private Vec3d interpolationTarget = Vec3d.ZERO;
    private float targetYaw;
    private float targetPitch;

    public RefocusingCubeEntity(EntityType<? extends RefocusingCubeEntity> type, World world) {
        super(type, world);
        intersectionChecked = true;
    }

    @Override
    protected void initDataTracker() {
        dataTracker.startTracking(COLOR, LaserColor.RED.ordinal());
        dataTracker.startTracking(LIT, false);
        dataTracker.startTracking(EMISSION, true);
        dataTracker.startTracking(HOLDER, -1);
    }

    public CubeOptics.Frame opticalFrame(float tickDelta) {
        float yaw = MathHelper.lerpAngleDegrees(tickDelta, prevYaw, getYaw());
        float pitch = MathHelper.lerp(tickDelta, prevPitch, getPitch());
        CubeOptics.Frame orientation = CubeOptics.frame(Vec3d.ZERO, yaw, pitch);
        return CubeOptics.frame(getLerpedPos(tickDelta).add(0, orientation.halfExtents().y, 0), yaw, pitch);
    }

    @Override
    protected Box calculateBoundingBox() {
        Vec3d extents = CubeOptics.frame(Vec3d.ZERO, getYaw(), getPitch()).halfExtents();
        return new Box(getX() - extents.x, getY(), getZ() - extents.z,
                getX() + extents.x, getY() + extents.y * 2, getZ() + extents.z);
    }

    public void orient(float yaw, float pitch) {
        setYaw(MathHelper.wrapDegrees(yaw));
        setPitch(MathHelper.clamp(pitch, -90, 90));
        setBoundingBox(calculateBoundingBox());
        LaserBeamNetwork.invalidate(getWorld());
    }

    @Override
    public void tick() {
        super.tick();
        LaserBeamNetwork.registerCube(getWorld(), getId());
        if (getWorld().isClient) {
            if (interpolationSteps > 0) {
                Vec3d next = getPos().lerp(interpolationTarget, 1.0D / interpolationSteps);
                orient(getYaw() + MathHelper.wrapDegrees(targetYaw - getYaw()) / interpolationSteps,
                        getPitch() + (targetPitch - getPitch()) / interpolationSteps);
                setPosition(next);
                interpolationSteps--;
            }
            return;
        }
        PlayerEntity holder = holder();
        if (holder != null && holder.isAlive() && !holder.isSpectator() && squaredDistanceTo(holder) <= 25.0D) {
            carry(holder);
        } else {
            release();
            if (!hasNoGravity()) {
                setVelocity(getVelocity().add(0, -0.04D, 0));
            }
            boolean wasGrounded = isOnGround();
            double fallingSpeed = getVelocity().y;
            move(MovementType.SELF, getVelocity());
            double friction = isOnGround() ? 0.76D : 0.98D;
            setVelocity(getVelocity().multiply(friction, verticalCollision ? 0 : 0.98D, friction));
            if (!hasNoGravity()) {
                settleOnFace();
            }
            if (!wasGrounded && isOnGround() && fallingSpeed < -0.15D) {
                playSound(SoundEvents.BLOCK_NETHERITE_BLOCK_FALL, 0.45F, 0.8F);
            }
            for (LivingEntity entity : getWorld().getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(0.06D, 0, 0.06D),
                    entity -> entity.isAlive() && !entity.isSpectator() && !entity.isSneaking())) {
                if (entity.getY() < getBoundingBox().maxY - 0.08D) {
                    pushAwayFrom(entity);
                }
            }
        }
        checkBlockCollision();
    }

    private void carry(PlayerEntity player) {
        float yawChange = MathHelper.wrapDegrees(player.getYaw() - getYaw());
        float pitchChange = MathHelper.clamp(player.getPitch(), -90, 90) - getPitch();
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(yawChange), Math.abs(pitchChange)) / 5.0F));
        for (int step = 0; step < steps; step++) {
            if (!tryRotate(getYaw() + yawChange / steps, getPitch() + pitchChange / steps, true)) {
                break;
            }
        }
        Vec3d targetCenter = player.getEyePos().add(player.getRotationVec(1.0F).multiply(1.8D)).add(0, -0.15D, 0);
        Vec3d desired = targetCenter.subtract(opticalFrame(1.0F).center());
        if (desired.lengthSquared() > 16.0D) {
            release();
            return;
        }
        if (desired.lengthSquared() > 0.64D) {
            desired = desired.normalize().multiply(0.8D);
        }
        setVelocity(Vec3d.ZERO);
        move(MovementType.SELF, desired);
        fallDistance = 0;
        velocityDirty = true;
    }

    private void settleOnFace() {
        float restPitch = Math.round(getPitch() / 90.0F) * 90.0F;
        float difference = restPitch - getPitch();
        if (difference == 0) {
            return;
        }
        float step = MathHelper.clamp(difference * (isOnGround() ? 0.28F : 0.12F), -6, 6);
        float pitch = Math.abs(difference) < 0.1F ? restPitch : getPitch() + step;
        // On contact, keep the supporting plane fixed while the center of mass lowers.
        // In flight and while carried, rotate about the center instead of the AABB's bottom.
        tryRotate(getYaw(), pitch, !isOnGround());
    }

    private boolean tryRotate(float yaw, float pitch, boolean preserveCenter) {
        float oldYaw = getYaw();
        float oldPitch = getPitch();
        Vec3d oldPosition = getPos();
        Vec3d center = opticalFrame(1.0F).center();
        orient(yaw, pitch);
        if (preserveCenter) {
            double halfHeight = (getBoundingBox().maxY - getBoundingBox().minY) * 0.5D;
            setPosition(center.add(0, -halfHeight, 0));
        }
        if (!getWorld().isSpaceEmpty(this, getBoundingBox())) {
            orient(oldYaw, oldPitch);
            setPosition(oldPosition);
            return false;
        }
        velocityDirty = true;
        return true;
    }

    @Nullable
    public PlayerEntity holder() {
        Entity entity = getWorld().getEntityById(dataTracker.get(HOLDER));
        return entity instanceof PlayerEntity player ? player : null;
    }

    public boolean isHeld() {
        return dataTracker.get(HOLDER) != -1;
    }

    public void release() {
        dataTracker.set(HOLDER, -1);
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (player.isSpectator()) {
            return ActionResult.PASS;
        }
        if (getWorld().isClient) {
            return ActionResult.SUCCESS;
        }
        if (holder() == player) {
            release();
        } else if (isHeld()) {
            player.sendMessage(Text.translatable("message.justifylasers.cube_occupied"), true);
        } else if (player.isSneaking()) {
            float yaw = getYaw();
            float pitch = getPitch();
            orient(Math.round(yaw / 90.0F) * 90.0F + 90.0F, 0);
            if (!getWorld().isSpaceEmpty(this, getBoundingBox())) {
                orient(yaw, pitch);
            }
        } else {
            boolean alreadyHolding = !getWorld().getEntitiesByClass(RefocusingCubeEntity.class,
                    player.getBoundingBox().expand(6), cube -> cube.holder() == player).isEmpty();
            if (!alreadyHolding && squaredDistanceTo(player) <= 25.0D) {
                dataTracker.set(HOLDER, player.getId());
                setVelocity(Vec3d.ZERO);
                playSound(SoundEvents.BLOCK_NETHERITE_BLOCK_HIT, 0.4F, 1.25F);
            }
        }
        return ActionResult.CONSUME;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (isRemoved() || isInvulnerableTo(source)) {
            return false;
        }
        if (getWorld().isClient) {
            return true;
        }
        if (source.getAttacker() instanceof PlayerEntity player && !player.isSpectator()) {
            if (source.getSource() != player || squaredDistanceTo(player) > 36.0D) {
                return false;
            }
            if (isHeld() && holder() != player) {
                return false;
            }
            if (player.isSneaking()) {
                if (!player.getAbilities().creativeMode) {
                    ItemStack item = asItemStack();
                    if (!player.getInventory().insertStack(item)) {
                        dropStack(item);
                    }
                }
                discard();
            } else {
                kick(player);
            }
            return true;
        }
        if (source.isIn(DamageTypeTags.IS_EXPLOSION)) {
            dropStack(asItemStack());
            discard();
            return true;
        }
        return false;
    }

    public void kick(PlayerEntity player) {
        release();
        Vec3d forward = player.getRotationVec(1.0F);
        Vec3d horizontal = new Vec3d(forward.x, 0, forward.z).normalize();
        double speed = player.isSprinting() ? 0.65D : 0.38D;
        setVelocity(horizontal.multiply(speed).add(0, 0.16D, 0));
        velocityModified = true;
        velocityDirty = true;
        playSound(SoundEvents.BLOCK_NETHERITE_BLOCK_HIT, 0.6F, 0.7F);
    }

    @Override
    public void pushAwayFrom(Entity entity) {
        if (isHeld() || entity.isSpectator()) {
            return;
        }
        Vec3d offset = getPos().subtract(entity.getPos()).multiply(1, 0, 1);
        if (offset.lengthSquared() > 1.0E-6D && getVelocity().horizontalLengthSquared() < 0.025D) {
            Vec3d push = offset.normalize().multiply(entity.isSprinting() ? 0.055D : 0.025D);
            addVelocity(push);
            velocityDirty = true;
        }
    }

    @Override
    public boolean isCollidable() {
        return !isRemoved();
    }

    @Override
    public boolean collidesWith(Entity other) {
        return (other.isCollidable() || other.isPushable()) && !isConnectedThroughVehicle(other);
    }

    @Override
    public boolean isPushable() {
        return !isRemoved();
    }

    @Override
    public boolean canHit() {
        return !isRemoved();
    }

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int steps, boolean interpolate) {
        interpolationTarget = new Vec3d(x, y, z);
        targetYaw = yaw;
        targetPitch = pitch;
        interpolationSteps = Math.max(1, Math.min(steps, 3));
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }

    public void setBeamInput(@Nullable LaserBeamNetwork.CubeInput input) {
        dataTracker.set(LIT, input != null);
        if (input != null) {
            dataTracker.set(COLOR, input.color().ordinal());
            dataTracker.set(EMISSION, input.emission());
        }
    }

    public LaserColor getColor() {
        return LaserColor.byIndex(dataTracker.get(COLOR));
    }

    public boolean isLit() {
        return dataTracker.get(LIT);
    }

    public boolean emitsLight() {
        return dataTracker.get(EMISSION);
    }

    public ItemStack asItemStack() {
        ItemStack item = new ItemStack(ModEntities.REFOCUSING_CUBE_ITEM);
        item.getOrCreateNbt().putInt("Color", getColor().ordinal());
        if (hasCustomName()) {
            item.setCustomName(getCustomName());
        }
        return item;
    }

    @Override
    public ItemStack getPickBlockStack() {
        return asItemStack();
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        dataTracker.set(COLOR, LaserColor.byIndex(nbt.getInt("Color")).ordinal());
        dataTracker.set(LIT, false);
        release();
        setBoundingBox(calculateBoundingBox());
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Color", getColor().ordinal());
        // A saved cube is a free physical object; player attachment never survives a world reload.
    }

    @Override
    public void remove(RemovalReason reason) {
        release();
        LaserBeamNetwork.removeCube(getWorld(), getId());
        super.remove(reason);
    }
}

package net.askcraft.justifylasers.block.entity;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.LaserReceiverBlock;
import net.askcraft.justifylasers.laser.LaserBeamNetwork;
import net.askcraft.justifylasers.laser.LaserBeamPath;
import net.askcraft.justifylasers.laser.LaserBeamTrace;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.laser.LaserDamage;
import net.askcraft.justifylasers.laser.LaserRedstoneMode;
import net.askcraft.justifylasers.registry.ModBlockEntities;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class LaserEmitterBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory {
    public static final int MIN_RANGE = 1;
    public static final int MAX_RANGE = 512;
    public static final int DEFAULT_RANGE = 64;
    public static final double BEAM_HIT_RADIUS = 0.11D;
    public static final int BEAM_WIDTH_STEPS = 200;
    public static final int DEFAULT_BEAM_WIDTH_STEP = BEAM_WIDTH_STEPS / 2;
    public static final float MIN_BEAM_WIDTH_SCALE = 0.1F;
    public static final float MAX_BEAM_WIDTH_SCALE = 10.0F;
    public static final int PROPERTY_COUNT = 14;

    private boolean enabled = true;
    private LaserRedstoneMode redstoneMode = LaserRedstoneMode.IGNORE;
    private LaserColor color = LaserColor.RED;
    private boolean breakBlocks;
    private boolean damageEntities;
    private boolean igniteEntities;
    private int damageStep = LaserDamage.DEFAULT_DAMAGE_STEP;
    private int knockbackStep = LaserDamage.DEFAULT_KNOCKBACK_STEP;
    private int hitsPerSecond = LaserDamage.MAX_HITS_PER_SECOND;
    private int beamWidthStep = DEFAULT_BEAM_WIDTH_STEP;
    private int beamRange = DEFAULT_RANGE;
    private boolean lightEmission = true;
    private boolean minecraftLighting = true;
    private boolean active;

    private long ticks;
    @Nullable
    private BlockPos heatedBlock;
    private float heat;
    private int lastBreakStage = -1;

    private final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> enabled ? 1 : 0;
                case 1 -> redstoneMode.ordinal();
                case 2 -> color.ordinal();
                case 3 -> breakBlocks ? 1 : 0;
                case 4 -> damageEntities ? 1 : 0;
                case 5 -> active ? 1 : 0;
                case 6 -> beamWidthStep;
                case 7 -> lightEmission ? 1 : 0;
                case 8 -> minecraftLighting ? 1 : 0;
                case 9 -> damageStep;
                case 10 -> knockbackStep;
                case 11 -> hitsPerSecond;
                case 12 -> igniteEntities ? 1 : 0;
                case 13 -> beamRange;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            LaserBeamNetwork.invalidate(world);
            switch (index) {
                case 0 -> enabled = value != 0;
                case 1 -> redstoneMode = LaserRedstoneMode.byIndex(value);
                case 2 -> color = LaserColor.byIndex(value);
                case 3 -> breakBlocks = value != 0;
                case 4 -> damageEntities = value != 0;
                case 5 -> active = value != 0;
                case 6 -> beamWidthStep = clampBeamWidthStep(value);
                case 7 -> lightEmission = value != 0;
                case 8 -> minecraftLighting = value != 0;
                case 9 -> damageStep = LaserDamage.clampDamageStep(value);
                case 10 -> knockbackStep = LaserDamage.clampKnockbackStep(value);
                case 11 -> hitsPerSecond = LaserDamage.clampHitsPerSecond(value);
                case 12 -> igniteEntities = value != 0;
                case 13 -> beamRange = clampBeamRange(value);
                default -> {
                }
            }
        }

        @Override
        public int size() {
            return PROPERTY_COUNT;
        }
    };

    public LaserEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LASER_EMITTER, pos, state);
    }

    @Override
    public void setWorld(World world) {
        super.setWorld(world);
        LaserBeamNetwork.registerEmitter(world, pos);
    }

    @Override
    public void cancelRemoval() {
        super.cancelRemoval();
        if (world != null) {
            LaserBeamNetwork.registerEmitter(world, pos);
        }
    }

    public static void serverTick(World world, BlockPos pos, BlockState state, LaserEmitterBlockEntity emitter) {
        emitter.ticks++;
        emitter.refreshActiveState();
        LaserBeamNetwork.beginEmitterTick(world, pos);

        if (!emitter.active) {
            emitter.clearHeating();
            return;
        }

        LaserBeamPath path = LaserBeamNetwork.path(emitter, 1.0F);
        LaserBeamTrace trace = path.last();

        if (emitter.damageEntities && LaserDamage.isHitTick(emitter.ticks - 1L, emitter.hitsPerSecond)) {
            Set<UUID> hitEntities = new HashSet<>();
            for (LaserBeamTrace segment : path.segments()) {
                emitter.damageEntities(segment, hitEntities);
            }
        }

        if (trace.hasBlockHit()) {
            emitter.spawnImpactEffects(trace);
            BlockState hitState = world.getBlockState(trace.hitBlock());
            boolean receiverInput = hitState.getBlock() instanceof LaserReceiverBlock
                    && hitState.get(LaserReceiverBlock.FACING) == trace.hitSide();
            if (emitter.breakBlocks && !receiverInput) {
                emitter.heatBlock(trace.hitBlock(), trace.end());
            } else {
                emitter.clearHeating();
            }
        } else {
            emitter.clearHeating();
        }

        if (emitter.ticks % 80L == 0L) {
            world.playSound(null, pos, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.BLOCKS, 0.42F, 1.72F);
        }
    }

    public static void clientTick(World world, BlockPos pos, BlockState state, LaserEmitterBlockEntity emitter) {
        emitter.ticks++;
    }

    private void refreshActiveState() {
        if (world == null || world.isClient) {
            return;
        }

        boolean powered = world.isReceivingRedstonePower(pos);
        boolean shouldBeActive = enabled && redstoneMode.allows(powered);
        boolean activeChanged = active != shouldBeActive;
        active = shouldBeActive;
        boolean shouldEmitLight = shouldBeActive && lightEmission;
        boolean shouldLightWorld = shouldBeActive && minecraftLighting;

        BlockState state = getCachedState();
        BlockState updatedState = state;
        if (state.contains(LaserEmitterBlock.COLOR) && state.get(LaserEmitterBlock.COLOR) != color) {
            updatedState = updatedState.with(LaserEmitterBlock.COLOR, color);
        }
        if (state.contains(LaserEmitterBlock.LIT) && state.get(LaserEmitterBlock.LIT) != shouldBeActive) {
            updatedState = updatedState.with(LaserEmitterBlock.LIT, shouldBeActive);
        }
        if (state.contains(LaserEmitterBlock.EMITTING_LIGHT)
                && state.get(LaserEmitterBlock.EMITTING_LIGHT) != shouldEmitLight) {
            updatedState = updatedState.with(LaserEmitterBlock.EMITTING_LIGHT, shouldEmitLight);
        }
        if (state.contains(LaserEmitterBlock.MINECRAFT_LIGHTING)
                && state.get(LaserEmitterBlock.MINECRAFT_LIGHTING) != shouldLightWorld) {
            updatedState = updatedState.with(LaserEmitterBlock.MINECRAFT_LIGHTING, shouldLightWorld);
        }

        if (updatedState != state) {
            world.setBlockState(pos, updatedState, Block.NOTIFY_LISTENERS);
            LaserBeamNetwork.invalidate(world);
        }

        if (activeChanged) {
            world.playSound(
                    null,
                    pos,
                    shouldBeActive ? SoundEvents.BLOCK_BEACON_ACTIVATE : SoundEvents.BLOCK_BEACON_DEACTIVATE,
                    SoundCategory.BLOCKS,
                    0.7F,
                    shouldBeActive ? 1.45F : 1.15F
            );
            world.updateComparators(pos, state.getBlock());
        }
    }

    private void damageEntities(LaserBeamTrace trace, Set<UUID> hitEntities) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }

        double hitRadius = BEAM_HIT_RADIUS * getBeamWidthScale();
        Box searchBox = new Box(trace.start(), trace.end()).expand(hitRadius + 0.35D);
        List<LivingEntity> targets = serverWorld.getEntitiesByClass(
                LivingEntity.class,
                searchBox,
                entity -> entity.isAlive() && !entity.isSpectator()
        );
        DamageSource damageSource = LaserDamage.source(serverWorld, trace.start());
        float damage = LaserDamage.damageForStep(damageStep);
        double knockback = LaserDamage.knockbackForStep(knockbackStep);

        for (LivingEntity target : targets) {
            Box hitBox = target.getBoundingBox().expand(hitRadius);
            if ((hitBox.contains(trace.start()) || hitBox.raycast(trace.start(), trace.end()).isPresent())
                    && hitEntities.add(target.getUuid())) {
                if (LaserDamage.hit(target, damageSource, trace.axis(), damage, knockback)
                        && igniteEntities && !target.isFireImmune()) {
                    target.setOnFireFor(4);
                }
            }
        }
    }

    private void spawnImpactEffects(LaserBeamTrace trace) {
        if (!(world instanceof ServerWorld serverWorld) || ticks % 2L != 0L) {
            return;
        }

        Vec3d axis = trace.axis();
        Vec3d impact = trace.end().subtract(axis.multiply(0.018D));
        serverWorld.spawnParticles(
                new DustParticleEffect(color.vector(), 1.25F),
                impact.x, impact.y, impact.z,
                3,
                0.035D, 0.035D, 0.035D,
                0.004D
        );
        serverWorld.spawnParticles(
                ParticleTypes.ELECTRIC_SPARK,
                impact.x, impact.y, impact.z,
                1,
                0.055D, 0.055D, 0.055D,
                0.025D
        );

        if (breakBlocks && ticks % 4L == 0L) {
            serverWorld.spawnParticles(
                    ParticleTypes.SMOKE,
                    impact.x, impact.y, impact.z,
                    1,
                    0.025D, 0.025D, 0.025D,
                    0.008D
            );
        }
    }

    private void heatBlock(BlockPos targetPos, Vec3d impact) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }

        BlockState targetState = world.getBlockState(targetPos);
        float hardness = targetState.getHardness(world, targetPos);
        if (targetState.isAir() || hardness < 0.0F) {
            clearHeating();
            return;
        }

        if (!targetPos.equals(heatedBlock)) {
            clearHeating();
            heatedBlock = targetPos.toImmutable();
            heat = 0.0F;
        }

        heat += 1.0F;
        float requiredHeat = MathHelper.clamp(18.0F + hardness * 28.0F, 12.0F, 600.0F);
        int breakStage = Math.min(9, (int) (heat / requiredHeat * 10.0F));
        if (breakStage != lastBreakStage) {
            world.setBlockBreakingInfo(breakerId(), targetPos, breakStage);
            lastBreakStage = breakStage;
        }

        if (heat < requiredHeat) {
            return;
        }

        world.setBlockBreakingInfo(breakerId(), targetPos, -1);
        boolean broken = serverWorld.breakBlock(targetPos, true, null);
        if (broken) {
            LaserBeamNetwork.invalidate(world);
            serverWorld.spawnParticles(
                    ParticleTypes.FLAME,
                    impact.x, impact.y, impact.z,
                    9,
                    0.16D, 0.16D, 0.16D,
                    0.035D
            );
            serverWorld.spawnParticles(
                    ParticleTypes.LARGE_SMOKE,
                    impact.x, impact.y, impact.z,
                    5,
                    0.12D, 0.12D, 0.12D,
                    0.025D
            );
            world.playSound(null, targetPos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.55F, 1.65F);
        }
        heatedBlock = null;
        heat = 0.0F;
        lastBreakStage = -1;
    }

    private void clearHeating() {
        if (world != null && heatedBlock != null) {
            world.setBlockBreakingInfo(breakerId(), heatedBlock, -1);
        }
        heatedBlock = null;
        heat = 0.0F;
        lastBreakStage = -1;
    }

    private int breakerId() {
        return -1_000_000 - Math.abs(pos.hashCode());
    }

    public void handleButton(int buttonId) {
        if (buttonId >= LaserEmitterScreenHandler.BEAM_WIDTH_BUTTON_BASE
                && buttonId <= LaserEmitterScreenHandler.BEAM_WIDTH_BUTTON_MAX) {
            beamWidthStep = clampBeamWidthStep(buttonId - LaserEmitterScreenHandler.BEAM_WIDTH_BUTTON_BASE);
        } else if (buttonId >= LaserEmitterScreenHandler.DAMAGE_BUTTON_MIN
                && buttonId <= LaserEmitterScreenHandler.DAMAGE_BUTTON_MAX) {
            damageStep = buttonId - LaserEmitterScreenHandler.DAMAGE_BUTTON_BASE;
        } else if (buttonId >= LaserEmitterScreenHandler.KNOCKBACK_BUTTON_BASE
                && buttonId <= LaserEmitterScreenHandler.KNOCKBACK_BUTTON_MAX) {
            knockbackStep = buttonId - LaserEmitterScreenHandler.KNOCKBACK_BUTTON_BASE;
        } else if (buttonId >= LaserEmitterScreenHandler.HIT_RATE_BUTTON_MIN
                && buttonId <= LaserEmitterScreenHandler.HIT_RATE_BUTTON_MAX) {
            hitsPerSecond = buttonId - LaserEmitterScreenHandler.HIT_RATE_BUTTON_BASE;
        } else if (buttonId >= LaserEmitterScreenHandler.RANGE_BUTTON_MIN
                && buttonId <= LaserEmitterScreenHandler.RANGE_BUTTON_MAX) {
            beamRange = buttonId - LaserEmitterScreenHandler.RANGE_BUTTON_BASE;
        } else {
            switch (buttonId) {
                case LaserEmitterScreenHandler.BUTTON_ENABLED -> enabled = !enabled;
                case LaserEmitterScreenHandler.BUTTON_REDSTONE -> redstoneMode = redstoneMode.next();
                case LaserEmitterScreenHandler.BUTTON_COLOR -> color = color.next();
                case LaserEmitterScreenHandler.BUTTON_BREAK_BLOCKS -> breakBlocks = !breakBlocks;
                case LaserEmitterScreenHandler.BUTTON_DAMAGE_ENTITIES -> damageEntities = !damageEntities;
                case LaserEmitterScreenHandler.BUTTON_LIGHT_EMISSION -> lightEmission = !lightEmission;
                case LaserEmitterScreenHandler.BUTTON_MINECRAFT_LIGHTING -> minecraftLighting = !minecraftLighting;
                case LaserEmitterScreenHandler.BUTTON_IGNITE_ENTITIES -> igniteEntities = !igniteEntities;
                case LaserEmitterScreenHandler.BUTTON_RESET_DAMAGE_SETTINGS -> {
                    damageStep = LaserDamage.DEFAULT_DAMAGE_STEP;
                    knockbackStep = LaserDamage.DEFAULT_KNOCKBACK_STEP;
                    hitsPerSecond = LaserDamage.MAX_HITS_PER_SECOND;
                    igniteEntities = false;
                }
                default -> {
                    return;
                }
            }
            refreshActiveState();
        }
        sync();
    }

    private void sync() {
        LaserBeamNetwork.invalidate(world);
        markDirty();
        if (world != null) {
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
        }
    }

    public PropertyDelegate getPropertyDelegate() {
        return propertyDelegate;
    }

    public boolean isBeamActive() {
        BlockState state = getCachedState();
        return state.contains(LaserEmitterBlock.LIT) && state.get(LaserEmitterBlock.LIT);
    }

    public LaserColor getColor() {
        return color;
    }

    public float getBeamWidthScale() {
        return beamWidthScale(beamWidthStep);
    }

    public int getBeamRange() {
        return beamRange;
    }

    public static int clampBeamRange(int range) {
        return MathHelper.clamp(range, MIN_RANGE, MAX_RANGE);
    }

    public boolean isLightEmissionEnabled() {
        return lightEmission;
    }

    public static int clampBeamWidthStep(int step) {
        return MathHelper.clamp(step, 0, BEAM_WIDTH_STEPS);
    }

    public static float beamWidthScale(int step) {
        int clampedStep = clampBeamWidthStep(step);
        double progress = clampedStep / (double) BEAM_WIDTH_STEPS;
        return (float) (MIN_BEAM_WIDTH_SCALE * Math.pow(
                MAX_BEAM_WIDTH_SCALE / MIN_BEAM_WIDTH_SCALE,
                progress
        ));
    }

    public long getTicks() {
        return ticks;
    }

    public LaserBeamTrace getBeamTrace() {
        if (world == null) {
            Vec3d center = Vec3d.ofCenter(pos);
            return new LaserBeamTrace(center, center, getCachedState().get(LaserEmitterBlock.FACING), null);
        }
        return LaserBeamNetwork.path(this, 1.0F).segments().get(0);
    }

    @Override
    public void markRemoved() {
        clearHeating();
        if (world != null) {
            LaserBeamNetwork.removeEmitter(world, pos);
        }
        super.markRemoved();
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putBoolean("Enabled", enabled);
        nbt.putInt("RedstoneMode", redstoneMode.ordinal());
        nbt.putInt("Color", color.ordinal());
        nbt.putBoolean("BreakBlocks", breakBlocks);
        nbt.putBoolean("DamageEntities", damageEntities);
        nbt.putBoolean("IgniteEntities", igniteEntities);
        nbt.putInt("DamageStep", damageStep);
        nbt.putInt("KnockbackStep", knockbackStep);
        nbt.putInt("HitsPerSecond", hitsPerSecond);
        nbt.putInt("BeamWidthStep", beamWidthStep);
        nbt.putInt("BeamRange", beamRange);
        nbt.putBoolean("LightEmission", lightEmission);
        nbt.putBoolean("MinecraftLighting", minecraftLighting);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        LaserBeamNetwork.invalidate(world);
        if (nbt.contains("Enabled")) {
            enabled = nbt.getBoolean("Enabled");
        }
        redstoneMode = LaserRedstoneMode.byIndex(nbt.getInt("RedstoneMode"));
        color = LaserColor.byIndex(nbt.getInt("Color"));
        breakBlocks = nbt.getBoolean("BreakBlocks");
        if (nbt.contains("DamageEntities")) {
            damageEntities = nbt.getBoolean("DamageEntities");
        }
        igniteEntities = nbt.getBoolean("IgniteEntities");
        damageStep = nbt.contains("DamageStep")
                ? LaserDamage.clampDamageStep(nbt.getInt("DamageStep"))
                : LaserDamage.DEFAULT_DAMAGE_STEP;
        knockbackStep = nbt.contains("KnockbackStep")
                ? LaserDamage.clampKnockbackStep(nbt.getInt("KnockbackStep"))
                : LaserDamage.DEFAULT_KNOCKBACK_STEP;
        hitsPerSecond = nbt.contains("HitsPerSecond")
                ? LaserDamage.clampHitsPerSecond(nbt.getInt("HitsPerSecond"))
                : LaserDamage.MAX_HITS_PER_SECOND;
        beamWidthStep = nbt.contains("BeamWidthStep")
                ? clampBeamWidthStep(nbt.getInt("BeamWidthStep"))
                : DEFAULT_BEAM_WIDTH_STEP;
        beamRange = nbt.contains("BeamRange") ? clampBeamRange(nbt.getInt("BeamRange")) : DEFAULT_RANGE;
        lightEmission = !nbt.contains("LightEmission") || nbt.getBoolean("LightEmission");
        // Saves predating the lighting split used LightEmission for both settings.
        minecraftLighting = nbt.contains("MinecraftLighting")
                ? nbt.getBoolean("MinecraftLighting")
                : lightEmission;
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.justifylasers.laser_emitter");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new LaserEmitterScreenHandler(syncId, playerInventory, this);
    }
}

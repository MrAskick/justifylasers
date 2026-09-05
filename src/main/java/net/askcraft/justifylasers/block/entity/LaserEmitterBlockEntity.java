package net.askcraft.justifylasers.block.entity;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.laser.LaserBeamTrace;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.laser.LaserRedstoneMode;
import net.askcraft.justifylasers.registry.ModBlockEntities;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
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

import java.util.List;

public class LaserEmitterBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory {
    public static final double MAX_RANGE = 64.0D;
    public static final double BEAM_HIT_RADIUS = 0.11D;
    public static final int BEAM_WIDTH_STEPS = 200;
    public static final int DEFAULT_BEAM_WIDTH_STEP = BEAM_WIDTH_STEPS / 2;
    public static final float MIN_BEAM_WIDTH_SCALE = 0.1F;
    public static final float MAX_BEAM_WIDTH_SCALE = 10.0F;
    public static final int PROPERTY_COUNT = 9;

    private boolean enabled = true;
    private LaserRedstoneMode redstoneMode = LaserRedstoneMode.IGNORE;
    private LaserColor color = LaserColor.RED;
    private boolean breakBlocks;
    private boolean damageEntities;
    private int beamWidthStep = DEFAULT_BEAM_WIDTH_STEP;
    private boolean lightEmission = true;
    private boolean minecraftLighting = true;
    private boolean active;

    private long ticks;
    @Nullable
    private BlockPos heatedBlock;
    private float heat;
    private int lastBreakStage = -1;
    @Nullable
    private LaserBeamTrace clientBeamTrace;

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
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
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

    public static void serverTick(World world, BlockPos pos, BlockState state, LaserEmitterBlockEntity emitter) {
        emitter.ticks++;
        emitter.refreshActiveState();

        if (!emitter.active) {
            emitter.clearHeating();
            return;
        }

        LaserBeamTrace trace = LaserBeamTrace.trace(world, pos, state, MAX_RANGE);

        if (emitter.damageEntities && emitter.ticks % 4L == 0L) {
            emitter.damageEntities(trace);
        }

        if (trace.hasBlockHit()) {
            emitter.spawnImpactEffects(trace);
            if (emitter.breakBlocks) {
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
        if (!state.get(LaserEmitterBlock.LIT)) {
            emitter.clientBeamTrace = null;
        } else if (emitter.clientBeamTrace == null || emitter.ticks % 2L == 0L) {
            emitter.clientBeamTrace = LaserBeamTrace.trace(world, pos, state, MAX_RANGE);
        }
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

    private void damageEntities(LaserBeamTrace trace) {
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

        for (LivingEntity target : targets) {
            if (target.getBoundingBox().expand(hitRadius).raycast(trace.start(), trace.end()).isPresent()) {
                target.damage(world.getDamageSources().magic(), 2.5F);
                target.setOnFireFor(1);
            }
        }
    }

    private void spawnImpactEffects(LaserBeamTrace trace) {
        if (!(world instanceof ServerWorld serverWorld) || ticks % 2L != 0L) {
            return;
        }

        Vec3d axis = Vec3d.of(trace.direction().getVector());
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
            sync();
            return;
        }

        switch (buttonId) {
            case LaserEmitterScreenHandler.BUTTON_ENABLED -> enabled = !enabled;
            case LaserEmitterScreenHandler.BUTTON_REDSTONE -> redstoneMode = redstoneMode.next();
            case LaserEmitterScreenHandler.BUTTON_COLOR -> color = color.next();
            case LaserEmitterScreenHandler.BUTTON_BREAK_BLOCKS -> breakBlocks = !breakBlocks;
            case LaserEmitterScreenHandler.BUTTON_DAMAGE_ENTITIES -> damageEntities = !damageEntities;
            case LaserEmitterScreenHandler.BUTTON_LIGHT_EMISSION -> lightEmission = !lightEmission;
            case LaserEmitterScreenHandler.BUTTON_MINECRAFT_LIGHTING -> minecraftLighting = !minecraftLighting;
            default -> {
                return;
            }
        }
        refreshActiveState();
        sync();
    }

    private void sync() {
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
        if (world.isClient && clientBeamTrace != null) {
            return clientBeamTrace;
        }
        return LaserBeamTrace.trace(world, pos, getCachedState(), MAX_RANGE);
    }

    @Override
    public void markRemoved() {
        clearHeating();
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
        nbt.putInt("BeamWidthStep", beamWidthStep);
        nbt.putBoolean("LightEmission", lightEmission);
        nbt.putBoolean("MinecraftLighting", minecraftLighting);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (nbt.contains("Enabled")) {
            enabled = nbt.getBoolean("Enabled");
        }
        redstoneMode = LaserRedstoneMode.byIndex(nbt.getInt("RedstoneMode"));
        color = LaserColor.byIndex(nbt.getInt("Color"));
        breakBlocks = nbt.getBoolean("BreakBlocks");
        if (nbt.contains("DamageEntities")) {
            damageEntities = nbt.getBoolean("DamageEntities");
        }
        beamWidthStep = nbt.contains("BeamWidthStep")
                ? clampBeamWidthStep(nbt.getInt("BeamWidthStep"))
                : DEFAULT_BEAM_WIDTH_STEP;
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

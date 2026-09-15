package net.askcraft.justifylasers.platform;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.entity.RefocusingCubeEntity;
import net.askcraft.justifylasers.forge.EnergyModeCondition;
import net.askcraft.justifylasers.forge.ForgeLaserCrystalItem;
import net.askcraft.justifylasers.forge.ForgeRefocusingCubeItem;
import net.askcraft.justifylasers.item.LaserCrystalItem;
import net.askcraft.justifylasers.item.LaserModuleItem;
import net.askcraft.justifylasers.item.LaserPartItem;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.network.LaserPolicyPacket;
import net.askcraft.justifylasers.network.LaserSettingsPacket;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public final class Platform {
    public static final String ENERGY_UNIT = "FE";

    private static RegisterEvent registrationEvent;

    public static void onRegister(RegistryKey<? extends Registry<?>> key, Runnable registration) {
        modBus.addListener((RegisterEvent event) -> {
            if (!event.getRegistryKey().equals(key)) return;
            registrationEvent = event;
            try {
                registration.run();
            } finally {
                registrationEvent = null;
            }
        });
    }

    public static net.minecraft.network.packet.Packet<net.minecraft.network.listener.ClientPlayPacketListener> cubeSpawnPacket(net.minecraft.entity.Entity cube) {
        return NetworkHooks.getEntitySpawningPacket(cube);
    }

    private static IEventBus modBus;
    public static final SimpleChannel NETWORK = NetworkRegistry.newSimpleChannel(
            JustifyLasers.id("main"), () -> "4", "4"::equals, "4"::equals);

    public static void initialize(IEventBus bus) {
        modBus = bus;
    }

    public static <T, V extends T> V register(Registry<T> registry, Identifier id, V value) {
        if (registrationEvent == null) throw new IllegalStateException("Registration outside loader lifecycle: " + id);
        registrationEvent.register(registry.getKey(), helper -> helper.register(id, value));
        return value;
    }

    public static <T extends BlockEntity> BlockEntityType<T> blockEntityType(BiFunction<BlockPos, BlockState, T> factory, Block... blocks) {
        return BlockEntityType.Builder.create(factory::apply, blocks).build(null);
    }

    public static EntityType<RefocusingCubeEntity> cubeEntityType() {
        return EntityType.Builder.<RefocusingCubeEntity>create(RefocusingCubeEntity::new, SpawnGroup.MISC)
                .setDimensions(0.9F, 0.9F).makeFireImmune().maxTrackingRange(34).trackingTickInterval(1)
                .setShouldReceiveVelocityUpdates(true).build(JustifyLasers.id("refocusing_cube").toString());
    }

    public static ItemGroup.Builder itemGroupBuilder() {
        return ItemGroup.builder();
    }

    public static Item tabletItem(Item.Settings settings) {
        return new PlatformTabletItem(settings);
    }

    public static Item cubeItem(Item.Settings settings) {
        return new ForgeRefocusingCubeItem(settings);
    }

    public static LaserCrystalItem crystalItem(Item.Settings settings, LaserColor color) {
        return new ForgeLaserCrystalItem(settings, color);
    }

    public static LaserModuleItem moduleItem(Item.Settings settings, LaserModule module, int rangePerItem) {
        return new net.askcraft.justifylasers.forge.ForgeLaserModuleItem(settings, module, rangePerItem);
    }

    public static LaserPartItem partItem(Item.Settings settings, String partId) {
        return new net.askcraft.justifylasers.forge.ForgeLaserPartItem(settings, partId);
    }

    public static void addToRedstoneTab(ItemConvertible... items) {
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey().equals(ItemGroups.REDSTONE)) {
                for (ItemConvertible item : items) event.accept(() -> item);
            }
        });
    }

    public static <T extends ScreenHandler> ScreenHandlerType<T> screenType(LaserScreenFactory.MenuFactory<T> factory) {
        return IForgeMenuType.create((syncId, inventory, buffer) -> factory.create(syncId, inventory, buffer.readBlockPos()));
    }

    public static void openScreen(PlayerEntity player, NamedScreenHandlerFactory factory) {
        if (player instanceof ServerPlayerEntity serverPlayer && factory instanceof LaserScreenFactory laser) {
            NetworkHooks.openScreen(serverPlayer, laser, buffer -> laser.writeScreenOpeningData(serverPlayer, buffer));
        } else {
            player.openHandledScreen(factory);
        }
    }

    public static void registerSettingsReceiver() {
        NETWORK.registerMessage(3, net.askcraft.justifylasers.network.SaberTogglePacket.class, net.askcraft.justifylasers.network.SaberTogglePacket::write, net.askcraft.justifylasers.network.SaberTogglePacket::new,
                (packet, supplier) -> {
                    var context = supplier.get();
                    context.enqueueWork(() -> { if (context.getSender() != null) packet.apply(context.getSender()); });
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        NETWORK.registerMessage(4, net.askcraft.justifylasers.network.SaberStatePacket.class, net.askcraft.justifylasers.network.SaberStatePacket::write, net.askcraft.justifylasers.network.SaberStatePacket::new,
                (packet, supplier) -> { var context = supplier.get(); context.enqueueWork(packet::deliver); context.setPacketHandled(true); }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        NETWORK.registerMessage(2, net.askcraft.justifylasers.network.LaserGunControlPacket.class,
                net.askcraft.justifylasers.network.LaserGunControlPacket::write, net.askcraft.justifylasers.network.LaserGunControlPacket::new,
                (packet, supplier) -> {
                    var context = supplier.get();
                    context.enqueueWork(() -> { if (context.getSender() != null) packet.apply(context.getSender()); });
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        NETWORK.registerMessage(1, LaserPolicyPacket.class, LaserPolicyPacket::write, LaserPolicyPacket::new,
                (packet, contextSupplier) -> {
                    var context = contextSupplier.get();
                    context.enqueueWork(() -> LaserConfig.applyServerMode(packet.technicalMode()));
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayerEntity player) {
                NETWORK.send(PacketDistributor.PLAYER.with(() -> player), new LaserPolicyPacket(LaserConfig.technicalMode()));
            }
        });
        NETWORK.registerMessage(0, LaserSettingsPacket.class, LaserSettingsPacket::write, LaserSettingsPacket::new,
                (packet, contextSupplier) -> {
                    var context = contextSupplier.get();
                    context.enqueueWork(() -> {
                        ServerPlayerEntity player = context.getSender();
                        if (player != null) packet.apply(player);
                    });
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void onEndWorldTick(Consumer<ServerWorld> callback) {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.LevelTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END && event.level instanceof ServerWorld world) callback.accept(world);
        });
    }

    public static void registerEnergy() {
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, (AttachCapabilitiesEvent<BlockEntity> event) -> {
            if (!(event.getObject() instanceof net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity machine)) return;
            var fluid = LazyOptional.of(() -> new PlatformWaterStorage(machine));
            var items = new java.util.EnumMap<Direction, LazyOptional<net.minecraftforge.items.IItemHandler>>(Direction.class);
            for (Direction side : Direction.values()) items.put(side, LazyOptional.of(() -> new net.minecraftforge.items.wrapper.SidedInvWrapper(machine, side)));
            event.addCapability(JustifyLasers.id("industry_ports"), new ICapabilityProvider() {
                @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
                    if (capability == ForgeCapabilities.FLUID_HANDLER && machine.kind() == net.askcraft.justifylasers.industry.MachineKind.CRYSTAL_GROWER) return fluid.cast();
                    if (capability == ForgeCapabilities.ITEM_HANDLER) return items.get(side == null ? Direction.UP : side).cast();
                    return LazyOptional.empty();
                }
            });
            event.addListener(() -> { fluid.invalidate(); items.values().forEach(LazyOptional::invalidate); });
        });
        CraftingHelper.register(new EnergyModeCondition.Serializer());
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, (AttachCapabilitiesEvent<BlockEntity> event) -> {
            BlockEntity entity = event.getObject();
            if (!(entity instanceof LaserEmitterBlockEntity emitter && emitter.isPoweredEmitter())
                    && !(entity instanceof net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity receiver
                        && receiver.kind() == net.askcraft.justifylasers.block.LaserOpticBlock.Kind.ENERGY_RECEIVER)
                    && !(entity instanceof net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity)) return;
            // Attachment runs before subclass fields initialize; resolve ports lazily.
            LazyOptional<PlatformEnergyStorage> emitterPort = LazyOptional.of(() -> entity instanceof net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity machine
                    ? machine.energyPort() : ((LaserEmitterBlockEntity) entity).energyPort());
            var ports = new java.util.EnumMap<Direction, LazyOptional<PlatformEnergyStorage>>(Direction.class);
            if (entity instanceof net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity receiver)
                for (Direction side : Direction.values()) ports.put(side, LazyOptional.of(() -> receiver.energyPort(side)));
            event.addCapability(JustifyLasers.id("energy"), new ICapabilityProvider() {
                @Override
                public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
                    if (capability != ForgeCapabilities.ENERGY) return LazyOptional.empty();
                    if (entity instanceof net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity receiver)
                        return receiver.isEnergyOutput(side) ? ports.get(side).cast() : LazyOptional.empty();
                    return emitterPort.cast();
                }
            });
            event.addListener(() -> {
                emitterPort.invalidate();
                ports.values().forEach(LazyOptional::invalidate);
            });
        });
    }

    public static boolean isModLoaded(String id) {
        return ModList.get().isLoaded(id);
    }

    public static void exportEnergy(net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity receiver) {
        var world = receiver.getWorld();
        int remaining = Math.min(receiver.energy().stored(), LaserConfig.get().maxInput);
        if (!receiver.exportsEnergy() || remaining <= 0) return;
        for (Direction side : Direction.values()) {
            if (!receiver.isEnergyOutput(side) || remaining <= 0) continue;
            BlockPos target = receiver.getPos().offset(side);
            if (!world.isChunkLoaded(target)) continue;
            BlockEntity entity = world.getBlockEntity(target);
            if (entity == null) continue;
            var storage = entity.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).orElse(null);
            if (storage == null || !storage.canReceive()) continue;
            int sent = storage.receiveEnergy(remaining, false);
            receiver.energy().extract(sent, false);
            remaining -= sent;
        }
    }

    public static void exportEnergy(net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity receiver) {
        var world = receiver.getWorld();
        int remaining = Math.min(receiver.energy().stored(), LaserConfig.get().machineTransfer);
        if (!receiver.exportsEnergy() || remaining <= 0) return;
        for (Direction side : Direction.values()) {
            if (remaining <= 0) continue;
            BlockPos target = receiver.getPos().offset(side);
            if (!world.isChunkLoaded(target)) continue;
            BlockEntity entity = world.getBlockEntity(target);
            if (entity == null) continue;
            var storage = entity.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).orElse(null);
            if (storage == null || !storage.canReceive()) continue;
            int sent = storage.receiveEnergy(remaining, false);
            receiver.energy().extract(sent, false);
            remaining -= sent;
        }
    }


    public static void opticPortsChanged(net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity optic) {
        var world = optic.getWorld();
        world.updateNeighborsAlways(optic.getPos(), optic.getCachedState().getBlock());
        world.updateListeners(optic.getPos(), optic.getCachedState(), optic.getCachedState(), net.minecraft.block.Block.NOTIFY_ALL);
    }

    public static Path configDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    private Platform() {
    }

    public static void sendSaberState(ServerPlayerEntity player, net.askcraft.justifylasers.network.SaberStatePacket packet) {
        NETWORK.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}

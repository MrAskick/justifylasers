package net.askcraft.justifylasers.platform;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.entity.RefocusingCubeEntity;
import net.askcraft.justifylasers.item.LaserCrystalItem;
import net.askcraft.justifylasers.item.LaserModuleItem;
import net.askcraft.justifylasers.item.LaserPartItem;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.item.RefocusingCubeItem;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.neoforge.EnergyModeCondition;
import net.askcraft.justifylasers.network.LaserPolicyPacket;
import net.askcraft.justifylasers.network.PolicyPayload;
import net.askcraft.justifylasers.network.SettingsPayload;
import net.askcraft.justifylasers.registry.ModBlockEntities;
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.nio.file.Path;
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

    private static IEventBus modBus;

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
                .dimensions(0.9F, 0.9F).makeFireImmune().maxTrackingRange(34).trackingTickInterval(1)
                .setShouldReceiveVelocityUpdates(true).build(JustifyLasers.id("refocusing_cube").toString());
    }

    public static ItemGroup.Builder itemGroupBuilder() {
        return ItemGroup.builder();
    }

    public static Item tabletItem(Item.Settings settings) {
        return new net.askcraft.justifylasers.item.ExtraterrestrialTabletItem(settings);
    }

    public static Item cubeItem(Item.Settings settings) {
        return new RefocusingCubeItem(settings);
    }

    public static LaserCrystalItem crystalItem(Item.Settings settings, LaserColor color) {
        return new LaserCrystalItem(settings, color);
    }

    public static LaserModuleItem moduleItem(Item.Settings settings, LaserModule module, int rangePerItem) {
        return new LaserModuleItem(settings, module, rangePerItem);
    }

    public static LaserPartItem partItem(Item.Settings settings, String partId) {
        return new LaserPartItem(settings, partId);
    }

    public static void addToRedstoneTab(ItemConvertible... items) {
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey().equals(ItemGroups.REDSTONE)) {
                for (ItemConvertible item : items) event.add(item);
            }
        });
    }

    public static <T extends ScreenHandler> ScreenHandlerType<T> screenType(LaserScreenFactory.MenuFactory<T> factory) {
        return IMenuTypeExtension.create((syncId, inventory, buffer) -> factory.create(syncId, inventory, buffer.readBlockPos()));
    }

    public static void openScreen(PlayerEntity player, NamedScreenHandlerFactory factory) {
        if (player instanceof ServerPlayerEntity serverPlayer && factory instanceof LaserScreenFactory laser) {
            serverPlayer.openMenu(laser, buffer -> laser.writeScreenOpeningData(serverPlayer, buffer));
        } else {
            player.openHandledScreen(factory);
        }
    }

    public static void registerSettingsReceiver() {
        modBus.addListener((RegisterPayloadHandlersEvent event) -> {
            var registrar = event.registrar("4");
            registrar.playToServer(net.askcraft.justifylasers.network.SaberTogglePayload.ID, net.askcraft.justifylasers.network.SaberTogglePayload.CODEC,
                    (payload, context) -> payload.packet().apply((ServerPlayerEntity) context.player()));
            registrar.playToClient(net.askcraft.justifylasers.network.SaberStatePayload.ID, net.askcraft.justifylasers.network.SaberStatePayload.CODEC,
                    (payload, context) -> payload.packet().deliver());
            registrar.playToServer(net.askcraft.justifylasers.network.GunControlPayload.ID, net.askcraft.justifylasers.network.GunControlPayload.CODEC,
                    (payload, context) -> payload.packet().apply((ServerPlayerEntity) context.player()));
            registrar.playToServer(SettingsPayload.ID, SettingsPayload.CODEC,
                    (payload, context) -> payload.packet().apply((ServerPlayerEntity) context.player()));
            registrar.playToClient(PolicyPayload.ID, PolicyPayload.CODEC,
                    (payload, context) -> LaserConfig.applyServerMode(payload.packet().technicalMode()));
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayerEntity player) {
                PacketDistributor.sendToPlayer(player, new PolicyPayload(new LaserPolicyPacket(LaserConfig.technicalMode())));
            }
        });
    }

    public static void registerEnergy() {
        onRegister(NeoForgeRegistries.CONDITION_SERIALIZERS.getKey(), () ->
                register(NeoForgeRegistries.CONDITION_SERIALIZERS, JustifyLasers.id("energy_mode"), EnergyModeCondition.CODEC));
        modBus.addListener((RegisterCapabilitiesEvent event) -> {
                event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, context) -> new PlatformTabletEnergy(stack),
                        net.askcraft.justifylasers.registry.ModIndustry.EXTRATERRESTRIAL_TABLET);
                event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.INDUSTRIAL_MACHINE, (machine, side) ->
                        machine.kind() == net.askcraft.justifylasers.industry.MachineKind.CRYSTAL_GROWER ? new PlatformWaterStorage(machine) : null);
                event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.INDUSTRIAL_MACHINE, (machine, side) ->
                        new net.neoforged.neoforge.items.wrapper.SidedInvWrapper(machine, side == null ? net.minecraft.util.math.Direction.UP : side));
                event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.INDUSTRIAL_MACHINE, (machine, side) -> machine.energyPort());
                event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.LASER_EMITTER,
                        (emitter, side) -> emitter.isPoweredEmitter() ? emitter.energyPort() : null);
                event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.LASER_OPTIC,
                        (receiver, side) -> receiver.kind() == net.askcraft.justifylasers.block.LaserOpticBlock.Kind.ENERGY_RECEIVER
                                && receiver.isEnergyOutput(side) ? receiver.energyPort(side) : null);
        });
    }

    public static void exportEnergy(net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity receiver) {
        var world = receiver.getWorld();
        int remaining = Math.min(receiver.energy().stored(), LaserConfig.get().maxInput);
        if (!receiver.exportsEnergy() || remaining <= 0) return;
        for (var side : net.minecraft.util.math.Direction.values()) {
            if (!receiver.isEnergyOutput(side) || remaining <= 0) continue;
            BlockPos target = receiver.getPos().offset(side);
            if (!world.isChunkLoaded(target)) continue;
            var storage = world.getCapability(Capabilities.EnergyStorage.BLOCK, target, side.getOpposite());
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
        for (var side : net.minecraft.util.math.Direction.values()) {
            if (remaining <= 0) continue;
            BlockPos target = receiver.getPos().offset(side);
            if (!world.isChunkLoaded(target)) continue;
            var storage = world.getCapability(Capabilities.EnergyStorage.BLOCK, target, side.getOpposite());
            if (storage == null || !storage.canReceive()) continue;
            int sent = storage.receiveEnergy(remaining, false);
            receiver.energy().extract(sent, false);
            remaining -= sent;
        }
    }


    public static void registerExplorationLoot() {
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.LootTableLoadEvent event) -> {
            var pool = net.askcraft.justifylasers.industry.TabletLoot.pool(event.getName());
            if (pool != null) event.getTable().addPool(pool.name(net.askcraft.justifylasers.industry.TabletLoot.POOL_NAME).build());
        });
    }

    public static void onEndWorldTick(Consumer<ServerWorld> callback) {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post event) -> {
            if (event.getLevel() instanceof ServerWorld world) callback.accept(world);
        });
    }

    public static boolean isModLoaded(String id) {
        return ModList.get().isLoaded(id);
    }

    public static void opticPortsChanged(net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity optic) {
        var world = optic.getWorld();
        world.invalidateCapabilities(optic.getPos());
        world.updateNeighborsAlways(optic.getPos(), optic.getCachedState().getBlock());
        world.updateListeners(optic.getPos(), optic.getCachedState(), optic.getCachedState(), net.minecraft.block.Block.NOTIFY_ALL);
    }

    public static Path configDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    private Platform() {
    }

    public static void sendSaberState(ServerPlayerEntity player, net.askcraft.justifylasers.network.SaberStatePacket packet) {
        PacketDistributor.sendToPlayer(player, new net.askcraft.justifylasers.network.SaberStatePayload(packet));
    }
}

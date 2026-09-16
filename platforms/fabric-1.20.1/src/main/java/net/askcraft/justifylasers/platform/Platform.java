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
import net.askcraft.justifylasers.network.LaserPolicyPacket;
import net.askcraft.justifylasers.network.LaserSettingsPacket;
import net.askcraft.justifylasers.registry.ModBlockEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import team.reborn.energy.api.EnergyStorage;

import java.nio.file.Path;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public final class Platform {
    public static final String ENERGY_UNIT = "E";

    public static void onRegister(RegistryKey<? extends Registry<?>> key, Runnable registration) {
        registration.run();
    }

    public static net.minecraft.network.packet.Packet<net.minecraft.network.listener.ClientPlayPacketListener> cubeSpawnPacket(net.minecraft.entity.Entity cube) {
        return new net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket(cube);
    }

    public static <T, V extends T> V register(Registry<T> registry, Identifier id, V value) {
        return Registry.register(registry, id, value);
    }

    public static <T extends BlockEntity> BlockEntityType<T> blockEntityType(BiFunction<BlockPos, BlockState, T> factory, Block... blocks) {
        return FabricBlockEntityTypeBuilder.create(factory::apply, blocks).build();
    }

    public static EntityType<RefocusingCubeEntity> cubeEntityType() {
        return FabricEntityTypeBuilder.create(SpawnGroup.MISC, RefocusingCubeEntity::new)
                .dimensions(EntityDimensions.fixed(0.9F, 0.9F)).fireImmune()
                .trackRangeBlocks(544).trackedUpdateRate(1).forceTrackedVelocityUpdates(true).build();
    }

    public static ItemGroup.Builder itemGroupBuilder() {
        return FabricItemGroup.builder();
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
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register(entries -> {
            for (ItemConvertible item : items) entries.add(item);
        });
    }

    public static <T extends ScreenHandler> ScreenHandlerType<T> screenType(LaserScreenFactory.MenuFactory<T> factory) {
        return new ExtendedScreenHandlerType<>((syncId, inventory, buffer) -> factory.create(syncId, inventory, buffer.readBlockPos()));
    }

    public static void openScreen(PlayerEntity player, NamedScreenHandlerFactory factory) {
        if (!(factory instanceof LaserScreenFactory laser)) {
            player.openHandledScreen(factory);
            return;
        }
        player.openHandledScreen(new ExtendedScreenHandlerFactory() {
            @Override
            public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buffer) {
                laser.writeScreenOpeningData(player, buffer);
            }

            @Override
            public Text getDisplayName() {
                return laser.getDisplayName();
            }

            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity player) {
                return laser.createMenu(syncId, inventory, player);
            }
        });
    }

    public static void registerSettingsReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(net.askcraft.justifylasers.network.SaberTogglePacket.ID, (server, player, handler, buffer, sender) -> server.execute(() -> new net.askcraft.justifylasers.network.SaberTogglePacket().apply(player)));
        ServerPlayNetworking.registerGlobalReceiver(net.askcraft.justifylasers.network.LaserGunControlPacket.ID, (server, player, handler, buffer, sender) -> {
            var packet = new net.askcraft.justifylasers.network.LaserGunControlPacket(buffer);
            server.execute(() -> packet.apply(player));
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PacketByteBuf buffer = PacketByteBufs.create();
            new LaserPolicyPacket(LaserConfig.technicalMode()).write(buffer);
            sender.sendPacket(LaserPolicyPacket.ID, buffer);
        });
        ServerPlayNetworking.registerGlobalReceiver(LaserSettingsPacket.ID, (server, player, handler, buffer, sender) -> {
            LaserSettingsPacket packet = new LaserSettingsPacket(buffer);
            server.execute(() -> packet.apply(player));
        });
    }

    public static void registerEnergy() {
        EnergyStorage.ITEM.registerForItems((stack, context) -> new PlatformTabletEnergy(context),
                net.askcraft.justifylasers.registry.ModIndustry.EXTRATERRESTRIAL_TABLET);
        net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.registerForBlockEntity((machine, side) ->
                machine.kind() == net.askcraft.justifylasers.industry.MachineKind.CRYSTAL_GROWER ? new PlatformWaterStorage(machine) : null, ModBlockEntities.INDUSTRIAL_MACHINE);
        EnergyStorage.SIDED.registerForBlockEntity((machine, side) -> machine.energyPort(), ModBlockEntities.INDUSTRIAL_MACHINE);
        for (String ore : new String[]{"wolframite", "photonic_crystal"})
            for (String variant : new String[]{"", "_buried", "_large"})
            net.fabricmc.fabric.api.biome.v1.BiomeModifications.addFeature(
                    net.fabricmc.fabric.api.biome.v1.BiomeSelectors.foundInOverworld(),
                    net.minecraft.world.gen.GenerationStep.Feature.UNDERGROUND_ORES,
                    RegistryKey.of(net.minecraft.registry.RegistryKeys.PLACED_FEATURE, JustifyLasers.id("ore_" + ore + variant)));

        ResourceConditions.register(JustifyLasers.id("energy_mode"), json -> json.get("enabled").getAsBoolean() == LaserConfig.technicalMode());
        EnergyStorage.SIDED.registerForBlockEntity((emitter, side) -> emitter.isPoweredEmitter() ? emitter.energyPort() : null,
                ModBlockEntities.LASER_EMITTER);
        EnergyStorage.SIDED.registerForBlockEntity((receiver, side) ->
                receiver.kind() == net.askcraft.justifylasers.block.LaserOpticBlock.Kind.ENERGY_RECEIVER
                        && receiver.isEnergyOutput(side) ? receiver.energyPort(side) : null, ModBlockEntities.LASER_OPTIC);
    }

    public static void exportEnergy(net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity receiver) {
        var world = receiver.getWorld();
        int remaining = Math.min(receiver.energy().stored(), LaserConfig.get().maxInput);
        if (!receiver.exportsEnergy() || remaining <= 0) return;
        for (var side : net.minecraft.util.math.Direction.values()) {
            if (!receiver.isEnergyOutput(side) || remaining <= 0) continue;
            BlockPos target = receiver.getPos().offset(side);
            if (!world.isChunkLoaded(target)) continue;
            EnergyStorage storage = EnergyStorage.SIDED.find(world, target, side.getOpposite());
            if (storage == null || !storage.supportsInsertion()) continue;
            try (var transaction = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                long sent = storage.insert(remaining, transaction);
                if (sent > 0 && receiver.energyPort(side).extract(sent, transaction) == sent) {
                    transaction.commit();
                    remaining -= (int) sent;
                }
            }
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
            EnergyStorage storage = EnergyStorage.SIDED.find(world, target, side.getOpposite());
            if (storage == null || !storage.supportsInsertion()) continue;
            try (var transaction = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                long sent = storage.insert(remaining, transaction);
                if (sent > 0 && receiver.energyPort().extract(sent, transaction) == sent) {
                    transaction.commit();
                    remaining -= (int) sent;
                }
            }
        }
    }


    public static void registerExplorationLoot() {
        net.fabricmc.fabric.api.loot.v2.LootTableEvents.MODIFY.register((resources, manager, id, table, source) -> {
            var pool = net.askcraft.justifylasers.industry.TabletLoot.pool(id);
            if (source.isBuiltin() && pool != null) table.pool(pool);
        });
    }

    public static void onEndWorldTick(Consumer<ServerWorld> callback) {
        ServerTickEvents.END_WORLD_TICK.register(callback::accept);
    }

    public static boolean isModLoaded(String id) {
        return FabricLoader.getInstance().isModLoaded(id);
    }

    public static void opticPortsChanged(net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity optic) {
        var world = optic.getWorld();
        world.updateNeighborsAlways(optic.getPos(), optic.getCachedState().getBlock());
        world.updateListeners(optic.getPos(), optic.getCachedState(), optic.getCachedState(), net.minecraft.block.Block.NOTIFY_ALL);
    }

    public static Path configDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }

    private Platform() {
    }

    public static void sendSaberState(ServerPlayerEntity player, net.askcraft.justifylasers.network.SaberStatePacket packet) {
        var buffer = PacketByteBufs.create();
        packet.write(buffer);
        ServerPlayNetworking.send(player, net.askcraft.justifylasers.network.SaberStatePacket.ID, buffer);
    }
}

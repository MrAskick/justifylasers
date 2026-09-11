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
        ResourceConditions.register(JustifyLasers.id("energy_mode"), json -> json.get("enabled").getAsBoolean() == LaserConfig.technicalMode());
        EnergyStorage.SIDED.registerForBlockEntity((emitter, side) -> emitter.isPoweredEmitter() ? emitter.energyPort() : null,
                ModBlockEntities.LASER_EMITTER);
    }

    public static void onEndWorldTick(Consumer<ServerWorld> callback) {
        ServerTickEvents.END_WORLD_TICK.register(callback::accept);
    }

    public static boolean isModLoaded(String id) {
        return FabricLoader.getInstance().isModLoaded(id);
    }

    public static Path configDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }

    private Platform() {
    }
}

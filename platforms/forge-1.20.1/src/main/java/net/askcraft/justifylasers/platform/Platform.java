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
            JustifyLasers.id("main"), () -> "2", "2"::equals, "2"::equals);

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
        CraftingHelper.register(new EnergyModeCondition.Serializer());
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, (AttachCapabilitiesEvent<BlockEntity> event) -> {
            if (!(event.getObject() instanceof LaserEmitterBlockEntity emitter) || !emitter.isPoweredEmitter()) return;
            LazyOptional<PlatformEnergyStorage> energy = LazyOptional.of(emitter::energyPort);
            event.addCapability(JustifyLasers.id("energy"), new ICapabilityProvider() {
                @Override
                public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
                    return capability == ForgeCapabilities.ENERGY ? energy.cast() : LazyOptional.empty();
                }
            });
            event.addListener(energy::invalidate);
        });
    }

    public static boolean isModLoaded(String id) {
        return ModList.get().isLoaded(id);
    }

    public static Path configDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    private Platform() {
    }
}

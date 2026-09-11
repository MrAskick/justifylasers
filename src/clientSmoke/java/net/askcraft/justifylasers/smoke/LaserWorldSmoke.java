package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.client.screen.PoweredLaserEmitterScreen;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.entity.RefocusingCubeEntity;
import net.askcraft.justifylasers.laser.CubeOptics;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.network.LaserSettingsPacket;
import net.askcraft.justifylasers.platform.ClientPlatform;
import net.askcraft.justifylasers.platform.Platform;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModEntities;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

public class LaserWorldSmoke {
    public static final LaserWorldSmoke INSTANCE = new LaserWorldSmoke();
    private boolean started;
    private int loadingTicks;
    private int ticks;
    private RefocusingCubeEntity cube;
    private volatile boolean networkVerified;
    private static final BlockPos EMITTER = new BlockPos(200, 3, 204);

    public void tick(MinecraftClient client) {
        if (client.getOverlay() != null) return;
        if (!started && ++loadingTicks >= 30) {
            started = true;
            GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
            client.options.pauseOnLostFocus = false;
            client.options.setPerspective(Perspective.FIRST_PERSON);
            client.options.hudHidden = true;
            client.options.getViewDistance().setValue(6);
            client.options.getSimulationDistance().setValue(5);
            client.options.getFov().setValue(60);
            SmokeWorldAccess.start(client);
            return;
        }
        if (client.world == null || client.player == null) return;
        int frame = ++ticks;
        if (frame == 50) client.getServer().execute(() -> setScene(client));
        if (frame >= 120 && frame <= 180) {
            double t = (frame - 120) / 60.0D;
            client.getServer().execute(() -> aim(-27 + 54 * t, 10 * Math.sin(t * Math.PI * 2)));
        }
        if (frame == 155) capture(client, "sweep-hot");
        if (frame == 185) capture(client, "trail-hot");
        if (frame == 195) {
            client.options.hudHidden = false;
            client.getServer().execute(() -> {
                ServerPlayerEntity player = player(client);
                player.getInventory().setStack(0, new ItemStack(Items.OBSIDIAN));
                player.getInventory().selectedSlot = 0;
                player.teleport(player.getServerWorld(), 207, 2.1D, 202, 5, -18);
            });
        }
        if (frame == 210) capture(client, "hand-occlusion");
        if (frame == 220) {
            client.options.hudHidden = true;
            client.getServer().execute(() -> {
                LaserEmitterBlockEntity emitter = (LaserEmitterBlockEntity) client.getServer().getOverworld().getBlockEntity(EMITTER);
                emitter.handleButton(0);
                player(client).teleport(player(client).getServerWorld(), 207, 2.1D, 202, 20, 0);
            });
        }
        if (frame == 245) capture(client, "cooling");
        if (frame == 280) capture(client, "cold-trail");
        if (frame == 282) client.getServer().execute(() -> {
            ServerWorld world = client.getServer().getOverworld();
            for (int y = 2; y <= 5; y++) world.setBlockState(new BlockPos(205, y, 207), Blocks.OBSIDIAN.getDefaultState());
        });
        if (frame == 298) capture(client, "world-occlusion");
        if (frame == 300) client.getServer().execute(() -> {
            ServerWorld world = client.getServer().getOverworld();
            for (int y = 2; y <= 5; y++) world.setBlockState(new BlockPos(205, y, 207), Blocks.AIR.getDefaultState());
            world.setBlockState(new BlockPos(204, 3, 209), Blocks.AIR.getDefaultState());
            world.setBlockState(new BlockPos(204, 4, 209), Blocks.AIR.getDefaultState());
            player(client).teleport(world, 207.2D, 2.1D, 207.7D, 72, 0);
        });
        if (frame == 340) capture(client, "grazing-and-removed-blocks");
        if (frame == 370) {
            LoggerFactory.getLogger("justifylasers-scorch-smoke").info("SCORCH_WORLD_SMOKE_PASSED shader={}", IrisCompatibility.isShaderPackInUse());
            client.options.hudHidden = false;
            client.getServer().execute(() -> {
                ServerPlayerEntity player = player(client);
                player.teleport(player.getServerWorld(), 200, 3.1D, 201, 0, 0);
                LaserEmitterBlockEntity emitter = (LaserEmitterBlockEntity) player.getWorld().getBlockEntity(EMITTER);
                emitter.initializeOwner(player);
                Platform.openScreen(player, emitter);
            });
        }
        if (frame == 395) {
            if (!(client.currentScreen instanceof PoweredLaserEmitterScreen)
                    || !(client.player.currentScreenHandler instanceof LaserEmitterScreenHandler handler)
                    || !handler.isPoweredEmitter() || handler.getSlot(0).getStack().isEmpty()) {
                throw new AssertionError("Real emitter menu, properties, or inventory failed to synchronize");
            }
            ClientPlatform.sendSettings(new LaserSettingsPacket(
                    handler.syncId, LaserEmitterScreenHandler.DAMAGE_BUTTON_BASE + 137));
        }
        if (frame == 420) {
            var handler = (LaserEmitterScreenHandler) client.player.currentScreenHandler;
            if (handler.getDamageStep() != 137 || handler.getBeamRange() != 65 || handler.getOwnerName().isEmpty()
                    || !handler.canManageSecurity() || handler.getEnergyCost() != LaserConfig.get().basePerTick + 96) {
                throw new AssertionError("Settings / module range / energy / security did not round-trip through the server");
            }
            capture(client, "network-menu");
            ClientPlatform.sendSettings(new LaserSettingsPacket(
                    handler.syncId + 1, LaserEmitterScreenHandler.DAMAGE_BUTTON_BASE + 100));
            ClientPlatform.sendSettings(new LaserSettingsPacket(handler.syncId, LaserEmitterScreenHandler.RANGE_BUTTON_BASE + 300));
            client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance)
                    .map(ButtonWidget.class::cast)
                    .filter(button -> button.visible && button.getMessage().getString().equals(
                            Text.translatable("gui.justifylasers.powered.modules").getString()))
                    .findFirst().orElseThrow().onPress();
        }
        if (frame == 440) {
            capture(client, "network-energy-menu");
            client.getServer().execute(() -> {
                var handler = (LaserEmitterScreenHandler) player(client).currentScreenHandler;
                if (handler.getDamageStep() != 137 || handler.getBeamRange() != 65) throw new AssertionError("Stale menu or module bypass payload was accepted");
                var emitter = (LaserEmitterBlockEntity) client.getServer().getOverworld().getBlockEntity(EMITTER);
                emitter.getStack(LaserModule.RANGE.slot()).increment(1);
                emitter.getStack(LaserModule.THICKNESS.slot()).increment(1);
                emitter.markDirty();
                networkVerified = true;
            });
        }
        if (frame == 460) {
            if (!networkVerified) throw new AssertionError("Server did not finish network checks");
            var emitter = (LaserEmitterBlockEntity) client.world.getBlockEntity(EMITTER);
            var handler = (LaserEmitterScreenHandler) client.player.currentScreenHandler;
            if (emitter.getBeamRange() != 73 || emitter.getBeamWidthStep() != 103
                    || handler.getBeamRange() != 73 || handler.getSlot(LaserModule.RANGE.slot()).getStack().getCount() != 9
                    || handler.getEnergyCost() != LaserConfig.get().basePerTick + 105) {
                throw new AssertionError("In-place upgrade merges did not synchronize to world rendering and menu");
            }
            LoggerFactory.getLogger("justifylasers-scorch-smoke").info("NETWORK_MENU_SMOKE_PASSED");
            client.player.closeHandledScreen();
            client.getServer().execute(() -> {
                var world = client.getServer().getOverworld();
                var player = player(client);
                player.teleport(world, 205, 2.1, 204, 0, 4);
                for (LaserColor color : LaserColor.values()) {
                    player.getInventory().setStack(color.ordinal(), new ItemStack(ModLaserParts.CRYSTALS.get(color)));
                    var item = new ItemEntity(world, 201 + color.ordinal() * 0.8, 2.7, 207,
                            new ItemStack(ModLaserParts.CRYSTALS.get(color)));
                    item.setVelocity(Vec3d.ZERO);
                    item.setPickupDelay(32767);
                    world.spawnEntity(item);
                }
                player.getInventory().selectedSlot = 0;
                var frameItem = new ItemFrameEntity(world, new BlockPos(204, 3, 208), Direction.NORTH);
                frameItem.setHeldItemStack(new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.CYAN)));
                world.spawnEntity(frameItem);
            });
        }
        if (frame == 470) client.player.getInventory().selectedSlot = 0;
        if (frame == 490) {
            if (!(client.player.getMainHandStack().getItem() instanceof net.askcraft.justifylasers.item.LaserCrystalItem)) {
                throw new AssertionError("Crystal held-item stack did not synchronize");
            }
            capture(client, "crystals-hand-ground-frame");
            client.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
        }
        if (frame == 515) {
            capture(client, "crystals-third-person");
            client.options.setPerspective(Perspective.FIRST_PERSON);
            client.player.getInventory().selectedSlot = 4;
            client.reloadResources();
        }
        if (frame == 550) {
            capture(client, "crystals-reloaded-hand");
            LoggerFactory.getLogger("justifylasers-scorch-smoke").info("CRYSTAL_WORLD_SMOKE_PASSED shader={}", IrisCompatibility.isShaderPackInUse());
            client.scheduleStop();
        }
    }

    private void setScene(MinecraftClient client) {
        ServerWorld world = client.getServer().getOverworld();
        world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, client.getServer());
        world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, client.getServer());
        world.setTimeOfDay(11000);
        world.getEntitiesByClass(RefocusingCubeEntity.class, new Box(195, 0, 195, 215, 10, 215), entity -> true)
                .forEach(RefocusingCubeEntity::discard);
        world.getEntitiesByClass(ItemEntity.class, new Box(195, 0, 195, 215, 10, 215), entity -> true)
                .forEach(ItemEntity::discard);
        world.getEntitiesByClass(ItemFrameEntity.class, new Box(195, 0, 195, 215, 10, 215), entity -> true)
                .forEach(ItemFrameEntity::discard);
        for (int x = 198; x <= 210; x++) {
            for (int z = 200; z <= 211; z++) {
                for (int y = 2; y <= 7; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
                world.setBlockState(new BlockPos(x, 1, z), Blocks.POLISHED_DEEPSLATE.getDefaultState());
            }
        }
        for (int x = 201; x <= 208; x++) {
            for (int y = 2; y <= 5; y++) {
                world.setBlockState(new BlockPos(x, y, 209), (x < 205 ? Blocks.IRON_BLOCK : Blocks.STONE).getDefaultState());
            }
        }
        LaserConfig.applyServerMode(true);
        world.setBlockState(EMITTER, ModBlocks.POWERED_LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST));
        LaserEmitterBlockEntity emitter = (LaserEmitterBlockEntity) world.getBlockEntity(EMITTER);
        emitter.setStack(0, new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.RED)));
        emitter.setStack(LaserModule.SCORCH_MARKS.slot(),
                new ItemStack(ModLaserParts.MODULES.get(LaserModule.SCORCH_MARKS)));
        emitter.setStack(LaserModule.ENTITY_DAMAGE.slot(), new ItemStack(ModLaserParts.MODULES.get(LaserModule.ENTITY_DAMAGE)));
        emitter.setStack(LaserModule.RANGE.slot(), new ItemStack(ModLaserParts.ADVANCED_RANGE_MODULE, 8));
        emitter.setStack(LaserModule.THICKNESS.slot(), new ItemStack(ModLaserParts.MODULES.get(LaserModule.THICKNESS), 32));
        emitter.energy().restore(2_000_000);
        emitter.getPropertyDelegate().set(3, 0);
        emitter.getPropertyDelegate().set(4, 0);
        emitter.getPropertyDelegate().set(8, 0);
        emitter.handleButton(999);
        cube = new RefocusingCubeEntity(ModEntities.REFOCUSING_CUBE, world);
        cube.setNoGravity(true);
        aim(-27, 0);
        world.spawnEntity(cube);
        ServerPlayerEntity player = player(client);
        player.changeGameMode(GameMode.CREATIVE);
        player.teleport(world, 207, 2.1D, 202, 20, 0);
        player.getAbilities().flying = true;
        player.sendAbilitiesUpdate();
    }

    private void aim(double yaw, double pitch) {
        cube.orient((float) yaw, (float) pitch);
        double halfHeight = CubeOptics.frame(Vec3d.ZERO, (float) yaw, (float) pitch).halfExtents().y;
        cube.setPosition(204.5D, 3.5D - halfHeight, 204.5D);
    }

    private static ServerPlayerEntity player(MinecraftClient client) {
        return client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
    }

    private static void capture(MinecraftClient client, String suffix) {
        String name = "scorch-" + (IrisCompatibility.isShaderPackInUse() ? "kappa-" : "vanilla-") + suffix + ".png";
        ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(),
                message -> LoggerFactory.getLogger("justifylasers-scorch-smoke").info(message.getString()));
    }
}

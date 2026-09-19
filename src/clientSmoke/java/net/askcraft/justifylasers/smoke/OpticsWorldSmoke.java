package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.LaserOpticBlock;
import net.askcraft.justifylasers.block.LaserPartBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.askcraft.justifylasers.client.LaserSoundController;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.client.screen.PoweredLaserEmitterScreen;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.entity.RefocusingCubeEntity;
import net.askcraft.justifylasers.laser.LaserBeamNetwork;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.laser.OpticPortMode;
import net.askcraft.justifylasers.network.LaserSettingsPacket;
import net.askcraft.justifylasers.platform.ClientPlatform;
import net.askcraft.justifylasers.platform.Platform;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModEntities;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;

public final class OpticsWorldSmoke {
    private static final BlockPos SOURCE = new BlockPos(200, 3, 204);
    private static final BlockPos MIRROR = new BlockPos(206, 3, 204);
    private static final BlockPos SPLITTER = new BlockPos(206, 3, 208);
    private static int loadingTicks, ticks;
    private static boolean started;

    public static void tick(MinecraftClient client) {
        if (client.getOverlay() != null) return;
        if (!started && ++loadingTicks >= 30) {
            started = true;
            GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1440, 900);
            client.options.pauseOnLostFocus = false;
            client.options.setPerspective(Perspective.FIRST_PERSON);
            client.options.hudHidden = true;
            client.options.getGuiScale().setValue(3);
            client.options.getViewDistance().setValue(6);
            client.options.getSimulationDistance().setValue(5);
            client.options.getFov().setValue(65);
            SmokeWorldAccess.start(client);
            return;
        }
        if (client.world == null || client.player == null) {
            if (started && client.currentScreen != null) {
                client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                        .filter(button -> button.getMessage().getString().equals(Text.translatable("selectWorld.backupWarning.proceed").getString())
                                || button.getMessage().getString().equals(Text.translatable("selectWorld.backupJoinSkipButton").getString()))
                        .findFirst().ifPresent(ButtonWidget::onPress);
            }
            if (started && ++loadingTicks == 150)
                LoggerFactory.getLogger("justifylasers-client-smoke").info("SMOKE_WORLD_LOADING screen={}",
                        client.currentScreen == null ? "none" : client.currentScreen.getClass().getName() + ": " + client.currentScreen.getTitle().getString());
            if (loadingTicks > 1200) throw new AssertionError("Timed out opening the isolated cube-smoke fixture");
            return;
        }
        int frame = ++ticks;
        if (Boolean.getBoolean("justifylasers.smokePrompt7")) { Prompt7WorldSmoke.tick(client, frame); return; }
        if (Boolean.getBoolean("justifylasers.smokePrompt6")) { Prompt6WorldSmoke.tick(client, frame); return; }
        if (Boolean.getBoolean("justifylasers.smokePrompt5")) { Prompt5WorldSmoke.tick(client, frame); return; }
        if (Boolean.getBoolean("justifylasers.smokeLightBridge")) { LightBridgeWorldSmoke.tick(client, frame); return; }
        if (Boolean.getBoolean("justifylasers.smokeAlpha25") || Boolean.getBoolean("justifylasers.smokeAlpha26")) { Alpha25WorldSmoke.tick(client, frame); return; }
        if (Boolean.getBoolean("justifylasers.smokeAlpha24")) { Alpha24WorldSmoke.tick(client, frame); return; }
        if (Boolean.getBoolean("justifylasers.smokeSolar")) { SolarWorldSmoke.tick(client, frame); return; }
        if (Boolean.getBoolean("justifylasers.smokeSaberAudio")) { SaberAudioSmoke.tick(client, frame); return; }
        if (Boolean.getBoolean("justifylasers.smokeOres")) { OreWorldSmoke.tick(client, frame); return; }
        if (Boolean.getBoolean("justifylasers.smokeDualWeapons")) { DualWeaponWorldSmoke.tick(client, frame); return; }
        if (Boolean.getBoolean("justifylasers.smokeTablet")) { TabletWorldSmoke.tick(client, frame); return; }
        if (Boolean.getBoolean("justifylasers.smokeIndustry")) {
            IndustryWorldSmoke.tick(client, frame);
            return;
        }
        if (Boolean.getBoolean("justifylasers.smokeSabers")) {
            SaberWorldSmoke.tick(client, frame);
            return;
        }
        if (Boolean.getBoolean("justifylasers.smokeCubeLens")) {
            if (frame >= 30) CubeLensSmoke.tick(client, frame - 30);
            return;
        }
        if (frame == 50) client.getServer().execute(() -> scene(client));
        if (frame == 130) {
            var emitter = (LaserEmitterBlockEntity) client.world.getBlockEntity(SOURCE);
            if (emitter == null || !emitter.isBeamActive() || LaserBeamNetwork.path(emitter, 1).segments().size() < 6)
                throw new AssertionError("Crystal / mirror / splitter route failed to synchronize");
            for (BlockPos pos : new BlockPos[]{MIRROR, SPLITTER, SPLITTER.south(3)}) {
                if (client.getBlockEntityRenderDispatcher().get(client.world.getBlockEntity(pos)) == null)
                    throw new AssertionError("Missing optic renderer");
            }
            var mirror = (LaserOpticBlockEntity) client.world.getBlockEntity(MIRROR);
            var splitter = (LaserOpticBlockEntity) client.world.getBlockEntity(SPLITTER);
            if (!mirror.emitsShaderLight() || mirror.rgb() != splitter.rgb()
                    || mirror.rgb() == LaserOpticBlockEntity.IDLE_COLOR)
                throw new AssertionError("Mirror did not synchronize the mixed incoming beam color");
            TextureModelSmoke.verifyParticleSprites(client);
            LoggerFactory.getLogger("justifylasers-client-smoke").info("MIRROR_COLOR_SMOKE_PASSED rgb={}", Integer.toHexString(mirror.rgb()));
            capture(client, "overview-day");
            client.getServer().execute(() -> client.getServer().getOverworld().setTimeOfDay(18000));
        }
        if (frame == 160) {
            capture(client, "overview-night");
            client.options.hudHidden = false;
            client.getServer().execute(() -> player(client).teleport(client.getServer().getOverworld(), 198.5, 2.1, 204.5, -90, 6));
        }
        if (frame == 185) {
            capture(client, "jade-status");
            client.getServer().execute(() -> {
                player(client).getInventory().setStack(0, new ItemStack(ModBlocks.CONFIGURATOR));
                player(client).getInventory().selectedSlot = 0;
            });
        }
        if (frame == 205) {
            capture(client, "configurator-preview");
            client.reloadResources();
        }
        if (frame == 255) {
            if (soundSources() < 1 || soundSources() > LaserConfig.get().maxLaserSoundSources)
                throw new AssertionError("Laser sounds did not resume after reload or exceeded the voice limit");
            client.getServer().execute(() -> {
                var player = player(client);
                var emitter = (LaserEmitterBlockEntity) player.getWorld().getBlockEntity(SOURCE);
                emitter.initializeOwner(player);
                Platform.openScreen(player, emitter);
            });
        }
        if (frame == 275) {
            if (!(client.currentScreen instanceof PoweredLaserEmitterScreen)) throw new AssertionError("Emitter menu failed to open");
            client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                    .filter(button -> button.getMessage().getString().equals(Text.translatable("gui.justifylasers.powered.modules").getString()))
                    .findFirst().orElseThrow().onPress();
        }
        if (frame == 290) {
            capture(client, "ten-module-slots");
            client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                    .filter(button -> button.getMessage().getString().isEmpty())
                    .max(java.util.Comparator.comparingInt(ButtonWidget::getX)).orElseThrow().onPress();
            var handler = (LaserEmitterScreenHandler) client.player.currentScreenHandler;
            ClientPlatform.sendSettings(new LaserSettingsPacket(handler.syncId, LaserEmitterScreenHandler.FILTER_BUTTON_BASE + 3));
            ClientPlatform.sendSettings(new LaserSettingsPacket(handler.syncId, LaserEmitterScreenHandler.BUTTON_FILTER_PLAYER, client.player.getGameProfile().getName()));
        }
        if (frame == 310) {
            var handler = (LaserEmitterScreenHandler) client.player.currentScreenHandler;
            if (handler.targetFlags() != 15 || handler.excludedPlayerCount() != 1) throw new AssertionError("Filter settings did not round-trip through networking");
            capture(client, "network-target-filter");
            client.player.closeHandledScreen();
            LaserConfig.get().laserVolume = 0;
        }
        if (frame == 330) {
            if (soundSources() != 0) throw new AssertionError("Zero laser volume did not stop voices");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("OPTICS_WORLD_SMOKE_PASSED shader={} jade={} jei={}",
                    IrisCompatibility.isShaderPackInUse(), Platform.isModLoaded("jade"), Platform.isModLoaded("jei"));
            if (Platform.isModLoaded("jei")) TooltipSmoke.verifyAndOpenGuide();
        }
        if (frame == 350) {
            if (Platform.isModLoaded("jei")) {
                capture(client, "jei-crystal-guide");
                LoggerFactory.getLogger("justifylasers-client-smoke").info("JEI_GUIDE_SMOKE_PASSED");
            }
            client.setScreen(null);
            LaserConfig.get().laserVolume = 0.65;
            LaserConfig.get().maxLaserSoundSources = 4;
            client.getServer().execute(() -> {
                var world = client.getServer().getOverworld();
                for (int i = 0; i < 12; i++) {
                    BlockPos pos = new BlockPos(200 + i % 4, 3, 200 + i / 4);
                    world.setBlockState(pos, ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.UP));
                    var emitter = (LaserEmitterBlockEntity) world.getBlockEntity(pos);
                    emitter.getPropertyDelegate().set(13, 2);
                    emitter.getPropertyDelegate().set(8, 0);
                }
            });
        }
        if (frame >= 370 && frame <= 390 && soundSources() > 4) throw new AssertionError("Twelve emitters exceeded the four-source audio limit");
        if (frame == 390) {
            if (LaserBeamNetwork.paths(client.world, 1).size() < 12 || soundSources() < 1)
                throw new AssertionError("Audio stress scene was not active");
            LaserConfig.get().laserVolume = 0;
        }
        if (frame == 410) {
            if (soundSources() != 0) throw new AssertionError("Audio stress scene did not mute");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("LASER_AUDIO_LIMIT_SMOKE_PASSED emitters=12 limit=4");
            client.options.hudHidden = true;
            client.getServer().execute(() -> portScene(client));
        }
        BlockPos portCenter = new BlockPos(230, 4, 230);
        if (frame == 450) {
            var splitter = (LaserOpticBlockEntity) client.world.getBlockEntity(portCenter);
            if (splitter.outputPorts().size() != 5 || splitter.rgb() != LaserColor.CYAN.rgb())
                throw new AssertionError("Six-port splitter did not synchronize its color and modes");
            for (Direction side : splitter.outputPorts()) {
                var receiver = (LaserOpticBlockEntity) client.world.getBlockEntity(portCenter.offset(side, side == Direction.DOWN ? 2 : 3));
                if (receiver == null || receiver.rgb() != LaserColor.CYAN.rgb() || !receiver.emitsShaderLight())
                    throw new AssertionError("Missing illuminated receiver on " + side);
            }
            capture(client, "six-port-models-active");
            client.getServer().execute(() -> {
                var serverSplitter = (LaserOpticBlockEntity) client.getServer().getOverworld().getBlockEntity(portCenter);
                serverSplitter.setPortMode(Direction.UP, OpticPortMode.DISABLED);
                serverSplitter.setPortMode(Direction.NORTH, OpticPortMode.INPUT);
            });
        }
        if (frame == 480) {
            var splitter = (LaserOpticBlockEntity) client.world.getBlockEntity(portCenter);
            if (splitter.portMode(Direction.UP) != OpticPortMode.DISABLED || splitter.portMode(Direction.NORTH) != OpticPortMode.INPUT
                    || splitter.outputPorts().size() != 3) throw new AssertionError("Port edits did not synchronize");
            capture(client, "six-port-models-configured");
            client.getServer().execute(() -> ((LaserEmitterBlockEntity) client.getServer().getOverworld().getBlockEntity(portCenter.west(4)))
                    .getPropertyDelegate().set(0, 0));
        }
        if (frame == 510) {
            var receiver = (LaserOpticBlockEntity) client.world.getBlockEntity(portCenter.east(3));
            if (receiver.rgb() != LaserOpticBlockEntity.IDLE_COLOR || receiver.emitsShaderLight())
                throw new AssertionError("Receiver retained emission after the beam stopped");
            capture(client, "six-port-models-idle");
            client.options.hudHidden = false;
        }
        if (frame == 560) {
            capture(client, "configurator-model");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("SIX_PORT_MODELS_SMOKE_PASSED");
            client.options.hudHidden = true;
            client.getServer().execute(() -> occlusionScene(client));
        }
        if (frame == 620) {
            capture(client, "foreground-model-occlusion");
            client.getServer().execute(() -> {
                var world = client.getServer().getOverworld();
                world.setBlockState(new BlockPos(277, 3, 284), Blocks.AIR.getDefaultState());
                world.setBlockState(new BlockPos(282, 3, 282), Blocks.AIR.getDefaultState());
                var cube = new RefocusingCubeEntity(ModEntities.REFOCUSING_CUBE, world);
                cube.orient(0, 0);
                cube.setPosition(282.5, 3.5 - cube.getHeight() / 2, 282.5);
                cube.setNoGravity(true);
                world.spawnEntity(cube);
                world.setBlockState(new BlockPos(278, 3, 282), ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST));
                var emitter = (LaserEmitterBlockEntity) world.getBlockEntity(new BlockPos(278, 3, 282));
                emitter.getPropertyDelegate().set(13, 12);
                emitter.getPropertyDelegate().set(2, LaserColor.CYAN.ordinal());
                emitter.getPropertyDelegate().set(8, 0);
            });
        }
        if (frame == 660) {
            var cubes = client.world.getEntitiesByClass(RefocusingCubeEntity.class, new Box(282, 2, 282, 283, 5, 283), cube -> true);
            if (cubes.size() != 1 || !cubes.get(0).isLit()) throw new AssertionError("Cube core fixture is not illuminated");
            capture(client, "cube-white-core");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("BEAM_DEPTH_AND_CORE_SMOKE_CAPTURED shader={}", IrisCompatibility.isShaderPackInUse());
            client.getServer().execute(() -> {
                var world = client.getServer().getOverworld();
                for (int x = 280; x <= 285; x++) {
                    world.setBlockState(new BlockPos(x, 5, 283), Blocks.AIR.getDefaultState());
                    world.setBlockState(new BlockPos(x, 6, 283), Blocks.AIR.getDefaultState());
                }
                world.setBlockState(new BlockPos(281, 5, 283), ModLaserParts.DECORATIONS.get("red_crystal").getDefaultState());
                world.setBlockState(new BlockPos(283, 5, 283), ModLaserParts.DECORATIONS.get("cyan_crystal").getDefaultState());
                player(client).teleport(world, 282.5, 3.1, 280, 0, -15);
            });
        }
        if (frame == 705) {
            capture(client, "crystals-against-sky");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("CRYSTAL_SKY_SMOKE_CAPTURED shader={}", IrisCompatibility.isShaderPackInUse());
            client.getServer().execute(() -> scorchScene(client));
        }
        if (frame == 745) {
            assertScorchAt(new BlockPos(304, 3, 308));
            capture(client, "scorch-beam-contact");
            client.getServer().execute(() -> {
                var world = client.getServer().getOverworld();
                ((LaserEmitterBlockEntity) world.getBlockEntity(new BlockPos(304, 3, 301))).getPropertyDelegate().set(0, 0);
                world.setBlockState(new BlockPos(300, 3, 305), ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST));
                var emitter = (LaserEmitterBlockEntity) world.getBlockEntity(new BlockPos(300, 3, 305));
                emitter.getPropertyDelegate().set(13, 10);
                emitter.getPropertyDelegate().set(7, 4);
                emitter.getPropertyDelegate().set(8, 0);
                player(client).teleport(world, 304.5, 1.88, 302, 0, 0);
            });
        }
        if (frame == 790) {
            assertScorchAt(new BlockPos(304, 3, 308));
            capture(client, "beam-in-front-of-scorch");
            client.getServer().execute(() -> ((LaserEmitterBlockEntity) client.getServer().getOverworld()
                    .getBlockEntity(new BlockPos(300, 3, 305))).getPropertyDelegate().set(0, 0));
        }
        if (frame == 815) {
            assertScorchAt(new BlockPos(304, 3, 308));
            capture(client, "scorch-without-foreground-beam");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("SCORCH_BEAM_ORDER_SMOKE_CAPTURED shader={}", IrisCompatibility.isShaderPackInUse());
        }
        if (frame >= 815) CubeLensSmoke.tick(client, frame - 815);
    }

    private static void assertScorchAt(BlockPos pos) {
        try {
            var field = net.askcraft.justifylasers.client.render.LaserScorchRenderer.class.getDeclaredField("MARKS");
            field.setAccessible(true);
            var marks = (net.askcraft.justifylasers.laser.LaserScorchMarks) field.get(null);
            if (marks.marks().stream().noneMatch(mark -> mark.patch().position().equals(pos)))
                throw new AssertionError("Missing client-side surface mark at " + pos);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void scorchScene(MinecraftClient client) {
        var world = client.getServer().getOverworld();
        for (int x = 299; x <= 311; x++) for (int z = 299; z <= 309; z++) {
            world.setBlockState(new BlockPos(x, 1, z), Blocks.POLISHED_DEEPSLATE.getDefaultState());
            for (int y = 2; y <= 8; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
        }
        for (int x = 300; x <= 309; x++) for (int y = 2; y <= 6; y++)
            world.setBlockState(new BlockPos(x, y, 308), Blocks.IRON_BLOCK.getDefaultState());
        world.setBlockState(new BlockPos(304, 3, 301), ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.SOUTH));
        var emitter = (LaserEmitterBlockEntity) world.getBlockEntity(new BlockPos(304, 3, 301));
        emitter.getPropertyDelegate().set(13, 10);
        emitter.getPropertyDelegate().set(7, 16);
        emitter.getPropertyDelegate().set(8, 0);
        player(client).teleport(world, 306, 2, 303, 18, 1);
    }

    private static void occlusionScene(MinecraftClient client) {
        var world = client.getServer().getOverworld();
        world.setTimeOfDay(9000);
        world.getOtherEntities(null, new Box(274, 0, 275, 291, 12, 289), entity -> !(entity instanceof net.minecraft.entity.player.PlayerEntity))
                .forEach(net.minecraft.entity.Entity::discard);
        for (int x = 275; x <= 290; x++) for (int z = 276; z <= 288; z++) {
            world.setBlockState(new BlockPos(x, 1, z), Blocks.POLISHED_DEEPSLATE.getDefaultState());
            for (int y = 2; y <= 10; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
        }
        world.setBlockState(new BlockPos(277, 3, 284), ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST));
        var emitter = (LaserEmitterBlockEntity) world.getBlockEntity(new BlockPos(277, 3, 284));
        emitter.getPropertyDelegate().set(13, 11);
        emitter.getPropertyDelegate().set(7, 8);
        emitter.getPropertyDelegate().set(8, 0);
        world.setBlockState(new BlockPos(288, 3, 284), Blocks.IRON_BLOCK.getDefaultState());
        world.setBlockState(new BlockPos(282, 3, 282), ModBlocks.LASER_MIRROR.getDefaultState().with(LaserOpticBlock.FACING, Direction.NORTH));
        ((LaserOpticBlockEntity) world.getBlockEntity(new BlockPos(282, 3, 282))).aim(new Vec3d(0, 0, -1));
        player(client).getInventory().clear();
        player(client).teleport(world, 282.5, 2, 278, 0, 1.5F);
    }

    private static void portScene(MinecraftClient client) {
        var world = client.getServer().getOverworld();
        world.setTimeOfDay(9000);
        BlockPos center = new BlockPos(230, 4, 230);
        for (int x = 224; x <= 236; x++) for (int z = 225; z <= 235; z++) {
            world.setBlockState(new BlockPos(x, 1, z), Blocks.POLISHED_DEEPSLATE.getDefaultState());
            for (int y = 2; y <= 10; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
        }
        world.setBlockState(center.west(4), ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST));
        var emitter = (LaserEmitterBlockEntity) world.getBlockEntity(center.west(4));
        emitter.getPropertyDelegate().set(13, 12);
        emitter.getPropertyDelegate().set(2, LaserColor.CYAN.ordinal());
        emitter.getPropertyDelegate().set(7, 8);
        emitter.getPropertyDelegate().set(8, 0);
        world.setBlockState(center, ModBlocks.BEAM_SPLITTER.getDefaultState().with(LaserOpticBlock.FACING, Direction.WEST));
        for (Direction side : Direction.values()) {
            if (side == Direction.WEST) continue;
            world.setBlockState(center.offset(side, side == Direction.DOWN ? 2 : 3), ModBlocks.ENERGY_RECEIVER.getDefaultState()
                    .with(LaserOpticBlock.FACING, side.getOpposite()));
        }
        var player = player(client);
        player.getInventory().setStack(0, new ItemStack(ModBlocks.CONFIGURATOR));
        player.getInventory().selectedSlot = 0;
        player.teleport(world, 234, 5.3, 224.5, 35, 18);
    }

    private static int soundSources() {
        try {
            var loops = LaserSoundController.class.getDeclaredField("LOOPS");
            var transients = LaserSoundController.class.getDeclaredField("TRANSIENTS");
            loops.setAccessible(true);
            transients.setAccessible(true);
            return ((Map<?, ?>) loops.get(null)).size() + ((Collection<?>) transients.get(null)).size();
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void scene(MinecraftClient client) {
        var world = client.getServer().getOverworld();
        world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, client.getServer());
        world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, client.getServer());
        world.setTimeOfDay(9000);
        world.getOtherEntities(null, new Box(196, 0, 196, 220, 15, 220), entity -> !(entity instanceof net.minecraft.entity.player.PlayerEntity))
                .forEach(net.minecraft.entity.Entity::discard);
        for (int x = 197; x <= 218; x++) for (int z = 197; z <= 218; z++) {
            world.setBlockState(new BlockPos(x, 1, z), Blocks.POLISHED_DEEPSLATE.getDefaultState());
            for (int y = 2; y <= 9; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
        }
        for (int x = 198; x < 216; x++) for (int y = 2; y < 7; y++)
            world.setBlockState(new BlockPos(x, y, 213), Blocks.IRON_BLOCK.getDefaultState());
        LaserConfig.applyServerMode(true);
        world.setBlockState(SOURCE.down(), Blocks.POLISHED_ANDESITE.getDefaultState());
        world.setBlockState(SOURCE, ModBlocks.POWERED_LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST));
        var emitter = (LaserEmitterBlockEntity) world.getBlockEntity(SOURCE);
        emitter.setStack(0, new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.RED)));
        emitter.setStack(LaserModule.RANGE.slot(), new ItemStack(ModLaserParts.ADVANCED_RANGE_MODULE, 8));
        emitter.setStack(LaserModule.THICKNESS.slot(), new ItemStack(ModLaserParts.MODULES.get(LaserModule.THICKNESS), 32));
        emitter.setStack(LaserModule.ENTITY_DAMAGE.slot(), new ItemStack(ModLaserParts.MODULES.get(LaserModule.ENTITY_DAMAGE)));
        emitter.setStack(LaserModule.TARGET_FILTER.slot(), new ItemStack(ModLaserParts.MODULES.get(LaserModule.TARGET_FILTER)));
        emitter.energy().restore(2_000_000);
        emitter.getPropertyDelegate().set(3, 0);
        emitter.getPropertyDelegate().set(4, 0);
        emitter.getPropertyDelegate().set(8, 0);
        world.setBlockState(SOURCE.east(3).down(), Blocks.POLISHED_ANDESITE.getDefaultState());
        world.setBlockState(SOURCE.east(3), ModLaserParts.DECORATIONS.get("blue_crystal").getDefaultState().with(LaserPartBlock.MIXING, true));
        world.setBlockState(MIRROR.down(), Blocks.POLISHED_ANDESITE.getDefaultState());
        world.setBlockState(MIRROR, ModBlocks.LASER_MIRROR.getDefaultState().with(LaserOpticBlock.FACING, Direction.UP));
        ((LaserOpticBlockEntity) world.getBlockEntity(MIRROR)).aim(new Vec3d(1, 0, -1));
        world.setBlockState(SPLITTER, ModBlocks.BEAM_SPLITTER.getDefaultState().with(LaserOpticBlock.FACING, Direction.NORTH));
        for (Direction face : new Direction[]{Direction.EAST, Direction.WEST, Direction.SOUTH}) {
            world.setBlockState(SPLITTER.offset(face, 3), ModBlocks.ENERGY_RECEIVER.getDefaultState().with(LaserOpticBlock.FACING, face.getOpposite()));
        }
        world.setBlockState(new BlockPos(201, 4, 212), ModLaserParts.DECORATIONS.get("green_crystal").getDefaultState().with(LaserPartBlock.MOUNT, Direction.NORTH));
        world.setBlockState(new BlockPos(204, 6, 211), ModLaserParts.DECORATIONS.get("cyan_crystal").getDefaultState().with(LaserPartBlock.MOUNT, Direction.DOWN));
        world.setBlockState(new BlockPos(204, 7, 211), Blocks.IRON_BLOCK.getDefaultState());
        var player = player(client);
        player.changeGameMode(GameMode.CREATIVE);
        player.teleport(world, 211, 5, 199, 28, 17);
        player.getInventory().clear();
        player.getAbilities().flying = true;
        player.sendAbilitiesUpdate();
    }

    private static ServerPlayerEntity player(MinecraftClient client) {
        return client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
    }

    private static void capture(MinecraftClient client, String name) {
        String prefix = IrisCompatibility.isShaderPackInUse() ? "optics-kappa-" : "optics-vanilla-";
        ScreenshotRecorder.saveScreenshot(client.runDirectory, prefix + name + ".png", client.getFramebuffer(), text -> { });
    }

    private OpticsWorldSmoke() { }
}

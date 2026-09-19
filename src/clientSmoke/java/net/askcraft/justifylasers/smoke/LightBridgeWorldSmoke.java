package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.LightBridgeBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.bridge.LightBridgeNetwork;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import org.slf4j.LoggerFactory;

final class LightBridgeWorldSmoke {
    private static final double DECK_Y = 9 + net.askcraft.justifylasers.bridge.BridgeOrientation.HALF_HEIGHT;
    private static final BlockPos ORIGIN = new BlockPos(1000, 9, 1000);
    private static final BlockPos GROWER = new BlockPos(1035, 9, 1000);
    private static final BlockPos MODULE_EMITTER = new BlockPos(1050, 9, 1000);
    private static final BlockPos LIFT_EMITTER = new BlockPos(1080, 9, 1000);
    private static double walkingStart;
    private static double previousX;
    private static double liftedFrom, loweredFrom;

    static void tick(MinecraftClient client, int frame) {
        if (frame == 1) {
            String language = System.getProperty("justifylasers.smokeLanguage", "en_us");
            if (!client.getLanguageManager().getLanguage().equals(language)) {
                client.getLanguageManager().setLanguage(language);
                client.options.language = language;
                client.reloadResources();
            }
            client.getServer().submit(() -> { setup(client); return true; }).join();
            camera(client,new Vec3d(1008,11,995),new Vec3d(1002,9.3,1002));
        }
        if (frame == 90) {
            lithiumCollisionRegression(client);
            client.getServer().submit(() -> { inventoryRegression(client); return true; }).join();
            var particle = client.getBlockRenderManager().getModels().getModelParticleSprite(ModBlocks.LIGHT_BRIDGE.getDefaultState());
            if (particle.getContents().getId().getPath().equals("missingno")) throw new AssertionError("Bridge destruction particle is missing from the block atlas");
            for(int i=0;i<3;i++) {
                var pos=ORIGIN.south(i*7);
                var field=LightBridgeNetwork.at(client.world,pos);
                if(field==null||field.width()!=i+1||!field.active()) throw new AssertionError("Client bridge snapshot missing width="+(i+1));
            }
            capture(client,"one-wide"); camera(client,new Vec3d(1006,10.3,1003),new Vec3d(1000.5,9.4,1007.7));
        }
        if (frame == 130) {capture(client,"two-wide");camera(client,new Vec3d(1007,10.5,1010),new Vec3d(1001,9.5,1015));}
        if (frame == 170) {capture(client,"three-wide");camera(client,new Vec3d(1006,6.8,1006),new Vec3d(1007,10,1008));}
        if (frame == 210) {
            capture(client,"against-sky");
            client.getServer().execute(() -> {
                var player=player(client);
                player.changeGameMode(GameMode.SURVIVAL);
                player.getAbilities().flying=false; player.sendAbilitiesUpdate();
                player.teleport(client.getServer().getOverworld(),1003,DECK_Y,1000.5,-90,0);
            });
        }
        if (frame == 240) {walkingStart=previousX=client.player.getX();client.options.forwardKey.setPressed(true);}
        if (frame > 243 && frame < 285) {
            double x = client.player.getX();
            if (x - previousX < .08 || Math.abs(client.player.getY()-DECK_Y) > .002)
                throw new AssertionError("Bridge movement stutters at tick " + frame + ": " + client.player.getPos() + " dx=" + (x-previousX));
            previousX = x;
        }
        if (frame == 285) {
            client.options.forwardKey.setPressed(false);
            if(client.player.getX()<walkingStart+3||Math.abs(client.player.getY()-DECK_Y)>.08) throw new AssertionError("Client movement did not stay on light bridge: "+client.player.getPos());
            capture(client,"walking");
        }
        if (frame == 360) {
            if(client.player==null||Math.abs(client.player.getY()-DECK_Y)>.08) throw new AssertionError("Server lost standing player / flying check");
            client.getServer().submit(() -> {
                var player=player(client);
                if(!player.isOnGround()||Math.abs(player.getY()-DECK_Y)>.08) throw new AssertionError("Server/client disagree about support");
                client.getServer().getOverworld().removeBlock(ORIGIN.west(3),false);
                return true;
            }).join();
        }
        if (frame == 385) {
            if(client.player.getY()>8.8) throw new AssertionError("Disconnected bridge still supports player");
            capture(client,"power-off");
            client.getServer().submit(() -> { setupGrowth(client); return true; }).join();
            camera(client, new Vec3d(1039, 10.1, 997), Vec3d.of(GROWER).add(1, 1, 1));
        }
        if (frame == 430) {
            var machine = (net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity)client.world.getBlockEntity(GROWER);
            var mixed = new net.askcraft.justifylasers.laser.LightMixture();
            mixed.add(LaserColor.RED.rgb(), 1); mixed.add(LaserColor.BLUE.rgb(), 1);
            if (machine.lightFlux() <= 480_000 || machine.lightRgb() != mixed.rgb()) {
                String server = client.getServer().submit(() -> {
                    var world = client.getServer().getOverworld();
                    var actual = (net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity) world.getBlockEntity(GROWER);
                    var sources = java.util.stream.Stream.of(GROWER.west(5), GROWER.west(2).north(3)).map(pos -> {
                        var source = (LaserEmitterBlockEntity) world.getBlockEntity(pos);
                        return pos + "/" + source.isBeamActive() + "/" + source.opticalBudget() + "/" + source.energy().stored();
                    }).toList();
                    return actual.lightFlux() + "/" + Integer.toHexString(actual.lightRgb()) + "/" + actual.status() + "; sources=" + sources;
                }).join();
                capture(client, "grower-sync-failure");
                throw new AssertionError("Touching combiners/grower mixed light not synchronized: " + machine.lightFlux() + "/" + Integer.toHexString(machine.lightRgb()) + "; server=" + server);
            }
            capture(client, "grower-mixed");
            client.getServer().execute(() -> net.askcraft.justifylasers.platform.Platform.openScreen(player(client),
                    (net.askcraft.justifylasers.platform.LaserScreenFactory)client.getServer().getOverworld().getBlockEntity(GROWER)));
        }
        if (frame == 455) {
            if (!(client.currentScreen instanceof net.askcraft.justifylasers.client.screen.IndustrialMachineScreen screen)
                    || screen.getScreenHandler().rate() != 480_000 || screen.getScreenHandler().lightFlux() <= 480_000)
                throw new AssertionError("Growth GUI must synchronize the reference and actual flux independently");
            capture(client, "grower-menu"); client.player.closeHandledScreen();
            client.getServer().execute(() -> client.getServer().getOverworld().removeBlock(GROWER.west(2).north(3), false));
        }
        if (frame == 485) {
            var machine = (net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity)client.world.getBlockEntity(GROWER);
            if (machine.lightRgb() != LaserColor.RED.rgb() || machine.lightFlux() >= 480_000 || machine.lightFlux() == 0)
                throw new AssertionError("Removing one input must change both growth color and speed");
            capture(client, "grower-red");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("LIGHT_BRIDGE_WORLD_OK shader={} walking/snapshot/widths/flight-check/power-off/particles/adjacent-combiners/colored-growth",IrisCompatibility.isShaderPackInUse());
            client.getServer().submit(() -> { setupModules(client); return true; }).join();
            camera(client, new Vec3d(1054, 10, 995), Vec3d.of(MODULE_EMITTER).add(3, .5, .5));
        }
        if (frame == 525) {
            capture(client, "world-modules");
            camera(client, new Vec3d(1050, 9, 998), Vec3d.ofCenter(MODULE_EMITTER));
            client.getServer().execute(() -> net.askcraft.justifylasers.platform.Platform.openScreen(player(client),
                    (net.askcraft.justifylasers.platform.LaserScreenFactory) client.getServer().getOverworld().getBlockEntity(MODULE_EMITTER)));
        }
        if (frame == 545) { capture(client, "emitter-main"); named(client, "gui.justifylasers.powered.modules"); }
        if (frame == 560) { capture(client, "emitter-modules"); settings(client, 0); }
        if (frame == 575) {
            var screen = (net.askcraft.justifylasers.client.screen.PoweredLaserEmitterScreen) client.currentScreen;
            if (!screen.getScreenHandler().getSlot(1).isEnabled() || !screen.getScreenHandler().getSlot(2).isEnabled()
                    || screen.getScreenHandler().getSlot(3).isEnabled()) throw new AssertionError("Mining slots are on the wrong page");
            capture(client, "mining-slots"); named(client, "gui.justifylasers.powered.storage");
        }
        if (frame == 590) {
            var screen = (net.askcraft.justifylasers.client.screen.PoweredLaserEmitterScreen) client.currentScreen;
            if (screen.getScreenHandler().slots.stream().filter(slot -> slot.isEnabled()).count() != 45) throw new AssertionError("Storage must show 9 + 36 slots");
            capture(client, "storage"); back(client);
            client.getServer().submit(() -> {
                var emitter = (LaserEmitterBlockEntity) client.getServer().getOverworld().getBlockEntity(MODULE_EMITTER);
                emitter.setStack(6, new ItemStack(ModLaserParts.MODULES.get(LaserModule.ENTITY_DAMAGE)));
                return true;
            }).join();
            named(client, "gui.justifylasers.powered.modules");
        }
        if (frame == 597) settings(client, 0);
        if (frame == 605) { capture(client, "damage-ignition-slot"); back(client); settings(client, 1); }
        if (frame == 620) { named(client, "gui.justifylasers.filter.mob_list"); }
        if (frame == 635) {
            capture(client, "registry-filter"); client.player.closeHandledScreen();
            client.getServer().execute(() -> net.askcraft.justifylasers.platform.Platform.openScreen(player(client),
                    (net.askcraft.justifylasers.platform.LaserScreenFactory) client.getServer().getOverworld().getBlockEntity(MODULE_EMITTER.east(2))));
        }
        if (frame == 655) {
            if (!(client.currentScreen instanceof net.askcraft.justifylasers.client.screen.LaserModuleScreen)) throw new AssertionError("Placed module GUI failed to open");
            capture(client, "world-module-menu"); client.player.closeHandledScreen();
            LoggerFactory.getLogger("justifylasers-client-smoke").info("ALPHA30_MODULE_GUI_OK shader={} slots/storage/damage-ignition/registry/world-module", IrisCompatibility.isShaderPackInUse());
            client.getServer().submit(() -> { setupLift(client); return true; }).join();
        }
        if (frame == 685) liftedFrom = client.player.getY();
        if (frame == 785) {
            if (client.player.getY() < liftedFrom + 2) throw new AssertionError("Connected player is not rising in the lift beam: " + client.player.getPos());
            capture(client, "lift"); loweredFrom = client.player.getY();
            client.getServer().submit(() -> {
                var emitter = (LaserEmitterBlockEntity) client.getServer().getOverworld().getBlockEntity(LIFT_EMITTER);
                emitter.setStack(LaserModule.ENTITY_LOWER.slot(), new ItemStack(ModLaserParts.MODULES.get(LaserModule.ENTITY_LOWER)));
                return true;
            }).join();
        }
        if (frame == 855) {
            if (client.player.getY() > loweredFrom - 1) throw new AssertionError("Connected player is not descending in the lower beam: " + client.player.getPos());
            client.getServer().submit(() -> {
                var player = player(client);
                if (Math.abs(player.getY() - client.player.getY()) > .5 || !net.askcraft.justifylasers.laser.LaserBeamEffects.supports(player))
                    throw new AssertionError("Server/client disagree about beam movement/support");
                return true;
            }).join();
            capture(client, "lower");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("ALPHA30_MOVEMENT_OK shader={} lift/lower/flight-check", IrisCompatibility.isShaderPackInUse());
            client.getServer().execute(() -> {
                var player = player(client);
                player.teleport(client.getServer().getOverworld(), 1003, DECK_Y, 1007.5, -90, 0);
                player.setVelocity(Vec3d.ZERO);
            });
        }
        if (frame == 900) {
            if (Math.abs(client.player.getY() - DECK_Y) > .03) throw new AssertionError("Not standing on bridge before logout");
            client.world.disconnect();
            client.disconnect();
            client.setScreen(new net.minecraft.client.gui.screen.TitleScreen());
            SmokeWorldAccess.start(client);
        }
        if (frame > 900 && frame < 1020 && Math.abs(client.player.getY() - DECK_Y) > .04)
            throw new AssertionError("Rejoined player fell through bridge: frame=" + frame + " pos=" + client.player.getPos());
        if (frame == 1020) {
            capture(client, "rejoined-bridge");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("ALPHA30_BRIDGE_REJOIN_OK shader={}", IrisCompatibility.isShaderPackInUse());
            client.getServer().execute(() -> {
                var pos = ORIGIN.south(7).west(3);
                var world = client.getServer().getOverworld();
                world.setBlockState(pos, ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST));
                net.askcraft.justifylasers.platform.Platform.openScreen(player(client), (LaserEmitterBlockEntity) world.getBlockEntity(pos));
            });
        }
        if (frame == 1025) org.lwjgl.glfw.GLFW.glfwSetCursorPos(client.getWindow().getHandle(), 0, 0);
        if (frame == 1040) {
            if (!(client.currentScreen instanceof net.askcraft.justifylasers.client.screen.LaserEmitterScreen screen)
                    || screen.getScreenHandler().luminousFlux() != 1_000_000) throw new AssertionError("Creative LM menu did not synchronize");
            capture(client, "creative-flux"); client.player.closeHandledScreen();
            client.getServer().execute(() -> {
                var world = client.getServer().getOverworld();
                var pos = MODULE_EMITTER.east(2);
                player(client).teleport(world, pos.getX(), 9, 998, 0, 10);
                world.setBlockState(pos, ModLaserParts.DECORATIONS.get(LaserModule.BLOCK_DESTRUCTION.id()).getDefaultState());
                var mining = (net.askcraft.justifylasers.block.entity.LaserPartBlockEntity) world.getBlockEntity(pos);
                mining.setStack(9, new ItemStack(ModLaserParts.MODULES.get(LaserModule.BLOCK_DROPS)));
                mining.setStack(10, new ItemStack(ModLaserParts.MODULES.get(LaserModule.SILK_TOUCH)));
                mining.setStack(11, new ItemStack(ModLaserParts.MODULES.get(LaserModule.IGNITION)));
                mining.setStack(12, new ItemStack(ModLaserParts.MODULES.get(LaserModule.BLOCK_COLLECTION)));
                net.askcraft.justifylasers.platform.Platform.openScreen(player(client), mining);
            });
        }
        if (frame == 1060) {
            if (!(client.currentScreen instanceof net.askcraft.justifylasers.client.screen.LaserModuleScreen screen)
                    || screen.getScreenHandler().slots.stream().filter(slot -> slot.isEnabled()).count() != 40)
                throw new AssertionError("Placed Mining must show four nested upgrade slots and the player inventory");
            capture(client, "world-mining-slots"); named(client, "gui.justifylasers.powered.storage");
        }
        if (frame == 1080) {
            var screen = (net.askcraft.justifylasers.client.screen.LaserModuleScreen) client.currentScreen;
            if (screen.getScreenHandler().slots.stream().filter(slot -> slot.isEnabled()).count() != 45)
                throw new AssertionError("Placed Mining storage must show nine slots and the player inventory");
            capture(client, "world-mining-storage");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("ALPHA30_NESTED_MINING_OK shader={} upgrades/storage/creative-lm", IrisCompatibility.isShaderPackInUse());
            client.player.closeHandledScreen();
            client.getServer().execute(() -> {
                var world = client.getServer().getOverworld();
                var pos = MODULE_EMITTER.south(2).east(5);
                player(client).teleport(world, pos.getX(), pos.getY(), pos.getZ() - 2, 0, 20);
                world.setBlockState(pos, ModBlocks.LASER_TURRET.getDefaultState());
                var turret = (net.askcraft.justifylasers.block.entity.LaserTurretBlockEntity) world.getBlockEntity(pos);
                turret.setStack(0, new ItemStack(ModBlocks.LASER_GUN));
                turret.setStack(1, new ItemStack(ModLaserParts.MODULES.get(LaserModule.TARGET_FILTER)));
                net.askcraft.justifylasers.platform.Platform.openScreen(player(client), turret);
            });
        }
        if (frame == 1100) { capture(client, "turret-main"); named(client, "gui.justifylasers.powered.filter"); }
        if (frame == 1120) { capture(client, "turret-filter"); named(client, "gui.justifylasers.filter.mob_list"); }
        if (frame == 1140) {
            capture(client, "turret-types"); client.player.closeHandledScreen();
            camera(client, new Vec3d(1054, 9, 1002), new Vec3d(1051.5, 9.6, 1003.5));
        }
        if (frame == 1160) {
            capture(client, "motor-and-collector");
            client.getServer().submit(() -> { setupRolledBridges(client); return true; }).join();
            camera(client, new Vec3d(1009, 15, 983), new Vec3d(1001, 12, 991));
        }
        if (frame == 1200) {
            for (int index = 0; index < 4; index++) {
                var span = LightBridgeNetwork.at(client.world, verticalFixture(index));
                if (span == null || !span.active() || span.rolled() != (index % 2 != 0)
                        || span.facing() != (index < 2 ? Direction.UP : Direction.DOWN))
                    throw new AssertionError("Vertical bridge roll was not synchronized: " + index);
            }
            capture(client, "vertical-roll");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("ALPHA31_CLIENT_OK shader={} turret/motor/collector", IrisCompatibility.isShaderPackInUse());
            client.getServer().submit(() -> { setupAlpha32(client); return true; }).join();
            camera(client, new Vec3d(1017, 10.5, 992), new Vec3d(1017, 10.7, 985));
        }
        if (frame == 1240) {
            for (var corner : new BlockPos[]{new BlockPos(1015, 9, 985), new BlockPos(1018, 9, 985),
                    new BlockPos(1018, 12, 985), new BlockPos(1015, 12, 985)}) {
                var sections = LightBridgeNetwork.fields(client.world).stream().filter(field -> field.origin().equals(corner)).toList();
                if (sections.size() != 2 || sections.stream().anyMatch(field -> !field.active() || field.connections() != 3))
                    throw new AssertionError("Corner tunnel is not connected on the client: " + corner);
            }
            capture(client, "corner-tunnel");
            camera(client, new Vec3d(1025, 10.1, 991), new Vec3d(1024.5, 9.5, 985));
        }
        if (frame == 1270) {
            capture(client, "diagonal");
            camera(client, new Vec3d(1020, 10, 987), new Vec3d(1020.8, 9.6, 983.5));
        }
        if (frame == 1300) {
            capture(client, "thickness-tiers");
            radialHeld = true;
            client.options.hudHidden = false;
            client.setScreen(new net.askcraft.justifylasers.client.screen.ConfiguratorRadialScreen(net.minecraft.util.Hand.MAIN_HAND) {
                @Override protected boolean selectionHeld() { return radialHeld; }
                @Override public void render(net.minecraft.client.gui.DrawContext context, int mx, int my, float delta) {
                    float scale = Math.max(.4F, Math.min(1, Math.min((height - 44) / 310F, (width - 20) / 310F)));
                    super.render(context, Math.round(width / 2F - 66 * scale), Math.round((height - 28) / 2F + 38 * scale), delta);
                }
            });
        }
        if (frame == 1320) {
            if (!(client.currentScreen instanceof net.askcraft.justifylasers.client.screen.ConfiguratorRadialScreen))
                throw new AssertionError("Radial menu did not stay open while held");
            capture(client, "configurator-radial");
            radialHeld = false;
            client.currentScreen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT, 0, 0);
        }
        if (frame == 1330) {
            if (client.currentScreen != null || net.askcraft.justifylasers.item.LaserConfiguratorItem.mode(client.player.getMainHandStack()) != 4)
                throw new AssertionError("Release did not select the hovered mode, synchronize it, and close the menu");
            capture(client, "configurator-violet");
            client.getServer().submit(() -> {
                if (net.askcraft.justifylasers.item.LaserConfiguratorItem.mode(player(client).getMainHandStack()) != 4)
                    throw new AssertionError("Server did not accept configurator mode");
                var viewer = player(client);
                viewer.changeGameMode(GameMode.SURVIVAL); viewer.getAbilities().flying = false; viewer.sendAbilitiesUpdate();
                viewer.teleport(client.getServer().getOverworld(), 1024.5, 11, 986.4, 0, 0);
                return true;
            }).join();
        }
        if (frame == 1360) { diagonalY = client.player.getY(); previousZ = client.player.getZ(); client.options.forwardKey.setPressed(true); }
        if (frame > 1363 && frame < 1380) {
            if (Math.abs(client.player.getY() - diagonalY) > .01 || client.player.getZ() - previousZ < .07)
                throw new AssertionError("Diagonal bridge stutter / lost support: " + client.player.getPos());
            previousZ = client.player.getZ();
        }
        if (frame == 1380) {
            client.options.forwardKey.setPressed(false);
            capture(client, "diagonal-walking");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("ALPHA32_CLIENT_OK shader={} corner/diagonal-walking/radial-release/energy-capabilities/thickness-II", IrisCompatibility.isShaderPackInUse());
            client.scheduleStop();
        }
    }

    private static boolean radialHeld;
    private static double diagonalY, previousZ;

    private static void setupAlpha32(MinecraftClient client) {
        var world = client.getServer().getOverworld();
        NativeInventoryProbe.verifyConfiguratorEnergy();
        for (BlockPos p : BlockPos.iterate(1014, 8, 982, 1027, 15, 995)) world.setBlockState(p, Blocks.AIR.getDefaultState());
        for (BlockPos p : BlockPos.iterate(1014, 8, 982, 1027, 8, 995)) world.setBlockState(p, Blocks.SMOOTH_STONE.getDefaultState());
        int[][] corners = {{1015, 9, 0}, {1018, 9, 2}, {1018, 12, 4}, {1015, 12, 6}};
        for (var c : corners) world.setBlockState(new BlockPos(c[0], c[1], 985), ModBlocks.CORNER_LIGHT_BRIDGE.getDefaultState()
                .with(LightBridgeBlock.FACING, Direction.SOUTH).with(LightBridgeBlock.ROTATION, c[2]));
        for (int i = 0; i < 2; i++) {
            world.setBlockState(new BlockPos(1016 + i, 9, 985), ModBlocks.LIGHT_BRIDGE.getDefaultState().with(LightBridgeBlock.FACING, Direction.SOUTH));
            world.setBlockState(new BlockPos(1016 + i, 12, 985), ModBlocks.LIGHT_BRIDGE.getDefaultState().with(LightBridgeBlock.FACING, Direction.SOUTH).with(LightBridgeBlock.MOUNT, Direction.DOWN));
            world.setBlockState(new BlockPos(1015, 10 + i, 985), ModBlocks.LIGHT_BRIDGE.getDefaultState().with(LightBridgeBlock.FACING, Direction.SOUTH).with(LightBridgeBlock.MOUNT, Direction.EAST));
            world.setBlockState(new BlockPos(1018, 10 + i, 985), ModBlocks.LIGHT_BRIDGE.getDefaultState().with(LightBridgeBlock.FACING, Direction.SOUTH).with(LightBridgeBlock.MOUNT, Direction.WEST));
        }
        world.setBlockState(new BlockPos(1015, 9, 983), ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.SOUTH));
        world.setBlockState(new BlockPos(1024, 9, 985), ModBlocks.LIGHT_BRIDGE.getDefaultState().with(LightBridgeBlock.FACING, Direction.SOUTH).with(LightBridgeBlock.ROTATION, 1));
        world.setBlockState(new BlockPos(1024, 9, 983), ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.SOUTH));
        for (BlockPos p : BlockPos.iterate(1014, 9, 994, 1025, 13, 994)) world.setBlockState(p, Blocks.STONE_BRICKS.getDefaultState());
        world.setBlockState(new BlockPos(1020, 9, 983), ModLaserParts.DECORATIONS.get("thickness_module").getDefaultState()
                .with(net.askcraft.justifylasers.block.LaserPartBlock.FACING, Direction.SOUTH));
        world.setBlockState(new BlockPos(1022, 9, 983), ModLaserParts.DECORATIONS.get("advanced_thickness_module").getDefaultState()
                .with(net.askcraft.justifylasers.block.LaserPartBlock.FACING, Direction.SOUTH));
        var stack = new ItemStack(ModBlocks.CONFIGURATOR);
        ((net.askcraft.justifylasers.item.LaserConfiguratorItem) stack.getItem()).writeEnergy(stack, 15_000);
        player(client).setStackInHand(net.minecraft.util.Hand.MAIN_HAND, stack);
    }

    private static BlockPos verticalFixture(int index) { return new BlockPos(995 + index * 3, index < 2 ? 10 : 15, 991); }

    private static void setupRolledBridges(MinecraftClient client) {
        var world = client.getServer().getOverworld();
        var viewer = player(client);
        viewer.changeGameMode(GameMode.CREATIVE);
        viewer.getAbilities().flying = true;
        viewer.sendAbilitiesUpdate();
        for (int index = 0; index < 4; index++) {
            var pos = verticalFixture(index);
            var forward = index < 2 ? Direction.UP : Direction.DOWN;
            for (int y = 6; y <= 19; y++) world.setBlockState(new BlockPos(pos.getX(), y, pos.getZ()), Blocks.AIR.getDefaultState());
            world.setBlockState(pos, ModBlocks.LIGHT_BRIDGE.getDefaultState().with(LightBridgeBlock.FACING, forward)
                    .with(LightBridgeBlock.MOUNT, forward).with(LightBridgeBlock.ROLLED, index % 2 != 0));
            world.setBlockState(pos.offset(forward, 4), Blocks.STONE_BRICKS.getDefaultState());
            var source = pos.offset(forward.getOpposite(), 2);
            world.setBlockState(source, ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, forward));
        }
    }

    private static void inventoryRegression(MinecraftClient client) {
        var world = client.getServer().getOverworld();
        BlockPos pos = ORIGIN.north(4).up(3);
        world.setBlockState(pos, ModBlocks.POWERED_LASER_EMITTER.getDefaultState());
        var emitter = (LaserEmitterBlockEntity) world.getBlockEntity(pos);
        NativeInventoryProbe.verify(emitter, new ItemStack(net.minecraft.item.Items.STONE), false);
        emitter.initializeOwner(player(client)); emitter.togglePrivacy(player(client));
        NativeInventoryProbe.verify(emitter, new ItemStack(net.minecraft.item.Items.STONE), true);
        emitter.togglePrivacy(player(client));
        NativeInventoryProbe.verify(emitter, new ItemStack(ModLaserParts.ADVANCED_THICKNESS_MODULE), false);
        world.setBlockState(pos, ModLaserParts.DECORATIONS.get("block_destruction_module").getDefaultState());
        NativeInventoryProbe.verify(world.getBlockEntity(pos), new ItemStack(net.minecraft.item.Items.STONE), false);
        world.setBlockState(pos, ModLaserParts.DECORATIONS.get("entity_damage_module").getDefaultState());
        NativeInventoryProbe.verify(world.getBlockEntity(pos), new ItemStack(ModLaserParts.MODULES.get(LaserModule.IGNITION)), false);
        world.setBlockState(pos, ModBlocks.LASER_TURRET.getDefaultState());
        NativeInventoryProbe.verify(world.getBlockEntity(pos), new ItemStack(ModBlocks.LASER_GUN), false);
        world.setBlockState(pos, net.askcraft.justifylasers.registry.ModIndustry.MACHINES.get(net.askcraft.justifylasers.industry.MachineKind.FUEL_GENERATOR).getDefaultState());
        NativeInventoryProbe.verify(world.getBlockEntity(pos), new ItemStack(net.minecraft.item.Items.COAL), false);
        world.removeBlock(pos, false);
        LoggerFactory.getLogger("justifylasers-client-smoke").info("ALPHA31_INVENTORIES_OK six-sides/simulation/extraction/privacy");
    }

    private static void lithiumCollisionRegression(MinecraftClient client) {
        Class<?> sweeper = null;
        for (String name : new String[]{"me.jellysquid.mods.lithium.common.entity.movement.ChunkAwareBlockCollisionSweeper",
                "net.caffeinemc.mods.lithium.common.entity.movement.ChunkAwareBlockCollisionSweeper"}) {
            try { sweeper = Class.forName(name); break; } catch (ClassNotFoundException ignored) { }
        }
        if (sweeper == null) return;
        var previous = LightBridgeNetwork.fields(client.world);
        var dimension = client.world.getRegistryKey().getValue();
        BlockPos origin = ORIGIN.up(30);
        var first = new net.askcraft.justifylasers.bridge.LightBridgeSpan(origin, Direction.EAST, 1, 4, 0xFFFFFF, 8000);
        var second = new net.askcraft.justifylasers.bridge.LightBridgeSpan(origin.south(), Direction.EAST, 1, 4, 0xFFFFFF, 8000);
        var query = first.bounds().union(second.bounds()).expand(.1);
        try {
            LightBridgeNetwork.receive(client.world, new net.askcraft.justifylasers.network.LightBridgePacket(dimension, java.util.List.of(first, second)));
            var constructor = sweeper.getDeclaredConstructor(net.minecraft.world.World.class, net.minecraft.entity.Entity.class, net.minecraft.util.math.Box.class);
            constructor.setAccessible(true);
            var collect = sweeper.getDeclaredMethod("collectAll"); collect.setAccessible(true);
            for (boolean terrain : new boolean[]{false, true}) {
                if (terrain) client.world.setBlockState(origin.east(2), Blocks.STONE.getDefaultState());
                int expected = terrain ? 3 : 2;
                Object bulk = constructor.newInstance(client.world, client.player, query);
                if (((java.util.List<?>) collect.invoke(bulk)).size() != expected) throw new AssertionError("Lithium lost/duplicated bulk shapes");
                Object partial = constructor.newInstance(client.world, client.player, query);
                var iterator = (java.util.Iterator<?>) partial;
                if (!iterator.hasNext()) throw new AssertionError("Missing prefetched bridge shape");
                if (((java.util.List<?>) collect.invoke(partial)).size() != expected) throw new AssertionError("Prefetch corrupted bulk collisions");
                Object remaining = constructor.newInstance(client.world, client.player, query);
                ((java.util.Iterator<?>) remaining).next();
                if (((java.util.List<?>) collect.invoke(remaining)).size() != expected - 1) throw new AssertionError("Partial iteration corrupted collision list");
                client.world.setBlockState(origin.east(2), Blocks.AIR.getDefaultState());
            }
            LoggerFactory.getLogger("justifylasers-client-smoke").info("ALPHA31_LITHIUM_OK bridge-only/mixed/prefetched/partial");
        } catch (ReflectiveOperationException exception) { throw new AssertionError("Native Lithium bridge regression", exception); }
        finally {
            client.world.setBlockState(origin.east(2), Blocks.AIR.getDefaultState());
            LightBridgeNetwork.receive(client.world, new net.askcraft.justifylasers.network.LightBridgePacket(dimension, previous));
        }
    }

    private static void setup(MinecraftClient client) {
        var world=client.getServer().getOverworld();
        world.setTimeOfDay(6000);world.setWeather(0,0,false,false);
        world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,world.getServer());
        world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,world.getServer());
        for(int x=994;x<=1025;x++)for(int z=995;z<=1020;z++)for(int y=4;y<=13;y++) {
            var block=y==4?Blocks.DEEPSLATE_TILES:y<9&&(x<=1000||x>=1023)?Blocks.STONE_BRICKS:Blocks.AIR;
            world.setBlockState(new BlockPos(x,y,z),block.getDefaultState(),3);
        }
        for(int i=0;i<3;i++) {
            var origin=ORIGIN.south(i*7);
            for(int width=0;width<=i;width++) world.setBlockState(origin.south(width),ModBlocks.LIGHT_BRIDGE.getDefaultState().with(LightBridgeBlock.FACING,Direction.EAST),3);
            var pos=origin.west(3);
            world.setBlockState(pos,ModBlocks.POWERED_LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING,Direction.EAST),3);
            var source=(LaserEmitterBlockEntity)world.getBlockEntity(pos);
            source.setStack(0,new ItemStack(ModLaserParts.CRYSTALS.get(i==0?LaserColor.CYAN:i==1?LaserColor.BLUE:LaserColor.RED)));
            source.setStack(LaserModule.RANGE.slot(),new ItemStack(ModLaserParts.ADVANCED_RANGE_MODULE,2));
            source.energy().restore(1_000_000);
            for(int z=0;z<=i;z++) world.setBlockState(origin.east(15).south(z),Blocks.STONE_BRICKS.getDefaultState(),3);
        }
        var player=player(client);
        world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, new net.minecraft.util.math.Box(993, 3, 994, 1026, 15, 1021), e -> true)
                .forEach(net.minecraft.entity.Entity::discard);
        player.changeGameMode(GameMode.CREATIVE);player.getAbilities().flying=true;player.sendAbilitiesUpdate();
        player.getInventory().clear();player.getInventory().setStack(0,new ItemStack(ModBlocks.LIGHT_BRIDGE));
    }

    private static void setupGrowth(MinecraftClient client) {
        var world = client.getServer().getOverworld();
        var player = player(client);
        player.changeGameMode(GameMode.CREATIVE); player.getAbilities().flying = true; player.sendAbilitiesUpdate();
        for (int x = 1028; x <= 1040; x++) for (int z = 995; z <= 1004; z++) for (int y = 8; y <= 12; y++)
            world.setBlockState(new BlockPos(x,y,z), (y == 8 ? Blocks.STONE_BRICKS : Blocks.AIR).getDefaultState());
        for (var pos : net.askcraft.justifylasers.industry.ChamberStructure.positions(GROWER))
            world.setBlockState(pos, net.askcraft.justifylasers.registry.ModIndustry.MACHINES.get(net.askcraft.justifylasers.industry.MachineKind.CRYSTAL_GROWER).getDefaultState());
        var grower = (net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity)world.getBlockEntity(GROWER);
        net.askcraft.justifylasers.industry.ChamberStructure.form(grower);
        NativeInventoryProbe.verify(grower, new ItemStack(net.askcraft.justifylasers.registry.ModIndustry.RAW_PHOTONIC_CRYSTAL), false);
        grower.setStack(0, new ItemStack(net.askcraft.justifylasers.registry.ModIndustry.RAW_PHOTONIC_CRYSTAL));
        grower.setStack(1, new ItemStack(net.minecraft.item.Items.QUARTZ, 2)); grower.fillWater(1000, false);
        for (int distance : new int[]{1,2}) world.setBlockState(GROWER.west(distance), ModBlocks.BEAM_COMBINER.getDefaultState()
                .with(net.askcraft.justifylasers.block.LaserOpticBlock.FACING, Direction.EAST));
        for (int i=0;i<2;i++) {
            BlockPos pos = i==0 ? GROWER.west(5) : GROWER.west(2).north(3);
            world.setBlockState(pos, ModBlocks.POWERED_LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, i==0 ? Direction.EAST : Direction.SOUTH));
            var source = (LaserEmitterBlockEntity)world.getBlockEntity(pos);
            source.setStack(0,new ItemStack(ModLaserParts.CRYSTALS.get(i==0 ? LaserColor.RED : LaserColor.BLUE)));
            source.setStack(LaserModule.RANGE.slot(), new ItemStack(ModLaserParts.ADVANCED_RANGE_MODULE, 32));
            source.energy().restore(1_000_000);
        }
        world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, new net.minecraft.util.math.Box(1028, 8, 995, 1041, 13, 1005), e -> true)
                .forEach(net.minecraft.entity.Entity::discard);
    }

    private static void setupModules(MinecraftClient client) {
        var world = client.getServer().getOverworld();
        for (int x = 1048; x <= 1062; x++) for (int z = 997; z <= 1004; z++) for (int y = 8; y <= 12; y++)
            world.setBlockState(new BlockPos(x, y, z), (y == 8 ? Blocks.DEEPSLATE_TILES : Blocks.AIR).getDefaultState());
        world.setBlockState(MODULE_EMITTER, ModBlocks.POWERED_LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST));
        var emitter = (LaserEmitterBlockEntity) world.getBlockEntity(MODULE_EMITTER);
        emitter.setStack(0, new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.CYAN)));
        emitter.setStack(7, new ItemStack(ModLaserParts.ADVANCED_RANGE_MODULE, 48));
        for (var module : new LaserModule[]{LaserModule.BLOCK_DESTRUCTION, LaserModule.BLOCK_DROPS, LaserModule.SILK_TOUCH, LaserModule.BLOCK_COLLECTION, LaserModule.IGNITION, LaserModule.TARGET_FILTER})
            emitter.setStack(module.slot(), new ItemStack(ModLaserParts.MODULES.get(module)));
        emitter.setStack(10, new ItemStack(net.minecraft.item.Items.STONE, 24)); emitter.setStack(13, new ItemStack(net.minecraft.item.Items.DIAMOND, 3));
        emitter.energy().restore(1_000_000); emitter.initializeOwner(player(client));
        world.setBlockState(MODULE_EMITTER.east(2), ModLaserParts.DECORATIONS.get("entity_lift_module").getDefaultState());
        world.setBlockState(MODULE_EMITTER.east(4), ModLaserParts.DECORATIONS.get("entity_heal_module").getDefaultState());
        world.setBlockState(MODULE_EMITTER.east(6), ModLaserParts.DECORATIONS.get("entity_lower_module").getDefaultState());
        world.setBlockState(MODULE_EMITTER.east(8), Blocks.STONE_BRICKS.getDefaultState());
        world.setBlockState(MODULE_EMITTER.south(2), net.askcraft.justifylasers.registry.ModIndustry.RAW_PHOTONIC_CRYSTAL instanceof net.minecraft.item.BlockItem item
                ? item.getBlock().getDefaultState() : ModLaserParts.DECORATIONS.get("raw_photonic_crystal").getDefaultState());
        world.setBlockState(MODULE_EMITTER.south(2).east(2), ModLaserParts.DECORATIONS.get("raw_wolframite").getDefaultState());
        world.setBlockState(MODULE_EMITTER.south(3), ModLaserParts.DECORATIONS.get("electric_motor").getDefaultState());
        world.setBlockState(MODULE_EMITTER.south(3).east(2), ModLaserParts.DECORATIONS.get("block_collection_module").getDefaultState());
        var lootPos = MODULE_EMITTER.south(4);
        world.setBlockState(lootPos, Blocks.CHEST.getDefaultState());
        ((net.minecraft.block.entity.ChestBlockEntity) world.getBlockEntity(lootPos)).setStack(0, new ItemStack(net.minecraft.item.Items.DIAMOND, 2));
        if (!net.askcraft.justifylasers.laser.MiningCollection.breakBlock(world, lootPos, true, false, emitter)
                || emitter.getStack(13).getCount() != 5) throw new AssertionError("Native collection hook lost the container contents");
    }

    private static void setupLift(MinecraftClient client) {
        var world = client.getServer().getOverworld();
        for (int x = 1078; x <= 1082; x++) for (int z = 998; z <= 1002; z++) for (int y = 8; y <= 24; y++)
            world.setBlockState(new BlockPos(x, y, z), (y == 8 ? Blocks.STONE_BRICKS : Blocks.AIR).getDefaultState());
        world.setBlockState(LIFT_EMITTER, ModBlocks.POWERED_LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.UP));
        var emitter = (LaserEmitterBlockEntity) world.getBlockEntity(LIFT_EMITTER);
        emitter.setStack(0, new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.CYAN)));
        emitter.setStack(LaserModule.RANGE.slot(), new ItemStack(ModLaserParts.ADVANCED_RANGE_MODULE, 2));
        emitter.setStack(LaserModule.ENTITY_LIFT.slot(), new ItemStack(ModLaserParts.MODULES.get(LaserModule.ENTITY_LIFT)));
        emitter.energy().restore(1_000_000);
        var player = player(client);
        player.changeGameMode(GameMode.SURVIVAL); player.getAbilities().flying = false; player.sendAbilitiesUpdate();
        player.teleport(world, LIFT_EMITTER.getX() + .5, LIFT_EMITTER.getY() + 1, LIFT_EMITTER.getZ() + .5, 0, 35);
        player.setVelocity(Vec3d.ZERO);
    }

    private static void named(MinecraftClient client, String key) {
        var text = net.minecraft.text.Text.translatable(key).getString();
        var button = client.currentScreen.children().stream().filter(net.minecraft.client.gui.widget.ButtonWidget.class::isInstance)
                .map(net.minecraft.client.gui.widget.ButtonWidget.class::cast).filter(widget -> widget.getMessage().getString().equals(text)).findFirst().orElseThrow();
        button.onPress();
    }
    private static void settings(MinecraftClient client, int index) {
        var buttons = client.currentScreen.children().stream().filter(net.minecraft.client.gui.widget.ButtonWidget.class::isInstance)
                .map(net.minecraft.client.gui.widget.ButtonWidget.class::cast).filter(widget -> widget.getMessage().getString().isEmpty())
                .sorted(java.util.Comparator.comparingInt(net.minecraft.client.gui.widget.ButtonWidget::getX)).toList();
        if (buttons.size() != 2 || !buttons.get(index).active) throw new AssertionError("Module settings are not available");
        buttons.get(index).onPress();
    }
    private static void back(MinecraftClient client) { client.currentScreen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0); }

    private static void camera(MinecraftClient client,Vec3d position,Vec3d target) {
        client.getServer().execute(() -> {
            Vec3d direction=target.subtract(position.add(0,1.62,0));
            float yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));
            float pitch=(float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z)));
            player(client).teleport(client.getServer().getOverworld(),position.x,position.y,position.z,yaw,pitch);
        });
    }
    private static ServerPlayerEntity player(MinecraftClient client) {return client.getServer().getPlayerManager().getPlayer(client.player.getUuid());}
    private static void capture(MinecraftClient client,String name) {ScreenshotRecorder.saveScreenshot(client.runDirectory,"alpha32-bridge-"+name+".png",client.getFramebuffer(),text -> {});}
    private LightBridgeWorldSmoke() { }
}

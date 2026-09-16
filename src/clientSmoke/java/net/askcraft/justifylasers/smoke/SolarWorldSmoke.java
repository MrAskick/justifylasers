package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.block.LaserComponentBlock;
import net.askcraft.justifylasers.block.LaserOpticBlock;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.askcraft.justifylasers.block.entity.SolarConcentratorBlockEntity;
import net.askcraft.justifylasers.client.ClientSettings;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.industry.SolarStructure;
import net.askcraft.justifylasers.laser.LaserBeamNetwork;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import org.slf4j.LoggerFactory;

final class SolarWorldSmoke {
    private static final BlockPos ORIGIN = new BlockPos(602, 2, 602);
    private static final BlockPos SOURCE = ORIGIN.add(1, 1, -1);
    private static final BlockPos RECEIVER = SOURCE.north(6);

    static void tick(MinecraftClient client, int frame) {
        if (frame == 10 && !client.getLanguageManager().getLanguage().equals("en_us")) {
            client.getLanguageManager().setLanguage("en_us"); client.options.language = "en_us";
            client.reloadResources();
        }
        if (frame == 30) client.getServer().execute(() -> scene(client));
        if (frame == 140) {
            verify(client, true);
            capture(client, "overview");
            camera(client, new Vec3d(601.2, 5.6, 599.4), new Vec3d(603.5, 4, 603.5));
        }
        if (frame == 180) {
            capture(client, "funnel");
            camera(client, new Vec3d(600.8, 2.8, 597.4), new Vec3d(603.5, 3.5, 602));
        }
        if (frame == 220) {
            capture(client, "resonator");
            client.getServer().execute(() -> client.getServer().getOverworld().setTimeOfDay(18000));
        }
        if (frame == 255) {
            verify(client, false);
            capture(client, "night");
            client.getServer().execute(() -> {
                var world = client.getServer().getOverworld();
                world.setTimeOfDay(6000);
                for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) world.setBlockState(ORIGIN.add(x, 4, z), Blocks.STONE.getDefaultState());
            });
        }
        if (frame == 300) {
            verify(client, false);
            capture(client, "roof-shadow");
            client.getServer().execute(() -> {
                var world = client.getServer().getOverworld();
                for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) world.setBlockState(ORIGIN.add(x, 4, z), Blocks.AIR.getDefaultState());
                world.setTimeOfDay(7000);
            });
            camera(client, new Vec3d(611.7, 5.8, 596.8), new Vec3d(603.5, 4, 603.5));
        }
        if (frame == 350) {
            verify(client, true);
            ClientSettings.get().solarLightShafts = false;
        }
        if (frame == 360) {
            capture(client, "shafts-disabled");
            ClientSettings.get().solarLightShafts = true;
            client.reloadResources();
        }
        if (frame == 420) {
            verify(client, true);
            capture(client, "reloaded");
            client.getServer().execute(() -> {
                var player = player(client);
                player.teleport(client.getServer().getOverworld(), 601.5, 3, 599.5, -35, 8);
                var source = (SolarConcentratorBlockEntity) client.getServer().getOverworld().getBlockEntity(SOURCE);
                source.initializeOwner(player);
                net.askcraft.justifylasers.platform.Platform.openScreen(player, source);
            });
        }
        if (frame == 450) {
            if (!menu(client).enabled() || menu(client).luminousFlux() <= 0) throw new AssertionError("Solar menu was not synchronized");
            capture(client, "gui-main"); press(client, 1);
        }
        if (frame == 465) { capture(client, "gui-security"); press(client, 3); }
        if (frame == 480) {
            if (!menu(client).isPrivate()) throw new AssertionError("Privacy button did not reach the server");
            press(client, 2);
        }
        if (frame == 490) { capture(client, "gui-redstone"); press(client, 3); }
        if (frame == 505) {
            if (menu(client).luminousFlux() != 0) throw new AssertionError("HIGH mode did not stop the generator");
            capture(client, "gui-redstone-waiting"); press(client, 3);
        }
        if (frame == 520) {
            if (menu(client).luminousFlux() <= 0) throw new AssertionError("LOW mode did not start the generator");
            press(client, 3); press(client, 0);
        }
        if (frame == 530) {
            client.getLanguageManager().setLanguage("ru_ru"); client.options.language = "ru_ru";
            client.reloadResources();
        }
        if (frame == 575) { capture(client, "gui-ru-main"); press(client, 1); }
        if (frame == 590) { capture(client, "gui-ru-security"); press(client, 2); }
        if (frame == 605) { capture(client, "gui-ru-redstone"); press(client, 0); }
        if (frame == 620) { press(client, 0); }
        if (frame == 635) {
            if (menu(client).enabled() || menu(client).luminousFlux() != 0) throw new AssertionError("GUI power switch did not stop the beam");
            capture(client, "gui-ru-off"); press(client, 0);
        }
        if (frame == 650) {
            if (!menu(client).enabled() || menu(client).luminousFlux() <= 0) throw new AssertionError("GUI power switch did not restore the beam");
            client.player.closeHandledScreen();
            client.getServer().execute(() -> client.getServer().getOverworld().breakBlock(ORIGIN, false));
        }
        if (frame == 675) {
            var source = (SolarConcentratorBlockEntity) client.world.getBlockEntity(SOURCE);
            if (source.formed() || source.isBeamActive()) throw new AssertionError("Broken multiblock remained visible/active");
            capture(client, "dismantled");
            camera(client, new Vec3d(605.3, 2.9, 609.2), new Vec3d(600.5, 2.5, 608.5));
        }
        if (frame == 710) {
            capture(client, "components");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("SOLAR_WORLD_SMOKE_PASSED shader={} gui=true privacy=true redstone=true lumens=true languages=en,ru", IrisCompatibility.isShaderPackInUse());
            client.scheduleStop();
        }
    }

    private static void scene(MinecraftClient client) {
        var world = client.getServer().getOverworld();
        world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, client.getServer());
        world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, client.getServer());
        world.setTimeOfDay(7000);
        world.setWeather(0, 6000, false, false);
        for (int x = 591; x <= 616; x++) for (int z = 591; z <= 615; z++) {
            world.setBlockState(new BlockPos(x, 1, z), Blocks.POLISHED_ANDESITE.getDefaultState());
            for (int y = 2; y < 12; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
        }
        for (var cell : SolarStructure.BODY) world.setBlockState(ORIGIN.add(cell.offset()), ModIndustry.COMPONENT_BLOCKS.get(cell.component()).getDefaultState());
        for(var cell:SolarStructure.RESONATORS)world.setBlockState(ORIGIN.add(cell.offset()),
                ModIndustry.COMPONENT_BLOCKS.get(cell.component()).getDefaultState().with(LaserComponentBlock.FACING,SolarStructure.resonatorFacing(cell)));
        world.setBlockState(SOURCE, ModIndustry.COMPONENT_BLOCKS.get("optical_resonator").getDefaultState().with(LaserComponentBlock.FACING, Direction.NORTH));
        var source = (SolarConcentratorBlockEntity) world.getBlockEntity(SOURCE);
        if (!SolarStructure.form(source)) throw new AssertionError("Solar fixture did not form");
        world.setBlockState(RECEIVER, ModBlocks.ENERGY_RECEIVER.getDefaultState().with(LaserOpticBlock.FACING, Direction.SOUTH));
        for (int i = 0; i < ModIndustry.COMPONENT_IDS.size(); i++)
            world.setBlockState(new BlockPos(596+i*2, 2, 609), ModIndustry.COMPONENT_BLOCKS.get(ModIndustry.COMPONENT_IDS.get(i)).getDefaultState());
        world.setBlockState(new BlockPos(607, 2, 609), ModIndustry.LASER_ABSORBING_GLASS.getDefaultState());
        var player = player(client);
        player.changeGameMode(GameMode.CREATIVE);
        player.getInventory().clear();
        player.getAbilities().flying = true;
        player.sendAbilitiesUpdate();
        camera(client, new Vec3d(611.7, 5.8, 596.8), new Vec3d(603.5, 4, 603.5));
    }
    private static void verify(MinecraftClient client, boolean active) {
        var source = (SolarConcentratorBlockEntity) client.world.getBlockEntity(SOURCE);
        if (source == null || !source.formed() || source.isBeamActive() != active)
            throw new AssertionError("Solar state did not synchronize: " + (source == null ? "missing" : source.status()));
        if (client.getBlockEntityRenderDispatcher().get(source) == null) throw new AssertionError("No solar renderer");
        if (LaserBeamNetwork.paths(client.world, 1).containsKey(SOURCE) != active) throw new AssertionError("Solar route did not follow daylight");
        if (active && ((LaserOpticBlockEntity)client.world.getBlockEntity(RECEIVER)).energy().stored() <= 0)
            throw new AssertionError("No energy reached the receiver");
    }
    private static void camera(MinecraftClient client, Vec3d position, Vec3d target) {
        client.getServer().execute(() -> {
            Vec3d direction = target.subtract(position.add(0, 1.62, 0));
            float yaw = (float)Math.toDegrees(Math.atan2(-direction.x, direction.z));
            float pitch = (float)-Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z)));
            player(client).teleport(client.getServer().getOverworld(), position.x, position.y, position.z, yaw, pitch);
        });
    }
    private static ServerPlayerEntity player(MinecraftClient client) { return client.getServer().getPlayerManager().getPlayer(client.player.getUuid()); }
    private static net.askcraft.justifylasers.screen.SolarConcentratorScreenHandler menu(MinecraftClient client) {
        if (!(client.currentScreen instanceof net.askcraft.justifylasers.client.screen.SolarConcentratorScreen screen)) throw new AssertionError("Solar screen did not open");
        return screen.getScreenHandler();
    }
    private static void press(MinecraftClient client, int index) {
        client.currentScreen.children().stream().filter(net.minecraft.client.gui.widget.ButtonWidget.class::isInstance)
                .map(net.minecraft.client.gui.widget.ButtonWidget.class::cast).skip(index).findFirst().orElseThrow().onPress();
    }
    private static void capture(MinecraftClient client, String name) {
        ScreenshotRecorder.saveScreenshot(client.runDirectory, "solar-" + (IrisCompatibility.isShaderPackInUse() ? "kappa-" : "vanilla-") + name + ".png",
                client.getFramebuffer(), text -> { });
    }
    private SolarWorldSmoke() { }
}

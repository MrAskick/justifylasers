package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.client.screen.IndustrialMachineScreen;
import net.askcraft.justifylasers.industry.MachineKind;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.platform.Platform;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import org.slf4j.LoggerFactory;

final class IndustryWorldSmoke {
    private static final BlockPos[] POSITIONS = {new BlockPos(400,2,402),new BlockPos(401,2,402),new BlockPos(403,2,402),new BlockPos(406,2,402)};
    private static boolean calibrationCaptured;
    private static int menus, assemblyStarted;

    static void tick(MinecraftClient client, int tick) {
        if (tick == 30) {
            String language = System.getProperty("justifylasers.smokeLanguage", "en_us");
            if (!client.getLanguageManager().getLanguage().equals(language)) {
                client.getLanguageManager().setLanguage(language);
                client.options.language = language;
                client.reloadResources();
            }
            client.options.hudHidden = true;
            client.getServer().execute(() -> scene(client));
        }
        if (tick == 90) {
            if (machine(client, 1).energy().stored() <= 0) throw new AssertionError("Generator did not power its neighbor through the loader energy API");
            capture(client, "workshop");
        }
        for (int index = 0; index < 4; index++) {
            int menu = index;
            if (tick == 110 + index * 60) {
                client.options.hudHidden = false;
                client.getServer().execute(() -> Platform.openScreen(player(client), (IndustrialMachineBlockEntity) client.getServer().getOverworld().getBlockEntity(POSITIONS[menu])));
            }
            if (tick == 130 + index * 60) {
                if (!(client.currentScreen instanceof IndustrialMachineScreen screen) || screen.getScreenHandler().kind() != MachineKind.values()[index])
                    throw new AssertionError("Machine menu kind/slots did not synchronize: " + index);
                var handler = screen.getScreenHandler();
                int extra = index == 2 ? 2 : index == 3 ? 1 : 0;
                if (handler.slots.stream().filter(slot -> slot.isEnabled()).count() != 37 + MachineKind.values()[index].inputs() + extra)
                    throw new AssertionError("Wrong machine slot count");
                capture(client, "menu-" + MachineKind.values()[index].id());
                screen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).findFirst().orElseThrow().onPress();
                menus++;
            }
            if (tick == 143 + index * 60) {
                var screen = (IndustrialMachineScreen) client.currentScreen;
                if (screen.getScreenHandler().enabled()) throw new AssertionError("Server did not apply machine pause button");
                screen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).findFirst().orElseThrow().onPress();
                client.player.closeHandledScreen();
                client.options.hudHidden = true;
            }
        }
        if (tick == 326) client.getServer().execute(() -> player(client).teleport(client.getServer().getOverworld(), 404.3, 3.0, 399.0, 0, 8));
        if (tick == 347) capture(client, "growing-crystal");
        if (tick == 355) {
            client.getServer().execute(() -> {
                var assembler = (IndustrialMachineBlockEntity) client.getServer().getOverworld().getBlockEntity(POSITIONS[3]);
                assembler.setStack(0, new ItemStack(ModIndustry.COMPONENTS.get("reinforced_laser_housing")));
                assembler.setStack(1, new ItemStack(ModIndustry.COMPONENTS.get("optical_resonator")));
                assembler.setStack(2, new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.CYAN)));
                assembler.setStack(3, new ItemStack(ModIndustry.COMPONENTS.get("energy_core")));
                assembler.setStack(IndustrialMachineBlockEntity.BLUEPRINT, new ItemStack(ModIndustry.BLUEPRINTS.get("powered_laser_emitter")));
                assembler.energy().restore(assembler.energy().capacity());
                player(client).teleport(client.getServer().getOverworld(), 407.7, 3.0, 398.5, 8, 10);
            });
            assemblyStarted = tick;
        }
        if (tick > 355 && tick < 805 && tick % 20 == 0) client.getServer().execute(() -> {
            var assembler = (IndustrialMachineBlockEntity) client.getServer().getOverworld().getBlockEntity(POSITIONS[3]);
            assembler.energy().restore(assembler.energy().capacity());
        });
        if (tick == 440 || tick == 580 || tick == 715) capture(client, "assembly-" + tick);
        if (tick > assemblyStarted + 100 && assemblyStarted != 0 && machine(client, 3).calibration() > 0 && !calibrationCaptured) {
            capture(client, "calibration-beam");
            calibrationCaptured = true;
        }
        if (tick == 810) {
            var assembler = machine(client, 3);
            if (!calibrationCaptured || !assembler.getStack(4).isOf(ModBlocks.POWERED_LASER_EMITTER_ITEM))
                throw new AssertionError("Assembly did not finish with a synchronized output and calibration beam");
            client.reloadResources();
            client.getServer().execute(() -> player(client).teleport(client.getServer().getOverworld(), 400.5, 3.4, 408.2, 180, 16));
        }
        if (tick == 830) capture(client, "ore-materials");
        if (Platform.isModLoaded("jei")) IndustryJeiSmoke.tick(client, tick);
        if (tick == 870) {
            capture(client, "reloaded");
            client.options.hudHidden = false;
            client.getServer().execute(() -> {
                var assembler = (IndustrialMachineBlockEntity) client.getServer().getOverworld().getBlockEntity(POSITIONS[3]);
                if (assembler.enabled()) assembler.toggle();
                for (int slot = 0; slot < 5; slot++) assembler.setStack(slot, ItemStack.EMPTY);
                int slot = 9;
                for (var entry : ModIndustry.COMPONENTS.entrySet()) {
                    player(client).getInventory().setStack(slot++, new ItemStack(entry.getValue()));
                    player(client).getInventory().setStack(slot + 8, new ItemStack(ModIndustry.BLUEPRINTS.get(entry.getKey())));
                }
                player(client).teleport(client.getServer().getOverworld(),407,3,400,0,0);
                Platform.openScreen(player(client),assembler);
            });
        }
        if (tick == 890 || tick == 925) capture(client, "assembly-ghosts-" + tick);
        if (tick == 926) press(client, 1);
        if (tick == 938) {
            if (!((IndustrialMachineScreen)client.currentScreen).getScreenHandler().canManageSecurity()) throw new AssertionError("Owner controls missing");
            press(client, 3);
        }
        if (tick == 950) {
            if (!((IndustrialMachineScreen)client.currentScreen).getScreenHandler().isPrivate()) throw new AssertionError("Privacy did not sync");
            capture(client,"security"); press(client, 3);
        }
        if (tick == 960) press(client, 2);
        if (tick == 972) press(client, 3);
        if (tick == 984) {
            if (((IndustrialMachineScreen)client.currentScreen).getScreenHandler().redstoneMode().ordinal() != 1) throw new AssertionError("Redstone mode did not sync");
            capture(client,"redstone"); client.player.closeHandledScreen();
        }
        if (tick == 985) client.getServer().execute(() -> {
            var grower = (IndustrialMachineBlockEntity)client.getServer().getOverworld().getBlockEntity(POSITIONS[2]);
            grower.setStack(0,ItemStack.EMPTY); grower.setStack(1,ItemStack.EMPTY);
            Platform.openScreen(player(client),grower);
        });
        if (tick == 1000) { capture(client,"growth-ghosts"); client.player.closeHandledScreen(); }
        if (tick == 1010) {
            client.options.hudHidden = true;
            client.getServer().execute(() -> player(client).teleport(client.getServer().getOverworld(),408.5,2.5,400,-25,0));
        }
        if (tick == 1032) capture(client,"frustum-edge");
        if (tick == 1040) client.getServer().execute(() -> player(client).teleport(client.getServer().getOverworld(),408.5,2.5,400,25,0));
        if (tick == 1060) capture(client,"frustum-return");
        if (tick == 1065) {
            LoggerFactory.getLogger("justifylasers-client-smoke").info("INDUSTRY_WORLD_SMOKE_PASSED menus={} nativeEnergy=true assembly=true calibration=true reload=true shader={}",
                    menus, net.askcraft.justifylasers.client.compat.IrisCompatibility.isShaderPackInUse());
            client.scheduleStop();
        }
    }

    private static void press(MinecraftClient client, int index) {
        client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).skip(index).findFirst().orElseThrow().onPress();
    }

    private static IndustrialMachineBlockEntity machine(MinecraftClient client, int index) { return (IndustrialMachineBlockEntity) client.world.getBlockEntity(POSITIONS[index]); }
    private static ServerPlayerEntity player(MinecraftClient client) { return client.getServer().getPlayerManager().getPlayer(client.player.getUuid()); }
    private static void capture(MinecraftClient client, String name) {
        ScreenshotRecorder.saveScreenshot(client.runDirectory, "industry-" + name + ".png", client.getFramebuffer(), text -> { });
    }
    private static void scene(MinecraftClient client) {
        net.askcraft.justifylasers.config.LaserConfig.get().laserAssemblyTicks = 120;
        var world = client.getServer().getOverworld();
        world.setTimeOfDay(9000);
        world.getGameRules().get(net.minecraft.world.GameRules.DO_MOB_SPAWNING).set(false, client.getServer());
        for (int x = 395; x <= 412; x++) for (int z = 392; z <= 409; z++) {
            world.setBlockState(new BlockPos(x,1,z), Blocks.POLISHED_DEEPSLATE.getDefaultState());
            for (int y = 2; y <= 10; y++) world.setBlockState(new BlockPos(x,y,z), Blocks.AIR.getDefaultState());
        }
        var player = player(client);
        player.changeGameMode(GameMode.CREATIVE); player.getInventory().clear();
        var materials = java.util.List.of(ModIndustry.RAW_PHOTONIC_CRYSTAL, ModIndustry.RAW_WOLFRAMITE,
                ModIndustry.WOLFRAMITE_INGOT, ModIndustry.PHOTONITE_CRYSTAL, ModIndustry.CRYSTAL_MOUNT);
        for (int slot = 0; slot < materials.size(); slot++) player.getInventory().setStack(slot, new ItemStack(materials.get(slot)));
        player.getInventory().selectedSlot = 8;
        player.getAbilities().allowFlying = true; player.getAbilities().flying = true; player.sendAbilitiesUpdate();
        player.teleport(world, 403, 3.7, 396, 0, 16);
        for (int index = 0; index < 4; index++) {
            var kind = MachineKind.values()[index];
            var positions = kind.multiblock() ? net.askcraft.justifylasers.industry.ChamberStructure.positions(POSITIONS[index]) : java.util.List.of(POSITIONS[index]);
            for (var pos : positions) {
                world.setBlockState(pos,ModIndustry.MACHINES.get(kind).getDefaultState());
                ((IndustrialMachineBlockEntity)world.getBlockEntity(pos)).initializeOwner(player);
            }
            if (kind.multiblock()) net.askcraft.justifylasers.industry.ChamberStructure.form((IndustrialMachineBlockEntity)world.getBlockEntity(POSITIONS[index]));
        }
        var generator = (IndustrialMachineBlockEntity) world.getBlockEntity(POSITIONS[0]);
        generator.setStack(0, new ItemStack(Items.COAL, 32));
        var smelter = (IndustrialMachineBlockEntity) world.getBlockEntity(POSITIONS[1]);
        smelter.setStack(0, new ItemStack(ModIndustry.RAW_WOLFRAMITE, 8));
        var grower = (IndustrialMachineBlockEntity) world.getBlockEntity(POSITIONS[2]);
        grower.setStack(0, new ItemStack(ModIndustry.RAW_PHOTONIC_CRYSTAL)); grower.setStack(1, new ItemStack(Items.QUARTZ, 2));
        grower.fillWater(8000, false); grower.energy().restore(100_000);
        int x = 399;
        for (var ore : ModIndustry.ORES.values()) { world.setBlockState(new BlockPos(x,2,405), ore.getDefaultState()); world.setBlockState(new BlockPos(x++,3,405), ore.getDefaultState()); }
    }
}

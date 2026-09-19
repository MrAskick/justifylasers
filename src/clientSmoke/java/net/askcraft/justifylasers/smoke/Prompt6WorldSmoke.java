package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserPartBlockEntity;
import net.askcraft.justifylasers.client.screen.IndustrialMachineScreen;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.industry.*;
import net.askcraft.justifylasers.platform.Platform;
import net.askcraft.justifylasers.registry.*;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import org.slf4j.LoggerFactory;

final class Prompt6WorldSmoke {
    private static final BlockPos SYNTH = new BlockPos(450,2,400), CUTTER = new BlockPos(454,2,400), GROWER = new BlockPos(460,2,400), MODULE = new BlockPos(458,2,400);
    static void tick(MinecraftClient client, int tick) {
        if (tick == 30) client.getServer().execute(() -> scene(client));
        if (tick == 100) {
            capture(client, "workshop");
            for (BlockPos pos : new BlockPos[]{SYNTH, CUTTER, GROWER}) {
                var machine = (IndustrialMachineBlockEntity)client.world.getBlockEntity(pos);
                if (machine == null || client.getBlockEntityRenderDispatcher().get(machine) == null || !machine.formed()) throw new AssertionError("New machine renderer / formation failed: " + pos);
            }
            var grower = (IndustrialMachineBlockEntity)client.world.getBlockEntity(GROWER);
            if (grower.spectralFlux() <= 0 || grower.progress() <= 0) throw new AssertionError("Inline spectrum module did not drive advanced growth: " + grower.status());
        }
        BlockPos[] positions = {SYNTH, CUTTER, GROWER, MODULE};
        for (int i = 0; i < positions.length; i++) {
            BlockPos pos = positions[i];
            if (tick == 115 + i * 50) client.getServer().execute(() -> {
                player(client).teleport(client.getServer().getOverworld(), pos.getX()+.5, 2.5, pos.getZ()-2, 0, 0);
                Platform.openScreen(player(client), (net.askcraft.justifylasers.platform.LaserScreenFactory)client.getServer().getOverworld().getBlockEntity(pos));
            });
            if (tick == 135 + i * 50) {
                if (i < 3 && !(client.currentScreen instanceof IndustrialMachineScreen) || i == 3 && !(client.currentScreen instanceof net.askcraft.justifylasers.client.screen.LaserModuleScreen)) throw new AssertionError("Machine/module menu did not synchronize");
                capture(client, "menu-" + i);
                if (i == 3) {
                    var field = client.currentScreen.children().stream().filter(net.minecraft.client.gui.widget.TextFieldWidget.class::isInstance).map(net.minecraft.client.gui.widget.TextFieldWidget.class::cast).findFirst().orElseThrow();
                    field.setFocused(true); field.setText("35DFFF");
                    client.currentScreen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_E, 0, 0);
                    if (client.currentScreen == null) throw new AssertionError("Hex E must not close the spectrum screen");
                }
            }
            if (tick == 150 + i * 50) client.player.closeHandledScreen();
        }
        if (Platform.isModLoaded("jei")) {
            if (tick == 310) IndustryJeiSmoke.show("chemical_synthesizer",4);
            if (tick == 325) capture(client,"jei-synthesizer");
            if (tick == 330) IndustryJeiSmoke.show("crystal_growth_chamber",5);
            if (tick == 345) capture(client,"jei-growth");
            if (tick == 350) IndustryJeiSmoke.show("laser_cutter",4);
            if (tick == 365) { capture(client,"jei-cutter"); client.setScreen(null); }
        }
        if (tick == 380) client.reloadResources();
        if (tick == 425) {
            client.getServer().execute(() -> {
                var module = (LaserPartBlockEntity)client.getServer().getOverworld().getBlockEntity(MODULE);
                if (module.spectrum() != 0x35DFFF) throw new AssertionError("RGB selection did not reach server");
            });
            capture(client, "reloaded");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("PROMPT6_WORLD_SMOKE_PASSED recipes=true growth=true fluidRegistration=true menus=4 reload=true");
            client.scheduleStop();
        }
    }
    private static void scene(MinecraftClient client) {
        var world = client.getServer().getOverworld(); var player = player(client);
        world.setTimeOfDay(7000); world.setWeather(0,0,false,false);
        player.changeGameMode(GameMode.CREATIVE); player.getInventory().clear();
        player.getAbilities().allowFlying = true; player.getAbilities().flying = true; player.sendAbilitiesUpdate();
        for (int x=444;x<=470;x++) for (int z=390;z<=407;z++) {
            world.setBlockState(new BlockPos(x,1,z), Blocks.POLISHED_DEEPSLATE.getDefaultState());
            for (int y=2;y<9;y++) world.setBlockState(new BlockPos(x,y,z), Blocks.AIR.getDefaultState());
        }
        player.teleport(world, 455, 4.2, 392, 0, 16);
        var synth = machine(client, SYNTH, MachineKind.CHEMICAL_SYNTHESIZER);
        var cutter = machine(client, CUTTER, MachineKind.LASER_CUTTER);
        var grower = machine(client, GROWER, MachineKind.CRYSTAL_GROWER);
        synth.restoreFluids(new IndustrialMachineBlockEntity.Fluids(ProcessFluid.WATER,8000,ProcessFluid.AMETHYST,1000));
        var recipe = IndustryRecipe.all(world).stream().filter(r -> r.process().outputFluid() == ProcessFluid.AMETHYST).findFirst().orElseThrow();
        for (int i=0;i<3;i++) { var item = recipe.inputs().get(i).getMatchingStacks()[0].copy(); item.setCount(recipe.counts().get(i)*8); synth.setStack(i,item); }
        synth.energy().restore(synth.energy().capacity());
        cutter.setStack(0,new ItemStack(CrystalGrowth.AMETHYST.grown(),8)); cutter.energy().restore(cutter.energy().capacity());
        grower.setStack(0,new ItemStack(CrystalGrowth.DIAMOND.natural(),4)); grower.fillFluid(ProcessFluid.DIAMOND,8000,false);
        beam(client,CUTTER.north(4),Direction.SOUTH);
        beam(client,GROWER.west(4),Direction.EAST);
        world.setBlockState(MODULE, ModLaserParts.MODULES.get(LaserModule.SPECTRUM).getBlock().getDefaultState());
        var module = (LaserPartBlockEntity)world.getBlockEntity(MODULE); module.initializeOwner(player);
        // Version-neutral item initialization also covers persisted RGB from an emitter slot.
        var stack = new ItemStack(ModLaserParts.MODULES.get(LaserModule.SPECTRUM)); net.askcraft.justifylasers.laser.LaserSpectrum.color(stack,CrystalGrowth.DIAMOND.spectrum()); module.initializeSpectrum(stack);
        int x=448;
        for (var fluid : ProcessFluid.values()) if (fluid != ProcessFluid.WATER) {
            world.setBlockState(new BlockPos(x++,2,404),fluid.fluid().getDefaultState().getBlockState());
            player.getInventory().setStack(fluid.ordinal()-1,new ItemStack(fluid.bucket()));
        }
        for (var crystal : CrystalGrowth.values()) player.getInventory().setStack(4+crystal.ordinal(),new ItemStack(crystal.grown()));
        player.getInventory().selectedSlot=8;
    }
    private static IndustrialMachineBlockEntity machine(MinecraftClient client, BlockPos pos, MachineKind kind) {
        var world = client.getServer().getOverworld();
        for (var part : kind.multiblock() ? ChamberStructure.positions(pos) : java.util.List.of(pos)) world.setBlockState(part,ModIndustry.MACHINES.get(kind).getDefaultState()
                .with(net.askcraft.justifylasers.block.IndustrialMachineBlock.FACING,Direction.NORTH));
        var machine = (IndustrialMachineBlockEntity)world.getBlockEntity(pos);
        if (kind.multiblock() && !ChamberStructure.form(machine)) throw new AssertionError("Machine failed to form");
        machine.initializeOwner(player(client)); return machine;
    }
    private static void beam(MinecraftClient client, BlockPos pos, Direction facing) {
        var world = client.getServer().getOverworld(); world.setBlockState(pos,ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING,facing));
        var beam = (LaserEmitterBlockEntity)world.getBlockEntity(pos); beam.getPropertyDelegate().set(54,651);
        beam.getPropertyDelegate().set(3,0); beam.getPropertyDelegate().set(4,0);
        net.askcraft.justifylasers.laser.LaserBeamNetwork.invalidate(world);
    }
    private static ServerPlayerEntity player(MinecraftClient client) { return client.getServer().getPlayerManager().getPlayer(client.player.getUuid()); }
    private static void capture(MinecraftClient client,String name) { ScreenshotRecorder.saveScreenshot(client.runDirectory,"prompt6-"+name+".png",client.getFramebuffer(),text -> { }); }
}

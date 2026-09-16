package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.LaserOpticBlock;
import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.askcraft.justifylasers.client.compat.RecipeNavigation;
import net.askcraft.justifylasers.client.screen.IndustrialMachineScreen;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.industry.ChamberStructure;
import net.askcraft.justifylasers.industry.MachineKind;
import net.askcraft.justifylasers.industry.TabletLoot;
import net.askcraft.justifylasers.laser.LaserBeamNetwork;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.platform.Platform;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import org.slf4j.LoggerFactory;

final class Alpha24WorldSmoke {
    private static final BlockPos CENTER = new BlockPos(724, 3, 724), A = CENTER.west(4), B = CENTER.north(4), RECEIVER = CENTER.east(4);
    private static final BlockPos ASSEMBLER = new BlockPos(730, 2, 722), GROWER = new BlockPos(730, 2, 726);

    static void tick(MinecraftClient client, int tick) {
        if (tick == 10 && !client.options.language.equals("en_us")) language(client, "en_us");
        if (tick == 30) client.getServer().execute(() -> scene(client));
        if (tick == 100) {
            if (!(client.world.getBlockEntity(CENTER) instanceof LaserOpticBlockEntity combiner)
                    || !combiner.getCachedState().get(LaserOpticBlock.LIT)) throw new AssertionError("Combiner did not synchronize");
            for (var pos : java.util.List.of(A, B)) {
                var source = (LaserEmitterBlockEntity) client.world.getBlockEntity(pos);
                if (!LaserBeamNetwork.path(source, 1).last().hitBlock().equals(RECEIVER)) throw new AssertionError("Combined beam did not reach receiver");
            }
            if (serverFlux(client) != 182400)
                throw new AssertionError("Combined FE/LM transmission changed in native mappings");
            capture(client, "combiner");
            client.options.hudHidden = false;
            client.getServer().execute(() -> player(client).getInventory().setStack(0, new ItemStack(ModIndustry.BLUEPRINTS.get("powered_laser_emitter"))));
        }
        if (tick == 140) {
            capture(client, "schematic-held");
            client.getServer().execute(() -> {
                player(client).getInventory().setStack(40, new ItemStack(ModIndustry.BLUEPRINTS.get("beam_combiner")));
                player(client).getInventory().setStack(0, new ItemStack(ModIndustry.BLANK_SCHEMATIC));
            });
        }
        if (tick == 170) { capture(client, "blank-and-offhand"); open(client, ASSEMBLER); }
        if (tick == 195) { capture(client, "assembly-menu"); arrow(client); }
        if (tick == 210 && Platform.isModLoaded("jei")) {
            Alpha24JeiSmoke.verifyMachine("assembly_chamber", 30);
            capture(client, "assembly-recipes");
            RecipeNavigation.showConstruction();
        }
        if (tick == 230 && Platform.isModLoaded("jei")) {
            capture(client, "construction-chamber");
            Alpha24JeiSmoke.solar(false);
        }
        if (tick == 250 && Platform.isModLoaded("jei")) { capture(client, "construction-solar"); Alpha24JeiSmoke.solar(true); }
        if (tick == 275) { if (Platform.isModLoaded("jei")) capture(client, "construction-solar-middle"); client.setScreen(null); open(client, GROWER); }
        if (tick == 300) { capture(client, "grower-menu"); arrow(client); }
        if (tick == 320) {
            if (Platform.isModLoaded("jei")) { Alpha24JeiSmoke.verifyMachine("crystal_growth_chamber", 1); capture(client, "grower-recipes"); }
            client.setScreen(null); language(client, "ru_ru");
        }
        if (tick == 370) {
            if (Platform.isModLoaded("jei")) Alpha24JeiSmoke.solar(false);
            else open(client, ASSEMBLER);
        }
        if (tick == 400) {
            capture(client, "localized-reloaded"); client.setScreen(null);
            client.getServer().execute(() -> {
                client.getServer().getOverworld().breakBlock(A, false);
                client.getServer().getOverworld().breakBlock(B, false);
            });
        }
        if (tick == 425) {
            if (serverFlux(client) != 0) throw new AssertionError("Ghost flux after source removal");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("ALPHA24_WORLD_SMOKE_PASSED combinedFlux=182400 loot=true recipes=true heldSchematics=true reload=true jei={} shader={}",
                    Platform.isModLoaded("jei"), net.askcraft.justifylasers.client.compat.IrisCompatibility.isShaderPackInUse());
            client.scheduleStop();
        }
    }

    private static void arrow(MinecraftClient client) {
        if (!(client.currentScreen instanceof IndustrialMachineScreen screen)) throw new AssertionError("Machine menu did not open");
        int arrow = screen.getScreenHandler().kind() == MachineKind.CRYSTAL_GROWER ? 181 : 219;
        boolean clicked = screen.mouseClicked((screen.width - 320) / 2 + arrow + 2, (screen.height - 234) / 2 + 76, 0);
        if (Platform.isModLoaded("jei")) {
            if (!clicked || client.currentScreen instanceof IndustrialMachineScreen) throw new AssertionError("Machine arrow did not open JEI");
        } else if (RecipeNavigation.available() || !(client.currentScreen instanceof IndustrialMachineScreen))
            throw new AssertionError("Optional JEI integration is not isolated");
    }
    private static void open(MinecraftClient client, BlockPos pos) {
        client.getServer().execute(() -> {
            player(client).teleport(client.getServer().getOverworld(), pos.getX() - 1, pos.getY(), pos.getZ(), 0, 0);
            Platform.openScreen(player(client), (IndustrialMachineBlockEntity) client.getServer().getOverworld().getBlockEntity(pos));
        });
    }
    private static void language(MinecraftClient client, String locale) {
        client.getLanguageManager().setLanguage(locale); client.options.language = locale; client.reloadResources();
    }
    private static void scene(MinecraftClient client) {
        var world = client.getServer().getOverworld();
        world.setTimeOfDay(6000); world.setWeather(0, 0, false, false);
        world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, client.getServer());
        world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, client.getServer());
        for (int x = 718; x <= 733; x++) for (int z = 717; z <= 730; z++) {
            world.setBlockState(new BlockPos(x, 1, z), Blocks.POLISHED_DEEPSLATE.getDefaultState());
            for (int y = 2; y <= 7; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
        }
        player(client).changeGameMode(GameMode.CREATIVE); player(client).getInventory().clear(); player(client).getInventory().selectedSlot = 0;
        player(client).teleport(world, 730, 4.4, 717, 42, 14);
        world.setBlockState(CENTER, ModBlocks.BEAM_COMBINER.getDefaultState().with(LaserOpticBlock.FACING, Direction.EAST));
        world.setBlockState(RECEIVER, ModBlocks.ENERGY_RECEIVER.getDefaultState().with(LaserOpticBlock.FACING, Direction.WEST));
        for (var pos : java.util.List.of(A, B)) {
            world.setBlockState(pos, ModBlocks.POWERED_LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, pos.equals(A) ? Direction.EAST : Direction.SOUTH));
            var source = (LaserEmitterBlockEntity) world.getBlockEntity(pos);
            source.setStack(0, new ItemStack(ModLaserParts.CRYSTALS.get(pos.equals(A) ? LaserColor.RED : LaserColor.BLUE)));
            source.setStack(LaserModule.RANGE.slot(), new ItemStack(ModLaserParts.ADVANCED_RANGE_MODULE, 2));
            source.energy().restore(1_000_000);
        }
        for (var pos : java.util.List.of(ASSEMBLER, GROWER)) {
            var kind = pos.equals(ASSEMBLER) ? MachineKind.ASSEMBLY_CHAMBER : MachineKind.CRYSTAL_GROWER;
            for (var cell : ChamberStructure.positions(pos)) world.setBlockState(cell, ModIndustry.MACHINES.get(kind).getDefaultState());
            var machine = (IndustrialMachineBlockEntity) world.getBlockEntity(pos);
            machine.initializeOwner(player(client));
            if (!ChamberStructure.form(machine)) throw new AssertionError("Fixture chamber cannot form");
        }
        var params = new LootContextParameterSet.Builder(world).add(LootContextParameters.ORIGIN, Vec3d.ofCenter(CENTER)).build(LootContextTypes.CHEST);
        for (String table : TabletLoot.CHESTS.keySet()) {
            int found = 0;
            for (int seed = 1; seed <= 160; seed++) {
                var drops = SmokeWorldAccess.loot(world, table).generateLoot(params, seed);
                long count = drops.stream().filter(stack -> stack.isOf(ModIndustry.BLUEPRINTS.get("extraterrestrial_tablet"))).count();
                if (count > 1) throw new AssertionError("Duplicate schematic pool in " + table);
                found += count;
            }
            if (found == 0) throw new AssertionError("Native loot hook did not run for " + table);
        }
    }
    private static ServerPlayerEntity player(MinecraftClient client) { return client.getServer().getPlayerManager().getPlayer(client.player.getUuid()); }
    private static long serverFlux(MinecraftClient client) {
        return client.getServer().submit(() -> ((LaserOpticBlockEntity) client.getServer().getOverworld().getBlockEntity(RECEIVER)).lastFlux()).join();
    }
    private static void capture(MinecraftClient client, String name) {
        ScreenshotRecorder.saveScreenshot(client.runDirectory, "alpha24-" + name + ".png", client.getFramebuffer(), text -> { });
    }
}

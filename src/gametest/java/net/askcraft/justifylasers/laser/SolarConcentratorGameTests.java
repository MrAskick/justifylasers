package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.LaserComponentBlock;
import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.LaserOpticBlock;
import net.askcraft.justifylasers.block.LaserReceiverBlock;
import net.askcraft.justifylasers.block.entity.LaserComponentBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserReceiverBlockEntity;
import net.askcraft.justifylasers.block.entity.SolarConcentratorBlockEntity;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.industry.SolarExposure;
import net.askcraft.justifylasers.industry.SolarStructure;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.askcraft.justifylasers.registry.ModEntities;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

public class SolarConcentratorGameTests implements FabricGameTest {
    private static final BlockPos ORIGIN = new BlockPos(2, 2, 2);
    private static final BlockPos SOURCE = new BlockPos(3, 2, 1);

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "solar")
    public void componentRotationsAreNotAssemblyConstraintsAndOriginSurvivesReload(TestContext context) {
        int i=0;
        for(var cell:SolarStructure.PARTS) {
            var state=ModIndustry.COMPONENT_BLOCKS.get(cell.component()).getDefaultState();
            if(state.contains(LaserComponentBlock.FACING)) state=state.with(LaserComponentBlock.FACING,new Direction[]{Direction.WEST,Direction.SOUTH,Direction.EAST,Direction.NORTH}[i++%4]);
            context.setBlockState(ORIGIN.add(cell.offset()),state);
        }
        var source=(SolarConcentratorBlockEntity)context.getBlockEntity(SOURCE);
        context.assertTrue(SolarStructure.form(source),"Arbitrary component rotations still form");
        context.assertTrue(SolarStructure.origin(source).equals(context.getAbsolutePos(ORIGIN)),"Origin found from geometry, not resonator facing");
        var saved=source.createNbt(); source.setStructureOrigin(null); source.readNbt(saved);
        context.assertTrue(SolarStructure.origin(source).equals(context.getAbsolutePos(ORIGIN)) && SolarStructure.matches(source),"Saved multiblock retains its physical origin");
        context.removeBlock(SOURCE); context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "solar")
    public void componentsPlaceAndDropTheirOriginalItems(TestContext context) {
        var world = context.getWorld();
        var player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        BlockPos support = context.getAbsolutePos(new BlockPos(3, 2, 3)), target = support.up();
        player.setPosition(Vec3d.ofCenter(support.north(2)));
        world.setBlockState(support, Blocks.STONE.getDefaultState());
        try {
            for (var entry : ModIndustry.COMPONENT_BLOCKS.entrySet()) {
                var block = entry.getValue();
                var item = block.asItem();
                context.assertTrue(item instanceof BlockItem && ((BlockItem) item).getBlock() == block, "Original block item: " + entry.getKey());
                ItemStack stack = new ItemStack(item, 2);
                player.setStackInHand(Hand.MAIN_HAND, stack);
                var hit = new BlockHitResult(Vec3d.ofCenter(support).add(0, .5, 0), Direction.UP, support, false);
                context.assertTrue(stack.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit)).isAccepted(), "Place " + entry.getKey());
                context.assertTrue(stack.getCount() == 1 && world.getBlockState(target).isOf(block), "Exactly one item becomes a block");
                context.assertTrue(world.getBlockEntity(target) instanceof LaserComponentBlockEntity, "Component entity registered");
                if (ModIndustry.COMPONENT_IDS.contains(entry.getKey()))
                    context.assertTrue(item == ModIndustry.COMPONENTS.get(entry.getKey()), "Recipe identity preserved");
                player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
                context.assertTrue(player.interactionManager.tryBreakBlock(target), "Player can mine even absorbing glass");
                var drops = world.getEntitiesByClass(ItemEntity.class, new Box(target).expand(.5), e -> e.getStack().isOf(item));
                context.assertTrue(drops.size() == 1 && drops.get(0).getStack().getCount() == 1, "Exactly one matching drop");
                drops.forEach(ItemEntity::discard);
            }
        } finally { player.discard(); }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "solar")
    public void allOrientationsFormAndNeverShareTheirBody(TestContext context) {
        for (Direction facing : Direction.Type.HORIZONTAL) {
            buildBody(context);
            BlockPos sourcePos = ORIGIN.add(1, 0, 1).offset(facing, 2);
            var source = resonator(context, sourcePos, facing);
            context.assertTrue(SolarStructure.form(source), "Complete pattern forms: " + facing);
            context.assertTrue(SolarStructure.origin(source).equals(context.getAbsolutePos(ORIGIN)), "Orientation preserves body origin");
            for (var cell : SolarStructure.BODY) {
                var member = (LaserComponentBlockEntity) context.getBlockEntity(ORIGIN.add(cell.offset()));
                context.assertTrue(member.formed() && member.controllerPos().equals(source.getPos()), "All 27 members share one controller");
            }
            BlockPos competingPos = ORIGIN.add(1, 0, 1).offset(facing.getOpposite(), 2);
            var competing = resonator(context, competingPos, facing.getOpposite());
            context.assertFalse(SolarStructure.form(competing), "A second resonator cannot double the output");
            context.removeBlock(competingPos);
            context.removeBlock(sourcePos);
            context.assertFalse(SolarStructure.matches(source), "Removed controller cannot reclaim a body");
            for (var cell : SolarStructure.BODY)
                context.assertFalse(((LaserComponentBlockEntity) context.getBlockEntity(ORIGIN.add(cell.offset()))).formed(), "Dismantling releases every member");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "solar")
    public void everyRequiredCellIsValidatedAndRemovalStopsTheSource(TestContext context) {
        buildBody(context);
        var source = resonator(context, SOURCE, Direction.NORTH);
        for (var cell : SolarStructure.BODY) {
            context.assertTrue(SolarStructure.form(source), "Valid body reforms");
            context.removeBlock(ORIGIN.add(cell.offset()));
            context.assertFalse(source.formed() || source.isBeamActive(), "Breaking any member immediately stops the multiblock");
            context.assertFalse(SolarStructure.form(source), "Missing cell cannot be ignored: " + cell.offset());
            context.setBlockState(ORIGIN.add(cell.offset()), ModIndustry.COMPONENT_BLOCKS.get(cell.component()));
        }
        context.removeBlock(SOURCE);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "solar")
    public void noonGeneratesWarmWideBeamAndNightStopsImmediately(TestContext context) {
        var world = context.getWorld();
        long time = world.getTimeOfDay();
        boolean mode = LaserConfig.technicalMode();
        try {
            LaserConfig.applyServerMode(true);
            world.setTimeOfDay(6000);
            var source = formed(context);
            source.solarTick();
            context.assertTrue(source.luminousFlux() == LaserConfig.get().solarPeakFlux, "Clear noon reaches the configured peak: " + source.luminousFlux());
            var path = LaserBeamNetwork.path(source, 1);
            context.assertTrue(source.isBeamActive() && path.segments().get(0).rgb() == SolarConcentratorBlockEntity.BEAM_RGB, "Warm white source uses the optics network");
            context.assertTrue(source.getBeamWidthScale() == LaserConfig.get().solarBeamWidth && source.getBeamRange() == LaserConfig.get().solarBeamRange, "Configured width and range");
            var saved = source.createNbt();
            source.readNbt(saved);
            context.assertTrue(source.transferBudget() == 0, "Loading saved state cannot issue stale energy");
            source.solarTick();
            context.assertTrue(source.transferBudget() > 0, "Fresh daylight validation restores generation");
            world.setTimeOfDay(18000);
            source.solarTick();
            context.assertTrue(source.output() == 0 && !source.isBeamActive(), "Night stops before the cached exposure expires");
            context.assertFalse(LaserBeamNetwork.paths(world, 1).containsKey(source.getPos()), "No ghost beam at night");
            world.setTimeOfDay(6000);
            LaserConfig.applyServerMode(false);
            source.solarTick();
            context.assertTrue(source.output() == 0, "Disabling technical content disables solar energy");
        } finally {
            context.removeBlock(SOURCE);
            world.setTimeOfDay(time);
            LaserConfig.applyServerMode(mode);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "solar")
    public void sunlightUsesActualTopAndSideOcclusion(TestContext context) {
        var world = context.getWorld();
        long time = world.getTimeOfDay();
        try {
            world.setTimeOfDay(6000);
            buildBody(context);
            var origin = context.getAbsolutePos(ORIGIN);
            for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
                context.setBlockState(ORIGIN.add(x, 4, z), Blocks.STONE);
            context.assertTrue(SolarExposure.measure(world, origin).fraction() == 0, "An opaque roof blocks noon light");
            // Raise the low-angle probe above neighboring GameTest structures.
            BlockPos sideProbe = origin.add(3, 32, 1);
            Vec3d start = Vec3d.of(sideProbe).add(.01, .5, .5);
            Vec3d angled = new Vec3d(1, .1, 0).normalize();
            // The fixture explicitly loads its probe corridor; production probes never do so.
            for (int x = sideProbe.getX() >> 4; x <= (sideProbe.getX() + 129) >> 4; x++) world.getChunk(x, sideProbe.getZ() >> 4);
            world.setBlockState(sideProbe.west().up(), Blocks.STONE.getDefaultState());
            try {
                context.assertTrue(SolarExposure.seesSun(world, start, angled), "Low side light reaches panels below the overhang");
                world.setBlockState(sideProbe, Blocks.STONE.getDefaultState());
                context.assertFalse(SolarExposure.seesSun(world, start, angled), "A side wall blocks that same ray");
            } finally {
                world.setBlockState(sideProbe, Blocks.AIR.getDefaultState());
                world.setBlockState(sideProbe.west().up(), Blocks.AIR.getDefaultState());
            }
            for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
                context.setBlockState(ORIGIN.add(x, 4, z), ModIndustry.LASER_ABSORBING_GLASS);
            context.assertTrue(SolarExposure.measure(world, origin).fraction() > .99, "Absorbing glass transmits sunlight but not lasers");
            context.assertTrue(SolarExposure.sunDirection(world, 1).y > .99, "The sun is overhead at noon");
            world.setTimeOfDay(1000);
            context.assertTrue(SolarExposure.sunDirection(world, 1).x > 0, "Morning light comes from the east");
            world.setTimeOfDay(11000);
            context.assertTrue(SolarExposure.sunDirection(world, 1).x < 0, "Evening light comes from the west");
        } finally { world.setTimeOfDay(time); }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "solar")
    public void absorbingGlassStopsMaximumMiningAndAllLootModes(TestContext context) {
        var target = new BlockPos(4, 3, 3);
        var sourcePos = new BlockPos(1, 3, 3);
        context.setBlockState(target, ModIndustry.LASER_ABSORBING_GLASS);
        context.setBlockState(sourcePos, ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST));
        var emitter = (LaserEmitterBlockEntity) context.getBlockEntity(sourcePos);
        emitter.getPropertyDelegate().set(3, 1);
        emitter.getPropertyDelegate().set(13, 8);
        emitter.getPropertyDelegate().set(14, 100);
        for (boolean drops : new boolean[]{true, false}) for (boolean silk : new boolean[]{true, false}) {
            context.assertFalse(LaserMining.breakBlock(context.getWorld(), context.getAbsolutePos(target), drops, silk), "Laser-proof in every loot mode");
            for (int i = 0; i < 30; i++) LaserEmitterBlockEntity.serverTick(emitter.getWorld(), emitter.getPos(), emitter.getCachedState(), emitter);
            context.assertTrue(context.getWorld().getBlockState(context.getAbsolutePos(target)).isOf(ModIndustry.LASER_ABSORBING_GLASS), "Maximum mining speed cannot consume glass");
            context.assertTrue(LaserBeamNetwork.path(emitter, 1).last().hitBlock().equals(context.getAbsolutePos(target)), "Laser cannot pass through its collision");
        }
        context.removeBlock(sourcePos);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "solar_energy", tickLimit = 30)
    public void solarBeamRecolorsAndSplitsWithoutCreatingEnergy(TestContext context) {
        var world = context.getWorld();
        long originalTime = world.getTimeOfDay();
        boolean originalMode = LaserConfig.technicalMode();
        world.setTimeOfDay(6000);
        LaserConfig.applyServerMode(true);
        var source = formed(context);
        var split = SOURCE.north();
        context.setBlockState(split, ModBlocks.BEAM_SPLITTER.getDefaultState().with(LaserOpticBlock.FACING, Direction.SOUTH));
        var splitter = (LaserOpticBlockEntity) context.getBlockEntity(split);
        for (Direction side : Direction.values()) splitter.setPortMode(side, side == Direction.SOUTH ? OpticPortMode.INPUT
                : side.getAxis() == Direction.Axis.X ? OpticPortMode.OUTPUT : OpticPortMode.DISABLED);
        context.setBlockState(split.east(), ModLaserParts.DECORATIONS.get("blue_crystal"));
        context.setBlockState(split.east(2), ModBlocks.ENERGY_RECEIVER.getDefaultState().with(LaserOpticBlock.FACING, Direction.WEST));
        context.setBlockState(split.west(2), ModBlocks.ENERGY_RECEIVER.getDefaultState().with(LaserOpticBlock.FACING, Direction.EAST));
        context.runAtTick(8, () -> {
            try {
                world.setTimeOfDay(6000);
                LaserConfig.applyServerMode(true);
                source.solarTick();
                var path = LaserBeamNetwork.path(source, 1);
                var ends = path.segments().stream().filter(ray -> ray.hasBlockHit()
                        && world.getBlockEntity(ray.hitBlock()) instanceof LaserOpticBlockEntity optic
                        && optic.kind() == LaserOpticBlock.Kind.ENERGY_RECEIVER).toList();
                context.assertTrue(ends.size() == 2 && Math.abs(ends.stream().mapToDouble(LaserBeamTrace::power).sum() - 1) < 1e-9,
                        "Two terminal branches conserve solar power: output=" + source.output() + ", path=" + path.segments());
                context.assertTrue(ends.stream().anyMatch(ray -> ray.rgb() == LaserColor.BLUE.rgb()), "A crystal recolors solar light");
                var east = (LaserOpticBlockEntity) context.getBlockEntity(split.east(2));
                var west = (LaserOpticBlockEntity) context.getBlockEntity(split.west(2));
                int before = east.energy().stored() + west.energy().stored();
                ServerTickEvents.END_WORLD_TICK.invoker().onEndTick(world);
                int first = east.energy().stored() + west.energy().stored();
                ServerTickEvents.END_WORLD_TICK.invoker().onEndTick(world);
                context.assertTrue(first == east.energy().stored() + west.energy().stored(), "Repeated callbacks never duplicate transfer");
                context.assertTrue(first - before <= source.output(), "Received energy never exceeds available sunlight");
                context.assertTrue(first > 0, "Energy receivers actually collect solar energy");
            } finally {
                context.removeBlock(SOURCE);
                world.setTimeOfDay(originalTime);
                LaserConfig.applyServerMode(originalMode);
            }
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "solar_redstone", tickLimit = 30)
    public void redstoneReceiverWorksWhenItTicksBeforeTheSource(TestContext context) {
        var world = context.getWorld();
        long time = world.getTimeOfDay();
        boolean mode = LaserConfig.technicalMode();
        world.setTimeOfDay(6000);
        LaserConfig.applyServerMode(true);
        var receiverPos = SOURCE.north();
        context.setBlockState(receiverPos, ModBlocks.LASER_RECEIVER.getDefaultState().with(LaserReceiverBlock.FACING, Direction.SOUTH));
        var source = formed(context);
        for (int tick = 4; tick <= 10; tick++) context.runAtTick(tick, () -> {
            var receiver = (LaserReceiverBlockEntity) context.getBlockEntity(receiverPos);
            LaserReceiverBlockEntity.serverTick(world, receiver.getPos(), receiver.getCachedState(), receiver);
            context.assertTrue(receiver.getCachedState().get(LaserReceiverBlock.POWER) == 15, "Receiver keeps its signal regardless of ticker order");
        });
        context.runAtTick(11, () -> {
            try {
                world.setTimeOfDay(18000);
                var receiver = (LaserReceiverBlockEntity) context.getBlockEntity(receiverPos);
                LaserBeamNetwork.invalidate(world);
                LaserReceiverBlockEntity.serverTick(world, receiver.getPos(), receiver.getCachedState(), receiver);
                context.assertTrue(receiver.getCachedState().get(LaserReceiverBlock.POWER) == 0, "Night disables even the previous-tick route");
                context.assertTrue(source.transferBudget() == 0, "No night energy");
            } finally {
                context.removeBlock(SOURCE);
                world.setTimeOfDay(time);
                LaserConfig.applyServerMode(mode);
            }
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "solar")
    public void rainAndDarkDimensionsCannotPowerTheCollector(TestContext context) {
        var world = context.getWorld();
        long time = world.getTimeOfDay();
        boolean raining = world.isRaining(), thundering = world.isThundering(), mode = LaserConfig.technicalMode();
        try {
            LaserConfig.applyServerMode(true);
            world.setTimeOfDay(6000);
            var source = formed(context);
            source.solarTick();
            world.setWeather(0, 200, true, false);
            world.setRainGradient(1);
            source.solarTick();
            context.assertTrue(source.output() == 0, "Rain immediately stops generation");
            world.setWeather(0, 200, true, true);
            world.setThunderGradient(1);
            source.solarTick();
            context.assertTrue(source.output() == 0, "Thunderstorms cannot generate power");
            context.assertFalse(SolarExposure.hasSun(world.getServer().getWorld(net.minecraft.world.World.NETHER)), "Nether has no sun");
            context.assertFalse(SolarExposure.hasSun(world.getServer().getWorld(net.minecraft.world.World.END)), "End has no sun");
        } finally {
            context.removeBlock(SOURCE);
            world.setTimeOfDay(time);
            world.setWeather(0, 0, raining, thundering);
            world.setRainGradient(raining ? 1 : 0);
            world.setThunderGradient(thundering ? 1 : 0);
            LaserConfig.applyServerMode(mode);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "solar")
    public void mirrorRedirectsTheSolarBudgetAndColor(TestContext context) {
        redirectedBeam(context, false);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "solar")
    public void portableCubeRedirectsTheSolarBudgetAndColor(TestContext context) {
        redirectedBeam(context, true);
    }

    private static void redirectedBeam(TestContext context, boolean useCube) {
        var world = context.getWorld();
        long time = world.getTimeOfDay();
        boolean mode = LaserConfig.technicalMode();
        net.askcraft.justifylasers.entity.RefocusingCubeEntity cube = null;
        try {
            world.setTimeOfDay(6000);
            LaserConfig.applyServerMode(true);
            var source = formed(context);
            BlockPos opticPos = SOURCE.north(), receiverPos = opticPos.east(2);
            if (useCube) {
                cube = new net.askcraft.justifylasers.entity.RefocusingCubeEntity(ModEntities.REFOCUSING_CUBE, world);
                cube.setNoGravity(true);
                cube.orient(-90, 0);
                cube.setPosition(context.getAbsolute(new Vec3d(3.5, 2.05, .5)));
                world.spawnEntity(cube);
            } else {
                context.setBlockState(opticPos, ModBlocks.LASER_MIRROR.getDefaultState().with(LaserOpticBlock.FACING, Direction.UP));
                ((LaserOpticBlockEntity) context.getBlockEntity(opticPos)).aim(new Vec3d(1, 0, 1));
            }
            context.setBlockState(receiverPos, ModBlocks.ENERGY_RECEIVER.getDefaultState().with(LaserOpticBlock.FACING, Direction.WEST));
            source.solarTick();
            // Other fixtures in this batch have long solar rays; they must not claim this cube's single input.
            var path = LaserBeamPath.trace(world, source, 1, new java.util.HashMap<>(), new java.util.HashMap<>(), new java.util.HashMap<>());
            context.assertTrue(path.segments().size() == 2, "Exactly one redirection: " + path.segments());
            context.assertTrue(context.getAbsolutePos(receiverPos).equals(path.last().hitBlock()), "Redirected solar beam reaches the energy receiver");
            context.assertTrue(path.last().rgb() == SolarConcentratorBlockEntity.BEAM_RGB && path.last().power() == 1, "Warm color and original power preserved");
            context.assertTrue(path.segments().stream().mapToDouble(LaserBeamTrace::length).sum() <= source.getBeamRange(), "Redirecting cannot extend the configured range");
        } finally {
            if (cube != null) cube.discard();
            context.removeBlock(SOURCE);
            world.setTimeOfDay(time);
            LaserConfig.applyServerMode(mode);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "solar")
    public void guiControlsAndRedstoneWorkAcrossTheWholeStructure(TestContext context) {
        var world = context.getWorld();
        long time = world.getTimeOfDay();
        boolean mode = LaserConfig.technicalMode();
        try {
            LaserConfig.applyServerMode(true);
            world.setTimeOfDay(6000);
            var source = formed(context);
            var player = context.createMockSurvivalPlayer();
            player.setPosition(Vec3d.ofCenter(source.getPos()));
            source.initializeOwner(player);
            var menu = new net.askcraft.justifylasers.screen.SolarConcentratorScreenHandler(12, player.getInventory(), source);
            source.solarTick();
            context.assertTrue(menu.luminousFlux() == source.peakFlux() && menu.enabled(), "No redstone is needed by default");
            context.assertTrue(menu.onButtonClick(player, 1), "Change to high signal mode");
            context.assertFalse(source.isBeamActive(), "HIGH waits for redstone");
            for (BlockPos power : new BlockPos[]{ORIGIN.west(), ORIGIN.east(3), ORIGIN.north(), ORIGIN.south(3), ORIGIN.down(), ORIGIN.up(3)}) {
                context.setBlockState(power, Blocks.REDSTONE_BLOCK);
                source.solarTick();
                context.assertTrue(source.isBeamActive() && menu.hasRedstoneSignal(), "Any exterior face detects redstone: " + power);
                context.removeBlock(power);
                source.solarTick();
                context.assertFalse(source.isBeamActive(), "Removing signal stops generation");
            }
            menu.onButtonClick(player, 1);
            context.assertTrue(source.isBeamActive() && menu.redstoneMode() == LaserRedstoneMode.LOW, "Inverted redstone mode");
            menu.onButtonClick(player, 0);
            context.assertFalse(source.isBeamActive() || source.opticalBudget() > 0, "Power switch disables routing and output immediately");
            menu.onButtonClick(player, 0);
            context.assertTrue(source.isBeamActive(), "Power switch restores generation");
            context.assertFalse(menu.onButtonClick(player, 9999), "Reject unknown controls");
            context.removeBlock(ORIGIN);
            context.assertFalse(menu.canUse(player) || menu.onButtonClick(player, 0), "A dismantled structure closes its controls");
        } finally { context.removeBlock(SOURCE); world.setTimeOfDay(time); LaserConfig.applyServerMode(mode); }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "solar")
    public void privacyProtectsEveryPartAndCannotBeChangedByGuests(TestContext context) {
        var source = formed(context);
        var owner = context.createMockSurvivalPlayer();
        var guest = context.createMockCreativeServerPlayerInWorld();
        guest.setUuid(java.util.UUID.randomUUID());
        owner.setPosition(Vec3d.ofCenter(source.getPos())); guest.setPosition(owner.getPos());
        try {
            source.initializeOwner(owner);
            context.assertFalse(source.togglePrivacy(guest), "A visitor cannot take ownership of a public structure");
            var menu = new net.askcraft.justifylasers.screen.SolarConcentratorScreenHandler(12, guest.getInventory(), source);
            context.assertTrue(source.togglePrivacy(owner), "Owner can lock the structure");
            context.assertFalse(menu.canUse(guest) || menu.onButtonClick(guest, 0) || menu.onButtonClick(guest, 2), "An already-open guest menu cannot bypass privacy");
            for (var cell : SolarStructure.BODY) {
                var member = (LaserComponentBlockEntity) context.getBlockEntity(ORIGIN.add(cell.offset()));
                context.assertTrue(member.canAccess(owner) && !member.canAccess(guest), "Each member resolves owner permissions");
                context.assertFalse(guest.interactionManager.tryBreakBlock(member.getPos()), "Creative visitors cannot break private components");
            }
            guest.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModBlocks.CONFIGURATOR));
            var hit = new BlockHitResult(Vec3d.ofCenter(source.getPos()), Direction.NORTH, source.getPos(), false);
            context.assertTrue(ModBlocks.CONFIGURATOR.useOnBlock(new ItemUsageContext(guest, Hand.MAIN_HAND, hit)) == net.minecraft.util.ActionResult.FAIL,
                    "The configurator cannot dismantle private machinery");
            var restored = new SolarConcentratorBlockEntity(source.getPos(), source.getCachedState());
            restored.readNbt(source.createNbt());
            context.assertTrue(restored.isPrivate() && restored.canManageSecurity(owner) && !restored.canAccess(guest), "Ownership persists across saves");
            SolarStructure.dismantle(source);
            context.assertFalse(source.canAccess(guest), "Dismantling does not erase the resonator's owner");
        } finally { guest.discard(); context.removeBlock(SOURCE); }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "solar")
    public void separateComponentsAreQuietAndDoNotConsumeRightClick(TestContext context) {
        var player = context.createMockSurvivalPlayer();
        BlockPos target = new BlockPos(3, 3, 3);
        var absolute = context.getAbsolutePos(target);
        player.setPosition(Vec3d.ofCenter(absolute.north()));
        var hit = new BlockHitResult(Vec3d.ofCenter(absolute), Direction.NORTH, absolute, false);
        for (var block : ModIndustry.COMPONENT_BLOCKS.values()) {
            if (block == ModIndustry.SMALL_SOLAR_CONCENTRATOR) continue;
            context.setBlockState(target, block);
            context.assertTrue(block.onUse(block.getDefaultState(), context.getWorld(), absolute, player, Hand.MAIN_HAND, hit)
                    == net.minecraft.util.ActionResult.PASS, "Unformed parts have no inspection popup: " + block.component());
            context.removeBlock(target);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "solar")
    public void solarMenuSynchronizesGigalumensAndKeepsPlayerInventoryIntact(TestContext context) {
        long previousPeak = LaserConfig.get().solarPeakFlux, time = context.getWorld().getTimeOfDay();
        boolean mode = LaserConfig.technicalMode();
        try {
            LaserConfig.get().solarPeakFlux = 1_800_000_001_001L;
            LaserConfig.applyServerMode(true); context.getWorld().setTimeOfDay(6000);
            var source = formed(context);
            source.solarTick();
            var player = context.createMockSurvivalPlayer();
            player.setPosition(Vec3d.ofCenter(source.getPos()));
            source.initializeOwner(player);
            var serverMenu = new net.askcraft.justifylasers.screen.SolarConcentratorScreenHandler(12, player.getInventory(), source);
            var clientMenu = new net.askcraft.justifylasers.screen.SolarConcentratorScreenHandler(12, player.getInventory(), source.getPos());
            for (int part = 0; part < 4; part++) {
                clientMenu.setProperty(6 + part, (short) (serverMenu.luminousFlux() >>> (16 * part)));
                clientMenu.setProperty(10 + part, (short) (serverMenu.peakFlux() >>> (16 * part)));
            }
            context.assertTrue(clientMenu.luminousFlux() == source.luminousFlux() && clientMenu.peakFlux() == source.peakFlux(), "No truncation in menu packets");
            player.getInventory().setStack(9, new ItemStack(Items.DIAMOND, 5));
            context.assertTrue(serverMenu.quickMove(player, 0).getCount() == 5, "Shift-click moves inventory to hotbar");
            context.assertTrue(player.getInventory().count(Items.DIAMOND) == 5, "No items lost or duplicated in a slotless machine");
        } finally {
            context.removeBlock(SOURCE); LaserConfig.get().solarPeakFlux = previousPeak;
            context.getWorld().setTimeOfDay(time); LaserConfig.applyServerMode(mode);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "solar_damage", tickLimit = 35)
    public void solarDamageTicksRespectArmorAndStopOutsideTheBeam(TestContext context) {
        var world = context.getWorld();
        // Other solar fixtures leave long redirected rays alive; keep this damage lane optically isolated.
        for (int edge = -1; edge <= 7; edge++) for (int y = 2; y <= 3; y++) {
            context.setBlockState(-1, y, edge, Blocks.OBSIDIAN);
            context.setBlockState(7, y, edge, Blocks.OBSIDIAN);
            context.setBlockState(edge, y, -1, Blocks.OBSIDIAN);
            context.setBlockState(edge, y, 7, Blocks.OBSIDIAN);
        }
        long time = world.getTimeOfDay();
        boolean mode = LaserConfig.technicalMode();
        world.setTimeOfDay(6000); LaserConfig.applyServerMode(true);
        var source = formed(context);
        var bare = context.spawnMob(net.minecraft.entity.EntityType.VILLAGER, new Vec3d(3.5, 2, .5));
        var armored = context.spawnMob(net.minecraft.entity.EntityType.VILLAGER, new Vec3d(3.5, 2, .5));
        var protectedMob = context.spawnMob(net.minecraft.entity.EntityType.VILLAGER, new Vec3d(3.5, 2, .5));
        var targets = java.util.List.of(bare, armored, protectedMob);
        targets.forEach(target -> { target.setAiDisabled(true); target.setNoGravity(true); });
        armored.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        protectedMob.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.RESISTANCE, 200, 1));
        context.runAtEveryTick(() -> targets.forEach(target -> target.setVelocity(Vec3d.ZERO)));
        float[] health = new float[1];
        context.runAtTick(2, () -> health[0] = bare.getHealth());
        context.runAtTick(12, () -> {
            context.assertTrue(Math.abs(health[0] - bare.getHealth() - 5) < .01, "Ten real ticks deal 5 HP at peak sunlight: " + health[0] + " -> " + bare.getHealth());
            context.assertTrue(armored.getHealth() > bare.getHealth() && protectedMob.getHealth() > bare.getHealth(), "Armor and Resistance protect against solar damage");
            context.assertFalse(bare.isOnFire(), "No unconfigured ignition damage");
            source.solarTick();
            float first = bare.getHealth();
            source.solarTick();
            context.assertTrue(first == bare.getHealth(), "Extra ticker calls cannot double solar damage");
            bare.setPosition(bare.getPos().add(3, 0, 0));
            health[0] = bare.getHealth();
        });
        context.runAtTick(16, () -> {
            try { context.assertTrue(health[0] == bare.getHealth(), "Leaving the beam ends damage"); }
            finally { targets.forEach(net.minecraft.entity.Entity::discard); context.removeBlock(SOURCE); world.setTimeOfDay(time); LaserConfig.applyServerMode(mode); }
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "solar_light", tickLimit = 25)
    public void pureLightConsumerReceivesLumensWithoutAnEnergyCapability(TestContext context) {
        var world = context.getWorld();
        long time = world.getTimeOfDay();
        boolean mode = LaserConfig.technicalMode();
        world.setTimeOfDay(6000); LaserConfig.applyServerMode(true);
        var source = formed(context);
        BlockPos target = context.getAbsolutePos(SOURCE.north());
        var state = ModIndustry.SOLAR_ABSORBER.getDefaultState();
        world.setBlockState(target, state);
        var sink = new TestLightSink(target, state);
        world.addBlockEntity(sink);
        context.runAtTick(3, () -> {
            try {
                source.solarTick();
                ServerTickEvents.END_WORLD_TICK.invoker().onEndTick(world);
                context.assertTrue(sink.received > 0 && sink.lastFlux == source.luminousFlux(), "Native light consumer: received=" + sink.received
                        + ", last=" + sink.lastFlux + ", flux=" + source.luminousFlux() + ", sink=" + world.getBlockEntity(target)
                        + ", path=" + LaserBeamNetwork.path(source, 1).segments());
                long allocated = sink.received;
                ServerTickEvents.END_WORLD_TICK.invoker().onEndTick(world);
                context.assertTrue(sink.received == allocated, "The light allocation cannot be spent twice");
            } finally { context.removeBlock(SOURCE); world.removeBlock(target, false); world.setTimeOfDay(time); LaserConfig.applyServerMode(mode); }
            context.complete();
        });
    }

    private static final class TestLightSink extends LaserComponentBlockEntity implements LaserLightSink {
        private long received;
        private long lastFlux;
        TestLightSink(BlockPos pos, net.minecraft.block.BlockState state) { super(pos, state); }
        @Override public boolean acceptsLaser(Direction side, Vec3d point) { return side == Direction.SOUTH; }
        @Override public void receiveLight(long lumens, int rgb) { lastFlux = lumens; received += lumens; }
    }

    private static void buildBody(TestContext context) {
        for (var cell : SolarStructure.BODY) context.setBlockState(ORIGIN.add(cell.offset()), ModIndustry.COMPONENT_BLOCKS.get(cell.component()));
        for (var cell : SolarStructure.RESONATORS) resonator(context, ORIGIN.add(cell.offset()), SolarStructure.resonatorFacing(cell));
    }
    private static SolarConcentratorBlockEntity resonator(TestContext context, BlockPos pos, Direction facing) {
        context.setBlockState(pos, ModIndustry.COMPONENT_BLOCKS.get("optical_resonator").getDefaultState().with(LaserComponentBlock.FACING, facing));
        return (SolarConcentratorBlockEntity) context.getBlockEntity(pos);
    }
    private static SolarConcentratorBlockEntity formed(TestContext context) {
        buildBody(context);
        var source = resonator(context, SOURCE, Direction.NORTH);
        context.assertTrue(SolarStructure.form(source), "The 3 x 3 x 3 + 4 structure is complete");
        return source;
    }
}

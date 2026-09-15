package net.askcraft.justifylasers.integration;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.entity.RefocusingCubeEntity;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.laser.LaserDamage;
import net.askcraft.justifylasers.network.LaserSettingsPacket;
import net.askcraft.justifylasers.platform.GameVersion;
import net.askcraft.justifylasers.platform.Platform;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModEntities;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class LaserIntegrationChecks {
    public static void verifyMekanismCable(TestContext context) {
        if (!Platform.isModLoaded("mekanism")) {
            context.complete();
            return;
        }
        var world = context.getWorld();
        var cube = Registries.BLOCK.get(
                GameVersion.id("mekanism", "creative_energy_cube")).getDefaultState();
        var facing = cube.getProperties().stream()
                .filter(property -> property.getName().equals("facing"))
                .map(DirectionProperty.class::cast).findFirst().orElseThrow();
        BlockPos supply = context.getAbsolutePos(new BlockPos(1, 4, 3));
        world.setBlockState(supply, cube.with(facing, Direction.EAST));
        var cable = Registries.BLOCK.get(
                GameVersion.id("mekanism", "basic_universal_cable")).getDefaultState();
        world.setBlockState(supply.east(), cable);
        world.setBlockState(supply.east(2), cable);
        world.setBlockState(supply.east(3), ModBlocks.POWERED_LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST));
        LaserEmitterBlockEntity emitter = (LaserEmitterBlockEntity) world.getBlockEntity(supply.east(3));
        emitter.setStack(0, new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.RED)));
        emitter.setStack(7, new ItemStack(ModLaserParts.ADVANCED_RANGE_MODULE, 64));
        emitter.setStack(8, new ItemStack(ModLaserParts.MODULES.get(LaserModule.THICKNESS), 64));
        BlockPos receiverPos = supply.east(6);
        world.setBlockState(receiverPos, ModBlocks.ENERGY_RECEIVER.getDefaultState()
                .with(net.askcraft.justifylasers.block.LaserOpticBlock.FACING, Direction.WEST));
        world.setBlockState(receiverPos.south(), cable);
        world.setBlockState(receiverPos.south(2), cable);
        world.setBlockState(receiverPos.south(3), ModBlocks.POWERED_LASER_EMITTER.getDefaultState());
        var sink = (LaserEmitterBlockEntity) world.getBlockEntity(receiverPos.south(3));
        sink.getPropertyDelegate().set(0, 0);
        try {
            Object source = world.getBlockEntity(supply);
            Object container = source.getClass().getMethod("getEnergyContainer").invoke(source);
            Object capacity = container.getClass().getMethod("getMaxEnergy").invoke(container);
            java.util.Arrays.stream(container.getClass().getMethods())
                    .filter(method -> method.getName().equals("setEnergy") && method.getParameterCount() == 1)
                    .findFirst().orElseThrow().invoke(container, capacity);
            Object config = source.getClass().getMethod("getConfig").invoke(source);
            Class<?> transmission = Class.forName("mekanism.common.lib.transmitter.TransmissionType");
            Class<?> side = Class.forName("mekanism.api.RelativeSide");
            Class<?> dataType = Class.forName("mekanism.common.tile.component.config.DataType");
            Object energyType = transmission.getField("ENERGY").get(null);
            Object front = side.getField("FRONT").get(null);
            Object energyConfig = config.getClass().getMethod("getConfig", transmission).invoke(config, energyType);
            var setSide = java.util.Arrays.stream(energyConfig.getClass().getMethods())
                    .filter(method -> method.getName().equals("setDataType") && method.getParameterCount() == 2)
                    .findFirst().orElseThrow();
            Object sides = front;
            if (setSide.getParameterTypes()[1].isArray()) {
                sides = java.lang.reflect.Array.newInstance(side, 1);
                java.lang.reflect.Array.set(sides, 0, front);
            }
            setSide.invoke(energyConfig, dataType.getField("OUTPUT").get(null), sides);
            energyConfig.getClass().getMethod("setEjecting", boolean.class).invoke(energyConfig, true);
            config.getClass().getMethod("sideChanged", transmission, side).invoke(config, energyType, front);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot fill the Mekanism test energy cube", exception);
        }
        context.runAtTick(30, () -> {
            check(context, world.getBlockState(supply).isOf(cube.getBlock()), "Mekanism energy source remains in place: "
                    + world.getBlockState(supply) + " at " + supply);
            check(context, emitter.energy().stored() > 0, "Mekanism transfers power through two Universal Cables into the emitter");
            check(context, emitter.isBeamActive(), "Cable-powered laser operates without direct energy injection: stored="
                    + emitter.energy().stored() + ", cost=" + emitter.energyCost() + ", mode=" + LaserConfig.technicalMode()
                    + ", crystal=" + (emitter.crystal() != null) + ", enabled=" + emitter.getPropertyDelegate().get(0));
            check(context, emitter.energyCost() == LaserConfig.get().basePerTick + 576, "Mekanism supplies a beam with the full module surcharge");
            check(context, sink.energy().stored() > 0,
                    "The laser receiver exports recovered power through a separate Universal Cable network");
            context.complete();
        });
    }

    public static void verify(TestContext context) {
        verifyDecorations(context);
        verifyWorkshopAndDualControls(context);
        IntegrationEnergy.tablet();
        boolean initialMode = LaserConfig.technicalMode();
        check(context, context.getWorld().getRecipeManager().get(JustifyLasers.id("laser_emitter")).isPresent() != initialMode,
                "Creative emitter cannot be crafted in technical mode");
        check(context, context.getWorld().getRecipeManager().get(JustifyLasers.id("powered_laser_emitter")).isEmpty(),
                "Powered emitters no longer bypass industrial assembly");
        check(context, context.getWorld().getRecipeManager().get(JustifyLasers.id("industry/powered_laser_emitter")).isPresent(),
                "Native recipe manager loads blueprint assembly");
        if (Platform.isModLoaded("mekanism")) check(context, initialMode, "Mekanism activates technical mode");
        try {
            LaserConfig.applyServerMode(true);
            verifyIndustry(context);
            verifyEnergyOutput(context);
            var world = context.getWorld();
            BlockPos relative = new BlockPos(1, 4, 3);
            BlockPos source = context.getAbsolutePos(relative);
            BlockPos target = context.getAbsolutePos(new BlockPos(4, 4, 3));
            context.setBlockState(relative, ModBlocks.POWERED_LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST));
            LaserEmitterBlockEntity emitter = (LaserEmitterBlockEntity) world.getBlockEntity(source);
            check(context, emitter != null && emitter.isPoweredEmitter(), "Powered block and block entity are registered");
            check(context, IntegrationEnergy.receive(emitter, 500, true) == 500 && emitter.energy().stored() == 0, "Simulated transfer cannot create energy");
            check(context, IntegrationEnergy.receive(emitter, 10000, false) == 10000 && emitter.energy().stored() == 10000, "Native sided energy capability accepts power");
            IntegrationEnergy.roundTrip(emitter);
            check(context, emitter.energy().stored() == 10000, "Energy persists through native block entity serialization");
            tick(emitter);
            check(context, !emitter.isBeamActive() && emitter.energy().stored() == 10000, "No crystal means no beam and no consumption");
            emitter.setStack(0, new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.CYAN)));
            check(context, emitter.crystal() != null && emitter.getColor() == LaserColor.CYAN, "Crystal controls color");
            var player = IntegrationEnergy.player(context);
            player.setPosition(Vec3d.ofCenter(source));
            emitter.initializeOwner(player);
            check(context, emitter.togglePrivacy(player), "Owner can make native emitter private");
            var guest = IntegrationEnergy.serverPlayer(context);
            guest.setPosition(player.getPos());
            check(context, !emitter.canAccess(guest), "Creative mode alone does not bypass privacy");
            check(context, !guest.interactionManager.tryBreakBlock(source) && world.getBlockEntity(source) == emitter,
                    "Server mining hook protects a private emitter on this loader");
            check(context, emitter.getAvailableSlots(Direction.DOWN).length == 0, "Private modules are not exposed to hoppers");
            check(context, emitter.togglePrivacy(player) && emitter.canAccess(guest), "Owner can restore public access");
            IntegrationEnergy.roundTrip(emitter);
            check(context, emitter.canManageSecurity(player), "Owner UUID survives native serialization");
            check(context, emitter.getBeamRange() == 1 && emitter.getBeamWidthScale() == 0.1F, "Native powered emitter starts with minimum geometry");
            emitter.setStack(7, new ItemStack(ModLaserParts.ADVANCED_RANGE_MODULE, 64));
            emitter.setStack(8, new ItemStack(ModLaserParts.MODULES.get(LaserModule.THICKNESS), 64));
            IntegrationEnergy.roundTrip(emitter);
            check(context, emitter.getBeamRange() == 512 && emitter.getBeamWidthScale() == 10, "Native full-stack upgrades survive serialization");
            check(context, emitter.energyCost() == LaserConfig.get().basePerTick + 576, "Native module energy cost survives serialization");
            emitter.removeStack(7);
            emitter.removeStack(8);
            check(context, emitter.energyCost() == LaserConfig.get().basePerTick, "Removing native upgrades restores base consumption");
            var menu = new LaserEmitterScreenHandler(42, player.getInventory(), emitter);
            player.currentScreenHandler = menu;
            check(context, new LaserSettingsPacket(42, 1).apply(player), "Powered redstone control is available");
            tick(emitter);
            check(context, !emitter.isBeamActive() && emitter.energy().stored() == 10000, "Redstone wait cannot drain power");
            emitter.getPropertyDelegate().set(1, 0);
            for (int id : new int[]{2, 3, 4, 7, 9, 10, 11, 2005, 6000, 5008, 1100}) check(context, !new LaserSettingsPacket(42, id).apply(player), "Reject unavailable control " + id);
            emitter.getPropertyDelegate().set(12, 1);
            emitter.getPropertyDelegate().set(15, 1);
            check(context, !emitter.ignitesEntities() && !emitter.hasSilkTouch() && !emitter.dropsBlocks() && !emitter.showsScorchMarks(), "Modules gate runtime effects");
            for (LaserModule module : LaserModule.values()) emitter.setStack(module.slot(), new ItemStack(ModLaserParts.MODULES.get(module)));
            emitter.setStack(LaserModule.RANGE.slot(), new ItemStack(ModLaserParts.ADVANCED_RANGE_MODULE, 8));
            IntegrationEnergy.roundTrip(emitter);
            for (LaserModule module : LaserModule.values()) check(context, emitter.hasModule(module), "Module survives save: " + module);
            check(context, emitter.crystal().color() == LaserColor.CYAN, "Crystal survives save");
            check(context, emitter.ignitesEntities() && emitter.hasSilkTouch() && emitter.dropsBlocks() && emitter.showsScorchMarks(), "Installed modules enable effects");
            var cube = new RefocusingCubeEntity(ModEntities.REFOCUSING_CUBE, world);
            cube.setPosition(Vec3d.ofCenter(source.up(3)));
            check(context, world.spawnEntity(cube) && cube.isCollidable(), "Physical cube spawns");
            check(context, cube.asItemStack().isOf(ModEntities.REFOCUSING_CUBE_ITEM), "Cube converts back to item");
            cube.discard();
            var damage = LaserDamage.source(world, Vec3d.ofCenter(source));
            check(context, damage.isIn(DamageTypeTags.BYPASSES_COOLDOWN) && !damage.isIn(DamageTypeTags.BYPASSES_ARMOR), "Damage data pack is loaded and respects armor");
            emitter.getPropertyDelegate().set(3, 1);
            emitter.getPropertyDelegate().set(14, 100);
            emitter.getPropertyDelegate().set(4, 0);
            emitter.getPropertyDelegate().set(13, 8);
            emitter.energy().restore(100000);
            world.setBlockState(target, Blocks.STONE.getDefaultState());
            // Contain the mining beam after its target breaks; adjacent tests share the world.
            world.setBlockState(target.east(), Blocks.BEDROCK.getDefaultState());
            context.runAtTick(3, () -> {
                try {
                    check(context, emitter.isBeamActive(), "A powered beam activates on the server");
                    check(context, emitter.energy().stored() < 100000, "Active emitter consumes energy");
                    check(context, world.isAir(target), "Powered beam mines");
                    check(context, world.getEntitiesByClass(ItemEntity.class, new Box(target).expand(1),
                            item -> item.getStack().isOf(Items.STONE)).size() == 1, "Silk Touch resolves native block loot");
                    emitter.energy().restore(0);
                    emitter.setStack(0, ItemStack.EMPTY);
                    check(context, !emitter.isBeamActive(), "Removing crystal stops all beam effects immediately");
                    check(context, menu.quickMove(player, LaserModule.IGNITION.slot()).getCount() == 1, "Module can be shift-clicked out");
                    check(context, !emitter.hasModule(LaserModule.IGNITION), "Shift-click cannot duplicate modules");
                    world.breakBlock(source, true);
                    check(context, world.getEntitiesByClass(ItemEntity.class, new Box(source).expand(2),
                            item -> item.getStack().isOf(ModLaserParts.MODULES.get(LaserModule.SILK_TOUCH))).size() == 1,
                            "Breaking emitter returns installed modules exactly once");
                    context.complete();
                } finally {
                    LaserConfig.applyServerMode(initialMode);
                }
            });
        } catch (Throwable failure) {
            LaserConfig.applyServerMode(initialMode);
            throw failure;
        }
    }

    private static void verifyWorkshopAndDualControls(TestContext context) {
        var player = IntegrationEnergy.player(context);
        var industry = net.askcraft.justifylasers.industry.IndustryRecipe.all(context.getWorld()).stream()
                .filter(recipe -> recipe.kind() == net.askcraft.justifylasers.industry.MachineKind.ASSEMBLY_CHAMBER).toList();
        check(context, industry.size() == 26 && industry.stream().map(r -> r.ticks()).distinct().count() == 26
                && industry.stream().map(r -> r.energy()).distinct().count() == 26, "Native recipe codec preserves all distinct assembly costs");
        for (var old : new net.minecraft.item.Item[]{net.askcraft.justifylasers.registry.ModIndustry.LASER_CHASSIS,
                net.askcraft.justifylasers.registry.ModIndustry.OPTICAL_ASSEMBLY}) {
            var stack = new ItemStack(old, 7);
            var data = GameVersion.itemData(stack); data.putString("MigrationNote", "keep"); GameVersion.setItemData(stack, data);
            player.getInventory().setStack(9, stack);
            old.inventoryTick(stack, context.getWorld(), player, 9, false);
            var updated = player.getInventory().getStack(9);
            check(context, updated.getItem() != old && updated.getCount() == 7 && GameVersion.itemData(updated).getString("MigrationNote").equals("keep"),
                    "Native legacy item migration preserves quantity and NBT/components");
        }
        for (var hand : net.minecraft.util.Hand.values()) {
            player.setStackInHand(hand, new ItemStack(ModBlocks.LASER_GUN));
            new net.askcraft.justifylasers.network.LaserGunControlPacket(hand, true).apply(player);
        }
        check(context, net.askcraft.justifylasers.item.LaserGunItem.isFiring(player, net.minecraft.util.Hand.MAIN_HAND)
                && net.askcraft.justifylasers.item.LaserGunItem.isFiring(player, net.minecraft.util.Hand.OFF_HAND), "Both native gun triggers start");
        net.askcraft.justifylasers.item.LaserGunItem.stopFiring(player, net.minecraft.util.Hand.MAIN_HAND);
        check(context, net.askcraft.justifylasers.item.LaserGunItem.isFiring(player, net.minecraft.util.Hand.OFF_HAND), "Stopping one native trigger preserves the other");
        net.askcraft.justifylasers.item.LaserGunItem.stopFiring(player, net.minecraft.util.Hand.OFF_HAND);
        for (var hand : net.minecraft.util.Hand.values()) {
            var saber = new ItemStack(ModBlocks.LASER_SABER);
            net.askcraft.justifylasers.item.LaserSaberItem.setActive(saber, true); player.setStackInHand(hand, saber);
            check(context, net.askcraft.justifylasers.laser.SaberCombat.swing(player, hand), "Native saber accepts " + hand);
        }
        var packet = new net.askcraft.justifylasers.network.SaberStatePacket(player.getId(), 1,
                net.askcraft.justifylasers.laser.SaberCombat.state(player, net.minecraft.util.Hand.OFF_HAND),
                net.askcraft.justifylasers.laser.SaberCombat.Contact.NONE, Vec3d.ZERO, 0x00FFFF, net.minecraft.util.Hand.OFF_HAND);
        var buffer = new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            packet.write(buffer);
            check(context, packet.equals(new net.askcraft.justifylasers.network.SaberStatePacket(buffer)), "Native offhand saber packet round-trip");
        } finally { buffer.release(); }
        for (var hand : net.minecraft.util.Hand.values()) player.setStackInHand(hand, ItemStack.EMPTY);
        net.askcraft.justifylasers.laser.SaberCombat.tick(player);
    }

    private static void verifyIndustry(TestContext context) {
        var world = context.getWorld();
        var origin = context.getAbsolutePos(new BlockPos(6, 5, 6));
        var block = net.askcraft.justifylasers.registry.ModIndustry.MACHINES.get(net.askcraft.justifylasers.industry.MachineKind.CRYSTAL_GROWER);
        for (var pos : net.askcraft.justifylasers.industry.ChamberStructure.positions(origin)) world.setBlockState(pos, block.getDefaultState());
        var controller = (net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity) world.getBlockEntity(origin);
        net.askcraft.justifylasers.industry.ChamberStructure.form(controller);
        var member = (net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity) world.getBlockEntity(origin.add(1, 1, 1));
        check(context, member.formed() && member.controller() == controller, "Eight native casing entities share one controller");
        for (var pos : net.askcraft.justifylasers.industry.ChamberStructure.positions(origin)) for (Direction side : Direction.values()) {
            controller.drainWater(controller.water(), false);
            controller.setStack(0, ItemStack.EMPTY);
            IntegrationEnergy.industrialPorts((net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity) world.getBlockEntity(pos), side);
        }
        check(context, controller.water() == 750 && controller.getStack(0).getCount() == 1,
                "Native fluid and item transfers reach the shared controller");
        IntegrationEnergy.roundTrip(controller);
        check(context, controller.water() == 750 && controller.getStack(0).getCount() == 1,
                "Tank and automated inventory survive native serialization");
        for (var pos : net.askcraft.justifylasers.industry.ChamberStructure.positions(origin)) world.setBlockState(pos, Blocks.AIR.getDefaultState());
    }

    private static void verifyDecorations(TestContext context) {
        var world = context.getWorld();
        BlockPos support = context.getAbsolutePos(new BlockPos(3, 2, 1));
        BlockPos target = support.up();
        var player = IntegrationEnergy.serverPlayer(context);
        player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        player.setPosition(Vec3d.ofCenter(support.north(2)));
        player.setYaw(0);
        world.setBlockState(support, Blocks.STONE.getDefaultState());
        for (var item : ModLaserParts.items()) {
            check(context, item instanceof net.askcraft.justifylasers.item.LaserPartItem, "Native part registration");
            var part = (net.askcraft.justifylasers.item.LaserPartItem) item;
            ItemStack stack = new ItemStack(item);
            player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, stack);
            var hit = new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(support).add(0, 0.5, 0), Direction.UP, support, false);
            check(context, stack.useOnBlock(new net.minecraft.item.ItemUsageContext(player, net.minecraft.util.Hand.MAIN_HAND, hit)).isAccepted(), "Native placement " + part.partId());
            check(context, stack.isEmpty() && world.getBlockState(target).isOf(part.getBlock()), "Native placement consumes exactly one part");
            check(context, world.getBlockEntity(target) instanceof net.askcraft.justifylasers.block.entity.LaserPartBlockEntity, "Native decoration block entity");
            check(context, part.getBlock().asItem() == item && world.getBlockState(target).getLuminance() == 0, "Original pick item and no vanilla lighting");
            player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
            check(context, player.interactionManager.tryBreakBlock(target), "Native survival decoration mining");
            var drops = world.getEntitiesByClass(ItemEntity.class, new Box(target).expand(0.5), entity -> entity.getStack().isOf(item));
            check(context, drops.size() == 1 && drops.get(0).getStack().getCount() == 1, "Native decoration loot returns exactly one " + part.partId());
            drops.forEach(ItemEntity::discard);
        }
        for (Direction face : Direction.values()) {
            BlockPos mounted = support.offset(face);
            world.setBlockState(mounted, Blocks.AIR.getDefaultState());
            ItemStack crystal = new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.BLUE));
            player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, crystal);
            var hit = new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(support).add(Vec3d.of(face.getVector()).multiply(0.5)), face, support, false);
            check(context, crystal.useOnBlock(new net.minecraft.item.ItemUsageContext(player, net.minecraft.util.Hand.MAIN_HAND, hit)).isAccepted(), "Crystal mounts on " + face);
            check(context, world.getBlockState(mounted).get(net.askcraft.justifylasers.block.LaserPartBlock.MOUNT) == face, "Crystal tip points away from " + face.getOpposite());
            world.setBlockState(mounted, Blocks.AIR.getDefaultState());
        }
        player.discard();
    }

    private static void verifyEnergyOutput(TestContext context) {
        var world = context.getWorld();
        BlockPos receiverPos = context.getAbsolutePos(new BlockPos(6, 4, 1));
        world.setBlockState(receiverPos, ModBlocks.ENERGY_RECEIVER.getDefaultState()
                .with(net.askcraft.justifylasers.block.LaserOpticBlock.FACING, Direction.NORTH));
        world.setBlockState(receiverPos.east(), ModBlocks.POWERED_LASER_EMITTER.getDefaultState());
        var receiver = (net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity) world.getBlockEntity(receiverPos);
        var sink = (LaserEmitterBlockEntity) world.getBlockEntity(receiverPos.east());
        receiver.receiveBeam(500, LaserColor.CYAN.rgb());
        Platform.exportEnergy(receiver);
        check(context, receiver.energy().stored() == 0 && sink.energy().stored() == 500,
                "Native receiver exports paid beam energy to adjacent native energy storage");
        receiver.energy().restore(1000);
        for (Direction side : Direction.values()) {
            receiver.setPortMode(side, net.askcraft.justifylasers.laser.OpticPortMode.OUTPUT);
            var cached = IntegrationEnergy.extractor(receiver, side);
            check(context, cached != null, "Native output on " + side);
            for (var mode : new net.askcraft.justifylasers.laser.OpticPortMode[]{
                    net.askcraft.justifylasers.laser.OpticPortMode.INPUT, net.askcraft.justifylasers.laser.OpticPortMode.DISABLED}) {
                receiver.setPortMode(side, mode);
                check(context, IntegrationEnergy.extractor(receiver, side) == null, "No native energy access through " + mode);
                check(context, cached.applyAsLong(1) == 0, "Cached native storage respects " + mode + " on " + side);
            }
            receiver.setPortMode(side, net.askcraft.justifylasers.laser.OpticPortMode.OUTPUT);
            check(context, cached.applyAsLong(1) == 1, "Reopening native output restores extraction");
        }
        check(context, IntegrationEnergy.extractor(receiver, null) == null, "No unsided bypass");
        receiver.setPortMode(Direction.EAST, net.askcraft.justifylasers.laser.OpticPortMode.DISABLED);
        receiver.setPortMode(Direction.UP, net.askcraft.justifylasers.laser.OpticPortMode.INPUT);
        IntegrationEnergy.roundTrip(receiver);
        check(context, receiver.portMode(Direction.EAST) == net.askcraft.justifylasers.laser.OpticPortMode.DISABLED
                && receiver.portMode(Direction.UP) == net.askcraft.justifylasers.laser.OpticPortMode.INPUT, "Native port state persists");
        Platform.exportEnergy(receiver);
        check(context, sink.energy().stored() == 500, "Auto-export obeys a disabled side");
        receiver.setPortMode(Direction.EAST, net.askcraft.justifylasers.laser.OpticPortMode.OUTPUT);
        Platform.exportEnergy(receiver);
        check(context, sink.energy().stored() == 1494 && receiver.energy().stored() == 0, "Re-enabled native output resumes transfer without loss or duplication");
        world.setBlockState(receiverPos, Blocks.AIR.getDefaultState());
        world.setBlockState(receiverPos.east(), Blocks.AIR.getDefaultState());
    }

    private static void tick(LaserEmitterBlockEntity emitter) {
        LaserEmitterBlockEntity.serverTick(emitter.getWorld(), emitter.getPos(), emitter.getCachedState(), emitter);
    }

    private static void check(TestContext context, boolean value, String message) {
        context.assertTrue(value, message);
    }
}

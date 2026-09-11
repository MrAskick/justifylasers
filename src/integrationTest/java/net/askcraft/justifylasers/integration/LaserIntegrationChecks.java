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
            context.complete();
        });
    }

    public static void verify(TestContext context) {
        verifyDecorations(context);
        boolean initialMode = LaserConfig.technicalMode();
        check(context, context.getWorld().getRecipeManager().get(JustifyLasers.id("laser_emitter")).isPresent() != initialMode,
                "Creative emitter cannot be crafted in technical mode");
        check(context, context.getWorld().getRecipeManager().get(JustifyLasers.id("powered_laser_emitter")).isPresent() == initialMode,
                "Powered recipe follows server technical mode");
        if (Platform.isModLoaded("mekanism")) check(context, initialMode, "Mekanism activates technical mode");
        try {
            LaserConfig.applyServerMode(true);
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
        player.discard();
    }

    private static void tick(LaserEmitterBlockEntity emitter) {
        LaserEmitterBlockEntity.serverTick(emitter.getWorld(), emitter.getPos(), emitter.getCachedState(), emitter);
    }

    private static void check(TestContext context, boolean value, String message) {
        context.assertTrue(value, message);
    }
}

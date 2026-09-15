package net.askcraft.justifylasers.integration;

import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.util.math.Direction;
import team.reborn.energy.api.EnergyStorage;

public final class IntegrationEnergy {
    public static net.minecraft.server.network.ServerPlayerEntity serverPlayer(net.minecraft.test.TestContext context) {
        var player = context.createMockCreativeServerPlayerInWorld();
        player.setUuid(java.util.UUID.randomUUID());
        return player;
    }

    public static net.minecraft.entity.player.PlayerEntity player(net.minecraft.test.TestContext context) {
        return context.createMockSurvivalPlayer();
    }

    public static long receive(LaserEmitterBlockEntity emitter, int amount, boolean simulate) {
        var port = EnergyStorage.SIDED.find(emitter.getWorld(), emitter.getPos(), Direction.UP);
        if (port == null) throw new AssertionError("Missing energy port");
        try (Transaction transaction = Transaction.openOuter()) {
            long received = port.insert(amount, transaction);
            if (!simulate) transaction.commit();
            return received;
        }
    }

    public static java.util.function.LongUnaryOperator extractor(
            net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity receiver, Direction side) {
        var port = EnergyStorage.SIDED.find(receiver.getWorld(), receiver.getPos(), side);
        if (port == null) return null;
        return amount -> {
            try (Transaction transaction = Transaction.openOuter()) {
                long extracted = port.extract(amount, transaction);
                transaction.commit();
                return extracted;
            }
        };
    }


    public static void industrialPorts(net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity member, Direction side) {
        var fluid = net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.find(member.getWorld(), member.getPos(), side);
        var items = net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(member.getWorld(), member.getPos(), side);
        if (fluid == null || items == null) throw new AssertionError("Missing native industrial ports");
        var water = net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(net.minecraft.fluid.Fluids.WATER);
        var raw = net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(net.askcraft.justifylasers.registry.ModIndustry.RAW_PHOTONIC_CRYSTAL);
        try (var transaction = Transaction.openOuter()) {
            if (fluid.insert(water, 81000, transaction) != 81000 || items.insert(raw, 1, transaction) != 1)
                throw new AssertionError("Native transfers failed");
        }
        if (member.water() != 0 || !member.getStack(0).isEmpty()) throw new AssertionError("Aborted transfer mutated controller");
        try (var transaction = Transaction.openOuter()) {
            fluid.insert(water, 81000, transaction);
            fluid.extract(water, 20250, transaction);
            items.insert(raw, 1, transaction);
            transaction.commit();
        }
    }

    public static void tablet() {
        var inventory = new net.minecraft.inventory.SimpleInventory(new net.minecraft.item.ItemStack(
                net.askcraft.justifylasers.registry.ModIndustry.EXTRATERRESTRIAL_TABLET));
        var context = net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext.ofSingleSlot(
                net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage.of(inventory, null).getSlot(0));
        var port = context.find(EnergyStorage.ITEM);
        if (port == null) throw new AssertionError("Missing native tablet energy storage");
        try (var transaction = Transaction.openOuter()) {
            if (port.insert(1000, transaction) != 256) throw new AssertionError("Tablet transfer limit");
        }
        if (port.getAmount() != 0) throw new AssertionError("Simulated charge mutated tablet");
        try (var transaction = Transaction.openOuter()) {
            if (port.insert(100, transaction) != 100 || port.extract(100, transaction) != 0)
                throw new AssertionError("Native tablet charge/extraction");
            transaction.commit();
        }
        if (port.getAmount() != 100 || net.askcraft.justifylasers.item.ExtraterrestrialTabletItem.charge(context.getItemVariant().toStack()) != 100)
            throw new AssertionError("Native tablet charge did not persist in the item");
    }

    public static void roundTrip(net.askcraft.justifylasers.platform.LaserBlockEntity emitter) {
        var saved = emitter.createNbt();
        emitter.readNbt(saved);
    }
}

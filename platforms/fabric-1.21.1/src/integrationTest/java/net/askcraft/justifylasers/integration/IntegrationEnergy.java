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
        return context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
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

    public static void roundTrip(LaserEmitterBlockEntity emitter) {
        var saved = emitter.createNbt(emitter.getWorld().getRegistryManager());
        emitter.readNbt(saved, emitter.getWorld().getRegistryManager());
    }
}

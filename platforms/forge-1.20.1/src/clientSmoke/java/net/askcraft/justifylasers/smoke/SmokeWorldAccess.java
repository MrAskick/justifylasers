package net.askcraft.justifylasers.smoke;

import net.minecraft.client.MinecraftClient;

public final class SmokeWorldAccess {
    public static void warmGenerator(net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity generator) {
        var hot=generator.createNbt();
        hot.putInt("Temperature",12000);hot.putInt("Fuel",12000);hot.putInt("FuelTotal",20000);
        hot.putInt("FuelMaxTemperature",12000);hot.putInt("FuelEfficiency",95);
        generator.readNbt(hot);
    }

    public static net.minecraft.loot.LootTable loot(net.minecraft.server.world.ServerWorld world, String table) {
        return world.getServer().getLootManager().getLootTable(new net.minecraft.util.Identifier("minecraft", table));
    }

    public static void start(MinecraftClient client) {
        client.createIntegratedServerLoader().start(client.currentScreen, "cube-smoke");
    }
}

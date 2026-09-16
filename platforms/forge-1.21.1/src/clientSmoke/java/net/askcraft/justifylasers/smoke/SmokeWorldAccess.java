package net.askcraft.justifylasers.smoke;

import net.minecraft.client.MinecraftClient;

public final class SmokeWorldAccess {
    public static void warmGenerator(net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity generator) {
        var hot=generator.createNbt(generator.getWorld().getRegistryManager());
        hot.putInt("Temperature",12000);hot.putInt("Fuel",12000);hot.putInt("FuelTotal",20000);
        hot.putInt("FuelMaxTemperature",12000);hot.putInt("FuelEfficiency",95);
        generator.readNbt(hot, generator.getWorld().getRegistryManager());
    }

    public static net.minecraft.loot.LootTable loot(net.minecraft.server.world.ServerWorld world, String table) {
        return world.getServer().getReloadableRegistries().getLootTable(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.LOOT_TABLE, net.minecraft.util.Identifier.of("minecraft", table)));
    }

    public static void start(MinecraftClient client) {
        client.createIntegratedServerLoader().start("cube-smoke", () -> client.setScreen(null));
    }
}

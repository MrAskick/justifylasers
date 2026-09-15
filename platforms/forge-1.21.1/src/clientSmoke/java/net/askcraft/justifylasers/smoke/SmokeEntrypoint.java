package net.askcraft.justifylasers.smoke;

import net.minecraft.client.MinecraftClient;
import net.minecraftforge.fml.common.Mod;

@Mod("justifylasers_client_smoke")
public class SmokeEntrypoint {
    public SmokeEntrypoint() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.TickEvent.ClientTickEvent event) -> {
                    if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) LaserClientSmoke.tick(MinecraftClient.getInstance());
                });
    }
}

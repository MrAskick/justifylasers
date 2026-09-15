package net.askcraft.justifylasers.client;

import net.askcraft.justifylasers.laser.SaberCombat;
import net.askcraft.justifylasers.laser.SaberState;
import net.askcraft.justifylasers.network.SaberStatePacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import org.joml.Vector3f;

public final class SaberFencingClient {
    public static void receive(SaberStatePacket packet) {
        var client = MinecraftClient.getInstance();
        if (client.world == null || !(client.world.getEntityById(packet.entity()) instanceof PlayerEntity player)) return;
        var s = packet.state();
        var previous = SaberCombat.state(player, packet.hand());
        SaberCombat.acceptState(player, packet.hand(), new SaberState(s.action(), s.cut(), s.stage(), client.world.getTime() - (packet.time() - s.started()),
                s.windup(), s.active(), s.recovery(), s.stamina(), s.capacity(), s.sequence(), s.staff()));
        if (s.action() == SaberState.Action.ATTACK) net.askcraft.justifylasers.client.render.LaserSaberRenderer.synchronizeAttack(player, packet.hand(), SaberCombat.state(player, packet.hand()).started());
        if (s.action() == SaberState.Action.ATTACK && s.sequence() != previous.sequence()) LaserSoundController.saberSwing(player.getEyePos(), s.staff());
        if (packet.contact() == SaberCombat.Contact.NONE) return;
        if (packet.contact() == SaberCombat.Contact.HIT) LaserSoundController.saberContact(packet.point());
        else LaserSoundController.saberClash(packet.point(), packet.contact() == SaberCombat.Contact.PARRY);
        if (!ClientSettings.get().saberSparks) return;
        int rgb = packet.rgb();
        var color = new Vector3f((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F);
        for (int i = 0; i < (packet.contact() == SaberCombat.Contact.PARRY ? 16 : 9); i++) {
            var r = client.world.random;
            var p = packet.point().add((r.nextDouble() - .5) * .22, (r.nextDouble() - .5) * .22, (r.nextDouble() - .5) * .22);
            client.world.addParticle(new DustParticleEffect(color, .7F), p.x, p.y, p.z, 0, 0, 0);
            if (i < 4) client.world.addParticle(net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z,
                    (r.nextDouble() - .5) * .3, r.nextDouble() * .16, (r.nextDouble() - .5) * .3);
        }
    }

    private SaberFencingClient() { }
}

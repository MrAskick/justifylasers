package net.askcraft.justifylasers.client;

import net.askcraft.justifylasers.item.LaserSaberItem;
import net.askcraft.justifylasers.laser.SaberCombat;
import net.askcraft.justifylasers.laser.SaberState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Arm;
import net.askcraft.justifylasers.laser.WeaponHands;

import java.util.Locale;

public final class SaberHud {
    public static void render() {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden || client.currentScreen != null
                || client.player.isSpectator()) return;
        var context = new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers());
        for (Hand hand : Hand.values()) if (LaserSaberItem.active(client.player.getStackInHand(hand))) renderHand(client, context, hand);
        context.draw();
    }

    private static void renderHand(MinecraftClient client, DrawContext context, Hand hand) {
        var state = SaberCombat.state(client.player, hand);
        int x = client.getWindow().getScaledWidth() / 2 - 30, y = client.getWindow().getScaledHeight() / 2 + 29;
        if (WeaponHands.dual(client.player)) x += WeaponHands.arm(client.player, hand) == Arm.RIGHT ? 38 : -38;
        context.fill(x - 1, y - 1, x + 61, y + 4, 0x88051019);
        int width = (int)(60 * Math.max(0, Math.min(1, state.stamina() / state.capacity())));
        int rgb = state.action() == SaberState.Action.BROKEN ? 0xFFE97048 : state.stamina() < state.capacity() * .25F ? 0xFFE9BB68 : 0xFF8AE5EF;
        context.fill(x, y, x + width, y + 2, rgb);
        if (state.action() != SaberState.Action.IDLE) {
            String phase = state.action() == SaberState.Action.ATTACK
                    ? state.elapsed(client.world.getTime()) < state.windup() ? "windup"
                    : state.activeAt(client.world.getTime()) ? "attack" : "recovery"
                    : state.action().name().toLowerCase(Locale.ROOT);
            var label = Text.translatable("gui.justifylasers.saber." + phase);
            context.drawText(client.textRenderer, label, x + 30 - client.textRenderer.getWidth(label) / 2, y + 7, 0xAACEDEE4, false);
        }
    }

    private SaberHud() { }
}

package net.askcraft.justifylasers.client.screen;

import net.askcraft.justifylasers.client.ConfiguratorControls;
import net.askcraft.justifylasers.energy.RechargeableItem;
import net.askcraft.justifylasers.item.LaserConfiguratorItem;
import net.askcraft.justifylasers.network.ConfiguratorModePacket;
import net.askcraft.justifylasers.platform.ClientPlatform;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

public class ConfiguratorRadialScreen extends Screen {
    private static final TechGui.Icon[] ICONS = {TechGui.Icon.SETTINGS, TechGui.Icon.COPY, TechGui.Icon.PASTE,
            TechGui.Icon.AIM, TechGui.Icon.ROTATE, TechGui.Icon.BATTERY};
    private final Hand hand;
    private final long opened = Util.getMeasuringTimeMs();
    private long closing;
    private int slot, current, hovered = -1;
    private float scale, centerX, centerY;
    private boolean cancelled;

    public ConfiguratorRadialScreen(Hand hand) {
        super(Text.translatable("gui.justifylasers.configurator.title"));
        this.hand = hand;
    }

    @Override protected void init() {
        slot = hand == Hand.MAIN_HAND ? client.player.getInventory().selectedSlot : 40;
        current = LaserConfiguratorItem.mode(client.player.getStackInHand(hand));
        centerX = width / 2F;
        centerY = (height - 28) / 2F;
        scale = Math.min(1, Math.min((height - 44) / 310F, (width - 20) / 310F));
        scale = Math.max(.4F, scale);
    }

    /** Dead centre and outside the ring cancel selection; sector zero points straight up. */
    public static int selection(double x, double y) {
        double radius = Math.hypot(x, y);
        if (radius < 43 || radius > 122) return -1;
        double angle = (Math.toDegrees(Math.atan2(y, x)) + 120 + 360) % 360;
        return (int) (angle / 60);
    }

    @Override public void tick() {
        if (client.player == null || !client.player.isAlive() || !(client.player.getStackInHand(hand).getItem() instanceof LaserConfiguratorItem)
                || slot != (hand == Hand.MAIN_HAND ? client.player.getInventory().selectedSlot : 40)) { cancelled = true; finish(false); }
        else if (!selectionHeld()) finish(true);
        if (closing != 0 && Util.getMeasuringTimeMs() - closing >= 160) client.setScreen(null);
    }

    protected boolean selectionHeld() { return ConfiguratorControls.selectionHeld(client); }

    private void finish(boolean choose) {
        if (closing != 0) return;
        closing = Util.getMeasuringTimeMs();
        if (choose && !cancelled && hovered >= 0) {
            ClientPlatform.sendConfiguratorMode(new ConfiguratorModePacket(hand, slot, hovered));
            current = hovered;
            client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(
                    net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, .9F));
        }
    }

    @Override public boolean keyReleased(int key, int scanCode, int modifiers) {
        if (net.askcraft.justifylasers.client.ClientSettingsKey.CONFIGURATOR.matchesKey(key, scanCode)) {
            finish(true); return true;
        }
        return super.keyReleased(key, scanCode, modifiers);
    }

    @Override public void close() { cancelled = true; finish(false); }
    @Override public boolean shouldPause() { return false; }
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = Util.getMeasuringTimeMs();
        float progress = Math.min(1, (now - opened) / 200F);
        float opening = 1 - (float) Math.pow(1 - progress, 3);
        float alpha = closing == 0 ? opening : Math.max(0, 1 - (now - closing) / 160F);
        if (alpha < .02F) return;
        float size = scale * (.86F + .14F * opening) * (closing == 0 ? 1 : .9F + .1F * alpha);
        if (closing == 0) hovered = selection((mouseX - centerX) / size, (mouseY - centerY) / size);
        context.fill(0, 0, width, height, tint(0x06141E, 68 * alpha));
        var matrices = context.getMatrices();
        matrices.push(); matrices.translate(centerX, centerY, 0); matrices.scale(size, size, 1);
        TechGui.annulus(context, 0, 0, 0, 39, 0, 360, tint(0x0B2332, 232 * alpha), tint(0x031320, 230 * alpha));
        for (int i = 0; i < LaserConfiguratorItem.MODE_COUNT; i++) {
            float angle = -90 + i * 60;
            boolean selected = hovered == i;
            int color = LaserConfiguratorItem.color(i);
            TechGui.annulus(context, 0, 0, 43, 108, angle - 28.5F, angle + 28.5F,
                    tint(selected ? color : 0x08293E, (selected ? 85 : 222) * alpha), tint(0x031522, 235 * alpha));
            TechGui.annulus(context, 0, 0, selected ? 106 : 107, 109.5F, angle - 28, angle + 28,
                    tint(color, (selected ? 250 : 140) * alpha), tint(color, 80 * alpha));
            TechGui.annulus(context, 0, 0, 44, 45, angle - 27, angle + 27, tint(color, 90 * alpha), tint(color, 150 * alpha));
            if (selected) TechGui.annulus(context, 0, 0, 110, 116, angle - 27, angle + 27,
                    tint(color, 50 * alpha), tint(color, 0));
            float x = (float) Math.cos(Math.toRadians(angle)) * 76;
            float y = (float) Math.sin(Math.toRadians(angle)) * 76;
            ICONS[i].draw(context, x - 12, y - 16, 24, selected ? TechGui.State.HOVERED : TechGui.State.NORMAL, alpha, 0xFF000000 | color);
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.justifylasers.configurator.short." + i),
                    Math.round(x), Math.round(y + 13), tint(selected ? 0xFFFFFF : 0xC5DCE5, 255 * alpha));
            if (current == i) TechGui.annulus(context, x, y + 28, 0, 1.5F, 0, 360, tint(color, 255 * alpha), tint(color, 255 * alpha));
        }
        int shown = hovered >= 0 ? hovered : current;
        int color = LaserConfiguratorItem.color(shown);
        for (int i = 0; i < 48; i++) TechGui.annulus(context, 0, 0, 117, i % 4 == 0 ? 121 : 119, i * 7.5F, i * 7.5F + 1,
                tint(0x70B5CB, 100 * alpha), tint(0x70B5CB, 30 * alpha));
        ICONS[shown].draw(context, -11, -26, 22, TechGui.State.SELECTED, alpha, 0xFF000000 | color);
        var stack = client.player == null ? net.minecraft.item.ItemStack.EMPTY : client.player.getStackInHand(hand);
        int charge = RechargeableItem.stored(stack);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(charge * 100 / LaserConfiguratorItem.CAPACITY + "%"), 0, 1, tint(color, 255 * alpha));
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("20 FE"), 0, 16, tint(0x8BADC1, 240 * alpha));
        TechGui.annulus(context, 0, 0, 38.5F, 40, -90, -90 + 360F * charge / LaserConfiguratorItem.CAPACITY,
                tint(color, 255 * alpha), tint(color, 150 * alpha));
        context.drawCenteredTextWithShadow(textRenderer, title, 0, -140, tint(0x91C2D5, 255 * alpha));
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("item.justifylasers.configurator.mode." + shown),
                0, 128, tint(color, 255 * alpha));
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.justifylasers.configurator.release",
                        net.askcraft.justifylasers.client.ClientSettingsKey.CONFIGURATOR.getBoundKeyLocalizedText()),
                0, 141, tint(0xAEC4CE, 230 * alpha));
        context.draw(); matrices.pop();
    }

    private static int tint(int rgb, float alpha) { return Math.max(0, Math.min(255, (int) alpha)) << 24 | rgb; }
}

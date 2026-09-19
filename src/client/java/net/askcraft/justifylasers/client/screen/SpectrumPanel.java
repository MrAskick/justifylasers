package net.askcraft.justifylasers.client.screen;

import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.laser.LaserSpectrum;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/** Shared spectrum controls for an installed module and a world-mounted one. */
final class SpectrumPanel {
    private final int x, y;
    private final IntSupplier source;
    private final Consumer<String> send;
    private final List<Channel> channels = new ArrayList<>();
    private final TextFieldWidget hex;
    private int rgb, pending = -1, timeout;
    private boolean changingText;

    SpectrumPanel(int x, int y, IntSupplier source, Consumer<ClickableWidget> add, Consumer<String> send) {
        this.x = x; this.y = y; this.source = source; this.send = send; rgb = source.getAsInt();
        var presets = new ArrayList<Integer>(); var names = new ArrayList<Text>();
        for (var color : LaserColor.values()) { presets.add(color.rgb()); names.add(Text.translatable(color.translationKey())); }
        int width = 190 / presets.size();
        for (int i = 0; i < presets.size(); i++) {
            int color = presets.get(i);
            var button = new ButtonWidget(x + i * width, y, width - 1, 12, names.get(i), b -> choose(color), supplier -> supplier.get()) {
                public void renderButton(DrawContext context, int mx, int my, float delta) { renderWidget(context, mx, my, delta); }
                public void renderWidget(DrawContext context, int mx, int my, float delta) {
                    TechGui.button(context, getX(), getY(), this.width, height, TechGui.State.of(true, rgb == color, isHovered(), isFocused(), false));
                    context.fill(getX() + 3, getY() + 3, getX() + this.width - 3, getY() + height - 3, 0xFF000000 | color);
                }
            };
            button.setTooltip(Tooltip.of(names.get(i))); add.accept(button);
        }
        for (int i = 0; i < 3; i++) { var channel = new Channel(i); channels.add(channel); add.accept(channel); }
        hex = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, x + 150, y + 49, 40, 12, Text.translatable("gui.justifylasers.spectrum.hex"));
        hex.setMaxLength(6); hex.setDrawsBackground(false); hex.setEditableColor(0xD6EDF4); updateText();
        hex.setChangedListener(value -> {
            if (changingText) return;
            int parsed = LaserSpectrum.parse(value);
            hex.setEditableColor(parsed < 0 ? 0xFF6268 : 0xD6EDF4);
            if (parsed >= 0) { rgb = parsed; updateChannels(); submit(); }
        });
        add.accept(hex);
    }
    private void choose(int color) { rgb = color; updateChannels(); updateText(); submit(); }
    private void updateChannels() { channels.forEach(Channel::refresh); }
    private void updateText() { changingText = true; hex.setText(String.format(Locale.ROOT, "%06X", rgb)); changingText = false; }
    private void submit() { pending = rgb; timeout = 40; send.accept(String.format(Locale.ROOT, "%06X", rgb)); }
    void tick() {
        if (channels.stream().anyMatch(channel -> channel.dragging) || hex.isFocused()) return;
        int current = source.getAsInt();
        if (pending >= 0 && pending != current && --timeout > 0) return;
        pending = -1;
        if (current != rgb) { rgb = current; updateChannels(); updateText(); }
    }
    void render(DrawContext context) {
        PoweredLaserEmitterScreen.panel(context, x + 148, y + 16, 42, 29, false);
        context.fillGradient(x + 151, y + 19, x + 187, y + 42, 0xFF000000 | rgb, 0xA0000000 | rgb);
        context.fill(x + 153, y + 28, x + 185, y + 31, 0xFFF5FFFF);
    }
    boolean keyPressed(int key, int scan, int modifiers) {
        if (!hex.isFocused() || key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) return false;
        hex.keyPressed(key, scan, modifiers);
        return true;
    }
    boolean drag(double mx, double my, int button, double dx, double dy) {
        for (var channel : channels) if (button == 0 && channel.dragging) return channel.mouseDragged(mx, my, button, dx, dy);
        return false;
    }
    boolean release(double mx, double my, int button) {
        for (var channel : channels) if (button == 0 && channel.dragging) { channel.onRelease(mx, my); return true; }
        return false;
    }
    private final class Channel extends SliderWidget {
        private final int shift, index;
        private boolean dragging;
        Channel(int index) {
            super(x, y + 16 + index * 16, 140, 12, Text.empty(), (rgb >> (16 - index * 8) & 255) / 255.0);
            this.index = index; shift = 16 - index * 8; updateMessage();
        }
        void refresh() { value = (rgb >> shift & 255) / 255.0; updateMessage(); }
        @Override protected void updateMessage() { setMessage(Text.literal("RGB".substring(index, index + 1) + "  " + Math.round(value * 255))); }
        @Override protected void applyValue() { rgb = rgb & ~(255 << shift) | (int)Math.round(value * 255) << shift; updateMessage(); updateText(); if (!dragging) submit(); }
        @Override public void onClick(double mx, double my) { dragging = true; super.onClick(mx, my); }
        @Override public void onRelease(double mx, double my) { super.onRelease(mx, my); dragging = false; submit(); }
        public void renderButton(DrawContext context, int mx, int my, float delta) { renderWidget(context, mx, my, delta); }
        public void renderWidget(DrawContext context, int mx, int my, float delta) {
            TechGui.slider(context, getX(), getY(), width, height, value, TechGui.State.of(true, dragging, isHovered(), isFocused(), false));
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, getMessage(), getX() + width / 2, getY() + 2, 0xD6EDF4);
        }
    }
}

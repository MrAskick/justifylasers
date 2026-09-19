package net.askcraft.justifylasers.client.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class TechTextButton extends ButtonWidget {
    private final Supplier<Text> label;
    private final BooleanSupplier selected;
    private final BooleanSupplier available;
    private TechGui.Icon icon;
    private BooleanSupplier indicator;

    TechTextButton(int x, int y, int width, int height, Supplier<Text> label, BooleanSupplier selected, BooleanSupplier available, Runnable action) {
        super(x, y, width, height, label.get(), button -> action.run(), DEFAULT_NARRATION_SUPPLIER);
        this.label = label; this.selected = selected; this.available = available;
    }
    TechTextButton icon(TechGui.Icon value) { icon = value; return this; }
    TechTextButton indicator(BooleanSupplier value) { indicator = value; return this; }
    public void renderButton(DrawContext context, int mx, int my, float delta) { renderWidget(context, mx, my, delta); }
    public void renderWidget(DrawContext context, int mx, int my, float delta) {
        setMessage(label.get()); active = available.getAsBoolean();
        var state = TechGui.State.of(active, false, isHovered(), isFocused(), selected.getAsBoolean());
        TechGui.button(context, getX(), getY(), width, height, state);
        var renderer = MinecraftClient.getInstance().textRenderer;
        boolean stacked = icon != null && height >= 28;
        int iconSize = height >= 40 ? 20 : 14;
        if (icon != null) icon.draw(context, getX() + (width - iconSize) / 2F, getY() + (height >= 40 ? 5 : 3), iconSize, state);
        if (indicator != null) TechGui.indicator(context, getX() + width - 11, getY() + 8, indicator.getAsBoolean(), active);
        float scale = Math.min(stacked ? .7F : .85F, (width - 12F) / Math.max(1, renderer.getWidth(getMessage())));
        var matrices = context.getMatrices();
        matrices.push(); matrices.translate(getX() + width / 2F, getY() + (stacked ? height >= 40 ? 29 : 18 : (height - 8 * scale) / 2F), 0); matrices.scale(scale, scale, 1);
        context.drawCenteredTextWithShadow(renderer, getMessage(), 0, 0, active ? 0xD7EDF5 : 0x57727F);
        matrices.pop();
    }
}

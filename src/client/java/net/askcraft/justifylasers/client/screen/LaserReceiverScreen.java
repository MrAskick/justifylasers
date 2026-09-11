package net.askcraft.justifylasers.client.screen;

import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.askcraft.justifylasers.screen.LaserReceiverScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class LaserReceiverScreen extends HandledScreen<LaserReceiverScreenHandler> {
    private ButtonWidget enabledButton;
    private ButtonWidget invertedButton;
    private ButtonWidget filterButton;
    private ButtonWidget emissionButton;
    private ButtonWidget decreaseButton;
    private ButtonWidget increaseButton;

    public LaserReceiverScreen(LaserReceiverScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 276;
        backgroundHeight = 258;
        titleX = 18;
        titleY = 13;
    }

    @Override
    protected void init() {
        backgroundHeight = Math.min(258, height - 12);
        super.init();
        int left = x + 22;
        int width = backgroundWidth - 44;
        enabledButton = addDrawableChild(button(left, y + 48, width, LaserReceiverScreenHandler.BUTTON_ENABLED));
        decreaseButton = addDrawableChild(button(left, y + 76, 22, LaserReceiverScreenHandler.BUTTON_SIGNAL_DOWN));
        increaseButton = addDrawableChild(button(left + width - 22, y + 76, 22, LaserReceiverScreenHandler.BUTTON_SIGNAL_UP));
        decreaseButton.setMessage(Text.literal("−"));
        increaseButton.setMessage(Text.literal("+"));
        decreaseButton.setTooltip(Tooltip.of(Text.translatable("gui.justifylasers.receiver.strength.tooltip")));
        increaseButton.setTooltip(Tooltip.of(Text.translatable("gui.justifylasers.receiver.strength.tooltip")));
        invertedButton = addDrawableChild(button(left, y + 104, width, LaserReceiverScreenHandler.BUTTON_INVERTED));
        invertedButton.setTooltip(Tooltip.of(Text.translatable("gui.justifylasers.receiver.inverted.tooltip")));
        filterButton = addDrawableChild(button(left, y + 132, width, LaserReceiverScreenHandler.BUTTON_COLOR_FILTER));
        filterButton.setTooltip(Tooltip.of(Text.translatable("gui.justifylasers.receiver.filter.tooltip")));
        emissionButton = addDrawableChild(button(left, y + 160, width, LaserReceiverScreenHandler.BUTTON_LIGHT_EMISSION));
        emissionButton.setTooltip(Tooltip.of(Text.translatable("gui.justifylasers.receiver.emission.tooltip")));
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.justifylasers.close"), button -> close())
                .dimensions(x + backgroundWidth - 72, y + backgroundHeight - 26, 54, 18).build());
        updateMessages();
    }

    private ButtonWidget button(int x, int y, int width, int id) {
        return ButtonWidget.builder(Text.empty(), button -> {
            if (client != null && client.interactionManager != null) {
                client.interactionManager.clickButton(handler.syncId, id);
            }
        }).dimensions(x, y, width, 20).build();
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        updateMessages();
    }

    private void updateMessages() {
        enabledButton.setMessage(toggle("gui.justifylasers.enabled", handler.isEnabled()));
        invertedButton.setMessage(toggle("gui.justifylasers.receiver.inverted", handler.isInverted()));
        emissionButton.setMessage(toggle("gui.justifylasers.light_emission", handler.isLightEmissionEnabled()));
        int filter = handler.getColorFilter();
        Text filterName = filter == 0 ? Text.translatable("gui.justifylasers.receiver.any_color")
                : Text.translatable(LaserColor.byIndex(filter - 1).translationKey())
                        .styled(style -> style.withColor(LaserColor.byIndex(filter - 1).rgb()));
        filterButton.setMessage(Text.translatable("gui.justifylasers.receiver.filter", filterName));
        decreaseButton.active = handler.getSignalStrength() > 0;
        increaseButton.active = handler.getSignalStrength() < 15;
    }

    private static Text toggle(String key, boolean enabled) {
        return Text.translatable(key, Text.translatable(enabled ? "gui.justifylasers.on" : "gui.justifylasers.off")
                .formatted(enabled ? Formatting.GREEN : Formatting.RED));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        RenderVersion.screenBackground(this, context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xF20B0F15);
        context.fillGradient(x + 2, y + 2, x + backgroundWidth - 2, y + backgroundHeight - 2, 0xF2252B35, 0xF211151C);
        int color = handler.hasBeam() ? handler.getReceivedColor().rgb() : 0x47515F;
        context.fill(x + 2, y + 2, x + backgroundWidth - 2, y + 4, 0xFF000000 | color);
        context.fill(x + 10, y + 38, x + backgroundWidth - 10, y + 39, 0xFF47515F);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawTextWithShadow(textRenderer, title, titleX, titleY, 0xFFF3F7FF);
        Text status = handler.hasBeam()
                ? Text.translatable("gui.justifylasers.receiver.receiving", Text.translatable(handler.getReceivedColor().translationKey())
                        .styled(style -> style.withColor(handler.getReceivedColor().rgb())))
                : Text.translatable("gui.justifylasers.receiver.no_beam").formatted(Formatting.GRAY);
        context.drawTextWithShadow(textRenderer, status, 18, 28, 0xFFF3F7FF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("gui.justifylasers.receiver.strength", handler.getSignalStrength()), backgroundWidth / 2, 82, 0xFF55FFFF);
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.justifylasers.receiver.output", handler.getOutputPower()),
                22, 188, handler.getOutputPower() > 0 ? 0xFF55FF55 : 0xFF98A4B5);
        if (backgroundHeight >= 250) {
            context.drawText(textRenderer, Text.translatable("gui.justifylasers.receiver.sides_hint"), 22, 205, 0xFF98A4B5, false);
        }
    }
}

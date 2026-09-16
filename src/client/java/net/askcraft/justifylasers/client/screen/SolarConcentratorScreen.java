package net.askcraft.justifylasers.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.client.render.SolarConcentratorPreviewRenderer;
import net.askcraft.justifylasers.laser.LuminousFlux;
import net.askcraft.justifylasers.platform.Platform;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.askcraft.justifylasers.screen.SolarConcentratorScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class SolarConcentratorScreen extends HandledScreen<SolarConcentratorScreenHandler> {
    private static final Identifier BACKGROUND = JustifyLasers.id("textures/gui/powered_emitter.png");
    private enum Page { MAIN, SECURITY, REDSTONE }
    private Page page = Page.MAIN;

    public SolarConcentratorScreen(SolarConcentratorScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 320; backgroundHeight = 234;
    }

    @Override protected void init() {
        super.init();
        button(8, 31, 41, 28, page == Page.MAIN ? TechGui.Icon.POWER : TechGui.Icon.BACK,
                () -> page == Page.MAIN ? Text.translatable("gui.justifylasers.industry.toggle") : powered("back"),
                () -> true, () -> page == Page.MAIN && handler.enabled(), () -> { if (page == Page.MAIN) send(0); else open(Page.MAIN); });
        button(272, 31, 40, 44, TechGui.Icon.SHIELD, () -> powered("security"), () -> true,
                () -> page == Page.SECURITY, () -> open(Page.SECURITY));
        button(272, 81, 40, 44, TechGui.Icon.REDSTONE, () -> powered("redstone"), () -> true,
                () -> page == Page.REDSTONE, () -> open(Page.REDSTONE));
        if (page == Page.SECURITY)
            button(65, 73, 189, 20, null, () -> Text.translatable("gui.justifylasers.powered.access", powered(handler.isPrivate() ? "private" : "public")),
                    handler::canManageSecurity, handler::isPrivate, () -> send(2));
        if (page == Page.REDSTONE)
            button(65, 57, 189, 22, null, () -> Text.translatable(handler.redstoneMode().translationKey()),
                    () -> true, () -> false, () -> send(1));
        button(13, 129, 23, 21, TechGui.Icon.MODULES, () -> Text.translatable("gui.justifylasers.jei.construction"),
                net.askcraft.justifylasers.client.compat.RecipeNavigation::available, () -> false,
                net.askcraft.justifylasers.client.compat.RecipeNavigation::showConstruction);
    }

    private void send(int id) { if (client.interactionManager != null) client.interactionManager.clickButton(handler.syncId, id); }
    private void open(Page next) { page = next; setFocused(null); setDragging(false); clearAndInit(); }
    private static Text powered(String key) { return Text.translatable("gui.justifylasers.powered." + key); }
    private static Text label(String key, Object... arguments) { return Text.translatable("gui.justifylasers.solar." + key, arguments); }

    private void button(int bx, int by, int w, int h, TechGui.Icon icon, Supplier<Text> label,
                        BooleanSupplier available, BooleanSupplier selected, Runnable action) {
        addDrawableChild(new ButtonWidget(x + bx, y + by, w, h, label.get(), button -> action.run(), supplier -> supplier.get()) {
            public void renderButton(DrawContext context, int mx, int my, float delta) { renderWidget(context, mx, my, delta); }
            public void renderWidget(DrawContext context, int mx, int my, float delta) {
                active = available.getAsBoolean(); setMessage(label.get());
                var state = TechGui.State.of(active, selected.getAsBoolean(), isHovered(), isFocused(), false);
                TechGui.button(context, getX(), getY(), width, height, state);
                if (icon != null) {
                    int size = Math.min(19, Math.min(width, height) - 7);
                    icon.draw(context, getX() + (width - size) / 2F, getY() + (height - size) / 2F, size, state);
                    setTooltip(Tooltip.of(getMessage()));
                } else fitted(context, getMessage(), getX() + 7, getY() + (height - 8) / 2, width - 14, active ? 0xBBECF1 : 0x65838D);
            }
        });
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        RenderVersion.screenBackground(this, context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
        if (page == Page.MAIN && mouseX >= x + 154 && mouseX < x + 255 && mouseY >= y + 43 && mouseY < y + 101)
            context.drawTooltip(textRenderer, label("flux_hint", handler.luminousFlux(), LuminousFlux.format(handler.peakFlux())), mouseX, mouseY);
    }

    @Override protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.draw();
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.setShaderColor(1, 1, 1, 1);
        try { context.drawTexture(BACKGROUND, x, y, backgroundWidth, backgroundHeight, 0, 0, 1, 1, 1, 1); }
        finally { RenderSystem.disableBlend(); }
        context.fill(x + 56, y + 31, x + 264, y + 125, 0xB3021825);
        context.fill(x + 56, y + 31, x + 264, y + 32, 0xFF25849C);
        for (var slot : handler.slots) {
            int sx = x + slot.x, sy = y + slot.y;
            context.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xC5266880);
            context.fill(sx, sy, sx + 16, sy + 16, 0xCE031521);
        }
        if (page == Page.MAIN) {
            context.fill(x + 149, y + 40, x + 150, y + 116, 0xFF185063);
            context.fill(x + 154, y + 95, x + 254, y + 100, 0xC0010E19);
            int fill = 98 * handler.utilization() / 1000;
            if (fill > 0) context.fillGradient(x + 155, y + 96, x + 155 + fill, y + 99, 0xFFFFF3C8, 0xFFB19254);
            SolarConcentratorPreviewRenderer.render(context, x, y, handler.luminousFlux() > 0, handler.small());
        }
    }

    @Override protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        float scale = Math.min(1, 144F / Math.max(1, textRenderer.getWidth(title)));
        fitted(context, title, 160 - Math.round(textRenderer.getWidth(title) * scale / 2), 16, 144, 0xB5F0F8);
        if (page == Page.MAIN) {
            fitted(context, label("collector"), 66, 36, 77, 0x86B9CA);
            fitted(context, label("range", handler.beamRange()), 66, 114, 77, 0x86B9CA);
            fitted(context, label("flux"), 154, 39, 100, 0x86B9CA);
            fitted(context, Text.literal(LuminousFlux.format(handler.luminousFlux())), 154, 51, 100, 0xFFF3C8);
            fitted(context, label("conversion", handler.convertedEnergyRate(), Platform.ENERGY_UNIT), 154, 67, 100, 0x8CFCE8);
            fitted(context, label("utilization", String.format(Locale.ROOT, "%.1f", handler.utilization() / 10.0)), 154, 82, 100, 0x86B9CA);
            fitted(context, Text.translatable("status.justifylasers.solar." + handler.status().name().toLowerCase(Locale.ROOT)),
                    154, 108, 100, handler.luminousFlux() > 0 ? 0x8CFCE8 : 0xBBECF1);
        } else {
            fitted(context, powered(page == Page.SECURITY ? "security" : "redstone_title"), 65, 39, 189, 0xBBECF1);
            if (page == Page.SECURITY) {
                fitted(context, powered("owner").copy().append(": " + (handler.ownerName().isEmpty() ? "—" : handler.ownerName())), 65, 55, 189, 0x86B9CA);
                fitted(context, powered(handler.canManageSecurity() ? "security_hint" : "owner_only"), 65, 100, 189, 0x86B9CA);
                fitted(context, label("privacy_scope"), 65, 114, 189, 0x618E9F);
            } else {
                fitted(context, Text.translatable("gui.justifylasers.powered.signal", powered(handler.hasRedstoneSignal() ? "detected" : "absent")),
                        65, 87, 189, 0x86B9CA);
                fitted(context, label("redstone_hint"), 65, 107, 189, 0x618E9F);
            }
        }
    }

    private void fitted(DrawContext context, Text text, int left, int top, int width, int rgb) {
        float scale = Math.min(1, width / (float) Math.max(1, textRenderer.getWidth(text)));
        context.getMatrices().push();
        context.getMatrices().translate(left, top, 0); context.getMatrices().scale(scale, scale, 1);
        context.drawText(textRenderer, text, 0, 0, rgb, false);
        context.getMatrices().pop();
    }
}

package net.askcraft.justifylasers.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.industry.MachineKind;
import net.askcraft.justifylasers.client.compat.RecipeNavigation;
import net.askcraft.justifylasers.platform.Platform;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.askcraft.justifylasers.screen.IndustrialMachineScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Locale;

public final class IndustrialMachineScreen extends HandledScreen<IndustrialMachineScreenHandler> {
    private static final Identifier BACKGROUND = JustifyLasers.id("textures/gui/powered_emitter.png");

    public IndustrialMachineScreen(IndustrialMachineScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 320; backgroundHeight = 234;
    }

    private enum Page { MAIN, SECURITY, REDSTONE }
    private Page page = Page.MAIN;

    @Override protected void init() {
        super.init();
        handler.setMachineSlotsVisible(page == Page.MAIN);
        button(8, 31, 41, 28, page == Page.MAIN ? TechGui.Icon.POWER : TechGui.Icon.BACK,
                () -> Text.translatable(page == Page.MAIN ? "gui.justifylasers.industry.toggle" : "gui.justifylasers.powered.back"),
                () -> true, () -> { if (page == Page.MAIN) send(0); else open(Page.MAIN); });
        button(272, 31, 40, 44, TechGui.Icon.SHIELD, () -> label("security"), () -> true, () -> open(Page.SECURITY));
        button(272, 81, 40, 44, TechGui.Icon.REDSTONE, () -> label("redstone"), () -> true, () -> open(Page.REDSTONE));
        if (page == Page.SECURITY)
            button(65, 75, 189, 20, null,
                    () -> Text.translatable("gui.justifylasers.powered.access", label(handler.isPrivate() ? "private" : "public")),
                    handler::canManageSecurity, () -> send(2));
        if (page == Page.REDSTONE)
            button(65, 58, 189, 22, null, () -> Text.translatable(handler.redstoneMode().translationKey()),
                    () -> true, () -> send(1));
        if (handler.kind().multiblock())
            button(13, 129, 23, 21, TechGui.Icon.MODULES, () -> Text.translatable("gui.justifylasers.jei.construction"),
                    RecipeNavigation::available, RecipeNavigation::showConstruction);
    }

    private static Text label(String key) { return Text.translatable("gui.justifylasers.powered." + key); }
    private void send(int id) { if (client.interactionManager != null) client.interactionManager.clickButton(handler.syncId, id); }
    private void open(Page next) { page = next; setFocused(null); setDragging(false); clearAndInit(); }

    private void button(int bx, int by, int w, int h, TechGui.Icon icon, java.util.function.Supplier<Text> label,
                        java.util.function.BooleanSupplier available, Runnable action) {
        addDrawableChild(new ButtonWidget(x + bx, y + by, w, h, label.get(), b -> action.run(), supplier -> supplier.get()) {
            public void renderButton(DrawContext context, int mx, int my, float delta) { renderWidget(context, mx, my, delta); }
            public void renderWidget(DrawContext context, int mx, int my, float delta) {
                active = available.getAsBoolean(); setMessage(label.get());
                var state = TechGui.State.of(active, false, isHovered(), isFocused(), false);
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
        if (page != Page.MAIN) return;
        if (RecipeNavigation.available() && handler.kind() != MachineKind.FUEL_GENERATOR
                && inside(mouseX, mouseY, arrowX() - 3, 68, 16, 19))
            context.drawTooltip(textRenderer, Text.translatable("gui.justifylasers.jei.recipes"), mouseX, mouseY);
        if (inside(mouseX, mouseY, 63, 102, 193, 8))
            context.drawTooltip(textRenderer, handler.kind() == MachineKind.CRYSTAL_GROWER
                    ? Text.translatable("gui.justifylasers.industry.growth_reference", net.askcraft.justifylasers.laser.LuminousFlux.format(handler.rate()))
                    : Text.literal(handler.energy() + " / " + handler.capacity() + " " + Platform.ENERGY_UNIT), mouseX, mouseY);
        if (handler.kind().fluidTank() && inside(mouseX, mouseY, 24, 70, 16, 54)) fluidTooltip(context, 0, mouseX, mouseY);
        if (handler.kind() == MachineKind.CHEMICAL_SYNTHESIZER && inside(mouseX, mouseY, 237, 63, 16, 27)) fluidTooltip(context, 1, mouseX, mouseY);
    }

    private void ghostInputs(DrawContext context) {
        var recipe = handler.recipe();
        if (recipe == null) return;
        for (int index = 0; index < recipe.inputs().size(); index++) {
            var slot = handler.getSlot(index);
            if (!slot.isEnabled() || slot.hasStack()) continue;
            var options = recipe.inputs().get(index).getMatchingStacks();
            if (options.length == 0) continue;
            var ghost = options[(int)(net.minecraft.util.Util.getMeasuringTimeMs() / 1400 % options.length)].copy();
            ghost.setCount(recipe.counts().get(index));
            context.drawItem(ghost, x + slot.x, y + slot.y);
            context.draw();
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 200);
            context.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xA3031521);
            context.getMatrices().pop();
            if (ghost.getCount() > 1) context.drawItemInSlot(textRenderer, ghost, x + slot.x, y + slot.y);
        }
    }

    @Override protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.draw();
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.setShaderColor(1, 1, 1, 1);
        try { context.drawTexture(BACKGROUND, x, y, backgroundWidth, backgroundHeight, 0, 0, 1, 1, 1, 1); }
        finally { RenderSystem.disableBlend(); }
        context.fill(x + 56, y + 31, x + 264, y + 125, 0xB3021825);
        context.fill(x + 56, y + 31, x + 264, y + 32, 0xFF25849C);
        for (var slot : handler.slots) if (slot.isEnabled()) {
            int sx = x + slot.x, sy = y + slot.y;
            context.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xC5266880);
            context.fill(sx, sy, sx + 16, sy + 16, 0xCE031521);
        }
        if (page != Page.MAIN) {
            fitted(context, label(page == Page.SECURITY ? "security" : "redstone"), x + 65, y + 39, 188, 0xBBECF1);
            if (page == Page.SECURITY)
                fitted(context, label("owner").copy().append(": " + (handler.ownerName().isEmpty() ? "—" : handler.ownerName())), x + 65, y + 56, 188, 0x86B9CA);
            return;
        }
        fitted(context, Text.translatable(handler.kind() == MachineKind.CRYSTAL_GROWER && handler.status() == IndustrialMachineBlockEntity.Status.NO_POWER
                        ? "gui.justifylasers.industry.no_light" : "gui.justifylasers.industry.status." + handler.status().name().toLowerCase(Locale.ROOT)),
                x + 64, y + 38, handler.kind().fluidTank() ? 139 : 191, handler.formed() ? 0xBBECF1 : 0xFFBE76);
        fitted(context, handler.kind() == MachineKind.CRYSTAL_GROWER
                ? Text.translatable("gui.justifylasers.industry.light_rate", net.askcraft.justifylasers.laser.LuminousFlux.format(handler.lightFlux()),
                    String.format(Locale.ROOT, "%.2f", (handler.recipe() != null && handler.recipe().process().crystal() != null ? handler.spectralFlux() : handler.lightFlux()) / (double)Math.max(1, handler.rate())))
                : Text.translatable("gui.justifylasers.industry.rate",
                handler.kind() == MachineKind.FUEL_GENERATOR ? "+" + (handler.status() == IndustrialMachineBlockEntity.Status.WORKING ? handler.rate() : 0)
                        : "−" + handler.rate(), Platform.ENERGY_UNIT), x + 64, y + 51, handler.kind().fluidTank() ? 139 : 191, 0x6CBBCA);
        if (handler.kind() == MachineKind.ASSEMBLY_CHAMBER)
            fitted(context, Text.translatable("gui.justifylasers.industry.blueprint_short"), x + 177, y + 59, 33, 0x87B6C4);
        ghostInputs(context);
        if (handler.kind() != MachineKind.FUEL_GENERATOR) context.drawText(textRenderer, Text.literal("→"), x + arrowX(), y + 74,
                RecipeNavigation.available() && inside(mouseX, mouseY, arrowX() - 3, 68, 16, 19) ? 0xFFFFFF : 0x8CF1F4, false);
        int amount = handler.kind() == MachineKind.FUEL_GENERATOR ? handler.fuel() : handler.progress();
        int maximum = handler.kind() == MachineKind.FUEL_GENERATOR ? handler.fuelTotal() : handler.duration();
        bar(context, 64, 93, 190, 3, amount, maximum, 0xFFB9F6FF, 0xFF3F9BBB);
        bar(context, 64, 103, 190, 5, handler.kind() == MachineKind.CRYSTAL_GROWER ? handler.lightFlux() : handler.energy(),
                handler.kind() == MachineKind.CRYSTAL_GROWER ? handler.rate() : handler.capacity(), 0xFF8CFCE8, 0xFF237F99);
        fitted(context, handler.kind() == MachineKind.FUEL_GENERATOR
                ? Text.translatable("gui.justifylasers.industry.generator_stats", handler.efficiency(), handler.temperature())
                : handler.kind() == MachineKind.LASER_CUTTER ? Text.translatable("gui.justifylasers.industry.cutting_flux", net.askcraft.justifylasers.laser.LuminousFlux.format(handler.lightFlux()))
                : Text.translatable("gui.justifylasers.industry.automation"), x + 64, y + 113, 192, 0x618E9F);
        if (handler.kind() == MachineKind.FUEL_GENERATOR)
            fitted(context, Text.translatable("gui.justifylasers.industry.charge"), x + 167, y + 43, 39, 0x87B6C4);
        if (handler.kind().fluidTank()) tank(context, 0, 24, 70, 54);
        if (handler.kind() == MachineKind.CHEMICAL_SYNTHESIZER) tank(context, 1, 237, 63, 27);
    }

    private void fluidTooltip(DrawContext context, int tank, int mouseX, int mouseY) {
        Text name = Text.translatable(handler.fluid(tank) == net.askcraft.justifylasers.industry.ProcessFluid.WATER
                ? "block.minecraft.water" : "block.justifylasers." + handler.fluid(tank).fluidId());
        context.drawTooltip(textRenderer, name.copy().append(": " + handler.fluidAmount(tank) + " / " + handler.tankCapacity() + " mB"), mouseX, mouseY);
    }
    private void tank(DrawContext context, int tank, int bx, int by, int height) {
        context.fill(x + bx, y + by, x + bx + 16, y + by + height, 0xBC051722);
        int fill = (int)((height - 2L) * handler.fluidAmount(tank) / Math.max(1, handler.tankCapacity()));
        int color = handler.fluid(tank).rgb();
        context.fillGradient(x + bx + 1, y + by + height - 1 - fill, x + bx + 15, y + by + height - 1, 0xE0000000 | color, 0xB0000000 | color);
        for (int line = 0; line <= 4; line++) context.fill(x + bx + 1, y + by + 1 + line * (height - 3) / 4, x + bx + 5, y + by + 2 + line * (height - 3) / 4, 0xA09AE0EF);
    }

    private int arrowX() { return handler.kind().fluidTank() ? 181 : 219; }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && page == Page.MAIN && inside((int) mouseX, (int) mouseY, arrowX() - 3, 68, 16, 19)
                && RecipeNavigation.show(handler.kind())) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void bar(DrawContext context, int bx, int by, int width, int height, long amount, int capacity, int top, int bottom) {
        context.fill(x + bx, y + by, x + bx + width, y + by + height, 0xC0010E19);
        int fill = (int)Math.min(width, Math.max(0, (long)width * amount / Math.max(1, capacity)));
        if (fill > 0) context.fillGradient(x + bx, y + by, x + bx + fill, y + by + height, top, bottom);
    }

    @Override protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        int availableWidth = 144;
        float scale = Math.min(1, availableWidth / (float)Math.max(1, textRenderer.getWidth(title)));
        fitted(context, title, 160 - Math.round(textRenderer.getWidth(title) * scale / 2), 16, availableWidth, 0xB5F0F8);
    }

    private boolean inside(int mx, int my, int bx, int by, int width, int height) {
        return mx >= x + bx && mx < x + bx + width && my >= y + by && my < y + by + height;
    }

    private void fitted(DrawContext context, Text text, int x, int y, int width, int rgb) {
        float scale = Math.min(1, width / (float)Math.max(1, textRenderer.getWidth(text)));
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0); context.getMatrices().scale(scale, scale, 1);
        context.drawText(textRenderer, text, 0, 0, rgb, false);
        context.getMatrices().pop();
    }
}

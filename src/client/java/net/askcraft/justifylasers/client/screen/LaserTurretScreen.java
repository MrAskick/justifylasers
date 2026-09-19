package net.askcraft.justifylasers.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.network.LaserSettingsPacket;
import net.askcraft.justifylasers.platform.ClientPlatform;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
import net.askcraft.justifylasers.screen.LaserTurretScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class LaserTurretScreen extends HandledScreen<LaserTurretScreenHandler> {
    private enum Page { MAIN, FILTER, TYPES }
    private Page page = Page.MAIN;
    private TextFieldWidget excludedPlayer;
    private EntityFilterPanel entityTypes;
    private String playerName = "";

    public LaserTurretScreen(LaserTurretScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 320;
        backgroundHeight = 234;
    }

    @Override protected void init() {
        if (excludedPlayer != null) playerName = excludedPlayer.getText();
        excludedPlayer = null;
        entityTypes = null;
        super.init();
        handler.showControls(page == Page.MAIN);
        tab(8, 31, "gui.justifylasers.powered.back", Page.MAIN);
        tab(8, 64, "gui.justifylasers.powered.filter", Page.FILTER);
        tab(8, 97, "gui.justifylasers.filter.mob_list", Page.TYPES);
        addDrawableChild(new TechTextButton(x + 272, y + 31, 40, 44,
                () -> text("power"), handler::enabled, () -> true, () -> command(0)).icon(TechGui.Icon.POWER).indicator(handler::enabled));
        addDrawableChild(new TechTextButton(x + 272, y + 81, 40, 44,
                () -> text("color"), () -> false, () -> handler.getSlot(0).hasStack(), () -> command(5)).icon(TechGui.Icon.GLOW));
        if (page == Page.TYPES) {
            entityTypes = new EntityFilterPanel(x + 64, y + 44, widget -> addDrawableChild(widget),
                    () -> client.world != null && client.world.getBlockEntity(handler.pos())
                            instanceof net.askcraft.justifylasers.block.entity.LaserTurretBlockEntity turret ? turret.filter() : null,
                    id -> ClientPlatform.sendSettings(new LaserSettingsPacket(handler.syncId, LaserEmitterScreenHandler.BUTTON_FILTER_TYPE, id)),
                    () -> ClientPlatform.sendSettings(new LaserSettingsPacket(handler.syncId, LaserEmitterScreenHandler.BUTTON_FILTER_MODE)));
        } else if (page == Page.FILTER) {
            String[] labels = {"hostile", "passive", "players", "owner"};
            for (int index = 0; index < labels.length; index++) {
                int flag = index;
                addDrawableChild(new TechTextButton(x + 64 + index % 2 * 98, y + 47 + index / 2 * 22, 92, 18,
                        () -> text(labels[flag]), () -> (handler.flags() & 1 << flag) != 0, handler::hasFilter, () -> command(flag + 1)));
            }
            excludedPlayer = addDrawableChild(new TextFieldWidget(textRenderer, x + 68, y + 96, 108, 14, text("player_name")));
            excludedPlayer.setMaxLength(16);
            excludedPlayer.setDrawsBackground(false);
            excludedPlayer.setEditableColor(0xD6EDF4);
            excludedPlayer.setPlaceholder(text("player_name"));
            excludedPlayer.setText(playerName);
            var exclude = addDrawableChild(new TechTextButton(x + 182, y + 92, 72, 18, () -> text("exclude"),
                    () -> false, () -> handler.hasFilter() && excludedPlayer.getText().matches("[A-Za-z0-9_]{1,16}"),
                    () -> ClientPlatform.sendSettings(new LaserSettingsPacket(handler.syncId, 6, excludedPlayer.getText()))));
            exclude.setTooltip(Tooltip.of(Text.translatable("gui.justifylasers.turret.exclude_hint", handler.exclusionCount())));
        }
    }

    private void tab(int left, int top, String key, Page target) {
        addDrawableChild(new TechTextButton(x + left, y + top, 41, 28, () -> Text.translatable(key),
                () -> page == target, () -> target == Page.MAIN || handler.hasFilter(), () -> open(target))
                .icon(target == Page.MAIN ? TechGui.Icon.BACK : target == Page.FILTER ? TechGui.Icon.SETTINGS : TechGui.Icon.MODULES));
    }
    private void open(Page next) { page = next; clearAndInit(); }
    private void command(int id) { client.interactionManager.clickButton(handler.syncId, id); }
    private static Text text(String key) { return Text.translatable("gui.justifylasers.turret." + key); }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!RenderVersion.SCREEN_RENDERS_BACKGROUND) renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
    public void renderBackground(DrawContext context) { context.fill(0, 0, width, height, 0x28050810); }
    public void renderBackground(DrawContext context, int mx, int my, float delta) { renderBackground(context); drawBackground(context, delta, mx, my); }

    @Override protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.draw();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        context.drawTexture(JustifyLasers.id("textures/gui/powered_emitter.png"), x, y, backgroundWidth, backgroundHeight, 0, 0, 1, 1, 1, 1);
        RenderSystem.disableBlend();
        PoweredLaserEmitterScreen.panel(context, x + 56, y + 31, 208, 94, false);
        for (var slot : handler.slots) if (slot.isEnabled())
            PoweredLaserEmitterScreen.slot(context, x + slot.x, y + slot.y, slot.id < 2 && slot.hasStack());
        if (entityTypes != null) entityTypes.render(context);
    }

    @Override protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        fit(context, title, 160, 14, 206, 0xD6EDF4, .95F);
        fit(context, page == Page.MAIN ? text(handler.firing() ? "firing" : "standby")
                : Text.translatable(page == Page.FILTER ? "gui.justifylasers.powered.filter" : "gui.justifylasers.filter.mob_list"),
                160, 35, 190, 0x5DDFEC, .8F);
        if (page == Page.MAIN) {
            fit(context, text("gun"), 112, 54, 64, 0xD6EDF4, .8F);
            fit(context, text("filter"), 208, 54, 64, 0xD6EDF4, .8F);
            fit(context, Text.translatable("gui.justifylasers.turret.owned_by", handler.ownerName()), 160, 106, 185, 0x7EA7B6, .75F);
        }
        if (page == Page.FILTER) fit(context, Text.translatable("gui.justifylasers.powered.excluded_count", handler.exclusionCount()),
                160, 115, 184, 0x7EA7B6, .65F);
    }

    private void fit(DrawContext context, Text text, int left, int top, int maxWidth, int color, float maxScale) {
        float scale = Math.min(maxScale, maxWidth / (float) Math.max(1, textRenderer.getWidth(text)));
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(left, top, 0);
        matrices.scale(scale, scale, 1);
        context.drawCenteredTextWithShadow(textRenderer, text, 0, 0, color);
        matrices.pop();
    }

    @Override protected void handledScreenTick() {
        super.handledScreenTick();
        if (page != Page.MAIN && !handler.hasFilter()) open(Page.MAIN);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (page != Page.MAIN && key == GLFW.GLFW_KEY_ESCAPE) { open(Page.MAIN); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
}

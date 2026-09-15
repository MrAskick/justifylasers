package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.client.render.TabletRenderer;
import net.askcraft.justifylasers.client.screen.TabletScreen;
import net.askcraft.justifylasers.item.ExtraterrestrialTabletItem;
import net.askcraft.justifylasers.platform.Platform;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.askcraft.justifylasers.screen.TabletScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.LoggerFactory;

final class TabletWorldSmoke {
    static void tick(MinecraftClient client, int tick) {
        if (tick == 30) {
            client.options.hudHidden = false;
            client.getServer().execute(() -> {
                var player = player(client);
                player.getInventory().clear();
                player.getInventory().selectedSlot = 0;
                player.getInventory().setStack(0, new ItemStack(ModIndustry.EXTRATERRESTRIAL_TABLET));
                player.getInventory().setStack(1, new ItemStack(ModIndustry.BLANK_SCHEMATIC,4));
                player.teleport(client.getServer().getOverworld(), 400, 4, 399, 0, 0);
                client.getServer().getOverworld().setTimeOfDay(9000);
            });
            client.player.getInventory().selectedSlot = 0;
        }
        if (tick == 65) client.getServer().execute(() -> Platform.openScreen(player(client),new TabletScreenHandler.Factory(0)));
        if (tick == 90) {
            if (!(client.currentScreen instanceof TabletScreen screen) || screen.getScreenHandler().charge() != 0) throw new AssertionError("Discharged tablet screen did not open");
            capture(client,"depleted");
            client.getServer().execute(() -> ExtraterrestrialTabletItem.setCharge(player(client).getMainHandStack(),42_000));
        }
        if (tick == 120) {
            if (!(client.currentScreen instanceof TabletScreen screen) || screen.getScreenHandler().charge() <= 40_000) throw new AssertionError("Tablet charge did not synchronize");
            capture(client,"active");
            click(client,55,93);
        }
        if (tick == 137) {
            var screen = (TabletScreen)client.currentScreen;
            if (screen.getScreenHandler().selected() != 1) throw new AssertionError("Projected pointer selected wrong schematic");
            var serverMenu = (TabletScreenHandler)player(client).currentScreenHandler;
            if (screen.getScreenHandler().assemblyTicks() != serverMenu.assemblyTicks()
                    || screen.getScreenHandler().assemblyRate() != serverMenu.assemblyRate())
                throw new AssertionError("Tablet must display the server's selected recipe costs");
            click(client,399,272);
        }
        if (tick == 150) {
            var screen = (TabletScreen)client.currentScreen;
            if (screen.getScreenHandler().status() != TabletScreenHandler.Status.RECORDED || screen.getScreenHandler().blanks() != 3)
                throw new AssertionError("Tablet recording failed");
            capture(client,"recorded");
            var start = locate(client,310,130); var end = locate(client,355,155);
            screen.mouseClicked(start[0],start[1],0);
            screen.mouseDragged(end[0],end[1],0,end[0]-start[0],end[1]-start[1]);
            screen.mouseReleased(end[0],end[1],0);
        }
        if (tick == 170) { capture(client,"rotated"); client.options.hudHidden = true; }
        if (tick == 185) { capture(client,"f1-hidden"); client.options.hudHidden = false; client.player.closeHandledScreen(); }
        if (tick == 200) { capture(client,"held"); client.getServer().execute(() -> player(client).setPitch(90)); }
        if (tick == 220) { capture(client,"pole"); client.getServer().execute(() -> player(client).setPitch(0)); }
        if (tick == 230) client.getServer().execute(() -> Platform.openScreen(player(client), new TabletScreenHandler.Factory(0)));
        if (tick == 245) {
            String language = System.getProperty("justifylasers.smokeLanguage", "ru_ru");
            client.getLanguageManager().setLanguage(language); client.options.language = language; client.reloadResources();
        }
        if (tick == 275) { capture(client,"localized"); client.player.closeHandledScreen(); }
        if (tick == 285) {
            client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);
            client.getServer().execute(() -> {
                var inventory = player(client).getInventory();
                inventory.setStack(40,inventory.removeStack(0));
                Platform.openScreen(player(client),new TabletScreenHandler.Factory(40));
            });
        }
        if (tick == 305) {
            if (!(client.currentScreen instanceof TabletScreen screen) || screen.getScreenHandler().tabletSlot() != 40
                    || !client.options.getPerspective().isFirstPerson()) throw new AssertionError("Offhand tablet did not enter hand view");
            capture(client,"offhand"); client.player.closeHandledScreen();
        }
        if (tick == 326) {
            if (client.options.getPerspective() != net.minecraft.client.option.Perspective.THIRD_PERSON_BACK) throw new AssertionError("Tablet did not restore the previous perspective");
            client.getServer().execute(() -> Platform.openScreen(player(client),new TabletScreenHandler.Factory(40)));
        }
        for (int component = 0; component < 5; component++) {
            if (tick == 345 + component * 20) client.interactionManager.clickButton(((TabletScreen)client.currentScreen).getScreenHandler().syncId,21+component);
            if (tick == 358 + component * 20) capture(client,"component-"+component);
        }
        if (tick == 450) {
            client.player.closeHandledScreen();
            client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
            client.setScreen(new SchematicGallery(false));
        }
        if (tick == 480) { capture(client,"schematic-gallery"); client.setScreen(new SchematicGallery(true)); }
        if (tick == 510) { capture(client,"schematic-close"); client.reloadResources(); }
        if (tick == 545) { capture(client,"schematic-reloaded"); }
        if (tick == 550) {
            LoggerFactory.getLogger("justifylasers-client-smoke").info("TABLET_WORLD_SMOKE_PASSED charge=true projectedClick=true recording=true rotation=true f1=true reload=true shader={}",
                    net.askcraft.justifylasers.client.compat.IrisCompatibility.isShaderPackInUse());
            client.scheduleStop();
        }
    }

    private static final class SchematicGallery extends net.minecraft.client.gui.screen.Screen {
        private final boolean close;
        SchematicGallery(boolean close) { super(net.minecraft.text.Text.literal("Schematics")); this.close = close; }
        @Override public boolean shouldPause() { return false; }
        @Override public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0,0,width,height,0xFF15202C);
            var items = java.util.List.copyOf(ModIndustry.BLUEPRINTS.values());
            int columns = close ? 3 : 6, rows = close ? 2 : 5, cw = width / columns, ch = height / rows;
            int count = close ? 6 : items.size();
            for (int i=0;i<count;i++) {
                int x=i%columns*cw+cw/2, y=i/columns*ch+ch/2;
                float size=close ? 92 : 40;
                context.getMatrices().push();
                context.getMatrices().translate(x-size/2,y-size/2-5,0);
                context.getMatrices().scale(size/16,size/16,size/16);
                context.drawItem(new ItemStack(items.get(i)),0,0);
                context.getMatrices().pop();
                var name=items.get(i).recipe();
                context.drawCenteredTextWithShadow(textRenderer,name,x,y+(int)size/2,0xCCDDE7);
            }
            context.draw();
        }
    }

    private static void click(MinecraftClient client, int x, int y) {
        var point = locate(client,x,y);
        ((TabletScreen)client.currentScreen).mouseClicked(point[0],point[1],0);
        ((TabletScreen)client.currentScreen).mouseReleased(point[0],point[1],0);
    }
    private static double[] locate(MinecraftClient client, int x, int y) {
        double best = Double.POSITIVE_INFINITY; double[] found = null;
        for (int py=0;py<client.getWindow().getScaledHeight();py++) for (int px=0;px<client.getWindow().getScaledWidth();px++) {
            var local = TabletRenderer.pointer(px,py);
            if (local == null) continue;
            double distance = Math.pow(local[0]-x,2)+Math.pow(local[1]-y,2);
            if(distance < best) { best = distance; found = new double[]{px,py}; }
        }
        if (found == null || best > 25) throw new AssertionError("Hand display projection is missing or outside the window: " + best);
        return found;
    }
    private static ServerPlayerEntity player(MinecraftClient client) { return client.getServer().getPlayerManager().getPlayer(client.player.getUuid()); }
    private static void capture(MinecraftClient client, String name) { ScreenshotRecorder.saveScreenshot(client.runDirectory,"tablet-"+name+".png",client.getFramebuffer(), text -> { }); }
}

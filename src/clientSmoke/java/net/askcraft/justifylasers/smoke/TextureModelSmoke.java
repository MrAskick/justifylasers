package net.askcraft.justifylasers.smoke;

import com.mojang.blaze3d.systems.RenderSystem;
import net.askcraft.justifylasers.client.render.LaserCrystalModel;
import net.askcraft.justifylasers.client.render.LaserGunModel;
import net.askcraft.justifylasers.client.render.LaserModuleModel;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class TextureModelSmoke {
    private static final String[] CATEGORIES = {"modules", "crystals", "turrets", "guns", "optics", "tools"};
    private static int ticks, stage;
    private static Gallery gallery;

    public static void tick(MinecraftClient client) {
        if (client.getOverlay() != null || ++ticks < 30) return;
        ticks = 0;
        if (gallery == null) {
            GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1600, 1000);
            client.options.pauseOnLostFocus = false;
            client.options.getGuiScale().setValue(2);
            client.onResolutionChanged();
            gallery = new Gallery();
            client.setScreen(gallery);
            verifyParticleSprites(client);
        } else {
            ScreenshotRecorder.saveScreenshot(client.runDirectory,
                    "texture-" + CATEGORIES[stage / 3] + "-" + stage % 3 + ".png", client.getFramebuffer(), text -> { });
            if (++stage == CATEGORIES.length * 3) {
                LoggerFactory.getLogger("justifylasers-client-smoke").info("TEXTURE_MODEL_SMOKE_PASSED");
                client.scheduleStop();
            }
        }
    }

    static void verifyParticleSprites(MinecraftClient client) {
        var blocks = new java.util.LinkedHashMap<String,net.minecraft.block.Block>(ModLaserParts.DECORATIONS);
        blocks.put("laser_mirror",net.askcraft.justifylasers.registry.ModBlocks.LASER_MIRROR);
        blocks.put("beam_splitter",net.askcraft.justifylasers.registry.ModBlocks.BEAM_SPLITTER);
        blocks.put("energy_receiver",net.askcraft.justifylasers.registry.ModBlocks.ENERGY_RECEIVER);
        blocks.put("laser_turret",net.askcraft.justifylasers.registry.ModBlocks.LASER_TURRET);
        blocks.forEach((id,block)-> {
            var sprite=client.getBlockRenderManager().getModel(block.getDefaultState()).getParticleSprite();
            if(!sprite.getContents().getId().toString().equals("justifylasers:block/particle/"+id))
                throw new AssertionError("Wrong baked breaking sprite for "+id+": "+sprite.getContents().getId());
        });
        LoggerFactory.getLogger("justifylasers-client-smoke").info("MODEL_PARTICLE_SPRITES_SMOKE_PASSED blocks={}",blocks.size());
    }

    private static final class Gallery extends Screen {
        private static final List<String> MODULES = List.of("block_destruction_module", "entity_damage_module", "range_module",
                "advanced_range_module", "thickness_module", "control_circuit", "silk_touch_module", "block_drops_module",
                "scorch_marks_module", "ignition_module", "target_filter_module");
        private final net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity[] optics = opticalModels();

        private Gallery() { super(Text.literal("Texture model review")); }

        @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            if (stage >= CATEGORIES.length * 3) return;
            int category = stage / 3, angle = stage % 3;
            context.fill(0, 0, width, height, 0xFF17222E);
            context.drawCenteredTextWithShadow(textRenderer, "JUSTIFY LASERS / " + CATEGORIES[category].toUpperCase(java.util.Locale.ROOT)
                    + " / " + new String[]{"FRONT", "BACK", "TOP"}[angle], width / 2, 12, 0xDFEFF8);
            int columns = category == 0 ? 4 : 3, count = category == 0 ? MODULES.size() : 9;
            int cw = width / columns, ch = (height - 38) / 3;
            for (int index = 0; index < count; index++) {
                int x = index % columns * cw + cw / 2, y = 38 + index / columns * ch;
                LaserColor color = LaserColor.byIndex(index);
                context.draw();
                var matrices = context.getMatrices();
                matrices.push();
                matrices.translate(x, y + ch * 0.46, 250);
                float scale = ch * 0.85F;
                matrices.scale(scale, -scale, scale);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(angle == 2 ? 65 : 20));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle == 1 ? -35 : 145));
                matrices.translate(-0.5, -0.5, -0.5);
                DiffuseLighting.enableGuiDepthLighting();
                RenderSystem.enableDepthTest();
                var consumers = context.getVertexConsumers();
                int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
                if (category == 0) LaserModuleModel.render(MODULES.get(index), ModelTransformationMode.GUI, matrices, consumers, light, OverlayTexture.DEFAULT_UV);
                else if (category == 1) LaserCrystalModel.render(new ItemStack(ModLaserParts.CRYSTALS.get(color)), ModelTransformationMode.GUI,
                        matrices, consumers, light, OverlayTexture.DEFAULT_UV);
                else if (category == 2) {
                    matrices.translate(0.5, 0.08, 0.5);
                    matrices.scale(1/24F, 1/24F, 1/24F);
                    LaserGunModel.renderStand(matrices, consumers, light, color.rgb(), 0);
                } else if(category==3) {
                    LaserGunModel.fitGui(matrices);
                    LaserGunModel.render(matrices, consumers, light, color.rgb());
                } else if(category==5) {
                    matrices.translate(0.18,0.23,0.18);
                    matrices.scale(0.64F,0.64F,0.64F);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((index%3)*30));
                    net.askcraft.justifylasers.client.render.LaserConfiguratorModel.render(matrices,consumers,light);
                } else {
                    int rgb=index%3==1?0xFF1717:0x26DCFF;
                    if(index<3) {
                        matrices.translate(0.5,0.5,0.5);
                        net.askcraft.justifylasers.client.render.RefocusingCubeModel.render(matrices,consumers,light,rgb,index%3!=0,true);
                    } else new net.askcraft.justifylasers.client.render.LaserOpticRenderer(null)
                            .render(optics[index-3],0,matrices,consumers,light,OverlayTexture.DEFAULT_UV);
                }
                consumers.draw();
                matrices.pop();
                RenderSystem.setShaderColor(1, 1, 1, 1);
                RenderSystem.defaultBlendFunc();
                String label = category == 0 ? MODULES.get(index).replace("_module", "").replace('_', ' ') : category==4
                        ? new String[]{"cube","mirror","splitter"}[index/3]+" / "+new String[]{"off","red","cyan"}[index%3] : category==5 ? "configurator / " + index : color.asString();
                context.drawCenteredTextWithShadow(textRenderer, label.toUpperCase(java.util.Locale.ROOT), x, y + ch - 15, 0x9BD5E7);
            }
        }

        private static net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity[] opticalModels() {
            var result=new net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity[6];
            try {
                var color=net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity.class.getDeclaredField("rgb");
                var emission=net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity.class.getDeclaredField("emission");
                color.setAccessible(true); emission.setAccessible(true);
                for(int i=0;i<6;i++) {
                    var block=i<3?net.askcraft.justifylasers.registry.ModBlocks.LASER_MIRROR:net.askcraft.justifylasers.registry.ModBlocks.BEAM_SPLITTER;
                    var state=block.getDefaultState().with(net.askcraft.justifylasers.block.LaserOpticBlock.FACING,i<3?net.minecraft.util.math.Direction.UP:net.minecraft.util.math.Direction.NORTH)
                            .with(net.askcraft.justifylasers.block.LaserOpticBlock.LIT,i%3!=0);
                    result[i]=new net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity(net.minecraft.util.math.BlockPos.ORIGIN,state);
                    color.setInt(result[i],i%3==1?0xFF1717:0x26DCFF); emission.setBoolean(result[i],true);
                    if(i<3) result[i].aim(new net.minecraft.util.math.Vec3d(0,0.32,-1));
                }
            } catch(ReflectiveOperationException e) {throw new AssertionError(e);}
            return result;
        }
    }
    private TextureModelSmoke() { }
}

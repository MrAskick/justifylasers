package net.askcraft.justifylasers.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.client.screen.TabletScreen;
import net.askcraft.justifylasers.item.ExtraterrestrialTabletItem;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class TabletRenderer {
    private static final OpticalComponentMesh BODY = body();
    private static Matrix4f screenProjection;
    private static long projectionTime;

    public static void renderFirstPerson(AbstractClientPlayerEntity player, ItemStack stack, Hand hand, float delta,
                                         MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        var client = MinecraftClient.getInstance();
        if (client.options.hudHidden || IrisCompatibility.isRenderingShadowPass()) { screenProjection = null; return; }
        boolean interacting = client.currentScreen instanceof TabletScreen;
        matrices.push();
        matrices.translate(0, interacting ? -.045 : -.41, interacting ? -1.10 : -1.17);
        float scale = interacting ? 1.3F : .94F;
        matrices.scale(scale, scale, scale);
        if (!player.isInvisible()) for (Arm arm : Arm.values()) {
            int sign = arm == Arm.RIGHT ? 1 : -1;
            LaserGunRenderer.drawArm(player, arm, new Vec3d(sign * .8, -.78, .44), new Vec3d(sign * .64, -.30, .10), matrices, consumers, light);
        }
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
        draw(stack, matrices, consumers, light, true);
        matrices.pop();
    }

    public static void renderItem(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        matrices.push(); matrices.translate(.5, .5, .5);
        if (mode == ModelTransformationMode.GROUND) matrices.scale(.75F, .75F, .75F);
        draw(stack, matrices, consumers, light, false);
        matrices.pop();
    }

    private static void draw(ItemStack stack, MatrixStack matrices, VertexConsumerProvider consumers, int light, boolean held) {
        int charge = held && MinecraftClient.getInstance().currentScreen instanceof TabletScreen screen ? screen.getScreenHandler().charge()
                : ExtraterrestrialTabletItem.charge(stack);
        BODY.render(matrices, consumers, light, 0x3FDBFF, charge > 0, true);
        matrices.push();
        // Front faces point toward -Z; both display axes must face the player after the hand transform.
        matrices.translate(.582, .371, -.056);
        matrices.scale(-.002425F, -.002425F, .002425F);
        if (held) {
            screenProjection = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix())
                    .mul(matrices.peek().getPositionMatrix()).invert();
            projectionTime = net.minecraft.util.Util.getMeasuringTimeMs();
        }
        boolean deferText = held && IrisCompatibility.isShaderPackInUse();
        if (deferText) TabletTextRenderer.beginDisplay();
        try { TabletDisplay.render(stack, charge, matrices, consumers, held); }
        finally { if (deferText) TabletTextRenderer.endDisplay(); }
        matrices.pop();
    }

    public static double[] pointer(double x, double y) {
        var client = MinecraftClient.getInstance();
        if (screenProjection == null || net.minecraft.util.Util.getMeasuringTimeMs() - projectionTime > 200 || client.options.hudHidden) return null;
        float nx = (float)(2 * x / client.getWindow().getScaledWidth() - 1), ny = (float)(1 - 2 * y / client.getWindow().getScaledHeight());
        Vector4f near = screenProjection.transform(new Vector4f(nx, ny, -1, 1)); near.div(near.w);
        Vector4f far = screenProjection.transform(new Vector4f(nx, ny, 1, 1)); far.div(far.w);
        float dz = far.z - near.z;
        if (Math.abs(dz) < 1e-7) return null;
        double t = -near.z / dz, px = near.x + (far.x - near.x) * t, py = near.y + (far.y - near.y) * t;
        return Double.isFinite(px) && Double.isFinite(py) ? new double[]{px, py} : null;
    }

    static void rectangle(MatrixStack matrices, VertexConsumerProvider consumers, double x, double y, double width, double height, double depth, int color) {
        var buffer = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(ComponentAtlas.texture("extraterrestrial_tablet", "ui")));
        for (int i = 0; i < 4; i++) RenderVersion.endVertex(RenderVersion.normal(buffer.vertex(matrices.peek().getPositionMatrix(),
                        (float)(x + (i >= 2 ? width : 0)), (float)(y + (i == 1 || i == 2 ? height : 0)), (float)depth)
                .color(color >> 16 & 255, color >> 8 & 255, color & 255, 255).texture(.5F, .5F)
                .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE), matrices.peek().getNormalMatrix(), 0, 0, -1));
    }

    static OpticalComponentMesh body() {
        var mesh = new OpticalComponentMesh.Builder("extraterrestrial_tablet");
        mesh.bevel("dark", -10.9, -7.6, -.8, 10.9, 7.6, .6, .25);
        var back = new OpticalComponentMesh.Builder("extraterrestrial_tablet");
        back.trimmedPanel("back", false, -9.5, -6.3, 9.5, 6.3, -.61);
        mesh.add(back.build(), net.minecraft.util.math.Direction.SOUTH);
        // Rails surround the recessed screen. Separate pads, vents and stepped corners follow the reference frame.
        for (int side : new int[]{-1,1}) {
            double y = side < 0 ? -7.4 : 6.35;
            mesh.bevel("metal", -8.65, y, -1.06, 8.65, y + 1.0, -.81, .07);
            for (int end : new int[]{-1,1}) {
                double vx = end < 0 ? -7.6 : 5.5;
                mesh.bevel("dark", vx - .10, y + .10, -1.15, vx + 2.2, y + .90, -1.065, .025);
                mesh.trimmedPanel("grill", false, vx, y + .15, vx + 2.1, y + .85, -1.16);
                mesh.bevel("armor", end * 8.2 - .27, y - .06, -1.20, end * 8.2 + .27, y + 1.06, -1.065, .035);
            }
            if (side > 0) {
                mesh.bevel("dark", -2.75, y -.08, -1.38, 2.75, y + 1.08, -1.065, .08);
                mesh.trimmedPanel("beam", true, -2.5, y + .08, 2.5, y + .92, -1.39);
            }
            double x = side < 0 ? -10.65 : 9.65;
            mesh.bevel("metal", x, -5.8, -1.06, x + 1.0, 5.8, -.82, .06);
            mesh.bevel("dark", x + .12, -2.2, -1.20, x + .88, 2.2, -1.065, .04);
            mesh.trimmedPanel("side_vent", false, x + .22, -1.96, x + .78, 1.96, -1.21);
            for (int end : new int[]{-1,1}) {
                double cy = end < 0 ? -7.48 : 5.98;
                mesh.bevel("dark", x -.38, cy, -1.42, x + 1.20, cy + 1.47, .24, .17);
                mesh.trimmedPanel("joint", true, x -.1, cy + .38, x + .92, cy + 1.06, -1.43);
                double outerX = side < 0 ? x -.65 : x + 1.22;
                mesh.bevel("dark", outerX, cy + .24, -1.21, outerX + .24, cy + 1.23, .1, .045);
                mesh.trimmedPanel("square_light", true, outerX + .06, cy + .53, outerX + .18, cy + .95, -1.22);
                double py = end < 0 ? -5.7 : 2.35;
                mesh.bevel("armor", x -.12, py, -1.16, x + .99, py + 3.32, -1.065, .025);
            }
            double edgeY = side < 0 ? -6.13 : 5.97;
            mesh.trimmedPanel("bezel", false, -9.5, edgeY, 9.5, edgeY + .15, -.91);
            mesh.trimmedPanel("bezel", false, side < 0 ? -9.52 : 9.35, -5.96, side < 0 ? -9.35 : 9.52, 5.96, -.91);
        }
        return mesh.build();
    }

    private TabletRenderer() { }
}

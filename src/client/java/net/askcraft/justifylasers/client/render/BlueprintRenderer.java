package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.item.AssemblyBlueprintItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

public final class BlueprintRenderer {
    private static final OpticalComponentMesh CARD = card();

    public static void render(ItemStack stack, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
        matrices.push();
        matrices.translate(.5, .5, .5);
        CARD.render(matrices, consumers, light, 0xFFFFFF, false, false);
        if (stack.getItem() instanceof AssemblyBlueprintItem blueprint) {
            var client = MinecraftClient.getInstance();
            ItemStack output = blueprint.output(client.world);
            if (!output.isEmpty() && !(output.getItem() instanceof AssemblyBlueprintItem)) {
                var icon = SchematicIcons.request(output);
                if (icon != null) {
                    var buffer = consumers.getBuffer(net.minecraft.client.render.RenderLayer.getEntityCutoutNoCull(icon));
                    for (int i = 0; i < 4; i++) {
                        boolean right = i >= 2, top = i == 1 || i == 2;
                        net.askcraft.justifylasers.platform.RenderVersion.endVertex(net.askcraft.justifylasers.platform.RenderVersion.normal(
                                buffer.vertex(matrices.peek().getPositionMatrix(), right ? .34F : -.34F, top ? .34F : -.34F, -.0105F)
                                        .color(255,255,255,255).texture(right ? 0 : 1, top ? 0 : 1).overlay(overlay).light(light),
                                matrices.peek().getNormalMatrix(), 0,0,-1));
                    }
                }
            }
        }
        matrices.pop();
    }

    static OpticalComponentMesh card() {
        var card = new OpticalComponentMesh.Builder("assembly_blueprint");
        Vec3d min = new Vec3d(-7,-7,0), max = new Vec3d(7,7,0);
        for (double[][] points : new double[][][]{
                {{-7,-6},{-7,6},{7,6},{7,-6}}, {{-7,6},{-6,7},{6,7},{7,6}}, {{-7,-6},{7,-6},{6,-7},{-6,-7}}})
            card.prism("front", "back", false, java.util.Arrays.stream(points).map(p -> new Vec3d(p[0],p[1],0)).toArray(Vec3d[]::new),
                    min, max, -.12, .12);
        return card.build();
    }

    private BlueprintRenderer() { }
}

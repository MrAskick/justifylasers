package net.askcraft.justifylasers.client.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LaserComponentRenderer {
    private static final Map<String, OpticalComponentMesh> MODELS = new LinkedHashMap<>();
    private static final Map<String, ItemModelBounds> BOUNDS = new LinkedHashMap<>();

    public static void render(String kind, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        var mesh = MODELS.computeIfAbsent(kind, LaserComponentRenderer::model);
        matrices.push();
        if (mode == ModelTransformationMode.GUI) BOUNDS.computeIfAbsent(kind, id ->
                ItemModelBounds.of(mesh.faces().stream().flatMap(face -> List.of(face.a(), face.b(), face.c(), face.d()).stream()).toList())).fitGui(matrices);
        else matrices.translate(.5, .5, .5);
        mesh.render(matrices, consumers, light, 0xFFFFFF, false, false);
        matrices.pop();
    }

    static OpticalComponentMesh model(String kind) {
        var body = new OpticalComponentMesh.Builder(kind);
        boolean core = kind.equals("energy_core"), controller = kind.equals("beam_controller");
        double depth = core || controller ? 5 : 1.8;
        if (controller) body.bevel("metal", -5, -6.9, -4.9, 5, 6.9, 4.9, .4);
        if (core) {
            body.bevel("dark", -3.6, -5.2, -3.6, 3.6, 5.2, 3.6, .6);
            for (Direction side : Direction.Type.HORIZONTAL) {
                var face = new OpticalComponentMesh.Builder(kind);
                face.panel("core", false, -3.1, -3.1, 3.1, 3.1, -3.63);
                body.add(face.build(), side);
            }
        }
        for (int sx : new int[]{-1,1}) for (int sz : new int[]{-1,1}) {
            double x = sx * 5.5, z = sz * (depth - 1.1);
            if (depth < 2 && sz == 1) continue;
            body.bevel("metal", x - 1, -5.4, z - .65, x + 1, 5.4, z + .65, .18);
        }
        for (double y : new double[]{-6.8,5.4})
            body.bevel("metal", -6.5, y, -depth, 6.5, y + 1.4, depth, .12);
        // Bezel and corner caps are separate solids; their front panels are offset by .03 model pixels.
        for (Direction side : new Direction[]{Direction.NORTH,Direction.SOUTH}) {
            var face = new OpticalComponentMesh.Builder(kind);
            for (int sx : new int[]{-1,1}) {
                double x = sx * 5.5;
                face.bevel("metal", x - 1, -4.6, -depth, x + 1, 4.6, -depth + .65, .12);
                face.panel("column", false, x - .58, -4, x + .58, 4, -depth - .03);
                for (double y : new double[]{-6.7,4.7}) {
                    face.bevel("armor", x - 1.05, y, -depth - .1, x + 1.05, y + 2, -depth + .8, .22);
                    face.panel("joint", false, x - .7, y + .3, x + .7, y + 1.7, -depth - .13);
                }
            }
            face.panel("beam", false, -3.8, 5.65, 3.8, 6.55, -depth - .03);
            face.panel("beam", false, -3.8, -6.55, 3.8, -5.65, -depth - .03);
            if (controller) {
                ring(face, "armor", 4.15, 3.4, -depth - .4, -depth + .08);
                face.panel("core", false, -3.25, -3.1, 3.25, 3.1, -depth - .05);
            } else if (!core && !kind.equals("reinforced_laser_housing")) {
                ring(face, "metal", 4.5, 2.2, -depth - .8, -depth + .15);
                ring(face, "armor", 3.55, 2.35, -depth - 1.18, -depth - .84);
                if (kind.equals("focusing_lens_assembly")) face.panel("core", false, -2.15, -2.15, 2.15, 2.15, -depth - .81);
            } else if (!core) ring(face, "armor", 4.3, 3.85, -depth - .13, -depth + .12);
            body.add(face.build(), side);
        }
        return body.build();
    }

    private static void ring(OpticalComponentMesh.Builder mesh, String material, double outer, double inner, double front, double back) {
        mesh.solid(material, -outer, -outer, front, outer, -inner, back);
        mesh.solid(material, -outer, inner, front, outer, outer, back);
        mesh.solid(material, -outer, -inner + .01, front, -inner, inner - .01, back);
        mesh.solid(material, inner, -inner + .01, front, outer, inner - .01, back);
    }

    private LaserComponentRenderer() { }
}

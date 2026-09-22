package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.printing.PrintDesign;
import net.askcraft.justifylasers.printing.PrintExamples;
import net.askcraft.justifylasers.printing.PrintSlice;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrintedMeshTest {
    private static final PrintTextures.Material MATERIAL = new PrintTextures.Material(Identifier.of("minecraft", "textures/block/stone.png"), null, false);
    private static final PrintDesign.Face FACE = new PrintDesign.Face("minecraft:block/stone", 0, 0, 16, 16, 0, 0x2388AA);

    @Test void bulkSubmissionRetainsTransformColorLightingAndNormals() {
        var result = new java.util.concurrent.atomic.AtomicReference<Object[]>();
        var consumer = (net.minecraft.client.render.VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{net.minecraft.client.render.VertexConsumer.class}, (proxy, method, args) -> {
                    assertEquals("vertex", method.getName());
                    assertEquals(14, args.length, "A complete vertex must be submitted in one call");
                    result.set(args);
                    return null;
                });
        var matrix = new org.joml.Matrix4f().translate(2,3,4).rotateY(.7F).scale(1.4F,.7F,2.1F);
        var expected = matrix.transformPosition(new org.joml.Vector3f(.25F,.5F,.75F));
        net.askcraft.justifylasers.platform.RenderVersion.entityVertex(consumer,matrix,.25F,.5F,.75F,
                0x9617AADD,.1F,.8F,12345,15728880,.5F,.25F,.75F);
        var actual = result.get();
        assertNotNull(actual);
        assertEquals(expected.x,(float)actual[0],1e-6F);
        assertEquals(expected.y,(float)actual[1],1e-6F);
        assertEquals(expected.z,(float)actual[2],1e-6F);
        assertEquals(23/255F,actual[3]); assertEquals(170/255F,actual[4]);
        assertEquals(221/255F,actual[5]); assertEquals(150/255F,actual[6]);
        assertEquals(.1F,actual[7]); assertEquals(.8F,actual[8]);
        assertEquals(12345,actual[9]); assertEquals(15728880,actual[10]);
        assertEquals(.5F,actual[11]); assertEquals(.25F,actual[12]); assertEquals(.75F,actual[13]);
    }

    @Test void fullFacesRemainFourVerticesWithTheirUvTintAndOutwardNormals() {
        var cube = PrintExamples.pedestal().elements().get(0);
        for (var side : Direction.values()) {
            var corners = cube.corners(side);
            var vertices = new ArrayList<PrintSlice.Vertex>();
            for (int i = 0; i < 4; i++) vertices.add(new PrintSlice.Vertex(corners[i], i * 4, 16 - i * 4));
            var builder = new PrintedMesh.Builder();
            builder.add(vertices, FACE, MATERIAL);
            var mesh = builder.build();
            assertEquals(1, mesh.faces().size());
            var face = mesh.faces().get(0);
            assertEquals(20, face.vertices().length, "No duplicate triangle vertices for a quad");
            assertEquals(FACE.tint(), face.tint());
            assertSame(MATERIAL, face.material());
            assertEquals(1, new Vec3d(face.nx(), face.ny(), face.nz()).dotProduct(Vec3d.of(side.getVector())), 1e-6);
            for (int i = 0; i < 4; i++) {
                int p = i * 5;
                assertEquals(corners[i].x / 16, face.vertices()[p], 1e-6);
                assertEquals(corners[i].y / 16, face.vertices()[p+1], 1e-6);
                assertEquals(corners[i].z / 16, face.vertices()[p+2], 1e-6);
                assertEquals(i / 4F, face.vertices()[p+3]);
                assertEquals(1 - i / 4F, face.vertices()[p+4]);
            }
        }
    }

    @Test void clippedPolygonsRetainTheirTriangleFanAndInterpolatedUvs() {
        var polygon = List.of(vertex(0,0), vertex(16,0), vertex(16,8), vertex(8,16), vertex(0,8));
        var builder = new PrintedMesh.Builder();
        builder.add(polygon, FACE, MATERIAL);
        assertEquals(60, builder.build().faces().get(0).vertices().length);
        var clipped = PrintSlice.below(polygon, 12);
        builder = new PrintedMesh.Builder();
        builder.add(clipped, FACE, MATERIAL);
        var vertices = builder.build().faces().get(0).vertices();
        for (int i = 0; i < vertices.length; i += 5) {
            assertTrue(vertices[i+1] <= .75F);
            assertEquals(vertices[i], vertices[i+3]);
            assertEquals(vertices[i+1], vertices[i+4]);
        }
    }

    @Test void emptySlicesHaveNoGeometryAndTrianglesRemainValidQuads() {
        var builder = new PrintedMesh.Builder();
        builder.add(List.of(), FACE, MATERIAL);
        builder.add(List.of(vertex(0,0), vertex(16,0)), FACE, MATERIAL);
        assertTrue(builder.build().faces().isEmpty());
        builder.add(List.of(vertex(0,0), vertex(16,0), vertex(0,16)), FACE, MATERIAL);
        var vertices = builder.build().faces().get(0).vertices();
        assertEquals(20, vertices.length);
        for (int i = 0; i < 5; i++) assertEquals(vertices[10+i], vertices[15+i]);
    }

    private static PrintSlice.Vertex vertex(double x, double y) { return new PrintSlice.Vertex(new Vec3d(x,y,0),x,y); }
}

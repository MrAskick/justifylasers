package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.bridge.LightBridgeSpan;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LightBridgeTransformTest {
    @Test void optimizedCoordinatesMatchEveryExistingOrientationAndJoin() {
        var camera = new Vec3d(-405.25,-58.375,18.75);
        for (Direction facing : Direction.values()) for (Direction mount : Direction.values())
            for (boolean roll : new boolean[]{false,true}) for (int angle = 0; angle < 8; angle++)
                for (int section = -1; section < 2; section++) for (int joins = 0; joins < 4; joins++) {
                    int width = section < 0 ? 3 : 1;
                    var span = new LightBridgeSpan(new BlockPos(-402,-60,22),facing,mount,roll,angle,section,joins,width,256,0x11EEFF,4_000_000);
                    var matrix = LightBridgeTransform.of(span,camera);
                    for (double x : new double[]{0,.38,width}) for (double z : new double[]{0,.2,127.8,256}) {
                        double y = .4385;
                        var old = span.point(x,z,y).subtract(camera);
                        var actual = matrix.origin().add(matrix.across().multiply(x)).add(matrix.forward().multiply(z)).add(matrix.up().multiply(y));
                        assertEquals(0,actual.distanceTo(old),1e-9,"Identical vertices, including inset and corner endpoints");
                        var gpu = matrix.matrix().transformPosition(new org.joml.Vector3f((float)x, (float)y, (float)z));
                        assertEquals(old.x, gpu.x, 5e-5);
                        assertEquals(old.y, gpu.y, 5e-5);
                        assertEquals(old.z, gpu.z, 5e-5);
                    }
                }
    }

    @Test void gpuCoordinatesRemainCameraRelativeNearWorldBorder() {
        var camera = new Vec3d(29_999_900.25,120.125,-29_999_999.5);
        var span = new LightBridgeSpan(new BlockPos(29_999_900,120,-29_999_990),Direction.SOUTH,3,512,0xAAFFFF,100);
        var gpu = LightBridgeTransform.of(span,camera).matrix().transformPosition(new org.joml.Vector3f(1.375F,.501F,512));
        var expected = span.point(1.375,512,.501).subtract(camera);
        assertEquals(expected.x,gpu.x,1e-5);
        assertEquals(expected.y,gpu.y,1e-5);
        assertEquals((float)expected.z,gpu.z,1e-5);
    }
}

package net.askcraft.justifylasers.client.render;

import net.minecraft.util.math.Direction;

final class RefocusingCubeChassis {
    static final OpticalComponentMesh MESH = build();

    private static OpticalComponentMesh build() {
        var body = new OpticalComponentMesh.Builder("refocusing_cube");
        for (int x : new int[]{-1, 1}) for (int y : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
            body.bevel("corner", x*5.4-1.8, y*5.4-1.8, z*5.4-1.8, x*5.4+1.8, y*5.4+1.8, z*5.4+1.8, 0.38);
        }
        var side = new OpticalComponentMesh.Builder("refocusing_cube");
        for (int sign : new int[]{-1, 1}) {
            side.box("edge", false, -3.6, sign*6.25-0.52, -6.92, 3.6, sign*6.25+0.52, -6.05);
            side.box("edge", false, sign*6.25-0.52, -3.6, -6.91, sign*6.25+0.52, 3.6, -6.04);
            side.panel("light", true, -0.06, sign*6.25-0.5, 0.06, sign*6.25+0.5, -6.935);
            side.panel("light", true, sign*6.25-0.5, -0.06, sign*6.25+0.5, 0.06, -6.925);
        }
        side.profile("ring", false, new double[]{4.76,4.9,5.14,5.43,5.65,5.65},
                new double[]{-6.51,-6.98,-7.07,-7.07,-6.85,-6.15},5.65,255);
        side.profile("ring", false, new double[]{5.65,4.76,4.76},new double[]{-6.15,-6.15,-6.51},5.65,255);
        side.profile("light", true, new double[]{5.18,5.39}, new double[]{-7.085,-7.085},5.65,255);
        // Optical windows are composited from the lit scene; no dark glass sheet enters the shader G-buffer.
        for (Direction face : Direction.values()) body.add(side.build(), face);
        var output = new OpticalComponentMesh.Builder("refocusing_cube");
        for (int sign : new int[]{-1, 1}) {
            output.box("stud",false,sign*2.8-0.35,5.35,-7.13,sign*2.8+0.35,6.35,-6.97);
            output.panel("light",true,sign*2.8-0.055,5.55,sign*2.8+0.055,6.15,-7.145);
        }
        body.add(output.build(), Direction.SOUTH);
        return body.build();
    }

    private RefocusingCubeChassis() { }
}

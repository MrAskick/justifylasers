package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.block.LaserOpticBlock;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.askcraft.justifylasers.laser.OpticPortMode;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;

import java.util.EnumMap;
import java.util.Map;

final class BeamSplitterModel {
    static final OpticalComponentMesh CHASSIS = chassis();
    static final Map<Direction, Map<OpticPortMode, OpticalComponentMesh>> PORTS = ports();

    static void render(LaserOpticBlockEntity optic, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        boolean active = optic.getCachedState().get(LaserOpticBlock.LIT);
        CHASSIS.render(matrices,consumers,light,optic.rgb(),active,optic.emitsShaderLight());
        for (Direction side : Direction.values()) {
            OpticPortMode mode=optic.portMode(side);
            PORTS.get(side).get(mode).render(matrices,consumers,light,optic.rgb(),active && mode!=OpticPortMode.DISABLED,optic.emitsShaderLight());
        }
    }

    private static OpticalComponentMesh chassis() {
        var body=new OpticalComponentMesh.Builder("beam_splitter");
        body.bevel("metal",-2.85,-2.85,-2.85,2.85,2.85,2.85,0.18);
        return body.build();
    }

    private static Map<Direction, Map<OpticPortMode, OpticalComponentMesh>> ports() {
        Map<Direction,Map<OpticPortMode,OpticalComponentMesh>> result=new EnumMap<>(Direction.class);
        for(Direction side:Direction.values()) {
            Map<OpticPortMode,OpticalComponentMesh> modes=new EnumMap<>(OpticPortMode.class);
            for(OpticPortMode mode:OpticPortMode.values()) {
                var b=new OpticalComponentMesh.Builder("beam_splitter");
                b.solid("metal",-2.12,-2.12,-6.65,2.12,2.12,-2.86);
                for(Direction shaftSide:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}) {
                    var strip=new OpticalComponentMesh.Builder("beam_splitter");
                    // Build each strip along Y, then turn the complete shaft toward the configured face.
                    strip.box("arm",true,-1.2,3.05,-2.15,1.2,6.25,-2.13);
                    var stripTurn=new OpticalComponentMesh.Builder("beam_splitter");
                    stripTurn.add(strip.build(),shaftSide);
                    b.add(stripTurn.build(),Direction.DOWN);
                }
                b.solid("steel",-2.4,-2.4,-3.15,2.4,2.4,-2.87);
                for(int sign:new int[]{-1,1}) {
                    b.solid("frame",sign*2.82-0.48,-2.31,-7.94,sign*2.82+0.48,2.31,-6.48);
                    b.solid("frame",-3.3,sign*2.82-0.48,-7.95,3.3,sign*2.82+0.48,-6.47);
                    b.solid("steel",sign*3.15-0.13,-2.3,-7.975,sign*3.15+0.13,2.3,-6.46);
                    b.solid("steel",-2.3,sign*3.15-0.13,-7.976,2.3,sign*3.15+0.13,-6.45);
                }
                for(int x:new int[]{-1,1}) for(int y:new int[]{-1,1}) {
                    // Corner caps wrap the frame; their side planes must also clear it, not just the front.
                    b.bevel("steel",x*2.8-0.52,y*2.8-0.52,-7.98,x*2.8+0.52,y*2.8+0.52,-6.43,0.12);
                    if(mode!=OpticPortMode.DISABLED) b.panel("corner",true,x*2.8-0.34,y*2.8-0.34,x*2.8+0.34,y*2.8+0.34,-7.995);
                }
                if(mode==OpticPortMode.INPUT) {
                    b.solid("metal",-2.29,-2.29,-6.54,2.29,2.29,-6.4);
                    b.profile("ring",true,new double[]{1.62,1.72,2.05,2.22,2.22},new double[]{-7.14,-7.91,-7.93,-7.64,-6.56},2.22,255);
                } else if(mode==OpticPortMode.OUTPUT) {
                    b.solid("metal",-2.29,-2.29,-6.54,2.29,2.29,-6.4);
                    // The square aperture is recessed; its bright surround belongs only to an output port.
                    b.box("output",true,-2.23,-2.23,-7.81,2.23,2.23,-6.56);
                } else b.box("disabled",false,-2.28,-2.28,-7.9,2.28,2.28,-6.5);
                var oriented=new OpticalComponentMesh.Builder("beam_splitter");
                oriented.add(b.build(),side);
                modes.put(mode,oriented.build());
            }
            result.put(side,Map.copyOf(modes));
        }
        return Map.copyOf(result);
    }

    private BeamSplitterModel() { }
}

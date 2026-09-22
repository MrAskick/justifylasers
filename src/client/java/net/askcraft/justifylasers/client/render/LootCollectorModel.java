package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.block.entity.LaserPartBlockEntity;
import net.askcraft.justifylasers.client.ClientSettings;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

final class LootCollectorModel {
    private static final String MATERIAL = "loot_collector";
    static final OpticalComponentMesh MESH = build();
    private static final ItemModelBounds BOUNDS = ItemModelBounds.of(MESH.faces().stream()
            .flatMap(face -> java.util.stream.Stream.of(face.a(),face.b(),face.c(),face.d())).toList());

    static void render(ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        matrices.push();
        if (mode == ModelTransformationMode.GUI) BOUNDS.fitGui(matrices);
        else matrices.translate(.5,.5,.5);
        MESH.render(matrices, consumers, light, 0x55E7F5, true, true);
        matrices.pop();
    }
    private static OpticalComponentMesh build() {
        var b = new OpticalComponentMesh.Builder(MATERIAL);
        b.bevel("dark",-7.35,-7.8,-7.35,7.35,3.55,7.35,.35);
        b.bevel("armor",-7.45,3.64,-7.45,7.45,7.5,7.45,.42);
        var top = new OpticalComponentMesh.Builder(MATERIAL);
        top.panel("lid",false,-5.3,-5.3,5.3,5.3,-7.53);
        b.add(top.build(),Direction.UP);
        for(int sx : new int[]{-1,1}) for(int sz : new int[]{-1,1}) {
            double x=sx*6.6,z=sz*6.6;
            b.bevel("metal",x-.83,-5.45,z-.83,x+.83,4.9,z+.83,.2);
            for(double y : new double[]{-8,5}) {
                b.bevel("armor",x-1.3,y,z-1.3,x+1.3,y+2.8,z+1.3,.3);
                for(Direction side : new Direction[]{sx<0?Direction.WEST:Direction.EAST,sz<0?Direction.NORTH:Direction.SOUTH}) {
                    var cap = new OpticalComponentMesh.Builder(MATERIAL);
                    double across = side.getAxis()==Direction.Axis.X ? z : x;
                    if(side==Direction.WEST || side==Direction.SOUTH) across=-across;
                    cap.panel("joint",false,across-.75,y+.5,across+.75,y+2.3,-7.93);
                    cap.panel("light",true,across-.29,y+.95,across+.29,y+1.85,-7.95);
                    b.add(cap.build(),side);
                }
            }
        }
        for(Direction side : Direction.Type.HORIZONTAL) {
            var face = new OpticalComponentMesh.Builder(MATERIAL);
            face.bevel("metal",-5.35,-7.2,-7.56,5.35,-5.6,-7.24,.075);
            face.bevel("metal",-5.35,4.4,-7.56,5.35,6.7,-7.2,.085);
            face.panel("light",true,-4.65,3.6,4.65,3.85,-7.50);
            for(int sign : new int[]{-1,1}) {
                double x=sign*5.55;
                face.bevel("armor",x-.38,-5.3,-7.65,x+.38,3.25,-7.2,.1);
                face.panel("light",true,x-.13,-4.6,x+.13,2.8,-7.67);
            }
            if(side==Direction.NORTH) {
                face.panel("front",false,-5.1,-5.4,5.1,-.8,-7.39);
                face.bevel("metal",-1.35,-.45,-7.8,1.35,4.25,-7.35,.10);
                face.panel("latch",false,-1.16,-.22,1.16,3.95,-7.82);
                face.panel("light",true,-.20,.25,.20,3.15,-7.85);
                face.panel("screen",false,-4.8,-5.05,4.8,-1.2,-7.43);
            } else {
                face.panel("side",false,-5.1,-5.3,5.1,3.1,-7.40);
                face.panel("light",true,-1.28,-.85,1.28,.2,-7.43);
                for(double y : new double[]{-.98,.33}) face.bevel("metal",-1.55,y,-7.48,1.55,y+.1,-7.40,.015);
            }
            b.add(face.build(),side);
        }
        var original=b.build();
        java.util.function.UnaryOperator<Vec3d> resize=p->p.multiply(.8).add(0,-.1,0);
        return new OpticalComponentMesh(MATERIAL,original.faces().stream().map(f->new OpticalComponentMesh.Face(resize.apply(f.a()),resize.apply(f.b()),resize.apply(f.c()),resize.apply(f.d()),f.normal(),f.ua(),f.ub(),f.uc(),f.ud(),f.glowing(),f.alpha())).toList());
    }
    private record BeamColors(long tick,java.util.Map<net.minecraft.util.math.BlockPos,Integer> colors) { }
    private static final java.util.Map<net.minecraft.world.World,BeamColors> BEAM_COLORS=new java.util.WeakHashMap<>();
    static Integer beamColor(LaserPartBlockEntity block){
        var world=block.getWorld();var cached=BEAM_COLORS.get(world);
        if(cached==null||cached.tick()!=world.getTime()){
            var colors=new java.util.HashMap<net.minecraft.util.math.BlockPos,Integer>();
            var strengths=new java.util.HashMap<net.minecraft.util.math.BlockPos,Double>();
            net.askcraft.justifylasers.laser.LaserBeamNetwork.paths(world,1).forEach((source,path)->{
                if(!(world.getBlockEntity(source) instanceof net.askcraft.justifylasers.laser.LaserBeamSource emitter))return;
                for(var ray:path.segments()){
                    var pos=net.minecraft.util.math.BlockPos.ofFloored(ray.end().add(ray.end().subtract(ray.start()).normalize().multiply(.002)));
                    double strength=ray.power()*emitter.luminousFlux();
                    if(strength>strengths.getOrDefault(pos,0d)){strengths.put(pos,strength);colors.put(pos,ray.rgb());}
                }
            });
            cached=new BeamColors(world.getTime(),colors);BEAM_COLORS.put(world,cached);
        }
        return cached.colors().get(block.getPos());
    }
    static void renderBlock(LaserPartBlockEntity block,MatrixStack matrices,VertexConsumerProvider consumers,int light){
        Integer color=beamColor(block);matrices.push();matrices.translate(.5,.5,.5);MESH.render(matrices,consumers,light,color==null?0xB3BCC5:color,color!=null,true);matrices.pop();
    }
    static void contents(LaserPartBlockEntity block, MatrixStack matrices, VertexConsumerProvider consumers) {
        var client=MinecraftClient.getInstance();
        if(!ClientSettings.get().machineDisplays || IrisCompatibility.isRenderingShadowPass() || client.player==null
                || client.player.squaredDistanceTo(Vec3d.ofCenter(block.getPos()))>32*32) return;
        for(int slot=0;slot<LaserPartBlockEntity.STORAGE_SIZE;slot++) {
            var stack=block.getStack(slot);
            if(stack.isEmpty()) continue;
            var icon=SchematicIcons.request(stack);
            if(icon==null) continue;
            var buffer=consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(icon));
            float x=.5F+(slot%3-1)*.112F,y=.4F+(-2F-slot/3F)*.8F/16F,z=.5F-7.48F*.8F/16F;
            float radius=.0208F;
            for(int i=0;i<4;i++) {
                boolean right=i>=2,top=i==1||i==2;
                RenderVersion.endVertex(RenderVersion.normal(buffer.vertex(matrices.peek().getPositionMatrix(),
                                x+(right?radius:-radius),y+(top?radius:-radius),z)
                        .color(255,255,255,255).texture(right?0:1,top?0:1).overlay(OverlayTexture.DEFAULT_UV)
                        .light(LightmapTextureManager.MAX_LIGHT_COORDINATE),matrices.peek().getNormalMatrix(),0,0,-1));
            }
        }
    }
    private LootCollectorModel() { }
}

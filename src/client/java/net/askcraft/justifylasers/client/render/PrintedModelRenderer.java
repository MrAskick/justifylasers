package net.askcraft.justifylasers.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.askcraft.justifylasers.block.PrintedModelBlock;
import net.askcraft.justifylasers.block.entity.PrintedModelBlockEntity;
import net.askcraft.justifylasers.printing.PrintAssembly;
import net.askcraft.justifylasers.printing.PrintDesign;
import net.askcraft.justifylasers.printing.PrintSlice;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

public final class PrintedModelRenderer implements BlockEntityRenderer<PrintedModelBlockEntity> {
    private record Draw(PrintDesign.Element element,PrintDesign.Face cap,int hiddenFaces){ }
    private static final Map<PrintAssembly, Geometry> OVERVIEWS = new WeakHashMap<>();
    private static final Map<PrintDesign, Geometry> PARTS = new WeakHashMap<>();
    private static final class Geometry {
        private final List<Draw> elements;
        private PrintedMesh complete, slice;
        private double sliceHeight = Double.NaN;

        private Geometry(List<Draw> elements) { this.elements = elements; }

        PrintedMesh mesh(double height) {
            if (height >= 16) {
                if (complete == null) complete = bake(Double.POSITIVE_INFINITY);
                return complete;
            }
            // Keep only the current construction layer, not sixteen copies of a large model.
            if (slice == null || sliceHeight != height) { slice = bake(height); sliceHeight = height; }
            return slice;
        }

        private PrintedMesh bake(double height) {
            var builder = new PrintedMesh.Builder();
            for (var draw : elements) bakeElement(draw.element(), draw.cap(), height < 16 ? 0 : draw.hiddenFaces(), height, builder);
            return builder.build();
        }
    }
    public static void clear(){OVERVIEWS.clear();PARTS.clear();}
    public PrintedModelRenderer(BlockEntityRendererFactory.Context context) { }
    @Override public void render(PrintedModelBlockEntity block,float delta,MatrixStack matrices,VertexConsumerProvider buffers,int light,int overlay) {
        render(block.design(),block.getCachedState().get(PrintedModelBlock.FACING),matrices,buffers,light,overlay);
    }
    public static void render(PrintDesign model,Direction direction,MatrixStack matrices,VertexConsumerProvider buffers,int light,int overlay) {
        render(model,direction,matrices,buffers,light,overlay,16,false);
    }
    public static void render(PrintDesign model,Direction direction,MatrixStack matrices,VertexConsumerProvider buffers,int light,int overlay,double height,boolean hologram) {
        if(model==null) return;
        if (model.assembly()!=null && model.assembly().hasBlockModels() && model.assembly().modelCount()<=8192) {
            var assembly=model.assembly(); var size=assembly.size();
            float scale=16F/Math.max(size.getX(),Math.max(size.getY(),size.getZ()));
            matrices.push();
            try {
                matrices.translate(.5,0,.5);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(switch(direction){case EAST->-90;case SOUTH->180;case WEST->90;default->0;}));
                matrices.translate(-size.getX()*scale/32,0,-size.getZ()*scale/32);
                matrices.scale(scale,scale,scale);
                var geometry=OVERVIEWS.computeIfAbsent(assembly,key->{
                    var result=new ArrayList<PrintDesign.Element>();
                    for(int i=0;i<key.count();i++){
                        var offset=Vec3d.of(key.offset(i)).multiply(16);
                        for(var cube:key.part(i,"").elements())result.add(new PrintDesign.Element(cube.from().add(offset),cube.to().add(offset),null,cube.faces(),cube.blockState()));
                    }
                    return new Geometry(prepare(result,cube->cube.faces().get(Direction.UP)));
                });
                geometry.mesh(Double.POSITIVE_INFINITY).render(matrices,buffers,light,overlay,hologram);
            } finally { matrices.pop(); }
            return;
        }
        model=model.preview();
        var mesh = PARTS.computeIfAbsent(model, key -> new Geometry(prepare(key.elements(), key::capMaterial))).mesh(height);
        matrices.push();
        try {
            matrices.translate(.5,0,.5);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(switch(direction){case EAST->-90;case SOUTH->180;case WEST->90;default->0;}));
            matrices.translate(-.5,0,-.5);
            mesh.render(matrices,buffers,light,overlay,hologram);
        } finally {
            matrices.pop();
        }
    }
    private static List<Draw> prepare(List<PrintDesign.Element> elements,Function<PrintDesign.Element,PrintDesign.Face> cap){
        var cells=new HashMap<Vec3d,PrintDesign.Element>();
        for(var cube:elements)if(!cube.blockState().isEmpty())cells.put(cube.from(),cube);
        var result=new ArrayList<Draw>(elements.size());
        for(var cube:elements){
            int hidden=0;var size=cube.to().subtract(cube.from());
            if(!cube.blockState().isEmpty())try{
                var model=SchematicBlockModels.model(cube.blockState());
                for(var side:Direction.values()){
                    var neighbor=cells.get(cube.from().add(side.getOffsetX()*size.x,side.getOffsetY()*size.y,side.getOffsetZ()*size.z));
                    if(neighbor!=null&&neighbor.to().subtract(neighbor.from()).equals(size)
                            &&(SchematicBlockModels.model(neighbor.blockState()).opaque()||neighbor.blockState().equals(cube.blockState())&&(model.hiddenAgainstSame()&(1<<side.ordinal()))!=0))hidden|=1<<side.ordinal();
                }
            }catch(RuntimeException unavailable){ /* Existing prints remain visible if their original mod is removed. */ }
            result.add(new Draw(cube,cap.apply(cube),hidden));
        }
        return List.copyOf(result);
    }
    private static void bakeElement(PrintDesign.Element cube,PrintDesign.Face cap,int hidden,double height,PrintedMesh.Builder builder){
            if(!cube.blockState().isEmpty() && bakeBlockModel(cube,hidden,height,builder)) return;
            for(var entry:cube.faces().entrySet()) {
                var face=entry.getValue();var corners=cube.corners(entry.getKey());var vertices=new ArrayList<PrintSlice.Vertex>(4);
                for(int i=0;i<4;i++){int uv=Math.floorMod(i-face.rotation()/90,4);vertices.add(new PrintSlice.Vertex(corners[i],uv==0||uv==3?face.u0():face.u1(),uv<2?face.v1():face.v0()));}
                bakePolygon(height<16?PrintSlice.below(vertices,height):vertices,face,builder);
            }
            var bounds=cube.bounds();
            if(height<16&&height>bounds.minY&&(height<bounds.maxY||height==bounds.maxY&&!cube.faces().containsKey(Direction.UP))&&cap!=null)
                bakePolygon(PrintSlice.cap(cube,height),cap,builder);
    }
    private static boolean bakeBlockModel(PrintDesign.Element cube,int hidden,double height,PrintedMesh.Builder builder) {
        final SchematicBlockModels.Model model;
        try { model=SchematicBlockModels.model(cube.blockState()); }
        catch(RuntimeException unsupported) { return false; }
        Vec3d scale=cube.to().subtract(cube.from());
        for(var face:model.faces()) {
            if(face.cull()!=null&&(hidden&(1<<face.cull().ordinal()))!=0)continue;
            var vertices=face.vertices().stream().map(vertex->new PrintSlice.Vertex(
                    cube.from().add(vertex.position().multiply(scale.x,scale.y,scale.z)),vertex.u(),vertex.v())).toList();
            bakePolygon(height<16?PrintSlice.below(vertices,height):vertices,face.material(),builder);
        }
        if(height>cube.from().y&&height<cube.to().y) bakePolygon(PrintSlice.cap(cube,height),cube.faces().get(Direction.UP),builder);
        return true;
    }
    private static void bakePolygon(List<PrintSlice.Vertex> vertices,PrintDesign.Face face,PrintedMesh.Builder builder){
        if(vertices.size()>=3) builder.add(vertices,face,PrintTextures.material(face.texture()));
    }
    public static void preview(DrawContext context,PrintDesign design,int x,int y,int size,float yaw,float pitch) {
        preview(context,design,x,y,size,yaw,pitch,true);
    }
    public static void preview(DrawContext context,PrintDesign design,int x,int y,int size,float yaw,float pitch,boolean clip) {
        if(design==null) return;
        context.draw(); if(clip)context.enableScissor(x,y,x+size,y+size);
        var matrices=context.getMatrices(); matrices.push();
        try {
            matrices.translate(x+size*.5,y+size*.55,150); matrices.scale(size*.59F,-size*.59F,size*.59F);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch)); matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
            matrices.translate(-.5,-.5,-.5);
            DiffuseLighting.enableGuiDepthLighting(); RenderSystem.enableDepthTest();
            render(design,Direction.NORTH,matrices,context.getVertexConsumers(),LightmapTextureManager.MAX_LIGHT_COORDINATE,OverlayTexture.DEFAULT_UV);
            context.getVertexConsumers().draw();
        } finally {
            matrices.pop(); if(clip)context.disableScissor(); RenderSystem.setShaderColor(1,1,1,1); RenderSystem.defaultBlendFunc(); DiffuseLighting.enableGuiDepthLighting();
        }
    }
}

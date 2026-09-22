package net.askcraft.justifylasers.client.render;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChamberSeamsTest {
    @Test void mirrorOwnsItsDepthAndUsesOneContinuousTextureField() {
        var faces = LaserMirrorModel.GLASS.faces();
        assertEquals(16, faces.size());
        var center = faces.get(0).ua();
        for (var face : faces) {
            assertEquals(255, face.alpha(), "Shader reflection depth belongs to the mirror, not the wall behind it");
            assertEquals(center, face.ua(), "All fan sectors share the same UV center");
            assertEquals(face.ua(), face.ud());
        }
        for (int i = 0; i < 8; i++) assertEquals(faces.get(i * 2).uc(), faces.get(((i + 1) % 8) * 2).ub());
        var vanilla = LaserMirrorModel.WINDOW.faces();
        assertEquals(faces.size(), vanilla.size());
        for (int i = 0; i < faces.size(); i++) {
            assertEquals(38, vanilla.get(i).alpha(), "Keep the existing glass opacity without shader packs");
            assertEquals(faces.get(i).a(), vanilla.get(i).a());
            assertEquals(faces.get(i).ua(), vanilla.get(i).ua());
            assertEquals(faces.get(i).uc(), vanilla.get(i).uc());
        }
    }
    @Test void printerSplitLidsShareOneSquareTextureWithoutHorizontalCompression(){
        var region=ComponentAtlas.load("photopolymer_printer","base").region("lid");
        for(var mesh:List.of(PrintingMachineModel.CASING,PrintingMachineModel.FRAME)){
            int count=0;
            for(var f:mesh.faces())if(region.contains(f.ua())&&Math.abs(f.normal().y)>.99){
                double worldWidth=f.c().subtract(f.b()).length(),worldHeight=f.b().subtract(f.a()).length();
                double textureWidth=Math.abs(f.uc().u()-f.ub().u()),textureHeight=Math.abs(f.ub().v()-f.ua().v());
                assertEquals(worldWidth/worldHeight,textureWidth/textureHeight,1e-5);count++;
            }
            assertEquals(4,count,"Two lids on the top and two on the bottom");
        }
    }
    @Test void newProcessMachinesHaveSeparatedSurfaces() {
        verify(PrintingMachineModel.ENCODER, "model encoder");
        verify(PrintingMachineModel.CASING, "photopolymer casing");
        verify(PrintingMachineModel.FRAME, "photopolymer printer");
        verify(PrintingMachineModel.HEAD, "printer carriage");
        verify(PrintingMachineModel.BED, "printer build plate");
        verify(ChemicalSynthesizerModel.BODY, "chemical synthesizer");
        verify(ChemicalSynthesizerModel.GAUGE, "synthesizer gauges");
        verify(LaserCutterModel.CASING, "laser cutter casing");
        verify(LaserCutterModel.FRAME, "laser cutter frame");
    }
    @Test void cutterMovingHeadDoesNotFightWithItsFrame() {
        for (var position : List.of(Vec3d.ZERO, new Vec3d(.35, 0, .34), new Vec3d(-.35, 0, -.34))) {
            var faces = new java.util.ArrayList<>(LaserCutterModel.FRAME.faces());
            faces.addAll(LaserCutterModel.RAILS.faces());
            for (var part : List.of(LaserCutterModel.CARRIAGE, LaserCutterModel.HEAD)) {
                var offset = part == LaserCutterModel.HEAD ? position : new Vec3d(0, 0, position.z);
                for (var face : part.faces()) faces.add(new OpticalComponentMesh.Face(
                        face.a().add(offset),face.b().add(offset),face.c().add(offset),face.d().add(offset),
                        face.normal(),face.ua(),face.ub(),face.uc(),face.ud(),face.glowing(),face.alpha()));
            }
            verify(new OpticalComponentMesh("laser_cutter", faces), "Complete moving cutter at " + position);
        }
    }
    @Test void spectrumTopAndPrismFacesPointOutward() {
        verify(LootCollectorModel.MESH, "loot collector");
        verify(SpectrumModuleModel.FRAME, "spectrum frame");
        verify(SpectrumModuleModel.PRISM, "spectrum prism");
        for (var face : SpectrumModuleModel.FRAME.faces()) {
            for (var point : List.of(face.a(), face.b(), face.c(), face.d()))
                assertTrue(Double.isFinite(point.lengthSquared()));
            if (Math.abs(face.a().y - 7.85 / 16) < 1e-8 && Math.abs(face.b().y - face.a().y) < 1e-8
                    && Math.abs(face.c().y - face.a().y) < 1e-8) assertTrue(face.normal().y > .99, "Dial cap faces up");
        }
        for (var face : SpectrumModuleModel.PRISM.faces()) {
            var center=face.a().add(face.b()).add(face.c()).add(face.d()).multiply(.25);
            assertTrue(center.subtract(new Vec3d(0,-.4/16,0)).dotProduct(face.normal()) > 0);
            assertFalse(face.glowing(), "Prism has no rainbow emission");
        }
    }
    @Test void bridgeWidthsHaveNoCoplanarOverlaps() {
        for (int width=1;width<=3;width++) verify(LightBridgeModel.MODELS[width-1], "hard light bridge " + width);
        verify(LightBridgeModel.CORNER, "corner hard light bridge");
    }
    @Test void bridgePortsFaceOutwardInsteadOfBeingCulledInsideTheChassis() {
        var port=ComponentAtlas.load("light_bridge","base").region("port");
        for(var mesh:LightBridgeModel.MODELS)for(var face:mesh.faces())if(port.contains(face.ua())) {
            var center=face.a().add(face.b()).add(face.c()).add(face.d()).multiply(.25);
            assertTrue(center.dotProduct(face.normal())>0,"Input port must face outward: "+center);
        }
    }
    @Test void largeCollectorPanelsUseOneContinuousSquareUvField() {
        var grid=ComponentAtlas.load("solar_concentrator","base").region("grid");
        int panels=0;
        for(var face:SolarConcentratorModel.DISH.faces())if(grid.contains(face.ua())){
            panels++;
            var points=List.of(face.a(),face.b(),face.c(),face.d());var uv=List.of(face.ua(),face.ub(),face.uc(),face.ud());
            for(int i=0;i<4;i++){
                var expected=grid.uv((points.get(i).x*16/21+1)/2,(points.get(i).z*16/21+1)/2);
                assertEquals(expected.u(),uv.get(i).u(),1e-7);assertEquals(expected.v(),uv.get(i).v(),1e-7);
            }
            assertTrue(face.normal().y>0,"Fronts of all panels face the sky");
        }
        assertEquals(16,panels,"Inner and outer sectors share one UV field, without repeated trims");
    }

    @Test void largeDishTracksBothHorizonsAndHasConservativeRenderBounds() {
        for(int degrees=-89;degrees<=89;degrees++){
            double radians=Math.toRadians(degrees);
            var sun=new Vec3d(Math.sin(radians),Math.cos(radians),0);
            float tilt=SolarConcentratorModel.sunTilt(sun);
            var normal=SolarConcentratorModel.dishPoint(new Vec3d(0,1,0),tilt).subtract(SolarConcentratorModel.dishPoint(Vec3d.ZERO,tilt));
            assertTrue(normal.dotProduct(sun)>.999999,"Dish normal tracks sun in world coordinates");
            for(var face:SolarConcentratorModel.DISH.faces())for(var point:List.of(face.a(),face.b(),face.c(),face.d())){
                var p=SolarConcentratorModel.dishPoint(point,tilt);
                assertTrue(Math.abs(p.x)<1.5 && Math.abs(p.z)<1.5 && p.y>-.5 && p.y<3.5,"Tracking dish stays inside renderer bounds");
            }
        }
        assertEquals(0,SolarConcentratorModel.sunTilt(new Vec3d(0,-1,0)),"Night park pose");
    }
    @Test void trackingDishStaysInsideOneBlockAtEverySunAngle() {
        for (int angle=-90;angle<=90;angle++) {
            double radians=Math.toRadians(angle);
            for (var face:SmallSolarConcentratorModel.DISH.faces()) for (var p:List.of(face.a(),face.b(),face.c(),face.d())) {
                double x=p.x*Math.cos(radians)-p.y*Math.sin(radians);
                double y=p.x*Math.sin(radians)+p.y*Math.cos(radians)+.1;
                assertTrue(Math.abs(x)<=.5 && y>=-.5 && y<=.5 && Math.abs(p.z)<=.5,
                        "Small collector exceeds its block at angle="+angle+": "+p);
            }
        }
    }
    @Test void solarConcentratorHasNoCoplanarOverlaps() {
        verify(SolarConcentratorModel.MESH, "solar concentrator");
        verify(SolarConcentratorModel.DISH, "large dish");
        verify(SolarConcentratorModel.PORT, "large port");
        verify(SolarConcentratorModel.OUTLET, "large outlet");
        verify(SmallSolarConcentratorModel.BASE, "small base");
        verify(SmallSolarConcentratorModel.DISH, "small dish");
        verify(SmallSolarConcentratorModel.PORT, "small port");
        verify(SmallSolarConcentratorModel.OUTLET, "small outlet");
        verify(FuelGeneratorModel.MESH, "fuel generator");
    }
    @Test void chambersAndCasingsHaveNoCoplanarOverlaps() {
        for (boolean grower : new boolean[]{false,true}) for (boolean casing : new boolean[]{false,true})
            verify(ChamberModel.chassis(grower, casing), "grower=" + grower + ", casing=" + casing);
    }

    @Test void newLaserComponentsHaveSeparatedPanels() {
        for (String kind : new String[]{"optical_resonator","reinforced_laser_housing","energy_core","focusing_lens_assembly","beam_controller"})
            verify(LaserComponentRenderer.model(kind), kind);
    }

    @Test void tabletAndSchematicHaveNoOverlappingFaces() {
        verify(TabletRenderer.body(), "tablet");
        verify(BlueprintRenderer.card(), "schematic");
    }

    @Test void chamberDetailPanelsKeepTheTextureAspectRatio() {
        for (String kind : new String[]{"crystal_chamber", "assembly_chamber", "crystal_chamber_casing", "assembly_chamber_casing"}) {
            var builder = new OpticalComponentMesh.Builder(kind);
            builder.trimmedPanel("column", true, 0,0,1.6,22,0);
            builder.trimmedPanel("beam", true, 0,0,24,3,0);
            builder.trimmedPanel("joint", true, 0,0,2,2,0);
            for (var face : builder.build().faces()) {
                double a = Math.hypot(face.ua().u()-face.ub().u(), face.ua().v()-face.ub().v()) / face.a().distanceTo(face.b());
                double b = Math.hypot(face.ub().u()-face.uc().u(), face.ub().v()-face.uc().v()) / face.b().distanceTo(face.c());
                assertEquals(1, a/b, .10, kind + ": avoid stretched detail panels");
            }
        }
    }

    private static void verify(OpticalComponentMesh mesh, String name) {
        var faces = mesh.faces();
        var overlaps = new java.util.ArrayList<String>();
        for (int i = 0; i < faces.size(); i++) {
            var a = faces.get(i);
            assertTrue(Double.isFinite(a.normal().lengthSquared()), name + " degenerate face " + i + ": " + a);
            for (int j = i + 1; j < faces.size(); j++) {
                var b = faces.get(j);
                if (a.normal().dotProduct(b.normal()) < .999999 || Math.abs(a.normal().dotProduct(a.a().subtract(b.a()))) > 1e-7) continue;
                if (overlap(a, b)) overlaps.add(i + "/" + j + " at " + a.a() + " / " + b.a() + " normal=" + a.normal());
            }
        }
        assertTrue(overlaps.isEmpty(), name + ": " + overlaps.size() + " coplanar overlaps: " + overlaps.stream().limit(12).toList());
    }

    private static boolean overlap(OpticalComponentMesh.Face a, OpticalComponentMesh.Face b) {
        var aa = List.of(a.a(), a.b(), a.c(), a.d());
        var bb = List.of(b.a(), b.b(), b.c(), b.d());
        for (var polygon : List.of(aa, bb)) for (int i = 0; i < 4; i++) {
            Vec3d edge = polygon.get((i + 1) % 4).subtract(polygon.get(i));
            if (edge.lengthSquared() < 1e-12) continue;
            Vec3d axis = edge.crossProduct(a.normal()).normalize();
            double minA = aa.stream().mapToDouble(axis::dotProduct).min().orElseThrow(), maxA = aa.stream().mapToDouble(axis::dotProduct).max().orElseThrow();
            double minB = bb.stream().mapToDouble(axis::dotProduct).min().orElseThrow(), maxB = bb.stream().mapToDouble(axis::dotProduct).max().orElseThrow();
            if (Math.min(maxA, maxB) - Math.max(minA, minB) <= 1e-6) return false;
        }
        return true;
    }
}

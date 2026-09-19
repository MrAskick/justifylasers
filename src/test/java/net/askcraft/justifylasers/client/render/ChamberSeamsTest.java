package net.askcraft.justifylasers.client.render;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChamberSeamsTest {
    @Test void newProcessMachinesHaveSeparatedSurfaces() {
        verify(ChemicalSynthesizerModel.BODY, "chemical synthesizer");
        verify(ChemicalSynthesizerModel.GAUGE, "synthesizer gauges");
        verify(LaserCutterModel.CASING, "laser cutter casing");
        verify(LaserCutterModel.FRAME, "laser cutter frame");
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

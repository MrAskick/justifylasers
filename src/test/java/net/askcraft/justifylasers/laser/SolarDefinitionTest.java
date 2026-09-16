package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.block.entity.SolarConcentratorBlockEntity;
import net.askcraft.justifylasers.industry.SolarExposure;
import net.askcraft.justifylasers.industry.SolarStructure;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SolarDefinitionTest {
    @Test void fourResonatorsAreOutsideTheBottomSides() {
        assertEquals(31,SolarStructure.PARTS.size());
        assertEquals(31,SolarStructure.PARTS.stream().map(SolarStructure.Cell::offset).distinct().count());
        assertEquals(4,SolarStructure.RESONATORS.size());
        for(var cell:SolarStructure.RESONATORS){
            assertEquals(0,cell.offset().getY());
            assertEquals(new net.minecraft.util.math.BlockPos(1,0,1).offset(SolarStructure.resonatorFacing(cell),2),cell.offset());
        }
    }
    @Test void bodyContainsExactlyTheDocumentedTwentySevenCells() {
        var cells = SolarStructure.BODY;
        assertEquals(27, cells.size());
        assertEquals(27, cells.stream().map(SolarStructure.Cell::offset).distinct().count());
        assertEquals(Map.of("small_solar_concentrator", 17L, "reinforced_laser_housing", 4L, "beam_controller", 4L,
                "energy_core", 1L, "laser_absorbing_glass", 1L), cells.stream().collect(
                Collectors.groupingBy(SolarStructure.Cell::component, Collectors.counting())));
        for (var cell : cells) {
            var pos = cell.offset();
            assertTrue(pos.getX() >= 0 && pos.getX() <= 2 && pos.getY() >= 0 && pos.getY() <= 2 && pos.getZ() >= 0 && pos.getZ() <= 2);
        }
        assertEquals(33, SolarExposure.PANELS.size());
        assertEquals(33, SolarExposure.PANELS.stream().distinct().count());
    }

    @Test void sourceContractDoesNotRelyOnInheritedMinecraftMethodNames() throws ReflectiveOperationException {
        // Inherited getWorld/getPos do not implement an unmapped interface after Forge's SRG remapping.
        for (var type : new Class<?>[]{LaserEmitterBlockEntity.class, SolarConcentratorBlockEntity.class})
            for (String method : new String[]{"beamWorld", "beamPosition"})
                assertEquals(type, type.getDeclaredMethod(method).getDeclaringClass());
        assertThrows(NoSuchMethodException.class, () -> LaserBeamSource.class.getMethod("getWorld"));
        assertThrows(NoSuchMethodException.class, () -> LaserBeamSource.class.getMethod("getPos"));
    }
}

package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaserSettingsTest {
    @Test
    void colorOrderAndValuesRemainCompatibleWithSavedIndices() {
        assertEquals(List.of("red", "orange", "yellow", "green", "cyan", "blue", "violet", "magenta", "white"),
                Arrays.stream(LaserColor.values()).map(LaserColor::asString).toList());
        assertArrayEquals(new int[]{0xFF0808, 0xFF5A00, 0xFFE600, 0x00FF3C, 0x00EEFF,
                        0x1250FF, 0x8A18FF, 0xFF00C8, 0xF8FCFF},
                Arrays.stream(LaserColor.values()).mapToInt(LaserColor::rgb).toArray());

        for (LaserColor color : LaserColor.values()) {
            assertSame(color, LaserColor.byIndex(color.ordinal()));
            assertSame(LaserColor.byIndex(color.ordinal() + 1), color.next());
            assertEquals("gui.justifylasers.color." + color.asString(), color.translationKey());
            assertEquals(color.red(), color.vector().x);
            assertEquals(color.green(), color.vector().y);
            assertEquals(color.blue(), color.vector().z);
        }
        assertSame(LaserColor.WHITE, LaserColor.byIndex(-1));
        assertSame(LaserColor.RED, LaserColor.byIndex(9));
    }

    @Test
    void redstoneModesKeepTheirTruthTableAndSavedOrder() {
        assertArrayEquals(new LaserRedstoneMode[]{LaserRedstoneMode.IGNORE, LaserRedstoneMode.HIGH,
                LaserRedstoneMode.LOW}, LaserRedstoneMode.values());
        assertTrue(LaserRedstoneMode.IGNORE.allows(false));
        assertTrue(LaserRedstoneMode.IGNORE.allows(true));
        assertFalse(LaserRedstoneMode.HIGH.allows(false));
        assertTrue(LaserRedstoneMode.HIGH.allows(true));
        assertTrue(LaserRedstoneMode.LOW.allows(false));
        assertFalse(LaserRedstoneMode.LOW.allows(true));
        for (LaserRedstoneMode mode : LaserRedstoneMode.values()) {
            assertSame(mode, LaserRedstoneMode.byIndex(mode.ordinal()));
            assertSame(LaserRedstoneMode.byIndex(mode.ordinal() + 1), mode.next());
        }
        assertSame(LaserRedstoneMode.LOW, LaserRedstoneMode.byIndex(-1));
    }

    @Test
    void widthIsLogarithmicAndClampedAcrossTheWholeSlider() {
        assertEquals(100, LaserEmitterBlockEntity.DEFAULT_BEAM_WIDTH_STEP);
        for (int step = 0; step <= 200; step++) {
            assertEquals(Math.pow(10.0D, (step - 100) / 100.0D),
                    LaserEmitterBlockEntity.beamWidthScale(step), 0.00001D);
            if (step > 0) {
                assertTrue(LaserEmitterBlockEntity.beamWidthScale(step)
                        > LaserEmitterBlockEntity.beamWidthScale(step - 1));
            }
        }
        assertEquals(0, LaserEmitterBlockEntity.clampBeamWidthStep(Integer.MIN_VALUE));
        assertEquals(200, LaserEmitterBlockEntity.clampBeamWidthStep(Integer.MAX_VALUE));
        assertEquals(0.1F, LaserEmitterBlockEntity.beamWidthScale(-1));
        assertEquals(10.0F, LaserEmitterBlockEntity.beamWidthScale(201));
    }

    @Test
    void screenProtocolPreservesExistingSettings() {
        assertEquals(14, LaserEmitterBlockEntity.PROPERTY_COUNT);
        assertEquals(64, LaserEmitterBlockEntity.DEFAULT_RANGE);
        assertEquals(0.11D, LaserEmitterBlockEntity.BEAM_HIT_RADIUS);
        assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5, 6}, new int[]{
                LaserEmitterScreenHandler.BUTTON_ENABLED,
                LaserEmitterScreenHandler.BUTTON_REDSTONE,
                LaserEmitterScreenHandler.BUTTON_COLOR,
                LaserEmitterScreenHandler.BUTTON_BREAK_BLOCKS,
                LaserEmitterScreenHandler.BUTTON_DAMAGE_ENTITIES,
                LaserEmitterScreenHandler.BUTTON_LIGHT_EMISSION,
                LaserEmitterScreenHandler.BUTTON_MINECRAFT_LIGHTING
        });
        assertEquals(1000, LaserEmitterScreenHandler.BEAM_WIDTH_BUTTON_BASE);
        assertEquals(1200, LaserEmitterScreenHandler.BEAM_WIDTH_BUTTON_MAX);
        assertEquals(7, LaserEmitterScreenHandler.BUTTON_IGNITE_ENTITIES);
        assertTrue(LaserEmitterScreenHandler.DAMAGE_BUTTON_MIN > LaserEmitterScreenHandler.BEAM_WIDTH_BUTTON_MAX);
        assertTrue(LaserEmitterScreenHandler.KNOCKBACK_BUTTON_BASE > LaserEmitterScreenHandler.DAMAGE_BUTTON_MAX);
        assertTrue(LaserEmitterScreenHandler.HIT_RATE_BUTTON_MIN > LaserEmitterScreenHandler.KNOCKBACK_BUTTON_MAX);
        assertTrue(LaserEmitterScreenHandler.HIT_RATE_BUTTON_MAX <= Short.MAX_VALUE);
        assertTrue(LaserEmitterScreenHandler.RANGE_BUTTON_MIN > LaserEmitterScreenHandler.HIT_RATE_BUTTON_MAX);
        assertEquals(5001, LaserEmitterScreenHandler.RANGE_BUTTON_MIN);
        assertEquals(5512, LaserEmitterScreenHandler.RANGE_BUTTON_MAX);
    }

    @Test
    void rangeIsClampedToWholeBlocks() {
        assertEquals(1, LaserEmitterBlockEntity.clampBeamRange(Integer.MIN_VALUE));
        assertEquals(512, LaserEmitterBlockEntity.clampBeamRange(Integer.MAX_VALUE));
        for (int range = 1; range <= 512; range++) {
            assertEquals(range, LaserEmitterBlockEntity.clampBeamRange(range));
        }
    }
}

package net.askcraft.justifylasers.client.compat;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.printing.PrintDesign;
import net.askcraft.justifylasers.printing.PrintExamples;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Sanitized one-block example shared by JEI's encoder and printer recipes. */
final class PrintingPreview {
    private static PrintDesign design;

    static PrintDesign model() {
        if (design == null) {
            try (var input = PrintingPreview.class.getResourceAsStream("/assets/justifylasers/printing/jei_preview.json")) {
                if (input == null) throw new IOException("Missing bundled printer preview");
                design = PrintDesign.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException | IllegalArgumentException failure) {
                JustifyLasers.LOGGER.warn("Could not load the printer recipe preview", failure);
                design = PrintExamples.pedestal();
            }
        }
        return design;
    }

    private PrintingPreview() { }
}

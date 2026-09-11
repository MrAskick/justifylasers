package net.askcraft.justifylasers.integration;

import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

public class LoaderGameTests {
    @GameTest(templateName = "justifylasers_integration:empty", tickLimit = 40)
    public void nativeEnergyAndGameplay(TestContext context) {
        LaserIntegrationChecks.verify(context);
    }
}

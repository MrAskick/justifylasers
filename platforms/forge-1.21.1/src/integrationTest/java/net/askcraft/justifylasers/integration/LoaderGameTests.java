package net.askcraft.justifylasers.integration;

import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.GameTestHolder;

@Mod("justifylasers_integration")
@GameTestHolder("justifylasers_integration")
public class LoaderGameTests {
    @GameTest(templateName = "justifylasers_integration:empty", tickLimit = 60)
    public void mekanismCable(TestContext context) {
        LaserIntegrationChecks.verifyMekanismCable(context);
    }

    @GameTest(templateName = "justifylasers_integration:empty", tickLimit = 40)
    public void nativeEnergyAndGameplay(TestContext context) {
        LaserIntegrationChecks.verify(context);
    }
}

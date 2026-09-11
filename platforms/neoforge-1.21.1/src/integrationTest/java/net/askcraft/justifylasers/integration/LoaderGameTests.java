package net.askcraft.justifylasers.integration;

import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@Mod("justifylasers_integration")
@GameTestHolder("justifylasers_integration")
@PrefixGameTestTemplate(false)
public class LoaderGameTests {
    @GameTest(templateName = "empty", templateNamespace = "justifylasers_integration", tickLimit = 60)
    public void mekanismCable(TestContext context) {
        LaserIntegrationChecks.verifyMekanismCable(context);
    }

    @GameTest(templateName = "empty", templateNamespace = "justifylasers_integration", tickLimit = 40)
    public void nativeEnergyAndGameplay(TestContext context) {
        LaserIntegrationChecks.verify(context);
    }
}

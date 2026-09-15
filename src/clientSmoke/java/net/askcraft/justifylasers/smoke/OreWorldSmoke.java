package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import org.slf4j.LoggerFactory;

public final class OreWorldSmoke {
    private static final String[] ORES = {"photonic_crystal_ore", "wolframite_ore", "deepslate_photonic_crystal_ore", "deepslate_wolframite_ore"};

    static void tick(MinecraftClient client, int frame) {
        if (frame == 30) {
            client.options.getFov().setValue(40);
            client.getServer().execute(() -> scene(client));
        }
        if (frame == 110) {
            verify(client);
            capture(client, "photonite");
            camera(client, new Vec3d(462, 4.2, 457.5), new Vec3d(464.5, 3.5, 460.5));
        }
        if (frame == 155) {
            capture(client, "wolframite");
            camera(client, new Vec3d(468.5, 5.1, 455.5), new Vec3d(469.5, 3.5, 460.5));
        }
        if (frame == 200) {
            capture(client, "adjacent-faces");
            client.setScreen(new Gallery());
        }
        if (frame == 230) {
            capture(client, "items");
            client.setScreen(null);
            client.reloadResources();
        }
        if (frame == 300) {
            verify(client);
            capture(client, "reloaded");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("ORE_WORLD_SMOKE_PASSED shader={}", IrisCompatibility.isShaderPackInUse());
            client.scheduleStop();
        }
    }

    private static void verify(MinecraftClient client) {
        for (String id : ORES) {
            var block = ModIndustry.ORES.get(id);
            checkModel(client.getBlockRenderManager().getModel(block.getDefaultState()), id);
            checkModel(client.getItemRenderer().getModel(new ItemStack(block), client.world, client.player, 0), id + " item");
        }
    }

    private static void checkModel(BakedModel model, String name) {
        for (Direction face : Direction.values()) {
            var quads = model.getQuads(null, face, Random.create(0));
            long inclusions = quads.stream().filter(q -> q.getSprite().getContents().getId().getPath().startsWith("component/")).count();
            if (inclusions != 78) throw new AssertionError("Incorrect mineral geometry on " + name + "/" + face + ": " + inclusions);
            for (var quad : quads) {
                var data = quad.getVertexData();
                if (data.length < 32) throw new AssertionError("Truncated baked quad: " + name);
                for (int vertex = 0; vertex < 4; vertex++) for (int axis = 0; axis < 3; axis++)
                    if (!Float.isFinite(Float.intBitsToFloat(data[vertex * (data.length / 4) + axis])))
                        throw new AssertionError("Invalid vertex position: " + name);
            }
        }
        if (model.getQuads(null, null, Random.create(0)).stream()
                .anyMatch(q -> q.getSprite().getContents().getId().getPath().startsWith("component/")))
            throw new AssertionError("Buried inclusions must be culled with their stone face: " + name);
        if (!model.getParticleSprite().getContents().getId().getPath().equals(name.startsWith("deepslate") ? "block/deepslate" : "block/stone"))
            throw new AssertionError("Incorrect ore particles: " + name);
    }

    private static void scene(MinecraftClient client) {
        var world = client.getServer().getOverworld();
        world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, client.getServer());
        world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, client.getServer());
        world.setTimeOfDay(9000);
        for (int x = 454; x <= 477; x++) for (int z = 452; z <= 466; z++) {
            world.setBlockState(new BlockPos(x, 1, z), Blocks.POLISHED_ANDESITE.getDefaultState());
            for (int y = 2; y < 10; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
        }
        for (int i = 0; i < 2; i++) {
            var pos = new BlockPos(460 + i * 4, 3, 460);
            world.setBlockState(pos.down(), Blocks.STONE_BRICKS.getDefaultState());
            world.setBlockState(pos, ModIndustry.ORES.get(ORES[i]).getDefaultState());
        }
        for (int x = 468; x <= 470; x++) for (int y = 2; y <= 4; y++)
            world.setBlockState(new BlockPos(x, y, 460), ModIndustry.ORES.get(ORES[(x + y) % 4]).getDefaultState());
        var player = player(client);
        player.changeGameMode(GameMode.CREATIVE);
        player.getInventory().clear();
        player.getAbilities().flying = true;
        player.sendAbilitiesUpdate();
        camera(client, new Vec3d(458, 4.2, 457.5), new Vec3d(460.5, 3.5, 460.5));
    }

    private static void camera(MinecraftClient client, Vec3d position, Vec3d target) {
        client.getServer().execute(() -> {
            var direction = target.subtract(position.add(0, 1.62, 0));
            float yaw = (float)Math.toDegrees(Math.atan2(-direction.x, direction.z));
            float pitch = (float)-Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z)));
            player(client).teleport(client.getServer().getOverworld(), position.x, position.y, position.z, yaw, pitch);
        });
    }

    private static ServerPlayerEntity player(MinecraftClient client) {
        return client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
    }

    private static void capture(MinecraftClient client, String name) {
        ScreenshotRecorder.saveScreenshot(client.runDirectory, "ores-" + (IrisCompatibility.isShaderPackInUse() ? "kappa-" : "vanilla-") + name + ".png",
                client.getFramebuffer(), text -> { });
    }

    private static final class Gallery extends Screen {
        private Gallery() { super(Text.literal("Ore model check")); }
        @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0xff252b35);
            for (int i = 0; i < ORES.length; i++) {
                int x = width / 4 + (i % 2) * width / 2, y = height / 4 + (i / 2) * height / 2;
                var matrices = context.getMatrices();
                matrices.push(); matrices.translate(x - 48, y - 48, 0); matrices.scale(6, 6, 6);
                context.drawItem(new ItemStack(ModIndustry.ORES.get(ORES[i])), 0, 0);
                matrices.pop();
                context.drawCenteredTextWithShadow(textRenderer, ORES[i], x, y + 55, 0xffffff);
            }
        }
        @Override public boolean shouldPause() { return false; }
    }

    private OreWorldSmoke() { }
}

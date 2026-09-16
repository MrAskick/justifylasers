package net.askcraft.justifylasers.mixin.client;

import net.askcraft.justifylasers.block.LaserOpticBlock;
import net.askcraft.justifylasers.client.render.LaserOpticRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BuiltinModelItemRenderer.class)
public abstract class BuiltinItemRendererMixin {
    @Inject(method = "reload", at = @At("TAIL"))
    private void justifylasers$reloadSchematicIcons(CallbackInfo ci) {
        net.askcraft.justifylasers.client.render.SchematicIcons.clear();
    }
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void justifylasers$renderOptic(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                                         VertexConsumerProvider consumers, int light, int overlay, CallbackInfo ci) {
        if (stack.isOf(net.askcraft.justifylasers.registry.ModIndustry.SMALL_SOLAR_CONCENTRATOR.asItem())) {
            matrices.push(); matrices.translate(.5,.5,.5);
            net.askcraft.justifylasers.client.render.SmallSolarConcentratorModel.render(null,0,matrices,consumers,light);
            matrices.pop(); ci.cancel(); return;
        }
        if (stack.getItem() instanceof net.askcraft.justifylasers.item.ExtraterrestrialTabletItem) {
            net.askcraft.justifylasers.client.render.TabletRenderer.renderItem(stack, mode, matrices, consumers, light);
            ci.cancel(); return;
        }
        if (stack.getItem() instanceof net.askcraft.justifylasers.item.LaserComponentItem part) {
            net.askcraft.justifylasers.client.render.LaserComponentRenderer.render(part.component(), mode, matrices, consumers, light);
            ci.cancel(); return;
        }
        if (stack.getItem() instanceof net.askcraft.justifylasers.item.AssemblyBlueprintItem
                || stack.isOf(net.askcraft.justifylasers.registry.ModIndustry.BLANK_SCHEMATIC)) {
            net.askcraft.justifylasers.client.render.BlueprintRenderer.render(stack, matrices, consumers, light, overlay);
            ci.cancel(); return;
        }
        if (stack.isOf(net.askcraft.justifylasers.registry.ModIndustry.PHOTONITE_CRYSTAL)) {
            net.askcraft.justifylasers.client.render.LaserCrystalModel.renderBare(mode, matrices, consumers, light, overlay);
            ci.cancel();
            return;
        }
        if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof net.askcraft.justifylasers.block.IndustrialMachineBlock machine) {
            net.askcraft.justifylasers.client.render.IndustrialMachineRenderer.renderItem(machine.kind(), matrices, consumers, light, overlay);
            ci.cancel();
            return;
        }
        if (stack.getItem() instanceof net.askcraft.justifylasers.item.LaserSaberItem) {
            net.askcraft.justifylasers.client.render.LaserSaberRenderer.renderItem(stack, mode, matrices, consumers, light);
            ci.cancel();
            return;
        }
        if (stack.getItem() instanceof net.askcraft.justifylasers.item.LaserGunItem) {
            net.askcraft.justifylasers.client.render.LaserGunRenderer.renderItem(stack, mode, matrices, consumers, light);
            ci.cancel();
        } else if (stack.isOf(net.askcraft.justifylasers.registry.ModBlocks.CONFIGURATOR)) {
            net.askcraft.justifylasers.client.render.LaserConfiguratorModel.render(matrices, consumers, light);
            ci.cancel();
        } else if (stack.isOf(net.askcraft.justifylasers.registry.ModBlocks.LASER_TURRET.asItem())) {
            net.askcraft.justifylasers.client.render.LaserTurretRenderer.renderItem(matrices, consumers, light);
            ci.cancel();
        }
        if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof LaserOpticBlock optic) {
            LaserOpticRenderer.renderItem(optic, matrices, consumers, light, overlay);
            ci.cancel();
        }
    }
}

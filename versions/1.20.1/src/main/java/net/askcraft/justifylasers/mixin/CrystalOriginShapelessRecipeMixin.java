package net.askcraft.justifylasers.mixin;

import net.askcraft.justifylasers.industry.CrystalSeed;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.registry.DynamicRegistryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShapelessRecipe.class)
abstract class CrystalOriginShapelessRecipeMixin {
    @Inject(method="craft(Lnet/minecraft/inventory/RecipeInputInventory;Lnet/minecraft/registry/DynamicRegistryManager;)Lnet/minecraft/item/ItemStack;",at=@At("RETURN"),cancellable=true)
    private void justifylasers$preserveCrystalOrigin(RecipeInputInventory input, DynamicRegistryManager registry, CallbackInfoReturnable<ItemStack> callback) {
        callback.setReturnValue(CrystalSeed.crafted(callback.getReturnValue(),input.size(),input::getStack));
    }
}

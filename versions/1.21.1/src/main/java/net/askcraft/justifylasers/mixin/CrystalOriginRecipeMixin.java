package net.askcraft.justifylasers.mixin;

import net.askcraft.justifylasers.industry.CrystalSeed;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShapedRecipe.class)
abstract class CrystalOriginRecipeMixin {
    @Inject(method="craft(Lnet/minecraft/recipe/input/CraftingRecipeInput;Lnet/minecraft/registry/RegistryWrapper$WrapperLookup;)Lnet/minecraft/item/ItemStack;",at=@At("RETURN"),cancellable=true)
    private void justifylasers$preserveCrystalOrigin(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registry, CallbackInfoReturnable<ItemStack> callback) {
        callback.setReturnValue(CrystalSeed.crafted(callback.getReturnValue(),input.getSize(),input::getStackInSlot));
    }
}

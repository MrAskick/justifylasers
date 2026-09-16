package net.askcraft.justifylasersbridge;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.util.Identifier;

// The universal packager adds the discovery annotation to this single loader-neutral entry.
public final class JeiBridge implements IModPlugin {
    private final IModPlugin delegate = (IModPlugin) LoaderBridge.plugin("client.compat.JustifyLasersJeiPlugin");
    @Override public Identifier getPluginUid() { return delegate.getPluginUid(); }
    @Override public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime runtime) { delegate.onRuntimeAvailable(runtime); }
    @Override public void onRuntimeUnavailable() { delegate.onRuntimeUnavailable(); }
    @Override public void registerCategories(IRecipeCategoryRegistration registration) { delegate.registerCategories(registration); }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) { delegate.registerRecipeCatalysts(registration); }
    @Override public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) { delegate.registerRecipeTransferHandlers(registration); }
    @Override public void registerRecipes(IRecipeRegistration registration) { delegate.registerRecipes(registration); }
}

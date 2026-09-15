package net.askcraft.justifylasers.compat;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class LoaderMixinPlugin implements IMixinConfigPlugin {
    private boolean enabled = true;

    @Override public void onLoad(String mixinPackage) {
        // NeoForge also reads Forge's manifest entry in a universal JAR. Only one
        // loader's hooks may be applied, especially hooks targeting optional Iris classes.
        if (mixinPackage.contains(".loader.forge.")) {
            try {
                Class.forName("net.neoforged.fml.loading.FMLLoader", false, getClass().getClassLoader());
                enabled = false;
            } catch (ClassNotFoundException ignored) {
                // Forge and legacy NeoForge 47 use the same Forge implementation.
            }
        }
    }

    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return enabled; }
    @Override public String getRefMapperConfig() { return null; }
    @Override public List<String> getMixins() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}

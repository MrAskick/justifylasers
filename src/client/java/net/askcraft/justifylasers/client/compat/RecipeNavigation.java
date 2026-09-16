package net.askcraft.justifylasers.client.compat;

import net.askcraft.justifylasers.industry.MachineKind;

import java.util.function.Consumer;

/** GUI hooks remain safe to load when no recipe viewer is installed. */
public final class RecipeNavigation {
    private static Consumer<MachineKind> recipes;
    private static Runnable construction;

    static void install(Consumer<MachineKind> viewer, Runnable multiblocks) {
        recipes = viewer;
        construction = multiblocks;
    }

    public static boolean available() { return recipes != null; }
    public static boolean show(MachineKind kind) {
        if (recipes == null || kind == MachineKind.FUEL_GENERATOR) return false;
        recipes.accept(kind);
        return true;
    }
    public static void showConstruction() { if (construction != null) construction.run(); }

    private RecipeNavigation() { }
}

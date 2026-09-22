package net.askcraft.justifylasers.client.compat;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModEntities;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

@JeiPlugin
public final class JustifyLasersJeiPlugin implements IModPlugin {
    @Override public void registerItemSubtypes(mezz.jei.api.registration.ISubtypeRegistration registration) {
        for (var item : java.util.List.of(net.askcraft.justifylasers.registry.ModIndustry.MODEL_SCHEMATIC, net.askcraft.justifylasers.registry.ModIndustry.PRINTED_MODEL.asItem()))
            registration.registerSubtypeInterpreter(item, (mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter<ItemStack>)(stack, context) -> {
                var model = net.askcraft.justifylasers.printing.PrintData.read(stack);
                return model == null ? "" : model.json();
            });
        registration.registerSubtypeInterpreter(net.askcraft.justifylasers.registry.ModNutrients.GROWTH_SEED,
                (mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter<ItemStack>)(stack, context) -> {
                    var type = net.askcraft.justifylasers.item.GrowthSeedItem.type(stack);
                    return type == null ? "" : type.id() + ":" + type.stage(stack);
                });
        registration.registerSubtypeInterpreter(net.askcraft.justifylasers.registry.ModIndustry.ASSEMBLY_BLUEPRINT,
                (mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter<ItemStack>)(stack, context) -> net.askcraft.justifylasers.item.AssemblyBlueprintItem.recipe(stack));
        registration.registerSubtypeInterpreter(ModLaserParts.AMPLIFIER,
                (mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter<ItemStack>)(stack, context) -> Integer.toString(net.askcraft.justifylasers.item.LaserAmplifierItem.tier(stack)));
    }

    @Override public void registerExtraIngredients(mezz.jei.api.registration.IExtraIngredientRegistration registration) {
        var model = PrintingPreview.model();
        registration.addExtraItemStacks(java.util.List.of(net.askcraft.justifylasers.printing.PrintData.schematic(model), net.askcraft.justifylasers.printing.PrintData.printed(model)));
        registration.addExtraItemStacks(java.util.Arrays.stream(net.askcraft.justifylasers.industry.CrystalGrowth.values())
                .map(crystal -> crystal.seed(0)).toList());
        registration.addExtraItemStacks(net.askcraft.justifylasers.registry.ModIndustry.BLUEPRINTS.values().stream()
                .map(net.askcraft.justifylasers.item.AssemblyBlueprintItem::getDefaultStack).toList());
        registration.addExtraItemStacks(net.askcraft.justifylasers.energy.AmplifierTier.AVAILABLE.stream().filter(tier -> tier > 1)
                .map(net.askcraft.justifylasers.item.LaserAmplifierItem::stack).toList());
    }
    @Override
    public Identifier getPluginUid() { return JustifyLasers.id("guide"); }

    @Override public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime runtime) {
        RecipeNavigation.install(kind -> runtime.getRecipesGui().showTypes(java.util.List.of(kind.programmable() ? PrintingJeiCategory.type(kind) : IndustryJeiCategory.type(kind))),
                () -> runtime.getRecipesGui().showTypes(java.util.List.of(MultiblockJeiCategory.TYPE)));
        var ingredients = runtime.getIngredientManager();
        var legacy = ingredients.getAllIngredients(VanillaTypes.ITEM_STACK).stream()
                .filter(stack -> stack.isOf(net.askcraft.justifylasers.registry.ModIndustry.LASER_CHASSIS)
                        || (stack.isOf(net.askcraft.justifylasers.registry.ModIndustry.MODEL_SCHEMATIC) || stack.isOf(net.askcraft.justifylasers.registry.ModIndustry.PRINTED_MODEL.asItem())) && net.askcraft.justifylasers.printing.PrintData.read(stack) == null
                        || stack.isOf(net.askcraft.justifylasers.registry.ModIndustry.OPTICAL_ASSEMBLY)
                        || stack.getItem() instanceof net.askcraft.justifylasers.item.LegacyCrystalSeedItem
                        || stack.isOf(ModLaserParts.AMPLIFIER) && !net.askcraft.justifylasers.energy.AmplifierTier.AVAILABLE.contains(net.askcraft.justifylasers.item.LaserAmplifierItem.tier(stack))
                        || stack.isOf(net.askcraft.justifylasers.registry.ModNutrients.GROWTH_SEED) && net.askcraft.justifylasers.item.GrowthSeedItem.type(stack) == null
                        || stack.getItem() instanceof net.askcraft.justifylasers.item.AssemblyBlueprintItem blueprint && !blueprint.recipe().isEmpty()
                        || stack.isOf(net.askcraft.justifylasers.registry.ModIndustry.ASSEMBLY_BLUEPRINT) && net.askcraft.justifylasers.item.AssemblyBlueprintItem.recipe(stack).isEmpty()).toList();
        if (!legacy.isEmpty()) ingredients.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, legacy);
    }

    @Override public void onRuntimeUnavailable() { RecipeNavigation.install(null, null); }

    @Override public void registerCategories(mezz.jei.api.registration.IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new GrowthSeedJeiCategory(registration.getJeiHelpers()));
        registration.addRecipeCategories(new AmplifierJeiCategory(registration.getJeiHelpers()));
        registration.addRecipeCategories(new MultiblockJeiCategory(registration.getJeiHelpers()));
        for (var kind : net.askcraft.justifylasers.industry.MachineKind.values())
            if (kind.programmable()) registration.addRecipeCategories(new PrintingJeiCategory(kind, registration.getJeiHelpers()));
            else if (kind != net.askcraft.justifylasers.industry.MachineKind.FUEL_GENERATOR)
                registration.addRecipeCategories(new IndustryJeiCategory(kind, registration.getJeiHelpers()));
    }

    @Override public void registerRecipeCatalysts(mezz.jei.api.registration.IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(net.minecraft.item.Items.CRAFTING_TABLE), GrowthSeedJeiCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(net.minecraft.item.Items.CRAFTING_TABLE), AmplifierJeiCategory.TYPE);
        for (var construction : MultiblockConstruction.all()) {
            registration.addRecipeCatalyst(construction.result(), MultiblockJeiCategory.TYPE);
        }
        net.askcraft.justifylasers.registry.ModIndustry.MACHINES.forEach((kind, block) -> {
            if (kind.programmable()) registration.addRecipeCatalyst(new ItemStack(block), PrintingJeiCategory.type(kind));
            else if (kind != net.askcraft.justifylasers.industry.MachineKind.FUEL_GENERATOR)
                registration.addRecipeCatalyst(new ItemStack(block), IndustryJeiCategory.type(kind));
        });
    }

    @Override public void registerRecipeTransferHandlers(mezz.jei.api.registration.IRecipeTransferRegistration registration) {
        for (var kind : net.askcraft.justifylasers.industry.MachineKind.values()) {
            if (kind == net.askcraft.justifylasers.industry.MachineKind.FUEL_GENERATOR || kind.programmable()) continue;
            registration.addRecipeTransferHandler(new mezz.jei.api.recipe.transfer.IRecipeTransferInfo<net.askcraft.justifylasers.screen.IndustrialMachineScreenHandler, net.askcraft.justifylasers.industry.MachineRecipeData>() {
                @Override public Class<net.askcraft.justifylasers.screen.IndustrialMachineScreenHandler> getContainerClass() { return net.askcraft.justifylasers.screen.IndustrialMachineScreenHandler.class; }
                @Override public java.util.Optional<net.minecraft.screen.ScreenHandlerType<net.askcraft.justifylasers.screen.IndustrialMachineScreenHandler>> getMenuType() { return java.util.Optional.of(net.askcraft.justifylasers.registry.ModScreenHandlers.INDUSTRIAL_MACHINE); }
                @Override public mezz.jei.api.recipe.RecipeType<net.askcraft.justifylasers.industry.MachineRecipeData> getRecipeType() { return IndustryJeiCategory.type(kind); }
                @Override public boolean canHandle(net.askcraft.justifylasers.screen.IndustrialMachineScreenHandler menu, net.askcraft.justifylasers.industry.MachineRecipeData recipe) {
                    return menu.kind() == kind && menu.formed() && (recipe.blueprint().isEmpty()
                            || menu.recipe() != null && menu.recipe().blueprint().equals(recipe.blueprint()));
                }
                @Override public java.util.List<net.minecraft.screen.slot.Slot> getRecipeSlots(net.askcraft.justifylasers.screen.IndustrialMachineScreenHandler menu, net.askcraft.justifylasers.industry.MachineRecipeData recipe) { return menu.slots.subList(0, recipe.inputs().size()); }
                @Override public java.util.List<net.minecraft.screen.slot.Slot> getInventorySlots(net.askcraft.justifylasers.screen.IndustrialMachineScreenHandler menu, net.askcraft.justifylasers.industry.MachineRecipeData recipe) { return menu.slots.subList(net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity.SLOT_COUNT, menu.slots.size()); }
            });
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (var kind : net.askcraft.justifylasers.industry.MachineKind.values()) if (kind.programmable())
            registration.addRecipes(PrintingJeiCategory.type(kind), java.util.List.of(PrintingPreview.model()));
        registration.addRecipes(GrowthSeedJeiCategory.TYPE, java.util.List.of(net.askcraft.justifylasers.industry.CrystalGrowth.values()));
        registration.addRecipes(AmplifierJeiCategory.TYPE, net.askcraft.justifylasers.energy.AmplifierTier.AVAILABLE.stream().filter(tier -> tier > 1)
                .map(AmplifierJeiCategory.Upgrade::new).toList());
        info(registration, ModLaserParts.AMPLIFIER, "amplifier_module");
        registration.addRecipes(MultiblockJeiCategory.TYPE, MultiblockConstruction.all());
        registration.addIngredientInfo(net.askcraft.justifylasers.item.AssemblyBlueprintItem.stack("extraterrestrial_tablet"),
                VanillaTypes.ITEM_STACK, Text.translatable("guide.justifylasers.tablet_schematic"));
        var world = net.minecraft.client.MinecraftClient.getInstance().world;
        if (world != null) {
            var recipes = net.askcraft.justifylasers.industry.IndustryRecipe.all(world);
            for (var kind : net.askcraft.justifylasers.industry.MachineKind.values())
                if (kind != net.askcraft.justifylasers.industry.MachineKind.FUEL_GENERATOR && !kind.programmable())
                    registration.addRecipes(IndustryJeiCategory.type(kind), recipes.stream().filter(recipe -> recipe.kind() == kind).toList());
        }
        info(registration, ModBlocks.LASER_EMITTER, "laser_emitter");
        info(registration, ModBlocks.LASER_RECEIVER, "laser_receiver");
        info(registration, ModEntities.REFOCUSING_CUBE_ITEM, "refocusing_cube");
        info(registration, ModBlocks.LASER_MIRROR, "laser_mirror");
        info(registration, ModBlocks.BEAM_SPLITTER, "beam_splitter");
        info(registration, ModBlocks.BEAM_COMBINER, "beam_combiner");
        info(registration, ModBlocks.CONFIGURATOR, "configurator");
        info(registration, ModBlocks.LASER_GUN, "laser_gun");
        info(registration, ModBlocks.LASER_SABER, "laser_saber");
        info(registration, ModBlocks.LIGHT_STAFF, "light_staff");
        info(registration, ModBlocks.LASER_TURRET, "laser_turret");
        info(registration, ModLaserParts.MODULES.get(LaserModule.TARGET_FILTER), "target_filter_module");
        ModLaserParts.CRYSTALS.values().forEach(item -> info(registration, item, "crystal"));
        if (LaserConfig.technicalMode()) {
            net.askcraft.justifylasers.registry.ModIndustry.MACHINES.forEach((kind, block) -> info(registration, block, kind.id()));
            info(registration, net.askcraft.justifylasers.registry.ModIndustry.RAW_WOLFRAMITE, "raw_wolframite");
            info(registration, net.askcraft.justifylasers.registry.ModIndustry.RAW_PHOTONIC_CRYSTAL, "raw_photonic_crystal");
            info(registration, net.askcraft.justifylasers.registry.ModIndustry.EXTRATERRESTRIAL_TABLET, "extraterrestrial_tablet");
            info(registration, net.askcraft.justifylasers.registry.ModIndustry.SMALL_SOLAR_CONCENTRATOR, "small_solar_concentrator");
            info(registration, net.askcraft.justifylasers.registry.ModIndustry.SOLAR_ABSORBER, "solar_absorber");
            info(registration, net.askcraft.justifylasers.registry.ModIndustry.LASER_ABSORBING_GLASS, "laser_absorbing_glass");
            info(registration, net.askcraft.justifylasers.registry.ModIndustry.ELECTRIC_MOTOR, "electric_motor");
            info(registration, ModBlocks.POWERED_LASER_EMITTER, "powered_laser_emitter");
            info(registration, ModBlocks.ENERGY_RECEIVER, "energy_receiver");
            info(registration, ModLaserParts.ADVANCED_RANGE_MODULE, "advanced_range_module");
            info(registration, ModLaserParts.CONTROL_CIRCUIT, "control_circuit");
            ModLaserParts.MODULES.forEach((module, item) -> {
                if (module != LaserModule.TARGET_FILTER) info(registration, item, module.id());
            });
        }
    }

    private static void info(IRecipeRegistration registration, ItemConvertible item, String key) {
        registration.addIngredientInfo(new ItemStack(item), VanillaTypes.ITEM_STACK,
                Text.translatable("guide.justifylasers." + key));
    }
}

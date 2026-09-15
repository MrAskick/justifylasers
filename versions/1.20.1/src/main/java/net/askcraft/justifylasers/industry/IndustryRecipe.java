package net.askcraft.justifylasers.industry;

import com.google.gson.JsonObject;
import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.platform.Platform;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public record IndustryRecipe(Identifier id, MachineRecipeData data) implements Recipe<Inventory> {
    public static final RecipeType<IndustryRecipe> TYPE = new RecipeType<>() { public String toString() { return "justifylasers:industrial"; } };
    public static final RecipeSerializer<IndustryRecipe> SERIALIZER = new Serializer();
    public static void initialize() {
        Platform.onRegister(RegistryKeys.RECIPE_TYPE, () -> Platform.register(Registries.RECIPE_TYPE, JustifyLasers.id("industrial"), TYPE));
        Platform.onRegister(RegistryKeys.RECIPE_SERIALIZER, () -> Platform.register(Registries.RECIPE_SERIALIZER, JustifyLasers.id("industrial"), SERIALIZER));
    }
    public static List<MachineRecipeData> all(World world) { return world.getRecipeManager().listAllOfType(TYPE).stream().map(IndustryRecipe::data).toList(); }
    @Override public boolean matches(Inventory inventory, World world) { return data.matches(inventory); }
    @Override public ItemStack craft(Inventory inventory, DynamicRegistryManager registry) { return data.output(inventory); }
    @Override public boolean fits(int width, int height) { return width * height >= data.inputs().size(); }
    @Override public ItemStack getOutput(DynamicRegistryManager registry) { return data.result().copy(); }
    @Override public Identifier getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return SERIALIZER; }
    @Override public RecipeType<?> getType() { return TYPE; }
    @Override public boolean isIgnoredInRecipeBook() { return true; }
    @Override public DefaultedList<Ingredient> getIngredients() { var list = DefaultedList.<Ingredient>of(); list.addAll(data.inputs()); return list; }

    private static final class Serializer implements RecipeSerializer<IndustryRecipe> {
        @Override public IndustryRecipe read(Identifier id, JsonObject json) {
            List<Ingredient> inputs = new ArrayList<>(); List<Integer> counts = new ArrayList<>();
            for (var entry : JsonHelper.getArray(json, "inputs")) {
                var input = entry.getAsJsonObject();
                inputs.add(Ingredient.fromJson(input.get("ingredient"))); counts.add(JsonHelper.getInt(input, "count", 1));
            }
            return new IndustryRecipe(id, new MachineRecipeData(JsonHelper.getString(json, "key"), MachineKind.valueOf(JsonHelper.getString(json, "machine")),
                    JsonHelper.getString(json, "blueprint", ""), inputs, counts, ShapedRecipe.outputFromJson(JsonHelper.getObject(json, "result")),
                    JsonHelper.getInt(json, "ticks", 0), JsonHelper.getInt(json, "energy", 0), JsonHelper.getInt(json, "water", 0)));
        }
        @Override public IndustryRecipe read(Identifier id, PacketByteBuf buffer) {
            String key = buffer.readString(128); MachineKind kind = buffer.readEnumConstant(MachineKind.class); String blueprint = buffer.readString(128);
            int size = buffer.readVarInt();
            if (size < 1 || size > 4) throw new IllegalArgumentException("Invalid industrial ingredient count");
            List<Ingredient> ingredients = new ArrayList<>(); List<Integer> counts = new ArrayList<>();
            for (int i = 0; i < size; i++) { ingredients.add(Ingredient.fromPacket(buffer)); counts.add(buffer.readVarInt()); }
            return new IndustryRecipe(id, new MachineRecipeData(key, kind, blueprint, ingredients, counts, buffer.readItemStack(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()));
        }
        @Override public void write(PacketByteBuf buffer, IndustryRecipe recipe) {
            var data = recipe.data;
            buffer.writeString(data.key()); buffer.writeEnumConstant(data.kind()); buffer.writeString(data.blueprint()); buffer.writeVarInt(data.inputs().size());
            for (int i = 0; i < data.inputs().size(); i++) { data.inputs().get(i).write(buffer); buffer.writeVarInt(data.counts().get(i)); }
            buffer.writeItemStack(data.result()); buffer.writeVarInt(data.ticks()); buffer.writeVarInt(data.energy()); buffer.writeVarInt(data.water());
        }
    }
}

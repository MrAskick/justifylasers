package net.askcraft.justifylasers.industry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.platform.Platform;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.RecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

import java.util.List;

public record IndustryRecipe(MachineRecipeData data) implements Recipe<RecipeInput> {
    public static final RecipeType<IndustryRecipe> TYPE = new RecipeType<>() { public String toString() { return "justifylasers:industrial"; } };
    public static final RecipeSerializer<IndustryRecipe> SERIALIZER = new Serializer();
    public static void initialize() {
        Platform.onRegister(RegistryKeys.RECIPE_TYPE, () -> Platform.register(Registries.RECIPE_TYPE, JustifyLasers.id("industrial"), TYPE));
        Platform.onRegister(RegistryKeys.RECIPE_SERIALIZER, () -> Platform.register(Registries.RECIPE_SERIALIZER, JustifyLasers.id("industrial"), SERIALIZER));
    }
    public static List<MachineRecipeData> all(World world) { return world.getRecipeManager().listAllOfType(TYPE).stream().map(entry -> entry.value().data()).toList(); }
    private static SimpleInventory inventory(RecipeInput input) {
        var inventory = new SimpleInventory(4);
        for (int i = 0; i < Math.min(4, input.getSize()); i++) inventory.setStack(i, input.getStackInSlot(i));
        return inventory;
    }
    @Override public boolean matches(RecipeInput input, World world) { return input.getSize() <= 4 && data.matches(inventory(input)); }
    @Override public ItemStack craft(RecipeInput input, RegistryWrapper.WrapperLookup registry) { return data.output(inventory(input)); }
    @Override public boolean fits(int width, int height) { return width * height >= data.inputs().size(); }
    @Override public ItemStack getResult(RegistryWrapper.WrapperLookup registry) { return data.result().copy(); }
    @Override public RecipeSerializer<?> getSerializer() { return SERIALIZER; }
    @Override public RecipeType<?> getType() { return TYPE; }
    @Override public boolean isIgnoredInRecipeBook() { return true; }
    @Override public DefaultedList<Ingredient> getIngredients() { var list = DefaultedList.<Ingredient>of(); list.addAll(data.inputs()); return list; }

    private record Input(Ingredient ingredient, int count) {
        static final Codec<Input> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Ingredient.DISALLOW_EMPTY_CODEC.fieldOf("ingredient").forGetter(Input::ingredient),
                Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Input::count)).apply(instance, Input::new));
    }
    private static final class Serializer implements RecipeSerializer<IndustryRecipe> {
        private static final MapCodec<IndustryRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("key").forGetter((IndustryRecipe recipe) -> recipe.data.key()),
                Codec.STRING.xmap(MachineKind::valueOf, MachineKind::name).fieldOf("machine").forGetter(recipe -> recipe.data.kind()),
                Codec.STRING.optionalFieldOf("blueprint", "").forGetter(recipe -> recipe.data.blueprint()),
                Input.CODEC.listOf().fieldOf("inputs").forGetter(recipe -> java.util.stream.IntStream.range(0, recipe.data.inputs().size()).mapToObj(i -> new Input(recipe.data.inputs().get(i), recipe.data.counts().get(i))).toList()),
                ItemStack.VALIDATED_CODEC.fieldOf("result").forGetter(recipe -> recipe.data.result()),
                Codec.intRange(0, 72_000).optionalFieldOf("ticks", 0).forGetter(recipe -> recipe.data.ticks()),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("energy", 0).forGetter(recipe -> recipe.data.energy()),
                Codec.intRange(0, 64_000).optionalFieldOf("water", 0).forGetter(recipe -> recipe.data.water())
        ).apply(instance, (key, machine, blueprint, inputs, result, ticks, energy, water) -> new IndustryRecipe(new MachineRecipeData(key, machine, blueprint,
                inputs.stream().map(Input::ingredient).toList(), inputs.stream().map(Input::count).toList(), result, ticks, energy, water))));
        private static final PacketCodec<RegistryByteBuf, IndustryRecipe> PACKET = new PacketCodec<>() {
            @Override public IndustryRecipe decode(RegistryByteBuf buffer) {
                String key = buffer.readString(128); MachineKind kind = buffer.readEnumConstant(MachineKind.class); String blueprint = buffer.readString(128);
                int size = buffer.readVarInt(); if (size < 1 || size > 4) throw new IllegalArgumentException("Invalid industrial ingredient count");
                var ingredients = new java.util.ArrayList<Ingredient>(); var counts = new java.util.ArrayList<Integer>();
                for (int i = 0; i < size; i++) { ingredients.add(Ingredient.PACKET_CODEC.decode(buffer)); counts.add(buffer.readVarInt()); }
                return new IndustryRecipe(new MachineRecipeData(key, kind, blueprint, ingredients, counts, ItemStack.PACKET_CODEC.decode(buffer), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()));
            }
            @Override public void encode(RegistryByteBuf buffer, IndustryRecipe recipe) {
                var data = recipe.data;
                buffer.writeString(data.key()); buffer.writeEnumConstant(data.kind()); buffer.writeString(data.blueprint()); buffer.writeVarInt(data.inputs().size());
                for (int i = 0; i < data.inputs().size(); i++) { Ingredient.PACKET_CODEC.encode(buffer, data.inputs().get(i)); buffer.writeVarInt(data.counts().get(i)); }
                ItemStack.PACKET_CODEC.encode(buffer, data.result()); buffer.writeVarInt(data.ticks()); buffer.writeVarInt(data.energy()); buffer.writeVarInt(data.water());
            }
        };
        @Override public MapCodec<IndustryRecipe> codec() { return CODEC; }
        @Override public PacketCodec<RegistryByteBuf, IndustryRecipe> packetCodec() { return PACKET; }
    }
}

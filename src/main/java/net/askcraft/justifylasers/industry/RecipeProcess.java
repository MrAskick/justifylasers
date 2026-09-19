package net.askcraft.justifylasers.industry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.PacketByteBuf;

/** Additional fluid/optical requirements. Legacy recipes keep their original defaults. */
public record RecipeProcess(String fluid, String product, int minimumFlux, int spectrum, String growth, int cuttingFlux) {
    public static final RecipeProcess BASIC = new RecipeProcess("water", "", 0, -1, "", 0);
    public static final Codec<RecipeProcess> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("fluid", "water").forGetter(RecipeProcess::fluid),
            Codec.STRING.optionalFieldOf("product", "").forGetter(RecipeProcess::product),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("minimum_flux", 0).forGetter(RecipeProcess::minimumFlux),
            Codec.intRange(-1, 0xFFFFFF).optionalFieldOf("spectrum", -1).forGetter(RecipeProcess::spectrum),
            Codec.STRING.optionalFieldOf("growth", "").forGetter(RecipeProcess::growth),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("cutting_flux", 0).forGetter(RecipeProcess::cuttingFlux)
    ).apply(instance, RecipeProcess::new));

    public RecipeProcess {
        ProcessFluid.byId(fluid);
        if (!product.isEmpty()) ProcessFluid.byId(product);
        if (!growth.isEmpty()) CrystalGrowth.byId(growth);
        if (minimumFlux < 0 || cuttingFlux < 0 || spectrum < -1 || spectrum > 0xFFFFFF) throw new IllegalArgumentException("Invalid optical recipe requirements");
    }
    public ProcessFluid inputFluid() { return ProcessFluid.byId(fluid); }
    public ProcessFluid outputFluid() { return product.isEmpty() ? null : ProcessFluid.byId(product); }
    public CrystalGrowth crystal() { return growth.isEmpty() ? null : CrystalGrowth.byId(growth); }
    public void write(PacketByteBuf buffer) {
        buffer.writeString(fluid, 32); buffer.writeString(product, 32); buffer.writeVarInt(minimumFlux);
        buffer.writeInt(spectrum); buffer.writeString(growth, 32); buffer.writeVarInt(cuttingFlux);
    }
    public static RecipeProcess read(PacketByteBuf buffer) {
        return new RecipeProcess(buffer.readString(32), buffer.readString(32), buffer.readVarInt(), buffer.readInt(), buffer.readString(32), buffer.readVarInt());
    }
}

package net.askcraft.justifylasers.industry;

import net.askcraft.justifylasers.registry.ModNutrients;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.Locale;

public enum ProcessFluid {
    WATER(0x479DDC), AMETHYST(0x9231DE), PHOTONITE(0xBC09F5), EMERALD(0x09CC3A), DIAMOND(0x12BEDC), PHOTOPOLYMER(0x292330);

    private final int rgb;
    ProcessFluid(int rgb) { this.rgb = rgb; }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String fluidId() { return this == PHOTOPOLYMER ? id() : id() + "_nutrient"; }
    public int rgb() { return rgb; }
    public int tint() { return 0xFF000000|rgb; }
    public net.minecraft.util.Identifier texture(boolean flowing) {
        return net.askcraft.justifylasers.platform.GameVersion.id(this==PHOTOPOLYMER?"justifylasers":"minecraft",
                "block/"+(this==PHOTOPOLYMER?"photopolymer":"water")+(flowing?"_flow":"_still"));
    }
    public int viscosity() { return this==PHOTOPOLYMER?6000:1000; }
    public Fluid fluid() { return this == WATER ? Fluids.WATER : ModNutrients.STILL.get(this); }
    public Item bucket() { return this == WATER ? Items.WATER_BUCKET : ModNutrients.BUCKETS.get(this); }
    public static ProcessFluid byId(String id) { return valueOf(id.toUpperCase(Locale.ROOT)); }
    public static ProcessFluid byIndex(int index) { return values()[Math.max(0, Math.min(values().length - 1, index))]; }
    public static ProcessFluid ofFluid(Fluid fluid) {
        for (var kind : values()) if (kind.fluid().matchesType(fluid)) return kind;
        return null;
    }
}

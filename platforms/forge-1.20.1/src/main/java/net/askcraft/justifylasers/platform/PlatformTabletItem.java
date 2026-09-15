package net.askcraft.justifylasers.platform;

import net.askcraft.justifylasers.item.ExtraterrestrialTabletItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;

final class PlatformTabletItem extends ExtraterrestrialTabletItem {
    PlatformTabletItem(Settings settings) { super(settings); }

    @Override public ICapabilityProvider initCapabilities(ItemStack stack, NbtCompound nbt) {
        return new ICapabilityProvider() {
            private final LazyOptional<PlatformTabletEnergy> energy = LazyOptional.of(() -> new PlatformTabletEnergy(stack));
            @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
                return capability == ForgeCapabilities.ENERGY ? energy.cast() : LazyOptional.empty();
            }
        };
    }
}

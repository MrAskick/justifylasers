package net.askcraft.justifylasers.compat;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.LaserOpticBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

@WailaPlugin
public final class JustifyLasersJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(LaserProvider.INSTANCE, LaserEmitterBlockEntity.class);
        registration.registerBlockDataProvider(LaserProvider.INSTANCE, LaserOpticBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(LaserProvider.INSTANCE, LaserEmitterBlock.class);
        registration.registerBlockComponent(LaserProvider.INSTANCE, LaserOpticBlock.class);
    }

    private enum LaserProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public Identifier getUid() { return JustifyLasers.id("laser_status"); }

        @Override
        public void appendServerData(NbtCompound data, BlockAccessor accessor) {
            NbtCompound laser = new NbtCompound();
            if (accessor.getBlockEntity() instanceof LaserEmitterBlockEntity emitter) {
                if (!emitter.canAccess(accessor.getPlayer())) {
                    laser.putBoolean("Locked", true);
                } else {
                    laser.putString("Status", emitter.status());
                    laser.putInt("Range", emitter.getBeamRange());
                    if (emitter.isPoweredEmitter()) {
                        laser.putInt("Energy", emitter.energy().stored());
                        laser.putInt("Capacity", emitter.energy().capacity());
                        laser.putInt("Cost", emitter.energyCost());
                    }
                }
            } else if (accessor.getBlockEntity() instanceof LaserOpticBlockEntity optic
                    && optic.kind() == LaserOpticBlock.Kind.ENERGY_RECEIVER) {
                laser.putInt("Energy", optic.energy().stored());
                laser.putInt("Capacity", optic.energy().capacity());
                laser.putInt("Input", optic.lastInput());
            }
            data.put("JustifyLasers", laser);
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            NbtCompound laser = accessor.getServerData().getCompound("JustifyLasers");
            if (laser.getBoolean("Locked")) {
                tooltip.add(Text.translatable("message.justifylasers.access_denied"));
                return;
            }
            if (laser.contains("Energy")) tooltip.add(Text.translatable("jade.justifylasers.energy",
                    laser.getInt("Energy"), laser.getInt("Capacity")));
            if (laser.contains("Cost")) tooltip.add(Text.translatable("jade.justifylasers.cost", laser.getInt("Cost")));
            if (laser.contains("Input")) tooltip.add(Text.translatable("jade.justifylasers.input", laser.getInt("Input")));
            if (laser.contains("Range")) tooltip.add(Text.translatable("jade.justifylasers.range", laser.getInt("Range")));
            if (laser.contains("Status")) tooltip.add(Text.translatable("jade.justifylasers.status",
                    Text.translatable("gui.justifylasers.powered.status." + laser.getString("Status"))));
        }
    }
}

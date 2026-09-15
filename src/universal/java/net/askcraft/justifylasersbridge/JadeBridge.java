package net.askcraft.justifylasersbridge;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;

public final class JadeBridge implements IWailaPlugin {
    private final IWailaPlugin delegate = (IWailaPlugin) LoaderBridge.plugin("compat.JustifyLasersJadePlugin");
    @Override public void register(IWailaCommonRegistration registration) { delegate.register(registration); }
    @Override public void registerClient(IWailaClientRegistration registration) { delegate.registerClient(registration); }
}

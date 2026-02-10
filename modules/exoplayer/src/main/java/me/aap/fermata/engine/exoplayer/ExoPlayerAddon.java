package me.aap.fermata.engine.exoplayer;

import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.FermataAddon;

public class ExoPlayerAddon implements FermataAddon {
    @Override
    public int getAddonId() {
        return 0;
    }

    @Override
    public AddonInfo getInfo() {
        return FermataAddon.findAddonInfo(getClass().getName());
    }

    @Override
    public void install() {
    }

    @Override
    public void uninstall() {
    }
}

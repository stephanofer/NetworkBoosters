package com.stephanofer.networkboosters;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;

public final class NetworkBoostersLoader implements PluginLoader {

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        // Runtime libraries are shaded and relocated; no network resolution during server boot.
    }
}
